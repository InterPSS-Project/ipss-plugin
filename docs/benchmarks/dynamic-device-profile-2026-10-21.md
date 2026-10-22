# Dynamic device update profile

PSS2A is the largest individual controller model by aggregate update cost in
Cases 1 and 2. The combined EXST1/ESST1A family is larger still. IEEET1 is the
most expensive controller per instance, but its small population makes its
aggregate contribution much smaller.

## Measured attribution

The following numbers are mean exclusive update times from three profiled
ten-second runs after 32 full warmups. Nested stabilizer preparation is charged
to PSS2A, not counted again in its calling exciter. These diagnostic times are
**not uninstrumented production timings**: per-device timers perturb execution.

| Model | Case 1 devices | Case 1 attributed time | Case 2 devices | Case 2 attributed time | Case 1 mean per device |
| --- | ---: | ---: | ---: | ---: | ---: |
| PSS2A | 333 | 1.610 s | 103 | 0.477 s | 4.83 ms |
| EXST1 | 91 | 1.383 s | 29 | 0.445 s | 15.20 ms |
| ESST4B | 208 | 1.028 s | 64 | 0.312 s | 4.94 ms |
| ESST1A | 23 | 0.425 s | 7 | 0.124 s | 18.47 ms |
| REGCA1 bundle | 96 | 0.405 s | 96 | 0.352 s | 4.22 ms |
| GGOV1 | 287 | 0.404 s | 88 | 0.116 s | 1.41 ms |
| IEEEG1 | 26 | 0.331 s | 15 | 0.175 s | 12.75 ms |
| IEEET1 | 11 | 0.277 s | 3 | 0.072 s | 25.20 ms |
| HYGOV | 20 | 0.029 s | 0 | — | 1.44 ms |

The renewable bundle includes its electrical, plant and wind controls; it is
not a measurement of the converter alone. Its exported unit ID is not unique
across buses, so aggregate timings and object counts are valid, but its individual
device ranking is unavailable from this export.

Shared state-array capture/advance adds 1.109 s in Case 1 and 0.331 s in Case 2.
Synchronous-machine update regions account for 0.212 s and 0.068 s respectively.
Network current assembly, network solves, recording and initialization lie
outside these device timers and must not be inferred from this table.

The highest individual controller observations are IEEET1 at bus 2057/unit 1
in Case 1 (38.35 ms per run) and IEEET1 at bus 4026/unit 1 in Case 2 (31.52 ms).
These short regions are susceptible to sampling and interruption noise; the
model-level aggregates are the basis for prioritizing work.

## Hot paths and optimization target

Case 1 PSS2A spends 1.316 s in boundary updates and 0.294 s preparing its held
output for exciters. Case 2 spends 0.393 s and 0.084 s respectively. Main-thread
flight-recorder stacks identify repeated `input`, `y`, `prepare` and block-output
calls in this path. The first candidate replaces block-ID dispatch and generic
preparation traversal with straight-line Java equations for the exact standard
forward graph. Operand order, arithmetic order, capture order, all preparation
passes, block kernels and integration stages remain unchanged. Nonstandard
graphs retain the existing evaluator.

An additional experiment cached the algebraic-block classification at plan
construction. Its Case 1 screening median was 11.112 s, so it was rejected and
removed from the retained candidate. Live classification remains in place.

The five focused scalar-preparation and lead-lag tests pass. They cover
predictor/corrector states and derivatives, live parameters, zero-time blocks,
nonfinite recovery, custom-block fallback and stale-binding rejection.

## Uninstrumented screening

Each screening process uses twelve complete warmups and five measurements,
with fresh preparation and full recording on every repetition. The retained
scalar candidate is compared to the same baseline used for the profile.

| Case / variant | Median total | Range |
| --- | ---: | ---: |
| 1 / retained baseline | 10.9813 s | 10.8582–11.7377 s |
| 1 / scalar preparation | 10.8834 s | 10.4567–11.1692 s |
| 2 / scalar preparation | 7.8250 s | 7.4636–8.3292 s |

