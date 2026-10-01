package dev.parseforge.application.usecase;
import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
public final class RepairEngineUseCase {
    private final EngineManager manager;
    private final Executor executor;
    public RepairEngineUseCase(EngineManager manager, Executor executor) { this.manager = manager; this.executor = executor; }
    public CompletableFuture<Void> execute(EngineId id, EngineProgressListener listener) {
        return execute(id, EngineInstallOptions.DEFAULT, listener);
    }
    public CompletableFuture<Void> execute(EngineId id, EngineInstallOptions options, EngineProgressListener listener) {
        return CompletableFuture.runAsync(() -> manager.repair(id, options, listener), executor);
    }
}
