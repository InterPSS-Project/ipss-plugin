# QSTS Implementation To-Do

This to-do list expands `qsts-study-support-plan.md` into
implementation slices. Each slice is intended to be small enough to land with
focused tests before the next layer starts.

## Working Definition

QSTS means sequential quasi-static time-series simulation for three-phase
distribution feeders:

1. Build or import the feeder network once.
2. Preserve static network construction exactly as it works today.
3. Store time-series metadata as sidecar model objects.
4. For each requested step, apply scheduled source/load/DER/control state.
5. Run the existing fixed-point distribution power-flow engine.
6. Record step results and optional reference-comparison CSVs.

The QSTS study runner, step context, state applier, result model, control queue,
and CSV exporter should be generic three-phase distribution functionality. The
OpenDSS-specific part is the adapter layer: parsing OpenDSS `LoadShape`,
`daily/yearly/duty`, `Set`/`Solve` options, `RegControl`, `CapControl`, Reactor
syntax, and DSS-Python reference comparisons.

Dynamic simulation, event protection, and simultaneous multi-period OPF stay
out of scope for the first QSTS delivery.

## Current Codebase Anchors

Existing classes to build around:

- `org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser`
  - Owns feeder file traversal, redirects, object dispatch, source bus creation,
    and parser instances.
  - Parses `LoadShape` logical lines into sidecar OpenDSS time-series metadata.
  - Should expose a new read-only time-series metadata object, but should not run
    studies.
  - Static parser mode builds on the existing `Static3PNetwork`; QSTS must not
    use DStab network classes for static studies.
- `org.interpss.threePhase.dataParser.opendss.OpenDSSLoadParser`
  - Creates static phase loads in static parser mode and dynamic-compatible
    loads only for dynamic parser mode.
  - Already keeps `ParsedLoad` sidecar data for allocation-factor updates.
  - Captures base load values and profile bindings for QSTS through
    `IPhaseLoad` and sidecar schedule metadata.
- `org.interpss.threePhase.dataParser.opendss.OpenDSSRegulatorParser`
  - Maps regulator data into transformer/regulator branch state and emits
    QSTS control metadata without changing static import behavior when controls
    are off.
- `org.interpss.threePhase.dataParser.opendss.OpenDSSCapacitorParser`
  - Creates static capacitor representation.
  - Emits capacitor state/control metadata for QSTS.
- `org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm`
  and `org.interpss.threePhase.powerflow.impl.DistributionPowerFlowAlgorithmImpl`
  - Existing per-step solver.
  - Should remain a solver, not a scheduler.
  - Static PF Y-matrix construction uses generic three-phase bus/branch
    contracts and must not require DStab-only load or generator classes.
- `org.interpss.threePhase.util.ThreePhaseObjectFactory`
  - Existing construction helper for three-phase network objects and algorithms.
  - Add factory helpers only if the codebase already uses factory entry points
    for the object being created.

New packages should be narrow:

- `org.interpss.threePhase.qsts`
  - Generic study runner, step context, state applier, result model, schedules,
    device state abstractions, and CSV exporter.
  - Depends on `INetwork3Phase`, `IBus3Phase`, `IBranch3Phase`, `IPhaseLoad`,
    and `IPhaseGen`; no DStab imports in generic QSTS classes.
- `org.interpss.threePhase.qsts.control`
  - Generic control queue, control action, and control loop abstractions.
- `org.interpss.threePhase.dataParser.opendss.timeseries`
  - OpenDSS adapter metadata: LoadShape/profile binding objects, parsed OpenDSS
    solve options, and OpenDSS control metadata.
- `org.interpss.threePhase.qsts.opendss`
  - Optional adapter package if conversion from OpenDSS metadata to generic QSTS
    schedules grows beyond simple factory methods.

Architecture status:

- Static/QSTS/PF uses the existing `Static3PNetwork`; do not create a parallel
  static network model.
- `IPhaseLoad` and `IPhaseGen` are the shared phase-vector device contracts for
  single-, two-, and three-phase devices.
- `IPhaseLoad` exposes both `getId()` and `setId(String)` so parser/factory
  code can stay on the generic phase-load boundary.
- `IStaticPhaseLoad`/`IStaticPhaseGen` specialize static devices;
  `IDynamicPhaseLoad`/`IDynamicPhaseGen` specialize dynamic devices.
- OpenDSS-specific metadata is adapter sidecar data. The QSTS engine consumes
  generic schedules, base states, controls, and phase-device interfaces.

## Slide 1: Scope Lock and Reference Notes

Goal: make the first implementation unambiguous before writing behavior.

To do:

- [ ] Add a source-behavior note table to
  `opendss-feeder-benchmark-findings.md` or a dedicated
  `opendss-qsts-reference-notes.md`.
- [ ] Record OpenDSS/DSS-Extensions behavior for `LoadShape`, `loadmult`,
  `status=fixed|variable|exempt`, `controlmode`, `maxcontrol`, and `hour`.
- [ ] Record what v1 supports versus explicitly reports as unsupported.
- [ ] Define v1 interpolation behavior: interval-indexed lookup first, with
  `hour` array behavior documented once verified.
- [ ] Define tolerances for DSS-Python comparison tests:
  - Load terminal P/Q: start at `1.0E-5` per-unit or equivalent kW/kvar
    tolerance after base conversion is confirmed.
  - Bus voltage magnitude: start at `1.0E-4` pu for mini cases.
  - Feeder smoke tests: report errors first; do not make broad feeders hard
    gating until static residual sources are understood.

Verification:

- [ ] Notes identify each OpenDSS behavior source used for QSTS semantics.
- [ ] Unsupported v1 behavior has a planned diagnostic path, not silent ignore.
- [ ] The plan still keeps static OpenDSS parsing and PF behavior unchanged.

## Slide 2: Generic QSTS Model and OpenDSS Metadata Adapter

Goal: add generic QSTS model objects and OpenDSS metadata storage without
applying time-series behavior.

Create:

- [x] `QstsScheduleData`
  - Generic schedules, base state stores, global study defaults, and control
    metadata references.
- [x] `QstsProfile`
  - Generic profile with id, time axis, P multipliers, Q multipliers, and
    diagnostics.
- [x] `QstsProfileRegistry`
  - Generic case-insensitive or adapter-configured profile lookup.
- [x] `QstsProfileBinding`
  - Generic device class, device id, selected profile ids, and status.
- [x] `QstsDeviceStatus`
  - Generic enum: `DEFAULT`, `FIXED`, `VARIABLE`, `EXEMPT`.
- [x] `QstsGlobalOptions`
  - Generic mode, number of steps, step size, load multiplier, control mode, and
    max control iterations.
- [x] `OpenDSSTimeSeriesData`
  - OpenDSS adapter object holding parsed `LoadShape`, `Set`/`Solve`, load
    binding, and later control metadata.
  - Lives under `dataParser/opendss/timeseries`.
- [x] `OpenDSSLoadShape`
  - OpenDSS parser-side representation or adapter subclass for `QstsProfile`.
  - Fields specific to OpenDSS: `npts`, `interval`, `sinterval`, `minterval`,
    `hour`, source metadata, and unsupported-form diagnostics.
- [x] `OpenDSSShapeRegistry`
  - OpenDSS adapter registry that can convert into `QstsProfileRegistry`.
- [x] `OpenDSSProfileType`
  - Enum: `DAILY`, `YEARLY`, `DUTY`.
- [x] `OpenDSSProfileBinding`
  - OpenDSS adapter binding with `dailyShapeId`, `yearlyShapeId`,
    `dutyShapeId`, and OpenDSS status text mapped to `QstsDeviceStatus`.
- [x] `OpenDSSGlobalTimeSeriesOptions`
  - OpenDSS adapter options that convert `mode`, `number`, `stepsize`,
    `loadmult`, `controlmode`, `maxcontrol`, and `hour` into `QstsGlobalOptions`.

Update:

- [x] `OpenDSSDataParser`
  - Add private `OpenDSSTimeSeriesData timeSeriesData`.
  - Add `getTimeSeriesData()`.
  - Pass `LoadShape` logical lines to a new parser instead of dropping them.
  - [ ] Parse supported `set` and `solve` options into metadata only.
- [x] `OpenDSSLoadParser`
  - Record `daily`, `yearly`, `duty`, and `status` on
    `OpenDSSProfileBinding`.
  - Keep current parser-created load behavior unchanged for static PF, while
    QSTS consumes the resulting objects through `IPhaseLoad`; legacy
    `ILoad1Phase` and `ILoad3Phase` are compatibility boundaries only.

Relationship with existing code:

- `OpenDSSDataParser` remains the only feeder parser entry point.
- Generic QSTS metadata references existing network objects by InterPSS object
  identity or generic device id. OpenDSS ids are converted at the adapter
  boundary.
- No generic QSTS class should parse OpenDSS syntax or construct feeder
  topology.
- `DistributionPowerFlowAlgorithmImpl` is not touched in this slice.

Verification:

- [x] Unit tests for registry case-insensitive lookup through schedule conversion.
- [x] Unit tests for interval conversion:
  - `interval=1` means one hour.
  - `sinterval=3600` means one hour.
  - `minterval=60` means one hour.
