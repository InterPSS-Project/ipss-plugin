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

## Controller binding and state-storage screening

Two isolated prototypes preserve the ten ST1 substeps and the existing numerical
equations. The compact-state prototype uses a bound state array, direct block
storage access, one activation read per contiguous owner, and skips known no-op
projection callbacks. The controller-reference prototype instead starts from the
committed solver and binds controller and participant references for endpoint
evaluation, retaining the pre-step attachment checks and live activity flags.
Neither experiment includes the pending ST1 observation-cache changes.

Each process runs twelve full warmups and five measurements, with fresh setup,
all observations and writer closure included. These are screening results; JIT
compilation remains active and the timing ranges overlap.

| Experiment | Case | Median total | Range |
| --- | ---: | ---: | ---: |
| Compact-state control | 1 | 10.8346 s | 10.3384–11.3026 s |
| Compact-state candidate | 1 | 10.7918 s | 10.5834–11.2153 s |
| Compact-state candidate | 2 | 7.5624 s | 7.4705–8.0460 s |
| Controller-reference control | 1 | 10.8453 s | 10.2869–11.2545 s |
| Controller-reference candidate | 1 | 10.6415 s | 10.0610–10.9508 s |
| Controller-reference candidate | 2 | 7.7252 s | 7.4331–8.1409 s |

The controller-reference Case 1 difference is 0.2038 s (1.88%); compact-state
storage differs by only 0.0429 s (0.40%). There is no adjacent Case 2 control in
either screen. All 51 runs per experiment reproduce their retained observations
byte for byte. Neither prototype replaces the selected performance checkpoint.
Before production use, controller-reference binding needs an unchanged live
lookup fallback for custom machine accessor overrides. The eight-second target
and independent-reference accuracy requirements remain unfulfilled across cases.

The lifecycle checks exposed a separate core defect: rectangular-voltage
postprocessing attempted to cast a dynamic network to a narrower load-flow
network type. Core commit `e2ec1a5fd` uses the existing network's bus list instead;
voltage-conversion equations are unchanged. With that fix, all 19 existing solver
and ST1 response/lifecycle tests pass for both the baseline and the bound-reference
prototype. The compact-state configuration passes its 17 tests as well. This
repairs the previously reported reinitialization exceptions, not the outstanding
full-system reference mismatch. Timing runs use the frozen runtime without this
later repair. Separate validation ran during the first three control warmups,
before the measured runs.

The [screening evidence index](checkpoints/controller-binding-screen-2026-10-21.json)
retains prototype sources, commands, run statuses, timings and before/after test
reports. Verified identical generated recordings share storage with the retained
baseline; all paths and file contents remain available. Supplied reference files
are untouched.


## PSS2A zero-time stage screening

An isolated prototype specializes zero-time predictor evaluations of the exact
delay, filter and washout block classes. It preserves the existing arithmetic,
captured-input order, state/derivative storage and subsequent refresh passes.
Custom, compound and non-windup blocks retain their existing kernels. Physical
parameters, ST1 substeps, network interval and convergence tolerance are unchanged.

Thirty-six focused tests pass, including bitwise block-storage comparisons
through zero-time and nonzero predictor/corrector stages, zero-time blocks,
custom-block fallback and binding replacement. All 34 complete Case 1 recordings
match the retained baseline byte for byte. Both processes use one logical CPU,
twelve full warmups and five measurements including recording and writer closure.

| Variant | Median total | Range |
| --- | ---: | ---: |
| Control | 10.843971 s | 10.770075–11.376105 s |
| Zero-time prototype | 10.695996 s | 10.548275–11.156199 s |

The median reduction is 0.147974 seconds (1.36%). Timing ranges
overlap and compilation remains active during measurement. This screen does not
establish a reliable performance improvement; the prototype stays outside
production and does not replace the selected checkpoint. Case 2 was not timed in
this screen. The eight-second target and independent-reference accuracy remain
open. The [screening evidence index](checkpoints/pss-zero-step-screen-2026-10-21.json)
retains the source, tests, exact commands, timings and verified affinity records.
Identical generated recordings share retained storage; supplied references are
unchanged.

## Synchronous-machine Norton-source cache screening

An isolated prototype reuses the final Norton source for exact standard round-rotor
and salient-pole machines when internal voltage, conductance, susceptance, rotor
angle and current-base scaling retain identical bits. Terminal current, electrical
power and field-current inputs still refresh with live voltage. Custom machine
subclasses retain the ordinary dispatcher. Physical parameters, integration stages,
substeps and convergence tolerance are unchanged.

Thirty-four focused tests pass. Added tests compare cached and uncached machine
outputs, power, field current and integrated states through changing voltage,
base scaling, angle and reinitialization. A separate source-cache test exercises
every scalar key, signed zeros, nonfinite values and explicit invalidation.
All 34 complete Case 1 recordings match the retained baseline byte for byte.

| Variant | Median total | Range |
| --- | ---: | ---: |
| Control | 10.709744 s | 10.642337–11.222181 s |
| Norton-source cache | 10.802068 s | 10.716232–11.235897 s |

The candidate median is 0.092324 seconds (0.86%) slower, with overlapping ranges.
However, a subsequent eligibility review found that the prototype's exact-class
guard excludes the imported `IeeeVcRoundRotorMachine` and
`IeeeVcSalientPoleMachine` subclasses. The unit test exercises core machine
classes; it does not prove execution in the large imported case. This screen
therefore does **not** establish the cache's performance on the intended machines.
Its recordings and timings remain valid observations, but the earlier conclusion
that the intended cache showed no benefit is withdrawn. A corrected screen must
assert nonzero cache use in the candidate and zero use in its disabled control.

