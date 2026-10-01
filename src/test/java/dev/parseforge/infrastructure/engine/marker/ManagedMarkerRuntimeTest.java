package dev.parseforge.infrastructure.engine.marker;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.parseforge.domain.model.ConversionRequest;
import dev.parseforge.domain.model.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManagedMarkerRuntimeTest {
    @TempDir Path root;

    @Test void digitalAndOcrConversionsDisablePdftextProcessPool() throws Exception {
        var manifest = new ObjectMapper().readTree("""
                {"python":{"executable":"runtime/python/python.exe"},
                 "llamaCpp":{"executable":"runtime/llamacpp/llama-server.exe"}}
                """);
        var runtime = new ManagedMarkerRuntime(root, manifest);
        Path pdf = Files.createFile(root.resolve("document with spaces.pdf"));
        Path output = Files.createDirectory(root.resolve("output with spaces"));

        for (boolean ocr : List.of(false, true)) {
            var request = new ConversionRequest(pdf, output, MarkerEngine.ID, OutputFormat.MARKDOWN, ocr);
            var spec = runtime.conversion(request);

            assertEquals(List.of("-I", "-X", "utf8", "-u", "-B", "-c",
                    ManagedMarkerRuntime.ENTRYPOINT, pdf.toRealPath().toString(),
                    "--output_dir", output.toAbsolutePath().toString(),
                    "--output_format", "markdown", "--disable_multiprocessing"),
                    spec.arguments().subList(0, 13));
            assertEquals(ocr ? 14 : 13, spec.arguments().size());
            assertEquals(ocr, spec.arguments().contains("--force_ocr"));
            assertFalse(spec.inheritEnvironment());
        }
    }
}
