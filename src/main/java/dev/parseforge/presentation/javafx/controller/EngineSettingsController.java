package dev.parseforge.presentation.javafx.controller;

import dev.parseforge.application.usecase.*;
import dev.parseforge.domain.model.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
    private final ProgressBar progress = new ProgressBar(-1);
    private final Button installButton = new Button("Instalar Marker");
    private final Button repairButton = new Button("Reparar");
    private final Button uninstallButton = new Button("Desinstalar");
    private final Button cancelButton = new Button("Cancelar instalación");
    private final Button checkButton = new Button("Actualizar estado");
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
        progress.setVisible(false); progress.setManaged(false);
        Label info = new Label("Marker · OCR y conversión avanzada · CPU / llama.cpp\n"
                + "Versión " + check.installation(id).version().value() + " · ~3,2 GB · instalación: al menos 7 GB libres\n"
                + "Los documentos se procesan localmente. Internet se utiliza para instalar el motor y descargar modelos.");
        info.setWrapText(true);
        info.setMinHeight(Region.USE_PREF_SIZE);
        info.setMaxWidth(Double.MAX_VALUE);
        operation.setWrapText(true);
        operation.setId("engine-operation");
        operation.setMinHeight(Region.USE_PREF_SIZE);
        view = new VBox(8, info, state, new HBox(8, installButton, repairButton, uninstallButton, cancelButton, checkButton), operation, progress);
        checkButton.setOnAction(ignored -> refresh());
        installButton.setOnAction(ignored -> begin(0));
        repairButton.setOnAction(ignored -> begin(1));
        uninstallButton.setOnAction(ignored -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Se eliminarán Marker, sus modelos y sus cachés privados. Los documentos se conservarán.",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.initOwner(owner); confirm.setHeaderText("¿Desinstalar Marker?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) begin(2);
        });
        cancelButton.setOnAction(ignored -> {
            cancellationRequested = true; cancel.execute(id);
            cancelButton.setDisable(true); operation.setText("Cancelando...");
        });
        refreshButtons();
    }
    public VBox view() { return view; }
    public boolean ready() { return check.state(id) == EngineState.READY; }
    public void converting(boolean value) { converting = value; refreshButtons(); }
    public void refresh() {
        busy = true; checking = true; busyListener.accept(true); refreshButtons();
        check.execute(id).whenComplete((value, error) -> Platform.runLater(() -> {
            busy = false; checking = false; busyListener.accept(false);
            operation.setText(error == null ? "" : "No se pudo comprobar Marker.");
            refreshButtons();
        }));
    }
    private void begin(int action) {
        if (busy || converting) return;
        cancellationRequested = false;
        busy = true; busyListener.accept(true);
        progress.setProgress(-1); progress.setVisible(true); progress.setManaged(true); refreshButtons();
        var listener = (dev.parseforge.application.port.out.EngineProgressListener) event -> Platform.runLater(() -> {
            // A click can precede registration of a queued background operation.
            if (cancellationRequested) cancel.execute(id);
            if (event.message().startsWith("[STDOUT]") || event.message().startsWith("[STDERR]")) log.accept(event.message());
            else operation.setText(event.message() + (event.fraction() >= 0 ? " · %.0f %%".formatted(event.fraction() * 100) : ""));
            progress.setProgress(event.fraction());
            refreshButtons();
        });
        CompletableFuture<Void> future = switch (action) {
            case 0 -> install.execute(id, listener);
            case 1 -> repair.execute(id, listener);
            default -> uninstall.execute(id, listener);
        };
        // Final-state UI update runs after the manager has released its lease.
        future.whenComplete((ignored, error) -> Platform.runLater(() -> {
            busy = false; cancellationRequested = false; busyListener.accept(false); progress.setVisible(false); progress.setManaged(false);
            refreshButtons();
            if (error != null) {
                Throwable cause = error; while (cause.getCause() != null && cause instanceof java.util.concurrent.CompletionException) cause = cause.getCause();
                operation.setText(cause.getMessage()); log.accept(cause.toString());
            }
        }));
    }
    private void refreshButtons() {
        EngineState value = check.state(id);
        state.setText("Estado: " + switch (value) {
            case READY -> "✓ Listo";
            case NOT_INSTALLED -> "No instalado";
            case BROKEN -> "Necesita reparación";
            case DOWNLOADING -> "Descargando";
            case VERIFYING -> "Verificando";
            case REMOVING -> "Desinstalando";
            case BUSY -> "En uso en otra ventana";
            default -> "Preparando";
        });
        boolean occupied = busy || converting || value == EngineState.BUSY;
        installButton.setDisable(occupied || value == EngineState.READY);
        repairButton.setDisable(occupied || value == EngineState.NOT_INSTALLED);
        uninstallButton.setDisable(occupied || value == EngineState.NOT_INSTALLED);
        checkButton.setDisable(busy || converting);
        cancelButton.setDisable(!busy || checking || cancellationRequested || value == EngineState.REMOVING);
    }
}
