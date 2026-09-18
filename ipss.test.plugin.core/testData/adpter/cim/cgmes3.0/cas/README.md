# CGMES 3.0 CAS fixtures (local symlinks)

These directories are **symlinks** into `~/Documents/Temp/cgmes-test-data/cas-v3.0.3/.../v3.0/`.
They are **not** committed to git (see `.gitignore`). CI without the Temp pack skips
`@Tag("requires-cas-download")` tests via `Assumptions.assumeTrue`.

Recreate after a fresh clone:

```bash
CAS_SRC="$HOME/Documents/Temp/cgmes-test-data/cas-v3.0.3/CGMES_ConformityAssessmentScheme_TestConfigurations_v3-0-3/v3.0"
DEST="testData/adpter/cim/cgmes3.0/cas"
mkdir -p "$DEST"
ln -sfn "$CAS_SRC/MiniGrid/MiniGrid-Merged" "$DEST/MiniGrid-Merged"
ln -sfn "$CAS_SRC/MicroGrid/MicroGrid-Type1/MicroGrid-Type1-BE-MAS" "$DEST/MicroGrid-Type1-BE-MAS"
ln -sfn "$CAS_SRC/MicroGrid/MicroGrid-Type1/MicroGrid-Type1-Merged" "$DEST/MicroGrid-Type1-Merged"
ln -sfn "$CAS_SRC/MicroGrid/MicroGrid-Type1/MicroGrid-BD-MAS" "$DEST/MicroGrid-BD-MAS"
```
