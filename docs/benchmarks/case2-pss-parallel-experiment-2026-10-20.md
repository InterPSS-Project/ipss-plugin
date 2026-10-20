# Case 2 controller and parallel-network experiment

The combined experimental candidate reaches **23.6035 seconds for ten simulated
seconds**, at the unchanged **1e-5 maximum-component voltage tolerance** and
**1/240-second integration step**. The **15-second target remains unachieved**.
The production control measured 37.1794 seconds. All timings below are single
fresh-process observations; repeat timings are required to establish their
variability before claiming a reproducible speedup.

## Measured changes

| Cumulative candidate | Integration seconds | Network seconds |
| --- | ---: | ---: |
| pss-direct | 28.9311 | 17.8382 |
| parallel-network | 26.5509 | 15.9606 |
| indexed-convergence | 26.1820 | 15.3846 |
| persistent-workers | 23.6035 | 13.3991 |

The PSS2A evaluator directly binds its two child graphs, preserves operand reads
and arithmetic order, and uses the existing block kernels and state storage.
It retains all three stage passes and the existing zero-time output-refresh
convergence rule. It builds on the earlier ST1 and indexed-network experiment.

The network experiment calculates per-bus current injections concurrently and
updates per-bus voltages concurrently. It keeps right-hand-side assembly, custom
injections, factorization, and triangular solution sequential. Four work
partitions use three reusable workers plus the calling thread. Each phase waits
for every worker before proceeding; failures are propagated after all workers
finish. Workers spin briefly and then park while idle. This reduces repeated
task creation and scheduling compared with the first fork/join candidate.

Convergence records bind live table entries to bus slots once per network call,
avoiding repeated synchronized key lookup while keeping the table values current.
The tolerance, minimum iterations, full convergence scan, and diagnostics are
unchanged. Every candidate performs **4,800 network calls and 38,819 iterations**.

## Numerical verification

All **2,405 samples × 4,810 channels = 11,568,050 physical values** in the combined
candidate match the unchanged full-network control byte for byte. Negative-time
history and both fault-event sides are retained. Selected-channel traces match
for every cumulative candidate. The decompressed full trace has SHA-256
`8b47e58bc57c6b003ad44648d16f2abb8ddc4cda5aa7e62a89f000a00343a2d4`.

The full diagnostic export is excluded from the timing table. This check proves
Case 2 numerical nonregression at the same tolerance; it does not establish
acceptance against the supplied reference or resolve reference initialization
issues. The revised harness hashes both the production classpath and every
overlay class before and after each run.

## Remaining implementation work

These changes remain **isolated experiments**, not production defaults. The direct
controller assumes the standard graph remains fixed. Production needs validated
fallback or invalidation for expression edits, block replacement, reinitialization,
and custom subclasses, plus focused state/derivative and limiter tests.

The parallel path requires explicit device-independence checks, handling of bus
reordering and attachment changes, deterministic failure behavior, and worker
shutdown/reinitialization. This experiment rejects bus notification adapters,
script devices, and shared generator-device objects, but those checks do not
constitute general thread-safety certification. Its daemon workers end with the
benchmark process; they are not suitable for unrestricted network lifetimes.

Network solution still takes **13.3991 seconds** and the rest of integration
about **10.2044 seconds**. Further work must reduce both to reach fifteen seconds
while preserving the physical and numerical contract. No convergence relaxation,
endpoint skipping, sample reduction, or parameter fitting was used.

The private evidence manifest is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-20-case2-pss-parallel/manifest.json`.
Compact sources, plans, runtime hashes and bounded logs are archived. Large
traces remain listed dependencies and are not duplicated in the archive.
