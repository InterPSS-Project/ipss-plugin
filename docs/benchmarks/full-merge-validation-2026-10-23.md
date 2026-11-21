# Full validation before merge

Follow-up: the [Svedala package repair](svedala-common-data-fix-2026-10-23.md)
resolves that run's voltage-comparison failure without changing its tolerance.
All 21 related comparison tests pass. The subsequent
[8,387-bus fixed-Q repair](pegase8387-fixed-reactive-limit-2026-10-23.md)
also resolves its convergence gate. After correcting the no-event contract for
the supplied nonstationary operating points, the
[subsequent complete validation passes](full-merge-validation-g-2026-10-23.md).
The results below preserve the original failed full-run evidence.

The fresh validation gate did not pass. Core is green, but the full plugin run
reproduces three existing accuracy failures. No commits or merges were created;
both `master` branches remain unchanged. The repaired working changes and
previous performance checkpoints are preserved.

## Scope and results

Run `merge-gate-20261023-f` rebuilt both reactors from the current working
sources into new isolated output directories. Core ran first and installed its
fresh library before plugin validation. Both commands used `install`, offline
Maven, bounded logs and a 2 GiB test heap; core retained its Mockito Java agent.
The plugin command enabled `extended-tests`. All offline Python tool tests and
the neutral-language check ran afterward.

| Suite/module | Tests | Passed | Failures | Errors | Existing skips |
| --- | ---: | ---: | ---: | ---: | ---: |
| Core | 3,096 | 3,096 | 0 | 0 | 0 |
| Plugin core | 435 | 431 | 0 | 0 | 4 |
| Three-phase plugin | 468 | 440 | 0 | 0 | 28 |
| Plugin integration module | 3,262 | 3,205 | 3 | 0 | 54 |
| Offline Python tools | 394 | 394 | 0 | 0 | 0 |

The full plugin reactor reports 4,165 tests: 4,076 pass, three fail and 86 have
existing skips. The integration total now includes the three parameter-boundary
tests that were previously checked in a separate focused run. The language
policy check also passes.

Both Maven reactors completed their requested collection of modules. The plugin
reactor returned failure normally; no failure-ignore setting or test exclusion
was added. Source inventories taken before and after all checks match, including
test code and numerical configuration. Acceptance thresholds were unchanged.

## Remaining merge gates

1. `CGMESCasP4AclfSmokeStubTest.testP4_ReliCapSvedala_AclfVsSv`:
   68 of 86 compared voltage magnitudes meet the tolerance, or 79.07%, below
   the required 85%.
2. `MatpowerCase8387PegaseTest.testCase8387PegasePqNonDivergentNormalControlPowerFlow`:
   the PQ load flow does not meet its 0.001 pu mismatch tolerance within
   50 iterations.
3. `Texas2kSixCaseDynamicSmokeTest.allSixCaseStacksInitializeAndHold`:
   Case 1's Bus1051 complex-voltage drift exceeds the 0.0002 pu no-event limit.
   The separate six-case fault and generator-trip tests pass.

These are the same failures recorded in the
[compatibility-repair validation](controller-compatibility-repair-2026-10-23.md).
Their prior existence does not make the current full build pass. Both
repositories must meet the requested full-validation condition before the
milestone commits and merge commits can be created.

## Evidence and preserved branches

The [validation index](checkpoints/full-merge-validation-2026-10-23.json) pins
the private evidence manifest and archive. They retain every Surefire XML
report, bounded logs and statuses, exact commands, before/after source hashes,
working source patches, newly added test sources and the produced build jars.
The previous recording-verification index is retained as a hashed dependency.
Supplied references were preserved.

The working branch remains `restore/best-two-case-20261023` in both repositories.
The core `master` head remains `7a9808da4561612b5236d6a03abfe7c6238fb707`, and
the plugin `master` head remains `74985520add5240245f79e77e40f2fbd527cdd79`.
