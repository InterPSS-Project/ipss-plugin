# CMLDBLU1 shared motor runtime

Status: both integration methods execute the composite's motor states, but
complete independent model qualification remains open. The catalog remains
`LOADABLE`; the boundary regression checks below are not full state acceptance.

## Ownership and numerical execution

The composite is an information-only container. Initialization creates its
distribution buses, motor devices and algebraic static/electronic components.
`StaggeredEulerSolver` must validate that expanded network and bind the actual
leaf devices. Previously it validated only the unexpanded network and omitted
the motors: a synthetic fault changed Motor A's E-prime by 0.19305 pu under
modified Euler and exactly zero under staggered integration. The shared path
now changes it by 0.19298 pu in the same experiment.

`InductionMotorImpl` and `LD1PACImpl` implement `StaggeredEulerParticipant` and
`IntegrationStateAccess` directly. `Cmldznu2Model.getIntegrationStateOwners()`
exposes the existing leaf owners for diagnostics. The parent has no duplicate
integration vector. Existing named-state keys are retained.

- A single-cage induction motor stores E-prime real/imaginary and slip. A
  double-cage motor also stores Ek real/imaginary; its Norton-source E-double-prime
  is an algebraic combination of E-prime and Ek.
- The compressor stores sensed voltage/frequency and two temperatures.
- Derivative evaluation refreshes algebraic current/torque and diagnostic rate
  buffers without advancing differential coordinates or committed timers.
- The ordinary method retains its established predictor/corrector ordering,
  including mechanical torque evaluation after the electrical update. Staggered
  integration evaluates the shared equations at one state/network endpoint.
  The two finite-step methods are therefore not asserted bit-identical.
- Accepted compressor measurement/temperature history and elapsed time commit
  once per interval. Motor protection remains in the engine's accepted
  `afterStep` callback; it is not also called by the shared completion hook.
- Zero-duration ordinary refreshes and repeated shared derivative evaluations
  do not commit state or protection history. Invalid trial buffers/nonfinite
  values fail before applying a partial state vector.
- Exact audited leaf types are required. Expanded unknown loads, duplicate
  ownership and later membership/cage-dimension changes require rejection or
  reinitialization. Load-specific structural guards are installed only when
  dynamic loads exist, avoiding new per-bus step guards for generator-only runs.

Electrical rates use reusable primitive buffers. A scalar complex-division
kernel preserves the established scaled arithmetic and passes bitwise checks
against the numerical library across both division branches. Compressor heating
uses the same magnitude arithmetic. A warmed RHS-only allocation check measures
zero bytes per compressor evaluation and 32 bytes per induction-motor evaluation
for the published immutable complex current. State application, network injection,
recording and diagnostic map reads are outside that allocation claim. No complete
simulation speedup is claimed from the allocation result.

The earlier shared-state refactor preserved the complete ordinary flat/fault
recordings byte-for-byte. The initialization corrections below change those
initial conditions and trajectories. Generic/optimized equivalence and
independent model accuracy have separate evidence.

## Legacy distribution initialization

The bus/load-specific legacy realization has its own initialization hook.
The zone-scoped model retains its existing initialization: independently
captured zone behavior differs in both reactive allocation and internal voltage.
Sharing motor equations does not establish identical distribution preparation.

The legacy hook computes the effective transformer ratio from the fixed-tap
ratio, transformer drop and compensated low-side voltage target. Discrete tap
moves are additive on the fixed-ratio grid. Positive moves use the upper bound;
negative moves use the lower bound. The retained discriminators exercise fixed
taps, line compensation, each movement direction and narrow voltage bands.

Initial component demand is allocated before the explicit capacitors are
stamped. The legacy induction-motor allocation uses
`Q/P = sqrt(1/LFm^2 - 1)`; this observed allocation convention is distinct from
the motor circuit's electrical reactive demand. Their difference becomes a
physical parallel admittance. The motor keeps its electrical Norton admittance
separate from the total stamped admittance and preserves both through warm
restoration. Selecting this initialization policy after preparing the motor
is rejected. The two-cage initial-power solve uses a tighter stopping criterion
only for this opt-in policy; the differential equations retain shared reusable
buffers under both integration methods.

Near-end compensation uses the raw load P/Q, allocated reactive-demand ratio
and low-side voltage. Far-end compensation balances the resulting feeder
endpoint. The internal load bus stores gross component Q, while its explicit
capacitor supplies the difference from net feeder Q. This prevents the engine
from adding an unintended residual static load. Tests require internal-node
current imbalance below `1e-9` pu without a fixed compensating current; the
nominal residual is about `1e-10` pu.

