package dev.parseforge.presentation.javafx.controller;

import dev.parseforge.application.port.out.ConversionEvent;
import dev.parseforge.application.port.out.UserSettingsRepository;
import dev.parseforge.application.settings.UserSettings;
import dev.parseforge.application.usecase.CancelConversionUseCase;
import dev.parseforge.application.usecase.StartConversionUseCase;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
import java.util.function.Consumer;

public final class MainController {
    private final Stage stage;
    private final StartConversionUseCase startConversion;
    private final CancelConversionUseCase cancelConversion;
    private final UserSettingsRepository settingsRepository;
    private final Consumer<Path> configureEngineExecutable;
    private final EngineId selectedEngineId;

    private final BorderPane root = new BorderPane();
    private final Label selectedPdfLabel = new Label("Ningún PDF seleccionado");
    private final TextField outputDirectoryField = new TextField();
    private final TextField markerExecutableField = new TextField();
    private final Button convertButton = new Button("CONVERTIR");
    private final Button cancelButton = new Button("Cancelar");
    private final Button openOutputButton = new Button("Abrir carpeta");
    private final ProgressIndicator progress = new ProgressIndicator(-1);
    private final Label phaseLabel = new Label("Listo");
    private final Label elapsedLabel = new Label("Tiempo: 00:00");
    private final TextArea logs = new TextArea();
    private final Timeline elapsedTimer;

    private Path selectedPdf;
    private Instant conversionStartedAt;

    public MainController(
            Stage stage,
            StartConversionUseCase startConversion,
            CancelConversionUseCase cancelConversion,
            UserSettingsRepository settingsRepository,
            Consumer<Path> configureEngineExecutable,
            EngineId selectedEngineId,
            UserSettings settings
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.startConversion = Objects.requireNonNull(startConversion, "startConversion");
        this.cancelConversion = Objects.requireNonNull(cancelConversion, "cancelConversion");
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
        this.configureEngineExecutable = Objects.requireNonNull(
                configureEngineExecutable, "configureEngineExecutable");
        this.selectedEngineId = Objects.requireNonNull(selectedEngineId, "selectedEngineId");

        markerExecutableField.setText(settings.markerExecutable());
        outputDirectoryField.setText(defaultOutputDirectory(settings.outputDirectory()));
        elapsedTimer = new Timeline(new KeyFrame(Duration.seconds(1), ignored -> updateElapsedTime()));
        elapsedTimer.setCycleCount(Timeline.INDEFINITE);
        buildView();
    }

    public Parent view() {
        return root;
    }

    private void buildView() {
        Label title = new Label("ParseForge");
        title.getStyleClass().add("title");
        Label privacy = new Label("Tus documentos se procesan localmente.");
        VBox header = new VBox(3, title, privacy);
        header.setPadding(new Insets(20, 24, 12, 24));
        root.setTop(header);

        VBox dropZone = buildDropZone();
        HBox outputRow = pathRow(outputDirectoryField, "Cambiar", this::chooseOutputDirectory);
        HBox markerRow = pathRow(markerExecutableField, "Seleccionar", this::chooseMarkerExecutable);

        TitledPane engineSettings = new TitledPane("Configuración de Marker", markerRow);
        engineSettings.setExpanded(markerExecutableField.getText().isBlank());
        engineSettings.setCollapsible(true);

        convertButton.getStyleClass().add("primary-button");
        convertButton.setOnAction(ignored -> startConversion());
        HBox conversionAction = new HBox(convertButton);
        conversionAction.setAlignment(Pos.CENTER);

        VBox form = new VBox(14,
                dropZone,
                new Label("Carpeta de salida"),
                outputRow,
                engineSettings,
                conversionAction);
        form.setPadding(new Insets(8, 24, 12, 24));

        VBox status = buildStatusPanel();
        VBox content = new VBox(10, form, status);
        VBox.setVgrow(status, Priority.ALWAYS);
        root.setCenter(content);
    }

