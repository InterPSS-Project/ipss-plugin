# Standard NR numerical integration — 2026-10-30

Measured integration and enhancement-document checkpoint: `0511b8afc37097c4febaabe5edcf226c987d7058`.

**The maintained calculator has been accelerated, but the 1.2x target remains unmet.**
The full three-fork public-API run produced ratios 1.250x, 1.348x and 1.214x.
The [portable checkpoint](checkpoints/standard-nr-integration-2026-10-30.json) records
this result. Earlier passing ratios 1.014x, 1.178x and 0.945x belong to the
[retired whole-solve experiment](rustpower-optimization-worklog-2026-10-30.md).
The [Jacobian-only checkpoint](prepared-polar-jacobian-2026-10-30.md) measured
11.63x, 20.33x and 33.85x. These are three different implementations.

## Maintained path and differences

`LoadflowAlgorithm.loadflow(false)` still calls the normal calculator and its
`LfCalcMethodRunner` Newton loop. `LfCalcIterOrchestrator` dispatches the ordinary
iteration body; `LfCalcNrEngine` selects rebuild/refactor/LU reuse and invokes
the existing matrix hooks. No production adapter invokes `PreparedNewtonSolver.solve()`.

| Work | Earlier numerical experiment | Maintained integration |
| --- | --- | --- |
| Iteration ownership | A private whole-solve loop | Existing calculator loop, termination reporting and failure handling |
| Electrical data | Cached admittance and primitive arrays | Prepared indexed model with exact boundary snapshots; full validation for controls/direct steps |
| Residual | Primitive network dot products | Existing mismatch hook uses primitive dot products and caches the evaluated RHS |
| Jacobian | Reduced angle/PQ-magnitude system | Public two-row bus API, owned CSC values, mask-specialized numerical edge loops and cached physical P/Q for diagonals |
| Linear solve | Decoded fixed-pivot LU workspaces | Same numerical component through the maintained JKLU backend; fresh pivoting factorization on rejected pivots |
| Repeated solves | Retained symbolic data | Compatible symbolic data retained; each call still starts a fresh nonlinear solve and refills numeric values |
| Voltage and results | Published after its private solve | Numerical voltage storage inside a certified ordinary solve; publication on every exit; existing allocation rules |

The standard API retains its full matrix coordinates and relative-magnitude
Newton variable. Symbolic analysis can view a bus-block ordering and maps its
permutations back to those public coordinates. Independent fixed equations are
handled cheaply inside LU. Public scalar/block inspection, mutable exposed row
views, RHS copies, panel solves and worker-owned solve contexts remain available.

The ordinary scope requires exact supported implementations, polar NR, disabled
adjustments/globalization, no model/configuration observers, no custom MW
controller and no relevant external-device/control state. Numerical voltage
storage additionally requires supported constant-power loads and disabled
iteration diagnostics. Logging/profiling, controllers, other load models and
direct NR-step calls retain the required current model publication/validation.

## Bottlenecks removed

- Repeated complex power calculations and object traversal during residual evaluation.
- Rebuilding symbolic analysis at iteration zero of each otherwise compatible solve.
- Packed factor decoding and transient RHS buffers during repeated solves.
- Repeated phasor calculations and load-derivative objects for constant-power models.
- Copies of every bus voltage for an HVDC rollback on AC-only networks.
- Voltage-history maps when adjustment controls are disabled.
- Formatting mismatch strings and collecting sparse metrics when their diagnostics are disabled.
- Temporary complex totals and stream allocations during generator-result allocation.

No previous solution is replayed. The normal convergence tolerance, bounded
reuse policy, voltage canonicalization and generator allocation/limit rules
remain in effect. Configured diagnostics remain observable.

## Mutation and fallback correctness

Exact validation precedes matrix-policy selection. Branch parameter changes
refresh mutual and terminal self-admittances; taps therefore update both.
PV/PQ/reference identities, membership, endpoints, activity or numbering changes
rebuild the prepared masks and structure. Structural zero slots remain allocated.
Muted edits are detected without relying on notification delivery.

Unsupported-model fallback preserves a pending structural invalidation before
clearing the prepared plan. Otherwise a mismatch fallback could leave an old LU
eligible for reuse. A regression exercises that ordering explicitly. Stable
constant-load caching still reads new generation and external injections each
solve and preserves authoritative contributed-load data.

## Controlled compensation correction after the measured checkpoint

Core commit `745ef2f40ca46574b38f23e35c8819fda8d393f4` corrects an invalidation gap found after the
measurement above. Yii includes effective switched-shunt/SVC/fixed-bank B, but
the prepared snapshot had not tracked those physical device contributions. The
stable-scope guard also omitted the separate SVC list.

The correction tracks effective in-service compensation, preserves pending
numeric Jacobian/LU invalidation, and excludes all compensation-bearing buses
from the unchanged-model scope. Control locks retain the committed physical B.
It preserves the existing total-Y formulation; a cached-base/live-device split
and its voltage-dependent injection derivatives remain a separate design step.

