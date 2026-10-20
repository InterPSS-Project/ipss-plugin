# Case 2 efficiency on one CPU core

The current requirement is **15 seconds for ten simulated seconds**, at **1e-5
maximum-component voltage tolerance** and a **quarter-cycle step (1/240 second)**,
using **one CPU core**. It remains unachieved. The fully sequential experimental
candidate takes **22.8559 seconds**,
compared with **32.8651 seconds**
for the unchanged production control under the same CPU restriction.

Earlier parallel timings do not qualify for this requirement.

## CPU restriction and measurements

The harness starts Java behind a file gate, applies Windows process-affinity mask
**1**, verifies it, and then releases the simulation. It verifies the same mask
after completion. The process mask restricts all Java and native threads to one
logical processor, which is stricter than allowing both hardware threads of one
physical core. Java also uses `-XX:ActiveProcessorCount=1` and `-XX:+UseSerialGC`.
Every run below reports one available processor and **zero simulation worker
threads**. The sequential candidate removes worker scheduling from its network
and controller execution paths.

| Single-core run | Integration seconds | Network seconds |
| --- | ---: | ---: |
| single-core-control | 32.8651 | 12.7233 |
| single-core-candidate | 22.6053 | 12.3540 |
| single-core-serial | 22.8559 | 12.1193 |

The intermediate candidate used the former scheduling framework configured for
one worker; it launched no worker threads. The final sequential candidate removes
that framework. Its 0.25-second difference from the intermediate run is not an
established regression: these are single fresh-process observations. Repeat
measurements are still needed before claiming timing stability.

Integration time excludes initialization and ordinary trace serialization.
The full-channel diagnostic run is excluded from this timing table. Changing the
CPU restriction also changes JVM execution and garbage-collector behavior, so
these measurements replace the earlier unconstrained baseline.

## Retained serial optimizations

The candidate keeps direct ST1, PSS2A and ST4 controller graphs, existing block
kernels and state storage, scoped ST4 bridge calculation reuse, indexed network
access, reusable linear-solve buffers, scalar finite-load arithmetic, and
convergence calculations performed during the voltage-update pass. The current
implementation remains experimental: graph edits, unusual subclasses, variable
limits, lifecycle changes, and failure behavior still need production safeguards
and focused tests before promotion.

The model equations, floating-point operation order on the tested paths,
integration stages, tolerance, sampling and voltage-dependent injection refreshes
are retained. All runs use **4,800 network calls and 38,819 iterations**.

## Numerical evidence

The one-core sequential full-network trace matches all **2,405 samples × 4,810
channels = 11,568,050 values** of the retained unchanged production control byte
for byte. Negative history and fault-side duplicates are preserved. That retained
full control predates affinity enforcement; the newly pinned production control
also matches every selected channel. These establish numerical nonregression,
not acceptance against the supplied reference or resolution of prefault
initialization issues.

The full-trace uncompressed SHA-256 is
`8b47e58bc57c6b003ad44648d16f2abb8ddc4cda5aa7e62a89f000a00343a2d4`. Per-run affinity records and runtime hashes cover both the
production classpath and candidate overlay before and after execution.

Three earlier byte-identical generated full traces now use NTFS hard links to
their existing generated control. Every path and SHA-256 remains valid, reclaiming
**278,819,835 bytes** without deleting evidence or touching supplied references.
These retained files must remain immutable; do not overwrite any linked path.

The private evidence manifest is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-20-case2-single-core/manifest.json`.
It includes a consolidated snapshot of the twelve candidate source files.
The next work must close the remaining single-core runtime gap and complete
production integration; parallel speedups are outside the updated goal.