- [x] Unit tests for array length validation:
  - `mult` supplies both P and Q multipliers.
  - `pmult/qmult` can differ but must match `npts` or documented fallback.
  - `hour` length matches multiplier length when present.
- [ ] Regression command:

```bash
mvn -pl ipss.plugin.3phase test -Dtest=TestOpenDSSDataParser
```

## Slide 3: LoadShape Parser

Goal: parse the LoadShape forms needed by real feeders and mini tests.

Create:

- [x] `OpenDSSLoadShapeParser`
  - Parses `New LoadShape.<id> ...` logical lines.
  - Supports inline arrays with parentheses or brackets.
  - Supports comma-separated and whitespace-separated numeric arrays.
  - Supports `mult`, `pmult`, `qmult`, `hour`, `npts`, `interval`,
    `sinterval`, `minterval`, and simple `csvfile`.
- [x] `OpenDSSParserDiagnostic`
  - Optional small value object for unsupported shape/file forms.

Update:

- [x] `OpenDSSDataParser`
  - In both master-file and redirected-file paths, replace the current
    `LoadShape` no-op with `loadShapeParser.parse(...)`.
  - Pass base folder and current file name so relative `csvfile` paths resolve
    consistently.

Relationship with existing code:

- Reuse token/list parsing patterns already present in `OpenDSSLoadParser` and
  `OpenDSSDataParser`.
- Keep parser diagnostics in `OpenDSSTimeSeriesData`; do not log unsupported
  time-series features as static parser failures unless the syntax is malformed.

Verification:

- [x] Mini parser tests for inline:
  - `mult=(0.5 1.0 1.2)`.
  - `pmult=(0.5,1.0) qmult=(0.4,0.9)`.
  - `hour=(0 1 2) mult=(1 0.9 1.1)`.
- [x] Mini parser tests for CSV files.
- [ ] Ckt7 and Ckt24 smoke parser tests assert expected nonzero shape counts.
- [x] Static parser regression remains green through DSS-Python-backed mini load-model comparison:

```bash
mvn -pl ipss.plugin.3phase test -Dtest="OpenDssParserPowerFlowComparisonTest#openDssLoadModelsMiniCaseMatchesDssPythonReference"
```

## Slide 4: Generic Load/Profile Binding and Base State

Goal: compute effective per-step load powers without losing nameplate/base
values.

Create:

- [x] `QstsLoadBaseState`
  - Stores base CP/CI/CZ or base three-phase load values.
  - Stores load code, phase code, connection type, model code, CVR parameters,
    ZIPV coefficients, nominal kV, and binding ids.
- [x] `QstsLoadStateStore`
  - Maps `IPhaseLoad` static load contracts to base state.
  - Keeps legacy `ILoad1Phase` and `ILoad3Phase` adaptation only at parser,
    test, or dynamic compatibility boundaries.
  - Provides restore/apply APIs.
- [x] `QstsLoadMultiplierResolver`
  - Resolves active profile by generic study mode.
  - Applies generic device status and global load multiplier rules.
  - Produces independent P/Q multipliers.
- [x] `OpenDSSLoadProfileAdapter`
  - Maps `daily/yearly/duty` and OpenDSS status semantics into generic QSTS
    binding/status/profile selection.

Update:

- [x] `OpenDSSLoadParser`
  - Register `QstsLoadBaseState` after load creation through the OpenDSS
    adapter.
  - Register profile bindings from load properties.
  - Extend `parseLoadPropertyData(...)` for post-creation `daily/yearly/duty`
    and `status` assignments, similar to existing `AllocationFactor` handling.
- [x] `OpenDSSTimeSeriesData`
  - Own or convert to a generic `QstsScheduleData` load-state store and expose
    read-only OpenDSS binding access.

Relationship with existing code:

- QSTS depends on `IPhaseLoad`, not DStab load classes. Legacy
  `ILoad1Phase`/`ILoad3Phase` objects are compatibility surfaces for parser,
  test, or dynamic-side code and should not be required by static QSTS.
- Base-state preservation is a sidecar first. Static phase load/generator
  interfaces expose the steady-state setters needed by both static and dynamic
  implementations.
- Voltage-response model handling remains in the current load model; QSTS
  changes scheduled nominal P/Q before the existing voltage-response behavior is
  evaluated. OpenDSS-specific status/profile semantics stay in the adapter.

Verification:

- [x] Unit tests for effective P/Q under:
  - `mult` only.
  - independent `pmult/qmult`.
  - global `loadmult`.
  - `status=fixed`.
  - `status=variable`.
  - `status=exempt` documented fallback.
- [x] Static PF before and after creating time-series metadata produces the same
  load values and voltages when no QSTS study is run.
- [x] Focused command:

```bash
mvn -pl ipss.plugin.3phase test -Dtest="QstsLoadStateStoreTest,OpenDssTimeSeriesMetadataTest"
mvn -pl ipss.plugin.3phase test -Dtest="OpenDssParserPowerFlowComparisonTest#openDssLoadModelsMiniCaseMatchesDssPythonReference"
```

## Slide 5: Generic QSTS Study Runner

Goal: run sequential fixed-point PF over a requested time range.

Create:

- [x] `QstsStudy`
  - Entry point: `from(INetwork3Phase, QstsScheduleData)`.
  - Options: mode, start index, number of steps, step size, PF method,
    loadmult override, controls mode, max control iterations.
  - Method: `QstsResult run()`.
- [x] `QstsMode`
  - Enum: `SNAPSHOT`, `DAILY`, `YEARLY`, `DUTY`.
- [x] `QstsControlMode`
  - Enum: `OFF`, `STATIC`, `TIME`, `EVENT`, `FROZEN`.
  - Implement only `OFF` and `FROZEN` initially unless source research justifies
    more.
- [x] `QstsStepContext`
  - Step index, absolute hour, mode, step size, global multipliers, and
    diagnostics.
- [x] `QstsStateApplier`
  - Restores base load state.
  - Applies scheduled effective load state.
  - Applies scheduled static generator injections.
  - Later applies source, capacitor, reactor, and tap states.
- [x] `QstsStepResult`
  - Converged flag, iteration count, max mismatch, failure reason, action count.
- [x] `QstsResult`
  - Ordered step results plus sampled result channels.
- [x] `QstsCsvExporter`
  - Bus voltage CSV first; load powers and branch currents next.
- [x] `OpenDSSQstsStudyFactory`
  - Adapter helper: `from(OpenDSSDataParser)` or
    `from(INetwork3Phase, OpenDSSTimeSeriesData)` returning `QstsStudy`.

Update:

- [x] `ThreePhaseObjectFactory`
  - Add a QSTS factory only if that is consistent with current factory usage.
- [x] `DistributionPowerFlowAlgorithm`
  - Avoid API changes unless iteration count or mismatch cannot be read
    otherwise. Prefer using existing getters first.
  - No API change was needed; the runner uses existing iteration/tolerance and
    warm-start voltage controls.

Relationship with existing code:

- `QstsStudy` owns the loop.
- `DistributionPowerFlowAlgorithmImpl` solves one network state at a time.
- `OpenDSSQstsStudyFactory` is the boundary between OpenDSS metadata and generic
  QSTS schedules.
- Generic QSTS code depends on `INetwork3Phase`, `IBus3Phase`, `IPhaseLoad`,
  and `IPhaseGen`; it must not depend on DStab classes. Legacy
  `ILoad1Phase`/`ILoad3Phase`/`IGen3Phase` access is adapter, test, or
  dynamic-side compatibility only.
- Warm start should reuse final bus voltages from the previous step by leaving
  solved voltages on the network, unless the solver initialization path forces
  a reset.

Verification:

- [ ] Two-bus daily-shape mini case:
  - 3 to 5 steps.
  - Controls off.
  - Compare bus voltage and load P/Q against checked-in DSS-Python CSV.
- [x] Failed-step test:
  - Force non-convergence with very low max iterations.
  - Assert the result includes step index, mode, hour, and failure reason.
- [x] CSV export test:
  - Stable header.
  - One row per step/bus/phase.
  - Numeric values finite or explicitly marked missing.
- [x] Focused command:

```bash
mvn -pl ipss.plugin.3phase test -Dtest="QstsStudyTest,QstsCsvExporterTest,OpenDSSQstsAdapterTest"
```

Result on 2026-06-06: focused QSTS suite
`mvn -pl ipss.plugin.3phase clean test -Dtest="*Qsts*,*OpenDSS*Qsts*" -Dsurefire.failIfNoSpecifiedTests=false`
passed with 21 tests, 0 failures, 0 errors.

## Slide 6: Control Queue Skeleton

Goal: introduce OpenDSS-like control loop plumbing before implementing real
regulator/capacitor behavior.

Create:

- [ ] `QstsControlModel`
  - Interface with `evaluate(QstsStepContext, QstsControlQueue)`.
- [ ] `QstsControlAction`
  - Device id, action type, scheduled time/hour, delay, priority, reason.
- [ ] `QstsControlQueue`
  - Priority/time ordered queue.
  - Tracks executed action history per step.
