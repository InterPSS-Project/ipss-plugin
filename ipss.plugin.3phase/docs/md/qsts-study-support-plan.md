# QSTS Study Support Plan

Detailed class-level work packages and verification gates are tracked in
`qsts-implementation-todo.md`.

## Goal

Add generic quasi-static time-series study support for three-phase distribution
feeders, with an initial OpenDSS adapter for imported feeders, while preserving
the current fixed-point power-flow and parser QA discipline.

The first implementation should support sequential quasi-static time-series
studies: build or import a feeder once, apply per-time-step
source/load/DER/control state updates, run fixed-point power flow, and export
comparable results. Full dynamic simulation and simultaneous multi-period
optimization are out of scope for this plan.

The QSTS engine should not be OpenDSS-specific. OpenDSS-specific behavior belongs
in parser and adapter classes that translate `LoadShape`, `daily/yearly/duty`,
`Set`/`Solve`, `RegControl`, `CapControl`, and Reactor semantics into generic
QSTS schedules, device states, and controls.

## Current State

The OpenDSS parser and fixed-point power flow now have strong static-PF coverage:

- OpenDSS load voltage-response models `1` through `8` are parsed and validated
  with formula tests and a DSS-Python-backed low-voltage mini feeder.
- Ckt7, Ckt24, IEEE8500, IEEE123, and several mini cases have comparison or QA
  fixtures.
- `LoadShape` and OpenDSS time-series metadata are stored as sidecar adapter
  data and converted into generic `QstsScheduleData`.
- Load definitions already expose time-series hooks in real feeders, especially
  `daily=...`, `yearly=...`, `duty=...`, `status=variable`, `xfkVA`, and
  `AllocationFactor`.
- The generic QSTS scheduler, parsed profile model, per-step state application,
  result object, and CSV exporter are in place for the current v1 path.
- Static QSTS and static PF now use the existing `Static3PNetwork` and generic
  phase-device contracts. They must not require DStab bus, branch, load, or
  generator objects.
- The OpenDSS static parser can materialize full-feeder topology into the
  existing static network model: source branch, lines, capacitors, transformers,
  regulators, reactors, loads, PV, and storage.

## Scope

### In Scope

- OpenDSS `LoadShape` parsing for `mult`, `pmult`, `qmult`, `npts`,
  `interval`, `sinterval`, `minterval`, `hour`, `csvfile`, and simple inline
  arrays.
- Binding load profiles through `daily`, `yearly`, and `duty`.
- Global `loadmult` handling.
- Sequential fixed-point power flow over a requested time range.
- Warm-starting each step from the prior solved voltage profile.
- OpenDSS-compatible control loops for transformer/regulator tap changes,
  capacitor switching, and controllable shunt/series reactors.
- DSS-Python-backed mini-case and feeder comparison tests.
- CSV result export for bus voltages, load powers, branch currents, device
  states, convergence, and per-step mismatch metrics.

### Out of Scope for Initial Delivery

- Electromagnetic/dynamic simulation.
- Protection events and fault studies.
- Full OpenDSS control queue parity.
- Simultaneous multi-period OPF with inter-temporal constraints.
- Complete OpenDSS monitor/energy-meter reporting parity.
- Every OpenDSS shape interpolation mode; v1 can start with interval-indexed
  steps and documented interpolation behavior.
- User-written control DLLs.

## Design Principles

- Keep static network construction separate from time-step state mutation.
- Do not mutate original nameplate values destructively; store base values and
  derive per-step effective values.
- Treat the fixed-point solver as a reusable per-step engine.
- Treat `IPhaseLoad` and `IPhaseGen` as the static PF/QSTS device contracts for
  one-, two-, and three-phase devices.
- Keep DStab classes on the dynamic-study side. Dynamic phase devices may
  implement the common interfaces, but QSTS must not depend on DStab types.
- Make DSS-Python the reference for time-series semantics, as with the device QA
  process.
- Prefer small mini-cases before broad feeders.
- Keep controls optional and explicit, because controls can hide loadshape and
  profile issues.
- Treat OpenDSS and DSS-Extensions/DSS-Python as behavioral references for
  control sequencing, deadbands, delays, phase selection, and tap/state limits.

## Architecture Snapshot

The current architecture has four explicit boundaries:

