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

`Cmldblu1Model` selects `LegacyStaticLoadPort` automatically for its static
portion when no motor fractions are active and the load bus is merged with its
parent or belongs to the represented distribution path. The composite may also
contain the legacy full-recovery electronic portion with active static frequency
coefficients, using the
[mixed current interface](cmldblu1-static-electronic-interface.md).
The combined configuration preserves the owned-shunt current balance and the
electronic leaf's pure-current interface in focused controls. Independent
half-second mixed/frequency fault trajectories with omitted and represented
distribution pass under both integration methods, including all event-side
samples. Motor-containing configurations retain their existing runtime and
low-voltage qualification remains open.

### Mixed static/electronic frequency qualification

Two cold reference runs contain 1,005 rows each. All active fields agree
exactly between repeats; four inactive allocated storage fields contain tiny
uninitialized differences and are excluded only from the repeat check. The
comparison retains both sides of fault application and clearing, checks the
accumulated source clock and compares every nonnegative-time row. It uses the
entered static frequency coefficients and an active full-recovery electronic
fraction without changing the numerical interface.

| Complete-response error | Modified Euler | Staggered |
| --- | ---: | ---: |
| Worst bus-voltage NRMSE | 0.000031125 | 0.0000005754 |
| Worst static P/Q NRMSE | 0.00033035 | 0.00027242 |
| Peak low-voltage G/B error, pu | 0.00000002609 | 0.00000002609 |
| Rotor-angle RMSE, degrees | 0.00003600 | 0.000009901 |

`CompositeMixedStaticFrequencyTest` enforces voltage and static P/Q NRMSE
below 0.001, rotor-angle RMSE below 0.01 degrees, and G/B peak error below
0.000005 pu for both methods. This qualifies the exercised omitted-distribution
profile; motors, restoration and longer runs remain separate work.

The raw cold-pair inputs, manifests, output and logs are retained in the private
`verification-artifacts/cmldblu1-mixed-frequency-20261105/captures.zip`,
SHA-256 `ad659d1bbf30cc17a2f2b0ea9834f9c08675e0457a8cb9a6f846a1e374aad7cb`.

### Represented-distribution frequency qualification

A second cold pair uses the same mixed static/electronic fractions and active
frequency coefficients with both distribution elements represented. Every
recorded field repeats exactly across the 1,005 rows. The generic static path
had 0.00251 bus-voltage NRMSE in this fault control. Selecting the prepared
distributed current port gives:

| Complete-response error | Modified Euler | Staggered |
| --- | ---: | ---: |
| Worst bus-voltage NRMSE | 0.000031168 | 0.0000010755 |
| Worst static P/Q NRMSE | 0.00010567 | 0.000094743 |
| Peak low-voltage G/B error, pu | 0.0000001966 | 0.0000001966 |
| Rotor-angle RMSE, degrees | 0.00005499 | 0.000008641 |

The same complete-row and event-side gates pass under both methods. The raw
reference pair and inputs are retained in the private
`verification-artifacts/cmldblu1-distributed-frequency-20261105/captures.zip`,
SHA-256 `40faed43c8f82bfaedfaea9b3b582b09e2bb82696e4b09736a50ed78113cf479`.

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
represented distribution, warm restoration, production-step and long-duration
qualification remain open. Their behavior must be established independently
before extending this port's eligibility.
Relay-coordinated shedding and restoration of the component compensation also
remain outside these trajectory controls; the unit checks cover load-factor
arithmetic only.

## Static frequency coefficients at the low-voltage boundary

A separate cold-repeated synthetic control enables both static active- and
reactive-power frequency coefficients while crossing the low-voltage threshold.
The reference retains every channel, the two event-side pairs, and prepared
conductance/susceptance. With this input, the previous implementation selected
the generic static-load interface because the frequency coefficients were
nonzero. That path gives about `0.00319` full-range voltage NRMSE under either
integration method, above the existing `0.001` gate.

The legacy static current port now remains eligible with nonzero static
frequency coefficients for a whole static component at the merged bus. Its
existing normal polynomial reads live bus frequency, and its first low-voltage
preparation retains the preceding accepted power. No numerical kernel or state equation is
duplicated. The complete new reference comparison gives worst bus-voltage
NRMSE `3.11e-5` with modified Euler and `8.13e-7` with staggered integration.
Static P/Q NRMSE remains below `0.000205`; prepared G/B peak errors are below
`1.8e-7` pu. The two cold reference captures agree byte for byte on every
recorded channel. The SHA-pinned private archive is
`verification-artifacts/cmldblu1-frequency-static-20261105/captures.zip`,
SHA-256 `6b8f9b955ccb6307cfe719463e45ae862cc224e5cd2e6f61eeceb594d18fa3b5`.

The new gate covers a whole static component with omitted distribution. Mixed
motor/electronic bundles, represented distribution with active frequency
coefficients, warm restoration and production-step accuracy remain separate.

## Ten-second production-step diagnostic

