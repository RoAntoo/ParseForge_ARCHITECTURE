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
    private final dev.parseforge.application.settings.EngineProfile profile;
    private final Runnable selectionChanged;
    private final java.util.function.Consumer<EngineSettingsController> expansionChanged;
    private final VBox details = new VBox(12);
    private final Button expand = new Button("▾");
    private boolean expanded = true;
    private boolean otherBusy;
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
    private final Button checkButton = new Button("Verificar estado");
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
        this(owner, dev.parseforge.application.settings.EngineProfile.marker(), install, repair, uninstall, check, cancel,
                busyListener, log, new ToggleGroup(), () -> {}, ignored -> {});
    }
    public EngineSettingsController(Stage owner, dev.parseforge.application.settings.EngineProfile profile, InstallEngineUseCase install,
            RepairEngineUseCase repair, UninstallEngineUseCase uninstall, CheckEngineStatusUseCase check,
            CancelEngineOperationUseCase cancel, Consumer<Boolean> busyListener, Consumer<String> log,
            ToggleGroup selections, Runnable selectionChanged, Consumer<EngineSettingsController> expansionChanged) {
        EngineId id = profile.id();
        this.profile = profile; this.selectionChanged = selectionChanged; this.expansionChanged = expansionChanged;
        this.owner = owner; this.id = id; this.install = install; this.repair = repair;
        this.uninstall = uninstall; this.check = check; this.cancel = cancel;
        this.busyListener = busyListener; this.log = log;
        installButton.setId("install-" + id); repairButton.setId("repair-" + id);
        uninstallButton.setId("uninstall-" + id); state.setId(controlId("engine-state"));
        cancelButton.setId(controlId("cancel-installation")); progress.setMaxWidth(Double.MAX_VALUE);
        checkButton.setId(controlId("check-engine-state"));
        healthCheck.setId(controlId("engine-health-check"));
        healthCheck.setTooltip(new Tooltip("Ejecuta pruebas adicionales de funcionamiento de " + profile.name() + "."));
        installButton.setText("Instalar " + profile.name());
        progress.setVisible(false); progress.setManaged(false);
        Label info = new Label("Versión " + check.installation(id).version().value() + " · Tamaño instalado: ~" + bytes(profile.installedBytes()) + "\n"
                + "Procesamiento local. Internet se utiliza para instalar o reparar el motor.");
        info.setWrapText(true);
        info.setMinHeight(Region.USE_PREF_SIZE);
        info.setMaxWidth(Double.MAX_VALUE);
        operation.setWrapText(true);
        operation.setId(controlId("engine-operation"));
        operation.setMinHeight(Region.USE_PREF_SIZE);
        elapsed.setWrapText(true); elapsed.setMinHeight(Region.USE_PREF_SIZE);
        timing = new Label((profile.ocr() ? "Tiempo estimado: 20 minutos o más. Puede finalizar antes. " : "El tiempo depende de la conexión y del equipo. ")
                + "Las descargas dependen de tu conexión a Internet "
                + "y las etapas de preparación dependen del rendimiento de tu equipo.");
        timing.setId(controlId("engine-install-estimate"));
        timing.setWrapText(true); timing.setMinHeight(Region.USE_PREF_SIZE);
        timing.getStyleClass().add("muted");
        var actions = new FlowPane(8, 8, installButton, repairButton, cancelButton);
        Hyperlink prerequisite = new Hyperlink("Visual C++ Runtime · instrucciones");
        prerequisite.setWrapText(true); prerequisite.setMinHeight(Region.USE_PREF_SIZE);
        prerequisite.setOnAction(ignored -> {
            try { java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://aka.ms/vs/17/release/vc_redist.x64.exe")); }
            catch (Exception error) { operation.setText("Descarga oficial: https://aka.ms/vs/17/release/vc_redist.x64.exe"); }
        });
        Label verification = new Label("Los archivos se comprueban siempre. Si omitís la prueba, "
                + "el funcionamiento se comprobará al convertir el primer PDF.");
        verification.setWrapText(true); verification.setMinHeight(Region.USE_PREF_SIZE);
        healthCheck.setWrapText(true);
        healthCheck.setMinHeight(Region.USE_PREF_SIZE);
        healthCheck.setMaxWidth(Double.MAX_VALUE);
        VBox advanced = new VBox(12, healthCheck, verification,
                new FlowPane(8, 8, checkButton, uninstallButton));
        if (profile.ocr()) advanced.getChildren().add(2, prerequisite);
        advanced.getStyleClass().add("engine-advanced");
        TitledPane settings = new TitledPane("Opciones del motor", advanced);
        settings.setId(controlId("engine-options")); settings.setExpanded(false);
        settings.setMinHeight(Region.USE_PREF_SIZE);
        Label name = new Label(profile.name()); name.getStyleClass().add("engine-name");
        selection.setId(id + "-selection"); selection.setToggleGroup(selections);
        selection.setAccessibleText("Seleccionar " + profile.name() + " para convertir");
        selection.setOnAction(ignored -> { if (ready()) { selection.setSelected(true); selectionChanged.run(); } });
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(8, name, spacer, selection); heading.setAlignment(Pos.CENTER_LEFT);
        Label description = new Label(profile.description());
        description.setWrapText(true); description.setMinHeight(Region.USE_PREF_SIZE);
        state.setWrapText(true); state.setMinHeight(Region.USE_PREF_SIZE);
        state.getStyleClass().add("engine-state");
        Label capacity = new Label("▰".repeat(profile.scopeBars()) + "▱".repeat(5 - profile.scopeBars()) + "  " + profile.level());
        capacity.setId(id + "-capacity"); capacity.getStyleClass().add("engine-capacity");
        capacity.setTooltip(new Tooltip("El nivel describe el alcance y complejidad del motor; no es una puntuación de calidad."));
        capacity.setAccessibleText("Capacidad: " + profile.level() + ". El nivel describe el alcance y complejidad del motor; no es una puntuación de calidad.");
        expand.setId("expand-" + id); expand.getStyleClass().add("engine-chevron");
        expand.setOnAction(ignored -> { expanded(!expanded); if (expanded) expansionChanged.accept(this); });
        Region stateSpacer = new Region(); HBox.setHgrow(stateSpacer, Priority.ALWAYS);
        HBox stateRow = new HBox(8, state, stateSpacer, expand); stateRow.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().setAll(heading, capacity, stateRow, details);
        card.setMaxWidth(Double.MAX_VALUE); card.setMinWidth(0);
        card.setMinHeight(Region.USE_PREF_SIZE); card.setId(id + "-card");
        card.setAccessibleRole(AccessibleRole.PARENT);
        card.getStyleClass().add("engine-card");
        requirements = new Label(profile.ocr() ? "Para instalar Marker necesitás Internet y al menos 7 GB libres." : "Necesitás Internet y al menos 500 MB libres para preparar el motor.");
        requirements.setWrapText(true); requirements.setMinHeight(Region.USE_PREF_SIZE);
        requirements.getStyleClass().add("muted");
        installButton.getStyleClass().add("primary-button");
        details.getChildren().setAll(description, info, requirements, actions, operation, progress, elapsed, timing, settings);
        details.setId(id + "-details"); details.setMinWidth(0);
        view = new VBox(0, card);
        view.setMinWidth(0); view.getStyleClass().add("engine-list");
        timer.setCycleCount(Timeline.INDEFINITE);
        checkButton.setOnAction(ignored -> refresh());
        installButton.setOnAction(ignored -> begin(0));
        repairButton.setOnAction(ignored -> begin(1));
        uninstallButton.setOnAction(ignored -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Se eliminarán " + profile.name() + " y sus archivos privados. Los documentos se conservarán.",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.initOwner(owner); confirm.setHeaderText("¿Desinstalar " + profile.name() + "?");
            styleDialog(confirm);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) begin(2);
        });
        cancelButton.setOnAction(ignored -> {
            cancellationRequested = true; cancel.execute(id);
            cancelButton.setDisable(true); operation.setText("Cancelando...");
        });
        for (ButtonBase button : java.util.List.of(selection, expand, installButton, repairButton, uninstallButton, cancelButton, checkButton)) {
            button.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ENTER && !button.isDisabled()) {
                    button.fire(); event.consume();
                }
            });
        }
        refreshButtons();
    }
    public VBox view() { return view; }
    public boolean ready() { return !busy && check.state(id) == EngineState.READY; }
    public boolean selected() { return selection.isSelected() && ready(); }
    private String controlId(String legacy) { return profile.id().value().equals("marker") ? legacy : legacy + "-" + profile.id(); }
    public EngineId id() { return id; }
    public boolean busy() { return busy || check.state(id) == EngineState.BUSY; }
    public void selected(boolean value) { selection.setSelected(value && ready()); updateSelectedStyle(); }
    private void updateSelectedStyle() { card.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), selected()); }
    public void expanded(boolean value) {
        expanded = value; show(details, value); expand.setText(value ? "▾" : "▸");
        expand.setAccessibleText((value ? "Ocultar detalles de " : "Mostrar detalles de ") + profile.name());
    }
    public void addConversionOption(javafx.scene.Node option) { details.getChildren().add(option); }
    public void otherBusy(boolean value) { otherBusy = value; refreshButtons(); }
    public void converting(boolean value) { converting = value; refreshButtons(); }
    public CompletableFuture<Void> refresh() {
        if (busy || converting || otherBusy) return CompletableFuture.completedFuture(null);
        var finished = new CompletableFuture<Void>();
        busy = true; checking = true; busyListener.accept(true); refreshButtons();
        check.execute(id).whenComplete((value, error) -> Platform.runLater(() -> {
            busy = false; checking = false;
            operation.setText(error == null ? "" : "No se pudo comprobar " + profile.name() + ".");
            refreshButtons(); finished.complete(null);
        }));
        return finished;
    }
    private void begin(int action) {
        if (busy || converting || otherBusy) return;
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
        if (checking) state.setText("Estado: Comprobando " + profile.name() + "...");
        else if (busy && value == EngineState.NOT_INSTALLED) state.setText("Estado: Preparando instalación...");
        if (!ready()) selection.setSelected(false);
        card.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), selection.isSelected());
        selection.setDisable(!ready() || converting || otherBusy);
        card.setAccessibleText(profile.name() + ". " + state.getText() + (selection.isSelected() ? ". Seleccionado" : ""));
        boolean occupied = busy || converting || otherBusy || value == EngineState.BUSY;
        healthCheck.setDisable(occupied);
        installButton.setDisable(occupied || value == EngineState.READY);
        repairButton.setDisable(occupied || value == EngineState.NOT_INSTALLED);
        uninstallButton.setDisable(occupied || value == EngineState.NOT_INSTALLED);
        checkButton.setDisable(busy || converting || otherBusy);
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
