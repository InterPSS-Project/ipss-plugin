# Case 2 voltage runtime experiments

The best measured warmed median in this follow-up is **11.2901 seconds** for ten
simulated seconds. The ten-second target remains open. All experiments retain
one logical CPU, 1/240-second steps, 1e-5 maximum-component convergence tolerance,
4,810 channels and 2,405 full-output samples per run.

Each series starts a fresh JVM, performs one complete warm-up, then measures
three newly imported and initialized cases in that same JVM. No numerical state
is reused between cases. Source and runtime binary hashes are checked, and CPU
affinity is verified before and after each series. Integration excludes channel
recording; the separate elapsed measurements include setup and recording.

## Measurements

| Experiment | Measured integration times, s | Median, s | Network range, s |
| --- | --- | ---: | ---: |
| Reuse solved voltage object, C2 settings | 11.2248 / 11.9765 / 11.5244 | 11.5244 | 6.2225–6.8305 |
| Direct voltage observer, C2 settings | 11.8684 / 11.3570 / 11.6547 | 11.6547 | 6.2309–6.7045 |
| Reuse solved voltage object, tiered compilation | 11.1078 / 11.5222 / 11.2901 | **11.2901** | 6.0376–6.4178 |
| Machine rotation cache, tiered compilation | 11.2031 / 11.4554 / 11.5242 | 11.4554 | 6.0457–6.3280 |

All runs perform 2,400 steps, 4,800 network calls and 38,819 network iterations.
The best series has runner elapsed times of 26.1505, 26.2319 and 24.9610 seconds.
Full recording adds approximately thirteen seconds outside integration. Java
heaps remain bounded at 2 GiB. No profiled run is included in this table.

These are sequential screening series with overlapping timing ranges, not a
statistical demonstration of each change's isolated benefit. The direct observer
and machine rotation cache did not improve their respective control medians.
They are not promoted into production. The simpler voltage-object candidate with
tiered compilation is retained as the next experimental control. It still needs
about 11.4% less integration time to meet the target.

## Implemented experiments

The preceding profile identified convergence observation, sparse triangular
solves and current-injection evaluation as prominent integration stacks. Its
611 samples also include ordinary recording, so their percentages must not be
interpreted as exclusive integration CPU percentages.

The voltage-object candidate seeds the existing rectangular-voltage cache with
the exact immutable solved object. This removes a reconstruction of the same
complex value. It also reuses the first voltage observation within a convergence
check for the standard bus class. Existing notification and subclass fallbacks
remain in place.

The direct-observer candidate additionally reuses the already computed voltage
magnitude and passes the solved value directly to convergence observation on the
guarded path. Observed buses and custom subclasses retain their setter/getter
path. The convergence norm, tolerance, comparison operators, invalid-value
handling and leading-bus tie ordering are unchanged.

The machine-rotation candidate caches an immutable sine/cosine pair keyed by the
exact rotor-angle bits. Terminal voltage, scaling, current and power calculations
remain live. It preserves the coordinate equations' arithmetic order and refreshes
the pair whenever the angle changes. Despite exact numerical results, the timing
screen does not justify adopting this additional cache.

## Verification and storage

Both voltage candidates pass 4,274 exact floating-point checks covering voltage
and angle observations, coordinate changes, component mutations, signed zero,
extreme values and notification history. The direct-observer check also verifies
that custom buses and notification listeners reject the fast update without
partially changing state or emitting notifications.

The machine check compares 21,000 observations across 1,000 rotor angles with
three changing terminal voltages per angle. It includes signed zero and very
large angles and checks terminal coordinates, injection current, electrical
power and internal current components. Every observation is finite and exactly
matches the preceding implementation.

All sixteen full traces, including warm-ups, match the retained control exactly:
**11,568,050 measurements per trace**, with matching channel metadata and initial
compensation records. Event-side duplicates are preserved. These results establish
numerical nonregression; independent-reference accuracy gaps remain as described
in the [preceding report](case2-clean-warm-runtime-2026-10-20.md).

After verification, identical generated full traces share immutable storage with
the retained control. This avoids 1,486,958,112 duplicate bytes for the sixteen
new exports. Supplied references remain untouched. Compact sources, classes,
producers, focused results, run metadata and hashes are retained privately under
`2026-10-20-case2-voltage-runtime`.

The next experiment should isolate conversion between primitive sparse-solver
buffers and complex row objects. Existing row aliases, custom injection hooks,
iteration callbacks and diagnostic observations must retain coherent values.
The measured results do not justify removing any of those behaviors implicitly.
