# Fixed-base/live-device split validation — 2026-10-30

The maintained ordinary polar NR path now keeps switched-shunt/SVC B out of its
cached fixed admittance and evaluates the live injection and voltage derivative
through the existing mismatch/Jacobian hooks. Public total Yii/Ybus and result
allocation remain unchanged. This is integrated into the standard NR lifecycle.

## Equations and invalidation

For positive capacitive B, `Y_total = Y_base + j diag(B_live)` and
`m_Q = Q_specified - Q_load - Q_external - Q_base(V) + B_live |V|²`.
The relative-magnitude Newton column includes `-2 B_live |V|²` in
`J = -dm/dx`. Both prepared residual paths and Jacobian paths use this split.
Total physical Q is retained separately for generator allocation.
`Q_load` denotes explicit mismatch load; impedance-load terms are already in the base.

The base is assembled directly, avoiding cancellation from `Yii - j B_live`.
It contains branches/transformers, terminal and bus shunts, fixed compensation
banks and existing impedance-load terms. Controlled B/service/list changes
refresh the live vector and invalidate numerical Jacobian/LU while retaining base
coefficients, CSC and compatible symbolic analysis. A certified numerical-only
adjustment selects refactor. PV/PQ/reference/membership/topology changes rebuild
structure, and HVDC updates retain their rebuild guard. Device control locks
preserve committed physical B.

Compensation-bearing buses retain full mutation validation. Specialized augmented
control solvers keep their existing generic equations. The public Yii model's
legacy pure-Z load precedence is preserved. This change does not introduce a
co-solved B state or change the existing control/limit equations.

## Full test results

**The full suite is not green. There are 16 distinct failing test
identities, all reproduced on the pre-change working-tree baseline; there are
no new failing identities in the final verified run.** Baseline includes the
existing unrelated core copy/reset work and the test fixture-directory correction.
Those unrelated source changes were retained and excluded from this feature.

