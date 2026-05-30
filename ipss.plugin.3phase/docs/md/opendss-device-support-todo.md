# OpenDSS Device Support TODO

## Success Criteria

- Original GRIDAPPSD/distopf OpenDSS files can be parsed without hand-normalized
  fixture edits for each supported milestone.
- Each supported feeder has:
  - parser coverage assertions,
  - fixed-point PF convergence,
  - DistOPF solve,
  - post-OPF fixed-point PF validation.
- Unsupported objects are reported through structured diagnostics instead of
  silent logging.
- Dynamic model contributions remain excluded from the PF Y-matrix.

## Phase 1: Parser Infrastructure

- [ ] Add `OpenDSSCommand` to hold command type, class, object name, properties,
  raw text, source file, and line number.
- [ ] Add `OpenDSSTokenizer` for OpenDSS key/value parsing.
- [ ] Add continuation-line handling for `~`.
- [ ] Add block-comment handling for `/* ... */`.
- [ ] Add recursive `redirect` and `compile` handling.
- [ ] Add case-insensitive object and property normalization.
- [ ] Add `OpenDSSParseReport`.
- [ ] Add parser coverage tests that fail when network-forming objects are
  skipped.

## Phase 2: Unit Conversion

- [x] Add `OpenDSSUnitConverter`.
- [x] Store linecode impedance length units in `LineConfiguration`.
- [x] Convert line length units to linecode units when using `LineCode`.
- [x] Support `none`, `mi`, `kft`, `ft`, `km`, and `m`.
- [x] Replace normalized GRIDAPPSD/distopf `test_line` fixture with native
  `Length=2000 units=ft`.
- [ ] Add impedance conversion assertions for the imported `test_line` branches.
- [ ] Run PF, OPF, and post-OPF PF validation for `test_line`.

## Phase 3: Small Linecode Cases

- [x] Add native fixtures/tests for:
  - `test_line_unbal_load`
  - `test_line_unbal_line`
  - `test_line_unbal_load_unbal_line`
- [x] Verify one-phase wye load handling on three single-phase loads.
- [x] Verify unbalanced line impedance phase mapping.
- [x] Verify DistOPF extraction keeps per-phase load and branch data through
  PF/OPF/post-OPF PF validation.

## Phase 4: Geometry and Cable Data

- [x] Add `OpenDSSWireDataParser`.
- [ ] Add `OpenDSSCableDataParser` for `CNData`.
- [x] Add `OpenDSSLineGeometryParser`.
- [x] Implement neutral reduction for `reduce=yes` on wire-based overhead
  geometries.
- [x] Add a Carson impedance approximation for wire-based overhead geometries.
- [x] Compare the `3Bus` geometry-derived line matrix with the OpenDSS-exported
  `4Bus-YY-Bal_dss` CSV reference for the same 4-wire geometry.
- [ ] Verify original GRIDAPPSD/distopf:
  - [x] `2Bus`
  - `2BusD`
  - `2Bus_1phase`
  - [ ] `3Bus` PF/OPF validation, blocked until OpenDSS `vminpu`/low-voltage
    load fallback behavior is represented for this stressed constant-power case
  - `4Bus-YY-Bal`
  - `4Bus-YD-Bal`
  - `4Bus-DY-Bal`
  - `4Bus-GrdYD-Bal`
- [ ] Compare computed matrices with OpenDSS/distopf reference matrices.

## Phase 5: Transformers and Regulators

- [ ] Refactor `OpenDSSTransformerParser` to parse quoted and parenthesized
  arrays.
- [ ] Support `buses=`, `conns=`, `kvs=`, `kvas=`, `wdg=`, `%r`, `%loadloss`,
  and `xhl` consistently.
- [ ] Support single-phase regulator transformers.
- [ ] Support delta-wye and grounded-wye-delta variants.
- [ ] Add fixed-ratio regulator metadata from `RegControl`.
- [ ] Add anti-float treatment for transformer connections that create floating
  nodes in the PF Y-matrix.
- [ ] Verify `test_reg`, IEEE13, IEEE34, and IEEE123 regulator branches.

## Phase 6: Loads, Capacitors, and Controls

- [ ] Support one-phase delta loads connected line-to-line.
- [ ] Map OpenDSS model 4 and ZIP inputs to documented PF approximations.
- [ ] Parse and retain `vminpu` and `vmaxpu` where relevant.
- [ ] Parse `CapControl` and keep capacitors fixed by default.
- [ ] Add optional fixed control-state import for capacitors/regulators.
- [ ] Verify IEEE34 and IEEE123 PF and OPF with fixed controls.

## Phase 7: DER Devices

- [ ] Add `OpenDSSGeneratorParser`.
- [ ] Add `OpenDSSPVSystemParser`.
- [ ] Add `OpenDSSStorageParser`.
- [ ] Map fixed generation into PF injections.
- [ ] Map controllable DER limits into `DistOpfModelData`.
- [ ] Add inverter capability constraints for parsed kVA/kW/kvar limits.
- [ ] Add post-OPF DER setpoint application to PF validation.
- [ ] Verify IEEE123 solar/wind ramp examples and 9500 DER files.

## Phase 8: Large Feeder Commands

- [ ] Parse `open` and `close` branch commands.
- [ ] Support `Fuse` as branch-like switch.
- [ ] Parse `setkvbase`.
- [ ] Improve voltage-base propagation for multiple base-kV levels.
- [ ] Ignore non-network `Monitor`, `EnergyMeter`, `BusCoords`, and
  `LatLongCoords` with structured diagnostics.
- [ ] Verify `smartds_small`, `9500-primary-network`, and `ieee9500_dss`.

## Phase 9: Triplex and Secondary Networks

- [ ] Parse triplex linecodes.
- [ ] Parse triplex lines.
- [ ] Parse `XfmrCode`.
- [ ] Decide full-secondary versus primary-only DistOPF extraction mode.
- [ ] Verify 9500 primary-only and full-secondary import modes separately.

## Immediate Implementation Slice

- [x] Implement `OpenDSSUnitConverter`.
- [x] Store linecode units.
- [x] Convert line lengths for linecode-based lines.
- [x] Update `DistOPFGridappsdDss/test_line` to use native 2000 ft lengths.
- [ ] Run `DistOpfOpenDssImportTest`.
- [ ] Run `mvn -pl ipss.plugin.3phase clean '-Dtest=org.interpss.threePhase.opf.dist.*Test' test`.
- [ ] Commit the parser/unit-conversion slice.
