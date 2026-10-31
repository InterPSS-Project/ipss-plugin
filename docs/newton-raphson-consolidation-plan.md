# Newton-Raphson consolidation plan

Status: Implementation in progress. Selection, shared polar/rectangular kernels, residual/state handling and common preparation have focused validation. Contributor migration and final qualification remain open. See [implementation evidence](nr-consolidation-progress.md) and [coverage manifest](nr-consolidation-test-coverage.json).

The target is one polar NR formulation and one rectangular NR formulation,
with a selectable execution mode controlling preparation and caching. Both
modes use the same equations, voltage-update rules and Newton iteration engine.
The APIs and component names below are proposed for the refactor.

## 1. Provide explicit execution options

Add a per-algorithm `NrExecutionMode`:

| Mode | Behavior | Intended use |
| --- | --- | --- |
| **`AUTO` - recommended default** | Retain preparation where supported and valid; use fresh preparation where required. Report any fallback and its reason. | Normal operation and compatibility with current behavior. |
| **`CACHED`** | Require reusable preparation. Rebuild or refresh it after supported mutations. Report unsupported capabilities explicitly. | Performance-sensitive applications and qualification tests that must prove cached execution occurred. |
| **`GENERIC`** | Obtain fresh model data and construct assembly information whenever assembly is requested. Execute the same numerical kernels. | Diagnostics, custom models and comparison against cached execution. |

Proposed usage:

```java
algorithm.setLfMethod(AclfMethodType.NR);
network.setPolarCoordinate(true); // false selects rectangular coordinates

algorithm.setNrExecutionMode(NrExecutionMode.CACHED);
// Alternatives: GENERIC or AUTO
```

Execution mode remains separate from Jacobian/LU reuse policy. Selecting
`GENERIC` does not change damping, convergence tolerances, control behavior or
the existing modified-Newton policy. Fresh preparation is required when
assembly is requested; the established numerical policy still determines when
a Jacobian refresh is needed.

Mode changes take effect at the next solve boundary.

## 2. Separate equations, preparation and model extensions

| Component | Responsibility |
| --- | --- |
| Shared Newton engine | Convergence, rebuild/refactor/LU-reuse decisions, linear solves, line search, control transitions and retries. |
| Polar formulation | One implementation of polar residuals, Jacobian entries and corrections using the existing relative-magnitude convention. |
| Rectangular formulation | One implementation of rectangular residuals, Jacobian entries and corrections, preserving existing row ordering and update conventions. |
| Assembly plan | Bus/branch indexing, equation masks, contributor layout, sparse destinations, cached coefficients and validity information. No independent equation formulas. |
| Numerical workspace | Voltages, calculated P/Q, residuals, derivatives and corrections, with stamps identifying the state at which they were evaluated. |
| Model adapters and equation contributors | Load models, custom models, external injections, ZBR auxiliaries and coordinated control equations. |
| Sparse backend adapter | Publish the same assembled values through JKLU or other supported matrix APIs, preserving inspection and RHS interfaces. |

The existing `DefaultNrSolver`, `ZbrNrSolver` and coordinated-solver classes can
remain as compatibility facades. Their common electrical equations come from
the same formulation implementation.

Custom contributors must be able to add or replace rows, declare auxiliary
variables and identify structural changes. This preserves co-solved controls
rather than treating them as ordinary bus injections.

## 3. Consolidate in reviewable phases

| Phase | Implementation work | Required gate |
| --- | --- | --- |
| **0. Establish baseline** | Freeze current source/runtime/configuration; inventory custom overrides, full/reduced matrices and control hooks. Preserve a test-only reference assembler. | Reproduce the baseline and record exact failure identities. Preserve unrelated working-tree edits. |
| **1. Add selection and reporting** | Introduce execution mode, capability checks and an execution report. Initially map these onto existing behavior. | Per-instance selection, unsupported-mode diagnostics and compatibility tests. |
| **2. Unify polar residual/Jacobian evaluation** | Extract one polar kernel from `PreparedPolarJacobian` and the polar branch of `AclfNetJMatrixHelper`. Collapse the ordinary and retained-workspace formulas. Make both execution modes supply inputs to this kernel. | Independent physical-current checks, finite differences, full/reduced matrix tests and cached-versus-rebuilt comparisons. |
| **3. Unify corrections and allocation** | Move polar voltage updates, correction maxima, trial evaluation and accepted-power allocation into shared routines. | Limits, negative-magnitude canonicalization, zero-scale trials, rejected trials, rollback and generator-allocation parity. |
| **4. Consolidate rectangular and specialized assembly** | Extract the rectangular formulation; connect ZBR/control contributors and network-response matrix views. | Rectangular derivatives, auxiliary residuals, active-set transitions and public matrix API compatibility. |
| **5. Retire production duplication** | Convert old helpers into facades. Remove internal prepared-specific residual/update/allocation dispatch and the prepared-mismatch sentinel. Retain deprecated public compatibility methods. | Both modes demonstrably invoke the same formulation classes; the old mathematical implementation exists only in tests. |
| **6. Qualify and document** | Run full regression testing and matched benchmarks; update the core enhancement document and selection examples. | No new failing or removed test identities; documented numerical and performance results for the actual executed modes. |

The sequence is polar evaluation first, then updates/allocation, followed by
removal of prepared-specific dispatch.

## 4. Define one invalidation contract

Validity must be checked before choosing rebuild, refactor or LU reuse.

