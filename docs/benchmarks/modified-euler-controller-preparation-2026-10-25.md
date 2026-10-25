# Default modified-Euler controller preparation

The imported-case modified-Euler solver now prepares qualified controller
graphs automatically. It can use the same direct ST4B and PSS2A evaluators
available to the staggered solver, a direct ordinary ST1 stage, and the typed
IEEEST stage without benchmark-specific setup calls.

Matched whole-run medians improve from **8.358 to 7.659 seconds in Case 1**
and **5.377 to 5.305 seconds in Case 2**, with byte-identical full recordings.
Case 2's whole-run improvement is small and its timing ranges overlap.

## Compatibility audit

| Enhancement | Ordinary modified-Euler integration |
| --- | --- |
| Primitive current handoff, packed network iteration and numerical buffers | Already implemented in the shared core; available through the maintained optimized profile |
| Converter compensation reformulation and accepted-endpoint certificate | Already shared; their numerical qualification and explicit profile selection remain unchanged |
| Required frequency, passive-bus dispatch and prepared recording | Already shared; Cases 1 and 2 retain 485 required frequency buses |
| Machine coordinate-transform reuse | Already enabled by the common machine implementation |
| Compiled controller bindings and direct ST4B/PSS2A evaluation | Prepared automatically for qualified standard graphs |
| Direct ST1 equations | Added ordinary predictor/corrector execution using the existing block kernels and reusable numerical storage |
| Typed IEEEST evaluation | Selected for qualified instances without requiring a process-wide experimental property |
| STORE history, discontinuity averaging, held staggered inputs, local STORE substeps and machine measurement boundary | Remain specific to staggered integration; transferring them would change the integration method |

The ST1 path retains input capture, predictor/corrector advancement and both
output-refresh passes. The direct equations share the existing block kernels;
they do not implement a second integrator. Network voltage-dependent currents,
controller limits and live numerical parameters remain current at their
prescribed stages. No network tolerance, time step or recording selection is
changed by controller preparation.

## Default selection and lifecycle

`PsseDStabSolver` enables controller preparation by default when the algorithm
uses `MODIFIED_EULER`. Other methods keep their existing dispatch. The staggered
solver retains its own preparation and histories.

Qualification checks exact audited model and owner classes, observers, wrapper
types, declared equations, configured blocks, built-in child ownership and
limits. Private annotation-generated fields can legitimately reside only in
their runtime wrappers; explicitly configured fields must match those wrappers.
Unknown/custom structures keep compatible controller execution, preserving the
ordinary solver's broader model support. Inactive attachments are considered
because a later event can activate them.

Inspect `solver.getControllerPreparationReport()` after initialization for
actual compiled/direct coverage and the compatible-controller count. To select
compatible execution explicitly, call
`solver.setControllerRuntimePreparationEnabled(false)` before initialization.
This option affects controller preparation only.

Structural changes require `invalidateRuntimePlan()` before editing and
recompilation, or solver reinitialization. Invalidation now releases direct
ST1, ST4B and PSS2A bindings, including the captured PSS2A child bindings, so
new equations cannot accidentally retain the old direct evaluator. Numerical
parameters remain live. Reinitialization releases solver-owned plans and
rebuilds coverage; it can return to compatible execution when preparation is
disabled.

## Validation

Coupled small-system tests compare raw voltage, frequency, machine-state,
controller-state, input and stored-derivative bits across faults, live parameter
changes and activation changes. They assert actual direct-kernel coverage.
Additional checks cover custom blocks, observers, structural invalidation and
reinitialization. Existing independent small-system limits remain unchanged.

The complete plugin `install` reactor passes: 4,256 total tests, 86 existing
skips, zero failures or errors (4,170 passed). Fresh report totals are 456 in
plugin core, 468 in three-phase and 3,332 in integration tests. The four new
preparation tests exercise direct ST1/ST4B/PSS2A/IEEEST coverage and lifecycle
behavior. Existing ordinary/staggered tests and independent component limits
also pass. A separate solver-replacement probe preserves exact initialized
histories after accumulated controller motion. Core source is unchanged;
no new core-suite result is claimed.

## Matched full-case performance

