package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import java.util.*;

/** Routes operations to independent lifecycle locks and private directories. */
public final class MultiEngineManager implements EngineManager, EngineRuntimeLocator {
    private final Map<EngineId, ManagedEngineManager> engines = new LinkedHashMap<>();
    public MultiEngineManager(List<ManagedEngineManager> managers) {
        for (var manager : managers) for (var descriptor : manager.availableEngines())
            if (engines.putIfAbsent(descriptor.id(), manager) != null)
                throw new IllegalArgumentException("Motor duplicado: " + descriptor.id());
    }
    private ManagedEngineManager require(EngineId id) {
        var manager = engines.get(id);
        if (manager == null) throw new IllegalArgumentException("Motor no disponible: " + id);
        return manager;
    }
    public List<EngineDescriptor> availableEngines() { return engines.values().stream().flatMap(m -> m.availableEngines().stream()).toList(); }
    public EngineState getState(EngineId id) { return require(id).getState(id); }
    public EngineState state(EngineId id) { return getState(id); }
    public EngineDescriptor descriptor(EngineId id) { return require(id).descriptor(id); }
    public EngineInstallation getInstallation(EngineId id) { return require(id).getInstallation(id); }
    public EngineState check(EngineId id) { return require(id).check(id); }
    public void install(EngineId id, EngineInstallOptions options, EngineProgressListener listener) { require(id).install(id, options, listener); }
    public void repair(EngineId id, EngineInstallOptions options, EngineProgressListener listener) { require(id).repair(id, options, listener); }
    public void uninstall(EngineId id, EngineProgressListener listener) { require(id).uninstall(id, listener); }
    public void cancelCurrentOperation(EngineId id) { require(id).cancelCurrentOperation(id); }
    public EngineRuntime acquire(ConversionRequest request) { return require(request.engineId()).acquire(request); }
}
