# Full repository validation: merge gate failed

Follow-up: the [compatibility repair](controller-compatibility-repair-2026-10-23.md)
resolves the compilation and general-contract failures below. Full core testing
now passes; three previously documented plugin accuracy gates still fail.
This page retains the original failed-build evidence.

The requested full-build gate did not pass. Neither repository was merged into
its main branch (`master`), and no milestone commits or release tags were
created. The existing performance checkpoint and backup refs remain intact.
Numerical sources, assertions and test selection were not changed.

## Results

| Repository/module | Result |
| --- | --- |
| Core parent and engine library | Fresh source build and local installation succeeded |
| Core test module | Test compilation failed; no core tests executed |
| Plugin core | 435 tests reported: 430 passed, 1 failed, 4 skipped |
| Three-phase plugin | 468 tests reported: 440 passed, 0 failed, 28 skipped |
| Plugin integration-test module | Test compilation failed; its tests did not execute |
| Sample module | Build succeeded; no tests discovered |

The plugin collection run reports 903 tests in total, including 32 skips and
one failing assertion. These counts exclude the integration tests that could
not compile. They also exclude duplicate results from the initial fail-fast
reactor run. Neither full repository suite passed.

## Blocking issues

1. Core's `IndexedVoltageRecordTableTest`, `RevisionVoltageValidationTest` and
   `ScalarVoltageScanTest` reference `IndexedVoltageRecordTable`, which the
   source rollback removed. Fresh test compilation cannot resolve that class.
2. Plugin integration tests and fixtures still call removed controller and
   solver APIs. Diagnostics span nine Java source files, including
   `CmlSolverRuntimePlanTest`, `Pss2aCompiledStageTest`,
   `St1CompiledLocalPlanTest`, the three ST4B optimization tests,
   `StaggeredVoltageCoordinatesTest`, `Texas2kNumericalOptions` and
   `Texas2kReferenceRunnerReportingTest`. Examples include
   `setCompiledLocalSt1Enabled`, `setCompiledControllerGraphsEnabled`,
   `setUseRectangularNetworkVoltage` and `getCompiledLocalAdvanceCount`.
3. `RepositoryTestConfigurationTest.repositoryTestsDoNotUseGlobalSettings`
   fails because four controller tests mutate or inspect JVM-wide settings:
   `PsseSt1LocalIntegrationTest`, `St1BoundaryReuseTest`,
   `St1FixedScheduleTest` and `Pss2aNumericFrameTest`.

The source-only rollback left newer tests and APIs out of sync. Earlier timing
replays rebuilt all production sources but used explicitly pinned benchmark
fixtures; they did not compile the entire current test tree. Their complete
recording matches therefore did not establish full-reactor compatibility.

Before merging, reconcile the removed implementations with their retained
tests and fixtures, replace global test settings with explicit instance
configuration, and rerun both full reactors. The failing tests and guard were
not disabled or weakened to obtain a passing result.

The subsequent [voltage-history review](voltage-history-test-relevance-2026-10-23.md)
distinguishes obsolete helper-specific tests from independent solver contracts.
It recommends keeping the fast path rather than restoring the deleted helper
solely for compilation, and records a separate behavioral comparison against
the earlier solver.

## Execution and evidence

Both builds used Java 21, cached Maven dependencies, fresh isolated output
directories, a 2 GiB test heap and the bounded-output runner. The core test
command retained its Mockito Java agent. The plugin run enabled
`extended-tests`. Existing test annotations account for the reported skips.

The initial plugin reactor stopped downstream execution after its first
assertion failure. A second collection run used Maven's test-failure collection
mode so downstream modules could run; an outer aggregator returned failure on
any assertion, compilation error or Maven failure. Its recorded
`merge_gate_passed` value is `false`. Maven module success in that collection
mode is not treated as evidence that its assertions passed.

Core used a temporary isolated-verification profile to preserve old build
evidence. The exact patch is retained in the private evidence archive. Those
temporary tracked configuration edits were restored after the run; its new
build outputs were moved into the plugin's ignored verification directory.
Both restored source branches and both main-branch heads remain unchanged.

The [validation index](checkpoints/milestone-full-validation-2026-10-23.json)
pins bounded logs and statuses, all generated Surefire XML reports, the
aggregate failure record, compilation diagnostics, the temporary core profile
patch and the tested revisions. Supplied references were preserved.
