# PowerWorld exciter repeatability, 2026-10-17

## Purpose and acceptance boundary

The [exciter audit](exciter-evidence-audit-2026-10-17.md) found six current
specifications whose hashes differ from their older manifests: AC7B, ESAC4A,
ESAC6A, ESAC8B, EXAC4 and REXSYS. The old specifications are not recovered by
this work. Instead, the current specifications are preserved with newly
generated runs, without modifying the older manifests or exports.

Before running, all six specifications were checked against the old manifests:
RAW/DYR paths, selected channels, 0.5 ms solver step, 0-1 s interval, and
Bus 1 three-phase j0.2 pu fault from 0.05 to 0.10 s agree. Auto-correction
remains disabled, and no model overrides are present. Available RAW/DYR
inputs match their old canonical hashes. This identifies the current
experiment; it does not manufacture the missing historical specification bytes.

## Permanent comparison tool

`org.interpss.core.dstab.reference.PowerWorldCsvRepeatability` is a reusable
public test utility and command-line entry point for the existing runner's
SINGLE-result CSV format. It compares the entire export, not just embedded
checkpoints or post-event samples:

- Require matching channel metadata, channel order and contingency.
- Require complete exports, finite numeric values and the declared row width.
- Preserve every timestamp, including pre/post-event duplicate samples.
- Require nondecreasing time and identical sample counts.
- Require numerical equality for every timestamp/channel; there is no error
  tolerance and no interpolation, timestep alignment or parameter fitting.
- Accept only representation changes: platform newlines, equivalent numeric
  spellings, and one known empty trailing field on a data row. Never discard
  a real channel, empty sample or inconvenient event-side row.

The tool is a repeatability guard, not an InterPSS-versus-PowerWorld solver
error bound. Existing trajectory tests remain responsible for cross-solver
comparison at their unchanged tolerances. All eight new guard tests and the
four existing native checkpoint integrity tests pass. The new test class is
registered in CorePluginTestSuite.

After compiling the test module, run from the repository root:

```powershell
java -cp ipss.test.plugin.core/target/test-classes `
  org.interpss.core.dstab.reference.PowerWorldCsvRepeatability `
  expected.csv actual.csv
```

The process exits nonzero for an invalid or numerically different export.
Its summary reports sample count, channel count and repeated-time sample
count. The prior AC1C cold run also passes this new guard: 2,003 samples,
19 channels and two repeated-time samples.

## Cold-run evidence

Each of the six current specifications is run twice, with fresh simulator
processes, under `target/exciter-cold-six-20261017/<model>/a` and `b`.
Outputs, staged native inputs, runnable AUX, logs and manifests remain local
under the repository's private-artifact policy. No old reference is replaced.
The private runner is unchanged from the earlier AC1C experiment (raw-byte
SHA-256 `0d6abbe93113afcc7113957d30278dda69e4392973a5da3aab611f27b4c9f61e`).

PowerWorld reports version 24.2026.9.3, executable SHA-256
`06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c`.
The export hash uses the runner's documented CRLF/CR-to-LF normalization,
retaining final newline and any trailing empty CSV field. This hash is not
silently changed to treat different serializations as the same artifact.

All twelve runs exit zero, reach validation, initialization, solve, export and
finalization, and have `status=complete`. Each cold pair has identical canonical
CSV hashes. The new strict verifier passes both A-versus-B and archived-versus-A
for all six models. Each comparison retains all 2,003 samples, including two
repeated-time event-side samples; every channel value is numerically identical.

