# Prepared polar NR Jacobian integration — 2026-10-30

The maintained core integration and algorithm documentation are committed in
core revision `1c0e8a4af`; the separate numerical experiment is `9014b6a6e`.
The [portable checkpoint](checkpoints/prepared-polar-jacobian-2026-10-30.json)
records audited timings, tests, source fingerprints and provenance. The measured
working tree contained additional uncommitted network-copy work; these timings
are not presented as a clean-checkout measurement of the scoped feature commit.

Ordinary polar NR now prepares Jacobian assembly through
DefaultNrSolver.createJMatrix()/updateJMatrix(). LfCalcNrEngine retains the
Newton loop, mismatch handling, voltage updates, controls and result lifecycle.
The earlier private whole-solve shortcut was removed. Its historical RustPower
timings remain in the [previous experiment](rustpower-optimization-worklog-2026-10-30.md)
and do not establish performance for this implementation.

## Assembly and sparse storage

PreparedPolarJacobian owns indexed buses and directed branches, cached mutual
and diagonal admittances, equation masks and scalar CSC destination positions.
It retains the existing two-equation bus blocks and relative-magnitude Newton
variable. It does not substitute the earlier reduced numerical solver.

Every slot that can become nonzero for the current topology and masks is
allocated, even when the current value is zero. Parallel branches add into
shared destinations. Numerical fills read current voltages and call the same
voltage-dependent load-derivative helper as the existing assembler.

KLUSparseEqnMatrix2x2Impl owns the prepared CSC arrays. Numeric publication
updates both those arrays and the ordinary row/block inspection view. The JKLU
bridge consumes the CSC arrays directly. RHS, bus-ID, block lookup and solve
APIs continue to operate normally. Existing matrix mutation methods leave
prepared mode; subsequent NR validation requests a new plan. The pattern
installer copies and validates its input, including sorted unique rows and
scalar diagonals. Numerical publication invalidates numeric factors while
retaining compatible symbolic analysis.

## Cache validation

INrSolver.validateJMatrixCache() reports unchanged, numeric or structural data.
LfCalcNrEngine calls it **before choosing** rebuild, refactor or LU reuse. A
second guard protects direct nrStep() calls. Specialized solvers retain their
existing cache policy. Validation compares exact snapshots rather than relying
on notifications or a hash of bus counts.

| Change | Action |
| --- | --- |
| Voltage magnitude/angle | Read current voltages when the existing NR policy requests a Jacobian fill |
| Impedance/multiplier, tap, phase, branch charging or terminal shunt | Refresh affected branch coefficients and endpoint diagonals; prohibit LU reuse |
| Bus shunt, standard contributed load data/status/membership, load-curve settings or network base | Refresh affected coefficients; prohibit LU reuse |
| PV/PQ/reference identities, load equation mask, activity, endpoints, membership, adjacency, IDs or numbering | Rebuild indexed destinations and create a new matrix/factorization context |
| Network replacement, coordinate/backend change or unsupported model | Discard incompatible prepared state and use the appropriate existing assembler |

Tap updates refresh mutual **and self-admittance** contributions. Same-count
PV/PQ swaps invalidate the plan because bus identities and masks are checked.
Edits made with notifications suppressed are detected. Validation does not
consume a change: numeric invalidation persists until values are refilled.

The existing pure constant-Z contributed-load contract computes its diagonal
from a voltage-dependent load value. That diagonal is refreshed on each
requested fill. Standard P/I/Z and ZIP derivatives, including low-voltage
curves, continue to use the current assembler's load helper.

## Scope and verification

The first scope is the exact ordinary network, bus, branch and load classes,
polar coordinates and the Java KLU backend. Specialized solver subclasses,
other network/bus/branch/load implementations, function/exponential loads, load-PV equations, augmented
zero-impedance models, transformer impedance tables, rectangular coordinates
and other backends keep their existing assembly path. Standard controls that
continue to use the ordinary formulation remain in the existing NR/control
workflow and trigger the same validation when their data or masks change.

Production changes are in the sibling core checkout:

- algo/impl/solver/PreparedPolarJacobian.java (new).
- algo/impl/solver/DefaultNrSolver.java and algo/impl/calc/LfCalcNrEngine.java.
- algo/INrSolver.java, algo/LoadflowAlgorithm.java and algo/impl/LoadflowAlgorithmImpl.java.
- sparse/impl/klu/KLUSparseEqnMatrix2x2Impl.java and sparse/solver/klu/BaseKLUEqnDoubleSolver.java.

These paths are relative to ../core/ipss.core_EMF/src/main/java/com/interpss/core/.
There are eight production files in this integration, plus focused tests; the
additional interface and public-API routing changes remove the earlier bypass.
Unrelated checkout edits and installed Maven release artifacts were preserved.