1. Static model boundary
   - `Static3PNetwork`, `Static3PBus`, `Static3PBranch`, `Static3PLoad`, and
     `Static3PGen` are the default steady-state model for QSTS and static PF.
   - `IBus3Phase` and `IBranch3Phase` provide the network/branch contract used
     by the fixed-point solver.
   - `IPhaseLoad` and `IPhaseGen` provide phase-vector load/generator access.
     `IPhaseLoad` includes both `getId()` and `setId(String)` so parser,
     factory, and base-state code can work through the generic boundary.

2. Dynamic model boundary
   - `org.interpss.threePhase.dynamic` and
     `org.interpss.threePhase.basic.dstab` remain dynamic-facing packages.
   - DStab load/generator classes can implement `IDynamicPhaseLoad` and
     `IDynamicPhaseGen`, but static QSTS/PF code should not cast to those
     classes.

3. Adapter boundary
   - `org.interpss.threePhase.dataParser.opendss` parses DSS syntax and stores
     OpenDSS-specific metadata.
   - Static parser mode materializes devices into `Static3PNetwork` and
     registers generic phase loads/generators plus sidecar schedule/control
     metadata.
   - OpenDSS behavior remains adapter-level behavior; generic QSTS classes do
     not parse DSS syntax.

4. Solver/control boundary
   - `QstsStudy` owns the time-step loop, schedule state, warm-start behavior,
     and result sampling.
   - `DistributionPowerFlowAlgorithmImpl` solves one network state at a time.
   - Control models evaluate solved state and schedule/apply device actions;
     they should not be embedded in parser code or in base static PF import.

This shape also supports the rapid-QSTS path: the base case, prepared schedule,
state deltas, factorization cache, and solver strategy can be reused by exact
PF, event-driven, sensitivity, and reduced-order methods without changing the
OpenDSS adapter.

## Proposed Architecture

### Generic Profile Model and OpenDSS Adapter Metadata

Add generic QSTS profile objects and keep OpenDSS parser details in an adapter
metadata package:

- `QstsProfile`
  - `id`
  - time axis
  - `double[] pMult`
  - `double[] qMult`
  - diagnostics
- `QstsProfileRegistry`
  - Lookup by profile id.
  - Validation for missing arrays and mismatched lengths.
- `QstsProfileBinding`
  - Device id, device class, selected generic profile ids, and device status.

- `OpenDSSLoadShape`
  - `id`
  - `npts`
  - `intervalHours`
  - `double[] hour`
  - `double[] pMult`
  - `double[] qMult`
  - source metadata for diagnostics
- `OpenDSSShapeRegistry`
  - Case-insensitive lookup by shape id.
  - Validation for missing arrays, mismatched lengths, and unsupported file
    references.
- `OpenDSSProfileBinding`
  - Device id, device class, selected profile type: `DAILY`, `YEARLY`, `DUTY`.
  - Status: `FIXED`, `VARIABLE`, or default.
  - Converts into generic `QstsProfileBinding`.

### Time-Series Study API

Add a new generic study API, separate from `OpenDSSDataParser`:

```java
QstsStudy study = OpenDSSQstsStudyFactory.from(parser);
study.setMode(QstsMode.YEARLY);
study.setStartIndex(0);
study.setNumberOfSteps(168);
study.setStepSizeHours(1.0);
study.setPowerFlowMethod(DistributionPFMethod.Fixed_Point);
QstsResult result = study.run();
```

Core objects:

- `QstsStudy`
  - Owns schedule options and runs the per-step loop.
- `QstsStepContext`
  - Current index, time in hours, mode, global multipliers, and shape lookup.
- `QstsStateApplier`
  - Applies effective source/load/DER/control states before each PF solve.
- `QstsResult`
  - Step convergence metadata and selected result channels.
- `QstsCsvExporter`
  - Writes bus voltage, load power, branch current, and device-state tables.
- `OpenDSSQstsStudyFactory`
  - Converts OpenDSS parser metadata into generic QSTS schedules.

### Performance and Parallel Execution Architecture

QSTS should avoid reparsing and rebuilding the network for each time step. The
core architecture should separate immutable base-case data from mutable
per-study state:

- `QstsBaseCase`
  - Holds the imported network topology, base device values, bus/branch lookup
    indexes, phase mappings, and solver-ready ordering.
  - Is built once from `INetwork3Phase` and reused by many study windows.
- `QstsNetworkSession`
  - Owns the mutable working network state for one sequential study window.
  - Starts from `QstsBaseCase`, restores base device values, applies deltas, and
    runs fixed-point PF.
- `QstsStateDelta`
  - Represents only the changes for one step or window: load multipliers, DER
    injections, source voltage, tap positions, capacitor/reactor states, and
    control actions.
