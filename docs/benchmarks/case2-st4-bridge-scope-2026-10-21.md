# Case 2: complete ST4 bridge reuse

An isolated prototype reuses the complete ST4 rectifier-bridge result inside a
single controller evaluation. The paired complete-case median changes from
**9.399965 seconds** to **9.322350 seconds**:
a reduction of **0.077615 seconds (0.83%)**.
The candidate is faster in 6 of seven adjacent control/candidate pairs.
It has not replaced production source and does not meet the eight-second target.

The gain is too small to explain the frozen/production gap. This experiment is
retained without production adoption; broader system qualification was not run.

## Implementation

The original frozen executable caches the complete `calcVB(calcVe())` result.
Production caches selected numerical inputs but still reads machine current,
calculates field current and evaluates the bridge repeatedly. The prototype
keeps the production integration lifecycle and equations and adds one temporary
scope around each Euler stage or output evaluation.

Reuse requires the exact standard controller, machine, machine-data, generator,
bus and network types; an eligible compiled controller graph; no machine or bus
observers; and either no stabilizer or already-held standard stabilizer output.
Unsupported configurations retain the ordinary reads. The first read evaluates
the ordinary equations. Later reads reuse a finite bridge value while still
multiplying by the current block input. Scope flags and the temporary value are
cleared in `finally`, including failed advances. Parameters remain live between
evaluations. Each candidate case records 5,152,994 reuses across 936,908 scopes.

## Numerical checks

- All 33 focused tests pass. Coverage includes coupled faults, live parameter
  changes, held/unheld stabilizer output, custom providers, observers, graph
  invalidation and failure recovery.
- Two control runs and one candidate run match all 12,923 accepted-state hash
  groups over 2,402 accepted steps, covering 164,591 numerical slots. The audit
  reads stored values without invoking model getters, includes controller
  history and changing complex values, and excludes listed diagnostic counters
  and optimization-policy flags. The raw audit is retained.
- Full binary and selected-channel recordings match throughout the completed
  campaigns. The binary hash is
  `123b1cce4ad4d4087e7dc294b47031bb1bfc52436dd086d588e531d5936f675f`.
  The 2,405 samples, 4,810 channels and event-side duplicates are preserved.

These checks establish scoped equivalence to the retained production trajectory.
They do not establish independent-reference accuracy or model verification.
The first two-case audit preceded clearing the inactive temporary value; the
accepted-state audit and timing experiment use the cleared value.

## Timing protocol and all measurements

One JVM alternates control and candidate cases, with 32 total warmups followed
by seven measurements per configuration. Each case starts fresh and includes
preparation, initialization and complete recording. CPU affinity is one logical
processor; the duration is ten simulated seconds, step is 1/240 second and
complex-voltage correction tolerance is `1e-5`. Both arms use the same isolated
overlay, so this measures the reuse toggle rather than a fresh production build.
Ordering is deterministic, control first; ranges and repeated measurements are
retained rather than treating the median difference as an exact cost breakdown.
Residual JVM compilation ranged from 6–79 ms in control measurements and
1–276 ms in candidate measurements. Those costs remain included; no samples
were discarded and no compilation time was subtracted.

| Configuration | Median | Range |
| --- | ---: | ---: |
| Control | 9.399965 s | 9.308220–9.444992 s |
| Candidate | 9.322350 s | 9.209132–9.492489 s |

- Control seconds: 9.3439901, 9.4449923, 9.3082199, 9.4034455, 9.3781778, 9.3999653, 9.4182302.
- Candidate seconds: 9.2091324, 9.3012274, 9.238926, 9.4924887, 9.3725889, 9.3852317, 9.3223502.

An earlier timing attempt stopped during run 37 when the two-GiB disk reserve
was reached. Its partial timings are excluded from this comparison. Its log,
statuses and incomplete recording remain retained. Storage was recovered by
sharing verified identical generated files, preserving their contents and paths;
the complete campaign was then rerun in a fresh JVM and directory.

The [evidence index](checkpoints/case2-st4-bridge-scope-2026-10-21.json) identifies
the source/class overlay, exact commands, focused tests, raw state hashes,
complete timing statuses and interrupted-attempt diagnostics. Production
integration and broader system qualification remain outstanding.
