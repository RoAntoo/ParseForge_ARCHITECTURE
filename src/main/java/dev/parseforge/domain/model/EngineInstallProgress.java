package dev.parseforge.domain.model;
public record EngineInstallProgress(Phase phase, String message, long completedBytes, long totalBytes) {
    public enum Phase { PREPARING, DOWNLOADING, VERIFYING, EXTRACTING, INSTALLING_PACKAGES,
        PREPARING_MODELS, HEALTH_CHECKING, COMPLETED, FAILED, CANCELLED, REMOVING }
    public double fraction() { return totalBytes > 0 ? Math.min(1, (double) completedBytes / totalBytes) : -1; }
    public static EngineInstallProgress phase(Phase phase, String message) {
        return new EngineInstallProgress(phase, message, 0, -1);
    }
}

