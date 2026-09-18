# CGMES 3.0 CAS / ReliCap fixtures (local symlinks)

Symlinks into `~/Documents/Temp/cgmes-test-data/` — **not** committed.
CI without the Temp pack skips `@Tag("requires-cas-download")` via `assumeTrue`.

## Linked packs (highlights)

- MiniGrid / MicroGrid Type1–Type2 (+ HVDC) / Type3 IGMs+CGMs
- PST PhaseTapChanger Linear Type1/2 + Table Type3
- SmallGrid, RealGrid, FullGrid, PowerFlow (+ `PowerFlow-Instance`)
- CAS Svedala-Merged; ReliCap Svedala/Belgovia cimxml

Override CAS root with `-Dipss.cgmes.cas.root=` (parent of `v3.0/`).
