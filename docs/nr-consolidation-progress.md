# Newton consolidation implementation evidence

The [consolidation plan](newton-raphson-consolidation-plan.md) is being implemented. Selection and reporting are verified. Shared equation extraction, complete cross-mode coverage, final regression comparison and performance qualification remain required before completion.

## Frozen baseline

Core source starts at `5309d648648378f7bec22226fe5ef6c8d80c52be`; plugin starts at `65017e90f7c6446cb028faf038cef8e898be9462`. Existing core network-copy/reset work remains outside this refactor. Working patches, untracked dependencies, 1,844 production source hashes and three executable class/resource directories are preserved under `ipss.test.plugin.core/target/nr-consolidation-20261030/`. The manifest retains the exact dependency hashes and original source/runtime associations.

The full isolated source-reactor baseline used extended tests, all test-class names, bounded heaps/logs and suite-wrapper-engine exclusion. All four test modules produced reports, and production/sample modules compiled. The run executed 7,683 test identities: 7,576 passed, 91 skipped, 11 failed and five errored. No fork dumps occurred. The full baseline audit preserves every identity and failure diagnostic; Maven's successful exit with failure-ignore enabled is not used as evidence that these tests passed.

Baseline failures cover a DCLF island assertion, three three-phase integration errors, two input/import errors, four dynamic assertions and six piecewise-network assertions. Their exact identities and exception types are in the [checkpoint](benchmarks/checkpoints/nr-consolidation-phase-1-2026-10-30.json), with complete diagnostics retained in the local audit. Final qualification must identify every newly failing, missing or unexpectedly skipped identity and review any changed baseline diagnostic.

## Call and model inventory

| Component | Existing ownership and consolidation requirement |
| --- | --- |
| `DefaultNrSolver.createJMatrix/updateJMatrix` | Dispatches between retained polar preparation and the public network assembler. Both must reach a common formulation. |
| `AclfNetJMatrixHelper` | Owns independent polar and rectangular formulas and full/reduced masks; also serves continuation power flow, direct callers and additional auxiliary blocks. Preserve those matrix APIs. |
| `PreparedPolarJacobian` | Combines snapshots, CSC destinations, two Jacobian implementations, two residual implementations, correction application and generation allocation. Move equations and state operations out of preparation. |
| `LfCalcMismatchHelper` / `LfCalcNrEngine` | Refresh external injections, carry a prepared sentinel or bus residual array, select rebuild/refactor/LU reuse and publish RHS. Replace the sentinel with a typed evaluated-state contract. |
| `LfCalcIterOrchestrator` | Owns convergence, control transitions, rollback and ordinary/stable iteration handling. Preserve the established numerical policy. |
| `LoadflowAlgorithmImpl` | Opens/closes prepared scopes and selects prepared allocation. Replace these with common preparation/workspace lifecycle operations. |
| `ZbrNrSolver` / `CoordinatedControlZbrNrSolver` | Add auxiliary variables, residuals, Jacobian entries, bound transitions and network-response views. Keep their facades and route electrical blocks through shared formulations. |
| Step optimizers and network-response consumers | Depend on correction signs, zero-scale identity, trial rollback and public voltage visibility. They must use shared correction/evaluation routines without losing callbacks. |
| `PreparedNewtonSolver` | Historical standalone numerical API still contains an independent polar implementation. Preserve its public contract through the consolidated implementation; it is not the maintained benchmark path. |

Retained preparation currently admits exact built-in static network/bus/branch/load types and uninitialized dynamic containers, polar coordinates, and Java KLU. Function/exponential/PV load providers, custom classes, child networks, initialized dynamics, impedance tables and augmented branches need fresh or specialized contracts. The selection API explicitly reports these limitations; later phases must integrate common equations and contributors rather than maintaining a second assembler for them.

Public physical Yii and dynamic Norton contributions retain their existing ownership. Fixed network coefficients and live switched-shunt/SVC susceptance must remain separate. Current custom Yii/power methods and load-provider precedence remain authoritative through adapters. Full rectangular PV row masks require independent derivative validation during extraction; comparing only against the old assembler cannot establish correctness.

The former `AclfNetJMatrixHelper` mathematical implementation is frozen as `FrozenNrJacobianAssembler` in core test sources, including source hash and provenance. Independent current-balance, finite-difference and mutation tests remain required alongside this oracle.

## Phase 1: selection and reporting

