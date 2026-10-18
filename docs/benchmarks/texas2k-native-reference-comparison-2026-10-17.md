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

An explicit `TWO_STEP` renewable integration option now captures the converter,
electrical, plant, and wind inputs at a common old endpoint. It advances states
once and refreshes algebraic commands at the second solver stage. The ordinary
integration option remains the default while full-system coordination and
limiter/event histories are unresolved. Seven new tests cover the first two
commercial startup steps, common-endpoint coupling, bypasses, dip freezes,
algebraic refreshes, and reinitialization. All **114 focused Java tests** and
**14 comparison-contract tests** pass. A default-option Case 2 check reproduces
every exported field exactly across 41 samples and 50 signal channels.

On the unchanged one-second two-bus wind profile, the new option reduces the
maximum active-power-order error from `0.000648616` to `0.00000345644` pu and
the active-current-state error from `0.000284216` to `0.0000131210` pu. Terminal
active-power error falls from `0.000228824` to `0.00000285113` pu on system base.
The reactive-path discrepancy remains; these results do not establish complete
model conformance.

Changing only the 107 renewable devices does **not** resolve the full network.
The Case 2 first-fault diagnostic completes 1.2 seconds, including clearing,
in 88.068 seconds. Worst prefault voltage/P/Q NRMSE is `0.00198368` /
`0.00573435` / `0.00652358`; maximum rotor-angle error is `0.441953` degrees.
Eighteen of the twenty prefault criteria fail, and the ten-second trajectory is
incomplete. This candidate is retained as a failed mixed-integration diagnostic.

An independent audit of the supplied first-fault outputs covers 30 GENROU
machines across all six cases. Using recorded speed to predict the next
rotor-angle state, the two-step recurrence is within one float32 rounding unit
for **600,021 of 600,060** intervals, compared with 596,146 for the trapezoidal
identity. In the first three milliseconds after Case 2 fault application,
generator 6244's maximum identity error is `7.74e-10` radians for the two-step
update and `1.03e-6` radians for the trapezoidal update. This localizes the next
integration investigation to a consistent machine/controller/network update;
it does not verify the remaining machine equations or full-system accuracy.

The commercial application guide's equation 15.2 confirms
`2H*dn/dt = (Pm - D*n)/(1+n) - Telec` for the native Flux0 convention. The
frozen GENROU adapter already implements this equation. The GENSAL adapter
was missing the corresponding override and now uses the same calculation.
Four public two-bus response profiles cover speed deviations of both signs,
with zero and nonzero damping. Each repeats with an identical CSV hash from
a second cold process. Independent Java initialization plus the corrected
derivative predicts the first native speed increment within `5.38e-10` pu;
the old equation differs by `9.67e-7` to `1.18e-6` pu. The pulse initializes
both STATE and STORE consistently, with zero previous derivative.

All 35 focused machine checks pass, including four new checks that fail before
the correction. The six-case fault/trip smokes, all-six machine attachment
check, and four reference-integrity checks also pass. Case 2 has all 25 GENSAL
units offline, so the corrected first-fault run reproduces every exported
field exactly in all 2,403 samples. Its worst prefault angle error remains
`0.325219` degrees. This correction does not resolve Case 2 or establish
ten-second acceptance. The existing shared staggered-Euler solver supports a
smaller controller set; extending it to the actual Texas2k stack remains open.

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

## Shared integration and state-storage diagnostics

The opt-in staggered solver now binds the active Case 2 machine/controller and
renewable stack: 103 synchronous machines, 96 renewable bundles, and 3,478 shared
machine/controller states. The last count excludes states integrated within
renewable bundles. GGOV1 and HYGOV expose evaluation separately from state
advancement and history commitment. Compound controller filters expose their
physical first-order states rather than a synthetic aggregate state.

A separate opt-in renewable storage experiment retains single-precision STATE
and STORE coordinates. REPCA1 reactive-reference states retain their absolute
physical coordinate while the public controller API continues to expose
increments. The commercial model-library state layout and immutable commercial
two-bus outputs define this check. Parameters, operating points, load assumptions,
and comparison limits are unchanged. The original solver remains the default.

On the two-bus one-second probe, maximum reactive PI integral error falls from
`0.0000728680` to zero, voltage PI integral error from `0.000829404` to
`0.00000709295` pu, and terminal reactive-power error from `0.000122375` to
`0.00000540610` pu. This is limited evidence for storage behavior; it does not
establish fleet or event conformance.

