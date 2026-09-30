package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.engine.marker.*;
import dev.parseforge.infrastructure.process.LocalProcessExecutor;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Explicit opt-in real downloads/conversions, never part of the ordinary test suite. */
public final class ManagedEngineRealSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("<data-root> install|health|digital|ocr|cancel|repair|uninstall [pdf]");
        System.setProperty("parseforge.dataDir", args[0]);
        var paths = new EnginePathResolver();
        var manifests = new EngineManifestRepository();
        var checksums = new ChecksumVerifier();
        var verifier = new MarkerEngineVerifier(manifests, checksums, new LocalProcessExecutor());
        var installer = new MarkerInstaller(paths, manifests, new HttpsDownloadClient(checksums), verifier, new LocalProcessExecutor());
        var manager = new ManagedEngineManager(paths, manifests, installer, verifier);
        var id = new EngineId("marker");
        Files.createDirectories(paths.dataRoot().resolve("logs"));
        System.out.println("JAVA_ROOT=" + paths.dataRoot() + " REAL=" + paths.dataRoot().toRealPath());
        EngineProgressListener listener = p -> {
            if (p.fraction() < 0 || p.completedBytes() == p.totalBytes()) System.out.println(p.phase() + ": " + p.message());
        };
        switch (args[1]) {
            case "install" -> manager.install(id, listener);
            case "repair" -> manager.repair(id, listener);
            case "uninstall" -> manager.uninstall(id, listener);
            case "health" -> {
                var health = verifier.verify(manifests.descriptor(), paths.engine(id), true, listener, new OperationCancellation());
                if (!health.ready()) throw new IllegalStateException(health.detail());
            }
            case "inspect" -> {
                var evidence = new LinkedHashMap<String, Object>();
                evidence.put("logicalDataRoot", paths.dataRoot().toString());
                evidence.put("javaRealDataRoot", paths.dataRoot().toRealPath().toString());
                evidence.put("state", manager.check(id).name());
                evidence.put("version", manifests.descriptor().version());
                var outputs = new ArrayList<Map<String, String>>();
                Path validation = paths.dataRoot().resolve("validation");
                if (Files.exists(validation)) try (var files = Files.walk(validation)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                        String content = Files.readString(file);
                        String normalized = content.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
                        if (!normalized.contains("parseforge stage 3a") || !normalized.contains("readable markdown"))
                            throw new IllegalStateException("Markdown de validación incorrecto: " + file);
                        outputs.add(Map.of("path", file.toString(), "content", content));
                    }
                }
                evidence.put("outputs", outputs);
                long bytes = 0;
                if (Files.exists(paths.engine(id))) try (var files = Files.walk(paths.engine(id))) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) bytes += Files.size(file);
                }
                evidence.put("installedBytes", bytes);
                evidence.put("directChildrenAlive", ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).count());
                if (args.length > 2) new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                        .writeValue(Path.of(args[2]).toFile(), evidence);
                System.out.println("EVIDENCE=" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence));
            }
            default -> {
                var engine = new MarkerEngine(new LocalProcessExecutor(), manager);
                Path output = paths.dataRoot().resolve("validation/output-" + args[1] + "-" + UUID.randomUUID());
                Files.createDirectories(output);
                var request = new ConversionRequest(Path.of(args[2]), output, id, OutputFormat.MARKDOWN, !args[1].equals("digital"));
                Thread cancel = args[1].equals("cancel") ? Thread.startVirtualThread(() -> {
                    try { Thread.sleep(35000); engine.cancel(); } catch (InterruptedException ignored) { }
                }) : null;
                try {
                    var result = engine.convert(request, e -> System.out.println(e));
                    if (args[1].equals("cancel") ? result.status() != ConversionStatus.CANCELLED
                            : result.status() != ConversionStatus.COMPLETED || result.outputFiles().isEmpty())
                        throw new IllegalStateException(result.toString());
                    System.out.println("CONVERSION=" + result);
                } finally { if (cancel != null) cancel.interrupt(); }
            }
        }
        System.out.println("STATE=" + manager.check(id));
    }
}
