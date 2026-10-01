package dev.parseforge.infrastructure.config;
import dev.parseforge.application.settings.UserSettings;
import dev.parseforge.infrastructure.engine.EnginePathResolver;
import java.nio.file.Files;

public final class InstalledSettings {
    private InstalledSettings() { }
    public static UserSettings load(EnginePathResolver paths) {
        var target = new JsonUserSettingsRepository(paths.configFile());
        if (!Files.exists(paths.configFile()) && Files.isRegularFile(paths.legacyConfigFile())) {
            var settings = new JsonUserSettingsRepository(paths.legacyConfigFile()).load();
            target.save(settings); return settings;
        }
        return target.load();
    }
}
