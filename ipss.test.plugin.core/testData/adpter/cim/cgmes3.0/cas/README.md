# CGMES 3.0 CAS / ReliCap fixtures

Local copies of the CAS v3.0.3 and ReliCap test configurations used by the coverage stubs. Directory names match the previous symlink layout.

## Coverage stubs

| Class | Focus |
|-------|--------|
| `CGMESCasCoverageStubTest` | P0–P3 CAS + core ReliCap IGMs |
| `CGMESCasType3HourCoverageStubTest` | Parameterized Type3 CGM hours |
| `CGMESCasP4AclfSmokeStubTest` | P4 SV-seeded NR + Aclf vs SvVoltage (`cgmes-p4-aclf`) |
| `CgmesSvCompareSupport` | SvVoltage reader + \|V\|/angle compare helpers |
| `CGMESReliCapDcCoverageStubTest` | ReliCap HVDC / multi-MAS CGM / NCP presence |

## P4 compare notes

- Seed LF from SvVoltage, then NR (`setInitBusVoltage(false)`).
- Compare \|V\| (pu) primarily; angles use differential reference (swing/first bus).
- Defaults: `vTolPu=0.02` (MiniGrid uses 0.05), `angTolDeg=1.0`, `minMatch=0.85` (MiniGrid 0.50), `minAngMatch=0.5`.
- Overrides: `-Dipss.cgmes.p4.vTolPu` / `angTolDeg` / `minMatch` / `minAngMatch`.

Override CAS root: `-Dipss.cgmes.cas.root=` (parent of `v3.0/`).
