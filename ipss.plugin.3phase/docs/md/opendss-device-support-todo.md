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
- [x] Add impedance conversion assertions for the imported `test_line` branches.
- [x] Run PF, OPF, and post-OPF PF validation for `test_line`.

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
  - [x] `2BusD`
  - [x] `2Bus_1phase`
  - [ ] `3Bus` PF/OPF validation, blocked until OpenDSS `vminpu`/low-voltage
    load fallback behavior is represented for this stressed constant-power case
  - [x] `4Bus-YY-Bal` PF/OPF validation
  - [ ] `4Bus-YD-Bal`; parser/connection coverage and three-phase delta load
    injection added, PF/OPF pending transformer phase-shift/convergence
    validation
  - [x] `4Bus-DY-Bal` parser, fixed-point PF, OPF, and post-OPF PF validation
  - [ ] `4Bus-GrdYD-Bal`; parser/connection coverage and three-phase delta
    load injection added, PF/OPF pending transformer phase-shift/convergence
    validation
- [ ] Compare computed matrices with OpenDSS/distopf reference matrices.

## Phase 5: Transformers and Regulators

- [ ] Refactor `OpenDSSTransformerParser` to parse quoted and parenthesized
  arrays.
- [ ] Support `buses=`, `conns=`, `kvs=`, `kvas=`, `wdg=`, `%r`, `%loadloss`,
  and `xhl` consistently.
  - [x] Convert multiline `wdg=` transformer `%r` and `xhl` from OpenDSS
    percent-on-kVA values to ohms before InterPSS PU conversion.
  - [x] Parse one-line two-winding transformer `%rs=(r1,r2,...)` arrays into
    series resistance; Ckt24 step transformers depend on adding the first two
    winding values.
- [x] Support single-phase regulator transformer parsing and fixed-point PF.
  - [x] Normalize single-phase regulator thermal limits on the one-phase PU base
    used by OpenDSS-imported single-phase loads.
- [x] Support delta-wye and grounded-wye-delta transformer connection parsing
  using InterPSS `XFormerConnectCode` and existing anti-float Y-matrix handling.
  - [x] Strip OpenDSS terminal node suffixes from transformer bus names.
  - [x] Preserve `.4` floating-neutral and `.0` grounded-neutral semantics for
    wye windings.
- [x] Add fixed-ratio regulator metadata from `RegControl`.
  - [x] Parse `transformer=`, `winding=`, `vreg=`, and `ptratio=` and apply the
    fixed winding target after voltage-base propagation.
- [ ] Add anti-float treatment for transformer connections that create floating
  nodes in the PF Y-matrix.
- [ ] Verify regulator branches:
  - [x] `test_reg` parser, fixed-point PF, OPF, and post-OPF PF validation
  - [ ] IEEE13
  - [ ] IEEE34
  - [ ] IEEE123

## Phase 6: Loads, Capacitors, and Controls

- [x] Support balanced three-phase delta load current injection.
- [ ] Support one-phase delta loads connected line-to-line.
- [ ] Map OpenDSS model 4 and ZIP inputs to documented PF approximations.
  - [x] Parse Ckt7 `model=4` loads and apply OpenDSS CVR behavior:
    `P=P0*V^CVRwatts`, `Q=Q0*V^CVRvars`, with defaults `1` and `2`.
  - [ ] Add ZIP/model-8 behavior.
- [x] Parse `Load.<id>.AllocationFactor=...` updates and apply them to
  `xfkVA`/power-factor loads before PU conversion.
- [x] Parse OpenDSS load `bus=`, parenthesized `kVA=(...)`, and default wye
  connection syntax used by EPRI Ckt7.
- [x] Parse and retain `vminpu` and `vmaxpu` where relevant.
  - [x] Preserve load continuation lines beginning with `~` before parsing.
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

## Phase 10: DSS-Python Mismatch Pinpointing

- [x] Export solver-independent DSS-Python references for bus voltages, element
  currents, element powers, and element Yprim from the same compiled DSS files.
- [x] Add InterPSS post-solve KCL residual diagnostics ranked by bus/phase.
- [x] Run fixed-point tolerance sensitivity at `1e-4`, `1e-6`, and `1e-8`;
  classify residual mismatch as convergence-limited or modeling-limited.
- [x] Compare DSS-Python and InterPSS element currents by source-to-worst-bus
  path and rank the first sharp current/drop mismatch.
- [ ] Add model toggles for loads, capacitors, line shunts, fixed regulator
  taps, regulator controls, and center-tap/triplex transformers.
- [x] Compare parsed load models against OpenDSS `model`, `conn`, `kV`,
  `Vminpu`, and `Vmaxpu`, especially below-voltage fallback behavior.
- [x] Compare device Y blocks by category: lines, switches, regulators,
  center-tap/load transformers, and capacitors.
- [x] Implement and validate closer OpenDSS low-voltage load fallback behavior
  for `model=1` loads below `Vminpu`, especially two-phase 120/240 V secondary
  loads.
- [x] Document the identified mismatch source with a minimal DSS-Python-backed
  regression case.

Current finding:

- OpenDSS documentation says `Vminpu` is the minimum per-unit voltage for which
  the selected load model applies; below it, the load transitions to a constant
  impedance model matched at the transition voltage. OpenDSS also documents the
  low-voltage convergence modification and `Vlowpu` transition behavior, and
  clarifies that 2-phase and 3-phase element voltage ratings are line-line by
  convention.
- The IEEE8500 controls-off fixed-point mismatch is modeling-limited, not
  tolerance-limited. Tightening fixed-point tolerance from `1e-4` to `1e-8`
  leaves the worst voltage-magnitude error essentially unchanged at about
  `0.0083 pu`.
- DSS-Python `Yprim` comparisons match for the high-side transformer and the
  `ln6504018-1` line near the largest branch-drop discrepancy, and DSS regulator
  taps are `1.0` with controls disabled. These are carrying the downstream
  mismatch rather than causing it.
- Capacitors materially affect the absolute voltage profile, but disabling all
  capacitors in both tools still leaves the downstream secondary mismatch.
- DSS-Python-backed mini cases show that the current InterPSS `model=1`
  below-`Vminpu` matched-impedance fallback is already close for center-tap
  120/240 V services at about `0.816 pu`: the two-phase and split single-phase
  low-voltage mini cases both compare within `0.0006 pu`. This rules out the
  basic OpenDSS `Vminpu` fallback formula as the primary IEEE8500 mismatch
  source for the observed low-voltage region.
- The strongest remaining source is cumulative device/model behavior not yet
  isolated by the mini cases. The next diagnostic should aggregate DSS-Python
  and InterPSS load powers/currents by load type and feeder region, then compare
  the largest downstream current differences against capacitor and triplex/
  service-transformer contributions.
- Follow-up diagnostics pinpoint the dominant remaining IEEE8500 mismatch to
  center-tap service transformers. DSS-Python-vs-InterPSS branch aggregation
  shows InterPSS has about `49 kW` and `122 kvar` less aggregate service
  transformer loss across `1177` matched service transformers, while lines,
  triplex lines, capacitors, and terminal loads are much smaller contributors.
  The service transformer XfmrCode definitions include `%noloadloss=.2` and
  `%imag=0.5`; the current center-tap explicit-Y builder parses `%Rs`, `Xhl`,
  `Xht`, and `Xlt`, but does not carry `%noloadloss` or `%imag` into a
  magnetizing/no-load shunt. The magnitude matches the aggregate loss gap, so
  the next implementation fix is to parse these XfmrCode fields and add the
  equivalent no-load admittance to the primary side of the center-tap explicit
  Y block, then rerun IEEE8500 fixed-point comparison.
- Implemented the center-tap no-load branch fix by parsing `%imag` and
  `%noloadloss` from `XfmrCode`/inline transformer data and adding `G-jB` to the
  center-tap primary self-admittance. IEEE8500 controls-off fixed-point max
  voltage error dropped from about `0.0083 pu` to `0.0044 pu`. Service
  transformer aggregate loss mismatch dropped from about `-49 kW, -122 kvar` to
  about `+0.2 kW, -9.4 kvar`. Remaining branch mismatch is now dominated by
  upstream/main-feeder line and substation transformer differences rather than
  secondary service transformers.
- Ckt24 device-level QA added several reusable parser lessons. Continuation
  lines can place `cmatrix=[...] units=kft` together, so parser properties must
  be extracted independently. Repeated `Neutral=... Kron=yes` declarations are
  meaningful and should be applied sequentially, not collapsed. Missing `kW`
  with `xfkVA=0` and `pf` can still mean the OpenDSS default `10 kW`, not zero.
- The final Ckt24 residual was a transformer modeling gap, not a downstream
  lateral issue. The source-path voltage diagnostic showed a phase-B `dV` jump
  across `Transformer.step_05410_G2101CD0200`; branch power comparison then
  showed InterPSS had zero real transformer loss while DSS-Python had about
  `6.58 kW`. The root cause was parsing `%rs=(0.4725,0.4725)` without adding
  the winding resistances to series R. After the fix, Ckt24 max voltage error
  dropped from about `0.017935 pu` to about `0.004969 pu`.
- The next Ckt24 depth plot showed a remaining depth-0 mismatch at `sourcebus`.
  The root cause was the OpenDSS circuit `Vsource` Thevenin impedance:
  `New Circuit.ckt24 ... R1=0.63 X1=6.72 R0=4.07 X0=15.55`. DSS-Python reports
  the named `bus1` terminal after this impedance, while InterPSS had pinned
  `sourcebus` directly as the ideal swing bus. The parser now creates an
  internal ideal swing bus and connects it to `sourcebus` with a
  sequence-to-phase source branch. This reduced the Ckt24 depth-0 mismatch to
  about `1e-4 pu` and the max voltage error to about `0.002934 pu`; the
  remaining visible jump is now across or immediately after `SubXFMR`.

## Immediate Implementation Slice

- [x] Implement `OpenDSSUnitConverter`.
- [x] Store linecode units.
- [x] Convert line lengths for linecode-based lines.
- [x] Update `DistOPFGridappsdDss/test_line` to use native 2000 ft lengths.
- [x] Run `DistOpfOpenDssImportTest`.
- [x] Run `mvn -pl ipss.plugin.3phase clean '-Dtest=org.interpss.threePhase.opf.dist.*Test' test`.
- [x] Commit the parser/unit-conversion slice.
