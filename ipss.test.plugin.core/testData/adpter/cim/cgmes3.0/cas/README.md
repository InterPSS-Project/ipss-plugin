# CGMES 3.0 CAS / ReliCap fixtures (local symlinks)

Symlinks into `~/Documents/Temp/cgmes-test-data/` — **not** committed.

## Coverage stubs

| Class | Focus |
|-------|--------|
| `CGMESCasCoverageStubTest` | P0–P3 import smokes (SmallGrid→FullGrid, PST, ReliCap) |
| `CGMESCasType3HourCoverageStubTest` | Parameterized Type3 CGM hours (subset; `-Dipss.cgmes.type3.allHours=true` for all 24) |
| `CGMESCasP4AclfSmokeStubTest` | P4 NR load-flow converge smokes (`cgmes-p4-aclf`) |

Override CAS root: `-Dipss.cgmes.cas.root=` (parent of `v3.0/`).