Case 2's first prefault second still fails the required limits. The shared
double-precision variant has worst voltage/P/Q NRMSE of `0.00198924`,
`0.00573879`, and `0.00652494`, with maximum rotor-angle error `0.441010` degrees.
Using single-precision renewable storage gives `0.00198830`, `0.00573411`,
`0.00652135`, and `0.441264` degrees. Each variant passes only two of the twenty
prefault channel criteria and is worse than the retained original baseline.
The denominator remains each full ten-second reference range. These are
completed prefault diagnostics, not completed ten-second disturbance runs.

Core regression passes 2,949 tests; focused plugin regression passes 89 tests.
With both new options disabled, all 41 samples and 50 signals in the first
20 ms of Case 2 reproduce the original baseline exactly. Renewable event-history
averaging, remaining limiter behavior, and full-fleet accuracy remain open.
No full ten-second event is accepted by this follow-up.

Frozen inputs, sources, classpaths, raw traces, comparison reports, and validation
logs are retained under the private verification-artifact directory
`2026-10-17-shared-integration`. An earlier precision run failed because its
frozen runtime mixed incompatible versions of a shared wind-controller class;
that assembly failure is retained separately and is not numerical evidence.

## Absolute plant reactive-reference limits, October 18 follow-up

REPCA1's QMAX/QMIN limits apply to its absolute reactive reference. REECA1
consumes an incremental plant command in this implementation. The limiter and
anti-windup thresholds now subtract the initialized reference to use that same
incremental coordinate. Source parameters and the existing equilibrium guard
are retained. This correction applies to all three integration paths when
REECA1 initializes its plant controller.

A crafted two-bus commercial discriminator sets PFLAG to zero and plant
QMAX/QMIN to `0.12/-0.12`, then applies a moderate voltage disturbance at
`0.01` seconds. These deliberate probe settings are separate from the unchanged
Texas2k cases. Two fresh commercial processes produce byte-identical raw CSV.
Across 223 matched samples, maximum plant reactive-command error falls from
`0.0105335020` to `0.000000153265` pu. The new upper/lower limit tests fail
against the frozen old model and pass with the correction. A focused reactor
run passes 56 tests; 13 selected coupling/limit tests also pass when independently
compiled against the frozen corrected runtime. A later reactor attempt cannot
compile a concurrently edited drive-train reference; it is not a passing full
registered-suite result.

Matched Case 2 runs both complete through `1.2` seconds, including fault
application and clearing. Their physical inputs and references are byte-identical;
only REPCA1 runtime entries change. The first observed difference above
`1e-10` occurs at `0.2195` seconds. Worst prefault voltage/P/Q NRMSE changes
from `0.00198830/0.00573411/0.00652135` to
`0.00168580/0.00483185/0.00531888`. Maximum prefault rotor-angle error changes
from `0.441264` to `0.302794` degrees. Both variants still fail 18 of 20 prefault
channel criteria. No ten-second event is accepted.

Independent PSS2A diagram playback for the two observed ESST4B machines uses
the supplied reference speed and electrical power without feedback or fitted
offsets. Regulator-integral error remains below `0.00000168` pu. Replaying the
same diagram with the actual simulation inputs reproduces the solver's
stabilizer output within `0.000000000741` pu. This supports the checked equations
and dispatch, rather than complete stabilizer conformance. An isolated
machine-only storage-precision experiment leaves the failed Case 2 prefault
metrics almost unchanged and is retained as a diagnostic, not enabled in code.

The complete frozen evidence for this follow-up is retained in the private
verification-artifact directory `2026-10-18-plant-reference-limits`.

## Fleet localization and coupled-source check, October 18 follow-up

A completed one-second Case 2 diagnostic observes all 26 unique generators
monitored across its six supplied events. All original 50 signals remain
exactly unchanged in 2,003 samples; repeated prefault reference channels across
events also agree exactly. The broader observation finds maximum rotor-angle
error of `1.84454` degrees at bus 1078. Initial errors at buses 5038 and 6245
already exceed `0.002` degrees. Mechanical-power discrepancies occur in both
governor families; this check does not isolate a governor defect.

Independent PSS2A diagram playback, driven by the unchanged commercial speed
and power samples, covers all 16 monitored ESST4B units. Their outer regulator
integrals agree within `0.000002082` pu through one second. This extends the
previous limited stabilizer check, but does not verify the remaining exciters
or explain the full-network drift.