- `QstsPreparedSchedule`
  - Pre-resolves profiles into compact per-step multiplier arrays so the hot
    loop does not perform string lookup or parser-level interpretation.

Parallel execution should happen across independent windows, not inside a
single sequential control loop. For example, monthly windows can run in parallel
when each worker receives the same `QstsBaseCase` and its own
`QstsNetworkSession`. Within a month, steps remain sequential if warm-started
voltages and control state carry from one step to the next.

Fast execution depends on these rules:

- Parse once, build indexes once, and pre-resolve schedule arrays before the
  first solve.
- Reuse bus/branch ordering, matrix structure, base `Ybus`, and the `Ybus`
  factorization where topology and admittance-bearing device states are
  unchanged.
- Apply per-step device deltas in place on the session and then restore from
  captured base state, rather than cloning the entire parsed model every step.
- Use one network/session per parallel worker; do not share mutable bus,
  branch, load, or solver objects across workers.
- Store result channels in streaming or chunked form for large feeders so
  feeder-scale yearly runs do not retain every detailed object in memory.

The solver layer should therefore expose a reusable linear-solve context:

- `QstsYBusFactorizationCache`
  - Owns the assembled `Ybus`, ordering/permutation, symbolic factorization,
    and numeric factorization for a compatible base case.
  - Is shared read-only by batches whose network admittance model has not
    changed.
- `QstsLinearSolveContext`
  - Supplies fast repeated solves for per-step right-hand-side updates.
  - Lets load/source/injection changes reuse the same factorization.

The factorization cache must be invalidated only when the network admittance
changes: topology switching, branch impedance changes, transformer tap model
changes that alter `Ybus`, capacitor/reactor admittance state changes, voltage
source Thevenin impedance changes, or any control action that changes matrix
coefficients. Plain load multipliers, DER injections modeled as current/power
injections, and source voltage magnitude/angle updates should update the
right-hand side or nonlinear injection evaluation, not rebuild `Ybus`.

For control-enabled QSTS, the fast path depends on whether the control action
changes only injections or changes the network admittance matrix. Controls that
only affect load, DER, inverter, or storage injections can keep the same
`Ybus`. Controls that change shunt admittance, regulator taps, topology, or
branch parameters must update or rebuild the matrix instead of being modeled as
fictitious right-hand-side compensation currents.

The compensation-current experiment has been removed from the plugin because it
did not provide a reliable QSTS acceleration path. For shunt capacitor/reactor
switching, the supported behavior is to treat the state change as an
admittance-changing event and invalidate/update the matrix. For regulator tap
changes, the supported fast path is:

- keep the same sparse structure and reuse the symbolic factorization;
- update affected regulator branch matrix entries in place with
  `Ynew - Yold` for the `Yff/Yft/Ytf/Ytt` blocks;
- rerun numeric LU after each tap-changing admittance update;
- rebuild when the regulator touches the swing-bus boundary, because the
  swing-boundary transform has moved `Yns * Vs` out of the matrix and into the
  RHS, so both matrix and RHS boundary terms change.

Historical regulator RHS compensation experiments were tried and then removed:
under-relaxation, virtual series-resistance padding, and guarded fallback. On
IEEE123, `0.01` through `1.0E-5` pu per-phase padding caused repeated guarded
fallback; `1.0E-6` pu avoided repeated fallback but effectively behaved like
the unpadded damped-compensation path. The timing comparison on the same
IEEE123 three-step regulator QSTS case was:

- full rebuild: `96 ms`;
- symbolic reuse with new matrix: `32 ms`;
- symbolic reuse with in-place matrix value update: `20 ms`;
- direct compensation with `1.0E-6` pu virtual padding: `124 ms`.

The padded direct-compensation path performed fewer numeric factorizations, but
needed many more fixed-point iterations and still used one guarded fallback.
Therefore symbolic reuse plus in-place numeric matrix value update is the QSTS
default for regulator tap controls, and the compensation-current code path is
not part of the plugin.

Unsupported topology changes, switch open/close actions, or control deltas that
fail an accuracy/convergence guard should invalidate the factorization and use
the normal rebuild/refactor path.

The first implementation can use session cloning at window boundaries for
safety. Later optimization should replace deep clone costs with a lighter
snapshot/session builder that copies only mutable values and reuses immutable
indexes and solver structure.

### Foundation for Advanced Rapid-QSTS Methods

The generic QSTS design should leave extension points for advanced rapid-QSTS
methods identified in DOE/GMLC work on high-resolution distributed-PV impact
assessment: event-based simulation, linear power-flow approximation, parallel
time-separable solves, voltage-drop time-series approximation, and reduced-order
feeder models.

