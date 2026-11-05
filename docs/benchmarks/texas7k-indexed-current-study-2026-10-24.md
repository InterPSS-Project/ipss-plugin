# Texas7k indexed-current study

The goal remains a complete ten-second simulation within 20 seconds, including
recording, with a quarter-cycle network step and preserved accuracy. This study
does not meet that goal. It establishes an experimental data-transfer path
that preserves every recorded value while reducing measured work.

## Accurate full-case data-transfer prototype

Two conditions prevented the existing primitive current handoff from running:
the operating-point compensation occupied the generic custom-current table,
and four algebraic converters were excluded from deferred publication.

The isolated prototype copies every compensation current into indexed storage
and adds those values directly to the current workspace. It also permits the
algebraic converter case only when command getters belong to the final,
field-backed REECA1 or fixed-command implementations. Replaceable controller
callbacks retain their exclusion. Other existing eligibility guards remain.
Model coefficients, right-hand sides, integration, convergence settings,
matrix construction and factorization policy are unchanged.

This is an isolated compiled overlay, not a production change. Its fixed-source
API still needs configuration-atomicity, ownership, mutation, membership-change,
observer/fallback and reinitialization tests before integration into the engine.
Caller-owned injections must retain their existing behavior.

The complete 6,717-bus case retains all 2,705 dynamic records and 120 BSG entries.
The fault is applied at bus 111180 at 0.1 s and cleared after five cycles.
The network step is 1/240 s, voltage-correction tolerance is 1e-4 pu, and the
maximum iteration allowance remains 50.

Both paths record 2,403 float64 samples of all 6,717 bus voltages and 15 selected
machine channels, including event-side samples. Every trace is 129,464,040
bytes. All four traces have the same SHA-256:

`f355a8311595b4cecf38cdbf603b17bd0a0a0eaa9c2e944b1cc7a9d0bc8782b7`.

All timed stage counts also match: 7,200 network calls, 130,955 iterations and
a maximum of 27 iterations in one call. The indexed path executes 130,957
primitive solves, including initialization work outside the timed call counter.
No acceleration is used in this comparison.

## Warmed in-process comparison

Both paths run in one JVM on logical CPU 6 with the same Java-only solver,
array settings, rectangular coordinates, row solve, compiled controller
settings, 2 GiB heap and serial collection. Each path receives a complete
warmup before measurement. Compilation and process launch are outside the
measurement; import, preparation, dynamics, recording and serialization are
inside it.

| Phase | Whole-run seconds | Network seconds |
| --- | ---: | ---: |
| Control warmup | 96.969 | 64.413 |
| Indexed warmup | 85.625 | 62.485 |
| Measured control | 106.459 | 80.343 |
| Measured indexed | 77.337 | 57.449 |

This is one ordered A/B pair, not a stable median or an order-balanced study.
Earlier control timings were lower. The observed measured-pair reduction is
27.4 percent; the warmup pair shows a smaller reduction. Further paired timing
is required before assigning a dependable speedup. The 20-second target remains
unmet.

The four completed, byte-identical generated binaries share one physical file
through lossless hard links. All four paths, complete samples, run summaries
and hashes remain available. This avoids 388,392,120 bytes of duplicate storage
without removing channels or event-side rows. Supplied references are untouched.

## Why the faster two-step result is still unaccepted

The unchanged bus-150510 local equivalent was executed through ten seconds at
three two-step resolutions and with the ordinary quarter-cycle method:

| Method | Step | Final terminal voltage |
| --- | ---: | ---: |
| Ordinary modified Euler | 4.167 ms | 1.04029852 pu |
| Explicit two-step | 4.167 ms | 0.95966353 pu |
| Explicit two-step | 2.083 ms | 1.04029964 pu |
| Explicit two-step | 1.042 ms | 1.04030275 pu |

The quarter-cycle two-step trace develops persistent fast oscillations. Against
the finest two-step trace, terminal-voltage RMSE falls from 0.0635642 pu at
4.167 ms to 0.000449817 pu at 2.083 ms. Comparisons match integration ticks and
explicit event-side labels and include every stored sample of each compared
coarser trace. The original full sample sets remain retained.

This local refinement test identifies a coupled numerical-resolution problem;
it does not establish independent whole-model trajectory acceptance. The
existing independent equivalent profile remains quality-rejected because of
two pitch-state initialization residuals. The global quarter-cycle performance
requirement has not been replaced by a finer-step benchmark.

## Acceleration and profiling evidence

A separate shallow-acceleration experiment retains ordinary integration and
reduces network iterations to 56,837. Its unprofiled measured whole-run time is
61.327 s. The profiled run takes 75.374 s and is not used as a comparable timing.
The accelerated trajectory differs from the ordinary control by up to
0.00946046 pu voltage and 0.0782424 degrees in the recorded rotor angles.
It remains unaccepted for unchanged-trajectory performance claims.

The profile contains 3,157 execution samples. Network solution is present in
2,236 samples and differential integration in 736. Frequent leaf locations
include voltage access, triangular solve, convergence observation, collection
iteration and current-table addition. Allocation sampling weights are not
treated as exact per-site allocation totals.

The next engine work is to make the indexed fixed-source contract safe and
testable, then reduce its remaining bookkeeping and nonlinear-solve cost.
The controller/integration accuracy blocker remains separate from this
unchanged-trajectory data-transfer path.

The [machine-readable checkpoint](checkpoints/texas7k-indexed-current-study-2026-10-24.json)
indexes the prototype sources, compiled overlay, full traces, profile,
reproducers, refinement plot and integrity hashes in private evidence.
