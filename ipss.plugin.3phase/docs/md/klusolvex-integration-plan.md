# KLUSolveX Integration Development Plan

## Goal

Add an optional KLUSolveX-backed sparse complex solver for InterPSS
three-phase distribution studies, with the first production target being
fixed-point distribution power flow and QSTS factorization reuse.

Success criteria:

- Existing CSJ-backed sparse solving remains the default until KLUSolveX
  parity and deployment checks pass.
- KLUSolveX can be selected through the existing sparse-equation factory path.
- Fixed-point PF and QSTS cases produce the same voltage and current results as
  the current Java solver within existing OpenDSS/DSS-Python comparison
  tolerances.
- Repeated RHS solves reuse factorization when the sparse matrix is unchanged.
- Matrix-value updates for tap or shunt admittance changes reuse symbolic
  structure and rerun only numeric factorization when possible.
- Missing native binaries fail with a clear fallback or diagnostic, not a JVM
  crash.

## External Solver Facts

KLUSolveX is the DSS-Extensions fork of KLUSolve for DSS C-API:

- Repository: https://github.com/dss-extensions/klusolve
- License: LGPL 2.1 or later.
- Current public release observed: v1.2.2, dated 2026-02-19.
- It uses SuiteSparse KLU and Eigen, and can use system SuiteSparse/Eigen or
  download SuiteSparse source during CMake builds.
- Its advertised extensions include dense matrix functions, symbolic/numeric
  factorization reuse when the sparse matrix is unchanged, and incremental
  matrix update support for DSS C-API use cases.
- The C-style API uses sparse-set handles. Important functions documented by
  DSS-Extensions include `NewSparseSet`, `DeleteSparseSet`,
  `AddPrimitiveMatrix`, `IncrementMatrixElement`, `ZeroSparseSet`,
  `FactorSparseMatrix`, `SolveSparseSet`, `GetCompressedMatrix`,
  `GetTripletMatrix`, `GetSingularCol`, and `SetOptions`.

Primary implementation implication: do not expose KLUSolveX directly to power
flow algorithms. Wrap it behind the current InterPSS sparse solver interfaces
and keep the native dependency optional.

## Current InterPSS Integration Points

Core-side sparse factory:

- `com.interpss.core.sparse.SparseEqnObjectFactory`
- Existing extension hooks:
  - `setComplexEqnCreator(Function<Integer, ISparseEqnComplex>)`
  - `setComplextMatrix3x3EqnCreator(Function<Integer, ISparseEqnComplexMatrix3x3>)`
- Default implementations:
  - `CSJSparseEqnComplexImpl`
  - `CSJSparseEqnComplexMatrix3x3Impl`

Three-phase distribution users:

- `DistributionPowerFlowAlgorithmImpl`
  - owns fixed-point solver cache fields and the current power-flow solve loop.
  - builds power-flow-only Y matrices through `formYMatrixABCForPowerflow(...)`.
- `DStabNetwork3Phase` / `DStabNetwork3phaseImpl`
  - expose `formYMatrixABCForPowerflow()` and `getYMatrixABCForPowerflow()`.
- QSTS planning already expects a reusable linear solve context with
  factorization invalidation only when admittance changes.

## Architecture

### 1. Core Native Solver Module

Add a small native-backed solver package in core, keeping it isolated from
distribution-specific code:

```text
com.interpss.core.sparse.impl.klusolvex
  KlusolveXNativeLibrary
  KlusolveXSparseSet
  KlusolveXSparseEqnComplexImpl
  KlusolveXSparseEqnComplexMatrix3x3Impl
  KlusolveXSolverOptions
  KlusolveXAvailability
```

Responsibilities:

- Load the native `libklusolvex` library only when explicitly requested.
- Own native sparse-set handles and release them deterministically.
- Convert InterPSS complex sparse rows to KLUSolveX primitive matrix entries.
- Preserve InterPSS zero-based indexing.
- Track matrix state:
  - structure dirty
  - numeric values dirty
  - RHS dirty
  - factored
- Map KLUSolveX return codes to `IpssNumericException` or a typed solver
  status object.
- Expose singular-column diagnostics for tests and user messages.

