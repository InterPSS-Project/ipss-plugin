# PowSyBl PSS/E test fixtures

Vendored from [powsybl/powsybl-core](https://github.com/powsybl/powsybl-core) (`main`), Apache License 2.0.

Used by InterPSS tests under `org.interpss.core.adapter.psse` — see coverage matrix in
`ipss-core/docs/psse/PowSyBl_PSSE_RAW_Test_Cases.md`.

| Subfolder | Upstream roots |
|-----------|----------------|
| `nbreaker/` | node-breaker + busWithoutInjection* |
| `ieee/` | IEEE 14/24/30/57/118 + two-area / delimiter / zip / isolated |
| `dc/` | `twoTerminalDc*` + VSC (comma / `_updated_exported`) |
| `equipment/` | switched shunt, T3W/phase, remote control, edge cases |
| `parser/` | Example32, completed, Q-record, non-induction, whitespace-exported |
| `rawx/` | IEEE14/24/25, twoSubstations, MinimalExample, special chars |
| `negatives/` | unsupported / invalid / bad-data |
| `illinois/` | literature-based (sanitized filenames); synthetics skipped |

**Notes**
- `PSSEDirectParser` is comma-delimited. Prefer `_exported` / `_updated_exported` or convert space-delimited originals.
- Whitespace original kept as `parser/IEEE_14_bus_whitespaceAsDelimiter.raw`; smoke uses `_exported` comma form.
- NB RAWX: `PSSEJsonDirectParser` imports flat `sub` / `subnode` / `subswd` / `subterm` via `PSSESubstationImporter.parseRawx`.
