# Texas2k original-source verification, 2026-10-17

## Inputs and scope

The recovered `Texas2k_series24_cases_with_dynamics.zip` has raw SHA-256
`a66623bbf3b3faf910f494910f08cca9620066f8964c990aa04ea50b7106ca4e`.
Only its 27 native RAW/DYR/IDV entries were staged, preserving their exact
bytes. All extracted and privately copied files match their ZIP-entry SHA-256
values, recorded in the local `input-sha256.json`. No external original was
edited and no GE PSLF DYD was added to a native input set.

The six case directories are now under the default ignored
`ipss.test.plugin.core/testData/private/texas2k` root. Their original DYR files
contain 16,311 records across 17 native types, reproducing the reviewed per-case
totals. Full coverage tests pass both sibling GNET/MODREMOVE auto-discovery and
the strict attached/intentionally-skipped record accounting for all six cases.

Eleven ordinary test paths incorrectly required a derived Case 1 `_v36.RAW`
that is absent from the original archive. They now select its original
`Texas2k_series24_case1_2016summerPeak.RAW`, whose header declares version 34.
The loader detects the actual version. No file is renamed or relabeled.
Case 3 retains its provided original version-30 variant. The separate staged
diagnostic's custom `_v36.RAW` requirement is intentionally unchanged.

## Shared CML defect and correction

The first recovered-input smoke run passed its assertions but logged caught
NullPointerExceptions in `AbstractAnnotateController.hasAlgebraicControlBlock`.
While PSS2A was initializing, an output read traversed a child whose annotation
field list did not yet exist. The existing broad output-error handler returned
zero, so a passing smoke assertion did not establish exception-free execution.

The shared core now distinguishes annotation initialization from runtime
algebraic refresh. It suppresses runtime sweeps on partial graphs and treats
unconstructed child field lists as non-stageable. A try/finally restores the
lifecycle flag on successful, failed and nested initialization exits. This
does not change the dynamic equations, numerical integration method or live
zero-time output behavior after initialization.

Three new core tests cover an unconstructed child, an algebraic sibling beside
an unconstructed child, and a deliberately failed/repeated initialization that
must restore live output refresh. Together with existing zero-time and
modified-Euler stage tests, all 13 focused core tests pass. The complete core
regression passes 2,910 tests with zero failures, errors or skips. Its local
log is `target/cml-initialization-core-regression.log` in the plugin checkout.

After installing the corrected core, the recovered-source inventory, full-stack
coverage and smoke rerun passes six test methods with zero skips/failures/errors.
It contains no `NullPointerException` or `Error in getOutput` messages. Its log
is `target/texas2k-original-source-cml-fixed.log`.

## Six-case smoke and strict flat evidence

All six cases pass each existing smoke: 0.1 s no-event operation, a Bus 7159
three-phase bolted fault from 0.05 to 0.10 s through 0.25 s, and a Bus 1051
generator trip through 0.25 s. These are selected-channel smoke checks, not
full-system independent parity. Their existing bounds are unchanged.

The stronger one-second no-event gate was explicitly enabled and executed
for all six cases at 1/240 s, with the ordinary modified-Euler production
method, original inputs and full active model stacks. No controller freezes,
gain changes or model exclusions were introduced. It checks every active bus
voltage and synchronous-machine speed against unchanged limits of 1e-6 pu and
1e-7 pu, respectively.

| Case | Maximum voltage drift, pu | Maximum speed drift, pu |
|---|---:|---:|
| 1, 2016 summer peak | 5.32907052e-15 | 0 |
| 2, 2016 low load | 8.32667268e-15 | 0 |
| 3, 2024 summer peak | 1.68753900e-14 | 0 |
| 4, 2024 low load | 1.56541446e-14 | 0 |
| 5, 2024 high renewables | 3.12838644e-12 | 5.66213743e-14 |
| 6, 2024 low load with GFM | 1.38777878e-14 | 0 |

All six pass. REECA1 and wind active-reference state deltas are no larger than
5.56e-17 in these runs. This is current measured evidence, superseding the
old September 0/6 strict-flat result; it must not be attributed solely to this
small CML change without a controlled before/after strict-flat experiment.
Full ranking CSVs and `target/texas2k-original-strict-flat.log` are retained locally.

## Fifteen-fault execution and unresolved response severity

All 15 reviewed topology-selected faults execute and pass the existing smoke
assertions at 1/240 s through 0.5 s. They comprise six conventional-terminal,
four renewable-plant, four weak-grid and one GFM-terminal fault. The test checks
finite channels, fault depression below 0.1 pu, final fault-bus voltage above
0.5 pu and monitored machine-speed deviation below 0.1 pu. These are broad
execution/recovery checks, not a complete physical stability acceptance envelope.

