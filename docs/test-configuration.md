# Test configuration

Tests use JSON resources or explicit Java fixtures. Test code must not read or
change JVM system properties, including through `Boolean.getBoolean`,
`Integer.getInteger`, solver-selection wrappers, or JUnit property conditions.
`RepositoryTestConfigurationTest` scans every module's test sources to enforce
this rule.

## JSON run settings

Edit the section for the test class in its module's configuration file:

- `ipss.plugin.3phase/src/test/resources/test-config/ipss.plugin.3phase.json`
- `ipss.test.plugin.core/src/test/resources/test-config/ipss.test.plugin.core.json`

Setting names retain their previous names to make migration easy. They are JSON
keys now. For example, replace `-Dqsts.compare.steps=48` with
`"qsts.compare.steps": 48` in the `QstsLargeFeederComparisonExport` section.
Then select the test normally:

```bash
mvn -pl ipss.plugin.3phase test -Dtest=QstsLargeFeederComparisonExport
```

Maven options such as `-Dtest` and `-DskipTests` still select or control the build.
They do not configure test cases. Previous test-setting command lines in
historical reports must be translated to the matching JSON section before use.

`null` or an omitted key retains the test's fixture-specific fallback. This is
useful for optional export directories, external case paths, and CGMES floors
that vary by case. Paths resolve from the module directory, as existing fixtures
do. To use external data, set its case-root key explicitly. CGMES tests no longer
inspect `user.home`; the fallback base is `testData/external`, and the
`ipss.cgmes.cas.root`, `ipss.cgmes.cas24.root`, or `ipss.cgmes.relicap.root` setting
selects an external pack directly.

JSON is loaded from the test classpath when the class initializes. Re-run Maven
after editing the resource so it is copied to the test classpath. Missing files,
missing sections, and invalid typed settings fail with an explanatory error.
The reader is shared through the core plugin's test-scoped fixture JAR.

Large benchmarks and private diagnostics remain disabled by default. Enable
their existing flag in the corresponding JSON section; for example, set
`interpss.largeDclfTests` to `true` in each large test class you intend to run.
Tests with unavailable data still skip using their existing file assumptions.

## QSTS comparison and benchmark

`QstsLargeFeederComparisonExport` includes steps, feeder selection, master-file
override, output directory, mode, time-step size, tolerance, and control settings.
Its default output directory is `target/qsts-comparison`. Controls remain enabled
unless `qsts.compare.allowDisabledControls` explicitly allows a diagnostic run.

For the annual Ckt24 benchmark, update `QstsLargeFeederPerformanceBenchmark`:

```json
{
  "qsts.perf.case": "ckt24",
  "qsts.perf.masterFile": "master_ckt24_yearly_interpss.dss",
  "qsts.perf.mode": "YEARLY",
  "qsts.perf.stepSizeHours": 1.0,
  "qsts.perf.warmupSteps": 24,
  "qsts.perf.steps": 8760,
  "qsts.perf.repeats": 3,
  "qsts.perf.profile": false,
  "qsts.perf.tolerance": "1.0e-4",
  "qsts.perf.minIterations": 1,
  "qsts.perf.primitiveVoltageState": true,
  "qsts.perf.staticStateReuseEnabled": true,
  "sparseSolver": "java-klu"
}
```

Merge these keys into the existing section rather than replacing the whole file.
Profiling and static-state reuse are applied through explicit study settings.

For native runs, use `"sparseSolver": "klusolvex"` and set `nativeLibraryPath`
to the KLUSolveX library installed on the benchmark machine. The measured yearly
Ckt24 configuration uses Java KLU; the other large-feeder acceptance workloads
use KLUSolveX. Keep the same tolerance,
master, time-step size, and control settings on both engines. The tuned minimum
iteration setting still requires the normal convergence criterion to pass.
`qsts.perf.fixedPointProfile` enables detailed per-instance PF counters, and
`qsts.perf.flightRecording` optionally names a JVM flight-recording output file.
Leave both disabled during final timing runs.

For IEEE8500, `Master-InterPSS.dss` with `DAILY` and one-hour steps is a repeated
static operating point. `Master-InterPSS-QSTS-PV-Duty.dss` with `DUTY` and
`qsts.perf.stepSizeHours = 0.0002777777777777778` follows the one-second PV
profile; 8760 such steps represent 2 hours 26 minutes rather than an annual
load-profile study. Comparison exports expose matching `sparseSolver`,
`nativeLibraryPath`, `qsts.compare.minIterations`, and
`qsts.compare.primitiveVoltageState` settings.

QSTS prepares capacitor/regulator device references once per run while reading
live voltages and control states every step. Regulator tap changes update matrix
values without discarding stable topology. With primitive voltage state enabled,
`NONE` result sampling and `VOLTAGE_ONLY` output, QSTS synchronizes buses observed
by controls during the run and all bus voltages on exit. Full sampling, inverter
controls, and delayed control queues retain full voltage synchronization.

## Java fixtures and core APIs

Tests comparing solver modes use per-instance `DistributionPowerFlowOptions`.
Changing an algorithm's options clears its fixed-point cache. QSTS reads these
options from the algorithm actually used for the study.

`TestConfig.withOverrides` produces a separate immutable fixture. CGMES's static
comparison helpers use `CgmesComparisonFixture` scopes to apply case-specific
floors on the calling thread, with restoration on exit.

The sibling `core` library now provides `KLUSolverProfiler.withEnabled`,
`LfNrProfiler.withEnabled`, and `DclfContingencyConfig.setWoodburyMonitorBatchSize`.
The ACLF benchmark and batching parity test use these APIs. Build and install
that library locally before building the plugin against those APIs:

```bash
# From the sibling core checkout
mvn -pl ipss.core_EMF -am install -DskipTests
```

Production compatibility entry points still support legacy runtime properties.
Test sources use explicit configuration entry points.

## Verification

```bash
mvn -pl ipss.plugin.core test -Dtest=RepositoryTestConfigurationTest,TestConfigTest,CoreProfilingFixtureTest
mvn -pl ipss.plugin.3phase test -Dtest=OpenDssIeee13DailyQstsProfileTest,RegulatorSymbolicUpdatePerformanceTest,OpenDSSQstsFeederSmokeTest
mvn -pl ipss.test.plugin.core test -Dtest=DclfTransferPanelCacheTest,CgmesComparisonFixtureTest
```
