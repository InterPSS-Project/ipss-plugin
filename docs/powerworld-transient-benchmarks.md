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

Run the framework's dependency-free tests with:

```powershell
python ipss.test.plugin.core/src/test/python/test_powerworld_transient_benchmark.py
```

The generated PowerWorld trace is an independent reference artifact, not an
automatic InterPSS acceptance result. Match event timing and channel semantics,
then apply per-channel absolute tolerances in the corresponding InterPSS test.
