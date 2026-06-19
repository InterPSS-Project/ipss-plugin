# T&D Combined QSTS Feature Plan

## Goal

Add a combined transmission-and-distribution QSTS study layer that advances a
positive-sequence transmission load-flow case and one or more three-phase
distribution feeder QSTS cases on a shared time axis.

The first delivery should support sequential co-simulation:

1. Load or build the transmission network once.
2. Attach one or more distribution QSTS feeders to configured transmission
   boundary buses.
3. For each time step, apply transmission load/generation schedules and feeder
   schedules.
4. Iterate between transmission and feeder solves until boundary voltage and
   feeder net-injection mismatches converge.
5. Record transmission bus/branch results, feeder QSTS results, boundary
   exchange, convergence diagnostics, and optional CSV exports.

Full dynamic simulation, EMT simulation, protection events, and simultaneous
multi-period optimization are out of scope for the first delivery.

## Current Codebase Anchors

- `org.interpss.threePhase.qsts.QstsStudy`
  - Existing sequential static three-phase feeder QSTS runner.
  - Owns feeder time-step state application, fixed-point PF calls, control
    loops, warm start, and feeder result sampling.
- `org.interpss.threePhase.qsts.QstsScheduleData`
  - Existing generic feeder profile/schedule/options container.
- `org.interpss.threePhase.qsts.QstsResult`
  - Existing feeder result aggregate.
- `org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory`
  - Existing OpenDSS adapter boundary for feeder QSTS.
- `org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm`
  - Existing per-step distribution solver. It should remain a solver, not a
    T&D coordinator.
- `com.interpss.core.aclf.AclfNetwork`
  - Transmission steady-state network model.
- `org.interpss.multiNet.equivalent.NetworkEquivalent` and
  `NetworkEquivUtil`
  - Existing multi-network equivalent utilities. They are useful reference
    points for boundary semantics, but the first T&D QSTS path should not
    require full Thevenin reduction unless a later performance slice needs it.

## Design Principles

- Keep feeder QSTS generic and static. Do not put transmission-specific logic
  into `QstsStudy` or OpenDSS parser classes.
- Add a combined-study coordinator above the existing feeder QSTS runner.
- Treat boundary coupling as an explicit contract with two supported interface
  modes:
  - default three-sequence interface;
  - optional positive-sequence-only balanced interface.
- Preserve feeder phase detail internally. Convert only at the boundary using
  documented ABC-to-sequence and sequence-to-ABC rules.
- Keep schedules and result sampling independent for transmission and
  distribution, then align them by shared step index/time.
- Make the v1 coupling algorithm simple and inspectable before adding
  sensitivity, Thevenin, or parallel-window acceleration.

## Proposed Packages

### `org.interpss.threePhase.qsts.td`

Generic combined T&D study layer:

- `TdQstsStudy`
  - Public API and top-level run loop.
  - Owns time axis, boundary mappings, coupling options, convergence settings,
    and result collection.
- `TdQstsOptions`
  - Step count, step size, max coupling iterations, mismatch tolerances,
    relaxation factor, interface mode, and failure policy.
- `TdBoundaryInterfaceMode`
  - Enum for `THREE_SEQUENCE` and `POSITIVE_SEQUENCE_BALANCED`.
- `TdBoundaryMapping`
  - Maps one transmission bus id to one feeder study/source bus.
  - Stores feeder id, optional transformer/base metadata, load sign convention,
    interface mode override, and phase/sequence conversion metadata.
- `TdBoundaryExchange`
  - Per-step/per-iteration boundary values: transmission voltage, feeder source
    voltage, feeder net complex power/current, sequence injection, equivalent
    transmission load update, and mismatch.
- `TdQstsResult`
  - Combined result object with transmission step results, feeder `QstsResult`
    slices, boundary exchanges, convergence status, and diagnostics.
- `TdTransmissionStepApplier`
  - Applies scheduled transmission load/generation changes before each
    transmission solve.
- `TdTransmissionPowerFlowRunner`
  - Thin adapter around the existing ACLF algorithm API. This keeps the
    combined-study code testable without embedding a specific ACLF setup.
- `TdFeederSession`
  - Wraps one feeder `QstsStudy` and exposes single-step execution using the
    current step context. If `QstsStudy` cannot run one step cleanly today, add a
    small feeder-session API instead of duplicating QSTS state logic.
- `TdQstsCsvExporter`
  - Optional combined exporter for boundary exchange and transmission step
    summaries. Reuse `QstsCsvExporter` for feeder-level CSVs.

### Optional Adapter Packages

- `org.interpss.threePhase.qsts.td.opendss`
  - Builds `TdFeederSession` objects from OpenDSS parser/factory output.
- `org.interpss.threePhase.qsts.td.psse`
  - Later adapter for PSS/E or RAW-side time-series profile metadata if needed.

