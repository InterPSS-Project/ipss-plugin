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

## Imported-machine Norton-source cache screen

The corrected prototype adds an explicit eligibility hook to the core machine
and opts in the two final imported synchronous-machine classes. It does not
introduce a core-to-plugin dependency or opt arbitrary subclasses in. The
voltage-dependent terminal-current and electrical-power updates still execute
on every call. Only the final internal-source conversion can reuse its result,
keyed by exact internal voltage, conductance, susceptance, angle and current
base multiplier.

Forty-two focused tests pass, including imported round-rotor and salient-pole
models, changing terminal voltages, state advances, base multipliers, cache
disable/re-enable and reinitialization. Core scalar tests cover signed zeros,
extreme values and non-finite inputs. Controls assert zero reuse; candidates
assert positive reuse. Case 1 exercises 333 active synchronous machines and
12,860,500 reuses per completed candidate run. The previous inactive screen's
performance conclusion remains withdrawn.

Both cases use twelve alternating warmups and five measured pairs, alternating
which arm runs first in each pair. Both arms include the integrated guarded ST1
optimization, identical class overlays, one logical CPU, the quarter-cycle
network step, 1e-5 tolerance and full recording. No measurements or compilation
time are removed. All 44 complete recordings match their retained baseline.

| Case | Disabled control median | Cache median | Median paired reduction | Cache faster |
| --- | ---: | ---: | ---: | ---: |
| 1 | 10.490026 s | 10.678659 s | -0.091580 s | 1/5 |
| 2 | 7.569275 s | 7.695792 s | -0.201854 s | 2/5 |

The cache is not promoted: Case 1 is slower in four of five measured pairs and
has a higher median. This screen does not isolate which part of the extra cache
key/dispatch machinery accounts for the cost. Positive reuse counts alone are
insufficient evidence of a speedup. The implementation remains an isolated
prototype; the committed ST1 improvement is retained.

The [evidence index](checkpoints/imported-norton-source-screen-2026-10-21.json)
preserves sources/classes, tests, commands, raw timings, feature counters,
affinity and full-recording dependencies. Independent-reference accuracy and
the full eight-second objective remain open.

## Static-load work counts and bounded voltage-norm screen

A full Case 1 instrumented audit counted 33,269,630 static-load eligibility
checks, 1,126 cache rebuilds, zero eligibility fallbacks and 33,269,922 static
current-injection calls. Its complete recordings match the baseline. Thus the
existing classification cache is working; repeated reclassification does not
explain the sampled eligibility-method presence. These are work counts, not
exclusive time attribution or an uninstrumented timing result.

A separate convergence prototype skips a complex magnitude when both absolute
components are at most half the largest correction already observed. It retains
voltage-history publication and calculates potentially leading corrections
normally. The component norm is unchanged. It reports 50,700,905 skipped
magnitudes per complete candidate Case 1 run; controls report zero.

The initial prototype passed 43 focused tests and all 44 full recording checks
in the paired screen. Both cases used twelve alternating warmups and five
measured pairs, alternating pair order, one logical CPU, a quarter-cycle network
interval, 1e-5 tolerance, ten ST1 substeps and the committed ST1 boundary reuse.
Fresh parsing, preparation, initialization and complete recording are included.
Compilation and garbage collection remain included in every measurement.

| Case | Disabled control median | Candidate median | Median paired reduction | Candidate faster |
| --- | ---: | ---: | ---: | ---: |
| 1 | 10.487917 s | 10.405829 s | 0.121926 s | 4/5 |
| 2 | 7.505778 s | 7.521618 s | -0.022328 s | 1/5 |

An additional regression exposes a subnormal counterexample: rounding half of
three minimum-subnormal units upward to two units permits skipping a tied
magnitude, which can retain the wrong leading bus when visits are reversed.
The guard now requires the half-maximum to be normal and finite. This preserves
the strict geometric separation from the existing maximum. Forty-four tests
then pass, including the retained failing-before/passing-after regression,
200,000 randomized bounds, exhaustive small subnormal combinations, tolerance
boundaries, minimum iteration counts, both norms, custom complex magnitudes,
non-finite values and reversed visitation. Complete guarded Cases 1 and 2 have
unchanged recordings and the same skip counts as the timed prototype.

The optimization remains unpromoted. The initial screen's Case 1 reduction is
small and Case 2 has no gain; ranges overlap and compilation persists. The
guarded source has compatibility audits, not a new warmed timing qualification.
Production convergence behavior, tolerance, integration and recording remain
unchanged. A larger reduction must come from device-update cost or fewer
network iterations, with any changed iteration strategy checked against the
supplied trajectories. The eight-second objective and accuracy remain open.

