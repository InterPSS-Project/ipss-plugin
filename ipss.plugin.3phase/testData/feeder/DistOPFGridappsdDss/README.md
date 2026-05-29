This folder contains InterPSS parser-compatible OpenDSS fixtures derived from
GRIDAPPSD/distopf OpenDSS cases.

Source repository:
https://github.com/GRIDAPPSD/distopf

Imported source snapshot:
ce39973

Notes:
- `test_line/main-InterPSS.dss` follows the GRIDAPPSD/distopf
  `src/distopf/cases/dss/test_line/main.dss` feeder.
- The upstream file already uses explicit `Linecode` matrices. This fixture
  keeps the native 2000 ft branch lengths to verify OpenDSS line length unit
  conversion against a linecode declared in miles.
