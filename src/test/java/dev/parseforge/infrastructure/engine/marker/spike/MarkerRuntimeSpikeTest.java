package dev.parseforge.infrastructure.engine.marker.spike;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MarkerRuntimeSpikeTest {
    @TempDir Path root;

    @Test void removesPreviousMarkdownBeforeReusingTheModeOutput() throws Exception {
        Path stale = root.resolve("temp/output-digital/previous/stale.md");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "stale success");
        Path otherMode = root.resolve("temp/output-ocr/previous.md");
        Files.createDirectories(otherMode.getParent());
        Files.writeString(otherMode, "keep");
        Path output = MarkerRuntimeSpike.prepareOutputDirectory(root, "digital");
        assertEquals(root.toRealPath().resolve("temp/output-digital"), output);
        try (var entries = Files.list(output)) { assertEquals(0, entries.count()); }
        assertEquals("keep", Files.readString(otherMode));
    }

    @Test void createsOcrOutputAndRejectsUnsupportedModes() throws Exception {
        assertTrue(Files.isDirectory(MarkerRuntimeSpike.prepareOutputDirectory(root, "ocr")));
        assertThrows(IllegalArgumentException.class, () -> MarkerRuntimeSpike.prepareOutputDirectory(root, "../runtime"));
    }
}
