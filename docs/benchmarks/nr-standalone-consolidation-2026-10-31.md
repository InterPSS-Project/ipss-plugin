# Standalone NR consolidation and regression checkpoint

Core commit `1942c231edb0bcce00a00e35cd2dedc38e7c1b79` converts `PreparedNewtonSolver` into a compatibility
facade around the common Newton engine and polar equation implementation.
The correctness regression gate passes relative to the existing baseline.
Performance remains an open requirement; this checkpoint does not close the
consolidation plan or claim recovery of the historical one-warmup result.

## Common implementation and compatibility

`FixedOperatorNrSolver` provides owned fixed CSC inputs and equation masks to
`NrAssemblyPlan`. It uses `NrPowerEvaluation`, `NrBusEquations` and
`PolarNrFormulation`, then runs the maintained `LfCalcMethodRunner`,
`LfCalcIterOrchestrator` and `LfCalcNrEngine`. The production facade and adapter
contain no independent Newton loop or second electrical derivative assembler.
The former implementation is retained solely as a frozen test oracle.

The operator is preserved exactly, including nonsymmetric entries, structural
zeros and row summation order with a diagonal in the middle. It is not converted
into physical branches. Private indexing buses supply equation constraints;
they do not represent an imported controllable device model. The public
`equationCount()` retains its reduced physical degree count. The common backend
internally includes fixed-variable identity rows in a 2N layout.

The shared polar implementation supports the historical standalone absolute
magnitude convention and ordinary relative magnitude convention. Absolute
off-diagonal column normalization occurs outside the common edge kernel.
The ordinary voltage traversal uses the common Cartesian conversion in place,
without an extra array pass. A per-instance `NrIterationPolicy` preserves the
standalone contraction/reuse settings, strict tolerance boundary, correction
budget and finite-scale policy. The maintained workflow retains its defaults
and live automatic-adjustment setting.

The compatibility API still accepts fresh voltages, specified injections and
PV/reference targets per call. It preserves signed-zero reference angles and
the historical unwrapped angle after negative-magnitude canonicalization.
Its owned Java KLU context ignores global creators/order/reuse settings without
modifying them. Actual backend analysis/factor/refactor/fallback counts supply
the existing getters. Optional linear residual verification remains sequential;
a fresh-Jacobian certificate failure can retry pivoting, while retained-factor
certificate failure follows the historical failure policy.

## Verification

The final source-reactor run `nr-standalone-final-full-20261031` executes
**7,941 identities: 7,834 pass, 91 skip, 11 fail and five error**. All **528
required checks pass**. Against the preceding 7,921-identity checkpoint there
are 20 additions and no missing test, new failure, new skip, changed failure
diagnostic, fork dump or Java source drift. The same 16 existing failures/errors
remain. All 108 timed production classpath entries are byte-identical to
the final full-tested runtime.

The 20 new cases cover nonsymmetric and multi-reference systems, PV-only
systems, independent finite differences/current balance, repeated starts and
targets, reuse/certificate options, zero correction budget, exact tolerance,
large finite scales, row cancellation, reference-power finiteness, signed zero,
unwrapped angles, defensive copies, invalid inputs and global-setting isolation.
A 1,001-bus test compares the former fused implementation with the common
kernel. Sparse tests exercise actual certificate retry, retained-factor failure,
work counters and sequential/context restrictions. The four original standalone
tests remain and pass. The broader focused run has 581 passes and four unchanged
optional-backend skips; all nine benchmark-tool tests pass.

## Matched maintained-workflow observations

These measurements invoke public `loadflow(false)`, including voltage reset,
publication and generation allocation. They compare the frozen completion
runtime with this migration using the same compiled driver, inputs and settings.
Fresh JVMs run in before/after/after/before order on logical CPU 2, mask 4,
with `-Xms256m -Xmx2g`. IEEE118 uses 50,000 total warmups and 10,000 measurements
per JVM; the other cases use 50 total warmups and 100 measurements.

