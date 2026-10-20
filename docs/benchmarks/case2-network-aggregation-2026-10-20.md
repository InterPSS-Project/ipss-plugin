# Case 2 network aggregation experiments

This follow-up targets repeated convergence work and current-injection assembly
after the [primitive-transfer experiment](case2-qsts-primitive-transfer-2026-10-20.md).
All candidates preserve the quarter-cycle step, 1e-5 maximum-component voltage
tolerance, single logical CPU, and existing network iteration lifecycle.

The timing requirement now explicitly includes recording. The integration-only
numbers below are historical component measurements, not the acceptance metric.
Use simulation plus recording through final output close, and also report the
complete runner elapsed time including case loading and initialization.

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
