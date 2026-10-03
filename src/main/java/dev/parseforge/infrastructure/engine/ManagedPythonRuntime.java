package dev.parseforge.infrastructure.engine;

import com.fasterxml.jackson.databind.JsonNode;
import dev.parseforge.application.port.out.ProcessSpec;
import dev.parseforge.domain.model.ConversionRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Private runtime commands stay entirely in infrastructure. */
public interface ManagedPythonRuntime {
    ProcessSpec python(List<String> args, Duration timeout) throws IOException;
    ProcessSpec conversion(ConversionRequest request) throws IOException;
    @FunctionalInterface interface Factory {
        ManagedPythonRuntime create(Path root, JsonNode manifest) throws IOException;
    }
}
