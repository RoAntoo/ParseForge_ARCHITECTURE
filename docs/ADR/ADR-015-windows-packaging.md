# ADR-015: private Java image and per-user Windows setup

Date: 2026-09-30. Status: accepted for candidate 0.1.0.

Build with pinned Eclipse Temurin 21.0.12.1+1, jdeps, jlink and jpackage.
The application is nonmodular: a plain Launcher starts JavaFX from the classpath.
JavaFX Windows natives are in the pinned Maven jars. jdeps determines JDK roots;
jdk.crypto.ec is explicitly included for HTTPS. Inno Setup 6.7.1 wraps app-image.
Build-tool archives are SHA-256 verified before extraction/execution.

Default destination is `%LOCALAPPDATA%\Programs\ParseForge`, with
PrivilegesRequired=lowest. This is a deliberate adjustment from the proposed
Program Files example: installation needs no admin and avoids an elevation
request for app updates. The folder page remains available; protected locations
require appropriate permissions. No automatic all-users mode is offered.

Engines/logs remain `%LOCALAPPDATA%\ParseForge`; settings use
`%APPDATA%\ParseForge\config.json`, migrating Stage 3C settings when no target
exists. Explicit parseforge.dataDir isolates all paths for validation.
Stable AppId permits reinstall; uninstall removes only installer-owned files
and shortcuts. No UninstallDelete entry touches mutable roots or documents.

Marker prerequisite detection loads the required Visual C++ DLLs from System32.
Absent runtime blocks engine installation before downloads with a Microsoft
download URL. No Microsoft redistributable is bundled, downloaded or executed
by ParseForge. Complete engine health checks remain authoritative for compatibility.
The absence/presence path must be validated on clean Windows.

The application joins its own Windows Job Object before starting background
processes. The non-inheritable handle stays open until JVM teardown; children
inherit job membership, without breakaway. KILL_ON_JOB_CLOSE handles crashes.
Existing per-operation tracking still terminates children on cancellation.
Assignment failure prevents startup and writes diagnostics instead of weakening
the containment guarantee. Nested-job compatibility must be checked in the VM.

No public release or Stage 5 starts. A package build is separate from clean-VM
validation; only the latter can justify RELEASE READY.
