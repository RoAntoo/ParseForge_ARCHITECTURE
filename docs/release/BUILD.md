# Building the Windows candidate

From a Windows x64 checkout with Maven 3.9+ and PowerShell 5.1+/7:

```powershell
.\scripts\release.ps1
```

Build downloads pinned Temurin and Inno tools into ignored build/tools, validates
SHA-256 against scripts/release-tools.json, then runs mvn clean verify and copies
only runtime dependencies. No global JDK is used for the release. First build
requires Internet; subsequent builds reuse verified tool archives and Maven cache.
Tools are installed privately without elevation. No release is published.

Individual commands:

```powershell
.\scripts\build.ps1
.\scripts\package.ps1 -SkipBuild
.\scripts\smoke-installed.ps1
.\scripts\smoke-installed.ps1 -Ui
.\scripts\smoke-installed.ps1 -AppDirectory 'C:\path\to\installed\ParseForge' -Ui
.\scripts\test-installer.ps1
```

build.ps1 always runs tests. package.ps1 without SkipBuild also builds/tests.
SkipBuild is for a just-verified target/package-input; release.ps1 uses it only
after a successful build. Never package stale inputs.

The JDK is Temurin 21.0.12.1+1, OpenJFX 21.0.6, Inno Setup 6.7.1 and app 0.1.0.
Version comes from pom.xml and must be stable SemVer. Vendor/copyright identify
ParseForge contributors. Icon sources are in scripts/generate-icon.ps1; PNG and
multi-resolution ICO are committed resources. Changing the icon requires rerunning
the generator before building.

jdeps computes module roots from runtime jars. jlink adds jdk.crypto.ec for
HTTPS, strips debug/header/man-page files and compresses the runtime. Roots are
saved to build/jlink/modules.txt; transitive modules are listed by
build/jlink/runtime/bin/java.exe --list-modules. Third-party jars remain on the
classpath, retaining their native resources and license terms.

jpackage uses --type app-image, a private --runtime-image, explicit main class,
name/version/vendor/description/copyright/icon and UTF-8. Inno Setup packages
the image, offers Spanish/English, destination, desktop/Start shortcuts and launch.
Automatic installer logs are in the user's temporary directory; /LOG="path"
can select a diagnostic log. Runtime Java errors reach the startup log when
the JVM can start. A failure before JVM initialization can only show the native
launcher's error and must be investigated with runtime files and installer log.

Artifacts:

```text
build/jlink/runtime/
build/app-image/ParseForge/ParseForge.exe
build/installer/ParseForge-Setup-0.1.0.exe
build/release/ParseForge-Setup-0.1.0.exe
build/release/ParseForge-0.1.0-win-x64.zip
build/release/SHA256SUMS.txt
build/release/release-notes.md
```

Application logs rotate daily/5 MB with 7-day/50 MB retention. Diagnostics record
startup, lifecycle, process IDs, executable names, exit codes and elapsed time;
they do not persist PDF contents or conversion stdout. Engine preparation logs
may contain dependency diagnostics. Reported paths can contain the account name.

smoke-installed clears global Java/tool environment and PATH, starts ParseForge.exe
from a different working directory and uses a fresh isolated data root. It checks
private runtime, Windows Job Object, manifest loading and settings persistence;
Ui additionally opens/checks the window and saves a screenshot. This is a host
smoke, not evidence of clean Windows or engine installation/conversion.

test-installer is opt-in: it refuses an existing ParseForge registration/shortcuts,
installs the candidate into build/installed-test, verifies shortcuts/window,
reinstalls and uninstalls. It checks that a test document survives uninstall.
It intentionally leaves that document and diagnostic logs for review. Use a
clean test account if ParseForge is already installed.

Build is reproducible as a pipeline with fixed tools/dependencies and stable JAR
timestamp. Windows executable/ZIP timestamps mean byte-identical EXE/ZIP output
is not guaranteed. Each build gets its own SHA256SUMS. Code signing, updater and
automatic publication are outside this candidate.
