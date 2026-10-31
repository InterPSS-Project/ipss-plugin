# 25k NR regression diagnosis and partial recovery

The 25k slowdown is a real regression in the consolidated core. The historical
88.64 ms and 201.47 ms measurements both used exactly one initialization warmup
and one measured solve. Different warmup counts explain why the older warmed
result was faster, but do not explain the 88.64-to-201.47 ms increase.

## Historical results

| Core implementation | Total warmups per JVM | Measurements | InterPSS 25k ms | RustPower 25k ms |
| --- | ---: | --- | ---: | ---: |
| Before consolidation, `5309d6486` | 51: initialization plus 50 loop warmups | 3 JVMs, 100 solves each; mean | 48.262114 | 44.467043 |
| Before consolidation, `5309d6486` | 1 | 1 solve | 88.6434 | 43.2994 |
| Consolidated, `6e7e66180` | 1 | 1 solve | 201.4729 | 47.1063 |

The last two InterPSS observations differ by 2.27 times. The input, normalized
model, Ybus, independent voltage reference and bus mapping hashes match. Both
use Temurin 21.0.6, Java KLU, CPU 2 with affinity mask 4, `-Xms256m -Xmx2g`,
the same dependency JARs and six Newton iterations. Both retain one preparation
and compatible symbolic factors, with 50,000 scalar equations, 335,468
structural entries and 773,350 factorized entries. Neither silently falls back
to generic assembly. See the [earlier minimal run](rustpower-large-single-warmup-2026-10-30.md)
and [consolidated minimal run](nr-consolidation-minimal-warmup-2026-10-30.md).

## Findings

| Finding | Evidence | Implication |
| --- | --- | --- |
| The core change reproduces the slowdown. | Crossing both archived benchmark drivers with both immutable runtimes gives old-core samples of 89.15–124.23 ms and consolidated-core samples of 181.96–192.35 ms. Each fresh JVM has one warmup and one measured solve. | Driver/reporting changes do not explain the regression. |
| A repeated capability audit adds avoidable work. | Every unchanged solve calls `NrModelInputs.unsupportedReason()`, builds a bus-membership `IdentityHashMap` and traverses model/load types despite an existing validated plan. Main-thread allocation rises from 19,088 to 1,868,216 bytes per solve. | Plan admission repeats work that authoritative retained-plan validation can already cover. |
| Compilation magnifies the first measured solve. | Separate profiled runs report 12 ms versus 94 ms of JIT compilation-counter increase during the measured call. Compilation of `reusableElectricalInputLimitation` alone overlaps that call for approximately 42 ms. Both runs report zero collections. | Compiler work competes with the solve on the pinned CPU. This is not a measured garbage-collection pause. Compilation-event overlap is supporting evidence, not an exact additive CPU attribution. |
| The regression also exists after warmup. | A separate 50-total-warmup, 10-measurement diagnostic batch averages 47.65 ms before consolidation and 58.18 ms afterward, approximately 22% slower. The allocation increase persists. | Startup alone cannot explain the change. |

## Correction and matched follow-up

Core commit `2e83b268f` reuses the retained plan's authoritative validation at
solve admission. A structural change, absent/incompatible plan or open manual
solve scope still requires the appropriate full admission checks. Numerical
changes still refresh coefficients and invalidate factors. Generic execution
does not perform a capability audit whose result it never uses. The validated
boundary refresh is passed into scope entry rather than immediately repeated.
Equations, convergence settings and numerical factor-reuse policy are unchanged.

The follow-up below uses the same compiled benchmark driver and input with
three immutable source runtimes. Cold runs use the balanced order old,
regressed, fixed, fixed, regressed, old. Each is a fresh JVM with exactly one
warmup and one measured solve. The separate warmed diagnostic runs use 50
total warmups and ten measured solves per runtime. These are separate protocols
and must not be compared as if they had the same warmup count.

