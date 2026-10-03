package dev.parseforge.presentation.javafx;

import dev.parseforge.application.port.out.*;
import dev.parseforge.application.settings.*;
import dev.parseforge.application.usecase.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.config.JsonUserSettingsRepository;
import dev.parseforge.infrastructure.document.*;
import dev.parseforge.presentation.javafx.controller.MainController;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.robot.Robot;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfSystemProperty(named = "parseforge.uiTests", matches = "true")
class Stage7UiTest {
    @TempDir Path temp;
    private Stage stage;
    private MainController controller;
    private final EngineId marker = new EngineId("marker"), md = new EngineId("markitdown");
    private final CountDownLatch staleEntered = new CountDownLatch(1), staleRelease = new CountDownLatch(1);
    private final CountDownLatch engineEntered = new CountDownLatch(1), engineRelease = new CountDownLatch(1);
    private final AtomicBoolean cancelled = new AtomicBoolean(), fail = new AtomicBoolean();

    @Test void preflightRecommendationsRacesDropLongConversionAndLayout() throws Exception {
        Path digital = PdfFixtures.create(temp.resolve("documento ñ 文 con espacios.pdf"), "text", "text");
        Path scan = PdfFixtures.create(temp.resolve("libro-escaneado-".repeat(9) + ".pdf"), "image");
        Path mixed = PdfFixtures.create(temp.resolve("mixed.pdf"), "text", "image");
        Path blank = PdfFixtures.create(temp.resolve("blank.pdf"), "blank");
        String[] pages = new String[240]; Arrays.fill(pages, "image");
        Path longScan = PdfFixtures.create(temp.resolve("long.pdf"), pages);
        Path stale = Files.copy(digital, temp.resolve("stale.pdf"));
        Path unavailable = Files.copy(digital, temp.resolve("unavailable.pdf"));
        Path corrupt = Files.writeString(temp.resolve("corrupt.pdf"), "%PDF-1.7 broken");
        CountDownLatch toolkit = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); toolkit.countDown(); });
        assertTrue(toolkit.await(20, TimeUnit.SECONDS));
        var worker = Executors.newSingleThreadExecutor();
        var manager = mock(EngineManager.class);
        when(manager.check(any())).thenReturn(EngineState.READY);
        when(manager.getState(any())).thenReturn(EngineState.READY);
        when(manager.getInstallation(any())).thenAnswer(i -> new EngineInstallation(i.getArgument(0), new EngineVersion("test"), temp, EngineState.READY));
        var engine = mock(ConversionEngine.class);
        when(engine.convert(any(), any())).thenAnswer(i -> {
            ((ConversionEventListener)i.getArgument(1)).onEvent(new ConversionEvent.PhaseChanged("Procesando documento con Marker..."));
            engineEntered.countDown(); assertTrue(engineRelease.await(20, TimeUnit.SECONDS));
            return new ConversionResult(cancelled.get() ? ConversionStatus.CANCELLED : fail.get() ? ConversionStatus.FAILED : ConversionStatus.COMPLETED,
                    0, List.of(temp.resolve("generated.md")), fail.get() ? "Traceback internal detail" : null, java.time.Duration.ofSeconds(2));
        });
        doAnswer(i -> { cancelled.set(true); engineRelease.countDown(); return null; }).when(engine).cancel();
        var start = new StartConversionUseCase(id -> engine, worker);
        var staleResult = new CompletableFuture<DocumentPreflightResult>();
        var staleCallbackQueued = new CountDownLatch(1);
        var preflight = spy(new AnalyzeDocumentUseCase(file -> {
            if (file.equals(stale)) {
                staleEntered.countDown();
                while (staleRelease.getCount() > 0) try { staleRelease.await(); } catch (InterruptedException ignored) { }
            }
            if (file.equals(unavailable)) throw new IllegalStateException("unsupported inspection edge case");
            return new PdfBoxDocumentPreflight().inspect(file);
        }));
        doAnswer(invocation -> {
            var actual = (CompletableFuture<DocumentPreflightResult>) invocation.callRealMethod();
            actual.whenComplete((result, error) -> {
                if (error == null) staleResult.complete(result);
                else staleResult.completeExceptionally(error);
                // complete() runs the controller's registered callback, which enqueues its FX update.
                staleCallbackQueued.countDown();
            });
            return staleResult;
        }).when(preflight).analyze(stale);
        try {
            fx(() -> {
                stage = new Stage(); controller = new MainController(stage, start, new CancelConversionUseCase(start),
                        new JsonUserSettingsRepository(temp.resolve("settings.json")), new InstallEngineUseCase(manager, worker),
                        new RepairEngineUseCase(manager, worker), new UninstallEngineUseCase(manager, worker),
                        new CheckEngineStatusUseCase(manager, worker), new CancelEngineOperationUseCase(manager),
                        List.of(EngineProfile.marker(), EngineProfile.markItDown(193538258)),
                        new UserSettings("", temp.toString(), "", temp.toString(), "es", "marker", 1), false, preflight);
                var scene = new Scene(controller.view(), 1100, 760);
                scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm()); stage.setScene(scene); stage.show(); return null;
            });
            waitFor(() -> radio(marker).isSelected());
            choose(digital, "PDF digital");
            fx(() -> {
                assertTrue(label("document-summary").getText().contains("2 páginas"));
                assertTrue(label("document-summary").getText().contains("KB"));
                assertTrue(radio(marker).isSelected()); assertTrue(button("use-suggested-engine").isVisible());
                button("use-suggested-engine").fire(); assertTrue(radio(md).isSelected()); snapshot("digital"); return null;
            });
            choose(scan, "PDF escaneado");
            fx(() -> {
                assertTrue(radio(md).isSelected()); assertTrue(button("convert-pdf").isDisabled());
                assertTrue(label("document-advice").getText().contains("OCR"));
                assertEquals(scan.toString(), label("selected-pdf").getTooltip().getText());
                button("use-suggested-engine").fire(); assertTrue(radio(marker).isSelected());
                assertFalse(((CheckBox)node("force-ocr")).isSelected()); snapshot("scanned"); return null;
            });
            choose(mixed, "PDF mixto"); choose(blank, "Tipo indeterminado");
            fx(() -> { controller.selectPdf(stale); assertEquals("Analizando documento...", label("document-summary").getText()); return null; });
            assertTrue(staleEntered.await(5, TimeUnit.SECONDS));
            fx(() -> { controller.selectPdf(scan); return null; });
            waitFor(() -> label("document-summary").getText().contains("PDF escaneado"));
            staleRelease.countDown();
            assertTrue(staleCallbackQueued.await(5, TimeUnit.SECONDS));
            assertEquals(stale, staleResult.join().file());
            // This FX assertion is queued after the stale update, so it observes the state it leaves behind.
            fx(() -> {
                assertEquals(scan.getFileName().toString(), label("selected-pdf").getText());
                assertTrue(label("document-summary").getText().contains("PDF escaneado"));
                return null;
            });
            choose(unavailable, "Análisis previo no disponible");
            fx(() -> { assertFalse(button("convert-pdf").isDisabled()); return null; });
            choose(corrupt, "Análisis previo no disponible");
            fx(() -> { assertFalse(button("convert-pdf").isDisabled()); assertTrue(label("document-advice").getText().contains("Podés intentar convertirlo")); return null; });
            dragPdf(digital);
            waitFor(() -> label("document-summary").getText().contains("PDF digital"));
            choose(longScan, "PDF escaneado");
            fx(() -> {
                assertTrue(label("document-warning").getText().contains("Documento largo"));
                assertTrue(label("document-summary").getText().contains("240 páginas"));
                assertTrue(label("document-summary").getText().contains("Muestra de 20"));
                stage.setWidth(800); stage.setHeight(500); return null;
            });
            fx(() -> {
                layout(); var scroll = (ScrollPane)node("workspace-scroll");
                assertTrue(scroll.getContent().getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight());
                assertTrue(scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth() + 1);
                scroll.setVvalue(.65); snapshot("small-preflight"); button("convert-pdf").fire(); return null;
            });
            assertTrue(engineEntered.await(5, TimeUnit.SECONDS));
            waitFor(() -> !label("conversion-elapsed").getText().endsWith("00:00:00"));
            fx(() -> {
                assertEquals("Motor: Marker", label("conversion-engine").getText());
                assertTrue(label("conversion-advice").getText().contains("OCR"));
                assertTrue(((ProgressIndicator)node("cancel-conversion").getParent().lookup(".progress-indicator")).isIndeterminate());
                layout(); assertTrue(button("cancel-conversion").localToScene(button("cancel-conversion").getBoundsInLocal()).getMaxY() < stage.getScene().getHeight());
                snapshot("small-converting"); button("cancel-conversion").fire(); return null;
            });
            waitFor(() -> label("conversion-state").getText().equals("Conversión cancelada"));
            fx(() -> { assertFalse(button("convert-pdf").isDisabled()); cancelled.set(false); button("convert-pdf").fire(); return null; });
            waitFor(() -> label("conversion-state").getText().equals("Conversión completada"));
            fx(() -> { assertTrue(label("conversion-output").getText().contains("generated.md")); fail.set(true); button("convert-pdf").fire(); return null; });
            waitFor(() -> Window.getWindows().size() > 1);
            fx(() -> {
                Stage dialog = (Stage)Window.getWindows().stream().filter(w -> w != stage && w.isShowing()).findFirst().orElseThrow();
                var pane = (DialogPane)dialog.getScene().lookup(".dialog-pane"); assertFalse(pane.getContentText().contains("Traceback"));
                ((Button)pane.lookupButton(ButtonType.OK)).fire();
                stage.setWidth(1100); stage.setHeight(760); ((ScrollPane)node("workspace-scroll")).setVvalue(.7);
                snapshot("long-scanned"); System.out.println("UI_STAGE7_PASS scale=" + stage.getOutputScaleX()); return null;
            });
        } finally {
            staleRelease.countDown(); engineRelease.countDown(); worker.shutdownNow();
            fx(() -> { controller.close(); for (var window : List.copyOf(Window.getWindows())) window.hide(); Platform.exit(); return null; });
        }
    }
    private void choose(Path file, String text) throws Exception {
        fx(() -> { controller.selectPdf(file); return null; }); waitFor(() -> label("document-summary").getText().contains(text));
    }
    private void dragPdf(Path file) throws Exception {
        fx(() -> {
            // Exercise the JavaFX drop handler with the same file payload as an OS dragboard.
            var board = mock(Dragboard.class);
            when(board.hasFiles()).thenReturn(true); when(board.getFiles()).thenReturn(List.of(file.toFile()));
            when(board.getTransferModes()).thenReturn(Set.of(TransferMode.COPY));
            var drop = node("pdf-drop-zone");
            var event = new DragEvent(DragEvent.DRAG_DROPPED, board, 5, 5, 5, 5,
                    TransferMode.COPY, new Object(), drop, new PickResult(drop, 5, 5));
            javafx.event.Event.fireEvent(drop, event);
            assertEquals(file.getFileName().toString(), label("selected-pdf").getText()); return null;
        });
    }
    private Node node(String id) { return stage.getScene().lookup("#" + id); }
    private Label label(String id) { return (Label)node(id); }
    private Button button(String id) { return (Button)node(id); }
    private RadioButton radio(EngineId id) { return (RadioButton)node(id + "-selection"); }
    private void layout() { controller.view().applyCss(); controller.view().layout(); }
    private void snapshot(String name) throws Exception {
        layout(); var image = stage.getScene().snapshot(null);
        var output = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<output.getHeight(); y++) for (int x=0; x<output.getWidth(); x++) output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        Path folder = Path.of("build/stage7-ui", System.getProperty("glass.win.uiScale", "default")); Files.createDirectories(folder);
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
