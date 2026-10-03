package dev.parseforge.infrastructure.engine;

import dev.parseforge.domain.exception.ConversionException;
import dev.parseforge.domain.exception.ErrorCode;
import dev.parseforge.domain.model.ConversionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Keeps failed/partial conversions away from previously exported documents. */
public final class ConversionOutputWorkspace implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ConversionOutputWorkspace.class);
    private final Path destination;
    private final ConversionRequest request;
    private boolean retainForRecovery;

    public ConversionOutputWorkspace(ConversionRequest original) {
        try {
            Files.createDirectories(original.outputDirectory());
            destination = original.outputDirectory().toRealPath();
            ConversionStorageGuard.check(original.inputFile(), destination);
            Path staging = Files.createTempDirectory(destination, ".parseforge-");
            request = new ConversionRequest(original.inputFile(), staging, original.engineId(),
                    original.outputFormat(), original.forceOcr());
        } catch (IOException error) {
            throw new ConversionException(ErrorCode.PERMISSION_DENIED,
                    "No fue posible preparar la carpeta de salida.", error);
        }
    }

    public ConversionRequest request() { return request; }

    public List<Path> publish(Path markdown) {
        Path staging = request.outputDirectory();
        try {
            Path expected = EngineFiles.safeResolve(staging, staging.relativize(markdown).toString());
            if (!Files.isRegularFile(expected, LinkOption.NOFOLLOW_LINKS) || Files.size(expected) == 0) {
                throw new ConversionException(ErrorCode.OUTPUT_NOT_CREATED,
                        "El motor no generó el Markdown esperado o el archivo está vacío.");
            }
            List<Path> files;
            try (var entries = Files.walk(staging)) {
                files = entries.filter(path -> !path.equals(staging)).toList();
            }
            // Validate every source and destination before changing existing outputs.
            for (Path source : files) {
                String relative = staging.relativize(source).toString();
                EngineFiles.safeResolve(staging, relative);
                Path target = EngineFiles.safeResolve(destination, relative);
                boolean directory = Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS);
                if (!directory && !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Archivo de salida no regular: " + source);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                        && directory != Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Conflicto en la ruta de salida: " + target);
            }
            // Assets first; publish the Markdown only once its dependencies are in place.
            for (Path source : files) {
                Path target = EngineFiles.safeResolve(destination, staging.relativize(source).toString());
                if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(target);
                else if (!source.equals(expected)) Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Path target = destination.resolve(staging.relativize(expected));
            Files.move(expected, target, StandardCopyOption.REPLACE_EXISTING);
            return List.of(target);
        } catch (IOException error) {
            retainForRecovery = true;
            throw new ConversionException(ErrorCode.OUTPUT_NOT_CREATED,
                    "No fue posible guardar la conversión. Los archivos generados se conservan en " + staging, error);
        }
    }

    @Override public void close() {
        if (retainForRecovery) return;
        try {
            EngineFiles.deleteTree(destination, request.outputDirectory());
        } catch (IOException error) {
            log.warn("Could not remove conversion workspace {}", request.outputDirectory(), error);
        }
    }
}
