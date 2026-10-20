# OEL3C imported auxiliary runtime and physical-input routing

Follow-up: [PowerWorld input translation, repeatable artifacts and switch diagnosis](oel3c-powerworld-switching-2026-10-19.md)
adds the execution evidence missing at this snapshot and preserves the new
coupled counterexamples; complete trajectory acceptance remains open.

## Scope and acceptance

OEL3C now has an exact standard-record importer and a zero-injection auxiliary
runtime connected to the AC1C summation port. This is bounded runtime support,
not complete OEL3C trajectory acceptance. Other exciter hosts and takeover
connections remain unsupported and are explicitly rejected. Core GENROU is
reused; no new synchronous-machine equations or shared integration scheme are
introduced. No full Texas2k dynamic simulation is run.

The prior [equation foundation](oel3c-foundation-2026-10-19.md) remains immutable
evidence for the two-coordinate kernel. Its statement that the model was not
importable describes that earlier snapshot, not this runtime slice.

## Source contract

The exact native layout is two integer selectors followed by ten constants:
input selector, exponent, pickup, input scale, filter time, gain, integral time,
proportional gain, integral upper/lower limits, and output upper/lower limits.
No wrapper allocation or dialect alias is inferred. All twelve values are
required; malformed integer selectors and invalid constants are rejected.

The source documents and hashes are those frozen in the foundation evidence.
The [PowerWorld OEL3C diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Over%20Excitation%20Limiter%20OEL3C.htm)
identifies a summation-point limiter. The reference tool's generic low-value-gate
note is not treated as permission to fabricate a takeover initialization bias.
The tested takeover configuration fails the reference initial-condition check
at both steps and both cold repeats. These four rejected runs are retained;
none counts as an accepted trajectory or as evidence that every possible
takeover parameterization must fail.

## Implementation

- Input 1 uses generator field voltage, input 2 uses physical field current on
  the exciter base, input 3 uses the exciter feedback signal Vfe. The documented
  fallback integer selector uses field voltage. Missing/nonfinite physical
  field current fails initialization rather than being replaced with voltage.
- AC1C exposes an explicit initialization/measurement/connection port. The
  limiter initializes after the exciter states are constructed, before the AVR
  reference is derived. This avoids initializing from an uninitialized machine
  when the core initializes bus auxiliaries before generator controllers.
- Vfe is the AC1C field-feedback expression, including nonzero KE, KD and
  rectifier coupling KC. It is not an alias for generator field voltage.
- A zero filter time has algebraic feedthrough during network iterations;
  differential state advances only in the integration stages. A failed network
  solve restores its prior algebraic observation frame.
- Disabling the device mid-predictor rolls back the trial state and cached
  output. Inactive device/machine/bus output is neutral. Duplicate/manual port
  ownership, host replacement and changed connection are rejected.
- Named integration states remain distinct from published observations: the
  zero-time published input is algebraic, while unused lag storage is held.
- GNET and explicit generator-model removal skip the auxiliary through the
  normal audited importer dispositions. No supplemental `.dyd` input is used.

## Repeatable references and SMIB results

Seven successful physical-routing profiles, two timesteps (0.0005 and 0.00025 s)
and two cold repeats produce 28 clean runs, 14 exact raw CSV pairs and 42,140
raw rows. Exact readback covers both AC1C and OEL3C selectors, constants and
state/variable allocations. Four rejected takeover runs and the earlier pilots
are retained separately. An early pilot exposed an automatic AC1C E1 correction;
the final inputs specify E1=0.75 with both saturation values zero. This is a
declared disabled-saturation parameterization, not a fitted saturation curve.

Sixteen registered SMIB profiles cover all physical selectors, fallback, flat
and three-cycle faults, zero/positive filter time, and both timesteps. Flat bus
voltage drift is about 1.1e-16 pu. The field-current and Vfe fault profiles
activate the limiter; the field-voltage routing fault remains inactive at its
declared pickup and is not presented as an active-limiter discriminator.

The first diagnostic accidentally paired the reference's constant-power load
conversion with the default InterPSS load treatment. Its raw outputs and errors
are preserved. The matched-load diagnostic explicitly uses constant-power P/Q
outside the all-loads-in-admittance path, as in the existing GENROU benchmark.
No model parameters or initial states were fitted.

Matched-load regular-endpoint field-voltage error is about 0.00122 pu at the
coarse step and 0.00062 pu at the fine step; speed error is about 1.11e-6 and
5.49e-7 pu respectively. Bus-voltage maxima depend on event-adjacent sample
timing and do not yet demonstrate monotonic convergence. Proportional-switch
observations retain a separate discrepancy, despite much smaller integral
errors. These are diagnostics, not widened acceptance envelopes.

The comparison retains every raw row and reports initialization history,
event-side, regular endpoint and terminal hold partitions separately. Linear
interpolation uses the unmodified reference clock. One fine-step final sample
falls just beyond the InterPSS endpoint and is explicitly classified as a
terminal hold, not silently dropped or claimed as an exact endpoint. Exact
instantaneous event-post comparison remains open.

## Regression evidence

Final gates: 77 focused tests, 431 core tests and 2,037 registered scoped
SMIB/model/solver tests, zero failures/errors, with six existing core skips and
one existing opt-in diagnostic skip. All 155 offline checks pass. Source
reconciliation covers 1,899 Java/POM files and 139 unchanged earlier fixture
parts. All sixteen new SMIB exports repeat exactly between focused and broad
regression runs. All 28 reference runs pass the permanent quality checker.
These regression results do not promote the diagnostic fault comparisons to
accepted trajectories.

## Remaining work

- Resolve the proportional-switch observation/solver-stage discrepancy and
  exact instantaneous event-side observations before trajectory acceptance.
- Add permanent independent coupled-trajectory gates after the observation
  protocol and per-channel bounds are justified; current SMIB checks prove
  routing, finite bounded evolution and flat equilibrium, not cross-tool parity.
- Complete supported-host coverage and source-backed takeover semantics where
  applicable; do not substitute summation for a requested takeover connection.
- Add independently repeatable PowerWorld execution artifacts; its diagram is
  currently a source reference, not a new executed trajectory oracle here.
- Close remaining excitation-limiter source rows and model-family plan items.

The generated source inventory counts OEL3C as a fourth bounded runtime path,
not as a completely verified model. The broader development goal remains open.
