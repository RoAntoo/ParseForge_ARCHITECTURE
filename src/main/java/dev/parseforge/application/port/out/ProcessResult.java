package dev.parseforge.application.port.out;

import java.time.Duration;

public record ProcessResult(int exitCode, boolean cancelled, boolean timedOut, Duration duration) {
}