Core implementation commit: `400e588e0` (`Add per-algorithm NR execution selection and reporting`). This commit also preserves the frozen test assembler and leaves the unrelated core copy/reset work untouched.

`NrExecutionMode` is per algorithm: `AUTO` selects supported retention and reports a fresh-evaluation requirement; `CACHED` requires a reusable contract and reports affected model identifiers on rejection; `GENERIC` disables retained preparation. A requested mode is captured at solve entry, so initialization callbacks can select the following solve without changing the current one. Legacy boolean settings map to `AUTO`/`GENERIC`.

`NrExecutionReport` records the captured request, actual preparation strategy, coordinates, builds, coefficient refreshes, structural rebuilds, assembly requests, factorization requests, successful triangular solves and immutable diagnostics. Interface defaults retain legacy third-party solver execution and explicitly reject strict caching without a declared contract. Capability restrictions are separate from convergence, damping and numerical LU reuse.

The final phase-one source gate passed all 117 tests with no skips, failures or errors. Twelve `NrExecutionModeTest` cases cover all modes, per-instance isolation, next-boundary changes, repeated preparation, boolean compatibility, null selection, backend/custom-solver fallbacks, third-party interface defaults, muted unsupported-model mutations, late strict-cache rejection and recovery. Existing prepared Jacobian, shared live-device, continuous/fixed-generation controls, dynamic-container and plugin transfer/API tests also execute.

Evidence paths:

- `ipss.test.plugin.core/target/nr-consolidation-baseline-full-20261030.log` and `.status.json`.
- `ipss.test.plugin.core/target/nr-consolidation-20261030/baseline-test-audit.json`.
- `.verification-build/nr-consolidation-mode-final-20261030/` and the corresponding bounded log.
- `ipss.test.plugin.core/target/nr-consolidation-20261030/mode-final-audit.json`.

This phase initially maps modes onto the existing implementations. It does not yet satisfy the requirement that cached and generic modes share their mathematical kernels. Performance has not been requalified for the consolidation.

## Polar Jacobian extraction

Core commit `87eae80ff` replaces the independent ordinary/prepared and retained-workspace Jacobian formulas with `PolarNrFormulation`. `NrAssemblyPlan` supplies indexed electrical inputs and sparse destinations, and `NrNumericalWorkspace` supplies current numerical state. Fresh public matrix assembly and retained preparation call the same kernel. Physical-current arithmetic for these paths is shared through `NrPowerEvaluation`.

Fresh/custom matrices retain their row-based publication lifecycle; retained KLU preparation publishes directly into its owned CSC values. Full/reduced masks, parallel accumulation, relative-magnitude columns, current load derivatives, live shunt B and additional auxiliary dimensions remain supported. Jacobian-only evaluation does not authorize publishing an RHS that its caller has not explicitly evaluated.

The extraction gate passes 136 tests without skips, failures or errors. Eleven new `NrPolarFormulationTest` cases check full/reduced KLU/CSJ matrices against the frozen assembler, independently differentiate physical power balance with ZIP loads and live shunts, and exercise transformer asymmetry, inactive rows and additional contributor blocks. Existing mutation parity now compares against the frozen assembler so unification cannot turn it into a self-comparison.

Evidence is retained in `.verification-build/nr-consolidation-polar-jacobian-gate-20261030/`, its bounded log and `ipss.test.plugin.core/target/nr-consolidation-20261030/polar-jacobian-audit.json`. Executed source copies and hashes are preserved in the same evidence root. The first extraction attempt's four lifecycle failures and their corrected gate are both retained.

This closes only the polar Jacobian portion of phase 2. Residual formulas, typed evaluated-state publication, complete preparation ownership, common correction/allocation routines, rectangular/contributor extraction and retirement of the historical standalone numerical implementation remain open. A full post-consolidation regression comparison and matched RustPower performance qualification have not yet run.

## Shared residuals, corrections and allocation

The next checkpoint replaces the engine's prepared sentinel and ordinary residual array with `NrEvaluatedResidual`. Both strategies now publish a typed evaluated result; retained results validate ownership, evaluation revision and current inputs. Fresh standard model results use an exact getter-input snapshot. Custom models, specialized electrical tables and other models without a complete snapshot contract require fresh RHS callbacks. External-device preparation and auxiliary residual publication keep their established order.

