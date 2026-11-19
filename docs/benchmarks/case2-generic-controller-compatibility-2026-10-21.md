# Case 2 generic controller compatibility

The generic controller lifecycle contracts have been restored alongside the
recovered direct ST1, ST4 and PSS2A kernels. The seven measured complete-case
times after 32 warmups were 7.570759, 7.572756, 7.542933, 7.965914, 7.627552,
7.542238 and 7.720636 seconds. The median is **7.572756 seconds**; all seven
measurements are below eight seconds. This preserves the performance target
for this Case 2 run, but does not establish a speedup over the previous session.

Fresh preparation, initialization and full recording are included. The run uses
one verified logical processor, `1e-5` complex-voltage correction tolerance,
quarter-cycle steps and ten simulated seconds. All 2,405 samples and 4,810
channels, including event-side duplicates, retain the canonical recording hash:
`123b1cce4ad4d4087e7dc294b47031bb1bfc52436dd086d588e531d5936f675f`.
Two preliminary complete audit cases also match. This is observation equivalence
to the retained baseline; independent-reference accuracy remains unresolved.

## Restored contracts

Core commit `fd9d0e515a1c0829963b5525ee50ea38117bf05d` restores four generic CML
files from the saved pre-rollback branch. They provide optional compiled-stage
evaluation, explicit lifecycle phases, nested output scheduling, prepared-output
reuse and compound-block eligibility checks. The recovered direct model kernels
and fast network path remain in place. No test assertions were removed or weakened.

The four existing test classes execute **19 tests, all passing**:

- `CmlCompiledStageLifecycleTest`
- `CmlNestedOutputScheduleTest`
- `CmlOrderedObservationTest`
- `CmlPreparedOutputReuseTest`

They cover stage ordering and cleanup, nested preparation, live inputs and
parameters, unsupported structures, observation failures, invalidation and
recompilation, and state/history equality. A subsequent clean build of all core
sources produced 2,373 class files byte-identical to the effective core classes
used for the tests and timing; no expected classes were missing or extra.

## Remaining qualification

The full core test compilation remains incomplete. After including the test
module's 52 main-source fixtures and declared Mockito dependencies, it reports
75 compiler errors across eight network test files. The missing contracts cover
indexed voltage history, membership tracking/direct iteration, buffered current
assembly, efficient voltage publication and scalar static-load controls.
Those errors are API references, not 75 executed test failures. Earlier diagnostic
commands omitted fixtures and produced additional setup errors; their raw logs
are retained but are not treated as regression counts.

Network compatibility, plugin controller compatibility, applicable system gates
and independent-reference accuracy still need work. The active goal remains open;
this checkpoint is not a production-release qualification.

## Rebuild

```powershell
python tools/run_bounded.py --log target/generic-contract-source-build.log -- python tools/build_case2_source_checkpoint.py --checkpoint docs/benchmarks/checkpoints/case2-generic-controller-compatibility-2026-10-21.json --output target/generic-contract-source-build
```

Use new output names for later builds. The [checkpoint index](checkpoints/case2-generic-controller-compatibility-2026-10-21.json)
identifies a source-only archive, dependency verification, exact revisions,
all timing samples, focused-test results and the remaining compile errors.
The original [source restoration checkpoint](case2-source-restoration-2026-10-21.md)
and both pre-rollback stashes remain available.
