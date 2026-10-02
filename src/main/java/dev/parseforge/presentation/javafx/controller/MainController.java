package dev.parseforge.presentation.javafx.controller;

import dev.parseforge.application.port.out.ConversionEvent;
import dev.parseforge.application.port.out.UserSettingsRepository;
import dev.parseforge.application.settings.UserSettings;
import dev.parseforge.application.usecase.*;
import dev.parseforge.domain.exception.ConversionException;
import dev.parseforge.domain.model.ConversionRequest;
import dev.parseforge.domain.model.ConversionResult;
import dev.parseforge.domain.model.ConversionStatus;
import dev.parseforge.domain.model.EngineId;
import dev.parseforge.domain.model.OutputFormat;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.shape.SVGPath;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletionException;

public final class MainController {
    private final Stage stage;
    private final StartConversionUseCase startConversion;
    private final CancelConversionUseCase cancelConversion;
    private final UserSettingsRepository settingsRepository;
    private final java.util.Map<EngineId, EngineSettingsController> engineCards = new java.util.LinkedHashMap<>();
    private final ToggleGroup selections = new ToggleGroup();
    private boolean initializingEngines = true;
    private boolean reconcilingEngines;
    private EngineId selectedEngineId;

    private final BorderPane root = new BorderPane();
    private final Label selectedPdfLabel = new Label("Ningún PDF seleccionado");
    private final Label dropTitle = new Label("Arrastrá tu archivo PDF");
    private final Label dropHint = new Label("Soltá el documento acá o buscá el archivo en tu equipo.");
    private final Button selectPdfButton = new Button("SELECCIONAR ARCHIVO");
    private final Button changeDirectoryButton = new Button("CAMBIAR");
    private final Label destinationLabel = new Label();
    private final Label actionHint = new Label();
    private final Label systemState = new Label();
    private final VBox conversionStatus = new VBox(12);
    private final VBox dropZone = new VBox(18);
    private final TextField outputDirectoryField = new TextField();
    private final Button convertButton = new Button("CONVERTIR");
    private final CheckBox forceOcr = new CheckBox("Forzar OCR");
    private final Button cancelButton = new Button("Cancelar");
    private final Button openOutputButton = new Button("Abrir carpeta");
    private final ProgressIndicator progress = new ProgressIndicator(-1);
    private final Label phaseLabel = new Label("Listo");
    private final Label elapsedLabel = new Label("Tiempo transcurrido: 00:00:00");
    private final TextArea logs = new TextArea();
    private final Timeline elapsedTimer;

    private Path selectedPdf;
    private Instant conversionStartedAt;
    private String lastInputDirectory;
    private boolean conversionBusy;
    private boolean engineBusy;
    private final boolean developmentOverrideAvailable;
    private UserSettings settings;

    public MainController(
            Stage stage,
            StartConversionUseCase startConversion,
            CancelConversionUseCase cancelConversion,
            UserSettingsRepository settingsRepository,
            InstallEngineUseCase installEngine,
            RepairEngineUseCase repairEngine,
            UninstallEngineUseCase uninstallEngine,
            CheckEngineStatusUseCase checkEngine,
            CancelEngineOperationUseCase cancelEngine,
            EngineId selectedEngineId,
            UserSettings settings,
            boolean developmentOverrideAvailable
    ) {
        this(stage, startConversion, cancelConversion, settingsRepository, installEngine, repairEngine, uninstallEngine,
                checkEngine, cancelEngine, java.util.List.of(dev.parseforge.application.settings.EngineProfile.marker()), settings, developmentOverrideAvailable);
    }
    public MainController(Stage stage, StartConversionUseCase startConversion, CancelConversionUseCase cancelConversion,
            UserSettingsRepository settingsRepository, InstallEngineUseCase installEngine, RepairEngineUseCase repairEngine,
            UninstallEngineUseCase uninstallEngine, CheckEngineStatusUseCase checkEngine, CancelEngineOperationUseCase cancelEngine,
            java.util.List<dev.parseforge.application.settings.EngineProfile> profiles, UserSettings settings, boolean developmentOverrideAvailable) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.startConversion = Objects.requireNonNull(startConversion, "startConversion");
        this.cancelConversion = Objects.requireNonNull(cancelConversion, "cancelConversion");
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
        this.developmentOverrideAvailable = developmentOverrideAvailable;
        this.settings = settings;

