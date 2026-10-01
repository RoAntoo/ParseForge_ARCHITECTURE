package dev.parseforge.infrastructure.engine;

import dev.parseforge.domain.model.EngineId;
import java.nio.file.Path;
import java.util.Map;

/** Resolves the host Java environment, never a Codex package/cache path. */
public final class EnginePathResolver {
    private final Path dataRoot;
    private final Path configRoot;
    public EnginePathResolver() {
        this(System.getenv(), System.getProperty("user.home"), System.getProperty("parseforge.dataDir"));
    }
    public EnginePathResolver(Map<String, String> environment, String home, String override) {
        String local = environment.get("LOCALAPPDATA");
        dataRoot = (override != null && !override.isBlank() ? Path.of(override)
                : local != null && !local.isBlank() ? Path.of(local, "ParseForge")
                : Path.of(home, "AppData", "Local", "ParseForge")).toAbsolutePath().normalize();
        if (dataRoot.getParent() == null) throw new IllegalArgumentException("Invalid data root");
        String roaming = environment.get("APPDATA");
        configRoot = (override != null && !override.isBlank() ? dataRoot.resolve("config")
                : roaming != null && !roaming.isBlank() ? Path.of(roaming, "ParseForge")
                : Path.of(home, "AppData", "Roaming", "ParseForge")).toAbsolutePath().normalize();
    }
    public Path dataRoot() { return dataRoot; }
    public Path engine(EngineId id) { return dataRoot.resolve("engines").resolve(id.value()); }
    public Path configFile() { return configRoot.resolve("config.json"); }
    public Path legacyConfigFile() { return dataRoot.resolve("config/config.json"); }
    public Path logs() { return dataRoot.resolve("logs"); }
}
