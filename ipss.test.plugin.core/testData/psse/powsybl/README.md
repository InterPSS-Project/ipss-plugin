# PowSyBl PSS/E test fixtures

Vendored from [powsybl/powsybl-core](https://github.com/powsybl/powsybl-core) (`main`), Apache License 2.0.

Used by InterPSS tests under `org.interpss.core.adapter.psse` to extend RAW/RAWX coverage toward the catalog in `ipss-core/docs/psse/PowSyBl_PSSE_RAW_Test_Cases.md`.

| Subfolder | Upstream roots |
|-----------|----------------|
| `nbreaker/` | `psse-model-test` / `psse-converter` node-breaker fixtures |
| `ieee/` | IEEE 14/24/30/57/118 + two-area / delimiter / zip / isolated |
| `dc/` | `twoTerminalDc*` + VSC zero-R (comma form: `_updated_exported` or converted) |
| `equipment/` | switched shunt, T3W/phase, remote control, edge cases |
| `rawx/` | IEEE14/24, twoSubstations, MinimalExample RAWX |
| `negatives/` | unsupported version / invalid / bad-data |
| `illinois/` | literature-based WSCC 9, IEEE 39, IEEE 300 |

**Note:** `PSSEDirectParser` is comma-delimited. Space-delimited PowSyBl originals were replaced with `_updated_exported.raw` where available, or converted to comma form for DirectParser coverage.
