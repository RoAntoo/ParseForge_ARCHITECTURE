package dev.parseforge.application.usecase;
import dev.parseforge.application.port.out.EngineManager;
import dev.parseforge.domain.model.*;
import java.util.concurrent.*;
public final class CheckEngineStatusUseCase {
    private final EngineManager manager;
    private final Executor executor;
    public CheckEngineStatusUseCase(EngineManager manager, Executor executor) { this.manager = manager; this.executor = executor; }
    public CompletableFuture<EngineState> execute(EngineId id) { return CompletableFuture.supplyAsync(() -> manager.check(id), executor); }
    public EngineState state(EngineId id) { return manager.getState(id); }
    public EngineInstallation installation(EngineId id) { return manager.getInstallation(id); }
}
