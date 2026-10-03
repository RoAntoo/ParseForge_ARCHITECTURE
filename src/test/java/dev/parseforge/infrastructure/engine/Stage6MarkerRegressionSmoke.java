package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.engine.marker.*;
import dev.parseforge.infrastructure.process.*;
import java.nio.file.*;
import java.util.*;

/** Existing managed Marker: conversion regression only, no lifecycle mutation. */
public final class Stage6MarkerRegressionSmoke {
    public static void main(String[] args) throws Exception {
        System.setProperty("parseforge.dataDir", args[0]);
        System.setProperty("parseforge.logDir", Path.of(args[2]).resolve("logs").toString());
        WindowsApplicationJob.initialize();
        var paths = new EnginePathResolver(); var manifests = new EngineManifestRepository();
        var verifier = new MarkerEngineVerifier(manifests, new ChecksumVerifier(), new LocalProcessExecutor());
        var manager = new ManagedEngineManager(paths, manifests, new MarkerInstaller(paths, manifests,
                new HttpsDownloadClient(new ChecksumVerifier()), verifier, new LocalProcessExecutor()), verifier);
        var engine = new MarkerEngine(new LocalProcessExecutor(), manager);
        if (manager.check(MarkerEngine.ID) != EngineState.READY) throw new IllegalStateException("Existing Marker not READY");
        var evidence = new LinkedHashMap<String, Object>(); evidence.put("existingMarkerReady", true);
        for (String mode : List.of("digital", "ocr", "cancel")) {
            Path output = Files.createDirectories(Path.of(args[2], mode));
            var request = new ConversionRequest(Path.of(args[1]), output, MarkerEngine.ID, OutputFormat.MARKDOWN, !mode.equals("digital"));
            var result = engine.convert(request, event -> {
                if (mode.equals("cancel") && event instanceof ConversionEvent.EngineStarted) Thread.startVirtualThread(() -> {
                    try {
                        for (int i = 0; i < 400 && ProcessHandle.current().descendants().noneMatch(ProcessHandle::isAlive); i++) Thread.sleep(25);
                        Thread.sleep(1000); engine.cancel();
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            });
            if (result.status() != (mode.equals("cancel") ? ConversionStatus.CANCELLED : ConversionStatus.COMPLETED))
                throw new IllegalStateException(mode + ": " + result);
            if (!mode.equals("cancel") && (result.outputFiles().isEmpty() || !Files.readString(result.outputFiles().getFirst()).contains("Readable Markdown")))
                throw new IllegalStateException("Invalid Marker output");
            if (ProcessHandle.current().descendants().anyMatch(ProcessHandle::isAlive)) throw new IllegalStateException("Orphaned Marker process");
            evidence.put(mode, Map.of("status", result.status().name(), "seconds", result.duration().toMillis()/1000.0, "outputFiles", result.outputFiles().stream().map(Path::toString).toList()));
            System.out.println("MARKER REGRESSION PASS " + mode);
        }
        evidence.put("orphans", 0); evidence.put("markerRoot", paths.engine(MarkerEngine.ID).toString());
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[2], "evidence.json").toFile(), evidence);
    }
}