Bus 1084 has the largest observed renewable reactive-power excursion. An
isolated commercial playback uses its unchanged model parameters, initial P/Q,
and recorded terminal-voltage boundary. The prescribed waveform is sampled on
the commercial integration clock; whole-case acceptance sampling is unchanged.
Two fresh processes produce byte-identical raw CSV. Across 2,003 samples,
terminal-voltage discrepancy is below `0.000000203` pu. Checked converter and
controller states differ by at most `0.000000924` pu, including the plant
reactive integral. This supports the checked component equations and storage
under that prescribed boundary. It is not a whole-network comparison.

The coupled three-bus discriminator exposes an initialization crash in the
extended staggered solver: its classical source was incorrectly cast to a
machine with electrical states. Classical sources now evaluate their mechanical
states without that cast. The new regression fails against the frozen previous
solver and passes with the correction. Six frozen-runtime solver tests and
13 focused reactor tests pass. The latter count includes those same six tests;
these are overlapping checks, not 19 distinct tests.

With this correction, the coupled synchronous-machine/renewable diagnostic
completes one second. It retains the source model parameters in an explicitly
synthetic network, with an independently solved operating point. Maximum rotor
angle error is `0.000348398` degrees; maximum bus-voltage error is
`0.000020884` pu. Terminal P/Q maximum errors are `0.000095765` and
`0.000081869` pu on the system base. Two fresh commercial runs are byte-identical.
Case 2 has no active classical machines, so this correction does not resolve
its full-network discrepancy.

Separate native netting probes establish that operation order matters:
netting before I/Z load conversion makes the negative load follow I/Z;
netting afterward retains constant P/Q. Each profile repeats in two fresh
processes. The supplied preparation order is still unconfirmed. A Case 2
after-conversion experiment preserves initial injected power, but worsens
maximum prefault angle error from `0.302794` to `0.361175` degrees. The existing
load-conversion order remains unchanged; comparison error alone does not
establish the original preparation procedure.

The remaining 17 active renewable units use PFLAG zero and have zero initial
reactive power in the solved InterPSS case. Bus 1011's controlled playback
exposes an initialization-sensitive recovery selector: the commercial two-bus
initialization reports a tiny positive Q (`3.85e-11` Mvar), while the full-case
model starts at exactly zero. With zero directional recovery-rate limits,
those signs select different subsequent current behavior. Its current and
electrical-controller discrepancies are consequently much larger than bus
1084's. This is unresolved evidence for the zero-Q branch; it is not accepted
as proof of a PFLAG-zero equation defect or a justified change to the case's
initial Q. No initial-Q offsets are fitted to the supplied references.

Uncontrolled initial P/Q and inactive-model playback attempts are retained as
rejected diagnostics. They are excluded from the numerical claims above.
Frozen inputs, runtimes, producers, raw traces, comparison reports, and test
logs are retained under the private verification-artifact directory
`2026-10-18-fleet-localization`. No full ten-second event is accepted.

## Retained converter commands, October 18 follow-up

The zero-Q probe now retains bus 1011's original zero reactive-power limits.
The tiny positive Q still arises in the commercial linear startup solve.
Explicitly restoring the declared zero Q after that solve isolates the
component's exact-zero branch; two fresh processes reproduce its raw output
exactly. This restoration is confined to the component test. No such Q offset
or reset is applied to the Texas2k cases.

An independent recurrence audit identifies a command-staging difference.
The converter consumes a command formed from the advanced electrical-control
states and the preceding step's proportional Q inputs. Refreshing those
inputs before the converter derivative is evaluated changes the trajectory.
Across 2,001 intervals, the retained-input recurrence agrees within
`0.00000000542` pu, compared with `0.000006134` pu for simultaneous input
refresh. The opt-in two-step path now retains the completed control command
through zero-duration network refreshes. Its model parameters and initial
physical operating point are unchanged.

Prescribed-voltage component comparisons retain each commercial probe's
initial P/Q. For exact-zero Q, maximum converter reactive-current error falls
from `0.000119838` to `0.00000004843` pu. The reactive and voltage PI state
errors fall from `0.000016928/0.000014742` to
`0.0000001193/0.00000000559` pu. The tiny-positive-Q profile also agrees within
`0.00000001491` pu at the reactive-current state. The checked wind profile and
the complete coupled wind/synchronous-machine trace remain unchanged.
Fifteen focused reactor tests pass, including the new command-retention check.
An earlier test attempt failed to compile because its test constructor omitted
an argument; that setup error is retained separately.

