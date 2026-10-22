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

The initial implementation left ESST4B, IEEET1, IEEEG1 and renewable bundles
uncovered. The October 22 extension below adds selected checks for the first
three families. Renewable bundles, load, relay and dynamic shunt-specific
checks remain uncovered. Existing device checks and unsupported-model rejection
continue to apply.

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

## October 22 coverage extension

A further review of the linked [ESST4B](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST4B.htm),
[IEEET1](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20IEEET1.htm)
and [IEEEG1](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20IEEEG1,%20IEEEG1D%20and%20IEEEG1_GE.htm)
pages was checked against the implemented initialization equations. The
pre-initialization validator now includes:

- ESST4B: finite scalar data, optional Tr/Ta lags, reversed PI limits, and
  warnings when existing initialization substitutes gains for an inactive PI
  path. The absent Vgmax upper limit may be positive infinity; NaN, negative
  infinity and infinite gains are rejected.
- IEEET1: optional Tr/Ta lags, strictly positive Te, an enabled feedback
  denominator Tf, finite scalar data and reversed regulator limits.
- IEEEG1: T2/T1 lead-lag causality, strictly positive valve-positioner T3,
  optional turbine lags, finite scalar data, rate/position limits, and the
  shaft-fraction sum used as an initialization divisor. Existing fraction
  normalization and rate-sign corrections generate warnings.

These are selected checks, not complete validation of every parameter or
variant. Reports identify existing initialization corrections without adding
new corrections. Validation leaves all input values and integration settings
unchanged; blocking errors are rejected by the existing initialization gate.

All 51 focused tests passed: 14 validator tests, existing solver/substep tests,
exciter builder tests, and three independent small-system trajectory tests.
Tests include non-mutation, zero-time bypasses, singular initialization,
optional infinite limits and initialization rejection. The existing unrelated
performance prototypes were excluded from the test classpath.

The six-case import audit was repeated at 1/240 second with ST1 substep counts
10, 4 and 1. All 18 reports contain zero errors in the covered rules and the
same 44 PSS2A warnings per case when inactive models are included. Active and
inactive warning counts remain those in the table above. No new warning was
introduced by the three additional families. Remaining attached uncovered
devices are 107 renewable bundles in Cases 1–2, 301 in Cases 3–5, and 301 plus
five grid-forming devices in Case 6. Counts include inactive devices.

This is a parameter audit, not a new six-case dynamic acceptance run. The new
checks do not establish the cause of prefault motion or resolve the outstanding
full-system reference errors.

The generic warning threshold must not be confused with a model-specific
correction rule: at 1/240 second and multiplier two it is 8.333333 ms, whereas
the linked PSS2A page's T2/T4 correction threshold uses 0.1 times that value.
The [sub-interval guide](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Transient_Stability_Integration_Subinterval_Models.htm)
also lists PSS2A among models with specialized default sub-interval behavior.
Neither observation authorizes copying another implementation's numerical
policy into this solver. Test local integration against unchanged model data
and supplied trajectories before adjusting physical time constants.

The [extension evidence index](checkpoints/dynamic-parameter-validation-extension-2026-10-22.json)
retains the reports, test result, source snapshots and reproduction commands.

## Converter checks and stability policy, October 22

The follow-up review covered the overview's ten linked categories, the numerical
integration/subinterval pages and selected machine, exciter, stabilizer, governor
and renewable model pages. These sources justify model-specific validation;
they do not define one correction rule that can safely be applied to every
implementation. The [REGC_A page](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGC_A.htm)
also documents a distinct network-boundary treatment and unused parameters.
Its correction rules must not silently replace the imported converter contract.

The validator now checks all fourteen floating-point REGCA1 converter inputs for
finite values. Constructor comparisons alone admitted NaN. It also checks the
Tg/Tfltr time constants against the converter's network interval, reports an
unexpected low-voltage selector, and rejects missing parent-generator or
electrical-controller attachments. Zero Tg/Tfltr remain valid algebraic paths.
The same checks cover inactive devices and devices encountered through either
generator or bus attachment; dual attachment does not duplicate the report.

Coverage is explicit: `Regca1Model.converter` identifies these selected checks;
`Regca1Model.bundleControllers` remains uncovered. This does not certify the
electrical, plant or wind-control components of the bundle. ST1 substeps and
internal electrical-controller subdivisions do not reduce the converter's own
integration interval. No source parameter, state, timestep or limit is changed.

All 55 focused tests passed, including 18 validator tests. The four added tests
fail against the prior validator and pass with the new checks. They cover every
floating-point field with NaN, infinity, attachment failures, zero-time bypass,
inactive devices, duplicate discovery, non-mutation and effective-step selection.
Existing small-system trajectory checks are included. This was an isolated
focused build against the retained compatible runtime, not a full reactor gate
or a new full-system dynamic acceptance run.

The six-case import audit was repeated at ST1 substep counts 10, 4 and 1: all 18
reports have zero errors. Converter counts are 107 in Cases 1–2 and 301 in
Cases 3–6, including inactive units. No converter timestep warnings were added;
the existing PSS2A warning counts above remain unchanged. Thus this audit does
not identify invalid converter parameters as the cause of instability.

For further stability work, use the following order:

1. Reject non-finite inputs, unsupported configurations, singular equations and
   invalid model-specific relationships before state initialization.
2. Report source and effective parameters separately, including existing
   initialization corrections, affected device, rule and integration interval.
   This before/after audit is still incomplete.
3. For fast but valid dynamics, test local substeps with fixed physical
   parameters. A screening estimate is `ceil(k * networkStep / T)` for a single
   active pole with time constant T and chosen resolution margin k. It is not
   a coupled stability bound; feedback gain, limiter switching and the actual
   integration scheme must be evaluated. Do not apply the estimate to bypassed
   zero-time states or automatically accept it as an accuracy guarantee.
4. Make any physical parameter correction an explicit, separately identified
   case variant with old/new values and a reason. Re-run initialization residuals,
   prefault motion and complete reference comparisons for that variant.

The [converter evidence index](checkpoints/converter-parameter-validation-2026-10-22.json)
retains the reports, source hashes and regression result.
