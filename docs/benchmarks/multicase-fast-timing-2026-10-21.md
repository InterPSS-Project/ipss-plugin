# Cross-case qualification of the recovered fast runtime

The recovered runtime does **not yet meet the eight-second target across cases**.
Case 1 completes with a warmed median of **11.486569 seconds**. Cases 3 and 4
fail coordinated power-flow preparation before dynamic initialization, so neither
has a valid complete-simulation timing. These failures are retained as failures;
the control requirements were not relaxed to obtain a timing.

| Case | Complete-case median | Measured range | Result |
| --- | ---: | ---: | --- |
| 1 | 11.487 s | 11.428–11.571 s | Seven measured runs completed |
| 2, preceding retained session | 7.573 s | 7.542–7.966 s | Earlier baseline, not rerun in this batch |
| 3 | Unavailable | — | Three shunt controls unsettled before dynamics |
| 4 | Unavailable | — | Five shunt controls unsettled before dynamics |

## Protocol and observations

Each requested case uses the same effective source-built runtime and JVM options
as the [generic controller compatibility checkpoint](case2-generic-controller-compatibility-2026-10-21.md).
The intended protocol is a separate JVM per case, 32 complete warmup runs followed
by seven measured runs, one verified logical CPU, `1e-5` complex-voltage correction
tolerance, quarter-cycle integration and output steps (`1/240` second), and ten
simulated seconds. The load-flow power mismatch tolerance is `1e-3` pu. No timing
run overlaps another benchmark or a compilation job.

The timer includes fresh input loading, coordinated load-flow preparation,
dynamic initialization, startup history, fault application and clearing, every
recorded channel, writer closure, and status output. Byte comparisons and
consolidation of identical completed recordings occur after the timed interval.
The successful Case 1 run records all 6,190 channels at all 2,405 samples,
including two duplicate event timestamps. Every recorded value is finite, and
all 39 repetitions have byte-identical full-network and selected recordings.

The first supplied bus-fault event is used for each case: bus 7249 in Case 1,
bus 5437 in Case 3, and bus 2088 in Case 4. Application is at 1 second and clearing
at 1.0833333333333333 seconds. Original RAW, DYR and sibling preparation files are
staged and hashed; no reference trajectory values enter the simulation.

Case 1 measured totals, in seconds:

```text
11.4486841, 11.4283644, 11.5708505, 11.5577311,
11.5187311, 11.4412879, 11.4865685
```

Its median preparation/initialization time is **0.254812 seconds**, and its
median dynamics-plus-recording time is **11.228464 seconds**. Preparation takes
15 iterations before netting and zero additional iterations after netting.
The synchronous-machine inventory is 333, compared with 103 in the preceding
Case 2 run; both have 96 bound converter generators. This inventory difference
does not by itself isolate the cost of device updates from network iteration
and recording. The phase measurements locate the remaining Case 1 performance
gap after preparation.

## Preparation failures exposed by Cases 3 and 4

Both stop with `CONTROLS_UNSETTLED` at reported iteration 9,996 under a configured
10,000-iteration ceiling. The maximum electrical mismatches are only
`2.636728e-5` pu and `2.657991e-5` pu, respectively, but enabled voltage controls
remain unsettled. CPU affinity remains one logical processor before and after
each failed process; the failures are not affinity failures.

| Case | Unsettled shunt buses | Cold attempt elapsed time |
| --- | --- | ---: |
| 3 | 1007, 5191, 6151 | 18.497 s |
| 4 | 1007, 3126, 5191, 6151, 6356 | 18.666 s |

Those elapsed times describe failed preparation attempts, not ten-second dynamic
simulations. No warm timing series or dynamic trajectory was produced for either
case.

The affected RAW records have continuous shunts and colocated active generators
with both reactive limits equal to zero. Source inspection identifies a coverage
gap: `CoordinatedControlZbrNrSolver.supportsInnerSwitchedShunt()` excludes any
parent with an active generator, while `GeneratorFirstShuntLoadflow` skips
non-regulating fixed-Q generator groups and leaves their shunts to ordinary outer
adjustment. The observed failures therefore require qualification of that
preparation path before cross-case performance can be accepted. This inspection
does not yet prove which individual outer-adjustment transition causes the stall.

## Evidence and limits

The [checkpoint index](checkpoints/multicase-fast-timing-2026-10-21.json) identifies
the private archive containing commands, inputs, plans, all completed run
statuses, failure statuses, CPU-affinity records and the bounded console log.
It also identifies retained full recordings by hash, without duplicating those
large files in the archive. The Case 1 full-network recording SHA-256 is:

```text
d0068ceb29f00e87ff6136e2ee6439beb7fe51f9aca7eeee70006dfcb6205e83
```

The runtime and numerical equations were unchanged for these tests. Finite and
repeatable recordings establish successful execution, not independent-reference
accuracy. Reference accuracy and the previously documented API/system-test
qualification remain open. The earlier Case 2 timing is contextual evidence from
a preceding session; this batch is not a paired speedup measurement against it.