The unchanged Case 2 run completes through `1.2` seconds with the correction,
including fault application and clearing. All 218 initial diagnostic fields
are exactly unchanged. Worst prefault voltage/P/Q NRMSE is now
`0.00197972/0.00596826/0.00714784`; maximum rotor-angle error is `0.416129`
degrees. These are worse than the previous full-network metrics, despite
the independently improved component agreement. The full-network discrepancy
remains unresolved; the component correction is not an acceptance result.

Separately forcing positive or negative recovery selection for the 17 zero-Q
units also fails the prefault requirements. Those are sensitivity experiments,
not verified reference preparation rules. Solving power flow before netting the
two unmodeled generators changes maximum initial rotor-angle error only from
`0.00320180` to `0.00318654` degrees and does not close the initialization gap.
Neither experiment changes the default case preparation.

Frozen evidence for this follow-up is retained under the private
verification-artifact directory `2026-10-18-converter-command-staging`.
No full ten-second event is accepted.

## Initialization discriminators, October 18 follow-up

Independent GENROU initialization at all 26 monitored machines uses only each
commercial reference's terminal P/Q and voltage magnitude. Maximum errors in
the four electrical states are below `0.000000445` pu; maximum relative rotor
orientation error is `0.000033920` degrees. No reference rotor states or bus
angles are injected. This supports initialization at a given physical
operating point; it does not establish the full network's operating point.

A two-bus converted-network startup prototype agrees with the commercial
startup solve within `0.000000127` pu in voltage and `0.000000762` degrees in
bus angle. Two fresh commercial processes reproduce the captured operating
points exactly. Applying that procedure to the original Case 2 RAW increases
maximum initial rotor-angle error across the 26 monitors from `0.00320180` to
`0.00327459` degrees. Using the RAW's recorded Newton tolerance instead gives
`0.00318620` degrees and leaves a larger discarded network-current residual.
Neither preparation discriminator meets the initialization requirement. The
strict power-flow preparation remains in use.

## Current-limit staging and dip history, October 18 follow-up

The frozen one-second fleet observation identifies 11 units crossing the high
voltage threshold and 15 reaching the circular current limit. All 96 active
plant controllers use the same selector combination. Two additional commercial
component replays retain the original Case 2 parameters and initial operating
points at buses 1026 and 1033. Each prescribed voltage profile is constructed
from the frozen fleet observation; each commercial replay repeats exactly in
two fresh processes. These are component discriminators, not independent
full-network acceptance runs.

The checks isolate three defects in the opt-in two-step path. The converter's
active command must combine the newly advanced power order and voltage filter
with the current limit computed before those states advance. The linked Q
integrator must remain frozen when the downstream voltage PI wants to move
farther into saturation, including when its limited derivative is already zero.
Finally, zero-duration network refreshes must not consume a dip transition or
start its post-dip hold timer. Exposed commercial timer variables confirm the
transition timing; observing them leaves every original output sample unchanged.

With these corrections, maximum active-current error falls from `0.303491` to
`0.00000146` pu in the current-limit replay, and from `0.0189640` to
`0.000000387` pu in the high-voltage replay. The checked Q-integral traces agree
exactly. Previous wind and solar component traces and the complete coupled
wind/synchronous-machine trace remain byte-identical. All three new regression
checks fail against the previous frozen runtime and pass with the corrections.
The focused reactor checks pass 18 tests; a separate compatibility run passes
59 additional tests. This is not a full registered-suite pass. The first focused
attempt encountered a floating-point boundary in the test's nominally zero
current limit; the corrected test drives the limit decisively into saturation.

The unchanged Case 2 run completes through `1.2` seconds, with all 218 initial
diagnostic fields exactly unchanged. Worst prefault voltage/P/Q NRMSE improves
to `0.00118173/0.00224012/0.00331266`; maximum rotor-angle error falls to
`0.211550` degrees. Four of 20 prefault criteria pass. The remaining requirements
still fail, and the run does not cover the ten-second acceptance window.

Frozen evidence for these follow-ups is retained under the private
verification-artifact directory `2026-10-18-current-limit-staging`.
No full ten-second event is accepted.

## First complete ten-second window and exciter localization

The frozen corrected runtime completes Case 2's first bus fault through ten
seconds. All 2,405 samples and 218 diagnostic fields through `1.2` seconds
reproduce the preceding run exactly. Every required comparison channel is
present and every period is complete. This event passes 6 of 40 channel
criteria and fails acceptance.