| Case | Previous explicit setup: median / range | Automatic default: median / range | Median reduction |
| --- | ---: | ---: | ---: |
| 1 | 8.358 / 7.888–8.719 s | 7.659 / 7.547–7.900 s | 8.4% |
| 2 | 5.377 / 5.279–5.781 s | 5.305 / 5.146–5.544 s | 1.3% |

The control disables automatic preparation and executes the previous explicit
benchmark setup. The new arm uses the enabled constructor default and removes
that setup. Each case uses one JVM with four alternating warmup pairs followed
by five alternating measurement pairs; Case 2 reverses the starting order.
Every invocation uses a fresh network. Residual compilation remains included.
Automatic preparation is faster in four of five Case 1 pairs and three of five
Case 2 pairs. Median paired savings are 0.512 s and 0.140 s respectively.

The complete timer includes import, power-flow preparation, dynamic/controller
initialization, ten simulated seconds, full recording, selected CSV export,
writer closure and run metadata. Hashing, integrity comparisons and duplicate
storage sharing occur afterward. Both modes use logical CPU 6 only, with OS
affinity mask 64 checked before and after the JVM, Java 21, `-Xmx2g`, one active
processor, Serial GC, `-Xbatch`, `MaxInlineLevel=30` and `FreqInlineSize=600`.
No test build or intrusive profiler overlaps the timed cohorts.

Both modes retain ordinary `MODIFIED_EULER`, pure Java KLU, quarter-cycle
steps (`1/240 s`), a `1e-5` pu complex voltage-correction criterion, two minimum
fresh iterations and 50 maximum. The bus-6307 fault runs from 1.000 to
1.083333 seconds. Generator-priority solved netting, current-P/impedance-Q
conversion and 0.001 pu power-flow mismatch tolerance are unchanged. The same
existing optimized network/recording flags are selected in both arms; this
study does not promote those separate numerical profiles to new defaults.

| Coverage or region median | Case 1: previous → automatic | Case 2: previous → automatic |
| --- | ---: | ---: |
| Compiled controllers | 692 → 692 | 221 → 221 |
| Ordinary direct ST1 controllers | 0 → 114 | 0 → 36 |
| Ordinary direct ST1 stages | 0 → 547,884 | 0 → 173,016 |
| Direct ST4B / PSS2A controllers after preparation | 208 / 333 | 64 / 103 |
| Device integration region | 4.207 → 3.735 s | 1.568 → 1.466 s |
| Network region | 2.905 → 2.876 s | 2.785 → 2.790 s |
| Setup/initialization region | 0.436 → 0.355 s | 0.461 → 0.328 s |
| Network iterations | 20,559 → 20,559 | 22,732 → 22,732 |

The new code removes generic ST1 evaluation work and supplies automatic
preparation while preserving compiled coverage. Both modes retain 485 frequency
buses, 7,278,060 passive-bus stage skips, 2,399 checked INITIAL endpoint reuses
and three auxiliary factorizations. The device-region medians fall by 0.472 s
and 0.103 s. Region medians are separate statistics and do not add into an
exact decomposition of whole-run medians. In particular, Case 2 demonstrates
only a small whole-run gain despite reduced device work.

All 36 warmed-batch recordings and both two-run pilots retain 2,405 samples,
including two negative-time startup samples and both event sides. Case 1 keeps
6,190 channels and Case 2 keeps 4,810, including all 2,000 bus voltage magnitudes
and angles. Binary traces, selected CSV and channel metadata match both the
control arm and the previous optimized recordings byte for byte. No offsets,
time shifts, resampling or channel reduction are used.

This establishes numerical nonregression for controller preparation. The
[existing optimized profile's accuracy limitations](texas2k-default-euler-performance-2026-10-25.md#accuracy-limits-and-tighter-controls)
remain unchanged; byte identity does not establish new independent reference
acceptance. These matched controls measure this change more directly than
unmatched historical minimum timings.

The [evidence checkpoint](checkpoints/modified-euler-controller-preparation-2026-10-25.json)
retains all measured times and coverage, full-reactor reports, exact first-party
jars, source snapshots/patch, runner, classpath resources, input/dependency
hashes, affinity and failed attempts. Directory deltas and byte-verified
generated-output sharing limit additional storage. Supplied references and
previous manifest dependencies remain intact.