## Boundary Coupling Contract

The combined T&D feature should support two interface modes. The mode can be set
globally in `TdQstsOptions` and overridden per `TdBoundaryMapping` when a study
uses mixed boundary assumptions.

### Mode 1: Three-Sequence Interface (Default)

This is the default combined T&D interface. Use it when the transmission side can
provide a sequence-space boundary representation and the feeder should return
unbalanced feeder behavior back to the transmission interface.

Transmission to distribution:

- Transmission provides boundary voltage in three-sequence form.
- The coordinator converts sequence voltage to ABC source voltage for the
  feeder boundary.
- The feeder QSTS step solves in ABC phase coordinates.

Distribution to transmission:

- The feeder samples ABC boundary current or complex power at the source or
  substation terminal.
- The coordinator converts ABC boundary quantities to sequence components.
- The transmission-side boundary update receives sequence injections, preserving
  positive, negative, and zero sequence components where the transmission
  formulation supports them.

This mode aligns with the existing multi-network equivalent utilities that
already carry `Complex3x1` and `Complex3x3` sequence/phase data. It should be
the first-class API even if the initial implementation internally supports only
the positive-sequence transmission solve.

### Mode 2: Positive-Sequence Balanced Interface

Use this option when the transmission side provides only positive-sequence
voltage and the boundary is assumed balanced.

Transmission to distribution:

- Transmission provides only the solved positive-sequence boundary voltage.
- The coordinator creates a balanced ABC source voltage for the feeder.
- Negative- and zero-sequence source components are treated as zero.

Distribution to transmission:

- The feeder still solves in ABC phase coordinates.
- The coordinator aggregates feeder ABC boundary power/current into a
  positive-sequence equivalent injection.
- Negative- and zero-sequence feedback is ignored or reported as diagnostics,
  depending on `TdQstsOptions`.

This mode is the simpler compatibility path for positive-sequence-only ACLF
studies. It should be explicit because it assumes a balanced boundary and cannot
fully feed unbalanced distribution effects back to the transmission model.

### Transmission to Distribution

For each attached feeder and coupling iteration:

- Read the solved transmission boundary voltage in the configured interface
  mode.
- Convert the boundary voltage to feeder ABC source voltage.
- Apply feeder source voltage magnitude/angle before running the feeder step.
- Preserve the feeder control state and warm-start voltage state across time
  steps.

### Distribution to Transmission

After each feeder step:

- Sample feeder source/substation ABC complex power or current from the feeder
  result.
- Convert feeder boundary output according to the configured interface mode:
  - `THREE_SEQUENCE`: ABC to positive-, negative-, and zero-sequence injection;
  - `POSITIVE_SEQUENCE_BALANCED`: ABC aggregate to positive-sequence equivalent
    injection only.
- Update the equivalent load or injection at the mapped transmission boundary
  bus on the transmission base.
- Use explicit sign convention:
  - Positive feeder P/Q means power consumed by the feeder from transmission.
  - DER export becomes reduced load or negative injection at the boundary.

### Coupling Iteration

For each time step:

1. Apply transmission schedules.
2. Apply the previous or base boundary feeder injections.
3. Solve transmission ACLF.
4. For each feeder, apply boundary voltage and run one feeder QSTS step.
5. Update boundary injections from feeder source power.
6. Repeat transmission and feeder solves until both conditions hold:
   - boundary voltage change is below tolerance;
   - feeder injection change is below tolerance.
7. Store the converged result or fail according to `TdQstsOptions`.

The v1 algorithm can use fixed-point iteration with optional relaxation:

```text
Pboundary[k+1] = alpha * Pfeeder[k] + (1 - alpha) * Pboundary[k]
Qboundary[k+1] = alpha * Qfeeder[k] + (1 - alpha) * Qboundary[k]
```

Sensitivity-based acceleration and Thevenin-equivalent updates are later
performance slices.

## Implementation Slices

### Slice 1: API and Boundary Model

- Add `TdBoundaryMapping`, `TdBoundaryExchange`, `TdQstsOptions`, and
  `TdQstsResult`.
- Add `TdBoundaryInterfaceMode` with default `THREE_SEQUENCE` and optional
  `POSITIVE_SEQUENCE_BALANCED`.
- Add unit tests for mapping validation, sign convention, base-MVA conversion,
  ABC/sequence conversion metadata, and positive-sequence balanced aggregation.
- Document the public API with a small pseudo-usage example.

Acceptance:

- No changes to existing `QstsStudy` behavior.
- No OpenDSS parser changes.
- Unit tests run with only lightweight objects/mocks where possible.

### Slice 2: Feeder Single-Step Session

- Add `TdFeederSession` or a small `QstsStudy` single-step execution API.
- Reuse existing `QstsStateApplier`, control queue behavior, and result
  sampling.