| Module | Baseline tests | Final tests | Final failures | Final errors | Final skips |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ipss.core.test` | 3,212 | 3,219 | 1 | 0 | 0 |
| `ipss.plugin.core` | 457 | 457 | 0 | 0 | 4 |
| `ipss.plugin.3phase` | 478 | 478 | 0 | 3 | 28 |
| `ipss.test.plugin.core` | 3,440 | 3,441 | 10 | 2 | 59 |

These are Surefire's aggregate execution counts: 7,587 baseline
and 7,595 final. The final XML reports contain
7,595 unique module/class/test-name identities.
All test-bearing reactor modules completed. A separate fresh package build also
completed every module, including the sample module, which has no test source
tree. Extended tests were enabled and `-Dtest=*` included test classes with
nonstandard filenames. Existing disabled/conditional cases remain recorded as
skipped.

The initial run hit the configured 16 MiB console-capture limit while suite
wrappers repeated tests. The completed comparison selects every test class
directly and excludes only the `junit-platform-suite` engine's duplicate wrapper
executions. No test methods or comparison samples were removed. Baseline and
final compiled classes were retained; direct Surefire runs reference them through
separate report directories, excluding installed reactor binaries from the
classpath. The final changed production class fingerprints match the fresh
package build. The initial logs and stopped-run receipts remain available.

Maven used `maven.test.failure.ignore=true` so later modules still ran after
failures. Its successful reactor exit is **not** a passing-test claim. The XML
audit checks failures/errors independently and compares test identity and exception
type against baseline. The raw logs, XML receipts and skip reasons are retained
in `ipss.test.plugin.core\target\fixed-base-live-device-20261030\test-audit.json`.

| Module | Existing failing test identity | Failure type |
| --- | --- | --- |
| `ipss.core.test` | `com.interpss.test.core.ca.dclf.DclfMultiOutageTest1.multiOpenOutageSkipsPrunedIslandBranchFactors` | `org.opentest4j.AssertionFailedError` |
| `ipss.plugin.3phase` | `org.interpss.multiNet.trans_dist.IEEE9_3Phase_1PAC_2SubNet_test.test_IEEE9_addFeeder_1pac_Positive_Seq_Dstab` | `java.lang.ClassCastException` |
| `ipss.plugin.3phase` | `org.interpss.multiNet.unit.IEEE9_3Phase_1PAC_mnet_3ph3seq_test.test_multiNet_Dstab_IEEE9_1Feeder` | `java.lang.NullPointerException` |
| `ipss.plugin.3phase` | `org.interpss.multiNet.unit.investigate_3PHSubNetYabc.test_phaseShift_SeqEquiv_IEEE9Bus_1port` | `com.interpss.common.exp.InterpssException` |
| `ipss.test.plugin.core` | `org.interpss.core.adapter.psse.raw.aclf.RealEDTestCases.testCase1` | `com.interpss.common.exp.InterpssException` |
| `ipss.test.plugin.core` | `org.interpss.core.adapter.psse.raw.aclf.RoseHulman_UserTestCases.odm_testCase` | `java.lang.NullPointerException` |
| `ipss.test.plugin.core` | `org.interpss.core.dstab.EndpointAlignedStateMonitorTest.fullSolverVoltageAndMachineSpeedMatchSynchronousStateSnapshotsAtFaultEdges` | `org.opentest4j.AssertionFailedError` |
| `ipss.test.plugin.core` | `org.interpss.core.dstab.Reecc1NativeConformanceTest.allSevenStatesCommandsAndNetworkBoundaryFollowTheNativeFault` | `org.opentest4j.AssertionFailedError` |
| `ipss.test.plugin.core` | `org.interpss.core.dstab.Texas2kSixCaseDynamicSmokeTest.allSixCasesRideThroughThreeCycleFault` | `org.opentest4j.AssertionFailedError` |
| `ipss.test.plugin.core` | `org.interpss.core.dstab.Texas2kSixCaseDynamicSmokeTest.allSixCasesRideThroughGeneratorTrip` | `org.opentest4j.AssertionFailedError` |
| `ipss.test.plugin.core` | `org.interpss.plugin.piecewise.IEEE14TestAclfNetPiesewise.testCase1` | `org.opentest4j.AssertionFailedError` |
| `ipss.test.plugin.core` | `org.interpss.plugin.piecewise.IEEE14TestAclfNetPiesewise.testCase2` | `org.opentest4j.AssertionFailedError` |
| `ipss.test.plugin.core` | `org.interpss.plugin.piecewise.IEEE14TestAclfNetPiesewise.testCase4` | `org.opentest4j.AssertionFailedError` |
| `ipss.test.plugin.core` | `org.interpss.plugin.piecewise.IEEE14TestAclfNetPiesewise.testCase2_1` | `org.opentest4j.AssertionFailedError` |
| `ipss.test.plugin.core` | `org.interpss.plugin.piecewise.IEEE14TestAclfNetPiesewise.testCase2_2` | `org.opentest4j.AssertionFailedError` |
| `ipss.test.plugin.core` | `org.interpss.plugin.piecewise.IEEE14TestAclfNetPiesewise.testCase4_1` | `org.opentest4j.AssertionFailedError` |

The focused core gate passed **148 tests** without failures/errors/skips,
including 21 compensation cases. Coverage includes notification-suppressed B
changes, service/list/reconnection changes, control locks, rollback, fixed-bank
and tap/base refresh, signed-B finite differences at non-unit voltage, direct
base construction with very large live B, total Yii, shared devices, retained
numerical workspace, cache-policy ordering and symbolic retention.

All **12 public plugin NR tests** also passed. The new integration case compares
prepared and legacy solvers across repeated live-B changes, service changes and
a PV-to-PQ transition; it verifies voltages and contributed generator results
with shared SVC/shunt devices at PQ/PV buses and shunts at the swing bus.

## Reproduction and retained evidence

Use Java 21 and the locally installed dependencies. From the plugin checkout:

```powershell
$env:MAVEN_OPTS='-Xmx2g'
$env:JAVA_TOOL_OPTIONS='-Xmx2g'
python tools/run_bounded.py --log ipss.test.plugin.core/target/<unique>.log -- mvn -o -f tools/benchmark-reactor/pom.xml '-Pisolated-verification,extended-tests' '-Dverification.run.id=<unique>' '-Dtest=*' '-Dsurefire.excludeJUnit5Engines=junit-platform-suite' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.failure.ignore=true' test
```

The core test working directory is now `${project.basedir}/..`, preserving its
core-root-relative fixture paths when invoked from the paired reactor. Normal
standalone core builds retain the same fixture root. `JAVA_TOOL_OPTIONS` bounds
the test heaps while preserving the configured Mockito agent.

Retain these directories and logs:

- `.verification-build/fixed-base-live-device-baseline-direct-20261030/`
- `.verification-build/fixed-base-live-device-verified-direct-20261030/`
- `.verification-build/fixed-base-live-device-baseline-20261030/`
- `.verification-build/fixed-base-live-device-verified-20261030/`
- `.verification-build/fixed-base-live-device-build-20261030/`
- `ipss.test.plugin.core/target/fixed-base-live-device-full-baseline-20261030.log`
- `ipss.test.plugin.core/target/fixed-base-live-device-full-verified-20261030.log`
- `ipss.test.plugin.core/target/fixed-base-live-device-full-baseline-direct-20261030.log`
- `ipss.test.plugin.core/target/fixed-base-live-device-full-verified-direct-20261030.log`
- `ipss.test.plugin.core/target/fixed-base-live-device-build-20261030.log`
- `ipss.test.plugin.core/target/fixed-base-live-device-focused-20261030.log`
- `ipss.test.plugin.core/target/fixed-base-live-device-plugin-focused-20261030.log`
- `ipss.test.plugin.core/target/fixed-base-live-device-20261030/`

The [checkpoint](checkpoints/fixed-base-live-device-2026-10-30.json) records source
and runtime fingerprints, aggregate counts, failure identities and the full audit
checksum. Plugin report classpaths were checked to reference the paired verified
core classes. The retained `run_frozen_tests.ps1` records the direct-run classpath
and engine configuration. Earlier measured benchmark runtimes/evidence were not
overwritten.

No performance benchmark was rerun for this correctness change. The historical
[standard-NR comparison](standard-nr-integration-2026-10-30.md) still misses the
1.2x criterion; this report makes no new speedup or performance-completion claim.
