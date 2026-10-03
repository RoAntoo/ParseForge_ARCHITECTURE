package dev.parseforge.presentation.javafx;

import dev.parseforge.application.port.out.*;
import dev.parseforge.application.settings.UserSettings;
import dev.parseforge.application.usecase.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.config.JsonUserSettingsRepository;
import dev.parseforge.presentation.javafx.controller.MainController;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in native JavaFX verification; fixtures exercise presentation without downloading engines. */
@EnabledIfSystemProperty(named = "parseforge.uiTests", matches = "true")
class Stage5UiTest {
    @TempDir Path temporary;
    private Stage stage;
    private MainController controller;
    private final EngineId id = new EngineId("marker");
    private final AtomicReference<EngineState> state = new AtomicReference<>(EngineState.NOT_INSTALLED);
    private final AtomicReference<EngineProgressListener> installationProgress = new AtomicReference<>();
    private final CountDownLatch installationRelease = new CountDownLatch(1);
    private final CountDownLatch conversionRelease = new CountDownLatch(1);
    private final AtomicReference<ConversionStatus> result = new AtomicReference<>(ConversionStatus.COMPLETED);
    private final ExecutorService engineExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService conversionExecutor = Executors.newSingleThreadExecutor();

    @Test void validatesOnboardingEngineLifecycleConversionAndSmallWindow() throws Exception {
        CountDownLatch toolkit = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); toolkit.countDown(); });
        assertTrue(toolkit.await(20, TimeUnit.SECONDS));
        var settings = new JsonUserSettingsRepository(temporary.resolve("config.json"));
        EngineManager manager = mock(EngineManager.class);
        when(manager.getState(id)).thenAnswer(ignored -> state.get());
        when(manager.check(id)).thenAnswer(ignored -> state.get());
        when(manager.getInstallation(id)).thenAnswer(ignored ->
                new EngineInstallation(id, new EngineVersion("1.0"), temporary, state.get()));
        doAnswer(invocation -> {
            state.set(EngineState.INSTALLING);
            EngineProgressListener listener = invocation.getArgument(2);
            installationProgress.set(listener);
            listener.onProgress(EngineInstallProgress.phase(EngineInstallProgress.Phase.PREPARING, "Preparando Marker"));
            assertTrue(installationRelease.await(20, TimeUnit.SECONDS));
            return null;
        }).when(manager).install(eq(id), any(EngineInstallOptions.class), any(EngineProgressListener.class));
        doAnswer(ignored -> { state.set(EngineState.NOT_INSTALLED); installationRelease.countDown(); return null; })
                .when(manager).cancelCurrentOperation(id);
        doAnswer(invocation -> {
            state.set(EngineState.READY);
            ((EngineProgressListener)invocation.getArgument(2)).onProgress(
                    EngineInstallProgress.phase(EngineInstallProgress.Phase.COMPLETED, "Marker está listo"));
            return null;
        }).when(manager).repair(eq(id), any(EngineInstallOptions.class), any(EngineProgressListener.class));
        ConversionEngine engine = mock(ConversionEngine.class);
        when(engine.descriptor()).thenReturn(new EngineDescriptor(id, "Marker", "1.0"));
        when(engine.convert(any(), any())).thenAnswer(invocation -> {
            ConversionEventListener listener = invocation.getArgument(1);
            listener.onEvent(new ConversionEvent.PhaseChanged("Procesando documento..."));
            assertTrue(conversionRelease.await(20, TimeUnit.SECONDS));
            return new ConversionResult(result.get(), 0, List.of(), null, null);
        });
        doAnswer(ignored -> { result.set(ConversionStatus.CANCELLED); conversionRelease.countDown(); return null; }).when(engine).cancel();
        EngineRegistry registry = mock(EngineRegistry.class); when(registry.require(id)).thenReturn(engine);
        var start = new StartConversionUseCase(registry, conversionExecutor);
        try {
            fx(() -> {
                stage = new Stage();
                controller = new MainController(stage, start, new CancelConversionUseCase(start), settings,
                        new InstallEngineUseCase(manager, engineExecutor), new RepairEngineUseCase(manager, engineExecutor),
                        new UninstallEngineUseCase(manager, engineExecutor), new CheckEngineStatusUseCase(manager, engineExecutor),
                        new CancelEngineOperationUseCase(manager), id, UserSettings.empty(), false, neutralPreflight());
                Scene scene = new Scene(controller.view(), 1100, 760);
                scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
                stage.setScene(scene); stage.setMinWidth(800); stage.setMinHeight(500); stage.show();
                controller.showWelcomeIfNeeded(); return null;
            });
            waitFor(() -> button("install-marker").isVisible() && !button("install-marker").isDisabled());
            fx(() -> {
                assertTrue(button("convert-pdf").isDisabled());
                assertEquals("ParseForge 0.1.0", label("app-version").getText());
                Stage welcome = welcome(); assertNotNull(welcome); snapshot(welcome.getScene(), "welcome");
                ((Button)welcome.getScene().lookup("#welcome-understood")).fire();
                assertEquals(0, settings.load().welcomeDialogVersion());
                controller.showWelcomeIfNeeded();
                welcome = welcome();
                ((CheckBox)welcome.getScene().lookup("#hide-welcome")).setSelected(true);
                ((Button)welcome.getScene().lookup("#welcome-understood")).fire();
                assertEquals(1, settings.load().welcomeDialogVersion());
                controller.showWelcomeIfNeeded(); assertNull(welcome());
                snapshot(stage.getScene(), "not-installed");
                button("install-marker").fire(); return null;
            });
            waitFor(() -> installationProgress.get() != null && label("engine-operation").getText().equals("Preparando Marker"));
            fx(() -> {
                assertTrue(button("convert-pdf").isDisabled());
                assertEquals(-1, ((ProgressBar)stage.getScene().lookup(".progress-bar")).getProgress());
                snapshot(stage.getScene(), "installing"); return null;
            });
            installationProgress.get().onProgress(new EngineInstallProgress(EngineInstallProgress.Phase.DOWNLOADING, "Descargando archivo", 100, 1000));
            waitFor(() -> ((ProgressBar)stage.getScene().lookup(".progress-bar")).getProgress() == 0.1);
            fx(() -> { button("cancel-installation").fire(); return null; });
            waitFor(() -> button("install-marker").isVisible() && !button("install-marker").isDisabled());
            state.set(EngineState.BROKEN);
            fx(() -> { button("check-engine-state").fire(); return null; });
            waitFor(() -> label("engine-state").getText().equals("Estado: Necesita reparación"));
            fx(() -> { assertFalse(button("repair-marker").isDisabled()); snapshot(stage.getScene(), "broken"); return null; });
            fx(() -> { button("repair-marker").fire(); return null; });
            waitFor(() -> label("engine-state").getText().equals("Estado: ✓ Listo") && !button("check-engine-state").isDisabled());
            Path pdf = temporary.resolve("documento-con-un-nombre-muy-largo-".repeat(4) + ".pdf");
            Files.writeString(pdf, "%PDF-1.4\nfixture");
            fx(() -> {
                assertTrue(button("convert-pdf").isDisabled());
                assertTrue(((RadioButton)stage.getScene().lookup("#marker-selection")).isSelected());
                ((RadioButton)stage.getScene().lookup("#marker-selection")).fire();
                assertTrue(((RadioButton)stage.getScene().lookup("#marker-selection")).isSelected());
                snapshot(stage.getScene(), "ready");
                button("expand-marker").fire();
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                var card = stage.getScene().lookup("#marker-card");
                assertTrue(card.getBoundsInLocal().getHeight() < 230, "La tarjeta debe conservar su altura natural");
                assertTrue(label("engine-state").localToScene(label("engine-state").getBoundsInLocal()).getMinY() >= 95,
                        "El estado del motor debe quedar visible debajo del encabezado");
                button("expand-marker").fire();
                controller.selectPdf(pdf); return null;
            });
            waitFor(() -> !button("convert-pdf").isDisabled());
            fx(() -> {
                assertFalse(button("convert-pdf").isDisabled());
                String longPath = temporary.resolve("carpeta-de-destino-".repeat(7)).toString();
                ((TextField)stage.getScene().lookup("#output-directory")).setText(longPath);
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                assertEquals(longPath, label("destination-path").getTooltip().getText());
                assertFalse(button("convert-pdf").isDisabled());
                assertEquals(1, settings.load().welcomeDialogVersion());
                snapshot(stage.getScene(), "selected-long-path");
                ((TextField)stage.getScene().lookup("#output-directory")).setText(pdf.toString());
                assertTrue(button("convert-pdf").isDisabled());
                controller.selectPdf(temporary.resolve("missing.pdf")); assertTrue(button("convert-pdf").isDisabled());
                snapshot(stage.getScene(), "invalid-pdf");
                controller.selectPdf(pdf);
                ((TextField)stage.getScene().lookup("#output-directory")).setText(temporary.resolve("output").toString());
                stage.setWidth(800); stage.setHeight(500); return null;
            });
            waitFor(() -> stage.getWidth() <= 801 && !button("convert-pdf").isDisabled());
            fx(() -> {
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                ScrollPane workspace = (ScrollPane)stage.getScene().lookup("#workspace-scroll");
                assertTrue(workspace.getContent().getLayoutBounds().getHeight() > workspace.getViewportBounds().getHeight());
                assertTrue(workspace.getContent().getLayoutBounds().getWidth() <= workspace.getViewportBounds().getWidth() + 1);
                assertTrue(button("convert-pdf").localToScene(button("convert-pdf").getBoundsInLocal()).getMaxY() < stage.getScene().getHeight());
                snapshot(stage.getScene(), "small-top");
                workspace.setVvalue(1); return null;
            });
            fx(() -> { snapshot(stage.getScene(), "small-bottom"); stage.setWidth(1100); stage.setHeight(780); button("convert-pdf").fire(); return null; });
            waitFor(() -> label("conversion-state").getText().equals("Procesando documento..."));
            fx(() -> {
                assertTrue(button("convert-pdf").isDisabled());
                assertTrue(button("select-pdf").isDisabled());
                assertTrue(button("change-output-directory").isDisabled());
                assertTrue(button("install-marker").isDisabled());
                snapshot(stage.getScene(), "converting"); button("cancel-conversion").fire(); return null;
            });
            waitFor(() -> label("conversion-state").getText().equals("Conversión cancelada"));
            fx(() -> { assertFalse(button("convert-pdf").isDisabled()); snapshot(stage.getScene(), "cancelled"); return null; });
            result.set(ConversionStatus.COMPLETED);
            fx(() -> { button("convert-pdf").fire(); return null; });
            waitFor(() -> label("conversion-state").getText().equals("Conversión completada"));
            fx(() -> {
                assertFalse(button("convert-pdf").isDisabled()); snapshot(stage.getScene(), "completed");
                System.out.println("UI_SCALE=" + stage.getOutputScaleX() + " x " + stage.getOutputScaleY());
                result.set(ConversionStatus.FAILED); button("convert-pdf").fire(); return null;
            });
            waitFor(() -> label("conversion-state").getText().equals("Conversión fallida") && Window.getWindows().size() > 1);
            fx(() -> {
                Stage error = (Stage)Window.getWindows().stream().filter(window -> window != stage && window.isShowing()).findFirst().orElseThrow();
                snapshot(error.getScene(), "conversion-error-dialog");
                ((Button)((DialogPane)error.getScene().lookup(".dialog-pane")).lookupButton(ButtonType.OK)).fire();
                assertFalse(button("convert-pdf").isDisabled()); snapshot(stage.getScene(), "conversion-error");
                verify(manager).repair(eq(id), any(EngineInstallOptions.class), any(EngineProgressListener.class));
                stage.setWidth(1920 / stage.getOutputScaleX()); stage.setHeight(1080 / stage.getOutputScaleY()); return null;
            });
            fx(() -> { snapshot(stage.getScene(), "1920x1080"); stage.setMaximized(true); return null; });
            waitFor(stage::isMaximized);
            fx(() -> { snapshot(stage.getScene(), "maximized"); stage.setMaximized(false); return null; });
            waitFor(() -> !stage.isMaximized());
            fx(() -> { stage.setWidth(1366); stage.setHeight(768); return null; });
            fx(() -> { snapshot(stage.getScene(), "1366x768"); System.out.println("UI_STAGE5_PASS"); return null;
            });
        } finally {
            installationRelease.countDown(); conversionRelease.countDown();
            engineExecutor.shutdownNow(); conversionExecutor.shutdownNow();
            fx(() -> { controller.close(); for (Window window : List.copyOf(Window.getWindows())) window.hide(); Platform.exit(); return null; });
        }
    }

    private AnalyzeDocumentUseCase neutralPreflight() {
        return new AnalyzeDocumentUseCase(file -> new DocumentPreflightResult(file, 100, 1, DocumentType.UNKNOWN, false, false, 0, 1, 0));
    }
    private Button button(String id) { return (Button)stage.getScene().lookup("#" + id); }
    private Label label(String id) { return (Label)stage.getScene().lookup("#" + id); }
    private Stage welcome() {
        return (Stage)Window.getWindows().stream().filter(window -> window != stage && window.isShowing()
                && window.getScene().lookup("#welcome-dialog") != null).findFirst().orElse(null);
    }
    private void snapshot(Scene scene, String name) throws Exception {
        scene.getRoot().applyCss(); scene.getRoot().layout();
        var image = scene.snapshot(null);
        var output = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        Path folder = Path.of("target", "stage5-ui", System.getProperty("glass.win.uiScale", "default"));
        Files.createDirectories(folder); ImageIO.write(output, "png", folder.resolve(name + ".png").toFile());
    }
    private void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!fx(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) fail("Timeout esperando el estado de la interfaz");
            Thread.sleep(30);
        }
    }
    private <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(25, TimeUnit.SECONDS);
    }
}
