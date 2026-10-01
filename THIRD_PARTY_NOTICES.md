# Third-party notices — ParseForge 0.1.0

The application is Apache-2.0; this does not replace third-party licenses.
Review date: 2026-09-30. Installer contains the Java application and runtime,
not Marker, Python, llama.cpp, wheels, fonts or inference models.

## Distributed in the application

| Component | Version | License / source |
| --- | --- | --- |
| Eclipse Temurin / OpenJDK | 21.0.12.1+1 | GPLv2 with Classpath Exception; runtime/legal includes terms; https://adoptium.net/about/ |
| OpenJFX | 21.0.6 | GPLv2 with Classpath Exception; https://github.com/openjdk/jfx21u/tree/21.0.6%2B3 |
| Jackson annotations/core/databind | 2.18.2 | Apache-2.0; https://github.com/FasterXML/jackson |
| SLF4J | 2.0.16 | MIT; https://www.slf4j.org/license.html |
| Logback core/classic | 1.5.16 | EPL-1.0 or LGPL-2.1; choose EPL-1.0; https://logback.qos.ch/license.html |
| JNA / JNA platform | 5.16.0 | Apache-2.0 or LGPL-2.1; choose Apache-2.0; https://github.com/java-native-access/jna/tree/5.16.0 |
| ParseForge icon | 0.1.0 | Original project artwork, Apache-2.0; scripts/generate-icon.ps1 |

The build retains embedded license/notice files under third-party-licenses;
OpenJDK module notices remain in runtime/legal. The linked project repositories
provide corresponding source. The build inventory fixes dependency names and hashes.

## Downloaded by the user for Marker

| Component | Pinned version / revision | Terms / source |
| --- | --- | --- |
| CPython | 3.12.10 | PSF License; embedded LICENSE.txt; https://docs.python.org/3.12/license.html |
| pip | 25.0.1 | MIT; wheel metadata |
| Marker | 2.0.0 | Apache-2.0, verified installed wheel LICENSE/METADATA; https://github.com/datalab-to/marker |
| Surya | 0.22.1 | Apache-2.0, verified installed wheel metadata; https://github.com/datalab-to/surya |
| PyTorch / torchvision | pinned in marker-requirements.lock | BSD-style licenses plus bundled notices; https://github.com/pytorch/pytorch |
| llama.cpp | b11149 | MIT and bundled third-party terms; https://github.com/ggml-org/llama.cpp |
| Surya OCR GGUF | 6a3a4c30e5e74446d4f8b6afd05b2f2da970f470 | Modified AI Pubs Open Rail-M; https://huggingface.co/datalab-to/surya-ocr-2-gguf |
| Surya layout2 | 0aee81d5fd9275c0582e545bf3a56944b1e75679 | License is downloaded and hashed with the model; https://huggingface.co/datalab-to/surya_layout2 |
| GoNoto font | fixed URL/hash in engine manifest | SIL Open Font License; https://github.com/satbyy/go-noto-universal |
| Remaining Python wheels | 84 wheels total | Individual wheel metadata and bundled notices; docs/release/MARKER_LICENSE_INVENTORY.md |

Model licenses differ from code licenses. Upstream states that the model license
permits research, personal use and startups below its funding/revenue threshold,
and requires additional terms for other commercial use. The pinned model terms
must be read; ParseForge grants no additional model rights. No engine/model
bundle is distributed in this candidate installer. Retain wheel license files.

## Build tools and system prerequisites

Maven and Inno Setup 6.7.1 are build-only tools. Inno Setup has its own license
(https://jrsoftware.org/files/is/license.txt) and requests commercial users to
purchase a license. Build scripts download verified tools to build/tools.

Visual C++ Runtime is a Microsoft component. Redistribution is subject to Visual
Studio terms (https://learn.microsoft.com/en-us/cpp/windows/redistributing-visual-cpp-files).
This candidate uses detection and a clear blocking message plus the official
download link; it does not bundle or execute an unverified redistributable.
The installed engine health check still verifies actual native compatibility.
