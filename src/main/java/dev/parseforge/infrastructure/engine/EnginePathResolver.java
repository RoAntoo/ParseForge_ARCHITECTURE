package dev.parseforge.infrastructure.engine;

import dev.parseforge.domain.model.EngineId;
import java.nio.file.Path;
import java.util.Map;

/** Resolves the host Java environment, never a Codex package/cache path. */
public final class EnginePathResolver {
    private final Path dataRoot;
    public EnginePathResolver() {
        this(System.getenv(), System.getProperty("user.home"), System.getProperty("parseforge.dataDir"));
    }
    public EnginePathResolver(Map<String, String> environment, String home, String override) {
        String local = environment.get("LOCALAPPDATA");
        dataRoot = (override != null && !override.isBlank() ? Path.of(override)
                : local != null && !local.isBlank() ? Path.of(local, "ParseForge")
                : Path.of(home, "AppData", "Local", "ParseForge")).toAbsolutePath().normalize();
        if (dataRoot.getParent() == null) throw new IllegalArgumentException("Invalid data root");
    }
    public Path dataRoot() { return dataRoot; }
    public Path engine(EngineId id) { return dataRoot.resolve("engines").resolve(id.value()); }
    public Path configFile() { return dataRoot.resolve("config/config.json"); }
}