- [ ] `QstsControlLoop`
  - Runs solve/evaluate/apply/re-solve until queue is empty, `maxcontrol` is hit,
    or an intermediate solve fails.
- [ ] `QstsNoOpControlModel`
  - Test control model that proves queue mechanics without device semantics.

Update:

- [ ] `QstsStudy`
  - Add controls-off path that preserves TS4 behavior.
  - Add no-op/static skeleton path with loop guards.
- [ ] `QstsResult`
  - Add control action log and per-step final device-state channel.

Relationship with existing code:

- Control models should apply state through the same state applier/device-state
  abstractions used by scheduled QSTS state.
- No OpenDSS control-specific assumptions should be embedded in generic QSTS or
  `DistributionPowerFlowAlgorithmImpl`.

Verification:

- [ ] Controls-off results are bitwise or tolerance-identical to Slide 5.
- [ ] No-op queue produces zero actions and no extra solves.
- [ ] Scripted fake action changes a tracked state and exports action history.
- [ ] `maxcontrol` guard produces a non-success result with action history.

## Slide 6b: Base-Case Reuse and Parallel Execution

Goal: make the QSTS architecture fast enough for feeder-scale daily, monthly,
and yearly studies without reparsing or rebuilding the full network at every
step.

Create:

- [ ] `QstsBaseCase`
  - Immutable or read-mostly representation of the imported network topology,
    base load/device values, bus/branch indexes, phase mappings, and fixed
    solver ordering.
  - Built once from `INetwork3Phase`.
- [ ] `QstsNetworkSession`
  - Mutable worker-local execution state for one sequential study window.
  - Holds the working `INetwork3Phase` or a session view over mutable device
    state.
  - Restores base values and applies deltas without reparsing.
- [ ] `QstsStateDelta`
  - Compact per-step state change object for loads, DERs, source values, taps,
    capacitor/reactor states, and control actions.
- [ ] `QstsPreparedSchedule`
  - Pre-resolved arrays for P/Q multipliers, global multipliers, source
    schedules, and device-state schedules.
  - No parser strings or profile-name lookup in the hot solve loop.
- [ ] `QstsYBusFactorizationCache`
  - Cached `Ybus`, ordering/permutation, symbolic factorization, and numeric
    factorization for a compatible base case.
  - Reused across batches/windows when topology and admittance-bearing device
    states are unchanged.
- [ ] `QstsLinearSolveContext`
  - Worker-local solve wrapper using the cached factorization for repeated
    right-hand-side updates.
  - Supports fast per-step PF iterations when only injections/load multipliers
    change.
- [x] Remove compensation-current acceleration from the plugin
  - Deleted the `QstsControlCompensationPolicy` path and the regulator tap
    compensation/KCL/damping tests.
  - QSTS now enables the fixed-point `Ybus` cache only when controls are off.
  - Controls that change admittance, including capacitor switching and
    regulator tap changes, must update or rebuild the matrix rather than being
    approximated as fictitious terminal current injections.
  - Historical direct-compensation experiments were slower and less robust on
    IEEE123 than symbolic reuse plus in-place sparse-matrix value update:
    full rebuild `96 ms`, symbolic reuse with new matrix `32 ms`, in-place
    value update `20 ms`, direct compensation with `1.0E-6` pu virtual padding
    `124 ms`.
  - Regulator numeric rebuild now reuses the same sparse matrix object where
    possible: downstream regulator tap changes apply `Ynew - Yold` deltas to
    the existing `Yff/Yft/Ytf/Ytt` matrix blocks and then rerun numeric LU with
    the cached symbolic table. If a regulator touches the swing bus boundary,
    the implementation conservatively rebuilds the matrix because the
    swing-boundary RHS transformation also changes.
- [ ] `QstsStudyWindow`
  - Window definition: start step, number of steps, initial state policy, and
    output target.
- [ ] `QstsParallelStudyRunner`
  - Runs independent `QstsStudyWindow` instances with one
    `QstsNetworkSession` per worker.
  - Merges ordered result chunks after workers finish.

Update:

- [ ] `QstsStudy`
  - Run a single sequential window using `QstsNetworkSession`.
  - Accept a prepared schedule to avoid repeated profile lookup.
- [ ] `QstsStateApplier`
  - Apply a `QstsStateDelta` in place to the worker-local session.
  - Provide a restore path from `QstsBaseCase` or session base-state arrays.
- [ ] Fixed-point PF integration
  - Reuse `QstsYBusFactorizationCache` for all batches whose `Ybus` is unchanged.
  - Rebuild numeric factorization only when an admittance-changing delta is
    applied.
  - For controls that change admittance, update affected sparse-matrix values
    in place when the sparsity pattern is unchanged; otherwise rebuild.
  - Record per-step solve path: factorization reused, matrix values updated, or
    `Ybus` rebuilt/refactored.
- [ ] `QstsResult`
  - Support chunked or streaming result collection so yearly studies do not keep
    every bus/phase object in memory.

Relationship with existing code:

- `OpenDSSDataParser` is only used before `QstsBaseCase` and
  `QstsPreparedSchedule` are created.
- `DistributionPowerFlowAlgorithmImpl` still solves one session state at a time.
- Mutable InterPSS network objects are never shared across parallel workers.
- Monthly or weekly parallelism is safe only when each window has a defined
  initial-state policy:
  - reset-to-base for embarrassingly parallel scenario windows;
  - carry-in state from the prior window when exact sequential QSTS semantics are
    required;
  - warm-start seed from a previous run when approximate acceleration is
    explicitly accepted.

Performance rules:

- [ ] Parse feeder files once.
- [ ] Build bus/branch/device indexes once.
- [ ] Pre-resolve profile ids into numeric arrays once.
- [ ] Reuse radial ordering and matrix sparsity structure where topology is
  unchanged.
- [ ] Reuse `Ybus` and its factorization across batches when only load/source/
  injection values change.
- [ ] For capacitor/reactor controls, treat switching as an admittance-changing
  event and invalidate/update the matrix instead of using RHS current
  compensation.
- [x] Use symbolic factorization reuse plus in-place sparse-matrix value updates
  as the default regulator-tap QSTS acceleration method:
  - tap changes preserve the sparse structure, so the symbolic table is reused;
  - downstream regulator tap changes update only affected `Yff/Yft/Ytf/Ytt`
    matrix blocks in place and rerun numeric LU;
  - regulators touching the swing-bus boundary rebuild/reapply the
    swing-boundary transformation because the moved `Yns * Vs` RHS term changes;
  - direct regulator RHS compensation, damping, and virtual impedance padding
    were removed from the plugin after the IEEE123 experiments.
- [ ] Track factorization invalidation explicitly:
  - topology switching;
  - branch impedance changes;
  - transformer tap changes that alter `Ybus`;
  - capacitor/reactor admittance state changes;
  - voltage-source Thevenin impedance changes;
  - any control action that changes matrix coefficients.
- [ ] Avoid full network clone per step.
- [ ] Allow clone/session creation per parallel window, not per time step.
- [ ] Keep control queues worker-local.

Verification:

- [ ] Sequential baseline and one-window `QstsParallelStudyRunner` produce the
  same results.
- [ ] Parallel reset-to-base windows produce the same results as running those
  windows independently one at a time.
- [ ] Carry-in monthly windows produce the same final ordered result as one full
  sequential yearly run when exact carry-in mode is selected.
- [ ] Add a microbenchmark or timing test that reports:
  - parse/build time;
  - schedule preparation time;
  - per-step delta apply time;
  - per-step PF solve time;
  - result write time.
- [ ] Add a guard test proving two parallel windows do not share mutable load,
  bus, branch, solver, or control-queue objects.
- [ ] Add a factorization-cache test:
  - load multiplier changes reuse the same factorization;
  - DER/source injection changes reuse the same factorization;
  - capacitor/reactor admittance changes invalidate or update the matrix;
  - tap changes reuse symbolic factorization and update matrix values in place
    when the sparsity pattern is unchanged.
- [ ] Add matrix update accuracy tests:
  - [x] capacitor control states and terminal kvar match DSS-Python on OpenDSS
    cap-control mini feeders;
  - [x] Ckt24 capacitor-control QSTS converges with controls enabled and does
    not use the fixed `Ybus` cache;
  - [x] regulator tap symbolic-factorization reuse matches full numeric `Ybus`
    rebuild on the IEEE 13 daily QSTS regulator-control case;
  - [x] IEEE123 per-phase regulator QSTS policy matches full numeric `Ybus`
    rebuild while reusing symbolic factorization for repeated tap solves;
  - [x] IEEE13 and IEEE123 regulator tests assert in-place sparse-matrix value
    updates are used for downstream regulator tap changes;
  - [x] direct regulator compensation, damping, and virtual series-resistance
    padding experiment results are documented as removed paths;
  - [x] larger feeder smoke coverage passes after in-place sparse-matrix updates:
    IEEE123, Ckt7, Ckt24, Ckt24 capacitor comparison, IEEE123 regulator
    symbolic update, and IEEE8500 controls-off smoke cases.
  - unsupported control actions force rebuild/refactor and report the reason.

## Slide 7: DSS-Python Reference Harness

Goal: make QSTS behavior verifiable against executable OpenDSS semantics.

Create:

- [ ] `src/test/resources/opendss/qsts/mini/...`
  - Mini DSS feeders for each shape/status/model behavior.
- [ ] `src/test/resources/opendss-reference/qsts/...`
  - Checked-in DSS-Python output CSVs.
- [ ] `scripts/opendss_qsts_reference_export.py` or test helper location
  matching existing repo conventions.
  - Runs DSS-Python/OpenDSSDirect.
  - Exports bus voltages, load powers, branch currents, device states,
    convergence, and control actions when available.
- [ ] Java CSV comparison utility for QSTS tests.
- [ ] `OpenDSSQstsReferenceComparisonTest`
  - Adapter-specific tests comparing generic `QstsResult` to DSS-Python CSVs.

Mini cases:

- [ ] Uniform `mult`.
- [ ] Independent `pmult/qmult`.
- [ ] `loadmult` plus per-load shape.
- [ ] `status=fixed` versus `status=variable`.
- [ ] Model 1 constant power under shape.
- [ ] Model 4 CVR under shape.
- [ ] Model 8 ZIPV under shape.

Relationship with existing code:

- Reference files should be stable test resources, not generated during Maven
  tests.
- Exploratory DSS-Python generation can write to `target/`.

Verification:

- [ ] Maven tests compare Java results to checked-in CSVs.
- [ ] Reference generator has a documented command and deterministic output.
- [ ] Every mini case first compares load P/Q, then bus voltage.

## Slide 7b: Advanced Rapid-QSTS Extension Points

Goal: make the exact QSTS v1 architecture a foundation for later rapid-QSTS
methods such as event-based simulation, linear PF approximation, parallel
time-separable solves, voltage-drop time-series approximation, and reduced-order
feeder models.

Create:

- [ ] `QstsSolverStrategy`
  - Interface used by `QstsStudy` for exact fixed-point PF, linearized PF,
    voltage-drop approximation, and hybrid strategies.
- [ ] `QstsEventDetector`
  - Detects approximation-breaking events: voltage threshold crossing, load/PV
    ramp threshold, control threshold crossing, topology/admittance change, and
    approximation error bound violation.
- [ ] `QstsEventDrivenRunner`
  - Runs full PF at event points and uses approximation/interpolation during
    quiet intervals when the accuracy policy allows it.
- [ ] `QstsLinearSensitivityModel`
  - Stores sensitivities from injections/device states to voltage/current
    outputs around a solved base point.
- [ ] `QstsVoltageDropApproximation`
  - Approximate radial voltage update model for fast screening between exact
    solves.
- [ ] `QstsSolutionReuseStrategy`
  - Optional vector-quantization/nearest-state acceleration layer.
  - Reuses prior solved states when load/PV/control state features are within an
    accepted similarity threshold.
  - Must be implemented as an approximate strategy, not as part of the exact
    `QstsStudy` core.
- [ ] `QstsReducedOrderFeederModel`
  - Equivalent model for feeder sections behind boundary buses.
- [ ] `QstsAccuracyPolicy`
  - Error tolerance, required exact-solve cadence, event thresholds, and
    fallback-to-exact rules.

Relationship with existing code:

- Exact `QstsStudy` remains the reference path.
- Advanced strategies reuse `QstsBaseCase`, `QstsPreparedSchedule`,
  `QstsStateDelta`, result channels, and the verification harness.
- Approximate strategies must not live in the OpenDSS adapter.
- Vector-quantization and ML-style solution reuse do not change the core PF or
  control algorithms. They sit above exact QSTS as optional shortcuts with
  explicit error accounting.
- Reduced-order feeders must preserve boundary quantities needed by downstream
  full feeder regions: voltage, current, P/Q, and device/control state exposure.

Verification:

- [ ] Exact strategy and existing `QstsStudy` produce identical results.
- [ ] Linear sensitivity and voltage-drop approximation are always compared
  against exact PF on mini feeders before use on large feeders.
- [ ] Event-driven runner falls back to exact PF when approximation error or
  control threshold rules are violated.
- [ ] Solution-reuse strategy reports cache hit rate, skipped exact solves, and
  voltage/control-action error versus exact QSTS.
- [ ] Reduced-order feeder model has boundary-equivalence tests against the full
  feeder model.
- [ ] Performance reports separate speedup from approximation error:
  - exact PF solve count reduction;
  - total runtime reduction;
  - max/percentile voltage error;
  - missed control event count;
  - reduced-order boundary P/Q error.

## Slide 8: Feeder Smoke Studies

Goal: prove the implementation scales beyond mini cases without letting broad
feeders hide device-level bugs.

Create:

- [ ] `QstsFeederSmokeTest`
  - Generic smoke-test base/helper for imported feeders.
- [x] `OpenDSSQstsFeederSmokeTest`
  - OpenDSS adapter/reference implementation of the smoke tests.
  - IEEE13 24-step daily scheduled-profile regression is already covered by
    `OpenDssIeee13DailyQstsProfileTest`.
  - [x] Ckt7 first 24 yearly steps, controls off, as the first large feeder with
    real OpenDSS load-shape bindings.
  - [x] IEEE123 first 24 repeated-state steps, controls off, to protect topology
    and static-QSTS integration while regulator/capacitor controls remain under
    staged implementation.
  - [x] Ckt24 first 24 repeated-state steps, controls off, using the InterPSS static
    fixture until a supported annual/scheduled fixture is added. The near-zero
    OpenDSS busbar branch `subxfmr_lsb->05410(1)` is protected by the parser
    line-impedance floor so static fixed-point setup no longer sees zero Yii
    diagonal elements.
  - [ ] Ckt24 low-load scheduled yearly window with static controls enabled by
    default. Controls-off runs must use the explicit diagnostic override
    (`--allow-disabled-controls` for DSS-Python or
    `-Dqsts.compare.allowDisabledControls=true` for InterPSS):
    - profile files in `testData/feeder/Ckt24`:
      `LS_PhaseA.txt`, `LS_PhaseB.txt`, `LS_PhaseC.txt`,
      `LS_ThreePhase.txt`, and `Other_Bus_Load.txt`;
    - individual allocated-load shapes reach their minimum near hour 6540,
      while `Other_Bus_Load` reaches its minimum near hour 6987;
    - selected low-variation windows from the existing profiles:
      24-hour window `startIndex=6601` / OpenDSS `hour=6602`, and
      168-hour window `startIndex=6573` / OpenDSS `hour=6574`;
    - use these windows to exercise larger voltage movement than the repeated
      state smoke while staying within the real Ckt24 loadshape data.
    - 2026-06-11 controlled one-step status: transformer no-load shunt support
      reduced branch-flow mismatch to `maxPDelta=3.25626791 kW` with no P
      failures; remaining branch-flow mismatch is reactive-only
      (`maxQDelta=22.57404852 kvar`, `qFailures=428`) and is the next
      upstream phasor/modeling slice.
  - [x] IEEE8500 short repeated-state window, controls off, enabled as a legacy
    runtime sentinel before promoting controlled comparison/performance runs.
  - Optional 168-step Ckt7/Ckt24 run disabled or tagged until runtime is
    acceptable.
- [x] `OpenDSSQstsComparisonSummary`
  - Convergence, step count, runtime, voltage sample count, and iteration
    summary for smoke runs.
  - Worst voltage error by step/phase once a DSS-Python reference window is
    checked in.
  - Worst load P/Q error by step/load once a DSS-Python reference window is
    checked in.

Update:

- [ ] `opendss-feeder-benchmark-findings.md`
  - Add QSTS section with feeder residuals and suspected static-model causes.

Relationship with existing code:

- Feeder smoke tests should use the same generic study API as mini tests. The
  OpenDSS parser is only the source of network and schedule metadata for these
  specific fixtures.
- Generated comparison CSVs and plots belong under `target/load-comparison` or
  another `target/` path, not under source control unless they become fixtures.

Verification:

- [x] Controls-off selected feeder window converges every step for IEEE123,
  Ckt7, Ckt24, and IEEE8500 short-window coverage.
- [x] Every recorded voltage sample is finite and the maximum magnitude remains
  below a broad smoke ceiling. Do not assert tight feeder tolerances until a
  DSS-Python reference window is available.
- [x] Smoke output reports iteration counts and runtime for larger feeders so
  later performance slices can compare factorization reuse, monthly partitioning,
  and parallel execution.
  - Initial enabled QSTS smoke evidence:
    Ckt7 yearly 24 steps converged with max iteration count 5 and max voltage
    1.05 pu; IEEE8500 repeated-state 6 steps converged with max iteration count
    15 and max voltage 1.05 pu; IEEE123 repeated-state 24 steps converged with
    max iteration count 3 and max voltage 1.0 pu; Ckt24 repeated-state 24 steps
    converged with max iteration count 6 and max voltage 1.05 pu.
  - Static DSS-Python comparison gate also passed for IEEE123, IEEE8500, and
    Ckt7 after adding the smoke tests.
- [ ] DSS-Python reference windows are added in this order: Ckt7 first 24 yearly
  steps, Ckt24 low-load scheduled window, IEEE8500 selected window, then
  control-enabled windows.
