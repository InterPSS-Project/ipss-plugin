# Case 2 network aggregation experiments

This follow-up targets repeated convergence work and current-injection assembly
after the [primitive-transfer experiment](case2-qsts-primitive-transfer-2026-10-20.md).
All candidates preserve the quarter-cycle step, 1e-5 maximum-component voltage
tolerance, single logical CPU, and existing network iteration lifecycle.

The timing requirement now explicitly includes recording. The integration-only
numbers below are historical component measurements, not the acceptance metric.
Use simulation plus recording through final output close, and also report the
complete runner elapsed time including case loading and initialization.

The current objective supersedes the historical ten-second target below:
**under eight seconds for ten simulated seconds**, at 1e-5 tolerance and a
1/240-second step, on one logical CPU after warm-up. Complete runner time,
including case loading, initialization and recording, is the primary metric.
The user reports a Case 2 reference-run total below seven seconds. That is a
user-supplied comparison, not a locally reproduced timing measurement.

## Implemented candidates

The convergence candidate uses the already computed maximum bus change to make
one tolerance decision per iteration. It retains per-bus checks for custom or
observed buses and diagnostic-alert mode. Leading-bus selection, invalid-voltage
handling, minimum iterations, strict component-wise comparison and inclusive
complex-magnitude comparison remain unchanged. The closed standard-bus path
assumes settings stay fixed during equation assembly; iteration callbacks still
run before the decision mode is selected.

The current candidate stores generator and dynamic-device references in arrays.
Containment-list changes invalidate the corresponding array, including inverse
removal, reordering and same-size replacement. The next indexed access refreshes
an invalidated array, preserving changes that occur during current assembly.
Generator/device status and the generator's current model are read live.
Voltage-dependent static-load current is recalculated on every call.

Real and imaginary current components accumulate in the existing order, avoiding
intermediate complex sums. The implementation retains the existing complex-add
path for custom complex subclasses and preserves null/error behavior. Custom bus
subclasses retain the original assembly path. A startup property can disable the
experimental array path for comparison.

The combined candidate adds both changes to the previously checked direct-voltage
candidate, which passes the solved voltage to convergence observation and reuses
its magnitude. Every candidate is a fresh twelve-source build with verified
source and class hashes. None of these experimental overlays changes production
defaults.

## Focused checks

The convergence check runs 504 actual network-solver cases against the preceding
implementation. Its cases cover both norms, minimum iterations, exhausted
budgets, unchanged voltages, exact and neighboring tolerance values, oscillating
inputs, observed buses, and alert mode. Complete diagnostic records, callback
order and stored voltages match exactly. The combined candidate passes the same
check.

The current check compares 1,010 observations across the preceding implementation,
the new implementation disabled, and the new implementation enabled. Current
bits, output-call order, errors and inverse links match. It exercises live status
and model changes, reorderings, inverse removal/re-addition, same-size replacement,
mutation during assembly, empty lists, signed zero, large values, invalid values,
null output and custom complex getters. The combined candidate also passes.

The first convergence-check compilation used a setter unavailable on its base
solver. That attempt executed no tests. The corrected fixture sets the protected
tolerance in its test subclass; both logs are retained.

## Timing and full-channel validation

Each series uses one complete warm-up and three fresh imports/initializations in
the same JVM. Normal tiered compilation, a 2 GiB heap and serial GC are retained;
CPU affinity is verified before and after execution. Integration timing excludes
recording. Separate runner elapsed timing includes setup and output.

| Candidate | Measured integration times, s | Median, s | Network range, s |
| --- | --- | ---: | ---: |
| Convergence reduction | 11.4003 / 11.0114 / 11.2197 | 11.2197 | 6.0913–6.3169 |
| Current arrays and accumulation | 10.9808 / 11.5055 / 10.9398 | **10.9808** | 5.8981–6.3182 |
| Combined with direct voltage update | 11.1578 / 11.4442 / 11.1312 | 11.1578 | 5.8967–6.1563 |

