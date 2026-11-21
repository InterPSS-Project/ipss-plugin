# Texas7k compiled-controller and numerical-kernel study

The best new three-measurement whole-run median is **21.407 seconds** with
default JVM inlining, one logical CPU and full recording. A subsequent batch
combining two numerical kernels measures **24.302 seconds** across five runs.
The 20-second objective remains open. These are isolated experimental runtime
overlays; this checkpoint does not replace production runtime code.

## Active devices and comparison with Texas2k Case 1

The initialized 7K inventory counts active objects, rather than DYR records.
Four additional attached converter records are inactive and are excluded.

| Object or workload | Texas2k Case 1 | Texas7k | Change |
| --- | ---: | ---: | ---: |
| Synchronous machines | 333 | 463 | +39.0% |
| Renewable converter bundles | 96 | 167 | +74.0% |
| Generating units | 429 | 630 | +46.9% |
| Exciters | 333 | 463 | +39.0% |
| Governors | 333 | 463 | +39.0% |
| Stabilizers | 333 | 203 | −39.0% |
| Machines/bundles plus these three controller categories | 1,428 | 1,759 | +23.2% |
| Buses | 2,000 | 6,717 | 3.36× |

A renewable bundle includes its internal controls; this is not a count of
individual differential states or elementary control blocks. All 203 7K
stabilizers are IEEEST, whereas Case 1 has 333 PSS2A models. The 7K governor
population is 421 GGOV1, 23 IEEEG1 and 19 HYGOV. Its exciters comprise 307 ST4B,
19 IEEET1 and the 137 units across eight families added in the earlier support
slice. These differences make per-object counts an incomplete cost predictor.

The originally quoted 11.404–12.287-second 7K integration measurements include
device and bus-frequency updates. Case 1's retained 4.883-second integration
attribution also includes direct event-network work and comes from a different
solver snapshot. Their numerical ratio is approximately 2.3–2.5, but it is not
a matched performance comparison. The initialized inventory and the
[earlier device profile](dynamic-device-profile-2026-10-21.md) support the
counts above; no matched new 2K timing was run in this slice.

## Whole-run measurements

Every measurement creates a fresh network and includes import, power flow,
initialization, ten simulated seconds, in-memory recording, binary export and
recovery tables. Warmups are complete runs. The quarter-cycle step, current-P /
impedance-Q load profile, five-cycle fault and recording remain unchanged.
Affinity is verified before and after every batch; device updates are serial,
with `ActiveProcessorCount=1`, Serial GC and a bounded 2 GiB heap.

| Experiment | CPU | Warmups / measurements | Median whole time | Measured range |
| --- | ---: | ---: | ---: | ---: |
| Generic controller stage, paired batch | 2 | 6 shared / 3 | 31.137 s | 28.032–32.144 s |
| Typed IEEEST stage, same paired batch | 2 | 6 shared / 3 | 30.174 s | 26.201–30.897 s |
| Typed stage, default JVM inlining | 6 | 3 / 3 | 21.407 s | 21.265–22.091 s |
| Typed stage plus folded loads and JDK angle kernel | 6 | 6 / 5 | 24.302 s | 23.468–30.851 s |

The paired controller ranges overlap. The two CPU-6 batches are separate JVM
trials and do not establish a causal speedup or slowdown for the combined
kernels. A preceding CPU-6 trial with aggressive inlining also costs more than
the default-inlining trial; its process/main-thread CPU counters and compiler
times are retained. Default-inlining measurements still contain 204–861 ms of
compilation, which remains included in the reported durations.

The default-inlining device-and-measurement region measures 6.858–7.078 seconds
and its network region 11.479–11.566 seconds. In the last combined measurement,
the corresponding times are 8.023 and 13.175 seconds. Process CPU measurements
also vary, so the whole difference cannot be assigned to waiting or scheduling.
CPU counters do not distinguish clock variation, sibling-thread contention and
memory stalls. No stable combined-kernel runtime gain is established.

