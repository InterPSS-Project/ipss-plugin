# Restore the published EDCLF voltage equation

The core 1.4.8 dependency changed the documented EDCLF method into two frozen-current
voltage corrections. The existing IEEE14 checks consequently failed, although they
passed with core 1.4.7. The LCC qualification artifact reproduced the same failures;
its EDCLF classes were byte-identical to core 1.4.8. This was a dependency-transition
regression, not an LCC interaction or a test-order requirement.

The interface still specifies equation (6) of *An Extended DC Power Flow Model
Considering Voltage Magnitude*. Equations (5)--(6) prescribe one complex linear
solve with the conjugate power-injection diagonal added to the nodal matrix. The
[author-uploaded paper](https://www.researchgate.net/publication/351819971_An_Extended_DC_Power_Flow_Model_Considering_Voltage_Magnitude)
identifies DOI `10.35833/MPCE.2019.000146` and describes the non-iterative formulation.

With known-voltage buses M, PQ buses N and absolute voltage vector U, the restored
operator is:

`(YNN + diag(SN*)) U = 2 SN* exp(j thetaN) - YNM VM exp(j thetaM)`.

Its result is rotated into each PQ bus's local angle frame, and the real component
is used for the voltage magnitude. Core history identifies the substitution in
`16f40d07c` (commit message `dev`), later brought into the 1.4.8 lineage. No documented
public method migration was found. Replacing the old test values with the new
iteration's output would therefore bless a different algorithm.

The scoped restoration changes only `EDclfSolver.calPQBusVoltMag()` and the public
`calculateEDclf` flow: restore the power diagonal and one linear voltage solve,
rebuilding the partition for each call. Current load aggregation and the separate
reactive-power API remain. No new algorithm option or altered tolerance is added.

## Independent validation

`IEEE14_EDclfPaperOracleTest` reads branch resistance, reactance, line charging,
transformer ratios, bus shunts and injections directly from the CDF fixture. It
stamps a dense complex matrix independently of the core Y-matrix helpers and solves
its equivalent real block system with a separate dense LU implementation. It accepts
the DC angles as inputs, then verifies all nine PQ-bus complex voltages against the
published linear equation, including a 1e-12 nodal residual check. A second test
checks the single-solve public-method contract. The original three voltage
checkpoints and their 1e-4 tolerance are unchanged.

Both new tests pass on core 1.4.7 and fail on unmodified 1.4.8. They pass with the
restoration. This is an independent check of the published approximation, not a
claim that EDCLF is an exact nonlinear AC power flow.

| Validation | Result |
|---|---|
| Original IEEE14 class, core 1.4.7 | 6 passed |
| Original IEEE14 class, core 1.4.8 | 2 failures out of 6 |
| Original IEEE14 class, LCC qualification artifact | Identical 2 failures |
| Published-equation oracle, before restoration | 2 failures out of 2 |
| Full core regression, restored artifact | 3,797 tests; 0 failures/errors; 16 skips |
| Plugin DCLF/EDCLF regression, restored artifact | 160 tests; 0 failures/errors; 26 skips |

The plugin run selects all 38 classes directly referencing the DCLF factory or EDCLF
algorithm, including the new oracle and unchanged compatibility tests. Skips comprise
four disabled historical print-only variants and 22 opt-in large-case/performance
checks; no accuracy assertion was disabled to obtain this result.

Core work uses branch `agent/edclf-contract-20261104`, based on `3b1292682`, and unique
artifact `1.4.8-edclf-contract`. Released artifacts, shared branches and default
plugin dependencies are unchanged. Detailed artifact hashes, logs and bytecode
comparisons are retained privately under `edclf-core-version-isolation-20261104`;
restoration validation is retained under `edclf-paper-contract-restoration-20261104`.
