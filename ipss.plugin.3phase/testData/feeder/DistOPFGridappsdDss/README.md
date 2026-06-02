This folder contains InterPSS parser-compatible OpenDSS fixtures derived from
GRIDAPPSD/distopf OpenDSS cases.

Source repository:
https://github.com/GRIDAPPSD/distopf

Imported source snapshot:
ce39973

Notes:
- `test_line/main-InterPSS.dss` follows the GRIDAPPSD/distopf
  `src/distopf/cases/dss/test_line/main.dss` feeder.
- The `test_line*` fixtures follow the corresponding GRIDAPPSD/distopf
  `src/distopf/cases/dss/test_line*` feeders.
- `test_reg/main-InterPSS.dss` follows the GRIDAPPSD/distopf
  `src/distopf/cases/dss/test_reg/main.dss` feeder and verifies one-line
  single-phase regulator transformer import.
- The upstream files already use explicit `Linecode` matrices. These fixtures
  keep the native 2000 ft branch lengths to verify OpenDSS line length unit
  conversion against linecodes declared in miles.
- `2Bus/main-InterPSS.dss` follows the GRIDAPPSD/distopf
  `src/distopf/cases/dss/2Bus/2Bus.DSS` feeder and keeps the native
  `WireData`/`LineGeometry` definitions to verify geometry-derived line
  configuration import.
- `2BusD/main-InterPSS.dss` follows the GRIDAPPSD/distopf
  `src/distopf/cases/dss/2BusD/2Bus.DSS` feeder and adds balanced
  single-phase delta loads on the geometry-derived line case.
- `2Bus_1phase/main-InterPSS.dss` follows the GRIDAPPSD/distopf
  `src/distopf/cases/dss/2Bus_1phase/2Bus1ph.DSS` feeder and keeps the native
  one-phase, two-wire geometry with neutral reduction.
- `3Bus/main-InterPSS.dss` follows the GRIDAPPSD/distopf
  `src/distopf/cases/dss/3Bus/3Bus.DSS` feeder. It is used for native geometry
  import and matrix-reference tests; full PF/OPF validation remains pending
  because the stressed constant-power case needs OpenDSS low-voltage load
  fallback behavior.
- `4Bus-YY-Bal/main-InterPSS.dss` follows the GRIDAPPSD/distopf
  `src/distopf/cases/dss/4Bus-YY-Bal/4Bus-YY-Bal.DSS` feeder. It is used for
  multiline wye-wye transformer import coverage; full PF/OPF validation remains
  pending.
