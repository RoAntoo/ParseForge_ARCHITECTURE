package dev.parseforge.domain.model;
public record EngineVersion(String value) {
    public EngineVersion { if (value == null || value.isBlank()) throw new IllegalArgumentException("Empty version"); }
}

