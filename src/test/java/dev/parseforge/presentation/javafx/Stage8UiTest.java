package dev.parseforge.presentation.javafx;

import dev.parseforge.application.port.out.*;
import dev.parseforge.application.settings.*;
import dev.parseforge.application.usecase.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.config.JsonUserSettingsRepository;
import dev.parseforge.infrastructure.document.*;
import dev.parseforge.presentation.javafx.controller.MainController;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Native UI with real PDFBox fixtures and controlled engine operations; never downloads runtimes. */
@EnabledIfSystemProperty(named = "parseforge.uiTests", matches = "true")
class Stage8UiTest {
    @TempDir Path temp;
    private Stage stage;
    private MainController controller;
    private DialogPane settings;
    private final EngineId marker = new EngineId("marker"), md = new EngineId("markitdown");
    private final Map<EngineId, EngineState> states = new ConcurrentHashMap<>();
    private final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);

    @Test void homeSettingsActionsContrastKeyboardAndResponsiveScreenshots() throws Exception {
        states.put(marker, EngineState.READY); states.put(md, EngineState.READY);
        Path digital = PdfFixtures.create(temp.resolve("informe-digital.pdf"), "text", "text");
        Path scanned = PdfFixtures.create(temp.resolve("documento-escaneado.pdf"), "image", "image");
        var worker = Executors.newSingleThreadExecutor();
        var manager = mock(EngineManager.class);
        when(manager.getState(any())).thenAnswer(i -> states.get(i.getArgument(0)));
        when(manager.check(any())).thenAnswer(i -> states.get(i.getArgument(0)));
        when(manager.getInstallation(any())).thenAnswer(i -> new EngineInstallation(i.getArgument(0), new EngineVersion("fixture"), temp, states.get(i.getArgument(0))));
        doAnswer(i -> { states.put(i.getArgument(0), EngineState.READY); return null; })
                .when(manager).install(any(), any(EngineInstallOptions.class), any(EngineProgressListener.class));
        var engine = mock(ConversionEngine.class);
        when(engine.convert(any(), any())).thenAnswer(i -> {
            ((ConversionEventListener)i.getArgument(1)).onEvent(new ConversionEvent.PhaseChanged("Procesando documento…"));
            entered.countDown(); assertTrue(release.await(25, TimeUnit.SECONDS));
            return new ConversionResult(ConversionStatus.COMPLETED, 0, List.of(temp.resolve("documento-escaneado.md")), null, java.time.Duration.ofSeconds(3));
        });
        var start = new StartConversionUseCase(id -> engine, worker);
        CountDownLatch toolkit = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); toolkit.countDown(); });
        assertTrue(toolkit.await(20, TimeUnit.SECONDS));
        try {
            fx(() -> {
                stage = new Stage(); controller = new MainController(stage, start, new CancelConversionUseCase(start),
                        new JsonUserSettingsRepository(temp.resolve("settings.json")), new InstallEngineUseCase(manager, worker),
                        new RepairEngineUseCase(manager, worker), new UninstallEngineUseCase(manager, worker),
                        new CheckEngineStatusUseCase(manager, worker), new CancelEngineOperationUseCase(manager),
                        List.of(EngineProfile.marker(), EngineProfile.markItDown(193538258)),
                        new UserSettings("", temp.toString(), "", temp.toString(), "es", "marker", 1), false,
                        new AnalyzeDocumentUseCase(new PdfBoxDocumentPreflight()));
                Scene scene = new Scene(controller.view(), 1100, 760);
                scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
                stage.setScene(scene); stage.show(); return null;
            });
            waitFor(() -> radio("marker").isSelected());
            fx(() -> {
                assertNull(home("repair-marker")); assertNull(home("uninstall-marker")); assertNull(home("marker-details"));
                assertTrue(button("convert-pdf").isDisabled()); assertTrue(button("open-settings").isVisible());
                assertTrue(radio("marker").isSelected()); assertFalse(radio("markitdown").isSelected());
                layout(); assertContrast(button("convert-pdf")); snapshot(stage.getScene(), "home-empty");
                openSettings("marker");
                assertTrue(node("marker-details").isVisible()); assertFalse(node("markitdown-details").isVisible());
                assertTrue(button("repair-marker").isVisible()); assertTrue(button("uninstall-marker").isVisible());
                assertFalse(button("install-marker").isVisible()); snapshot(settings.getScene(), "settings-marker");
                key(node("settings-markitdown"), KeyCode.ENTER);
                assertTrue(node("markitdown-details").isVisible()); assertTrue(radio("marker").isSelected());
                snapshot(settings.getScene(), "settings-markitdown");
                button("close-settings").fire(); controller.selectPdf(digital); return null;
            });
            waitFor(() -> !button("convert-pdf").isDisabled());
            fx(() -> {
                radio("markitdown").requestFocus(); key(radio("markitdown"), KeyCode.ENTER);
                assertTrue(radio("markitdown").isSelected()); assertFalse(home("force-ocr").isVisible());
                assertEquals("Convertir a Markdown", button("convert-pdf").getText());
                layout(); assertEquals(Color.web("#D92535"), background(button("convert-pdf")));
                assertContrast(button("convert-pdf")); snapshot(stage.getScene(), "home-digital-markitdown");
                for (String state : List.of("hover", "pressed", "focused")) {
                    button("convert-pdf").pseudoClassStateChanged(PseudoClass.getPseudoClass(state), true);
                    layout(); assertContrast(button("convert-pdf"));
                    if (state.equals("focused")) {
                        assertEquals(Color.web("#D92535"), background(button("convert-pdf")));
                        assertEquals(Color.web("#2466a8"), button("convert-pdf").getBorder().getStrokes().getFirst().getTopStroke());
                    }
                    snapshot(stage.getScene(), "cta-" + state);
                    button("convert-pdf").pseudoClassStateChanged(PseudoClass.getPseudoClass(state), false);
                }
                controller.selectPdf(scanned); return null;
            });
            waitFor(() -> label("document-summary").getText().contains("PDF escaneado"));
            fx(() -> {
                assertTrue(button("convert-pdf").isDisabled()); layout();
                assertNotEquals(Color.web("#D92535"), background(button("convert-pdf"))); assertContrast(button("convert-pdf"));
                snapshot(stage.getScene(), "scanned-markitdown-disabled");
                key(button("use-suggested-engine"), KeyCode.ENTER);
                assertTrue(radio("marker").isSelected()); assertTrue(home("force-ocr").isVisible());
                snapshot(stage.getScene(), "home-scanned-marker");
                button("open-settings").requestFocus(); key(button("open-settings"), KeyCode.TAB);
                assertNotSame(button("open-settings"), stage.getScene().getFocusOwner());
                key(stage.getScene().getFocusOwner(), KeyCode.TAB, true);
                assertSame(button("open-settings"), stage.getScene().getFocusOwner());
                openSettings("markitdown"); states.put(md, EngineState.BROKEN); button("check-engine-state-markitdown").fire(); return null;
            });
            waitFor(() -> label("engine-state-markitdown").getText().contains("ERROR"));
            fx(() -> {
                assertTrue(button("repair-markitdown").isVisible()); assertTrue(button("uninstall-markitdown").isVisible());
                snapshot(settings.getScene(), "settings-broken");
                // Destructive action opens a confirmation, cancelling never calls uninstall.
                Platform.runLater(() -> {
                    DialogPane confirmation = (DialogPane)Window.getWindows().stream()
                            .filter(w -> w != stage && w != settings.getScene().getWindow() && w.isShowing())
                            .findFirst().orElseThrow().getScene().lookup(".dialog-pane");
                    ((Button)confirmation.lookupButton(ButtonType.CANCEL)).fire();
                });
                button("uninstall-markitdown").fire();
                verify(manager, never()).uninstall(any(), any());
                states.put(md, EngineState.NOT_INSTALLED); button("check-engine-state-markitdown").fire(); return null;
            });
            waitFor(() -> label("engine-state-markitdown").getText().equals("NO INSTALADO"));
            fx(() -> {
                assertTrue(button("install-markitdown").isVisible()); assertFalse(button("repair-markitdown").isVisible());
                assertFalse(button("uninstall-markitdown").isVisible()); assertFalse(button("check-engine-state-markitdown").isVisible());
                snapshot(settings.getScene(), "settings-not-installed"); button("close-settings").fire();
                assertFalse(button("attention-markitdown").isManaged());
                snapshot(stage.getScene(), "engine-not-installed");
                key(button("quick-install-markitdown"), KeyCode.ENTER); return null;
            });
            waitFor(() -> label("engine-state-markitdown").getText().equals("✓ LISTO"));
            fx(() -> {
                assertTrue(settings.getScene().getWindow().isShowing()); button("close-settings").fire();
                verify(manager).install(eq(md), any(EngineInstallOptions.class), any(EngineProgressListener.class));
                stage.setWidth(800); stage.setHeight(500);
                String path = temp.resolve("carpeta-larga-".repeat(10)).toString();
                ((TextField)home("output-directory")).setText(path);
                assertEquals(path, label("destination-path").getTooltip().getText());
                return null;
            });
            fx(() -> {
                layout(); ScrollPane scroll = (ScrollPane)home("workspace-scroll");
                assertTrue(scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth() + 1);
                scroll.setVvalue(1); layout();
                var bounds = button("convert-pdf").localToScene(button("convert-pdf").getBoundsInLocal());
                assertTrue(bounds.getMinY() > 0 && bounds.getMaxY() < stage.getScene().getHeight());
                snapshot(stage.getScene(), "small-window");
                openSettings("marker"); return null;
            });
            fx(() -> {
                assertTrue(settings.getScene().getWindow().getHeight() <= stage.getHeight());
                snapshot(settings.getScene(), "small-settings"); button("close-settings").fire();
                key(button("convert-pdf"), KeyCode.ENTER); return null;
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            fx(() -> {
                assertFalse(button("convert-pdf").isVisible()); assertTrue(button("cancel-conversion").isVisible());
                assertFalse(button("cancel-conversion").isDisabled());
                layout(); var bounds = button("cancel-conversion").localToScene(button("cancel-conversion").getBoundsInLocal());
                assertTrue(bounds.getMinY() > 0 && bounds.getMaxY() < stage.getScene().getHeight());
                assertNotEquals(Color.web("#D92535"), background(button("cancel-conversion")));
                snapshot(stage.getScene(), "conversion-in-progress"); return null;
            });
            release.countDown(); waitFor(() -> label("conversion-state").getText().equals("Conversión completada"));
            fx(() -> {
                assertTrue(button("convert-pdf").isVisible()); stage.setWidth(1100); stage.setHeight(760); return null;
            });
            waitFor(() -> stage.getScene().getWidth() > 1000 && stage.getScene().getHeight() > 650);
            fx(() -> {
                ((ScrollPane)home("workspace-scroll")).setVvalue(1);
                snapshot(stage.getScene(), "conversion-completed");
                System.out.println("UI_STAGE8_PASS scale=" + stage.getOutputScaleX()); return null;
            });
        } finally {
            release.countDown(); worker.shutdownNow();
            fx(() -> { controller.close(); stage.hide(); Platform.exit(); return null; });
        }
    }
    private Node home(String id) { return stage.getScene().lookup("#" + id); }
    private Node node(String id) { Node node = home(id); return node != null ? node : settings.lookup("#" + id); }
    private Button button(String id) { return (Button)node(id); }
    private Label label(String id) { return (Label)node(id); }
    private RadioButton radio(String id) { return (RadioButton)home(id + "-selection"); }
    private void openSettings(String id) {
        button("open-settings").fire();
        settings = (DialogPane)Window.getWindows().stream().filter(w -> w != stage && w.isShowing())
                .findFirst().orElseThrow().getScene().lookup("#engine-settings-dialog");
        ((ToggleButton)node("settings-" + id)).fire();
    }
    private void key(Node target, KeyCode key) { key(target, key, false); }
    private void key(Node target, KeyCode key, boolean shift) {
        javafx.event.Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, shift, false, false, false));
        javafx.event.Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", key, shift, false, false, false));
    }
    private void layout() { controller.view().applyCss(); controller.view().layout(); }
    private Color background(Button button) { return (Color)button.getBackground().getFills().getFirst().getFill(); }
    private void assertContrast(Button button) {
        if (!button.isDisabled()) {
            assertEquals(Color.WHITE, button.getTextFill(), "Active CTA uses the requested white lettering");
        }
        double a = luminance(background(button)), b = luminance((Color)button.getTextFill());
        assertTrue((Math.max(a,b)+.05)/(Math.min(a,b)+.05) >= 4.5, "CTA text contrast must be >= 4.5:1: " + background(button) + " / " + button.getTextFill() + " ratio=" + (Math.max(a,b)+.05)/(Math.min(a,b)+.05));
    }
    private double luminance(Color color) { return .2126*linear(color.getRed())+.7152*linear(color.getGreen())+.0722*linear(color.getBlue()); }
    private double linear(double value) { return value <= .04045 ? value/12.92 : Math.pow((value+.055)/1.055, 2.4); }
    private void snapshot(Scene scene, String name) throws Exception {
        scene.getRoot().applyCss(); scene.getRoot().layout(); var image = scene.snapshot(null);
        var output = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<output.getHeight(); y++) for (int x=0; x<output.getWidth(); x++) output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        Path folder = Path.of("build/stage8-ui", System.getProperty("glass.win.uiScale", "default")); Files.createDirectories(folder);
        ImageIO.write(output, "png", folder.resolve(name + ".png").toFile());
    }
    private void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!fx(condition::getAsBoolean)) { if (System.nanoTime() > deadline) fail("UI timeout"); Thread.sleep(30); }
    }
    private <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(25, TimeUnit.SECONDS);
    }
}
