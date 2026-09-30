# ADR-012: Engine runtime under LOCALAPPDATA

Status: accepted for Stage 3C. Date: 2026-09-30.

Java resolves its own LOCALAPPDATA environment variable:
`%LOCALAPPDATA%/ParseForge/engines/marker`. Config is stored under
`ParseForge/config/config.json`; diagnostics under `ParseForge/logs`.
With no LOCALAPPDATA, Windows user-home AppData/Local is the fallback.
No Codex LocalCache path is part of production code.

`parseforge.dataDir` explicitly overrides the data root for development/testing.
Validation reports logical and Java toRealPath roots. File operations reject
links, redirected parents and paths outside the expected tree.

Python, llama.cpp, models, HOME, temp and PATH are private. The environment uses a
small Windows allowlist, excluding inherited Python settings, model service URLs
and API keys. Legacy roaming settings can migrate on first launch; manual
executable settings are ignored for normal conversion. An explicit
`parseforge.marker.override` remains available for development.