The preceding control median was 11.2901 seconds. These sequential screens have
overlapping timing ranges, so the median differences do not establish isolated
speedups statistically. The simpler current-array candidate is retained as the
next experimental control; the combined candidate does not improve its median.
The current-array runner elapsed times are 26.2121, 26.4296 and 24.6215 seconds.
Its integration median alone still exceeds ten seconds; recording adds further
work that must also fit within the requested time.

Every run performs 2,400 steps, 4,800 network calls and 38,819 network iterations.
All twelve complete traces, including warm-ups, match the retained control
exactly: 4,810 channels, 2,405 samples and 11,568,050 finite measurements per
trace. Event-side duplicates, selected-channel output, channel metadata and
initial compensation records are unchanged. No run meets the ten-second target.

After verification, the twelve full traces share immutable storage with the
retained control, avoiding 1,115,218,584 duplicate bytes. Supplied references are
unchanged. Compact source/class snapshots, producers, focused checks, logs, run
metadata and hashes are retained privately under
`2026-10-20-case2-network-aggregation`.

Numerical agreement with the retained control is a nonregression check. It does
not close the independent-reference accuracy gaps described in the earlier
reports. The performance goal remains ten seconds for ten simulated seconds;
no convergence tolerance, sampling, channel coverage or worker-count relaxation
is part of these experiments.

## Lossless recording path

The reference runner accepts `"full_network_format": "BINARY64"` when
`"full_network_diagnostics": true`. The default remains `CSV_GZIP` for existing
consumers. The binary option writes the same channel values and event sides using
one reusable row buffer and a 256 KiB output buffer. It avoids decimal formatting
and compression during simulation. All writes remain on the simulation thread.
The 512 MiB file limit, 2 GiB free-space reserve and refusal to overwrite existing
observations apply to both formats. Selected-channel CSV output remains enabled.

`full-network.bin` begins with the eight ASCII bytes `IPSSOBS1` and a big-endian
32-bit channel count. Each row contains a big-endian binary64 timestamp, a
big-endian 32-bit UTF-8 event-side byte count, those event-side bytes, and one
big-endian binary64 value per channel in manifest order. Event sides are limited
to 1,024 bytes. The existing JSON channel manifest identifies the format and file,
retains units and device identities, and records the sample count and time bounds.
No precision reduction, sample removal, clock adjustment or event merging occurs.

`tools/benchmark_observations.py` streams either format as
`(time, event_side, values)`. Exhausting the iterator validates the complete trace,
including finite values, metadata, row counts, truncation and trailing binary data.
The standalone command `python tools/benchmark_observations.py <run-directory>`
validates a saved trace without creating another large output file.

The runner's `simulation_output_wall_s` starts before startup-history recording
and ends after both trace writers close and the full-network manifest is written.
`setup_initialization_wall_s` reports the preceding setup separately; `elapsed_s`
includes both phases. Compilation remains outside these timings. Files are closed
normally; no additional durability synchronization is requested.

Nineteen focused Java tests pass, including both recording formats, synchronous
machine and converter power, state/cache preservation, initialization inventory
binding, trip handling, duplicate event times, selected-channel reporting and
storage guards. Four Python reader tests cover exact values including signed
zero, event duplicates, truncation, extra data, nonfinite values and inconsistent
metadata.

### Warmed recording comparison

Both series use the same current-array runtime overlay, one full warm-up and
three measured fresh cases in one JVM, pinned to one logical CPU. All 4,810
channels and 2,405 rows are written, including the selected-channel CSV. Solver
configuration and the 38,819 network iterations are unchanged.

| Recording format | Simulation plus all recording, s | Median, s | Median including loading/initialization, s |
| --- | --- | ---: | ---: |
| Compressed CSV control | 24.6046 / 24.2968 / 24.2962 | 24.2968 | 25.6608 |
| Buffered binary64 | 11.1153 / 11.3273 / 11.2635 | **11.2635** | **12.7331** |