| Change | Required treatment |
| --- | --- |
| Voltage magnitude/angle | Invalidate the evaluated residual. Refresh Jacobian values when requested by the existing NR policy. |
| Pure specified P/Q or constant external injection | Refresh the residual; invalidate derivatives when the contributing model declares a derivative change. |
| Switched-shunt/SVC B or physical service state | Read current live B; refresh affected Jacobian values and invalidate incompatible numerical factors. |
| Impedance, tap, phase, bus/terminal shunt, fixed bank or load coefficients | Refresh affected base coefficients and derivatives; invalidate numerical factors. |
| PV/PQ, reference identity or control-equation change | Rebuild equation masks/layout and affected sparse structure; invalidate incompatible factors. |
| Bus/branch membership, endpoints, activity or numbering | Rebuild indexing and structural preparation. |
| Contributor addition/removal or auxiliary-variable change | Rebuild contributor layout and affected structure. |
| Coordinate, backend or network replacement | Discard incompatible preparation, workspace and factors. |

A numerical zero must retain its structural slot. Notifications alone cannot
certify validity: notification-suppressed mutations need snapshot validation
unless an explicit ownership contract guarantees unchanged data.

## 5. Preserve model and observation semantics

The consolidated implementation must retain:

- Fixed-base/live-device treatment, with physical dynamic shunts remaining in
  the Norton matrix.
- Existing load models and their derivatives, including custom-model behavior.
- PV/PQ/reference constraints, control limits, auxiliary equations and rollback.
- Current voltages visible to controllers, callbacks, logging and observers at
  the required boundaries.
- Separate full-network response views that retain mandatory electrical
  auxiliaries while respecting existing control exclusions.

A contributor needing fresh evaluation can still run through the common
kernel. `AUTO` reports that requirement; `CACHED` rejects an unsupported reuse
contract rather than silently claiming a cache hit.

## 6. Maintain API compatibility and expose actual behavior

Retain the current prepared-Newton methods during deprecation:

| Existing setting | Proposed mapping |
| --- | --- |
| `setPreparedNewtonEnabled(false)` | `GENERIC` |
| `setPreparedNewtonEnabled(true)` | `AUTO`, preserving existing fallback behavior. |

Add a coordinate-neutral execution report containing:

- Requested mode and actual execution strategy.
- Preparation builds, coefficient refreshes and structural rebuilds.
- Jacobian assemblies, factorizations and triangular solves.
- Fallback reasons and affected model/contributor identifiers.

This makes it clear whether a benchmark actually exercised caching.

## 7. Acceptance and performance measurements

| Area | Checks |
| --- | --- |
| Equations | Independent physical-current balance and finite differences for polar/rectangular, full/reduced matrices and voltage-dependent loads. |
| Cache correctness | Identical fixtures in `CACHED` and `GENERIC`, exercising electrical, structural and notification-suppressed mutations. |
| Controls | PV/PQ transitions, switched shunts/SVCs, ZBR auxiliaries, coordinated controls and rollback. |
| State handling | Line-search trials, observation boundaries, accepted-power reuse and generation allocation. |
| Compatibility | Custom subclasses, matrix inspection, RHS APIs and supported sparse backends. |
| Integration | Full core/plugin regression audit and relevant dynamic-initialization/fault checks. |

Performance comparisons distinguish:

1. Unified cached versus unified generic: measures preparation reuse.
2. Unified implementation versus the frozen maintained implementation: checks
   refactor regressions.
3. External benchmark comparisons: reported separately with matched timing
   boundaries.

Retain the existing small/large power-flow cases and coupled dynamic
benchmarks. Inputs, tolerances, controls, recordings and exports stay fixed.
Results include actual-mode coverage, medians, ranges, compilation time and
executable fingerprints.

The completion criterion is one production equation implementation per
coordinate form, selectable cached/generic execution, preserved compatibility
and verified results. Performance improvement remains a measured outcome.

## 8. Detailed implementation TODO list

Items marked complete have evidence in [the progress record](nr-consolidation-progress.md). Complete an item only when its code or
document change and the corresponding verification evidence are available.
Record the implementing commit and evidence path beside completed items.
Historical validation provides references; establish a fresh baseline for this
refactor because other work may have changed the checkout.

The task IDs are stable references for implementation reviews. Complete the
groups in dependency order: baseline, selection, shared preparation, polar
equations, updates/allocation, rectangular/contributors, cleanup, qualification
and delivery. Sections 8.2 and 8.3 together implement phase 2.

### 8.0 Baseline and contracts - phase 0

- [x] **BASE-01** Record core/plugin source commits, working-tree patches,
  untracked source dependencies and the unrelated edits that must be preserved.
- [x] **BASE-02** Freeze a working executable with its classpath, first-party
  classes/resources, dependency hashes, inputs and configuration before changing
  or replacing artifacts.
- [x] **BASE-03** Inventory every production caller of `createJMatrix()`,
  `updateJMatrix()`, mismatch evaluation, voltage updates, generation allocation
  and `createNetworkResponseJMatrix()`.
- [x] **BASE-04** Document duplicated formulas in `PreparedPolarJacobian.fill()`,
  `fillWorkspaceJacobian()`, its residual routines, and the polar/rectangular
  branches of `AclfNetJMatrixHelper`.
- [x] **BASE-05** Inventory custom overrides and model families, including
  custom Yii/power calculations, load functions, child networks, augmented ZBRs
  and coordinated controls. Record their current support restrictions.
- [ ] **BASE-06** Freeze representative residuals, matrix entries, corrections,
  accepted voltages and allocated generation for full/reduced matrix forms and
  PQ/PV/reference/inactive buses.
- [x] **BASE-07** Preserve the old assembler as a test-only oracle with its
  source provenance. Add independent physical-current and derivative references
  so correctness is not established solely by comparing two implementations.
- [x] **BASE-08** Run and audit the current full source-reactor suite. Record
  every test identity, failure/error, skip and fork dump before implementation.
- [ ] **BASE-09** Fix benchmark cases, timing boundaries, warmup/sample protocol,
  numerical settings and acceptance criteria before collecting changed results.

