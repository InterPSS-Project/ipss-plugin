# TIOCR1 accepted-step runtime

TIOCR1 uses the same inverse-time curve, moving-element position, breaker timer
and physical trip actions with modified-Euler and staggered integration. These
are accepted-step histories, not trial differential coordinates. The staggered
solver explicitly admits this relay and its owned load-shedding port, checks
their attachment ownership, and leaves the existing accepted-step callback in
control of protection actions. It does not create a second relay integrator.

`afterStep(0)` is read-only. Previously a zero-duration call could reset a partly
advanced position when reset time was zero and current had fallen below pickup.
Invalid intervals are rejected without changing history. `IntegrationStateAccess`
exposes the existing named histories, an empty differential-coordinate map and
a shared not-evaluated derivative snapshot. Observation cannot advance timers.

Current measurement evaluates the selected terminal's branch-admittance equation
using primitive arithmetic. Both terminal voltages and admittances remain live;
the calculation avoids temporary complex products and sums. Tests compare it
with the original complex equation across both terminals and varied phasors.
No runtime speedup is claimed without a separate timing benchmark.

## Accepted protection and staggered history

Relay trips can change the network after the integration step, outside the
scheduled event handler. The relay now increments a monotonic action revision
only when it actually opens a branch or sheds additional load. The staggered
solver binds these relay owners during initialization and marks an input
discontinuity when their revision changes. The next interval
uses its existing discontinuity handling instead of extrapolating derivatives
across the previous topology. No extra network solve or controller state vector
is added. A monitoring-only operation does not dirty the matrix or trigger this
notification. Unrelated matrix changes are not treated as relay actions; switching
controllers retain their own event/history policies. Revision checks use direct
bindings and reusable primitive storage, with no per-step attachment scan.

Before this correction, the two operating SMIB controls had peak rotor-angle
errors near 0.005 degrees despite matching trip times. After the correction,
all three retained relay reference controls pass complete boundary comparisons,
including event-side duplicates, under staggered integration. The existing
modified-Euler comparisons remain in place.

The new staggered bounds are maximum absolute errors: voltage `4e-6` pu at the
source and `9e-7` pu at the adjacent bus, active power `3.6e-6` pu, reactive power
`2.6e-5` pu, rotor angle `9.5e-5` degrees, and speed deviation `4.2e-8` pu.
These tighter bounds apply to the qualified synthetic profiles only.

## Regression validation

Core validation passed with 486 tests and four existing skips; the selected
three-phase checks passed all 15 tests. The registered suite and the two newly
registered reference groups were executed in separate invocations, covering
3,625 tests with four unchanged baseline failures, no errors and 13 skips.
Evidence retention checks every previous baseline test case and the exact
failure messages, rather than comparing aggregate counts alone.

The relay lifecycle checks and both-method base reference comparisons pass.
The earlier broad matrix-dirty experiment caused a switching-compensator
regression; the final owner-specific action revision passes that test too.
Only the final revision is retained as the production implementation. Private
evidence includes the final runtime/source hashes, complete reports and the
unchanged independent capture identities.

## Reset and repeated pickup

Independent cold-repeated SMIB captures now cover a short fault that only partly
advances the moving element, recovery to zero, and a second fault that reaches
pickup. Monitoring and physical-trip configurations pass with both integration
methods. All nonnegative grid samples and event-side duplicates are compared;
the immutable captures also retain negative-time startup samples.

Reset is checked throughout the recovered interval, second pickup agrees within
one integration interval, and the physical trip agrees within `5e-6` seconds.
Voltage and active/reactive power additionally require full-reference-range
NRMSE below `0.001`. The new profiles use their own tightened peak-error bounds;
the earlier single-fault bounds remain unchanged. In particular, the repeated
fault has a staggered voltage peak error of `6.09e-6` pu, above the earlier
single-fault bound but below its new `6.7e-6` pu bound. No model parameters or
production equations were changed for this qualification.

## Remaining qualification

