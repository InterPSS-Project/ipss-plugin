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
configuration. Raw relay diagnostic current/position publication differs around
trip-event samples and still needs an explicit observation-stage contract.
Protection coordination, additional reset/re-pickup settings, remote-action combinations, reconnection,
production-step and longer-duration cases remain on the implementation work list.
The accepted-history tests also exercise both monitored ends, but those additional
terminal cases are lifecycle tests rather than new independent trajectory captures.

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
