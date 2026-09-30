package dev.parseforge.infrastructure.process;

import dev.parseforge.application.port.out.ProcessResult;
import dev.parseforge.application.port.out.ProcessSpec;
import dev.parseforge.application.port.out.ProcessStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProcessExecutorTest {
    @TempDir
    Path workingDirectory;

    @Test
    void capturesBothStreamsAndExitCode() {
        LocalProcessExecutor executor = new LocalProcessExecutor();
        List<String> output = Collections.synchronizedList(new ArrayList<>());

        ProcessResult result = executor.execute(javaProcess(),
                (stream, line) -> output.add(stream + ":" + line));

        assertEquals(0, result.exitCode());
        assertFalse(result.cancelled());
        assertFalse(result.timedOut());
        assertTrue(output.contains("STDOUT:stdout-line"));
        assertTrue(output.contains("STDERR:stderr-line"));
    }

    @Test
    void cancelsAnActiveProcess() throws Exception {
        LocalProcessExecutor executor = new LocalProcessExecutor();
        CountDownLatch ready = new CountDownLatch(1);

        CompletableFuture<ProcessResult> running = CompletableFuture.supplyAsync(() ->
                executor.execute(javaProcess("wait"), (stream, line) -> {
                    if (stream == ProcessStream.STDOUT && line.equals("ready")) {
                        ready.countDown();
                    }
                }));

        assertTrue(ready.await(5, TimeUnit.SECONDS), "The fixture process did not start");
        executor.cancel();
        ProcessResult result = running.get(5, TimeUnit.SECONDS);

        assertTrue(result.cancelled());
        assertFalse(result.timedOut());
    }

    @Test
    void cancellationWithoutAnActiveRunDoesNotCancelTheNextRun() {
        LocalProcessExecutor executor = new LocalProcessExecutor();

        executor.cancel();
        ProcessResult result = executor.execute(javaProcess(), (stream, line) -> { });

        assertEquals(0, result.exitCode());
        assertFalse(result.cancelled());
    }

    @Test
    void keepsDrainingAStreamAfterTheOutputListenerFails() {
        LocalProcessExecutor executor = new LocalProcessExecutor();
        AtomicInteger stdoutCalls = new AtomicInteger();

        ProcessResult result = executor.execute(
                javaProcess(Duration.ofSeconds(3), "many-lines"),
                (stream, line) -> {
                    if (stream == ProcessStream.STDOUT) {
                        stdoutCalls.incrementAndGet();
                        throw new IllegalStateException("listener failure");
                    }
                });

        assertEquals(0, result.exitCode());
        assertFalse(result.timedOut());
        assertEquals(1, stdoutCalls.get());
    }

    private ProcessSpec javaProcess(String... fixtureArguments) {
        return javaProcess(Duration.ofSeconds(10), fixtureArguments);
    }

    private ProcessSpec javaProcess(Duration timeout, String... fixtureArguments) {
        String executableName = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe"
                : "java";
        Path executable = Path.of(System.getProperty("java.home"), "bin", executableName);
        List<String> arguments = new ArrayList<>(List.of(
                "-cp",
                Path.of("target", "test-classes").toAbsolutePath().toString(),
                ProcessTestFixture.class.getName()));
        arguments.addAll(List.of(fixtureArguments));
        return new ProcessSpec(executable, arguments, Map.of(), workingDirectory, timeout);
    }
}