The foundation should be:

- `QstsSolverStrategy`
  - Common interface for exact fixed-point PF, linearized PF, voltage-drop
    approximation, and future hybrid strategies.
  - Lets a study choose accuracy/speed tradeoffs without changing parser or
    schedule code.
- `QstsEventDetector`
  - Detects when a full solve is required: load/PV ramp threshold, voltage limit
    crossing, tap/capacitor control threshold crossing, topology/admittance
    change, or approximation error bound exceeded.
- `QstsEventDrivenRunner`
  - Skips or approximates quiet intervals and runs full PF at event points.
  - Stores interpolation/approximation diagnostics in the result.
- `QstsLinearSensitivityModel`
  - Captures voltage/current sensitivities around a solved base point.
  - Supports fast screening of many PV/load scenarios before exact PF
    confirmation.
- `QstsSolutionReuseStrategy`
  - Optional vector-quantization or nearest-state lookup layer that reuses prior
    solved states when the current state is sufficiently similar.
  - Does not change the core exact QSTS solver; it is an approximate acceleration
    strategy governed by `QstsAccuracyPolicy`.
- `QstsReducedOrderFeederModel`
  - Represents equivalent feeder regions behind boundary buses while preserving
    boundary voltage/current/P/Q behavior needed by the study.
- `QstsAccuracyPolicy`
  - Defines when approximate methods are allowed, maximum voltage/current error,
    required full-solve cadence, and fallback-to-exact rules.

These methods should be layered on top of `QstsBaseCase`,
`QstsPreparedSchedule`, `QstsStateDelta`, and `QstsYBusFactorizationCache`, not
mixed into the OpenDSS adapter or the exact solver core. This keeps the initial
exact QSTS implementation useful as the reference solver while allowing faster
engines to share the same inputs, device state model, result channels, and
verification harness.

### Hosting-Capacity Metrics Layer

PV hosting-capacity studies are an application layer on top of QSTS, not a
change to the core solver. The QSTS result model should still make them easy to
build by exposing streaming metrics such as:

- instantaneous maximum/minimum voltage;
- moving n-minute voltage averages and time outside ANSI Range A;
- instantaneous ANSI Range B violations;
- maximum instantaneous and moving-average line/transformer loading;
- total capacitor/regulator/control-device state changes;
- first-violation penetration level and time-vector diagnostics.

These metrics can be implemented as `QstsMetricCollector` plugins that consume
`QstsResult` chunks during a run. They should not force full retention of every
bus voltage or branch current time point in memory.

### OpenDSS Control Compatibility Layer

Time-series studies need a small OpenDSS-like control layer rather than a simple
"solve once per profile point" loop. The plan is to reproduce the observable
behavior needed by feeder studies first, then tighten toward OpenDSS source-code
semantics as tests demand.

Reference sources to inspect before implementation:

- EPRI OpenDSS `RegControl` documentation and source implementation.
- EPRI/OpenDSS and DSS-Extensions `CapControl` documentation, especially
  `CTPhase` and `PTPhase` phase-selection behavior.
- DSS-Extensions/DSS C-API source files for `RegControl`, `CapControl`,
  `ControlQueue`, `Capacitor`, `Transformer`, and `Reactor`.
- DSS-Python and OpenDSSDirect.py behavior as executable references for
  controls-off, static-control, and time-series control modes.

Recommended control-side objects:

- `QstsControlModel`
  - Common interface for evaluating a control action after a PF solve.
- `QstsControlQueue`
  - Stores pending tap/switch/state actions with step time, delay, priority,
    and owning device.
- `OpenDSSRegControlModel`
  - Regulator/LTC voltage control, line-drop compensation, remote bus
    monitoring, tap limits, tap increment, reverse-mode hooks, and phase
    selection.
- `OpenDSSCapControlModel`
  - Capacitor switching based on voltage, current, kvar, PF, and time modes;
    includes ON/OFF thresholds, deadband, delays, voltage override, CT/PT ratios,
    and CTPhase/PTPhase handling.
- `OpenDSSReactorModel`
  - Reactor PD element representation, including shunt/series connection,
    enabled state, kvar/kV and impedance-matrix forms.
- `OpenDSSControlledReactorState`
  - Optional state wrapper for reactors switched by script actions, external
    schedules, or any OpenDSS-compatible control mode identified from reference
    cases. OpenDSS has a `Reactor` element; a separate standard `ReactorControl`
    object is not assumed until verified from reference cases/source.

