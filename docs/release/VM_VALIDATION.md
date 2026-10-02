# Clean Windows release validation

Status: **VM VALIDATION PENDING**. No clean-VM result is claimed.
Candidate: ParseForge 0.1.0 with Stage 5 UI. Date: 2026-10-01, America/Buenos_Aires.
The host build and smoke results are in STAGE_4_RESULT.md and STAGE_5_RESULT.md, separate from this checklist.

Record before execution:

| Field | Value |
| --- | --- |
| Windows edition/version/build, x64 | Pending |
| VM provider / clean snapshot | Pending |
| RAM / CPU cores | Pending |
| Free disk space | Pending; at least 7 GB for Marker staging |
| Java/Python/Marker/llama.cpp/dev tools absent | Pending |
| Visual C++ Runtime initially absent or present | Pending |
| Setup SHA-256 | Copy from candidate SHA256SUMS.txt and verify in VM |
| Tester / date | Pending |

Copy only setup and SHA256SUMS into the VM. Launch the installed EXE from a
shortcut, without Maven or developer tools. Keep setup/runtime logs on failures.
For every checkbox record PASS/FAIL, detail and optional screenshot; unchecked
means untested, not failure.

- [ ] Double-click setup; choose Spanish and installation folder.
- [ ] Install without app elevation; desktop and Start shortcuts exist.
- [ ] Launch ParseForge.exe from shortcut; no Java/native launcher error.
- [ ] First launch shows the welcome dialog; close it and use the app normally.
- [ ] Select "No volver a mostrar al iniciar"; restart retains the preference.
- [ ] Main window has the Marker sidebar, PDF drop area and destination folder.
- [ ] Drop a real PDF, replace it and verify a non-PDF gets understandable feedback.
- [ ] Long PDF names/output paths truncate with tooltips; no horizontal overflow.
- [ ] At 800x500, scroll to every control; conversion action/footer remain visible.
- [ ] Review maximized/restored at 1920x1080 and 1366x768 (100%).
- [ ] Change actual Windows scaling to 125% and 150% at 1920x1080; restart and review dialogs/layout.
- [ ] Convertir is disabled with missing/broken/busy engine or missing/invalid input/destination.
- [ ] Marker starts NOT_INSTALLED; no installation-time document upload.
- [ ] Without VC runtime: engine install blocks before GB downloads with a clear message.
- [ ] Install official Microsoft x64 VC runtime; restart ParseForge.
- [ ] Install Marker from app; bytes/speed/ETA appear only when measurable/stable.
- [ ] Preparation uses indeterminate progress, elapsed time and approximate-time note.
- [ ] Installation completes READY; capture installation log and elapsed time.
- [ ] Digital PDF converts to readable Markdown.
- [ ] Forzar OCR produces readable Markdown.
- [ ] Cancel conversion; no private Python/Surya/llama processes remain.
- [ ] Kill ParseForge.exe during conversion; Job Object leaves no engine processes.
- [ ] Restart preserves READY, separate input/output folders, engine and language.
- [ ] Repair Marker reaches READY; failed/cancelled repair preserves prior engine.
- [ ] Uninstall Marker reaches NOT_INSTALLED; user documents/outputs survive.
- [ ] Reinstall Marker reaches READY.
- [ ] Reinstall setup over the same destination; config/engines/models survive.
- [ ] Uninstall app; app files/shortcuts removed; documents/settings/engines survive.
- [ ] Reinstall app; retained Marker/config are discovered.

## Results and errors

Not executed. Record actual errors, logs and screenshots here. The setup is
unsigned; record any SmartScreen prompt separately from functional failures.
Commercial model-use terms must be reviewed for the intended deployment.

## Final decision

**VM VALIDATION PENDING**. Mark RELEASE READY only after all required functional
checks pass with real evidence. If a required check fails, record BLOCKED and
the concrete cause. Do not infer a clean-VM pass from the host smoke.