The median simulation-plus-recording time falls by **53.6%**. Complete runner
elapsed time falls by **50.4%**. Integration alone has a 10.1515-second median in
the binary series, compared with 11.2055 seconds in the CSV series; these timings
include the effects of each recorder's allocation and runtime behavior. This is
a sequential warmed comparison, not a statistical isolation of those effects.
The ten-second target remains unmet, whether measured through recording close or
including case loading and initialization.

All eight traces, including warm-ups, preserve the retained control exactly:
11,568,050 finite channel measurements per trace, binary64 timestamps, event-side
labels, duplicate event rows and channel metadata. Selected CSV and initial
compensation files are byte-identical to the control. The full binary trace is
92,592,513 bytes, versus 92,934,882 bytes for compressed CSV. Identical repeat
traces share immutable storage after verification, avoiding extra retained
copies. The compact evidence package is retained privately under
`2026-10-20-case2-binary-recording`.

The recorder and inclusive timing are repository changes; the numerical runtime
used here is still the separately preserved experimental overlay. Exact agreement
with the old control proves recording nonregression, not independent-reference
accuracy acceptance.

## Follow-up screens with recording included

A warmed binary-output profile contains 581 integration execution samples. The
largest sampled leaf is the factored sparse triangular solve (14.6%). Voltage
angle evaluation, EMF iteration, convergence observation and static-load cache
checks also appear prominently. These samples identify candidates; profiled
wall times are not qualifying performance measurements.

Three further series retain one logical CPU, one complete warm-up, three measured
fresh cases, all output channels, 1/240-second steps and 1e-5 convergence tolerance.

| Candidate | Simulation plus recording, s | Median, s | Network iterations |
| --- | --- | ---: | ---: |
| Setter-invalidated voltage and load-part caches | 11.4121 / 11.0638 / 11.2477 | 11.2477 | 38,819 |
| Exact small-increment frequency unwrap | 11.3380 / 11.5655 / 11.4214 | 11.4214 | 38,819 |
| Existing constant-impedance matrix split | 12.1077 / 11.5224 / 11.4800 | 11.5224 | 40,871 |

None establishes an improvement over the retained 11.2635-second median. The
cache difference is within the observed run-to-run range; the other two are
slower. None is promoted to production or replaces the experimental control.

The cache candidate invalidates rectangular-voltage storage only when component
bits change and invalidates load-part eligibility when coefficient references
change. Custom subclasses retain the previous checks. It passes 4,274 voltage
assertions with matching notifications and 2,065 load/current observations with
matching bits and errors. The first load fixture caught exceptions but omitted
the engine's NaN-current `Error`; the corrected fixture compares both kinds of
failure. All four complete traces match the control exactly.

The frequency candidate bypasses `IEEEremainder` only when the computed increment
is within ±π, where the quotient rounds to zero. All other increments use the
existing calculation. It passes 203,375 exact angle checks, including neighboring
boundary values, signed zero, extreme values and invalid values, plus the three
existing ramp, angle-jump and snapshot-restoration tests. All four full traces
also match the control exactly.

The matrix split uses the engine's existing option to stamp only the initialized
constant-impedance load component. Current-dependent load behavior and the
physical I/Z curve remain unchanged. Earlier independent load observations
validated that representation. Here it increases iteration count by 5.3% and
changes the finite-tolerance trajectory: maximum control differences are
0.0060935 pu voltage and 1.26581° rotor angle. Bus-phase difference is 1.15250°
using principal periodic differences; raw subtraction across the ±180° branch
cut would misleadingly report almost 360°. All four matrix-split repeats match
each other, but they do not match the control.

A separate diagnostic compares both quarter-cycle trajectories with the supplied
full-system reference. It validates the reference's accumulated float clock
against the declared 1 ms grid and interpolates within each event phase. Both
event-post rows are matched by event identity. Clearing occurs at 1.083333 seconds
in this benchmark and 1.084 seconds in the supplied trace; no fitted clock shift
or rotor-angle offset is applied. This is not a formal same-grid acceptance test.
The control's worst prefault voltage error is 0.131920 pu and its worst full-window
rotor RMSE is 117.675° at bus 5263/unit 1. The matrix split gives 0.131905 pu and
117.631°, respectively. These large independent-reference discrepancies confirm
that the frozen performance fixture remains unsuitable for accuracy acceptance;
bit-exact recording and runtime checks do not validate its physical trajectory.