The [evidence index](checkpoints/bounded-voltage-norm-2026-10-21.json) retains
sources/classes, counts, tests, the numerical counterexample, raw timings,
affinity and full-recording dependencies without copying the large recordings.

## Controller dispatch and output-refresh screens, October 22

Two isolated experiments use the deferred-publication network-acceleration
prototype as their common baseline. Both retain one logical CPU, a 1/240-second
network step, 1e-5 voltage-correction tolerance, ten ST1 substeps and complete
recording. Both arms include the extended pre-initialization parameter checks.
They are performance screens on a prototype whose full-system reference
accuracy remains unresolved, not production or reference-acceptance results.

The PSS2A experiment gives each standard block input a typed call site, avoiding
the shared polymorphic setter. It keeps the ordered arithmetic, live reads,
binding checks, compound-filter propagation and custom-block fallback. All 31
focused tests pass, including bitwise state, derivative and output comparisons.

Each case uses twelve alternating warmups and five measured pairs, alternating
pair order. Timings include fresh parsing, preparation, initialization, all
recording and writer closure; compilation and garbage collection are included.
All 44 complete recordings match the retained baseline byte for byte.

| PSS2A typed-input screen | Control median, s | Candidate median, s | Median paired reduction, s | Candidate faster |
| --- | ---: | ---: | ---: | ---: |
| Case 1 | 10.060274 | 9.990163 | 0.074512 | 4/5 |
| Case 2 | 6.668631 | 6.823393 | -0.164355 | 2/5 |

The Case 1 improvement is small and Case 2 has no demonstrated gain. This
experiment is not promoted.

A separate ST1 audit tested whether upstream derivatives and saved history
were exactly zero at local-step entry. None of 273,828 Case 1 calls or 86,472
Case 2 calls met the complete guarded eligibility condition. Skipping upstream
state updates on an assumed equilibrium would therefore be unjustified.

A second audit instead checked the final repeated algebraic preparation pass.
Under the existing standard-graph, positive-Ta, zero-feedback conditions, none
of the eight block inputs changed in 2,404,402 Case 1 evaluations or 766,238
Case 2 evaluations. Both audits retained identical full recordings. These are
input-refresh observations, not evidence that the upstream states are constant.

The ST1 prototype skips only that final preparation pass while the guarded
local integration scope is active. It preserves every substep, state update,
history update and output publication. Active feedback, nonzero feedback state
or history, changed selectors and observed/custom objects retain the ordinary
path. All 43 focused tests pass, including counts 1, 4, 10 and 128, changing
limits and inputs, reactivation and discontinuities.

The same timing protocol produces the following results. Candidate runs skip
the audited number of refresh passes; controls skip zero. All 44 recordings
are identical, including network iteration counts and event-side samples.

| ST1 output-refresh screen | Control median, s | Candidate median, s | Median paired reduction, s | Candidate faster |
| --- | ---: | ---: | ---: | ---: |
| Case 1 | 10.186788 | 10.136256 | 0.214022 | 3/5 |
| Case 2 | 6.692323 | 6.842452 | -0.150130 | 2/5 |

Ranges overlap substantially and compilation continues during measurement.
These results do not establish a consistent improvement; the prototype remains
unpromoted. A longer warmup is needed before interpreting differences this small.
Cross-screen absolute times do not isolate the effect of either optimization.

Cases 3 and 4 with the ST1 prototype still fail at 0.045833333 seconds, with
identical partial recordings. They are not completed ten-second benchmarks.
The eight-second target for Case 1 and independent trajectory accuracy remain
open. Production defaults and the unrelated pending performance work are unchanged.

The [evidence index](checkpoints/controller-refresh-screens-2026-10-22.json)
retains the prototypes, tests, audit counts, raw paired timings, affinity,
failure prefixes and full-recording hashes. Large generated recordings remain
in the dedicated external artifact store; supplied references are unchanged.

## Endpoint derivative audit, October 22

An isolated audit checks whether the evaluation at the start of a step repeats
the stored values from the previous network endpoint. It uses the same
deferred-publication network prototype, extended parameter validation, 1e-5
tolerance, quarter-cycle interval and ten ST1 substeps as the preceding screens.
No evaluation is skipped. The instrumentation reads stored states, derivatives,
block inputs, machine power and field voltage; it avoids output getters that
could refresh algebraic values. Renewable internal states are not covered.

| Audit | Case 1 | Case 2 |
| --- | ---: | ---: |
| Reusable-endpoint evaluations checked | 2,400 | 2,400 |
| Scalar probes per evaluation | 42,844 | 13,420 |
| Evaluations with every probe unchanged | 0 | 0 |
| Machines with recurring PSS2A input/rate changes | 208 | 64 |
| Stored state-value changes during evaluation | 0 | 0 |

