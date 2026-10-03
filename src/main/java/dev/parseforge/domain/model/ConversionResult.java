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
        Duration duration,
        dev.parseforge.domain.exception.ErrorCode errorCode
) {
    public ConversionResult(ConversionStatus status, int exitCode, List<Path> outputFiles, String errorMessage, Duration duration) {
        this(status, exitCode, outputFiles, errorMessage, duration, null);
    }
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