- [ ] Worst errors are stable across repeated runs.
- [ ] Any hard assertion uses a tolerance justified by mini-case and static
  feeder residual evidence.

## Slide 8b: Hosting-Capacity Metrics Layer

Goal: support PV hosting-capacity studies as a metrics/application layer on top
of QSTS without changing the core solver.

Create:

- [ ] `QstsMetricCollector`
  - Streaming interface that consumes per-step bus, branch, load, DER, and
    control-state channels.
- [ ] `QstsVoltageViolationMetric`
  - Instantaneous max/min voltage.
  - ANSI Range A moving-average duration.
  - ANSI Range B instantaneous violations.
- [ ] `QstsThermalLoadingMetric`
  - Instantaneous and moving-average line/transformer loading.
- [ ] `QstsControlOperationMetric`
  - Regulator tap actions, capacitor state changes, and other control operation
    counts.
- [ ] `QstsHostingCapacityResult`
  - First violation, violation duration, limiting metric, penetration level,
    and optional time-vector diagnostics.

Relationship with existing code:

- Metrics consume `QstsResult` chunks or streaming result channels.
- Metrics must not require storing all bus voltages and branch currents for a
  year-long run.
- Hosting-capacity scenario generation and PV deployment sampling should be a
  study/application layer, separate from exact QSTS execution.

Verification:

- [ ] Moving-average voltage metric reproduces hand-calculated windows on a
  small synthetic time series.
- [ ] Instantaneous violation metrics distinguish ANSI Range A duration from
  Range B excursions.
- [ ] Thermal metrics report both instantaneous and moving-average loading.
- [ ] Control operation counts match the QSTS control action log.

## Slide 9: Regulator and Transformer Tap Controls

Goal: add OpenDSS-compatible tap control after controls-off QSTS is stable.

Create:

- [ ] `OpenDSSRegControlData`
  - Parsed metadata: transformer, winding, controlled winding, `vreg`, `band`,
    `ptratio`, `ctprim`, `R`, `X`, monitored bus, delay, tap limits, tap
    increment, phase selection, reverse-mode hooks.
- [ ] `OpenDSSRegControlModel`
  - Monitored voltage calculation.
  - Line-drop compensation.
  - Deadband and tap-step rounding.
  - Tap limit enforcement.
  - Per-phase tap behavior for single-phase regulator banks.
- [ ] `OpenDSSTapState`
  - Current tap position, effective ratio, min/max, phase.
- [ ] `QstsTapChangingStrategy`
  - Generic strategy enum inspired by PowerGridModel-style operation:
    `DISABLED`, `FAST_STEP`, `ANY_VALID_TAP`, `MIN_VOLTAGE_TAP`,
    `MAX_VOLTAGE_TAP`, and `OPENDSS_EVENT_QUEUE`.
  - Keeps optimization-style tap search separate from OpenDSS event-queue
    emulation.
- [ ] `QstsTapSearchController`
  - Generic voltage-band tap search for non-OpenDSS studies.
  - Supports one-step/fast-step and binary-search style tap selection when
    control delay/event semantics are not required.

Update:

- [ ] `OpenDSSRegulatorParser`
  - Continue current static fixed-tap behavior.
  - Also register `OpenDSSRegControlData` in `OpenDSSTimeSeriesData`.
- [ ] `QstsStateApplier`
  - Expose a generic device-state application extension point for tap updates.
- [ ] OpenDSS tap-state adapter
  - Apply OpenDSS tap state to existing `DStab3PBranch` transformer/regulator
    objects.
- [ ] `QstsCsvExporter`
  - Export tap position and effective ratio per step.

Relationship with existing code:

- Tap state should update existing regulator transformer branch ratios.
- Existing fixed-tap static parser behavior remains valid when QSTS controls are
  off or frozen.
- OpenDSS `RegControl` behavior should live in the OpenDSS adapter/control
  model. The generic QSTS control loop only schedules and applies actions.
- PowerGridModel-style tap optimization is useful as a generic InterPSS strategy
  for study modes that want a valid voltage-band tap quickly. It should not
  replace OpenDSS-compatible `RegControl` emulation when validating imported DSS
  feeders, because OpenDSS uses delayed control-queue actions and re-checks the
  controller when the pending action executes.
- Binary-search tap selection can reduce power-flow/control iterations, but it
  changes operation-count semantics. Use it for fast planning/optimization
  studies, and use `OPENDSS_EVENT_QUEUE` for DSS parity and tap-operation
  counting.

Verification:

- [ ] DSS-Python mini cases:
  - Local voltage regulator.
  - Remote-bus regulator.
  - Line-drop compensated regulator.
  - Three parallel single-phase regulators with different taps.
- [ ] Compare tap positions first, then bus voltages.
- [ ] Controls-off and frozen-tap runs remain identical to pre-control QSTS.
- [ ] Strategy tests:
  - one-step control reaches the same final band as repeated OpenDSS-like static
    actions when delays are ignored;
  - binary-search control reaches an in-band tap with fewer PF solves where the
    tap-voltage relation is monotonic;
  - saturated tap cases report out-of-band-at-limit rather than failing
    silently;
  - `OPENDSS_EVENT_QUEUE` mode matches DSS-Python tap positions and event counts
    for reference feeders.

## Slide 10: Capacitor Controls

Goal: support OpenDSS capacitor switching behavior needed by QSTS feeders.

Create:

- [x] `CapacitorControlData` plus OpenDSS adapter metadata
  - Controlled capacitor, monitored element, terminal, control type, ON/OFF
    settings, CT/PT ratios, delays, voltage override, `Vmax`, `Vmin`,
    `CTPhase`, `PTPhase`.
- [ ] `CapacitorBankControl`
  - [x] Voltage, current, kvar, and PF mode state evaluation.
  - [x] Phase-selection behavior for `AVG`, `MIN`, `MAX`, and explicit phase.
  - [ ] Time mode evaluation.
  - [x] Delay-aware queued ON/OFF transitions.
  - [x] Static-control ON/OFF transitions for immediate control iterations.
- [x] `QstsCapacitorStateSample`
  - Generic closed/open state, terminal kvar, and operation count sample.
  - OpenDSS-specific section-state mapping remains adapter work if multi-section
    capacitor banks are needed.

Update:

- [x] `OpenDSSCapacitorParser`
  - Register capacitor phase metadata and parse `CapControl` metadata.
- [ ] `QstsStateApplier`
  - Expose a generic device-state application extension point for capacitor
    updates.
- [x] OpenDSS capacitor-state adapter
  - Apply OpenDSS capacitor state to the existing network representation.
- [x] `QstsCsvExporter`
  - Export capacitor state and kvar per step.

Relationship with existing code:

- Capacitor controls use solved voltage/current/power signals from the current
  network state, then schedule actions through `QstsControlQueue`.
- Static capacitor import stays unchanged when QSTS is not run.

Verification:

- [x] Unit mini cases:
  - Voltage ON/OFF switching against static three-phase capacitor load.
  - OpenDSS voltage and kvar/voltage-override metadata parsing.
- [x] DSS-Python mini cases:
  - Voltage ON/OFF switching.
  - Current switching.
  - kvar/PF switching.
  - `PTPhase` MIN/MAX behavior.
  - Voltage override.
- [x] DSS-Python mini cases:
  - `CTPhase` AVG/MAX/MIN behavior.
  - Explicit phase selection beyond PTPhase min/max.
- [x] Compare capacitor state and terminal powers before voltage assertions.

## Slide 11: Reactor Modeling and Switched State

Goal: support OpenDSS `Reactor` elements and any verified switched-reactor
behavior needed by reference feeders.

Create:

- [ ] `OpenDSSReactorParser`
  - Separate parser if reactor complexity grows beyond current
    `OpenDSSDataParser.parseReactorData(...)`.
- [ ] `OpenDSSReactorData`
  - kvar/kV, R/X, Z matrix, R/X matrices, sequence impedance, connection,
    shunt/series, enabled state.
- [ ] `OpenDSSReactorModel`
  - Yprim/state adaptation for InterPSS network representation.
- [ ] `OpenDSSControlledReactorState`
  - Enabled/open state and scheduled changes.

Update:

- [ ] `OpenDSSDataParser`
  - Route `Reactor` parsing through the dedicated parser once created.
- [ ] `QstsStateApplier`
  - Apply reactor enabled-state changes.
- [ ] `QstsCsvExporter`
  - Export reactor state, terminal powers, and currents.

Relationship with existing code:

- Start from the existing `parseReactorData(...)` behavior and extract it only
  when QSTS requires more state.
- Do not assume a standard `ReactorControl` object exists until source/reference
  cases verify the control mechanism.

Verification:

- [ ] DSS-Python mini cases:
  - Shunt wye reactor.
  - Delta or line-line reactor.
  - Series reactor.
  - Scheduled switched reactor.
- [ ] Compare Yprim-equivalent solved terminal powers before control behavior.

## Slide 12: Generator, PV, and Storage Extensions

Goal: extend static QSTS beyond loads using scheduled generator injections.
Do not use dynamic PV/DER device models in static QSTS studies.

Create:

