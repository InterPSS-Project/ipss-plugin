# NR completion ownership and matched measurements

Core commit `eb713f1ed` lets the maintained solver publish voltages and allocate accepted generation
while its audited unchanged-input scope remains open. It then closes the scope
on every exit. This removes a repeated full model/voltage audit at completion;
admission and mutations outside the scope retain their existing validation.
The common polar/rectangular equations and numerical Jacobian/LU policy are
unchanged.

`INrSolver.finishNrExecution()` owns the common completion boundary. Its default
implementation preserves legacy end-hook, result-publication and allocation
order. The ordinary default solver can consume its accepted-power receipt
before releasing ownership, after publishing all voltages. The engine's
observation callback runs after voltage publication and before allocation, so
allocation callbacks still see this solve's cached-use observation. The engine completion operation resolves the current allocation owner and
tolerance after legacy end hooks. It may update accepted results, while
electrical inputs and voltages remain unchanged. The engine retains completion callbacks per algorithm
instance rather than allocating one on each solve.

The optimized completion requires the existing retained-voltage scope and
known generator implementations without observers. Unknown or observed generator
callbacks keep conservative completion. A consumed receipt is not reissued when
the scope closes. Manual compatibility allocation after scope exit still checks
current model coefficients, voltages and injections. No broader cache capability
or new Newton formulation is introduced.

## Profile evidence

Separate warmed JFR runs of the frozen contributor runtime use the public load-flow API with the algorithm
profiler disabled, preserving its retained-voltage behavior. Recording begins
after warmup. Accepted-power validation at completion appears in these sampled
main-thread stacks:

| Case | Accepted-current samples | Total main-thread samples |
| --- | ---: | ---: |
| IEEE118 | 25 | 284 |
| PEGASE9241 | 75 | 542 |
| 25k | 77 | 823 |

The full validation method also appears at solve admission. These are sampled
stack counts, not exact phase timers or additive CPU attributions. The complete
recordings, compiled diagnostic driver, sample timings, counters, method/stack
aggregates and independent accuracy checks are retained under
`ipss.test.plugin.core/target/nr-warmed-profile-20261031/`. JFR reports unavailable
operating-system counter warnings on this workstation; the retained evidence
does not depend on those counters. Profile timing is not qualification timing.

## Matched warmed observations

The frozen contributor runtime and the completion runtime execute the same
compiled benchmark driver with matching settings. Each cohort uses fresh JVMs
in before, after, after, before order, on verified logical CPU 2 with affinity
mask 4 and `-Xms256m -Xmx2g`. The small cohort executes IEEE118 followed by
PEGASE9241; the large cohort executes 25k. IEEE118 has 50,000 total warmups and
10,000 measurements per JVM; the other cases have 50 total warmups and 100
measurements. Initialization is included in Java's warmup count.

| Case | Before mean ms | After mean ms | Mean time reduction |
| --- | ---: | ---: | ---: |
| IEEE118 | 0.043767 | 0.039720 | 9.25% |
| PEGASE9241 | 16.630539 | 14.648169 | 11.92% |
| 25k | 55.448177 | 50.417988 | 9.07% |

These limited observations contain 40,800 measured solves. All samples and
per-process distributions are retained, including tails; none are removed. The IEEE118 before cohort includes a
14.7118 ms tail observation; its median changes from 0.0409 to 0.0379 ms
(7.33%), compared with the 9.25% mean reduction.
Import, driver compilation, accuracy checks and hashing are outside the timers.
Residual runtime compilation remains included. No build or intrusive profiling
overlaps timing. The timer includes voltage reset, public load flow, voltage
publication and generation allocation, matching the maintained benchmark.

Both runtimes start from the same compensated flat state with 1e-6 pu tolerance
and a 20-iteration limit. Controls, globalization, update limits and low-voltage
load adjustment are disabled. Every solve converges in four iterations for
IEEE118 and six for the other cases. Every after-runtime call reports owned
completion, one retained preparation and compatible symbolic reuse. All final
voltage files are byte-identical across runtimes for each case. Independent
original-Ybus residual and complex-voltage checks pass. Complete runtime and
driver fingerprints match before and after the batch.

## One-warmup and targeted external checks

The separate one-warmup 25k comparison runs before, after, after, before in
fresh JVMs, with exactly one initialization warmup and one measured solve each.
Before samples are **141.2544 and 139.5063 ms**; after samples are **110.3744 and
111.3014 ms**. Inputs, settings, driver, CPU, six Newton iterations and retained
symbolic use match. All final voltage files are byte-identical and independent
accuracy checks pass. These two observations per runtime show partial startup
recovery, not restoration of the historical 86–90 ms pre-consolidation samples.
The warmed and one-warmup cohorts remain separate.

A targeted IEEE118 check measures the full-regression-tested completion runtime
directly against pinned RustPower 0.5.2 KLU. There are three fresh processes per
runtime, alternating order, with 50,000 warmups and 10,000 measurements each on
verified CPU 2. InterPSS averages **0.04043174 ms**, RustPower **0.02851170 ms**,
giving **1.41808**. The three process ratios are 1.42855, 1.40939 and 1.41640;
all miss <=1.2. All 60,000 measured samples remain. Actual owned completion,
retained preparation/workspace/symbolic use and independent accuracy checks pass.
This cohort runs IEEE118 alone rather than after IEEE39, so it must not be used
to attribute a change from a different historical timing cohort. It establishes
that the required overall target is still unmet. No unchanged batch is repeated
to seek a passing result.

## Regression gate

The final full source reactor executes **7,921 identities: 7,814 pass, 91 skip,
11 fail and five error**. All **508 required focused identities pass**. Compared
with the contributor checkpoint, there are 15 additions and no missing identity,
new failure, new skip, changed baseline failure diagnostic or fork dump. The
same 16 existing suite failures/errors remain. All 5,163 Java sources match
the frozen snapshot, including recorded pre-existing unrelated working edits.
All 108 production classpath entries in the full run are byte-identical to
the runtime used for the matched before/after observations.

Thirteen new completion cases cover both coordinates/all three modes, repeated
load changes, generation parity, consumed receipts, unknown/observed generator
callbacks, legacy end-hook injection mutation, allocation-owner/tolerance replacement
and result-observation order.
They also cover successful publication and publication exceptions/errors with
same-instance recovery. Two additional lifecycle cases fail after scope exit
or allocation and recover through an actual Newton correction. Earlier focused
gates and fixture-only compilation/recovery-test errors remain in the evidence.

Final all-five-case qualification, complete startup recovery, standalone
numerical API migration, separate contributor coefficient/capability contracts
and complete release coverage remain required.

Local evidence is under
`ipss.test.plugin.core/target/nr-owned-completion-20261031/`:
`matched-final/runs.json` retains commands, fingerprints and receipts;
`matched-final-summary.json` retains complete pooled distributions and voltage
hashes; `cold-final/runs.json` keeps the separate one-warmup cohort.
`full-final-audit.json` records the final full regression. Each process folder
retains raw samples and complete final voltages. The targeted external evidence
is under `ipss.test.plugin.core/target/nr-owned-completion-final-rustpower118-20261031/`.
The
[machine-readable checkpoint](checkpoints/nr-owned-completion-2026-10-31.json)
records the gate and evidence hashes. See the
[consolidation plan](../newton-raphson-consolidation-plan.md) for remaining work.
