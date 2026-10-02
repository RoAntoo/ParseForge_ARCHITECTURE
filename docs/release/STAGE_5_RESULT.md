# Stage 5 — UI/UX polish

Candidate: **ParseForge 0.1.0**. Date: 2026-10-01, America/Buenos_Aires.
Status: **HOST UI VERIFIED — CLEAN VM VALIDATION PENDING**.

The supplied Stage 5 brief and screenshot guided the layout: navy/cream sidebar,
PDF workspace, destination card and stable conversion action/footer. Marker is
the only engine shown. Tesseract/DeepParse and the mockup's 2.4.0 version were
not added. The version label is generated from the Maven project version.

## Changes

- Marker card with radio selection, full-card mouse/keyboard interaction and
  explicit real state. Install, repair, cancel and advanced settings reuse the
  existing use cases; uninstall confirmation and optional probes remain available.
- Scrollable workspace and independently scrollable engine sidebar. Minimum
  window: 800x500 logical pixels, verified against long paths and expanded content.
  Initial window size respects the primary screen's available bounds.
- Wrapped text, truncated PDF/destination labels, full-path tooltips, focus/hover
  feedback and a neutral disabled conversion button.
- Ready engine, readable PDF, usable output folder and compatible idle state
  determine conversion availability. Existing creation of a new output folder
  is preserved; PDF/output changes are disabled during conversion.
- Welcome dialog with a scrollable body. Checking "No volver a mostrar al iniciar"
  persists `welcomeDialogVersion: 1`; unchecking leaves version 0 and the next
  launch can show the help again. Existing configuration/legacy fields are retained.
- Conversion feedback/cancellation and collapsible technical logs remain accessible.

The only Application change adds the welcome preference to `UserSettings`, with
compatible constructors. The existing JSON repository persists it and reads
older files as version 0. Persistence regression tests cover both cases.
No conversion/runtime/installer/engine infrastructure was rewritten.

## UI validation

`Stage5UiTest` is an opt-in Windows JavaFX test, using fixture engine/conversion
ports and real presentation controllers/use cases. It downloads no engine assets.
It verifies welcome persistence, no-engine/ready/broken states, repair, installation
cancel, real per-file vs indeterminate progress, invalid PDFs/output folders,
long paths, conversion/cancel/success/error feedback and conversion prerequisites.

Native JavaFX output scales were forced using `glass.win.uiScale`; the test reports
the observed output scale. This checks the renderer/layout at each scale, and does
not replace changing Windows display settings in the clean VM.

| Case | Host result |
| --- | --- |
| JavaFX output scale 1.0 / 1.25 / 1.5 | PASS |
| 1920x1080 physical-size equivalent, restored and maximized | Captured at each scale |
| 1366x768 restored window | Captured |
| Minimum width/height 800x500 | PASS: scroll, no horizontal overflow, Convertir visible |
| Truncated long input/destination path | PASS: complete tooltip retained |
| Marker card natural height and visible state | PASS |
| Onboarding hidden preference survives later file selection | PASS |
| Real PDF drag from Explorer and real Marker conversion in VM | Pending |

Run from a Windows desktop session with JDK 21:

```powershell
mvn test "-Dtest=Stage5UiTest" "-Dparseforge.uiTests=true" "-Dglass.win.uiScale=1.0"
mvn test "-Dtest=Stage5UiTest" "-Dparseforge.uiTests=true" "-Dglass.win.uiScale=1.25"
mvn test "-Dtest=Stage5UiTest" "-Dparseforge.uiTests=true" "-Dglass.win.uiScale=1.5"
```

Snapshots: `target/stage5-ui/<scale>`. The normal Maven suite skips the desktop
test unless explicitly enabled. Release build runs `mvn clean verify` under the
pinned Temurin JDK. Final package/smoke evidence is recorded in
`STAGE_5_EVIDENCE.json`; clean VM checks are in `VM_VALIDATION.md`.

## Package validation

- `scripts/release.ps1`: PASS, including `mvn clean verify` with 80 passing
  tests, no failures/errors and the opt-in desktop test skipped in that run.
  The desktop test subsequently passed at all three scales on the final source.
- Rebuilt setup and portable ZIP are in `build/release`, with SHA-256 in
  `SHA256SUMS.txt`. Packaged application JAR matches the verified build JAR.
- Packaged startup and UI smoke: PASS using private Java 21.0.12.1, a restricted
  PATH without global Java and isolated settings/engine roots. Windows Job Object,
  settings read/write and opening the actual application window passed.
- `images/app-window.png` captures the packaged application's initial
  NOT_INSTALLED state. Engine lifecycle/conversion UI test screenshots use
  fixture ports; they are not real Marker runtime validation.

## Remaining release validation

Copy the rebuilt setup and SHA256SUMS to the clean Windows VM. Validate first
launch, hide/restart welcome, actual Windows 100/125/150% display settings,
Explorer PDF drop, small windows, Marker install/repair and digital/OCR conversion.
No VM result or public-release readiness is claimed here.
