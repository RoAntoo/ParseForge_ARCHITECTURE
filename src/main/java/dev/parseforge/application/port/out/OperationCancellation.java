package dev.parseforge.application.port.out;
import dev.parseforge.domain.exception.EngineInstallException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
/** Cooperative cancellation, including blocking I/O and process execution. */
public final class OperationCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CopyOnWriteArrayList<Runnable> callbacks = new CopyOnWriteArrayList<>();
    public boolean isCancelled() { return cancelled.get() || Thread.currentThread().isInterrupted(); }
    public void check() {
        if (isCancelled()) throw new EngineInstallException(EngineInstallException.Code.INSTALL_CANCELLED, "Operación cancelada.");
    }
    public AutoCloseable onCancel(Runnable callback) {
        callbacks.add(callback);
        if (cancelled.get()) callback.run();
        return () -> callbacks.remove(callback);
    }
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) callbacks.forEach(callback -> {
            try { callback.run(); } catch (RuntimeException ignored) { }
        });
    }
}

