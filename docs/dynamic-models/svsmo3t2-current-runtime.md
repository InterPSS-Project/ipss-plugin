# SVSMO3T2 current-controller runtime

The implementation is **partial**. The base coupled SMIB fault passes for both
modified Euler and staggered Euler. Coordinated shunt switching passes its full
coupled matrix with staggered integration and an explicitly selected binary32
endpoint clock. Modified Euler retains its single-operation coupled gate.
Broader limiter combinations, production steps and long-duration qualification
remain open.

## Ownership and execution

The exact record has a quoted FACTS name followed by the model name, twenty
ICON fields and forty-four CON fields. It has no bus-number or generator-ID
prefix. Import preserves the name, resolves a unique static shunt FACTS owner
and binds its local/remote buses before simulation. A named record cannot be
mistaken for a generator at `Bus0`. Missing, ambiguous and duplicate owners are
rejected. Enabled MSS slots resolve individually named fixed shunts, including
their nominal off-state conductance. Missing targets, duplicate slots and
unavailable nominal-admittance metadata fail before controller attachment.
An enabled selector with no assigned banks is rejected: independent negative
captures show that this configuration causes the entire source model to be
ignored even though the startup API reports success. The capture probe checks
that diagnostic instead of treating uninitialized channel storage as evidence.

`Svsmo3t2Model` evaluates Java equations against immutable typed data and fixed
bus/device bindings. Six-state predictor/corrector and derivative buffers are
reused. The warmed allocation check measures zero bytes across its complete
predictor/corrector loop. This is a kernel allocation result, not an end-to-end
runtime benchmark. The controller reads voltage; it does not require a passive
bus-frequency measurement. Unknown observers retain conservative measurement
selection.

Ordinary integration requires successful initialization and a matching positive
predictor/corrector interval. Duplicate predictors, orphan correctors, interval
changes and zero-duration projections during a pending predictor are rejected
without changing coordinates or accepted history. Nonfinite projected trials
restore the prior coordinates from a reusable buffer; a failed corrector can
be retried after its external input is repaired. Shared-state projection uses
the same rollback protection. These guards do not change valid stage equations
or qualify the remaining nonlinear and repeated-switching branches.

