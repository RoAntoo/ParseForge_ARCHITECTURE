package dev.parseforge.application.usecase;
import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.EngineId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
public final class UninstallEngineUseCase {
    private final EngineManager manager;
    private final Executor executor;
    public UninstallEngineUseCase(EngineManager manager, Executor executor) { this.manager = manager; this.executor = executor; }
    public CompletableFuture<Void> execute(EngineId id, EngineProgressListener listener) {
        return CompletableFuture.runAsync(() -> manager.uninstall(id, listener), executor);
    }
}

