# Voltage-history optimization and test relevance

Follow-up: the [retained-solver repair](controller-compatibility-repair-2026-10-23.md)
implements the recommendation below. Its 26 convergence/lifecycle checks and
full core suite pass without restoring the removed history-table implementation.
This page retains the original comparison and its failures.

Keep the restored fast implementation. Restoring `IndexedVoltageRecordTable`
solely to compile its tests is not justified. Its implementation-specific
tests should follow the decision to retain or retire that implementation.
General convergence and callback contracts still require coverage and repair.

## What the tests mean

The missing helper has 24 implementation-specific test methods across three
classes: eight in `IndexedVoltageRecordTableTest`, eleven in
`RevisionVoltageValidationTest` and five in `ScalarVoltageScanTest`. They check
bound hash-table entries, revision reuse, escaped views, scalar scan eligibility,
clone/serialization behavior and optimization counters. Those exact mechanisms
are absent from the restored solver. A counter asserting that the removed fast
path ran is not a requirement for a different implementation.

Some underlying expectations remain useful: both convergence norms and their
boundary rules, live previous-voltage history, first-invalid-bus diagnostics,
minimum iteration counts, and safe behavior around callbacks and mutations.
Move such assertions to the retained implementation where applicable. Retire
tests solely concerned with the removed helper's private mechanics rather than
restore unused production code to satisfy them. Do not disable general solver
tests as part of that cleanup.

## Performance evidence checked

The original updates were real optimizations of their respective older solver
configurations. The retained evidence archives were checked by SHA-256, and
the reported medians were recalculated from their raw run records.

| Historical comparison | Control median | Enabled median | Reduction |
| --- | ---: | ---: | ---: |
| Indexed batch voltage history | 19.033455 s | 17.865658 s | 6.14% |
| Revision-based membership validation | 14.543461 s | 13.828422 s | 4.92% |
| Scalar voltage scan | 9.719638 s | 9.608225 s | 1.15% |

These are separate historical controls and configurations, with complete
recording included. They must not be added together or compared directly with
the restored 6.429334-second Case 2 result to attribute an end-to-end regression.
The scalar comparison has overlapping individual timings and a small measured
effect. It does not prove the update caused the later slowdown.

The restored solver already keeps previous voltages in an indexed array and
checks convergence through the voltage-publication observer inside the network
solve. It does not use the separate `IndexedVoltageRecordTable.scan` pass.
Reintroducing that table is therefore not a demonstrated performance requirement
for the retained architecture. No new complete-simulation timing comparison of
that table against the restored fused path was run in this review.

## Independent behavior check

To distinguish obsolete optimization tests from useful solver tests, this
review ran a separate set of 24 convergence and callback tests against two
isolated core builds with the same launcher and external libraries:

| Core implementation | Tests found | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Earlier solver, before the table was introduced (`9e479a4f5`) | 24 | 24 | 0 | 0 |
| Restored solver (`46c8576e6`) | 24 | 7 | 17 | 0 |

These are not the 24 helper-specific tests described above. They comprise
`DStabNetworkConvergenceTest` from the parent of the table-introduction commit
and the current, unchanged `DStabNetworkIterationLifecycleTest`. The older
convergence test source avoids the subsequently added table-toggle/counter
test while preserving the preexisting behavioral assertions. Repository test
files and production sources were not edited. The earlier core was rebuilt
from all 1,833 main Java sources; the restored core used the fresh source build
from full validation. No archived engine fallback was on either test classpath.

Failures involve duplicate/unassigned convergence slots in synthetic fixtures,
casting proxy networks to `BaseDStabNetworkImpl`, and downstream diagnostic or
callback expectations that are not reached after those failures. This establishes
a compatibility difference under the same test fixtures. It is not evidence of
17 independent numerical errors in the supplied full-network runs.

The earlier solver passes without `IndexedVoltageRecordTable`, demonstrating
that restoring the helper is not necessary to preserve these contracts. Keep
the efficient standard-network path and address generic fallback/fixture
preconditions explicitly. The full-repository merge gate remains failed until
the meaningful contracts and remaining test-build incompatibilities are resolved.

## Evidence

The [review index](checkpoints/voltage-history-test-relevance-2026-10-23.json)
pins the historical evidence dependencies, recalculated performance summaries,
both isolated runtime snapshots, source revision, exact test sources, compile
and run commands, bounded logs and all individual failures. No production
source changes, restored optimization, merges or milestone commits were made
for this review. Supplied references and existing performance checkpoints are
preserved.
