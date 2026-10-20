# Case 2 efficiency baseline at 1e-5 network tolerance

The current target is **15 seconds for ten simulated seconds**, reduced from the
20-second target used when collecting this baseline. It has not been achieved.
See the [direct-controller experiment](case2-direct-controller-experiment-2026-10-20.md)
for the subsequent measurements. This check uses the compiled production classes, a quarter-cycle
step (1/240 second), the established Case 2 bus-6307 event, current-P/impedance-Q
loads, and a **1e-5 maximum-component voltage stopping tolerance**. The maximum
iteration count remains 500. No endpoint is skipped or convergence rule changed.

## Measured time

| Run | Integration, seconds | Network portion, seconds | Complete process work reported by runner, seconds |
| --- | ---: | ---: | ---: |
| Baseline with profiling | 38.8441 | 18.8714 | 42.9810 |
| Baseline without profiling | 37.1794 | 18.0841 | 40.7984 |
| Candidate without profiling | 36.3253 | 17.1514 | 40.1595 |

Integration excludes ordinary CSV serialization and initialization. The enclosing
runner time includes preparation and serialization, but excludes Java process
launch and the Python provenance checks. These are single fresh-process
observations. The candidate's observed 2.30% reduction needs repeat
timing before it can be treated as an established benefit.

## Bottlenecks and candidate

The profiled baseline spends 18.87 seconds in network solution and about 19.97
seconds elsewhere in integration. It performs 38,819 iterations in 4,800 calls,
with a maximum of 20 iterations per call. Source inspection confirms numeric
factorization is reused until the matrix is dirty.

The largest sampled leaf cost is EMF collection iteration (10.18% of execution
samples), followed by factored triangular solves (6.61%), angle arithmetic (5.84%)
and compiled signal resolution (5.41%). These sample percentages are indicators,
not isolated wall-time measurements. Garbage-collection pauses total 0.110 seconds.

The isolated candidate changes five core classes: indexed bus/device traversal
in hot loops, reuse of the ordinary linear-solve work buffer, a bit-keyed immutable
rectangular-voltage cache, and reuse of the zero complex constant. Model equations,
operation order within each injection, and voltage-dependent injection refreshes
remain unchanged. The candidate is **not applied to production**.

## Numerical check and limits

Separate full-channel runs retain all **2,405 samples and 4,810 channels**,
including negative-time history and both fault-event sides. Their decompressed
CSV contents match byte for byte: **11,568,050 physical values**. Iteration counts
also match exactly. Full diagnostic export timings are excluded from the timing
table above.

This establishes Case 2 non-regression relative to the unchanged run at the same
tolerance. It does not establish agreement with the supplied reference, resolve
previous reference discrepancies, or validate every lifecycle supported by the
five changed classes. The next implementation work must achieve a substantially
larger saving and complete focused lifecycle tests before production promotion.

Private run sources, runtime hashes, profile summaries and trace dependencies are
listed in `target/case2-network-efficiency-verification-20261020/performance-summary.json`.
The current source build passed offline; the initial online build attempt failed
on Maven cache access before compiling. Both bounded logs are retained.
