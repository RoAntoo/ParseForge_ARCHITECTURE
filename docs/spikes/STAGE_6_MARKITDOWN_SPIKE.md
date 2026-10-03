# Stage 6 — MarkItDown private Windows runtime spike

Date: 2026-10-01, America/Buenos_Aires. Result: **HOST PASS; CLEAN VM PENDING**.
The user will validate the clean Windows VM manually. Host isolation does not
establish clean-VM compatibility.

Official source: https://github.com/microsoft/markitdown. Tested published wheel:
**MarkItDown 0.1.8**, **embedded CPython 3.12.10 x64**, bootstrap **pip 25.0.1**.
Only `markitdown[pdf]` was resolved. The 32 transitive wheels, exact versions,
immutable HTTPS artifact URLs, sizes and SHA-256 hashes are recorded in
`STAGE_6_MARKITDOWN_SPIKE.json` and the shipped manifest/requirements lock.
No `[all]`, plugins, cloud integrations or OCR dependencies were requested.
The core dependency Magika includes ONNX Runtime and its bundled detection
assets; these are upstream required dependencies, not additional OCR models.

## Clean private installation on the host

`scripts/spikes/markitdown-spike.py` creates a new directory with spaces and ñ
under `build/stage6-spike`, downloads/hash-checks CPython and pip, extracts
CPython's stdlib and resolves PDF wheels. It then installs entirely offline with
`--no-index --only-binary=:all: --require-hashes --no-compile`.
Development Python only orchestrates the spike. All package operations/probes
use the new private interpreter with `-I -X utf8 -u -B`, a scrubbed environment
and a PATH containing only its runtime. The runtime audit confirms every
interpreter/prefix/import path is inside the isolated root. No global Python,
Java, Marker, WinGet or llama.cpp is involved in conversion.

| Measurement | Actual result |
| --- | ---: |
| Private CPython including expanded stdlib | 31,400,978 bytes |
| PDF packages plus pip bootstrap | 162,137,280 bytes |
| Installed runtime, without mutable metadata/logs | 193,538,258 bytes (~193.5 MB decimal) |
| Downloaded CPython + pip + 32 wheels | 80,164,780 bytes (~80.2 MB decimal) |
| Fresh preparation + resolution/download/offline installation | 29.25 seconds |
| Small one-page digital PDF | 2.203 seconds |
| Three-page PDF, Unicode filename and spaced path | 2.047 seconds |

These times describe this host and connection; the UI makes no universal time
promise. A later real installation through ParseForge's UI took 17.28 seconds
and occupied 193,565,874 bytes including engine metadata and logs.

## CLI and malformed PDF finding

The documented `markitdown input.pdf -o output.md` succeeds on both digital
fixtures. However, the unmodified CLI returns **0** for the corrupt fixture:
its general converter can fall back to text. This does not satisfy a PDF-only
application's error contract.

ParseForge therefore validates the PDF container with the already installed
`PDFDocument(PDFParser(file))` before calling `markitdown.__main__.main()` in the
same process. The actual `-c` entrypoint is recorded verbatim in the JSON evidence
and `ManagedMarkItDownRuntime.ENTRYPOINT`. Input and explicit `-o` output remain
separate arguments; no shell or interpolated paths are used. This narrow adapter
adds no dependency. The corrupt fixture then returns a nonzero exit code and
ParseForge reports failure instead of successful Markdown.

The final spike passed digital, multipage, explicit output, Unicode/spaced
paths, invalid-PDF errors and cancellation of a 15,000-page generated fixture.
The CLI process terminates after cancellation. Real Java executor cancellation
and forced JVM termination using the Windows Job Object are also validated in
the Stage 6 host evidence. Fixtures are generated from stdlib, contain no
personal documents and stay outside Git.

## Reproducibility and limits

The app installs reviewed artifacts from
`src/main/resources/engines/markitdown-windows-x64.json` and
`markitdown-requirements.lock`; it never resolves dependencies dynamically.
The spike script is a maintenance generator; rerunning it resolves dependencies
again and requires reviewing the resulting manifest/lock diff.

The `_pth` file has explicit LF bytes so the spike and Java installer hash the
same file. Staging-bound Windows pip launchers are removed; conversion always
uses the private interpreter. Critical hashes include the static stdlib,
package files and notices; pip's installation-path-dependent RECORD is excluded.
No VM, actual Windows display scaling or scanned-PDF OCR capability is claimed
for MarkItDown. The clean VM checklist remains pending.