An additional cold-repeated independent SMIB profile isolates remote static-load
shedding with all transfer-trip targets disabled. Both methods retain all lines,
shed the requested fraction once and pass complete voltage, power, rotor-angle
and speed trajectories. The active load follows constant current and the reactive
load constant impedance. All event-side duplicates are retained. Peak voltage
errors are `0.000183` pu for modified Euler and `2.66e-6` pu for staggered
integration; peak rotor-angle errors are below `5.1e-5` degrees. The reference
capture identity is pinned in `Tiocr1LoadOnlyReferenceTest`; this profile does
not establish dynamic-load or low-voltage shedding equivalence.

Remote static-load lifecycle checks now exercise constant-P, constant-I and
constant-Z active loads with constant-Z reactive load under both methods. They
verify physical removed power from the combined matrix change and live current
correction at changing terminal voltages. Repeated network rebuilds and duplicate
refresh calls preserve the accepted shed fraction exactly once. These checks
cover normal-voltage operation; low-voltage curve transitions, dynamic-load
targets and additional independent remote-action trajectories remain separate requirements.

These results qualify the base coupled boundary profiles, not every relay
configuration. Raw relay diagnostic current/position publication around branch-trip
event samples still needs an explicit observation-stage contract; the energized
load-only position is qualified separately below.
Protection coordination, additional reset/re-pickup settings, remote-action combinations, reconnection,
production-step and longer-duration cases remain on the implementation work list.
Both monitored ends now have independent monitoring-only trajectory captures with
distinct charging currents, as described below. Opposite-end physical actions
and additional terminal configurations remain separate requirements.

## Attachment ownership and atomic rejection

The relay binds to its original monitored terminal and network. Construction
validates every transfer branch and optional remote load host before attaching
any relay port. Network map identity is checked in addition to endpoint network
identity, so an unrelated object with the same bus or branch identifier cannot
be used as a target. These checks use direct map lookups, without network scans.

Initialization, trial callbacks, attribute refresh and accepted-step processing
reject detached or reparented targets before modifying histories or physical
outputs. Every configured target is checked before the first transfer trip or
load action; a missing later target cannot produce a partial trip. Restoring the
original attachment permits its normal lifecycle to continue. Same-network
reinitialization still resets protection history as before.

Both integration methods exercise cross-network targets with matching IDs,
reparenting, restoration and removal of a later transfer target. Existing
independent trajectory tests retain their original tolerances and samples.
This ownership correction does not change relay curves, pickup sequencing,
breaker timing, or extend the qualification scope listed above.

## Left-end current and energized relay position

The moving element uses the solved current at the beginning of its accepted
interval. Independent reconstruction from the complete load-only reference,
including both sides of every event, reproduces position within `1.86e-7`.
Substituting the right-end current produces a `0.002962` error. The reference
therefore distinguishes this input timing from a controller-equation change.

The ordinary predictor samples that current into one reusable scalar; the
corrector and observation methods cannot overwrite it. The staggered solver
samples the same input from its existing indexed relay bindings after the left
network solve and before advancing differential coordinates. The accepted
`afterStep` callback remains the only owner of position, breaker timers and
physical actions. Zero-duration calls do not consume or change the sample.
Operated relays skip unused integration-input measurements.
Standalone accepted-step calls without an integration-stage sample continue to
use their supplied network boundary.

Remote load shedding does not necessarily open the monitored branch. Its relay
position now remains latched at one when that branch stays energized, matching
every post-operation sample of the independent load-only reference. An action
that opens the monitored branch still clears the position. The previous
unconditional reset incorrectly produced a unit position error after shedding.

Both-method coupled position checks now require peak errors below `3e-5` for
modified Euler and `8e-7` for staggered integration; measured errors are
`2.72e-5` and `6.80e-7`, respectively. The tests also require the recorded-input
left-current reconstruction to pass and its right-current negative control to
fail. Physical boundary, trip-time and reset checks retain their original gates.
This does not qualify branch-trip observation ordering, dynamic-load actions,
reconnection or other configurations listed above.

## Independent terminal selection with charging current

Cold-paired monitoring-only captures select either end of the same synthetic
branch, with nonzero total line charging. The line remains connected. The
complete recorded phasors reconstruct the selected terminal current within
`4.59e-7` pu, whereas substituting the other terminal produces about `0.163` pu
error. Thus the test distinguishes terminal selection from reversing an
equivalent series current on a line without charging.

