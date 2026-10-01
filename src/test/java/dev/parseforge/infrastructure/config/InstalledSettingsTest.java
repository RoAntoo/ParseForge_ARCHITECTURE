package dev.parseforge.infrastructure.config;

import dev.parseforge.application.settings.UserSettings;
import dev.parseforge.infrastructure.engine.EnginePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class InstalledSettingsTest {
    @TempDir Path temp;
    private EnginePathResolver paths() {
        return new EnginePathResolver(Map.of("LOCALAPPDATA", temp.resolve("local").toString(),
                "APPDATA", temp.resolve("roaming").toString()), temp.toString(), null);
    }
    private Path historical() { return temp.resolve(".parseforge/config.json"); }
    private UserSettings settings(String folder) {
        return new UserSettings("", folder, "input", folder, "es", "marker");
    }
    @Test void migratesHomeSettingsWhenPrimaryIsMissingOrEmpty() {
        var paths = paths(); var old = settings("historical");
        new JsonUserSettingsRepository(historical()).save(old);
        assertEquals(old, InstalledSettings.load(paths, historical()));
        assertEquals(old, new JsonUserSettingsRepository(paths.configFile()).load());
        new JsonUserSettingsRepository(paths.configFile()).save(UserSettings.empty());
        assertEquals(old, InstalledSettings.load(paths, historical()));
        assertEquals(old, new JsonUserSettingsRepository(paths.configFile()).load());
    }
    @Test void nonEmptyPrimaryAndLegacyTakePriorityOverHomeSettings() {
        var paths = paths(); var legacy = settings("legacy"); var primary = settings("primary");
        new JsonUserSettingsRepository(historical()).save(settings("historical"));
        new JsonUserSettingsRepository(paths.legacyConfigFile()).save(legacy);
        assertEquals(legacy, InstalledSettings.load(paths, historical()));
        new JsonUserSettingsRepository(paths.configFile()).save(primary);
        assertEquals(primary, InstalledSettings.load(paths, historical()));
        assertEquals(primary, new JsonUserSettingsRepository(paths.configFile()).load());
    }
    @Test void emptyLegacyAllowsFinalHomeMigration() {
        var paths = paths(); var old = settings("historical");
        new JsonUserSettingsRepository(paths.legacyConfigFile()).save(UserSettings.empty());
        new JsonUserSettingsRepository(historical()).save(old);
        assertEquals(old, InstalledSettings.load(paths, historical()));
    }
}
