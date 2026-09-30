package dev.parseforge.domain.model;
import java.nio.file.Path;
public record EngineInstallation(EngineId id, EngineVersion version, Path root, EngineState state) { }

