package dev.parseforge.presentation.javafx.controller;

import dev.parseforge.application.usecase.*;
import dev.parseforge.domain.model.*;
import javafx.application.Platform;
import javafx.scene.AccessibleRole;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import dev.parseforge.presentation.javafx.DownloadEstimate;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

public final class EngineSettingsController {
    private final Stage owner;
    private final EngineId id;
    private final InstallEngineUseCase install;
    private final RepairEngineUseCase repair;
    private final UninstallEngineUseCase uninstall;
    private final CheckEngineStatusUseCase check;
    private final CancelEngineOperationUseCase cancel;
    private final Consumer<Boolean> busyListener;
    private final Consumer<String> log;
    private final Label state = new Label("Comprobando Marker...");
    private final Label operation = new Label();
    private final Label elapsed = new Label();
    private final DownloadEstimate estimate = new DownloadEstimate();
    private final Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1), ignored -> tick()));
    private long started;
    private long lastDownload;
    private String detail = "";
    private final ProgressBar progress = new ProgressBar(-1);
    private final Button installButton = new Button("Instalar Marker");
    private final Button repairButton = new Button("Reparar");
    private final Button uninstallButton = new Button("Desinstalar");
    private final Button cancelButton = new Button("Cancelar instalación");
    private final Button checkButton = new Button("Actualizar estado");
    private final CheckBox healthCheck = new CheckBox("Probar funcionamiento al finalizar (opcional)");
    private final VBox card = new VBox(12);
    private final RadioButton selection = new RadioButton();
    private final Label timing;
    private final Label requirements;
    private final VBox view;
    private boolean busy;
    private boolean converting;
    private boolean checking;
    private boolean cancellationRequested;
    public EngineSettingsController(Stage owner, EngineId id, InstallEngineUseCase install,
            RepairEngineUseCase repair, UninstallEngineUseCase uninstall, CheckEngineStatusUseCase check,
            CancelEngineOperationUseCase cancel, Consumer<Boolean> busyListener, Consumer<String> log) {
        this.owner = owner; this.id = id; this.install = install; this.repair = repair;
        this.uninstall = uninstall; this.check = check; this.cancel = cancel;
        this.busyListener = busyListener; this.log = log;
        installButton.setId("install-marker"); repairButton.setId("repair-marker");
        uninstallButton.setId("uninstall-marker"); state.setId("engine-state");
        cancelButton.setId("cancel-installation"); progress.setMaxWidth(Double.MAX_VALUE);
        checkButton.setId("check-engine-state");
        healthCheck.setId("engine-health-check");
        healthCheck.setTooltip(new Tooltip("Ejecuta pruebas adicionales de Marker y llama.cpp. Puede tardar varios minutos."));
        progress.setVisible(false); progress.setManaged(false);
        Label info = new Label("Versión " + check.installation(id).version().value() + " · ~3,2 GB\n"
                + "Los documentos se procesan localmente. Internet se utiliza para instalar el motor y descargar modelos.");
        info.setWrapText(true);
        info.setMinHeight(Region.USE_PREF_SIZE);
        info.setMaxWidth(Double.MAX_VALUE);
        operation.setWrapText(true);
        operation.setId("engine-operation");
        operation.setMinHeight(Region.USE_PREF_SIZE);
        elapsed.setWrapText(true); elapsed.setMinHeight(Region.USE_PREF_SIZE);
        timing = new Label("Tiempo estimado: 20 minutos o más. Puede finalizar antes. Los tiempos son aproximados. "
                + "Las descargas dependen de tu conexión a Internet "
                + "y las etapas de preparación dependen del rendimiento de tu equipo.");
        timing.setId("engine-install-estimate");
        timing.setWrapText(true); timing.setMinHeight(Region.USE_PREF_SIZE);
        timing.getStyleClass().add("muted");
        var actions = new FlowPane(8, 8, installButton, repairButton, cancelButton);
        Hyperlink prerequisite = new Hyperlink("Visual C++ Runtime · instrucciones");
        prerequisite.setWrapText(true); prerequisite.setMinHeight(Region.USE_PREF_SIZE);
        prerequisite.setOnAction(ignored -> {
            try { java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://aka.ms/vs/17/release/vc_redist.x64.exe")); }
            catch (Exception error) { operation.setText("Descarga oficial: https://aka.ms/vs/17/release/vc_redist.x64.exe"); }
        });
        Label verification = new Label("Los archivos y modelos se comprueban siempre. Si omitís la prueba, "
                + "el funcionamiento se comprobará al convertir el primer PDF.");
        verification.setWrapText(true); verification.setMinHeight(Region.USE_PREF_SIZE);
        healthCheck.setWrapText(true);
        healthCheck.setMinHeight(Region.USE_PREF_SIZE);
        healthCheck.setMaxWidth(Double.MAX_VALUE);
        VBox advanced = new VBox(12, info, healthCheck, verification, prerequisite,
                new FlowPane(8, 8, checkButton, uninstallButton));
        advanced.getStyleClass().add("engine-advanced");
        TitledPane settings = new TitledPane("Opciones del motor", advanced);
        settings.setId("engine-options"); settings.setExpanded(false);
        settings.setMinHeight(Region.USE_PREF_SIZE);
        Label name = new Label("Marker"); name.getStyleClass().add("engine-name");
        selection.setId("marker-selection"); selection.setToggleGroup(new ToggleGroup());
        selection.setAccessibleText("Seleccionar Marker");
        selection.setOnAction(ignored -> { selection.setSelected(ready()); notifyState(); });
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(8, name, spacer, selection); heading.setAlignment(Pos.CENTER_LEFT);
        Label description = new Label("Conversión avanzada con reconocimiento de estructura. Ideal para documentos complejos y OCR.");
        description.setWrapText(true); description.setMinHeight(Region.USE_PREF_SIZE);
        state.setWrapText(true); state.setMinHeight(Region.USE_PREF_SIZE);
        state.getStyleClass().add("engine-state");
        card.getChildren().setAll(heading, description, state);
        card.setMaxWidth(Double.MAX_VALUE); card.setMinWidth(0);
        card.setMinHeight(Region.USE_PREF_SIZE); card.setId("marker-card");
        card.setAccessibleRole(AccessibleRole.RADIO_BUTTON);
        card.setAccessibleText("Marker, motor de conversión");
        card.setFocusTraversable(true);
        card.getStyleClass().add("engine-card");
        card.setOnMouseClicked(ignored -> select());
        card.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.SPACE || event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                select(); event.consume();
            }
        });
        requirements = new Label("Para instalar Marker necesitás Internet y al menos 7 GB libres.");
        requirements.setWrapText(true); requirements.setMinHeight(Region.USE_PREF_SIZE);
        requirements.getStyleClass().add("muted");
        installButton.getStyleClass().add("primary-button");
        view = new VBox(14, card, requirements, actions, operation, progress, elapsed, timing, settings);
        view.setMinWidth(0); view.getStyleClass().add("engine-list");
        timer.setCycleCount(Timeline.INDEFINITE);
        checkButton.setOnAction(ignored -> refresh());
        installButton.setOnAction(ignored -> begin(0));
        repairButton.setOnAction(ignored -> begin(1));
        uninstallButton.setOnAction(ignored -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Se eliminarán Marker, sus modelos y sus cachés privados. Los documentos se conservarán.",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.initOwner(owner); confirm.setHeaderText("¿Desinstalar Marker?");
            styleDialog(confirm);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) begin(2);
        });
        cancelButton.setOnAction(ignored -> {
            cancellationRequested = true; cancel.execute(id);
            cancelButton.setDisable(true); operation.setText("Cancelando...");
        });
        refreshButtons();
    }
    public VBox view() { return view; }
    public boolean ready() { return !busy && check.state(id) == EngineState.READY; }
    public boolean selected() { return selection.isSelected() && ready(); }
    private void select() { if (ready()) { selection.setSelected(true); notifyState(); } }
    public void converting(boolean value) { converting = value; refreshButtons(); }
    public void refresh() {
        if (busy || converting) return;
        busy = true; checking = true; busyListener.accept(true); refreshButtons();
        check.execute(id).whenComplete((value, error) -> Platform.runLater(() -> {
            busy = false; checking = false;
            operation.setText(error == null ? "" : "No se pudo comprobar Marker.");
            refreshButtons();
        }));
    }
    private void begin(int action) {
        if (busy || converting) return;
        cancellationRequested = false;
        started = System.nanoTime(); lastDownload = 0; estimate.reset(); detail = ""; timer.playFromStart();
        busy = true; busyListener.accept(true);
        progress.setProgress(-1); progress.setVisible(true); progress.setManaged(true); refreshButtons();
        var listener = (dev.parseforge.application.port.out.EngineProgressListener) event -> Platform.runLater(() -> {
            // A click can precede registration of a queued background operation.
            if (cancellationRequested) cancel.execute(id);
            if (event.message().startsWith("[STDOUT]") || event.message().startsWith("[STDERR]")) log.accept(event.message());
            else {
                detail = event.message() + (event.fraction() >= 0 ? " · %.0f %%".formatted(event.fraction() * 100) : "");
                if (event.phase() == EngineInstallProgress.Phase.DOWNLOADING) {
                    long now = System.nanoTime(); lastDownload = now;
                    estimate.update(event.message(), event.completedBytes(), now);
                    detail += "\n" + bytes(event.completedBytes()) + (event.totalBytes() > 0 ? " / " + bytes(event.totalBytes()) : " descargados");
                    if (estimate.bytesPerSecond() > 0) detail += " · " + bytes((long)estimate.bytesPerSecond()) + "/s";
                    var remaining = estimate.remainingSeconds(event.completedBytes(), event.totalBytes());
                    detail += remaining.isPresent() ? " · Restante de este archivo: ~" + remaining.getAsLong() + " s"
                            : " · Calculando tiempo restante de este archivo...";
                } else { lastDownload = 0; estimate.reset(); }
                operation.setText(detail);
            }
            progress.setProgress(event.fraction());
            refreshButtons();
        });
        var options = new EngineInstallOptions(healthCheck.isSelected());
        CompletableFuture<Void> future = switch (action) {
            case 0 -> install.execute(id, options, listener);
            case 1 -> repair.execute(id, options, listener);
            default -> uninstall.execute(id, listener);
        };
        // Final-state UI update runs after the manager has released its lease.
        future.whenComplete((ignored, error) -> Platform.runLater(() -> {
            busy = false; cancellationRequested = false; progress.setVisible(false); progress.setManaged(false);
            timer.stop(); tick();
            refreshButtons();
            if (error != null) {
                Throwable cause = error; while (cause.getCause() != null && cause instanceof java.util.concurrent.CompletionException) cause = cause.getCause();
                operation.setText(cause.getMessage()); log.accept(cause.toString());
            }
            refreshButtons();
        }));
    }
    private void tick() {
        long seconds = Math.max(0, (System.nanoTime() - started) / 1_000_000_000L);
        elapsed.setText("Tiempo transcurrido: %02d:%02d".formatted(seconds / 60, seconds % 60)
                + (busy ? " · ParseForge continúa trabajando." : ""));
        show(elapsed, true);
        if (busy && lastDownload > 0 && System.nanoTime() - lastDownload > 3_000_000_000L) {
            estimate.reset();
            operation.setText(detail.split("\\n")[0] + "\nEsperando datos... Calculando tiempo restante de este archivo...");
        }
    }
    private static String bytes(long value) {
        return value >= 1_000_000_000 ? "%.2f GB".formatted(value / 1e9)
                : value >= 1_000_000 ? "%.1f MB".formatted(value / 1e6)
                : "%.1f KB".formatted(value / 1e3);
    }
    private void refreshButtons() {
        EngineState value = check.state(id);
        state.setText("Estado: " + switch (value) {
            case READY -> "✓ Listo";
            case NOT_INSTALLED -> "No instalado";
            case BROKEN -> "Necesita reparación";
            case DOWNLOADING -> "Descargando";
            case INSTALLING -> "Instalando";
            case VERIFYING -> "Verificando";
            case REMOVING -> "Desinstalando";
            case BUSY -> "En uso en otra ventana";
            default -> "Preparando";
        });
        if (checking) state.setText("Estado: Comprobando Marker...");
        else if (busy && value == EngineState.NOT_INSTALLED) state.setText("Estado: Preparando instalación...");
        selection.setSelected(ready());
        card.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), selection.isSelected());
        card.setDisable(!ready() || converting);
        card.setAccessibleText("Marker. " + state.getText() + (selection.isSelected() ? ". Seleccionado" : ""));
        boolean occupied = busy || converting || value == EngineState.BUSY;
        healthCheck.setDisable(occupied);
        installButton.setDisable(occupied || value == EngineState.READY);
        repairButton.setDisable(occupied || value == EngineState.NOT_INSTALLED);
        uninstallButton.setDisable(occupied || value == EngineState.NOT_INSTALLED);
        checkButton.setDisable(busy || converting);
        cancelButton.setDisable(!busy || checking || cancellationRequested || value == EngineState.REMOVING);
        show(installButton, value == EngineState.NOT_INSTALLED && !busy);
        show(repairButton, !busy && value != EngineState.NOT_INSTALLED);
        show(cancelButton, busy && !checking);
        show(requirements, value != EngineState.READY && !checking);
        show(timing, busy && !checking);
        show(operation, !operation.getText().isBlank());
        show(elapsed, !elapsed.getText().isBlank());
        notifyState();
    }
    private void notifyState() { busyListener.accept(busy || check.state(id) == EngineState.BUSY); }
    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible); node.setManaged(visible);
    }
    private void styleDialog(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
    }
}
