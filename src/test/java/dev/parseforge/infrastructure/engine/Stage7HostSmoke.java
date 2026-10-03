package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.domain.exception.*;
import dev.parseforge.infrastructure.document.*;
import dev.parseforge.infrastructure.engine.marker.*;
import dev.parseforge.infrastructure.engine.markitdown.*;
import dev.parseforge.infrastructure.process.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in local real engines, synthetic PDFs. Does not alter engine installations or settings. */
public final class Stage7HostSmoke {
    public static void main(String[] args) throws Exception {
        Path evidenceRoot = Files.createDirectories(Path.of(args[0]).toAbsolutePath());
        System.setProperty("parseforge.logDir", evidenceRoot.resolve("logs").toString());
        if (!WindowsApplicationJob.initialize()) throw new IllegalStateException("Windows Job required");
        Path fixtures = evidenceRoot.resolve("fixtures"); PdfFixtures.main(new String[]{fixtures.toString(), "long"});
        var evidence = new LinkedHashMap<String, Object>();
        var measurements = new LinkedHashMap<String, Object>(); evidence.put("preflight", measurements);
        for (String name : List.of("digital-text", "scanned-image-only", "mixed", "blank", "digital-long", "scanned-long")) {
            var result = new PdfBoxDocumentPreflight().inspect(fixtures.resolve(name + ".pdf"));
            measurements.put(name, Map.of("type", result.type().name(), "pages", result.pageCount(), "sampledPages", result.sampledPages(),
                    "milliseconds", result.elapsedMillis(), "bytes", result.fileSize(), "textRatio", result.textPageRatio()));
        }
        try { new PdfBoxDocumentPreflight().inspect(fixtures.resolve("corrupt.pdf")); throw new AssertionError("Corruption accepted"); }
        catch (IllegalStateException expected) { evidence.put("unclassifiedParserFailureSoftFallback", "PASS"); }
        write(evidenceRoot, evidence);
        var paths = new EnginePathResolver(); var checksum = new ChecksumVerifier();
        var markerManifest = new EngineManifestRepository();
        var markerVerifier = new MarkerEngineVerifier(markerManifest, checksum, new LocalProcessExecutor());
        var markerManager = new ManagedEngineManager(paths, markerManifest, new MarkerInstaller(paths, markerManifest,
                new HttpsDownloadClient(checksum), markerVerifier, new LocalProcessExecutor()), markerVerifier);
        var mdManifest = new EngineManifestRepository("markitdown");
        var mdVerifier = new MarkItDownEngineVerifier(mdManifest, checksum, new LocalProcessExecutor());
        var mdManager = new ManagedEngineManager(paths, mdManifest, new MarkItDownInstaller(paths, mdManifest,
                new HttpsDownloadClient(checksum), mdVerifier, new LocalProcessExecutor()), mdVerifier, ManagedMarkItDownRuntime::new);
        for (var manager : List.of(mdManager, markerManager)) {
            if (args.length > 1 && args[1].equals("markitdown") && manager != mdManager) continue;
            EngineId id = manager == mdManager ? MarkItDownEngine.ID : MarkerEngine.ID;
            if (manager.check(id) != EngineState.READY) throw new IllegalStateException(id + " not ready");
            ConversionEngine engine = manager == mdManager ? new MarkItDownEngine(new LocalProcessExecutor(), manager) : new MarkerEngine(new LocalProcessExecutor(), manager);
            List<String> cases = manager == mdManager ? List.of("digital-text", "digital-long", "digital-long-cancel") : List.of("digital-text", "scanned-image-only", "mixed", "scanned-long-cancel");
            for (String name : cases) {
                boolean cancel = name.endsWith("-cancel");
                Path input = fixtures.resolve(name.replace("-cancel", "") + ".pdf");
                var phases = new CopyOnWriteArrayList<String>();
                var result = engine.convert(new ConversionRequest(input, evidenceRoot.resolve(id.value()).resolve(name), id, OutputFormat.MARKDOWN, false), event -> {
                    if (event instanceof ConversionEvent.PhaseChanged phase) phases.add(phase.phase());
                    if (cancel && event instanceof ConversionEvent.EngineStarted) Thread.startVirtualThread(() -> {
                        try {
                            for (int attempt = 0; attempt < 400 && ProcessHandle.current().descendants().noneMatch(ProcessHandle::isAlive); attempt++) Thread.sleep(25);
                            Thread.sleep(id.equals(MarkItDownEngine.ID) ? 100 : 5000); engine.cancel();
                        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    });
                });
                if (result.status() != (cancel ? ConversionStatus.CANCELLED : ConversionStatus.COMPLETED)) throw new IllegalStateException(name + " " + result);
                if (!cancel && !Files.readString(result.outputFiles().getFirst()).contains("Readable Markdown")) throw new IllegalStateException("Expected text missing: " + name);
                if (ProcessHandle.current().descendants().anyMatch(ProcessHandle::isAlive)) throw new IllegalStateException("Orphans after " + name);
                evidence.put(id + "/" + name, Map.of("status", result.status().name(), "seconds", result.duration().toMillis()/1000.0,
                        "phases", phases, "orphans", 0, "outputs", result.outputFiles().stream().map(Path::toString).toList()));
                write(evidenceRoot, evidence); System.out.println("HOST PASS " + id + " " + name);
            }
        }
        evidence.put("fullLongScannedConversion", "NOT RUN: cancellation exercised; full book completion remains manual");
        evidence.put("fixtureOrigin", "Synthetic PDFs generated locally; no personal book used");
        write(evidenceRoot, evidence);
    }
    private static void write(Path root, Map<String, Object> evidence) throws Exception {
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(root.resolve("evidence.json").toFile(), evidence);
    }
}
