# ADR-014: Real versus indeterminate progress

Status: accepted for Stage 3C. Date: 2026-09-30.

Installation shows bytes for the current artifact when HTTP supplies
Content-Length. Unknown totals, extraction, package installation and verification
use indeterminate progress. No overall percentage is inferred from stage count
or time.

Conversion shows indeterminate progress plus elapsed HH:MM:SS independently of
stdout/stderr. A silent engine retains a visible processing state. The timer
updates a label without adding logs.

MarkerProgressParser returns empty because stage percentages do not quantify
whole-document completion. No ETA is shown without reliable quantified progress.
Application events use SYSTEM/INSTALL; engine stdout/stderr retain their labels.