The recurring changes are in the second PSS2A input path. The inspected
bus-1050 example selects electrical power (input code 3), not bus frequency.
Machine electrical evaluation follows stabilizer evaluation, so the next
initial evaluation can refresh the stabilizer from the newer machine power.
These nonzero differences rule out removing the complete evaluation as
redundant. A proposed explanation based on bus-frequency updates was corrected
after checking the selector and sampled values.

Other observed field changes occur only during startup, except six Case 2
changes in bus 5263's ST1 washout derivative. A targeted second audit shows all
six are changes between positive and negative zero. They do not explain the
full-system trajectory error. This observation is not permission to discard
signed-zero behavior in a bitwise regression.

Both initial audit runs and the completed targeted Case 2 run reproduce the
retained full-network recordings, selected-channel CSVs and channel metadata
byte for byte. The first targeted attempt completed numerical work but failed
when serializing an unavailable initial-frequency marker as numeric NaN. The
corrected attempt represents that marker as text; the failed attempt is retained
as an instrumentation failure, not a dynamic convergence failure.

These runs are numerical audits, not warmed timing measurements. Production
code and integration order are unchanged. Selective reuse would need explicit
dependency and lifecycle guards, including startup, events, input or parameter
changes, activation and observers; unchanged values in these two cases alone
do not prove a general cache is safe. Independent reference accuracy and the
eight-second objective remain open.

The [audit evidence index](checkpoints/endpoint-derivative-audit-2026-10-22.json)
retains the source overlays, commands, raw probe counts, sampled values,
attempt classifications and complete-recording hashes.

## Current-path phase profile, October 22

A new Case 1 profile uses the deferred-publication network prototype, extended
parameter validation, one logical CPU, 1e-5 voltage-correction tolerance,
quarter-cycle steps, ten ST1 substeps and complete recording. Twelve fresh-case
warmups precede four sampled runs. The requested sample period is 2 ms; the
2,173 captured main-thread samples must not be interpreted as exact phase times.
Compiler inlining and sampling bias can put substantial apparent cost on a
method that does little work.

Two separate timing audits therefore measure network phases and device phases
with wall-clock counters. Each uses eight warmups and two observed runs. These
instrumented totals include counter overhead and are not a new performance
qualification. The device audit's median phase times are:

| Measured region | Seconds |
| --- | ---: |
| Current assembly | 1.228 |
| Sparse solve using retained factors | 0.758 |
| Network voltage publication | 0.455 |
| Custom-current handling | 0.000635 |
| Ordinary exciter evaluation | 1.965 |
| Stabilizer evaluation | 1.151 |
| Local ST1 advancement, including its evaluations | 0.800 |
| Governor evaluation | 0.547 |
| Renewable boundary evaluation | 0.194 |
| Machine derivative evaluation | 0.160 |
| Electrical-only endpoint preparation | 0.136 |

These regions do not cover the entire run. In particular, current assembly,
solve and publication omit the outer convergence/mixing work; the device
regions omit global state advancement, renewable advancement, bus-frequency
updates and recording. They must not be added up and called total simulation
time. The fully instrumented runs take 10.641 and 10.541 seconds including
preparation and recording. The custom-current hook, despite its prominent
sample count, takes under a millisecond across the complete run. It is not a
meaningful optimization target here.

ST4B receives a further non-mutating audit. Its 1,508,664 output calls all use
exactly two algebraic refresh passes. Together with 1,509,080 step calls, this
produces 7,544,568 preparation passes and voltage reads. A second audit reads
the raw bits of all six stored block inputs around each relevant pass:

- The final step-preparation pass changes no input in any of 1,509,080 calls.
- The first output-preparation pass changes no input in any of 1,508,664 calls.
- The second output-preparation pass changes no input in any of 1,508,664 calls.

This identifies a larger candidate than reducing ST1's substep count: explicit
reuse of a prepared ST4B output within a single solver evaluation. The audit
does not yet authorize skipping passes in general. An implementation must
establish a standard, side-effect-free graph and unchanged machine inputs,
limits and parameters within that scope, preserve all state/derivative work,
and retain the existing path for unsettled feedback or custom behavior. No
cross-step or cross-network-iteration cache follows from this evidence.

All 38 completed runs across the sampling profile, phase timers and two ST4B
audits reproduce the retained complete recordings and metadata byte for byte.
The first network-timer harness attempt failed compilation because of a local
variable-name error; it ran no simulation and is separately classified in the
evidence archive. No production code changed. These Case 1 audits do not extend
accuracy or performance acceptance to other cases, and the eight-second target
and independent trajectory acceptance remain open.

The [phase-profile evidence index](checkpoints/phase-profiles-2026-10-22.json)
retains the sampling recording, instrumented sources, commands, timings, input
change masks, affinity checks and hashes of all 38 complete recordings.
