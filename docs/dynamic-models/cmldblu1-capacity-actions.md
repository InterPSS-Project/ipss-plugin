# Composite load partial-capacity actions

The bus-specific legacy composite exposes a source-owner capacity action because
its consuming leaves live on expanded internal buses. The parent bus's ordinary
dynamic-load list is not a complete list of those leaves. Actions must resolve
the original source load ID against `getTargetLoadIds()` and call the owner once;
matching internal suffixes or shedding a phantom parent static load is incorrect.

`Cmldblu1Model.canChangeLoad(sourceLoadId, factor)` accepts an exact singleton
source ID or a whole-owner `*`/`#` target. The factor is an increment relative to
initial capacity: two `-0.25` actions leave 50%, rather than 56.25%. A selected
individual ID within an aggregated owner is rejected because its separate
capacity allocation is not retained. Positive restoration, nonfinite factors
and negative remaining capacity are also rejected before mutation. Zero remaining
capacity requires a distinct, exclusively owned transformer/feeder subtree;
collapsed or foreign-attached topology is rejected. The
whole-owner `changeLoad(factor)` applies only a successful prepared action.

Independent matched shed/no-shed captures establish these physical changes:

| Quantity | Change for remaining capacity ratio r |
| --- | --- |
| Static/electronic allocated P and Q | Multiply by r |
| Internal feeder R and X | Divide by r |
| Near and far compensation susceptance | Multiply by r |
| Transformer impedance and tap | Unchanged |
| Substation susceptance | Unchanged |

The implementation updates both branch/shunt objects and their current matrix
stamps; subsequent matrix construction therefore retains the same physical
network. Numerical compensation pairs remain paired: they are not additional
physical shunts. Expanded motors and algebraic leaves receive the same initial-
capacity decrement, preserving each leaf's own states and protection history.
The prepared leaf array is reused, and both integration methods use this owner.

Four independent captures comprise two cold repetitions of a 25% action and a
matched zero-action control. Each pair matches exactly across all 1,006 samples,
including startup and event-side rows. Reconstructing internal voltages from
external network phasors, branch currents and the recorded distribution data
supports fixed transformer/substation values and scaled feeder/compensation;
scaling the transformer or substation instead is distinguishably incorrect.
The retained archive SHA-256 is
`09a82dd77febe6dceb050ef837b24da9f82c3def9a80d5ac726d947a22feadf9`.

`CompositeLoadChangeTest` first reproduced the unsupported owner action under
both methods. It now checks repeated partial actions, physical element scaling,
rejected-action nonmutation, motor online fractions and endpoint current balance,
as well as all-sample independent reference ratios. Complete relay-triggered
trajectory qualification is separate. Full shedding follows the verified disconnection behavior below; no infinite
feeder impedance or artificial small admittance substitutes for disconnection. This is not a claim
of complete composite-model or zone-dialect qualification.

## Full-trip discriminator

A subsequent cold pair with a 100% action also matches exactly across all 1,006
rows. Allocated demand, near/far compensation and effective feeder R/X diagnostics
become zero. Internal voltage diagnostics stop changing, while the external
network continues responding. Reconstructing total current into the source load
bus from both physical lines and subtracting active fault current gives at most
`5.56e-6 pu` after the trip away from the fault-clearing event. Original event-side
samples remain in the archive; the boundary check explicitly separates that
clearing discontinuity rather than treating it as a steady network equation.

This identifies complete disconnection of the composite boundary, including its
substation contribution, despite unchanged stored transformer/substation
parameters. It does **not** justify extrapolating partial-capacity equations to
zero, preserving the energized substation, or inserting a small substitute
admittance. The implementation removes only the owned transformer and feeder matrix stamps,
deactivates their two internal buses and owned leaves, and uses the engine's
normal identity-row representation for inactive buses. Parent-bus loads, shunts
and active fault stamps remain untouched. Internal differential states and
voltage observations remain frozen while the external network keeps solving.
A later matrix rebuild reproduces this disconnected topology. Reconnection is
not supported by this capacity-action API; neither positive restoration nor
foreign-attached or partly collapsed topology is silently accepted. The fully collapsed extension below is separately qualified. Full-trip captures and integrity manifest
SHA-256: `dada624db14a401ddda2fa66c6fa9b6f5b320df013c95fa9fd3579bc8e0761b6`.