| Quantity | Worst prefault result | Worst fault/postfault or full-window result |
| --- | ---: | ---: |
| Voltage NRMSE | 0.00118173 | 0.00861415 |
| Terminal P NRMSE | 0.00224012 | 0.0201838 |
| Terminal Q NRMSE | 0.00331266 | 0.0985075 |
| Rotor angle | 0.211550 degrees maximum error | 19.0749 degrees ten-second RMSE |

The two voltage/power periods retain the full ten-second reference range as
their denominator. Fault application, including the fault, begins the relaxed
period. Rotor angle uses the confirmed absolute maximum-error and RMSE limits
in degrees. No offset or tolerance is changed. The complete project scope
remains six cases and 36 disturbances; no event has passed the full contract.

An independent prefault exciter replay now covers all 26 monitored generators.
It supplies recorded terminal voltage, field current, speed, current, and
stabilizer output, and uses the frozen solver's actual controller-state
bindings. Reference exciter states are not injected. Sixteen ESST4B units and
one IEEET1 unit agree within `0.0000123` pu in field voltage. The nine units
using the shared ST1 implementation differ by up to `0.00642330` pu. This
localizes a controller discrepancy without establishing its cause.

A separate two-bus commercial voltage playback retains bus 1078's GENROU and
EXST1 parameters and omits the stabilizer. Replaying its recorded physical
inputs through the same controller integration gives maximum field-voltage
error `0.00817420` pu. A second fresh commercial process adds observations of
the constants, compensated voltage, and voltage reference; all original
channels remain exactly unchanged. The constants retain the declared values
to native storage precision, compensated voltage equals terminal voltage,
and the voltage reference is constant. Simple input-lag alternatives do not
establish a correction. No ST1 production change is made by this follow-up.

The first replay helper failed compilation because its evaluation method used
a narrower controller type than the loader returns. A first equation audit
also keyed parameters by bus alone and encountered another unit at a
multiple-generator bus; its corrected version uses bus and unit ID. Both
incomplete attempts are retained separately from the completed evidence.

Frozen evidence is retained under the private verification-artifact directory
`2026-10-18-first-full-window-and-st1-localization`.

## Disconnected-machine reporting

All three supplied Case 2 generator-trip references set seven machine channels
to exactly zero for all 18,001 disconnected samples: angle, speed, field voltage,
mechanical power, terminal voltage, terminal P, and terminal Q. Internal rotor
states remain nonzero and can continue changing. The comparison runner now
reports zero for disconnected-machine channels while preserving the engine's
retained states. Connected-machine and bus observations are unchanged. Optional
diagnostic-generator observations also fall back to the original scoring subset
when that extra observation list is absent.

The new regression fails against the previous frozen runner and passes with
the correction. A focused reactor run passes its one reporting test. Its first
attempt stopped because an upstream module contained no matching specified test;
the corrected command permits that upstream condition and confirms the test
actually runs in the integration module. No new full-suite result is claimed.

An independent ten-second angle/speed audit supports the supplied angle
convention. The largest one-step angle residual is `0.000217` degrees; nearly all
increments agree within two native float32 units. Pairwise angle discrepancies
also remain, so an angle offset is not an accepted remedy. Absolute-angle
acceptance and all thresholds are unchanged.

The first expanded event batch stopped at initialization because five plans
lacked the extra observation list. The corrected second batch was deliberately
stopped after identifying the disconnected-channel reporting issue. Failed and
interrupted outputs are retained as diagnostics, not completed event results.
The next batch uses a fresh frozen runner and the original model parameters.

Frozen evidence is retained under the private verification-artifact directory
`2026-10-18-disconnected-machine-reporting`.

## Six complete Case 2 disturbances and EXST1 lower limits

The frozen runtime with corrected disconnected-machine reporting completes all
six original Case 2 disturbances through ten seconds. Every required channel
and comparison period is complete. Eighteen of 240 channel criteria pass; none
of the six events passes the full contract. The first bus-fault trace reproduces
the preceding complete ten-second trace exactly.

| Case 2 disturbance | Criteria passed | Fault/postfault voltage NRMSE | Terminal P NRMSE | Terminal Q NRMSE | Full-window rotor-angle RMSE, degrees |
| --- | ---: | ---: | ---: | ---: | ---: |
| Bus fault 1 | 6/40 | 0.00861415 | 0.0201838 | 0.0985075 | 19.0749 |
| Generator trip 1 | 3/40 | 0.0383526 | 0.0348342 | 0.0691809 | 21.5406 |
| Bus fault 2 | 2/40 | 0.0199602 | 0.0367603 | 0.0235084 | 13.7107 |
| Generator trip 2 | 4/40 | 0.0374497 | 0.0250785 | 0.0378127 | 18.8921 |
| Bus fault 3 | 1/40 | 0.00904878 | 0.0234430 | 0.0256851 | 13.1331 |
| Generator trip 3 | 2/40 | 0.0356466 | 0.0417326 | 0.0394875 | 19.2217 |

