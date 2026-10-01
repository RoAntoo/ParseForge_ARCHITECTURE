package dev.parseforge.presentation.javafx;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.parseforge.infrastructure.config.JsonUserSettingsRepository;
import dev.parseforge.infrastructure.engine.EnginePathResolver;
import dev.parseforge.infrastructure.process.WindowsApplicationJob;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.nio.file.*;
import java.util.*;

/** Plain main class supports JavaFX on the classpath in a jpackage launcher. */
public final class Launcher {
    private static Path uiReport;
    private static Path logDirectory;
    public static void main(String[] args) {
        EnginePathResolver paths = new EnginePathResolver();
        try {
            logDirectory = resolveLogDirectory(paths.logs());
            System.setProperty("parseforge.logDir", logDirectory.toString());
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> startupFailure(logDirectory, error));
            boolean job = WindowsApplicationJob.initialize();
            org.slf4j.LoggerFactory.getLogger(Launcher.class).info("Startup; Java {}; private runtime={}; Windows job={}",
                    System.getProperty("java.version"), System.getProperty("java.home"), job);
            if (args.length == 2 && args[0].equals("--smoke-test")) {
                writeReport(Path.of(args[1]), paths, job, false); return;
            }
            if (args.length == 2 && args[0].equals("--smoke-ui")) uiReport = Path.of(args[1]);
            Application.launch(ParseForgeApplication.class, args);
        } catch (Throwable error) { startupFailure(logDirectory, error); System.exit(1); }
    }
    static Path resolveLogDirectory(Path configured) throws java.io.IOException {
        try {
            Files.createDirectories(configured);
            Path probe = Files.createTempFile(configured, ".write-probe-", ".tmp");
            try { Files.writeString(probe, "ParseForge log write probe"); }
            finally { Files.delete(probe); }
            return configured;
        }
        catch (java.io.IOException | SecurityException unavailable) {
            return Files.createTempDirectory("ParseForge-logs-");
        }
    }
    private static void writeReport(Path file, EnginePathResolver paths, boolean job, boolean ui) throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("javaHome", System.getProperty("java.home"));
        report.put("dataRoot", paths.dataRoot().toString());
        report.put("configFile", paths.configFile().toString());
        report.put("logs", logDirectory.toString()); report.put("windowsJob", job); report.put("uiOpened", ui);
        var repository = new JsonUserSettingsRepository(paths.configFile());
        boolean isolated = System.getProperty("parseforge.dataDir") != null && !System.getProperty("parseforge.dataDir").isBlank();
        var settings = repository.load();
        if (isolated) {
            settings = new dev.parseforge.application.settings.UserSettings("", "out", "in", "out", "es", "marker");
            repository.save(settings);
        }
        report.put("settingsReadable", repository.load().equals(settings));
        report.put("settingsPersisted", Files.isRegularFile(paths.configFile()));
        report.put("markerRoot", paths.engine(new dev.parseforge.domain.model.EngineId("marker")).toString());
        report.put("manifestLoaded", new dev.parseforge.infrastructure.engine.EngineManifestRepository().descriptor().version());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), report);
    }
    static void completeUiSmoke(Stage stage) {
        if (uiReport == null) return;
        var delay = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(8));
        delay.setOnFinished(ignored -> {
            try {
                if (!stage.isShowing() || stage.getScene().lookup("#engine-state") == null)
                    throw new IllegalStateException("Window did not open");
                var pixels = stage.getScene().snapshot(null);
                var png = new java.awt.image.BufferedImage((int)pixels.getWidth(), (int)pixels.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y=0; y<png.getHeight(); y++) for (int x=0; x<png.getWidth(); x++)
                    png.setRGB(x, y, pixels.getPixelReader().getArgb(x, y));
                javax.imageio.ImageIO.write(png, "png", uiReport.resolveSibling("ui-smoke.png").toFile());
                writeReport(uiReport, new EnginePathResolver(), true, true);
                Platform.exit();
            } catch (Exception error) { startupFailure(logDirectory, error); Platform.exit(); }
        }); delay.play();
    }
    private static void startupFailure(Path logs, Throwable error) {
        try {
            try (var writer = new java.io.PrintWriter(Files.newBufferedWriter(logs.resolve("startup-error.log"),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND))) { error.printStackTrace(writer); }
            Runnable dialog = () -> {
                ButtonType open = new ButtonType("Abrir carpeta de logs");
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "ParseForge no pudo iniciarse correctamente.\nSe guardó un registro técnico en:\n" + logs, open, ButtonType.CLOSE);
                alert.setHeaderText("Error al iniciar ParseForge");
                if (alert.showAndWait().orElse(ButtonType.CLOSE) == open)
                    try { java.awt.Desktop.getDesktop().open(logs.toFile()); } catch (Exception ignored) { }
            };
            if (Platform.isFxApplicationThread()) dialog.run();
            else try { Platform.runLater(dialog); } catch (IllegalStateException notStarted) {
                int action = javax.swing.JOptionPane.showOptionDialog(null,
                        "ParseForge no pudo iniciarse.\nSe guardó un registro técnico en:\n" + logs,
                        "ParseForge", javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.ERROR_MESSAGE,
                        null, new String[]{"Abrir carpeta de logs", "Cerrar"}, "Cerrar");
                if (action == 0) try { java.awt.Desktop.getDesktop().open(logs.toFile()); } catch (Exception ignored) { }
            }
        } catch (Throwable ignored) { error.printStackTrace(); }
    }
}