| Runtime | One-warmup samples, ms | Warmed mean, ms | Warmed range, ms | Main-thread allocation per measured solve |
| --- | --- | ---: | --- | ---: |
| Before consolidation, `5309d6486` | 86.2432, 90.0294 | 58.88055 | 51.3154–73.9371 | 19,088 bytes |
| Regressed, `6e7e66180` | 220.2366, 183.0308 | 71.98364 | 67.5253–77.5803 | 1,868,216 bytes |
| Corrected, `2e83b268f` | 138.4255, 140.9354 | 60.71914 | 57.1878–67.9152 | 19,624 bytes |

The correction removes approximately 99% of the measured allocation and
substantially reduces the one-warmup penalty. It does not restore the old
one-warmup performance. The corrected warmed mean is approximately 3.1% above
the old mean in this batch, with overlapping ranges; this small sample does not
establish a persistent 3.1% regression. Absolute timings drift between batches,
so the earlier 47.65 ms observation must not be mixed with this follow-up table.

A separate corrected JFR run reports a 49 ms compilation-counter increase,
zero collections and 19,624 allocated bytes in the measured call. Remaining
compilation includes preparation validity, polar assembly, voltage checks and
load-derivative methods. Further startup work remains. Profiled observations
are kept separate from the timing comparison; JFR adds instrumentation and
produces operating-system counter warnings on this workstation.

Every follow-up solve converges in six iterations. Independent Ybus evaluation
and all-bus voltage comparison pass, as do explicit preparation/workspace and
retained-symbolic checks. No RustPower or 70k rerun is included in this diagnosis;
it makes no new RustPower ratio or <=1.2 performance claim.

## Verification and retained evidence

The focused correction gate passes all **456 tests**, with no failures, errors
or skips. New cases cover unsupported model replacement after a retained solve,
including notification-suppressed mutation in both automatic and strict cached
modes, and mutation while a manual solve scope remains open.

The full source-reactor run, `nr-admission-full-20261030`, executes **7,868
identities: 7,761 pass, 91 skip, 11 fail and five error**. All 456 required
focused identities pass. No new failure, new skip, changed baseline failure
diagnostic or fork dump occurs relative to the prior 7,853-identity full run.
The 16 existing failures/errors remain; Maven's failure-ignore exit status is
not treated as evidence that every test passes.

The raw identity comparison contains 16 additions and one missing name. Three
additions are the new NR admission tests. Concurrent controller qualification
replaces the former single `Pss2bReferenceReplayTest` method with three passing
archive-backed replay tests. Source review confirms that the replacement helper
still traverses every supplied row and checks accepted voltage memory and
derivatives, with added step-size, state/output and multi-section checks. The
audit retains the missing name, source hashes and replacement identities; it
records reviewed coverage replacement rather than silently treating names or
fixture bytes as identical.

The source manifest identifies the exact compiled snapshot, including existing
unrelated working-tree edits. One unrelated `Hyg3PreparedCurveTest.java` changed
after capture; that later edit is outside this run. Core NR production source
remains identical to the tested snapshot. The raw and reviewed audits are
retained as `full-test-audit.raw.json` and `full-test-audit.json`.

Local evidence is retained under
`ipss.test.plugin.core/target/nr-25k-regression-20261030/`. `evidence/runs.json`
records driver/runtime crossover commands, fingerprints, affinity observations,
counts and accuracy. `after-fix/runs.json` records all follow-up runs. The JSON
diagnostics retain every warmup and measured sample, CPU/compilation/GC counters,
allocation and loaded-class counts. Final all-bus voltage files, JFR recordings,
compilation summaries, executed source copies and failed attempts are preserved.
`NrSolveDiagnostic.java` is the separate diagnostic driver; it deliberately
does not enable the algorithm profiler, which changes voltage-workspace behavior.

The [machine-readable checkpoint](checkpoints/nr-25k-regression-diagnosis-2026-10-30.json)
records sample arrays, evidence hashes and the regression gate. Broader
consolidation and final performance qualification remain open.