    private VBox buildDropZone() {
        Label instruction = new Label("Arrastrá un PDF aquí");
        Label or = new Label("o");
        Button selectPdfButton = new Button("Seleccionar PDF");
        selectPdfButton.setOnAction(ignored -> choosePdf());
        selectedPdfLabel.setWrapText(true);

        VBox dropZone = new VBox(8, instruction, or, selectPdfButton, selectedPdfLabel);
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
        logs.setWrapText(false);
        logs.setPromptText("Los logs del motor aparecerán aquí.");
        VBox.setVgrow(logs, Priority.ALWAYS);

        cancelButton.setDisable(true);
        cancelButton.setOnAction(ignored -> {
            phaseLabel.setText("Cancelando...");
            cancelButton.setDisable(true);
            cancelConversion.cancel();
        });
        openOutputButton.setOnAction(ignored -> openOutputDirectory());
        HBox actions = new HBox(10, cancelButton, openOutputButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(10, state, new Label("Logs"), logs, actions);
        panel.getStyleClass().add("status-panel");
        panel.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(panel, Priority.ALWAYS);
        BorderPane.setMargin(panel, new Insets(0, 24, 20, 24));
        return panel;
    }

    private HBox pathRow(TextField field, String buttonText, Runnable action) {
        field.setEditable(false);
        HBox.setHgrow(field, Priority.ALWAYS);
        Button button = new Button(buttonText);
        button.setOnAction(ignored -> action.run());
        return new HBox(8, field, button);
    }

    private void choosePdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar documento PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf"));
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

    private void chooseMarkerExecutable() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar ejecutable del motor");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Ejecutables", "*.exe", "*.cmd", "*.bat"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            markerExecutableField.setText(file.getAbsolutePath());
            configureEngineExecutable.accept(file.toPath());
            saveSettings();
        }
    }

    private void startConversion() {
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

        Path executable = Path.of(markerExecutableField.getText()).toAbsolutePath().normalize();
        configureEngineExecutable.accept(executable);
        saveSettings();
        setBusy(true);
        logs.clear();
        appendLog("SYSTEM", "Iniciando conversión de " + selectedPdf.getFileName());
        conversionStartedAt = Instant.now();
        elapsedTimer.playFromStart();

        ConversionRequest request = new ConversionRequest(
                selectedPdf, output, selectedEngineId, OutputFormat.MARKDOWN);
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
                    ? "Marker finalizó correctamente. Revisá la carpeta de salida."
                    : "Conversión completada.");
        } else if (result.status() == ConversionStatus.CANCELLED) {
            phaseLabel.setText("Conversión cancelada");
            appendLog("SYSTEM", "Conversión cancelada por el usuario.");
        } else {
            phaseLabel.setText("Conversión fallida");
            String message = result.error().orElse("Marker no pudo completar la conversión.");
            appendLog("ERROR", message);
            showAlert(Alert.AlertType.ERROR, "Marker finalizó con un error", message);
        }
    }

    private String validateInputs() {
        if (selectedPdf == null || !Files.isRegularFile(selectedPdf)) {
            return "Seleccioná un archivo PDF válido.";
        }
        if (outputDirectoryField.getText().isBlank()) {
            return "Seleccioná una carpeta de salida.";
        }
        if (markerExecutableField.getText().isBlank()
                || !Files.isRegularFile(Path.of(markerExecutableField.getText()))) {
            return "Seleccioná un ejecutable válido para el motor.";
        }
        return null;
    }

    private void setBusy(boolean busy) {
        convertButton.setDisable(busy);
        cancelButton.setDisable(!busy);
        progress.setVisible(busy);
    }

    private void acceptPdfDrag(DragEvent event, VBox dropZone) {
        if (event.getGestureSource() != dropZone && firstPdf(event.getDragboard()) != null) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!dropZone.getStyleClass().contains("drop-zone-active")) {
                dropZone.getStyleClass().add("drop-zone-active");
            }
        }
        event.consume();
    }

    private void receivePdfDrop(DragEvent event, VBox dropZone) {
        Path pdf = firstPdf(event.getDragboard());
        if (pdf != null) {
            selectPdf(pdf);
            event.setDropCompleted(true);
        } else {
            event.setDropCompleted(false);
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

    private void selectPdf(Path pdf) {
        selectedPdf = pdf.toAbsolutePath().normalize();
        selectedPdfLabel.setText(selectedPdf.getFileName() + System.lineSeparator() + selectedPdf);
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
        long minutes = elapsed.toMinutes();
        long seconds = elapsed.minusMinutes(minutes).toSeconds();
        elapsedLabel.setText("Tiempo: %02d:%02d".formatted(minutes, seconds));
    }

    private void saveSettings() {
        settingsRepository.save(new UserSettings(
                markerExecutableField.getText(), outputDirectoryField.getText()));
    }

    private String defaultOutputDirectory(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return Path.of(System.getProperty("user.home"), "Documents", "ParseForge").toString();
    }

    private void setInitialDirectory(DirectoryChooser chooser, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        File directory = Path.of(path).toFile();
        if (directory.isDirectory()) {
            chooser.setInitialDirectory(directory);
        }
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
        alert.setTitle("ParseForge");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