Native access choice:

- First choice: JNA or Java Foreign Function and Memory API if the supported
  runtime baseline allows it.
- Fallback: JNI only if JNA/FFM introduces unacceptable overhead or packaging
  issues.

Do not make KLUSolveX a compile-time dependency of all InterPSS users. The Java
classes can exist in core, but native loading should happen only after explicit
solver selection.

### 2. Solver Selection Boundary

Add a single configuration entrypoint, for example:

```java
SparseEqnSolverProvider.useDefault();
SparseEqnSolverProvider.useKlusolveX();
SparseEqnSolverProvider.useKlusolveXIfAvailable();
```

Internally this should set the existing `SparseEqnObjectFactory` creators.

Recommended runtime controls:

- System property: `ipss.sparse.solver=csj|klusolvex|auto`
- System property: `ipss.klusolvex.library.path=/path/to/libklusolvex`
- Optional diagnostic property: `ipss.klusolvex.trace=true`

Default behavior:

- `csj`: always use current Java implementation.
- `klusolvex`: fail fast if the native library cannot be loaded.
- `auto`: use KLUSolveX if available; otherwise log fallback to CSJ.

### 3. Matrix Assembly Contract

The existing `ISparseEqnComplexMatrix3x3` facade expands each bus-level 3x3
block into scalar complex matrix entries. The KLUSolveX implementation should
reuse that public contract:

- `addToA(Complex3x3 block, int i, int j)` expands to 9 scalar updates.
- `setA(...)` updates scalar entries in-place where possible.
- `setBi(...)`, `setBVector(...)`, and `addToB(...)` update only the RHS.
- `factorization(buildSymbolTable, tolerance)` maps to:
  - full rebuild and symbolic analysis when structure is dirty or
    `buildSymbolTable == true`;
  - numeric refactor when only values changed;
  - no-op when already factored and matrix is clean.
- `solveEqn(...)` calls `FactorSparseMatrix` if required, then
  `SolveSparseSet`.

The wrapper should keep Java-side row data during Phase 1. That avoids changing
existing `getA(...)`, `multiply(...)`, debug output, and test expectations.
Later, after parity, a lower-memory mode can use KLUSolveX as the primary
matrix store.

### 4. QSTS Linear Solve Context

After the native wrapper is stable, add a distribution-level linear solve
context:

```text
QstsLinearSolveContext
QstsYBusFactorizationCache
QstsSparseMatrixUpdate
QstsSolverDiagnostics
```

Purpose:

- Own a solver-ready Ybus instance per QSTS worker.
- Reuse factorization for load/source/DER RHS-only steps.
- Apply in-place admittance value updates for regulator tap steps and rerun
  numeric factorization.
- Keep capacitor/reactor RHS-compensation support separate from matrix-value
  update support.
- Force rebuild when topology, node ordering, swing-boundary treatment, or
  unsupported device changes alter the sparse structure.

This context should be optional at first. The fixed-point solver can continue
using existing caches while KLUSolveX parity is proven.

## Milestones

### Phase 0: API and Licensing Review

Tasks:

- Confirm KLUSolveX exported function signatures on Windows, macOS, and Linux.
- Verify LGPL obligations for shipping native binaries with InterPSS
  distributions.
- Decide whether native binaries are distributed, user-installed, or resolved
  from an external package.
- Document supported OS/architecture matrix:
  - macOS arm64/x64
  - Linux x64/arm64
  - Windows x64
  - Windows x86 only if still required by user workflows.

Verification:

- A short license/deployment note in `docs/md`.
- A local smoke test that loads `libklusolvex` and creates/deletes a sparse
  set.

### Phase 1: Native Wrapper Smoke Tests

Tasks:

- Implement `KlusolveXNativeLibrary` and handle lifecycle.
- Add `KlusolveXSparseSet` with create/delete/factor/solve methods.
- Add tiny scalar complex solve tests:
  - 1x1 non-singular.
  - 2x2 non-singular.
  - singular matrix with diagnostic column.
  - repeated RHS solve without matrix rebuild.

Verification:

- Tests skip cleanly when KLUSolveX is unavailable.
- When enabled, solve results match hand-calculated expected values.

