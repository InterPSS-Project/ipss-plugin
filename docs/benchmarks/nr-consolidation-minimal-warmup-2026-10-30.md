# Consolidated core NR: one warmup and one measured solve

The consolidated maintained core misses the InterPSS / RustPower <= 1.2 runtime-ratio target on both large cases with this minimal warmup protocol. Every solve converges and passes independent accuracy checks. Each number below is one observation; no timing samples were removed.

| Case | RustPower KLU ms | Core CACHED ms | Core GENERIC ms | CACHED / RustPower | <= 1.2 |
| --- | ---: | ---: | ---: | ---: | --- |
| ACTIVSg25k | 47.1063 | 201.4729 | 3644.6750 | 4.277 | Fail |
| ACTIVSg70k | 244.2178 | 407.2549 | 10208.4142 | 1.668 | Fail |

These measurements use the public `LoadflowAlgorithm.loadflow(false)` pipeline and explicit per-instance `NrExecutionMode.CACHED` / `GENERIC`. Both strategies execute the consolidated core formulations. Execution reports verify the requested and actual strategy; cached runs retain one preparation, symbolic structure and the eligible polar workspace. Core numerical source is commit `6e7e66180` with the unrelated working changes recorded by the full source-reactor snapshot. Exact executed sources, classpath hashes and commands are retained.

The [previous one-warmup report](rustpower-large-single-warmup-2026-10-30.md) measured the pre-consolidation runtime: ratios 2.047 and 1.668. The present 25k observation is slower; these are different source snapshots and separate single observations, so they do not establish a cause or a steady-state regression size. The earlier 50-warmup/100-sample batch also used the older implementation. Its passing large-case ratios do not qualify the consolidated implementation. No warmed performance claim is made here.

## Protocol and verification

Each case/runtime uses a fresh process pinned to logical CPU 2, affinity mask `0x4`, with exactly one warmup and one measured solve. Java's required initialization solve is its only warmup. Across RustPower, cached NR and generic NR there are six measured solves and twelve total solves. A setup attempt stopped on a sandboxed Git-provenance read before any solver phase; its evidence is retained separately and adds no solves.

Inputs and starting voltages are unchanged from the qualified large-case cohort: 25k uses compensated flat start; 70k uses the saved-state restart with required reference/PV setpoints. Both use tolerance `1e-6` pu, maximum 20 iterations and the existing fixed-control canonical nodal models. RustPower reports five iterations for 25k and six for 70k; InterPSS reports six for both. These are nodal solve comparisons, with the normalization and control limitations described in the [large-case qualification](rustpower-large-cases-2026-10-30.md).

The Java timer includes voltage reset and public load flow, including final voltage publication and generation allocation. RustPower includes voltage reset, solve and calculated-power retrieval. Import, initial preparation, process launch, compilation of the benchmark driver and post-solve checks are outside the timer. Runtime JIT work during a measured solve remains included. Java heap is bounded at 2 GiB. No builds or profiling were run as part of the timing cohort.

- The largest independent InterPSS residual is `3.02793e-8` pu; maximum complex-voltage error is `5.49596e-9` pu. Both strategies meet the qualification limits.
- Actual cached reports show zero rebuilds during the measured solve, four/five Jacobian assemblies and factorization requests for 25k/70k, and six triangular solves each. Aggregate diagnostics verify one plan and two workspace scopes per case.
- Both explicit mode reports record two public load-flow calls: one warmup and one measured solve. Raw CSVs retain the single sample for each runtime, including the slow generic observations.
- Input/file hashes, runtime fingerprints and CPU-affinity checks pass. Benchmark telemetry records batch compilation/process CPU totals, including initialization and untimed checks; these totals do not isolate compilation within the measured solve.
- Before timing, the corrected combined source reactor executed 7,853 identities: 7,746 passed, 91 skipped and the same 16 baseline failures/errors remained. All 453 focused checks passed. No baseline identity disappeared or gained a failure/skip. The [full checkpoint](checkpoints/nr-consolidation-preparation-full-2026-10-30.json) records the reviewed sub-tolerance differences in four existing assertions. Unrelated plugin edits after compilation are outside that tested snapshot; core NR source remained unchanged.

Evidence: `ipss.test.plugin.core/target/nr-consolidation-preparation-minimal-retry-20261030/` contains the two case directories, complete raw timings/voltages, mode reports, source copies, environment/runtime fingerprints, `runs.json`, `preflight.json` and `result-audit.json`. The bounded log uses the same prefix with `.log`. The [machine-readable checkpoint](checkpoints/nr-consolidation-minimal-warmup-2026-10-30.json) retains counts and validation results.

The implementation goal remains open: contributor/standalone-API migration, coverage closure and matched warmed/frozen-baseline performance qualification are still required. This run does not meet the minimal-warmup target.
