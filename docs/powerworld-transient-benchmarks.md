# Repeatable PowerWorld transient benchmarks

`powerworld_transient_benchmark.py` turns a declarative JSON specification into
a complete PowerWorld AUX run. It uses the documented AUX command interface
directly and therefore does not require ESA or the SimAuto COM server.

Each specification selects one PSS/E RAW/DYR pair, an ordered transient
contingency, the integration interval, and arbitrary PowerWorld object/field
selectors. This makes the same runner usable for machines, exciters,
governors, stabilizers, renewable controllers, and network channels.

## Run a benchmark

From the repository root on a Windows host with PowerWorld installed:

```powershell
python ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py `
  ipss.test.plugin.core/testData/reference/powerworld/specs/type3-wind-bus1062.json `
  --powerworld-exe "C:\Program Files\PowerWorld\Simulator Education-Evalution 24\pwrworld.exe"
```

Set `POWERWORLD_EXE` to omit the command-line executable argument. Use
`--generate-only` on hosts without PowerWorld to validate the specification and
inspect the generated AUX file.

Pass the specifications directory to regenerate the entire checked-in suite in
stable filename order:

```powershell
python ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py `
  ipss.test.plugin.core/testData/reference/powerworld/specs `
  --powerworld-exe "C:\Program Files\PowerWorld\Simulator Education-Evalution 24\pwrworld.exe" `
  --publish ipss.test.plugin.core/testData/reference/powerworld
