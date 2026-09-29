package dev.parseforge.domain.model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public record ConversionResult(
        ConversionStatus status,
        int exitCode,
        List<Path> outputFiles,
        String errorMessage,
        Duration duration
) {
    public ConversionResult {
        if (status == null || !status.isTerminal()) {
            throw new IllegalArgumentException("A conversion result must have a terminal status");
        }
        outputFiles = outputFiles == null ? List.of() : List.copyOf(outputFiles);
        duration = duration == null ? Duration.ZERO : duration;
    }

    public Optional<String> error() {
        return Optional.ofNullable(errorMessage).filter(message -> !message.isBlank());
    }
}
