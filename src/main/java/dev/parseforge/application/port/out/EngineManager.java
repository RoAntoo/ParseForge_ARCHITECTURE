package dev.parseforge.application.port.out;
import dev.parseforge.domain.model.*;
import java.util.List;
public interface EngineManager {
    List<EngineDescriptor> availableEngines();
    EngineState getState(EngineId id);
    EngineInstallation getInstallation(EngineId id);
    EngineState check(EngineId id);
    default void install(EngineId id, EngineProgressListener listener) { install(id, EngineInstallOptions.DEFAULT, listener); }
    void install(EngineId id, EngineInstallOptions options, EngineProgressListener listener);
    default void repair(EngineId id, EngineProgressListener listener) { repair(id, EngineInstallOptions.DEFAULT, listener); }
    void repair(EngineId id, EngineInstallOptions options, EngineProgressListener listener);
    void uninstall(EngineId id, EngineProgressListener listener);
    void cancelCurrentOperation(EngineId id);
}
