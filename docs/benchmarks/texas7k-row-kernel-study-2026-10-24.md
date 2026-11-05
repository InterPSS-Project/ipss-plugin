# Texas7k row-kernel and direct-controller experiments

These isolated experiments retain ordinary modified Euler integration, the
quarter-cycle network step, 1e-4 pu voltage-correction tolerance and the
50-iteration ceiling. The additional 137 exciter ports remain available in
the opt-in solver; its separate full-network disturbance-accuracy gap remains
open. No experimental runtime classes in this study are promoted to production.

Every completed run records all 6,717 bus voltages and 15 selected machine
channels at all 2,403 sample boundaries, including both event sides. Each trace
contains 16,176,996 float64 measurements. Byte identity is checked against the
retained ordinary control, including sample times and event kinds. This is an
implementation-equivalence check, not independent full-network validation.

## Parallel row work

Four persistent workers evaluate disjoint current and voltage rows; the
triangular solve remains serial. The first prototype uses phased barriers;
the second separates the row kernels and uses release/acquire completion with
parking outside network batches. Both produce the exact control trace and
the same 130,955 iterations across 7,200 network calls, with a maximum of 27.

| Four-worker prototype | Cold whole-run time |
| --- | ---: |
| Phased barriers | 108.011 s |
| Release/acquire completion | 98.583 s |

These runs use logical CPUs 2, 4, 6 and 8. They do not demonstrate a speedup
over the earlier scalar indexed prototype, whose cold whole-run time was
93.970 s. Warm scalar measurements are not presented as equivalent cold trials.
The added coordination did not produce a whole-run gain in these trials.

A factor-structure probe finds one block, 28,838 lower and 28,838 upper terms.
Small trailing separators leave one dominant component. This limits a simple
coarse partition of the triangular solve; no parallel factor solve was added.

## Direct ST4 evaluation

The ordinary integration driver explicitly enables the existing direct ST4
evaluation graph after initialization. It retains block kernels, preparation
passes and integration stages. All three recorded traces are byte-identical
to the ordinary control.

| Phase | Whole run | Network |
| --- | ---: | ---: |
| Direct warmup | 90.779 s | 61.754 s |
| Direct measured | 79.856 s | 58.853 s |
| Indexed control measured | 83.186 s | 62.180 s |

This order does not include an independent indexed warmup in that JVM. Setup,
compilation and unchanged network work also vary. The single comparison does
not establish a stable direct-controller speedup.

## Static-row snapshots

A separate prototype prebinds standard static-only rows and refreshes their
ZIP coefficients, load-change factor, voltage curve and stamped-admittance
settings at each network-call boundary. Each iteration still evaluates current
from the latest voltage. Device rows keep their existing live dispatcher;
matrix and bus voltages are still published on each iteration.

The focused probe passes 64,416 raw-bit comparisons against the existing row
writer. It covers both voltage curves, stamped and unstamped compensation,
changed load settings and low-voltage boundaries. Custom arithmetic values
are rejected by the snapshot path.

One warmup for each implementation followed by one measured run for each,
in the same JVM on logical CPU 6, gives:

| Phase | Whole run | Network |
| --- | ---: | ---: |
| Indexed warmup | 94.105 s | 62.162 s |
| Snapshot warmup | 81.203 s | 58.472 s |
| Snapshot measured | 77.737 s | 56.038 s |
| Indexed control measured | 99.818 s | 76.184 s |

All four traces have the exact control hash and unchanged iteration counts.
This single, unbalanced pair is promising, but the control time varies
substantially from earlier runs. It does not establish a stable median or
justify a proportional speedup claim. The machine-readable checkpoint retains
all timing records and full trace hashes.

Arbitrary callbacks that change static parameters within a
network call, observer changes, membership replacement and ownership need
explicit capability/fallback contracts and permanent regression tests before
this snapshot mechanism can become a production API.

The next substantial reduction requires numerical voltage/current storage
through the nonlinear iteration, with carefully guarded publication to bus
objects. Voltage-dependent injections must continue to refresh on every
iteration. Removing eligibility checks or suppressing observable callbacks
without those contracts would change the runtime semantics.

The [checkpoint](checkpoints/texas7k-row-kernel-study-2026-10-24.json) indexes
private prototype sources, compiled overlays, complete traces, affinity
records, bounded logs and hashes. The 20-second whole-run objective remains
unmet. Production code and the existing accuracy gates are unchanged.
