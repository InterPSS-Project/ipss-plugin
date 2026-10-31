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
Observation does not alter the equations.

Zero-duration event calls can project an active PI constraint. They do not
integrate derivatives or advance timers. Current limiting uses instantaneous
terminal voltage, separately from the filtered regulation signal. Blocking
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
Tripping publishes zero current and freezes this controller's continuous state.
The stressed independent trip capture has divergent allocated-state and held
algebraic observations after tripping; post-trip state equivalence remains open.

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
- Verify short blocking intervals and reinitialization/reconnection histories.
- Qualify non-unit STATCOM/network MVA-base ratios and remote regulation during
  coordinated bank actions.
- Extend validation and coupled tests to production steps and longer runs,
  then measure complete warmed recording/export time.

Private evidence retains exact source/tool provenance, executed probes, original
captures and diagnostic outputs. The public implementation tracker contains only
model-specific work and priorities.
