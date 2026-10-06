# ParseForge 0.1.0 — candidate

Status: PACKAGE BUILT — VM VALIDATION PENDING. No public release has been published.

- Document inputs: DOCX, EPUB, PPTX, XLSX/XLS, HTML, text, Outlook MSG and ZIP via MarkItDown; image OCR via Marker. File chooser, drag/drop, engine compatibility and output naming support all enabled formats.
- Existing PDF-only MarkItDown installations need one repair to install the new verified offline dependencies.
- Stage 8: clean header with Ajustes, compact engine cards and a document-first home.
- Separate engine settings dialog with per-state actions, version and size details; uninstall confirmation retained.
- Main Convertir a Markdown CTA uses #F63A48, white text and distinct interaction states.
- Marker Forzar OCR remains a per-conversion option beside the document flow.
- Shared cream/navy color tokens, visible keyboard focus and responsive scrolling at 100/125/150%.
- Local background PDF preflight: page count, size, estimated type, selectable text and OCR advice.
- Explicit contextual engine recommendations, with no automatic selection; scanned PDFs require Marker.
- Distributed sampling for long PDFs, eight-second analysis timeout and nonblocking fallback for inconclusive inspection.
- Persistent conversion sidebar with engine, elapsed time, real phase and Cancelar; indeterminate progress, no synthetic ETA.
- Clear typed errors, cancellation as a normal outcome, destination/temp low-space checks for large inputs and bounded UI logs.

- Windows x64 installer and portable ZIP include a private Temurin Java runtime.
- Install/repair Marker or MarkItDown from Ajustes with verified downloads.
- Compact engine cards with exclusive ready-engine selection, separate technical settings and persisted safe fallback.
- Select a ready engine by clicking its card or radio; internal controls keep their own actions.
- MarkItDown 0.1.8 uses independent private CPython 3.12.10 and locked PDF and Office dependencies (~245 MB installed).
- Ligero / Avanzado indicate scope/resources. Forzar OCR is available in the conversion flow when Marker is selected.
- Digital, Unicode/spaced-path PDF conversion, malformed PDF detection and cancellation for MarkItDown.
- Cream/navy workspace with document drop area and destination path tooltips.
- First-start welcome dialog with a persisted "No volver a mostrar" preference.
- Independent vertical scroll for the workspace/sidebar; Convertir is reachable by scrolling; Cancelar stays visible during conversion.
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
