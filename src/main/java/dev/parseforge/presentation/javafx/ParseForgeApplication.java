package dev.parseforge.presentation.javafx;

import dev.parseforge.application.port.out.UserSettingsRepository;
import dev.parseforge.application.settings.UserSettings;
import dev.parseforge.application.usecase.CancelConversionUseCase;
import dev.parseforge.application.usecase.StartConversionUseCase;
import dev.parseforge.infrastructure.config.JsonUserSettingsRepository;
import dev.parseforge.infrastructure.engine.SimpleEngineRegistry;
import dev.parseforge.infrastructure.engine.marker.MarkerCommandBuilder;
import dev.parseforge.infrastructure.engine.marker.MarkerEngine;
import dev.parseforge.infrastructure.process.LocalProcessExecutor;
import dev.parseforge.presentation.javafx.controller.MainController;
import dev.parseforge.application.usecase.*;
import dev.parseforge.infrastructure.engine.*;
import dev.parseforge.infrastructure.engine.marker.*;
import dev.parseforge.infrastructure.engine.markitdown.*;
import dev.parseforge.application.settings.EngineProfile;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ParseForgeApplication extends Application {
    private ExecutorService backgroundExecutor;
    private CancelConversionUseCase cancelConversion;
    private MultiEngineManager engineManager;
    private ExecutorService engineExecutor;
    MainController controller;

    @Override
    public void start(Stage stage) throws Exception {
        EnginePathResolver paths = new EnginePathResolver();
        UserSettingsRepository settingsRepository = new JsonUserSettingsRepository(
                paths.configFile());
        UserSettings settings = dev.parseforge.infrastructure.config.InstalledSettings.load(paths);
        EngineManifestRepository manifests = new EngineManifestRepository();
        ChecksumVerifier checksums = new ChecksumVerifier();
        var verifier = new MarkerEngineVerifier(manifests, checksums, new LocalProcessExecutor());
        var installer = new MarkerInstaller(paths, manifests, new HttpsDownloadClient(checksums),
                verifier, new LocalProcessExecutor());
        var markerManager = new ManagedEngineManager(paths, manifests, installer, verifier);
        var markItDownManifest = new EngineManifestRepository("markitdown");
        var markItDownVerifier = new MarkItDownEngineVerifier(markItDownManifest, checksums, new LocalProcessExecutor());
        var markItDownInstaller = new MarkItDownInstaller(paths, markItDownManifest, new HttpsDownloadClient(checksums),
                markItDownVerifier, new LocalProcessExecutor());
        var markItDownManager = new ManagedEngineManager(paths, markItDownManifest, markItDownInstaller, markItDownVerifier,
                ManagedMarkItDownRuntime::new);
        engineManager = new MultiEngineManager(List.of(markerManager, markItDownManager));
        LocalProcessExecutor processExecutor = new LocalProcessExecutor();
        String override = System.getProperty("parseforge.marker.override");
        MarkerEngine markerEngine = override == null || override.isBlank()
                ? new MarkerEngine(processExecutor, engineManager)
                : new MarkerEngine(() -> Path.of(override), processExecutor, new MarkerCommandBuilder());
        engineExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("engine-worker-", 0).factory());

        backgroundExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual()
                .name("conversion-worker-", 0).factory());
        StartConversionUseCase startConversion = new StartConversionUseCase(
                new SimpleEngineRegistry(List.of(markerEngine, new MarkItDownEngine(new LocalProcessExecutor(), engineManager))), backgroundExecutor);
        cancelConversion = new CancelConversionUseCase(startConversion);

        controller = new MainController(
                stage,
                startConversion,
                cancelConversion,
                settingsRepository,
                new InstallEngineUseCase(engineManager, engineExecutor),
                new RepairEngineUseCase(engineManager, engineExecutor),
                new UninstallEngineUseCase(engineManager, engineExecutor),
                new CheckEngineStatusUseCase(engineManager, engineExecutor),
                new CancelEngineOperationUseCase(engineManager),
                List.of(EngineProfile.marker(), EngineProfile.markItDown(markItDownManifest.manifest().path("installedBytes").asLong())),
                settings,
                override != null && !override.isBlank() && java.nio.file.Files.isRegularFile(Path.of(override)),
                new AnalyzeDocumentUseCase(new dev.parseforge.infrastructure.document.PdfBoxDocumentPreflight()));

        var screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(controller.view(), Math.min(1100, screen.getWidth() - 32),
                Math.min(760, screen.getHeight() - 64));
        scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
        stage.setTitle("ParseForge");
        stage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/icons/ParseForge.png")));
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.setScene(scene);
        stage.show();
        javafx.application.Platform.runLater(controller::showWelcomeIfNeeded);
        org.slf4j.LoggerFactory.getLogger(getClass()).info("Application window opened; config={}, engines={}",
                paths.configFile(), paths.dataRoot().resolve("engines"));
        Launcher.completeUiSmoke(stage);
    }

    @Override
    public void stop() {
        if (controller != null) controller.close();
        if (cancelConversion != null) {
            cancelConversion.cancel();
        }
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        if (engineManager != null) engineManager.availableEngines().forEach(engine -> engineManager.cancelCurrentOperation(engine.id()));
        if (engineExecutor != null) engineExecutor.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }

}
