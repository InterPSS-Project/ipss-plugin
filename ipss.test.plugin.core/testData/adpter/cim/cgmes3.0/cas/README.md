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

- Seed LF from SvVoltage, then NR (`setInitBusVoltage(false)`). SV is not written back as a solved result.
- One floor for every P4 case, including MiniGrid and the promoted packs. Dead buses (`|V| < 0.2` pu) are excluded. A flow match needs both P and Q inside tolerance on the sequence-correct terminal.
- `|V|`: `vTolPu=0.005`, `minMatch=0.98`. Angle: `angTolDeg=0.5`, `minAngMatch=0.95` (differential, swing or first live bus).
- Branch P/Q: `pTolMw=1`, `qTolMvar=1`, `minFlowMatch=0.95`. `missingBus=0` and `missingBranch=0` for every in-topology `ACLineSegment` and `PowerTransformer` terminal.
- Overrides: `-Dipss.cgmes.p4.vTolPu` / `angTolDeg` / `minMatch` / `minAngMatch` / `pTolMw` / `qTolMvar` / `minFlowMatch`.

## Adding a new pack

```bash
# Example: copy a CAS v3.0 pack into the repo (no symlink)
CAS_ROOT=~/Documents/Temp/cgmes-test-data/cas-v3.0.3/CGMES_ConformityAssessmentScheme_TestConfigurations_v3-0-3/v3.0
DEST=ipss.test.plugin.core/testData/adpter/cim/cgmes3.0/cas/MyPack-Name
mkdir -p "$DEST"
cp -R "$CAS_ROOT/path/to/pack/"*.xml "$DEST/"
# Prefer XML profiles the tests need (EQ/SSH/TP/SV/EQBD). Skip large SHACL/xlsx unless required.
```

## CAS 2.4.15 Type4 Difference packs

Copied under `MicroGrid-Type4-T4_*_Difference_v2/` from Temp `cas-v2.4.15/MicroGrid/Type4_T4/`.
These are **DifferenceModel** profiles (`EQ_DIFF` / `TP_DIFF` + SSH/DL), not stand-alone EQ/TP/SV
IGMs. Import stubs assert file presence and SSH readability; full Difference apply + P4 needs a
base model + Difference merge (not wired yet).

```bash
SRC=~/Documents/Temp/cgmes-test-data/cas-v2.4.15/MicroGrid/Type4_T4
DEST=ipss.test.plugin.core/testData/adpter/cim/cgmes3.0/cas
for d in T4_BE_BB_Difference_v2 T4_BE_NB_Difference_v2 T4_NL_BB_Difference_v2 T4_NL_NB_Difference_v2; do
  mkdir -p "$DEST/MicroGrid-Type4-$d"
  cp -R "$SRC/$d/"*.xml "$DEST/MicroGrid-Type4-$d/"
done
```
