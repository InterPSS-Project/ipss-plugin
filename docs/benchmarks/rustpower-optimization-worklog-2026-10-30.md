# Prepared AC load-flow optimization — 2026-10-30

Latest maintained-path result: [standard NR integration](standard-nr-integration-2026-10-30.md).
This page records its earlier implementation and measurements.

**Historical whole-solve experiment.** The working source now uses prepared
Jacobian assembly through the existing NR hooks, described in the
[integration report](prepared-polar-jacobian-2026-10-30.md). Its timing must be
measured separately. The E batch below describes the retained source snapshots,
not the subsequent hook implementation. The private whole-solve adapter has
been retired; its measured source remains in that batch's evidence directory.

The target for this experiment was a pooled mean InterPSS time **no greater than 1.2 times
RustPower's local Python KLU time on every published case**. The original
16–32x comparison remains in [the baseline report](rustpower-loadflow-2026-10-30.md).
Earlier 2x and 1.5x targets were superseded. **The final E batch meets this
pooled-mean criterion on all three cases.** IEEE 118 is close to the boundary:
two individual fork ratios were approximately 1.23x, so these measurements do
not establish a per-fork or per-machine 1.2x guarantee.

## Why the original path was slower

Profiling the original PEGASE six-step solve found approximately 37.9 ms in
mismatch evaluation, 34.8 ms in Jacobian construction and 68.3 ms in the sparse
solver, within 199.1 ms total. It performed four symbolic analyses, four full
factorizations and one numeric refactor per solve. Object traversal, temporary
complex values, a larger equation system and repeated sparse preparation all
contributed. These intrusive phase counters are diagnostic, not final timings.

