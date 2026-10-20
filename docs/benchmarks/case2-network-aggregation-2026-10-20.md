# Case 2 network aggregation experiments

This follow-up targets repeated convergence work and current-injection assembly
after the [primitive-transfer experiment](case2-qsts-primitive-transfer-2026-10-20.md).
All candidates preserve the quarter-cycle step, 1e-5 maximum-component voltage
tolerance, single logical CPU, and existing network iteration lifecycle.

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
Its integration median still needs approximately 9% reduction to meet the target.

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
