# Shared NR equation contributors

Core commit `a871c59b7` composes zero-impedance branch equations and coordinated
controls around the existing common electrical formulations. Ordinary fresh and
retained NR still use the same polar and rectangular kernels. The contributor
pipeline changes equation ownership and lifecycle; it does not introduce another
Newton iteration engine.

`NrEquationContributor` declares auxiliary blocks, an exact structural key,
Jacobian and residual writers, corrections, accepted-state commits and reversible
trial state. Mandatory electrical auxiliaries precede optional controls.
`NrEquationAssembly` owns their offsets, structural receipts and ordered stamps.
Keys compare actual immutable descriptors rather than hashes alone. Unchanged
network validation scans retained primitive/reference snapshots without
allocating another snapshot proportional to network size.

`ZbrNrSolver` and `CoordinatedControlZbrNrSolver` retain their public facades and
protected compatibility hooks. Existing ZBR, remote reactive control, full-Newton
tap and reduced converter equations keep their numerical rules. Potential
converter slots remain present when their numerical derivative is zero, allowing
later nonzero values without an incompatible symbolic pattern.

Every owned network-response matrix retains mandatory electrical auxiliaries
and excludes optional controls. This role selection follows that particular
matrix through validation, updates, RHS publication, corrections and trials,
including after newer full or response matrices have been created. Weak identity
keys avoid retaining abandoned matrices. Response creation does not alter the
full matrix's layout receipt or evaluate optional control layouts.

Joint trials use the common voltage/load snapshot and contributor-owned state.
Cleanup restores every captured owner even if another cleanup throws, preserves
the primary failure, and is idempotent. Coordinated controls still explicitly
reject unsupported rectangular execution; no coordinate conversion is hidden.

## Verification

The final source-reactor run, `nr-contributors-final-full-20261031`, executes
**7,906 identities: 7,799 pass, 91 skip, 11 fail and five error**. All **493
required focused identities pass**. Compared with the preceding lifecycle
checkpoint, there are 28 additions and no missing identity, new failure, new
skip, changed failure diagnostic or fork dump. The same 16 existing suite
failures/errors remain; this is not a wholly green full suite. All 5,162 Java
source files match the frozen compiled snapshot, including pre-existing
unrelated working-tree changes.

The 28 new cases cover ordered assembly in both coordinates and both sparse
backends, exact keys despite hash collisions, ten structural mutations,
zero-to-nonzero KLU symbolic reuse with an independent linear residual check,
multiple response ownership, allocation scaling, capture/cleanup failures and
same-solver recovery. Integration cases exercise actual control-row replacement
and a combined controlled network with a ZBR leaf, including reversible flow
compensation. The focused gate also executes 56 existing coordinated-control
tests and the existing ZBR, selection and lifecycle cases.

## Remaining work

The new layout guard is structural. Separate electrical-coefficient revisions,
contributor capabilities and full cache admission in MODE-06 remain required.
Specialized solvers retain their existing fresh execution restrictions. Full
custom-hook/handoff closure, migration of the independent historical standalone
numerical API and all required interaction sequences remain open. This checkpoint
does not establish the final <=1.2 runtime-ratio target against RustPower.

Local evidence is under
`ipss.test.plugin.core/target/nr-consolidation-contributors-20261030/`.
`full-final-audit.json` retains every identity, comparison and report hash.
Source manifests, working patches and new source copies use the
`nr-contributors-final-full` prefix under
`ipss.test.plugin.core/target/nr-consolidation-20261030/`. Bounded build logs,
earlier gates and failed compilation attempts are preserved separately.

See the [machine-readable checkpoint](checkpoints/nr-consolidation-contributors-2026-10-31.json),
[coverage manifest](../nr-consolidation-test-coverage.json) and
[remaining plan](../newton-raphson-consolidation-plan.md).

## Warmed performance recheck

The regression-tested maintained cached path and pinned RustPower 0.5.2 KLU
runtime were measured on all five cases. These are local matched timings;
published absolute times from another machine are not used as the denominator.
The criterion is the pooled arithmetic mean InterPSS / RustPower ratio <=1.2
for every case. All samples are retained, with no outlier removal.

| Case | RustPower mean ms | Cached NR mean ms | Ratio | <=1.2 |
| --- | ---: | ---: | ---: | --- |
| IEEE39 | 0.012455 | 0.015839 | 1.272 | Fail |
| IEEE118 | 0.029030 | 0.041378 | 1.425 | Fail |
| PEGASE9241 | 12.871481 | 16.579384 | 1.288 | Fail |
| 25k | 41.246868 | 55.094422 | 1.336 | Fail |
| 70k | 218.960811 | 249.367158 | 1.139 | Pass |

There are three fresh processes per runtime in each of two separate cohorts,
alternating runtime order. IEEE39 and IEEE118 have 50,000 warmups and 10,000
measurements per process; PEGASE9241, 25k and 70k have 50 total warmups and 100
measurements. Initialization is included in Java's warmup count. Each process
runs its cohort's cases in the listed order. This is warmed evidence, not a
fresh-process one-warmup result for each case. There are 121,800 measured solves.

Both runtimes use verified CPU 2, affinity mask 4. Java uses Temurin 21.0.6,
`-Xms256m -Xmx2g` and Java KLU. Both use 1e-6 pu tolerance and a 20-iteration
limit, with controls, globalization, update limits and low-voltage adjustment
disabled. Starts are compensated flat except the qualified 70k saved-state
restart. InterPSS timing includes voltage reset, public load flow, voltage
publication and generation allocation; RustPower includes reset, solve and
calculated-power retrieval. Import, driver compilation, validation and hashing
are outside timing; residual JIT work remains included. No builds or intrusive
profiling overlap the timing batch.

All independent original-Ybus residual and all-bus voltage checks pass. All
15 case/process receipts report explicit CACHED execution, one preparation,
positive retained residual/workspace use and compatible symbolic reuse. Every
repeat index and positive iteration count is audited. Source/runtime and
evidence fingerprints match after both cohorts; the RustPower package files
also match the pinned historical package. Numerical accuracy and execution
coverage do not turn an unmet runtime criterion into a pass.

The 25k historical 48.26 ms result used 51 total warmups. Its 88.64 ms result
and the regressed 201.47 ms result both used one. The prior matched diagnosis
established the repeated-admission regression and partial correction. These
new warmed observations must not be used to claim recovery of that remaining
startup regression or attribute differences between unmatched timing batches.
See the [historical diagnosis](nr-25k-regression-diagnosis-2026-10-30.md).

The checkpoint retains pooled/per-process distributions, all execution and
accuracy receipts, and source/runtime/input hashes. Raw evidence is under
`ipss.test.plugin.core/target/nr-contributors-rustpower-small-20261031/` and
`ipss.test.plugin.core/target/nr-contributors-rustpower-large-20261031/`.
`performance-audit.json` in the contributor evidence directory verifies both
cohorts. Child exit code 2 records a completed comparison with an unmet runtime
target, not a numerical failure. Final architecture, generic/frozen comparisons
and complete release qualification remain open.
