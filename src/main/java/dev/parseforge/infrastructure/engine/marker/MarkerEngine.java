package dev.parseforge.infrastructure.engine.marker;

import dev.parseforge.application.port.out.ConversionEngine;
import dev.parseforge.application.port.out.ConversionEvent;
import dev.parseforge.application.port.out.ConversionEventListener;
import dev.parseforge.application.port.out.ProcessExecutor;
import dev.parseforge.application.port.out.ProcessResult;
import dev.parseforge.application.port.out.ProcessSpec;
import dev.parseforge.application.port.out.ProcessStream;
import dev.parseforge.application.port.out.EngineRuntimeLocator;
import dev.parseforge.application.port.out.OperationCancellation;
import dev.parseforge.infrastructure.engine.CancellableProcessRunner;
import dev.parseforge.domain.exception.EngineInstallException;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import dev.parseforge.domain.exception.ConversionException;
import dev.parseforge.domain.exception.ErrorCode;
import dev.parseforge.domain.model.ConversionRequest;
import dev.parseforge.domain.model.ConversionResult;
import dev.parseforge.domain.model.ConversionStatus;
import dev.parseforge.domain.model.EngineDescriptor;
import dev.parseforge.domain.model.EngineId;
import dev.parseforge.domain.model.EngineState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class MarkerEngine implements ConversionEngine {
    public static final EngineId ID = new EngineId("marker");

    private final Supplier<Path> executableSupplier;
    private final ProcessExecutor processExecutor;
    private final MarkerCommandBuilder commandBuilder;
    private final EngineRuntimeLocator locator;
    private final AtomicReference<OperationCancellation> managedConversion = new AtomicReference<>();

    public MarkerEngine(ProcessExecutor executor, EngineRuntimeLocator locator) {
        this.processExecutor = Objects.requireNonNull(executor);
        this.locator = Objects.requireNonNull(locator);
        this.executableSupplier = null;
        this.commandBuilder = null;
    }

    public MarkerEngine(
            Supplier<Path> executableSupplier,
            ProcessExecutor processExecutor,
            MarkerCommandBuilder commandBuilder
    ) {
        this.executableSupplier = Objects.requireNonNull(executableSupplier, "executableSupplier");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
        this.commandBuilder = Objects.requireNonNull(commandBuilder, "commandBuilder");
        this.locator = null;
    }

    @Override
    public EngineDescriptor descriptor() {
        return locator == null ? new EngineDescriptor(ID, "Marker", "external") : locator.descriptor(ID);
    }

    @Override
    public EngineState state() {
        if (locator != null) return locator.state(ID);
        Path executable = executableSupplier.get();
        return executable != null && Files.isRegularFile(executable)
                ? EngineState.AVAILABLE
                : EngineState.NOT_CONFIGURED;
    }

    @Override
    public ConversionResult convert(ConversionRequest request, ConversionEventListener listener) {
        if (locator != null) {
            var cancellation = new OperationCancellation();
            if (!managedConversion.compareAndSet(null, cancellation)) throw new IllegalStateException("Marker está ocupado.");
            Instant started = Instant.now();
            try (var runtime = locator.acquire(request)) {
                cancellation.check();
                return convert(request, listener, runtime.command(), cancellation);
            } catch (EngineInstallException error) {
                if (error.code() != EngineInstallException.Code.INSTALL_CANCELLED) throw error;
                return new ConversionResult(ConversionStatus.CANCELLED, -1, List.of(), "Conversión cancelada", Duration.between(started, Instant.now()));
            } finally { managedConversion.compareAndSet(cancellation, null); }
        }
        Path executable = executableSupplier.get();
        if (executable == null || !Files.isRegularFile(executable)) {
            throw new ConversionException(ErrorCode.ENGINE_NOT_INSTALLED,
                    "Seleccioná un ejecutable válido de Marker.");
        }
        return convert(request, listener, commandBuilder.build(executable, request), null);
    }

    private ConversionResult convert(ConversionRequest request, ConversionEventListener listener, ProcessSpec spec, OperationCancellation cancellation) {
        Instant outputScanStart = Instant.now();
        listener.onEvent(new ConversionEvent.EngineStarted(descriptor().displayName()));
        listener.onEvent(new ConversionEvent.PhaseChanged("Procesando documento con Marker..."));

        dev.parseforge.application.port.out.ProcessOutputListener output = (stream, line) -> listener.onEvent(
                new ConversionEvent.LogReceived(toConversionStream(stream), line));
        ProcessResult processResult = cancellation == null ? processExecutor.execute(spec, output)
                : CancellableProcessRunner.execute(processExecutor, spec, output, cancellation);
        listener.onEvent(new ConversionEvent.EngineStopped(processResult.exitCode()));

        if (processResult.cancelled()) {
            return new ConversionResult(ConversionStatus.CANCELLED, processResult.exitCode(),
                    List.of(), "Conversión cancelada", processResult.duration());
        }
        if (processResult.timedOut()) {
            return new ConversionResult(ConversionStatus.FAILED, processResult.exitCode(),
                    List.of(), "Marker excedió el tiempo máximo de ejecución", processResult.duration());
        }
        if (processResult.exitCode() != 0) {
            return new ConversionResult(ConversionStatus.FAILED, processResult.exitCode(),
                    List.of(), "Marker terminó con código " + processResult.exitCode(), processResult.duration());
        }

        List<Path> outputs = findCreatedMarkdown(request.outputDirectory(), outputScanStart);
        outputs.forEach(path -> listener.onEvent(new ConversionEvent.OutputCreated(path)));
        return new ConversionResult(ConversionStatus.COMPLETED, processResult.exitCode(),
                outputs, null, processResult.duration());
    }

    @Override
    public void cancel() {
        var cancellation = managedConversion.get();
        if (cancellation != null) cancellation.cancel();
        processExecutor.cancel();
    }

    private ConversionEvent.Stream toConversionStream(ProcessStream stream) {
        return switch (stream) {
            case STDOUT -> ConversionEvent.Stream.STDOUT;
            case STDERR -> ConversionEvent.Stream.STDERR;
        };
    }

    private List<Path> findCreatedMarkdown(Path directory, Instant startedAt) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .filter(path -> modifiedAfter(path, startedAt))
                    .toList();
        } catch (IOException error) {
            return List.of();
        }
    }

    private boolean modifiedAfter(Path path, Instant startedAt) {
        try {
            return !Files.getLastModifiedTime(path).toInstant().isBefore(startedAt);
        } catch (IOException error) {
            return false;
        }
    }
}
