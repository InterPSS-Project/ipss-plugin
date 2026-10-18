# Texas2k supplied-reference comparison: work in progress

## Acceptance contract

The supplied corpus contains 36 ten-second disturbances across six cases. The
updated user contract covers all matched bus-voltage magnitudes, machine
terminal P/Q, and generator rotor angles. Bus-voltage angles are absent from
the references. The user confirmed the following limits and conventions:

- Voltage and P/Q: NRMSE below `0.001` before the event and `0.005` from event
  application, including the fault, through the end of the run.
- Both periods use the **full ten-second reference range** as the denominator.
- Rotor angle: maximum absolute error below `0.002` degrees before the event;
  **RMSE below `0.01` degrees** across the full ten seconds.
- Load conversion: constant-current P and constant-impedance Q.

The prefault side at the application timestamp is a prefault observation; the
duplicate event-post sample belongs to the fault/postfault period. The earlier
single `0.001` full-window gate is retained only as historical evidence.
The updated target has **not** been achieved. Partial or failed trajectories
cannot pass.

Commercial-tool documentation and reproducible commercial-tool outputs are the
primary verification references for the exact model and version. ANDES is a
secondary consistency check; agreement with it does not establish correctness
or satisfy commercial-reference acceptance. Resolve discrepancies against the
commercial references and documented equations. Do not change model behavior
or relax tolerances solely to match ANDES. Missing commercial verification
remains an open verification gap.

The local comparison producer retains input and reference SHA-256 hashes,
freezes compiled runtime jars, preserves event-pre/event-post samples, and
validates the native float32 clock before mapping samples by integration-step
count. It does not fit parameters, initial angles, offsets, or trajectories.
Simulator-driving programs and full numerical evidence remain in the ignored
private verification archive, following repository policy.

## First validation case

Case 2 is the first testing and benchmarking case, as requested by the user.
Its largest initial voltage difference is `2.14042e-6` pu across the supplied
observations. This power-flow agreement supports starting here; dynamic
acceptance still requires the separate voltage, terminal P/Q, and rotor-angle
checks above.

The first disturbance is `case2_bus_fault_01` at bus 6307. A 1.2-second
diagnostic covers the prefault interval, fault application, clearing, and early
recovery. It uses the confirmed constant-current P / constant-impedance Q
profile, a fully settled power flow, and a frozen runtime. The full ten-second
reference range remains the normalization denominator for this short check.
After the prefault mismatch is resolved, Case 2's remaining faults and
generator trips follow before expanding to the other five cases. Short
diagnostics cannot establish ten-second acceptance.

The first frozen run completes 1.2 seconds in 92.009 seconds of wall time,
including loading and initialization. Power flow reaches zero unsettled
controls; the largest initial Norton residual is `1.025e-12` pu. Fault
application and clearing both converge. The prefault comparison nevertheless
fails 18 of the 20 channel criteria:

| Quantity | Worst prefault error | Required limit | Channels passing |
| --- | ---: | ---: | ---: |
| Bus voltage, NRMSE | 0.00148441 | < 0.001 | 2 / 5 |
| Terminal P, NRMSE | 0.00305938 | < 0.001 | 0 / 5 |
| Terminal Q, NRMSE | 0.00388275 | < 0.001 | 0 / 5 |
| Rotor angle, maximum degrees | 0.325219 | < 0.002 | 0 / 5 |

Using only observed prefault squared errors and the full ten-second reference
sample count gives rotor-angle RMSE lower bounds of `0.01845`–`0.02994` degrees
for these five machines. Even zero subsequent error cannot make this runtime
pass the ten-second angle criterion. The unobserved remainder is incomplete;
no full-window NRMSE result is claimed.

Recorded-boundary controller diagnostics retain the original Case 2 data.
Replaying reference speed and electrical power through the five PSS2A
stabilizers gives maximum output errors below `1.8e-5` pu. Replaying terminal
voltage, field current, and stabilizer output through their exciters gives
maximum EFD errors below `7.6e-5` pu for four ESST4B machines and `0.001224` pu
for the EXST1 machine. These diagnostics use 0.5-, 0.25-, and 0.125-ms
integration steps and narrow the remaining investigation; they do not prove
full-system agreement or complete model conformance. Fourteen comparison
contract tests pass, including event sides, full-window normalization,
degree-based angle limits, and rejection of incomplete trajectories.

