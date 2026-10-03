package dev.parseforge.application.settings;

import dev.parseforge.domain.model.*;
import java.util.*;
import java.util.function.Function;

public final class EngineSelection {
    private EngineSelection() { }
    public static EngineId restore(String preferred, List<EngineId> engines, Function<EngineId, EngineState> states) {
        return engines.stream().filter(id -> id.value().equals(preferred) && states.apply(id) == EngineState.READY)
                .findFirst().orElseGet(() -> engines.stream().filter(id -> states.apply(id) == EngineState.READY).findFirst().orElse(null));
    }
}
