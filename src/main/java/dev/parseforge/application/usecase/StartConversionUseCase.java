package dev.parseforge.application.usecase;

import dev.parseforge.application.port.out.ConversionEngine;
import dev.parseforge.application.port.out.ConversionEvent;
import dev.parseforge.application.port.out.ConversionEventListener;
import dev.parseforge.application.port.out.EngineRegistry;
import dev.parseforge.domain.model.ConversionJob;
import dev.parseforge.domain.model.ConversionRequest;
import dev.parseforge.domain.model.ConversionResult;
import dev.parseforge.domain.model.ConversionStatus;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

public final class StartConversionUseCase {
    private final EngineRegistry engineRegistry;
    private final Executor executor;
    private final AtomicReference<ActiveConversion> active = new AtomicReference<>();

    public StartConversionUseCase(EngineRegistry engineRegistry, Executor executor) {
        this.engineRegistry = Objects.requireNonNull(engineRegistry, "engineRegistry");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public CompletableFuture<ConversionResult> start(
            ConversionRequest request,
            ConversionEventListener listener
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(listener, "listener");

        ConversionEngine engine = engineRegistry.require(request.engineId());
        ConversionJob job = new ConversionJob(request);
        ActiveConversion conversion = new ActiveConversion(job, engine);
        if (!active.compareAndSet(null, conversion)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("There is already an active conversion"));
        }

        return CompletableFuture.supplyAsync(() -> run(conversion, request, listener), executor)
                .whenComplete((ignored, error) -> active.compareAndSet(conversion, null));
    }

    public boolean cancelActive() {
        ActiveConversion conversion = active.get();
        if (conversion == null) {
            return false;
        }
        synchronized (conversion.job()) {
            ConversionStatus status = conversion.job().status();
            if (status.isTerminal()) {
                return false;
            }
            if (status == ConversionStatus.PENDING) {
                conversion.job().transitionTo(ConversionStatus.CANCELLED);
            } else if (status == ConversionStatus.PREPARING || status == ConversionStatus.RUNNING) {
                conversion.job().transitionTo(ConversionStatus.CANCELLING);
            }
        }
        conversion.engine().cancel();
        return true;
    }

    public boolean hasActiveConversion() {
        return active.get() != null;
    }

    private ConversionResult run(
            ActiveConversion conversion,
            ConversionRequest request,
            ConversionEventListener listener
    ) {
        ConversionJob job = conversion.job();
        try {
            synchronized (job) {
                if (job.status() == ConversionStatus.CANCELLED) {
                    return new ConversionResult(ConversionStatus.CANCELLED, -1, null,
                            "Conversión cancelada", null);
                }
                job.transitionTo(ConversionStatus.PREPARING);
            }
            listener.onEvent(new ConversionEvent.PhaseChanged("Preparando motor..."));
            synchronized (job) {
                if (job.status() == ConversionStatus.CANCELLING) {
                    job.transitionTo(ConversionStatus.CANCELLED);
                    return new ConversionResult(ConversionStatus.CANCELLED, -1, null,
                            "Conversión cancelada", null);
                }
                job.transitionTo(ConversionStatus.RUNNING);
            }
            // Cancellation can arrive between RUNNING and the engine registering
            // its process. Re-deliver it when the engine becomes observable.
            ConversionResult result = conversion.engine().convert(request, event -> {
                listener.onEvent(event);
                if (job.status() == ConversionStatus.CANCELLING) conversion.engine().cancel();
            });
            finishJob(job, result);
            return result;
        } catch (RuntimeException error) {
            if (!job.status().isTerminal()) {
                if (job.status() == ConversionStatus.CANCELLING) {
                    job.transitionTo(ConversionStatus.CANCELLED);
                } else {
                    job.fail(error.getMessage());
                }
            }
            throw error;
        }
    }

    private void finishJob(ConversionJob job, ConversionResult result) {
        if (result.status() == ConversionStatus.COMPLETED) {
            job.complete(result.outputFiles());
        } else if (result.status() == ConversionStatus.CANCELLED) {
            if (job.status() != ConversionStatus.CANCELLING) {
                job.transitionTo(ConversionStatus.CANCELLING);
            }
            job.transitionTo(ConversionStatus.CANCELLED);
        } else {
            job.fail(result.error().orElse("La conversión falló"));
        }
    }

    private record ActiveConversion(ConversionJob job, ConversionEngine engine) { }
}