`NrBusEquations` supplies ordinary mismatch arithmetic to public bus helpers and bulk polar evaluation. Polar correction application is shared by retained and published voltages and line-search trials. Rectangular corrections also have one implementation, preserving absolute-coordinate signs and limits. Trial rollback restores both coordinate views and derived load totals after rejection or exceptions. Correction maxima are measured before public update callbacks can change another bus; zero-scale trials preserve all voltage views.

Generation allocation now has one lifecycle boundary and shares total-generation rules with public model results. An accepted-power receipt is consumed only while its voltage, model and specified injections remain current; otherwise established model allocation runs. Existing per-generator distribution, fixed generation and compensation rules remain authoritative. Third-party optional hooks remain behind compatibility adapters. Sparse publication and corrections support block-only matrix implementations as well as primitive scalar access.

The final checkpoint executes **368 tests, all passing, with no skips or fork dumps**. Its 63 new cases cover three-mode residual mutations, opaque custom state, rectangular RHS ordering, correction limits/scales and observer visibility, trial rollback, stale accepted generation, allocation parity and block-only sparse APIs. Existing coordinated-control, zero-impedance, HVDC, switched-shunt, PQ-to-NR and optimizer tests execute alongside plugin power-flow/dynamic-transfer tests.

Evidence: `.verification-build/nr-consolidation-state-allocation-final-20261030/`, its bounded log, `ipss.test.plugin.core/target/nr-consolidation-20261030/state-allocation-audit.json`, and the [machine-readable checkpoint](benchmarks/checkpoints/nr-consolidation-state-allocation-2026-10-30.json). Executed source bytes and runtime hashes are retained. Earlier failed extraction attempts remain available.

Preparation ownership, rectangular Jacobian extraction, specialized contributor contracts, the historical standalone solver facade, complete coverage mapping, the full post-change regression and performance qualification remain open. The earlier one-warmup benchmark describes the frozen pre-consolidation runtime and does not qualify these changes.

## Rectangular Jacobian extraction

Core commit `31af0303f` moves rectangular assembly into `RectangularNrFormulation`. Both coordinates now use the indexed inputs, numerical workspace, physical-current evaluation and sparse publication adapters. `AclfNetJMatrixHelper` is a formulation facade. Rectangular rows remain `[Q, P]`, both voltage columns remain active for PV buses, reduced PV rows use the voltage-squared constraint, and corrections retain their negative sign.

The former full rectangular matrix suppressed off-diagonal PV reactive derivatives while keeping that row's physical diagonal derivatives. Full matrices now retain the complete physical reactive row. Independent current-balance finite differences verify the correction; the frozen assembler remains the oracle for preserved entries. PV active-load derivatives and switched-shunt/SVC chain rules are included.

The extraction gate passes **379 tests with no failures, errors, skips or fork dumps**, including 11 new rectangular cases covering full/reduced matrices, both sparse backends, voltage-dependent loads, independent derivatives, live-device updates, block-only matrix publication and additional auxiliary dimensions. Evidence is in `.verification-build/nr-consolidation-rectangular-first-20261030/`, its bounded log and the [checkpoint](benchmarks/checkpoints/nr-consolidation-rectangular-2026-10-30.json).

Reusable rectangular preparation remains explicitly unsupported at this checkpoint. Preparation ownership, contributor contracts, the standalone API migration and final full-regression/performance gates are still required.

## Common preparation and rectangular retention

Core commit `68ac08e09` moves snapshots and electrical coefficient refresh into a validity component owned by `NrAssemblyPlan`. Retained and fresh assembly use the same indexing/mask/destination builder. Numerical buffers belong to `NrNumericalWorkspace`, and `NrEvaluationContext` manages their solve state. `PreparedPolarJacobian` is now a deprecated polar compatibility facade with no equations or numerical ownership.

Ordinary rectangular NR can now retain Java-KLU preparation through the same context and kernels. Its public voltage views remain current; primitive voltage retention remains restricted to eligible polar workflows. Residual vectors preserve rectangular `[Q, P]` publication and the PV voltage constraint. Unsupported model/backend contracts still produce explicit fallback/rejection diagnostics.

The expanded gate executes **439 passing tests**, without skips or fork dumps. It includes 24 foundation assembly contracts: 20 positive numerical cases and four expected strict-CSJ rejections, spanning both coordinates, all modes and full/reduced forms. Full-matrix contracts exercise the common plan directly; the six additional repeated public-solve comparisons exercise the ordinary reduced Newton workflow and its actual mode/build/assembly/solve counters. Sixty cross-mode/coordinate residual-mutation cases check stale publication and fresh numerical parity. Independent derivative checks remain in the polar and rectangular formulation suites.

