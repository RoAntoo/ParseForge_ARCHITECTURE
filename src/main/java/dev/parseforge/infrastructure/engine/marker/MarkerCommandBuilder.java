package dev.parseforge.infrastructure.engine.marker;

import dev.parseforge.application.port.out.ProcessSpec;
import dev.parseforge.domain.model.ConversionRequest;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MarkerCommandBuilder {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofHours(6);

    public ProcessSpec build(Path executable, ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        return new ProcessSpec(
                executable,
                List.of(
                        request.inputFile().toString(),
                        "--output_dir", request.outputDirectory().toString(),
                        "--output_format", request.outputFormat().commandValue(),
                        "--disable_multiprocessing"),
                Map.of("PYTHONUTF8", "1"),
                request.outputDirectory(),
                DEFAULT_TIMEOUT);
    }
}
