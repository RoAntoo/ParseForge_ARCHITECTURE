package dev.parseforge.infrastructure.engine;

import com.fasterxml.jackson.databind.*;
import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.domain.exception.EngineInstallException;
import dev.parseforge.infrastructure.engine.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static dev.parseforge.domain.exception.EngineInstallException.Code.*;
import static dev.parseforge.domain.model.EngineInstallProgress.Phase.*;

public class PinnedPythonEngineInstaller implements EngineInstaller {
    private final EnginePathResolver paths;
    private final EngineManifestRepository manifests;
    private final DownloadClient downloads;
    private final EngineVerifier verifier;
    private final ProcessExecutor executor;
    private final ManagedPythonRuntime.Factory runtimes;
    public PinnedPythonEngineInstaller(EnginePathResolver paths, EngineManifestRepository manifests, DownloadClient downloads,
                           EngineVerifier verifier, ProcessExecutor executor, ManagedPythonRuntime.Factory runtimes) {
        this.paths = paths; this.manifests = manifests; this.downloads = downloads;
        this.verifier = verifier; this.executor = executor; this.runtimes = runtimes;
    }
    @Override public void install(EngineDescriptor descriptor, EngineInstallOptions options, EngineProgressListener listener, OperationCancellation cancellation) {
        Objects.requireNonNull(options);
        Path engines = paths.engine(descriptor.id()).getParent();
        Path staging = engines.resolve(descriptor.id() + ".installing-" + UUID.randomUUID());
        Path finalRoot = paths.engine(descriptor.id());
        Path previous = engines.resolve(descriptor.id() + ".previous");
        JsonNode manifest = manifests.manifest();
        boolean committed = false;
        EngineInstallException.Code failureCode = PERMISSION_DENIED;
        try {
            cancellation.check();
            if (!System.getProperty("os.name").startsWith("Windows") || !List.of("amd64", "x86_64").contains(System.getProperty("os.arch")))
                throw new EngineInstallException(HEALTH_CHECK_FAILED, descriptor.displayName() + " requiere Windows x64.");
            Files.createDirectories(engines);
            if (manifest.has("llamaCpp")) dev.parseforge.infrastructure.process.WindowsNativePrerequisites.requireMarkerRuntime();
            EngineFiles.safeResolve(paths.dataRoot(), paths.dataRoot().relativize(engines).toString());
            if (Files.getFileStore(engines).getUsableSpace() < manifest.path("minimumFreeBytes").asLong())
                throw new EngineInstallException(DISK_SPACE_LOW, "Espacio libre insuficiente para instalar " + descriptor.displayName() + ".");
            listener.onProgress(EngineInstallProgress.phase(PREPARING, "Preparando instalación de " + descriptor.displayName() + "."));
            Files.createDirectory(staging);
            Files.createDirectories(staging.resolve("downloads/wheels"));
            Files.createDirectories(staging.resolve("logs"));
            Files.createDirectories(staging.resolve("runtime/python/Lib/site-packages"));
            var pythonZip = download(manifest.path("python"), staging.resolve("downloads/python.zip"), listener, cancellation);
            Path llamaZip = manifest.has("llamaCpp") ? download(manifest.path("llamaCpp"), staging.resolve("downloads/llama.zip"), listener, cancellation) : null;
            var pipWheel = download(manifest.path("pip"), staging.resolve("downloads/pip.whl"), listener, cancellation);
            failureCode = EXTRACTION_FAILED;
            listener.onProgress(EngineInstallProgress.phase(EXTRACTING, "Extrayendo runtimes verificados..."));
            EngineFiles.extract(pythonZip, staging.resolve("runtime/python"), cancellation);
            String pythonVersion = manifest.path("python").path("version").asText();
            String pthStem = "python" + pythonVersion.split("\\.")[0] + pythonVersion.split("\\.")[1];
            EngineFiles.extract(staging.resolve("runtime/python/" + pthStem + ".zip"), staging.resolve("runtime/python/Lib"), cancellation);
            Files.writeString(staging.resolve("runtime/python/" + pthStem + "._pth"), "Lib\n.\nLib/site-packages\nimport site\n");
            EngineFiles.extract(pipWheel, staging.resolve("runtime/python/Lib/site-packages"), cancellation);
            if (llamaZip != null) EngineFiles.extract(llamaZip, staging.resolve("runtime/llamacpp"), cancellation);
            // Upstream package must match the reviewed layout; never search PATH.
            Files.write(staging.resolve("downloads/requirements.lock"), manifests.lock());
            for (JsonNode wheel : manifest.path("packages").path("wheels")) {
                Path target = EngineFiles.safeResolve(staging.resolve("downloads/wheels"), wheel.path("file").asText());
                download(wheel, target, listener, cancellation);
            }
            failureCode = PACKAGE_INSTALL_FAILED;
            listener.onProgress(EngineInstallProgress.phase(INSTALLING_PACKAGES, "Instalando paquetes con lock y hashes..."));
            var runtime = runtimes.create(staging, manifest);
            run(runtime.python(List.of("-m", "pip", "--isolated", "--disable-pip-version-check", "install",
                    "--no-index", "--only-binary=:all:", "--no-compile", "--no-warn-script-location", "--find-links",
                    staging.resolve("downloads/wheels").toString(), "--require-hashes",
                    "-r", staging.resolve("downloads/requirements.lock").toString()), Duration.ofMinutes(15)),
                    staging.resolve("logs/install.log"), listener, cancellation);
            failureCode = MODEL_DOWNLOAD_FAILED;
            if (!manifest.path("models").isEmpty()) listener.onProgress(EngineInstallProgress.phase(PREPARING_MODELS, "Preparando modelos privados con revisiones fijas..."));
            for (JsonNode model : manifest.path("models"))
                download(model, EngineFiles.safeResolve(staging, model.path("path").asText()), listener, cancellation);
            for (JsonNode asset : manifest.path("assets"))
                download(asset, EngineFiles.safeResolve(staging, asset.path("path").asText()), listener, cancellation);
            for (JsonNode ref : manifest.path("refs")) {
                Path target = EngineFiles.safeResolve(staging, ref.path("path").asText());
                Files.createDirectories(target.getParent()); Files.writeString(target, ref.path("content").asText());
            }
            var metadata = new ObjectMapper().createObjectNode();
            for (String key : List.of("schemaVersion", "id", "platform", "engineVersion")) metadata.set(key, manifest.path(key));
            metadata.put("pythonVersion", pythonVersion);
            metadata.set("python", manifest.path("python"));
            metadata.set("pip", manifest.path("pip"));
            metadata.set("packages", manifest.path("packages"));
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(staging.resolve("engine.json").toFile(), metadata);
            failureCode = HEALTH_CHECK_FAILED;
            var health = verifier.verify(descriptor, staging, true, options.runHealthCheck(), listener, cancellation);
            if (!health.ready()) throw new EngineInstallException(HEALTH_CHECK_FAILED, health.detail());
            if (!options.runHealthCheck()) listener.onProgress(EngineInstallProgress.phase(VERIFYING,
                    "Archivos verificados. Prueba de funcionamiento omitida."));
            cancellation.check();
            // No more cancellation points after this commit boundary.
            EngineFiles.deleteTree(staging, staging.resolve("downloads"));
            EngineFiles.deleteTree(staging, staging.resolve("cache/pip"));
            // Windows pip launchers embed staging paths; conversion uses private
            // Python and the module entrypoint, so those launchers are unnecessary.
            EngineFiles.deleteTree(staging, staging.resolve("runtime/python/Scripts"));
            EngineFiles.safeResolve(engines, descriptor.id().value());
            EngineFiles.safeResolve(engines, previous.getFileName().toString());
            if (Files.exists(previous)) throw new IOException("Hay un backup pendiente de recuperación");
            if (Files.exists(finalRoot)) Files.move(finalRoot, previous, StandardCopyOption.ATOMIC_MOVE);
            try { Files.move(staging, finalRoot, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException error) {
                if (Files.exists(previous)) Files.move(previous, finalRoot, StandardCopyOption.ATOMIC_MOVE);
                throw error;
            }
            committed = true;
            // A cleanup failure must not roll back a successfully committed runtime.
            try { EngineFiles.deleteTree(engines, previous); }
            catch (IOException error) { listener.onProgress(EngineInstallProgress.phase(COMPLETED, descriptor.displayName() + " listo; quedó un backup para limpiar al reiniciar.")); }
            listener.onProgress(EngineInstallProgress.phase(COMPLETED, options.runHealthCheck()
                    ? descriptor.displayName() + " instalado y verificado."
                    : descriptor.displayName() + " instalado. Archivos verificados; prueba de funcionamiento omitida."));
        } catch (EngineInstallException error) { throw error; }
        catch (Exception error) {
            cancellation.check();
            throw new EngineInstallException(error instanceof AccessDeniedException ? PERMISSION_DENIED : failureCode,
                    "No se pudo preparar " + descriptor.displayName() + ": " + error.getMessage(), error);
        } finally {
            // Preserve diagnostics outside staging even when installation fails.
            try {
                for (String name : List.of("install", "health-check")) {
                    Path installLog = (committed ? finalRoot : staging).resolve("logs/" + name + ".log");
                    if (!Files.isRegularFile(installLog)) continue;
                    Path logs = paths.dataRoot().resolve("logs");
                    Files.createDirectories(logs);
                    Path diagnostic = EngineFiles.safeResolve(paths.dataRoot(), "logs/" + staging.getFileName()
                            + (name.equals("install") ? "" : ".health-check") + ".log");
                    Files.copy(installLog, diagnostic, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) { }
            try { EngineFiles.deleteTree(engines, staging); } catch (IOException ignored) { }
        }
    }
    private Path download(JsonNode item, Path target, EngineProgressListener listener, OperationCancellation cancellation) {
        return downloads.download(new DownloadRequest(URI.create(item.path("url").asText()), target,
                item.path("sha256").asText(), item.path("bytes").asLong(-1)),
                (bytes, total) -> listener.onProgress(new EngineInstallProgress(DOWNLOADING,
                        "Descargando " + target.getFileName(), bytes, total)), cancellation).file();
    }
    private void run(ProcessSpec spec, Path log, EngineProgressListener listener, OperationCancellation cancellation) throws Exception {
        cancellation.check();
        try (var writer = Files.newBufferedWriter(log); var registration = cancellation.onCancel(executor::cancel)) {
            ProcessResult result = CancellableProcessRunner.execute(executor, spec, (stream, line) -> {
                synchronized (writer) {
                    try { writer.write("[" + stream + "] " + line); writer.newLine(); writer.flush(); }
                    catch (IOException error) { throw new UncheckedIOException(error); }
                }
                listener.onProgress(EngineInstallProgress.phase(INSTALLING_PACKAGES, "[" + stream + "] " + line));
            }, cancellation);
            cancellation.check();
            if (result.exitCode() != 0 || result.cancelled() || result.timedOut())
                throw new EngineInstallException(PACKAGE_INSTALL_FAILED, "No se pudieron instalar las dependencias. Revisá el log de instalación.");
        }
    }
    @Override public void uninstall(EngineDescriptor descriptor, EngineProgressListener listener, OperationCancellation cancellation) {
        Path root = paths.engine(descriptor.id());
        Path removed = root.resolveSibling(descriptor.id() + ".removing");
        try {
            cancellation.check();
            listener.onProgress(EngineInstallProgress.phase(REMOVING, "Desinstalando " + descriptor.displayName() + " y sus archivos privados..."));
            EngineFiles.safeResolve(root.getParent(), removed.getFileName().toString());
            EngineFiles.safeResolve(root.getParent(), root.getFileName().toString());
            if (Files.exists(root)) Files.move(root, removed, StandardCopyOption.ATOMIC_MOVE);
            // Committed removal is finished even if cancellation is requested now.
            EngineFiles.deleteTree(root.getParent(), removed);
            listener.onProgress(EngineInstallProgress.phase(COMPLETED, descriptor.displayName() + " desinstalado."));
        } catch (Exception error) {
            throw new EngineInstallException(PERMISSION_DENIED, "No se pudo eliminar el motor. Reintentá la desinstalación.", error);
        }
    }
}
