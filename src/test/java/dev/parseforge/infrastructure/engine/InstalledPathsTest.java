package dev.parseforge.infrastructure.engine;
import dev.parseforge.infrastructure.config.JsonUserSettingsRepository;
import dev.parseforge.application.settings.UserSettings;
import dev.parseforge.domain.model.EngineId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class InstalledPathsTest {
    @TempDir Path temp;
    @Test void separatesRoamingSettingsFromLocalEnginesAndLogs() {
        var paths = new EnginePathResolver(Map.of("LOCALAPPDATA",temp.resolve("local").toString(),
                "APPDATA",temp.resolve("roaming").toString()),temp.toString(),null);
        assertEquals(temp.resolve("local/ParseForge/engines/marker"),paths.engine(new EngineId("marker")));
        assertEquals(temp.resolve("roaming/ParseForge/config.json"),paths.configFile());
        assertEquals(temp.resolve("local/ParseForge/logs"),paths.logs());
        var settings = new UserSettings("","out","in","out","es","marker");
        new JsonUserSettingsRepository(paths.configFile()).save(settings);
        assertEquals(settings,new JsonUserSettingsRepository(paths.configFile()).load());
    }
    @Test void isolatedOverrideKeepsAllTestDataInsideRoot() {
        var paths = new EnginePathResolver(Map.of("APPDATA","ignored"),temp.toString(),temp.resolve("isolated").toString());
        assertEquals(temp.resolve("isolated/config/config.json"),paths.configFile());
        assertEquals(paths.configFile(),paths.legacyConfigFile());
    }
    @Test void migrationPreservesFoldersLanguageAndEngineWithoutOverwritingNewSettings() {
        var paths = new EnginePathResolver(Map.of("LOCALAPPDATA",temp.resolve("local").toString(),
                "APPDATA",temp.resolve("roaming").toString()),temp.toString(),null);
        var old = new UserSettings("","out","in","out","es","marker");
        new JsonUserSettingsRepository(paths.legacyConfigFile()).save(old);
        assertEquals(old,dev.parseforge.infrastructure.config.InstalledSettings.load(paths));
        assertEquals(old,new JsonUserSettingsRepository(paths.configFile()).load());
        new JsonUserSettingsRepository(paths.configFile()).save(UserSettings.empty());
        assertEquals(UserSettings.empty(),dev.parseforge.infrastructure.config.InstalledSettings.load(paths));
        assertTrue(java.nio.file.Files.exists(paths.legacyConfigFile()));
    }
}
