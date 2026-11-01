# Compressor algebraic thermal bypass

Status: the legacy compressor characteristic supports zero thermal time constant
through the shared numerical engine. Independent recorded-input and coupled
SMIB controls qualify the stated initialization, event, trip and restart
behavior under modified Euler and staggered integration. Composite attachment
uses the same leaf implementation; complete composite trajectories, restoration,
production-interval/long-duration coverage and remaining parameter-validation
rules are still open. Catalog status remains `LOADABLE`.

## Equations and numerical storage

For positive `Tth`, the existing differential temperature equation remains
`dT/dt = (Rstall * I_rms² - T) / Tth`. For `Tth = 0`, independent captures retain
zero as the prepared parameter and expose the algebraic temperature
`T = Rstall * I_rms²`. The implementation preserves that value instead of
introducing a small positive time constant or discarding the thermal model.

The exact standalone reader now accepts zero and still rejects negative or
nonfinite thermal times before attachment. Allocation, parameter order, source
load ownership and MVA-base conventions are unchanged.

`LD1PACImpl` retains its allocated temperature slots and named observations.
Their differential rates are zero in bypass mode, and applying an integration
vector projects them to the current algebraic value. Trial voltage/current
evaluation and RHS evaluation refresh the observation without integrating a
temperature, timer or thermal fraction. A fully thermally tripped group retains
zero representative heating current and temperature. The parallel physical
shunt remains outside motor heating.

Individual running/stalled power equations are shared between Norton-current
publication and bypass evaluation. Reusable primitive buffers perform the
current arithmetic; the bypass RHS creates no temporary power objects. The
warmed allocation test measures zero allocated bytes per RHS call. This is a
kernel-storage result; no complete-run speedup is claimed.

## Accepted thermal-action input

Algebraic temperature and the input to a discrete trip decision have distinct
evaluation boundaries. Using the freshly calculated right-endpoint temperature
for the action caused an early thermal-fraction change in the first
implementation.

The shared accepted-heating buffers now supply bypass thermal decisions:

1. Initialize the original operating-point temperature and capture network
   compensation before applying initial thermal shedding.
2. Apply the initial physical action, resolve the changed network and sample
   its settled heating input through the existing initial-endpoint contract.
3. Refresh instantaneous temperature at trial/network points without changing
   the accepted heating input or committing an irreversible thermal action.
4. At an accepted step, process run/stall/restart actions and evaluate thermal
   shedding using the separately accepted heating input. Refresh power and
   algebraic temperature afterward, then sample heating for the next interval.
5. A scheduled network event immediately refreshes temperature and resamples
   heating at that zero-time boundary. The corresponding thermal action waits
   for the accepted integration step.

This preserves the independently observed restart followed by a thermal trip
at the same accepted endpoint. The running flag can be one while the connected
thermal fraction is zero. Cooling cannot reconnect a tripped population.

`ThermalBypass` and `ThermalActionTemperatureA/B` expose this distinction through
read-only integration histories. Live-network current evaluation remains fresh;
the bypass decision retains its explicit accepted input independently of the
electrical measurement mode. Both integration methods use these same fields
and equations. No waveform shift or reference-state seeding is used in coupled
runs.

## Independent checks and criteria

Cold-repeat controls cover no initial shedding, partial initial shedding and
complete initial tripping at both integration intervals. Every original channel,
negative startup sample and event-side duplicate is retained. Recorded-input
replay feeds voltage/frequency, independently integrates timers and computes
temperature and thermal fractions through the production kernel. Temperature
coordinates are deliberately supplied incorrect finite values to verify that
the bypass projection owns their algebraic value.

Both replay methods use maximum-error limits of 2.5e-6 for temperature and P/Q,
and 1e-6 for thermal fractions. Full coupled runs retain exact source-grid
run/stall actions under both methods. Staggered limits are range-normalized RMSE
3e-6 for voltage, 4e-5 for P and 3e-6 for Q; absolute RMSE is limited to
0.0002 degrees for angle and 2e-7 pu for speed. Maximum temperature difference
is limited to 3e-5, thermal fractions to 2e-6 and timers to 2e-8 seconds.

Modified Euler retains the existing thermal-control boundary criteria at 0.5 ms:
range-normalized RMSE 1e-4 for voltage and 1e-3 for P/Q, absolute RMSE
0.01 degrees for angle and 5e-6 pu for speed. Those limits scale with the smaller
interval. Its temperature maximum limit is 1e-3 at 0.5 ms and scales with the
interval; thermal fractions are limited to 1e-5. Paired refinement must reduce
boundary RMSE with explicit observation-precision floors. Earlier control gates
are unchanged.

Algebraic temperature directly exposes individual-current input differences.
Each coupled report therefore retains their complete decomposition: both the
source `Rstall * I_rms²` equation error and the unexplained temperature-difference
residual must be below 5e-7. Numerical-method differences remain in the raw
reports. Import rejection, finite zero rates, zero-time purity, accepted-input
isolation, complete-trip projection, composite attachment and reusable storage
have separate checks.
