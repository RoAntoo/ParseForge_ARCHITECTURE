package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.engine.marker.*;
import dev.parseforge.infrastructure.engine.markitdown.*;
import dev.parseforge.infrastructure.process.*;

import java.nio.file.*;
import java.util.*;

/** Opt-in real conversion using an already installed engine; never installs or repairs it. */
public final class ConversionOutputSmoke {
    public static void main(String[] args) throws Exception {
        String id = args[0];
        if (!Set.of("marker", "markitdown").contains(id)) throw new IllegalArgumentException("Unknown engine");
        System.setProperty("parseforge.dataDir", args[1]);
        Path evidence = Files.createDirectories(Path.of(args[3]).toAbsolutePath());
        System.setProperty("parseforge.logDir", evidence.resolve("logs").toString());
        WindowsApplicationJob.initialize();
        var paths = new EnginePathResolver();
        var manifests = new EngineManifestRepository(id);
        EngineVerifier verifier = id.equals("marker")
                ? new MarkerEngineVerifier(manifests, new ChecksumVerifier(), new LocalProcessExecutor())
                : new MarkItDownEngineVerifier(manifests, new ChecksumVerifier(), new LocalProcessExecutor());
        // These collaborators are required by the manager; no lifecycle mutation is invoked.
        EngineInstaller installer = id.equals("marker")
                ? new MarkerInstaller(paths, manifests, new HttpsDownloadClient(new ChecksumVerifier()), verifier, new LocalProcessExecutor())
                : new MarkItDownInstaller(paths, manifests, new HttpsDownloadClient(new ChecksumVerifier()), verifier, new LocalProcessExecutor());
        ManagedPythonRuntime.Factory runtimes = id.equals("marker")
                ? ManagedMarkerRuntime::new : ManagedMarkItDownRuntime::new;
        var manager = new ManagedEngineManager(paths, manifests, installer, verifier, runtimes);
        ConversionEngine engine = id.equals("marker")
                ? new MarkerEngine(new LocalProcessExecutor(), manager)
                : new MarkItDownEngine(new LocalProcessExecutor(), manager);
        Path run = Files.createTempDirectory(evidence, id + "-");
        Path input = Files.copy(Path.of(args[2]), run.resolve("documento ñ.pdf"));
        Path output = Files.createDirectory(run.resolve("salida"));
        var request = new ConversionRequest(input, output, new EngineId(id), OutputFormat.MARKDOWN);
        var result = engine.convert(request, ignored -> {});
        if (result.status() != ConversionStatus.COMPLETED || result.outputFiles().size() != 1)
            throw new IllegalStateException("Real conversion failed: " + result);
        Path markdown = result.outputFiles().getFirst();
        byte[] previous = Files.readAllBytes(markdown);
        if (previous.length == 0) throw new IllegalStateException("Empty real output");
        // Corrupt only our copied fixture, using the same name/output destination.
        Files.writeString(input, "not a PDF");
        var failure = engine.convert(request, ignored -> {});
        if (failure.status() != ConversionStatus.FAILED || !failure.outputFiles().isEmpty()
                || !Arrays.equals(previous, Files.readAllBytes(markdown)))
            throw new IllegalStateException("Failed conversion replaced a previous output");
        try (var entries = Files.list(output)) {
            if (entries.anyMatch(p -> p.getFileName().toString().startsWith(".parseforge-")))
                throw new IllegalStateException("Temporary conversion output was not cleaned");
        }
        if (ProcessHandle.current().descendants().anyMatch(ProcessHandle::isAlive))
            throw new IllegalStateException("Orphaned engine process");
        var report = Map.of("engine", id, "success", result.status().name(),
                "durationSeconds", result.duration().toMillis() / 1000.0,
                "markdown", markdown.toString(), "failedConversion", failure.status().name(),
                "previousOutputPreserved", true, "temporaryOutputCleaned", true, "orphans", 0);
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(run.resolve("evidence.json").toFile(), report);
        System.out.println("CONVERSION OUTPUT SMOKE PASS " + run);
    }
}
