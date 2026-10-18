# Texas2k supplied-reference comparison: work in progress

## Acceptance contract

The supplied corpus contains 36 ten-second disturbances across six cases, with
five bus-voltage magnitudes and five generator rotor angles per disturbance.
The user's angle clarification selects rotor angles; bus-voltage angles are
absent from these references. Every requested channel must satisfy
`RMSE / (reference maximum - reference minimum) < 0.001`.
This target has **not** been achieved. Partial or failed trajectories cannot pass.

The local comparison producer retains input and reference SHA-256 hashes,
freezes compiled runtime jars, preserves event-pre/event-post samples, and
validates the native float32 clock before mapping samples by integration-step
count. It does not fit parameters, initial angles, offsets, or trajectories.
Simulator-driving programs and full numerical evidence remain in the ignored
private verification archive, following repository policy.

## Rotor-angle reference

PSS/E 36.7 `SET_RELANG` supports absolute synchronous-frame angles with
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

The authoritative installed API page is PSS/E 36.7
`psspy/_as_gen/psspy.set_relang.html`, SHA-256
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
  The correction is independently reproduced by native PSS/E on a public
  two-machine fixture, including both limit directions. Zero-power sharing also
  counts active machines only. The full core regression passes **2,945 tests**,
  with no failures, errors or skips.

## Native wind-speed discrepancy

A native PSS/E 36.7 two-bus discriminator uses the unchanged Case 4 Bus 2089
REGCA1/REECA1/REPCA1/WTTQA1 parameter stack. PFLAG=1 retains the generator-speed
multiplier even though WTTQA1 already forms `Pref = torque * speed`.
For `P0=0.625`, `speed0=0.8775`, and `Tpord=0.03`, native PSS/E reports
`dPord/dt = -2.5521`, matching `(0.625 * 0.8775 - 0.625) / 0.03`.
Adding WTDTA1 retains this startup derivative; changing only PFLAG to zero
removes it. This is a genuine non-equilibrium initial condition in the supplied
profile, not a signal to fit a gain or suppress the derivative.

The native DYR importer now explicitly selects that speed convention.
Typed clients retain the existing power-reference convention, and the ANDES
comparison fixture explicitly selects its existing convention. No comparison
tolerance is relaxed. Focused REECA1, wind-control and cross-tool tests pass.
The PSS/E Model Library 36.7 REECA1/WTTQA1 diagrams and native outputs support
this distinction; it is not a claim of complete wind-model conformance.

In first-20-ms diagnostics, maximum bus-voltage error falls from 0.01655 to
0.000947 pu in Case 4 and from 0.006732 to 0.0000953 pu in Case 5. These short
errors are diagnostics, not full-window NRMSE acceptance.

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
quantity. They remain diagnostic variants. Load conversion is currently
assumed constant impedance for both P and Q; the reference CONL settings have
not been confirmed.

The registered plugin regression was executed with a bounded 3-GB test heap.
It exposes failures in the old Texas2k no-event stationarity assertion and
SVSMO1T2 comparison. The stationarity premise conflicts with the independently
observed native wind startup. Both failures are retained and must be addressed;
the full registered gate is not reported as passing. An earlier smaller-heap
attempt exhausted the test heap and is not an accuracy result.

The installed native PSS/E Xplore accepts only 50 buses. Its Texas2k load probe
reports just 50 loaded buses despite a zero read error, so it cannot regenerate
the complete 2,000-bus references. Small native discriminators are supported;
full-case comparisons use the supplied immutable outputs.
