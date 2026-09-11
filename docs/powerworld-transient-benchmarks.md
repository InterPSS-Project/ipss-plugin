# Repeatable PowerWorld transient benchmarks

`powerworld_transient_benchmark.py` turns a declarative JSON specification into
a complete PowerWorld AUX run. It uses the documented AUX command interface
directly and therefore does not require ESA or the SimAuto COM server.

Each specification selects either a PSS/E RAW case or a complete PowerWorld
AUX network, one PSS/E DYR file, an ordered transient contingency, the
integration interval, and arbitrary PowerWorld object/field selectors. RAW and
AUX network inputs are mutually exclusive; dynamic `.dyd` data is never mixed
into this PSS/E DYR workflow. This makes the same runner usable for machines, exciters,
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
- `reeca-active-path-weak-grid.json`: the same public Bus-1062 renewable
  controller profile on a weak grid, exercised by a tiny finite-impedance pulse
  to discriminate the REECA1 active-power/current path;
- `smib-gensal.json`: the core GENSAL synchronous machine;
- `smib-genrou-esst1a.json` and `smib-genrou-esst4b.json`: GENROU with two
  static-exciter families;
- `smib-genrou-ieeet1.json`: GENROU with the legacy rotating IEEE exciter;
- `smib-genrou-ieeeg1.json`: GENROU with the steam-turbine governor chain;
- `smib-genrou-tgov1.json`: GENROU with the native two-state TGOV1 valve and
  turbine-power chain;
- `smib-genrou-tgov1d.json`: the corresponding native TGOV1D record with an
  asymmetric speed deadband and explicit turbine rating;
- `smib-genrou-gastd.json`: GENROU with native GASTD fuel-valve, fuel-flow,
  and exhaust-temperature dynamics;
- `smib-genrou-gast2ad.json`: GENROU with the native GAST2AD speed,
  temperature-control, transport-delay, valve, fuel, and turbine chain;
- `smib-genrou-gastwdd.json`: GENROU with native GASTWDD power transducer,
  PID, temperature-control, valve, fuel, and turbine dynamics;
- `smib-genrou-degov1d.json`: GENROU with the native DEGOV1D electric-control
  box, actuator, electric-power droop transducer, and engine delay;
- `smib-genrou-tgov3d.json`: GENROU with the native TGOV3D lead-lag, bounded
  valve, steam-bowl, reheater, crossover, and intercept-valve chain;
- `smib-genrou-hygov.json`: GENROU with the hydro governor/water column.
- `smib-genrou-ggov1.json`: GENROU with the ten-state GE general governor,
  turbine, load-limiter, acceleration, and temperature-control chain.
- `smib-genrou-ggov1d.json`: the same ten-state chain through native GGOV1D,
  with a small asymmetric frequency deadband exercising the D-specific path.
- `smib-genrou-ieeeg3d.json`: GENROU with the native IEEEG3D hydro governor,
  including asymmetric frequency deadband and turbine-base conversion.
- `smib-genrou-ieeeg3.json`: GENROU with native IEEEG3, the PSS/E conversion
  target for the approved WECC HYGOV4 model.
- `smib-genrou-esst1a-pss2a.json`: GENROU and ESST1A with a representative
  Texas2k dual-input PSS2A stabilizer.
- `regfma1-bus1062.json`: a public all-line three-bus system with the Texas2k
  REGFMA1 parameter profile and all nine named PowerWorld machine states.
- `smib-genrou-ac7c.json` through `smib-genrou-ac11c.json` (excluding the
  PSLF-only AC10C): native PSS/E AC exciters, the reused core GENROU, and every
  named PowerWorld exciter-state channel.
- `smib-genrou-dc1c.json` and `smib-genrou-dc2c.json`: the native revision-C
  commutator exciters, reused core GENROU, and all five named PowerWorld
  controller channels. The paired cases isolate DC1C's constant regulator
  limits from DC2C's terminal-voltage-scaled limits.
- `smib-genrou-dc4c.json`: the native PID commutator exciter with compound
  potential source, controlled rectifier, saturation, and rate feedback.
- `smib-genrou-exac4.json`: the IEEE Type AC4 high-initial-response exciter,
  reused core GENROU, and all three named PowerWorld controller states.
- `smib-genrou-esac4a.json`: the IEEE Type AC4A high-initial-response exciter,
  including its UEL high-value gate and asymmetric field-voltage limits.
