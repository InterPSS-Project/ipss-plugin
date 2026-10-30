# Power-flow enhancements applied to dynamic simulation

The maintained path now prepares ordinary polar load flow on eligible dynamic
network containers and reuses complex CSC destinations during numerical network
refactorization. It also closes two invalidation gaps: whole-row replacement
invalidates factors, and explicit factorization advances the operator revision
even when edits arrived through public mutable rows.

This is an integration and correctness improvement. The matched timings below
do not establish a general speedup or satisfy the separate load-flow 1.2x target.

## What transfers

| Mechanism | Maintained dynamic application |
| --- | --- |
| Prepared polar residual/Jacobian with indexed buses, branches and cached coefficients | Static load flow before dynamic initialization, on audited built-in positive-sequence types. The usual model/control restrictions and generic fallback remain. |
| Separate sparse structure from numerical coefficients | Cache row-entry-to-CSC destinations, check exact live row structure, and refill numerical values without rebuilding column counts, indices or hashes. |
| Validate before factor reuse | Match complete analyzed CSC pointers/indices, including deliberate hash collisions and transferred symbolic hints. Advance numerical operator epochs on explicit refactorization. |
| Fixed-base/live-device split | Applies to static power-flow preparation. Physical switched shunts and SVC admittances remain in the dynamic Norton matrix. A physical switch updates the matrix and invalidates its numerical factors. |
| Primitive current buffers, decoded triangular solves and endpoint reuse | Already implemented in the shared dynamic engine. Their existing qualification and profile selection remain in force. |

Moving a physical dynamic shunt into a lagged current injection would change
the network fixed-point iteration. This change preserves the physical Norton
formulation, modified-Euler stages, event ordering, tolerances and histories.
The qualified auxiliary converter compensation transformation remains separate.

The configured SVC and switched-shunt factories previously accepted base bus
interfaces but cast the network and remote target to static-only types. They
now use the declared base interfaces, so dynamic containers support the same
local/remote construction and PV/PQ transition rules.

Eligible initialization uses exact built-in dynamic network/bus/branch/load
types. Initialized networks, custom/three-phase types and unsupported static
models retain generic assembly. Programmatic networks with no low-voltage
configuration preserve no-adjustment semantics. Dynamic initialization
invalidates an existing prepared static plan.

`LoadflowAlgorithm.setPreparedNewtonEnabled(false)` selects generic preparation
per algorithm. `getJkluSolver().setCscPatternReuseEnabled(false)` disables CSC
destination reuse for that matrix; replacements own independent settings.
Direct mutable-row edits still require marking dirty or explicit refactorization
before solving, consistent with the existing API.

## Regression validation

The complete source reactor finished all nine modules with extended tests,
direct selection of all test classes, and the suite wrapper engine excluded to
avoid duplicate execution. XML results were audited against the preceding
fixed-base/live-device run; Maven's ignore-failures exit code was not treated
as a passing test result.

| Module | Cases | Passed | Skipped | Failures/errors |
| --- | ---: | ---: | ---: | ---: |
| Core | 3,233 | 3,232 | 0 | 1 |
| Plugin core | 457 | 453 | 4 | 0 |
| Three-phase plugin | 478 | 447 | 28 | 3 |
| Plugin integration | 3,442 | 3,371 | 59 | 12 |
| Total | 7,610 | 7,503 | 91 | 16 |

All 15 added cases pass. There are no new failing cases, removed test identities
or fork dumps. The same 16 baseline failures/errors remain: one DCLF assertion,
three three-phase fixture/type errors, two integration input/fixture errors,
and ten existing integration assertions across endpoint monitoring, controller
conformance, large-case smoke tests and piecewise load-flow tests. The exact
case identities and messages are retained in `test-audit.json`.

New core tests cover prepared/generic public load flow, residual/Jacobian parity,
voltage/electrical/control/topology mutations, initialization invalidation,
custom-model fallback, configured local/remote compensation devices, known
complex solutions, mutable-row replacement/reordering, dimension changes,
zero/null slots, failed preparation recovery, explicit refactor epochs,
transferred symbolic hints, opt-out and a real CSC hash collision.

The coupled nine-bus test records every bus complex voltage/frequency, every
machine angle/speed/Pe/Pm/Efd and registered controller named state. It retains
both fault-event sides, switches physical shunt B, verifies the complete
post-clearing admittance matrix, asserts prepared-path coverage and observes
actual CSC numerical refill. The comparison bound is 1e-8 in native units.

## Matched complete-run timing

| Case | OFF median / range (s) | ON median / range (s) | ON/OFF median |
| --- | ---: | ---: | ---: |
| Nine-bus | 0.013181 / 0.006344–0.070892 | 0.012029 / 0.006532–0.077367 | 0.913 |
| 6,717-bus | 20.172036 / 19.569234–20.518654 | 20.704526 / 19.317061–25.924693 | 1.026 |

Timing ranges overlap on both cases. All measured invocations, including residual compilation, are retained in the checkpoint. These observations do not isolate a causal runtime benefit.

OFF and ON are two configurations of the same maintained build. OFF uses
generic static preparation and ordinary CSC conversion; ON requests prepared
static load flow and numerical destination reuse. The OFF conversion also
constructs destination metadata so reuse can be re-enabled later. This is not
a timed comparison against a previous-release binary.