Exit evidence: baseline source/runtime manifest, support inventory, numerical
fixtures, full-test audit and benchmark protocol.

### 8.1 Selection, capabilities and reporting - phase 1

- [x] **MODE-01** Add `NrExecutionMode` with `AUTO`, `CACHED` and `GENERIC`;
  store the selection per `LoadflowAlgorithm` instance with `AUTO` as the default.
- [x] **MODE-02** Define setters/getters and next-solve-boundary semantics in
  `LoadflowAlgorithm` and `LoadflowAlgorithmImpl`; keep the coordinate selection
  independent from execution mode.
- [x] **MODE-03** Implement `GENERIC` using fresh assembly inputs and the same
  formulation kernels. Preserve the existing Jacobian refresh and LU-reuse
  policy, including validation required before factor reuse.
- [x] **MODE-04** Implement explicit capability checks for `CACHED`. Supported
  mutations must refresh/rebuild preparation normally; an unsupported reuse
  contract must produce a useful model/contributor diagnostic.
- [x] **MODE-05** Implement `AUTO` selection and fallback at coherent evaluation
  boundaries. Report the actual strategy and fallback reason.
- [ ] **MODE-06** Define capabilities separately for structural reuse,
  coefficient reuse and delayed voltage publication. Observers or live models
  must not accidentally inherit an unsafe capability.
- [x] **MODE-07** Add a coordinate-neutral execution report with requested and
  actual mode, preparation builds, refreshes, rebuilds, Jacobian assemblies,
  factorizations, triangular solves and fallback identifiers.
- [x] **MODE-08** Bridge `setPreparedNewtonEnabled(false)` to `GENERIC` and
  `true` to `AUTO`; retain deprecated public methods and define compatible
  behavior for existing third-party `INrSolver` implementations.
- [x] **MODE-09** Test per-instance isolation, repeated solves, mode changes,
  unsupported cached execution and fallback reporting without mutating global
  JVM properties inside tests.

Exit evidence: configuration/API tests and reports proving the requested mode
was either exercised or explicitly rejected/fallen back.

### 8.2 Shared preparation, workspace and sparse publication - phase 2

- [ ] **PLAN-01** Extract an assembly plan containing indexing, equation masks,
  contributor layout, coefficients and sparse destinations; keep residual,
  Jacobian, correction and allocation formulas outside the plan.
- [x] **PLAN-02** Introduce a numerical workspace for both coordinate forms,
  including phasors, calculated P/Q, load derivatives, RHS and correction data.
- [ ] **PLAN-03** Define an evaluated-state stamp covering voltage, model,
  external injection and equation/control revisions. Prevent a residual or
  accepted-power result from being reused at a different state.
- [x] **PLAN-04** Preserve the distinction between voltage changes and model
  changes: voltage updates require fresh residuals, while the existing NR policy
  decides whether a lagged Jacobian/LU remains eligible.
- [ ] **PLAN-05** Implement structural invalidation for network identity,
  membership, ordering, activity, endpoints, coordinates, masks, contributor
  layout and backend changes.
- [x] **PLAN-06** Implement coefficient invalidation for impedance/multipliers,
  taps, phase shifts, terminal/bus shunts, fixed banks and load parameters. A tap
  change must update both mutual and terminal self-admittances.
- [x] **PLAN-07** Refresh live switched-shunt/SVC B independently from the fixed
  base. Preserve physical service-state, control-lock and existing load-model
  semantics, including large-B base isolation.
- [x] **PLAN-08** Validate notification-suppressed writes through authoritative
  snapshots unless an explicit ownership contract certifies unchanged data.
- [x] **PLAN-09** Check validity before policy selection in `LfCalcNrEngine`
  and again for direct NR-step callers or changes after policy selection.
- [x] **PLAN-10** Allocate structural slots for values that can become nonzero;
  distinguish a numerical zero from an absent matrix entry.
- [x] **PLAN-11** Add backend publication adapters for the common numerical
  values, including direct JKLU CSC filling and supported existing matrix APIs.
  Preserve coefficient inspection, RHS access and mutation invalidation.
- [ ] **PLAN-12** Resolve model adapters and destination mappings outside the
  numerical loops; avoid introducing per-nonzero allocation or unnecessary
  virtual dispatch in place of the current indexed writes.

Exit evidence: mutation/invalidation tests and sparse publication checks for
cached and rebuilt preparation, including known-solution linear solves.

### 8.3 One polar residual/Jacobian implementation - phase 2

- [x] **POLAR-01** Identify shared nodal P/Q and load/live-device evaluation
  primitives that can serve both bulk NR evaluation and public helper facades.
- [x] **POLAR-02** Extract one polar residual routine using current model data,
  external injections and the existing mismatch sign conventions.
- [x] **POLAR-03** Extract one polar Jacobian routine and remove the mathematical
  split between ordinary prepared filling and retained-workspace filling.
- [x] **POLAR-04** Retain the existing relative-magnitude correction convention
  and the analytic fixed-base/live-device voltage derivatives.
- [ ] **POLAR-05** Reuse calculated powers and derivatives from the same
  evaluated state when assembling the Jacobian; refresh them when their stamp
  is invalid.
- [x] **POLAR-06** Support reduced and full polar matrices through equation-mask
  options in the same kernel, including reference and PV row/column behavior.
- [x] **POLAR-07** Route `AclfNetJMatrixHelper.createJMatrix()` and
  `updateJMatrix()` polar requests through the shared formulation while
  preserving their public contracts and additional-block dimensions.
- [x] **POLAR-08** Route `DefaultNrSolver` and `LfCalcMismatchHelper` through one
  evaluated-result contract; keep external-device preparation and auxiliary
  residual refresh in their established order.
