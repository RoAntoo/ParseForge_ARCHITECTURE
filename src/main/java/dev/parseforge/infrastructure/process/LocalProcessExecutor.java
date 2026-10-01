package dev.parseforge.infrastructure.process;

import dev.parseforge.application.port.out.ProcessExecutor;
import dev.parseforge.application.port.out.ProcessOutputListener;
import dev.parseforge.application.port.out.ProcessResult;
import dev.parseforge.application.port.out.ProcessSpec;
import dev.parseforge.application.port.out.ProcessStream;
import dev.parseforge.domain.exception.ConversionException;
import dev.parseforge.domain.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalProcessExecutor implements ProcessExecutor {
    private static final Logger log = LoggerFactory.getLogger(LocalProcessExecutor.class);
    private static final Duration GRACEFUL_SHUTDOWN = Duration.ofSeconds(2);

    private final AtomicReference<Execution> activeExecution = new AtomicReference<>();

    @Override
    public ProcessResult execute(ProcessSpec spec, ProcessOutputListener listener) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(listener, "listener");
        Execution execution = new Execution();
        if (!activeExecution.compareAndSet(null, execution)) {
            throw new IllegalStateException("The process executor is already busy");
        }
        Instant startedAt = Instant.now();
        try {
            if (!Files.isRegularFile(spec.executable())) {
                throw new ConversionException(ErrorCode.ENGINE_NOT_INSTALLED,
                        "No se encontró el ejecutable del motor: " + spec.executable());
            }

            List<String> command = new ArrayList<>(spec.arguments().size() + 1);
            command.add(spec.executable().toString());
            command.addAll(spec.arguments());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(false);
            if (spec.workingDirectory() != null) {
                builder.directory(spec.workingDirectory().toFile());
            }
            if (!spec.inheritEnvironment()) {
                builder.environment().clear();
            }
            builder.environment().putAll(spec.environment());

            if (execution.cancellationRequested.get()) {
                return cancelledBeforeStart(startedAt);
            }

            Process process;
            try {
                process = builder.start();
            } catch (IOException error) {
                throw new ConversionException(ErrorCode.PROCESS_START_FAILED,
                        "No fue posible iniciar el motor.", error);
            }
            execution.process.set(process);
            log.info("Engine process started; pid={}, executable={}", process.pid(), spec.executable().getFileName());
            if (execution.cancellationRequested.get()) {
                terminateAsync(execution, process);
            }

            try (var readers = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> stdout = readers.submit(() -> readLines(
                        process.getInputStream(), ProcessStream.STDOUT, listener, execution));
                Future<?> stderr = readers.submit(() -> readLines(
                        process.getErrorStream(), ProcessStream.STDERR, listener, execution));

                boolean finished = waitFor(process, spec.timeout(), execution);
                if (!finished) {
                    terminateTree(execution, process, true);
                }
                // Persistent engine services can outlive their parent and hold its pipes open.
                terminateTrackedChildren(execution);
                awaitReader(stdout);
                awaitReader(stderr);

                int exitCode = safeExitCode(process);
                boolean cancelled = execution.cancellationRequested.get() && exitCode != 0;
                log.info("Engine process ended; pid={}, exit={}, cancelled={}, elapsedMs={}",
                        process.pid(), exitCode, cancelled, Duration.between(startedAt, Instant.now()).toMillis());
                return new ProcessResult(
                        exitCode,
                        cancelled,
                        !finished && !execution.cancellationRequested.get(),
                        Duration.between(startedAt, Instant.now()));
            }
        } finally {
            Process remaining = execution.process.get();
            if (remaining != null && remaining.isAlive()) {
                terminateTree(execution, remaining, true);
            }
            terminateTrackedChildren(execution);
            activeExecution.compareAndSet(execution, null);
        }
    }

    @Override
    public void cancel() {
        Execution execution = activeExecution.get();
        if (execution == null) {
            return;
        }
        execution.cancellationRequested.set(true);
        Process process = execution.process.get();
        if (process != null) {
            terminateAsync(execution, process);
        }
    }

    private boolean waitFor(Process process, Duration timeout, Execution execution) {
        try {
            long deadline = timeout == null ? Long.MAX_VALUE : System.nanoTime() + timeout.toNanos();
            while (true) {
                process.descendants().forEach(handle -> execution.children.putIfAbsent(handle.pid(), handle));
                if (process.waitFor(50, TimeUnit.MILLISECONDS)) {
                    return true;
                }
                if (System.nanoTime() >= deadline) {
                    return false;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            execution.cancellationRequested.set(true);
            terminateTree(execution, process, true);
            return false;
        }
    }

    private void readLines(
            InputStream input,
            ProcessStream stream,
            ProcessOutputListener listener,
            Execution execution
    ) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            boolean listenerFailed = false;
            while ((line = reader.readLine()) != null) {
                if (listenerFailed) {
                    continue;
                }
                try {
                    listener.onLine(stream, line);
                } catch (RuntimeException error) {
                    listenerFailed = true;
                    log.warn("Process output listener failed for {}; further lines will be discarded",
                            stream, error);
                }
            }
        } catch (IOException error) {
            if (!execution.cancellationRequested.get()) {
                log.warn("Could not read process {}", stream, error);
            }
        }
    }

    private ProcessResult cancelledBeforeStart(Instant startedAt) {
        return new ProcessResult(-1, true, false, Duration.between(startedAt, Instant.now()));
    }

    private void terminateAsync(Execution execution, Process process) {
        if (execution.terminationStarted.compareAndSet(false, true)) {
            Thread.startVirtualThread(() -> terminateTree(execution, process, false));
        }
    }

    private void terminateTree(Execution execution, Process process, boolean forceImmediately) {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(handle -> execution.children.putIfAbsent(handle.pid(), handle));
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();

        if (!forceImmediately) {
            try {
                process.waitFor(GRACEFUL_SHUTDOWN.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        terminateTrackedChildren(execution);
    }

    private void terminateTrackedChildren(Execution execution) {
        execution.children.values().stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        long deadline = System.nanoTime() + GRACEFUL_SHUTDOWN.toNanos();
        while (execution.children.values().stream().anyMatch(ProcessHandle::isAlive)
                && System.nanoTime() < deadline) {
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
    }

    private void awaitReader(Future<?> reader) {
        try {
            reader.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException error) {
            log.warn("Process output reader failed", error.getCause());
        }
    }

    private int safeExitCode(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException stillRunning) {
            return -1;
        }
    }

    private static final class Execution {
        private final AtomicReference<Process> process = new AtomicReference<>();
        private final AtomicBoolean cancellationRequested = new AtomicBoolean();
        private final AtomicBoolean terminationStarted = new AtomicBoolean();
        private final ConcurrentHashMap<Long, ProcessHandle> children = new ConcurrentHashMap<>();
    }
}