Compact profile, build, check, comparison and timing evidence is retained under
`2026-10-20-case2-recorded-runtime-screens`. Identical repeated output files share
immutable storage after validation. The best retained timing remains 11.2635
seconds including recording, and the ten-second goal remains open.

## Reusable row view of sparse factors

The next candidate retains the existing LU factorization and creates a reusable
row view of its flat triangular factors. Each row accumulates its solution in
local scalars instead of repeatedly updating scattered workspace entries. Lower
and upper coefficients retain the library's subtraction order. Diagonal division
reuses its constant ratio and scale, preserving the original arithmetic rather
than substituting complex reciprocal multiplication.

This path accepts only triangular factors with independent blocks. Coupled blocks
and other factor layouts use the existing library solve. Matrix invalidation and
symbolic-table replacement discard the view; every numeric factorization or
refactorization rebuilds it. A failed rebuild cannot leave the old factor usable.
The core adapter exposes `setUseFactoredRows(true)` and the default-off
`ipss.jklu.rowSolve` property. Flat factors must also be available; these runs use
`-Djklu.complex.factor.columnKernelMinN=0`. The solver remains single-threaded.

The initial focused comparison checks 85,440 double observations, repeated RHS
solves, value changes, pivoting, independent and coupled blocks, invalid RHS
lengths and nonfinite values. Enabled, disabled and control outputs have identical
digests. The maximum independently calculated residual is 1.15e-14. An initial
candidate incorrectly required precomputed diagonal reciprocals and consequently
never activated; the solve counter caught this before any timing claim.

The integrated adapter and separate helper pass 14 selected JUnit tests: four new
lifecycle/numerical tests and ten existing complex sparse-solver tests. They cover
AMD and natural ordering, structural changes, dimension growth, symbolic reuse,
enable/disable changes, missing flat factors and the optional reciprocal mode.
A broader initial test also tried COLAMD, but its library analysis failed with a
null-row exception before the control solve. That ordering is not covered by the
successful new regression run. This is focused clean compilation and JUnit
validation, not a full Maven suite.

| Series | Warm-ups | Simulation plus recording, s | Median, s |
| --- | ---: | --- | ---: |
| Experimental row view, ordinary tiered settings | 1 | 11.3452 / 10.8723 / 11.0102 | 11.0102 |
| Experimental row view, expanded inlining | 3 | 10.9299 / 10.5337 / 11.1376 | 10.9299 |
| Integrated helper, expanded inlining | 3 | 10.9438 / 10.8702 / 10.7660 | 10.8702 |
| Matched control with row view disabled, expanded inlining | 3 | 11.3575 / 11.2446 / 10.8966 | 11.2446 |

Expanded inlining uses `-XX:MaxInlineLevel=30 -XX:FreqInlineSize=600`. Every series
uses one logical CPU, `ActiveProcessorCount=1`, SerialGC and a 2 GiB heap. Each
warm-up and measurement reconstructs the network in the same JVM. Compilation is
outside these timings. The integrated helper's complete runner times are 11.5382,
11.3126 and 11.2036 seconds, including case loading and initialization. The median
integration-only time is 9.7793 seconds, which must not be mistaken for meeting
the inclusive ten-second goal.

All 22 complete traces in these four series match the retained control bit for
bit: 4,810 channels, 2,405 rows and 11,568,050 finite measurements per trace, with
event-side duplicate timestamps preserved. Each run retains 38,819 timed network
iterations. The integrated run's lifetime counter records 34,454 row-view solves,
including initialization; unsupported factors continue through the fallback.
An earlier six-run attempt stopped at the 2 MiB console budget and is excluded
from timing conclusions. Its retry used an 8 MiB budget and completed normally.
The first matched-control attempt reached the 2 GiB free-space reserve and was
also excluded. Identical older generated CSVs and completed traces were verified
and moved to immutable shared storage, recovering about 0.9 GB without changing
their paths or contents. Partial files and supplied references were preserved.
The control retry completed with the same output and storage limits.

