# Reproducing the best two-case timings

The corrected source build measures **8.909746 s for Case 1 and 6.429334 s for
Case 2** on quiet logical CPU 6, including preparation and complete recording.
This recovers the requested under-nine-second / roughly-6.5-second performance.
Case 1 remains 0.313238 s (3.6%) above its exact historical median; Case 2 is
0.090650 s faster. The exact historical 8.596508 s / 6.519984 s median pair
has not been reproduced. Individual fast runs are not substituted for medians.

## Comparable measurements

Each row uses sixteen alternating warmups followed by ten measurements in the
same JVM. Five measurements use the optimized controller evaluator and five
use the control evaluator in the original alternating order. The table reports
the five optimized measurements. Compilation finishes before timing. No other
simulation or build runs concurrently with the clean cohorts.

| Configuration | Logical CPU | Output volume | Case 1 median | Case 2 median |
| --- | ---: | --- | ---: | ---: |
| Historical retained measurements | 0 | Historical location | 8.596508 s | 6.519984 s |
| Unchanged frozen executable, rerun | 0 | E: HDD | 9.375764 s | 6.951692 s |
| Source rebuild, historical compiler mode | 0 | E: HDD | 9.241236 s | 6.639965 s |
| Same rebuilt engine, direct SSD output | 0 | C: SSD | 9.3967 s | 6.7530 s |
| Same rebuilt engine, preferred performance core | 8 | C: SSD | 9.228841 s | 6.749377 s |
| Same rebuilt engine, quiet performance core | 6 | C: SSD | **8.909746 s** | **6.429334 s** |

The quiet-core Case 1 measurements span 8.833860–8.971779 s; Case 2 spans
6.215331–6.604295 s. CPU 6 was selected after sampling background load on
performance-core pairs; it is a workstation-specific placement choice, not a
portable assumption that logical CPU 6 is always fastest.

All runs use ten simulated seconds, a quarter-cycle network step, the pure Java
sparse solver, 1e-5 pu network voltage-correction tolerance, one ST1 local step,
and the saved fault and preparation settings. Every complete run matches the
protected full-network binary, selected-channel CSV and channel metadata.
All channel samples and event-side duplicates are retained. The timers include
parsing, power flow, initialization, simulation, recording and writer closure;
post-run comparison hashes are outside the timers.

An initial Case 1 rebuild cohort measured 9.398896 s but overlapped a diagnostic
compilation. It is retained as exploratory evidence and excluded from causal
comparisons. Its five timings, including the 12.125743 s outlier, remain in the
evidence. The corresponding initial Case 2 cohort measured 6.642168 s.

## What was missing from the reproduction recipe

The earlier source replay compiled main classes with `-g:none`; the historical
build used `-g`. For the installed JDK, this also changes lambda deduplication,
so removing debug metadata does not necessarily leave the same instructions.
The replay tool now defaults to `-g` and records the compiler mode. The old
mode remains available as `--compiler-debug none` for explicit diagnostics.

With the corrected compiler setting, 3,850 of 3,854 main classes match the
frozen executable after canonicalizing class files and removing debug
metadata. The four remaining differences are three power-flow preparation
classes and `DynamicParameterValidator`; the dynamic hot classes match. All
23 staged harness and fixture classes also match. The frozen runtime's other
146 classes were audited: they are test and benchmark helpers, not hidden
numerical-library replacements. Source builds use 105 pinned external
dependencies and no archived engine classes.

This is a real reproducibility correction, but the measured effect does not
explain the entire remaining timing gap. The previously recovered accelerated
network solver and helper remain committed in core
`46c8576e617fc0427dc42d3de7f9cafe64e38ff6`; plugin numerical sources remain at
the restored source point. No additional numerical changes were made here.

## Where the remaining time goes

| Case 1 measurement | Historical CPU 0 | Rebuilt CPU 0 | Rebuilt quiet CPU 6 |
| --- | ---: | ---: | ---: |
| Complete case wall time | 8.596508 s | 9.241236 s | 8.909746 s |
| Process CPU time | 8.328125 s | 8.703125 s | 8.578125 s |
| Setup and initialization | 0.334687 s | 0.373617 s | 0.366426 s |
| Simulation and recording | 8.255670 s | 8.866111 s | 8.519215 s |
| JIT compilation during measured run | 195 ms | 171 ms | 166 ms |
| Garbage collection during measured run | 114 ms | 97 ms | 102 ms |

