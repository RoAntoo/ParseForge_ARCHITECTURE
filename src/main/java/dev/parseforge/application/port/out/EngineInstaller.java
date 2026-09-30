package dev.parseforge.application.port.out;
import dev.parseforge.domain.model.EngineDescriptor;
public interface EngineInstaller {
    void install(EngineDescriptor descriptor, EngineProgressListener listener, OperationCancellation cancellation);
    default void repair(EngineDescriptor descriptor, EngineProgressListener listener, OperationCancellation cancellation) {
        install(descriptor, listener, cancellation);
    }
    void uninstall(EngineDescriptor descriptor, EngineProgressListener listener, OperationCancellation cancellation);
}

