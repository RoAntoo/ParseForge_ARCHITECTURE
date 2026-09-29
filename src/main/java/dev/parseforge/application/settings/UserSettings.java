package dev.parseforge.application.settings;

public record UserSettings(String markerExecutable, String outputDirectory) {
    public UserSettings {
        markerExecutable = markerExecutable == null ? "" : markerExecutable;
        outputDirectory = outputDirectory == null ? "" : outputDirectory;
    }

    public static UserSettings empty() {
        return new UserSettings("", "");
    }
}