The [WECC composite-load specification](https://www.wecc.org/sites/default/files/documents/meeting/2024/WECC%20Comp%20Load%20Model%20Specification_final.pdf)
describes distribution compensation, the minimum feeder voltage and important
model-dialect differences. Exact legacy behavior is checked using independent
captures rather than assuming the newer zone model has the same preparation.

## Independent comparison

Synthetic motor, mixed-motor and electronic fixtures run for half a second at
0.5 ms and 0.25 ms under both methods. A fault is applied at 0.05 s and cleared
at 0.10 s. The mixed fixture activates all three induction-motor branches.
Reference channel parts retain all allocated states and diagnostics and prove
identical common channels/timestamps. Every new cold pair is identical after
explicit diagnostic-storage initialization. The original uncleared captures are
also retained: some unwritten slots contain allocator-dependent constants.
Where an uncleared counterpart exists, every calculated state and boundary
value is unchanged.
That observation does not qualify clearing storage for arbitrary disturbances.

The references retain startup rows and both event-side duplicates. Comparison
uses physical grid/event-side correspondence and independently reconstructs the
reference's accumulated binary32 clock; it does not shift trajectories or fit
parameters. Voltage NRMSE uses each full reference channel range. Rotor error is
in degrees. State diagnostics list source slots against the retained legacy
electrical-axis aliases; this is not acceptance of complete coordinate/frame
equivalence. Compressor frequency is reported as deviation from nominal.

The initialization matrix retains fifteen ordinary discriminators under both
methods, including loading-factor changes and near/far compensation. Internal
voltages, taps, allocated motor P/Q and feeder/compensation values have `2e-6`
absolute comparison gates. The former nominal internal-voltage error of about
0.00467 pu is removed. Motor electrical-coordinate and initial-Q-admittance
differences are retained separately; their exact internal parity remains open.

Across the complete half-second motor, mixed-motor and electronic comparisons
at both steps and under both methods, the worst external-voltage NRMSE is
0.00024045 and the worst rotor-angle RMSE is 0.00026382 degrees. Every voltage
channel must satisfy NRMSE below 0.001, and rotor RMSE must remain below 0.01
degrees. Per-channel peak caps additionally detect regressions. The largest
rotor peak difference is about 0.000404 degrees.

The electronic fixture's modified-Euler Q peak is 0.002830 pu immediately after
fault application at 0.5 ms, and 0.001439 pu at 0.25 ms. Its speed peak also
approximately halves. Staggered Q peaks remain below 0.000018 pu. The maintained
caps distinguish the methods and interval sizes. All samples, including event
sides, remain in the comparison. Full internal-state qualification is still
required.

An additional feeder-floor discriminator is explicitly diagnostic. The analytic
initializer reaches 0.95 pu; the source reaches approximately 0.950076 pu with a
slightly different prepared feeder impedance. Current balance and the physical
voltage floor pass, while exact source preparation and coupled parity remain
open. A zero-tap-step reference did not finish initialization after more than
180 CPU seconds; that failed attempt is retained and establishes no accepted
zero-step behavior.

## Remaining qualification

- Extend accepted distribution initialization to additional base/ownership
  cases. Resolve the feeder-floor endpoint difference and zero-step semantics,
  and independently qualify the remaining motor initialization coordinates.
- Implement/qualify the allocated motor angle-deviation observations and the
  separate restartable-compressor stall timer. Their source slots remain
  explicitly unmapped rather than filled with invented values.
- Qualify all remaining published diagnostics, zero-time-constant bypasses,
  active stall/thermal/UV/contactors, reclosure, full disconnection and load
  changes through the complete solver, not just isolated history callbacks.
- Resolve electronic recovery memory during trial network iterations, preserve
  exact legacy versus extended recovery behavior, and qualify LTC actions.
- Extend independent coupled qualification to production quarter-cycle steps,
  longer runs, base/ownership edge cases and extreme valid parameters.

## Maintained checks and retained evidence

`Cmldblu1SharedSmibTest` checks actual solver dispatch and complete flat/fault
recordings. `Cmldblu1SharedStateTest` covers pure evaluation, zero-time refresh,
buffers, histories, cage projection, warm serialization, ownership guards and
allocation. `Cmldblu1InitializationTest` checks the cold-paired distribution
matrix, physical current balance and warm reactive-matching restoration while
retaining explicit motor-state and feeder-floor discrepancies.
`Cmldblu1StateConformanceTest` verifies retained reference hashes,
channel-part integrity, clocks and boundary regressions while writing explicit
incomplete-state diagnostics. `MotorElectricalKernelTest` checks scalar
arithmetic. The integration tests are registered in `CorePluginTestSuite`.

Exact executable/source provenance, original and regenerated captures,
pre/post recordings, manifests and test reports remain under the existing
private verification-artifact directory.

Validation of this slice: 149 focused checks pass across the reactor; the core
module passes 458 checks with four existing skips; 15 three-phase motor checks
pass. The registered core suite executes 2,891 checks with zero errors, 13 skips
and the same four failures as the retained pre-change baseline. It therefore
does not establish a completely green system gate. Neutral-language validation
also passes. These results retain the incomplete model-qualification status above.
