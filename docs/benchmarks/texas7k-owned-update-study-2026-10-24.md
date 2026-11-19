# Texas7k device, frequency and recording study

The latest experimental whole-run path measures 23.792 and 24.436 seconds
after four complete warmups: a 24.114-second median in this small matched
batch. Every recorded value matches the retained numerical control. The
20-second objective remains open. The complete network/update optimization
remains experimental; an exact frequency-angle arithmetic shortcut has been
committed to core as `4b87c8724`.

## Production frequency-angle shortcut

Bus frequency measurement reduces the change in angle with
`Math.IEEEremainder(delta, 2*pi)`. When `delta` is already in the closed
principal interval, the nearest-even quotient is zero and the remainder is
exactly `delta`. The production implementation returns it directly in that
case and retains the library reduction for larger jumps and nonfinite inputs.
The final addition to the stored unwrapped angle retains its original order.
Filter equations, integration stages, time constants, event rebasing and
initialization are unchanged.

Validation covers:

- Two million arithmetic inputs, including principal-interval boundaries,
  signed zeros, subnormals, large finite values, infinities and NaNs. Finite
  results and signed zeros match bit for bit; NaN classification is unchanged.
- 300,000 state/history comparisons across 20,000 predictor/corrector stages,
  five time steps, large angle jumps and 75 event rebases.
- The complete core reactor: 3,099 tests, zero failures, errors or skips.
  The existing frequency regression class now has five passing tests, including
  new library-reduction comparisons through the public filter interface.
- Both existing independent converter checkpoint methods, using Java KLU and
  unchanged tolerances.
- A complete 7K run loading the actual freshly built production class. Its
  bytecode hash is checked explicitly, and every recorded value matches the
  numerical control through the experimental network/update path.

The installed dependency-cache jars were preserved for prior checkpoint
reproduction. Validation loads the freshly built core classes explicitly.

## Whole-system recording and numerical settings

Every completed full run retains 2,403 sample boundaries, all 6,717 bus
voltages and 15 selected machine channels: 16,176,996 float64 measurements.
Physical time, solver time and both event sides are preserved. The step remains
1/240 s for ten seconds. Network tolerance remains 1e-4 pu, with two minimum
iterations and a maximum of 50. There are 7,200 network calls, 31,599
iterations and three auxiliary factorizations. The observed maximum is six
iterations in one call. All numerical solvers are Java implementations.

The control is the event-safe converter-compensation formulation from the
[previous convergence study](texas7k-converter-compensation-study-2026-10-24.md).
Its complete trace hash is unchanged throughout this study. This establishes
implementation equivalence to that numerical control; independent complete
network-reference acceptance remains open. Only three machine rotor angles
are recorded in the full-network trace.

## Device and frequency scheduling

The case has 421 active GGOV1 governors. The experimental update plan groups
eligible machines by bus and advances their local machine/controller stages
with four owned workers. All machines at a selected bus retain their original
relative order. Bus attribute updates and public bus-frequency publication
retain the original serial order. Unknown types, observable objects and
unsupported dependencies retain ordinary execution.

The first full attempt failed before integration because custom equation
models can deliberately lack an annotation-wrapper list. That failed attempt
and its source are retained. The corrected plan handles those models and
advances 421 active machines in each of 4,800 integration stages: 2,020,800
machine updates per full run. The plan also includes inactive records, which
remain inactive.

A subsequent variant balances groups across workers and computes standard
bus-frequency filters in the same parallel batch. Corrected frequency outputs
are buffered and published at each bus's original location in the serial loop.
This preserves what frequency-dependent controllers observe during the stage.
The source audit finds no controller reads of the frequency block's internal
storage. A focused check compares 50,400 raw state/history values, including
the filter's predictor history, across both convergence norms and 60
observer-triggered fallback steps. It also checks 840 owned machine advances.

General mutation and lifecycle contracts still need qualification before this
whole-runtime path can be promoted. These checks do not claim universal
parallel safety for arbitrary user controllers.

## Matched measurements

All times below include import, power flow, initialization, stepping,
in-memory recording, binary export and recovery tables. Each row reports the
two measured whole-run values from its own matched JVM batch.

| Experiment | Control measurements | Candidate measurements |
| --- | ---: | ---: |
| Parallel numerical network rows | 35.515 / 26.030 s | 26.861 / 27.014 s |
| Scalar short controller expressions | 27.123 / 25.868 s | 27.381 / 25.459 s |
| Owned machine updates | 26.495 / 26.489 s | 26.031 / 26.126 s |
| Balanced machine and frequency updates | 26.226 / 26.786 s | 24.546 / 24.839 s |
| Exact local-angle shortcut on the balanced path | 25.150 / 27.693 s | 23.792 / 24.436 s |

The parallel row experiment does not establish a network-time improvement;
its slower control includes an export/compilation outlier. Scalar expressions
pass 33,600 arithmetic/read-order comparisons, three existing binding contracts
and 50,880 controller state/history comparisons, but their measured timing
gain is not established. Neither is the preferred runtime optimization.

The scalar-expression batch uses logical CPU 6. The worker experiments use
the allowed logical processors 2, 4, 6 and 8. Within each batch, controls and
candidates use the same affinity and JVM configuration. Cross-batch differences
must not be attributed solely to one source change. The sample counts are too
small for a stable cross-host performance claim.

Two early-worker-wake experiments are rejected. The first activated workers
after the corrector solve, leaving them active across work that did not lead
immediately to integration; that experiment was intentionally stopped after
verifying its process identity. Restricting activation to the initial and
predictor stages still measures 39.759 and 49.735 seconds. Parked workers
remain preferred. All completed wake-experiment traces still match exactly.

## Recording and compilation findings

Phase timers identify an 8.615-second recovery/export outlier in one matched
control. Bulk binary serialization preserves the exact file bytes. In the
latest measured batch, binary encoding/writing is approximately 0.06–0.08 s
and recovery-table computation is separately recorded. Recording stays enabled;
no samples or channels are removed.

An earlier retained bounded profile records 10.660 seconds of compilation
events, including two compilations of the benchmark driver totaling 7.284 s.
That profile does not directly attribute any particular unprofiled outlier.
The latest batch also records compilation-time counters around each run:
early warmups consume 12–18 seconds of compiler time, whereas subsequent
measurements are much lower. Compiler-time counters aggregate compiler work
and are not interchangeable with application elapsed time. They demonstrate
why complete warmups and explicit phase timing are required.

Further work must reduce the remaining network, publication and update cost
while checking trajectories and lifecycle behavior. The 20-second objective
has not been met, and the experimental whole-runtime path is not promoted.

## Retained evidence

The [checkpoint](checkpoints/texas7k-owned-update-study-2026-10-24.json)
indexes 136 private files and 306 external artifacts. It retains production
source and bytecode, regression reports, the full core validation log, source
overlays, compiled prototypes, reproducers, successful and rejected controls,
phase timings, affinity records and full traces. All indexed hashes were
checked after capture.

There are 48 complete identical traces and two retained partial traces.
Sharing hash-identical generated files saves 1,165,176,360 physical bytes.
All paths, channels, samples and event sides remain available. Supplied
references and previously installed dependency jars are unchanged.