A separate 0.25-ms full-network diagnostic covers the complete prefault
interval while retaining the 0.5-ms reference observation grid. All five
voltage channels pass, with worst NRMSE `0.000587661`. Worst P/Q NRMSE falls to
`0.00148466` / `0.00167444`, and maximum rotor-angle error falls to `0.218461`
degrees. P, Q, and angle acceptance remains unmet. This step-size sensitivity
requires further integration/coupling investigation. The observation option
also reproduces all 50 numerical columns exactly at all 41 samples of a
separate ordinary-step, 20-ms regression check.

## Integration and operating-point follow-up

An independent cold replay exactly reproduces the commercial two-bus wind
trajectory. Three unconstrained lag states follow
`x[n+1] = x[n] + h*(1.5*f[n] - 0.5*f[n-1])`, with zero previous derivative
at startup and `h=0.0005` seconds. Here `f=(u-x)/T`, using the recorded inputs
and unchanged time constants:

| State | Maximum recurrence error (pu) | Steps checked |
| --- | ---: | ---: |
| REGCA1 active current | 7.09e-8 | 2,002 |
| REECA1 active-power order | 7.38e-8 | 2,002 |
| WTTQA1 measured power | 7.25e-8 | 2,002 |

Each float32-rounded prediction differs by at most one rounding unit. This
identifies the observed two-step update for these branches, rather than a
Heun update. Limiter transitions, event histories, and coordination of all
model derivatives still need verification before a production integration
change. Adding two Heun startup steps alone does not solve Case 2: worst
prefault voltage/P/Q NRMSE remains `0.00141946` / `0.00295138` / `0.00385832`,
and maximum rotor-angle error is `0.333880` degrees.

A separate two-bus probe retains Case 2 generator 6245's GENROU parameters
and operating P/Q. Its initial rotor-angle error is `5.86e-5` degrees, and all
four electrical-state errors are below `5.5e-7` pu. The commercial reference
repeats with an identical raw CSV hash. Stator-equation reconstruction from
30 supplied Case 2 observations, covering 26 generators, instead locates
initial network-angle differences up to `0.004342` degrees. The reconstructed
internal-voltage magnitudes agree within `1.20e-7` pu. These inferred angles
are diagnostic observations only; they are not injected into simulations or
substituted for the supplied rotor-angle acceptance channels.

Enabling the tap, shunt, phase-shift, and interchange controls recorded in the
RAW file changes the solved Case 2 voltages by at most `5.33e-15` pu and angles
by `1.86e-12` degrees. Explicit augmented zero-impedance power flow also gives
the same solution; this case has no eligible branches at its saved `0.0002`-pu
threshold. Neither variant resolves the initial network-angle gap. The
reference generation's remaining preparation settings are still unconfirmed.

## Rotor-angle reference

The reference simulator's `SET_RELANG` supports absolute synchronous-frame angles with
`SWITCH=0`, or angles relative to a selected machine (`SWITCH=1`, positive
`IBUSEX`). System-average and weighted-average references are additional modes.
This is an output-reference choice; a generator rotor angle need not equal its
terminal-bus voltage angle or be zero at the slack bus.

Thirty supplied ANGLE channels were checked against their GENROU/GENSAL
rotor-angle STATE channels over 2,005 samples per case, through the first fault
boundary. The maximum difference between ANGLE and STATE converted to degrees
is `3.55834e-5` degrees, consistent with native float/export rounding. There is
no observed additional reference-machine subtraction in these supplied channels.
The comparison therefore retains the synchronous-frame convention.

The archived installed API page for `SET_RELANG` has SHA-256
`a45a5476b0c324fd0c287b4cdb019bb2a7c53ed2f894ea081d44b9f1597ce1ce`.

## Shared engine corrections

- Core `3681e4e50` removes repeated CML expression parsing, symbol-array
  allocation, unused observation collections, and repeated parameter-field
  lookup. Live signal and parameter values are still read on every evaluation.
  A paired 1.2-second fault benchmark uses identical physical classes, heap
  limits and unprofiled execution. Runtime falls from 325.201 to 223.536 seconds
  (31.26%); all numerical columns in all 2,403 samples are identical.
- Core `f7b082ed0` removes additional switched-shunt/SVC additions where the
  aggregate capacitor adapter already includes those banks. Sequence-network,
  dynamic-network and initial-load paths now count active banks once, including
  control-locked banks. In the Case 1 RAW initialization diagnostic, the largest
  fixed Norton correction falls from 4.93246 to 0.000799 pu.
- Core `124bca40f` makes the existing unmodeled-generator negative-load
  conversion active and constant-power, preserves other modeled generators at
  the same bus, and avoids duplicate equivalents on repeated initialization.