The matched control uses the same clean-built classes and JVM settings, changing
only the row-view enable property. Its counter remains zero. Compared with its
11.2446-second median, the enabled helper's 10.8702-second median is **3.3% lower**.
Complete runner medians are 11.7071 and 11.3126 seconds, respectively. These are
sequential small-sample measurements with overlapping ranges, so a repeatable
speedup of that size is not yet established.

The API and regression tests are core changes. The full-system timing still uses
the preserved experimental network/controller overlay alongside that adapter;
it does not describe the default production configuration. The original two
experimental series also use different warm-up policies and cannot isolate the
effect of expanded inlining. Compact sources, compiled classes, tests, complete
and stopped run metadata, and verified trace hashes are retained privately under
`2026-10-20-case2-factored-rows`. The ten-second target and independent-reference
accuracy acceptance remain unmet.

## Bound recording channels and reusable CSV rows

The full recorder now calculates each synchronous or grid-forming generator's
terminal power once per observation and emits both P and Q from that result.
Adjacent channel evaluation consumes the pair immediately. Every new row
refreshes the measurement, including rows with identical event timestamps.
Electrical caches and retained air-gap power remain unchanged by observation.

The selected-channel recorder binds device references and column names once for
the initialized case. Each sample refreshes a reusable primitive array, checking
live bus and machine status. Repeated selections retain the original unique
column order. A changed selection or structural device inventory requires a new
recorder. A reusable string builder then assembles each selected CSV row before
writing it. CSV double formatting, line endings, column order and all values are
preserved; the full trace remains lossless binary64.

All series below retain the same numerical overlay, enabled row solver, expanded
inlining, 1e-5 tolerance, 1/240-second step, three complete warm-ups and three fresh
measured cases in one JVM. Output close and metadata writing remain timed.

| Recording configuration | Logical CPU | Simulation plus recording, s | Median, s | Complete runner median, s |
| --- | ---: | --- | ---: | ---: |
| Previous recorder | 0 | 10.9438 / 10.8702 / 10.7660 | 10.8702 | 11.3126 |
| Bound channels and paired terminal power | 0 | 10.1857 / 10.2984 / 10.1719 | 10.1857 | 10.7777 |
| Also buffer selected CSV rows | 0 | 10.2252 / 9.7867 / 10.1292 | 10.1292 | 10.5419 |
| Same buffered recorder, alternate CPU placement | 4 | 9.7885 / 9.8913 / 10.0189 | 9.8913 | 10.4126 |

On the original CPU placement, the combined recording change lowers the measured
inclusive median by **6.8%**. The additional row-buffer difference is small
relative to run variability and is not established as an isolated speedup.
Changing to logical CPU 4 is a separate placement experiment, not a code change.
Each JVM's process affinity is verified before and after execution to contain
exactly one logical CPU; `ActiveProcessorCount=1`, SerialGC and the 2 GiB heap
remain in force. No additional simulation workers are created.

All 18 new complete traces, including warm-ups, match the retained numerical
control bit for bit. Each has 4,810 channels, 2,405 rows and 11,568,050 finite
measurements, with both event sides retained. Selected CSV files, initial
compensation and channel metadata also remain identical. Timed network iteration
count remains 38,819. The final reporting/storage test run passes all 20 selected
tests, including same-time voltage/status changes, reconnection, electrical-cache
preservation, duplicate selection, signed zero, subnormal/extreme finite CSV
values, and rejection of nonfinite values.

Verified identical generated files share immutable storage after each completed
series. Compact source, class, test, timing and trace-hash evidence is retained
privately under `2026-10-20-case2-recording-bindings`. These changes are committed
benchmark-recorder code; the measured numerical network/controller runtime
continues to include the separately preserved experimental overlay.

The best measured simulation-plus-recording median is below ten seconds, but
one run remains above ten and complete runner time remains above ten in every
measured run. The complete-time goal is therefore still open. Matching the
retained control does not resolve its independent-reference accuracy failures.

