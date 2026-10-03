package dev.parseforge.infrastructure.engine.markitdown;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.engine.EngineManifestRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MarkItDownEngineTest {
    @TempDir Path temp;
    private ConversionRequest request;
    private MarkItDownEngine engine;
    private ProcessExecutor executor;
    private EngineRuntimeLocator.EngineRuntime lease;
    @BeforeEach void setup() throws Exception {
        Path root = Files.createDirectories(temp.resolve("privado ñ con espacios"));
        Path input = Files.writeString(temp.resolve("PDF digital 日本語 ñ.pdf"), "%PDF fixture");
        Path output = Files.createDirectories(temp.resolve("salida con espacios ñ"));
        request = new ConversionRequest(input, output, MarkItDownEngine.ID, OutputFormat.MARKDOWN, false);
        var spec = new ManagedMarkItDownRuntime(root, new EngineManifestRepository("markitdown").manifest()).conversion(request);
        executor = mock(ProcessExecutor.class);
        var locator = mock(EngineRuntimeLocator.class); lease = mock(EngineRuntimeLocator.EngineRuntime.class);
        when(locator.descriptor(MarkItDownEngine.ID)).thenReturn(new EngineDescriptor(MarkItDownEngine.ID, "MarkItDown", "0.1.8"));
        when(lease.command()).thenReturn(spec);
        when(locator.acquire(any())).thenAnswer(invocation -> {
            when(lease.command()).thenReturn(new ManagedMarkItDownRuntime(root,
                    new EngineManifestRepository("markitdown").manifest()).conversion(invocation.getArgument(0)));
            return lease;
        });
        engine = new MarkItDownEngine(executor, locator);
    }
    @Test void privateCommandPreservesUnicodeAndSpacesAndUsesExplicitOutputWithoutOcr() throws Exception {
        var spec = lease.command();
        assertEquals(List.of("-I", "-X", "utf8", "-u", "-B", "-c", ManagedMarkItDownRuntime.ENTRYPOINT,
                request.inputFile().toRealPath().toString(), "-o", ManagedMarkItDownRuntime.output(request).toString()), spec.arguments());
        assertFalse(spec.inheritEnvironment());
        assertFalse(spec.environment().containsKey("OPENAI_API_KEY"));
        assertFalse(spec.environment().containsKey("PYTHONPATH"));
        assertFalse(spec.environment().get("PATH").contains("marker"));
        assertTrue(spec.executable().toString().endsWith("runtime\\python\\python.exe"));
        assertThrows(IllegalArgumentException.class, () -> new ManagedMarkItDownRuntime(temp,
                new EngineManifestRepository("markitdown").manifest()).conversion(new ConversionRequest(request.inputFile(), request.outputDirectory(), request.engineId(), request.outputFormat(), true)));
    }
    @ParameterizedTest
    @CsvSource({"document.pdf,document.md", "document.PDF,document.md", "document.PdF,document.md",
            "document.txt,document.txt.md", "document,document.md", "a,a.md", "document.pdf.txt,document.pdf.txt.md"})
    void outputRemovesOnlyCaseInsensitivePdfSuffix(String filename, String expected) {
        var input = new ConversionRequest(temp.resolve(filename), request.outputDirectory(), MarkItDownEngine.ID, OutputFormat.MARKDOWN);
        assertEquals(request.outputDirectory().resolve(expected), ManagedMarkItDownRuntime.output(input));
    }
    @Test void successReportsOnlyExpectedMarkdownAndReleasesLease() throws Exception {
        Files.writeString(ManagedMarkItDownRuntime.output(request), "previous");
        Files.writeString(request.outputDirectory().resolve("unrelated.md"), "other document");
        when(executor.execute(any(), any())).thenAnswer(invocation -> {
            ProcessSpec command = invocation.getArgument(0);
            Files.writeString(Path.of(command.arguments().getLast()), "# fixture");
            return new ProcessResult(0, false, false, Duration.ZERO);
        });
        List<ConversionEvent> events = new ArrayList<>();
        var result = engine.convert(request, events::add);
        assertEquals(ConversionStatus.COMPLETED, result.status());
        assertEquals(List.of(ManagedMarkItDownRuntime.output(request)), result.outputFiles());
        assertTrue(events.stream().anyMatch(e -> e instanceof ConversionEvent.OutputCreated));
        assertEquals("# fixture", Files.readString(ManagedMarkItDownRuntime.output(request)));
        assertEquals("other document", Files.readString(request.outputDirectory().resolve("unrelated.md")));
        verify(lease).close();
    }
    @Test void nonzeroExitFailsEvenIfPreviousOutputExists() throws Exception {
        Files.writeString(ManagedMarkItDownRuntime.output(request), "previous");
        when(executor.execute(any(), any())).thenReturn(new ProcessResult(7, false, false, Duration.ZERO));
        var result = engine.convert(request, e -> {});
        assertEquals(ConversionStatus.FAILED, result.status()); assertTrue(result.outputFiles().isEmpty());
        assertTrue(result.error().orElseThrow().contains("7")); verify(lease).close();
    }
    @Test void zeroExitCannotReusePreviousOutput() throws Exception {
        Path output = ManagedMarkItDownRuntime.output(request);
        Files.writeString(output, "previous");
        when(executor.execute(any(), any())).thenReturn(new ProcessResult(0, false, false, Duration.ZERO));
        assertEquals(ConversionStatus.FAILED, engine.convert(request, e -> {}).status());
        assertEquals("previous", Files.readString(output));
    }
    @Test void failedProcessDoesNotOverwritePreviousOutputWithPartialMarkdown() throws Exception {
        Path output = ManagedMarkItDownRuntime.output(request);
        Files.writeString(output, "previous");
        when(executor.execute(any(), any())).thenAnswer(invocation -> {
            ProcessSpec command = invocation.getArgument(0);
            Files.writeString(Path.of(command.arguments().getLast()), "partial");
            return new ProcessResult(7, false, false, Duration.ZERO);
        });
        assertEquals(ConversionStatus.FAILED, engine.convert(request, e -> {}).status());
        assertEquals("previous", Files.readString(output));
        try (var entries = Files.list(request.outputDirectory())) {
            assertEquals(List.of(output), entries.toList());
        }
    }
    @Test void timeoutAndMissingOutputFail() {
        when(executor.execute(any(), any())).thenReturn(new ProcessResult(1, false, true, Duration.ZERO));
        assertEquals(ConversionStatus.FAILED, engine.convert(request, e -> {}).status());
        when(executor.execute(any(), any())).thenReturn(new ProcessResult(0, false, false, Duration.ZERO));
        assertEquals(ConversionStatus.FAILED, engine.convert(request, e -> {}).status());
    }
    @Test void cancellationBetweenStartEventAndProcessRegistrationReleasesLease() {
        var result = engine.convert(request, e -> { if (e instanceof ConversionEvent.EngineStarted) engine.cancel(); });
        assertEquals(ConversionStatus.CANCELLED, result.status());
        verify(executor, never()).execute(any(), any()); verify(lease).close();
    }
    @Test void cancelledProcessIsNotReportedAsSuccess() {
        when(executor.execute(any(), any())).thenReturn(new ProcessResult(-1, true, false, Duration.ZERO));
        assertEquals(ConversionStatus.CANCELLED, engine.convert(request, e -> {}).status()); verify(lease).close();
    }
}