The matrix-rebuild regression exposed a separate cached-sequence requirement:
updating `ShuntY` and the live sparse matrix alone left the old physical shunt in
the bus's prepared positive-sequence equivalent. A later fault-clearing rebuild
restored that old value. The owner now applies the same delta through
`applyPositiveSequenceShuntAdmittanceChange`, keeping all three representations
consistent. The test failed under both methods before the correction, then
passed along with the broader composite/tap tests (40 tests, no skips). It checks
both internal-bus diagonals before and after rebuilding and resumes simulation
with endpoint current balance.


The runtime full-trip regression applies two partial stages followed by the
remaining capacity trip under both methods, with an unrelated parent shunt.
It checks inactive bus/branch/device ownership, matrix rebuild consistency,
continued external simulation and frozen internal states. Complete relay-driven
full-trip reference trajectory qualification remains a separate gate.

Relay integration can use `canPrepareLoadChange(sourceId, factor)` after the
composite has initialized but before the engine builds its dynamic matrix.
`canChangeLoad` additionally requires the live matrix and must be checked again
at the action boundary. `ownsCapacityLoad` tests consuming-leaf identity without
allocating a collection; a caller that also walks source-bus loads must skip
these already-owned leaves to avoid applying a wildcard action twice.

The integrator subsequently passed all six complete control/partial/full-trip
reference comparisons under both methods, including relay timing and internal
prepared observations while retaining distinct physical voltages. The coupled
voltage/power range-normalized error envelopes are `5e-5` for modified Euler and
`8e-6` for staggered integration, with a `6e-5` degree peak rotor-angle envelope.
The matrix-rebuild correction is included. Full-trip staggered maximum normalized
error is `3.88e-6`. The final owner regression also retains an unrelated active
parent load alongside the parent shunt through disconnection, rebuilding and
continued simulation. These qualify the represented capacity-action profiles;
other composite thermal, tap and trajectory gaps remain separate.


## Fully omitted distribution: owned removal on an energized parent

A separate paired discriminator sets both feeder impedance and transformer
reactance to zero. With a static/electronic mixture, the full action removes all
owned demand and physical shunts while the common parent remains energized.
This differs from deactivating the represented internal subtree. Stored internal
voltage observations freeze at the accepted pre-action boundary; they do not
represent the parent bus voltage after disconnection.

The runtime removes the tracked owned low-side and far-side shunts from the
physical shunt, prepared sequence cache and live diagonal. It separately removes
each active owned leaf's equivalent admittance before deactivating that leaf.
This is necessary even when physical and numerical shunt stamps cancel: the
next matrix rebuild must also see the correct ownership. It never changes the
parent's activity, unrelated loads or shunts, or connected generators/branches.
`Distribution.LowVoltage` and `Distribution.LoadVoltage` expose the composite's
live/frozen observations separately from the physical bus object.

`CompositeCollapsedFullTripTest` retains all four complete cold traces, two
control/full-action pairs, including startup records and every event-side
sample. All nonnegative samples are compared under both methods. Voltage/power
range-normalized maximum errors are `3.224e-5` for modified Euler and `3.796e-6`
for staggered integration; peak rotor errors are `5.971e-5` and `3.445e-5` degrees.
The existing represented-profile envelopes remain unchanged. The new collapsed
profile uses the same physical envelopes and a `1.5e-6` staggered relay-position
peak envelope: its control and full-action runs both have the same `1.235e-6`
position error before operation. Modified Euler retains `5e-5` for position.

A direct owner regression additionally retains unrelated parent load and shunts,
including a shunt adjustment made after initialization, checks the rebuilt
matrix diagonal, and resumes the external simulation. Positive restoration is
still rejected. Partly omitted distribution full removal and collapsed wildcard
relay routing remain unsupported; this does not expand those contracts. The
independent trajectory profile is static/electronic, not a claim of every motor
protection trajectory or full composite-model qualification.

Private complete capture archive:
`cmldblu1-collapsed-shedding-20261104/captures.zip`, SHA-256
`79598412cecf97c2c595a46506c012d3d9f61dfdbfe3f2918955a7871344c059`.
The exact source inputs, probe, simulator provenance, and raw channels are
retained there. `tools/composite_collapsed_shedding_reference_probe.py` recreates
the discriminator without changing the shared probe.
