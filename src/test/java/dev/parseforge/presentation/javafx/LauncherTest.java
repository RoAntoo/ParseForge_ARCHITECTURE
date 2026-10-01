package dev.parseforge.presentation.javafx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class LauncherTest {
    @TempDir Path temp;
    @Test void createsAndKeepsConfiguredLogDirectory() throws Exception {
        Path configured = temp.resolve("logs");
        assertEquals(configured, Launcher.resolveLogDirectory(configured));
        assertTrue(Files.isDirectory(configured));
        try (var files = Files.list(configured)) { assertEquals(0, files.count()); }
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void existingDirectoryWithoutFileCreationPermissionUsesTemporaryFallback() throws Exception {
        Path configured = Files.createDirectory(temp.resolve("unwritable"));
        var view = Files.getFileAttributeView(configured, AclFileAttributeView.class);
        var original = view.getAcl();
        var denied = new ArrayList<>(original);
        denied.add(0, AclEntry.newBuilder().setType(AclEntryType.DENY).setPrincipal(view.getOwner())
                .setPermissions(AclEntryPermission.WRITE_DATA).build());
        Path fallback = null;
        try {
            view.setAcl(denied);
            assertEquals(configured, Files.createDirectories(configured));
            assertThrows(java.io.IOException.class, () -> Files.createTempFile(configured, "denied-", ".tmp"));
            fallback = Launcher.resolveLogDirectory(configured);
            assertNotEquals(configured, fallback);
            Files.writeString(fallback.resolve("startup-error.log"), "diagnostic");
        } finally {
            view.setAcl(original);
            if (fallback != null) {
                Files.deleteIfExists(fallback.resolve("startup-error.log"));
                Files.deleteIfExists(fallback);
            }
        }
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
