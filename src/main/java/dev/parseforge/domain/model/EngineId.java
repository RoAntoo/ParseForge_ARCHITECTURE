package dev.parseforge.domain.model;

import java.util.Locale;
import java.util.Objects;

public record EngineId(String value) {
    public EngineId {
        Objects.requireNonNull(value, "value");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z][a-z0-9-]{1,63}")) {
            throw new IllegalArgumentException("Invalid engine id: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