- [ ] **POLAR-09** Preserve custom model overrides through adapters or row
  contributors. Guard against recursive calls between public helper facades
  and the common evaluator, and against double-stamping physical shunts.
- [ ] **POLAR-10** Compare `AUTO`, `CACHED` and `GENERIC` at identical voltage and
  model states against the test oracle and independent current/finite-difference
  checks. Assert positive execution coverage for each supported strategy.

Exit evidence: one production polar formula implementation with full/reduced,
model and mutation parity established before changing update/allocation logic.

### 8.4 Corrections, trial states and generation allocation - phase 3

- [x] **STATE-01** Move polar correction application and correction-maxima
  calculation into one routine shared by every execution mode.
- [x] **STATE-02** Preserve angle/relative-magnitude conventions, global polar
  step scaling, variable-update limits and inactive/reference/PV behavior.
- [x] **STATE-03** Preserve negative-magnitude canonicalization and continuous
  PV target restoration during line-search trials, including zero-scale trials.
- [ ] **STATE-04** Give each trial state a valid evaluation stamp; restore both
  workspace and published state on rejected trials and rollback.
- [ ] **STATE-05** Define voltage-publication boundaries for controllers,
  callbacks, EMF observers, logging and direct public getters. Publish before
  any consumer that cannot read the numerical workspace.
- [x] **STATE-06** Reuse accepted calculated P/Q only when voltage, model,
  injection and control stamps still match; exclude rejected trial results.
- [ ] **STATE-07** Consolidate generation allocation through the established
  allocation rules, including fixed generation, distributed generation,
  multiple generators and physical reactive compensation.
- [x] **STATE-08** Remove independent correction and allocation mathematics from
  the prepared implementation after shared routines pass their gates.
- [ ] **STATE-09** Test trial/rollback histories, callback visibility, repeated
  solves and allocated P/Q as well as final voltage convergence.

Exit evidence: state-history and allocation parity, including observation and
line-search cases that cannot retain unpublished voltages.

### 8.5 Rectangular formulation and specialized contributors - phase 4

- [x] **EXT-01** Extract the rectangular residual/Jacobian implementation and
  reuse the same physical power/load evaluation primitives as polar NR.
- [x] **EXT-02** Preserve rectangular row ordering, PV voltage constraints,
  full/reduced matrix behavior and absolute rectangular corrections.
- [x] **EXT-03** Centralize rectangular correction application while retaining
  its existing update-limit and line-search conventions.
- [x] **EXT-04** Validate rectangular live-B chain rules and voltage-dependent
  load derivatives with independent finite differences.
- [x] **EXT-05** Introduce contributor contracts for additional variables,
  appended or replaced rows, residuals, derivatives and layout revisions.
- [x] **EXT-06** Adapt `ZbrNrSolver` and coordinated solvers to contribute their
  auxiliary/control equations around the shared electrical kernel, preserving
  existing public classes and supported configurations.
- [ ] **EXT-07** Preserve co-solved device columns, limits and bound transitions;
  changing a control active set must invalidate the appropriate layout/factors.
- [x] **EXT-08** Preserve `createNetworkResponseJMatrix()` semantics, including
  mandatory electrical auxiliaries and existing control-variable exclusions.
- [ ] **EXT-09** Preserve custom residual, matrix, update and allocation hooks
  through explicit compatibility adapters and test their invocation behavior.
- [x] **EXT-10** Keep unsupported coordinate/model combinations explicit until
  their equations are implemented and qualified; avoid silent method changes.
- [ ] **EXT-11** Verify PQ-to-NR handoff, full-Newton control responses and
  dynamic pre-initialization load flow continue to use the shared NR contracts.

Exit evidence: rectangular and augmented-system parity with valid contributor
layout changes, auxiliary convergence checks and compatible public APIs.

The [contributor checkpoint](benchmarks/nr-consolidation-contributors-2026-10-31.md)
closes EXT-05/06/08/10. The shared assembly orders mandatory electrical
auxiliaries before optional controls, owns exact structural keys and retains
each matrix's equation roles through residuals, corrections and trial rollback.
Twenty-eight new cases pass in the 7,906-identity full run. Layout validation
does not yet provide the separate coefficient/capability contracts in MODE-06;
EXT-07/09/11 and final qualification remain open.

### 8.6 Remove prepared-specific production dispatch - phase 5

- [x] **CLEAN-01** Replace `PREPARED_MISMATCH` in `LfCalcNrEngine` with a typed,
  state-stamped evaluated result.
- [x] **CLEAN-02** Remove internal prepared-versus-generic branches for residual
  evaluation and RHS publication in `LfCalcMismatchHelper` and `LfCalcNrEngine`.
- [x] **CLEAN-03** Retire internal calls to `computePreparedPowerMismatch()`,
  `setPreparedPowerMismatch()`, `updatePreparedVoltage()` and
  `allocatePreparedGeneration()` after compatibility adapters are in place.
- [x] **CLEAN-04** Replace prepared-specific scope handling in
  `LoadflowAlgorithmImpl` with common preparation/workspace lifecycle handling.
- [x] **CLEAN-05** Reduce or replace `PreparedPolarJacobian` with preparation-only
  responsibilities; remove its duplicated residual, Jacobian, update and
  allocation equations from production.
- [ ] **CLEAN-06** Convert remaining matrix helpers into formulation facades and
  replace implementation-class eligibility checks with audited capabilities
  where the new contracts support that change.
- [ ] **CLEAN-07** Preserve deprecated public entry points and metrics as
  documented compatibility shims; keep the old assembler confined to tests.
- [ ] **CLEAN-08** Review production sources and execution-coverage tests to
  confirm one polar kernel and one rectangular kernel are selected in every
  supported execution mode.