Evidence is in `.verification-build/nr-consolidation-plan-ownership-verified-20261030/`, its bounded log and the [preparation checkpoint](benchmarks/checkpoints/nr-consolidation-preparation-2026-10-30.json). Source snapshots are retained under `ipss.test.plugin.core/target/nr-consolidation-20261030/preparation-sources/`. The expanded test's initial API-name error and three ineffective rectangular test disturbances are preserved with their corrected gate; no production numerical fix was needed for those test setup errors.

The full combined reactor is being rerun for this checkpoint. Contributor contracts, historical standalone API migration, coverage closure and matched performance qualification remain outstanding; this passing focused gate does not complete the consolidation.

## Full-regression compatibility correction

The first full preparation run executes 7,842 identities: 7,734 pass, 91 skip, 11 fail and six error. It preserves all baseline identities but adds one new split/merge error: `IEEE14TestAclfSubNetBuild.splitMergeNetworkTest2`. The common fresh builder rejected an endpoint object outside the current bus list. The former public assembler read that endpoint's own voltage and masks while using its matrix column, which this legacy workflow still requires.

Core commit `6e7e66180` restores that fresh adapter contract. Retained preparation still requires endpoint ownership, and evaluated RHS results with external terminals require fresh callbacks rather than reusing a snapshot that does not own those voltages. The corrected focused gate passes **453 tests**, including ten new cross-coordinate/backend/full-form endpoint cases and all four split/merge plugin tests. Evidence and hashes are in the [endpoint checkpoint](benchmarks/checkpoints/nr-consolidation-external-terminal-2026-10-30.json) and the retained `external-terminal-audit.json`.

Four existing piecewise assertions have the same exception type and assertion location but change a compared number by at most approximately `1.3e-13`, below their `1e-10` assertion tolerance. Their substantial pre-existing discrepancies remain; the audit retains the original and changed diagnostics. The first full result is not accepted as a passing regression gate. A fresh full run on the corrected snapshot is required before measuring it.

The corrected full run, `nr-consolidation-preparation-full-fixed-20261030`, executes **7,853 identities: 7,746 pass, 91 skip, 11 fail and five error**. All 453 required focused identities pass. No baseline identity disappears, gains a failure/error or becomes newly skipped, and no fork dumps occur. The four changed baseline diagnostics are reviewed as sub-tolerance numerical variation at unchanged assertion sites. The [full checkpoint](benchmarks/checkpoints/nr-consolidation-preparation-full-2026-10-30.json) and retained audit record the raw comparison and that review. Concurrent unrelated plugin edits after source capture are listed explicitly; this gate covers the compiled snapshot, and its core NR source remains current.

The following [minimal-warmup benchmark](benchmarks/nr-consolidation-minimal-warmup-2026-10-30.md) verifies explicit cached/generic execution with one warmup and one measured solve per case/runtime. Cached NR / RustPower ratios are **4.277 for 25k and 1.668 for 70k**; neither meets the <=1.2 target. All accuracy, count, source/runtime fingerprint and affinity checks pass. These single observations do not qualify warmed performance or attribute the 25k slowdown. Earlier pre-consolidation results remain separate. The benchmark driver now captures every new numerical component and mode report, while preserving legacy-runtime comparison support.

## 25k performance regression and admission correction

The [matched diagnosis](benchmarks/nr-25k-regression-diagnosis-2026-10-30.md)
confirms that the 88.64-to-201.47 ms historical increase is a core regression:
both observations use one warmup, and both old/new benchmark drivers reproduce
the core-version difference. The earlier 48.26 ms result uses 51 total warmups
and is a separate protocol.

Consolidation repeats a complete capability and endpoint-membership audit at
every solve admission, even when an existing plan's authoritative snapshots can
validate the model. This adds about 1.85 MB of allocation per unchanged solve
and contributes substantial compilation work during the first measured solve.
A separate warmed diagnostic also reproduces approximately 22% overhead.

Core commit `2e83b268f` reuses the retained plan's boundary validation result.
Cold/structurally changed inputs and open manual scopes keep full admission
checks; unsupported notification-suppressed model replacements still trigger
automatic fallback or strict-cache rejection. Generic execution skips its
unused capability audit. Equations and numerical policy are unchanged.

