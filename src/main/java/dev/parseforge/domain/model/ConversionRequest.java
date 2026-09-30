package dev.parseforge.domain.model;

import java.nio.file.Path;
import java.util.Objects;

public record ConversionRequest(
        Path inputFile,
        Path outputDirectory,
        EngineId engineId,
        OutputFormat outputFormat,
        boolean forceOcr
) {
    public ConversionRequest(Path inputFile, Path outputDirectory, EngineId engineId, OutputFormat outputFormat) {
        this(inputFile, outputDirectory, engineId, outputFormat, false);
    }
    public ConversionRequest {
        inputFile = Objects.requireNonNull(inputFile, "inputFile").toAbsolutePath().normalize();
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
        Objects.requireNonNull(engineId, "engineId");
        Objects.requireNonNull(outputFormat, "outputFormat");
    }
}
