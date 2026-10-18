# Shared dynamic-network iteration lifecycle

## Scope

Baseline: plugin `5122b620a`, core `12c231df5`. This slice adds the shared
positive-sequence solver lifecycle needed for algebraic converter-boundary
models. **It does not implement or verify REGCA1 high-voltage management.**
No production model opts in yet; all physical equations, DYR schemas, state
coordinates, iteration ceilings and convergence tolerances remain unchanged.

The WECC 2014 second-generation wind specification, Appendix A (PDF pages
34-35), separates network-iteration corrections from differential states and
resets working correction after a solved network. The consulted PDF SHA-256 is
`aef13405133f110351eeb341ffb4c674af5b498bd7881a936c03521e3584caea`.
Its diagrams were visually reviewed. Native reference simulator REGCA1 also exposes
previous voltage, previous angle and overvoltage correction as VARs, separately
from its three differential states. The installed Model Library SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.

Keep vendor behavior separate: [PowerWorld REGC_A](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGC_A.htm)
describes an algebraic VLim boundary and unused Khv/Qmin parameters. That is not
permission to reinterpret native Khv, Iolim or Accel, or to fit one finite gain
to a different simulator's trace. Exact converter semantics remain open.

## API and ownership

`com.interpss.dstab.device.NetworkIterationAware` is an additive Java interface,
not an EMF feature or serialized schema change. An attached, active device can
opt in. The default solver calls:

```text
beginNetworkSolution(immutable Context)
  beforeNetworkIteration(0) -> network equation solve
  beforeNetworkIteration(1) -> network equation solve -> convergence check
  ... until converged or failed ...
endNetworkSolution(success), in reverse begin order
```

Context records INITIAL/PREDICTOR/CORRECTOR (or DIRECT), step-start and physical
evaluation times, numerical budget and voltage tolerance. The callbacks do not
define a new integration scheme. Ordinary current/state/monitoring getters must
stay pure; model-specific working updates belong in explicit callbacks, not in
repeated reads. Completion must not replace the accepted boundary output with a
different injection merely because working correction is reset.

Participants are rediscovered for each solve, so trips or activation changes
between solves are honored. Bus devices, contributing-generator devices and
branch devices are included; identity deduplication prevents a generator from
being called twice through its bus-list alias. Inactive devices/parents and
branches with inactive endpoints are excluded. A device owns delegation to
unregistered composed controllers. Custom solvers replacing default network
iteration must implement this contract explicitly; this is not a claim about
all three-phase or external solver implementations.

## Independent native localization for the next model slice

Nine diagnostic native exports separately examine an explicitly forced Ip
STATE/STORE step at 0.05 s, Tg=1000 s, Volim=10 pu, and the previous-voltage/
angle VARs. These are **not physical faults or production parameter changes**.
One-/two-iteration profiles have two recognized non-convergence reports each
and are rejected as transient references, despite successful API/export status.

Those rejected iterates are nevertheless useful for inspecting working memory:
at Accel 0.4, 0.8 and 1.0, the two observed numerical updates satisfy
`Vprev_new = (1-Accel)*Vprev_old + Accel*Vterminal_old` within 9.61e-8 pu.
This discriminates magnitude-memory deceleration; it is not an inferred extra
differential state. Deliberately changing previous-angle VAR in the inactive-HV,
full-LVG profile leaves reported terminal/state channels unchanged. That limited
observation does not identify general angle behavior or active boundary logic.

The unforced-memory, 200-iteration state-step profile is repeated cold. Both
complete CSVs have SHA-256
`a3200733aca30f225adccc6b04d0011cded090574cd6eeaca7e9d1d44534e9f7`.
Both, and the separately forced-memory 200-iteration profile, pass the permanent
log/CSV quality gate: 205 samples, ten channels, two initialization-history
samples, one repeated-time sample, final native time 0.0502500571311 s.
All nine raw bundles, failures, quality logs, frozen script and audit are retained
privately under `2026-10-17-regca-iteration-memory`. The probe SHA-256 is
`4a6f00c4559e527ef0bfecfd7c44b2cdd99829c5c2dead195971db03d4ccf482`.
No InterPSS gain, limiter or stored model state was fitted to these exports.

