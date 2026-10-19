# IEEEST zero-numerator lag and DC initialization

## Exact convention, not a disabled branch

The pinned native IEEEST table explicitly replaces s*T5 by one when T5=0,
with T6 positive. The terminal transfer is then 1/(1+s*T6), not zero.
The previous implementation made its washout gain zero, disabling the output.
The failing-before tests record zero instead of the first 4.99375e-5 pu lag
step, and zero instead of the 0.05 pu limited nonzero-DC output.

`IeeestTerminalBlock` specializes the existing core washout block. For nonzero
T5 it delegates initialization, output and derivative directly to the core.
For exact T5=0 it selects the unity-DC lag equation while inheriting the same
first-order predictor/corrector. No global CML integration change is made.
Tests compare ordinary positive/negative washouts bit-for-bit at both stages,
compare the lag recurrence with the core delay block, and distinguish exact
zero from a very small numerator. Existing named-state keys remain unchanged.

## DC term in deviation coordinates

The controller stores deviations from its solved initial input. An ordinary
washout removes the DC term; the zero-numerator lag must retain it:

```
u = u0 + delta(u)
y = Ks*u0 + lag(Ks*delta(u), T6)
```

The DC term is derived from the initial physical input, never from an oracle
state or fitted offset. Initialization first seeds the deviation realization;
the DC output is available when the existing core initializes the exciter
after the stabilizer. The nonzero electrical-power local flat test remains
at its voltage/speed equilibrium while producing the declared limited output.
`getUnclippedOutput()` exposes the physical terminal signal without advancing
storage or applying output clipping/cutouts.

## Independent evidence and limits

Four accelerating-power flat/fault profiles at 0.5/0.25 ms have eight cold
native runs, all with successful initialization and unchanged parameter
readbacks. Their complete CSV pairs match byte-for-byte. All eighteen allocated
generator/exciter/stabilizer state channels agree with live reads under the
declared CSV serialization. Every raw row, negative history, duplicate event
side and all seven published stabilizer states remain in the lossless fixtures.

For this convention the native last state equals the unclipped terminal output,
not the ordinary washout's memory coordinate. Its static clipping identity is
checked independently at every row. Exact recorded Pe/Pm playback compares
both that state and Vstab: fault maxima are approximately 1.257e-5/3.150e-6 pu
at the coarse/fine steps, under separate 1.4e-5/3.5e-6 bounds. The fine-step
maximum must be below 35 percent of the coarse value. Flat playback stays zero
within 1e-12. No earlier benchmark bound or model parameter is changed.

The nonzero electrical-power native flat pilots are deliberately rejected:
the native exciter initialization reports nonzero derivatives with the
stabilizer's nonzero DC output. Four repeated runs at both steps retain that
initialization rejection separately. No reference correction, gain adjustment,
or copied initial state is used to make them pass. The source-level local DC
test is not presented as accepted native trajectory evidence for those runs.

Coupled accelerating-power terminal-state differences are about
1.381e-5/1.092e-5 pu and do not shrink at the playback rate. Complete coupled
and seven-state coordinate acceptance therefore remains open, as do additional
input/base/resistance, remote-measurement, denominator-validation and lifecycle
profiles. These distinctions prevent a runnable or finite case becoming a
complete-model verification claim.

- [x] Preserve both failing-before model tests and rejected native initializations.
- [x] Implement the exact-zero lag and physical DC term without global CML changes.
- [x] Prove ordinary washout stage/storage non-regression and the analytic lag.
- [x] Cold-repeat four valid profiles and compare all raw terminal-state/output rows.
- [x] Finish latest-baseline focused/core/scoped-SMIB gates and evidence seal.
- [ ] Close the rejected-profile initialization, full coordinate/coupled and
  broader selector/lifecycle gaps before complete-family acceptance.

No full Texas2k dynamic simulations are run.

Final gates pass 100 focused, 415 plugin-core (six existing skips) and 1,803
scoped SMIB/model/solver tests (one existing skip), with zero failures/errors.
All 55 offline probe/wording checks pass. Source reconciliation covers 1,867
Java/POM files and 13 lossless fixture parts, including the latest committed
event-frequency solver guards. The private evidence set is
`2026-10-19-ieeest-zero-numerator`; it preserves accepted and rejected runs
separately, both failing-before tests, all raw rows, source contracts,
stage/storage proofs and terminal regression reports.
