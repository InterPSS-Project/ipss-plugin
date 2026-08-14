# PowSyBl As-Read Mismatch Investigations

Tracking survey outliers from [`PSSE_PowSyBl_InitMismatch_Test`](../../test/java/org/interpss/core/adapter/psse/raw/aclf/powsybl/PSSE_PowSyBl_InitMismatch_Test.java)
and the catalog section in `ipss-core/docs/psse/PowSyBl_PSSE_RAW_Test_Cases.md`
(“As-read NR mismatch survey”).

**Shared helper:** [`PowSyBlMismatchInvSupport`](PowSyBlMismatchInvSupport.java)

**How to run** (from `ipss-plugin/`, after `mvn -pl ipss.test.plugin.core -am install -DskipTests`):

```bash
# Example: completed RAW (closed POW-2)
mvn -pl ipss.test.plugin.core -q exec:java \
  -Dexec.classpathScope=compile \
  -Dexec.mainClass=investigation.powsybl.done.Ieee14CompletedMismatchInvestigation
```

Or run the `main` from the IDE with working directory `ipss.test.plugin.core/`
(set `PowSyBlMismatchInvSupport.ROOT = ""` if needed).

---

## Case index

| ID | Class | Fixture | Survey \|maxMis\| | Focus |
|----|-------|---------|-------------------|-------|
| POW-1 | [`done/Ieee14DelimiterMismatchInvestigation`](done/Ieee14DelimiterMismatchInvestigation.java) | `ieee/IEEE_14_bus_delimiter.raw` | Infinity @ Bus5 | Closed: non-standard delimiters unsupported |
| POW-2 | [`done/Ieee14CompletedMismatchInvestigation`](done/Ieee14CompletedMismatchInvestigation.java) | `parser/IEEE_14_bus_completed*.raw` | ≈3.01 @ Bus12/Bus14 | Closed: WATL 2T residual; MTDC fixture-invalid |
| POW-3 | [`Ieee14ZipLoadMismatchInvestigation`](Ieee14ZipLoadMismatchInvestigation.java) | `ieee/IEEE_14_buses_zip_load.raw` | ≈0.105 @ Bus2/Bus5 | ZIP load vs constant-PQ mismatch |
| POW-4 | [`TwoAreaTrf3wMismatchInvestigation`](TwoAreaTrf3wMismatchInvestigation.java) | `ieee/two_area_case_trf3w.raw` | ≈1.34 @ Bus9 / 3W star | 3W star as-read vs 2W baseline |
| POW-5 | [`done/Ieee24RawxMismatchInvestigation`](done/Ieee24RawxMismatchInvestigation.java) | `rawx/IEEE_24_bus_rev35.rawx` | ≈0.825 → ≈0.011 | Closed: RAWX `swshunt` import added |

---

## Status notes (seeded from 2026-08-14 survey)

### POW-1 — Delimiter Infinity
- Not a clean solved-as-read residual; Infinity implies NaN/Inf in network equations.
- Compare branch R/X and Bus5 debug vs `IEEE_14_bus.raw`.

### POW-2 — Completed RAW ~3 pu (closed)
- **MTDC warn:** fixture `ntermdcbus` has `IB=401` on DC buses 1–4 (same in RAW + RAWX). Parser fields correct; `validateTopology` correctly rejects. Converters `Bus20..23`; `Bus401` absent. MTDC does not inject.
- **Residual:** active 2T `WATL P1` (Bus12↔Bus14); `EATL P1` blocked. Extend-load ≈±2.5 pu ≈ `|maxMis|≈3`.
- **RAWX “solved”:** JSON parser skips `twotermdc` / `ntermdc*` / `vscdc`.
- Keep off allowlist (parser-completeness fixture with unsolved/extra HVDC, not a clean solved IEEE14).

### POW-3 — ZIP load ~0.1 pu
- Moderate residual; may be expected if ZIP is evaluated differently than PSS/E snapshot.
- Confirm ZIP coefficients survive `PSSEDirectParser` and how `mismatch(NR)` applies them.

### POW-4 — Two-area 3W ~1.3 pu
- Sibling `two_area_case.raw` (no 3W) is small (~0.005).
- Star bus `3WNDTR_4_9_8_1` is worst Q bus — check star V/θ and winding mapping.

### POW-5 — RAWX IEEE24 gap (closed)
- RAW IEEE24 residual ~0.011; RAWX was ~0.825 because `PSSEJsonDirectParser` skipped `swshunt`.
- Fix: import RAWX `swshunt` (v35+ fields `ibus/shntid/modsw/…/binit/sN,nN,bN`) via `addSwitchedShunt`.
- After fix, RAWX switched-shunt count and as-read residual match RAW.

---

## Cross-case takeaways

- Allowlist IEEE14/57/118 support “solved LF → small as-read mismatch”.
- These five fixtures should not be treated as allowlist candidates until each investigation closes (fixture intent vs importer gap).
- Prefer fixing importer gaps when the RAW twin is already near-solved; treat Infinity / ~3 pu cases as data or unsupported-equipment until proven otherwise.
