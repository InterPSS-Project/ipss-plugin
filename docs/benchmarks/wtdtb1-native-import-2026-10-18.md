# WTDTB1 / WTDTBU1 native implementation and bounded verification

Status: **LOADABLE with partial independent verification**, not VERIFIED.
Approved native renewable coverage advances to **18 loadable families and nine
missing implementations**. This is not a claim that the entire May 2026 list,
all native versions or all Texas2k cases are fully verified.

## Implemented path

The native PSS/E reader accepts exactly:

- `IBUS 'WTDTB1' ID H DAMP Htfrac Freq1 Dshaft Tp /`.
- `IBUS 'USRMDL' ID 'WTDTBU1' 103 0 0 6 5 3 H DAMP Htfrac Freq1 Dshaft Tp /`.

The six metadata integers in the legacy record are validated, not discarded.
Unproven flat WTDTBU1 forms, alternate allocations, wrong wrappers, missing or
extra values and PowerWorld's eight-parameter WTGT_B form are rejected.
The native standard and legacy forms produce identical complete native CSVs
for the independently exercised profile. The current native convention is
explicitly scoped to **PSS/E 36.7**, not silently asserted for every library version.

Attachment requires the supported REGCA1/REECA1 host. The drivetrain record may
precede that host in the DYR file. Missing targets, incompatible hosts and
duplicate/mixed drive trains cannot silently replace an existing model. GNET
and whole-stack model removal retain their intentional-skip dispositions.
No DYD input or supplemental cross-tool reader was added.

Five native-coordinate states are exposed through an immutable map, also
available as `WTDTB1.*` entries on the converter's named-state interface.
The common drivetrain boundary preserves WTDTA1's API and existing equations.

## Equations and initialization: conventions are separate

The default typed kernel retains the published diagram. The native builder
selects `Wtdtb1Model.forNativePsse367`, whose observed callback differences are
explicit instead of changing the published reference implementation:

- Two-mass generator shaft/damping feedback has the independently discriminated
  `wg/wt` factor. Separate turbine- and generator-speed perturbations distinguish
  it from the published torque-balance equation.
- Native one-mass mechanical torque divides by the retained turbine coordinate,
  not by the changing generator speed. A separate native speed perturbation
  distinguishes this branch.
- Native machine damping uses deviation from nominal speed; the published
  WTGT_B diagram uses deviation from initialized W0. Native non-unit equilibrium
  is initialized backwards: `Tshaft0 = Pe0/w0 + DAMP*(w0-1)` and
  `Pm0 = w0*Tshaft0`. The aerodynamic model receives this balanced mechanical
  input, not an unadjusted electrical-power value.
- Rotor-angle deviation integrates speed relative to the initialized speed.
  The earlier non-unit constant angle drift is fixed and independently checked
  against PowerWorld at W0=.97.
- In one-mass mode, inactive published coordinates retain their initial values;
  they are not overwritten with a fabricated moving state. The effective
  one-mass turbine-speed signal follows the generator speed.

Both complete and staged modified Euler remain available. The opt-in two-step
path remains separate; native storage precision is not imposed on the default
solver. Reinitialization clears predictor/history data. Tp=0 is an algebraic
electrical-power refresh, and an aerodynamic model deactivates the fifth state.
Freq1 is Hz for this six-CON contract; it is not passed through the older
WTDTA1 exchange-frequency convention.

Native source: PSS/E 36.7 Model Library, physical pages 897-898, SHA-256
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
Published comparison: [PowerWorld WTGT_B description and diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20WTGT_B.htm).
Its separate Ht/Hg, stiffness, MVA-base and W0 inputs are not native DYR fields.

## Reference quality and rejected probes

The accepted native matrix has seven scenarios at both .5 and .25 ms, each
cold-repeated: two-mass, one-mass, aerodynamic, two-/one-mass Tp=0, and
non-unit aerodynamic two-/one-mass. All 28 runs pass the native initialization
check; each pair has byte-identical complete CSV output. The audit checks every
manifest input/output hash, actual CON values against declared float values,
five STATE/three VAR/six CON allocations, finite samples and event-side clocks.
The total is **84,154 raw samples** including prehistory and duplicate events.
Native DSMDL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.

Earlier non-unit probes reused PFLAG=1 while WTTQA1 already supplied the
speed-converted reference. PSS/E reported a suspect REECA1 initialization,
including a -1.44 power-order derivative. These runs and the rejected generated
checkpoint source are retained as failed diagnostics. They were removed from
the accepted reference matrix, not blessed as trajectory evidence. The valid
non-unit cases explicitly use compatible PFLAG=0 and pass native initialization.
Other invalid allocation/DSTATE-query diagnostics remain rejected as well.

The public typed checkpoints retain every nonnegative input endpoint, both
event sides, five-state/network checkpoints every 5 ms, the first two endpoints
after each event, and the fine reference's extra terminal tick. Input integration
uses the declared native step; original drifting float serialization clocks
remain in the checkpoints and complete raw archive. No initial state or gain is
fitted to a trace. Fixed body and bundle hashes detect accidental mutation.