The same reproducer for switched shunt and SVC now returns NUMERIC with zero
mismatch/Jacobian error at the displayed precision. Its previous errors for a
B change of 0.1 at V=1.07 were 0.11449 pu and 0.22898, respectively. The focused
gate passed 322 tests, including 14 new device cases, with no failures/errors/skips.
Evidence is retained in `ipss.test.plugin.core/target/prepared-compensation-fix-20261030/`
and `ipss.test.plugin.core/target/prepared-compensation-cache-gate-20261030.log`.

The three-fork timings below still identify the earlier measured commit and
its frozen runtime. No new performance qualification is claimed for this fix.

## Full matched result

| Case | RustPower mean ms | Standard NR mean ms | InterPSS / RustPower | Further time reduction needed |
| --- | ---: | ---: | ---: | ---: |
| IEEE39 | 0.012497 | 0.015626 | 1.250x | 4.0% |
| IEEE118 | 0.029513 | 0.039782 | 1.348x | 11.0% |
| PEGASE9241 | 13.144641 | 15.953858 | 1.214x | 1.1% |

The criterion is the pooled arithmetic mean for **every** case at or below 1.2.
All three currently miss it. The last column expresses the additional runtime
reduction required at the measured RustPower means. No outliers were removed.
There were 120,600 timed solves across both implementations: 30,000 per small
case/runtime and 300 per large case/runtime. All observed iterations were 4/4/6.

Both runtimes used CPU 2 / affinity `0x4`, compensated flat starts before every
timed solve, `1e-6 pu` tolerance and maximum 20 iterations. Timing includes start
reset, public load flow, voltage publication and generator allocation. Independent
validation uses P at PV/PQ buses and Q at PQ buses; PV Q is solved output.
Maximum independent mismatch was `5.321e-8 pu`; maximum complex-voltage error
was `4.526e-9 pu`.

The final gate ran 332 core and 89 plugin tests: **420 passed, one existing skip,
zero failures/errors**. These include 55 prepared-model/assembly tests and 11
public-API tests, plus sparse solves, controls, HVDC, zero-impedance, import,
logging and profiling behavior. Mutation parity also exercises the numerical
workspace against the independent current assembler.

The audit checked all 56 retained evidence checksums, 108 frozen runtime
fingerprints, measured source snapshots and focused test receipts. Evidence:

- `ipss.test.plugin.core/target/standard-nr-integrated-comparison-20261030/`
- `ipss.test.plugin.core/target/standard-nr-integrated-final-gate-20261030.log`
- `ipss.test.plugin.core/target/standard-nr-integration-audit-20261030.json`
- `.verification-build/standard-nr-integrated-final-20261030/` (retain this measured runtime)

The measured working tree includes pre-existing unrelated core network-copy/reset
work. The scoped feature commit excludes it; this is not a clean-checkout timing
claim for that commit alone. Exact measured sources and runtime hashes are retained.

## Remaining target work

The integration gap is smaller, but completion requires further reduction of
small-case model/validation and iteration overhead, and repeatable end-to-end
improvement of IEEE118. A warmed standalone-kernel diagnostic measured about
32 microseconds on IEEE118 and 11.2 milliseconds on PEGASE9241; it excludes
normal API start/reset and workflow work and is not qualification evidence.
Exploratory JVM inlining flags did not establish a solution and were not used
for the matched result above.

Preserve exact invalidation and all public outputs while improving this data
flow. Any replacement of scans needs trustworthy change tracking that also
covers muted setters. New claims must rerun the full public API comparison;
a kernel microbenchmark or one passing pilot is insufficient.

## Reproduction

Use a fresh output directory and an isolated verification ID. Run from the plugin
root with Java 21 and the paired core checkout; keep heaps and logs bounded.
Pass the test report explicitly so the harness uses that exact verified classpath.

```powershell
$env:MAVEN_OPTS='-Xmx2g'
python tools/run_bounded.py --log ipss.test.plugin.core/target/standard-nr-gate-new.log -- mvn -o -f tools/benchmark-reactor/pom.xml -Pisolated-verification '-Dverification.run.id=standard-nr-new' -pl com.interpss:ipss.core.test,com.interpss:ipss.test.plugin.core -am test '-Dtest=FixedPatternLuTest,PreparedNewtonSolverTest,PreparedPolarJacobianTest,PreparedNewtonLoadflowTest,KLUSparseEqn*Test,KLUFactoredRowsTest,Loadflow*Test,*Nr*Test,Matpower*Test,IEEE14*Test,IEEE9*Test,AclfZero*Test,*Hvdc*Test' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xmx2g'
python tools/run_bounded.py --log ipss.test.plugin.core/target/standard-nr-comparison-new.log -- target/rustpower-benchmark-20261030/venv/Scripts/python.exe -u tools/benchmark_prepared_newton.py --test-report .verification-build/standard-nr-new/ipss.test.plugin.core/surefire-reports/TEST-org.interpss.core.aclf.PreparedNewtonLoadflowTest.xml --output ipss.test.plugin.core/target/standard-nr-comparison-new
```

A completed accuracy-valid comparison exits with status 1 when its performance
target is false. Inspect `target-verification.json`; do not reinterpret that as
passing qualification. Keep all samples and failed pilots.
