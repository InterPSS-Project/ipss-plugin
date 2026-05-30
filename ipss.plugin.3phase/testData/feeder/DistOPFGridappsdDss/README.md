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
- The upstream files already use explicit `Linecode` matrices. These fixtures
  keep the native 2000 ft branch lengths to verify OpenDSS line length unit
  conversion against linecodes declared in miles.
- `2Bus/main-InterPSS.dss` follows the GRIDAPPSD/distopf
  `src/distopf/cases/dss/2Bus/2Bus.DSS` feeder and keeps the native
  `WireData`/`LineGeometry` definitions to verify geometry-derived line
  configuration import.