PreparedPolarJacobianTest exercises coefficient and structure mutations,
reserved zeros, parallel branches, current-assembler entry parity, retained
symbolic analysis, RHS/inspection APIs, external matrix writes, muted edits,
network replacement, unsupported models and specialized subclasses. It checks
the actual reuse-policy decision and direct nrStep() guard. The public API tests
also compare complete solutions and generator allocation, actual Q limiting,
logging, observers, failure reporting and explicit backend selection.

The final gate is
ipss.test.plugin.core/target/prepared-jacobian-cached-coefficients-gate-20261030.log.
It ran 91 core and 85 plugin tests: **175 passed, one existing skip, zero
failures/errors**. The 37 focused Jacobian tests and 11 public API tests all
passed without skips. It uses bounded heaps and the paired isolated source reactor.
The final pre-commit combined component, hook, public API and workflow-log gate
also passed all 61 tests without skips; its log is
ipss.test.plugin.core/target/prepared-jacobian-commit-gate-20261030.log.
Performance evidence is retained separately under
ipss.test.plugin.core/target/prepared-jacobian-comparison-20261030-b/.
All repetitions and the earlier interrupted preliminary batch are retained.

## Measured results and remaining performance gap

Three fresh forks per implementation, pooled arithmetic means in milliseconds:

| Case | Existing assembler | Prepared hooks | RustPower Python KLU | Time saved vs existing | Prepared / RustPower |
| --- | ---: | ---: | ---: | ---: | ---: |
| IEEE 39 | 0.188241 | 0.155830 | 0.013396 | 17.2% | 11.63x |
| IEEE 118 | 0.695573 | 0.590438 | 0.029044 | 15.1% | 20.33x |
| PEGASE 9241 | 619.159230 | 425.726226 | 12.575510 | 31.2% | 33.85x |

Both InterPSS paths and RustPower retained 4/4/6 Newton steps. The prepared path
retained one plan per case/fork. Its scalar matrix sizes remain 78, 236 and
18,482, with 456, 1,106 and 130,858 structural entries, respectively. The
benchmark does not equate a single plan preparation with a single symbolic
analysis: the existing NR engine still controls symbolic/numeric solve actions.

There are **180,900 retained timed solves** across all three implementations.
The maximum independent InterPSS P/Q residual was 5.321e-8 pu; the maximum
complex-voltage error was 4.526e-9 pu. All 65 recorded checksums, 108 production
classpath fingerprints, measured source snapshots and focused test receipts
passed the final audit:
ipss.test.plugin.core/target/prepared-jacobian-completion-audit-20261030.json.

**This hook implementation does not meet the original 1.2x RustPower target.**
The target receipt records false, and the benchmark command returned nonzero
for that performance qualification. Accuracy, complete-sample and affinity
checks passed. The prior E batch's near-parity result belongs to a different
whole-solve implementation that also optimized mismatch evaluation and LU
workspace handling. Those optimizations are not supplied by Jacobian assembly
alone. Its source snapshots and results remain preserved; they must not be
reported as the performance of the current hook implementation.

## Reproduction

Run from the plugin root with Java 21 and the paired core checkout. Choose new
output/log names to preserve evidence:

    $env:MAVEN_OPTS='-Xmx2g'
    python tools/run_bounded.py --log ipss.test.plugin.core/target/jacobian-gate-new.log -- mvn -o -f tools/benchmark-reactor/pom.xml -Pisolated-verification "-Dverification.run.id=prepared-jacobian-20261030" -pl com.interpss:ipss.core.test,com.interpss:ipss.test.plugin.core -am test "-Dtest=PreparedPolarJacobianTest,PreparedNewtonLoadflowTest,KLUSparseEqn*Test,KLUFactoredRowsTest,Loadflow*Test,DefaultNr*Test,Matpower*Test,IEEE14*Test,IEEE9*Test,AclfZero*Test,SparseEqnSolver*Test" "-Dsurefire.failIfNoSpecifiedTests=false" "-DargLine=-Xmx2g"
    python tools/run_bounded.py --log ipss.test.plugin.core/target/jacobian-comparison-new.log -- target/rustpower-benchmark-20261030/venv/Scripts/python.exe -u tools/benchmark_prepared_newton.py --output ipss.test.plugin.core/target/jacobian-comparison-new --include-generic
    python tools/check_neutral_benchmark_language.py

The benchmark retains the earlier protocol: three forks per implementation,
CPU 2 / affinity 0x4, 50,000 warm-up and 10,000 timed small-case solves per fork,
50/100 PEGASE solves, fresh compensated flat starts, 1e-6 pu tolerance and no
outlier removal. The source and production classpath fingerprints are retained.
The target check still reports the original 1.2x criterion honestly, including
a nonzero exit status if this architecture does not meet it.
