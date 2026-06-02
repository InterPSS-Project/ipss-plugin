# OpenDSS Parser, Power Flow, and DistOPF Support Plan

## Goal

Extend the InterPSS 3-phase OpenDSS import path so the OpenDSS cases packaged
with `GRIDAPPSD/distopf` can be used directly for:

- InterPSS fixed-point distribution power flow validation.
- DistOPF model extraction and solve.
- Post-OPF fixed-point AC power-flow validation.

The implementation should preserve the current separation between power-flow
network modeling and dynamic simulation modeling. Dynamic model contributions
must not be injected into the power-flow Y-matrix.

## Current State

The current parser supports a useful subset:

- `Circuit` with source bus metadata when `basekv`, `bus1`, and `pu` are found.
- `Linecode` with `rmatrix`, `xmatrix`, and partial `cmatrix`.
- `Line` with explicit sequence data or linecode references.
- `Transformer` in several two-winding forms.
- `Load` for constant P, Z, and I models.
- `Capacitor` as fixed shunt.
- Redirect of some files, especially linecode files.

The current DistOPF verification uses:

- Local InterPSS OpenDSS feeders: `IEEE13`, `IEEE123`, `DistOPF2Bus`.
- A GRIDAPPSD/distopf-derived `test_line` fixture that already uses explicit
  linecode matrices, with length units normalized for the current parser.

## Unsupported Model Groups in GRIDAPPSD/distopf DSS Cases

The upstream cases include these OpenDSS features that need parser support:

- Geometry-derived impedance:
  - `WireData`
  - `CNData`
  - `LineGeometry`
  - `LineSpacing`
  - `LineConstantsCode`
  - lines using `geometry=...`
- Unit conversion:
  - `ft`, `kft`, `mi`, `m`, `km`, `none`
  - linecode units versus line length units
- Controls and script commands:
  - `RegControl`
  - `CapControl`
  - `open Line...`, `close Line...`
  - property edits such as `Generator.X.kw=...`
  - `Set voltagebases`, `setkvbase`, `CalcVoltageBases`, `Solve`
- DER and resource objects:
  - `Generator`
  - `PVSystem`
  - `Storage`
- Large-feeder secondary models:
  - `XfmrCode`
  - triplex linecodes and triplex lines
  - load transformers
  - low-voltage single-phase and split-phase buses
- Monitoring and non-network objects:
  - `EnergyMeter`
  - `Monitor`
  - `BusCoords`
  - `LatLongCoords`
  - `LoadShape`
- Richer transformer and load forms:
  - one-line transformers with quoted arrays
  - single-phase regulator transformers
  - delta-wye and open-wye/open-delta transformer variants
  - one-phase delta loads connected line-to-line
  - ZIP model inputs and voltage cutover parameters

## Architecture Changes

### Parser Front End

Add a normalized OpenDSS parsing front end:

- `OpenDSSScriptReader`
  - Reads a master `.dss` file.
  - Resolves `redirect` and `compile` recursively.
  - Joins continuation lines beginning with `~`.
  - Strips `!`, `//`, and block comments safely.
  - Keeps source file and line number for diagnostics.

- `OpenDSSCommand`
  - Structured representation of one command.
  - Fields: command type, class name, object name, property map, raw text,
    source location.

- `OpenDSSTokenizer`
  - Parses `key=value` assignments.
  - Handles arrays in `[]`, `()`, quoted strings, and OpenDSS separators.
  - Normalizes object/class names case-insensitively.

- `OpenDSSParseReport`
  - Counts parsed, ignored, unsupported, and failed commands.
  - Used by tests to assert support coverage improves over time.

### Object Registry

Replace hard-coded dispatch in `OpenDSSDataParser` with a registry:

- `OpenDSSObjectParser`
  - Interface: `boolean parse(OpenDSSCommand command, OpenDSSParseContext ctx)`.

- `OpenDSSParseContext`
  - Holds the `DStabNetwork3Phase`, linecode table, geometry table, transformer
    code table, loadshape table, voltage-base hints, and parse report.

Recommended parser classes:

- `OpenDSSCircuitParser`
- `OpenDSSLineCodeParser`
- `OpenDSSWireDataParser`
- `OpenDSSCableDataParser`
- `OpenDSSLineGeometryParser`
- `OpenDSSLineParser`
- `OpenDSSTransformerCodeParser`
- `OpenDSSTransformerParser`
- `OpenDSSLoadParser`
- `OpenDSSCapacitorParser`
- `OpenDSSRegControlParser`
- `OpenDSSCapControlParser`
- `OpenDSSGeneratorParser`
- `OpenDSSPVSystemParser`
- `OpenDSSStorageParser`
- `OpenDSSSwitchCommandParser`
- `OpenDSSSetCommandParser`

## Implementation Milestones

### M1: Parser Normalization and Diagnostics

