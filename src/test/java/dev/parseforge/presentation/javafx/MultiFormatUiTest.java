package dev.parseforge.presentation.javafx;

import dev.parseforge.application.port.out.*;
import dev.parseforge.application.settings.*;
import dev.parseforge.application.usecase.*;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.config.JsonUserSettingsRepository;
import dev.parseforge.presentation.javafx.controller.MainController;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "parseforge.uiTests", matches = "true")
class MultiFormatUiTest {
    @TempDir Path temp;
    @Test void selectsDocumentsWithoutPdfAnalysisAndOffersCompatibleEngine() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        var manager = mock(EngineManager.class);
        when(manager.getState(any())).thenReturn(EngineState.READY);
        when(manager.check(any())).thenReturn(EngineState.READY);
        when(manager.getInstallation(any())).thenAnswer(i -> new EngineInstallation(i.getArgument(0), new EngineVersion("fixture"), temp, EngineState.READY));
        var preflight = mock(DocumentPreflightService.class);
        var engine = mock(ConversionEngine.class);
        var start = new StartConversionUseCase(id -> engine, worker);
        var toolkit = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); toolkit.countDown(); });
        assertTrue(toolkit.await(20, TimeUnit.SECONDS));
        Stage[] stage = new Stage[1]; MainController[] controller = new MainController[1];
        try {
            fx(() -> {
                stage[0] = new Stage();
                controller[0] = new MainController(stage[0], start, new CancelConversionUseCase(start),
                    new JsonUserSettingsRepository(temp.resolve("settings.json")), new InstallEngineUseCase(manager, worker),
                    new RepairEngineUseCase(manager, worker), new UninstallEngineUseCase(manager, worker),
                    new CheckEngineStatusUseCase(manager, worker), new CancelEngineOperationUseCase(manager),
                    List.of(EngineProfile.marker(), EngineProfile.markItDown(244891991)),
                    new UserSettings("", temp.toString(), "", temp.toString(), "es", "marker", 1), false,
                    new AnalyzeDocumentUseCase(preflight));
                var scene = new Scene(controller[0].view(), 1100, 760);
                scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
                stage[0].setScene(scene); stage[0].show(); return null;
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!fx(() -> ((RadioButton)controller[0].view().lookup("#marker-selection")).isSelected())) {
                if (System.nanoTime() > deadline) fail("Engine selection timeout");
                Thread.sleep(30);
            }
            fx(() -> {
                var root = controller[0].view();
                Button convert = (Button)root.lookup("#convert-pdf");
                Button suggested = (Button)root.lookup("#use-suggested-engine");
                Path word = Files.writeString(temp.resolve("documento.DOCX"), "fixture");
                controller[0].selectDocument(word);
                assertTrue(convert.isDisabled());
                assertTrue(suggested.isVisible()); assertEquals("Usar MarkItDown", suggested.getText());
                suggested.fire(); assertFalse(convert.isDisabled());
                root.applyCss(); root.layout();
                var screenshot = stage[0].getScene().snapshot(null);
                var bitmap = new java.awt.image.BufferedImage((int)screenshot.getWidth(), (int)screenshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
                    bitmap.setRGB(x, y, screenshot.getPixelReader().getArgb(x, y));
                Path screenshots = Files.createDirectories(Path.of("build/multiformat/ui"));
                javax.imageio.ImageIO.write(bitmap, "png", screenshots.resolve("word-selected.png").toFile());
                assertFalse(root.lookup("#force-ocr").isVisible());
                for (String ext : List.of("epub", "pptx", "xlsx", "html", "txt", "zip")) {
                    controller[0].selectDocument(Files.writeString(temp.resolve("documento." + ext), "fixture"));
                    assertFalse(convert.isDisabled(), ext);
                }
                controller[0].selectDocument(Files.writeString(temp.resolve("foto.PNG"), "fixture"));
                assertTrue(convert.isDisabled()); assertEquals("Usar Marker", suggested.getText());
                suggested.fire(); assertFalse(convert.isDisabled());
                assertTrue(root.lookup("#force-ocr").isVisible());
                controller[0].selectDocument(Files.writeString(temp.resolve("archivo.exe"), "fixture"));
                assertTrue(convert.isDisabled());
                verifyNoInteractions(preflight);
                return null;
            });
        } finally {
            fx(() -> { if (controller[0] != null) controller[0].close(); if (stage[0] != null) stage[0].close(); return null; });
            worker.shutdownNow(); Platform.exit();
        }
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(25, TimeUnit.SECONDS);
    }
}
