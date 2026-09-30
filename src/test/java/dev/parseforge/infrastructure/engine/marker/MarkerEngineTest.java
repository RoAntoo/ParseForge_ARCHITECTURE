package dev.parseforge.infrastructure.engine.marker;

import dev.parseforge.application.port.out.ConversionEvent;
import dev.parseforge.application.port.out.ProcessExecutor;
import dev.parseforge.application.port.out.ProcessResult;
import dev.parseforge.application.port.out.ProcessStream;
import dev.parseforge.domain.model.ConversionRequest;
import dev.parseforge.domain.model.ConversionResult;
import dev.parseforge.domain.model.ConversionStatus;
import dev.parseforge.domain.model.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarkerEngineTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void translatesProcessOutputAndSuccessfulExitIntoConversionEvents() throws Exception {
        Path executable = Files.createFile(temporaryDirectory.resolve("marker_single.exe"));
        Path output = Files.createDirectory(temporaryDirectory.resolve("output"));
        ProcessExecutor processExecutor = mock(ProcessExecutor.class);
        doAnswer(invocation -> {
            var listener = (dev.parseforge.application.port.out.ProcessOutputListener) invocation.getArgument(1);
            listener.onLine(ProcessStream.STDOUT, "marker-log");
            return new ProcessResult(0, false, false, Duration.ofSeconds(2));
        }).when(processExecutor).execute(any(), any());
        MarkerEngine engine = new MarkerEngine(
                () -> executable, processExecutor, new MarkerCommandBuilder());
        List<ConversionEvent> events = new ArrayList<>();

        ConversionResult result = engine.convert(request(output), events::add);

        assertEquals(ConversionStatus.COMPLETED, result.status());
        assertTrue(events.stream().anyMatch(event ->
                event instanceof ConversionEvent.LogReceived log
                        && log.stream() == ConversionEvent.Stream.STDOUT
                        && log.message().equals("marker-log")));
        assertInstanceOf(ConversionEvent.EngineStarted.class, events.getFirst());
    }

    @Test
    void mapsNonZeroExitCodeToFailedConversion() throws Exception {
        Path executable = Files.createFile(temporaryDirectory.resolve("marker_single.exe"));
        Path output = Files.createDirectory(temporaryDirectory.resolve("output"));
        ProcessExecutor processExecutor = mock(ProcessExecutor.class);
        when(processExecutor.execute(any(), any())).thenReturn(
                new ProcessResult(7, false, false, Duration.ofSeconds(1)));
        MarkerEngine engine = new MarkerEngine(
                () -> executable, processExecutor, new MarkerCommandBuilder());

        ConversionResult result = engine.convert(request(output), ignored -> { });

        assertEquals(ConversionStatus.FAILED, result.status());
        assertEquals(7, result.exitCode());
        assertTrue(result.error().orElseThrow().contains("7"));
    }

    @Test
    void delegatesCancellationToProcessExecutor() throws Exception {
        Path executable = Files.createFile(temporaryDirectory.resolve("marker_single.exe"));
        ProcessExecutor processExecutor = mock(ProcessExecutor.class);
        MarkerEngine engine = new MarkerEngine(
                () -> executable, processExecutor, new MarkerCommandBuilder());

        engine.cancel();

        verify(processExecutor).cancel();
    }

    private ConversionRequest request(Path outputDirectory) {
        return new ConversionRequest(
                temporaryDirectory.resolve("input.pdf"),
                outputDirectory,
                MarkerEngine.ID,
                OutputFormat.MARKDOWN);
    }
}
