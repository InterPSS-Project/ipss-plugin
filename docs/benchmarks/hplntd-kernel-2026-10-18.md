# Hybrid plant thirteen-state kernel: bounded independent evidence

## Implemented scope

`HplntdInitialization` and `HplntdKernel` provide plant-base equilibrium,
thirteen immutable semantic named-state observations, reactive/active control,
reference slews, frequency derivative limits and low/high-voltage freeze with
recovery timing. Absolute plant commands and increments from the frozen initial
commands are separate outputs. They do not alias REPCC.

The default explicit modified-Euler predictor/corrector remains unchanged.
The kernel also implements the existing `StaggeredEulerParticipant` boundary,
allowing the enclosing solver to own derivative history and event averaging.
Repeated evaluation does not advance the clock. A committed step can accept
the enclosing simulation's actual endpoint clock independently of its nominal
integration step. Frozen-state projection lets the shared solver clear stale
outward history. Mixed pending integration stages and invalid clocks are rejected.

This is **kernel evidence, not a loadable model**. Production wrappers,
bus/device attachment, unique multi-unit ownership and coupled network runtime
remain to be implemented. Renewable runtime presence remains **26/27**, with
HPLNTD missing. No row is promoted to fully verified.

Initialization currently requires positive integral gains and enabled active
and reactive control paths, with initial commands/references within configured
bounds. Unsupported initialization is explicitly rejected. Reactive, voltage
and power-factor equilibrium selectors have equation tests; the independent
profiles below use reactive control with feedback/feedforward enabled.

## Source and numerical-method separation

The frozen Model Library section 36.4, printed pages 1023-1035, defines the exact
35 ICON / 61 CON / 13 STATE / 45 VAR bus model and its downstream interface.
Its SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The diagrams and scaling notes were visually reviewed; data tables and
initialization/freeze rules were read in full.

Application Guide volume 2, chapter 13 and chapter 24, confirms the separation
between derivatives, numerical integration, observation and pause handling.
Its SHA-256 is
`ef101abe537e06d860c4adcdb4ab8d54f704ba6ad3cda843aa3ab9189152c274`.
The initialization/run flowcharts were rendered and visually reviewed.
The existing opt-in solver implements the documented STORE rule also cited in
the [earlier solver report](ieee39-benchmark-2026-10-15.md).

For matching that reference method, the shared solver uses:

```
rateAtEvent = (newRate + retainedRate) / 2
xNext = x + dt * (1.5*rate - 0.5*retainedRate)
```

The first equation applies only after a discontinuity. It is not a change to
the model's gain or physical differential equations. A separate scalar test
verifies both reference-slew states across all ten profiles, including the
2*dt lag realization, derivative limits and event startup. Errors stay below
1e-7 pu without reseeding states from the reference trace. This resolves the
approximately 0.0055-pu fast-state mismatch found with unmatched integration.

The fine-step recovery mismatch was independently isolated to the simulation
clock: single-precision accumulated reference timestamps can fall on a different
side of Tfrz expiry than a locally accumulated double-precision clock. The
comparison supplies recorded accepted endpoint times as clock inputs. It does
not replace state values or fit a timer, threshold or parameter. The default
solver boundary continues to advance using its own accepted clock.

## Independent profiles and retained failures

Six new four-bus/two-unit profiles were cold-run twice: twelve successful runs,
exact paired RAW/DYR/probe/complete-CSV bytes, 89 channels plus time and 405 or
805 samples. Inputs include measured frequency, nearby complex voltages,
branch P/Q, all thirteen plant states and all downstream states/VARs.

Reference-step, low-voltage freeze/frequency-rate, and initially high-voltage
freeze profiles run at 0.0005 and 0.00025 seconds. The low-voltage test applies
a three-phase bus-2 fault from 0.05 to 0.10 seconds: three cycles at 60 Hz.
Tests require freeze, recovery, release and frequency derivative limits actually
to activate. The high-voltage test starts above its threshold; transient
high-voltage entry/recovery remains open.

Four earlier flat/reference profiles remain regression evidence. They lack a
frequency measurement channel, so state 9 is diagnostic only for those four;
their recorded frequency deadband must remain exactly inactive. The six new
profiles compare all thirteen states independently. All ten profiles compare
Pcmd/Qcmd and both incremental outputs, with ten 35-column paired traces and
5,648 retained rows including history and event-side duplicates.

Matched-method command residuals are approximately 1.61e-6 pu coarse and
2.16e-7 pu fine in the fully measured reference-step profiles. The largest
fault measurement-filter residual is 6.23e-5 pu coarse and 1.57e-5 pu fine;
reactive-command residuals are 4.07e-6 and 1.20e-6 pu. Fast reference states
agree within initial measurement/float precision. Each channel has its own
bound, tightened to approximately ten percent above the observed maximum,
with exact/inactive floors of 1e-12. No gains, limits or initial states are fit.

The activating fault trace also exposed a real reactive-integral bug: limiting
the combined proportional-plus-integral output must not stop this model's
integral storage before its own bound or voltage freeze. Correcting that check
removes the persistent approximately 0.0149-pu offset. The earlier failing
Heun comparisons, unmatched-clock staggered comparison and non-activating high
probe are preserved, not overwritten or relabeled as accepted coverage.
See the [historical diagnostic](hplntd-kernel-diagnostic-2026-10-18.md).

## Remaining acceptance work

- Exact import and deferred network attachment for HPLNTDU and REAXDU, unique
  plant ownership and stage-coherent multi-unit advancement.
- Actual coupled network comparisons for this kernel, including faults,
  frequency, trip/return, base changes, pause/resume and all controller selectors.
- Disabled Q/F, proportional-only, alternate feedback/feedforward and
  initialization-outside-bounds policies; active-PI and integral-bound projection.
- Independent voltage/PF and compensation profiles, short-Tfrq bypass, reference
  rate saturation in both directions, repeated voltage interruptions and
  high-voltage entry/recovery.
- MSS switching with physical Mvar thresholds and correct shunt boundary units.
- Downstream held-output, base-load and ignored-unit lifecycle; the previously
  preserved manual-base pause reset remains a separate source/tool discrepancy.
- An activating independent PowerWorld comparison. Current evidence is not a
  claim of cross-vendor dialect or complete controller equivalence.

No full Texas2k dynamic simulation is run or required.

## Final gates and archive

On plugin Java baseline `08c49944f`, the final gates pass with zero failures
or errors: **51 focused**, **371 plugin-core** (six optional skips), and
**1,455 selected SMIB/model/solver** tests (one existing diagnostic skip).
The nine new kernel tests cover equilibrium/selectors, stage discipline,
frequency rates and bypass, low/high freeze with interruption/recovery,
incremental coordinates, proportional saturation, pure derivative evaluation,
frozen-state projection and explicit accepted-clock validation.

Generated inventory text matches its compiled generator. The wording guard
checks 209 documents/new Java files with zero violations, and all six wording
policy tests pass. All test classpaths use isolated companion core `38e269ecf`,
SHA-256 `fbd9f015887b51ba438a538120cff00d0a724e76fd017674ec28fb0a924ab125`.
No shared dependency installation is changed.

Private archive `2026-10-18-hplntd-kernel` seals scoped sources, all twelve
new native runs, ten paired traces, exact scripts, final test reports and
diagnostics. It verifies the earlier immutable diagnostic archive rather than
overwriting it. The source audit matches every owned Java/resource file to the
tested snapshot and all other baseline Java/POMs to Git, including the separately
committed governor correction. Unrelated working-tree changes are excluded.
