# ADR-013: Staged atomic installation

Status: accepted for Stage 3C. Date: 2026-09-30.

Installation uses a unique sibling `marker.installing-<uuid>`. HTTPS downloads
write `.part`, validate expected size and SHA-256, then rename. Extraction rejects
traversal and enforces expanded-size/entry limits. Execution requires verified
archives/wheels. Private Python installs wheels with require-hashes, no-index and
no-compile. Models/font and all critical file hashes are verified before commit.

Update 2026-10-01: runtime health probes are optional for installation and repair,
unchecked by default in the UI. The choice does not disable integrity checks.
Selected probes remain blocking: failure preserves the previous runtime. Their
output, exit status and timeout result survive staging cleanup in a separate log.
The UI gives a planning estimate of 20 minutes or more and labels download ETAs
as per-file estimates.

Source/native hashes come from bundled reviewed metadata. pip-generated RECORD
and console launchers contain installation details and are excluded from portable
hashes. Unused launchers are removed; Marker runs via private Python's CLI entrypoint.

Commit uses same-volume atomic moves: active to `marker.previous`, then staging
to active. On failure, restore the backup. Startup recovers an interrupted swap,
finishes interrupted removal and cleans abandoned staging under an OS lock. The
two moves are recoverable, not a single filesystem transaction.

Cancellation stops downloads and owned processes, cleans staging and preserves
the prior installation, up to the commit boundary. Commit/removal then finishes.
Diagnostics survive outside staging. A backup cleanup failure preserves the
committed runtime; startup retries cleanup.
