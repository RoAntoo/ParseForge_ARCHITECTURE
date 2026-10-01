package dev.parseforge.application.port.out;
import dev.parseforge.domain.model.*;
import java.nio.file.Path;
public interface EngineVerifier {
    default EngineVerificationResult verify(EngineDescriptor descriptor, Path root, boolean full,
            EngineProgressListener listener, OperationCancellation cancellation) {
        return verify(descriptor, root, full, full, listener, cancellation);
    }
    EngineVerificationResult verify(EngineDescriptor descriptor, Path root, boolean full, boolean runHealthCheck,
        EngineProgressListener listener, OperationCancellation cancellation);
}
