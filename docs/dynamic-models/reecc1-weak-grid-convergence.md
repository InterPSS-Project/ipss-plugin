# REECC1 weak-grid fixture convergence

The existing weak-grid battery-controller trajectory test and endpoint recording
test now explicitly select qualified bounded network acceleration. This is a
fixture policy, not a change to the production default or controller equations.
The default unaccelerated weak-grid solve remains limited by the 50-iteration cap.

The battery test previously stopped at fault application (0.05 s), with maximum
voltage correction 0.0001497 pu after 50 iterations against 0.0001 pu tolerance.
Its original introduction predates the reduction of the default cap from 500 to
50. The cap remains 50; no accuracy threshold was relaxed.

The accepted policy checks the original, unmixed fixed-point voltage correction
before convergence. A test solver records every network solve and asserts finite
correction at or below 0.0001 pu, successful convergence, the unchanged cap, and
actual use of bounded acceleration. This is a voltage fixed-point residual
certificate, not an independently computed current-balance residual.

| Test | Network calls | Maximum iterations | Maximum original correction (pu) | Bounded mixes |
| --- | ---: | ---: | ---: | ---: |
| Battery trajectory | 6000 | 25 | 0.0000992597976 | 14 |
| Endpoint recording | 45 | 41 | 0.0000998639410 | 41 |

All seven tests pass. All 15 existing independent battery trajectory ceilings are
unchanged: maximum bus-1 voltage error is 0.000761174 pu, voltage measurement error
0.000826178 pu and reactive-current command error 0.001665305 pu. The existing
trajectory comparison excludes samples immediately adjacent to fault edges;
this change does not expand that exclusion. Endpoint timing is checked separately.

A diagnostic using rectangular Java sparse solves and paired converter
compensation removal completed the battery run but exceeded three existing
ceilings; the solid-fault recording test still failed convergence. It was rejected
as the fixture remedy. Neither that path nor acceleration is enabled globally.

Staggered integration currently rejects this electrical controller at its model
eligibility check. Supporting that method and independently qualifying its full
trajectory remain separate runtime work, not completed by these fixture changes.

Validation: `Reecc1NativeConformanceTest,EndpointAlignedStateMonitorTest`, bounded
2 GiB Java heap, seven tests, zero failures/errors/skips. Diagnostic logs and
source hashes are retained privately under
`verification-artifacts/reecc1-weak-grid-convergence-20261104`.