- Core `9893d5bae` clamps a violating machine at its reactive limit and
  redistributes the residual among machines with headroom. It preserves machine
  active power and the solved bus reactive total; infeasible totals return false.
  The correction is independently reproduced by the reference simulator on a public
  two-machine fixture, including both limit directions. Zero-power sharing also
  counts active machines only. The full core regression passes **2,945 tests**,
  with no failures, errors or skips.
- Core `1208a94c2` preserves solved slack-machine P/Q when reactive limits
  cannot supply the bus total. A sole slack machine retains the required Q;
  with several machines, the first active machine absorbs unavoidable excess
  after the other machines reach their limits. The allocation still reports
  the limit violation. Eight reference-simulator scenarios cover both directions and
  reproduce exactly from a second cold process. All **2,947 core tests** pass.
- Core `2576b7950` restores Q-limit enforcement after a bound is released,
  solves the changed network before another limit is selected, and includes
  colocated loads in generator power-flow results before dynamic initialization.
  Both defects fail focused regressions before correction. The complete core
  gate then passes **2,949 tests**, with no failures, errors, or skips.

## Power-flow voltage check requested by the user

The six cases were solved independently before any dynamic-state initialization,
using fixed input taps/shunts/phase shifts/interchange, active generator Q-limit
control, Java KLU, and a `1e-10`-pu mismatch tolerance. All six final runs converge
with zero unsettled controls and zero PV-generator Q-limit violations. All
2,000 active buses per case are compared with the solved RAW voltage values:

| Case | Bus-voltage RMSE vs RAW (pu) | Maximum difference vs RAW (pu) | Maximum difference vs first dynamic reference (pu) |
| --- | ---: | ---: | ---: |
| 1 | 7.016e-7 | 9.795e-6 | 3.141e-5 |
| 2 | 1.565e-6 | 2.288e-5 | 2.140e-6 |
| 3 | 5.003e-7 | 7.283e-6 | 3.400e-4 |
| 4 | 1.939e-6 | 9.797e-6 | 5.640e-4 |
| 5 | 6.889e-7 | 9.405e-6 | 2.596e-4 |
| 6 | 5.981e-7 | 6.017e-6 | 1.698e-4 |

The last column uses all 360 supplied initial bus-voltage/ETERM observations,
covering 304 unique case/bus pairs. These samples occur after reference-simulator dynamic
initialization, at `-2*dt`; they are distinct from a saved power-flow bus-voltage
export. The corrected power-flow solution agrees closely with the supplied
solved RAW values. The remaining initial dynamic-reference differences are
retained for investigation; neither data set is used to fit voltage offsets.
Before correction, Case 3 and 4 initial-reference differences reached `0.02270`
and `0.02020` pu respectively. Intermediate incomplete control variants are
preserved as failed diagnostics, not accepted power-flow solutions.

## Native wind-speed discrepancy

A reference-simulator two-bus discriminator uses the unchanged Case 4 Bus 2089
REGCA1/REECA1/REPCA1/WTTQA1 parameter stack. PFLAG=1 retains the generator-speed
multiplier even though WTTQA1 already forms `Pref = torque * speed`.
For `P0=0.625`, `speed0=0.8775`, and `Tpord=0.03`, the reference simulator reports
`dPord/dt = -2.5521`, matching `(0.625 * 0.8775 - 0.625) / 0.03`.
Adding WTDTA1 retains this startup derivative; changing only PFLAG to zero
removes it. This is a genuine non-equilibrium initial condition in the supplied
profile, not a signal to fit a gain or suppress the derivative.

The DYR importer explicitly selects the speed convention demonstrated by the
commercial reference. Existing typed-client behavior is retained for API
compatibility. The ANDES fixture explicitly selects its different convention
as a secondary regression check; that convention does not define the
commercial model's behavior or acceptance criteria. No comparison tolerance
is relaxed. Focused REECA1, wind-control and cross-tool tests pass.
The installed model-library REECA1/WTTQA1 diagrams and reference outputs support
this distinction; it is not a claim of complete wind-model conformance.

In first-20-ms diagnostics, maximum bus-voltage error falls from 0.01655 to
0.000947 pu in Case 4 and from 0.006732 to 0.0000953 pu in Case 5. These short
errors are diagnostics, not full-window NRMSE acceptance.