Both imported terminal configurations pass modified-Euler and staggered SMIB
fault comparisons, retaining every nonnegative grid sample and both sides of
fault application and clearing. The original negative-time samples remain in
the immutable reference archive. Voltage and active/reactive power each require
full-reference-range NRMSE below `0.001`, alongside the existing physical peak
limits. These checks do not alter model equations or source parameters.

| Complete terminal comparison | Modified Euler | Staggered |
| --- | ---: | ---: |
| Maximum current error, pu | 0.000522 | 0.000000869 |
| Maximum moving-element position error | 0.0000194 | 0.000000290 |

New current bounds are `5.8e-4` and `1e-6` pu, and position bounds are `2.2e-5`
and `3.3e-7`, respectively. Tests pin the reference archive and require exact
cold-repeat data identity. This closes independent monitoring-terminal
selection coverage at the tested interval; it does not qualify opposite-end
physical actions, dynamic-load shedding, reconnection or production-step timing.

## Reinitializing load-target ownership

Every preparation clears the previous static-load contribution before resolving
the current target. If a static source has been replaced by a dynamic current
port, shedding now changes that dynamic load alone. Previously the relay also
removed its cached static admittance, double-counting the action.

A both-method regression reproduces the defect before the fix and checks the
dynamic power reduction, unchanged network stamp and absence of stale static
current correction after voltage changes. The focused relay and reference suite
passes without changing trajectory tolerances. This preparation fix does not
establish composite-load shedding support: expanded internal components and
their equivalent distribution network require model-owned capacity changes.

## Partial composite-load action

The remote load port now resolves an expanded `CMLDBLU1` owner by its original
source-load identity during initialization. A singleton source or whole-owner
wildcard is supported; selecting one source from an aggregate is rejected.
Relay wildcard actions on a collapsed parent/internal bus are also rejected
until the unrelated static residual can be allocated without counting owned
components twice. The source-owner API remains distinct from a whole-bus relay.
The dynamic matrix does not yet exist at that preparation stage, so live-matrix
and owner validity are checked again before the accepted action. Failed target
preparation cannot silently become a successful relay action.

The action calls the model's capacity operation once, rather than searching for
internal leaf IDs or removing an obsolete parent static-load stamp. That operation
changes demand and the verified distribution equivalent together. The composite
also updates the bus's cached positive-sequence shunt admittance, preventing fault
clearing or another matrix rebuild from restoring the old compensation.

Complete shed/no-shed comparisons retain startup evidence and all three pairs
of event-side samples: fault application, relay operation and fault clearing.
The internal `VAR29` reference is a prepared-voltage diagnostic: on the relay
action's pre-solve row it already uses changed feeder parameters with the previous
solved boundary. The recorder retains physical `Load.V` separately from
`Load.PreparedV`; it does not change physical voltage to match that diagnostic.
Independent reconstruction of the mixed-boundary reference row agrees within
`4.1e-7` pu. Both physical and diagnostic samples remain in the complete recording.

The partial-action comparison passes both integration methods. Maximum
range-normalized RMSE across external voltage/power and internal voltage
observations is `3.28e-5` for modified Euler and `5.36e-6` for staggered integration.
Peak rotor-angle errors are below `4.9e-5` degrees. The matched no-action control
also passes. Regression envelopes require normalized error below `5e-5` and
`8e-6`, respectively, with unchanged full reference ranges and all event samples.

## Owned composite disconnection

Full-capacity removal also passes complete independent reference comparisons
under both integration methods when the composite owns a distinct transformer,
feeder and internal-bus subtree. The operation removes those branch stamps,
deactivates owned internal devices and buses, and leaves their last physical
observations frozen. Parent attachments and active external faults are preserved;
subsequent matrix rebuilds retain the disconnected topology.

The preparation and action checks reject foreign internal attachments and
unsupported collapsed topologies before mutation. Full-trip references retain
all relay and fault event samples. Full-trip voltage/power normalized errors are
below the same `5e-5` modified-Euler and `8e-6` staggered regression envelopes,
with rotor-angle peak error below `6e-5` degrees. Restoration remains unsupported.
