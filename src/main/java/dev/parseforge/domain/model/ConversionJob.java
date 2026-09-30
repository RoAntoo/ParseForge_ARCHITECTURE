package dev.parseforge.domain.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ConversionJob {
    private final UUID id;
    private final ConversionRequest request;
    private ConversionStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private List<Path> outputFiles = List.of();
    private String error;

    public ConversionJob(ConversionRequest request) {
        this.id = UUID.randomUUID();
        this.request = Objects.requireNonNull(request, "request");
        this.status = ConversionStatus.PENDING;
    }

    public synchronized void transitionTo(ConversionStatus next) {
        Objects.requireNonNull(next, "next");
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid conversion transition: " + status + " -> " + next);
        }
        status = next;
        if (next == ConversionStatus.RUNNING && startedAt == null) {
            startedAt = Instant.now();
        }
        if (next.isTerminal()) {
            finishedAt = Instant.now();
        }
    }

    public synchronized void complete(List<Path> outputs) {
        outputFiles = outputs == null ? List.of() : List.copyOf(outputs);
        transitionTo(ConversionStatus.COMPLETED);
    }

    public synchronized void fail(String message) {
        error = message;
        transitionTo(ConversionStatus.FAILED);
    }

    public UUID id() { return id; }
    public ConversionRequest request() { return request; }
    public synchronized ConversionStatus status() { return status; }
    public synchronized Instant startedAt() { return startedAt; }
    public synchronized Instant finishedAt() { return finishedAt; }
    public synchronized List<Path> outputFiles() { return outputFiles; }
    public synchronized String error() { return error; }
}
