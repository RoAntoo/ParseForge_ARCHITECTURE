package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.domain.exception.*;
import dev.parseforge.infrastructure.engine.marker.ManagedMarkerRuntime;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

/** Owns the engine lifecycle; a conversion lease excludes installation/removal. */
public final class ManagedEngineManager implements EngineManager, EngineRuntimeLocator {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ManagedEngineManager.class);
    private final EnginePathResolver paths;
    private final EngineManifestRepository manifests;
    private final EngineInstaller installer;
    private final EngineVerifier verifier;
    private final ManagedPythonRuntime.Factory runtimes;
    private final ReentrantLock lifecycle = new ReentrantLock();
    private volatile EngineState state = EngineState.NOT_INSTALLED;
    private volatile OperationCancellation current;
    public ManagedEngineManager(EnginePathResolver paths, EngineManifestRepository manifests,
                                EngineInstaller installer, EngineVerifier verifier) {
        this(paths, manifests, installer, verifier, ManagedMarkerRuntime::new);
    }
    public ManagedEngineManager(EnginePathResolver paths, EngineManifestRepository manifests,
                                EngineInstaller installer, EngineVerifier verifier, ManagedPythonRuntime.Factory runtimes) {
        this.paths = paths; this.manifests = manifests; this.installer = installer; this.verifier = verifier;
        this.runtimes = runtimes;
    }
    private EngineDescriptor require(EngineId id) {
        if (!manifests.descriptor().id().equals(id)) throw new IllegalArgumentException("Motor no disponible: " + id);
        return manifests.descriptor();
    }
    @Override public List<EngineDescriptor> availableEngines() { return List.of(manifests.descriptor()); }
    @Override public EngineState getState(EngineId id) { require(id); return state; }
    @Override public EngineState state(EngineId id) { return getState(id); }
    @Override public EngineDescriptor descriptor(EngineId id) { return require(id); }
    @Override public EngineInstallation getInstallation(EngineId id) {
        return new EngineInstallation(id, new EngineVersion(require(id).version()), paths.engine(id), state);
    }
    @Override public EngineState check(EngineId id) {
        require(id);
        if (!lifecycle.tryLock()) return state;
        LifecycleFileLock diskLock = null;
        try {
            diskLock = fileLock(id);
            if (diskLock == null) { state = EngineState.BUSY; return state; }
            recover(id);
            state = installedState(id);
            return state;
        } catch (IOException error) { state = EngineState.BROKEN; return state; }
        finally { if (diskLock != null) diskLock.close(); lifecycle.unlock(); }
    }
    private EngineState installedState(EngineId id) {
        Path root = paths.engine(id);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return EngineState.NOT_INSTALLED;
        return verifier.verify(require(id), root, false, false, ignored -> {}, new OperationCancellation()).ready()
                ? EngineState.READY : EngineState.BROKEN;
    }
    private void recover(EngineId id) throws IOException {
        Path root = paths.engine(id);
        Path previous = root.resolveSibling(id + ".previous");
        if (Files.exists(previous)) {
            EngineFiles.safeResolve(root.getParent(), previous.getFileName().toString());
            EngineFiles.safeResolve(root.getParent(), root.getFileName().toString());
            if (!Files.exists(root)) Files.move(previous, root, StandardCopyOption.ATOMIC_MOVE);
            else if (installedState(id) == EngineState.READY) EngineFiles.deleteTree(root.getParent(), previous);
            else {
                Path broken = root.resolveSibling(id + ".installing-recovered-" + UUID.randomUUID());
                Files.move(root, broken, StandardCopyOption.ATOMIC_MOVE);
                Files.move(previous, root, StandardCopyOption.ATOMIC_MOVE);
                EngineFiles.deleteTree(root.getParent(), broken);
            }
        }
        EngineFiles.deleteTree(root.getParent(), root.resolveSibling(id + ".removing"));
        if (Files.isDirectory(root.getParent())) {
            try (var entries = Files.list(root.getParent())) {
                for (Path entry : entries.filter(p -> p.getFileName().toString().startsWith(id + ".installing-")).toList())
                    EngineFiles.deleteTree(root.getParent(), entry);
            }
        }
    }
    @Override public void install(EngineId id, EngineInstallOptions options, EngineProgressListener listener) { operate(id, options, listener, 0); }
    @Override public void repair(EngineId id, EngineInstallOptions options, EngineProgressListener listener) { operate(id, options, listener, 1); }
    @Override public void uninstall(EngineId id, EngineProgressListener listener) { operate(id, EngineInstallOptions.DEFAULT, listener, 2); }
    private void operate(EngineId id, EngineInstallOptions options, EngineProgressListener listener, int operation) {
        Objects.requireNonNull(options);
        var descriptor = require(id);
        if (!lifecycle.tryLock()) throw new IllegalStateException("El motor está ocupado. Esperá a que termine la conversión u operación.");
        var cancellation = new OperationCancellation();
        LifecycleFileLock diskLock = null;
        current = cancellation;
        log.info("Engine lifecycle operation started; engine={}, operation={}", id, operation);
        try {
            diskLock = fileLock(id);
            if (diskLock == null) throw new IllegalStateException(manifests.descriptor().displayName() + " está en uso por otra ventana de ParseForge.");
            recover(id);
            state = operation == 2 ? EngineState.REMOVING : EngineState.INSTALLING;
            EngineProgressListener progress = event -> {
                if (event.phase() == EngineInstallProgress.Phase.DOWNLOADING) state = EngineState.DOWNLOADING;
                else if (event.phase() == EngineInstallProgress.Phase.VERIFYING ||
                        event.phase() == EngineInstallProgress.Phase.HEALTH_CHECKING) state = EngineState.VERIFYING;
                else if (event.phase() != EngineInstallProgress.Phase.COMPLETED) state = operation == 2 ? EngineState.REMOVING : EngineState.INSTALLING;
                listener.onProgress(event);
            };
            if (operation == 2) installer.uninstall(descriptor, progress, cancellation);
            else if (operation == 1) installer.repair(descriptor, options, progress, cancellation);
            else installer.install(descriptor, options, progress, cancellation);
        } catch (RuntimeException error) {
            log.warn("Engine lifecycle operation failed; engine={}, operation={}", id, operation, error);
            listener.onProgress(EngineInstallProgress.phase(error instanceof EngineInstallException installError &&
                    installError.code() == EngineInstallException.Code.INSTALL_CANCELLED
                    ? EngineInstallProgress.Phase.CANCELLED : EngineInstallProgress.Phase.FAILED, error.getMessage()));
            throw error;
        } catch (IOException error) { throw new IllegalStateException("No se pudo recuperar la operación anterior", error); }
        finally {
            current = null;
            boolean interrupted = Thread.interrupted();
            try { if (diskLock != null) state = installedState(id); }
            finally {
                if (diskLock != null) diskLock.close();
                lifecycle.unlock(); if (interrupted) Thread.currentThread().interrupt();
                log.info("Engine lifecycle operation ended; engine={}, state={}", id, state);
            }
        }
    }
    @Override public void cancelCurrentOperation(EngineId id) {
        require(id); var operation = current; if (operation != null) operation.cancel();
    }
    @Override public EngineRuntime acquire(ConversionRequest request) {
        require(request.engineId());
        if (!lifecycle.tryLock()) throw new ConversionException(ErrorCode.ENGINE_NOT_INSTALLED, manifests.descriptor().displayName() + " está ocupado. Esperá a que termine la operación.");
        LifecycleFileLock diskLock = null;
        try {
            diskLock = fileLock(request.engineId());
            if (diskLock == null) throw new ConversionException(ErrorCode.ENGINE_NOT_INSTALLED, manifests.descriptor().displayName() + " está en uso por otra ventana de ParseForge.");
            state = installedState(request.engineId());
            if (state != EngineState.READY) throw new ConversionException(ErrorCode.ENGINE_NOT_INSTALLED,
                    state == EngineState.BROKEN ? manifests.descriptor().displayName() + " necesita reparación. Abrí Motores de conversión." : "Instalá " + manifests.descriptor().displayName() + " desde Motores de conversión.");
            ProcessSpec command = runtimes.create(paths.engine(request.engineId()), manifests.manifest()).conversion(request);
            LifecycleFileLock lease = diskLock;
            return new EngineRuntime() {
                private boolean closed;
                @Override public ProcessSpec command() { return command; }
                @Override public void close() { if (!closed) { closed = true; lease.close(); lifecycle.unlock(); } }
            };
        } catch (RuntimeException error) { if (diskLock != null) diskLock.close(); lifecycle.unlock(); throw error; }
        catch (IOException error) {
            if (diskLock != null) diskLock.close();
            lifecycle.unlock();
            throw new ConversionException(ErrorCode.ENGINE_NOT_INSTALLED, manifests.descriptor().displayName() + " necesita reparación.", error);
        }
    }
    private LifecycleFileLock fileLock(EngineId id) throws IOException {
        Path engines = paths.engine(id).getParent();
        Files.createDirectories(engines);
        Path lockPath = EngineFiles.safeResolve(paths.dataRoot(), "engines/." + id + ".lock");
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock != null) return new LifecycleFileLock(channel, lock);
        } catch (OverlappingFileLockException busy) { }
        channel.close(); return null;
    }
    private record LifecycleFileLock(FileChannel channel, FileLock lock) {
        void close() {
            try { lock.release(); } catch (IOException ignored) { }
            try { channel.close(); } catch (IOException ignored) { }
        }
    }
}
