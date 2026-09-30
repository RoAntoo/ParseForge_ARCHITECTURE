package dev.parseforge.application.port.out;
import dev.parseforge.domain.model.*;
import java.nio.file.Path;
public interface EngineVerifier {
    EngineVerificationResult verify(EngineDescriptor descriptor, Path root, boolean full,
        EngineProgressListener listener, OperationCancellation cancellation);
}

