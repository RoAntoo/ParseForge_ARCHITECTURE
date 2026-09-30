package dev.parseforge.application.port.out;
import dev.parseforge.domain.model.*;
public interface EngineRuntimeLocator {
    EngineState state(EngineId id);
    EngineDescriptor descriptor(EngineId id);
    EngineRuntime acquire(ConversionRequest request);
    interface EngineRuntime extends AutoCloseable {
        ProcessSpec command();
        @Override void close();
    }
}
