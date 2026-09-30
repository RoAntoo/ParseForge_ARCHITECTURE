package dev.parseforge.domain.model;

import java.util.Objects;

public record EngineDescriptor(EngineId id, String displayName, String version, java.util.Set<EngineCapability> capabilities) {
    public EngineDescriptor(EngineId id, String displayName, String version) {
        this(id, displayName, version, java.util.Set.of());
    }
    public EngineDescriptor {
        Objects.requireNonNull(id, "id");
        displayName = requireText(displayName, "displayName");
        version = requireText(version, "version");
        capabilities = java.util.Set.copyOf(capabilities);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
