# Parallel model integration validation

The combined REECC1, TIOCR1 and composite-load implementation wave passes
222 tests with no failures, errors or skips. This includes shared renewable
controller regressions, both integration methods, relay physical-action
recording and complete reference trajectories for omitted distribution elements.
Existing accuracy thresholds are unchanged.

The registered suite now includes the breaker/transfer reference test and the
partly collapsed composite full-removal test. REECC1's optional two-step path
and its remaining qualification boundaries are documented in
[the controller report](reecc1-two-step-integration.md).

This focused result does not qualify the entire branch for merging. The latest
full registered suite, before this wave, reported 4,756 tests with 215 failures,
two errors and 13 skips. No new failing identities appeared relative to its
previous baseline, but the remaining model and system gates must be resolved.

Private evidence is retained at
`verification-artifacts/parallel-model-wave-222-20261104`.
The archive contains the bounded run log and all selected test XML reports.
Tested source is `48fa1151e` plus the two suite registrations.
Archive SHA-256:
`dc6d2a86e11db892a967d3fc6a6b4d9d3bd5d3d399416c1582582c61cac22e1e`.
