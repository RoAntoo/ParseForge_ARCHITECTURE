package dev.parseforge.domain.model;

/** File integrity is always checked; runtime probes are optional. */
public record EngineInstallOptions(boolean runHealthCheck) {
    public static final EngineInstallOptions DEFAULT = new EngineInstallOptions(false);
    public static final EngineInstallOptions WITH_HEALTH_CHECK = new EngineInstallOptions(true);
}