Per time step, the study loop should support:

1. Apply scheduled source, load, DER, capacitor, reactor, and tap initial states.
2. Run fixed-point PF.
3. Evaluate controls against solved voltages/currents/powers.
4. Queue tap/switch/state actions if outside deadband or thresholds.
5. Apply queued actions whose delay/control-mode rules allow execution.
6. Re-solve until the control queue is empty, `maxcontrol` is reached, or a
   non-convergence condition is reported.
7. Save final device states and result channels for the step.

When factorization reuse is enabled, steps 4 and 5 should distinguish
injection-only controls from admittance-changing controls. The result should
record whether each control action reused the existing factorization, updated
matrix values in place, or forced a `Ybus` rebuild, so performance reports can
separate pure RHS updates, numeric refactors, and full matrix rebuilds.

### Load State Application

Generic QSTS load sidecar state is based on `IPhaseLoad`. Single-, two-, and
three-phase loads are represented as ABC phase vectors with inactive phases set
to zero. Legacy `ILoad1Phase` and `ILoad3Phase` remain compatibility surfaces,
but QSTS core must not depend on DStab load types. Each load should keep:

- Base CP/CI/CZ or base three-phase load.
- Parsed OpenDSS model code and voltage-response parameters.
- Optional daily/yearly/duty shape ids.
- Status and class.
- Current effective multiplier.

Per-step effective load:

- `status=fixed`: ignore loadshape and use base load except for global rules
  verified against DSS-Python.
- `status=variable`: apply selected profile and global `loadmult`.
- For `pmult/qmult`, scale P and Q independently before voltage-response
  evaluation.
- For `mult`, apply the same multiplier to P and Q.
- Preserve OpenDSS voltage-response model behavior after the scheduled nominal
  P/Q is updated.

### Generator, PV, and Storage State Application

Static QSTS models PV, storage, and other steady-state DERs as generators through
`IPhaseGen`. OpenDSS `PVSystem` and `Storage` details are preserved as adapter
metadata that drives static P/Q setpoints and storage energy-state updates.
Dynamic PV/DER model classes are not used in QSTS.

The static DER path should keep:

- Base per-phase P/Q injection and sign convention.
- Rated kVA, available P, Q limits, PF limits, and cut-in/out flags.
- PV irradiance/shape bindings and curve ids as adapter metadata.
- Storage kW/kVA/kWh ratings, state of charge, reserve, efficiencies, and
  charge/discharge limits.

Inverter and storage controls are layered on top of these static generator
states. They should update generator setpoints and energy state through generic
QSTS state appliers, not through dynamic simulation models.

Scheduled storage dispatch now uses a storage-specific sidecar state keyed by
`IPhaseGen`. `QstsStateApplier` restores base static generator state, skips
storage devices in the generic generator multiplier path, and applies
energy-limited charge/discharge dispatch with state-of-charge carryover between
steps. OpenDSS `StorageController` behavior remains an adapter/controller layer
to add after mini-case reference behavior is defined.

### Source and Global Options

Parse and store relevant `Set` / `Solve` options:

- `mode=daily|yearly|duty|snapshot`
- `number`
- `stepsize`
- `loadmult`
- `controlmode`
- `maxcontrol`
- control delays and time variables needed to reproduce queued actions
- `hour` where used

The static parser may still ignore execution commands for one-shot PF, but it
should record them in a time-series configuration object for the study API.

## OpenDSS Source/Behavior Research Tasks

Before coding control logic, do a short reference-code pass and save notes in
this document or `opendss-feeder-benchmark-findings.md`.

- [ ] Inspect OpenDSS/DSS C-API `RegControl` source for:
  - monitored voltage calculation
  - line-drop compensation equation
  - remote bus behavior
  - tap step rounding and deadband
  - tap limits and reverse-flow behavior
  - delay and control-queue interaction
- [ ] Inspect `CapControl` source for:
  - voltage/current/kvar/PF/time control modes
  - ON/OFF threshold logic
  - voltage override
  - PTPhase/CTPhase AVG/MAX/MIN behavior
  - capacitor state transitions and delays
- [ ] Inspect `ControlQueue` and solution control-mode flow for:
  - `controlmode=off`, static, time, event, and default behavior
  - `maxcontrol` stopping conditions
  - cleanup/end-of-time-step semantics
- [ ] Inspect `Reactor` and related switching/script behavior for:
  - reactor Yprim construction
  - shunt versus series connection
  - enabled-state updates
  - whether any reference feeders use reactor switching through script,
    `SwtControl`, or another controller.
