# GGOV1 declared valve limits and Case 5 startup

The original Case 5 has 18 online GGOV1 governors whose computed initial fuel
valve position is below the entered minimum. Expanding that minimum to make
the initial derivatives zero removes a real startup transient in the supplied
reference. The imported-model path now retains the declared valve limits.

This correction follows a specific independently observed discrepancy. It does
not change the RAW/DYR parameters, converted operating point, numerical
tolerance, integration step, acceptance limits or reference alignment.

## Independent localization

All 23 distinct synchronous machines observed across the six original Case 5
events were replayed through the last converged baseline endpoint, 0.771 s.
The complete machine, exciter, stabilizer and governor stacks initialize
normally and evolve freely. Only terminal voltage magnitude and phase are
prescribed. Reference phase is reconstructed from supplied machine states and
P/Q/V using the stator equation; its largest voltage-magnitude closure residual
is 2.49e-7 pu. This is conditional model evidence, not a full-network acceptance
result.

The local-voltage control reproduces the coupled rotor trajectories within
0.000372 degrees over this prefix. On the supplied boundary, 22 of 23 machines
already meet the 0.02-degree rotor limit. Unit 7406/2 is the exception: its
mechanical power differs by 6.0293 MW and its rotor angle by 0.72954 degrees.

The supplied governor states identify the initialization error directly:

- The computed unrestricted valve position is approximately 0.134685.
- The entered minimum is 0.15; the supplied initial actuator and load-limiter
  integral states are both 0.15.
- The governor integral and temperature measurement retain the unrestricted
  initial value. Turbine power starts at 14.3221 MW.
- With the original gain 1.11, no-load fuel 0.1 and 372 MW turbine base, the
  minimum valve implies 20.646 MW. The supplied turbine state rises toward
  that value with the entered 0.12-second lag.

The former initialization expanded the minimum to approximately 0.134685 and
therefore held mechanical power near its initial value. A zero-derivative audit
alone incorrectly made this altered equilibrium appear healthy.

## Implementation and conditional verification

`useDeclaredValveLimits` is copied by the builder and selected by the direct
GGOV1/GGOV1D import path. It keeps the normalized entered position limits and
clips the initial actuator and load-limiter integral to those limits. Initial
turbine power, governor integral and measurement history retain the computed
operating point. Existing typed clients retain their prior default and can
select the declared-limit behavior explicitly.

| Unit 7406/2, supplied-voltage replay | Before | Declared limits |
|---|---:|---:|
| Maximum mechanical-power error, MW | 6.029295 | 0.000170 |
| Maximum rotor-angle error, degrees | 0.729540 | 0.000194 |
| Rotor-angle RMSE, degrees | 0.559720 | 0.0000804 |
| Maximum electrical active-power error, MW | 8.237713 | 0.002123 |
| Maximum electrical reactive-power error, Mvar | 9.492829 | 0.017663 |

All 23 conditional rotor comparisons now meet 0.02 degrees; the largest is
0.002030 degrees at 6242/1. Every output in the other 22 machine replays is
exactly unchanged. The six-case cold inventory finds 18 affected governors,
all in Case 5. This inventory is not a substitute for coupled trajectory tests.

The isolated focused run passes 47 tests with no skips. New lower-bound,
in-range and upper-bound tests check initialization, stored states, first
turbine derivative, the analytic lag response, unchanged parameters and
reinitialization. The same tests on the old governor fail both out-of-range
profiles and pass the in-range control. Parser tests cover both import variants;
the existing independent and trip conformance checks also pass.

The scoped changes are compiled over the frozen comparison runtime so unrelated
concurrent source changes cannot affect the numerical comparison. The registered
suite finds 2,038 tests: 2,006 pass, 19 fail, nine are skipped and four are aborted.
The identical-dependency control with the old governor has the same 19 failures
plus the two new out-of-range regressions. The correction adds no failure to
this recorded suite; it is not a claim that the complete suite passes.