Both processes use one logical CPU, twelve complete warmups and five measurements,
including recording and writer closure. Compilation remains active during
measurement. The cache is not promoted into production. Case 2 was not timed in
this screen. Independent-reference accuracy and the eight-second target remain
unresolved.

The [screening evidence index](checkpoints/norton-source-screen-2026-10-21.json)
retains sources, commands, tests, timings, affinity records and recording hashes.
Identical generated recordings share retained storage; supplied references are
unchanged.

## Guarded ST1 substep-boundary reuse screening

The standard local ST1 path evaluates the graph at the end of one substep and
again at the start of the next. An initial prototype skipped the latter pass
whenever regulator time constant Ta was positive. Complete Cases 1 and 2 still
matched, but a stronger test with active rate feedback found a field-output
difference. The repeated pass can refresh derivative history; positive Ta alone
does not justify removing it. The failed test and initial source are retained.

The revised prototype additionally requires zero rate-feedback gain and zero
feedback state, standard scalar input equations, supported exact machine and
block types, ordinary limit objects and no machine/bus observers. Zero-Ta and
active-feedback models keep the original sequence. It retains all ten physical
substeps, field-publication order, convergence tolerance and network interval.

Thirty-four focused tests pass. They include bitwise block state, derivative,
input, output, local history and field comparisons over changing setpoints,
limit selectors, voltage, status history, three Ta values, two feedback gains,
and counts 1, 4, 10 and 128. Nonzero feedback state and late observer attachment
disable reuse. The new test exercises the optimization and its fallback, rather
than relying only on unchanged large-case output.

All 34 complete Case 1 recordings match the retained baseline. Every candidate
run reports 1,967,238 skipped redundant graph evaluations; every control reports
zero. Both arms use the same class overlay with the feature disabled for control,
one logical CPU, twelve warmups and five measurements including writer closure.

| Variant | Median total | Range |
| --- | ---: | ---: |
| Disabled control | 10.788260 s | 10.617502–11.216924 s |
| Guarded boundary reuse | 10.483388 s | 10.418873–11.289080 s |

The median reduction is 0.304873 seconds (2.83%). This is a promising
screen, with overlapping ranges and compilation still active during measurement.
Longer timing qualification and Case 2 timing remain outstanding before promotion.
The guarded final prototype has full-case recording evidence for Case 1; the
earlier broader prototype's two-case regression is retained separately and must
not be represented as final-prototype Case 2 verification. Independent-reference
accuracy, early Case 3/4 failures and the eight-second objective remain unresolved.

A separate full Case 1 audit confirms the preceding Norton-source screen had
zero eligible machines and zero cache reuses. Its attached machine inventory
contains 410 `IeeeVcRoundRotorMachine` and 25 `IeeeVcSalientPoleMachine` instances,
including inactive machines. Its complete recording still matches the baseline.
This confirms the eligibility defect and withdraws that screen's cache-performance
conclusion. Future cache qualification must exercise the imported subclasses and
assert nonzero reuse.

The [evidence index](checkpoints/st1-boundary-screen-2026-10-21.json) retains both
experiments, sources/classes, the counterexample, tests, commands, feature counters,
affinity records and full-recording hashes. Supplied references are unchanged.

## ST1 boundary reuse qualification and integration

The longer paired campaign used 32 alternating warmups followed by seven
control/candidate pairs per case. Pair order alternated. Both arms used the same
class overlay with reuse disabled for control, one logical CPU, a 1/240-second
network step, 1e-5 voltage-correction tolerance and ten ST1 substeps. Timings
include fresh parsing, preparation, initialization, all recording and writer
closure. Compilation and garbage collection were not subtracted.

| Case | Control median | Candidate median | Median paired reduction | Candidate faster |
| --- | ---: | ---: | ---: | ---: |
| 1 | 10.687032 s | 10.459400 s | 0.282008 s | 6/7 pairs |
| 2 | 7.526230 s | 7.433640 s | 0.142034 s | 6/7 pairs |

The ranges overlap and occasional compilation continues during measurement.
All 92 complete recordings match their baseline hashes, including event-side
samples. Every candidate Case 1 run skips 1,967,238 redundant graph evaluations;
every candidate Case 2 run skips 626,922. Controls report zero skips.

The integrated source adds two eligibility guards beyond the timed prototype:
the selector must retain its standard input expressions, and active saved
washout history must be zero. These guards preserve the fallback for customized
graphs and externally restored history. Thirty-nine isolated focused tests and
41 tests with the existing worktree prototypes pass. They compare all local
state/history and block inputs, outputs and derivatives, including nonzero
feedback, zero regulator time, observer changes, altered selectors and counts
1, 4, 10 and 128.

Final-source complete Cases 1 and 2 match every baseline recording byte and
retain the same skip counts. The paired timing numbers above belong to the
earlier guarded prototype; final-source audits establish compatibility and
feature use, not another warmed timing measurement. Cases 3 and 4 retain their
existing failure at 0.04583333333333333 seconds, with identical partial records
and failure messages. Those failed runs are not performance successes.

The feature is integrated only into the supported direct ST1 local path. It
preserves all physical substeps, equations, state advances and publication
boundaries. Other pending observation-cache and compact-state prototypes remain
unpromoted. The Case 1 eight-second target, early Case 3/4 failures and supplied
reference trajectory accuracy remain unresolved.

The [qualification index](checkpoints/st1-boundary-qualification-2026-10-21.json)
retains scripts, sources/classes, tests, commands, affinity, counters and hashes.
Full generated recordings remain identified dependencies rather than extra copies.