## Workflow redesign toward the eight-second target

A whole-run main-thread profile after three complete warm-ups collected 624
execution samples: 361 in network solution, 226 in the rest of integration,
14 in recorder code, and 23 in setup or other work. Sampling percentages are not
component wall times. Profiling adds overhead, particularly during setup, so its
elapsed time is excluded from qualifying timing comparisons. The profile uses
the same single-CPU placement, numerical overlay and full output as the preceding
CPU-4 series.

The most frequent sampled leaves are voltage retrieval, current-injection
assembly and the row-oriented triangular solver. Repeated branch/device scans
also remain visible. These results favor removing repeated dispatch and data
transfer from the iteration loop. Recording is already a small remaining cost;
removing output cannot provide the required improvement and is not an allowed
benchmark change.

Three clean-built prototypes test parts of that design:

1. Pass the solved rectangular voltage directly to convergence observation for
   exact standard buses without notification listeners. Polar, observed and
   custom buses retain getter-based observation.
2. Store preceding voltages in a reusable indexed array and specialize the
   static-only current-injection path. Every iteration still evaluates the live
   voltage-dependent load, load-change factor and current load-model settings.
   The leading zero in current accumulation is retained to preserve signed-zero
   behavior and arithmetic order.
3. Compile network-iteration participant bindings once and rebuild them when
   membership changes. Network bus/branch lists, bus generator/device lists,
   branch device lists and generator-model changes invalidate the plan. Each
   solve still checks live bus, generator, branch-endpoint and device status,
   preserves identity deduplication and callback order, and uses the original
   scan for unsupported network, bus, generator or branch subclasses.

The static-current and indexed-convergence prototype passes 3,010 current/error/
mutation observations and 504 convergence-norm, threshold, diagnostic and callback
cases against the preceding control. The schedule passes another 3,000 membership
observations, including reordering, replacement, inverse containment, removal,
reinsertion and status changes; its binding array is reused on 2,249 observations.
These are focused differential checks, not a full production test suite.

| Configuration | Simulation plus recording median, s | Complete runner measurements, s | Complete runner median, s |
| --- | ---: | --- | ---: |
| Previous CPU-4 control | 9.8913 | 10.4126 / 10.3419 / 10.4732 | 10.4126 |
| Direct solved-voltage handoff | 9.8860 | 10.7383 / 10.3862 / 9.9685 | 10.3862 |
| Indexed convergence and static-only injection | 9.5647 | 10.1045 / 10.2135 / 9.9321 | 10.1045 |
| Also prepare the participant schedule | 9.0368 | 9.7749 / 9.4830 / 9.4215 | 9.4830 |

Every series uses three full warm-ups and three fresh measured cases in one JVM,
the same logical CPU 4, a 2 GiB heap, SerialGC and no simulation worker threads.
The first handoff change establishes no material timing improvement. The combined
prototype has a complete-time median 8.9% below the earlier control, although
these sequential small-sample runs are not a randomized performance study.
Its network component takes 4.33–4.42 seconds, with integration taking 8.60–8.71
seconds. Full recording and output finalization remain included in the reported
simulation and complete times.

All 18 new warm-up and measured traces are bit-exact to the retained control:
4,810 channels, 2,405 rows and 11,568,050 finite measurements per trace. Selected
CSV, metadata, startup compensation and event-side duplicate samples match too.
All runs retain 38,819 timed network iterations. The profile's four generated
full and selected traces also have the same retained hashes. Verified duplicate
generated files share immutable storage; supplied references are preserved.
Compact source, class, profile, test and timing evidence is retained privately
under `2026-10-20-case2-workflow-redesign`.

The eight-second complete-time objective remains unmet. These are experimental
overlays, not a change to production defaults, and exact agreement with the local
control is only a nonregression result, not independent-reference acceptance.

The indexed prototype changes the protected legacy voltage-table representation.
That table is deliberately excluded from its differential check. A production
implementation needs an explicit compatibility path for subclasses and custom
networks before this experimental implementation can replace the general solver.

