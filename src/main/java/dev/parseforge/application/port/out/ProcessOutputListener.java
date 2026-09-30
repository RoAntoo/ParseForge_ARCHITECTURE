package dev.parseforge.application.port.out;

@FunctionalInterface
public interface ProcessOutputListener {
    void onLine(ProcessStream stream, String line);
}
