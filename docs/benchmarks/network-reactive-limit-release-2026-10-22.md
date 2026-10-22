# Coupled reactive-limit release investigation

Core commit `8b1b0ed77` replaces the fixed-neighbor estimate used to admit a
reactive-limit release with the coupled polar Newton electrical response.
Physical parameter values and reactive bounds are unchanged. A positive finite
voltage sensitivity admits a transactional trial only when the predicted target
Q lies within the existing admission margin. Failed trials retain the existing
rollback behavior. The predictor does not change voltages while querying them.

This fixes a concrete Case 3 error. The old estimate rejected bus 5170 because
it assumed adjacent voltages could not respond. The supplied reference requires
7.267192 Mvar at 1.0 pu, within the generator's limits. The new candidate reaches
7.267151 Mvar at 1.0 pu in 14 preparation iterations, with no unsettled controls.
Across all 2,000 buses the worst cold voltage difference is 0.0000113015 pu,
compared with the previous 0.0155448 pu error. The first physical cold row is
compared directly; no fitted time or angle offset is applied.

| Case | Preparation iterations before netting | Short-run outcome |
|---|---:|---|
| 1 | 15 | Completes 0.1 s; recordings byte-identical to control |
| 2 | 16 | Completes 0.1 s; recordings byte-identical to control |
| 3 | 14 | Completes 0.1 s using the isolated four-substep renewable diagnostic |
| 4 | 15 | Existing integration failure at 0.0458333 s remains |
| 5 | — | Existing preparation failure remains |
| 6 | 22 | Existing integration failure at 0.05 s remains; failure prefix byte-identical |

Cases 1, 2 and 6 have identical prepared bus voltages/angles and generator P/Q.
The serialized preparation JSON differs in field order, so those comparisons
use parsed values. All probes use one CPU, a quarter-cycle network step and
1e-5 dynamic voltage convergence tolerance; these cold short runs are not
warmed ten-second timing evidence. No claim of reaching the eight-second goal
is made.

## Remaining admission-margin issue

The candidate is **not yet qualified for benchmark promotion**. The Case 4
reference comparison exposes an existing admission margin that subtracts the
power-flow tolerance from the physical Q range. At bus 2100, the 0.001 pu
tolerance becomes a 0.1 Mvar release margin. The generator's reference Q is
31.850538 Mvar, only 0.099462 Mvar below its 31.95 Mvar maximum. The coupled
estimate therefore keeps this bus at its limit, whereas the old approximate
estimate happened to permit the release.

This changes the worst Case 4 cold voltage error from approximately 0.00000496
pu to 0.000051367 pu at bus 2100. The margin must distinguish numerical
admission uncertainty from a physical control deadband; reducing it merely to
fit this case would not establish a general fix. Add an independently solvable
near-bound regression and verify nonlinear feasibility and anti-cycling before
changing the rule.

Separate large generator-Q discrepancies remain at bus 2065: 94.0449 Mvar in
Case 3 and 98.2580 Mvar in Case 4. Voltage agreement alone does not validate the
generator/shunt reactive-power allocation or dynamic initialization.

## Verification and limits

Eleven focused tests pass, including free and regulated neighboring buses,
minimum/maximum reactive releases, stiff-network rejection, invalid targets,
bit-preserving queries and an augmented zero-impedance connection. The latter
uses a fresh nonlinear solve after manually changing its active set; the live
solver normally rebuilds auxiliary mismatch within its transition lifecycle.

The broader selection compiles against freshly built core classes and runs 302
tests: 301 pass. The VSC legacy-versus-reduced comparison fails identically with
the old predictor: expected 0.024608710249774107, actual 0.02460871053658383.
Its assertion was not relaxed. The full Maven gate stops at test compilation
because `IndexedVoltageRecordTableTest`, `RevisionVoltageValidationTest` and
`ScalarVoltageScanTest` reference the absent `IndexedVoltageRecordTable` API.
Production compilation succeeds. These failures remain visible; there is no
full-suite acceptance claim.

The response matrix is currently factorized for every eligible admission query.
This avoids stale sensitivity reuse after a voltage, topology or active-set
change. A future cache must have solve-local invalidation and measured benefit.
No dynamic-network factorization policy is changed.

The [evidence index](checkpoints/network-reactive-limit-release-2026-10-22.json)
retains source, tests, logs, reference comparisons and hashes for the complete
short-run recordings. Full outputs remain in the generated-artifact store;
supplied references are preserved.