Each table entry is the worst channel in its quantity and period. Voltage and
terminal P/Q retain the full ten-second reference range as denominator.
Prefault rotor-angle maximum errors range from `0.204012` to `1.04970` degrees,
exceeding the `0.002`-degree limit in every event. No acceptance threshold,
initial state, reference signal, time alignment, or angle offset is fitted.
These six runs precede the separate lower-limit correction below. The project
scope remains all six cases and 36 disturbances.

The commercial controller model library distinguishes the lower field-voltage
bound of EXST1, `Vt * Vrmin - Kc * Ifd`, from ESST1A, `Vt * Vrmin`. The shared
implementation previously used the latter for both source models. Importing an
EXST1 record now retains its limit convention in the controller data; both
positive and negative field currents keep their signs. The existing ESST1A
convention is unchanged.

A sustained two-bus voltage playback activates the lower bound using the
original Case 2 generator and EXST1 parameters. Two fresh commercial processes
produce exactly identical raw traces. In 580 saturated samples, the corrected
output equation reduces maximum field-voltage error from `0.883235` to
`0.000000525` pu. This check uses the recorded physical inputs at the controller's
publication stage and independently evolved controller states. It is a lower-bound
check, not a claim that the complete EXST1 trajectory agrees. No reference
controller state is injected.

The new source-model regression fails with the previous bound and passes with
the correction. Twenty-eight focused tests pass in both an isolated frozen
JUnit run and the reactor. The initial reactor command stopped during argument
parsing; the next attempt encountered an unfinished unrelated wind-model test
source. Both failed attempts are retained. The later reactor run completes and
executes all 28 tests. The corrected frozen Case 2 prefix reproduces all 2,405
samples and 218 diagnostic quantities through `1.2` seconds exactly, confirming
that this bound correction does not change the unresolved startup discrepancy.
An additional compatibility reactor run passes 27 tests covering the parser,
staggered solver, and independent and secondary ST1 comparisons. These are 55
distinct focused/compatibility tests, not a full registered-suite pass. The new
regression is included in the registered core plugin suite.

Other ST1 findings remain diagnostic. The two ESST1A regulator-state channels
can differ from the machine field-voltage channel by `0.00641584` and
`0.000827789` pu, so controller states and published machine signals must be
checked separately. A declared unity-leadlag EXST1 experiment and an IEEET1
control with the same regulator gain and pole show different numerical update
behavior. The existing two-step rule matches the IEEET1 control within
`0.000000075` pu; none of the canonical EXST1 update alternatives tested so far
establishes a replacement. Both control experiments repeat exactly in fresh
commercial processes. No ST1 integration or field-publication correction is
adopted from these alternatives.

Frozen runs, failed attempts, source snapshots, and numerical checks are retained
under the private verification-artifact directory
`2026-10-18-case2-six-events-and-exst1-limits`. No ten-second event is accepted.

## ST1 local integration and field publication

Read-only observations of the commercial model-writing interface expose the
state, derivative, and retained integration-history values. Every state read is
cross-checked against the normal state API. All original channel samples remain
exactly unchanged when these observations are added. A second fresh process
reproduces the controlled EXST1 history observations exactly.

The observations distinguish ST1's ten internal steps per network step from the
common network-step rule used by the IEEET1 control. With the existing
discontinuity-history averaging rule, ten local steps reproduce the independent
EXST1 reference-step endpoint within `0.000000167` pu. The original Case 2
voltage playback, with all controller parameters unchanged, reduces maximum
field-voltage error from `0.00817420` to `0.0000125` pu. Replaying all nine
observed ST1 units through the production solver's actual local path gives a
maximum error of `0.0000539` pu. Only physical boundary signals and initial
field voltage are supplied; reference controller states are not injected.

The opt-in staggered solver now advances ST1 states on those local steps while
holding machine, network, and stabilizer inputs at the preceding network
endpoint. It publishes field voltage before refreshing the last local
algebraic input. This preserves the observed distinction between a zero-Ta
regulator's internal value and the field voltage supplied to the machine.
Machine states and other controller families retain their common network-step
rule. The default simulation method is unchanged.