Exit evidence: source review and execution tests demonstrate that selection
changes preparation behavior without selecting a second formula implementation.

The [lifecycle checkpoint](benchmarks/nr-consolidation-lifecycle-2026-10-30.md)
closes CLEAN-03/04 for ordinary execution: legacy optional hooks are confined to
their compatibility adapter, and engine/algorithm lifecycle observations come
from `INrSolver`. Nine positive bridge/compatibility/exception cases pass in the
full 7,878-identity run. Specialized contributor capabilities and migration of
the independent historical standalone solver remain required for CLEAN-06/08.

### 8.7 Numerical, integration and performance qualification - phase 6

- [ ] **VERIFY-01** Parameterize supported fixtures over both coordinates and all
  three execution modes; assert that cached coverage is real and that expected
  unsupported cases produce explicit diagnostics. Complete the required
  combination matrix in section 9.1.
- [ ] **VERIFY-02** Exercise every mutation in section 4, including device
  addition/removal/reconnection, notification-suppressed writes, rollback,
  structural zeros and coordinate/backend/network replacement.
- [ ] **VERIFY-03** Check residuals, full/reduced Jacobians, linear corrections
  and accepted powers against independent current/derivative references using
  justified absolute/relative tolerances established before implementation.
- [ ] **VERIFY-04** Extend or reuse `PreparedPolarJacobianTest`,
  `PreparedDynamicLoadflowTest`, `CommonLiveShuntModelTest` and
  `LowVoltageLoadJacobianTest` for the new shared-kernel contracts.
- [ ] **VERIFY-05** Run continuous-shunt, fixed-generation, coordinated-control,
  ZBR, FDNR and PQ-to-NR tests, including PV/PQ transitions and rejected trials.
- [ ] **VERIFY-06** Check matrix inspection/RHS APIs, symbolic retention,
  numerical refactorization and invalidation before any factor reuse.
- [ ] **VERIFY-07** Run the full combined core/plugin reactor with extended
  tests, bounded logs/heaps and suite-wrapper duplication excluded. Audit XML
  identities, failures/errors, skips and fork dumps against the fresh baseline.
- [ ] **VERIFY-08** Verify new tests actually executed: a successful build with
  `failIfNoTests=false` is insufficient evidence of coverage.
- [ ] **VERIFY-09** Run coupled dynamic initialization/fault checks with existing
  event-side samples, channels, integration settings and physical Norton shunts
  retained. State any limits on large-case machine-state coverage.
- [ ] **VERIFY-10** Benchmark unified cached versus unified generic and both
  against the frozen maintained implementation using matched cases and timing
  boundaries; record actual execution modes and reuse counters.
- [ ] **VERIFY-11** Keep builds/profiling outside timing batches. Preserve
  complete warmups/samples, residual compilation, JVM/CPU/affinity settings,
  median/range results and executable/input/configuration fingerprints.
- [ ] **VERIFY-12** Keep external-reference comparisons separate, preserve
  recording/export requirements and report performance-target outcomes only
  where the compared scopes and numerical settings match.

Exit evidence: component and full-suite audits, dynamic checks and reproducible
matched benchmark reports, with unresolved baseline failures identified.

### 8.8 Documentation, commits and delivery - phase 6

- [ ] **DELIVER-01** Update the core algorithm enhancement document with the
  final ownership boundaries, selection semantics and invalidation contract.
- [ ] **DELIVER-02** Add examples for `AUTO`, `CACHED` and `GENERIC` in both
  coordinate forms, including an unsupported-cache diagnostic and fallback
  report.
- [ ] **DELIVER-03** Document compatibility mappings, deprecated methods,
  contributor support restrictions and the distinction between preparation
  reuse and the numerical Jacobian/LU policy.
- [ ] **DELIVER-04** Save test/benchmark reports and machine-readable checkpoints
  with exact source/runtime provenance and baseline comparisons.
- [ ] **DELIVER-05** Run the repository neutral-language checker and scoped
  whitespace checks before delivering documentation and new Java files.
- [ ] **DELIVER-06** Create scoped commits tied to the phase gates, preserve
  unrelated edits and retained references, and include the evidence needed for
  each commit to be reviewed independently.

### 8.9 Final completion checklist

- [ ] **DONE-01** Production contains one polar formulation and one rectangular
  formulation; cached and generic execution invoke the same equation kernels.
- [ ] **DONE-02** All three execution modes are selectable per algorithm,
  observable in reports and tested for supported/unsupported capabilities.
- [ ] **DONE-03** Preparation contains indexing/cache/validity information;
  model contributors retain their own equations and control ownership.
- [ ] **DONE-04** Public solver/matrix/model behavior remains compatible and the
  old mathematical assembler is present only as a test oracle.
- [ ] **DONE-05** Numerical and full-regression gates pass without new failing
  or removed test identities; remaining baseline failures are recorded.
- [ ] **DONE-06** Performance results are reproducible, use the maintained
  implementation and disclose actual cache coverage and qualification limits.
- [ ] **DONE-07** Every requirement in section 9 maps to executed tests and
  reviewed evidence, including expected rejection/fallback tests for unsupported
  combinations; all final release gates are satisfied.

## 9. Full test coverage specification

This section defines required test coverage for the consolidation. It is an
implementation and release requirement; the checks below remain planned until
their test results and evidence are recorded.

Full coverage means every declared requirement, supported configuration,
invalidation action and critical state transition has an executed test. A
required case cannot be satisfied by a skipped test, an unexercised cache path
or a successful build with no matching tests. Source coverage metrics provide
additional evidence and do not replace numerical verification.

### 9.1 Required configuration combinations

Use a small electrical fixture containing reference, PV, PQ and inactive buses
to exercise the full foundation matrix:

| Axis | Required values |
| --- | --- |
| Coordinates | Polar; rectangular. |
| Execution mode | `AUTO`; `CACHED`; `GENERIC`. |
| Matrix form | Reduced NR; full electrical Jacobian. |
| Sparse backend | Maintained pure-Java JKLU; CSJ, where the declared contract supports it. |

This produces 24 primary combinations before adding model-specific cases. For
each combination, record either successful execution of its declared numerical
contract or an explicit expected capability rejection. `AUTO` must identify any
fallback. An unsupported backend/coordinate combination must not silently select
a different backend or coordinate form.

For every positive numerical case:

1. Compare residuals, Jacobian values, linear corrections and accepted solution
   outputs against an independent reference appropriate to that case.
2. Exercise cold construction, at least two assembly requests and a repeated
   solve on the same algorithm instance.
3. Assert requested and actual execution strategy, kernel invocation and relevant
   build/refresh/factorization counters. Cached fixtures must demonstrate real
   reuse after preparation; generic fixtures must demonstrate fresh preparation
   when assembly is requested.
4. Hold tolerances, initialization, damping, line search, controls and NR reuse
   policy constant across the mode comparison.

Run each additional model family under its declared supported coordinate/mode
combinations. Add the mandatory interaction sequences in section 9.3 rather
than relying solely on isolated unit cases.

### 9.2 Requirement-to-test matrix

Each row needs named test classes/methods, expected results, execution evidence
and a disposition for any unsupported combination.

| ID | Coverage area | Required cases | Required assertions |
| --- | --- | --- | --- |
| TC-01 | Selection and compatibility | Defaults, explicit modes, two independent algorithms, next-solve mode changes, deprecated boolean settings and third-party solver adapters. | Correct requested/actual mode; per-instance isolation; documented compatibility; useful rejection/fallback reasons. |
| TC-02 | Common electrical equations | Full/reduced polar and rectangular matrices; PQ/PV/reference/inactive buses; non-unit voltage and nonzero angle. | Independent physical P/Q balance, correct row ordering/masks, derivative agreement and correct linear correction. |
| TC-03 | Load models | Constant P/I/Z, ZIP, exponential, function and contributed loads, distributed generation, low-voltage adjustment and threshold boundaries. | Correct values and derivatives; refresh after parameter/service changes; explicit capability handling; existing model precedence retained. |
| TC-04 | Branch and transformer models | Lines, ground/parallel branches, transformers, asymmetric taps, phase shifts, impedance multipliers, terminal charging/shunts and supported impedance tables. | Both endpoint self-admittances and mutual terms update correctly; ground/ZBR handling and custom contracts remain valid. |
| TC-05 | Compensation | Positive/negative/zero B; switched shunt and SVC together; fixed banks; devices at PQ/PV/reference buses; service/lock changes, addition/removal/reconnection and large B. | Exactly one physical contribution; base isolation; current live Q and derivatives; correct generation results; physical Yii/Y APIs retained. |
| TC-06 | Structural invalidation | Bus/branch addition/removal, activity, endpoints, ordering, numbering, PV/PQ/reference changes and contributor layout changes. | Correct plan/mask rebuild and factor invalidation; no stale destinations; numerical zero slots retained. |
| TC-07 | Numerical invalidation | Every coefficient mutation, notification-suppressed writes, external injection changes and mutations after reuse-policy selection. | Fresh residual/model data and the required refactor/rebuild before solving; ordinary voltage updates preserve the configured NR reuse policy. |
| TC-08 | Evaluation ownership | Different trial voltages, restored voltages, changed injections, changed model/control state and repeated accepted states. | Residuals, Jacobians and accepted powers carry valid state stamps; stale RHS and allocation reuse are rejected. |
| TC-09 | Sparse interfaces | Direct CSC versus matrix publication, inspection, RHS APIs, structural zeros, changed dimensions, symbol transfers and deliberately colliding pattern hashes. | Equivalent matrix/solution; exact structural compatibility; correct numerical invalidation; supported public mutations remain visible. |
| TC-10 | Correction rules | Polar relative magnitude, rectangular absolute corrections, global/per-variable limits, inactive/reference updates, PV target restoration and negative polar magnitude. | Existing correction conventions and bounds; correct maxima; equivalent represented phasor after canonicalization. |
| TC-11 | Line search and rollback | Existing supported optimizers, zero/partial/full scale, accepted/rejected trials, numerical failure and control rollback. | Continuous trials, current residual evaluation, restoration of workspace/published state and no reuse of rejected trial results. |
| TC-12 | Reactive/control transitions | PV-to-PQ-to-PV, generator Q limits, local/remote voltage/Q control, continuous/discrete shunts and SVCs, tap/phase/area controls. | Correct active sets, row/column changes, limits, control residuals and electrical convergence in all supported modes. |
| TC-13 | ZBR and electrical auxiliaries | Actual augmented zero-impedance branches, retained terminal shunts, auxiliary flows, multiple auxiliaries and topology changes. | Electrical and auxiliary residuals both converge; auxiliary rows/RHS are refreshed; no duplicate series/shunt contribution. |
| TC-14 | External and coupled injections | Existing supported LCC/VSC/HVDC coupling, external-power refresh, rollback and network-response Jacobians. | Correct refresh ordering and derivatives; mandatory electrical auxiliaries retained; existing control exclusions honored. |
| TC-15 | Custom models and observers | Custom Yii/power/load providers, legacy solver overrides, callbacks, EMF observers, logging and getters during trials and accepted iterations. | Correct callback/publication order, preserved overrides, no recursion/double stamping, and explicit limits on reusable preparation. |
| TC-16 | Generation and result allocation | Reference/PV/PQ generation, fixed P/Q, multiple generators, inactive generators, distributed generation and compensation with non-unit voltage. | Existing allocation/limit rules, totals and public results; allocation uses the accepted state rather than a trial or stale power vector. |
| TC-17 | Rebuild/refactor/LU policy | Forced rebuild, numerical refactor and permitted LU reuse, each under generic and cached preparation with identical policy settings. | Correct action and counter values; invalid model changes override stale reuse; mode choice does not silently alter numerical policy. |
| TC-18 | Lifecycle | Cold solve, retained preparation, previous-solution initialization, network/backend/coordinate replacement, mode switches and exceptions during solve cleanup. | Correct lifecycle reset; no cross-network or cross-algorithm state leakage; scope cleanup and documented final state. |
| TC-19 | Method handoffs and public helpers | Direct NR steps, matrix create/update calls, PQ-to-NR handoff, full-Newton control responses and ordinary PQ/GS/FDNR consumers. | Shared-kernel coverage and public helper compatibility; unchanged behavior of other methods and declared unsupported combinations. |
| TC-20 | Plugin and import integration | Imported standard networks, supported static/dynamic load-flow containers, custom/three-phase compatibility and child-network behavior. | Correct model/provider selection and outputs; specialized solvers keep their supported contracts; required fixtures are present. |
| TC-21 | Dynamic integration | Load flow before initialization, invalidation at initialization, coupled small-case faults, shunt switching, clearing and representative large-case runs. | Physical Norton Y retained; unchanged integration/tolerance settings; complete required timestamps, event sides, channels and state comparisons. |
| TC-22 | Failure and recovery | Singular/failed factorization, failed preparation, non-convergence, non-finite model results, invalid voltage/configuration and missing required fixtures. | Existing failure contracts and diagnostics; no use of an obsolete factor/evaluation; safe cleanup and valid recovery or explicit termination. |
| TC-23 | Architecture and instrumentation | Every mode/coordinate kernel, contributors, cache hits, fallbacks and legacy API shims. | One production formulation per coordinate; all new selection/invalidation decisions exercised; old mathematical implementation confined to test references. |
| TC-24 | Performance qualification | Unified cached/generic comparison, frozen maintained baseline and separate external-reference comparisons. | Matched workload and settings; actual mode/reuse coverage, retained samples, compilation time and runtime/source fingerprints. |

