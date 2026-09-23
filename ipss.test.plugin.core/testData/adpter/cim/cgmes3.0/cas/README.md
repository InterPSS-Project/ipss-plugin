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
| `CGMESCasP4AclfSmokeStubTest` | P4 SV-seeded NR + Aclf vs SvVoltage / SvPowerFlow (`cgmes-p4-aclf`) |
| `CGMESCasP4AclfUnconvergedStubTest` | ReliCap DC Espheim–Svedala standalone (synthetic swing + soft floors); full AC+DC CGM assemble still needs assembled TP/SV |
| `CgmesSvCompareSupport` | SvVoltage / SvPowerFlow readers + \|V\|/angle/flow compare helpers |
| `CGMESReliCapDcCoverageStubTest` | ReliCap HVDC / multi-MAS CGM / NCP presence |

## P4 compare notes

- Seed LF from SvVoltage, then NR (`setInitBusVoltage(false)`). SV is not written back as a solved result.
- Closed retained switches are zero-Z branches; UCTE synonym bases (220↔225, 380↔400) are unified across line+switch islands so LF does not auto-turn them into 1:1 xfrs.
- Dead buses (`|V| < 0.2` pu) are excluded. A flow match needs both P and Q inside tolerance on the sequence-correct terminal.
- Shared hard floor (MiniGrid / PowerFlow / PST / SmallGrid): `|V|` `vTolPu=0.005` / `minMatch≈1.0`, angle `0.5°` / `0.95`, branch P/Q `1` MW/Mvar / `0.95`, `missingBus=0`, `missingBranch=0`.
- Soft / intermediate floors (see smoke class javadoc for live values):
  - MicroGrid Type1 / Type2 / BaseCase: shared \|V\|; flow soft `5` MW / `10` Mvar
  - Svedala-Merged: `|V|` `0.015` / `0.90`, angle `1.5°` / `0.85`, flow `0.43`
  - ReliCap IGMs: voltage-primary; angle floors `0.50`–`0.70`; Portheim hard NR; Espheim soft NR
  - T4 / RealGrid / Type3 hours: hard NR; `|V|` `0.02` / `0.85`, angle ≥`0.50`
  - FullGrid: hard NR; `|V|` `0.02` / `0.70`, soft angle
  - DC Espheim–Svedala (Unconverged): synthetic swing; soft `|V|` `0.05` / `0.50`
- Overrides: `-Dipss.cgmes.p4.vTolPu` / `angTolDeg` / `minMatch` / `minAngMatch` / `pTolMw` / `qTolMvar` / `minFlowMatch` / `maxMissingBus`.

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