- [x] `OpenDSSGeneratorModel`
  - OpenDSS adapter metadata for `PVSystem` and `Storage` parsed as static
    generators.
  - Retains PV configuration such as `Pmpp`, irradiance, kV, connection,
    cut-in/out, temperature, and curve ids.
  - Retains battery configuration such as state, kW/kWh ratings, stored energy,
    reserve, charge/discharge percentages, and efficiencies.
- [x] `QstsGeneratorBaseState`
  - Base P/Q, sign convention, phase mapping, and generator model fields.
- [x] `QstsGeneratorStateApplier`
  - Applies `Generator` and `PVSystem` scheduled injections.
  - Implemented as the generator path inside generic `QstsStateApplier`, using
    `IPhaseGen` base states rather than dynamic DER models.
- [x] `QstsGeneratorStateStore`
  - Sidecar base-state store for existing InterPSS `IPhaseGen` static
    generator objects, with legacy `IGen3Phase` adaptation only at the network
    boundary.

Update:

- [x] Existing or new OpenDSS DER parsers for `PVSystem`.
- [x] OpenDSS `Storage` parser.
- [ ] OpenDSS `Generator` parser.
- [x] `OpenDSSTimeSeriesData` to store generator bindings.
- [x] `QstsCsvExporter` to export generator terminal powers.
  - Uses `QstsResult.getGeneratorPowers()` sampled from `IPhaseGen` by
    `QstsStudy`; no DStab generator casts are required.

Relationship with existing code:

- Scheduled PV/DER injections are represented through `IPhaseGen` generator
  objects in static studies. Legacy `IGen1Phase`/`IGen3Phase` objects are
  compatibility surfaces for parser, test, or dynamic-side code and should not
  be required by static QSTS. OpenDSS PV/storage configuration remains adapter
  metadata that drives static P/Q injections.
- Generator injections must use the same sign convention as existing InterPSS
  generator objects.
- Do not mix DER QSTS behavior into DistOPF schedule classes; QSTS is a
  sequential PF study, not a multi-period optimization model.

Verification:

- [x] PVSystem parser metadata and static generator mini test.
- [x] Storage parser metadata and static generator mini test.
- [x] PV duty-curve mini case with QSTS runner.
  - Covered by `OpenDSSQstsAdapterTest`, which verifies an OpenDSS PVSystem daily
    curve drives static `IPhaseGen` injections through the generic applier.
- [x] Generator terminal-power CSV exporter test.
- [x] Storage charge/discharge mini case with QSTS runner.
  - Covered by `QstsStorageBaseStateTest`, which verifies sequential
    charge/discharge carryover through `QstsStateApplier` and static
    `IPhaseGen` injections.
- [x] Per-step terminal powers match DSS-Python before bus voltage assertions.
  - `OpenDssQstsMultiStepReferenceTest` covers shaped load, PVSystem, and
    scheduled storage terminal P/Q with checked-in multi-step reference rows.

## Slide 12b: Control Implementation Sequence

Goal: turn the OpenDSS control roadmap into implementation slices without
blocking QSTS on DER/storage features whose static models are not ready yet.

Priority order:

1. Finish capacitor controls.
2. Add inverter controls after static PV/DER generator models expose the needed
   configuration and limits.
3. Add storage controls after the static storage generator model has state of
   charge, energy limits, charge/discharge modes, and per-step state updates.

### Slice 12b-1: Capacitor Control Completion

Status: active. This is the current control slice because capacitors already
have a static network representation as negative constant-Z shunt loads.

Create or update:

- [x] `CapacitorControlData`
  - Generic power-flow control data, not OpenDSS-specific.
  - Captures capacitor id, monitored element, terminal, control type, ON/OFF
    settings, CT/PT ratios, voltage override, limits, delays, and phase
    selection.
- [x] `CapacitorBankControl`
  - Applies immediate static-control ON/OFF decisions to the existing
    three-phase capacitor load representation.
  - Supports voltage, current, kvar, and PF decision signals.
  - Uses solved bus voltages and branch currents from the current PF state.
- [x] `OpenDSSCapacitorParser`
  - Parses `CapControl` into generic `CapacitorControlData`.
  - Keeps OpenDSS syntax and naming inside the adapter boundary.
- [x] `DistributionPowerFlowAlgorithm`
  - Exposes capacitor control registration and enable/disable hooks.
- [x] `OpenDSSQstsStudyFactory`
  - Passes parsed capacitor controls into the QSTS runner.
- [x] `QstsControlQueue`
  - Add delay-aware capacitor ON/OFF actions.
  - Preserve controls-off and immediate static-control modes.
- [x] `QstsCapacitorStateSample`
  - Track closed/open state, terminal kvar, and operation count.
- [x] `QstsCsvExporter`
  - Export per-step capacitor state, kvar, and operation count.
  - Supports exporting capacitor state samples directly from `QstsResult`.

Verification:

- [x] Unit test static voltage ON/OFF switching on a three-phase capacitor.
- [x] Unit test delayed capacitor queue scheduling and cancellation.
- [x] Unit test QSTS delayed capacitor queue operation count and per-step
  capacitor state export.
- [x] Unit test generic control queue replacement/cancellation.
- [x] Unit test capacitor state CSV export.
- [x] Unit test OpenDSS voltage and kvar/voltage-override metadata parsing.
- [x] Regression: IEEE123 regulator comparison remains unchanged after
  capacitor-control hooks.
- [x] Regression: Ckt7 and IEEE8500 parser/PF comparisons still pass with
  `CapControl` files present.
- [x] DSS-Python mini case: voltage ON/OFF switching, compare capacitor state
  and terminal powers before bus voltages.
- [x] DSS-Python mini case: current switching.
- [x] DSS-Python mini case: kvar and PF switching.
  - Kvar-open and PF-open state and terminal power comparisons are covered by
    `OpenDssCapControlMiniComparisonTest`.
- [x] DSS-Python mini case: `PTPhase` MIN/MAX phase selection.
- [x] DSS-Python mini case: `CTPhase` AVG, MIN, MAX and explicit phase
  selection beyond PTPhase min/max.
- [x] DSS-Python mini case: voltage override.
- [x] DSS-Python mini case: delayed control-queue operation count.
  - Covered by `OpenDssCapControlMiniComparisonTest` using
    `DelayedHighVoltageOpen.dss` and
    `capcontrol-delayed-dss-python-operation-reference.csv`.

Exit criteria:

- Controls-off QSTS remains identical to the pre-control baseline.
- Immediate static capacitor control reaches a stable capacitor state without
  oscillation or silent max-control failure.
- DSS-Python mini cases match capacitor states and terminal powers before
  voltage comparison is used as an acceptance signal.

### Slice 12b-2: Inverter Control Foundation

Status: next after capacitor control. Implement only after static PV/DER
generator models expose the required inverter fields. Do not use dynamic DER
classes in QSTS.

Create or update:

- [x] Static DER/inverter capability model on top of `IPhaseGen`
  - Rated kVA, available P, Q limits, PF limit, voltage curve ids, cut-in/out
    flags, and per-phase capability.
  - Enough fields to evaluate inverter controls without dynamic models.
  - Uses `IPhaseGen` directly; legacy `IGen1Phase` and `IGen3Phase` objects are
    compatibility surfaces only.
- [x] `InverterControlData`
  - Generic data for active control modes and curve references.
  - Must not depend on OpenDSS syntax.
- [x] `InverterGenAdapter` or equivalent static inverter adapter
  - Keeps `IPhaseGen` as the basic phase-generator contract.
  - Wraps an `IPhaseGen` plus PV/storage/generator metadata needed to determine
    inverter power injection.
  - [x] Calls `InverterControlModel` only after it has resolved a generic target
    P/Q/PF setpoint.
  - [x] Terminal voltage interpretation and generic curve-to-setpoint
    evaluation for `VOLTVAR`, `VOLTWATT`, `WATTPF`, and `WATTVAR`.
  - [x] Own richer capability lookup, available P, Q limits, and cut-in/out
    state from PV/storage/generator metadata.
- [x] `QstsInverterAdapterStore`
  - Sidecar registry keyed by `IPhaseGen` or generator id.
  - Lets QSTS find inverter-capable static generators without adding inverter
    methods to `IPhaseGen`.
  - OpenDSS adapter can populate this store from `PVSystem`, `Storage`, and
    `InvControl` metadata; non-OpenDSS adapters can provide their own metadata.
- [x] `OpenDSSInvControlParser`
  - Adapter for OpenDSS `InvControl`.
  - Parse only the modes we can verify with static generator models.
  - Converts OpenDSS per-unit curve semantics at the adapter boundary:
    `VOLTVAR` and `WATTVAR` reactive-power ordinates scale by inverter kvar
    base, `VOLTWATT` active-power ordinates scale by available/rated kW, and
    `WATTPF` watt abscissas scale by active-power base.
- [x] `InverterControlModel`
  - Static PF setpoint updates for:
    - [x] `VOLTVAR`
    - [x] `VOLTWATT`
    - [x] `WATTPF`
    - [x] `WATTVAR`
  - [x] Enforce kVA capability and generator sign convention.
  - This slice implements generic setpoint application primitives on
    `IPhaseGen`; OpenDSS curve evaluation and QSTS control-loop invocation
    remain separate adapter/integration work.