- [ ] Create a source-note table mapping each OpenDSS source behavior to the
  intended InterPSS adaptation.

## Implementation Progress Summary

Completed or established:

- Generic QSTS model and runner:
  `QstsStudy`, `QstsStepContext`, `QstsStateApplier`, `QstsResult`,
  `QstsStepResult`, schedule/profile objects, and CSV export.
- OpenDSS adapter metadata:
  `OpenDSSTimeSeriesData`, `OpenDSSLoadShape`, profile bindings, global options,
  PV/storage metadata, CapControl metadata, and InvControl metadata.
- Static phase-device boundary:
  `IPhaseLoad`, `IPhaseGen`, static/dynamic specializations, static bus phase
  views, and removal of plugin-local static adapters.
- Static full-feeder import path:
  existing `Static3PNetwork` is used for static parser mode; source, lines,
  transformers, regulators, capacitors, reactors, loads, PV, and storage are
  materialized without DStab objects.
- Control foundation:
  capacitor controls are implemented for the static PF and QSTS paths with
  DSS-Python mini-case coverage, including delayed control-queue operation
  counts; inverter and storage controls remain staged after their static model
  foundations.
- Regulator performance foundation:
  regulator tap QSTS uses symbolic factorization reuse and in-place sparse
  matrix value updates as the default method. Direct RHS compensation,
  under-relaxation, and virtual impedance padding were tested and removed from
  the plugin because they were slower and less robust than matrix value update.
- Verification:
  QSTS focused suite passes; full OpenDSS parser/PF comparison suite has passed
  with IEEE123, IEEE8500, Ckt7, Ckt24, IEEE13, and mini-case coverage recorded
  in `qsts-implementation-todo.md`.

Next high-value slices:

1. Add PV duty-curve QSTS coverage with inverter controls enabled and
   checked-in DSS-Python terminal P/Q references.
2. Implement inverter control setpoint modes on top of the static `IPhaseGen`
   capability model.
3. Wire scheduled storage dispatch through `QstsStateApplier`, including energy
   carryover between steps.
4. Begin `QstsBaseCase`, `QstsPreparedSchedule`, and factorization-cache work
   only after the exact QSTS reference path has stable mini-case coverage.

## Milestones

### TS1: Plan and Data Model

- [ ] Add `OpenDSSLoadShape`, `OpenDSSShapeRegistry`, and
  `OpenDSSProfileBinding`.
- [ ] Add parser storage for time-series metadata without changing static PF
  behavior.
- [ ] Add unit tests for shape lookup, interval conversion, and array-length
  validation.

Verification:

- Existing OpenDSS static parser/PF tests remain unchanged.
- Shape metadata can be inspected without applying it.

### TS2: LoadShape Parser

- [ ] Parse inline `mult`, `pmult`, `qmult`, `hour`, `npts`, `interval`,
  `sinterval`, and `minterval`.
- [ ] Parse simple `csvfile=...` one-column and two-column shape files.
- [ ] Preserve file/line diagnostics for unsupported shape forms.
- [ ] Add Ckt7/Ckt24 shape parsing smoke tests.

Verification:

- Ckt7 `LoadShapes_ckt7.dss` produces expected shape counts.
- Unsupported shape forms are reported, not silently ignored.

### TS3: Load/Profile Binding

- [ ] Parse `daily=`, `yearly=`, and `duty=` properties on loads.
- [ ] Parse `status=fixed|variable|exempt` enough to drive profile application.
- [ ] Keep base load values immutable and expose effective per-step values.
- [ ] Add mini tests for `mult`, `pmult/qmult`, `loadmult`, and fixed versus
  variable load status.

Verification:

- Static PF results are unchanged when the time-series study is not used.
- Per-step load effective P/Q matches DSS-Python load powers in mini cases.

### TS4: Sequential Fixed-Point QSTS Engine

- [ ] Add generic `QstsStudy`.
- [ ] Run one fixed-point solve per step.
- [ ] Warm-start voltages from the previous step.
- [ ] Capture convergence, iteration count, max voltage mismatch, and failure
  reason per step.
- [ ] Add CSV export for bus voltage by step and phase.

Verification:

- A two-bus mini case with a short daily shape matches DSS-Python bus voltages
  for every step within a tight tolerance.
- A failed step reports enough context to reproduce it.

### TS4b: Control Queue Skeleton

