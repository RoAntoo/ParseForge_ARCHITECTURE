package dev.parseforge.application.usecase;

import java.util.Objects;

public final class CancelConversionUseCase {
    private final StartConversionUseCase startConversion;

    public CancelConversionUseCase(StartConversionUseCase startConversion) {
        this.startConversion = Objects.requireNonNull(startConversion, "startConversion");
    }

    public boolean cancel() {
        return startConversion.cancelActive();
    }
}
