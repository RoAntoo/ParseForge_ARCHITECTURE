package dev.parseforge.infrastructure.engine.markitdown;

import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.engine.EngineManifestRepository;
import dev.parseforge.infrastructure.process.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in actual private MarkItDown process killed with its owning JVM. */
public final class MarkItDownForcedCloseSmoke {
    public static void main(String[] args) throws Exception {
        Path runtime = Path.of(args[0]).toAbsolutePath(), pdf = Path.of(args[1]).toAbsolutePath(), report = Path.of(args[2]).toAbsolutePath();
        Path pidFile = report.resolveSibling("job-python.pid");
        if (args.length == 4) {
            if (!WindowsApplicationJob.initialize()) throw new IllegalStateException("Windows Job required");
            var executor = new LocalProcessExecutor();
            Thread.startVirtualThread(() -> {
                try {
                    for (int i = 0; i < 200; i++) {
                        var child = ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).findFirst();
                        if (child.isPresent()) { Files.writeString(pidFile, Long.toString(child.get().pid())); return; }
                        Thread.sleep(50);
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            var spec = new ManagedMarkItDownRuntime(runtime, new EngineManifestRepository("markitdown").manifest())
                    .conversion(new ConversionRequest(pdf, report.getParent(), MarkItDownEngine.ID, OutputFormat.MARKDOWN, false));
            executor.execute(spec, (stream, line) -> {});
            return;
        }
        Files.deleteIfExists(pidFile);
        var java = Path.of(System.getProperty("java.home"), "bin/java.exe");
        Process child = new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                MarkItDownForcedCloseSmoke.class.getName(), runtime.toString(), pdf.toString(), report.toString(), "child")
                .redirectErrorStream(true).redirectOutput(report.resolveSibling("job-child.log").toFile()).start();
        try {
            for (int i = 0; i < 200 && !Files.isRegularFile(pidFile); i++) Thread.sleep(50);
            if (!Files.isRegularFile(pidFile)) throw new IllegalStateException("Private Python did not start");
            long pythonPid = Long.parseLong(Files.readString(pidFile));
            ProcessHandle python = ProcessHandle.of(pythonPid).orElseThrow();
            if (!python.isAlive() || !child.isAlive()) throw new IllegalStateException("Conversion ended before forced close");
            child.destroyForcibly();
            if (!child.waitFor(10, TimeUnit.SECONDS)) throw new IllegalStateException("JVM remained alive");
            for (int i = 0; i < 100 && python.isAlive(); i++) Thread.sleep(50);
            if (python.isAlive()) { python.destroyForcibly(); throw new IllegalStateException("Orphaned Python"); }
            new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(report.toFile(),
                    Map.of("forcedJvmClose", "PASS", "privatePythonTerminated", true, "orphanProcesses", 0, "pythonPid", pythonPid));
            System.out.println("MARKITDOWN FORCED CLOSE PASS python=" + pythonPid);
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }
}
