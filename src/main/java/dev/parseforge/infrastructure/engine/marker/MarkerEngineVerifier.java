package dev.parseforge.infrastructure.engine.marker;

import com.fasterxml.jackson.databind.*;
import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.domain.exception.EngineInstallException;
import dev.parseforge.infrastructure.engine.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static dev.parseforge.domain.model.EngineInstallProgress.Phase.*;

public final class MarkerEngineVerifier implements EngineVerifier {
    private final EngineManifestRepository manifests;
    private final ChecksumVerifier checksums;
    private final ProcessExecutor executor;
    public MarkerEngineVerifier(EngineManifestRepository manifests, ChecksumVerifier checksums, ProcessExecutor executor) {
        this.manifests = manifests; this.checksums = checksums; this.executor = executor;
    }
    @Override public EngineVerificationResult verify(EngineDescriptor descriptor, Path root, boolean full, boolean runHealthCheck,
            EngineProgressListener listener, OperationCancellation cancellation) {
        try {
            cancellation.check();
            var tree = new EngineFiles.CheckedTree(root);
            JsonNode expected = manifests.manifest();
            Path metadata = EngineFiles.safeResolve(root, "engine.json");
            JsonNode installed = new ObjectMapper().readTree(metadata.toFile());
            for (String field : List.of("schemaVersion", "id", "platform", "engineVersion")) {
                if (!expected.path(field).equals(installed.path(field))) throw new java.io.IOException("Manifiesto instalado incompatible");
            }
            listener.onProgress(EngineInstallProgress.phase(VERIFYING, "Verificando archivos de Marker..."));
            for (JsonNode file : expected.path("criticalFiles")) {
                cancellation.check();
                String relative = file.path("path").asText();
                Path path = tree.resolve(relative);
                if (!Files.isRegularFile(path)) throw new java.io.IOException("Falta archivo: " + relative);
                if (full || relative.startsWith("runtime/llamacpp/") ||
                        (relative.startsWith("runtime/python/") && !relative.substring("runtime/python/".length()).contains("/")))
                    checksums.verify(path, file.path("sha256").asText(), cancellation);
            }
            for (JsonNode file : expected.path("models")) {
                Path path = tree.resolve(file.path("path").asText());
                if (!Files.isRegularFile(path) || Files.size(path) != file.path("bytes").asLong())
                    throw new java.io.IOException("Modelo incompleto: " + path.getFileName());
                if (full) checksums.verify(path, file.path("sha256").asText(), cancellation);
            }
            for (JsonNode ref : expected.path("refs")) {
                Path path = tree.resolve(ref.path("path").asText());
                checksums.verify(path, ref.path("sha256").asText(), cancellation);
            }
            if (runHealthCheck) {
                listener.onProgress(EngineInstallProgress.phase(HEALTH_CHECKING, "Probando Python, Marker, Surya, Torch CPU y llama.cpp..."));
                var runtime = new ManagedMarkerRuntime(root, expected);
                String audit = "import sys,importlib.metadata as m,torch,torchvision,marker,surya; from pathlib import Path; "
                        + "root=Path.cwd().resolve(); "
                        + "assert all(Path(p).resolve().is_relative_to(root) for p in [sys.executable,sys.prefix,sys.base_prefix,*sys.path]); "
                        + "assert '+cpu' in torch.__version__ and '+cpu' in torchvision.__version__; "
                        + "assert torch.version.cuda is None and torch.version.hip is None; "
                        + "assert m.version('marker-pdf')==" + "'" + descriptor.version() + "'; print('Marker CPU listo')";
                List<ProcessSpec> specs = List.of(
                        runtime.python(List.of("--version"), Duration.ofSeconds(30)),
                        runtime.python(List.of("-c", audit), Duration.ofMinutes(3)),
                        runtime.python(List.of("-m", "pip", "check"), Duration.ofMinutes(1)),
                        runtime.python(List.of("-c", ManagedMarkerRuntime.ENTRYPOINT, "--help"), Duration.ofMinutes(3)),
                        new ProcessSpec(runtime.resolve(expected.path("llamaCpp").path("executable").asText()),
                                List.of("--version"), runtime.environment(), runtime.root(), Duration.ofSeconds(30), false));
                List<String> names = List.of("Python", "Marker / Torch CPU", "Dependencias Python", "Apertura de Marker", "llama.cpp");
                Path diagnostic = tree.resolve("logs/health-check.log");
                Files.createDirectories(diagnostic.getParent());
                try (var writer = Files.newBufferedWriter(diagnostic)) {
                    for (int index = 0; index < specs.size(); index++) {
                        var spec = specs.get(index);
                        String name = names.get(index);
                        String starting = "Probando " + name + " (límite: " + spec.timeout().toSeconds() + " s)...";
                        writer.write(starting); writer.newLine(); writer.flush();
                        listener.onProgress(EngineInstallProgress.phase(HEALTH_CHECKING, starting));
                        cancellation.check();
                        ProcessResult result;
                        try (var registered = cancellation.onCancel(executor::cancel)) {
                            result = CancellableProcessRunner.execute(executor, spec, (stream, line) -> {
                                synchronized (writer) {
                                    try { writer.write("[" + stream + "] " + line); writer.newLine(); writer.flush(); }
                                    catch (IOException error) { throw new UncheckedIOException(error); }
                                }
                                listener.onProgress(EngineInstallProgress.phase(HEALTH_CHECKING, "[" + stream + "] " + line));
                            }, cancellation);
                        } catch (Exception error) {
                            if (error instanceof EngineInstallException installError
                                    && installError.code() == EngineInstallException.Code.INSTALL_CANCELLED) throw installError;
                            cancellation.check();
                            synchronized (writer) {
                                writer.write(name + ": error al ejecutar la prueba"); writer.newLine();
                                error.printStackTrace(new PrintWriter(writer)); writer.flush();
                            }
                            throw new IOException("No se pudo ejecutar la prueba de " + name + ": " + error.getMessage(), error);
                        }
                        writer.write(name + ": exit=" + result.exitCode() + ", timedOut=" + result.timedOut()
                                + ", cancelled=" + result.cancelled() + ", elapsedMs=" + result.duration().toMillis());
                        writer.newLine(); writer.flush();
                        cancellation.check();
                        if (result.exitCode() != 0 || result.timedOut() || result.cancelled())
                            throw new IOException(result.timedOut()
                                    ? "La prueba de " + name + " superó el límite de " + spec.timeout().toSeconds()
                                            + " s. Podés reintentar sin la prueba de funcionamiento opcional."
                                    : "Falló la prueba de " + name + " (código " + result.exitCode() + "). Revisá el log de funcionamiento.");
                    }
                }
            }
            return new EngineVerificationResult(true, "Marker listo");
        } catch (EngineInstallException error) {
            if (error.code() == EngineInstallException.Code.INSTALL_CANCELLED) throw error;
            return new EngineVerificationResult(false, error.getMessage());
        } catch (Exception error) {
            cancellation.check();
            return new EngineVerificationResult(false, error.getMessage());
        }
    }
}