For failure cases, assert the failure stage/reason and state consequences rather
than accepting any exception or any `false` result as sufficient coverage.
For stateful controls, verify histories and rollback as well as final voltage.

### 9.3 Mandatory interaction sequences

Isolated tests must be supplemented by these sequences. Compare each accepted
state with a freshly constructed equivalent model or an independent numerical
reference, and check the required cache/factor action at each transition.

1. Warm a cached plan and eligible LU, change live shunt/SVC B or service state
   with notifications suppressed, solve, restore the device state and solve
   again. Check base retention, live derivatives and generation allocation.
2. Move a regulated bus through PV/PQ transitions while a co-solved control
   reaches/leaves a limit. Check masks, auxiliary layout, residuals and the
   network-response matrix before and after rollback.
3. Change a transformer tap and phase with terminal shunts present. Check both
   self and mutual coefficients, then compare cached and fresh assembly.
4. Change branch/bus activity, endpoints or membership after preparation. Check
   numbering and references, exact structure validation and treatment of
   isolated/unsupported networks before any reused solve.
5. Evaluate a line-search candidate, reject it, restore state and accept another
   candidate. Ensure candidate powers/RHS cannot leak into accepted allocation.
6. Enable an observer or custom callback after a retained-workspace solve.
   Verify current voltages at the callback boundary and the reported change in
   publication/reuse capability.
7. Apply a supported model change at an allowed callback between policy
   selection and assembly, and exercise direct NR-step calls. Verify the final
   validation prevents use of the earlier receipt.
8. Cause preparation/factorization failure after a successful solve, repair the
   model and solve again. Ensure recovery builds valid state without exposing
   the obsolete numerical factors as usable.
9. Switch execution mode, coordinate or backend between solves, then replace
   the network. Verify no stale plan, state stamp or factor crosses ownership
   boundaries.
10. Run coupled initialization, fault application, physical shunt switching and
    clearing. Verify physical Y and accepted state histories throughout.

These cases test serialized lifecycle/callback behavior and independent
algorithm instances; they do not imply thread-safe concurrent mutation of one
network.

### 9.4 Test-suite ownership and traceability

The following new suite names are proposed. Existing tests are seeds to extend
or retain, rather than substitutes for missing combinations.

| Proposed suite | Existing tests to reuse or extend | Coverage |
| --- | --- | --- |
| `NrExecutionModeTest` | Prepared-path selection and API compatibility cases. | TC-01, TC-18, TC-23. |
| `NrFormulationParityTest` | `PreparedPolarJacobianTest`, `CommonLiveShuntModelTest`, `LowVoltageLoadJacobianTest`, `JacobianDiagTest`. | TC-02 through TC-05; the 24 primary combinations. |
| `NrCacheMutationTest` | Prepared mutation tests and `AclfFastDecoupledXbTest` compatibility checks. | TC-06 through TC-08, TC-17 and mutation sequences. |
| `NrSparsePublicationTest` | `SparseEqnSolverParityTest`, `KLUSparseEqnMatrix2x2Test` and CSJ matrix tests. | TC-09, TC-22 and factor/structure receipts. |
| `NrStatePublicationTest` | Existing polar/rectangular optimizer tests and generation-allocation checks. | TC-10, TC-11, TC-15, TC-16. |
| `NrContributorIntegrationTest` | `ContinuousShuntNrIntegrationTest`, `FixedGenerationShuntNrTest`, coordinated/ZBR and native-PQ coupled-control tests. | TC-12 through TC-14, TC-19. |
| `NrLifecycleRecoveryTest` | Existing solver failure, rollback and custom-model tests. | TC-18, TC-22 and explicit negative capability cases. |
| Plugin integration tests | `PreparedDynamicLoadflowTest` in core, `DynamicPowerFlowTransferTest` in plugin integration, existing import/three-phase/handoff suites. | TC-20, TC-21 and public integration behavior. |
| Maintained benchmark drivers | Existing small/large power-flow and coupled dynamic drivers. | TC-24, separately from instrumented test runs. |