        lastInputDirectory = settings.lastInputDirectory();
        for (var profile : profiles) {
            var card = new EngineSettingsController(stage, profile, installEngine, repairEngine, uninstallEngine, checkEngine,
                    cancelEngine, ignored -> engineStateChanged(), line -> appendLog("INSTALL", line), selections,
                    () -> selectEngine(profile.id()), expanded -> engineCards.values().forEach(c -> c.expanded(c == expanded)));
            card.expanded(profiles.size() == 1); engineCards.put(profile.id(), card);
            if (profile.ocr()) card.addConversionOption(forceOcr);
        }
        outputDirectoryField.setText(defaultOutputDirectory(settings.lastOutputDirectory().isBlank()
                ? settings.outputDirectory() : settings.lastOutputDirectory()));
        elapsedTimer = new Timeline(new KeyFrame(Duration.seconds(1), ignored -> updateElapsedTime()));
        elapsedTimer.setCycleCount(Timeline.INDEFINITE);
        buildView();
        var checks = engineCards.values().stream().map(EngineSettingsController::refresh).toArray(java.util.concurrent.CompletableFuture[]::new);
        java.util.concurrent.CompletableFuture.allOf(checks).whenComplete((ignored, error) -> Platform.runLater(() -> {
            initializingEngines = false;
            selectedEngineId = dev.parseforge.application.settings.EngineSelection.restore(settings.selectedEngine(),
                    java.util.List.copyOf(engineCards.keySet()), id -> engineCards.get(id).ready()
                        ? dev.parseforge.domain.model.EngineState.READY : dev.parseforge.domain.model.EngineState.NOT_INSTALLED);
            engineStateChanged();
        }));
    }

    public Parent view() {
        return root;
    }

    private void buildView() {
        Label title = new Label("ParseForge");
        title.getStyleClass().add("title");
        Label privacy = new Label("PROCESAMIENTO LOCAL");
        privacy.getStyleClass().add("eyebrow");
        VBox header = new VBox(3, title, privacy);
        header.getStyleClass().add("brand-header");
        Label enginesTitle = new Label("MOTORES DE CONVERSIÓN");
        enginesTitle.getStyleClass().add("eyebrow");
        VBox engines = new VBox(12, enginesTitle);
        engineCards.values().forEach(card -> engines.getChildren().add(card.view()));
        engines.setPadding(new Insets(24, 18, 24, 18));
        ScrollPane engineScroll = scrollPane(engines, "engine-scroll");
        engineScroll.setId("engine-scroll");

        convertButton.getStyleClass().add("primary-button");
        convertButton.setOnAction(ignored -> startConversion());
        convertButton.setId("convert-pdf"); cancelButton.setId("cancel-conversion"); forceOcr.setId("force-ocr");
        outputDirectoryField.setId("output-directory"); logs.setId("engine-logs"); phaseLabel.setId("conversion-state");
        convertButton.setMaxWidth(Double.MAX_VALUE);
        convertButton.setMinHeight(48);
        actionHint.setId("conversion-hint"); actionHint.setWrapText(true);
        actionHint.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        actionHint.setMaxWidth(Double.MAX_VALUE); actionHint.setAlignment(Pos.CENTER);
        VBox conversionAction = new VBox(12, convertButton, actionHint);
        conversionAction.getStyleClass().add("conversion-action");
        BorderPane sidebar = new BorderPane(engineScroll, header, null, conversionAction, null);
        sidebar.getStyleClass().add("sidebar"); sidebar.setPrefWidth(300); sidebar.setMinWidth(300); sidebar.setMaxWidth(300);
        root.setLeft(sidebar);

        buildDropZone();
        Label destinationTitle = new Label("CARPETA DE DESTINO"); destinationTitle.getStyleClass().add("eyebrow");
        destinationLabel.setMinWidth(0); destinationLabel.setMaxWidth(Double.MAX_VALUE);
        destinationLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        destinationLabel.setId("destination-path");
        destinationLabel.textProperty().bind(outputDirectoryField.textProperty());
        Tooltip destinationTooltip = new Tooltip();
        destinationTooltip.textProperty().bind(outputDirectoryField.textProperty());
        destinationLabel.setTooltip(destinationTooltip);
        outputDirectoryField.setVisible(false); outputDirectoryField.setManaged(false);
        outputDirectoryField.textProperty().addListener((observable, before, after) -> updateConvertState());
        HBox.setHgrow(destinationLabel, Priority.ALWAYS);
        changeDirectoryButton.setOnAction(ignored -> chooseOutputDirectory());
        changeDirectoryButton.setId("change-output-directory");
        changeDirectoryButton.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox destinationRow = new HBox(12, icon("M2 7 L2 22 L27 22 L30 10 L13 10 L10 7 Z M2 7 L2 3 L11 3 L14 6 L26 6 L26 10", "folder-icon"),
                destinationLabel, changeDirectoryButton);
        destinationRow.setAlignment(Pos.CENTER_LEFT);
        VBox destination = new VBox(12, destinationTitle, destinationRow, outputDirectoryField);
        destination.getStyleClass().add("destination-panel"); destination.setMinWidth(0);
        forceOcr.setTooltip(new Tooltip("Activá OCR para reconocer texto en páginas escaneadas."));
        VBox content = new VBox(24, dropZone, destination, buildStatusPanel());
        content.setMinWidth(0); content.getStyleClass().add("workspace");
        ScrollPane workspaceScroll = scrollPane(content, "workspace-scroll");
        workspaceScroll.setId("workspace-scroll");
        dropZone.prefHeightProperty().bind(javafx.beans.binding.Bindings.max(330,
                javafx.beans.binding.Bindings.min(480, workspaceScroll.heightProperty().multiply(0.48))));
        systemState.getStyleClass().add("system-state"); systemState.setWrapText(true);
        systemState.setMinWidth(0);
        Label version = new Label("ParseForge " + dev.parseforge.presentation.javafx.AppVersion.current());
        version.setId("app-version"); version.getStyleClass().add("version");
        version.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(12, systemState, spacer, version); footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("footer");
        root.setCenter(new BorderPane(workspaceScroll, null, null, footer, null));
        updateConvertState();
    }

    private VBox buildDropZone() {
        dropTitle.getStyleClass().add("drop-title"); dropTitle.setWrapText(true);
        dropTitle.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        dropHint.getStyleClass().add("muted"); dropHint.setWrapText(true);
        dropHint.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        dropTitle.setAlignment(Pos.CENTER); dropHint.setAlignment(Pos.CENTER);
        selectPdfButton.setId("select-pdf"); selectPdfButton.getStyleClass().add("primary-button");
        selectPdfButton.setOnAction(ignored -> choosePdf());
        selectedPdfLabel.setId("selected-pdf"); selectedPdfLabel.setMinWidth(0);
        selectedPdfLabel.setMaxWidth(Double.MAX_VALUE);
        selectedPdfLabel.setAlignment(Pos.CENTER); selectedPdfLabel.setVisible(false); selectedPdfLabel.setManaged(false);
        selectedPdfLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        javafx.scene.layout.StackPane uploadIcon = new javafx.scene.layout.StackPane(
                icon("M16 24 L16 3 M8 11 L16 3 L24 11 M3 20 L3 29 L29 29 L29 20", "upload-icon"));
        uploadIcon.getStyleClass().add("upload-circle");
        uploadIcon.setMinSize(72, 72); uploadIcon.setMaxSize(72, 72);
        dropZone.getChildren().setAll(uploadIcon, dropTitle, dropHint, selectPdfButton, selectedPdfLabel);
        dropZone.setId("pdf-drop-zone"); dropZone.setMinWidth(0);
        dropZone.setMinHeight(330); dropZone.setPrefHeight(390);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.getStyleClass().add("drop-zone");
        dropZone.setOnDragOver(event -> acceptPdfDrag(event, dropZone));
        dropZone.setOnDragExited(event -> dropZone.getStyleClass().remove("drop-zone-active"));
        dropZone.setOnDragDropped(event -> receivePdfDrop(event, dropZone));
        return dropZone;
    }

    private VBox buildStatusPanel() {
        progress.setVisible(false);
        progress.setPrefSize(34, 34);
        HBox state = new HBox(12, progress, new VBox(4, phaseLabel, elapsedLabel));
        state.setAlignment(Pos.CENTER_LEFT);

        logs.setEditable(false);
        logs.setWrapText(true); logs.setPrefRowCount(7);
        logs.setPromptText("Los logs del motor aparecerán aquí.");
        TitledPane details = new TitledPane("Detalles técnicos", logs);
        details.setExpanded(false); details.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        cancelButton.setDisable(true);
        cancelButton.setOnAction(ignored -> {
            phaseLabel.setText("Cancelando...");
            cancelButton.setDisable(true);
            cancelConversion.cancel();
        });
        openOutputButton.setOnAction(ignored -> openOutputDirectory());
        HBox actions = new HBox(10, cancelButton, openOutputButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        phaseLabel.setWrapText(true); phaseLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox panel = new VBox(10, conversionStatus, details);
        conversionStatus.getChildren().setAll(state, actions);
        conversionStatus.setVisible(false); conversionStatus.setManaged(false);
        panel.getStyleClass().add("status-panel");
        return panel;
    }

    private ScrollPane scrollPane(VBox content, String style) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinWidth(0); scroll.setMinHeight(0); scroll.getStyleClass().add(style);
        return scroll;
    }

    private SVGPath icon(String path, String style) {
        SVGPath icon = new SVGPath(); icon.setContent(path); icon.getStyleClass().add(style);
        return icon;
    }

    private void choosePdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar documento PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
        chooser.setInitialDirectory(validDirectory(lastInputDirectory));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            selectPdf(file.toPath());
        }
    }

    private void chooseOutputDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Seleccionar carpeta de salida");
        setInitialDirectory(chooser, outputDirectoryField.getText());
        File directory = chooser.showDialog(stage);
        if (directory != null) {
            outputDirectoryField.setText(directory.getAbsolutePath());
            saveSettings();
        }
    }

    private void startConversion() {
        if (conversionBusy || engineBusy || startConversion.hasActiveConversion()) return;
        if (!selectedEngineReady() && !developmentOverrideAvailable) {
            showAlert(Alert.AlertType.INFORMATION, "Instalá o repará un motor para continuar",
                    "Elegí un motor listo desde el panel Motores de conversión.");
            return;
        }
        String validationError = validateInputs();
        if (validationError != null) {
            showAlert(Alert.AlertType.WARNING, "No se puede iniciar", validationError);
            return;
        }

        Path output = Path.of(outputDirectoryField.getText()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(output);
        } catch (IOException error) {
            showAlert(Alert.AlertType.ERROR, "Carpeta no disponible",
                    "No fue posible crear la carpeta de salida.");
            return;
        }

        if (selectedEngineId == null && developmentOverrideAvailable) selectedEngineId = new EngineId("marker");
        saveSettings();
        setBusy(true);
        conversionStatus.setVisible(true); conversionStatus.setManaged(true);
        logs.clear();
        appendLog("SYSTEM", "Iniciando conversión de " + selectedPdf.getFileName());
        conversionStartedAt = Instant.now();
        phaseLabel.setText("Procesando documento...");
        elapsedLabel.setText("Tiempo transcurrido: 00:00:00");
        elapsedTimer.playFromStart();

        ConversionRequest request = new ConversionRequest(
                selectedPdf, output, selectedEngineId, OutputFormat.MARKDOWN, selectedEngineId.value().equals("marker") && forceOcr.isSelected());
        startConversion.start(request, event -> Platform.runLater(() -> handleEvent(event)))
                .whenComplete((result, error) -> Platform.runLater(() -> finishConversion(result, error)));
    }

    private void handleEvent(ConversionEvent event) {
        switch (event) {
            case ConversionEvent.EngineStarted started ->
                    appendLog("SYSTEM", "Motor iniciado: " + started.engineName());
            case ConversionEvent.PhaseChanged phase -> phaseLabel.setText(phase.phase());
            case ConversionEvent.LogReceived log -> appendLog(log.stream().name(), log.message());
            case ConversionEvent.OutputCreated output ->
                    appendLog("SYSTEM", "Archivo creado: " + output.path());
            case ConversionEvent.EngineStopped stopped ->
                    appendLog("SYSTEM", "Motor finalizado con código " + stopped.exitCode());
        }
    }

    private void finishConversion(ConversionResult result, Throwable error) {
        elapsedTimer.stop();
        setBusy(false);
        if (error != null) {
            Throwable cause = unwrap(error);
            String detail = cause instanceof ConversionException conversionError
                    ? conversionError.getMessage()
                    : "Ocurrió un error inesperado al convertir el documento.";
            phaseLabel.setText("Conversión fallida");
            appendLog("ERROR", cause.toString());
            showAlert(Alert.AlertType.ERROR, "No fue posible completar la conversión", detail);
            return;
        }

        if (result.status() == ConversionStatus.COMPLETED) {
            phaseLabel.setText("Conversión completada");
            appendLog("SYSTEM", result.outputFiles().isEmpty()
                    ? "El motor finalizó correctamente. Revisá la carpeta de salida."
                    : "Conversión completada.");
        } else if (result.status() == ConversionStatus.CANCELLED) {
            phaseLabel.setText("Conversión cancelada");
            appendLog("SYSTEM", "Conversión cancelada por el usuario.");
        } else {
            phaseLabel.setText("Conversión fallida");
            String message = result.error().orElse("El motor no pudo completar la conversión.");
            appendLog("ERROR", message);
            showAlert(Alert.AlertType.ERROR, "El motor finalizó con un error", message);
        }
    }

    private String validateInputs() {
        if (selectedPdf == null || !Files.isRegularFile(selectedPdf) || !Files.isReadable(selectedPdf)
                || !selectedPdf.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            return "Seleccioná un archivo PDF válido.";
        }
        return validOutputDirectory() ? null : "Seleccioná una carpeta de destino disponible.";
    }

    private void setBusy(boolean busy) {
        conversionBusy = busy;
        engineCards.values().forEach(card -> card.converting(busy));
        updateConvertState();
        cancelButton.setDisable(!busy);
        progress.setVisible(busy);
        progress.setManaged(busy);
        selectPdfButton.setDisable(busy); changeDirectoryButton.setDisable(busy);
        openOutputButton.setDisable(busy); forceOcr.setDisable(busy);
        dropTitle.setText(busy ? "Procesando tu PDF" : selectedPdf == null ? "Arrastrá tu archivo PDF" : "PDF seleccionado");
    }

    private boolean validOutputDirectory() {
        try {
            if (outputDirectoryField.getText().isBlank()) return false;
            Path output = Path.of(outputDirectoryField.getText()).toAbsolutePath().normalize();
            // A default/new folder can be created by the existing conversion flow.
            while (!Files.exists(output)) {
                output = output.getParent();
                if (output == null) return false;
            }
            return Files.isDirectory(output) && Files.isWritable(output);
        } catch (java.nio.file.InvalidPathException | SecurityException error) { return false; }
    }

    private boolean selectedEngineReady() {
        return selectedEngineId != null && engineCards.containsKey(selectedEngineId) && engineCards.get(selectedEngineId).selected();
    }
    private void selectEngine(EngineId id) {
        if (conversionBusy || engineBusy || !engineCards.get(id).ready()) return;
        selectedEngineId = id; engineStateChanged();
    }
    private void engineStateChanged() {
        if (reconcilingEngines || initializingEngines) return;
        reconcilingEngines = true;
        try {
            engineBusy = engineCards.values().stream().anyMatch(EngineSettingsController::busy);
            // Preserve the active preference during repair/verification. Choose a
            // safe fallback after lifecycle operations release their lease.
            if (!engineBusy && !conversionBusy && (selectedEngineId == null || !engineCards.get(selectedEngineId).ready()))
                selectedEngineId = dev.parseforge.application.settings.EngineSelection.restore("", java.util.List.copyOf(engineCards.keySet()),
                        id -> engineCards.get(id).ready() ? dev.parseforge.domain.model.EngineState.READY : dev.parseforge.domain.model.EngineState.NOT_INSTALLED);
            engineCards.forEach((id, card) -> {
                card.selected(id.equals(selectedEngineId));
                card.otherBusy(engineBusy && !card.busy());
            });
            updateConvertState(); saveSettings();
        } finally { reconcilingEngines = false; }
    }

    private void updateConvertState() {
        boolean ready = selectedEngineReady();
        String validationError = validateInputs();
        String reason;
        if (conversionBusy) reason = "CONVERSIÓN EN CURSO";
        else if (engineBusy) reason = "ESPERÁ A QUE TERMINE LA OPERACIÓN DEL MOTOR";
        else if (!ready && !developmentOverrideAvailable) reason = "INSTALÁ O REPARÁ UN MOTOR PARA CONTINUAR";
        else if (validationError != null) reason = selectedPdf == null ? "SELECCIONÁ UN PDF PARA CONTINUAR" : validationError;
        else reason = "TODO LISTO PARA CONVERTIR";
        boolean enabled = !conversionBusy && !engineBusy && !startConversion.hasActiveConversion()
                && (ready || developmentOverrideAvailable) && validationError == null;
        convertButton.setDisable(!enabled);
        actionHint.setText(reason);
        systemState.setText(conversionBusy ? "● CONVIRTIENDO DOCUMENTO" : engineBusy ? "● COMPROBANDO / PREPARANDO MOTOR"
                : ready || developmentOverrideAvailable ? "● LISTO PARA PROCESAR LOCALMENTE" : "○ INSTALÁ UN MOTOR PARA COMENZAR");
    }

    private void acceptPdfDrag(DragEvent event, VBox dropZone) {
        if (!conversionBusy && event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!dropZone.getStyleClass().contains("drop-zone-active")) {
                dropZone.getStyleClass().add("drop-zone-active");
            }
        }
        event.consume();
    }

    private void receivePdfDrop(DragEvent event, VBox dropZone) {
        Path pdf = firstPdf(event.getDragboard());
        if (!conversionBusy && pdf != null) {
            selectPdf(pdf);
            event.setDropCompleted(true);
        } else {
            event.setDropCompleted(false);
            if (!conversionBusy) invalidPdf();
        }
        dropZone.getStyleClass().remove("drop-zone-active");
        event.consume();
    }

    private Path firstPdf(Dragboard dragboard) {
        if (!dragboard.hasFiles()) {
            return null;
        }
        return dragboard.getFiles().stream()
                .map(File::toPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                .findFirst()
                .orElse(null);
    }

    public void selectPdf(Path pdf) {
        if (conversionBusy) return;
        if (pdf == null || !Files.isRegularFile(pdf) || !Files.isReadable(pdf)
                || !pdf.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            invalidPdf(); return;
        }
        selectedPdf = pdf.toAbsolutePath().normalize();
        lastInputDirectory = selectedPdf.getParent().toString();
        selectedPdfLabel.setText(selectedPdf.getFileName().toString());
        selectedPdfLabel.setTooltip(new Tooltip(selectedPdf.toString()));
        selectedPdfLabel.setVisible(true); selectedPdfLabel.setManaged(true);
        dropTitle.setText("PDF seleccionado"); dropHint.setText("Podés elegir otro archivo antes de convertir.");
        dropZone.getStyleClass().remove("drop-zone-invalid");
        selectPdfButton.setText("CAMBIAR ARCHIVO");
        updateConvertState();
        saveSettings();
    }

    private void invalidPdf() {
        selectedPdf = null;
        selectedPdfLabel.setVisible(false); selectedPdfLabel.setManaged(false);
        dropTitle.setText("Elegí un archivo PDF válido");
        dropHint.setText("No se pudo seleccionar ese archivo. Revisá que sea un PDF accesible en tu equipo.");
        selectPdfButton.setText("SELECCIONAR ARCHIVO");
        if (!dropZone.getStyleClass().contains("drop-zone-invalid")) dropZone.getStyleClass().add("drop-zone-invalid");
        updateConvertState();
    }

    private void openOutputDirectory() {
        if (!Desktop.isDesktopSupported()) {
            showAlert(Alert.AlertType.WARNING, "Acción no disponible",
                    "El sistema no permite abrir la carpeta automáticamente.");
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            showAlert(Alert.AlertType.WARNING, "Acción no disponible",
                    "El sistema no permite abrir la carpeta automáticamente.");
            return;
        }
        Path output = Path.of(outputDirectoryField.getText());
        try {
            Files.createDirectories(output);
            desktop.open(output.toFile());
        } catch (IOException error) {
            showAlert(Alert.AlertType.ERROR, "No fue posible abrir la carpeta", output.toString());
        }
    }

    private void appendLog(String source, String message) {
        logs.appendText("[" + source + "] " + message + System.lineSeparator());
    }

    private void updateElapsedTime() {
        if (conversionStartedAt == null) {
            return;
        }
        java.time.Duration elapsed = java.time.Duration.between(conversionStartedAt, Instant.now());
        long seconds = elapsed.toSeconds();
        elapsedLabel.setText("Tiempo transcurrido: %02d:%02d:%02d".formatted(seconds / 3600, seconds / 60 % 60, seconds % 60));
    }

    private void saveSettings() {
        settings = new UserSettings(settings.markerExecutable(), outputDirectoryField.getText(), lastInputDirectory,
                outputDirectoryField.getText(), settings.language(), selectedEngineId == null ? "" : selectedEngineId.value(), settings.welcomeDialogVersion());
        settingsRepository.save(settings);
    }

    public void showWelcomeIfNeeded() {
        if (settings.welcomeDialogVersion() >= 1) return;
        Dialog<ButtonType> welcome = new Dialog<>();
        welcome.initOwner(stage); welcome.setTitle("Bienvenido a ParseForge");
        welcome.getDialogPane().setId("welcome-dialog");
        Label title = new Label("Bienvenido a ParseForge"); title.getStyleClass().add("welcome-title"); title.setWrapText(true);
        Label message = new Label("Convertí tus documentos PDF a Markdown en tu equipo.\n\n"
                + "ParseForge utiliza motores de conversión instalables. Para comenzar, instalá un motor desde el panel Motores de conversión.\n\n"
                + "Marker permite estructura avanzada y OCR. MarkItDown es ligero y está orientado a PDFs digitales con texto seleccionable. Elegí un motor listo para convertir.\n\n"
                + "Tus documentos se procesan localmente. Internet solo es necesario para instalar o reparar el motor.");
        message.setWrapText(true); message.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        CheckBox hide = new CheckBox("No volver a mostrar al iniciar"); hide.setId("hide-welcome"); hide.setWrapText(true);
        VBox content = new VBox(18, title, message, hide); content.setPadding(new Insets(12)); content.setMinWidth(0);
        ScrollPane scroll = scrollPane(content, "welcome-scroll");
        scroll.setPrefViewportWidth(440); scroll.setPrefViewportHeight(330);
        welcome.getDialogPane().setContent(scroll);
        ButtonType understood = new ButtonType("ENTENDIDO", ButtonBar.ButtonData.OK_DONE);
        welcome.getDialogPane().getButtonTypes().add(understood);
        welcome.getDialogPane().lookupButton(understood).setId("welcome-understood");
        welcome.getDialogPane().lookupButton(understood).getStyleClass().add("primary-button");
        welcome.getDialogPane().getStylesheets().addAll(stage.getScene().getStylesheets());
        welcome.setOnHidden(ignored -> {
            if (hide.isSelected()) {
                settings = new UserSettings(settings.markerExecutable(), settings.outputDirectory(), settings.lastInputDirectory(),
                        settings.lastOutputDirectory(), settings.language(), settings.selectedEngine(), 1);
                saveSettings();
            }
        });
        welcome.show();
    }

    private String defaultOutputDirectory(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return Path.of(System.getProperty("user.home"), "Documents", "ParseForge").toString();
    }

    private void setInitialDirectory(DirectoryChooser chooser, String path) {
        chooser.setInitialDirectory(validDirectory(path));
    }

    private File validDirectory(String configured) {
        if (configured != null && !configured.isBlank()) {
            try { Path path = Path.of(configured); if (Files.isDirectory(path)) return path.toFile(); }
            catch (java.nio.file.InvalidPathException ignored) { }
        }
        Path home = Path.of(System.getProperty("user.home"));
        Path documents = home.resolve("Documents");
        return (Files.isDirectory(documents) ? documents : home).toFile();
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(stage);
        alert.getDialogPane().getStylesheets().addAll(stage.getScene().getStylesheets());
        alert.setTitle("ParseForge");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
