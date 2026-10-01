package dev.parseforge.presentation.javafx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LauncherTest {
    @TempDir Path temp;
    @Test void createsAndKeepsConfiguredLogDirectory() throws Exception {
        Path configured = temp.resolve("logs");
        assertEquals(configured, Launcher.resolveLogDirectory(configured));
        assertTrue(Files.isDirectory(configured));
    }
    @Test void unusableConfiguredDirectoryFallsBackToWritableTemporaryDirectory() throws Exception {
        Path blocked = Files.writeString(temp.resolve("blocked"), "file, not directory");
        Path fallback = Launcher.resolveLogDirectory(blocked.resolve("logs"));
        try {
            assertNotEquals(blocked.resolve("logs"), fallback);
            assertTrue(Files.isDirectory(fallback));
            Files.writeString(fallback.resolve("startup-error.log"), "diagnostic");
            assertEquals("diagnostic", Files.readString(fallback.resolve("startup-error.log")));
            assertEquals("file, not directory", Files.readString(blocked));
        } finally {
            Files.deleteIfExists(fallback.resolve("startup-error.log"));
            Files.deleteIfExists(fallback);
        }
    }
}
