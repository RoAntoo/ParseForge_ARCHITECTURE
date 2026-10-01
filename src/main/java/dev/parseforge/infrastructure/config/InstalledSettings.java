package dev.parseforge.infrastructure.config;
import dev.parseforge.application.settings.UserSettings;
import dev.parseforge.infrastructure.engine.EnginePathResolver;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InstalledSettings {
    private InstalledSettings() { }
    public static UserSettings load(EnginePathResolver paths) {
        return load(paths, Path.of(System.getProperty("user.home"), ".parseforge", "config.json"));
    }
    static UserSettings load(EnginePathResolver paths, Path homeConfigFile) {
        var target = new JsonUserSettingsRepository(paths.configFile());
        var settings = target.load();
        if (!Files.exists(paths.configFile()) && Files.isRegularFile(paths.legacyConfigFile())) {
            settings = new JsonUserSettingsRepository(paths.legacyConfigFile()).load();
            target.save(settings);
        }
        if (settings.equals(UserSettings.empty()) && Files.isRegularFile(homeConfigFile)) {
            var historical = new JsonUserSettingsRepository(homeConfigFile).load();
            if (!historical.equals(UserSettings.empty())) {
                target.save(historical); settings = historical;
            }
        }
        return settings;
    }
}
