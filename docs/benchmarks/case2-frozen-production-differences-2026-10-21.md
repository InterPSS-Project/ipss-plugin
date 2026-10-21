# Case 2: frozen executable versus production

The frozen executable retains two network optimizations that the current
production path does not: convergence checking during voltage publication, and
a cached list of network-iteration callback participants. These are the strongest
remaining candidates for investigation. Their individual timing effects have
not yet been measured.

This audit changes no simulation code. It compares the effective classes used
by the retained runs, disassembled method instructions, available source
variants, input files, external dependencies, and run settings.

## Compared versions and timings

| Item | Frozen working baseline | Current production path |
| --- | --- | --- |
| Identity | Executable indexed by `e2e0a892f` | Core `7a9808da4561612b5236d6a03abfe7c6238fb707`; plugin audit HEAD `6578ca8f7e86fae4e3534d7151943f594c0f4a79` |
| Complete-case median | 8.5065261 s | 9.6082251 s |
| Measured runs after 12 warmups | 5 | 6 |
| Setup/initialization median | 0.3798433 s | 0.31810215 s |
| Simulation/output median | 8.132447 s | 9.1890786 s |

The complete-case difference is **1.101699 s**. These are retained measurements
from separate benchmark sessions, not a newly interleaved timing experiment.
Phase medians need not add to the complete-case median. The phase measurements
place the gap in simulation/output rather than setup; they do not isolate
recording time from simulation work.

Both runs include fresh case preparation and full recording, use one logical
processor, simulate ten seconds at a quarter-cycle step, and require `1e-5`
complex-voltage correction convergence. Both use `1e-3` pu load-flow tolerance.

The frozen artifact is a reconstructed executable assembled from retained
classes and experimental overlays. It is **not an exact historical source
checkout**. The source tree has not been replaced by it. The production audit
uses the effective build directories from the measured production command;
it does not claim that unrelated uncommitted source edits were rebuilt.

## Concrete implementation differences

| Area | Frozen executable | Production path | Interpretation |
| --- | --- | --- | --- |
| Network convergence | A voltage-publication observer computes correction and updates indexed previous voltages while solved voltages are published. | Publishes voltages, then calls `IndexedVoltageRecordTable.scan` in a separate pass. Scalar voltage access is enabled, but the extra pass remains. | A fused pass was not carried into production. Individual timing effect unmeasured. |
| Iteration callback participants | `IterationBinding[]` is cached by network identity and membership version; reusable collections filter live active devices and deduplicate identities. | Allocates collections and scans buses, generators, branches and dynamic-device lists for each network solution. | A participant cache was not carried into production. Its membership invalidation hooks must accompany any port. |
| Current assembly and solver transfer | Uses cached runtime participants and primitive current buffers; 36,370 buffered solves are recorded. | The measured configuration uses ordinary current transfer and live traversal, with guarded scalar static-load arithmetic. | A real path difference, but not established as a speed advantage: disabling cached current traversal and primitive transfer together previously improved the frozen stack. |
| Controller evaluation | Specialized ST1, ST4 and PSS2A direct evaluators, including experimental output/bridge reuse. | Compiled-stage lifecycle with preparation, reuse guards, invalidation and fallback handling; typed PSS2A wiring and guarded ST4 bridge-input reuse are already present. | Not simply interpreted versus compiled. Recent PSS2A probes account for only a small, uncertain part of the gap. |
| Voltage representation | Deferred-angle and rectangular-voltage caching in the base load-flow bus implementation. | Deferred-angle publication localized to the dynamic bus implementation, with observer/type guards and inherited-cache invalidation. | Both avoid unnecessary polar conversions; placement and guard costs differ. |
| Machine update optimizations | Does not have all current production optimizations. | Adds cached frame transformations and guarded electrical endpoint preparation. | Production also contains improvements absent from the frozen runtime; this is not a simple rollback comparison. |

In particular, production `DStabSolverImpl.networkIterationParticipants()`
constructs a new list and identity set and walks the network on every call.
The frozen implementation uses `iterationBindings` and reuses its collections.
Its branch/device membership hooks are part of the cache contract: copying only
the cached array would risk stale registrations.

Production `iterateNetworkSolution()` calls `solveNetEqn()` and then the voltage
record table's `scan()`. The frozen implementation installs its publication
observer around `solveNetEqn()` and reduces the correction statistics afterward.
A production port must preserve finite-value checks, the selected norm, live
voltage-dependent injections, diagnostics, observer behavior and fallback paths.

## What matches

- All four case input files match by SHA-256.
- External dependency files, classpath order and hashes match.
- The plans differ only in the explicit `rectangular_network_voltage` option:
  production sets it to true; the frozen implementation already performs
  rectangular publication through its experimental path.
- Heap, single-processor JVM setting, garbage collector, compilation settings
  and sparse row-solve selection match. The frozen command additionally enables
  experimental primitive current transfer.
- Full-network and selected-channel recorder methods have the same normalized
  disassembled instructions. So do the sparse triangular-solve kernel and the
  compared load-flow preparation methods.
- Retained full recordings have the same SHA-256:
  `123b1cce4ad4d4087e7dc294b47031bb1bfc52436dd086d588e531d5936f675f`.
  They contain 2,405 samples and 4,810 channels, including event-side duplicates.

Matching trajectories establish equivalence for this retained Case 2 run;
they do **not** establish independent-reference accuracy. That qualification
remains unresolved for both versions.

## Evidence and limits

The effective directory inventory contains 5,148 byte-identical classes,
116 classes with different bytes, 23 frozen-only classes and 11 production-only
classes. Total counts are 5,287 frozen and 5,275 production, including runner
classes. Byte differences include compiler metadata and generated helpers;
they are not 116 independent behavioral changes.

All 116 differing classes were disassembled. Comparison normalizes constant-pool
indices and whitespace while preserving instruction comments. Equal normalized
methods are useful evidence but do not prove equality of all class metadata or
bootstrap behavior. The retained source variant for the frozen staggered solver
is older than its effective class; the executable is authoritative for that
class, so a complete exact-source diff cannot be claimed.

Previous conditional tests also constrain interpretation:

- Disabling frozen cached current traversal and primitive transfer together
  reduced its median from 8.5102 to 8.2016 seconds. That combined test cannot
  assign the change to either component independently.
- The recent combined direct PSS2A stage/output probe changed production from
  9.6912 to 9.5461 seconds, with overlapping run ranges. Output-only reuse
  changed 9.6027 to 9.5813 seconds. Neither explains the full gap.
- These effects come from different configurations and sessions and must not
  be added into a purported timing decomposition.

The next discriminating experiments should restore the participant cache with
complete invalidation, then fuse convergence with voltage publication, one at
a time. Each needs warmed complete-case timing and unchanged full recordings.
The audit identifies missing mechanisms; it does not yet assign the full
1.10-second gap to them.

Related evidence:
[working baseline](checkpoints/case2-working-performance-baseline.json),
[frozen checkpoint](checkpoints/case2-recovered-fast-2026-10-21.json),
[conditional ablations](checkpoints/case2-fast-stack-ablations-2026-10-21.json),
[PSS2A probes](checkpoints/case2-pss-direct-evaluation-probe-2026-10-21.json),
and [this audit's retained inventory](checkpoints/case2-frozen-production-diff-2026-10-21.json).