```

`--publish` always names the suite root. This is identical for a single JSON
specification and a specification directory; the runner creates the model-name
subdirectory itself.

The default output is `target/powerworld-benchmarks/<benchmark-name>/`:

- `run.aux` is the exact executable PowerWorld script;
- `progress.txt` proves each required run phase completed;
- `powerworld.csv` contains the requested time-series channels;
- `powerworld.log` preserves import, validation, correction, and solve messages;
- `powerworld-corrected.dyr` is written only when the specification explicitly
  enables `auto_correct`;
- `manifest.json` records the specification, input and artifact SHA-256 hashes,
  event definition, timestep, result selectors, executable identity/hash, and
  completion status.

Text hashes canonicalize CRLF and LF line endings so a reference remains valid
after either a Windows or Unix checkout; executable hashes remain byte-exact.

PowerWorld may emit two samples at an event time: the value immediately before
the event and the value immediately after it. A comparison must preserve that
distinction or explicitly align InterPSS's end-of-step event convention.

## Add another model

Copy a specification under
`ipss.test.plugin.core/testData/reference/powerworld/specs/`, change the RAW/DYR
inputs, and list the desired channels. PowerWorld accepts generic dynamic-state
selectors such as:

```text
GEN 1 '1'|TSGenMachineState:2
GEN 1 '1'|TSExciterState:1
GEN 1 '1'|TSGovernorState:1
GEN 1 '1'|TSStabilizerState:1
```

Generator `TSGenP` and `TSGenQ` selectors are exported by PowerWorld as `TSMW`
and `TSMvar`. The checked-in renewable reference also demonstrates REGCA1,
REECA1, and Type-3 drive-train state selectors, while the conventional
reference identifies GENROU angle/speed/flux states and ESST4B states by their
PowerWorld column headers.

For synchronous-machine rotor comparisons, request the generic
`TSRotorAngle` and `TSSpeed` fields for both the studied generator and the
reference generator. Do not substitute `TSGenMachineState:1/2`: PowerWorld can
emit metadata for those selectors while returning constant or even absent
trajectory values. Compare relative angle and relative speed across the two
generators so the result is independent of PowerWorld's configured angle
reference. The runner validates every trajectory row width as well as the
metadata count to catch silently omitted result columns.

Keep automatic correction disabled unless the benchmark intentionally targets
PowerWorld's documented corrected interpretation. If enabled, declare expected
log text so an unexpected or missing correction fails the run.

The initial suite deliberately covers both renewable and conventional stacks:

- `type3-wind-bus1062.json`: REGCA1/REECA1/REPCA1 plus Type-3 wind controls;
- `smib-gensal.json`: the core GENSAL synchronous machine;
- `smib-genrou-esst1a.json` and `smib-genrou-esst4b.json`: GENROU with two
  static-exciter families;
- `smib-genrou-ieeet1.json`: GENROU with the legacy rotating IEEE exciter;
- `smib-genrou-ieeeg1.json`: GENROU with the steam-turbine governor chain;
- `smib-genrou-hygov.json`: GENROU with the hydro governor/water column.
- `smib-genrou-ggov1.json`: GENROU with the ten-state GE general governor,
  turbine, load-limiter, acceleration, and temperature-control chain.
- `smib-genrou-esst1a-pss2a.json`: GENROU and ESST1A with a representative
  Texas2k dual-input PSS2A stabilizer.

This is the required contract for each newly implemented model, not an optional
one-off check. Add a specification and publish its immutable PowerWorld output
alongside the model slice; then add a Java trajectory test that maps named
PowerWorld channels to InterPSS signals and enforces per-channel tolerances. A
model is only marked cross-tool verified after all three pieces exist: the JSON
specification, the hashed reference artifact, and the registered InterPSS
comparison test. ANDES remains a second oracle where it implements an
equation-equivalent model.

Every published trace contains both boundary channels (bus voltage and
generator MW/Mvar) and named internal model states. The test suite verifies the
input and artifact hashes, finite trajectories, duplicate pre/post-event
samples, a material fault response, and voltage recovery. A model-specific
InterPSS comparison should add semantic state mapping and tolerances rather than
assuming that state indices or speed/angle normalizations match across tools.

Use a model-appropriate integration step in the specification. The synchronous
machine-only GENSAL contract uses `1/240 s`; the faster ESST1A regulator contract
uses `0.5 ms`, matching its independent ANDES comparison. A reduced-step check
is required before attributing a fast controller-state discrepancy to model
equations. For GENROU, PowerWorld's `PsiQpp` state maps to InterPSS `Psikq`, not
the derived network-interface flux returned by `getPsiq11()`.

For ESST4B, PowerWorld initializes a zero-`Kim` inner PI as a pure proportional
path and freezes the integrator while the total PI output is saturated. The
generic InterPSS PI block's back-calculation state is therefore not an
equivalent implementation. The ESST4B contract uses a model-specific freeze
non-windup PI block and directly checks the sensed-voltage, regulator-delay,
outer-regulator, and machine-flux channels. ANDES uses a different stored-bias
PI realization, so its ESST4B comparison is retained for boundary and common
observable outputs rather than solver-specific PI state coordinates.

For the Type-3 renewable contract, the official PowerWorld/WECC REEC_A signal
order is normative: with `QFLAG=1,VFLAG=1`, PIQ stores an absolute voltage
reference and the following summing junction subtracts filtered terminal
voltage before PIV. The direct 0.5 ms contract checks 15 active boundary and
internal signals. Its current maximum errors are `6.43e-5 pu` bus voltage,
`0.079 MW`, `0.051 Mvar`, `7.73e-4 pu` REGCA1 current state, `3.00e-6 pu` PIQ,
`3.01e-4 pu` PIV, and `3.24e-4 pu` normalized generator speed. PowerWorld
REECA1 State 5 is excluded because it is the inactive `QFLAG=0`/`Tiq` path in
this `QFLAG=1` benchmark. ANDES 2.0 uses a zero-based PIQ and omits the
terminal-voltage subtraction for `VFLAG=1`; its trace remains a bounded
secondary comparison, not an equation-equivalent REECA1 oracle.

For GGOV1, PowerWorld's numbered result channels represent the named diagram
signals, which are not always the raw numerical integration coordinates.
`Governor Differential Control`, `Accel Control`, and `Temp Detection LL` map
to the corresponding block outputs exposed by InterPSS. The 0.5 ms contract
checks both bus voltages, generator MW/Mvar, relative rotor angle/speed, all
four GENROU electrical states, and all ten GGOV1 signals. ANDES does not
implement GGOV1, so this PowerWorld artifact is the independent model oracle.

For PSS2A, the artifact exports every one of PowerWorld's 19 named stabilizer
state slots. The registered comparison maps both washout/transducer chains,
both output lead-lags, the final active M-by-N ramp-filter stage, and the
optional GE lead-lag/output, together with the GENROU and ESST1A boundary
signals. Nested child-controller signals must be read from the initialized
child CML evaluator; querying a nested expression through the parent evaluator
collapses it to the child controller's final output. ANDES does not implement
PSS2A, so PowerWorld is the independent model oracle.

Run the framework's dependency-free tests with:

```powershell
python ipss.test.plugin.core/src/test/python/test_powerworld_transient_benchmark.py
```

The generated PowerWorld trace is an independent reference artifact, not an
automatic InterPSS acceptance result. Match event timing and channel semantics,
then apply per-channel absolute tolerances in the corresponding InterPSS test.