- `smib-genrou-esac6a.json`: the IEEE Type AC6A rotating exciter, including
  both lead-lag stages, field-current feedback, saturation, and rectifier load.
- `smib-genrou-esac8b.json`: the native 15-parameter Basler DECS ESAC8B
  profile with core GENROU and all five official PowerWorld exciter states:
  `EFD`, sensed `Vt`, derivative output, integral output, and `VR`.
- `smib-genrou-ac8b.json`: native PSS/E-v33 AC8B against PowerWorld's
  equation-equivalent `ESAC8B_PTI` common profile. The manifest hashes the
  separate native and PowerWorld DYR presentations; the comparison covers all
  five states, while AC8B-only PID/field limits and rectifier loading remain in
  the independent ANDES and equation-level tests.
- `smib-genrou-ac7b.json`: the native 27-parameter PSS/E AC7B profile with
  reused core GENROU and all six named PowerWorld states: `VE`, sensed `Vt`,
  `Kir`, `Kdr`, `VA`, and rate feedback. `Kir` maps to the stored outer-PID
  integral, while `Kdr`, `VA`, and feedback map to complete block outputs rather
  than InterPSS's internal lag/integrator coordinates.
- `smib-genrou-rexsys.json`: native 31-parameter PSS/E REXSYS with both PI
  loops, both voltage-path lead-lags, selectable feedback washout/lead-lag,
  rotating exciter, saturation, and rectifier loading active. It exports all
  ten named PowerWorld states; the channel labeled `Voltage PI` is the stored
  voltage-PI integrator, whereas `Current PI` is the complete block output.
- `smib-genrou-hyg3.json` and `smib-genrou-hyg3-pid.json`: native PSS/E HYG3
  double-derivative (`Cflag=0`) and PID (`Cflag=1`) branches. Each artifact
  exports all nine PowerWorld governor states plus GENROU and boundary signals.
  The comparison maps PowerWorld's `K1`, `K2 first`, and `K2 second` channels
  to their canonical block coordinates while retaining InterPSS's internal
  lag storage for numerical diagnostics.
- `smib-genrou-h6e.json`: native H6E with load-control droop, PID derivative
  filtering, pilot valve and gate servos, nonlinear Kaplan blade and power
  curves, and water-column dynamics. The artifact exports all nine named
  PowerWorld H6E states. They map directly to semantic InterPSS getters for
  measured electrical power, measured speed, PID integral, derivative storage,
  valve velocity, gate, deliberate-deadband blade demand, blade servo, and
  turbine flow.
- `smib-genrou-hygovr1.json`: native 26-CON PSS/E HYGOVR1 interchange record,
  loaded by PowerWorld without adding its richer native-display-only curve
  fields. It exports the filtered speed input, all four lead-lag states,
  integral governor, servo velocity, gate, turbine flow, and filtered electrical
  power, plus GENROU and boundary channels.
- `smib-genrou-hygov2d.json`: native 19-CON PSS/E HYGOV2D with nonzero
  integral gain and zero deadband so all six published states move during the
  fault. The cross-tool profile sets `Kp=0` because PowerWorld 24 initializes
  both HYGOV2 and HYGOV2D state 1 near `-Kp` when it is nonzero, causing an
  artificial pre-fault gate ramp. The independent HYGOV2D equation test retains
  nonzero-`Kp` coverage.

This is the required contract for each newly implemented model, not an optional
one-off check. Add a specification and publish its immutable PowerWorld output
alongside the model slice; then add a Java trajectory test that maps named
PowerWorld channels to InterPSS signals and enforces per-channel tolerances. A
model is only marked cross-tool verified after all three pieces exist: the JSON
specification, the hashed reference artifact, and the registered InterPSS
comparison test. ANDES remains a second oracle where it implements an
equation-equivalent model.

When PowerWorld's PTI loader requires a documented dialect extension that is
not part of the native PSS/E record, the specification may declare
`powerworld_dyr` separately from `dyr`. The runner loads only that declared
PowerWorld DYR, never combines dynamic-data formats, and hashes both inputs in
the manifest. AC7C, AC8C, AC9C, and AC11C use this mechanism: their native
PSS/E records have 38, 31, 45, and 40 parameters, while PowerWorld 24 requires
its SCL field in equivalent 39-, 32-, 46-, and 41-parameter records. The runner
also treats model-load, validation, script-action, and transient-start errors
in the PowerWorld log as hard failures; completion markers alone are not enough
to publish a reference artifact.

