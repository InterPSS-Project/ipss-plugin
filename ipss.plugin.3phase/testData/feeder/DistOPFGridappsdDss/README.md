This folder contains InterPSS parser-compatible OpenDSS fixtures derived from
GRIDAPPSD/distopf OpenDSS cases.

Source repository:
https://github.com/GRIDAPPSD/distopf

Imported source snapshot:
ce39973

Notes:
- `test_line/main-InterPSS.dss` follows the GRIDAPPSD/distopf
  `src/distopf/cases/dss/test_line/main.dss` feeder.
- The upstream file already uses explicit `Linecode` matrices. The current
  InterPSS OpenDSS parser does not convert OpenDSS distance units, so the
  2000 ft branch lengths are normalized to 0.3787878788 mi in this fixture.
