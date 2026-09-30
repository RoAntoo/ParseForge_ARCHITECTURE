package dev.parseforge.infrastructure.engine.marker;

import dev.parseforge.application.port.out.ConversionEngine;
import dev.parseforge.application.port.out.ConversionEvent;
import dev.parseforge.application.port.out.ConversionEventListener;
import dev.parseforge.application.port.out.ProcessExecutor;
import dev.parseforge.application.port.out.ProcessResult;
import dev.parseforge.application.port.out.ProcessSpec;
import dev.parseforge.application.port.out.ProcessStream;
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

    public MarkerEngine(
            Supplier<Path> executableSupplier,
            ProcessExecutor processExecutor,
            MarkerCommandBuilder commandBuilder
    ) {
        this.executableSupplier = Objects.requireNonNull(executableSupplier, "executableSupplier");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
        this.commandBuilder = Objects.requireNonNull(commandBuilder, "commandBuilder");
    }

    @Override
    public EngineDescriptor descriptor() {
        return new EngineDescriptor(ID, "Marker", "external");
    }

    @Override
    public EngineState state() {
        Path executable = executableSupplier.get();
        return executable != null && Files.isRegularFile(executable)
                ? EngineState.AVAILABLE
                : EngineState.NOT_CONFIGURED;
    }

    @Override
    public ConversionResult convert(ConversionRequest request, ConversionEventListener listener) {
        Path executable = executableSupplier.get();
        if (executable == null || !Files.isRegularFile(executable)) {
            throw new ConversionException(ErrorCode.ENGINE_NOT_INSTALLED,
                    "Seleccioná un ejecutable válido de Marker.");
        }
        Instant outputScanStart = Instant.now();
        listener.onEvent(new ConversionEvent.EngineStarted(descriptor().displayName()));
        listener.onEvent(new ConversionEvent.PhaseChanged("Procesando documento con Marker..."));

        ProcessSpec spec = commandBuilder.build(executable, request);
        ProcessResult processResult = processExecutor.execute(spec,
                (stream, line) -> listener.onEvent(new ConversionEvent.LogReceived(
                        toConversionStream(stream), line)));
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
