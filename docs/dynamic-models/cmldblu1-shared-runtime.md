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

Frozen pre-change and maintained modified-Euler executables produce identical
complete flat/fault CSV contents for the motor, mixed-motor and electronic fixtures, including
all recorded values and event-side samples. Generic/optimized equivalence is
separate from independent model accuracy.

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

For the motor fixture at 0.5 ms, the worst external-voltage NRMSE is about
0.001105 under staggered integration and 0.001104 under modified Euler. Rotor
RMSE is approximately 0.000461 and 0.000533 degrees, respectively. The initial
internal load voltage is about 0.00467 pu below the reference. Halving the step
does not remove that initialization discrepancy. Per-channel boundary maximum
errors, RMSE, range-normalized RMSE and mapped-state errors are retained in
private generated comparisons. The boundary caps detect regressions around
these explicitly incomplete results; they do not replace stricter acceptance
requirements or qualify every state.

## Remaining qualification

- Resolve distribution/tap/reactive-compensation initialization against the
  independently observed internal voltages, then tighten state and boundary
  gates. Do not change gains or initial states to fit the trajectories.
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
allocation. `Cmldblu1StateConformanceTest` verifies retained reference hashes,
channel-part integrity, clocks and boundary regressions while writing explicit
incomplete-state diagnostics. `MotorElectricalKernelTest` checks scalar
arithmetic. The integration tests are registered in `CorePluginTestSuite`.

Exact executable/source provenance, original and regenerated captures,
pre/post recordings, manifests and test reports remain under the existing
private verification-artifact directory.

Validation of this slice: 117 focused checks pass across the reactor; the core
module passes 458 checks with four existing skips; 15 three-phase motor checks
pass. The registered core suite executes 2,859 checks with zero errors, 13 skips
and the same four failures as the retained pre-change baseline. It therefore
does not establish a completely green system gate. Neutral-language validation
also passes. These results retain the incomplete model-qualification status above.