Deliverables:

- Add `OpenDSSScriptReader`, `OpenDSSCommand`, and `OpenDSSTokenizer`.
- Preserve compatibility with existing `OpenDSSDataParser` public API.
- Add structured diagnostics without failing on non-network plotting commands.
- Add tests using:
  - GRIDAPPSD/distopf `test_line`
  - `test_line_unbal_load`
  - `test_line_unbal_line`
  - `test_line_unbal_load_unbal_line`

Verification:

- Parser report shows all network-forming commands parsed.
- Fixed-point PF converges for the four small linecode-based cases.
- DistOPF solves and post-OPF PF validation converges.

### M2: Unit Conversion

Deliverables:

- Add `OpenDSSUnitConverter`.
- Convert linecode impedance units and line length units consistently.
- Support `mi`, `kft`, `ft`, `km`, `m`, and `none`.
- Remove the need for hand-normalized fixtures such as converted `2000 ft`.

Verification:

- Use upstream GRIDAPPSD/distopf `test_line/main.dss` without edits.
- Compare imported branch impedances against expected per-unit values.
- PF and OPF verification pass for the original small test cases.

### M3: Line Geometry and Cable Data

Deliverables:

- Parse `WireData`, `CNData`, and basic `LineGeometry`.
- Compute phase impedance matrices for common overhead and concentric-neutral
  cable definitions.
- Support `reduce=yes` neutral reduction.
- For v1 geometry support, allow a documented approximation if full Carson
  earth-return modeling is not yet implemented.

Verification:

- Import original GRIDAPPSD/distopf:
  - `2Bus`
  - `2BusD`
  - `2Bus_1phase`
  - `3Bus`
  - `4Bus-YY-Bal`
  - `4Bus-YD-Bal`
  - `4Bus-DY-Bal`
  - `4Bus-GrdYD-Bal`
- Compare geometry-derived branch matrices with OpenDSS-exported matrices or
  the corresponding distopf CSV conversion.
- PF convergence and post-OPF PF validation for supported topologies.

### M4: Transformer, Delta, and Regulator Coverage

Deliverables:

- Improve transformer token parsing for `buses=`, `conns=`, `kvs=`, `kvas=`,
  `wdg=`, quoted arrays, and parenthesized arrays.
- Add explicit support for:
  - three-phase wye-wye
  - delta-wye
  - grounded-wye-delta
  - single-phase regulator transformers
  - fixed tap ratios
- Parse `RegControl` into fixed regulator metadata for PF and DistOPF v1.
- Keep automatic tap-changing out of v1 OPF unless modeled as fixed input.

Verification:

- Import and solve:
  - `test_reg`
  - IEEE13 regulator branches
  - IEEE34 regulator branches
  - IEEE123 regulator branches
- Confirm no floating buses around delta-wye transformers by anti-float shunt or
  robust primitive stamping.
- DistOPF extraction treats regulators as fixed-ratio branches unless explicitly
  enabled as controls in a later mixed-integer milestone.

### M5: Loads, Capacitors, and Controls

Deliverables:

- Support one-phase delta loads connected between two phases.
- Support OpenDSS load models used in IEEE34 and 9500 at least as PF-compatible
  approximations:
  - model 1: constant P/Q
  - model 2: constant Z
  - model 5: constant current
  - model 4/ZIP inputs mapped to documented approximation for v1
- Parse `CapControl` but keep capacitors fixed by default.
- Parse property edits such as `Load.X.vminpu=.85` where they affect supported
  models.

Verification:

- IEEE34 PF convergence.
- DistOPF solves IEEE34 with fixed capacitors/regulators.
- Voltage comparison against OpenDSS or distopf reference within documented
  tolerance.

### M6: DER Resource Parsing for OPF

Deliverables:

- Parse `Generator`, `PVSystem`, and `Storage`.
- Map DERs into `DistOpfModelData` as controllable or fixed injections based on
  `DistOpfOptions`.
- Add inverter capability limits when `kVA`, `kW`, `kvar`, `pf`, and phases are
  available.
- For v1, storage can be represented as fixed injection or simple bounded P/Q;
  time-coupled SOC should be a later multi-period feature.

Verification:

- Import:
  - `ieee123_dss/SolarRamp*.DSS`
  - `ieee123_dss/WindRamp*.DSS`
  - `9500-primary-network/PVSystems_primary.dss`
  - `9500-primary-network/Generators.dss`
  - `9500-primary-network/EnergyStorage.dss`
- DistOPF DER curtailment objective uses parsed DER limits.
- Post-OPF PF applies DER setpoints and converges.

### M7: Large Feeder Topology and Switching

Deliverables:

- Parse `open` and `close` commands.
- Mark branches as in-service/out-of-service before voltage-base propagation.
- Support fuses as branch-like switches for PF/OPF extraction.
- Add radial validation that accounts for open ties.
- Improve voltage-base propagation for large feeders with multiple base-kV
  levels and explicit `setkvbase` commands.

Verification:

- Import and solve:
  - `smartds_small/opendss/Master.dss`
  - `9500-primary-network/Master.dss`
  - `9500-primary-network/Master-unbal-initial-config.dss`
- Compare bus/branch counts against OpenDSS/distopf import counts.
- Run OR-Tools DistOPF for large cases; keep ojAlgo as small-case default only.

### M8: Triplex and Secondary Networks

Deliverables:

- Parse triplex linecodes and triplex lines.
- Decide internal representation:
  - map split-phase conductors into existing 3-phase containers with explicit
    missing-phase handling, or
  - add a secondary-network adapter that collapses service transformers and
    triplex services for primary-only OPF.
- Add load transformer code parsing for `XfmrCode`.

Verification:

- 9500 full secondary model parses without dropping connected loads.
- PF can solve primary-only and full-secondary modes.
- DistOPF v1 may default to primary-only extraction, with full-secondary OPF
  marked experimental.

## PF Requirements

Power-flow support should be verified at three levels:

1. Parser construction:
   - no missing network-forming objects
   - no duplicate bus/branch IDs
   - radial/meshed topology report

2. Fixed-point PF:
   - Y-matrix should include only linear network elements and fixed shunts
   - dynamic models are excluded from the PF Y-matrix
   - non-linear loads/gens are modeled as current injections
   - singular Y-matrix cases use the existing forward/backward fallback or
     explicit anti-float treatment

3. Reference comparison:
   - bus voltage magnitudes by phase
   - source P/Q
   - selected branch flows
   - number of connected buses and energized branches

## OPF Requirements

DistOPF support should be staged separately from parser support:

- V1 imported OPF:
  - radial feeders
  - fixed capacitors and regulators
  - fixed or controllable DER P/Q depending on options
  - angle-coupled LinDistFlow default
  - OR-Tools for large cases

- V2 imported OPF:
  - regulator/capacitor controls as mixed-integer or enumerated fixed cases
  - storage schedules
  - multiple periods
  - nonlinear branch-flow validation mode

For every imported OPF case:

- Run OPF.
- Apply setpoints explicitly to a network copy.
- Run fixed-point AC PF.
- Report voltage violations, thermal violations, and max OPF/PF voltage
  mismatch.

## Test Matrix

### Small Cases

- `test_line`
- `test_line_unbal_load`
- `test_line_unbal_line`
- `test_line_unbal_load_unbal_line`
- `test_reg`
- `2Bus`, `2BusD`, `2Bus_1phase`
- `3Bus`
- `4Bus-*`

### IEEE Cases

- IEEE13: geometry, regulators, capacitors, delta loads.
- IEEE34: kft units, long feeder voltage drop, regulators, capacitors, delta
  loads, model 4/5 loads.
- IEEE123: switches, regulators, capacitors, load files, DER ramp examples.

### Large Cases

- `smartds_small`
- `9500-primary-network`
- `ieee9500_dss`

## Suggested Class-Level TODO List

- Add `OpenDSSScriptReader`.
- Add `OpenDSSCommand`.
- Add `OpenDSSTokenizer`.
- Add `OpenDSSParseContext`.
- Add `OpenDSSParseReport`.
- Add `OpenDSSUnitConverter`.
- Add `OpenDSSWireDataParser`.
- Add `OpenDSSCableDataParser`.
- Add `OpenDSSLineGeometryParser`.
- Add `OpenDSSLineConstantsParser`.
- Refactor `OpenDSSLineParser` to use parsed command properties and unit
  conversion.
- Refactor `OpenDSSTransformerParser` to use parsed command arrays.
- Extend `OpenDSSLoadParser` for single-phase delta and ZIP approximations.
- Add `OpenDSSRegControlParser`.
- Add `OpenDSSCapControlParser`.
- Add `OpenDSSGeneratorParser`.
- Add `OpenDSSPVSystemParser`.
- Add `OpenDSSStorageParser`.
- Add `OpenDSSSwitchCommandParser`.
- Add OpenDSS import benchmarks for each milestone.
- Add post-OPF fixed-point PF validation tests for each supported case group.

## Recommended Implementation Order

1. Parser front-end normalization and diagnostics.
2. Unit conversion.
3. Small linecode-based GRIDAPPSD/distopf cases.
4. Geometry and cable data for 2/3/4-bus cases.
5. Transformer/regulator support for IEEE13/IEEE34/IEEE123.
6. Load model and capacitor-control support.
7. DER parsing and OPF mapping.
8. Large-feeder switching and 9500 primary-network support.
9. Triplex/secondary support.

This order keeps each slice independently testable and avoids hiding parser
errors behind OPF model approximations.