For full-system PSS/E datasets, use the explicit `gnet` and `mod_remove`
specification fields for the third and fifth optional `TSLoadPTI` arguments.
The runner stages them as `inputs/gnet.idv` and `inputs/mod_remove.idv`, emits
only relative paths in `run.aux`, and records semantic input names plus SHA-256
hashes in the manifest. Other optional PTI companion files remain available
through the five-position `pti_companion_files` array (MCRE, MTRLD, GNET,
BASEGEN, MODREMOVE). External source locations are recorded as
`external/<filename>` so generated artifacts do not disclose local usernames.

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

For AC11C, PowerWorld's ninth exported `PIKpoTio1` state is the OEL PI block
output, not its stored integral coordinate. The comparison maps that channel
to InterPSS's semantic OEL-regulator output while the local equation test
separately retains direct coverage of the OEL integral state. The other eight
PowerWorld channels map directly to `VE`, sensed voltage, and the main/PSS/UEL
PI and `Kb/Tb` coordinates.

DC1C and DC2C use a different PowerWorld PTI dialect boundary: each native
19-parameter PSS/E record remains intact, PowerWorld's SCL property stays
typed-only, and only the final typed `Spdmlt` value is present in the separate
20-parameter PowerWorld input. The five exported channels map to field
voltage, sensed voltage, bounded regulator output, rate-feedback output, and
lead-lag output. Both common cross-tool profiles use `Tr=0`; PowerWorld 24
accepts nonzero `Tr` but exports and drives the sensed-voltage path as
unfiltered, while InterPSS's documented nonzero-transducer behavior remains
covered by its independent five-state equation oracle. DC2C deliberately uses
tight regulator limits so the terminal-voltage-scaled ceiling is active during
the fault and its stored-state non-windup behavior is part of the contract.

For DC4C, PowerWorld's state named `PI` is the proportional-plus-integral
output `Kpr*error + integral`, not the raw integral coordinate and not the full
PID output that also includes the separately exported derivative signal. The
common trajectory activates the compound potential source, PID, regulator,
field integrator, and rate feedback. Saturation and `KC1` rectifier loading are
disabled in this cross-tool fixture because PowerWorld 24's combined nonlinear
initial state immediately drifts before the contingency; the focused equation
tests separately exercise the official `KC1*VFE/VE` input, FEX regions,
`VBMAX`, and saturation curve.

For AC1C, the native 23-value PSS/E record is loaded without a dialect
translation. The artifact exports PowerWorld's five named states: rotating
exciter voltage `VE`, sensed terminal voltage, regulator output `VA`,
lead-lag output `VLL`, and rate-feedback output `VF`. These map directly to
the semantic InterPSS AC1C accessors; the comparison also reuses the standard
GENROU electrical-state mapping. A second cold run produces the identical raw
CSV, while the manifest uses line-ending-independent canonical text hashes.

AC2C follows the same native-record and state-mapping contract with its
25-value PSS/E record. Its fixture keeps the high-initial-response
`KB*(VA-KH*VFE)` path active (`KB=2`, `KH=0.5`) and exports the same five
PowerWorld state names. The full trajectory comparison therefore tests the
behavior distinguishing AC2C from AC1C rather than merely checking import.

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

The weak-grid active-path discriminator extends that contract to four seconds
at `0.5 ms` and exports both bus voltages, generator P/Q, all three REGCA1
states, and REECA1 sensed voltage, measured power, PIQ, PIV, and active-power
order. PowerWorld validates it with zero errors or warnings and no automatic
correction; two cold runs produce identical raw CSV SHA-256
`8ef376610f7a7ed61dc03455d84cc7292d923642893e43e111e689bd5ad169ba`.
The registered comparison's maximum errors are `8.83e-4 pu` plant voltage,
`8.16e-4 pu` POI voltage, `0.0257 MW`, `0.0186 Mvar`, `1.74e-4 pu` REGCA1
Iq, `4.67e-5 pu` REGCA1 Ip, `2.57e-4 pu` measured voltage, `6.52e-5 pu`
measured power, `2.35e-5 pu` PIQ, `1.74e-4 pu` PIV, and `3.47e-5 pu`
active-power order. The tight local-state agreement rules out a standalone
REGCA1/REECA1 active-path equation or limiter defect for this public profile;
it does not substitute for a matched full-fleet Case-5 trace.