A separate literal static per-iteration prototype does not reproduce the native
first forced-state output. In particular, native's same-time forced-state export
can report elevated terminal voltage with unchanged saved magnitude/correction.
That distinguishes a lifecycle/event-side question from merely selecting a gain.
The failed prototype is retained privately and is not wired into production.

### Native check of the formerly accepted synthetic five-plant recovery

The seven-bus, five-renewable fixture fits the installed small-case environment.
Its dynamic records are exported by reflection from the unchanged typed test
profiles, not hand-transcribed. The typed REPCA1 PUflag=0 extension is explicitly
omitted from the native record; all units use the common 100-MVA base. Native
generator buses with Qmin=Qmax=0 represent the fixed-Q load-flow operating point
(ordinary native PQ buses did not retain online generators). No dynamic gain,
limiter, collector impedance or event was changed. Grid GENCLS H=9,999,999 and
Xd'=1e-5 match the core infinite-machine fixture.

Both cold runs initialize successfully; every renewable starts at 1 MW/0 Mvar,
and all seven initial voltage magnitudes match InterPSS within 5.7104e-8 pu.
With h=0.5 ms and a 500-iteration budget, each native run nevertheless produces
**211 network non-convergence reports**, first at reported time 0.0525 s and
last at 0.1775 s. Both full exports are identical (1,005 samples, SHA-256
`6c0408b47088a27ab2d8157d1de12a244805c567ebe42e8a005c46f79447287a`).
The permanent quality gate correctly rejects both exports despite normal API
completion. Thus native recovery appearance does not rescue the former
unconverged InterPSS recovery snapshot. This independently corroborates a
problematic synthetic fault/domain, **not** exact native-versus-InterPSS timing,
every model equation, or thirty-plant behavior. No unconverged channels are
scored as accepted trajectories. The frozen native inputs/scripts, parameter and
equilibrium exports, initial-point checks, failed construction attempts, raw
logs/CSVs and quality rejections are retained privately under
`2026-10-17-native-five-plant-failure`.

## Failure contract

Every begun participant is ended once, including a participant whose begin
throws. Reverse-order cleanup also runs after budget exhaustion, failed network
equations, invalid voltages and callback exceptions. The original failure is
preserved and additional cleanup exceptions are suppressed rather than masking
it. Fatal errors propagate after cleanup. Recursive network solves from callbacks
are rejected and the guard is released even on failure.

An end callback can itself fail: that solve is not reported successful, and
remaining participants receive false. Completion is not transactional across
devices that have already ended. Neither cleanup nor the existing step-failure
latch rolls back bus voltages or differential states; reestablish the operating
point and initialize before retrying a failed partial step.

Last-solve telemetry distinguishes DEVICE_BEGIN_CALLBACK_FAILURE,
DEVICE_ITERATION_CALLBACK_FAILURE and DEVICE_END_CALLBACK_FAILURE. The voltage
change is explicitly NaN when a callback prevents meaningful measurement; this
must not be interpreted as a zero KCL residual. The previous componentwise
voltage convergence rule and the prior failure propagation remain intact.
There is not yet a separate participant convergence-veto or joint device-residual
criterion. Before a converter opts in, its working-correction convergence must
be checked explicitly; small voltage updates alone do not prove an active
accumulating boundary is solved. The callbacks are lifecycle infrastructure,
not a claim of a complete nonlinear converter-network solver.

## Completed evidence and remaining gates

Three clean failing-before assertions establish absent begin/iteration/end and
stage context on the old solver. Ten lifecycle tests now exercise callback order,
multiple reads, context times, failure/cleanup causes, duplicate registration,
activation, branches, zero budgets, invalid voltages, recursion and fatal errors.
Together with the eleven prior convergence tests, all 21 focused core tests pass.
The complete core build/local install passes **2,928 tests with no skips,
failures or errors**. Its local core 1.4.3 JAR SHA-256 is
`0f0395e5bfe2703ea4743f82523820eebbba370691388d7da4c115913293ce49`.
This is not a newly published artifact or version.

Two registered plugin guards wrap an imported REGCA1 generator in a no-op
lifecycle. Flat and three-cycle-fault runs preserve all 5,000 captured endpoint
values per profile exactly (three complex bus voltages, three converter states
and synchronous speed). Repeated output reads leave named states unchanged.
A predictor callback failure returns false without publishing an endpoint or
advancing time, invalidates the operating point and blocks retry.

