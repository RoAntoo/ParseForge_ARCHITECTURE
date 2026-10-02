package dev.parseforge.application.settings;

public record UserSettings(String markerExecutable, String outputDirectory, String lastInputDirectory,
                           String lastOutputDirectory, String language, String selectedEngine,
                           int welcomeDialogVersion) {
    public UserSettings(String markerExecutable, String outputDirectory, String lastInputDirectory,
                        String lastOutputDirectory, String language, String selectedEngine) {
        this(markerExecutable, outputDirectory, lastInputDirectory, lastOutputDirectory, language, selectedEngine, 0);
    }
    public UserSettings(String markerExecutable, String outputDirectory) {
        this(markerExecutable, outputDirectory, "", outputDirectory, "es", "marker");
    }
    public UserSettings {
        markerExecutable = markerExecutable == null ? "" : markerExecutable;
        outputDirectory = outputDirectory == null ? "" : outputDirectory;
        lastInputDirectory = lastInputDirectory == null ? "" : lastInputDirectory;
        lastOutputDirectory = lastOutputDirectory == null ? outputDirectory : lastOutputDirectory;
        language = language == null ? "es" : language;
        selectedEngine = selectedEngine == null ? "marker" : selectedEngine;
    }

    public static UserSettings empty() {
        return new UserSettings("", "");
    }
}