The summary exposes responses that must not be called reasonable or verified
without independent diagnosis:

| Case/category | Fault bus | Voltage at 0.5 s, pu | Maximum monitored speed deviation, pu |
|---|---|---:|---:|
| 6, GFM terminal | Bus1016 | 1.69941852707 | 0.0347408763677 |
| 5, weak grid | Bus1011 | 1.51931220004 | 0.0392896383493 |
| 4, weak grid | Bus1011 | 1.39361456300 | 0.0227989235198 |
| 6, weak grid | Bus1011 | 1.36269231135 | 0.0244464006802 |
| 3, weak grid | Bus1011 | 1.36164456648 | 0.0161258725922 |

Across all 15 runs the largest monitored speed deviation is 0.0477002091244 pu.
The next diagnosis should prioritize the GFM/weak-grid responses and the leading
machines/controllers, comparing first divergent states with an independent
simulator and halving the step. A real unstable case, a model error, and a
coupling/integration error must be distinguished without fitting parameters or
relaxing limits. A passed smoke matrix does not close this requirement.

All 15 raw trajectory hashes match the summary. Each trace has 65-135 channels,
including nearby voltage/angle, generator speed/angle, P/Q, EFD/PM, renewable
outputs and semantic controller states. The completed traces, strict-flat
rankings, input manifest and logs have also been copied outside build output
under the ignored `ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-17-source-recovery`
archive. The copied fault traces were hash-checked again. The seven current
twice-cold PowerWorld SMIB bundles and the failed full-case probe are preserved
there too, without replacing older references.

## Full regression and remaining work

The shared-core fix is committed as `e886019ce` in the sibling core repository.
Validation uses that locally rebuilt/installed core with its existing 1.4.3
development coordinate; no release or dependency-version bump is implied.
Build/install that core source before reproducing these plugin results.

The full registered plugin command exits zero on 2026-10-17:

```text
mvn -q test -Pextended-tests --fail-at-end
    -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest
    -Dsurefire.failIfNoSpecifiedTests=false
```

It ran with original private inputs available and
`texas2k.fault.matrix.enabled=true` for the reviewed 15-event matrix. The
strict-flat gate was run separately with `texas2k.drift.enabled=true`, not
inferred from a disabled test. Both opt-in defaults are restored to false
after validation. No trajectory tolerance or independent checkpoint changed.

Surefire reports written during the full run contain 2,696 tests: 2,606 passed,
90 skipped, zero failures/errors. Module totals are 129 core-plugin tests
(4 skipped), 468 three-phase tests (28 skipped), and 2,099 integration tests
(58 skipped). The unrelated untracked WECC240/JAX test is excluded and unchanged.
The full local log is `target/texas2k-original-source-regression.log`, also copied
into the private verification archive. There are no CML output-error messages
or null annotation-field-list exceptions in this full run. An unrelated
MultiNet dummy-bus test still logs a caught null-bus exception also seen in the
earlier baseline; a green reactor is not a general exception-free guarantee.

Complete native PSS/E/PowerWorld Texas2k trajectory parity remains open, as do
the broader approved-model branch/profile and reproducibility gaps. Finite
fault execution or a stationary flat run alone is not an independent model
acceptance claim.

### Full-case PowerWorld capability probe

The unchanged private benchmark runner was also tried on the original Case 1
version-34 RAW, native DYR and both GNET/MODREMOVE IDVs. It used a new isolated
output directory, no DYD, no model overrides and disabled auto-correction.
PTI34 is a documented input choice in PowerWorld's
[case-opening format interface](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/OpenCaseType_Function.htm).
The selected event was the same three-cycle Bus 7159 fault; result selectors
requested three nearby voltages, two generator speeds/angles, P/Q and machine
states. The simulator executable is the same current version that completed
the seven twice-cold SMIB profiles.

This full-case attempt timed out after 90 s, with only the START progress
marker and no RAW_LOADED marker. Its manifest records `status=timeout`, and no
trajectory was accepted or published. The isolated inputs, specification and
generated AUX remain under `target/texas2k-case1-powerworld-original-a` (the
specification is `target/texas2k-case1-powerworld-source.json`). The runner
terminated only the processes it started. This is an unresolved case-loading
or tool-capability result, not evidence of a bad dynamic model or a proven
license failure. Smaller SMIB success alone cannot establish full-case support.