- [ ] Add generic `QstsControlModel` and `QstsControlQueue`.
- [ ] Implement controls-off, frozen-control-state, and static-control study
  modes.
- [ ] Add per-step control action log and final device-state export.
- [ ] Add loop guard for `maxcontrol`, oscillating controls, and non-converged
  intermediate PF solves.

Verification:

- A no-op control queue produces identical results to TS4.
- A scripted one-step capacitor/tap state change is applied and exported.

### TS5: DSS-Python-Backed Mini QA

- [ ] Create mini DSS cases for:
  - Uniform `mult`.
  - Independent `pmult/qmult`.
  - `loadmult` plus per-load shape.
  - `status=fixed` versus `status=variable`.
  - OpenDSS load models `1`, `4`, and `8` under a shape.
- [ ] Add Python reference export helper for multi-step voltage and load-power
  CSVs.
- [x] Add enabled Java regressions comparing InterPSS time-series results to
  checked-in DSS-Python references.
  - `OpenDssQstsMultiStepReferenceTest` compares load, PVSystem, and storage
    terminal P/Q against `qsts-multistep-dss-python-device-reference.csv`.

Verification:

- All mini cases compare against DSS-Python, not only formulas.

### TS6: Feeder Smoke Tests

- [ ] Run Ckt7 yearly first 24 steps with controls off.
- [ ] Run Ckt24 first 24 or 168 steps with controls off after shape parsing is
  stable.
- [ ] Export per-step worst voltage error and convergence table.
- [ ] Document residual patterns in `opendss-feeder-benchmark-findings.md`.

Verification:

- Controls-off feeder runs converge across the selected step window.
- Worst errors are stable and explainable by already-known static modeling
  gaps, not time-series state application bugs.

### TS7: Regulator and Transformer Tap Controls

- [ ] Parse RegControl properties needed for OpenDSS-compatible behavior:
  transformer, winding, controlled winding, `vreg`, `band`, `ptratio`,
  `ctprim`, `R`, `X`, monitored bus, delay, tap limits, tap increment, and
  phase-selection properties.
- [ ] Implement monitored voltage calculation and line-drop compensation.
- [ ] Implement tap-change action selection using OpenDSS deadband and rounding
  semantics verified from source/DSS-Python.
- [ ] Support independent single-phase regulator transformer branches and
  per-phase tap changes.
- [ ] Export tap position and effective ratio per step.
- [ ] Add DSS-Python mini cases for:
  - local voltage regulator
  - remote-bus regulator
  - line-drop-compensated regulator
  - three parallel single-phase regulators with different taps.

Verification:

- Controls-off and frozen-tap results stay reproducible.
- Static and time-series regulator mini cases match DSS-Python tap positions
  and voltages.

### TS7b: Capacitor Controls

- [x] Parse CapControl properties: controlled capacitor, monitored element,
  terminal, type, ON/OFF settings, CT/PT ratios, delays, voltage override,
  `Vmax`, `Vmin`, `CTPhase`, and `PTPhase`.
- [x] Implement voltage, current, kvar, PF, and time control modes in priority
  order guided by OpenDSS source behavior.
- [x] Implement capacitor state updates and export per-step states/kvar.
- [x] Add DSS-Python mini cases for:
  - voltage ON/OFF switching
  - current switching
  - kvar/PF switching
  - PTPhase/CTPhase AVG/MAX/MIN behavior
  - voltage override
  - delayed control-queue operation count.

Verification:

- Mini cases match DSS-Python capacitor states, terminal powers, and bus
  voltages for every step.

### TS7c: Reactor Modeling and Reactor State Control

- [ ] Add OpenDSS `Reactor` parser support if not already present.
- [ ] Implement reactor Yprim for kvar/kV, R/X, Z, R/X matrices, positive/
  negative/zero sequence impedance, wye/delta, shunt/series, and enabled state
  as needed by reference feeders.
- [ ] Support per-step reactor enabled-state changes through scripts, schedules,
  or any verified OpenDSS control mechanism.
- [ ] Export reactor state, terminal powers, and currents per step.
- [ ] Add DSS-Python mini cases for:
  - shunt wye reactor
  - delta/line-line reactor
  - series reactor
  - scheduled switched reactor.

Verification:

- Reactor Yprim and solved terminal powers match DSS-Python before control
  behavior is enabled.
- Switched-reactor mini cases match DSS-Python state and voltage behavior.

### TS8: DER and Storage Extension

