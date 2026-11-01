# Compressor thermal protection and initialization actions

Status: shared thermal equations, initialization actions, independent replay and
coupled SMIB controls pass under both integration methods with the declared
method-specific criteria. Complete composite-load parity, restoration,
production-step/long-duration coverage and remaining parameter-validation rules
remain open. Catalog status remains `LOADABLE`.

## Shared numerical implementation

`LD1PACImpl` uses the same nine state fields, derivative buffers and protection
history under modified Euler and staggered integration. No wrapper model or
second set of equations is introduced. Repeated RHS/current requests do not
commit thermal fractions or advance a timer.

The representative motor temperature follows
`dT/dt = (Rstall * I_rms^2 - T) / Tth`. Thermal shedding can occur while the
run/stall flag is still running. For increasing thresholds, the remaining
fraction is one below `Th1t`, decreases linearly to zero at `Th2t`, and is
latched at its lowest accepted value. Cooling cannot reconnect thermally
tripped motors. If `Th2t` is zero or no greater than `Th1t`, reaching `Th1t`
causes an instantaneous complete trip.

Group B holds its thermal fraction while its restart timer is active and it
is waiting to restart. At an accepted restart it evaluates the current curve.
A completely thermally tripped group cannot restart; its allocated restart
timer can nevertheless continue integrating. The run flag, timer and physical
connection are distinct quantities and remain available in diagnostics.

A complete thermal trip resets the corresponding temperature and its accepted
integration history to zero. Its representative heating current and temperature
rate remain zero. State application preserves that projection, so an old
two-step derivative cannot resurrect the temperature. Voltage/frequency
measurement and timer equations continue. The physical compensating shunt
remains connected independently of the motor fractions.

These rules apply to the legacy characteristic used by native imports. Other
typed/zone policies retain their existing thermal convention.

## Initial physical action boundary

Immediately applying thermal shedding during operating-point compensation
caused the solver to replace the shed power with a fixed current. Static-load
allocation was correct; the compensation current was absorbing the physical
action.

The optional `InitialEndpointAction` contract separates these operations:

1. Initialize model states and capture the original operating-point residual.
2. Call `acceptInitialEndpoint()` to apply the physical action without advancing
   time. A changed injection requests a network solve.
3. Resolve that changed network without recomputing operating-point compensation.
4. Call `acceptInitialNetworkEndpoint()` to sample settled electrical inputs.

Both solver entrypoints use the contract. Thermal actions and resampling are
idempotent; the initialized temperature remains its physical initial value
unless complete tripping projects it to zero. The declared characteristic
selects participation, preserving custom typed-model validation. The
`InitialEndpointAccepted` history reports the boundary state. Low-level callers
that bypass the plugin solver must dispatch this lifecycle explicitly.

The native controls with initial shedding have nonflat startup history. The
comparison explicitly integrates the retained negative-time startup steps and
compares every startup/event-side row. It does not replace initial states with
reference samples or shift recorded trajectories.

## Numerical method and network input stage

The thermal staggered controls explicitly retain the accepted network endpoint
between steps through `setReuseNetworkEndpoint(true)`. A scheduled event still
requires a fresh initial solve. Adding an initial solve after an internal
thermal action changes when the generator observes its new input; it is a
finite-step coupling-order change, rather than a pure recording optimization.
Production defaults and existing comparison configurations are unchanged.

Independent generator-state captures retain every original reference channel
unchanged and repeat exactly. For the instantaneous-trip discriminator at
0.5 ms, the accepted-endpoint stage reduces rotor-angle RMSE from approximately
0.01031 to 0.000091 degrees and P range-normalized RMSE from 0.00133 to 0.0000109.
Both complete recordings remain retained.

Modified Euler keeps its own predictor/corrector and discontinuity convention.
It is not forced to reproduce the reference two-step temperature trace at each
finite timestep. Recorded-input tests independently verify its held-input Heun
update to 2e-15 before thermal projection, bound trip/run actions on the
physical grid, and compare every unmodified reference sample.

For the two-fault controls, its temperature comparison envelope is
`h / Tth * (initial forcing imbalance + total variation of Rstall * I_rms^2)`
plus the retained state-storage precision allowance. The linear thermal curve
transfers that envelope to its fraction through `1 / (Th2t - Th1t)` when the
thresholds are increasing. Instantaneous actions are checked separately.
This is a finite-step comparison bound for these controls, not a universal
error estimate for arbitrary histories.

At 0.5 ms the modified-Euler coupled limits are range-normalized RMSE 1e-4 for
voltage, 1e-3 for P/Q, absolute RMSE 0.01 degrees for rotor angle and 5e-6 pu
for speed. They scale linearly with the smaller timestep. Paired step refinement
must reduce boundary and thermal-state/fraction RMSE, with explicit observation
precision floors. Existing earlier-profile limits are unchanged.

Staggered replay retains maximum errors of 2.5e-6 for temperatures and individual
currents, 5e-5 for fractions and terminal P/Q, and 2e-8 seconds for all five
timers. Coupled staggered controls retain the existing strict state/boundary
gates and exact run/stall actions. All tests keep the physical shunt, every
channel and both sides of scheduled events. The secondary initial-network
recording identifies whether its historical `repolished.csv` filename contains
a fresh solve or a held accepted endpoint.

No whole-simulation speedup is claimed. Warm restoration, composite internal
coordinates/static-load behavior, additional initial-action interactions,
time-constant validation and production-interval/long-duration qualification
remain separate work.

The thermal captures use zero frequency sensitivity. They do not extend the
earlier frequency-input qualification to nonflat startup with active P/Q
frequency gains. Unmasked frequency-state differences remain in the coupled
reports; that combined initialization/measurement profile is still open.
