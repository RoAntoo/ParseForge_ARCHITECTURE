# ParseForge

ParseForge is a Windows-first, local-first desktop application for converting PDF documents to editable formats. The first engine integration targets [Marker](https://github.com/datalab-to/marker).

## Current status

The repository contains the first executable proof of concept:

- JavaFX PDF selection and drag and drop;
- configurable Marker executable and output directory;
- asynchronous process execution with live stdout/stderr;
- cancellation of the process tree;
- persisted local UI settings;
- clean boundaries between domain, application, infrastructure, and presentation.

Marker installation is intentionally not automated yet. This is the next major milestone described in `ParseForge_ARCHITECTURE_MVP.md`.

The [Stage 3B autonomous runtime spike](docs/spikes/marker-autonomous-runtime.md)
verifies private CPython and llama.cpp, Java conversion/cancellation, and hashed
artifacts. Its scripts are experimental and are not connected to the UI.

## Requirements for development

- JDK 21 or newer;
- Maven 3.9+;
- an existing Marker installation to perform a real conversion.

## Run

```powershell
mvn javafx:run
```

In the app, select `marker_single.exe` (or the equivalent launcher from the active Marker environment), a PDF, and an output directory.

## Test and package

```powershell
mvn test
mvn package
```

## Privacy

Documents are sent only to the selected local conversion engine. ParseForge does not upload document contents.