| Case | Before mean ms | After mean ms | Time change |
| --- | ---: | ---: | ---: |
| case118 | 0.04053262 | 0.04142093 | +2.19% |
| case9241pegase | 14.88515300 | 15.60380000 | +4.83% |
| activsg25k | 51.75682400 | 52.44140250 | +1.32% |

All 40,800 samples remain, including tails. Every final voltage file is
byte-identical across the paired runtimes. Independent original-Ybus residual
and complex-voltage checks pass; every call exercises actual owned completion,
one retained plan and compatible symbolic reuse. No build or profiling overlaps
timing. Import, compilation of the driver, validation and hashing are outside
the timer; residual runtime compilation remains included.

The earlier migration prototype is also retained: means increased 3.95% for
IEEE118 and 5.82% for PEGASE9241, while 25k decreased 0.48%. Diagnostic JFR runs
were separate from timing. Source review motivated removal of the extra voltage
traversal, ordinary edge normalization and reuse-policy object growth. The
refined table still shows overhead, most clearly on PEGASE9241. These changes
do not establish performance preservation or attribute a precise percentage to
any one method. Raw recordings and their OS-counter warnings remain available.

The separate one-warmup 25k samples are **[114.933, 116.2366] ms before**
and **[116.0818, 109.1147] ms after**, with one measured solve per fresh JVM.
All four final voltages are byte-identical and accuracy checks pass. These
observations do not restore the historical 86-90 ms result and are separate
from the warmed table.

## Current RustPower comparison

These two separate cohorts measure pinned RustPower KLU against the full-tested
maintained cached workflow, across all five cases. Small and large cases retain
the harness-required separate cohorts. Three fresh processes per runtime in each
cohort alternate order on CPU 2. Each small case has 50,000 total warmups and
10,000 measurements; each large case has 50 total warmups and 100 measurements.
Java initialization is included in the warmup count. The 25k compensated-flat
and 70k saved-state starts, tolerance 1e-6, 20-iteration limit, fixed-control
models and timed boundaries match the retained benchmark protocol.

| Case | RustPower mean ms | InterPSS cached mean ms | Ratio | <=1.2 |
| --- | ---: | ---: | ---: | --- |
| case39 | 0.01273880 | 0.01534499 | 1.205 | Fail |
| case118 | 0.02846419 | 0.04042566 | 1.420 | Fail |
| case9241pegase | 12.65277267 | 14.54659233 | 1.150 | Pass |
| activsg25k | 40.65786367 | 50.90125567 | 1.252 | Fail |
| activsg70k | 217.90609333 | 236.09773833 | 1.083 | Pass |

The performance criterion remains open unless every case meets <=1.2; a passing
subset does not qualify the overall goal. No historical RustPower time is
divided into a new InterPSS time. Source snapshots include the new adapter,
iteration policy and common method runner. Actual mode, preparation/workspace,
owned completion, symbolic use, independent accuracy, all repeat identities,
runtime/file fingerprints and affinity receipts are retained. Target failure
is recorded separately from solver/accuracy failure. All 121,800 measured
sample identities pass the count audit; no samples or outliers are removed.

## Evidence and remaining work

Local evidence is in `ipss.test.plugin.core/target/nr-standalone-consolidation-20261031/`:
`full-final-audit.json`, `focused-hotloop-audit.json`, `source-consolidation-audit.json`,
`matched-hotloop/`, `cold-hotloop/`, `full-versus-measured-runtime.json`, and the
earlier `matched/` and `profiles/` directories. Source manifests, working patches
and bounded logs use each unique run name. External evidence is in
`ipss.test.plugin.core/target/nr-standalone-rustpower-final-20261031/`.
The [machine-readable checkpoint](checkpoints/nr-standalone-consolidation-2026-10-31.json)
retains the exact summaries, comparisons and new test identities.

Specialized coefficient/capability contracts, extension handoff coverage, the
complete plan coverage matrix and final generic/frozen comparisons remain.
Further performance work must remove the observed overhead and meet <=1.2 on
every external case before the goal can be completed.
