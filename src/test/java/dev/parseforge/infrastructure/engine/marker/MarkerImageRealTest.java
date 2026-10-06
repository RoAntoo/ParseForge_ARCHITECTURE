package dev.parseforge.infrastructure.engine.marker;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.engine.EngineManifestRepository;
import dev.parseforge.infrastructure.process.LocalProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "parseforge.markerImageTests", matches = "true")
class MarkerImageRealTest {
    @Test void recognizesImageTextWithInstalledPrivateMarker() throws Exception {
        Path root = Path.of(System.getenv("LOCALAPPDATA"), "ParseForge/engines/marker");
        var runtime = new ManagedMarkerRuntime(root, new EngineManifestRepository().manifest());
        var locator = mock(EngineRuntimeLocator.class);
        when(locator.descriptor(MarkerEngine.ID)).thenReturn(new EngineDescriptor(MarkerEngine.ID, "Marker", "2.0.0"));
        when(locator.acquire(any())).thenAnswer(invocation -> {
            var lease = mock(EngineRuntimeLocator.EngineRuntime.class);
            when(lease.command()).thenReturn(runtime.conversion(invocation.getArgument(0)));
            return lease;
        });
        Path evidence = Files.createDirectories(Path.of("build/multiformat/marker-image"));
        var request = new ConversionRequest(Path.of("build/multiformat/fixtures/documento ñ.png"), evidence, MarkerEngine.ID, OutputFormat.MARKDOWN);
        try (var writer = Files.newBufferedWriter(evidence.resolve("conversion.log"))) {
            var result = new MarkerEngine(new LocalProcessExecutor(), locator).convert(request, event -> {
                if (event instanceof ConversionEvent.LogReceived log) {
                    try { writer.write(log.message()); writer.newLine(); writer.flush(); }
                    catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                }
            });
            assertEquals(ConversionStatus.COMPLETED, result.status(), result.error().toString());
            String content = Files.readString(result.outputFiles().getFirst());
            assertTrue(content.contains("ParseForge"), content);
            assertTrue(content.contains("OCR"), content);
        }
    }
}