The Case 1 median difference is 0.0979 s (0.89%). Ranges overlap and JIT remains
active, so this is a provisional small improvement, not a demonstrated large
speedup. There is no adjacent Case 2 control in this screening; its number is
not a Case 2 speedup claim. All 51 repetitions complete and all recorded channels
match the retained baseline byte for byte. The prior fully warmed performance
checkpoint remains selected; these screenings do not replace it.

The scalar implementation and its focused tests are retained. The rejected
classification variant and its timing evidence are preserved separately.
The [scalar-preparation evidence index](checkpoints/pss-scalar-preparation-2026-10-21.json)
records both experiments, source hashes and test results. Recompiling the final
retained controller source produces the same four class files as the screened
scalar candidate. Its Case 3/4 checks retain the original failure at 0.045833 s
and byte-identical partial recordings.

## Reproduction and limits

The profiled baseline is plugin commit `3e1bf2d3c`, with its retained core and
classpath in `checkpoints/st1-generated-inputs-2026-10-21.json`. The provisional
ST1 observation-cache changes are excluded. Settings are one logical CPU,
1/240-second steps, 1e-5 pu dynamic voltage-correction tolerance, 1e-3 pu load-flow
tolerance, and full recording through writer closure. All 70 profile repetitions
complete. Their full observations match the retained baseline byte for byte.

Instrumented whole-case ranges are 15.592–15.798 s for Case 1 and 8.921–9.252 s
for Case 2. They cannot be compared as a speed regression against ordinary runs.
Flight-recorder samples also identify profiler overhead explicitly.

The existing independent-reference accuracy gap and the early dynamic failures
in Cases 3 and 4 remain unresolved. Equality to the retained executable is a
nonregression check, not independent-reference verification.

Local evidence is in `target/device-model-profile-20261021`: the source overlays,
protocol and hashes, commands, affinity records, all warm-run statuses,
per-instance counters, aggregate `device-summary.json`, and both flight recordings
and their stack summaries. The profiler is an isolated diagnostic overlay;
production device updates do not contain its timers.
The [profile evidence index](checkpoints/dynamic-device-profile-2026-10-21.json)
retains a compact private archive of those sources, counters, flight recordings
and run metadata, with hashes. It references the existing baseline archive
instead of copying the complete source or simulation-output trees.

## Follow-up execution sampling without per-device timers

A second Case 1 profile uses the committed scalar-preparation controller overlay,
32 complete warmups and three sampled runs, without any per-device timing calls.
It excludes both pending observation-cache and compact-state-update prototypes.
The network step, tolerance, single logical CPU and full recording are unchanged.
All 35 runs reproduce the retained binary observations byte for byte. The sampled
whole-case durations are 11.1457, 11.0184 and 10.7185 seconds; these include flight
recording overhead and are not a replacement performance checkpoint.

Of 1,763 main-thread execution samples, 711 (40.3%) are in network solution work,
208 (11.8%) in PSS2A, 205 (11.6%) in the ST1 family, 221 (12.5%) in machine-package
paths, 72 (4.1%) in ESST4B and 66 (3.7%) in renewable-model paths. These categories
are stack classifications, not exclusive stopwatch measurements. Machine-package
samples include controller ownership accessors and voltage-compensation helpers;
they must not all be attributed to machine differential equations. Network
classification takes precedence over model classes deeper in that stack.

Network leaf samples are concentrated in the factored row solve, current-injection
assembly and static-load evaluation. Controller-related leaf samples also include
machine ownership and controller lookup methods. Line-level samples can be
affected by JIT inlining and safepoints, so a method's sample count alone does not
establish its removable cost. Output recording remains included; the small count
classified directly as recording is not a complete wall-time I/O measurement.

This supports investigating both the network and controller paths. Reducing ST1
substep count addresses only one portion of the work and has not met the accuracy
requirements in the one- and four-step experiments. The
[sampling evidence index](checkpoints/case1-sampling-profile-2026-10-21.json)
retains the flight recording, source, commands, all statuses, affinity verification
and detailed stack summary.
