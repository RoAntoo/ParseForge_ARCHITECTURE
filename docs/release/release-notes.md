# ParseForge 0.1.0 — candidate

Status: PACKAGE BUILT — VM VALIDATION PENDING. No public release has been published.

- Windows x64 installer and portable ZIP include a private Temurin Java runtime.
- Install/repair Marker or MarkItDown from the Motores de conversión sidebar with verified downloads.
- Compact engine cards with exclusive ready-engine selection, independent accordion details and persisted safe fallback.
- Select a ready engine by clicking its card or radio; internal controls keep their own actions.
- MarkItDown 0.1.8 uses independent private CPython 3.12.10 and locked PDF dependencies (~194 MB installed, ~80 MB downloaded).
- Ligero / Avanzado indicate scope/resources. Forzar OCR is available only in Marker's details.
- Digital, Unicode/spaced-path PDF conversion, malformed PDF detection and cancellation for MarkItDown.
- Cream/navy workspace with PDF drop area and destination path tooltips.
- First-start welcome dialog with a persisted "No volver a mostrar" preference.
- Independent vertical scroll for the workspace/sidebar; conversion action stays visible.
- Convertir reflects engine, input, destination and operation readiness.
- Download feedback shows actual bytes, rolling speed and approximate ETA when stable.
- Preparation stages show elapsed time and indeterminate progress.
- Marker installation/repair shows an estimate of 20 minutes or more (it may finish sooner).
- Final runtime probes are optional and unchecked by default; file/model integrity
  checks always run. Selected probes report the failing step and preserve a
  separate health-check log with output and timeout details.
- Documents remain local. Marker and models (~3.2 GB) are downloaded separately.
- Windows Job Objects contain private subprocesses on application crashes.
- Reinstall and app uninstall preserve settings, engines, models and documents.

Marker requires Microsoft Visual C++ Runtime x64. If absent, ParseForge blocks
engine installation with the official Microsoft download address. The setup
does not redistribute this prerequisite. Model licenses contain commercial
restrictions; see THIRD_PARTY_NOTICES.md before business use.

Pending: clean Windows validation, digital/OCR conversion and process cleanup
from the installed application, plus upgrade/uninstall checks in the VM.
Installer is unsigned; there is no updater or commercial signing.