- Preserve warm-start/control state across consecutive calls.

Acceptance:

- A feeder-only test using `TdFeederSession` produces the same results as the
  existing multi-step `QstsStudy` path for a mini feeder.
- Existing QSTS tests still pass.

### Slice 3: Transmission Step Runner

- Add `TdTransmissionPowerFlowRunner` and `TdTransmissionStepApplier`.
- Support static schedules for boundary-independent transmission loads and
  generators.
- Support reversible boundary load updates so each coupling iteration starts
  from a known base state.

Acceptance:

- A small transmission-only test applies two scheduled load steps and solves
  ACLF.
- Boundary load updates do not accumulate across iterations.

### Slice 4: Coupled T&D Run Loop

- Add `TdQstsStudy`.
- Implement fixed-point coupling with max iteration, voltage tolerance, P/Q
  tolerance, and relaxation.
- Support one transmission bus connected to one feeder first.
- Add convergence diagnostics when the coupled step fails.

Acceptance:

- A deterministic toy case converges in one or two coupling iterations.
- Result contains transmission status, feeder result, boundary exchange history,
  and final mismatch.

### Slice 5: Multi-Feeder Support

- Extend `TdQstsStudy` to multiple feeder sessions on distinct or shared
  transmission buses.
- Aggregate feeders mapped to the same transmission boundary bus.
- Keep each feeder session isolated and mutable only within that feeder.

Acceptance:

- Two feeder sessions can attach to one transmission bus and the boundary load
  equals the sum of both feeder source powers.
- Feeder controls/warm-start state do not leak across sessions.

### Slice 6: OpenDSS Adapter and Example

- Add an OpenDSS-oriented helper that creates feeder sessions from existing
  `OpenDSSQstsStudyFactory` output.
- Add a small example under tests or samples:
  - IEEE 9-bus or smaller transmission case.
  - One OpenDSS mini feeder attached to a load bus.
  - Two or three time steps with changing feeder load/PV profile.

Acceptance:

- Example runs from Maven without external services.
- Results include boundary exchange CSV and feeder QSTS CSVs.

### Slice 7: Performance and Robustness

- Add optional parallel feeder solves within one coupling iteration after the
  single-threaded path is correct.
- Add streaming/chunked result export for long yearly studies.
- Consider Thevenin/sensitivity acceleration only after fixed-point coupling has
  baseline correctness and profiling data.

Acceptance:

- Multi-feeder run remains deterministic with parallel feeder execution off.
- Parallel mode uses one mutable feeder session per worker and does not share
  mutable solver/network state.

## Verification Plan

Run focused tests after each slice:

```bash
mvn -pl ipss.plugin.3phase test -Dtest="*Qsts*"
mvn -pl ipss.plugin.3phase test -Dtest="*TdQsts*"
```

Run wider regression before merging the combined feature:

```bash
mvn -pl ipss.plugin.3phase test
mvn -pl ipss.test.plugin.core test -Dtest=CorePluginTestSuite
```

Reference checks:

- Feeder-only `TdFeederSession` equals existing `QstsStudy` for the same mini
  feeder and time window.
- Transmission-only schedules match direct ACLF runs with manually updated
  loads.
- Coupled toy case satisfies boundary P/Q balance and voltage mismatch
  tolerances in both interface modes.
- Three-sequence mode preserves nonzero negative/zero-sequence feeder boundary
  diagnostics when the feeder is unbalanced.
- Positive-sequence balanced mode reports or ignores negative/zero sequence
  components according to the configured diagnostics policy.
- Multi-feeder case verifies aggregation and independent feeder state.

## Open Questions

- Should the first transmission schedule source be a simple Java API only, CSV,
  or adapter metadata from a PSS/E-style source?
- Which base should be canonical at the boundary: transmission network base MVA
  or feeder base MVA with conversion at the mapper?
- Should v1 attach feeders as equivalent loads only, or also support feeder DER
  export as explicit generator injection objects?
- In three-sequence mode, should v1 solve full sequence feedback in
  transmission, or record negative/zero sequence as boundary diagnostics until a
  sequence-capable transmission runner is available?
- In positive-sequence balanced mode, what threshold should flag excessive
  negative/zero-sequence feeder feedback as a violated balanced-boundary
  assumption?
- Where should the long-term public API live if combined studies need to span
  both `ipss.plugin.core` and `ipss.plugin.3phase` modules?

## Non-Goals for V1

- Three-phase transmission network solving.
- Dynamic co-simulation between transmission DStab and feeder dynamics.
- Full OpenDSS monitor, energy meter, or protection-event parity.
- Thevenin-equivalent reduction as the default coupling method.
- Multi-period OPF or DistOPF co-optimization.
