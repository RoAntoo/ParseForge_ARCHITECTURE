package dev.parseforge.infrastructure.engine.marker;

import dev.parseforge.application.port.out.ProcessSpec;
import dev.parseforge.domain.model.ConversionRequest;
import dev.parseforge.domain.model.EngineId;
import dev.parseforge.domain.model.OutputFormat;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkerCommandBuilderTest {
    @Test void forwardsForceOcrForDevelopmentOverride() {
        var request = new ConversionRequest(Path.of("input.pdf"), Path.of("output"),
                new EngineId("marker"), OutputFormat.MARKDOWN, true);
        var spec = new MarkerCommandBuilder().build(Path.of("marker.exe"), request);
        assertEquals("--force_ocr", spec.arguments().getLast());
    }

    @Test
    void keepsUserPathsAsSeparateProcessArguments() {
        Path executable = Path.of("C:/Marker Runtime/marker_single.exe");
        ConversionRequest request = new ConversionRequest(
                Path.of("C:/My Documents/a document.pdf"),
                Path.of("C:/Output Folder"),
                new EngineId("marker"),
                OutputFormat.MARKDOWN);

        ProcessSpec spec = new MarkerCommandBuilder().build(executable, request);

        assertEquals(executable.toAbsolutePath().normalize(), spec.executable());
        assertEquals(request.inputFile().toString(), spec.arguments().get(0));
        assertEquals("--output_dir", spec.arguments().get(1));
        assertEquals(request.outputDirectory().toString(), spec.arguments().get(2));
        assertEquals("--output_format", spec.arguments().get(3));
        assertEquals("markdown", spec.arguments().get(4));
        assertEquals("--disable_multiprocessing", spec.arguments().get(5));
    }
}
