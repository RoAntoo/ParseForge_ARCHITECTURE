package dev.parseforge.application.port.out;
import java.util.Optional;
public interface EngineProgressParser {
    Optional<Double> parse(String line);
}

