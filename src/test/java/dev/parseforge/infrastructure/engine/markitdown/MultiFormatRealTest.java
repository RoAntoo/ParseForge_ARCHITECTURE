package dev.parseforge.infrastructure.engine.markitdown;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.engine.EngineManifestRepository;
import dev.parseforge.infrastructure.process.LocalProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real private runtime, opt-in; no installation or network calls. */
@EnabledIfSystemProperty(named = "parseforge.multiformatTests", matches = "true")
class MultiFormatRealTest {
    @TempDir Path output;
    @Test void convertsOfflineFixturesAndPreservesPreviousOutputOnCorruption() throws Exception {
        Path root = Path.of("build/multiformat").toAbsolutePath();
        var runtime = new ManagedMarkItDownRuntime(root, new EngineManifestRepository("markitdown").manifest());
        var locator = mock(EngineRuntimeLocator.class);
        when(locator.descriptor(MarkItDownEngine.ID)).thenReturn(new EngineDescriptor(MarkItDownEngine.ID, "MarkItDown", "0.1.8"));
        when(locator.acquire(any())).thenAnswer(invocation -> {
            ConversionRequest request = invocation.getArgument(0);
            var lease = mock(EngineRuntimeLocator.EngineRuntime.class);
            when(lease.command()).thenReturn(runtime.conversion(request));
            return lease;
        });
        var engine = new MarkItDownEngine(new LocalProcessExecutor(), locator);
        for (String extension : List.of("pdf", "docx", "epub", "pptx", "xlsx", "xls", "html", "htm", "txt", "md", "csv", "json", "xml", "zip", "msg")) {
            Path fixture = root.resolve("fixtures/documento ñ." + extension);
            var request = new ConversionRequest(fixture, output.resolve(extension), MarkItDownEngine.ID, OutputFormat.MARKDOWN);
            var result = engine.convert(request, ignored -> {});
            assertEquals(ConversionStatus.COMPLETED, result.status(), extension + ": " + result.error());
            assertEquals(List.of(request.outputDirectory().resolve("documento ñ.md")), result.outputFiles());
            assertTrue(Files.readString(result.outputFiles().getFirst()).contains(extension.equals("msg") ? "Test Email Message" : "ParseForge"), extension);
        }
        for (String extension : List.of("pdf", "docx", "epub", "pptx", "xlsx", "xls", "msg", "zip")) {
            Path copied = Files.copy(root.resolve("fixtures/documento ñ." + extension), output.resolve("corrupt." + extension));
            var request = new ConversionRequest(copied, output.resolve("corrupt-" + extension), MarkItDownEngine.ID, OutputFormat.MARKDOWN);
            assertEquals(ConversionStatus.COMPLETED, engine.convert(request, ignored -> {}).status());
            Path markdown = ManagedMarkItDownRuntime.output(request);
            String previous = Files.readString(markdown);
            Files.writeString(copied, "invalid container");
            assertEquals(ConversionStatus.FAILED, engine.convert(request, ignored -> {}).status(), extension);
            assertEquals(previous, Files.readString(markdown), extension);
            try (var files = Files.list(request.outputDirectory())) {
                assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".parseforge-")));
            }
        }
    }
}
