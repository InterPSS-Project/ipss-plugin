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
