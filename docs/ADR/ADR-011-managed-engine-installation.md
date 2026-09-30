# ADR-011: Managed engine installation

Status: accepted for Stage 3C. Date: 2026-09-30.

JavaFX depends on application use cases and an EngineManager port. The
composition root explicitly injects infrastructure for manifest loading,
downloads, package/model preparation, verification and lifecycle operations.

The bundled schema-v1 manifest pins archives, 84 wheels, a hash lock, model
revisions, an auxiliary font and expected runtime hashes. Installation never
resolves latest versions, uses WinGet or executes global Python/pip. Maintenance
tooling may query PyPI to resolve already reviewed hashes to URLs; the application
never invokes that tooling.

An in-process lock and an OS file lock guard engine lifetime. Conversion owns a
runtime lease; install/repair/uninstall cannot run during a conversion, including
from another process. Downloads require an explicit Install/Repair action.
Uninstall requires UI confirmation. Signing and automatic updates are outside MVP.
