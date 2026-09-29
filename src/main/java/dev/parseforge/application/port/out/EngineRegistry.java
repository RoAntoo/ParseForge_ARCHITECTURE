package dev.parseforge.application.port.out;

import dev.parseforge.domain.model.EngineId;

public interface EngineRegistry {
    ConversionEngine require(EngineId id);
}
