# SVSMO3T2 current-controller runtime

The implementation is **partial**. The base coupled SMIB fault passes its
voltage and rotor-angle gates. That result does not qualify coordinated shunt
switching, every limiter combination or production-step simulation.

## Ownership and execution

The exact record has a quoted FACTS name followed by the model name, twenty
ICON fields and forty-four CON fields. It has no bus-number or generator-ID
prefix. Import preserves the name, resolves a unique static shunt FACTS owner
and binds its local/remote buses before simulation. A named record cannot be
mistaken for a generator at `Bus0`. Missing, ambiguous and duplicate owners are
rejected. Coordinated MSS switching currently raises an explicit unsupported
error before attachment.

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

The extended recorded-input matrix remains explicitly diagnostic. It exercises
lead-lag coordinates, reference changes, slow reset, nonlinear droop, definite
and thermal ratings, deadband, blocking, trips and bypasses. Execution alone is
not acceptance. Remaining work includes:

- Complete coordinated MSS ownership, switching, discharge and breaker timing.
- Finish coupled and internal-state qualification of every selector/limiter
  branch, including current reversal and zero-time-constant paths.
- Resolve the remaining finite-stage behavior during exhausted-rating and
  thermal-limit transitions without fitting parameters or shifting traces.
- Verify short blocking intervals and reinitialization/reconnection histories.
- Extend validation and coupled tests to production steps and longer runs,
  then measure complete warmed recording/export time.

Private evidence retains exact source/tool provenance, executed probes, original
captures and diagnostic outputs. The public implementation tracker contains only
model-specific work and priorities.
