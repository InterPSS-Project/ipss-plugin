# CGMES 3.0 CAS / ReliCap fixtures (local symlinks)

Symlinks into `~/Documents/Temp/cgmes-test-data/` — **not** committed (see
`cgmes3.0/.gitignore` and module `.gitignore`). CI without the Temp pack skips
`@Tag("requires-cas-download")` tests via `Assumptions.assumeTrue`.

## Linked packs

| Link | Target (under Temp) |
|------|---------------------|
| MiniGrid-Merged | cas-v3.0.3/.../v3.0/MiniGrid/MiniGrid-Merged |
| MicroGrid-Type1-* / MicroGrid-BD-MAS | .../MicroGrid/MicroGrid-Type1/... |
| MicroGrid-Type2-* | .../MicroGrid/MicroGrid-Type2/... |
| PST-PhaseTapChangerLinear-Type1/2, Table-Type3 | .../v3.0/PST/... |
| SmallGrid, RealGrid, FullGrid, PowerFlow, CAS-Svedala | .../v3.0/{name} |
| ReliCap-Svedala-cimxml, ReliCap-Belgovia-cimxml | relicapgrid/Instance/.../cimxml |

Override CAS root with `-Dipss.cgmes.cas.root=` (parent of `v3.0/`).
