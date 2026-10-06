package dev.parseforge.infrastructure.engine.markitdown;

import com.fasterxml.jackson.databind.*;
import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.domain.exception.EngineInstallException;
import dev.parseforge.infrastructure.engine.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;

public final class MarkItDownEngineVerifier implements EngineVerifier {
    private final EngineManifestRepository manifests;
    private final ChecksumVerifier checksums;
    private final ProcessExecutor executor;
    public MarkItDownEngineVerifier(EngineManifestRepository manifests, ChecksumVerifier checksums, ProcessExecutor executor) {
        this.manifests = manifests; this.checksums = checksums; this.executor = executor;
    }
    public EngineVerificationResult verify(EngineDescriptor descriptor, Path root, boolean full, boolean runHealthCheck,
            EngineProgressListener listener, OperationCancellation cancellation) {
        try {
            cancellation.check();
            var tree = new EngineFiles.CheckedTree(root);
            JsonNode expected = manifests.manifest();
            JsonNode installed = new ObjectMapper().readTree(tree.resolve("engine.json").toFile());
            for (String field : List.of("schemaVersion", "id", "platform", "engineVersion"))
                if (!expected.path(field).equals(installed.path(field))) throw new IOException("Manifiesto instalado incompatible");
            if (!expected.path("python").path("version").asText().equals(installed.path("pythonVersion").asText()))
                throw new IOException("Versión de Python incompatible");
            listener.onProgress(EngineInstallProgress.phase(EngineInstallProgress.Phase.VERIFYING, "Verificando archivos de MarkItDown..."));
            for (JsonNode file : expected.path("criticalFiles")) {
                cancellation.check();
                String relative = file.path("path").asText();
                Path path = tree.resolve(relative);
                if (!Files.isRegularFile(path) || (file.has("bytes") && Files.size(path) != file.path("bytes").asLong()))
                    throw new IOException("Archivo incompleto: " + relative);
                // Hash executable/config on startup; full verification hashes every dependency.
                if (full || (relative.startsWith("runtime/python/") && !relative.substring("runtime/python/".length()).contains("/")))
                    checksums.verify(path, file.path("sha256").asText(), cancellation);
            }
            if (runHealthCheck) {
                var runtime = new ManagedMarkItDownRuntime(root, expected);
                String audit = "import sys,importlib.metadata as m,markitdown,pdfminer,mammoth,lxml,pptx,pandas,openpyxl,xlrd,olefile; from pathlib import Path; r=Path.cwd().resolve(); "
                        + "assert all(Path(p).resolve().is_relative_to(r) for p in [sys.executable,sys.prefix,sys.base_prefix,*sys.path]); "
                        + "assert m.version('markitdown')=='" + descriptor.version() + "'; print('MarkItDown privado listo')";
                Path log = tree.resolve("logs/health-check.log"); Files.createDirectories(log.getParent());
                try (var writer = Files.newBufferedWriter(log)) {
                    for (var spec : List.of(runtime.python(List.of("-c", audit), Duration.ofMinutes(1)),
                            runtime.python(List.of("-m", "pip", "check"), Duration.ofMinutes(1)))) {
                        listener.onProgress(EngineInstallProgress.phase(EngineInstallProgress.Phase.HEALTH_CHECKING, "Probando MarkItDown privado..."));
                        var result = CancellableProcessRunner.execute(executor, spec, (stream, line) -> {
                            synchronized (writer) {
                                try { writer.write("[" + stream + "] " + line); writer.newLine(); writer.flush(); }
                                catch (IOException error) { throw new UncheckedIOException(error); }
                            }
                            listener.onProgress(EngineInstallProgress.phase(EngineInstallProgress.Phase.HEALTH_CHECKING, "[" + stream + "] " + line));
                        }, cancellation);
                        writer.write("exit=" + result.exitCode() + ", timedOut=" + result.timedOut() + ", cancelled=" + result.cancelled());
                        writer.newLine(); writer.flush();
                        cancellation.check();
                        if (result.exitCode() != 0 || result.cancelled() || result.timedOut()) throw new IOException("Falló la prueba de MarkItDown. Revisá el log de funcionamiento.");
                    }
                }
            }
            return new EngineVerificationResult(true, "MarkItDown listo");
        } catch (EngineInstallException error) {
            if (error.code() == EngineInstallException.Code.INSTALL_CANCELLED) throw error;
            return new EngineVerificationResult(false, error.getMessage());
        } catch (Exception error) {
            cancellation.check(); return new EngineVerificationResult(false, error.getMessage());
        }
    }
}