- [x] Parse and bind `Generator`, `PVSystem`, and `Storage` shape references
  after basic load time-series support is stable.
  - `Generator` support includes the concrete IEEE8500 PV pattern in
    `P174_Run_360kW_PV.DSS`: `Generator.G1`, `LoadShape.PVCurve`, and
    `generator.g1.duty=PVcurve`.
  - `PVSystem` and `Storage` continue to use static `IPhaseGen` metadata and
    the inverter adapter sidecar, not DStab dynamic DER classes.
- [x] Add official OpenDSS `PVSystem` example metadata coverage.
  - Parses `MyPvsT` and `MyEff` `XYCurve` data, `MyIrrad` `LoadShape`, and
    `MyTemp` `TShape`/`TDaily`.
  - Uses these parser-side details to populate generic inverter capability
    values for static QSTS control without extending `IPhaseGen`.
- [x] Parse OpenDSS `XYCurve` records into generic `QstsControlCurve` data for
  inverter control modes.
  - The OpenDSS adapter scales InvControl per-unit curves into generic
    engineering-unit curves before `InverterGenAdapter` evaluates them.
- [x] Apply P/Q injections per step with consistent sign conventions for
  scheduled generic generator/PV/storage metadata.
- [x] Add DSS-Python-backed InvControl mini references for `VOLTVAR`,
  `VOLTWATT`, `WATTPF`, and `WATTVAR`.
  - These compare PVSystem terminal P/Q before bus voltages.
  - VOLTVAR/VOLTWATT still use wider tolerances while OpenDSS PV terminal
    control details are investigated beyond the generic QSTS control loop.
- [x] Add QSTS inverter PF/control iteration so inverter setpoint changes can
  trigger another PF solve within `maxControlIterations`.
- [ ] Add PV duty-curve QSTS mini case using IEEE8500-style data and checked-in
  DSS-Python terminal P/Q references.

Verification:

- Per-step DER terminal powers match DSS-Python references.

## QA Strategy

Use the same QA ladder as static feeder work:

- Start with one-device mini cases.
- Compare solved load powers before voltage.
- Compare bus voltage by step/phase.
- Export depth plots only after per-device powers match.
- Use DSS-Python references checked into `src/test/resources/opendss-reference`
  for stable tests.
- Keep exploratory full-feeder CSVs under `target/load-comparison`.

Minimum enabled regression set for v1:

- Shape parser unit tests.
- Effective load P/Q mini tests.
- DSS-Python-backed two-bus daily shape voltage comparison.
- DSS-Python-backed low-voltage CVR/ZIP shaped-load comparison.
- Ckt7 first-day controls-off smoke test.

## Open Questions

- Whether `status=exempt` should be modeled distinctly in v1 or treated as
  fixed until a DSS-Python mini case proves the required semantics.
- Whether interpolation between `hour` points is needed for the first supported
  feeders, or whether interval-indexed steps are sufficient.
- How much of OpenDSS control queue ordering must be replicated before
  time-series controls are useful.
- Whether result storage should be in-memory only for tests or stream directly
  to CSV for feeder-scale studies.

## Suggested First Implementation Slice

1. Inspect and summarize OpenDSS/DSS C-API source behavior for `ControlQueue`,
   `RegControl`, `CapControl`, and `Reactor`; save the mapping from source
   behavior to InterPSS adaptation before coding.
2. Add data classes for `OpenDSSLoadShape` and profile bindings.
3. Parse inline `LoadShape` arrays and load `daily/yearly/duty` properties.
4. Add a two-bus mini DSS case with a three-point daily `mult` shape.
5. Add a DSS-Python reference CSV for per-step bus voltage and load power.
6. Implement a minimal generic `QstsStudy` plus `OpenDSSQstsStudyFactory` that
   applies load multipliers and runs fixed-point PF for each step.
7. Add the control-queue skeleton before any regulator/capacitor control
   implementation.
8. Gate with mini DSS-Python comparison before touching Ckt7/Ckt24.

## Reference Links

- EPRI OpenDSS RegControl documentation:
  `https://opendss.epri.com/RegControl.html`
- EPRI OpenDSS CapControl phasing technote:
  `https://opendss.epri.com/TechNoteOpenDSSCapControlPhasing.html`
- EPRI OpenDSS Reactor documentation:
  `https://opendss.epri.com/Reactor.html`
- DSS-Extensions control element reference:
  `https://dss-extensions.org/dss-format/toc_control.html`
- DSS-Extensions Reactor property reference:
  `https://dss-extensions.org/dss-format/Reactor.html`
- DSS C-API / DSS-Extensions source repository:
  `https://github.com/dss-extensions/dss_capi`
