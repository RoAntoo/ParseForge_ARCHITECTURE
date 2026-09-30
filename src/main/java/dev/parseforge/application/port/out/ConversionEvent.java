package dev.parseforge.application.port.out;

import java.nio.file.Path;

public sealed interface ConversionEvent {
    record EngineStarted(String engineName) implements ConversionEvent { }
    record PhaseChanged(String phase) implements ConversionEvent { }
    record LogReceived(Stream stream, String message) implements ConversionEvent { }
    record OutputCreated(Path path) implements ConversionEvent { }
    record EngineStopped(int exitCode) implements ConversionEvent { }

    enum Stream {
        STDOUT,
        STDERR,
        SYSTEM
    }
}
