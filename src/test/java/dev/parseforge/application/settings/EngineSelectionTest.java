package dev.parseforge.application.settings;

import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.config.JsonUserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EngineSelectionTest {
    @TempDir Path temp;
    private final EngineId marker = new EngineId("marker"), md = new EngineId("markitdown");
    @Test void persistsMarkItDownAndRestoresOnlyReadyEngines() {
        var repository = new JsonUserSettingsRepository(temp.resolve("config.json"));
        repository.save(new UserSettings("", "out", "in", "out", "es", md.value(), 1));
        var states = new HashMap<>(Map.of(marker, EngineState.READY, md, EngineState.READY));
        assertEquals(md, EngineSelection.restore(repository.load().selectedEngine(), List.of(marker, md), states::get));
        states.put(md, EngineState.BROKEN);
        assertEquals(marker, EngineSelection.restore(md.value(), List.of(marker, md), states::get));
        states.put(marker, EngineState.NOT_INSTALLED);
        assertNull(EngineSelection.restore(md.value(), List.of(marker, md), states::get));
        states.put(md, EngineState.READY);
        assertEquals(md, EngineSelection.restore("removed-engine", List.of(marker, md), states::get));
    }
}
