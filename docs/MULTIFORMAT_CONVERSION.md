# Document inputs to Markdown

ParseForge now accepts the union of its bundled engine profiles in the file chooser and drag/drop. `DocumentFormats` defines the same compatibility rules for presentation and engine execution. An incompatible selection disables conversion and offers the compatible ready engine without changing the user's selection automatically.

- Marker: PDF and PNG, JPEG, GIF, BMP, TIFF, WEBP images. The upstream image provider processes the first frame of multi-frame images; the home shows this limitation for GIF/TIFF.
- MarkItDown: PDF, DOCX, EPUB, PPTX, XLSX, XLS, HTML/HTM, TXT, MD, CSV, JSON, XML, Outlook MSG and ZIP.

PDFBox preflight remains specific to PDFs. Other documents bypass it, invalidate pending PDF inspections, and receive format-specific conversion advice. The existing `selectPdf` entrypoint and CSS IDs remain compatible with older UI drivers; new callers can use `selectDocument`.

MarkItDown's pinned profile now includes `pdf,docx,pptx,xlsx,xls,outlook` extras, with 44 wheels, SHA-256 checks and critical file inventory. Missing Office dependencies make an older PDF-only installation require repair. The health check imports the Office converters as well as the PDF components. Marker Office/EPUB providers are not enabled because they require additional native rendering dependencies; these inputs use MarkItDown in this build. DOC, ODT, RTF, audio/video and cloud services remain outside the enabled local profiles.

The MarkItDown wrapper validates PDF, ZIP-based document containers, XLS workbooks and MSG containers before invoking the upstream CLI. This prevents malformed binary inputs from falling back to successful plain-text conversion. All input extensions use a common output stem, e.g. `book.epub` produces `book.md`. Existing output staging and failure preservation remain in place.

## Verification on Windows, 2026-10-05

- Existing automated tests run with Java 21.
- `MultiFormatRealTest`: real private MarkItDown conversion for all 15 enabled extensions, with Unicode filenames; damaged PDF, DOCX, EPUB, PPTX, XLSX, XLS, MSG and ZIP fail and preserve earlier Markdown.
- `MarkerImageRealTest`: installed private Marker recognizes “ParseForge image OCR” from a generated PNG.
- `MultiFormatUiTest`: non-PDF selection bypasses PDF preflight, incompatible engines disable conversion, suggested engine buttons enable it, image OCR options update correctly, and unsupported input is rejected.
- All 44 wheels downloaded with the hash lock and installed into an isolated clean package directory. 7,304 critical package files match the manifest; bootstrap pip and CPython's site-packages README are verified against the private base runtime.
- Native UI screenshot inspected at `build/multiformat/ui/word-selected.png`. Real conversion reports and installation audit remain under `build/multiformat`.

The real engine and native UI tests are opt-in and do not install or repair user engines. The maintenance scripts under `scripts/engines` prepare isolated build fixtures and dependency evidence; they are not part of the application's conversion or installation flow.
