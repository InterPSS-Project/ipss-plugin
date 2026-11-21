# Texas7k single-CPU runtime baseline

The updated objective is a complete ten-second simulation in 20 seconds on
one logical CPU, including recording and export. The preferred single-CPU
baseline currently measures 24.287–26.038 seconds, with a 24.603-second median
after six complete warmups. The objective remains open.

The Java process is pinned to logical CPU 6: requested and observed affinity
masks are both `0x40` before and after execution. Every phase records
`-XX:ActiveProcessorCount=1`, `-XX:+UseSerialGC` and one configured integration
worker. Ordinary serial updates create no integration worker threads. The
comparison batch plan executes its sole worker synchronously in the caller.

## Complete measurements

| Device update path | Three measured whole-run values | Median |
| --- | --- | ---: |
| Ordinary serial updates | 26.038 / 24.287 / 24.603 s | 24.603 s |
| One-worker machine/frequency batch plan | 26.486 / 26.552 / 28.476 s | 26.552 s |

Ordinary serial updates remain preferred. The six measured runs follow six
complete alternating warmups in the same JVM. Import, load flow, initialization,
controller preparation, stepping, in-memory recording, binary export and
recovery tables are included. Separate profiled runs are excluded from these
medians. Earlier four-processor measurements do not qualify the updated target.

## Numerical and recording invariants

All fourteen complete trajectories, including both profile runs, reproduce the
existing fingerprint-checked endpoint trace exactly:
`cf74add8e7a177512da3d7296a1460f59dd79b2ad2dda056dea493ff3bae9c09`.

Each retains 2,403 sample boundaries, every one of 6,717 bus-voltage channels
and 15 channels from three selected machines: 16,176,996 float64 values.
Physical times, solver times and both event sides are retained. No measurements
are omitted to obtain the reported timings.

The step remains 1/240 s. Network tolerance remains 1e-4 pu, with a minimum of
two iterations for fresh solves and a maximum of 50. There are 7,200 network
calls, 26,803 iterations, 2,397 accepted-endpoint reuses and three auxiliary
factorizations. The observed maximum is six iterations in one call. Every
numerical solver is Java.

Recorded accuracy therefore inherits the identical trajectory's checks in the
[accepted-endpoint study](texas7k-accepted-endpoint-study-2026-10-25.md): worst
fault/postfault voltage NRMSE 0.00069694 against the tighter internal control,
and worst full-run rotor-angle RMSE 0.00061611 degrees among the three recorded
machines. Complete independent network-reference acceptance remains open.

## Profiling observations

The retained profiles separate integration-stack samples from import and export
samples. Sparse triangular solves, voltage access, the fused network loop,
controller bindings and device/list traversal supply candidates for further
measurement. Sample counts are not elapsed CPU seconds, and inclusive counts
overlap.

Activating the first recording causes substantial additional compilation:
25.289 seconds of compilation events in the ordinary profile. That profile also
repeats expensive setup work and is not a steady timing reference. The following
one-worker profile is retained separately. Neither profile contributes to the
reported performance medians. Further optimizations require exact trajectory
checks and unprofiled matched measurements.

## Superseded worker experiment

The preceding coordinator experiment parks the caller while integration workers
finish. It passes 30,000 alternating commands, 1,457,483 publication checks,
seven propagated failures, interrupt preservation and shutdown checks. Its
machine/frequency probe also passes 50,400 raw state/history comparisons and
60 observer-triggered fallback steps.

Its four-processor timing batch was intentionally stopped after the objective
changed to one logical CPU. The process identity was checked before termination;
completed and partial evidence is retained. It supplies no single-CPU timing
claim and is not promoted. Single-CPU work does not depend on that coordinator
change.

## Evidence

The public [checkpoint](checkpoints/texas7k-single-cpu-study-2026-10-25.json)
indexes exact runtime source and compiled-class archives, launchers, all
fourteen complete trajectories, phase timings, compilation counters, CPU
affinity records, bounded profiles and their summaries. The private manifest
hash is `8bdd2b18350fd41a28fcf4d789e8eaeff735833c42c2034ed62e05b24c567188`.
Only identical generated traces share storage. Supplied references are
preserved. The runtime remains experimental; production promotion and the
single-CPU 20-second objective are unfinished.