The nine-bus case uses 32 complete warmups per arm and 15 measured pairs. The
large case uses four complete warmups per arm and five measured pairs. Order
alternates within successive pairs in the same JVM. Every invocation imports
a fresh network. The timer includes import, power flow, dynamic initialization,
controller preparation, stepping, complete selected recording, exports and
writer closure. Subsequent integrity hashing, comparison and verified sharing
of identical generated files are excluded. All samples, compilation time and
process/main-thread CPU time are retained without outlier removal.

OS affinity is verified before and after execution on logical CPU 6 (mask 64).
Java 21 uses `-Xmx2g`, SerialGC, `-Xbatch` and one active JVM processor. The
existing large-case optimization profile is identical in both arms. No builds
or intrusive profiling ran during the timing batch.

The 6,717-bus case uses its existing coordinated-control/zero-impedance NR solver,
so both arms correctly fall back from prepared polar NR. Its dynamic trace
contains all bus voltage magnitudes and the existing selected machine channels;
only three rotor-angle channels are recorded. This validates the shared complex
network adapter on that profile, not prepared-NR acceleration on that case.
It retains 26,803 network iterations and three compensated-operator
factorizations per invocation. CSC conversion occurs at factorization, so it
does not dominate the thousands of subsequent network solves. Existing packed
iteration and endpoint reuse remain the relevant mechanisms for that work.

## Numerical evidence and limits

The nine-bus study retains 94 complete runs, each with 43 samples and 72 channels. The maximum OFF/ON absolute channel difference is 3.77476e-15, below the 1e-8 integration-test bound; physical/solver timestamps and sample kinds match exactly. Per-mode traces are stable across repeated fresh imports.

All 18 large-case runs retain 2403 samples and 6732 channels, including both fault-event sides. Their complete binary traces are byte-identical across both modes and match the retained historical SHA-256 `858c877c21e8c75917ac57444479272952a1e4817fac707f62ff377ea5f8d3a8`. All 6717 initial bus voltage/angle, generation/load P/Q and compensation-B rows also match exactly. No recorded trajectory difference or iteration-count change was introduced.

Measured compilation time (ms), kept inside the timer, is recorded per invocation in the checkpoint. The large-case OFF values are [127, 611, 441, 125, 12]; ON values are [569, 486, 103, 185, 45].

Internal OFF/ON parity and agreement with a retained historical trace are
distinct from independent reference acceptance. Full large-case reference
qualification remains open. The existing 123 buses outside the final 2% recovery
band remain an observed trajectory result, not an optimization acceptance test.
No gains, time shifts, filtering, altered tolerances, integration changes or
channel reductions were used to improve agreement or runtime.

Two preliminary attempts are retained. The first completed the small batch
and four large warmup invocations, then stopped because Windows file identity
was unavailable through the initial storage-accounting method, which counted
verified hardlinks repeatedly. Its short-case timings included substantial
compilation; none are silently merged with the revised warmup cohort. The
second stopped during small-case warmup when sandboxed hardlink creation was
denied. The completed study uses exact file-identity checks and the required
filesystem access. Complete generated traces are shared only after full SHA-256
verification; recording density and event-side duplicates are unchanged.

## Reproduction and retained evidence

```powershell
$env:JAVA_TOOL_OPTIONS='-Xmx2g'
python tools/run_bounded.py --log ipss.test.plugin.core/target/<unique-test>.log -- mvn -o -f tools/benchmark-reactor/pom.xml '-Pisolated-verification,extended-tests' '-Dverification.run.id=<unique-test>' '-Dtest=*' '-Dsurefire.excludeJUnit5Engines=junit-platform-suite' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.failure.ignore=true' test
python tools/benchmarks/run_dynamic_pf_transfer.py --input ipss.test.plugin.core/testData/private/texas7k --output ipss.test.plugin.core/target/<unique-study> --classpath-report .verification-build/<unique-test>/ipss.test.plugin.core/surefire-reports/TEST-org.interpss.core.dstab.DynamicPowerFlowTransferTest.xml --cpu 6 --warmups 4 --measurements 5 --small-warmups 32 --small-measurements 15
```

Audit XML rather than interpreting the ignore-failures build result as green.
Inputs remain private. Logs and exports retain the repository's output limits,
512 MiB study/file budgets and 2 GiB free-space reserve.

Evidence root:
`ipss.test.plugin.core/target/dynamic-pf-transfer-20261030/`.
It retains the initial source snapshot, failed attempts, full test audit,
completed `matched-study-qualified/` configuration, all runs, timing/compilation
records, traces, initial power-flow tables, numerical comparison, affinity,
loaded-class/dependency/directory hashes and source patches. The final benchmark
driver was compiled after a storage-accounting/warmup correction and prepended
as a benchmark-only directory; production classes are those from the full test
run. Both directories are fingerprinted. The final nine-module package build passes.
A comparison of all 7,076 class files against the full-test build finds only
the expected benchmark-driver change; production and test-fixture classes
match exactly. Runtime source patches also preserve
the unrelated pre-existing network-copy/reset work present during validation;
that work is excluded from this feature's commits.

Core implementation commit: `fcd07b033d2684c09ab9bcc1c53b69013bc2128c`. The plugin harness,
regression, launcher and report are committed together with this document.

See the [machine-readable checkpoint](checkpoints/dynamic-power-flow-transfer-2026-10-30.json)
and the core design note
`ipss.core_EMF/docs/algo_impl/dynamic_simulation_power_flow_transfer.md`.