The completed Case 2 prefix retains exactly the same 218 startup quantities and
covers `1.2` seconds. Thirteen of its 20 prefault criteria pass, compared with
four previously. All five voltage channels and all five terminal P channels
pass. Two Q channels and all five rotor-angle channels still fail.

| First-event acceptance quantity | Previous worst prefault result | Corrected worst prefault result | Limit |
| --- | ---: | ---: | ---: |
| Voltage NRMSE | 0.00118173 | 0.000546972 | 0.001 |
| Terminal P NRMSE | 0.00224012 | 0.000829732 | 0.001 |
| Terminal Q NRMSE | 0.00331266 | 0.00148035 | 0.001 |
| Rotor-angle maximum error, degrees | 0.211550 | 0.115433 | 0.002 |

The table uses the original first event's acceptance channels and unchanged
full-ten-second reference ranges. Across all 26 diagnostic machines, maximum
prefault angle error remains `0.356432` degrees. This prefix does not establish
ten-second acceptance or acceptance of the other disturbances.

Twenty focused tests pass, including three new integration/publication and
lifecycle regressions. All three fail their intended assertions against the
previous solver and pass with the correction. A separate negative control
confirms upper-bound priority when a severe voltage dip causes output bounds
to cross. The new regression class is registered in the core plugin suite;
no full registered-suite pass is claimed. The extended IEEE39 exporter remains
excluded by the normal test configuration and is not counted as validation.

An early prefix completed but its strict startup check detected two field
observations differing by at most `0.000000000000014` pu. Restoring the original
initial field refresh preserves every startup observation exactly. A first
ten-second attempt was deliberately interrupted while correcting the new
output path's crossed-bound priority; its partial output is retained and is
not scored as a completed event. A fresh ten-second run uses the corrected,
frozen runtime. No parameter, reference, angle offset, or tolerance is fitted.

Frozen evidence and rejected attempts are retained under the private
verification-artifact directory `2026-10-18-st1-local-integration`. The required
scope remains six cases and 36 ten-second disturbances; no event is accepted.

## Corrected ten-second window and coupled stabilizer check

The corrected first Case 2 bus-fault run completes all ten seconds. Its first
2,405 observations reproduce the final ST1 prefix exactly, including all 218
diagnostic quantities. All required acceptance samples and channels are present.
Fifteen of 40 criteria pass, compared with six before the ST1 correction. The
run still fails acceptance.

| First-event quantity | Corrected worst result | Limit | Passing channels |
| --- | ---: | ---: | ---: |
| Prefault voltage NRMSE | 0.000546972 | 0.001 | 5/5 |
| Prefault terminal P NRMSE | 0.000829732 | 0.001 | 5/5 |
| Prefault terminal Q NRMSE | 0.00148035 | 0.001 | 3/5 |
| Prefault rotor-angle maximum error, degrees | 0.115433 | 0.002 | 0/5 |
| Fault and postfault voltage NRMSE | 0.00798972 | 0.005 | 2/5 |
| Fault and postfault terminal P NRMSE | 0.0181861 | 0.005 | 0/5 |
| Fault and postfault terminal Q NRMSE | 0.0998041 | 0.005 | 0/5 |
| Full-window rotor-angle RMSE, degrees | 24.4420 | 0.01 | 0/5 |

The worst full-window rotor-angle RMSE increases from `19.0749` to `24.4420`
degrees. The correction's component evidence and improved prefault results
therefore leave a substantial unresolved full-system discrepancy. The remaining
five Case 2 disturbances are being evaluated with this exact frozen runtime;
their in-flight trajectories are not scored or presented as complete results.

A new independent two-bus experiment retains bus 1078's original GENROU, EXST1,
and PSS2A parameters and applies a declared external voltage waveform. Two
fresh commercial processes reproduce all 2,003 output rows exactly. Replaying
the recorded physical speed and electrical-power inputs through the production
stabilizer gives maximum output error `0.0000000640` pu with the existing common
network-step recurrence. Ten stabilizer substeps increase that error to
`0.0000779` pu; the stabilizer's integration rule is unchanged.

The production solver also runs the complete machine, exciter, and stabilizer
together against the external voltage source. Over this one-second experiment,
maximum rotor-angle error is `0.000297` degrees, stabilizer-output error is
`0.000000719` pu, and field-voltage error is `0.000114` pu. Maximum terminal
voltage difference is `0.00000670` pu. A companion run without the stabilizer
has maximum rotor-angle error `0.000325` degrees. The source waveform uses the
declared physical time points; no reference machine or controller states are
injected. These coupled checks cover one original unit and narrow the next
investigation toward the other fleet components and network interactions.

