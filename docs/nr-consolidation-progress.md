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