## Accepted and diagnostic comparisons

Ten unit-speed native profiles compare all five states at both steps. The four
non-unit profiles compare the speed/filter coordinates and independently check
shaft/rotor kinematics. **Their absolute integrated-coordinate precision is
still diagnostic, not accepted as exact trajectory parity.** Their angle error
is explained by the measured speed-error integral to within 5e-7 rad at scored
checkpoints, without widening an absolute angle tolerance.

Native float-state stalling is measurable: the valid one-mass non-unit profiles
have 1,226 coarse and 2,634 fine event-free intervals with an unchanged speed
STATE despite a nonzero double-precision RHS whose increment is below half a
native float ULP. This identifies a storage-resolution issue; it is not a proof
of complete STATE/STORE recurrence or permission to fit model coefficients.
Some velocity/integrated-coordinate errors do not decrease under step halving.
Default double-precision modified Euler is intentionally retained.

PowerWorld W0=.97 was run twice cold at each step with unchanged exported
parameters and automatic correction disabled. Permanent playback checks all
five states. Maximum scored errors are approximately 1.16e-7/5.55e-8 rad for
shaft angle, 6.8e-8/3.11e-8 rad for rotor angle, and below 6e-8 pu for speed
(coarse/fine). The previous W0=1 references and bounds remain unchanged.
PowerWorld executable SHA-256:
`06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c`.

## Full-solver SMIB coverage

At .5 and .25 ms, the public small-network case passes flat-run equilibrium,
a three-phase Bus 2 fault at .05 s cleared at .10 s, and generator-trip state
holding. The parent generator and converter are inactive after the trip.

The coupled native fault comparison scores all five continuous drivetrain
states, including both event sides, and three bus voltages plus P/Q at the
represented network endpoints. All five coupled state errors decrease under
step halving. Representative maximum differences:

- Generator-speed deviation: 1.439e-6 / 7.194e-7 pu.
- Shaft angle: 4.248e-5 / 2.162e-5 rad.
- Rotor-angle deviation: 2.591e-5 / 1.383e-5 rad.
- Bus 1/2/3 voltage: approximately 4.8e-5 / 1.8e-5 / 3.2e-6 pu at both steps.

The voltage residual is not claimed to converge with the mechanical states.
InterPSS's regular output has no second instantaneous algebraic sample at each
event. The two native instantaneous network-post sides are retained and their
errors reported, but **are not accepted**. This is not full network parity.
The fine run includes the native extra terminal tick rather than discarding it.

The generic core validator logs "No machine defined" before honoring its
allow-generator-without-machine setting. This is an inspected legacy message
for the converter bus, not a missing REGCA1 dynamic device: attachment,
initialization, active/offline status and solver steps are asserted explicitly.

## Final regression and reproducible evidence

The isolated snapshot is committed plugin `54429b9c7` plus this scoped slice,
using core 1.4.3 JAR SHA-256
`f7380328ff7b4d81686a5aae2c172d964013ae74deb81c964050a6a74db0d4ad`.
Unrelated uncommitted REECA1/REGCA1 two-step and WECC240/JAX changes are not
included or claimed tested by this slice.

- Plugin-core units: **160 tests, zero failures/errors, six optional skips**.
- Registered SMIB/model suite plus catalog and staggered-solver regressions:
  **1,217 tests, zero failures/errors, one existing diagnostic skip**.

Commands (from the isolated snapshot root):

```text
mvn -q -pl ipss.plugin.core -am test -Pextended-tests,isolated-verification -Dverification.run.id=wtdtb1-native-final-units-20261018-l --fail-at-end
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=wtdtb1-native-final-smib-20261018-m -Dtest=DynamicModelSmibTestSuite,DynamicModelCatalogTest,PsseStaggeredEulerSolverTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
```

Both commands exit zero. Counts come from fresh Surefire XML, not test-name
selection alone. The preceding unit run's configuration-policy failure is
retained as rejected gate evidence: the new SMIB test read a JVM-global output
property. It now writes six deterministic module-local
`target/wtdtb1-smib/{flat,fault,trip}-{step}.csv` artifacts without global settings.
No equation, physical parameter or trajectory tolerance changed for that fix.

The local ignored archive
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-wtdtb1-native-import`
retains raw references, accepted/rejected probe identities, runnable scripts,
inputs, source hashes, fresh XML/logs and complete InterPSS SMIB exports. Public
typed checkpoints have fixed body digests and native bundle SHA-256
`69d8920a617600b4f397bef59f98add26e24dc4087958a327f63765673fd9ace`.

## Remaining acceptance work

- Close native non-unit absolute integrated-coordinate and storage-precision
  differences without fitting gains or changing the default numerical method.
- Export and independently compare instantaneous network-post event outputs.
- Extend PowerWorld branch/base coverage beyond the recorded profiles.
- Add independently referenced reconnect/restart behavior and other compatible
  host combinations where the authoritative contract requires them.

The evidence inventory deliberately labels this model NATIVE_PARTIAL_PROFILE.
Nine approved native renewable implementations are still missing; this slice
does not close the overall coverage goal.
