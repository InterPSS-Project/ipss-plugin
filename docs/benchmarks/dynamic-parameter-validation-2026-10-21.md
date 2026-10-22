# Dynamic parameter validation review

## Documentation findings

The [validation overview](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Transient_Stability_Dialog_Validation.htm)
separates blocking errors, warnings and informational messages. Validation and
automatic correction are distinct operations. Corrections change model input
data, and successful integration does not establish physical correctness.
The linked category pages for machines, other generator controls, exciters,
governors, stabilizers, loads, relays, areas and shunts were reviewed as the
coverage inventory. Detailed checks were reviewed for the principal synchronous
machine and controller families used in these cases.

Time-constant checks must distinguish an integrator, an optional lag and a
lead-lag pair. A zero lag can represent a valid algebraic block; a nonzero
lead with a zero lag needs explicit model treatment rather than an automatic
generic bypass. A model's actual integration interval matters, including local
substeps. A conservative pole-resolution warning is useful, but it does not
prove stability of coupled feedback equations.

The overview's printed machine-reactance inequality is reversed relative to
its own correction rules. The individual [GENROU](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENROU.htm)
and [GENSAL](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENSAL.htm)
pages and implemented equation denominators are the relevant checks. Equality
and reduced-order cases need model-specific treatment; do not copy the overview
inequality into code.

The [EXST1](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXST1_PTI.htm),
[ESST1A](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST1A%20and%20ESST1A_GE.htm)
and [PSS2A](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS2A.htm)
pages use different time-constant correction thresholds. Their dialects and
integration policies are not interchangeable. The governor pages also contain
gain, rate, selector and limit rules. These need independent equation checks
before changing a benchmark model to match another simulator's corrections.

## Implemented checks

`DynamicParameterValidator` performs a non-mutating scan before state
initialization in the imported-data solver adapter, including its staggered
subclass. It can also be called explicitly without running load flow or dynamics:

```java
solver.setParameterTimeStepMultiple(2.0); // Advisory threshold; no parameter changes.
var report = solver.validateModelParameters();
report.requireNoErrors();
```

Initialization rejects reported errors automatically. Warnings do not block a
run. `getParameterValidationReport()` retains the report, with owner, model,
parameter, value, effective integration step and initial activity. Attached
inactive devices are checked because they may later be activated.

Current rules cover finite scalar controller data, machine inertia/rating and
selected reactance relationships, model-specific time constants, lead-lag
causality and reversed limits. Supported families are classical, round-rotor
and salient-pole machines, ST1, PSS2A, TGOV1, HYGOV and GGOV1. Existing HYGOV and
GGOV1 feasibility checks are also invoked. These are selected rules, not a
claim of exhaustive parameter validation for those families.

ST1 uses its configured local step. Other covered models use the network step.
The default warning margin is two times the effective step, a conservative
screening choice rather than a reproduction of another integration scheme's
acceptance boundary. The network step and substep counts are never changed by
validation. Reversed limits are warnings because some current models already
reorder them during initialization; the validator does not reorder them.

PSS2A, HYGOV and GGOV1 already have initialization correction policies. The
report calls attention to them; it does not add new corrections or yet provide
a complete before/after record of every existing effective parameter change.
Source parameters, supplied references and their comparison conventions remain
unchanged.

Uncovered families are listed explicitly. These include ESST4B, IEEET1,
IEEEG1 and renewable bundles in the supplied cases. Load, relay and dynamic
shunt-specific checks also remain outside this first implementation. Existing
device checks and unsupported-model rejection continue to apply.

## Six-case audit

All six supplied RAW/DYR pairs were inspected before netting or dynamic
initialization, at a 1/240-second network interval. Counts below use the default
two-times-step warning margin. All reported warnings concern PSS2A T2/T4;
each affected stabilizer has two warnings.

| Case | Errors in covered rules | Active warnings | Active stabilizers | Inactive warnings |
|---|---:|---:|---:|---:|
| 1 | 0 | 44 | 22 | 0 |
| 2 | 0 | 10 | 5 | 34 |
| 3 | 0 | 44 | 22 | 0 |
| 4 | 0 | 6 | 3 | 38 |
| 5 | 0 | 10 | 5 | 34 |
| 6 | 0 | 6 | 3 | 38 |

Case 2's active affected machines are 1050/1, 6053/1, 6257/1, 7311/1 and
7428/3. Their T2 and T4 values are respectively 4.902, 6.940, 6.396, 7.286
and 6.074 ms, compared with the 4.166667 ms network interval.

The same audit at ST1 counts four and one produced the same warnings. ST1's
checked time constants do not cross this screen, even at one local step.
Consequently this test alone cannot explain the observed one-step accuracy
loss or establish the cause of Case 3 instability. PSS2A's feedback and
integration behavior is a concrete follow-up target; changing its physical
time constants just to remove warnings would not establish reference fidelity.

## Verification

Thirty-two focused tests passed, including nine new tests for validation,
local versus network step, zero-time blocks, invalid data, initialization
gating, inactive devices, reactance ordering, immutable reports and coverage
gaps. The new test class is registered in `CorePluginTestSuite`.

Complete ten-second Cases 1 and 2 with the new initialization gate reproduced
their retained full-network recordings, selected-channel CSVs and channel
metadata byte for byte. These are regression checks, not proof of independent
trajectory accuracy or new warmed timing measurements. The six-case parameter
audit is not a six-case dynamic simulation.

The [evidence index](checkpoints/dynamic-parameter-validation-2026-10-21.json)
identifies the retained audit reports, input hashes, tests, source snapshots and
recording comparisons. Pending performance prototypes were excluded from the
full-network regression; the 32 tests also passed with those worktree edits.
