# 8,387-bus fixed-reactive-limit convergence repair

Follow-up: the user clarified that the Case 1 no-event operating point is not
stationary. Its smoke contract was corrected, and the
[subsequent full validation passes](full-merge-validation-g-2026-10-23.md).
The scope and evidence below describe this earlier reactive-limit repair.

The normal-control PQ test failed because Bus7726 repeatedly switched between
fixed reactive power and voltage regulation. Its imported generator has
**Qmin = Qmax = 1.1 Mvar**. With no reactive range, it cannot regain voltage
regulation by releasing either bound. The old local backoff test nevertheless
released the bound whenever voltage crossed its target, causing another limit
violation and restarting the cycle.

Both the adjustment query and the bus-level backoff operation now retain fixed-Q
operation when the reactive range has zero width. Positive-width ranges retain
their existing behavior, including narrow ranges. The pending-limit validation
uses the same bus-level operation. No mismatch tolerance, iteration budget,
solver method, input data or enabled control was relaxed.

| Original normal-control PQ test | Before | After |
| --- | ---: | ---: |
| Iterations | 50, exhausted | 20, converged |
| Maximum active-power mismatch | 0.002295781 pu | 0.000001750 pu |
| Maximum reactive-power mismatch | 0.000015219 pu | 0.000078314 pu |
| Configured mismatch tolerance | 0.001 pu | 0.001 pu |
| Unsettled controls | 1 | 0 |

Requiring the electrical equations to settle before releasing a limit was also
tested in isolation. That experiment still cycled, so it is not part of the fix.
The successful production diagnostic uses the freshly built core without any
diagnostic solver overrides.

## CGMES compatibility

The broader check exposed a related importer omission in MicroGrid T4 BE.
BE-G1 has zero scalar Q bounds but declares an initial reactive capability
curve. Ignoring the curve incorrectly treated it as a fixed-Q source. The
generator importer now evaluates the declared curve at signed SSH active power
and uses those bounds instead of the scalar placeholders. This follows the
[CIM reactive-limit precedence](https://utf13-reports.ucaiug.io/18v06-18v07/CIM18v07_ReleaseNotes.pdf)
and [ENTSO-E schedule/curve convention](https://www.entsoe.eu/Documents/CIM_documents/Grid_Model_CIM/QUALITY%20OF%20CGMES%20DATASETS%20AND%20CALCULATIONS%202nd%20edition.pdf).

The mapper supports constant segments and linear interpolation, with endpoint
bounds outside the supplied abscissae, explicit power multipliers and the
existing unit convention when multipliers are absent. It rejects empty,
duplicate-abscissa and invalid-bound curves. The segment styles follow the
[ENTSO-E curve definition](https://eepublicdownloads.entsoe.eu/clean-documents/CIM_documents/Grid_Model_CIM/RemedialAction_Profile_Specification_v2.1.pdf).
Bounds are evaluated at import; runtime redispatch and curve reevaluation are
outside this repair. Supplied fixture files are unchanged.

## Validation

- Full core reactor: **3,097 passed**, no failures, errors or skips.
- Full plugin-core module: **435 passed**, four existing skips, no failures or errors.
- All MATPOWER test classes, the CGMES comparison class and direct-import class:
  **69 passed**, one existing skip, no failures or errors.
- All five 8,387-bus tests pass. The original normal-control test now also
  checks that Bus7726 retains fixed-Q operation at 0.011 pu and that independently
  recomputed PQ mismatch is below 0.001 pu.
- The new core regression covers both voltage directions in polar and
  rectangular coordinates. It fails against the retained pre-fix core.
- Four capability-curve tests cover segment selection, signed schedules, exact
  breakpoints, interpolation, power multipliers, missing curves and malformed data.
- Svedala retains 116 voltage matches at its tightened **0.006 pu** limit.

The first full-core invocation used the plugin working directory and failed to
locate existing root-relative fixtures. The corrected run from the core root
passes in full. Both logs are retained. The first broader integration check
exposed the capability-curve omission; the final check includes its repair.

The [evidence index](checkpoints/pegase8387-fixed-reactive-limit-2026-10-23.json)
pins bounded logs, test XML, source patches, fixture hashes and diagnostic
sources. This resolves the 8,387-bus gate from the preceding full validation.
The separate Case 1 no-event dynamic-voltage gate remains unresolved. No merge
or milestone commit was created, and the complete plugin reactor was not rerun.
