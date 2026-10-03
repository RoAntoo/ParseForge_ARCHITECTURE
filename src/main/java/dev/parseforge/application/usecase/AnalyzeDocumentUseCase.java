package dev.parseforge.application.usecase;

import dev.parseforge.application.port.out.DocumentPreflightService;
import dev.parseforge.domain.model.DocumentPreflightResult;
import java.nio.file.Path;
import java.util.concurrent.*;

/** One active inspection per window. Obsolete work is interrupted and never queued. */
public final class AnalyzeDocumentUseCase implements AutoCloseable {
    private final DocumentPreflightService service;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), Thread.ofPlatform().daemon().name("pdf-preflight").factory());
    private Future<?> running;
    public AnalyzeDocumentUseCase(DocumentPreflightService service) { this.service = service; }
    public synchronized CompletableFuture<DocumentPreflightResult> analyze(Path file) {
        cancel();
        var result = new CompletableFuture<DocumentPreflightResult>();
        running = worker.submit(() -> {
            try { result.complete(service.inspect(file)); }
            catch (Exception error) { result.completeExceptionally(error); }
        });
        Future<?> task = running;
        result.orTimeout(8, TimeUnit.SECONDS).whenComplete((ignored, error) -> {
            if (error != null) task.cancel(true);
        });
        return result;
    }
    public synchronized void cancel() {
        if (running != null) running.cancel(true);
        worker.getQueue().clear();
    }
    public synchronized void close() { cancel(); worker.shutdownNow(); }
}
