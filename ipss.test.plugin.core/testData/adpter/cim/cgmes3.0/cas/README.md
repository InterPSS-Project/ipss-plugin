# CGMES 3.0 CAS / ReliCap fixtures

**Real file copies** of the CAS v3.0.3 and ReliCap configurations used by the coverage stubs.
Do **not** use symlinks into `~/Documents/Temp/...` — copy the needed profile XMLs (and any
supporting files the tests read) into this tree under the same directory names.

Resolution order in the stubs (`casDir` / friends):

1. `-Dipss.cgmes.cas.root=` (parent of `v3.0/`), then `v3.0/` + relative path
2. This in-repo directory (`testData/adpter/cim/cgmes3.0/cas/<name>/`)
3. Fallback Temp download tree under `~/Documents/Temp/cgmes-test-data/` (dev convenience only)

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

## Adding a new pack

```bash
# Example: copy a CAS v3.0 pack into the repo (no symlink)
CAS_ROOT=~/Documents/Temp/cgmes-test-data/cas-v3.0.3/CGMES_ConformityAssessmentScheme_TestConfigurations_v3-0-3/v3.0
DEST=ipss.test.plugin.core/testData/adpter/cim/cgmes3.0/cas/MyPack-Name
mkdir -p "$DEST"
cp -R "$CAS_ROOT/path/to/pack/"*.xml "$DEST/"
# Prefer XML profiles the tests need (EQ/SSH/TP/SV/EQBD). Skip large SHACL/xlsx unless required.
```
