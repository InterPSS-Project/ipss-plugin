# Native exciter evidence audit, 2026-10-17

## Coverage and changes

The May 2026 approved catalog's 64 exciter rows with native reference simulator mappings
resolve to 59 distinct native models. The generated
[evidence inventory](../exciter-evidence.md) links all 59 to native DYR fixtures,
focused tests, embedded checkpoints and registered trajectory comparisons.
The guard checks fixture presence, canonical model name and accepted native
record length, as well as test registration and readable checkpoints.

The selected comparisons use 46 archived PowerWorld datasets and 13 native
reference simulator datasets. Additional ANDES or native allocation comparisons remain in
the suite; this is one explicit evidence path per approved native mapping,
not an exhaustive profile inventory or a VERIFIED designation.

The audit exposed six focused classes absent from CorePluginTestSuite:

- DStabNetworkBuilderScrxTest: 3 tests.
- Dc3aExciterTest: 4 tests.
- Esac4aExciterTest: 4 tests.
- Exac4ExciterTest: 5 tests.
- Exst2ExciterTest: 5 tests.
- St5bExciterTest: 4 tests.

All six are now registered; SCRX's class is public for suite selection.
The existing assertions, equations, fixture parameters and trajectory
tolerances are unchanged. The inventory plus restored classes pass all 28
focused tests, with zero failures, errors or skips.

## Local PowerWorld provenance audit

The private archive was inspected read-only against the 46 selected PowerWorld
manifests. Its runner's `_canonical_bytes` contract replaces CRLF and CR with
LF for text files and otherwise preserves bytes, including a final newline.
This is distinct from the no-final-newline native embedded checkpoint
fingerprint contract. Raw-byte hashes alone therefore cannot adjudicate these
PowerWorld manifest entries on a Windows checkout.

All 46 PowerWorld output CSVs match their recorded canonical hashes. Only 29
of the 46 datasets also have every declared input present and hash-matching
in this local checkout. The other 17 have these reproducibility gaps:

| Gap | Native models |
|---|---|
| Missing declared PowerWorld-specific DYR | AC11C, AC7C, AC8C, AC9C, DC1C, DC2C |
| Missing declared DYR | AC8B, EXAC1A, ST2C, ST3C, ST5C |
| Current specification hash differs | AC7B, ESAC4A, ESAC6A, ESAC8B, EXAC4, REXSYS |

No old manifest hash was rewritten and no archived simulator output was
replaced in this slice. A matching output hash protects an archived export;
it does not prove a current runnable benchmark or complete embedded/raw
sample equivalence. Even the 29 input-complete datasets still need current
simulator-version, license, initialization and twice-cold-regeneration review
before making a fresh repeatability claim.

### AC1C twice-cold discriminator

The existing private PowerWorld runner was executed twice with the unchanged
`specs/smib-genrou-ac1c.json` specification into separate new directories:
`target/exciter-cold-20261017/run-a` and `run-b`. Each run independently starts
PowerWorld and reaches RAW/DYR loading, load flow, validation, initialization,
simulation, export and finalization. Both exit zero with `status=complete`.
The SMIB profile is a three-phase j0.2 pu fault at generator Bus 1 from 0.05
to 0.10 s, with 0.5 ms steps through 1 s; auto-correction remains disabled.

The installed PowerWorld executable reports version 24.2026.9.3, SHA-256
`06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c`.
Both fresh CSVs have the same canonical SHA-256,
`5afcdfc6de50b1d7d1719995336e85ecf1884bc14e3b941ba76c7c31aa6423a1`,
and 437,506 canonical bytes. The older archived export remains at
`419f8e6781db515870a1ffd6eeedec69e13d47508667abd22f918cbeccc204f4`
and 439,509 canonical bytes, from a different executable hash.

Comparison of all 2,027 lines finds exactly 2,003 data rows, with time plus
19 channel values per row. Every data value, timestamp (including event-side
duplicates) and metadata line is identical. The only change is removal of
one empty trailing CSV field on each data row: 2,003 fewer bytes. This proves
repeatable current export and full numerical agreement with the old export
for this one AC1C profile, not fresh acceptance of every exciter.

The existing AC1C InterPSS trajectory regression remains part of the full gate
below. Neither its checkpoints nor its comparison bounds are replaced.
The two raw runs, their staged inputs, runnable AUX, logs and manifests stay
local under the repository's private-artifact policy.

Runner file raw-byte SHA-256:
`0d6abbe93113afcc7113957d30278dda69e4392973a5da3aab611f27b4c9f61e`.
Canonical input hashes are RAW
`986c6b3c8201ff0c4581a60e48af3438a14c692c12e4f8b966f4f2c710daa02b`,
DYR `790d598047e32f0eb38b9996cbc8e41acd5988d704d9ae97e239f1dafb32b5d9`,
and specification
`7acb565b32d523f699f948a5d150bc43288d98d03c94f817a7b089b8b2783217`.
They match the older reference's declared inputs. To reproduce locally:

```powershell
python ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py `
  ipss.test.plugin.core/testData/reference/powerworld/specs/smib-genrou-ac1c.json `
  --output target/exciter-cold-20261017/run-a --timeout-s 60
```

Repeat with a different new output directory. Do not use `--publish` to
overwrite the older reference.

Next work must recover the exact missing inputs and resolve specification
changes from immutable history, then regenerate into new run directories.
Do not rename native/vendor models, reconstruct inputs from desired curves,
or update manifests merely to bless current files. Preserve old artifacts and
record any new exact inputs, simulator version and hashes separately.

## Release gates

Full registered reactor validation exits zero on 2026-10-17:

```text
mvn -q test -Pextended-tests --fail-at-end
    -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest
    -Dsurefire.failIfNoSpecifiedTests=false
```

Surefire reports written during this run contain 2,688 tests: 2,575 passed,
113 skipped, zero failures/errors. Module totals are 129 core-plugin tests
(4 skipped), 468 three-phase tests (28 skipped), and 2,091 integration tests
(81 skipped). All three evidence-inventory tests and all 25 restored focused
tests pass. The existing AC1C InterPSS trajectory test also passes at its
unchanged bounds. The local full log is `target/exciter-evidence-regression.log`.
The unrelated untracked WECC240/JAX comparison is excluded and unchanged.
Private Texas2k source RAW/DYR/IDV inputs are still unavailable, so six-case
flat/smoke acceptance and the 15-fault matrix are unexecuted for this slice.
Stabilizer and renewable-control evidence inventories remain open.
