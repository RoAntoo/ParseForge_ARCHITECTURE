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
                    terminateTree(process, true);
                }
                awaitReader(stdout);
                awaitReader(stderr);

                int exitCode = safeExitCode(process);
                boolean cancelled = execution.cancellationRequested.get() && exitCode != 0;
                return new ProcessResult(
                        exitCode,
                        cancelled,
                        !finished && !execution.cancellationRequested.get(),
                        Duration.between(startedAt, Instant.now()));
            }
        } finally {
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
            if (timeout == null) {
                process.waitFor();
                return true;
            }
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            execution.cancellationRequested.set(true);
            terminateTree(process, true);
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
            while ((line = reader.readLine()) != null) {
                listener.onLine(stream, line);
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
            Thread.startVirtualThread(() -> terminateTree(process, false));
        }
    }

    private void terminateTree(Process process, boolean forceImmediately) {
        List<ProcessHandle> descendants = process.descendants().toList();
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
    }
}
