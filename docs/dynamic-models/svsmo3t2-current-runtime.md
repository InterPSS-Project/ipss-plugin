# SVSMO3T2 current-controller runtime

The implementation is **partial**. The base coupled SMIB fault passes its
voltage and rotor-angle gates. Coordinated shunt switching also has independent
signal-replay and single-operation coupled gates. Repeated-operation coupled
timing, every limiter combination and production-step simulation remain open.

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
duration and exhausted definite-time rating are committed histories.
Tripping publishes zero current and freezes this controller's continuous state.
The stressed independent trip capture has divergent allocated-state and held
algebraic observations after tripping; post-trip state equivalence remains open.

Independent discriminators also establish source initialization selections:
positive slow-reset gains suppress nonlinear droop even when the separate
slow-reset enable flag is off; the thermal rating branch requires a zero
definite-time duration. Entered parameters stay immutable, and the prepared
selections are exposed. These source-specific selections must not be inferred
from the generic block diagram alone.

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
both engines. Its complete base fault comparison retains startup samples and
both sides of fault application/clearing. It gates every bus voltage at
range-normalized RMSE below 0.001, rotor-angle RMSE below 0.01 degrees, prefault
angle error below 0.002 degrees, and all six state errors individually. The
observed base voltage RMSE is below 0.0001 pu and rotor-angle RMSE is approximately
0.00031 degrees. The compressed reference is hash-pinned.

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
is approximately 0.000624 and rotor-angle RMSE is below 0.000131 degrees.

Repeated-operation coupled profiles remain diagnostic: capacitor discharge,
successive capacitor insertions, cooling-slot transfer and paired reactor actions
show a few endpoint status differences, with worst voltage NRMSE approximately
0.01111. The exact recorded-clock replay passes these same profiles. A controlled
replay that changes only to the accumulated double-precision clock produces
status differences in capacitor discharge, successive capacitor insertion,
cooling-slot transfer and successive reactor removal. Paired reactor insertion
also has a coupled discrepancy absent from either isolated replay, so finite-stage
current feedback remains relevant. No time shift or widened voltage gate accepts
these coupled profiles.

The extended recorded-input matrix remains explicitly diagnostic. It exercises
lead-lag coordinates, reference changes, slow reset, nonlinear droop, definite
and thermal ratings, deadband, blocking, trips and bypasses. Execution alone is
not acceptance. Remaining work includes:

- Resolve repeated-operation coupled MSS timing while retaining the exact
  recorded-current replay and physical-stamp gates.
- Finish coupled and internal-state qualification of every selector/limiter
  branch, including current reversal and zero-time-constant paths.
- Resolve the remaining finite-stage behavior during exhausted-rating and
  thermal-limit transitions without fitting parameters or shifting traces.
- Verify short blocking intervals and reinitialization/reconnection histories.
- Qualify non-unit STATCOM/network MVA-base ratios and remote regulation during
  coordinated bank actions.
- Extend validation and coupled tests to production steps and longer runs,
  then measure complete warmed recording/export time.

Private evidence retains exact source/tool provenance, executed probes, original
captures and diagnostic outputs. The public implementation tracker contains only
model-specific work and priorities.