A separate whole-static, omitted-distribution SMIB run uses quarter-cycle
steps (`1/240 s`) and repeated faults. Two cold reference captures are
numerically identical across every recorded field. Both integration methods
retain all samples, including the reference's final clock-rounded overshoot
and both sides of each event. Comparison uses integration ordinal and event
side, with every accumulated binary32 reference timestamp checked explicitly;
raw timestamps are preserved without interpolation or offsets.

| Error over the complete recording | Modified Euler | Staggered |
| --- | ---: | ---: |
| Worst bus-voltage NRMSE | 0.00021852 | 0.000018183 |
| Static active-power NRMSE | 0.00091973 | 0.000075007 |
| Static reactive-power NRMSE | 0.00091967 | 0.000075194 |
| Rotor-angle RMSE, degrees | 0.00073550 | 0.00024159 |
| Peak rotor-angle error, degrees | 0.0043661 | 0.0029371 |

This extends diagnostic evidence, not the accepted model scope. Both angle
RMSE values exceed the existing half-second gate of 0.0002 degrees. The paired
fine-step runs below extend the diagnosis but do not yet resolve the
integration-order and duration effects needed for production-step acceptance. No
equations, model parameters or existing accuracy thresholds changed.

The probe accepts an explicit end time through ten seconds, preserving its
half-second default. The shared recorder has a bounded eleven-second ceiling
to retain clock-rounded reference endpoints; callers verify every source clock
sample. The existing static-port and
ownership regression selection passes all 109 checks.

Private diagnostic archive SHA-256:
`c9403da77b75d2665126b63dd5c7ae46375bd8d7ede9e97a07eae91b306e75d8`.

### Long-duration timestep refinement

The same repeated-fault profile now has cold-repeated ten-second captures at
0.5 ms and 0.25 ms. All recorded reference fields repeat exactly, and their
half-second prefixes exactly match the previously retained short controls.
Full comparisons retain all event-side duplicates and explicitly validate
every accumulated reference timestamp. At 0.25 ms, the accumulated clock needs
eleven extra steps to cross ten seconds; assuming only one overshoot step is
insufficient.

| Step | Method | Worst voltage NRMSE | Worst static P/Q NRMSE | Angle RMSE, degrees |
| --- | --- | ---: | ---: | ---: |
| 0.5 ms | Modified Euler | 0.000056737 | 0.00023312 | 0.00081014 |
| 0.5 ms | Staggered | 0.000055910 | 0.00022956 | 0.00080985 |
| 0.25 ms | Modified Euler | 0.00023752 | 0.00097561 | 0.0037571 |
| 0.25 ms | Staggered | 0.00023749 | 0.00097550 | 0.0037570 |

Reducing the step does not monotonically reduce reference error. In a separate
internal convergence diagnostic, comparing the two modified-Euler runs at
common nominal-time/event-side samples gives maximum differences of
`4.332e-7 pu` in voltage and `1.719e-5 degrees` in angle. That comparison is not
independent acceptance and does not replace the full reference comparisons.
The staggered cross-step maxima are `0.00017396 pu` and `0.00005004 degrees`.

These results do not establish a load-equation defect or justify relaxing the
existing angle gate. Reference-step, finite-precision and event-boundary
effects still need independent discrimination. Both methods' long-duration
qualification remains open. The 109 focused regression checks still pass;
only the diagnostic recorder's bounded duration allowance changed.

Private refinement archive SHA-256:
`68fd59c23ec1a8068e87e20b081cfbc9c1ba3dd9f2e8f743bd30ccbec4cdb05f`.

### Source-machine precision discriminator

The SMIB recorder now includes `MACHINE.Eq1`, `MACHINE.Ed1`,
`MACHINE.Psikd` and `MACHINE.Psikq`. Recording these coordinates leaves every
previously recorded value exactly unchanged in both fine-step runs and both
integration methods. This permits diagnosis of the source machine separately
from the load current port.

The initial field-state difference is `5.04e-9 pu` or less. At the final
recorded endpoint, it grows to approximately `9.90e-5 pu` with 0.5 ms steps
and `0.00031576 pu` with 0.25 ms steps under either method.

An independent recorded-input check uses the fixture's field equation
`dEq1/dt = (Efd - XadIfd) / 6`, with the original recorded field voltage and
field-current demand. Trapezoidal replay with and without binary32 state
rounding gives:

| Step | Unrounded replay peak error, pu | Rounded replay peak error, pu |
| --- | ---: | ---: |
| 0.5 ms | 0.00017562 | 0.000021339 |
| 0.25 ms | 0.00064579 | 0.000012279 |

The rounded one-step field-state predictions match approximately 97% of the
recorded updates. Angle-only replay does not account for the whole coupled
angle discrepancy. These observations support finite-precision accumulation
in the machine-state path as a contributor; endpoint replay is not the
reference's complete staged integration and does not prove sole causation.
No rounding is added to the production solver, and no load equations or
acceptance thresholds are changed. The full long-duration gate remains open.

The 109 focused regression checks pass with the additional diagnostic fields.
Private machine-boundary and replay archive SHA-256:
`52fc1f5a5200a8804e8052d9e57246e6889e3451b7f886d3d180d42e9f0e3aa5`.