The intended runtime separates preparation from stage execution. Initialization
validates models and builds stable bus/device slots, controller dependency order,
callback schedules and output bindings. A network workspace owns RHS, solved
voltage and preceding-voltage buffers. Each integration stage refreshes external
inputs, evaluates its controller equations and voltage-dependent injections,
solves with the retained factorization, checks convergence, and commits accepted
state before recording. Structural changes invalidate the affected prepared plan;
status and numerical inputs remain live. Custom device calls retain a synchronized
object-model path. Algebraic evaluation must not advance hidden controller history.

The next substantial implementation work is to extend the prepared schedule to
the remaining integration callbacks and to give common controller kernels reusable
numerical storage with explicit stage ownership. A change in network iteration
count or arithmetic requires a fresh independent-reference comparison; faster
execution alone does not establish an acceptable simulation. Existing reference
accuracy failures remain unresolved.

## Dynamic-device updates: local ST1 numerical storage

The prepared-schedule run spends about 4.3 seconds of integration outside network
solution. This includes device updates, measurements and scheduling; it is not a
controller-only timer. ST1 is a useful first target because its local integration
performs ten substeps, each with two controller evaluations. Repeated block
dispatch and state access therefore multiply even when the network is unchanged.

Two additional clean-built experimental overlays preserve those substeps and all
existing preparation passes. The first moves the local update into the direct
ST1 evaluator with fixed block bindings. The second captures inputs, coefficients,
states and derivatives into reusable primitive arrays, evaluates standard blocks
there, and restores block integration state at the stage boundary. The primitive
path is restricted to stages with no stabilizer or an existing fixed PSS2A
observation hold. Other stabilizers retain the object-based local update. Custom
static gains retain their existing calls. Voltage and stabilizer observation reuse
is confined to the local stage; network-dependent inputs refresh afterward.

| Configuration | Simulation plus recording median, s | Complete runner measurements, s | Complete runner median, s |
| --- | ---: | --- | ---: |
| Prepared participant schedule | 9.0368 | 9.7749 / 9.4830 / 9.4215 | 9.4830 |
| Bulk local ST1 update | 8.9452 | 9.5200 / 9.3265 / 9.5532 | 9.5200 |
| Primitive local ST1 storage | 8.7799 | 9.5711 / 9.2370 / 9.0521 | 9.2370 |

The same three-warm-up, three-measurement, single-CPU protocol applies. Bulk
dispatch alone establishes no total-time improvement. Primitive storage gives a
2.6% lower complete-time median than the preceding schedule experiment, but the
small sequential samples overlap and do not establish a repeatable isolated
speedup. Its integration takes 8.23–8.50 seconds, including 4.32–4.42 seconds of
network solution. Each run confirms 36 ST1 models and 86,472 primitive local
advances, including startup. Recording and finalization remain included.

Both prototypes pass 480 focused steps across four ST1 configurations, including
setpoint changes, discontinuities, deactivation/reactivation and limiter settings.
Machine values, controller inputs, states, outputs, derivatives and retained
integration history match the object-based control exactly. All twelve complete
warm-up/measured traces remain bit-exact to the retained full-system control,
including all 4,810 channels, 2,405 rows, event duplicates, selected CSV, metadata
and startup compensation. Timed network iterations remain 38,819. These are
focused differential checks, not the complete production test suite.

Compact source, class, failed-build, test and timing evidence is retained privately
under `2026-10-20-case2-st1-local-storage`. Identical generated traces share
immutable storage. Neither prototype changes production defaults. The primitive
prototype duplicates standard block equations; production kernels should derive
from a shared equation definition and retain a synchronized fallback for custom
blocks to avoid maintaining two independent model implementations.

The next device-update targets are typed global state bindings, one activation
check per device per stage, and prepared integration callback schedules. Broader
controller evaluations can be split into algebraic evaluation, derivative
evaluation and accepted-history commit only after their dependencies and history
effects are explicit. Removing repeated passes or lowering substep counts without
that analysis would change the numerical method. The eight-second complete-time
target and independent-reference accuracy acceptance both remain unmet.
