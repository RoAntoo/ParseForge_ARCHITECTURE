package dev.parseforge.application.port.out;
import dev.parseforge.domain.model.*;
public interface EngineInstaller {
    default void install(EngineDescriptor descriptor, EngineProgressListener listener, OperationCancellation cancellation) {
        install(descriptor, EngineInstallOptions.DEFAULT, listener, cancellation);
    }
    void install(EngineDescriptor descriptor, EngineInstallOptions options, EngineProgressListener listener, OperationCancellation cancellation);
    default void repair(EngineDescriptor descriptor, EngineProgressListener listener, OperationCancellation cancellation) {
        repair(descriptor, EngineInstallOptions.DEFAULT, listener, cancellation);
    }
    default void repair(EngineDescriptor descriptor, EngineInstallOptions options, EngineProgressListener listener, OperationCancellation cancellation) {
        install(descriptor, options, listener, cancellation);
    }
    void uninstall(EngineDescriptor descriptor, EngineProgressListener listener, OperationCancellation cancellation);
}
