package dev.parseforge.domain.model;

public enum OutputFormat {
    MARKDOWN("markdown");

    private final String commandValue;

    OutputFormat(String commandValue) {
        this.commandValue = commandValue;
    }

    public String commandValue() {
        return commandValue;
    }
}
