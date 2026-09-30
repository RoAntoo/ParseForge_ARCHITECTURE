package dev.parseforge.application.port.out;
import dev.parseforge.domain.model.*;
import java.util.List;
public interface EngineManager {
    List<EngineDescriptor> availableEngines();
    EngineState getState(EngineId id);
    EngineInstallation getInstallation(EngineId id);
    EngineState check(EngineId id);
    void install(EngineId id, EngineProgressListener listener);
    void repair(EngineId id, EngineProgressListener listener);
    void uninstall(EngineId id, EngineProgressListener listener);
    void cancelCurrentOperation(EngineId id);
}

