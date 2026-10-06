package dev.parseforge.infrastructure.engine.markitdown;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.domain.exception.EngineInstallException;
import dev.parseforge.domain.exception.ConversionException;
import dev.parseforge.domain.exception.ErrorCode;
import dev.parseforge.infrastructure.engine.CancellableProcessRunner;
import dev.parseforge.infrastructure.engine.ConversionOutputWorkspace;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class MarkItDownEngine implements ConversionEngine {
    public static final EngineId ID = new EngineId("markitdown");
    private final ProcessExecutor executor;
    private final EngineRuntimeLocator locator;
    private final AtomicReference<OperationCancellation> active = new AtomicReference<>();
    public MarkItDownEngine(ProcessExecutor executor, EngineRuntimeLocator locator) {
        this.executor = Objects.requireNonNull(executor); this.locator = Objects.requireNonNull(locator);
    }
    public EngineDescriptor descriptor() { return locator.descriptor(ID); }
    public EngineState state() { return locator.state(ID); }
    public ConversionResult convert(ConversionRequest request, ConversionEventListener listener) {
        if (!ID.equals(request.engineId())) throw new IllegalArgumentException("Motor incorrecto");
        dev.parseforge.domain.model.DocumentFormats.requireSupported(request);
        var token = new OperationCancellation();
        if (!active.compareAndSet(null, token)) throw new IllegalStateException("MarkItDown está ocupado.");
        Instant start = Instant.now();
        try (var workspace = new ConversionOutputWorkspace(request);
             var runtime = locator.acquire(workspace.request())) {
            token.check();
            listener.onEvent(new ConversionEvent.EngineStarted("MarkItDown"));
            listener.onEvent(new ConversionEvent.PhaseChanged("Procesando documento con MarkItDown..."));
            var result = CancellableProcessRunner.execute(executor, runtime.command(), (stream, line) ->
                    listener.onEvent(new ConversionEvent.LogReceived(stream == ProcessStream.STDOUT
                            ? ConversionEvent.Stream.STDOUT : ConversionEvent.Stream.STDERR, line)), token);
            listener.onEvent(new ConversionEvent.EngineStopped(result.exitCode()));
            if (result.cancelled()) return result(ConversionStatus.CANCELLED, result, "Conversión cancelada", ErrorCode.USER_CANCELLED);
            if (result.timedOut()) return result(ConversionStatus.FAILED, result, "MarkItDown excedió el tiempo máximo.", ErrorCode.PROCESS_TIMEOUT);
            if (result.exitCode() != 0) return result(ConversionStatus.FAILED, result, "MarkItDown terminó con código " + result.exitCode(), ErrorCode.PROCESS_CRASHED);
            Path output;
            listener.onEvent(new ConversionEvent.PhaseChanged("Guardando Markdown..."));
            try {
                output = workspace.publish(ManagedMarkItDownRuntime.output(workspace.request())).getFirst();
            } catch (ConversionException error) {
                return result(ConversionStatus.FAILED, result, error.getMessage(), error.code());
            }
            listener.onEvent(new ConversionEvent.OutputCreated(output));
            return new ConversionResult(ConversionStatus.COMPLETED, 0, List.of(output), null, result.duration());
        } catch (EngineInstallException error) {
            if (error.code() != EngineInstallException.Code.INSTALL_CANCELLED) throw error;
            return new ConversionResult(ConversionStatus.CANCELLED, -1, List.of(), "Conversión cancelada", Duration.between(start, Instant.now()));
        } finally { active.compareAndSet(token, null); }
    }
    private static ConversionResult result(ConversionStatus status, ProcessResult process, String detail, ErrorCode code) {
        return new ConversionResult(status, process.exitCode(), List.of(), detail, process.duration(), code);
    }
    public void cancel() { var token = active.get(); if (token != null) token.cancel(); executor.cancel(); }
}
