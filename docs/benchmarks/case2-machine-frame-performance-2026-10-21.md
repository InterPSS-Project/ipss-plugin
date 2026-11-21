# Case 2: combined executable performance

The new working executable reproduces a **7.9112594-second warmed median** for
ten simulated seconds, with a `1e-5` complex-voltage correction tolerance,
quarter-cycle steps, one logical processor, fresh case preparation and full
recording. The first extended run measured 7.899725 seconds; an independent
archive-only replay reproduced the result.

This is a median result, not an eight-second upper bound. Two of seven measured
archive-replay cases exceeded eight seconds. The goal remains active; production
source integration and independent-reference accuracy are also unresolved.

## Execution path

The selected executable combines the previously frozen runtime with production
machine-frame reuse. It disables cached current traversal and primitive current
transfer, following the earlier conditional test that found their combined
disablement faster on this runtime. Voltage-dependent currents still refresh.

Machine-frame reuse caches only sine and cosine for an unchanged rotor angle.
It retains live voltage/current operands and the existing arithmetic order. The
overlay contains the machine base class, the round-rotor and salient-pole class
families, their helper classes, and a runner with explicit reuse selection.
The frame-transform helper is added to the frozen runtime. No controller
equations, tolerances, integration stages or recording channels were removed.

The resulting executable contains 5,288 directory classes. It remains a
reconstructed executable, not an exact historical source checkout. The production
repositories were not rolled back or replaced.

## Complete-case measurements

| Comparison | Warmups / measurements | Median seconds | Measured range, seconds |
| --- | --- | --- | --- |
| Production participant cache disabled | 12 / 6 | 9.609221 | 9.389092–9.816565 |
| Experimental production participant cache enabled | 12 / 6 | 9.482661 | 9.186439–9.683277 |
| Combined frozen runtime, frame reuse disabled | 12 / 5 | 8.354058 | 8.191382–8.639530 |
| Combined frozen runtime, frame reuse enabled | 12 / 5 | 8.086571 | 7.903513–8.497310 |
| Combined runtime, extended warmup | 24 / 7 | 7.899725 | 7.810444–8.704136 |
| Independent archive-only replay | 24 / 7 | 7.911259 | 7.816616–8.569965 |

The participant-cache difference is 0.12656045 seconds, with overlapping ranges.
It does not explain the original production-versus-frozen gap. The matched
machine-frame comparison improves the median by 0.2674875 seconds. These are
separate pairs; their effects must not be added as a timing decomposition.

The extended protocol was declared before its measurements: 24 full warmups,
then seven complete measurements. Every measurement is retained. Compilation
continued after twelve warmups, motivating the longer protocol; compilation time
was never subtracted from wall time. The independent replay uses the same 24/7
protocol in a fresh JVM.

Independent replay measurements, in order:

`8.101799, 8.5699651, 7.9954821, 7.8189051, 7.9112594, 7.8808672, 7.8166163` seconds.

Each run includes importing a fresh case, power-flow preparation, dynamic
initialization, simulation and recording. File-equality checks and hard-link
deduplication occur after the measured case. JVM flags retain a 2 GiB heap,
one active processor, serial garbage collection and the prior compilation
configuration. OS process affinity is checked before and after the campaign.

## Numerical and lifecycle evidence

All 62 repetitions in the two extended campaigns produced the same complete
recording, including the warmups. Their SHA-256 matches the previous baseline:

`123b1cce4ad4d4087e7dc294b47031bb1bfc52436dd086d588e531d5936f675f`

The recording retains 2,405 samples, 4,810 channels and event-side duplicates.
Inputs and external dependencies are verified against the original frozen
manifest. Archive replay contains no mutable build directories on its runtime
classpath. Four focused machine-frame tests passed against the combined runtime.
Trajectory identity qualifies this optimization against the retained baseline;
it does not resolve independent-reference discrepancies.

The separate participant-cache prototype passed 18 focused discovery/lifecycle
tests. It tracks branch edits and falls back after raw list storage escapes.
However, source review found an unhandled effective-machine replacement path:
`DStabGenImpl.setDynamicGenDevice()` can restore an earlier machine without
incrementing the membership version when `genDevice` already refers to that
object. The prototype is **not adopted**. Its tests do not establish complete
mutation coverage, and the cache is not included in the new machine overlay.
The previously frozen runtime retains its existing participant handling.

## Recovery and next work

The selected checkpoint is recorded in
[the working baseline index](checkpoints/case2-working-performance-baseline.json).
[The machine-frame checkpoint index](checkpoints/case2-frozen-machine-frame-2026-10-21.json)
pins the original executable archive, a small overlay archive, class origins,
machine sources, runner sources, test evidence and retained measurements. A
separate replay manifest retains the independently staged class inventory and
replay evidence. Existing references remain untouched. Verified duplicate
generated outputs share storage through hard links.

Replay with a new output directory:

```powershell
python tools/run_bounded.py --log target/<new>.log -- python tools/replay_case2_fast_checkpoint.py --machine-frame-reuse --output target/<new>
```

The runner requires the private archives, matching dependencies and retained
case inputs. It verifies hashes and staging budgets before execution, then
checks settings, CPU affinity, repetition counts and complete recording hashes.
Without the additional flag it still replays the original frozen executable.

The next work should establish consistent timing below eight seconds and move
the validated execution mechanisms into a reproducible production source path.
The extra production convergence scan remains an unmeasured candidate; the
participant cache requires the additional invalidation fix before promotion.
Independent-reference accuracy remains a separate required qualification.
