package dev.parseforge.presentation.javafx;

import dev.parseforge.application.settings.UserSettings;
import dev.parseforge.infrastructure.config.JsonUserSettingsRepository;
import dev.parseforge.infrastructure.engine.*;
import dev.parseforge.infrastructure.engine.markitdown.*;
import dev.parseforge.infrastructure.process.*;
import dev.parseforge.application.port.out.OperationCancellation;
import dev.parseforge.domain.model.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Opt-in real UI installation/lifecycle/conversion with isolated data. */
public final class Stage6HostSmoke {
    private static Stage stage;
    private static ParseForgeApplication app;
    private static Path root;
    private static final Map<String, Object> evidence = new LinkedHashMap<>();
    public static void main(String[] args) throws Exception {
        root = Path.of(args[0]).toAbsolutePath();
        System.setProperty("parseforge.dataDir", root.toString());
        System.setProperty("parseforge.logDir", root.resolve("logs").toString());
        if (!WindowsApplicationJob.initialize()) throw new IllegalStateException("Windows Job required");
        var paths = new EnginePathResolver();
        var settings = new JsonUserSettingsRepository(paths.configFile());
        settings.save(new UserSettings("", root.resolve("output ñ").toString(), "", root.resolve("output ñ").toString(), "es", "markitdown", 1));
        CountDownLatch toolkit = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); toolkit.countDown(); });
        if (!toolkit.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("JavaFX startup failed");
        try {
            open(); waitFor(() -> !button("check-engine-state-markitdown").isDisabled(), 30);
            fx(() -> { button("expand-markitdown").fire(); button("install-markitdown").fire(); return null; });
            long before = System.nanoTime();
            waitFor(() -> radio().isSelected() && !radio().isDisabled(), 240);
            evidence.put("uiInstallSeconds", (System.nanoTime() - before) / 1e9);
            evidence.put("uiInstall", "PASS"); System.out.println("HOST UI INSTALL PASS");
            convert(Path.of(args[1]), "digital"); convert(Path.of(args[2]), "multipageUnicode");
            var manifest = new EngineManifestRepository("markitdown");
            var verifier = new MarkItDownEngineVerifier(manifest, new ChecksumVerifier(), new LocalProcessExecutor());
            var health = verifier.verify(manifest.descriptor(), paths.engine(MarkItDownEngine.ID), true, true, ignored -> {}, new OperationCancellation());
            if (!health.ready()) throw new IllegalStateException(health.detail());
            evidence.put("fullIntegrityAndHealth", "PASS");
            open(); waitFor(() -> radio().isSelected() && !radio().isDisabled(), 30);
            if (!settings.load().selectedEngine().equals("markitdown")) throw new IllegalStateException("Selection was not persisted");
            evidence.put("restartPersistence", "PASS");
            fx(() -> { button("expand-markitdown").fire(); button("repair-markitdown").fire(); return null; });
            waitFor(() -> radio().isSelected() && !radio().isDisabled(), 240);
            evidence.put("uiRepair", "PASS"); System.out.println("HOST UI REPAIR PASS");
            // An unclassified preflight parser error allows an engine attempt, which must fail.
            fx(() -> { app.controller.selectPdf(Path.of(args[3])); return null; });
            waitFor(() -> label("document-advice").getText().contains("Podés intentar convertirlo"), 40);
            fx(() -> {
                if (button("convert-pdf").isDisabled()) throw new IllegalStateException("Soft preflight failure blocked conversion");
                button("convert-pdf").fire();
                return null;
            });
            waitFor(() -> label("conversion-state").getText().equals("Conversión fallida") && Window.getWindows().size() > 1, 40);
            fx(() -> {
                Stage error = (Stage)Window.getWindows().stream().filter(w -> w != stage && w.isShowing()).findFirst().orElseThrow();
                ((Button)((DialogPane)error.getScene().lookup(".dialog-pane")).lookupButton(ButtonType.OK)).fire(); return null;
            });
            evidence.put("invalidPdf", "PASS");
            fx(() -> { app.controller.selectPdf(Path.of(args[4])); return null; });
            waitFor(() -> !button("convert-pdf").isDisabled(), 15);
            fx(() -> { button("convert-pdf").fire(); return null; });
            waitFor(() -> label("conversion-state").getText().contains("Procesando PDF digital"), 30);
            fx(() -> { button("cancel-conversion").fire(); return null; });
            waitFor(() -> label("conversion-state").getText().equals("Conversión cancelada") && !button("convert-pdf").isDisabled(), 30);
            waitFor(() -> ProcessHandle.current().descendants().noneMatch(ProcessHandle::isAlive), 10);
            evidence.put("uiCancellation", "PASS"); evidence.put("orphansAfterCancellation", 0);
            fx(() -> { snapshot(); return null; });
            fx(() -> { ((TitledPane)stage.getScene().lookup("#engine-options-markitdown")).setExpanded(true); return null; });
            Platform.runLater(() -> button("uninstall-markitdown").fire());
            waitFor(() -> Window.getWindows().size() > 1, 10);
            fx(() -> {
                Stage confirm = (Stage)Window.getWindows().stream().filter(w -> w != stage && w.isShowing()).findFirst().orElseThrow();
                ((Button)((DialogPane)confirm.getScene().lookup(".dialog-pane")).lookupButton(ButtonType.OK)).fire(); return null;
            });
            waitFor(() -> button("install-markitdown").isVisible() && !button("install-markitdown").isDisabled(), 40);
            if (Files.exists(paths.engine(MarkItDownEngine.ID))) throw new IllegalStateException("Uninstall left runtime");
            evidence.put("uiUninstall", "PASS");
            fx(() -> { button("install-markitdown").fire(); return null; });
            waitFor(() -> radio().isSelected() && !radio().isDisabled(), 240);
            evidence.put("uiReinstall", "PASS"); convert(Path.of(args[1]), "afterReinstall");
            evidence.put("markerUnchanged", "Marker absent in isolated root; real Marker regression recorded separately");
            evidence.put("windowsJob", true); evidence.put("dataRoot", root.toString());
            long bytes;
            try (var files = Files.walk(paths.engine(MarkItDownEngine.ID))) { bytes = files.filter(Files::isRegularFile).mapToLong(p -> { try { return Files.size(p); } catch (Exception e) { throw new RuntimeException(e); } }).sum(); }
            evidence.put("installedBytesIncludingMetadataAndLogs", bytes);
            new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(root.resolve("host-evidence.json").toFile(), evidence);
            System.out.println("HOST STAGE6 PASS " + root.resolve("host-evidence.json"));
        } finally {
            fx(() -> { if (app != null) app.stop(); for (var w : List.copyOf(Window.getWindows())) w.hide(); Platform.exit(); return null; });
        }
    }
    private static void convert(Path pdf, String key) throws Exception {
        fx(() -> { app.controller.selectPdf(pdf); return null; });
        waitFor(() -> !button("convert-pdf").isDisabled(), 15);
        fx(() -> { button("convert-pdf").fire(); return null; });
        waitFor(() -> label("conversion-state").getText().equals("Conversión completada"), 45);
        Path output = root.resolve("output ñ").resolve(pdf.getFileName().toString().replaceFirst("\\.pdf$", ".md"));
        if (!Files.readString(output).contains("Readable Markdown")) throw new IllegalStateException("Incorrect Markdown " + output);
        evidence.put(key, Map.of("result", "PASS", "output", output.toString())); System.out.println("HOST CONVERSION PASS " + key);
    }
    private static void open() throws Exception {
        fx(() -> { if (app != null) { app.stop(); stage.hide(); } app = new ParseForgeApplication(); stage = new Stage(); app.start(stage); return null; });
    }
    private static Button button(String id) { return (Button)stage.getScene().lookup("#" + id); }
    private static Label label(String id) { return (Label)stage.getScene().lookup("#" + id); }
    private static RadioButton radio() { return (RadioButton)stage.getScene().lookup("#markitdown-selection"); }
    private static void waitFor(BooleanSupplier check, int seconds) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!fx(check::getAsBoolean)) { if (System.nanoTime() > until) throw new IllegalStateException("Host UI timeout"); Thread.sleep(100); }
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(25, TimeUnit.SECONDS);
    }
    private static void snapshot() throws Exception {
        var image = stage.getScene().snapshot(null);
        var output = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        ImageIO.write(output,"png",root.resolve("host-window.png").toFile());
    }
}