The terminal current uses the explicit STATCOM MVA base. The solved static
admittance remains in the network matrix; the device supplies only the current
correction that replaces this contribution with its controlled current.
Voltage changes refresh current direction and the static-current correction.
They do not turn a limited current source into a fixed susceptance. This follows
the distinction in the [WECC static-var specification](https://www.powerworld.com/WebHelp/Content/Other_Documents/WECC-Static-Var-System-Modeling-Aug-2011.pdf).

Named states expose published coordinates. In particular, the voltage sensor
exports `(1 - Tc1/Tb1)` times its low-pass storage, while the transient-gain
block exports its low-pass storage directly. `IntegrationStateAccess` separately
exposes numerical storage and the states/rates of the last derivative evaluation.
Its optional `getIntegrationHistories()` method returns an immutable detached
snapshot of accepted current, timers, rating availability and limiter/trip flags.
An empty default map means that histories are not exposed. Reading diagnostics
does not evaluate inputs, commit a step or alter the equations; these maps are
constructed only when requested, outside the numerical loop.

Zero-duration event calls can project an active PI constraint. They do not
integrate derivatives or advance timers. Current limiting uses instantaneous
terminal voltage, separately from the filtered regulation signal. Overvoltage recovery
duration and exhausted definite-time rating are committed histories. External
blocking is latched at acceptance, so a zero-time fault network solve retains
the previously accepted current. The internal output-lag coordinate remains
the droop and slow-reset feedback even while external current is blocked.
The definite-time rating timer samples the previous accepted physical output;
trial states cannot consume an additional interval before threshold pickup.
Both solver policies project this bus device's algebraic limits after the
settled event solution, separately from numerical integration and timer commit.
The overvoltage branch compares the voltage-dependent full short-term
capability against the currently available rating before reducing that rating.
It is distinct from either always scaling the available rating or simply
clipping the capability envelope. Independent captures with different voltage
spans and short-term multipliers distinguish these equations. No fitted
voltage threshold or added smoothing is used.
An overvoltage trip first latches the command and removes available current
rating. The PI command is projected to zero, while residual current decays through
the output lag. The next scheduled electrical-network event detaches the device,
sets physical current to zero and freezes the controller's continuous states.
The prepared solver device list dispatches this event without scanning all buses
each step. `acceptNetworkEvent()` also provides an explicit boundary for callers;
it rejects an unfinished predictor and is idempotent after removal.
Reinitialization resets trip/removal flags and timers. Automatic notification
from arbitrary external matrix edits remains unqualified. MSS bank stamps use
the accepted boundary described below.

Independent discriminators also establish source initialization selections:
positive slow-reset gains suppress nonlinear droop even when the separate
slow-reset enable flag is off; the thermal rating branch requires a zero
definite-time duration. Entered parameters stay immutable, and the prepared
selections are exposed. These source-specific selections must not be inferred
from the generic block diagram alone.

## Shared integration contract

The model directly implements `StaggeredEulerParticipant`. Both solvers use the
same six numerical coordinates, derivative equations and limiter projection.
`readStaggeredState` and `evaluateStaggered` do not advance states or timers;
`applyStaggeredState` projects a trial; `completeStaggeredStep` commits the
continuous-step histories. MSS actions remain in the separate accepted-endpoint
callback. The staggered solver owns reusable state/rate/history/trial arrays.
The warmed read/evaluate/apply regression observed 88 allocated bytes across
100,000 groups; it does not measure complete simulation time.

This numerical selection is explicit:

```java
var algorithm = context.getDynSimuAlgorithm();
var solver = new StaggeredEulerSolver(algorithm);
solver.setEndpointTimePrecision(StaggeredEulerSolver.EndpointTimePrecision.SINGLE);
algorithm.setSolver(solver);
algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
```

The enum passed to the derivative API remains `MODIFIED_EULER`; the selected
solver applies the two-history staggered recurrence. Imported cases continue
to select the ordinary predictor/corrector solver by default.

Endpoint-time precision defaults to `DOUBLE`. `SINGLE` accumulates the
participant callback clock with binary32 addition. Every captured nonnegative
timestamp matches that arithmetic exactly. State integration weights and the
algorithm's nominal clock remain unchanged; traces record reference, nominal
and participant endpoint times separately. No output time shift is applied.
The new two-argument completion callback delegates to the existing one-argument
callback for older participants, preserving their duration-based histories.

Configure the precision before initialization. Use `notifyDiscontinuity()` after
an external reference jump; registered network events are detected automatically.
Initially inactive bus models are omitted until initialized. Reactivating them,
or changing their attachments, requires coherent network/model reinitialization:
invalidate `network.setDStabNetInitialized(false)` before calling initialization.
Stable output identity is available even while a model is initially inactive.
Other unqualified dynamic bus-device families remain rejected by the staggered
solver. See [solver selection](../dynamic-solver-selection.md).

## Mechanically switched shunts

`Svsmo3t2MssController` keeps prepared numerical slots, statuses and timer
storage. The endpoint loop allocates no action objects or temporary slot lists;
its warmed allocation regression bounds incidental runtime allocation. Named
switching histories are available separately from the six continuous states.
Reading diagnostics never advances history.

Current above the upper threshold requests capacitor insertion or reactor
removal; current below the lower threshold requests the reverse. Equality is
inside the band. Source slot order determines which eligible device operates.
This differs from plant-controller families with a removal-first preference,
so their switching equations are not aliased here.

A common breaker command is latched after the demand delay. Returning inside
the band does not cancel an issued command. At completion the controller selects
the first ready slot, skipping capacitors still discharging. If none is ready,
the attempt finishes without switching; the next attempt requires a fresh demand
delay. Capacitor operations and reactor removal also reset the demand delay.
The exact source reactor-insertion branch retains lower pickup: another ready
reactor can operate at the next accepted endpoint under persistent low-current
demand. A paired-reactor discriminator establishes this distinction. No-candidate
or in-band evaluation clears the retained pickup. Capacitor discharge time does
not apply to reactors.

`Svsmo3t2MssBoundary` applies exactly one individual G+jB change, preserves other
matrix stamps and updates status/sequence caches so a network rebuild retains
the new bank state. This is a physical switched-shunt stamp, distinct from the
STATCOM current correction. The boundary binds devices once during import and
uses the accepted endpoint only. Predictors, algebraic event calls and duplicate
endpoint calls cannot dispatch another operation. The origin callback runs after
initial operating-point compensation, preventing a zero-delay bank insertion
from being absorbed into compensation current.

## Evidence and remaining work

The synthetic impedance-load SMIB uses identical machine/network parameters in
both engines. Both integration fixtures explicitly use the same 0.000001 pu
network voltage-correction tolerance; production defaults are unchanged.
Diagnostic selection uses the classpath fixture
`test-config/svsmo3t2.json`, independently of JVM-wide settings. Null matrix paths
select the hash-pinned bundled references, including the recorded-input replay.
Explicit fixture paths can select retained cold captures, and the limiter
diagnostic's optional profile key narrows that diagnostic only; acceptance tests
continue to run their full declared matrix.
Its complete base fault comparison retains startup samples and
both sides of fault application/clearing. It gates every bus voltage at
range-normalized RMSE below 0.001, rotor-angle RMSE below 0.01 degrees, prefault
angle error below 0.002 degrees, and all six state errors individually. The
observed base voltage RMSE is below 0.0001 pu and rotor-angle RMSE is approximately
0.000398 degrees. The compressed reference is hash-pinned.
With staggered integration, the same fault has worst voltage RMSE approximately
0.00000089 pu, maximum voltage error below 0.0000061 pu and rotor-angle RMSE
approximately 0.000455 degrees. Its six state gates are tightened separately.

Cold captures retain every raw channel. Disabled MSS timer storage is not
initialized consistently by the independent implementation. Its declared
canonical hash omits only those inactive cells; the original CSV remains intact.
No sample, event duplicate or defined signal is removed.

The synthetic MSS archive is self-contained and hash-pinned by
`Svsmo3t2MssReplayTest`. Paired cold captures prove repeatability of all defined
channels and every bank-status observation. Recorded-current replay matches all
observed statuses exactly across capacitor/reactor operations, discharge retries,
slot-order permutations, a latched breaker, zero delays, remote banks and
nonzero conductance. Physical tests separately exercise existing fault stamps,
matrix rebuilds, missing metadata and accepted-stage dispatch.
Demand, breaker and applicable capacitor-discharge histories agree within source
single-precision rounding; the largest observed timer error is below 0.000000029
seconds. The reactor-pair supplement retains both cold captures privately and
adds a separately hash-pinned public reference without replacing the base archive.

The single-operation coupled gate preserves every raw row, including distinct
RUN-end and next RUN-start observations at the same timestamp. It compares each
to its corresponding accepted or refreshed algebraic endpoint. Reference changes
use a zero-duration limiter projection without advancing timers. Every bus
passes range-normalized voltage RMSE below 0.001; rotor-angle RMSE is below
0.01 degrees and prefault maximum error below 0.002 degrees. Each continuous
state has its own maximum and RMS error gate. The worst observed voltage NRMSE
is approximately 0.000624 and rotor-angle RMSE is below 0.000158 degrees.

The full coupled MSS matrix now passes with staggered integration and the explicit
`SINGLE` endpoint clock at a 0.0005-second network step. This includes repeated
capacitor insertion/removal, discharge retries, cooling-slot transfer and paired
reactor actions. Every observed bank status matches. Worst all-bus voltage NRMSE
is approximately 0.000481, maximum voltage error is below 0.000399 pu and worst
rotor-angle RMSE is below 0.000544 degrees. All six states and physical current
have individual maximum/RMS gates. The reference archives remain unchanged.

Method and clock remain separate numerical policies. Ordinary modified Euler
and double-clock staggered repeated-operation comparisons remain diagnostic.
The double-clock staggered discrepancies occur in the same profiles as the
clock-only replay discriminator; the explicit binary32 clock resolves them.
Staggered integration separately resolves the paired-reactor insertion stage
discrepancy. These accuracy results do not establish a performance improvement
or change the ordinary solver's defaults.

`Svsmo3t2LimiterSmibTest` retains a separately hash-pinned synthetic limiter
archive, including paired cold captures that activate thermal rating and
combine slow reset with blocking. Its registered blocking test compares every
bus voltage and angle, rotor angle, physical current/reactive power, voltage
reference and all six states under both methods. Staggered blocking voltage
error is below 0.000008 pu and current error below 0.00004 pu; modified-Euler
voltage error is below 0.0031 pu and current error below 0.015 pu. Both pass
range-normalized voltage RMSE below 0.001. These bounds preserve the different
finite-step integration errors.

The rating-onset gate checks current through the first rating-expiration
transition. A separate complete-profile gate now covers definite and thermal
ratings, inactive/active overvoltage limiting, changed voltage spans and rating
multipliers, and a halved thermal step. Both methods compare every original
sample, bus voltage/angle, rotor angle, physical current/reactive power and all
six states. Every bus passes range-normalized voltage RMSE below 0.001;
reactive-power range NRMSE is below 0.003 and rotor-angle RMSE below 0.0016
degrees. Each state has separate maximum/RMS bounds. Thermal profiles must
actually cross the integral threshold, and every profile must reduce its
available current rating.

For the original definite-rating profile, the corrected staggered current
maximum error is below 0.000032 pu and voltage error below 0.000007 pu.
The coarser thermal profile retains a 0.0151 pu current maximum error and
0.0031 pu voltage maximum error within its explicit numerical envelope.
At half that step, the current maximum error is below 0.000017 pu and voltage
error below 0.0000034 pu. This branch-sensitive finite-step comparison is
reported separately; it does not establish production-step accuracy.
The published Mvar diagnostic also remains a separate comparison because its
event-side cached value can differ from the contemporaneous current/voltage
product. The physical reactive-power gate uses the independent per-unit output.
No event row or cached diagnostic is discarded.

The separately hash-pinned trip supplement compares complete mild overvoltage
trip, half-step trip, no-trip control and overlapping-fault discriminator runs
under both integration methods. Every trip-command and network-removal flag
matches both sides of every reference event. All bus voltages pass range NRMSE
below 0.001. Across these profiles, modified Euler has maximum voltage error
below 0.000230 pu and rotor-angle RMSE below 0.000350 degrees; staggered Euler
has maximum voltage error below 0.000066 pu and rotor-angle RMSE below
0.000095 degrees. Physical current and reactive power have separate maximum
and RMS gates. All six continuous states are gated throughout the independently
identified network-connected phase, including the pending trip interval.

Independent device-mode observations and reconstructed terminal KCL establish
the distinction between the trip command and subsequent electrical removal.
After removal, the source's inactive allocated-state storage can continue
changing with stale rates, while some algebraic diagnostics hold their last
values. Those samples remain in the full trace and all-row error metrics;
the runtime freezes its detached owner instead of reproducing unused storage
drift. The state gate uses the reference rating transition and the next known
network event to define its active phase, independently of implementation flags.
The more severe capacitive-fault trip remains diagnostic: its ordinary
modified-Euler rotor-angle comparison does not meet the base accuracy target.

The overlapping-fault discriminator also requires core event processing to
restore every still-active bus/branch fault after a matrix rebuild. Core commit
`b3ff6e402` supplies that correction without replaying unrelated one-shot actions;
its focused tests reproduce the original lost stamp and its complete isolated
core suite passes. The plugin comparisons use that exact core artifact.

The broader extended coupled and recorded-input matrices retain diagnostic
branches beyond these accepted profiles. They exercise
lead-lag coordinates, reference changes, slow reset, nonlinear droop, definite
and thermal ratings, deadband, blocking, trips and bypasses. Execution alone is
not acceptance. The blocked slow-reset discriminator supports the internal
current feedback but retains a deadband/output-coordinate residual; no gain or
threshold was fitted to remove it. Remaining work includes:

- Extend the qualified coupled MSS matrix to production steps and longer
  durations while retaining its declared integration/clock policy and the
  ordinary modified-Euler comparison.
- Complete repeated-operation modified-Euler acceptance/convergence checks;
  the selected staggered result does not qualify every integration policy.
- Finish coupled and internal-state qualification of every selector/limiter
  branch, including current reversal and zero-time-constant paths.
- Extend the rating/voltage-limit gates to current reversal, equal voltage
  thresholds, non-unit bases and production integration intervals; retain the
  explicit coarse thermal error envelope without fitting or shifting traces.
- Verify short blocking intervals, external/MSS-triggered trip removal and
  complete coupled reinitialization/reconnection histories; retain the accepted
  scheduled-event trip/control matrix and read-only history contract.
- Qualify non-unit STATCOM/network MVA-base ratios and remote regulation during
  coordinated bank actions.
- Extend validation and coupled tests to production steps and longer runs,
  then measure complete warmed recording/export time.

Private evidence retains exact source/tool provenance, executed probes, original
captures and diagnostic outputs. The public implementation tracker contains only
model-specific work and priorities.

## Modified-Euler switching discrimination

A test-only coupled clock cut retains the ordinary modified-Euler equations and
changes only the accepted endpoint clock presented to bank switching. It retains
all reference rows and event-side duplicates. It reduces differing bank-status
rows from nine to four: discharge, cooling-slot selection and repeated reactor
removal then agree. The cooling profile's peak terminal-voltage error falls from
0.032512 to 0.002587 pu. This is diagnostic evidence, not a production clock
policy change or acceptance of the complete modified-Euler matrix.

The remaining capacitor and reactor insertion differences start before any bank
operates. At the first endpoint following the reference change, capacitor
current is 0.350594 pu versus 0.339957 pu in the reference, straddling the 0.35 pu
pickup threshold. Reactor current is 0.243559 versus 0.257626 pu, straddling
0.25 pu. Ordinary predictor/corrector therefore starts pickup one 0.5 ms interval
earlier. Its first current agrees with an independently evaluated Heun recurrence
to 2e-12 pu; the captured reference agrees with the averaged-event two-step
increment to 2e-7 pu. Substituting either recurrence for the other fails the
first-interval discriminator. This identifies an integration-stage difference,
not evidence of incorrect bank-delay equations.

The tests retain the existing accepted staggered and single-operation gates.
The full repeated-operation modified-Euler matrix remains diagnostic: its paired
capacitor and reactor insertion peak voltage errors remain 0.034233 and
0.026749 pu respectively. No thresholds, model coefficients, production clock
or numerical equations were changed. Further qualification must distinguish an
explicit solver clock contract from integration-method accuracy; adjusting bank
pickup to hide a current-integration difference would change the model.

A coupled reinitialization regression now drives the controller to a latched
trip, accepts electrical detachment, restores and resolves the operating point,
invalidates cached network initialization and reinitializes the same solver.
For both ordinary modified Euler and staggered integration, the next forty
coupled endpoints match a fresh import: all six coordinates and bus voltage
within 1e-9, with accepted physical current within 1e-12 and identical
timer/discrete histories. This verifies clean solver/model
reuse after explicit reinitialization. It does not introduce an online reconnect
command or establish an independent reference reconnect trajectory.


## Directional blocking recovery

Paired short-fault controls distinguish undervoltage suppression from
post-overvoltage recovery. Undervoltage releases on voltage recovery without
starting `Sdelay`. Overvoltage arms a recovery hold; its elapsed duration starts
when terminal voltage recovers, not at initial blocking. Expiry releases the
following integration interval while the expiring interval retains its accepted
zero physical output. A zero duration bypasses this hold. The raw terminal
voltage still drives the separate trip detector.

The model now implements these distinct histories using a reusable boolean and
timer. There is no new expression evaluation, per-step allocation, precision
policy or timing offset. The generic diagram does not specify these directional
and accepted-output details; paired independent captures establish them.
A sensor bypass and a long fault reject the alternative hypothesis that the
recovery timer follows filtered regulation voltage. Every short-block control
has finite measurements, clean initial conditions and a repeating cold capture.

`directionalBlockingRecoveryMatchesEveryPhysicalOutputStatus` compares every
blocked/released physical-current row, including event duplicates, under both
methods. It retains full voltage, angle, current, power and six-state trajectories.
For the short undervoltage fault, peak bus voltage error falls from 0.180138 pu
to 0.002949 pu with ordinary modified Euler and 0.000118 pu with staggered
integration. The corrected overvoltage hold reduces peak voltage error from 0.031586 to
0.002242 pu and from 0.032671 to 0.00000649 pu respectively. Block/release
row mismatches fall from 29 to zero for the short undervoltage fault and from
11 to zero for the short overvoltage fault under each method. Existing accepted blocking, rating,
trip and MSS gates remain unchanged.

This is lifecycle acceptance, not blanket trajectory acceptance. In particular,
coarse modified-Euler overvoltage voltage range-NRMSE remains approximately
0.00145, above the unchanged 0.001 trajectory target. The complete diagnostic
trace is retained. Slow-reset controls with zero and doubled current deadband
retain an output-coordinate residual and nonzero native initialization
residuals; they are algebraic discriminators only. No undocumented offset is
introduced to fit those traces.


## Recovery clock and local integration discrimination

The overvoltage recovery timer uses accepted endpoint timestamps, including the
solver's explicitly selected endpoint precision. Its start is the preceding
accepted boundary when recovery is first observed. Summing nominal `dt` instead
ignored the declared clock and released one interval early in a half-step
control. With the explicit single-precision staggered clock, the correction
reduces peak voltage error from 0.00820631 to 0.00000435 pu and peak current error
from 0.0399162 to 0.00002063 pu; every blocked/released output row now matches.
The registered half-step test retains the unchanged 0.001 voltage range-NRMSE
and 0.01-degree rotor-angle RMSE gates. Default modified Euler continues to use
its existing double-precision clock; no timing shift or precision default is
introduced. Its half-step trace retains a one-row clock-policy difference.

Recorded-terminal-input replay localizes about 98% of the coarse modified-Euler
lag-state peak error to controller integration: 0.0107115 pu versus 0.0109086 pu
in the coupled run. A test-only shared-state Heun evaluator reproduces the
ordinary one-step recurrence within 2e-12 at every retained row. With fixed
outer coefficients and one outer history commit, one/two/four/eight local steps
give peak errors of 0.0107115/0.0107397/0.0107465/0.0107482 pu respectively, all
at the first post-hold release endpoint. The diagnostic uses explicitly linear
interpolation of recorded boundary inputs inside each interval while preserving
all original event-side samples. It does not qualify that interpolation as a
production solver policy.

Local refinement does not remove this finite-outer-step method-transition
residual. No production local-substep option or extra runtime work was added.
The timestamp defect and the remaining modified-Euler trajectory discrepancy
are separate findings; neither changes the other method's equations or gates.


## A second controller's MSS action removes a tripped device

A two-controller control distinguishes an internal bank operation from ordinary
continued integration. Independent uninterrupted cold pairs show the tripped
controller retaining positive decaying physical current until the other
controller inserts its bank. At the bank event, two rows with the same timestamp
retain the output-lag state at 0.00827986 pu while physical current changes from
that value to zero. Without the bank operation, physical current keeps decaying
until fault clearing. All original rows and event duplicates are retained.
The inactive source state subsequently drifts; it must not be interpreted as
physical injected current or used to fit active controller equations.

Earlier status-polling captures issued a new continuation call every interval.
Those calls themselves altered trip removal, so they are retained as rejected
lifecycle controls. The accepted bank/no-bank controls run uninterrupted across
the relevant interval. The scheduled-event control includes its actual event
boundary; its result alone does not separate an event from a continuation call.
The pickup setting sustains demand through the fault, rather than resetting the
bank delay before it expires. No current gain or trajectory threshold is fitted.

The MSS boundary now prepares controller references during initialization.
After a successful bank stamp it applies the existing electrical-removal commit
to active peers. This performs no integration and leaves nontripped peers
attached. The ordinary and staggered coupled tests both remove the tripped peer
at the bank endpoint; previously neither removed it. The no-bank and scheduled
paths retain their prior behavior. These are lifecycle gates, not new full
trajectory acceptance or a resolution of coarse modified-Euler method error.

A reusable checkpoint protects the bank controller. Ownership and unfinished
predictors are checked before any physical mutation. A failed matrix stamp
restores exact bank status, sequence cache, matrix diagonal, dirty flag and
controller histories; peer histories and injections remain untouched. The same
time can then be retried successfully, and repeated acceptance is idempotent.
The exact cache rollback uses the isolated core snapshot API. Preparation scans
once; ownership checks occur only on actual bank actions, not every integration
step. Disabled MSS does not build the peer registry.

This transaction ends after the physical stamp. Rollback of a later failed
network solve remains unfinished, as does arbitrary external topology-edit
notification. No shared solver lifecycle or default dependency was changed.

The scoped default regression selection executes 24 tests across five classes,
with no failures, errors or skips. This includes the unchanged scheduled trip,
blocking, rating and MSS gates; optional extended trajectory diagnostics were
not selected. The source-pair test was repeated after tightening comparison of
the assigned bank timer (two tests pass). Private provenance, original captures,
rejected controls, full logs, reports and source snapshots are retained at
`verification-artifacts/svs-mss-trip-boundary-20261104`.
Archive SHA-256: `f6071e67a8de558f6a03273a1803dd7553bbf059bcb9e0864546c83d37410c12`.

## Shared ownership metadata

MSS boundaries now share one immutable, network-owned ownership snapshot.
It stores flat bus identities, device offsets and device identities rather than
one object and one device array per bus for every controller. Empty buses still
have entries: attaching a device there invalidates the old snapshot. Acquisition
at explicit initialization reuses an unchanged snapshot or creates a new one.
Previously prepared boundaries retain their old snapshot and reject changed
ownership until they are reinitialized; another boundary cannot refresh them
implicitly. Network copies do not copy the transient adapter, and different
networks cannot reuse its ownership data.

The checks still run only during preparation and actual bank actions. There is
no additional per-integration-step network scan. Physical equations, trip
acceptance, bank ordering and exact stamp rollback are unchanged.

A synthetic metadata-only harness prepares 32 enabled MSS boundary instances on
7,000 buses. It does not solve an electrical network or measure complete
simulation time. After six warmups, seven samples give the following costs per
**complete 32-boundary preparation pass**:

| Ownership preparation | Allocated bytes | Median time | Observed range |
|---|---:|---:|---:|
| Previous per-boundary snapshots | 20,641,024 | 4.095 ms | 3.929–8.468 ms |
| Shared snapshot rebuilt | 60,480 | 1.776 ms | 1.185–1.894 ms |
| Shared snapshot reused | 4,352 | 1.831 ms | 0.584–5.016 ms |

Rebuilding allocates about 99.7% less; allocation is repeatable across samples.
These are warmed preparation measurements: rebuilding the snapshot does not
mean a cold JVM. The reuse timing range overlaps the baseline, so allocation
reduction is stronger evidence than a precise speedup ratio. No whole-simulation
performance improvement is claimed.

The existing default six-class selection passes 27 tests, then the completed
seven-test boundary class also passes after adding the explicit stale-owner
control: 28 distinct tests, no failures/errors/skips. Coverage includes network
copy/owner separation, devices added to previously empty buses, same-size
replacement, removal, explicit refresh, failed-stamp rollback and both-method
trip ordering. Optional extended trajectory diagnostics are outside this run.

The comparison baseline is plugin `a48e0e9d1`. Exact harness/source snapshots,
commands, logs and reports are retained privately at
`verification-artifacts/svs-shared-ownership-20261104`; its manifest pins the
candidate commit and the explicit isolated core dependency.
Archive SHA-256: `676e6090ac6a0e6ba29a0523a6ff833d9fdad8a7bfc5054c5f50842cd99e061a`.

## Independent first-release stage discriminator

A state-seeded check isolates the selected 0.0750–0.0755 s recovery interval.
All 605 source rows and event duplicates remain retained and hash-verified.
At this interval the sensor has no direct feedthrough, the transient gain is
unity and the current limits are inactive. Source equations evaluated from the
retained states therefore give an independent derivative calculation.

For interval length `h`, the two-step trial is
`x + h*(1.5*f_left - 0.5*f_previous)`; the modified-Euler trial is
`x + 0.5*h*(f_left + f_predictor)`. The preceding held derivative is negative
because the lag was decaying toward a zero command.

| Selected release lag | Current, pu |
|---|---:|
| Supplied reference | 0.0547207407653 |
| Independent two-step recurrence | 0.0547207390011 |
| Independent Heun recurrence | 0.0440032873506 |

The independent two-step result differs from this source sample by 1.77e-9 pu.
The selected-interval method gap is 0.01071745165 pu: 0.00065734363 pu comes
from subtracting the previous held slope, and 0.01006010802 pu from the remaining
stage-weight difference. This agrees within 5.95e-6 pu with the previously
reported full recorded-input replay peak, supporting its localization to this
release transition. It does **not** prove that the complete overvoltage
trajectory discrepancy is unavoidable.

The ordinary public predictor/corrector methods reproduce the independent Heun
calculation within 2e-12. The shared-state checks verify exposed derivatives and
publication/projection of an externally assembled two-step trial within 2e-12;
they do not execute or independently certify the staggered solver's integrator.
Changing only the corrector's terminal voltage between the retained left and
right values changes the sensor coordinate by its analytic amount and leaves
the release lag increment exactly unchanged. This isolates this interval's
stage semantics from instantaneous network feedback; later coupled response
still depends on the network.

Six focused tests pass, including the existing limiter SMIB checks. A final
single-test repeat checks the explicit decomposition. No production equation,
method, clock, coefficient or accuracy gate changes. The coarse modified-Euler
overvoltage voltage NRMSE of approximately 0.00144985 remains above 0.001;
full trajectory qualification remains open.

Private evidence: `verification-artifacts/svs-release-stage-discriminator-20261104`.
Archive SHA-256:
`dfc2338bdc4dd1d14008e994d79a0c464df866b293a36e19760bae8a226f189b`.
