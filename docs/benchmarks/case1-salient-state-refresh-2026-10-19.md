# Case 1 salient-state refresh and hydro observations

## Identified defect

The salient machine's q-axis flux coordinate shares its public storage with an
algebraic flux output. The shared integrator wrote the advanced coordinate, but
the next electrical evaluation overwrote it with its old cached value. At bus
6042, unit 1, the coordinate consequently remained exactly constant throughout
the original one-second diagnostic, although its derivative was nonzero.

The core fix captures the stored q-axis coordinate before refreshing that
alias. It preserves the machine equations, parameters, initialization and
integration step. Two regressions exercise an externally advanced coordinate
through repeated zero-time evaluations and a coupled salient-machine response.
Both fail on the previous core and pass with the correction.

## Localization against the supplied results

All 30 machines observed across the six original Case 1 events were replayed
through their complete machine, exciter, stabilizer and governor implementations.
Only terminal voltage was prescribed. Its phase was reconstructed from the
supplied machine states and terminal P/Q/V using the stator relation; the worst
magnitude closure was 2.814e-7 pu. This is conditional localization, not direct
bus-angle validation or coupled-network acceptance.

The replay driven by the previous tight run's own voltage reproduces its rotor
angles within 2.897e-6 degrees across all 30 machines. Initial electrical states
match the coupled run exactly. With the supplied reference voltage boundary,
28 of 30 rotor channels passed before the fix; both failures were salient
machines, at buses 6041 and 6042.

| Bus 6042 maximum absolute error | Previous core | Corrected core |
| --- | ---: | ---: |
| Rotor angle, degrees | 0.0781964 | 0.00412834 |
| Active power, MW | 1.01785 | 0.0304857 |
| Reactive power, Mvar | 1.00213 | 0.0147808 |
| Field voltage, pu | 0.00986309 | 0.000498483 |

All 30 reference-voltage rotor replays pass the unchanged 0.02-degree prefault
maximum-error limit after the fix. Mechanical power, field voltage, speed,
angle and electrical states evolve freely in these comparisons. An additional
diagnostic prescribing field voltage and mechanical power retained the salient
error before the fix, separating it from the attached controls.

An independent increment audit checks the supplied salient states against the
original differential equations. Their q-axis one-step residual stays below
7e-8 pu, while the previous local replay leaves residuals up to 5.05e-5 pu and
an exactly frozen q-axis coordinate. No reference state is injected into the
accepted integration path.

## Hydro observation coverage

The hydro governor now exposes four immutable stored integration coordinates:
Governor Filter, Temporary Droop Integral, Gate Position and Water Flow.
Retained derivatives identify the predictor, corrector or shared-integration
stage and the state at which they were evaluated. Reading them performs no
new derivative evaluation. A zero-time-constant filter retains its dormant
storage coordinate separately from its algebraic output.

In Case 1, all 20 hydro governors expose all 80 coordinates and rates at all
three startup observations. Every retained initial hydro derivative is zero.
The first 43 physical rows, covering startup and 20 ms, are bit-for-bit equal
to the previous control across all 6,190 physical channels. Thirty focused
hydro/solver/observation tests pass.

## Network tolerance control

The original one-second controls differ only in the dynamic network stopping
threshold. Both retain the 0.5 ms reference step, 1e-8 initialization tolerance,
the original converted operating point, and identical cold-state values.

| Previous-core prefault measure | 1e-4 | 1e-6 |
| --- | ---: | ---: |
| Passing rotor channels, out of 30 | 0 | 21 |
| Worst rotor maximum error, degrees | 0.0995656 | 0.0905535 |
| Passing voltage channels, out of 30 | 17 | 21 |
| Worst voltage NRMAE | 0.00408755 | 0.00575050 |
| Passing speed-proxy channels, out of 30 | 26 | 26 |
| Mean network iterations per call | 1.49550 | 9.44555 |

The stricter stopping threshold improves many channels but does not resolve
the state overwrite. Speed channels are generator-frequency proxies. Their
normalization and each voltage normalization use the originating event's full
ten-second reference range.

## Validation status

The corrected core passes 37 focused tests, including the two new failing-before
regressions and existing independent salient-machine comparisons.

The corrected coupled Case 1 run completes the original prefault second at
1e-6 network tolerance. It retains all 2,003 physical rows, including startup,
and 6,190 physical channels. All 240 supplied physical channels from the six
original events are compared without offsets or parameter fitting.

| Coupled prefault measure | Previous core, 1e-6 | Corrected core, 1e-6 |
| --- | ---: | ---: |
| Passing rotor channels, out of 30 | 21 | 30 |
| Worst rotor maximum error, degrees | 0.0905535 | 0.0175464 |
| Bus 6042 rotor maximum error, degrees | 0.0905535 | 0.00124893 |
| Passing voltage channels, out of 30 | 21 | 24 |
| Worst voltage NRMAE | 0.00575050 | 0.00182700 |
| Passing speed-proxy channels, out of 30 | 26 | 28 |
| Mean network iterations per call | 9.44555 | 9.45055 |

The remaining voltage failures are buses 1078, 1071, 1001, 1019, 1037 and 4004.
Bus 1037 has the largest voltage NRMAE: mean absolute error 7.28461e-5 pu,
maximum 1.29634e-4 pu. Speed proxies at buses 1078 and 4115 still fail their
channel-specific normalized limits. This prefault run does not validate any
postfault period.

The preliminary registered suite with the observation change and previous core
reported 2,294 successful, six failed, nine skipped and six aborted tests. One
failure was a missing output directory in the isolated harness. The other
failures include the existing six-case drift gate and unrelated power-flow
checks. With the corrected core and the output directory present, the registered
suite reports 2,295 successful, five failed, nine skipped and six aborted tests.
All five remaining failure identities were present in the preceding run;
complete root-cause attribution remains open. This is not a green system gate.

Reproducible inputs, source/runtime hashes, retained startup states, all physical
rows, diagnostic replays, figures and test logs are sealed in private evidence
set `2026-10-19-case1-salient-state-refresh`. Exact source-tool provenance stays
in the private manifests.

The six-case, 36-event objective, direct bus-frequency comparisons, and the
20-second runtime target remain open. The diagnostic boundary runs and their
timings do not establish those requirements. Acceptance limits remain unchanged.