- [x] QSTS state/result integration
  - Apply resolved inverter P/Q/PF setpoint updates after each PF solve.
  - Export inverter control mode, P/Q setpoint, limit status, and operation
    reason.
  - Current implementation is a direct generic bridge over `IPhaseGen` and
    resolved setpoints. It should be migrated behind `InverterGenAdapter`
    before OpenDSS curve behavior or richer inverter-specific logic is added.

Verification:

- [x] Unit tests for capability limiting and sign convention.
- [x] Unit tests for `VOLTVAR`, `VOLTWATT`, `WATTPF`, and `WATTVAR` static
  setpoint primitives on one-, two-, and three-phase `IPhaseGen` objects.
- [x] Unit tests for OpenDSS `InvControl` metadata mapping into generic
  `InverterControlData`.
- [x] Unit test QSTS applies resolved inverter setpoints through static
  `IPhaseGen` and exports control samples.
- [x] Unit tests for inverter adapter/store dispatch without extending
  `IPhaseGen`.
- [x] Unit tests for inverter adapter terminal-voltage and watt-driven
  curve-to-setpoint evaluation.
- [x] Unit tests for inverter adapter capability resolution from static
  PV/storage/generator metadata.
- [x] Concrete OpenDSS `XYCurve` parser path
  - Stores `XYCurve` data as generic `QstsControlCurve` records, independent
    of OpenDSS after parsing.
  - Applies curves to already-registered and later-registered
    `InverterGenAdapter` instances, so file order does not matter for
    `InvControl` curve references.
- [x] Official OpenDSS `PVSystem` example metadata coverage
  - Parses PV `P-TCurve`, efficiency `XYCurve`, irradiance `LoadShape`, and
    temperature `TShape`/`TDaily` metadata from the EPRI PVSystem example.
  - Computes static adapter available active power from `Pmpp`, irradiance,
    `%Pmpp`, P-T curve, and efficiency curve when `kw` is not explicitly set.
  - Keeps the OpenDSS PV details in parser/adapter metadata; QSTS control logic
    continues to operate through generic `IPhaseGen`, `InverterGenAdapter`, and
    `InverterCapabilityData`.
- [x] DSS-Python mini cases for `VOLTVAR`, `VOLTWATT`, `WATTPF`, and `WATTVAR`.
  - `OpenDSSInvControlMini` stores compact PVSystem-based feeders for each
    mode.
  - `invcontrol-mini-dss-python-generator-reference.csv` compares terminal
    generator P/Q before bus voltages.
  - QSTS now iterates PF/control after inverter setpoint changes. The WATTPF
    and WATTVAR direct setpoints match tightly; VOLTVAR/VOLTWATT still use
    looser tolerances while OpenDSS PV terminal-control details are
    investigated beyond the generic loop.
- [x] Compare generator terminal P/Q before bus voltages for PVSystem.
  - `OpenDSSPVSystemMini` uses the official EPRI PVSystem example curves and
    a DSS-Python reference CSV for generator injection P/Q.
  - The reference preserves OpenDSS terminal-power sign conversion by comparing
    against positive generator injection in QSTS.
- [x] QSTS PV duty-curve case with inverter controls enabled.
  - Parser-side coverage now includes the concrete IEEE8500
    `P174_Run_360kW_PV.DSS` pattern:
    `Generator.G1` plus `LoadShape.PVCurve` plus `generator.g1.duty=PVcurve`.
  - Parser/adapter-side coverage also includes the official OpenDSS `PVSystem`
    example with `MyPvsT`, `MyEff`, `MyIrrad`, and `MyTemp`.
  - QSTS acceptance is covered by `DutyWattPF.dss` and
    `invcontrol-duty-qsts-dss-python-generator-reference.csv`, which compare
    per-step terminal P/Q with inverter controls enabled.
- [x] Integrate inverter control into the QSTS control iteration loop.
  - After each PF solve, evaluate inverter controls against solved terminal
    voltages and watt output.
  - If any inverter P/Q/PF setpoint changes beyond tolerance, re-run PF until
    setpoints and network state stabilize or `maxControlIterations` is reached.
  - Preserve controls-off and one-shot behavior for tests that intentionally
    disable static controls.
  - Remaining VOLTVAR/VOLTWATT tolerance reflects OpenDSS PV terminal-control
    semantics, not the absence of QSTS PF/control re-solve.

Exit criteria:

- Static PV/DER QSTS does not reference any DStab dynamic model classes.
- Controls-off scheduled PV behavior remains unchanged.
- Inverter P/Q setpoints and capability clipping match DSS-Python mini cases.

### Slice 12b-3: Storage Control Foundation

Status: active foundation complete for scheduled dispatch. Storage controller
behavior remains deferred until reference behavior and mini cases are defined.

Create or update:

- [x] Static storage model fields
  - kW/kVA rating, kWh rating, stored energy, reserve, charge/discharge limits,
    efficiency, state, and dispatch mode.
- [x] `QstsStorageBaseState`
  - Base energy and dispatch state separate from normal generator P/Q state.
- [x] `QstsStorageStateStore`
  - Sidecar store keyed by static `IPhaseGen`, so storage can be detected
    without DStab classes or dynamic DER models.
- [x] Storage state applier
  - Per-step energy integration and charge/discharge clipping.
  - [x] Generator injection update using the same static `IPhaseGen` path.
  - [x] Wire scheduled storage dispatch through `QstsStateApplier`.
  - Skips storage devices in the generic generator multiplier path and applies
    energy-limited dispatch with deterministic state carryover.
- [x] `StorageControlData`
  - Generic controller configuration for storage dispatch.
- [ ] OpenDSS `StorageController` adapter
  - Parse after reference behavior is inspected and mini cases are defined.

Verification:

- [x] Unit tests for energy integration, reserve limits, efficiency, and
  charge/discharge sign convention.
- [x] Unit test for scheduled storage dispatch through `QstsStateApplier`.
- [x] Unit tests for generic storage-control configuration normalization.
- [x] DSS-Python mini cases for scheduled charge/discharge without controller.
- [ ] DSS-Python mini cases for `StorageController` only after the static model
  and parser support are complete.
- [x] Compare storage terminal P/Q before bus voltages.
- [x] Add DSS-Python state-of-charge reference rows for the scheduled
  charge/discharge mini case.
  - `storage-mini-dss-python-soc-reference.csv` compares cumulative stored kWh
    and SOC percent through the static parser-owned `QstsStorageStateStore`.

Exit criteria:

- Storage QSTS has deterministic energy-state carryover between steps.
- Controls-off storage scheduling works before any `StorageController` behavior
  is added.
- `StorageController` is not implemented against an incomplete storage model.

## Slide 12c: Static Phase-Device Boundary Migration

Goal: move static network, parser, and QSTS boundaries to expose
`IPhaseLoad` and `IPhaseGen` directly, with DStab classes only on the dynamic
study side.

Create or update:

- [x] `IPhaseGen`
  - Common phase-vector generator contract for 1P, 2P, and 3P devices.
- [x] `IStaticPhaseGen`
  - Static PF/QSTS generator specialization.
- [x] `IDynamicPhaseGen`
  - Dynamic generator specialization; DStab generator classes can implement
    this without becoming part of static algorithms.
- [x] `IAclfPhaseGen`
  - ACLF-bound phase generator for objects that are both `AclfGen` and
    phase-aware.
- [x] Remove plugin-local `StaticPhaseGenAdapter`
  - The temporary compatibility adapter is no longer needed after core
    phase-device views were added.
- [x] `IPhaseLoad`
  - Common phase-vector load contract for 1P, 2P, and 3P devices, including
    load model code, connection, nominal kV, and CP/CI/CZ access where
    applicable.
  - Includes `getId()` and `setId(String)` so parser, factory, and QSTS code can
    stay on the generic phase-load boundary.
- [x] `IStaticPhaseLoad`
  - Static PF/QSTS load specialization.
- [x] `IDynamicPhaseLoad`
  - Dynamic load specialization; DStab load classes can implement this without
    becoming part of static algorithms.
- [x] `IAclfPhaseLoad`
  - ACLF-bound phase load for objects that are both `AclfLoad` and
    phase-aware.
- [x] Remove plugin-local `StaticPhaseLoadAdapter`
  - The temporary compatibility adapter is no longer needed after core
    phase-device views were added.

Migration to do:

- [x] Update static network bus APIs to expose phase-device lists as
  `List<IPhaseLoad>` and `List<IPhaseGen>` or equivalent read-only views.
- [x] Keep DStab network and bus APIs typed to DStab dynamic classes only for
  dynamic studies.
  - `OpenDSSStaticDataParser` does not expose or accept a `DStabNetwork3Phase`.
  - `OpenDSSQstsStudyFactory` rejects dynamic OpenDSS parsers and operates on
    the parser's `Static3PNetwork`.
  - Static PF voltage update/positive-sequence helpers now use the generic
    `IBus3Phase` phase-voltage contract instead of DStab bus special cases.
- [x] Update OpenDSS parser-created static load/PV/storage paths to add devices
  through `IPhaseLoad` and `IPhaseGen` boundaries, not DStab-specific lists.
  - Static parser mode now builds these QSTS-relevant devices on the existing
    `Static3PNetwork` using `Static3PLoad` and `Static3PGen` phase views.
