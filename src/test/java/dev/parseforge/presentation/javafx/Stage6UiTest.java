package dev.parseforge.presentation.javafx;

import dev.parseforge.application.port.out.*;
import dev.parseforge.application.settings.*;
import dev.parseforge.application.usecase.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.config.JsonUserSettingsRepository;
import dev.parseforge.presentation.javafx.controller.MainController;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfSystemProperty(named = "parseforge.uiTests", matches = "true")
class Stage6UiTest {
    @TempDir Path temp;
    private Stage stage;
    private MainController controller;
    private final EngineId marker = new EngineId("marker"), md = new EngineId("markitdown");
    private final Map<EngineId, EngineState> states = new ConcurrentHashMap<>(Map.of(marker, EngineState.NOT_INSTALLED, md, EngineState.NOT_INSTALLED));
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicReference<ConversionRequest> converted = new AtomicReference<>();
    private EngineManager manager;
    private JsonUserSettingsRepository settings;
    private StartConversionUseCase start;

    @Test void settingsSelectionPersistenceFallbackKeyboardAndConversion() throws Exception {
        CountDownLatch toolkit = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); toolkit.countDown(); });
        assertTrue(toolkit.await(20, TimeUnit.SECONDS));
        settings = new JsonUserSettingsRepository(temp.resolve("config.json"));
        manager = mock(EngineManager.class);
        when(manager.check(any())).thenAnswer(i -> states.get(i.getArgument(0)));
        when(manager.getState(any())).thenAnswer(i -> states.get(i.getArgument(0)));
        when(manager.getInstallation(any())).thenAnswer(i -> new EngineInstallation(i.getArgument(0), new EngineVersion("0.1.8"), temp, states.get(i.getArgument(0))));
        doAnswer(i -> { states.put(i.getArgument(0), EngineState.READY); return null; })
                .when(manager).install(any(), any(EngineInstallOptions.class), any(EngineProgressListener.class));
        var registry = mock(EngineRegistry.class);
        for (var id : List.of(marker, md)) {
            var engine = mock(ConversionEngine.class);
            when(registry.require(id)).thenReturn(engine);
            when(engine.convert(any(), any())).thenAnswer(i -> {
                converted.set(i.getArgument(0));
                return new ConversionResult(ConversionStatus.COMPLETED, 0, List.of(), null, null);
            });
        }
        start = new StartConversionUseCase(registry, worker);
        try {
            open(UserSettings.empty());
            waitFor(() -> !button("check-engine-state").isDisabled() && !button("check-engine-state-markitdown").isDisabled());
            fx(() -> {
                assertTrue(button("convert-pdf").isDisabled());
                assertTrue(radio(marker).isDisabled()); assertTrue(radio(md).isDisabled());
                click(node("marker-card")); click(label("markitdown-capacity"));
                assertFalse(radio(marker).isSelected()); assertFalse(radio(md).isSelected());
                assertNull(stage.getScene().lookup("#marker-details")); assertNull(stage.getScene().lookup("#markitdown-details"));
                assertTrue(label("marker-capacity").getText().contains("Avanzado"));
                assertTrue(label("markitdown-capacity").getText().contains("Ligero"));
                assertTrue(label("markitdown-capacity").getTooltip().getText().contains("no es una puntuación de calidad"));
                assertTrue(node("marker-card").getBoundsInLocal().getHeight() < 260);
                snapshot("compact-both-not-installed");
                openSettings("marker"); assertTrue(node("marker-details").isVisible());
                openSettings("markitdown"); assertFalse(node("marker-details").isVisible());
                assertTrue(node("markitdown-details").isVisible());
                assertFalse(effectiveVisible(node("force-ocr")));
                assertTrue(button("install-markitdown").isVisible());
                snapshot("expanded-markitdown-not-installed"); button("install-markitdown").fire(); return null;
            });
            waitFor(() -> radio(md).isSelected() && !radio(md).isDisabled());
            Path pdf = Files.writeString(temp.resolve("PDF digital ñ con espacios.pdf"), "%PDF fixture");
            fx(() -> {
                button("close-settings").fire(); controller.selectPdf(pdf); return null;
            });
            waitFor(() -> !button("convert-pdf").isDisabled());
            fx(() -> {
                assertFalse(button("convert-pdf").isDisabled());
                assertEquals(md.value(), settings.load().selectedEngine());
                button("convert-pdf").fire(); return null;
            });
            waitFor(() -> converted.get() != null && !button("convert-pdf").isDisabled());
            assertEquals(md, converted.get().engineId()); assertFalse(converted.get().forceOcr());
            states.put(marker, EngineState.READY);
            fx(() -> { openSettings("marker"); button("check-engine-state").fire(); return null; });
            waitFor(() -> !radio(marker).isDisabled());
            fx(() -> {
                assertTrue(radio(md).isSelected()); assertFalse(radio(marker).isSelected());
                openSettings("marker"); assertFalse(node("markitdown-details").isVisible());
                assertTrue(node("marker-details").isVisible());
                assertTrue(radio(md).isSelected(), "Administrar Marker no debe cambiar el motor seleccionado");
                button("repair-marker").fire();
                assertTrue(radio(md).isSelected(), "Iniciar la reparación de Marker no debe cambiar el motor seleccionado");
                return null;
            });
            waitFor(() -> !button("repair-marker").isDisabled() && !button("check-engine-state").isDisabled());
            verify(manager).repair(eq(marker), any(EngineInstallOptions.class), any(EngineProgressListener.class));
            fx(() -> {
                assertTrue(radio(md).isSelected(), "Reparar Marker no debe cambiar el motor seleccionado");
                assertFalse(radio(marker).isSelected());
                button("close-settings").fire();
                click(node("marker-card").lookup(".engine-name"));
                assertTrue(radio(marker).isSelected()); assertFalse(radio(md).isSelected());
                assertTrue(node("marker-details").isVisible(), "Seleccionar no debe cambiar el panel de Ajustes");
                ((CheckBox)node("force-ocr")).setSelected(true);
                button("convert-pdf").fire(); return null;
            });
            waitFor(() -> converted.get().engineId().equals(marker) && !button("convert-pdf").isDisabled());
            assertTrue(converted.get().forceOcr());
            fx(() -> {
                click(node("markitdown-card")); assertTrue(radio(md).isSelected()); assertFalse(radio(marker).isSelected());
                click(label("marker-capacity")); assertTrue(radio(marker).isSelected());
                click(label("engine-state-markitdown")); assertTrue(radio(md).isSelected());
                radio(md).fire(); assertTrue(radio(md).isSelected(), "No se puede deseleccionar el único motor listo elegido");
                // Space opens settings; Enter selects a settings tab without changing conversion selection.
                stage.toFront(); stage.requestFocus(); button("open-settings").requestFocus();
                return null;
            });
            waitFor(() -> button("open-settings").isFocused());
            fx(() -> { key(button("open-settings"), KeyCode.SPACE); return null; });
            waitFor(() -> settingsPane.getScene().getWindow().isShowing());
            fx(() -> {
                assertFalse(node("marker-details").isVisible()); assertFalse(effectiveVisible(node("force-ocr")));
                key(node("settings-marker"), KeyCode.ENTER); assertTrue(node("marker-details").isVisible());
                assertTrue(radio(md).isSelected());
                key(node("settings-markitdown"), KeyCode.ENTER); assertTrue(node("markitdown-details").isVisible());
                node("settings-markitdown").requestFocus();
                key(node("settings-markitdown"), KeyCode.TAB);
                assertNotSame(node("settings-markitdown"), settingsPane.getScene().getFocusOwner());
                key(settingsPane.getScene().getFocusOwner(), KeyCode.TAB, true);
                assertSame(node("settings-markitdown"), settingsPane.getScene().getFocusOwner());
                button("close-settings").fire();
                snapshot("both-ready-markitdown-selected"); stage.setWidth(800); stage.setHeight(500); return null;
            });
            fx(() -> {
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                ScrollPane sidebar = (ScrollPane)node("engine-scroll"), workspace = (ScrollPane)node("workspace-scroll");
                assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, sidebar.getVbarPolicy());
                assertTrue(workspace.getContent().getLayoutBounds().getHeight() > workspace.getViewportBounds().getHeight());
                sidebar.setVvalue(1); workspace.setVvalue(1); snapshot("minimum-window-scrolled");
                assertTrue(button("convert-pdf").localToScene(button("convert-pdf").getBoundsInLocal()).getMaxY() < stage.getScene().getHeight());
                return null;
            });
            // Restore preferred MarkItDown only after both asynchronous checks complete.
            open(settings.load()); waitFor(() -> radio(md).isSelected());
            fx(() -> { assertFalse(radio(marker).isSelected()); snapshot("restart-selection"); return null; });
            states.put(md, EngineState.BROKEN);
            open(settings.load()); waitFor(() -> radio(marker).isSelected());
            assertEquals(marker.value(), settings.load().selectedEngine());
            states.put(marker, EngineState.NOT_INSTALLED);
            open(settings.load()); waitFor(() -> !button("check-engine-state").isDisabled());
            fx(() -> {
                assertFalse(radio(marker).isSelected()); assertFalse(radio(md).isSelected()); assertTrue(button("convert-pdf").isDisabled());
                snapshot("no-ready-fallback"); System.out.println("UI_STAGE6_PASS scale=" + stage.getOutputScaleX()); return null;
            });
        } finally {
            worker.shutdownNow();
            fx(() -> { controller.close(); stage.hide(); Platform.exit(); return null; });
        }
    }
    private void open(UserSettings initial) throws Exception {
        fx(() -> {
            if (stage != null) { controller.close(); stage.hide(); } stage = new Stage();
            controller = new MainController(stage, start, new CancelConversionUseCase(start), settings,
                    new InstallEngineUseCase(manager, worker), new RepairEngineUseCase(manager, worker), new UninstallEngineUseCase(manager, worker),
                    new CheckEngineStatusUseCase(manager, worker), new CancelEngineOperationUseCase(manager),
                    List.of(EngineProfile.marker(), EngineProfile.markItDown(193538258)), initial, false, neutralPreflight());
            Scene scene = new Scene(controller.view(), 1100, 760); scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            stage.setScene(scene); stage.show();
            button("open-settings").fire();
            settingsPane = (DialogPane)javafx.stage.Window.getWindows().stream()
                    .filter(w -> w != stage && w.isShowing()).findFirst().orElseThrow().getScene().lookup("#engine-settings-dialog");
            button("close-settings").fire(); return null;
        });
    }
    private DialogPane settingsPane;
    private void openSettings(String id) {
        button("open-settings").fire(); ((ToggleButton)node("settings-" + id)).fire();
    }
    private Node node(String id) {
        var home = stage.getScene().lookup("#" + id);
        return home != null ? home : settingsPane == null ? null : settingsPane.lookup("#" + id);
    }
    private AnalyzeDocumentUseCase neutralPreflight() {
        return new AnalyzeDocumentUseCase(file -> new DocumentPreflightResult(file, 100, 1, DocumentType.UNKNOWN, false, false, 0, 1, 0));
    }
    private Button button(String id) { return (Button)node(id); }
    private Label label(String id) { return (Label)node(id); }
    private RadioButton radio(EngineId id) { return (RadioButton)node(id + "-selection"); }
    private boolean effectiveVisible(Node node) { for (Node n = node; n != null; n = n.getParent()) if (!n.isVisible()) return false; return true; }
    private void click(Node target) {
        javafx.event.Event.fireEvent(target, new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, false, false, false, true, false, true,
                new javafx.scene.input.PickResult(target, 0, 0)));
    }
    private void key(Node target, KeyCode key) {
        key(target, key, false);
    }
    private void key(Node target, KeyCode key, boolean shift) {
        javafx.event.Event.fireEvent(target, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", key, shift, false, false, false));
        javafx.event.Event.fireEvent(target, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_RELEASED, "", "", key, shift, false, false, false));
    }
    private void snapshot(String name) throws Exception {
        stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); var image = stage.getScene().snapshot(null);
        var output = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        Path folder = Path.of("build/stage6-ui", System.getProperty("glass.win.uiScale", "default"));
        Files.createDirectories(folder); ImageIO.write(output, "png", folder.resolve(name + ".png").toFile());
    }
    private void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!fx(condition::getAsBoolean)) { if (System.nanoTime() > deadline) fail("Timeout de UI"); Thread.sleep(30); }
    }
    private <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(25, TimeUnit.SECONDS);
    }
}
