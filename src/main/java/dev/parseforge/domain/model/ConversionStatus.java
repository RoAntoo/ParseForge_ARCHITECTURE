package dev.parseforge.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum ConversionStatus {
    PENDING,
    PREPARING,
    RUNNING,
    CANCELLING,
    CANCELLED,
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(ConversionStatus next) {
        return allowedTransitions().contains(next);
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == COMPLETED || this == FAILED;
    }

    private Set<ConversionStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(PREPARING, CANCELLED, FAILED);
            case PREPARING -> EnumSet.of(RUNNING, CANCELLING, FAILED);
            case RUNNING -> EnumSet.of(CANCELLING, COMPLETED, FAILED);
            case CANCELLING -> EnumSet.of(CANCELLED, COMPLETED, FAILED);
            case CANCELLED, COMPLETED, FAILED -> EnumSet.noneOf(ConversionStatus.class);
        };
    }
}
