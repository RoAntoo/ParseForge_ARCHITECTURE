package dev.parseforge.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.parseforge.application.port.out.UserSettingsRepository;
import dev.parseforge.application.settings.UserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonUserSettingsRepository implements UserSettingsRepository {
    private static final Logger log = LoggerFactory.getLogger(JsonUserSettingsRepository.class);

    private final Path configFile;
    private final ObjectMapper mapper;

    public JsonUserSettingsRepository(Path configFile) {
        this.configFile = configFile;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public UserSettings load() {
        if (!Files.isRegularFile(configFile)) {
            return UserSettings.empty();
        }
        try {
            UserSettings settings = mapper.readValue(configFile.toFile(), UserSettings.class);
            return settings == null ? UserSettings.empty() : settings;
        } catch (IOException error) {
            log.warn("Could not load user settings from {}", configFile, error);
            return UserSettings.empty();
        }
    }

    @Override
    public void save(UserSettings settings) {
        Path temporary = configFile.resolveSibling(configFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(configFile.getParent());
            mapper.writeValue(temporary.toFile(), settings);
            try {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            log.warn("Could not save user settings to {}", configFile, error);
        }
    }
}
