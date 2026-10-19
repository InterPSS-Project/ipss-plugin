# PSS5C exact first-order low transducer and reference order diagnosis

## Implemented contract

The native thirty-constant reader and builder now accept `AL=0, BL>0` with the
existing full-order high transducer. This is the exact reduced transfer
function of the published low sensor, not a time-step approximation:

```
Hlow(s) = (1 + DLI*s) / (1 + BL*s + AL*s*s)
AL = 0:
  dx/dt = (speedDeviation - x) / BL
  lowSignal = x + (DLI/BL) * (speedDeviation - x)
```

The instantaneous numerator term is evaluated at each integration endpoint
and when output is queried. It also appears in the input vector for the
three low/intermediate bands in `Pss5cLocalLinearizer`. Omitting that term
would give the wrong first response and wrong B matrix even when equilibrium
looked correct.

`lowPosition` remains the first lag coordinate. `lowRate` is an explicitly
frozen inactive coordinate; all thirteen named-state keys are preserved.
Initialization at non-nominal constant speed is an equilibrium. Negative AL,
zero BL with AL zero, and reduced high-transducer orders remain unsupported
unless the documented whole-model bypass applies. No epsilon or minimum-time
replacement is used. Every positive AL retains the published second-order
equations, regardless of integration step.

The source is the unchanged native model diagram retained in the foundation
seal, document SHA-256
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`,
pages 106-107. The diagram's numerator and denominator establish the reduction
above. The separate [PowerWorld PSS5C description](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS5C.htm)
does not establish an exact native numerical order-switch threshold.

## Independent reference discriminator

The state-space probe now supports all five transducer coordinates and
single-coefficient DLI, AL and BL variants. Each tiny SMIB case independently
initializes, verifies all thirty constants, retains complete startup history,
and applies one documented signed state/storage perturbation. It never injects
reference states into an InterPSS trajectory comparison.

Eight cold pairs, eleven independent resets per run, produce 176 cases.
The raw response JSON, staged inputs and probe bytes repeat exactly within
every pair. The engine, wrapper, Python and source-document identities remain
the same as the preceding pole-contract slice. Three earlier sensor pilots
are retained separately; they are not counted as cold pairs.

The observations resolve an important part of the earlier discrepancy:

- With original positive AL near 0.00012739 and step 0.0001 s, the second
  low-state perturbation is erased exactly, in every subsequent recorded
  state and variable. The remaining measured decay rate is about -56.10704/s,
  close to the first-order `-1/BL`, not the second-order sensor poles.
- At step 0.00005 s the same entered coefficients retain two modes. Measured
  poles are approximately `-69.95494 +/- j54.37064`, versus the independently
  calculated polynomial roots `-69.95447 +/- j54.37170`.
- Doubling AL at the original step also restores two modes. Measured
  `-34.97759 +/- j51.97644` agrees with the changed published denominator.
- Halving DLI leaves these low-state poles/order unchanged at both steps,
  as expected for a numerator coefficient. Doubling BL at the fine step
  changes the two poles to the overdamped denominator's modes.
- Explicitly entering AL zero gives one mode at both steps: approximately
  -56.10704/s and -56.10727/s, against the analytic -56.10728/s.

Thus the reference's original coarse response is not evidence that the
published second-order denominator is intrinsically wrong. It exhibits
timestep-dependent order reduction. These samples do not prove the exact
switching threshold, equality behavior, all coefficient combinations or
other releases. No inferred threshold is added to production.

The two-state estimator rejects singular response matrices rather than using
a pseudoinverse. An erased second state is reported as a separate observed-
order counterexample, retaining the declared published order. No parameters,
coordinate transforms, clocks or curves are fitted. All startup snapshots,
binary histories and logs are kept; no trajectory rows are discarded.

## Verification scope

Seven new controller tests cover strict zero-AL parsing, independent first-
order and band slopes at both modified-Euler stages (including DLI zero),
full A/B-to-runtime reconstruction, instantaneous output and both limits,
non-nominal equilibrium and explicit unsupported orders. The class is
registered in the permanent core suite. Four additional imported SMIB runs
exercise flat and three-cycle three-phase fault responses at 0.0005 and
0.00025 seconds, asserting the inactive low-rate coordinate remains zero.

Eight new offline counterexamples exercise full versus erased response order,
sensor offsets, singular rejection, declared-versus-observed order, and finite
observations. No established trajectory tolerance changes. The first focused
attempt exposed two new-test mistakes: a stimulus that did not activate the
intended limits, and an exact-zero assertion on a floating-point eigensolver
result. Both were corrected without altering production equations or existing
acceptance bounds; its failed reports remain in the evidence seal.

This proves source-equation and import/solver support for the explicit first-
order low branch. It is **not** all-channel native trajectory acceptance,
complete PSS5C verification or completion of the model-coverage plan.

## Reproducibility and remaining work

The isolated tested source starts from `6452ba9afc1114138b9b4108cb18334f196fff25`.
Only the PSS5C builder condition is included from the shared builder file;
concurrent governor/parser changes are excluded, preserved and not certified
by this slice. The build uses the established isolated snapshot, offline
cache and `1.4.3-salient-refresh-20261019` core identity. Core jar SHA-256:
`21519010c48bd265678743df7e98d3dcea530f5ceda1ccd7921d123d7f6b5333`.
Final XML counts and source reconciliation are recorded in the private seal:
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-19-pss5c-low-transducer`.
Previous evidence and pinned jars remain unchanged.

| Final run | Tests | Failures/errors | Existing skips |
|---|---:|---:|---:|
| `pss5c-low-focused-2` | 143 | 0 / 0 | 0 |
| `pss5c-low-units-1` | 427 | 0 / 0 | 6 |
| `pss5c-low-smib-1` | 1,871 | 0 / 0 | 1 |

All 97 offline checks, neutral-wording checks and retained reference/dialect
audits pass. The source audit reconciles 1,884 Java/build files and 139
unchanged resource parts. Source comparisons allow CRLF-to-LF normalization
only; raw forms are retained. All four original full-order coupled CSVs are
byte-identical to the preceding slice. The typed model's compiled runtime
and comparison error vectors/times are also unchanged. New reduced-order
flat/fault exports are retained separately, not substituted for old evidence.

Remaining work is the frequency-field compatibility contract, complete
low/high state and raw-VAR observation semantics, reduced high/static-low
orders, remaining zero-gain/active-limit/lifecycle cases and independent
all-channel coupled trajectory acceptance. The original native replay gap is
not reclassified as accepted. Full Texas2k dynamics are not run here.

Follow-up: the [high-transducer slice](pss5c-high-transducer-2026-10-19.md)
adds proper published-equation high reductions and combined low/high tests.
Its AH-zero reference-order discrepancy remains open; the historical results
and acceptance limits recorded above are not replaced.
