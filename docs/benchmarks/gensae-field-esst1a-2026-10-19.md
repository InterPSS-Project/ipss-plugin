# GENSAE field current and composed ESST1A feedback verification

## Source-backed correction

The active field-ceiling discriminator exposed a persistent ESST1A controller
error: regulator-state residual was about 0.544 pu at both integration steps.
The existing washout sampled the clipped field output. Native source-library
PDF pages 257-258 place its tap after the field-current subtraction and UEL/OEL
gates, but before the terminal-voltage/current-dependent final field limits.
The [PowerWorld ESST1A reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST1A%20and%20ESST1A_GE.htm)
provides the independent model diagram and distinguishes the separate dialect.

`IEEE1981ST1Exciter` now uses a stateless custom CML tap at that location.
EXST1 retains its existing clipped-output feedback convention. Backwards
initialization uses the declared field output until the upstream regulator
has initialized; otherwise the new getter would incorrectly initialize the
washout to zero. Both the original wrong tap and failed initialization
realization remain private counterexamples. No machine equations, gains,
time constants, integration method or existing comparison bounds are fitted.

A read-only five-state snapshot exposes native allocation order. Lead-lag
storage is multiplied by its denominator time constant at observation only;
the existing generic named-state keys and integration coordinates are unchanged.
Live CML runtime blocks are used, not nullable symbolic Java fields.

## Independent reference matrix

Eleven configurations at 0.5 and 0.25 ms provide twenty-two profiles, each
cold-generated twice. Six open-generator configurations cover flat/fault,
resistance, 50/100/200 MVA and disabled saturation. Five composed configurations
cover flat/fault, disabled current-limit gains, IEEEVC compensation and an
actively current-derated field ceiling. Generator and controller constants,
two controller selectors and native state allocations are checked explicitly.

All twenty-two accepted cold pairs are raw-exact. The public hashed fixtures
retain every negative-history row and both repeated interval/event sides.
An earlier open-generator probe requested an undefined regulator reference:
one pair differed only by 3.31e-38 in that unused port. Those raw runs remain
counterexamples. The corrected probe never requests VREF without a regulator;
its twelve open profiles were independently cold-regenerated, not normalized.
Two GENROU compatibility runs preserve the earlier raw CSV hash.

## What the comparisons prove

- Twenty-two unforced coupled profiles have fixed per-state bounds over every
  raw row: five machine states and, where present, five exciter states.
  Significant non-angle residuals must shrink under timestep halving.
- Field-current replay uses recorded machine states and terminal phasors.
  Its maximum residual is 1.45e-6 pu, with separate fixed profile bounds.
  This verifies the algebraic field-current port, not predicted states.
- Ten history-aware field-gate replays retain every raw row. The native EFD
  port uses current regulator storage with the preceding recorded machine and
  network boundary, including event-side cache ordering. The independent
  native-port equation passes within 4e-7 pu; production field-current replay
  gives a maximum EFD residual of 2.24e-6 pu with tighter profile-specific bounds.
- The unshifted same-row gate diagnostic is retained. Its approximately
  1.886 pu event residual is not accepted by widening a bound or deleting rows.
- With the corrected tap, ceiling-profile regulator residual decreases from
  6.05e-4 to 2.95e-4 pu under step halving. Feedback-state residual decreases
  from 2.83e-6 to 1.31e-6 pu. The former nonshrinking discrepancy is removed.

Initialization, immutable state observation, active limiter/ceiling exercise,
the pre-ceiling tap and UEL/OEL gate order have permanent guards. Existing
GENROU/GENSAE and ANDES/PowerWorld ESST1A/EXST1 bounds remain unchanged.

## TODO and acceptance limits

- [x] Independently verify the GENSAE field-current normalization on all profiles.
- [x] Add composed-controller/current-limit and active-ceiling discriminators.
- [x] Correct the source-defined feedback tap and preserve backwards initialization.
- [x] Freeze per-profile all-row state, field-current and cached-gate bounds.
- [x] Preserve raw repeats, undefined-port and wrong-feedback counterexamples.
- [x] Finish final registered SMIB/core gates and source reconciliation.
- [ ] Establish complete instantaneous event-side coupled voltage/P/Q/EFD parity.
- [ ] Expand selector, auxiliary-limiter, zero-time and reconnect coverage.
- [x] Add longer multi-swing and composed-governor profiles; see the
  [three-second matrix and diagnostics](gensae-composed-recovery-2026-10-19.md).
- [ ] Resolve the longer-run washout, angle and coupled governor-state discrepancies
  before claiming complete composed-trajectory acceptance.
- [ ] Implement GENSAE checkpoint-history reconstruction.
- [ ] Execute the independent composed PowerWorld matrix with its stator convention.

This is bounded SMIB evidence, not VERIFIED or full-family/system acceptance.
No full Texas2k dynamic simulations are run. Exact runtime/source versions and
raw provenance belong to the private `2026-10-19-gensae-field-esst1a` evidence seal.

Final gates pass 29 focused, 410 plugin-core (six existing optional skips) and
1,751 scoped SMIB/model/solver tests (one existing diagnostic skip), with zero
failures/errors, plus 26 offline probe and six wording tests. Source reconciliation
checks 1,850 Java/POM files and all 66 public reference files against the tested
snapshot. Unrelated worktree changes are excluded.
