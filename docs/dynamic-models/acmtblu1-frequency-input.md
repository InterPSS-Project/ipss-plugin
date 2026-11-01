# Compressor frequency input and running-current sensitivity

Status: sampled-phase replay, frequency-dependent constitutive current and
expanded coupled controls pass through the shared modified-Euler and staggered
runtime. Full compressor protection, restoration and composite-load trajectory
qualification remains open. The catalog status remains `LOADABLE`.

## One measurement path and one state owner

The native standalone compressor now samples terminal phase directly. Passing
an already filtered bus-frequency signal through the motor's own `Tf` filter
introduced an additional measurement response. Rebasing the general bus
frequency block at network events also discarded the phase jump needed by this
model's independent measurement history.

`LD1PACImpl` retains its existing four continuous coordinates and reusable
derivative/current buffers. The frequency coordinate remains absolute pu in the
shared state vector; the independent allocated state is observed as deviation
from one. No parallel model implementation or duplicate state vector is added.

`FrequencyInputMode` selects the measurement contract before initialization:

- `BUS_FREQUENCY` preserves the typed-client input convention.
- `TERMINAL_PHASE_DOUBLE` uses an accepted phase sample and a double clock.
- `TERMINAL_PHASE_BINARY32` accumulates the accepted sample clock in binary32,
  matching the independently recorded input clock. Native imports select this
  mode. This does not change the nominal integration interval or the network's
  simulation clock.

The sampler computes the wrapped phase increment divided by the rated angular
frequency and its actual accepted clock increment. It advances only in the
accepted `afterStep` callback. Trial derivatives, Norton-current requests and
zero-duration refreshes cannot advance phase memory, timers or continuous
states. Reinitialization resets the measurement history. Changing its mode
after initialization is rejected.

## Explicit scheduled-event preparation

The small `NetworkEventInput` contract identifies a zero-time input-history
operation after a scheduled network event has converged and before integration
resumes. The common solver prepares the participating bus/device list during
initialization and notifies active owners after the event's `INITIAL` solve.
Both `DefaultDynamicSimulationSolver` and `StaggeredEulerSolver` use that path.
No per-iteration discovery or duplicate integration owner is introduced.

For this compressor, the callback rebases the held raw frequency input without
advancing phase or time. A positive `Tf` uses the declared average of the held
pre-event input and zero for the first following interval. A bypassed frequency
coordinate projects to zero deviation. Repeated notifications at the same
physical time are idempotent; invalid or decreasing times are rejected.

The unmodified external core solver does not dispatch this plugin contract.
Clients that manually drive model integration must deliver the explicit event
notification at the same converged boundary. Ordinary derivative evaluation is
not a substitute for this callback.

With `Tf = 0`, the raw coordinate records the newly accepted phase derivative,
while the published limited frequency belongs to the input used by the preceding
current calculation. Those observations are retained separately; the comparison
does not shift samples to make them coincide.

## Shared frequency-dependent current law

For the legacy compressor characteristic, the applied frequency is limited to
0.95–1.05 pu. The independently documented running factors are
`1 + CmpKpf * df` for active power and
`1 + CmpKqf * df / sqrt(1 - CompPF²)` for reactive power.
The reactive normalization is prepared outside numerical evaluation and refreshed
after valid live power-factor changes. Unity power factor with nonzero reactive
frequency sensitivity is rejected rather than regularized with an invented floor.

These factors apply to both running polynomial and running impedance branches.
The low-voltage running branch therefore uses
`P = CompLF * Gstall * V² * activeFactor` and
`Q = -Bstall * V² * reactiveFactor`. A stalled group uses the full stall
admittance without running frequency factors. Heating uses the same electrical
current law; parallel reactive compensation remains outside motor heating.

The equations belong to the legacy characteristic policy, independently of the
selected measurement mode. Composite consumers therefore receive the corrected
equations while their upstream measurement qualification remains a separate task.

## Independent checks and numerical limits

The retained cold-repeat controls include positive and bypassed `Tf`, nonzero
active/reactive sensitivity, moderate and deep faults, and two integration
intervals. Every reference channel, startup sample and event-side duplicate is
retained. The expanded coupled controls keep the existing voltage, P/Q, speed
and rotor-angle limits described in the [standalone import contract](acmtblu1-shared-import.md).

Recorded-phase replay isolates the measurement pipeline. The staggered filtered
state differs from the independent allocated state by less than 7.3e-8 pu; its
published output remains below the 2e-7 pu regression limit. Modified Euler is
checked against the exact held-input exponential response with an explicit
Heun truncation bound. Its complete differences from the source integration
are retained, including discontinuity peaks of approximately 0.0221 pu; they
are not presented as native state parity or hidden by time shifts.

The bypass coordinate satisfies the declared phase-derivative equation directly.
Its worst independent raw-coordinate difference is approximately 1.45e-6 pu at
a phase discontinuity, with a separate 2e-6 pu observation limit. The published
limited output stays within 2e-7 pu. The implementation retains mathematical
pi and does not fit a conversion constant to remove that small raw discrepancy.

Recorded-voltage/frequency checks independently qualify running/stalled P/Q and
both temperature derivatives. They retain the 2e-6 pu power and 2e-7 pu/s
derivative limits. These controls feed other continuous coordinates; they do
not establish independently integrated temperature or complete composite parity.

Active UV/contactor/thermal actions, restoration, production integration
intervals, long runs and the composite's separate internal-voltage/static-load
discrepancy remain open. No complete-run performance claim follows from these
component and short coupled checks.

The [electrical-input contract](acmtblu1-electrical-inputs.md) separately qualifies
accepted voltage/heating inputs, the sensed-voltage bypass and pre-event
endpoint derivatives. Frequency and electrical measurement modes are explicit
independent choices; they share the existing state vector and accepted callback.
