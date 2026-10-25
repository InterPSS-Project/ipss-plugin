# Texas2k: optional bus-frequency measurements

The imported-case solver's default `BusFrequencyMode.REQUIRED` now selects
**485 of 2,000 buses in both Cases 1 and 2**, omitting 1,515 unused frequency
measurements (75.75%). Complete recorded trajectories remain byte-identical
to the all-bus controls and the previous optimized recordings.

## Default behavior and configuration

The existing required-only default previously fell back to all buses because
the dependency classifier did not recognize built-in PSS2A child controllers.
It now recognizes children owned by the current exact parent implementation,
with their configured fields and observers checked. Foreign, detached or
custom children retain conservative all-bus coverage. The audited local ST1,
GAST, TGOV1 and IEESGO families are also recognized.

Generator buses, configured local/remote controller inputs and requested
frequency outputs remain measured, including potential consumers activated
later. Unknown models, custom callbacks and observers retain all buses.
Required frequency is therefore available without calculating it everywhere.
This changes dependency classification; model equations and integration stages
are unchanged. Generic core object-factory defaults are unchanged.

For explicit all-bus measurement, call
`solver.setBusFrequencyMode(DefaultDynamicSimulationSolver.BusFrequencyMode.ALL)` before
initialization. `REQUIRED` needs no setter. Inspect
`solver.getBusFrequencyReport()` after initialization to verify actual coverage
and any fallback reason. These APIs belong to `DefaultDynamicSimulationSolver`.

Declare requested frequency outputs before initialization. Changing model wiring
or output membership requires reinitialization or explicit all-bus coverage;
an omitted bus's frequency getter does not perform an on-demand calculation.
Configured PSS2A remote buses remain covered when live input selectors change.

## Matched complete-run timings

| Case | All-bus median / range | Required-only median / range | Median difference |
| --- | ---: | ---: | ---: |
| 1 | 8.511 / 8.308–9.525 s | 8.588 / 8.149–8.738 s | 0.077 s slower |
| 2 | 5.519 / 5.356–5.768 s | 5.375 / 5.239–5.708 s | 0.144 s faster |

Each case uses one JVM with four alternating warmup pairs followed by five
alternating measurement pairs. Case 2 reverses the starting order. All ten
measured invocations per case are retained. Required-only is faster in two
of five Case 1 pairs and three of five Case 2 pairs; median paired savings
are -0.107 s and +0.144 s respectively.

The timed interval includes fresh import, power-flow preparation, device and
controller initialization, ten simulated seconds, full recording, CSV export,
writer closure and run metadata. Residual JIT compilation stays included.
Hashing, byte comparisons and duplicate storage sharing occur afterward.

Both modes use the same current optimized profile, ordinary `MODIFIED_EULER`,
pure Java KLU, quarter-cycle steps (`1/240 s`), a `1e-5` pu complex
voltage-correction criterion, two minimum fresh iterations and 50 maximum.
The five-cycle bus-6307 fault runs from 1.000 to 1.083333 seconds.
Current-P/impedance-Q conversion and generator-priority solved-netting power
flow at 0.001 pu tolerance are unchanged. Renewable integration uses ordinary
modified Euler with double-precision states and no iteration-voltage memory.

OS affinity is fixed to logical CPU 6 (mask 64), verified before and after
each JVM. Java 21 uses a 2 GiB heap, one active processor, Serial GC, `-Xbatch`,
`MaxInlineLevel=30` and `FreqInlineSize=600`. Profiling and builds do not overlap
the timing cohorts. The benchmark leaves `REQUIRED` at its constructor default;
only the all-bus arm explicitly overrides it.

## Work removed and numerical verification

| Counter or region median | Case 1: all → required | Case 2: all → required |
| --- | ---: | ---: |
| Frequency measurements | 2,000 → 485 | 2,000 → 485 |
| Passive-bus stage skips | 0 → 7,278,060 | 0 → 7,278,060 |
| Device integration region | 4.400 → 4.282 s | 1.702 → 1.545 s |
| Network region | 2.976 → 3.160 s | 2.760 → 2.852 s |
| Network iterations | 20,559 → 20,559 | 22,732 → 22,732 |

Both modes retain 2,399 checked INITIAL endpoint reuses and three auxiliary
factorizations. The frequency change also allows the existing passive-bus
stage shortcut to operate. Device-region medians improve by 0.118 s and
0.157 s, but whole-run ranges overlap. Case 1 demonstrates no whole-run gain;
Case 2 shows a small gain. Region medians are separate statistics and cannot
be added into an exact decomposition of whole-run medians.

All 36 warmup/measured runs retain every one of 2,405 samples, including two
negative-time startup samples and both fault event sides. Case 1 records 6,190
channels and Case 2 records 4,810, including all 2,000 bus voltage magnitudes
and angles. Every binary trace, selected CSV and channel metadata file matches
both its all-bus control and the prior optimized recording byte for byte.
No offsets, shifts, sample reduction or channel removal are applied.

This establishes unchanged trajectories for this frequency change. The
[earlier optimized profile's accuracy limits](texas2k-default-euler-performance-2026-10-25.md#accuracy-limits-and-tighter-controls)
remain unresolved; this experiment establishes no new independent reference
acceptance. Earlier 10.022 s / 5.863 s medians used separate JVMs and a different
warmup protocol. They cannot identify the isolated benefit of frequency
selection; the matched all-bus controls above provide that comparison.

## Validation and retained evidence

Full plugin-core validation reports 452 passed tests and four existing skips.
Four focused integration/reference tests also pass, including local/remote
PSS2A fault histories and a live selector change with exact state equality.
Unit tests cover requested outputs, owned/foreign/custom child controllers,
observers, audited local families and custom-family fallback. Core source is
unchanged, so no new full core-suite result is claimed.

The [checkpoint](checkpoints/texas2k-optional-frequency-2026-10-25.json)
records every measured time, region medians, counters, trajectory hashes and
the private evidence manifest. That manifest retains the measured first-party
jars, source snapshots/patch, runner, exact classpath resources, dependency and
input hashes, affinity records, validation reports and failed compile attempt.
Classpath directory deltas reference an existing frozen runtime to avoid
duplicating unchanged resources. Only verified identical generated outputs
share storage; supplied references remain intact.
