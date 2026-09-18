# CGMES 3.0 CAS / ReliCap fixtures (local symlinks)

Symlinks into `~/Documents/Temp/cgmes-test-data/` — **not** committed.

## Coverage stubs

| Class | Focus |
|-------|--------|
| `CGMESCasCoverageStubTest` | P0–P3 CAS + core ReliCap IGMs |
| `CGMESCasType3HourCoverageStubTest` | Parameterized Type3 CGM hours |
| `CGMESCasP4AclfSmokeStubTest` | P4 NR load-flow converge smokes |
| `CGMESReliCapDcCoverageStubTest` | Britheim/Portheim, HVDC corridors, multi-MAS CGM, NCP presence |

Override: `-Dipss.cgmes.cas.root=` / `-Dipss.cgmes.relicap.root=`.