These are medians of separate quantities and do not form an additive timing
breakdown. Case 1's wall-time gap is about 0.645 s. Process CPU time increased
by about 0.375 s; the remaining wall-time excess is about 0.270 s. Setup adds
only about 0.039 s. JIT compilation and garbage collection did not increase
enough to account for the gap. Case 2's process CPU median is effectively
unchanged: 6.281250 s historically versus 6.265625 s now.

The solver's recorded numerical settings and work counts match the historical
runs exactly, including 18,340 / 20,202 network iterations and
2,513,491 / 778,557 numerical-frame stages for Cases 1 / 2. Extra iterations
or missing controller acceleration therefore do not explain these timings.
The unchanged frozen executable also misses the historical pair on the
current host. This supports an execution-environment contribution rather than
a new source regression alone; it does not establish a specific hardware cause.

The target-directory junction now points to an E: hard disk, whereas C: is an
NVMe SSD. The controlled SSD-output cohorts did not improve the medians, so
storage relocation is not an established cause. CPU 8 is marked as a preferred
performance core, but it also failed to recover the pair. CPU 0 was observed
near 5.285 GHz without a reported performance-limit flag; the JDK version,
single-CPU affinity, heap size, garbage collector and numerical configuration
match. No injected Java-options environment variables were found.

The final affinity-only comparison did recover much of the gap: moving the
same rebuilt engine and direct SSD output from CPU 0 to quiet CPU 6 reduced
Case 1's median from 9.3967 s to 8.909746 s and Case 2's from 6.7530 s to
6.429334 s. This establishes a material CPU-placement effect on the current
host. It does not reconstruct CPU 0's execution conditions during the
historical best run. Case 1's remaining 0.313238 s historical gap is unresolved.
On CPU 6, process CPU time still exceeds the historical median by 0.250 s,
accounting for most of that remaining gap; measured JIT and garbage-collection
time are lower than historically.

The historical run did not capture CPU frequency over the measured window,
interrupt and scheduling history, hardware performance counters or JIT machine
code. Its exact remaining throughput advantage cannot be assigned to one of
those causes from the retained evidence. Claiming a specific culprit would go
beyond what these comparisons establish.

## Reproduction and retained evidence

For the quiet-core profile on this workstation, run sequentially with fresh
output and log names:

```powershell
python tools/run_bounded.py --log target/best-quiet-case1.log -- python tools/replay_best_source_checkpoint.py --case 1 --cpu 6 --output ipss.test.plugin.core/target/best-quiet-case1
python tools/run_bounded.py --log target/best-quiet-case2.log -- python tools/replay_best_source_checkpoint.py --case 2 --cpu 6 --output ipss.test.plugin.core/target/best-quiet-case2
```

The [source rollback record](best-source-point-rollback-2026-10-23.md) retains
the original CPU 0 protocol. Default replay still uses CPU 0 unless `--cpu` is
specified. It now uses the historical compiler mode and reports CPU time,
setup, simulation/output, JIT and garbage-collection measurements alongside the
complete case timer. The selected working replay records the quiet CPU 6
profile explicitly. Generated-output budgets remain enforced. A further two
complete runs per case validate the updated source-build CLI with that profile;
those checks do not contribute to the warmed medians.

Across twelve timing cohorts, including the explicitly marked exploratory
cohort, and four final CLI verification runs, all 316 complete recordings
match. This is a trajectory-parity check, not a count of independent timing
measurements. Selected-route and output-confinement construction checks pass.

The [reproduction index](checkpoints/best-timing-reproduction-2026-10-23.json)
pins raw cohort records, affinity checks, commands, compiler audits, host
observations and an executable snapshot of the corrected source build.
Generated duplicates are replaced with verified pointers only after their
JVMs finish. Supplied references and retained dependencies are preserved.
The retained evidence archive is about 43 MiB and the executable about 8.4 MiB.

Trajectory identity establishes checkpoint parity. Independent reference
accuracy remains unresolved and is not qualified by these timing results.
