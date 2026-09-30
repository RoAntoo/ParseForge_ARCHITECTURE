package dev.parseforge.application.port.out;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ProcessSpec(
        Path executable,
        List<String> arguments,
        Map<String, String> environment,
        Path workingDirectory,
        Duration timeout,
        boolean inheritEnvironment
) {
    public ProcessSpec(Path executable, List<String> arguments, Map<String, String> environment,
                       Path workingDirectory, Duration timeout) {
        this(executable, arguments, environment, workingDirectory, timeout, true);
    }
    public ProcessSpec {
        executable = Objects.requireNonNull(executable, "executable").toAbsolutePath().normalize();
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