The conservative outer invocation medians, including final summary output,
are retained separately from the internal whole-run timers in the
[checkpoint](checkpoints/texas7k-compiled-kernel-study-2026-10-25.json).

## Implemented experiments and checks

- **Typed IEEEST stage:** fixed Java wiring reads operands in the original
  order and preserves arithmetic, existing block kernels, state storage,
  predictor/corrector stages and every preparation pass. Exact-class, wiring
  and wrapper checks retain the generic evaluator for custom structures.
  All 72,204 state/output comparisons, 203 live-parameter checks and the custom
  provider fallback pass. Five existing independent-reference methods also
  pass unchanged, exercising 20 typed models and 48,102 preparations. Every
  recorded full-network value is identical to the retained trajectory.
- **JDK voltage-angle kernel:** the exact standard complex value uses the JDK
  angle calculation through the existing lazy cache; custom values and
  observers retain the established path. All 600,051 cache/angle contract
  checks and four existing converter profiles pass. The checked individual
  angles differ by at most 4.441e-16 radians; all recorded full-network values
  are identical.
- **Folded I/Z load current:** coefficients are compiled at a coherent network
  boundary, while voltage-dependent currents refresh at every trial voltage.
  Constant-P, incoherent, cutoff and unsupported cases retain the original
  writer. The model and existing admittance stamp are preserved; switched
  shunts are not added again. The 661,120 component checks have a maximum
  scaled difference of 1.255e-14, and 50,400 state/history comparisons have a
  maximum difference of 8.492e-14. Observer fallback and four existing converter
  profiles pass. The small converter profiles alone do not prove positive
  folded-row coverage; the explicit I/Z probe and full case provide it.
- **Stage voltage guesses:** guarded predictor/corrector initial guesses keep
  fresh current evaluation and the original convergence test. Contract and
  small-system checks pass. Full-run iterations fall only from 26,803 to
  26,631, or 26,628 with the corrector guess. This is below a 1% reduction;
  neither variant was selected for a warmed timing qualification.

Two initial fixture/coverage attempts failed: one tried changing load treatment
after initialization, and another omitted recompilation after a test explicitly
reinitialized its controller. The corrected final fixtures pass without changing
engine guards or reference tolerances. Failed logs are retained.

## Numerical and storage contract

All complete runs retain **2,403 samples × 6,732 channels**: 6,717 bus-voltage
magnitudes plus five quantities for three selected machines. Both event sides,
physical time and solver time remain. All eleven combined-batch traces have
the same SHA-256, matching the standalone folded-load run. Its current
regrouping changes roundoff relative to the original trajectory.

Against two tighter internal controls, the folded-load result passes all
configured period limits. Worst fault/postfault voltage NRMSE is **0.000698**,
using the full-ten-second reference range; maximum voltage difference is
**0.001603 pu**. Worst full-run RMSE among the three recorded rotor angles is
**0.0006162°**; prefault rotor differences are zero. These are internal
convergence checks, not independent complete-network reference acceptance.

The combined run keeps the 1e-4 pu dynamic convergence tolerance, 50-iteration
ceiling and ordinary fresh-solve convergence test. It makes 26,803 iterations,
has a maximum of six and reuses 2,397 fingerprint-checked accepted INITIAL
endpoints. The auxiliary pure-Java converter treatment factors three times
and reuses those factors. No native sparse backend or integration worker is
used.

The private manifest indexes the tested source snapshots, bytecode archives,
JVM/affinity records, full traces, numerical checks, failed attempts and bounded
logs. It references the earlier frozen core archive. Identical generated traces
share storage only after hash verification. Supplied references are preserved.
The inherited `run.json` field named `source_commit` contains a command
placeholder; executable provenance comes from the archived hashes and retained
classpath, not that field. Concurrent later production changes are excluded
from these frozen-runtime measurements.