The inspected RustPower source at
[3eedbb13485a58c586b00fc07cff4c969a945d0c](https://github.com/chengts95/rustpower/tree/3eedbb13485a58c586b00fc07cff4c969a945d0c)
orders PQ/PV/reference buses, fills a reduced CSC Jacobian directly, reuses
primitive buffers and retains symbolic and numeric factorization workspaces.
That checkout declares 0.5.4; the published table and measured Python wheel
are 0.5.2. Source inspection does not prove the wheel has every later detail.

## Maintained implementation

The optimization runs through the ordinary LoadflowAlgorithm.loadflow(false)
API in the sibling ../core checkout. There is no duplicate numerical
implementation in the plugin module.

- PreparedNewtonSolver owns copied admittance, PQ/PV/reference ordering, a
  reduced CSC Jacobian, primitive workspaces and explicit failure results.
  Every call solves from its supplied voltages and fresh injections.
- FixedPatternLu uses separate integer and numerical L/U arrays derived from
  pivoting KLU, including block-triangular coupling, scaling and fresh pivoting
  after a failed fixed pivot. Symbolic analysis is retained only for the same
  prepared structure.
- PreparedPolarLoadflow integrates network input, cache invalidation, voltage
  publication, termination reports and generator allocation. Changed bus-role
  identities rebuild even when PV/PQ counts remain equal. Branch/topology
  changes and shunt replacements also rebuild. Full initialization explicitly
  invalidates the context after batch edits with model notifications suppressed.
- LoadflowAlgorithm and LoadflowAlgorithmImpl provide enable/disable and
  diagnostic accessors, lifecycle integration and the generic fallback.
- IAclfBusFunc and BaseAclfBusImpl provide a generator-Q allocation overload
  accepting calculated injection while retaining existing allocation rules.

These seven files are under ../core/ipss.core_EMF/src/main/java/com/interpss/core/.
The core starting commit was 730d52fa7f414fd051b025436c0f954dad995623.
Unrelated existing network-copy changes were preserved. Paired builds use an
isolated source reactor; installed Maven release jars were not replaced.

Eligible networks are ordinary polar NR networks using Java KLU, constant-power
loads and disabled adjustments. Custom buses and observers, nonlinear loads,
active controls, requested iteration logging, other sparse backends and other
unsupported configurations retain the generic workflow. Contributing load
records remain authoritative, including distributed generation and status
changes. Cached calculated injection is used only after convergence.

Numeric factors may be reused for at most two consecutive corrections after
small voltage-magnitude corrections and strong mismatch contraction. Each new
solve begins with a fresh numeric Jacobian/refactor; this does not reuse a
previous solution. Nonlinear P/Q convergence is checked every pass. Optional
linear-residual diagnostics are available separately. The measured cases retain
4/4/6 iterations.

## PV to PQ and structural changes

PV-to-PQ conversion releases the voltage magnitude and adds its Q equation.
The reduced Jacobian dimension, 2*npq + npv, increases by one per conversion.
Physical Ybus need not change, but the reduced equation pattern, permutation
and factorization do.

RustPower's optional
[QLimPlugin](https://github.com/chengts95/rustpower/blob/3eedbb13485a58c586b00fc07cff4c969a945d0c/src/basic/ecs/powerflow/qlim.rs)
checks Q after a converged AC pass, combines in-service generator limits at a
bus, clamps violations, changes PV to PQ and emits NodeTypeChangeEvent.
Its [structure updater](https://github.com/chengts95/rustpower/blob/3eedbb13485a58c586b00fc07cff4c969a945d0c/src/basic/ecs/powerflow/structure_update.rs)
clears Newton/solver caches, repartitions and continues from previous voltages
in the new order. Fixed sparse structure means fixed between structural changes.
The published numerical-core timing excludes Q-limit control passes.

The InterPSS prepared path retains the generic adjustment workflow when controls
are enabled. An actual Q-limit test verifies voltage-target release and Q clamp;
a separate test verifies same-count PV/PQ role changes invalidate the cache.

## Matched measurement protocol

tools/benchmark_prepared_newton.py compiles the API runner; the paired source
reactor must already have compiled and passed tests. It obtains the production
classpath from the test receipt and removes test classes/jars and empty entries.
The model, compensated flat starts, tolerance (1e-6 pu), 20-step limit and
independent reference checks match the baseline. Timers include voltage reset,
the public solve, voltage publication and generator allocation.

Windows topology selects the second performance core before timing. Both
runtimes use its first logical processor, CPU 2 / affinity mask 0x4. The driver
checks inherited child affinity during execution and before/after each process.
Three fresh forks per runtime alternate order. Each small case uses 50,000
warm-up and 10,000 timed solves per fork; PEGASE uses 50 warm-up and 100 timed
solves. All repetitions are retained without outlier filtering. Qualification
uses pooled arithmetic means, not a selected best fork.

Each output retains environment versions, production classpath hashes, snapshots
of the seven core sources and benchmark drivers, process/affinity receipts,
all raw timings and voltages, original Ybus/Sbus/reference arrays, independent
validation, sparse-cache counters, target verification and SHA-256 checksums.

The final batch, rustpower-pinned-comparison-20261030-e, measured:

| Case | RustPower mean ms | InterPSS mean ms | InterPSS / RustPower |
| --- | ---: | ---: | ---: |
| IEEE 39 | 0.012685 | 0.012867 | 1.014 |
| IEEE 118 | 0.030016 | 0.035368 | 1.178 |
| PEGASE 9241 | 12.519188 | 11.835981 | 0.945 |

Its largest independent P/Q residual was 5.307e-8 pu and complex-voltage error
4.494e-9 pu. Each case prepared/analyzed/factored once per fork across repeated solves;
there were no pivot fallbacks. There are 120,600 total timed solves across
both runtimes and all cases. The receipt has target_met: true.

Per-fork time ratios, in execution order:

| Case | Fork 0 | Fork 1 | Fork 2 |
| --- | ---: | ---: | ---: |
| IEEE 39 | 0.945 | 1.067 | 1.040 |
| IEEE 118 | 1.230 | 1.090 | 1.228 |
| PEGASE 9241 | 0.919 | 0.997 | 0.920 |

IEEE 118's RustPower fork means were 0.028541, 0.032713 and 0.028796 ms;
InterPSS means were 0.035096, 0.035646 and 0.035361 ms. Its pooled median ratio
was 0.0334 / 0.0280 = 1.193, but the prescribed criterion uses arithmetic
means. The pooled mean includes the slower RustPower fork and every outlier.
This spread is a practical limitation of the result.

Earlier C and D candidates missed IEEE 118 at 1.208x and 1.244x, respectively.
Their evidence and failed qualification receipts remain preserved. E followed
a code change and its regression gate; it was not a rerun of an unchanged
candidate until a passing sample appeared.

The final candidate evaluates mismatch with row-oriented admittance dot products
instead of scattered current updates, avoids saved-RHS copies when linear
diagnostics are disabled and avoids parsing the default reuse limit.
All use the same equations and convergence tolerance. No approximate
trigonometric evaluation or reduced output sampling was introduced.

## Verification and reproduction

The commands below describe the historical experiment. Re-running the current
working-tree drivers measures the subsequent hook integration. Reproducing E
requires its retained core sources and benchmark-driver snapshots in an
isolated checkout; do not interpret a run of the latest sources as an E rerun.

FixedPatternLuTest covers scaled block-triangular systems over 100 numeric
updates, invalid pivots and repivot recovery. PreparedNewtonSolverTest checks
independent equations, fresh loads, cache counts, invalid inputs, iteration
exhaustion, nonfinite intermediates and signed-zero starts.
PreparedNewtonLoadflowTest compares the public API with the generic path for
fresh loads/distributed generation, same-count PV/PQ identity swaps, shunts,
admittance/topology edits, allocation/limits, actual Q controls, backend selection,
logging, observers and initialization after muted edits.

The row-mismatch gate ran 23 core and 85 plugin tests: **107 passed, one existing
skip, zero failures/errors**. All 17 newly added focused tests passed without
skips. Log: ipss.test.plugin.core/target/rustpower-row-mismatch-gate-20261030.log.
The final completion audit, ipss.test.plugin.core/target/rustpower-completion-audit-20261030.json,
verified all 47 retained checksums, all 108 runtime classpath fingerprints,
the measured source snapshots against the current sources and the test receipts.

From the plugin root, with Java 21 and the paired core checkout:

    $env:MAVEN_OPTS='-Xmx2g'
    python tools/run_bounded.py --log ipss.test.plugin.core/target/prepared-gate-new.log -- mvn -o -f tools/benchmark-reactor/pom.xml -Pisolated-verification "-Dverification.run.id=rustpower-fast-20261030" -pl com.interpss:ipss.core.test,com.interpss:ipss.test.plugin.core -am test "-Dtest=FixedPatternLuTest,PreparedNewtonSolverTest,PreparedNewtonLoadflowTest,Matpower*Test,IEEE14*Test,IEEE9*Test,AclfZero*Test,Loadflow*Test" "-Dsurefire.failIfNoSpecifiedTests=false" "-DargLine=-Xmx2g"
    python tools/run_bounded.py --log ipss.test.plugin.core/target/prepared-comparison-new.log -- target/rustpower-benchmark-20261030/venv/Scripts/python.exe -u tools/benchmark_prepared_newton.py --output ipss.test.plugin.core/target/prepared-comparison-new
    python tools/check_neutral_benchmark_language.py

Choose fresh output names. The Python environment is the pinned baseline one.
Logs, failed experiments and earlier comparisons remain under
ipss.test.plugin.core/target/ and are not silently replaced or cleaned up.
