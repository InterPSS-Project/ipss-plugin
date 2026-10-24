# Successful milestone validation

Run `merge-gate-20261023-g` passes the complete core reactor, complete plugin
reactor with `extended-tests`, offline Python tool tests and neutral-language
check. Both reactors were freshly compiled from the working sources into new
isolated directories. Source inventories match before and after validation.

| Suite/module | Tests | Passed | Failures/errors | Existing skips |
| --- | ---: | ---: | ---: | ---: |
| Core | 3,097 | 3,097 | 0 | 0 |
| Plugin core | 439 | 435 | 0 | 4 |
| Three-phase plugin | 468 | 440 | 0 | 28 |
| Plugin integration | 3,262 | 3,208 | 0 | 54 |
| Offline Python tools | 394 | 394 | 0 | 0 |

The Java suites report **7,180 passed**, zero failures or errors, and 86 existing
skips. No new test exclusions or failure-ignore setting was added. All commands
used bounded logs; Maven test heaps were limited to 2 GiB. Core retained its
required Mockito Java agent. The plugin reactor included the sample module.

## Changes since the preceding full gate

- The [Svedala package dependency repair](svedala-common-data-fix-2026-10-23.md)
  resolves missing base-voltage definitions. All 116 compared buses satisfy the
  subsequently tightened, user-requested **0.006 pu** voltage tolerance.
- The [8,387-bus reactive-limit repair](pegase8387-fixed-reactive-limit-2026-10-23.md)
  retains fixed-Q sources when reactive bounds are equal, eliminating limit
  cycling. Its normal-control PQ test converges in 20 iterations at the unchanged
  0.001 pu mismatch tolerance. Declared CGMES capability curves now take
  precedence over scalar placeholder limits.
- The [Texas2k no-event contract](texas2k-no-event-contract-2026-10-23.md)
  follows the user's clarification that Case 1 is not a stationary initialization.
  The six-case operating-point suite checks initialization, successful no-event
  integration and finite recorded quantities. Motion remains diagnostic. The
  separate supplied ALLSYM equilibrium test retains its strict requirements.

The [earlier compatibility repairs](controller-compatibility-repair-2026-10-23.md)
and historical performance checkpoints remain preserved. This run makes no new
warmed-runtime measurement or full reference-trajectory accuracy claim.

## Evidence and integration

The [validation index](checkpoints/full-merge-validation-g-2026-10-23.json)
pins the private manifest and archive. The archive retains all test XML,
bounded logs and statuses, commands, before/after inventories, source patches,
new test sources, freshly built jars and required Common Data/license files.
The focused six-case/ALLSYM verification and preceding repair indexes are also
retained.

Packaging preserves the vendored upstream Common Data XML and license bytes
through targeted Git attributes, including their upstream trailing spaces.
This packaging rule does not change the validated fixture contents or Java
sources.

The index captures the successful **pre-commit, pre-merge** validation snapshot.
Subsequent local milestone commits and merge commits are recorded separately
in `merge-receipt.json` alongside its private manifest. The merge check requires
each resulting `master` tree to equal its tested milestone commit. The repository
main branches are named `master`.