For GGOV1, PowerWorld's numbered result channels represent the named diagram
signals, which are not always the raw numerical integration coordinates.
`Governor Differential Control`, `Accel Control`, and `Temp Detection LL` map
to the corresponding block outputs exposed by InterPSS. The 0.5 ms contract
checks both bus voltages, generator MW/Mvar, relative rotor angle/speed, all
four GENROU electrical states, and all ten GGOV1 signals. ANDES does not
implement GGOV1, so this PowerWorld artifact is the independent model oracle.

For TGOV1, PowerWorld exports two governor states named `Turbine Power` and
`Valve Position`. They map to the output of InterPSS's `T2/T3` turbine block
and `T1` non-windup valve lag, respectively; they are not the blocks' internal
low-pass storage coordinates. The native 0.5 ms terminal-fault contract checks
those states together with both bus voltages, generator P/Q, relative
angle/speed, and all four GENROU electrical states. Two cold runs reproduce the
same canonical CSV SHA-256
`c3e9bf0fca67b41ec1e9f256444f30667a718f2ec6e458a95fafe3bce995c7da`.
The separate TGOV1D artifact activates its asymmetric speed deadband and
produces canonical SHA-256
`7a59dcab72dd0a90e597d8581a7f9004cf098ce788fb988059d0465fe78fed81`;
it receives direct coverage independently rather than through TGOV1 aliasing.

For GASTD, the three exported states are `Fuel Valve`, `Fuel Flow`, and
`Exhaust Temperature`. They map to the live outputs of the InterPSS `T1`,
`T2`, and feedback `T3` lag blocks. The native record activates its asymmetric
speed deadband; focused tests separately cover turbine-rating conversion and
initial load-limit behavior. Two cold PowerWorld runs reproduce canonical CSV
SHA-256 `dadc49cd48dd439b77234b6dd56c93649f3c0f944d3b4a401b09f6962feabbbd`.
GAST/GASTD exposes the same labels through the core `ICMLStateProvider`, so
callers need not depend on PowerWorld's numeric state-slot ordering.

For GAST2AD, PowerWorld exports nine governor slots: the seven named active
states `Speed Governor`, `Valve Positioner`, `Fuel System`, `Radiation Shield`,
`Thermocouple`, `Temp Control`, and `Turbine Dynamics`, plus two inactive
reserved slots that remain exactly zero. The direct trace exposed and corrected
the temperature-controller initialization: the inactive controller starts and
anti-windups at `Vmax`, as shown by the published diagram, rather than starting
at the active speed command. Two cold PowerWorld runs reproduce canonical CSV
SHA-256 `6916cbb7e9683d58305865ab91fd48a30f295e8735778fce9e12bda5a49d9f4a`.
The seven semantic labels are also available from the InterPSS governor through
`ICMLStateProvider`; the two unnamed reserved slots are intentionally
not presented as user-facing states.

For GASTWDD, PowerWorld exports `Power Transducer`, `Valve Positioner`, `Fuel
System`, `Radiation Shield`, `Thermocouple`, `Temp Control`, `Turbine Dynamics`,
`PID 1`, and `PID 2`, followed by one inactive reserved slot. The same nine
labels are available through `ICMLStateProvider`. Annotated CML controllers
inherit field-name state snapshots from their common base, while models may
override those names with domain labels. The source fixture's
explicit `Vmax=3.0` avoids PowerWorld's initialization-time PID-limit expansion;
validation then reports zero errors/warnings and no limit modification. Two
cold runs reproduce canonical CSV SHA-256
`f069d2b60f7f14c212045e2ccbd63593884dfd423699acb91803183400565e9a`.

For DEGOV1D, the registered contract checks both bus voltages, generator
MW/Mvar, relative rotor angle/speed, all four GENROU electrical states, both
electric-control-box states, physical actuator output, and electric-power
droop input. InterPSS stores the second-order control box as position `z` and
rate `zdot`; PowerWorld exports the observable-canonical coordinates
`zdot + (T1-T3)/(T1*T2)*z` and `z + T3*zdot`. This is the exact analytical
similarity transform of the published transfer function, not a fitted mapping.
The actuator's first two numbered slots are retained in the artifact but not
compared as raw states because PowerWorld uses different internal realization
coordinates; the physical third actuator state is compared directly. Two cold
runs reproduce canonical CSV SHA-256
`cbf28a42e540660b3073a8ca6d5e61d727ef7d9fa3b03da1ca1c38d2e226aaa1`.
Validation reports zero errors/warnings and no limit modification.