All **456 focused tests pass**, including three new admission-mutation cases.
Matched one-warmup follow-up samples are 86–90 ms for the old core, 183–220 ms
for the regressed core and 138–141 ms for the correction. A separate warmed
batch averages 58.88, 71.98 and 60.72 ms respectively; old/corrected ranges
overlap. Allocation falls to 19,624 bytes. Startup regression remains, and no
new RustPower ratio is claimed. Full run `nr-admission-full-20261030` executes
7,868 identities: 7,761 pass, 91 skip and the same 16 failures/errors remain.
All 456 required checks pass, with no new failure/skip or changed baseline
failure diagnostic. One historical replay-test name is explicitly reviewed as
replaced by three passing controller-qualification tests; the raw missing name
and source comparison remain in the audit. A later unrelated governor test edit
is outside the compiled snapshot. Final consolidation qualification remains open.

## Common solver-owned lifecycle

Core commit `bc81289c5` removes concrete default-solver checks from ordinary NR
engine and algorithm lifecycle handling. `INrSolver` supplies stable-input,
retained-voltage, preparation-build and cached-use observations. Existing
virtual observation overrides and public prepared scope methods retain their
compatibility behavior. Legacy optional residual/correction/allocation hooks
remain inside their compatibility adapter; formulation equations and numerical
policy are unchanged.

All **465 required checks pass** in full run `nr-lifecycle-full-20261030`:
7,878 identities, 7,771 passed, 91 skipped and the same 16 failures/errors.
There are no missing identities, new failures/skips, changed failure diagnostics
or fork dumps, and no Java source changes after capture. Nine added lifecycle
cases cover a non-default bridge across both coordinates/all modes, old
observation overrides and exception cleanup/recovery.

The [checkpoint](benchmarks/nr-consolidation-lifecycle-2026-10-30.md) retains
matched observations against the preceding compiled runtime. Warmed medians are
54.36→54.59 ms for 25k and 248.54→242.54 ms for 70k. One-warmup samples and all
warmed samples are preserved separately; final voltage files are byte-identical
and independent residual checks pass. These limited observations make no final
RustPower qualification claim. CLEAN-03/04 are closed; contributor contracts,
standalone API migration, coverage closure and all-five-case performance
qualification remain open.

## Shared specialized equation contributors

Core commit `a871c59b7` introduces `NrEquationContributor` and
`NrEquationAssembly`. ZBR and coordinated controls now contribute equations
around the common electrical kernels, with explicit auxiliary offsets, exact
layout keys and ordered stamps. Each owned response matrix retains its own
electrical-only role selection through RHS, corrections and trial cleanup.
Potential zero converter derivatives retain structural slots. Rejected trials
restore voltages, loads, ZBR flows and control state even when cleanup fails.

All **493 required checks pass** in `nr-contributors-final-full-20261031`:
7,906 identities, 7,799 pass, 91 skip and the same 16 failures/errors. There are
28 new cases, no missing/newly failing/skipped identity, no changed baseline
failure diagnostic or fork dump, and no Java source drift after capture.
The [checkpoint](benchmarks/nr-consolidation-contributors-2026-10-31.md) closes
EXT-05/06/08/10. Separate coefficient/capability contracts, full custom/handoff
coverage, standalone migration and final matched performance remain open.

The same regression-tested snapshot completes a matched warmed RustPower
recheck on all five cases. Pooled cached/RustPower ratios are **1.272, 1.425,
1.288, 1.336 and 1.139** for IEEE39, IEEE118, PEGASE9241, 25k and 70k.
Only 70k meets <=1.2. The 25k means are 55.09 ms and 41.25 ms after 50 total
warmups; this is separate from the historical one-warmup regression. All
121,800 measured solves and per-process distributions are retained. Accuracy,
explicit cached execution, one preparation, workspace/symbolic reuse, affinity
and source/runtime integrity checks pass. No unchanged batch was repeated to
seek a passing ratio. The contributor checkpoint records the complete results;
generic/frozen comparisons and final qualification after migration remain open.

## Owned completion and remaining performance gap

The [completion checkpoint](benchmarks/nr-owned-completion-2026-10-31.md) adds
`INrSolver.finishNrExecution()`. The default contract retains legacy end,
result-publication and allocation order. An audited retained-voltage owner
publishes and consumes accepted powers before releasing unchanged inputs,
avoiding another full model/voltage audit. Unknown/observed generator callbacks
remain conservative, consumed receipts stay consumed, and every failure closes
the scope. Equations and Jacobian/LU policy are unchanged.

