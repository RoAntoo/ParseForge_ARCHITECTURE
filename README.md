# ParseForge

ParseForge is a Windows-first desktop app that converts PDFs to Markdown using
Marker as its first engine. Documents are processed locally.

## Managed Marker

Open **Configuración > Motores** and click **Instalar Marker**. ParseForge
downloads pinned private CPython, llama.cpp CPU, locked packages and private
models. It validates SHA-256, prepares staging, runs health checks and activates
the runtime. Normal conversion needs no Python, Marker, pip, WinGet, manual
executable path or administrator privileges.

Marker installs under `%LOCALAPPDATA%\ParseForge\engines\marker`.
It uses approximately 3.2 GB; have at least 7 GB free for preparation/repair.
Internet is needed to install or repair. Model revisions are pinned; the Hugging
Face cache runs offline during conversion. Repair safely reinstalls. Uninstall
requires confirmation and removes private models/caches. Documents and outputs
remain independent of the engine directory.

Requirements: Windows x64 and Microsoft Visual C++ Runtime x64. Import/CLI health
checks detect native-load failures; prerequisite distribution and validation on
clean Windows remain release work. JDK/Maven are development requirements.
Application packaging is outside Stage 3C.

## Development

JDK 21+ and Maven 3.9+:

```powershell
mvn javafx:run
mvn clean verify
```

Optional isolated data root:

```powershell
mvn javafx:run "-Dparseforge.dataDir=$env:USERPROFILE\Documents\ParseForge-test"
```

`-Dparseforge.marker.override=C:\...\marker_single.exe` is an explicit
development-only override, not selected in the normal UI.

## Architecture and validation

Domain separates engines from conversion. Application ports/use cases coordinate
lifecycle and conversion. Infrastructure owns HTTPS, hashes, filesystem and
tracked processes. JavaFX depends on use cases with explicit injection.

See [Stage 3C result](docs/STAGE_3C_RESULT.md) and [ADRs](docs/ADR).
The [3B spike](docs/spikes/marker-autonomous-runtime.md) remains historical evidence.
Runtimes, models, wheels and validation outputs remain outside Git.

Privacy: ParseForge does not upload documents or inherit external LLM/API
credentials. Internet is used to install the engine and download models.
