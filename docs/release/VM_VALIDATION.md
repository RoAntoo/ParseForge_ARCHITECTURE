# Clean Windows release validation

Status: **VM VALIDATION PENDING**. No clean-VM result is claimed.
Candidate: ParseForge 0.1.0 with Stage 6 MarkItDown and accordion UI. Date: 2026-10-01, America/Buenos_Aires.
The user will run the clean VM validation manually. Host evidence is in STAGE_6_RESULT.md.

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
- [ ] Main window shows compact Marker + MarkItDown cards, PDF drop area and destination folder.
- [ ] Chevrons expand details independently of radio selection; at most one card expands.
- [ ] Tab / Shift+Tab / Space / Enter support selection, expansion and lifecycle actions.
- [ ] Ligero / Avanzado indicators and explanatory tooltip are readable.
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
- [ ] MarkItDown starts NOT_INSTALLED and can expand even while unavailable.
- [ ] Install MarkItDown; progress uses actual per-file bytes or indeterminate preparation.
- [ ] MarkItDown reaches READY without global Python/Java, Marker or llama.cpp.
- [ ] Select MarkItDown; Forzar OCR is absent from its details.
- [ ] Convert a digital PDF and a multipage PDF with spaced/Unicode paths; verify expected .md content/name/destination.
- [ ] Corrupt PDF reports failure; controls return to usable state.
- [ ] Switch Marker → MarkItDown → Marker; conversion uses the selected engine.
- [ ] Restart retains MarkItDown selection when READY; unavailable selection falls back safely.
- [ ] Cancel a long MarkItDown conversion; private Python terminates without orphans.
- [ ] Force-close ParseForge during MarkItDown conversion; no private processes remain.
- [ ] Cancel MarkItDown installation; no partial READY runtime or orphan process remains.
- [ ] Verify / repair MarkItDown; it returns READY.
- [ ] Uninstall MarkItDown; Marker and user PDFs/Markdown remain usable.
- [ ] Reinstall MarkItDown; uninstall Marker; MarkItDown still converts.
- [ ] Reinstall Marker; both runtimes remain independent.
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