The selected plugin/native/endpoint/integrity run passes all 34 tests without
skips. The full isolated reactor run completes successfully: **2,732 tests,
2,643 passed, 89 skipped, zero failures/errors**.

| Module | Tests | Passed | Skipped | Failures/errors |
|---|---:|---:|---:|---:|
| ipss.plugin.core | 129 | 125 | 4 | 0 |
| ipss.plugin.3phase | 468 | 440 | 28 | 0 |
| ipss.test.plugin.core | 2,135 | 2,078 | 57 | 0 |

All six-case flat/fault/trip smokes, six strict one-second flats and fifteen
topology faults execute. Maximum strict-flat voltage drift is 3.1284e-12 pu;
maximum speed drift is 5.6622e-14 pu. All fifteen complete endpoint-v2 fault
exports are byte-identical to the guarded `5122b620a` baseline at identical
inputs, step, end time and channels. This is non-regression evidence, not
independent physical acceptance of the severe fault responses.

The first shared-output attempt ended during discovery with a missing test-setup
class; the class subsequently reappeared, with no competing Maven process
visible. The next attempt completed the dynamics gates but ended with seven
missing-class errors (IEEE39 anonymous class and embedded-data Case78), not
numerical assertion failures. The missing embedded class was present again on
inspection. Both infrastructure failures are retained separately, not classified
as model failures or silently counted as passing. Concurrent solver-visibility
and reference-runner edits are preserved and excluded from this slice's commit.
Additional concurrent core commits appeared after the tested lifecycle build.
They are not covered by its 2,928-test result. The three lifecycle sources and
exact tested JAR are preserved separately; the installed fingerprint was checked
again when accepting the plugin run. This report does not certify newer core
master or the later renewable/parser changes in the shared plugin checkout.

The opt-in `isolated-verification` Maven profile therefore gives every module a
separate build directory under `.verification-build/<run-id>/<artifact-id>`.
It changes only generated-output locations, not test discovery, source/resource
inputs, working directories, dependencies, integration methods or tolerances.
The ordinary build remains unchanged when the profile is inactive. Distinct
run IDs also isolate simultaneous verification builds from each other; test
programs' explicitly written case-output paths remain their existing paths.
The completed isolated run used:

```text
mvn -q test -Pextended-tests,isolated-verification
  -Dverification.run.id=network-iteration-20261017 --fail-at-end
  -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest
  -Dsurefire.failIfNoSpecifiedTests=false
```

The inactive and active build configurations were inspected separately; the
isolated profile does not suppress the failed tests. The formerly missing-class
GENSAL, embedded-data and IEEE39 benchmark tests pass in the isolated run.

The successful log SHA-256 is
`c5233e4b094bce9f0676dfe2f46c2065ec9fcf839f7ac1e0cd34c320aad645b7`.
The completed private bundle under
`2026-10-17-network-iteration-lifecycle/completed-regression` retains 688 hashed
artifacts: fresh reports, exact tested sources/JAR, enabled configuration,
complete fault trajectories, log and validation manifest. A subsequent read-only
audit verifies every artifact hash and all module report totals. Private opt-ins
are restored to false after archiving; enabled settings remain in the bundle.

### Commit and integration boundary

Core commit `95051f797` is on
`codex/network-iteration-lifecycle-20261017`, directly based on tested parent
`12c231df5`. Its three source files match the frozen tested sources exactly after
line-ending normalization. The separate checkout is
`tmp/core-network-iteration-validation` in this plugin workspace. Core master
has meanwhile advanced to `124bca40f`; it is deliberately not reset or rewritten.
The matching uncommitted lifecycle copies in that original checkout are left
untouched to avoid disturbing concurrent work. Integrating this commit with
newer core master requires a fresh combined core/plugin regression; neither the
old binary fingerprint nor the archived green run proves that combination.

The plugin slice includes the two lifecycle integration tests, suite registration,
opt-in output isolation and this documentation. Concurrent controller/parser
changes, solver-visibility edits, reference runner and unrelated JAX test are
excluded. No simulator checkpoint, physical parameter or tolerance is changed.

Still required: integration with newer core and its combined regression, exact native HV/Accel/Iolim and
low-voltage/angle discrimination, model-specific boundary implementation and
tests, repeated independent trajectory artifacts and complete Texas2k physical
acceptance. Infrastructure availability is not model verification.
