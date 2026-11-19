# Configurable controller integration substeps

The staggered solver now accepts an ST1-family local substep count, with a
default for the solver and optional overrides by machine ID. EXST1 and ESST1A
use this path. As of October 22, the default is **one local step per network
step**, following the user's acceptance of the plotted one-versus-ten-step
tradeoff. Ten steps remain available through `setSt1Substeps(10)`.

```java
var solver = new StaggeredEulerSolver(algorithm);
solver.setSt1Substeps(4);
solver.setSt1Substeps("Bus1-mach1", 10); // This machine keeps ten local steps.
algorithm.setSolver(solver);
algorithm.setSimuStepSec(1.0 / 240.0);
algorithm.initialization();
```

Set these options before initialization. `getSt1Substeps()` returns the default;
`getSt1Substeps(machineId)` returns the override or default. An override must
identify a machine with a supported ST1 exciter at initialization. Invalid
targets fail explicitly. Integers from 1 through 128 are accepted exactly;
one selects a single local step. Zero, negative counts and counts above 128
are rejected. The upper bound limits explicitly requested local work.

The local interval is the network step divided by the count. At 60 Hz and a
quarter-cycle network step, one local step gives 4.166667 ms, four give 1.041667 ms and ten give
0.416667 ms. Network solves and other controller families keep their existing
integration schedule. This option applies to the staggered solver; the
default predictor/corrector solver is unchanged.

Both optimized and fallback ST1 paths use the resolved count. Physical and
stabilizer inputs retain their existing observation boundary; event-history
averaging, limiter handling, and field publication before the final algebraic
refresh are preserved. The original direct-integration API overload uses
the shared one-step default. The count-taking overload accepts an explicit count, and callers must keep
that count fixed for the lifetime of its derivative history.

Changing the solver configuration after initialization is rejected. Create a
new solver and initialize it when changing the count. Reinitializing an
existing solver retains its configured counts and rebuilds local history.

The [subinterval-model guidance](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Transient_Stability_Integration_Subinterval_Models.htm)
provides a useful per-model configuration pattern. This implementation retains
its own STORE recurrence and accepts ten explicitly; it does not adopt another
integration method or silently round a requested count to a power of two.

## Validation and limits

Focused solver and local-integration tests cover the one-step default, explicit ten steps, a
per-machine override, counts 1/4/10/128 on both local paths, a repeated input
jump, invalid counts and targets, late mutation, reinitialization and the
existing independent first-step response checks. The local test class remains
registered in `CorePluginTestSuite`.

For the original configurable-count implementation, all 23 focused tests passed against the isolated change and again with the
pending worktree optimizations present. Four complete ten-second runs, Cases 1
and 2 at counts ten and four, reproduced every byte of the corresponding
retained full-network recording, selected-channel CSV and channel metadata.
The network step remained 1/240 second and voltage-correction tolerance 1e-5.
These were correctness runs, not warmed performance measurements. See the
[evidence index](checkpoints/configurable-controller-substeps-2026-10-21.json).

The one-step default is an explicitly accepted performance/accuracy tradeoff.
It does not establish agreement with the supplied reference. The earlier
one- and four-step comparisons (Core `docs/benchmarks/st1-single-step-experiment-2026-10-21.md`)
still show numerical differences, and full-network agreement with the supplied
reference remains unresolved. The historical ten-step reference-response tests
now select ten explicitly; their expected measurements and tolerances are unchanged.

The October 22 default change passed all 26 focused solver, local-integration
and fixed-traversal tests, both in isolation and with the existing pending
worktree optimizations. The default trajectory matches an explicit count of
one exactly; an explicit ten-step count and a ten-step machine override retain
their distinct matching trajectories. No new full-system accuracy or warmed
timing result is claimed for this default change. Historical benchmark
checkpoints retain their recorded counts and outputs. See the
[default-change evidence index](checkpoints/st1-single-default-2026-10-22.json).