For TGOV3D, the fixture uses zero speed deadband so the terminal fault excites
the governor rather than merely confirming its initialized state. The registered
comparison covers all six PowerWorld governor states. PowerWorld's `LL` is the
lead-lag block output. Its `StateT5` uses a different initialized coordinate
origin from InterPSS's absolute reheater pressure, so the comparison translates
only the independently initialized origin and compares the complete dynamic
trajectory. Two cold runs reproduce canonical CSV SHA-256
`de582e7b59d43cc8ec0d1f19a9311856fe0c1b419eb58ac65228fb23b39b9ce9`.
Validation reports zero errors/warnings, no autocorrection, and no limit
modification.

For GGOV1D, the registered GGOV1 comparison is parameterized over the base and
deadband variants. Both compare all ten named PowerWorld governor states plus
the GENROU and network boundary channels. The shared channel tolerances use the
worse independently measured maximum from the two variants and are tighter than
the former GGOV1-only bounds. Two cold GGOV1D runs reproduce canonical CSV
SHA-256 `f84132d66c1e10e924bac31d01f42349a54ed59428dfeaf2c543e45b26964e91`.
Validation reports zero errors/warnings, no autocorrection, and no limit
modification.

For IEEEG3 and IEEEG3D, the artifacts export all four published governor state coordinates:
mechanical power, servomotor position, gate position, and transient droop. The
registered parameterized comparison uses the same coordinates directly, with
no fitted or translated state. Two cold runs reproduce canonical CSV SHA-256
`f10ad6f4e34256a515cd1549995b48ad31849301dcf828198b1cc0d57f8b1208`
for IEEEG3 and
`209528bcbf0829497b7883f474312eae696ac5282264978b5ec8fc42454e3850`
for IEEEG3D.
Validation reports zero errors/warnings, no autocorrection, and no limit
modification.

For PSS2A, the artifact exports every one of PowerWorld's 19 named stabilizer
state slots. The registered comparison maps both washout/transducer chains,
both output lead-lags, the final active M-by-N ramp-filter stage, and the
optional GE lead-lag/output, together with the GENROU and ESST1A boundary
signals. Nested child-controller signals must be read from the initialized
child CML evaluator; querying a nested expression through the parent evaluator
collapses it to the child controller's final output. ANDES does not implement
PSS2A, so PowerWorld is the independent model oracle.

For ESDC1A and ESDC2A, separate native DYR inputs and hashed manifests establish
direct artifact credit for both models. The selected terminal-fault profile
keeps their regulator limits inactive, so PowerWorld produces the same
canonical trace and InterPSS residuals for each. This is intentional rather
than alias evidence: focused equation tests independently force the defining
constant ESDC1A limits and terminal-voltage-scaled ESDC2A limits. Each cold
native-model generation reproduces raw SHA-256
`501be019239a2fc19c7368916c9b79d46274b340253f99a1bb6ed24cb4f8b097`.

For DC3A, the 17-selector terminal-fault contract maps the three published
PowerWorld states directly: `EField`, sensed terminal voltage, and rheostat
position. The fixture uses the native 12-CON PSS/E `DC3A` record that satisfies
the approved ESDC3A catalog row; the PSLF-style ESDC3A layout is not used as a
PSS/E input. Two cold runs reproduce raw SHA-256
`bd90cac7da662127f3b872af6ff4a782a665b7a034a11d7d2d4cefacb918b939`.

For REGFMA1, the benchmark uses a complete AUX network loaded into a new
PowerWorld case. The same AUX topology is parsed directly into an InterPSS
`DStabilityNetwork`, so transformer-control or interchange conversion does not
contaminate the model comparison. At a matched 0.5 ms step, the three-cycle
terminal-fault contract checks three bus voltages, generator MW/Mvar, and all
nine named states (`DeltaDroop`, `IntEdroop`, `Pmeas`, `Qmeas`, `Vmeas`, and
the four P/Q limit integrals). Current maximum differences are `0.00433 pu`
bus voltage, `0.279 MW`, `0.847 Mvar`, `0.00124 rad` angle, and `1.21e-5 pu`
internal-voltage integral; the four inactive limit integrals match exactly.

Run the framework's dependency-free tests with:

```powershell
python ipss.test.plugin.core/src/test/python/test_powerworld_transient_benchmark.py
```

The generated PowerWorld trace is an independent reference artifact, not an
automatic InterPSS acceptance result. Match event timing and channel semantics,
then apply per-channel absolute tolerances in the corresponding InterPSS test.