Completed trajectories, the failed first compilation of the diagnostic helper,
frozen binaries, source snapshots, and exact comparison results are retained
under the private verification-artifact directory
`2026-10-18-st1-full-window-and-coupled-pss`. The original six-case, 36-event
contract, I/Z load assumption, normalization ranges, and angle limits remain
unchanged. No event is accepted.

## Low-voltage constant-current load discrepancy

A separate two-bus experiment isolates the static I/Z load characteristic using
a declared source-voltage sweep. A 50 MW, 20 Mvar load is converted to
constant-current P and constant-impedance Q. The current implementation applies
a sine reduction to the constant-current term below its shared `0.65` pu switch
voltage. The commercial reference instead retains constant current down to
`0.5` pu and follows an elliptic characteristic below that point.

The primary manual describes that transition graphically without printing its
equation. A scalar candidate recovered from separate native observations is
`I/I0 = sqrt(1 + 12*V*(1 - V)) - 1` below `0.5` pu, and unity above it. Independent
dense voltage sweeps with positive and negative active loads each contain
2,003 samples, including 941 below `0.5` pu. Each sweep repeats exactly in a
second fresh commercial process. Maximum active-power difference for this
candidate is `0.0000000341` pu on the 100 MVA system base. The existing sine
characteristic differs by up to `0.0323068` pu, or `3.231` MW for this load.
Constant-impedance Q agrees within `0.0000000207` pu.

These experiments isolate a network-load discrepancy while preserving the
confirmed I/Z assumption. The production correction and fleet check follow
below. Frozen producers, raw outputs,
manual-page provenance, cold-repeat checks, and comparison programs are retained
under the private verification-artifact directory
`2026-10-18-static-current-voltage-characteristic`. Full-system acceptance
remains unproven.

## Elliptic current-load implementation and fleet check

Core now exposes a typed, per-network constant-current voltage curve. Its
general default remains the existing sine characteristic. The reference
simulation adapter selects the verified elliptic characteristic before dynamic
load initialization, including reinitialization and runs with externally
supplied current injections. Constant-power and constant-impedance terms keep
their existing characteristics. The elliptic expression is evaluated in
rationalized form to preserve accuracy near zero voltage.

The production power and Norton-current paths agree with all 12,018 recorded
observations from the six cold-process sweeps within `0.0000000341` pu for P
and `0.0000000207` pu for Q. A public regression fixture contains 204 points
selected at fixed sample indices independently of their errors. All 2,953 core
tests and 21 focused plugin tests pass. Frozen negative controls confirm that
the preceding load calculation fails the native power assertion and the
preceding reference adapter fails the curve-selection assertion; all five
new tests pass with the correction. The adapter regression is registered in
the core plugin suite; no full registered-plugin-suite pass is claimed.

The completed first Case 2 prefix covers `1.2` seconds. Every one of its first
2,003 observations, including startup history and all prefault diagnostic
quantities, is exactly unchanged. Thirteen of 20 prefault criteria still pass.
For the 402 matched fault-and-clearing samples from fault application through
`1.2` seconds, the diagnostic errors are:

| Worst channel quantity over the short fault segment | Before | Elliptic current curve |
| --- | ---: | ---: |
| Voltage NRMSE | 0.00277852 | 0.00286267 |
| Terminal P NRMSE | 0.0180521 | 0.0191282 |
| Terminal Q NRMSE | 0.0156181 | 0.0153606 |
| Rotor-angle RMSE, degrees | 0.133198 | 0.148022 |

The NRMSE denominators remain the full ten-second reference ranges. Q improves
slightly in this segment; voltage, P, and angle errors increase slightly. The
isolated load-equation correction leaves other full-system discrepancies
unresolved. A fresh ten-second run uses the exact frozen runtime and unchanged
inputs. Its in-flight output is not scored or presented as a completed event.

The preceding ST1-only six-event batch completed the first generator trip
before its driver stopped on an overly broad startup assertion: different
events monitor different bus sets. All 208 shared machine quantities and
their first-second times match exactly. The corrected audit reports differing
bus-monitor columns explicitly, and the resumed batch reuses both completed
events. Acceptance scoring still requires each event's complete original
channel set.

Frozen code, raw component observations, negative controls, complete test
reports, the corrected prefix, and the batch-audit failure are retained under
the private verification-artifact directory
`2026-10-18-elliptic-current-load-correction`. The required scope remains all
six cases and 36 ten-second events. No event is accepted.
