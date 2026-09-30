package dev.parseforge.domain.model;
public enum EngineState {
    NOT_INSTALLED, DOWNLOADING, INSTALLING, VERIFYING, READY, BROKEN, REMOVING,
    /** Legacy development integration. */ AVAILABLE, NOT_CONFIGURED, CORRUPTED, BUSY
}
