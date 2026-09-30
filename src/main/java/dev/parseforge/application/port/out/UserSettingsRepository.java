package dev.parseforge.application.port.out;

import dev.parseforge.application.settings.UserSettings;

public interface UserSettingsRepository {
    UserSettings load();

    void save(UserSettings settings);
}
