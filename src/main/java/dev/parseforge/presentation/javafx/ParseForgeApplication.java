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
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class ParseForgeApplication extends Application {
    private ExecutorService backgroundExecutor;
    private CancelConversionUseCase cancelConversion;

    @Override
    public void start(Stage stage) {
        Path configDirectory = applicationDataDirectory();
        UserSettingsRepository settingsRepository = new JsonUserSettingsRepository(
                configDirectory.resolve("config.json"));
        UserSettings settings = settingsRepository.load();

        AtomicReference<Path> markerExecutable = new AtomicReference<>(pathOrNull(settings.markerExecutable()));
        LocalProcessExecutor processExecutor = new LocalProcessExecutor();
        MarkerEngine markerEngine = new MarkerEngine(
                markerExecutable::get, processExecutor, new MarkerCommandBuilder());

        backgroundExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual()
                .name("conversion-worker-", 0).factory());
        StartConversionUseCase startConversion = new StartConversionUseCase(
                new SimpleEngineRegistry(List.of(markerEngine)), backgroundExecutor);
        cancelConversion = new CancelConversionUseCase(startConversion);

        MainController controller = new MainController(
                stage,
                startConversion,
                cancelConversion,
                settingsRepository,
                markerExecutable::set,
                MarkerEngine.ID,
                settings);

        Scene scene = new Scene(controller.view(), 820, 720);
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

    private Path pathOrNull(String value) {
        return value == null || value.isBlank() ? null : Path.of(value);
    }
}
