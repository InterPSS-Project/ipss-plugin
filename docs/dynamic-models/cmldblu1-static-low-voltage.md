# Legacy static-load current port and low-voltage preparation

The legacy composite has an additional low-voltage static-load rule. The
voltage polynomial alone does not describe its deep-fault response. Independent
cold SMIB controls establish the following behavior for the whole static
component with both distribution elements omitted and zero frequency coefficients:

- Below 0.7 pu, prepare conductance and susceptance once from preceding normal
  power and the first low-voltage network iterate: `G = P_previous / V_first²`,
  `B = -Q_previous / V_first²`.
- Below that threshold, use `P = G*V²`, `Q = -B*V²`, regardless of the supplied
  voltage exponents. At and above the threshold, use the normal polynomial.
- Retain the prepared G/B after recovery and through subsequent faults. A later
  first entry uses the preceding power, rather than nominal initial power.
- A slowly crossing voltage prepares near nominal admittance; an abrupt fault
  can prepare a substantially larger value. This distinction depends on the
  first network iterate and cannot be reproduced by fitting a polynomial gain.

This is an explicitly qualified legacy numerical boundary rule. It is not a
replacement for another model dialect's published load equations.

Legacy static reactive demand uses the magnitude of `PFs`; a negative supplied
constant does not request leading reactive power. The original signed constant
remains available through the data API. `LegacyStaticLoadModel` applies this
policy to both component initialization and distribution compensation while
reusing the polynomial kernel. Typed and zone static loads retain their signed
power-factor convention.

## Numerical port and physical current

`Cmldblu1Model` selects `LegacyStaticLoadPort` automatically only when the whole
owned composite is static, transformer and feeder are omitted, and both static
frequency coefficients are zero. Other configurations retain their existing
runtime; their low-voltage qualification remains open.

The port retains the original physical component shunts. Let `Y_c` be only the
composite's newly added shunt and `Y_aux = -Y_c` its numerical port stamp:

`(Y_rest + Y_c + Y_aux)V = I_other + Y_aux*V - I_static(V)`.

Cancelling the numerical pair gives

`Y_rest*V = I_other - Y_c*V - I_static(V)`.

The capacitor current remains voltage dependent and is refreshed at every trial
voltage. Original bus shunts and unrelated source loads are excluded from the
cancellation. This representation also reproduces the first network voltage
used by the low-voltage preparation. Retaining the old auxiliary static stamp,
or moving only the static component while retaining its numerical capacitor
stamp, gives similar voltages but incorrect prepared G/B and static power.
Those alternatives were retained as rejected private experiments.

## Shared lifecycle and efficient execution

The implementation uses the core's existing `NetworkIterationAware` contract
under modified Euler and staggered integration. No second solver or expression
interpreter is introduced.

- Begin copies accepted preparation into working solve storage.
- Before-iteration callbacks prepare G/B and the preceding normal power.
- Current and monitoring getters do not prepare or commit that memory.
- The convergence callback publishes power at the accepted voltage.
- Successful completion retains the working preparation. Failed iteration or
  earlier callback failure discards it under the core completion contract;
  completion across different participants is not globally transactional.

The component has no differential state vector. Accepted G/B and preceding P/Q
are available through named diagnostics. Its scalar current kernel retains the
numerical library's scaled division and arithmetic order, including both
division branches; nonfinite inputs retain the generic fallback. Warmed current
queries are bounded at 64 bytes per query for the fresh immutable power and
current observations. Callback preparation and recording are outside that
allocation measurement; no complete-simulation speedup is claimed.

## Independent controls and remaining work

Raw cold-repeat captures retain all channels, negative startup samples and both
sides of each event. Undefined allocated diagnostics for an absent motor remain
in the files and are explicitly separated from active-channel repeatability.
No diagnostic storage is cleared. Complete coupled comparisons cover both
integration methods, two reference intervals, faults on either side of the
threshold, continuous crossing, recovery, repeated entry and a delayed first
entry. Current-port ownership checks also retain an unrelated load, an existing
shunt and colocated generation. Constant P/I/Z, mixed polynomial shapes,
both signs of the static power-factor input and exactly zero reactive power
have separate controls.
The PF-magnitude policy also has paired positive/negative-input controls with
transformer and feeder represented; active reference channels and complete
production trajectories are identical between the two signs. Those controls do not
qualify the distributed model's low-voltage cache.

Across the complete nonnegative-time half-second current-port controls, worst
voltage range-normalized RMSE is `4.31e-5`, static P/Q range-normalized RMSE is
`1.88e-4`, and rotor-angle RMSE is `5.39e-5` degrees. Normalization uses each
complete reference channel's range; exactly constant channels have a separate
absolute-error gate. No offsets, interpolation or event-sample reduction are
used. The accumulated reference clock is checked explicitly.

The complete composite remains `LOADABLE`. Mixed motor/electronic bundles,
represented distribution, nonzero frequency coefficients, warm restoration,
production-step and long-duration qualification remain open. Their behavior
must be established independently before extending this port's eligibility.
Relay-coordinated shedding and restoration of the component compensation also
remain outside these trajectory controls; the unit checks cover load-factor
arithmetic only.