A second native discriminator separates shaft speed from WTTQA1's filtered
speed reference. With no drive train, native SPEED stays exactly at
`-0.1224999428` deviation over one second while the torque speed-reference state
changes. Adding WTDTA1 produces changing shaft speed. The native convention now
retains initialized shaft speed only when the mechanical model is absent;
the connected drive train and the existing typed-client convention continue
to supply changing speed. Focused controller and cross-tool tests pass.

On the identical native two-bus inputs, the corrected active-power order ends
at `0.603619608` versus native `0.603622019` at one second. The largest order
error is `0.000632356` at time zero, which still exposes a startup-history
difference. This diagnostic does not establish full-system accuracy.

## Open validation

The previous full-run epoch was stopped after isolating the missing native
speed multiplier; its interrupted traces are preserved and marked aborted.
Case 4's first fault also fails at application: the network reaches its
500-iteration ceiling near the adjacent REGCA1 terminal. That convergence
failure remains open.

RAW initialization, solved power flow, removal of fixed Norton corrections,
and two explicit startup-history steps have been compared separately. Neither
the history experiment nor the fixed-control load-flow experiment establishes
the correct native startup algorithm or reproduces every operating-point
quantity. They remain diagnostic variants. The initial runs assumed constant
impedance for both P and Q; each later diagnostic declares its own load
assumption. The user has now confirmed constant-current P and impedance Q.

The retained-speed follow-up compares two explicitly declared load assumptions
with identical frozen code. Case 1 fault 2 through 1.2 seconds gives maximum
voltage/rotor-angle errors of `0.0197555` pu / `2.43263` degrees with impedance
P/Q, versus `0.00236607` pu / `0.0608879` degrees with constant-current P and
impedance Q. This load profile has since been confirmed by the user. The runner
now selects the load-current network
path for mixed load profiles instead of silently leaving them in a fixed Y.

The slack fix also exposes the need to finish all enabled power-flow controls:
the old 50-iteration diagnostic ends with 42 controls unsettled. A 300-iteration
allowance reaches `CONVERGED`, zero unsettled controls, and a maximum initial
Norton residual of `9.62e-13` pu. The first-20-ms voltage/rotor errors then fall
to `2.48108e-5` pu / `0.00132629` degrees. The runner now refuses an unsettled
power-flow result. A fresh ten-second current-P/impedance-Q diagnostic using
that operating point was stopped after its prefault rotor-angle error reached
`0.00829852` degrees, exceeding the updated `0.002`-degree maximum. The trace is
preserved and marked aborted. Full-window acceptance is still required.

Machine electrical output now uses instantaneous terminal `V * conjugate(I)`
on system base. The old `getPe()` field contains air-gap power, including stator
loss and possible event averaging, so it cannot substitute for terminal P/Q at
event boundaries. Historical traces retain their original column definitions.

The native frequency discriminator uses a finite-inertia grid. Giving both
simulators the same explicit 0.05-second bus-frequency filter reduces maximum
frequency differences to `3.62e-6` pu at the renewable bus and `3.53e-6` pu at
the grid bus. The installed application guide documents the reference simulator's default
filter as 0.04 seconds. All 107 Case 1/2 and all 306 Case 3–6 REPCA1 records
have FreqFlag=0, so this plant-controller frequency branch is inactive in the
supplied profiles. No global filter setting was fitted or changed.

A subsequent frozen Case 1 fault-2 prefix completes 1.2 seconds, including the
fault, in 154.55 seconds. This epoch includes the native speed multiplier but
precedes the retained-shaft-speed follow-up. Its maximum voltage error is
`0.0197468` pu and maximum rotor-angle error is `2.4254` degrees. Using only the
observed squared error and the full ten-second reference sample count/range
already gives NRMSE lower bounds of `0.00301678`, `0.00236012`, and `0.00430510`
for buses 6219, 6289, and 6024. Even zero future error cannot make this epoch
pass those channels. It remains an incomplete diagnostic, with all dependency
jars frozen and hashed in classpath order. The reference operating point,
startup history, and remaining fleet response still require verification.

The registered plugin regression was executed with a bounded 3-GB test heap.
It exposes failures in the old Texas2k no-event stationarity assertion and
SVSMO1T2 comparison. The stationarity premise conflicts with the independently
observed native wind startup. Both failures are retained and must be addressed;
the full registered gate is not reported as passing. An earlier smaller-heap
attempt exhausted the test heap and is not an accuracy result.

The installed reference-simulator license accepts only 50 buses. Its Texas2k load probe
reports just 50 loaded buses despite a zero read error, so it cannot regenerate
the complete 2,000-bus references. Small native discriminators are supported;
full-case comparisons use the supplied immutable outputs.
