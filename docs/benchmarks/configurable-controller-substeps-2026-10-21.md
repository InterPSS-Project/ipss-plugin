# Configurable controller integration substeps

The staggered solver now accepts an ST1-family local substep count, with a
default for the solver and optional overrides by machine ID. EXST1 and ESST1A
use this path. The unchanged default is ten local steps per network step.

```java
var solver = new PsseStaggeredEulerSolver(algorithm);
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
quarter-cycle network step, four local steps give 1.041667 ms and ten give
0.416667 ms. Network solves and other controller families keep their existing
integration schedule. This option applies to the staggered solver; the
default predictor/corrector solver is unchanged.

Both optimized and fallback ST1 paths use the resolved count. Physical and
stabilizer inputs retain their existing observation boundary; event-history
averaging, limiter handling, and field publication before the final algebraic
refresh are preserved. The original direct-integration API overload retains
ten steps. The new overload accepts an explicit count, and callers must keep
that count fixed for the lifetime of its derivative history.

Changing the solver configuration after initialization is rejected. Create a
new solver and initialize it when changing the count. Reinitializing an
existing solver retains its configured counts and rebuilds local history.

The [subinterval-model guidance](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Transient_Stability_Integration_Subinterval_Models.htm)
provides a useful per-model configuration pattern. This implementation retains
its own STORE recurrence and accepts ten explicitly; it does not adopt another
integration method or silently round a requested count to a power of two.

## Validation and limits

Focused solver and local-integration tests cover default preservation, a
per-machine override, counts 1/4/10/128 on both local paths, a repeated input
jump, invalid counts and targets, late mutation, reinitialization and the
existing independent first-step response checks. The local test class remains
registered in `CorePluginTestSuite`.

All 23 focused tests passed against the isolated change and again with the
pending worktree optimizations present. Four complete ten-second runs, Cases 1
and 2 at counts ten and four, reproduced every byte of the corresponding
retained full-network recording, selected-channel CSV and channel metadata.
The network step remained 1/240 second and voltage-correction tolerance 1e-5.
These were correctness runs, not warmed performance measurements. See the
[evidence index](checkpoints/configurable-controller-substeps-2026-10-21.json).

This setting enables controlled accuracy/performance experiments. It does not
make four steps an accepted replacement for ten. The earlier
[one- and four-step comparisons](st1-single-step-experiment-2026-10-21.md)
still show numerical differences, and full-network agreement with the supplied
reference remains unresolved.
