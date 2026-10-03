package dev.parseforge.application.usecase;

import dev.parseforge.application.port.out.DocumentPreflightService;
import dev.parseforge.domain.model.DocumentPreflightResult;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Obsolete inspections are interrupted; unresponsive work cannot hold up a new selection. */
public final class AnalyzeDocumentUseCase implements AutoCloseable {
    private final DocumentPreflightService service;
    private ExecutorService worker = newWorker();
    private Inspection running;
    private boolean closed;
    private static ExecutorService newWorker() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("pdf-preflight").factory());
    }
    public AnalyzeDocumentUseCase(DocumentPreflightService service) { this.service = service; }
    public synchronized CompletableFuture<DocumentPreflightResult> analyze(Path file) {
        if (closed) throw new RejectedExecutionException("Preflight is closed");
        cancel();
        var result = new CompletableFuture<DocumentPreflightResult>();
        var task = new Inspection(worker);
        running = task;
        task.future = worker.submit(() -> {
            try { result.complete(service.inspect(file)); }
            catch (Exception error) { result.completeExceptionally(error); }
            finally { task.finished.set(true); }
        });
        result.orTimeout(8, TimeUnit.SECONDS).whenComplete((ignored, error) -> {
            if (error instanceof TimeoutException || result.isCancelled()) cancel(task);
        });
        return result;
    }
    public synchronized void cancel() {
        if (running != null) cancel(running);
    }
    private synchronized void cancel(Inspection task) {
        task.future.cancel(true);
        // Future.cancel marks a Future done even when its callable ignores interruption.
        if (!task.finished.get() && worker == task.worker) {
            worker.shutdownNow();
            if (!closed) worker = newWorker();
        }
        if (running == task) running = null;
    }
    public synchronized void close() { closed = true; cancel(); worker.shutdownNow(); }
    private static final class Inspection {
        final ExecutorService worker;
        final AtomicBoolean finished = new AtomicBoolean();
        Future<?> future;
        Inspection(ExecutorService worker) { this.worker = worker; }
    }
}
