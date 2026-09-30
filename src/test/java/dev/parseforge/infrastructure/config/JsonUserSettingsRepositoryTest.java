package dev.parseforge.infrastructure.config;

import dev.parseforge.application.settings.UserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonUserSettingsRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test void persistsInputAndOutputDirectoriesIndependentlyAndReadsLegacySettings() throws Exception {
        Path config = temporaryDirectory.resolve("config.json");
        var repository = new JsonUserSettingsRepository(config);
        var settings = new UserSettings("", "output", "input", "output", "es", "marker");
        repository.save(settings);
        assertEquals(settings, new JsonUserSettingsRepository(config).load());
        Files.writeString(config, "{\"markerExecutable\":\"old.exe\",\"outputDirectory\":\"legacy-output\"}");
        assertEquals("legacy-output", repository.load().lastOutputDirectory());
        assertEquals("", repository.load().lastInputDirectory());
        Files.writeString(config, "{invalid");
        assertEquals(UserSettings.empty(), repository.load());
    }

    @Test
    void returnsEmptySettingsWhenJsonRootIsNull() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.json");
        Files.writeString(configFile, "null");

        UserSettings settings = new JsonUserSettingsRepository(configFile).load();

        assertEquals(UserSettings.empty(), settings);
    }
}
