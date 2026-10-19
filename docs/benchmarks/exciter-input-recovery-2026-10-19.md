# Exact exciter benchmark input recovery

Status: eleven input contracts recovered and 22 PowerWorld cold runs complete.
Final scoped regression gates pass. This is provenance/repeatability
evidence, not complete model or fresh cross-solver trajectory acceptance.

## What was missing and what is recovered

The [historical audit](exciter-evidence-audit-2026-10-17.md) recorded eleven
missing DYR inputs and six specification-hash mismatches. The latter already
have [separate current-specification cold evidence](powerworld-exciter-repeatability-2026-10-17.md).
This slice does not repeat or overwrite that completed six-model work.

All eleven deleted DYR files are found in immutable Git history with the
archived canonical SHA-256. The other required inputs also match their
archived content hashes. Thirty content-addressed resource assets now preserve
eleven complete RAW/DYR/vendor-DYR/specification contracts without restoring
obsolete filenames in the primary native-input directory. The seventeen-entry
contract catalog retains the six unavailable historical specification hashes.
Those six original specification bytes are still not recovered.

Recovered models: AC11C, AC7C, AC8C, AC9C, DC1C, DC2C, AC8B, EXAC1A,
ST2C, ST3C and ST5C. Native and PowerWorld DYR roles remain distinct. No model
is renamed to make a reader accept a different vendor dialect.

The hash contract replaces CRLF/CR with LF for the runner's declared text
extensions, retaining all other bytes and the final newline. It does not
claim to recover the historical platform's exact newline serialization.

## Permanent recovery and comparison guards

`tools/recover_benchmark_inputs.py` accepts only an exact expected canonical
content hash from the working input, a content-addressed asset or an immutable
Git blob at that path. It never selects a similarly named file or edits the
reference output. Recovery requires a new output directory inside `target/`,
validates every requested role before writing, and rejects path traversal,
conflicting roles and existing output directories. Eight offline tests cover
the recovery and rejection paths, including deleted history and corrupt assets.

`ExciterInputContractTest` freezes the catalog digest, checks all thirty asset
hashes and mutations, and requires exactly eleven complete contracts while
retaining the six specification gaps explicitly. It is registered in both the
ordinary core suite and the dedicated SMIB selection.

The existing exact `PowerWorldCsvRepeatability.compare` behavior is unchanged.
Its new diagnostic API reports changed-sample counts, maximum absolute errors
and times for every channel. It requires identical metadata, counts and
timestamps, and never interpolates, discards event rows or issues a tolerance-
based acceptance decision. Two additional tests check these boundaries and
prove that a reported discrepancy still fails the exact comparison API.

For example, recover one complete contract from the repository root into a
new workspace:

```powershell
python tools/recover_benchmark_inputs.py `
  ipss.test.plugin.core/src/test/resources/exciter-input-contracts.json `
  --assets ipss.test.plugin.core/src/test/resources/exciter-input-assets `
  --benchmark smib-genrou-ac11c --output target/new-exciter-inputs
```

The unchanged benchmark runner accepts the recovered specification with
`--repository target/new-exciter-inputs/smib-genrou-ac11c/workspace` and a new
`--output` directory. Repeat into another new directory; never publish over the
old reference. The slice's hidden-process wrapper and exact reproduction
inputs/scripts/logs are preserved in its private evidence archive.

## Cold-run results and historical discrepancy

All eleven specifications are regenerated twice in new PowerWorld processes,
with no parameter overrides or auto-correction. All 22 runs reach RAW/DYR load,
power flow, validation, initialization, solve, export and finalization. Each
strict cold-pair comparison retains 2,003 samples and two repeated-time event
samples and requires exact equality of every value.

Ten profiles additionally match every value of their older complete exports.
AC11C does not. With identical archived input hashes, its new pair is exactly
repeatable but differs slightly from the older executable's result:

| AC11C channel | Maximum historical difference | Time |
|---|---:|---:|
| Bus 1 voltage | 1.5498e-6 pu | 0.228 s |
| Bus 2 voltage | 5.365e-7 pu | 0.226 s |
| Generator active power | 0.00042725 MW | 0.4425 s |
| Generator reactive power | 0.001024485 Mvar | 0.257 s |
| Exciter state 1 | 0.000216841 | 0.100 s |
| Generator speed | 1.2e-7 pu | 0.545 s |

All channel rankings, including unchanged channels and event-side samples, are
retained privately with old/new executable identities and exact hashes. The
executable changed, but this does not prove the cause of the numerical drift.
The old export, manifest, embedded checkpoints and all InterPSS comparison
tolerances remain unchanged. AC11C historical equality remains a diagnostic,
not a tolerance-widening exercise or a claim of fresh coupled acceptance.

## Validation and remaining work

- [x] Recover all eleven missing input files by exact historical hashes.
- [x] Preserve thirty content-addressed assets and the seventeen-model catalog.
- [x] Cold-run/audit all eleven recovered specifications twice, retaining full
  outputs, initialization logs, staged inputs, scripts and manifests.
- [x] Require strict fresh-pair equality and distinguish ten historical matches
  from the AC11C discrepancy rather than treating both as the same claim.
- [x] Pass 23 focused checks, including all eleven existing model comparisons
  at unchanged tolerances and the input/CSV guards.
- [x] Pass 406 plugin-core tests (six existing optional skips) and 1,591 scoped
  SMIB/model/solver checks (one existing diagnostic skip), with zero failures
  or errors, plus 23 focused and fourteen offline recovery/language guards.
  Retain source/reference/report hashes for the scoped commit in private
  archive `2026-10-19-exciter-input-recovery`.
- [ ] Recover the six original specification byte sequences, diagnose AC11C's
  historical drift and independently accept the new complete cross-solver
  profiles, including post-initialization model/parameter readback. Selector,
  limiter, zero-time and lifecycle matrices remain open.

No production dynamic-model equation or parameter is changed in this slice.
No full Texas2k dynamic simulation is run.

Validation uses the code baseline `d3f1987be`; the intervening `78a7cc801`
commit changes only unrelated documentation. The isolated snapshot audit
matches 1,808 unchanged Java/POM files and four owned test/infrastructure
Java files, with no unexpected sources. Production model code is unchanged.
