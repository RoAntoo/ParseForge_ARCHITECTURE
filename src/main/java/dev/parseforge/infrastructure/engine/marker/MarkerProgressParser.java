package dev.parseforge.infrastructure.engine.marker;
import dev.parseforge.application.port.out.EngineProgressParser;
import java.util.Optional;
/** Marker emits per-stage progress, which does not quantify whole-document work. */
public final class MarkerProgressParser implements EngineProgressParser {
    @Override public Optional<Double> parse(String line) { return Optional.empty(); }
}

