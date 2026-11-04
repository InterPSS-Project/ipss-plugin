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

## Network convergence diagnostic

An explicit test-only option, `ipss.smoke.certifiedNetworkAcceleration=true`,
installs the existing bounded-acceleration solver in the imported-system smoke
fixture. The option is off by default. Each accepted network call is checked
against its original unmixed voltage correction, 1e-4 pu tolerance and
50-iteration cap. A mixed update alone cannot satisfy the certificate.

All smoke scenarios complete with this option enabled. Together with the shared
helper's battery-controller and endpoint-recording regressions, ten tests pass
without skips. The system scenarios actually use bounded mixing; no certificate
exceeds the iteration cap or voltage tolerance.

A fresh run with the option absent reproduces the same two default-path
failures. Their final voltage corrections are approximately 1.151e-4 and
1.609e-4 pu at the iteration cap. This isolates a convergence limitation; it does
not establish reference-trajectory accuracy, a speedup or grounds for changing
the production default. The registered default-path failures remain visible.

Private evidence archives are retained at
`verification-artifacts/system-certified-counted-20261104` (SHA-256
`07eb605e6e591f02cdd05f47a2dd460e36faebb8c6b420a04872e325f345b36e`)
and `verification-artifacts/system-default-control-20261104` (SHA-256
`d81fa2183a9a00de876a453f9d44c7f76d1f1e9f28a02b4db458ee69e269e48b`).
