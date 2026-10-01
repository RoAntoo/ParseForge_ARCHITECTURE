# ParseForge 0.1.0 — candidate

Status: PACKAGE BUILT — VM VALIDATION PENDING. No public release has been published.

- Windows x64 installer and portable ZIP include a private Temurin Java runtime.
- Install/repair Marker from Configuración > Motores with verified downloads.
- Download feedback shows actual bytes, rolling speed and approximate ETA when stable.
- Preparation stages show elapsed time and indeterminate progress.
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
