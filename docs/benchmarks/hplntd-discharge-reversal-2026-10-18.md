# Hybrid plant discharge and reactive-direction reversal

This verification slice follows the [physical-boundary implementation](hplntd-switching-boundary-2026-10-18.md).
It adds discriminating evidence and safety regressions; **no production model
equation or timer rule is changed**. The six new references are native
source-contract counterexamples, not accepted coupled fault trajectories.

## Exact discriminator

Use the same exact four-bus/two-unit network and ten 0.01 Mvar shunts as the
switching slice. A three-phase bus-2 fault starts at nominal 0.03 s and clears
at 0.08 s: three cycles at 60 Hz. Fault impedance is j1 pu on 100 MVA,
equivalently j529 ohm at 230 kV. Actual native event clocks are retained in
the physical queries immediately preceding each API action and in the audit.

Run three profiles at 0.5 and 0.25 ms. Change **only** CON J+51, Tout:
0, 0.03 and 0.15 s. Exact wrapper, all other constants, network, initial shunt
statuses and events remain identical. Validate all 61 actual float32 constants,
including the nonzero entered discharge time; no automatic correction is hidden.
Cold-regenerate each profile twice and retain raw output, logs and scripts.

The versioned model-library CON table (PDF page 1038), switching flowchart
(1041) and notes (1045–1047) were checked. The table explicitly identifies
J+51 as capacitor discharge time measured after opening; the capacitor cannot
be switched back on before that interval. The flowchart likewise conditions
capacitor insertion on its completed discharge interval. Exact document and
runtime hashes remain in private manifests.

## Functional native discrepancy

All 89 raw channels and every physical device-status query are byte-identical
across the three discharge times at each timestep, not merely numerically
close. Every capacitor is inserted before the fault, opened during it and
reclosed after clearing. With Tout=0.15 s at the coarse step, C1 opens at
0.03449998050928116 s and recloses at 0.08700008690357208 s: about 0.0525001 s
later, which violates the entered discharge interval. All five capacitors
show the same functional issue. This is stronger evidence than an unused
discharge VAR coordinate; physical contacts themselves reclose too soon.

The direct reactive-power jump also exposes a separate timing distinction.
At the first negative-Q endpoint after fault inception, the native fast-start
VAR remains zero from pre-fault positive-Q operation and a capacitor breaker
request starts immediately. On clearing, the opposite jump retains that same
timer. Published sign-specific persistence instead starts a new interval on
each reversal. The InterPSS kernel keeps that behavior; it is not modified to
reuse time accumulated on the opposite side of the reactive deadband.

## Regression coverage

`HplntdDischargeNativeConformanceTest` checks:

- Exact Tout-only input variation and equality of every native raw channel and
  physical query across the three values, including history and duplicate rows.
- Premature native reclose for each of five capacitors at both timesteps.
- Native retained fast timing versus the published reset on both sign reversals.
- Recorded-input InterPSS replay, preserving all timer/status coordinates and
  enforcing Tout before a capacitor breaker request and breaker delay thereafter.
- Imported three-cycle faults for all six profiles: physical status equals the
  controller state, each capacitor opens/recloses, and the published discharge
  interval is respected. Voltages and plant states must remain finite.

The replay files retain 66 columns, including both timer coordinates and native
endpoint-status queries. Coupled files retain four bus voltages and all ten
device statuses/discharge timestamps. They are source-contract/lifecycle guards,
not a widened numerical acceptance envelope for the native fault waveforms.

Two isolated mutations remove the lockout guard and sign-reversal timer reset
independently. Their targeted regressions must fail; the unchanged production
engine is restored before final gates. Neither mutation is committed.

`tools/hplntd_discharge_probe.py` and `tools/audit_hplntd_discharge.py` preserve
the runnable case and audit all twelve cold runs, exact inputs, actual parameters,
model allocation, source/runtime identity, event clocks, raw hashes and embedded
resources. Source list `HPLNTD` is not inferred to be a native wrapper alias.

## Remaining scope

The prior eight bounded coupled switching profiles remain unchanged. These six
counterexamples do not increase accepted native trajectory coverage. Exact
discharge/reversal parity requires resolving the documented reference behavior,
not removing a safety constraint to imitate it. Fine fast-band/slow-band clock
parity, instantaneous fault callback/current/frequency matching, alternate hosts
and exhaustive lifecycle coverage remain open. No full Texas2k dynamics ran.

## Final gates and artifact

32 focused Java checks, all 401 plugin-core tests (six existing optional skips),
and 1,541 selected SMIB/model/solver tests (one existing diagnostic skip) pass
with zero failures/errors. Forty-six offline probe/audit/language guards pass.
The two deliberate mutations each produce exactly one assertion failure and
zero errors; final gates run only after restoring the unchanged engine.

Private archive `2026-10-18-hplntd-discharge-reversal` retains twelve native runs,
six complete replay traces, six coupled physical traces, source snapshots,
mutation/final reports, actual opening/reclose/event clocks and a SHA inventory.
Generated archive JSON must round-trip before sealing. Prior accepted artifacts
and the frozen companion-core dependency remain unchanged.
