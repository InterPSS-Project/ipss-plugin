# Shared live-device equations across power-flow methods

The fixed-base/live-device split now extends beyond prepared polar NR to the
shared positive-sequence power balance, generic polar/rectangular Jacobian,
second-derivative helper, GS iteration diagonal and FDNR matrix assembly.
Specialized ZBR and coordinated NR solvers inherit the common electrical rows
while retaining their existing auxiliary and control equations.

## Formulation and solver coverage

For an audited built-in static or dynamic bus, the shared equations evaluate:

```text
Yii_physical = Yii_base + j B_live
S_physical(V) = V conj(Y_base V) - j B_live |V|^2 + S_external
```

The base includes branch/transformer admittances, bus/terminal shunts, fixed
compensation banks and the existing impedance-load contribution. It is
assembled directly, without adding then subtracting a potentially large live B.
Switched-shunt and SVC physical service status selects live B; a control lock
does not disconnect an in-service device. Both device families can coexist with
fixed banks on the same bus.

| Method or consumer | Live-device treatment |
| --- | --- |
| Prepared polar NR | Existing indexed base cache, live B vector, mismatch and relative-voltage derivative. |
| Generic polar NR, full Newton and line-search trials | Common live Q in power balance; diagonal Q derivative includes `-2 B_live |V|²` for relative-voltage corrections. |
| Rectangular NR and full rectangular Jacobian | Common live Q; rectangular mismatch derivatives include `2 B_live Vx` and `2 B_live Vy` in Q rows, respecting PV constraint masks. |
| Augmented ZBR and coordinated NR | Inherit the shared electrical balance/Jacobian. Auxiliary flows, converter equations, co-solved control columns and active-set rules retain their existing ownership. |
| Native decoupled PQ and PQ→NR | Both half-step residuals, trial evaluations and the Newton handoff share the power balance. Existing B1/B11 approximations remain unchanged. |
| GS and single GS steps | Shared live mismatch; compose the effective diagonal from base plus current live B to retain the physical GS iteration map. |
| XB FDNR | Shared live residuals; explicitly compose B″ from fixed base and current live B. A B change invalidates Q factors, while compatible P factors remain reusable. |
| Second-derivative helper | Include the live device's quadratic Q curvature explicitly in polar and rectangular forms. |
| QA, generation allocation and public result calculations | Continue to observe total physical power from the shared balance. |

Consistency means one physical device contribution with the derivatives and
iteration matrices required by each method. GS and XB FDNR require the live
diagonal in their iteration operators; removing it would change those methods.
This extension does not add a prepared Jacobian cache to every specialized
solver. Generic paths reassemble base coefficients through their existing
lifecycle, while the prepared polar path retains its indexed cache.

Public `bus.yii()`, `acCurrentIntoNet()` and Y-matrix inspection retain the
complete physical representation. Dynamic Norton matrices keep physical shunts
in Y. Custom and three-phase bus subclasses retain their own physical Yii model
and do not receive a second live-device injection. The existing pure-impedance
load precedence is preserved. PV/PQ/reference masks and structural changes
continue through each solver's established rebuild policy.

## Validation

The focused gate passes **109 tests**, with no failures, errors or skips.
Nineteen new parameterized/component cases cover nine solver configurations
and ten device variants per configuration, including signed B, physical service
state, control locks, SVC changes, notification-suppressed writes, fixed-bank
changes, reconnection to the swing bus, removal and repeated solves.

Each solver is compared with an unsplit physical-Y custom-bus reference. The
reference preserves the original full-diagonal arithmetic through the custom
model fallback. Independent checks use `V * conjugate(acCurrentIntoNet())`,
central finite differences for reduced/full polar and rectangular Jacobians,
and second differences for Q curvature. Additional checks cover GS iteration
parity, FDNR factor counts and PV/PQ transitions, large-B base isolation,
custom Yii overrides, inactive buses and pure-impedance-load precedence.

The full nine-module run completes **7,629 tests: 7,522 passed, 91 skipped,
and 16 baseline failures/errors**. All 19 added cases pass. There are no new
failure identities, removed test identities or fork dumps compared with
`dynamic-pf-transfer-full-final-20261030`.

| Module | Cases | Passed | Skipped | Failures/errors |
| --- | ---: | ---: | ---: | ---: |
| Core | 3,252 | 3,251 | 0 | 1 |
| Plugin core | 457 | 453 | 4 | 0 |
| Three-phase plugin | 478 | 447 | 28 | 3 |
| Plugin integration | 3,442 | 3,371 | 59 | 12 |
| Total | 7,629 | 7,522 | 91 | 16 |

The retained failures comprise the existing DCLF assertion, three three-phase
fixture/type errors, two integration fixture/input errors and ten existing
integration assertions. Exact identities, exception types, messages and XML
hashes are retained in the local `test-audit.json`. The baseline comparison
uses every test identity, rather than comparing only aggregate counts.

The full run uses direct selection of every test class with extended tests
enabled. Only the suite-wrapper engine is excluded to prevent duplicates.
Maven continues after failures so every module runs; the audited XML results,
test identities and baseline failure comparison determine the result rather
than Maven's ignore-failures exit code.

This is a formulation-consistency and regression gate. No new runtime speedup
or performance-target result is claimed.

## Reproduction and evidence

```powershell
$env:JAVA_TOOL_OPTIONS='-Xmx2g'
python tools/run_bounded.py --log ipss.test.plugin.core/target/<unique-run>.log -- mvn -o -f tools/benchmark-reactor/pom.xml '-Pisolated-verification,extended-tests' '-Dverification.run.id=<unique-run>' '-Dtest=*' '-Dsurefire.excludeJUnit5Engines=junit-platform-suite' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.failure.ignore=true' test
```

Local evidence is retained under
`ipss.test.plugin.core/target/all-pf-live-device-20261030/`, including the
pre-change source snapshot, focused/full XML audits, source/runtime fingerprints
and the original failed fixture-compilation attempt. The full XML/runtime
directory is `.verification-build/all-pf-live-device-full-20261030/`.
Existing private references and unrelated network-copy/reset work are preserved.
Three unrelated plugin source files changed after validation (controller and
parser/catalog work). They are preserved and excluded from this feature
commit. The test results apply to the frozen runtime above; those later source
edits are not covered by this run. Their paths, timestamps and hashes are
recorded in the checkpoint.

Core implementation commit: `5309d648648378f7bec22226fe5ef6c8d80c52be`.

See the [machine-readable checkpoint](checkpoints/power-flow-live-devices-all-methods-2026-10-30.json)
and the core algorithm enhancement document
`ipss.core_EMF/docs/algo_impl/ac_load_flow_enhancements.md`.
