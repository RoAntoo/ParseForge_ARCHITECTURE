package dev.parseforge.application.port.out;

@FunctionalInterface
public interface ConversionEventListener {
    void onEvent(ConversionEvent event);
}
