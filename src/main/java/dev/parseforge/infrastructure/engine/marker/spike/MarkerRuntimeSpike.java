package dev.parseforge.infrastructure.engine.marker.spike;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.parseforge.application.port.out.ProcessResult;
import dev.parseforge.application.port.out.ProcessSpec;
import dev.parseforge.application.port.out.ProcessStream;
import dev.parseforge.infrastructure.process.LocalProcessExecutor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Headless Java entrypoint using exactly the production process infrastructure. */
public final class MarkerRuntimeSpike {
    private MarkerRuntimeSpike() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !List.of("health", "digital", "ocr", "cancel").contains(args[1])) {
            throw new IllegalArgumentException("Usage: MarkerRuntimeSpike <engine-root> health|digital|ocr|cancel");
        }
        Path logical = Path.of(args[0]).toAbsolutePath().normalize();
        String mode = args[1];
        AutonomousMarkerRuntime runtime;
        try {
            runtime = new AutonomousMarkerRuntime(logical);
            runtime.verifyHashes();
        } catch (Exception error) {
            System.err.println("BROKEN: " + error.getMessage());
            throw error;
        }
        Path logs = runtime.root().resolve("logs");
        Files.createDirectories(logs);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("startedAt", Instant.now().toString());
        evidence.put("logicalRoot", logical.toString());
        evidence.put("javaRealRoot", runtime.root().toString());
        evidence.put("javaExecutable", ProcessHandle.current().info().command().orElse("unknown"));
        evidence.put("environment", runtime.environment());
        List<ProcessResult> results = new ArrayList<>();
        LocalProcessExecutor executor = new LocalProcessExecutor();
        ConcurrentHashMap<Long, ProcessHandle> observed = new ConcurrentHashMap<>();
        ConcurrentHashMap<Long, Map<String, Object>> processEvidence = new ConcurrentHashMap<>();
        AtomicBoolean stop = new AtomicBoolean();
        AtomicBoolean cancellationSent = new AtomicBoolean();
        AtomicLong llamaSeenAt = new AtomicLong();
        // Observe only this Java process's descendants; never scan/kill by engine name.
        Thread observer = Thread.startVirtualThread(() -> {
            while (!stop.get()) {
                ProcessHandle.current().descendants().forEach(handle -> {
                    observed.putIfAbsent(handle.pid(), handle);
                    var info = handle.info();
                    String command = info.command().orElse("");
                    String commandLine = info.commandLine().orElse("");
                    if (!command.isBlank() || !commandLine.isBlank()) {
                        processEvidence.put(handle.pid(), Map.of("pid", handle.pid(),
                                "command", command, "commandLine", commandLine,
                                "arguments", List.of(info.arguments().orElse(new String[0])),
                                "startInstant", info.startInstant().map(Object::toString).orElse("")));
                    }
                    String detail = command + " " + commandLine + " " + String.join(" ", info.arguments().orElse(new String[0]));
                    if (mode.equals("cancel") && detail.contains("llama-server")) {
                        llamaSeenAt.compareAndSet(0, System.nanoTime());
                        // Allow the read-only Windows monitor to record the model arguments.
                        if (System.nanoTime() - llamaSeenAt.get() >= Duration.ofSeconds(2).toNanos()
                                && cancellationSent.compareAndSet(false, true)) {
                            System.out.println("CANCEL_AFTER_LLAMA_CHILD: " + handle.pid());
                            executor.cancel();
                        }
                    }
                });
                try { Thread.sleep(25); } catch (InterruptedException interrupted) { return; }
            }
        });
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        try (var stdout = Files.newBufferedWriter(logs.resolve(mode + ".stdout.log"), StandardCharsets.UTF_8);
             var stderr = Files.newBufferedWriter(logs.resolve(mode + ".stderr.log"), StandardCharsets.UTF_8)) {
            dev.parseforge.application.port.out.ProcessOutputListener listener = (stream, line) -> {
                output.add(stream + ":" + line);
                if (!mode.equals("health") || line.startsWith("Python ") || line.startsWith("{")) {
                    System.out.println(stream + ": " + line);
                }
                try {
                    var writer = stream == ProcessStream.STDOUT ? stdout : stderr;
                    writer.write(line); writer.newLine(); writer.flush();
                } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            };
            if (mode.equals("health")) {
                String audit = "import sys,os,json,importlib.metadata as m,torch,torchvision,marker,surya; "
                        + "from pathlib import Path; root=Path.cwd().resolve(); "
                        + "print(json.dumps(dict(executable=sys.executable,prefix=sys.prefix,base_prefix=sys.base_prefix,path=sys.path,"
                        + "marker=list(marker.__path__),surya=surya.__file__,llama=os.environ['LLAMA_CPP_BINARY'],"
                        + "versions={n:m.version(n) for n in ['marker-pdf','surya-ocr','torch','torchvision']}))); "
                        + "assert all(Path(p).resolve().is_relative_to(root) for p in [sys.executable,sys.prefix,sys.base_prefix,*sys.path]); "
                        + "assert '+cpu' in torch.__version__ and '+cpu' in torchvision.__version__; "
                        + "assert torch.version.cuda is None and torch.version.hip is None; "
                        + "assert m.version('marker-pdf')=='2.0.0' and m.version('surya-ocr')=='0.22.1'";
                results.add(executor.execute(runtime.python(List.of("--version"), Duration.ofSeconds(30)), listener));
                results.add(executor.execute(runtime.python(List.of("-c", audit), Duration.ofMinutes(2)), listener));
                results.add(executor.execute(runtime.python(List.of("-c", AutonomousMarkerRuntime.ENTRYPOINT, "--help"), Duration.ofMinutes(2)), listener));
                results.add(executor.execute(new ProcessSpec(Path.of(runtime.environment().get("LLAMA_CPP_BINARY")),
                        List.of("--version"), runtime.environment(), runtime.root(), Duration.ofSeconds(30), false), listener));
            } else {
                Path pdf = runtime.root().resolve("temp/" + (mode.equals("digital") ? "digital.pdf" : "ocr.pdf"));
                results.add(executor.execute(runtime.conversion(pdf, runtime.root().resolve("temp/output-" + mode),
                        !mode.equals("digital")), listener));
            }
        } finally {
            stop.set(true);
            observer.join();
            evidence.put("results", results.stream().map(r -> Map.of("exitCode", r.exitCode(),
                    "cancelled", r.cancelled(), "timedOut", r.timedOut(), "durationMs", r.duration().toMillis())).toList());
            evidence.put("processes", processEvidence.values());
            evidence.put("cancellationSent", cancellationSent.get());
            evidence.put("aliveObservedPids", observed.values().stream().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).toList());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(logs.resolve(mode + ".json").toFile(), evidence);
        }
        boolean valid = results.stream().allMatch(r -> mode.equals("cancel") ? r.cancelled() : r.exitCode() == 0 && !r.timedOut());
        if (mode.equals("health")) {
            valid &= output.stream().anyMatch(line -> line.contains("Usage:"));
        } else if (!mode.equals("cancel")) {
            try (var files = Files.walk(runtime.root().resolve("temp/output-" + mode))) {
                valid &= files.anyMatch(p -> p.toString().endsWith(".md") && nonEmpty(p));
            }
        } else { valid &= cancellationSent.get(); }
        valid &= observed.values().stream().noneMatch(ProcessHandle::isAlive);
        System.out.println(valid ? (mode.equals("health") ? "READY" : "PASS: " + mode) : "BROKEN: " + mode);
        if (!valid) { throw new IllegalStateException("Spike validation failed; see " + logs.resolve(mode + ".json")); }
    }

    private static boolean nonEmpty(Path file) {
        try { return Files.size(file) > 0; } catch (java.io.IOException error) { return false; }
    }
}
