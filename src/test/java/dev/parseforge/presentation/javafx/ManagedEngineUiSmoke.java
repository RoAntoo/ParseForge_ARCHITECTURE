package dev.parseforge.presentation.javafx;

import javafx.application.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.*;

/** Opt-in driver of the actual JavaFX composition and engine buttons. */
public final class ManagedEngineUiSmoke {
    public static void main(String[] args) { Application.launch(SmokeApplication.class, args); }
    public static final class SmokeApplication extends Application {
        private ParseForgeApplication app;
        @Override public void start(Stage stage) throws Exception {
            List<String> args = getParameters().getRaw();
            System.setProperty("parseforge.dataDir", args.get(0));
            app = new ParseForgeApplication(); app.start(stage);
            Thread.startVirtualThread(() -> {
                try {
                    String action = args.get(1);
                    waitFor(() -> (label(stage).equals("Estado: ✓ Listo") || label(stage).equals("Estado: No instalado")
                            || label(stage).equals("Estado: Necesita reparación")) &&
                            fx(() -> !((Button)stage.getScene().lookup("#check-engine-state")).isDisabled()), 120);
                    snapshot(stage, action + "-before");
                    System.out.println("UI_INITIAL=" + label(stage));
                    if (List.of("digital", "ocr", "cancel").contains(action)) {
                        fx(() -> {
                            app.controller.selectPdf(Path.of(args.get(2)));
                            ((TextField)stage.getScene().lookup("#output-directory")).setText(
                                    Path.of(args.get(0), "validation", "ui-output-" + action + "-" + UUID.randomUUID()).toString());
                            ((CheckBox)stage.getScene().lookup("#force-ocr")).setSelected(!action.equals("digital"));
                            ((Button)stage.getScene().lookup("#convert-pdf")).fire();
                            return null;
                        });
                        Thread.sleep(5000);
                        snapshot(stage, action + "-processing");
                        if (action.equals("cancel")) {
                            Thread.sleep(30000);
                            fx(() -> { ((Button)stage.getScene().lookup("#cancel-conversion")).fire(); return null; });
                        }
                        String expected = action.equals("cancel") ? "Conversión cancelada" : "Conversión completada";
                        waitFor(() -> fx(() -> ((Label)stage.getScene().lookup("#conversion-state")).getText()).equals(expected), 600);
                        if (!action.equals("cancel")) fx(() -> {
                            String logs = ((TextArea)stage.getScene().lookup("#engine-logs")).getText();
                            if (!logs.contains("[STDOUT]") && !logs.contains("[STDERR]")) throw new IllegalStateException("Sin logs reales");
                            return null;
                        });
                    } else if (!action.equals("restart")) {
                        String button = switch (action) {
                            case "install", "cancel-install" -> "#install-marker";
                            case "repair" -> "#repair-marker";
                            case "uninstall" -> "#uninstall-marker";
                            default -> throw new IllegalArgumentException(action);
                        };
                        fx(() -> {
                            if (action.equals("uninstall")) Platform.runLater(() -> {
                                for (Window window : List.copyOf(Window.getWindows())) {
                                    if (window instanceof Stage dialog && dialog != stage && dialog.getScene() != null) {
                                        for (Node node : dialog.getScene().getRoot().lookupAll(".button")) {
                                            if (node instanceof Button confirm && confirm.isDefaultButton()) {
                                                System.out.println("UI_CONFIRM_UNINSTALL=" + confirm.getText());
                                                confirm.fire();
                                            }
                                        }
                                    }
                                }
                            });
                            var control = (Button)stage.getScene().lookup(button);
                            if (control.isDisabled()) throw new IllegalStateException("Acción no disponible: " + action);
                            control.fire();
                            return null;
                        });
                        if (action.equals("cancel-install")) {
                            Thread.sleep(1000);
                            fx(() -> { ((Button)stage.getScene().lookup("#cancel-installation")).fire(); return null; });
                        }
                        String expected = List.of("uninstall", "cancel-install").contains(action) ? "Estado: No instalado" : "Estado: ✓ Listo";
                        waitFor(() -> {
                            boolean finished = fx(() -> !((Button)stage.getScene().lookup("#check-engine-state")).isDisabled());
                            if (finished && !label(stage).equals(expected))
                                throw new IllegalStateException(fx(() -> ((Label)stage.getScene().lookup("#engine-operation")).getText()));
                            return finished && label(stage).equals(expected);
                        }, 1200);
                    } else if (!label(stage).equals("Estado: ✓ Listo")) throw new IllegalStateException(label(stage));
                    snapshot(stage, action + "-after");
                    System.out.println("UI_PASS=" + action + " " + label(stage));
                    fx(() -> { app.stop(); stage.close(); Platform.exit(); return null; });
                } catch (Throwable error) {
                    error.printStackTrace();
                    Platform.runLater(() -> { app.stop(); stage.close(); Platform.exit(); });
                    // Nonzero exit for shell orchestration after toolkit shutdown.
                    System.exit(1);
                }
            });
        }
        private String label(Stage stage) throws Exception {
            return fx(() -> ((Label)stage.getScene().lookup("#engine-state")).getText());
        }
        private void snapshot(Stage stage, String name) throws Exception {
            fx(() -> {
                var image = stage.getScene().snapshot(null);
                var output = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                var pixels = image.getPixelReader();
                for (int y=0; y<output.getHeight(); y++) for (int x=0; x<output.getWidth(); x++) output.setRGB(x,y,pixels.getArgb(x,y));
                Path path = Path.of("target/stage3c-ui-" + name + ".png").toAbsolutePath();
                ImageIO.write(output, "png", path.toFile());
                System.out.println("SCREENSHOT=" + path);
                return null;
            });
        }
        private interface Condition { boolean test() throws Exception; }
        private void waitFor(Condition condition, int seconds) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            long nextUpdate = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!condition.test()) {
                if (System.nanoTime() > deadline) throw new IllegalStateException("Timeout esperando la UI");
                if (System.nanoTime() > nextUpdate) {
                    System.out.println("UI_WAIT=" + fx(() -> {
                        var scene = appStage().getScene();
                        return ((Label)scene.lookup("#engine-state")).getText() + " / "
                                + ((Label)scene.lookup("#conversion-state")).getText();
                    }));
                    nextUpdate = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                }
                Thread.sleep(250);
            }
        }
        private Stage appStage() { return (Stage) Window.getWindows().stream().filter(w -> w instanceof Stage && w.isShowing()).findFirst().orElseThrow(); }
        private <T> T fx(Callable<T> action) throws Exception {
            var task = new FutureTask<>(action); Platform.runLater(task); return task.get(60, TimeUnit.SECONDS);
        }
    }
}