| Model | Channels | Canonical output SHA-256, both cold runs |
|---|---:|---|
| AC7B | 20 | `1d28faaa02e97cbf8603daa8adbf69e3426bcb440900ead6a9da0fb6f06770ca` |
| ESAC4A | 17 | `5b2aaf38018deac9d0372e366e6a658bb9ecab4af129c0bc020ecf12baad0b16` |
| ESAC6A | 19 | `e8546d0840196d25bfd1e04f49db928d29c4b7f0d8df0a99255592dc7cd801a0` |
| ESAC8B | 19 | `dbb14c9a3f570698baab7132c2d0710db4d9f0aba4c70864046648aac3629082` |
| EXAC4 | 17 | `5b2aaf38018deac9d0372e366e6a658bb9ecab4af129c0bc020ecf12baad0b16` |
| REXSYS | 24 | `88e97a444f2303fa4556ee1178dc93e9d28e3b66808a9cbe53d0e39a1fdbd23f` |

ESAC4A and EXAC4 have identical exports for this common profile. That is not
evidence that their model definitions or all limiter/selector branches are
interchangeable. Their native records and focused tests remain distinct.

Current specification snapshots are preserved as `<model>/source-spec.json`,
and the unchanged runner is snapshotted at the cold-run root. Each runnable
AUX uses its own staged `inputs/network.raw` and `inputs/powerworld.dyr`.
All twelve CSV, staged RAW/DYR and specification hashes were independently
checked against the new manifests.

| Model | Current specification canonical SHA-256 |
|---|---|
| AC7B | `f87c8bef5540fc7a35c5ec49c3a38fa7dbad24dea23c879970a8afeddc207f4e` |
| ESAC4A | `a259ce7fa12c3b86ba64e4f692a6c8470d0937f6ee5d446952b4940c5dbc3503` |
| ESAC6A | `5cee9c5ae47ed59d7ddc1c2e37e220df591ccef5593d3e1f46e5f3e3bbe0100a` |
| ESAC8B | `536674fd3808be9283cfe981ed4b66e8b2359bed4b8edc697a618c001576d449` |
| EXAC4 | `7771cf2433508fab3378172f6c800662b9f8bbb7dea5d2f52d43e0b8ccb9de57` |
| REXSYS | `08421fe7eb401f7d4cbefba093509b912a3fa19cb8e1f7a4c0c985460cf05489` |

Run the existing private runner with the chosen current specification and a
new output directory, then repeat in another new directory. For example:

```powershell
python ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py `
  ipss.test.plugin.core/testData/reference/powerworld/specs/smib-genrou-ac7b.json `
  --output target/new-ac7b-run --timeout-s 60
```

Do not use `--publish` to replace the older export. Raw artifacts remain in
the private archive; this report records the run identities and gate outcomes.

## Release gates and remaining work

The full registered reactor regression, including CorePluginTestSuite, exits
zero on 2026-10-17:

```text
mvn -q test -Pextended-tests --fail-at-end
    -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest
    -Dsurefire.failIfNoSpecifiedTests=false
```

Surefire reports written during this run contain 2,696 tests: 2,583 passed,
113 skipped, zero failures/errors. Module totals are 129 core-plugin tests
(4 skipped), 468 three-phase tests (28 skipped), and 2,099 integration tests
(81 skipped). All eight new verifier tests and all six selected existing
InterPSS trajectory comparisons pass at unchanged bounds. The local log is
`target/powerworld-repeatability-regression.log`.

The unrelated untracked WECC240/JAX comparison remains excluded and unchanged.
No production equations, model parameters, embedded references or trajectory
tolerances are changed in this slice.

The eleven other PowerWorld gaps in the earlier audit involve missing declared
DYR inputs and remain open. Historical specification-hash discrepancies for
these six models are preserved, even if current repeatability succeeds.
Neither a repeatable SMIB fault nor the public regression proves every limiter,
selector, zero-time branch, lifecycle event or Texas2k system acceptance.
The original Texas2k source ZIP was rediscovered in the parent folder during
this slice. Its 27 native input files are staged separately and byte-hash
verified; see the plan's availability correction. The full regression began
without the private dataset. Its strict flat and 15-fault acceptance gates
remain unexecuted, and the original Case 1 version-34 RAW must be handled
explicitly rather than silently substituted for a derived version-36 file.