- [x] Add static parser topology support for OpenDSS circuit source, line, and
  capacitor devices.
  - Static parser mode now creates source buses, source impedance branches,
    ordinary lines, and capacitor shunt loads on the existing `Static3PNetwork`.
- [x] Complete remaining full-feeder static parser topology migration for
  transformer and regulator branch/control objects.
  - Static parser mode now creates transformer and regulator branches directly
    on the existing `Static3PNetwork`, with phase-domain `Zabc` kept aligned
    with the generic branch series impedance.
  - Static parser mode also supports reactor branches and source-voltage-base
    propagation without relying on DStab bus lists.
  - Static load conversion now handles per-phase base conversion plus
    single-phase wye and delta load vectors through the static phase-load
    contract.
- [x] Introduce factory helpers for static phase loads/generators if the static
  EMF factory cannot directly create the required phase-device types.
- [x] Remove QSTS dependence on `StaticPhaseLoadAdapter` and
  `StaticPhaseGenAdapter` after static network APIs expose `IPhaseLoad` and
  `IPhaseGen` directly.
  - Plugin-local transition adapters were removed so `com.interpss.core.threephase`
    remains owned by core.
- [x] Sweep QSTS, control, and CSV exporter code for legacy `ILoad1Phase`,
  `ILoad3Phase`, `IGen1Phase`, and `IGen3Phase` usage; keep those only in
  adapter/parser compatibility code.
- [x] Ensure static PF Y-matrix construction never needs DStab-only generator or
  load classes.
- [x] Document which packages are static-facing versus dynamic-facing:
  - static/QSTS/PF: `IPhaseLoad`, `IPhaseGen`, `IStaticPhaseLoad`,
    `IStaticPhaseGen`;
  - dynamic: `IDynamicPhaseLoad`, `IDynamicPhaseGen`, DStab models;
  - parser adapters: OpenDSS metadata to generic static phase-device contracts.
  - `org.interpss.threePhase.qsts` and `org.interpss.threePhase.powerflow`
    are static-facing and should take `INetwork3Phase`, `IBus3Phase`,
    `IBranch3Phase`, `IPhaseLoad`, and `IPhaseGen`.
  - `org.interpss.threePhase.dynamic` and `org.interpss.threePhase.basic.dstab`
    remain dynamic-facing; DStab classes can implement dynamic phase-device
    specializations but are not QSTS/PF requirements.
  - `org.interpss.threePhase.dataParser.opendss` is an adapter boundary: it may
    preserve OpenDSS metadata, but static parser mode must materialize devices
    onto `Static3PNetwork` and generic phase-device interfaces.

Relationship with existing code:

- `IPhaseLoad` and `IPhaseGen` are the long-term QSTS and static PF contracts.
- `IAclfPhaseLoad` and `IAclfPhaseGen` exist because the base phase-device
  contracts should not extend full `AclfLoad`/`AclfGen`.
- The old plugin-local static phase adapters were transition tools and have
  been removed; core phase-device interfaces are the final static/QSTS
  boundary.
- DStab classes may implement dynamic phase-device interfaces, but static
  algorithms must not require DStab types.

Verification:

- [x] Unit test proves `IPhaseGen` supports a two-phase phase-vector device.
- [x] Unit test proves `IPhaseLoad` supports a two-phase phase-vector device.
- [x] Static bus API test proves contributed static loads/generators can be
  enumerated as `IPhaseLoad`/`IPhaseGen` without DStab casts.
- [x] Parser mini test proves OpenDSS PV/storage/load objects are created on a
  static network and reachable through `IPhaseGen`/`IPhaseLoad` static views.
- [x] Parser mini test proves OpenDSS circuit source, line, load, capacitor, and
  cap-control metadata can be parsed into `Static3PNetwork` without DStab buses.
- [x] Static PF regression proves IEEE123, IEEE8500, Ckt7, and Ckt24 still
  converge after static boundary migration.
- [x] QSTS regression proves no generic QSTS class imports DStab generator/load
  classes.
- [x] QSTS default PF regression proves a static network can run through the
  real fixed-point solver without DStab bus, branch, load, or generator objects.
  - `mvn -pl ipss.plugin.3phase clean test -Dtest="QstsStudyTest" -Dsurefire.failIfNoSpecifiedTests=false`
    passed: 4 tests, 0 failures, 0 errors.

## Cross-Slice Verification Checklist

Run after every slice that changes parser or solver-facing behavior:

```bash
mvn -pl ipss.plugin.3phase test -Dtest=TestOpenDSSDataParser
```

Run before declaring the QSTS v1 path complete:

```bash
mvn -pl ipss.plugin.3phase test -Dtest="*Qsts*,*OpenDSS*Qsts*"
mvn -pl ipss.plugin.3phase test -Dtest=TestOpenDSSDataParser
mvn -pl ipss.plugin.3phase test -Dtest=IEEE_13BusFeeder_Test
```

Run broader three-phase validation when QSTS touches shared three-phase load,
branch, capacitor, regulator, or power-flow code:

```bash
mvn -pl ipss.plugin.3phase test
```

Verification evidence to capture in PRs or development notes:

- [x] Classes created/updated in the slice.
  - Core phase-device interfaces: `IPhaseLoad`, `IPhaseGen`,
    `IStaticPhaseLoad`, `IStaticPhaseGen`, `IDynamicPhaseLoad`,
    `IDynamicPhaseGen`, `IAclfPhaseLoad`, `IAclfPhaseGen`.
  - Static network/device model: existing `Static3PNetwork`, `Static3PBus`,
    `Static3PBranch`, `Static3PLoad`, `Static3PGen`.
  - Generic QSTS: `QstsStudy`, `QstsStateApplier`, state stores, profile and
    schedule model, CSV exporter, generator/storage base-state objects.
  - Controls: capacitor control data/model, control queue hooks, inverter and
    storage metadata/control foundations.
  - Adapter boundary: OpenDSS time-series, loadshape, PV/storage, CapControl,
    InvControl, static parser topology updates, and DSS-Python QA helpers.
- [x] Static PF regression command and result.
  - `mvn -pl ipss.plugin.3phase clean test -Dtest="OpenDssParserPowerFlowComparisonTest" -Dsurefire.failIfNoSpecifiedTests=false`
    passed: 49 tests, 0 failures, 0 errors, 24 skipped.
  - Representative DSS-Python voltage comparisons:
    IEEE123 max `|V|` error `0.000150 pu`, max angle error `0.000953 deg`;
    IEEE13 max `|V|` error `0.001067 pu`, max angle error `0.009514 deg`;
    IEEE8500 max `|V|` error `0.004407 pu`, max angle error `0.219505 deg`;
    Ckt7 max `|V|` error `0.004709 pu`, max angle error `0.169313 deg`.
- [x] Mini DSS-Python comparison command and worst-error summary.
  - `mvn -pl ipss.plugin.3phase clean test -Dtest="OpenDssCapControlMiniComparisonTest" -Dsurefire.failIfNoSpecifiedTests=false`
    passed: 1 test, 0 failures; cap-control state and kvar comparisons match
    the DSS-Python reference cases.
  - The parser comparison suite also includes the load-model mini case:
    max `|V|` error `0.000060 pu`, max angle error `0.000019 deg`.
- [x] Any feeder smoke command and convergence/error summary.
  - `mvn -pl ipss.plugin.3phase clean test -Dtest="OpenDssParserPowerFlowComparisonTest#ieee8500YMatrixComponentAudit" -Dsurefire.failIfNoSpecifiedTests=false`
    passed and forms the static IEEE8500 Y-matrix component audit.
  - Ckt24 static parser group passed with source, busbar, line-code, triplex,
    transformer, and Thevenin-source checks.
- [ ] Known unsupported OpenDSS behavior and current diagnostic output.

## Recommended First Pull Request Boundary

The first PR should include only Slides 1 through 3:

- `OpenDSSTimeSeriesData`
- generic `QstsProfile`, `QstsProfileRegistry`, `QstsProfileBinding`,
  `QstsScheduleData`
- OpenDSS adapter `OpenDSSLoadShape`, `OpenDSSShapeRegistry`,
  `OpenDSSProfileBinding`
- `OpenDSSGlobalTimeSeriesOptions`
- `OpenDSSLoadShapeParser`
- `OpenDSSDataParser` updates to store shape metadata
- Parser-only tests

Do not apply profile multipliers or run QSTS in the first PR. The acceptance
criterion is that static PF is unchanged and shape metadata is inspectable.

## Recommended V1 Completion Boundary

QSTS v1 is complete when Slides 1 through 8 are done:

- LoadShape parsing.
- Load/profile binding.
- Sequential fixed-point study runner.
- Controls-off and frozen-control QSTS.
- DSS-Python-backed mini regressions.
- Ckt7/Ckt24 controls-off smoke studies.
- CSV export for bus voltages, load powers, convergence, and basic device state.

Regulator, capacitor, reactor, DER, and storage support should follow as
separate capability PRs because each requires independent OpenDSS source
behavior research and DSS-Python reference cases.
