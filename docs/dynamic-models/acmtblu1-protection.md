# Compressor UV, contactor and timer integration

Status: independent recorded-input and coupled SMIB UV/contactor controls pass
under modified Euler and staggered integration. Thermal actions now have
[separate qualification](acmtblu1-thermal.md). Restoration,
production-step/long-duration behavior and complete composite-load
parity remain open. The catalog status remains `LOADABLE`.

## One numerical owner

`LD1PACImpl` directly exposes nine integration coordinates for the legacy
compressor characteristic, used by native standalone and legacy composite
imports: sensed voltage, sensed frequency, both temperatures, both UV timers,
both stall timers and the restart timer. The same fields and reusable buffers
serve both methods. No expression interpreter, adapter or duplicate model is
introduced. Other typed/zone characteristic policies retain their existing
four differential coordinates and accepted-counter convention.

The legacy timer derivatives are zero or one, selected by the corresponding
pickup and run/trip state. Trial RHS/current requests never increment a timer.
Modified Euler integrates those rates through its predictor/corrector;
staggered integration uses its shared state vector and two-step history.
`afterStep` applies discrete actions and model-defined resets to the already
integrated values. Calling it alone cannot integrate a legacy timer again.

Native imports retain binary32 rounding at accepted timer storage. Derivative
calculations and nominal integration intervals remain double precision. The
selected integrator therefore owns the time integral; the precision policy
does not replace it with a separate counter.

Scheduled network events prepare pre-event endpoint rates before refreshing
held inputs. Dropped timer pickups reset at that explicit zero-time boundary;
the callback does not advance time. Repeated notification at the same time is
idempotent. Repeated derivative evaluation and `afterStep(0)` remain pure.

The independent staggered source's first timer sample after a zero-to-one
pickup is three quarters of an interval, followed by a catch-up increment.
Modified Euler integrates the held pickup over one full interval instead.
Recorded-input checks allow the resulting quarter-interval difference only at
the first sample following the scheduled event. Later timer samples retain
their strict limit. No timestamp shift or timer-delay adjustment is applied.

## Protection and representative motor current

UV and contactors use raw terminal-voltage magnitude, independently of the
motor's voltage filter. Either UV stage latches the remaining fraction at
`1 - Fuvr`; both UV timer states then reset to zero and remain inactive.

For the legacy contactor, falling voltage can only decrease the connected
fraction along the off curve. Recovering voltage can only increase it along
the on curve. Recovery takes the maximum of the existing fraction and the
on-curve value, rather than weighting recovery by a previous trip fraction.
The connected fraction changes at the explicit network-event boundary as well
as accepted endpoints.

A fully open contactor makes each representative group's heating current zero;
temperature cools through its differential equation. A partially connected
population retains the individual motor current. UV shedding acts outside the
representative motor branch, so even complete UV shedding does not zero that
individual heating current. The physical compensating shunt remains separate
from motor heating and is included in the terminal P/Q audit.

## Independent evidence and gates

Cold-repeat controls exercise both UV stages, partial and complete UV shedding,
complete contactor opening, partial opening and partial reclosure, at two
integration intervals with repeated faults. Accepted cold pairs match exactly
over every channel and sample, including event duplicates. Inconsistent earlier
captures are retained separately as rejected evidence; they are not overwritten
or accepted by clearing diagnostic storage.

`CompressorProtectionConformanceTest` retains independent timer integration in
recorded-continuous-state replay and strict coupled voltage, P/Q, speed and
rotor-angle checks. Replay gates bound UV/contact fractions, representative
currents and terminal P/Q at 2.5e-6 pu, with exact run/open flags. Staggered UV
timer replay is limited to 2e-9 seconds. Modified Euler uses the same limit
except for its explicit first-sample integration-order difference.

Coupled controls retain the existing channel-specific trajectory gates. UV
fraction is limited to 1e-7, contactor fraction to 5e-5, and contactor-open flags
must match exactly. All five staggered timer coordinates are checked at 2e-8
seconds maximum error. Both methods use the actual accepted/event-side samples;
complete raw and repolished trajectories remain available separately.

In the UV/contactor controls, the observed worst coupled contactor-fraction
errors are 1.34e-5 under modified Euler and 3.30e-6 under staggered integration.
UV fraction differs by at most 2.39e-8; open/closed flags match exactly. The
existing active restart controls also retain the strict staggered timer gate;
their worst restart-timer error is below 1.87e-9 seconds.

The shared-runtime tests also check state/derivative coverage, invalid buffers,
zero-time purity, one timer owner and allocation-free warmed compressor RHS
evaluation. These controls qualify the stated protection branches, not thermal
trip/reset behavior or the full composite model.

The [thermal implementation](acmtblu1-thermal.md) now qualifies the retained
instantaneous/reversed-threshold, complete-trip reset, irreversible shedding,
restart-pending and zero-threshold controls. Its explicit initialization and
method-specific criteria are separate from the UV/contactor gates above.
Restoration, parameter-validation and production-step/long-duration work remain.

The historical typed-load regression now checks the selected source load's
share of bus demand. Its post-fault voltage checkpoint is an internal
regression for that allocation; the independent both-method composite SMIB
captures remain the dynamic-accuracy gate.
