package dev.parseforge.presentation.javafx.controller;

import dev.parseforge.application.port.out.ConversionEvent;
import dev.parseforge.application.port.out.UserSettingsRepository;
import dev.parseforge.application.settings.UserSettings;
import dev.parseforge.application.settings.DocumentAdvice;
import dev.parseforge.application.settings.ConversionMessages;
import dev.parseforge.domain.model.DocumentPreflightResult;
import dev.parseforge.domain.exception.ErrorCode;
import dev.parseforge.application.usecase.*;
import dev.parseforge.domain.exception.ConversionException;
import dev.parseforge.domain.model.ConversionRequest;
import dev.parseforge.domain.model.ConversionResult;
import dev.parseforge.domain.model.ConversionStatus;
import dev.parseforge.domain.model.EngineId;
import dev.parseforge.domain.model.OutputFormat;
import dev.parseforge.domain.model.DocumentFormats;
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
    private final Label selectedDocumentLabel = new Label("Ningún documento seleccionado");
    private final Label dropTitle = new Label("Arrastrá tu documento");
    private final Label dropHint = new Label("Soltá el documento acá o buscá el archivo en tu equipo.");
    private final Button selectDocumentButton = new Button("SELECCIONAR ARCHIVO");
    private final Button changeDirectoryButton = new Button("CAMBIAR");
    private final Label destinationLabel = new Label();
    private final Label actionHint = new Label();
    private final Label systemState = new Label();
    private final VBox conversionStatus = new VBox(12);
    private final VBox dropZone = new VBox(18);
    private javafx.scene.layout.StackPane uploadIcon;
    private final TextField outputDirectoryField = new TextField();
    private final Button convertButton = new Button("Convertir a Markdown");
    private final Button settingsButton = new Button("Ajustes");
    private Dialog<Void> engineSettings;
    private final java.util.Map<EngineId, ToggleButton> settingsTabs = new java.util.LinkedHashMap<>();
    private final CheckBox forceOcr = new CheckBox("Forzar OCR");
    private final Button cancelButton = new Button("Cancelar");
    private final Button openOutputButton = new Button("Abrir carpeta");
    private final ProgressIndicator progress = new ProgressIndicator(-1);
    private final Label phaseLabel = new Label("Listo");
    private final Label elapsedLabel = new Label("Tiempo transcurrido: 00:00:00");
    private final TextArea logs = new TextArea();
    private final Timeline elapsedTimer;
    private final AnalyzeDocumentUseCase analyzeDocument;
    private enum PreflightState { IDLE, ANALYZING, READY, FAILED }
    private PreflightState preflightState = PreflightState.IDLE;
    private DocumentPreflightResult document;
    private ErrorCode preflightBlock;
    private long selectionRevision;
    private final VBox documentCard = new VBox(8);
    private final Label documentSummary = new Label();
    private final Label documentAdvice = new Label();
    private final Label documentWarning = new Label();
    private final Label conversionSummary = new Label();
    private final Button useSuggestedEngine = new Button();
    private final Label runningEngine = new Label();
    private final Label runningAdvice = new Label();
    private final Label completedOutput = new Label();
    private final VBox liveStatus = new VBox(8);
    private boolean cancelling;

    private Path selectedDocument;
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
            boolean developmentOverrideAvailable,
            AnalyzeDocumentUseCase analyzeDocument
    ) {
        this(stage, startConversion, cancelConversion, settingsRepository, installEngine, repairEngine, uninstallEngine,
                checkEngine, cancelEngine, java.util.List.of(dev.parseforge.application.settings.EngineProfile.marker()), settings, developmentOverrideAvailable, analyzeDocument);
    }
    public MainController(Stage stage, StartConversionUseCase startConversion, CancelConversionUseCase cancelConversion,
            UserSettingsRepository settingsRepository, InstallEngineUseCase installEngine, RepairEngineUseCase repairEngine,
            UninstallEngineUseCase uninstallEngine, CheckEngineStatusUseCase checkEngine, CancelEngineOperationUseCase cancelEngine,
            java.util.List<dev.parseforge.application.settings.EngineProfile> profiles, UserSettings settings, boolean developmentOverrideAvailable,
            AnalyzeDocumentUseCase analyzeDocument) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.startConversion = Objects.requireNonNull(startConversion, "startConversion");
        this.cancelConversion = Objects.requireNonNull(cancelConversion, "cancelConversion");
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
        this.developmentOverrideAvailable = developmentOverrideAvailable;
        this.settings = settings;
        this.analyzeDocument = Objects.requireNonNull(analyzeDocument);

        lastInputDirectory = settings.lastInputDirectory();
        for (var profile : profiles) {
            var card = new EngineSettingsController(stage, profile, installEngine, repairEngine, uninstallEngine, checkEngine,
                    cancelEngine, ignored -> engineStateChanged(), line -> appendLog("INSTALL", line), selections,
                    () -> selectEngine(profile.id()), selected -> openSettings(selected.id()));
            engineCards.put(profile.id(), card);
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
        Region headerSpace = new Region(); HBox.setHgrow(headerSpace, Priority.ALWAYS);
        settingsButton.setId("open-settings"); settingsButton.setOnAction(ignored -> openSettings(selectedEngineId));
        HBox header = new HBox(16, title, headerSpace, settingsButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("brand-header");
        root.setTop(header);
        Label enginesTitle = new Label("1. ELEGÍ UN MOTOR");
        enginesTitle.getStyleClass().add("eyebrow");
        VBox engines = new VBox(12, enginesTitle);
        engineCards.values().forEach(card -> engines.getChildren().add(card.view()));
        engines.setPadding(new Insets(24, 18, 24, 18));
        ScrollPane engineScroll = scrollPane(engines, "engine-scroll");
        engineScroll.setId("engine-scroll");

        convertButton.getStyleClass().add("convert-button");
        convertButton.setOnAction(ignored -> startConversion());
        convertButton.setId("convert-pdf"); cancelButton.setId("cancel-conversion"); forceOcr.setId("force-ocr");
        outputDirectoryField.setId("output-directory"); logs.setId("engine-logs"); phaseLabel.setId("conversion-state");
        convertButton.setMaxWidth(Double.MAX_VALUE);
        convertButton.setMinHeight(48);
        actionHint.setId("conversion-hint"); actionHint.setWrapText(true);
        actionHint.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        actionHint.setMaxWidth(Double.MAX_VALUE); actionHint.setAlignment(Pos.CENTER);
        VBox conversionAction = new VBox(8, convertButton, actionHint);
        conversionAction.getStyleClass().add("conversion-action");
        VBox sidebarFoot = new VBox(12, liveStatus, privacy); sidebarFoot.getStyleClass().add("sidebar-foot");
        BorderPane sidebar = new BorderPane(engineScroll, null, null, sidebarFoot, null);
        sidebar.getStyleClass().add("sidebar"); sidebar.setPrefWidth(240); sidebar.setMinWidth(240); sidebar.setMaxWidth(240);
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
        forceOcr.selectedProperty().addListener((o, before, after) -> refreshDocument());
        VBox statusPanel = buildStatusPanel();
        VBox content = new VBox(16, dropZone, buildDocumentCard(), destination, forceOcr, conversionAction, statusPanel);
        content.setMinWidth(0); content.getStyleClass().add("workspace");
        ScrollPane workspaceScroll = scrollPane(content, "workspace-scroll");
        workspaceScroll.setId("workspace-scroll");
        dropZone.prefHeightProperty().bind(javafx.beans.binding.Bindings.max(240,
                javafx.beans.binding.Bindings.min(360, workspaceScroll.heightProperty().multiply(0.48))));
        systemState.getStyleClass().add("system-state"); systemState.setWrapText(true);
        systemState.setMinWidth(0);
        Label version = new Label("ParseForge " + dev.parseforge.presentation.javafx.AppVersion.current());
        version.setId("app-version"); version.getStyleClass().add("version");
        version.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(12, systemState, spacer, version); footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("footer");
        root.setCenter(new BorderPane(workspaceScroll, null, null, footer, null));
        // Enter and Space activate every action; Tab traversal stays native JavaFX.
        for (Button button : java.util.List.of(settingsButton, selectDocumentButton, changeDirectoryButton,
                convertButton, cancelButton, openOutputButton, useSuggestedEngine)) {
            button.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ENTER && !button.isDisabled()) {
                    button.fire(); event.consume();
                }
            });
        }
        updateConvertState();
    }

    private void openSettings(EngineId initial) {
        if (engineSettings == null) {
            engineSettings = new Dialog<>(); engineSettings.initOwner(stage);
            engineSettings.setTitle("Ajustes · ParseForge"); engineSettings.setResizable(true);
            DialogPane pane = engineSettings.getDialogPane(); pane.setId("engine-settings-dialog");
            pane.setMinSize(0, 0);
            pane.getStylesheets().addAll(stage.getScene().getStylesheets());
            Label title = new Label("Ajustes"); title.getStyleClass().add("title");
            Label section = new Label("MOTORES"); section.getStyleClass().add("eyebrow");
            ToggleGroup group = new ToggleGroup();
            HBox tabs = new HBox(8); tabs.setId("settings-engines");
            VBox panels = new VBox();
            engineCards.forEach((id, card) -> {
                ToggleButton tab = new ToggleButton(card.name()); tab.setToggleGroup(group);
                tab.setId("settings-" + id); tab.getStyleClass().add("settings-tab");
                tab.setOnAction(ignored -> showSettingsEngine(id));
                tab.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == javafx.scene.input.KeyCode.ENTER) { tab.fire(); event.consume(); }
                });
                settingsTabs.put(id, tab); tabs.getChildren().add(tab); panels.getChildren().add(card.settingsView());
            });
            VBox body = new VBox(16, title, section, tabs, panels); body.setMinWidth(0);
            ScrollPane scroll = scrollPane(body, "settings-scroll"); scroll.setId("settings-scroll");
            scroll.setPrefViewportWidth(Math.min(600, stage.getScene().getWidth() - 100));
            scroll.setPrefViewportHeight(Math.min(560, stage.getScene().getHeight() - 150));
            pane.setContent(scroll);
            ButtonType close = new ButtonType("Volver al home", ButtonBar.ButtonData.CANCEL_CLOSE);
            pane.getButtonTypes().add(close); pane.lookupButton(close).setId("close-settings");
            engineSettings.setOnHidden(ignored -> settingsButton.requestFocus());
        }
        showSettingsEngine(initial != null ? initial : engineCards.keySet().iterator().next());
        if (!engineSettings.isShowing()) {
            engineSettings.show();
            engineSettings.setWidth(Math.min(680, stage.getWidth() - 48));
            engineSettings.setHeight(Math.max(320, Math.min(680, stage.getHeight() - 48)));
        }
    }

    private void showSettingsEngine(EngineId selected) {
        settingsTabs.get(selected).setSelected(true);
        engineCards.forEach((id, card) -> {
            card.settingsView().setVisible(id.equals(selected)); card.settingsView().setManaged(id.equals(selected));
        });
    }

    private VBox buildDropZone() {
        dropTitle.getStyleClass().add("drop-title"); dropTitle.setWrapText(true);
        dropTitle.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        dropHint.getStyleClass().add("muted"); dropHint.setWrapText(true);
        dropHint.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        dropTitle.setAlignment(Pos.CENTER); dropHint.setAlignment(Pos.CENTER);
        selectDocumentButton.setId("select-pdf"); selectDocumentButton.getStyleClass().add("primary-button");
        selectDocumentButton.setOnAction(ignored -> chooseDocument());
        selectedDocumentLabel.setId("selected-pdf"); selectedDocumentLabel.setMinWidth(0);
        selectedDocumentLabel.setMaxWidth(Double.MAX_VALUE);
        selectedDocumentLabel.setAlignment(Pos.CENTER); selectedDocumentLabel.setVisible(false); selectedDocumentLabel.setManaged(false);
        selectedDocumentLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        uploadIcon = new javafx.scene.layout.StackPane(
                icon("M16 24 L16 3 M8 11 L16 3 L24 11 M3 20 L3 29 L29 29 L29 20", "upload-icon"));
        uploadIcon.getStyleClass().add("upload-circle");
        uploadIcon.setMinSize(72, 72); uploadIcon.setMaxSize(72, 72);
        dropZone.getChildren().setAll(uploadIcon, dropTitle, dropHint, selectDocumentButton, selectedDocumentLabel);
        dropZone.setId("pdf-drop-zone"); dropZone.setMinWidth(0);
        dropZone.setMinHeight(240); dropZone.setPrefHeight(300);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.getStyleClass().add("drop-zone");
        dropZone.setOnDragOver(event -> acceptDocumentDrag(event, dropZone));
        dropZone.setOnDragExited(event -> dropZone.getStyleClass().remove("drop-zone-active"));
        dropZone.setOnDragDropped(event -> receiveDocumentDrop(event, dropZone));
        return dropZone;
    }

    private VBox buildStatusPanel() {
        progress.setVisible(false);
        progress.setPrefSize(34, 34);
        runningEngine.setId("conversion-engine"); elapsedLabel.setId("conversion-elapsed");
        runningAdvice.setId("conversion-advice"); runningAdvice.setWrapText(true);
        runningAdvice.setMinHeight(Region.USE_PREF_SIZE);
        VBox stateText = new VBox(4, runningEngine, phaseLabel, elapsedLabel);
        stateText.setMinWidth(0); HBox.setHgrow(stateText, Priority.ALWAYS);
        HBox state = new HBox(12, progress, stateText);
        state.setAlignment(Pos.CENTER_LEFT);

        logs.setEditable(false);
        logs.setWrapText(true); logs.setPrefRowCount(7);
        logs.setPromptText("Los logs del motor aparecerán aquí.");
        TitledPane details = new TitledPane("Detalles técnicos", logs);
        details.setExpanded(false); details.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        cancelButton.setDisable(true);
        cancelButton.setOnAction(ignored -> {
            cancelling = true;
            phaseLabel.setText("Cancelando...");
            cancelButton.setDisable(true);
            cancelConversion.cancel();
        });
        openOutputButton.setOnAction(ignored -> openOutputDirectory());
        HBox actions = new HBox(10, openOutputButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        phaseLabel.setWrapText(true); phaseLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox panel = new VBox(10, conversionStatus, details);
        completedOutput.setId("conversion-output"); completedOutput.setWrapText(true);
        completedOutput.setMinHeight(Region.USE_PREF_SIZE);
        conversionStatus.getChildren().setAll(completedOutput, actions);
        conversionStatus.setVisible(false); conversionStatus.setManaged(false);
        liveStatus.getChildren().setAll(state, runningAdvice, cancelButton);
        liveStatus.setVisible(false); liveStatus.setManaged(false);
        panel.getStyleClass().add("status-panel");
        return panel;
    }

    private VBox buildDocumentCard() {
        documentCard.setId("document-card"); documentCard.getStyleClass().add("destination-panel");
        documentSummary.setId("document-summary"); documentAdvice.setId("document-advice");
        documentWarning.setId("document-warning"); conversionSummary.setId("conversion-summary");
        for (Label label : java.util.List.of(documentSummary, documentAdvice, documentWarning, conversionSummary)) {
            label.setWrapText(true); label.setMinWidth(0); label.setMaxWidth(Double.MAX_VALUE);
            label.setMinHeight(Region.USE_PREF_SIZE);
        }
        useSuggestedEngine.setId("use-suggested-engine");
        useSuggestedEngine.setOnAction(ignored -> {
            if (document == null && selectedDocument != null) {
                selectEngine(new EngineId(DocumentFormats.isImage(selectedDocument) ? "marker" : "markitdown"));
            } else if (document != null) {
                String id = advice().suggestedEngine();
                if (!id.isBlank()) selectEngine(new EngineId(id));
            }
        });
        documentCard.getChildren().setAll(documentSummary, documentAdvice, documentWarning, useSuggestedEngine, conversionSummary);
        documentCard.setVisible(false); documentCard.setManaged(false);
        return documentCard;
    }

    private DocumentAdvice advice() {
        var lightweight = engineCards.get(new EngineId("markitdown"));
        return DocumentAdvice.forDocument(document, selectedEngineId, lightweight != null && lightweight.ready());
    }

    private void refreshDocument() {
        documentCard.setVisible(selectedDocument != null); documentCard.setManaged(selectedDocument != null);
        useSuggestedEngine.setVisible(false); useSuggestedEngine.setManaged(false);
        documentWarning.setText("");
        if (preflightState == PreflightState.ANALYZING) {
            documentSummary.setText("Analizando documento..."); documentAdvice.setText("El análisis se realiza localmente.");
        } else if (preflightState == PreflightState.READY && document != null) {
            String size = document.fileSize() < 1048576 ? "%.1f KB".formatted(document.fileSize() / 1024.0)
                    : "%.1f MB".formatted(document.fileSize() / 1048576.0);
            documentSummary.setText("Documento · %d páginas · %s\n%s (estimación)\nTexto seleccionable: %s".formatted(
                    document.pageCount(), size, documentTypeName(),
                    document.selectableTextDetected() ? "detectado" : "no detectado")
                    + (document.sampled() ? "\nMuestra de " + document.sampledPages() + " páginas distribuidas." : ""));
            var advice = advice(); documentAdvice.setText(advice.message());
            documentWarning.setText(document.longDocument() ? "Documento largo. El tiempo de conversión dependerá del motor y del rendimiento del equipo." : "");
            if (!advice.suggestedEngine().isBlank()) {
                var id = new EngineId(advice.suggestedEngine()); var card = engineCards.get(id);
                boolean available = card != null && card.ready() && !id.equals(selectedEngineId);
                useSuggestedEngine.setText("Usar " + engineName(id));
                useSuggestedEngine.setVisible(available); useSuggestedEngine.setManaged(available);
                useSuggestedEngine.setDisable(conversionBusy || engineBusy);
            }
        } else if (preflightState == PreflightState.READY && selectedDocument != null) {
            documentSummary.setText("Documento · " + DocumentFormats.extension(selectedDocument).toUpperCase(java.util.Locale.ROOT));
            documentAdvice.setText(DocumentFormats.isImage(selectedDocument)
                    ? "Marker reconocerá el texto de la imagen mediante OCR."
                    : "Conversión directa a Markdown. El análisis de páginas y OCR previo se aplica a PDF.");
            if (java.util.List.of("gif", "tif", "tiff").contains(DocumentFormats.extension(selectedDocument)))
                documentWarning.setText("En imágenes con varios cuadros o páginas, Marker procesa el primer cuadro.");
            if (selectedEngineId != null && !DocumentFormats.supports(selectedEngineId, selectedDocument)) {
                EngineId compatible = new EngineId(DocumentFormats.isImage(selectedDocument) ? "marker" : "markitdown");
                documentWarning.setText("Este formato requiere " + engineName(compatible) + ".");
                var card = engineCards.get(compatible);
                if (card != null && card.ready()) {
                    useSuggestedEngine.setText("Usar " + engineName(compatible));
                    useSuggestedEngine.setVisible(true); useSuggestedEngine.setManaged(true);
                    useSuggestedEngine.setDisable(conversionBusy || engineBusy);
                }
            }
        } else if (preflightState == PreflightState.FAILED) {
            documentSummary.setText("Análisis previo no disponible");
            documentAdvice.setText(preflightBlock == null
                    ? "No se pudo analizar previamente el documento. Podés intentar convertirlo igualmente."
                    : ConversionMessages.forCode(preflightBlock));
        }
        String ocr = selectedEngineId != null && selectedEngineId.value().equals("marker")
                ? forceOcr.isSelected() ? "OCR: forzado manualmente" : document != null && document.likelyNeedsOcr()
                    ? "OCR: automático cuando Marker lo necesite" : "OCR: automático" : "OCR: no disponible en MarkItDown";
        conversionSummary.setText("Motor: " + engineName(selectedEngineId)
                + (DocumentFormats.isPdf(selectedDocument) || DocumentFormats.isImage(selectedDocument) ? " · " + ocr : ""));
        documentWarning.setVisible(!documentWarning.getText().isBlank());
        documentWarning.setManaged(documentWarning.isVisible());
    }

    private String documentTypeName() {
        return switch (document.type()) {
            case DIGITAL -> "PDF digital"; case SCANNED -> "PDF escaneado";
            case MIXED -> "PDF mixto"; case UNKNOWN -> "Tipo indeterminado";
        };
    }
    private static String engineName(EngineId id) {
        return id == null ? "Sin seleccionar" : id.value().equals("marker") ? "Marker" : "MarkItDown";
    }
    private void beginPreflight() {
        long revision = ++selectionRevision;
        document = null; preflightBlock = null;
        analyzeDocument.cancel();
        if (!DocumentFormats.isPdf(selectedDocument)) {
            preflightState = PreflightState.READY;
            updateConvertState();
            return;
        }
        preflightState = PreflightState.ANALYZING;
        updateConvertState();
        analyzeDocument.analyze(selectedDocument).whenComplete((result, error) -> Platform.runLater(() -> {
            if (revision != selectionRevision) return;
            if (error == null) { document = result; preflightState = PreflightState.READY; }
            else {
                preflightState = PreflightState.FAILED;
                Throwable cause = unwrap(error);
                if (cause instanceof ConversionException failure && (failure.code() == ErrorCode.PDF_INVALID || failure.code() == ErrorCode.FILE_INACCESSIBLE))
                    preflightBlock = failure.code();
                appendLog("PREFLIGHT", cause.toString());
                org.slf4j.LoggerFactory.getLogger(getClass()).debug("Preflight unavailable", cause);
            }
            updateConvertState();
        }));
    }

    public void close() { selectionRevision++; analyzeDocument.close(); elapsedTimer.stop(); if (engineSettings != null) engineSettings.close(); }

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

    private void chooseDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar documento");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos compatibles",
                DocumentFormats.allExtensions().stream().map(ext -> "*." + ext).toList()));
        if (selectedEngineId != null) chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Formatos de " + engineName(selectedEngineId), DocumentFormats.extensions(selectedEngineId).stream().map(ext -> "*." + ext).toList()));
        chooser.setInitialDirectory(validDirectory(lastInputDirectory));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            selectDocument(file.toPath());
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
                    ConversionMessages.forCode(ErrorCode.PERMISSION_DENIED));
            return;
        }

        if (selectedEngineId == null && developmentOverrideAvailable) selectedEngineId = new EngineId("marker");
        saveSettings();
        setBusy(true);
        cancelling = false;
        liveStatus.setVisible(true); liveStatus.setManaged(true);
        runningEngine.setText("Motor: " + engineName(selectedEngineId));
        runningAdvice.setText(document != null && document.likelyNeedsOcr() && selectedEngineId.value().equals("marker")
                ? "Este documento puede necesitar OCR y tardar bastante. El motor sigue trabajando mientras no finalice o lo canceles." : "");
        completedOutput.setText("");
        conversionStatus.setVisible(true); conversionStatus.setManaged(true);
        logs.clear();
        appendLog("SYSTEM", "Iniciando conversión de " + selectedDocument.getFileName());
        conversionStartedAt = Instant.now();
        phaseLabel.setText("Procesando documento...");
        elapsedLabel.setText("Tiempo transcurrido: 00:00:00");
        elapsedTimer.playFromStart();

        ConversionRequest request = new ConversionRequest(
                selectedDocument, output, selectedEngineId, OutputFormat.MARKDOWN, selectedEngineId.value().equals("marker") && forceOcr.isVisible() && forceOcr.isSelected());
        startConversion.start(request, event -> Platform.runLater(() -> handleEvent(event)))
                .whenComplete((result, error) -> Platform.runLater(() -> finishConversion(result, error)));
    }

    private void handleEvent(ConversionEvent event) {
        switch (event) {
            case ConversionEvent.EngineStarted started ->
                    appendLog("SYSTEM", "Motor iniciado: " + started.engineName());
            case ConversionEvent.PhaseChanged phase -> { if (!cancelling) phaseLabel.setText(phase.phase()); }
            case ConversionEvent.LogReceived log -> appendLog(log.stream().name(), log.message());
            case ConversionEvent.OutputCreated output ->
                    appendLog("SYSTEM", "Archivo creado: " + output.path());
            case ConversionEvent.EngineStopped stopped ->
                    appendLog("SYSTEM", "Motor finalizado con código " + stopped.exitCode());
        }
    }

    private void finishConversion(ConversionResult result, Throwable error) {
        updateElapsedTime();
        elapsedTimer.stop();
        setBusy(false);
        if (error != null) {
            Throwable cause = unwrap(error);
            String detail = ConversionMessages.forError(cause);
            phaseLabel.setText("Conversión fallida");
            appendLog("ERROR", cause.toString());
            org.slf4j.LoggerFactory.getLogger(getClass()).error("Conversion failed", cause);
            showAlert(Alert.AlertType.ERROR, "No fue posible completar la conversión", detail);
            return;
        }

        if (result.status() == ConversionStatus.COMPLETED) {
            phaseLabel.setText("Conversión completada");
            completedOutput.setText(result.outputFiles().isEmpty() ? "Revisá la carpeta de salida."
                    : "Archivo generado: " + result.outputFiles().getFirst().getFileName() + "\n" + result.outputFiles().getFirst());
            completedOutput.setTooltip(new Tooltip(completedOutput.getText()));
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
            showAlert(Alert.AlertType.ERROR, "No se pudo completar la conversión", ConversionMessages.forCode(result.errorCode()));
        }
    }

    private String validateInputs() {
        if (selectedDocument == null || !DocumentFormats.supported(selectedDocument)) {
            return "Seleccioná un documento compatible.";
        }
        if (!Files.isRegularFile(selectedDocument) || !Files.isReadable(selectedDocument)) return ConversionMessages.forCode(ErrorCode.FILE_INACCESSIBLE);
        if (selectedEngineId != null && !DocumentFormats.supports(selectedEngineId, selectedDocument))
            return "Este formato no está disponible en " + engineName(selectedEngineId) + ". Elegí "
                    + (DocumentFormats.isImage(selectedDocument) ? "Marker" : "MarkItDown") + ".";
        if (preflightBlock != null) return ConversionMessages.forCode(preflightBlock);
        if (preflightState == PreflightState.ANALYZING) return "Analizando documento...";
        if (document != null && advice().incompatible()) return "Este PDF requiere OCR. Elegí Marker para convertirlo.";
        return validOutputDirectory() ? null : ConversionMessages.forCode(ErrorCode.PERMISSION_DENIED);
    }

    private void setBusy(boolean busy) {
        conversionBusy = busy;
        engineCards.values().forEach(card -> card.converting(busy));
        updateConvertState();
        cancelButton.setDisable(!busy);
        progress.setVisible(busy);
        progress.setManaged(busy);
        selectDocumentButton.setDisable(busy); changeDirectoryButton.setDisable(busy);
        openOutputButton.setDisable(busy); forceOcr.setDisable(busy);
        dropTitle.setText(busy ? "Procesando tu documento" : selectedDocument == null ? "Arrastrá tu documento" : "Documento seleccionado");
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
        else if (validationError != null) reason = selectedDocument == null ? "SELECCIONÁ UN DOCUMENTO PARA CONTINUAR" : validationError;
        else reason = "TODO LISTO PARA CONVERTIR";
        boolean enabled = !conversionBusy && !engineBusy && !startConversion.hasActiveConversion()
                && (ready || developmentOverrideAvailable) && validationError == null;
        convertButton.setDisable(!enabled);
        convertButton.setVisible(!conversionBusy); convertButton.setManaged(!conversionBusy);
        forceOcr.setVisible(selectedEngineId != null && selectedEngineId.value().equals("marker")
                && (selectedDocument == null || DocumentFormats.isPdf(selectedDocument) || DocumentFormats.isImage(selectedDocument)));
        forceOcr.setManaged(forceOcr.isVisible());
        actionHint.setText(reason);
        systemState.setText(conversionBusy ? "● CONVIRTIENDO DOCUMENTO" : engineBusy ? "● COMPROBANDO / PREPARANDO MOTOR"
                : ready || developmentOverrideAvailable ? "● LISTO PARA PROCESAR LOCALMENTE" : "○ INSTALÁ UN MOTOR PARA COMENZAR");
        if (selectedDocument == null) dropHint.setText("Formatos disponibles: " + String.join(", ",
                selectedEngineId == null ? DocumentFormats.allExtensions() : DocumentFormats.extensions(selectedEngineId)) + ".");
        selectDocumentButton.setTooltip(new Tooltip("Disponibles entre ambos motores: " + String.join(", ", DocumentFormats.allExtensions())));
        refreshDocument();
    }

    private void acceptDocumentDrag(DragEvent event, VBox dropZone) {
        if (!conversionBusy && event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!dropZone.getStyleClass().contains("drop-zone-active")) {
                dropZone.getStyleClass().add("drop-zone-active");
            }
        }
        event.consume();
    }

    private void receiveDocumentDrop(DragEvent event, VBox dropZone) {
        Path pdf = firstDocument(event.getDragboard());
        if (!conversionBusy && pdf != null) {
            selectDocument(pdf);
            event.setDropCompleted(true);
        } else {
            event.setDropCompleted(false);
            if (!conversionBusy) invalidDocument();
        }
        dropZone.getStyleClass().remove("drop-zone-active");
        event.consume();
    }

    private Path firstDocument(Dragboard dragboard) {
        if (!dragboard.hasFiles()) {
            return null;
        }
        return dragboard.getFiles().stream()
                .map(File::toPath)
                .filter(Files::isRegularFile)
                .filter(DocumentFormats::supported)
                .findFirst()
                .orElse(null);
    }

    /** Compatibility entrypoint for existing callers. */
    public void selectPdf(Path file) { selectDocument(file); }

    public void selectDocument(Path pdf) {
        if (conversionBusy) return;
        if (pdf == null || !Files.isRegularFile(pdf) || !Files.isReadable(pdf)
                || !DocumentFormats.supported(pdf)) {
            invalidDocument(); return;
        }
        selectedDocument = pdf.toAbsolutePath().normalize();
        lastInputDirectory = selectedDocument.getParent().toString();
        selectedDocumentLabel.setText(selectedDocument.getFileName().toString());
        selectedDocumentLabel.setTooltip(new Tooltip(selectedDocument.toString()));
        selectedDocumentLabel.setVisible(true); selectedDocumentLabel.setManaged(true);
        dropTitle.setText("Documento seleccionado"); dropHint.setText("Podés elegir otro archivo antes de convertir.");
        uploadIcon.setVisible(false); uploadIcon.setManaged(false);
        dropHint.setVisible(false); dropHint.setManaged(false);
        dropZone.prefHeightProperty().unbind(); dropZone.setMinHeight(180); dropZone.setPrefHeight(180);
        dropZone.getStyleClass().remove("drop-zone-invalid");
        selectDocumentButton.setText("CAMBIAR ARCHIVO");
        beginPreflight();
        saveSettings();
    }

    private void invalidDocument() {
        selectionRevision++; analyzeDocument.cancel(); document = null; preflightBlock = null; preflightState = PreflightState.IDLE;
        selectedDocument = null;
        selectedDocumentLabel.setVisible(false); selectedDocumentLabel.setManaged(false);
        dropTitle.setText("Elegí un documento compatible");
        dropHint.setText("Elegí un documento accesible de los formatos disponibles.");
        dropHint.setVisible(true); dropHint.setManaged(true);
        selectDocumentButton.setText("SELECCIONAR ARCHIVO");
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
        org.slf4j.LoggerFactory.getLogger(getClass()).info("[{}] {}", source, message);
        logs.appendText("[" + source + "] " + message + System.lineSeparator());
        if (logs.getLength() > 200_000) logs.deleteText(0, logs.getLength() - 150_000);
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
        Label message = new Label("Convertí tus documentos a Markdown en tu equipo.\n\n"
                + "ParseForge utiliza motores de conversión instalables. Para comenzar, instalá un motor desde el panel Motores de conversión.\n\n"
                + "Marker permite estructura avanzada y OCR. MarkItDown admite PDF digital, Word, EPUB, presentaciones, planillas y texto. Elegí un motor listo para convertir.\n\n"
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
