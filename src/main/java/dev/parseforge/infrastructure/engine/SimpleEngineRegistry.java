package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.ConversionEngine;
import dev.parseforge.application.port.out.EngineRegistry;
import dev.parseforge.domain.model.EngineId;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SimpleEngineRegistry implements EngineRegistry {
    private final Map<EngineId, ConversionEngine> engines;

    public SimpleEngineRegistry(Collection<ConversionEngine> engines) {
        this.engines = engines.stream().collect(Collectors.toUnmodifiableMap(
                engine -> engine.descriptor().id(), Function.identity()));
    }

    @Override
    public ConversionEngine require(EngineId id) {
        ConversionEngine engine = engines.get(id);
        if (engine == null) {
            throw new IllegalArgumentException("Unknown conversion engine: " + id);
        }
        return engine;
    }
}