### Phase 2: InterPSS Sparse Interface Adapter

Tasks:

- Implement `KlusolveXSparseEqnComplexImpl`.
- Implement `KlusolveXSparseEqnComplexMatrix3x3Impl`.
- Register them through `SparseEqnObjectFactory`.
- Preserve current `getA`, `getX`, `multiply`, `setToZero`, and matrix dirty
  semantics.
- Add focused tests that run the same sparse cases against CSJ and KLUSolveX.

Verification:

- Existing sparse unit tests pass under CSJ.
- New parity tests pass under KLUSolveX when available.
- Native handles are released in tests even on failure.

### Phase 3: Fixed-Point Power Flow Opt-In

Tasks:

- Add solver selection to fixed-point distribution PF setup.
- Run existing OpenDSS parser/PF comparison tests with CSJ and KLUSolveX.
- Validate:
  - bus voltages
  - swing power
  - branch currents
  - convergence iteration counts
  - singular/floating-phase diagnostics.

Verification:

- `OpenDssParserPowerFlowComparisonTest` remains within current tolerances.
- KLUSolveX and CSJ produce equivalent solved states on IEEE13, IEEE123, and
  existing OpenDSS mini cases.

### Phase 4: Factorization Reuse and Matrix-Value Updates

Tasks:

- Add explicit support for:
  - RHS-only repeated solves.
  - numeric refactor after value-only changes.
  - full rebuild after structure changes.
- Route QSTS regulator tap matrix updates through the value-update path.
- Route capacitor/reactor fixed shunt changes through either:
  - RHS compensation for supported cases; or
  - value-update plus numeric refactor.
- Capture solver timing counters:
  - matrix build time
  - symbolic factor time
  - numeric factor time
  - solve time
  - number of full rebuilds
  - number of numeric refactors
  - number of RHS-only solves.

Verification:

- Existing QSTS regulator and capacitor tests still pass.
- Performance benchmark shows fewer full rebuilds on repeated QSTS steps.
- Fallback paths are exercised for unsupported topology changes.

### Phase 5: Large Feeder Benchmark and Release Gate

Tasks:

- Benchmark current CSJ versus KLUSolveX on large OpenDSS feeders.
- Use one solver context per worker; do not share native handles across
  parallel workers.
- Validate memory and handle cleanup after repeated yearly-style runs.
- Add user documentation:
  - how to enable KLUSolveX;
  - how fallback works;
  - how to diagnose missing native libraries;
  - known unsupported platforms.

Verification:

- Large feeder benchmark captures solve time, matrix rebuild count, and memory.
- No native handle leaks across repeated test runs.
- Release documentation is ready before enabling `auto` mode by default.

## Risks and Mitigations

- Native packaging risk: keep CSJ as default and make KLUSolveX opt-in until
  binaries and license obligations are settled.
- Indexing risk: KLUSolveX arrays are zero-based; add tests that verify row,
  column, and phase ordering explicitly.
- Matrix-update risk: value updates must not silently change sparse structure.
  Track structure changes separately from numeric value changes.
- Stability/PF boundary risk: do not route dynamic simulation `solveNetEqn()`
  through QSTS-specific caches until the power-flow path is validated.
- Threading risk: use one KLUSolveX sparse-set handle per solver context and
  per worker.
- Diagnostics risk: singular matrices should report InterPSS bus/phase labels,
  not just native column numbers.

## Initial Implementation Order

1. Add native load/smoke-test wrapper.
2. Add KLUSolveX scalar complex sparse adapter.
3. Add KLUSolveX 3x3 block sparse adapter.
4. Register adapter through `SparseEqnObjectFactory`.
5. Add fixed-point PF opt-in and parity tests.
6. Add QSTS factorization reuse hooks.
7. Add matrix-value update path for regulator/shunt cases.
8. Add large feeder benchmarks and release documentation.

## Non-Goals for the First Release

- Replacing all InterPSS sparse solvers globally.
- Removing CSJ sparse solving.
- Using KLUSolveX for OPF linear programming or optimization solvers.
- Sharing one native sparse-set across threads.
- Refactoring the whole sparse interface before proving KLUSolveX parity.
