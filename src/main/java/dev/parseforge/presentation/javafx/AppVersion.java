package dev.parseforge.presentation.javafx;

import java.io.IOException;
import java.util.Properties;

/** UI version comes from the Maven project, including packaged builds. */
public final class AppVersion {
    private AppVersion() { }

    public static String current() {
        var properties = new Properties();
        try (var input = AppVersion.class.getResourceAsStream("/parseforge.properties")) {
            if (input == null) throw new IllegalStateException("Missing application version");
            properties.load(input);
            return properties.getProperty("version");
        } catch (IOException error) {
            throw new IllegalStateException("Could not read application version", error);
        }
    }
}
