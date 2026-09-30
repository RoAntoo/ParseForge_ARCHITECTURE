package dev.parseforge.application.usecase;
import dev.parseforge.application.port.out.EngineManager;
import dev.parseforge.domain.model.EngineId;
public record CancelEngineOperationUseCase(EngineManager manager) {
    public void execute(EngineId id) { manager.cancelCurrentOperation(id); }
}