The full run executes **7,921 identities: 7,814 pass, 91 skip and the same 16
failures/errors**. All **508 required checks pass**, including 15 additions.
There are no missing/newly failing/skipped identities, changed failure
diagnostics, fork dumps or Java source drift. The full-tested and measured
production classpaths are byte-identical across all 108 entries.

Matched before/after warmed means decrease 9.25% for IEEE118, 11.92% for
PEGASE9241 and 9.07% for 25k, with complete samples retained and byte-identical
voltages. Separate one-warmup 25k samples decrease from 139.51–141.25 ms to
110.37–111.30 ms. Startup recovery remains incomplete. A direct three-process
IEEE118/RustPower recheck still misses <=1.2: **0.040432 / 0.028512 ms = 1.418**.
This targeted check is separate from all-five qualification and the matched
before/after cohort. Standalone migration, coefficient/capability contracts,
full release coverage and final performance remain open.

## Standalone numerical API consolidation

The [standalone checkpoint](benchmarks/nr-standalone-consolidation-2026-10-31.md)
converts the historical fixed-operator API into a compatibility facade around
the same Newton engine, power evaluator and polar formulation. Twenty new cases
pass in the 7,941-identity full run; all 528 required checks pass. Public logical
degree counts and options remain, while the backend uses fixed-variable identity
rows. The frozen historical implementation is test-only. Matched timing and
current external results are retained, with measured overhead and the overall
performance criterion still open. CLEAN-06/07/08 remain subject to their broader
capability and complete source/API coverage requirements.

The final full audit retains all previous identities and the same 16 existing
failures/errors, with zero changed diagnostics or source drift. The ordinary
solver defaults remain unchanged. The compatibility API retains its per-instance
numerical policy and checked linear-solve behavior. The linked checkpoint keeps
the initial and refined timing batches; it does not claim performance recovery.

## Exact numerical factor reuse

The [factor-reuse checkpoint](benchmarks/nr-exact-factor-reuse-2026-10-31.md)
records core commit `74d19134c13d805fb76c3f8eaabc58e22609d3ba`. One accepted initial operator retains its
own numerical factor arrays; fresh CSC pattern/values, ownership and KLU
configuration must match exactly before reuse. The common Newton engine,
formulations, mutation validation and Jacobian refresh policy remain unchanged.
Retained/fixed-operator plans enable it; generic preparation remains conservative.

All 577 required checks pass in the 7,990-identity full run, with 49 additions,
91 existing skips and the same 16 failures/errors. There are no missing/new
failures/skips, changed diagnostics, fork dumps or source drift. All 108 timed
production classpath entries match the full run. Matched warmed means decrease
7.39% for IEEE118, 7.06% for PEGASE9241 and 9.99% for 25k, with byte-identical
voltages and independent accuracy checks. One-warmup recovery remains incomplete.
The linked checkpoint retains the separate all-five direct RustPower comparison,
complete samples, exact-cache/actual-work counts and bounded-heap observations.
Four cases meet <=1.2; IEEE118 remains above the target at 1.309. Architecture,
complete coverage/interaction closure and final release qualification remain open.

## Terminal contracts and rejected indexing candidate

The [terminal checkpoint](benchmarks/nr-terminal-evaluation-contracts-2026-10-31.md)
records test commit `65039c29a83a2ea3d3552bcd990391e3dba341c5` and 11 new independent endpoint cases. All 588
required checks pass in the 8,001-identity full run, with 91 existing skips and
the same 16 failures/errors. No test identities disappear or gain new failures/
skips, baseline diagnostics remain unchanged, and no fork dumps or source drift
occur. All 108 production classpath entries match the accepted benchmark runtime.

The measured terminal-index candidate did not improve IEEE118 and was reverted;
all 40,800 samples and both source versions remain. Its IEEE118 mean increases
6.18%, including a retained 16.4449 ms maximum; the median increases about 1.08%.
No general speedup is claimed. Production performance evidence remains the
accepted exact-factor checkpoint, with four passing cases and IEEE118 at 1.309.
Separate capabilities, complete adapter resolution and final qualification remain
open. TC-09 now explicitly records the missing adversarial equal-hash pattern test.
