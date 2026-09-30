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

    @Test
    void returnsEmptySettingsWhenJsonRootIsNull() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.json");
        Files.writeString(configFile, "null");

        UserSettings settings = new JsonUserSettingsRepository(configFile).load();

        assertEquals(UserSettings.empty(), settings);
    }
}