Maintain a machine-readable manifest mapping each TC ID and configuration to
its test identifiers, expected execution strategy, oracle, assertions and
evidence. Every row must be executable or have an explicit tested capability
rejection. Do not count an unsupported numerical case as a successful cached
solve or remove it from the inventory silently.

### 9.5 Full regression and release gates

Run focused suites after the relevant phase and the full combined source
reactor after production consolidation. Re-run affected gates after fixes; a
failed final gate cannot be cleared solely by an older passing run.

Template for the full run, from the plugin repository root:

```powershell
$nrCoverageRun = 'nr-consolidation-full-YYYYMMDD-HHMMSS'
$env:JAVA_TOOL_OPTIONS = '-Xmx2g'
python tools/run_bounded.py --log "ipss.test.plugin.core/target/$nrCoverageRun.log" -- mvn -o -f tools/benchmark-reactor/pom.xml '-Pisolated-verification,extended-tests' "-Dverification.run.id=$nrCoverageRun" '-Dtest=*' '-Dsurefire.excludeJUnit5Engines=junit-platform-suite' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.failure.ignore=true' test
```

Use a unique run ID. Offline execution assumes dependencies are already
available. Continuing after failures allows every module to complete; it does
not make Maven's exit status a test-success certificate. Preserve the required
Java agents when bounding heaps.

The release audit must verify:

- Core, plugin core, three-phase plugin and plugin integration reports are
  included; production/sample modules also compile through the source reactor.
- Jupiter/Vintage tests and nonstandard test-class names remain discoverable.
  Excluding the suite-wrapper engine only removes duplicated wrapper execution;
  compare actual test identities with the baseline.
- Every required new test executes, passes and is not skipped. Compare the
  manifest with XML, rather than checking only aggregate test totals.
- No previous test identity disappears, becomes unexpectedly skipped or gains
  a new failure/error. Any intentional change requires an explicit reviewed
  explanation in the audit.
- Existing baseline failures retain their assertion location, exception type
  and relevant diagnostics. A failure occurring earlier, worsening numerical
  behavior or new non-finite state requires investigation even if the test name
  was already failing.
- Required fixture/data availability is checked before qualification. Missing
  new fixtures cannot be converted into a skip that counts as coverage.
- Fork dumps, process crashes, truncated output, interrupted runs and unmatched
  specified test names cannot qualify a gate.
- Source and runtime receipts identify what actually ran, including unrelated
  working-tree changes. Later edits remain outside that tested snapshot until
  they are built and verified.

Run the full suite with the production default and run the dedicated cross-mode
matrix using explicit per-instance mode selection. This avoids forcing strict
cached execution onto tests whose purpose is to exercise unsupported/custom
models. Those tests must check the declared fallback/rejection behavior.

Collect statement/branch coverage for the consolidated packages when compatible
instrumentation is available. Review uncovered paths in the new mode selection,
invalidation, contributor dispatch, trial cleanup and failure recovery code.
All documented branches and transitions need tests; do not inflate coverage by
excluding new error paths. Instrumentation must remain outside performance
timing, and a coverage percentage alone does not establish numerical accuracy
or independent-reference acceptance.

### 9.6 Full-coverage execution checklist

- [x] **COVER-01** Create the requirement/configuration-to-test manifest for all
  TC rows and the 24 primary combinations.
- [ ] **COVER-02** Implement numerical and negative capability cases for both
  coordinates, all modes, matrix forms and declared supported backends.
- [ ] **COVER-03** Cover all supported load/branch/device families and their
  boundary conditions, including custom providers and legacy precedence.
- [ ] **COVER-04** Implement every structural/numerical mutation and evaluated-
  state invalidation case, including notification-suppressed writes.
- [ ] **COVER-05** Implement the mandatory interaction sequences in section 9.3
  and assert expected rebuild/refactor/reuse behavior at each transition.
- [ ] **COVER-06** Verify reduced/full polar and rectangular derivatives against
  independent physical and finite-difference references.
- [ ] **COVER-07** Cover corrections, optimizer trials, rollback, observers and
  accepted-power allocation with state-history assertions.
- [ ] **COVER-08** Cover control limits, PV/PQ transitions, actual ZBR auxiliaries,
  coupled injections and network-response views.
- [ ] **COVER-09** Verify sparse inspection/publication APIs, exact pattern
  compatibility, failed preparation/factorization cleanup and recovery.
- [ ] **COVER-10** Verify custom APIs, lifecycle replacement, per-instance
  isolation and the existing PQ/GS/FDNR/handoff integrations.
- [ ] **COVER-11** Run required coupled dynamic checks with complete existing
  timestamps/channels and physical Norton shunts retained.
- [ ] **COVER-12** Execute the full combined reactor, then audit XML identities,
  skipped cases, baseline diagnostics, new failures and process artifacts.
- [ ] **COVER-13** Review source-coverage gaps and prove positive common-kernel
  and cache coverage rather than inferring execution from configuration.
- [ ] **COVER-14** Execute matched performance studies separately from coverage
  instrumentation and archive all required samples and runtime receipts.
- [ ] **COVER-15** Close each coverage requirement with its test identifiers,
  assertions and evidence; retain unsupported-case and baseline-failure
  dispositions in the final release checkpoint.
