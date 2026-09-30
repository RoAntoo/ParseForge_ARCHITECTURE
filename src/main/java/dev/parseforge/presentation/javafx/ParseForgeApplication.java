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
    private ManagedEngineManager engineManager;
    private ExecutorService engineExecutor;
    MainController controller;

    @Override
    public void start(Stage stage) throws Exception {
        EnginePathResolver paths = new EnginePathResolver();
        UserSettingsRepository settingsRepository = new JsonUserSettingsRepository(
                paths.configFile());
        UserSettings settings = settingsRepository.load();
        if (settings.equals(UserSettings.empty())) {
            settings = new JsonUserSettingsRepository(applicationDataDirectory().resolve("config.json")).load();
        }
        EngineManifestRepository manifests = new EngineManifestRepository();
        ChecksumVerifier checksums = new ChecksumVerifier();
        var verifier = new MarkerEngineVerifier(manifests, checksums, new LocalProcessExecutor());
        var installer = new MarkerInstaller(paths, manifests, new HttpsDownloadClient(checksums),
                verifier, new LocalProcessExecutor());
        engineManager = new ManagedEngineManager(paths, manifests, installer, verifier);
        LocalProcessExecutor processExecutor = new LocalProcessExecutor();
        String override = System.getProperty("parseforge.marker.override");
        MarkerEngine markerEngine = override == null || override.isBlank()
                ? new MarkerEngine(processExecutor, engineManager)
                : new MarkerEngine(() -> Path.of(override), processExecutor, new MarkerCommandBuilder());
        engineExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("engine-worker-", 0).factory());

        backgroundExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual()
                .name("conversion-worker-", 0).factory());
        StartConversionUseCase startConversion = new StartConversionUseCase(
                new SimpleEngineRegistry(List.of(markerEngine)), backgroundExecutor);
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
                MarkerEngine.ID,
                settings,
                override != null && !override.isBlank() && java.nio.file.Files.isRegularFile(Path.of(override)));

        Scene scene = new Scene(controller.view(), 880, 900);
        scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
        stage.setTitle("ParseForge");
        stage.setMinWidth(700);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (cancelConversion != null) {
            cancelConversion.cancel();
        }
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        if (engineManager != null) engineManager.cancelCurrentOperation(MarkerEngine.ID);
        if (engineExecutor != null) engineExecutor.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private Path applicationDataDirectory() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "ParseForge");
        }
        return Path.of(System.getProperty("user.home"), ".parseforge");
    }

}
