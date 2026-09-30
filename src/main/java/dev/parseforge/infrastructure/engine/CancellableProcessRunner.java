package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Closes the cancellation-before-process-registration race. */
public final class CancellableProcessRunner {
    private CancellableProcessRunner() { }
    public static ProcessResult execute(ProcessExecutor executor, ProcessSpec spec,
            ProcessOutputListener listener, OperationCancellation cancellation) {
        cancellation.check();
        AtomicBoolean done = new AtomicBoolean();
        Thread watcher = Thread.startVirtualThread(() -> {
            while (!done.get()) {
                if (cancellation.isCancelled()) executor.cancel();
                try { Thread.sleep(25); } catch (InterruptedException stopped) { return; }
            }
        });
        try {
            cancellation.check();
            var result = executor.execute(spec, listener);
            cancellation.check();
            return result;
        } finally { done.set(true); watcher.interrupt(); }
    }
}