The current tracked reactor compiles the production modules and passes the
three-phase module's 468-test run (28 skipped), but its core test compilation
fails because the installed core dependency lacks the static-load matrix split
API. The unchanged-source control fails at the same API. A clean isolated build
using the recorded comparison core and its original dependency graph completes
2,386 registered tests: 2,355 pass, two assertions fail, two import tests error,
and 27 are skipped. All four failing tests concern static power flow or import
and also fail in the unchanged frozen-runtime control. All 43 governor tests
and both registered independent governor trajectory tests pass in this fresh
build. No installed dependency is replaced. The broader suite remains failing;
no system-wide green gate is claimed.

## Coupled Case 5 result

The corrected coupled run reaches 0.769 s and then fails network convergence.
It therefore remains a failed prefault run. The comparison retains all 1,541
converged rows, the cold history and all original six-event reference occurrences.
The initial snapshots confirm that only the 18 intended governor actuator and
load-integral coordinates change; turbine operating power is retained, and the
other 385 active device snapshots are identical.

| Common comparison interval and worst metric | Before | Declared limits |
|---|---:|---:|
| 0-0.1 s rotor maximum error, degrees | 0.060448 | 0.006608 |
| 0-0.1 s passing rotor occurrences, out of 30 | 29 | 30 |
| 0-0.1 s bus-voltage NRMAE | 0.002664 | 0.002664 |
| 0-0.3 s rotor maximum error, degrees | 0.625181 | 0.087038 |
| 0-0.769 s rotor maximum error, degrees | 5.102777 | 5.375339 |
| 0-0.769 s bus-voltage NRMAE | 0.045112 | 0.051132 |
| 0-0.769 s machine-terminal-voltage NRMAE | 0.046700 | 0.052436 |

The independently demonstrated governor correction removes the early rotor
outlier but does not repair the upstream voltage discrepancy. The later coupled
errors increase. Every normalized error above uses the relevant original
ten-second reference range, with the same samples used before and after; the
full prefault voltage and rotor gates still fail. These results do not justify
relaxing a postfault limit. The six-case, 36-event accuracy objective and the
runtime objective remain open.

## Network-memory observation used during localization

A read-only observer first reproduces all 1,545 rows and 5,258 physical channels
of the failed baseline exactly, including the same failed next step after
0.771 s. All three cold snapshots also agree. The observer records accepted
network algebraic memory and failed iterates separately, and closes its state
streams on failure.

Replaying accepted network memory together with local terminal voltage for
1016/1, 1016/2 and 1046/1 reproduces P/Q within 1.71e-13 MW/Mvar. Across 324,390
numeric state/output/rate entries the largest difference is 2.65e-14. No
differential or controller state is substituted. This repairs the earlier
voltage-only diagnostic's fidelity failure; it does not establish independent
converter correctness.

The first nonzero high-voltage correction in the coupled fleet occurs at
0.2385 s. The reference mismatch is already present by 0.1 s, so that correction
cannot explain the first discrepancy. The correction equations and failed-step
rollback are unchanged. The earlier negative replay and failed baseline remain
retained as evidence.

## Evidence locations

Generated evidence is retained under `target/case5-network-memory-observer-20261019`,
`target/case5-memory-boundary-replay-20261019`,
`target/case5-machine-boundary-20261019`,
`target/case5-ggov1-initial-limit-20261019`,
`target/ggov1-declared-limits-20261019` and
`target/case5-declared-limit-boundary-20261019`. The corrected coupled evidence
is in `target/case5-declared-limit-coupled-v2-20261019/case5`; its
`comparison/common-prefix/coupled-errors.png` shows the early improvement and
remaining prefault errors. The first failed preparation is retained separately.
Inputs, runtime classes, producers and comparison arrays have recorded hashes.
The supplied state export and consulted model-library provenance are retained
privately. No new reference-simulator run was used.

The immutable private evidence set is
`verification-artifacts/2026-10-19-case5-declared-governor-limits`. It retains the
source patch, raw supplied-state extraction, both successful and failed
diagnostics, common-sample comparisons, test controls and dependency provenance.
The expanded temporary build checkout is removed after its source archive,
changed build descriptors and test reports are verified and copied outside it.
