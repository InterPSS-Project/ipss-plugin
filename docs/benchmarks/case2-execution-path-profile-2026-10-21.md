# Case 2 execution-path comparison follow-up

The frozen and production paths differ in controller preparation, network
traversal and convergence publication, sparse workspace management, and voltage
representation. The sparse triangular-solve kernel and recording methods match.
The original [implementation audit](case2-frozen-production-differences-2026-10-21.md)
contains the method comparison and distinguishes the original frozen executable,
selected machine-frame baseline, and later endpoint candidate.

This follow-up changes no production source. It adds profiles of production and
the frozen endpoint candidate, plus an isolated production workspace experiment.

## Profile findings

Each profile campaign uses 32 warmups followed by three recorded cases on one
logical processor. Every case includes fresh preparation, initialization and
full recording, ten simulated seconds, a quarter-cycle step, and `1e-5`
complex-voltage correction tolerance. All 70 campaign recordings match the
previously retained complete recording.

| Sampled simulation work | Production | Frozen endpoint candidate |
| --- | ---: | ---: |
| Controller execution | 24.3% | 16.5% |
| Current assembly | 21.2% | 28.3% |
| Sparse solve | 18.5% | 20.6% |
| Separate convergence scan | 7.6% | Integrated into network work |
| Recording | 1.1% | 1.1% |

These are proportions of 1,706 and 1,676 execution samples, not measured
component durations or an additive explanation of the elapsed-time difference.
The categories are selected portions of each profile. In particular, the frozen
implementation still performs convergence checking during voltage publication.
The first recorded case in each campaign incurred substantial instrumentation
startup compilation; none of these profiled runs qualifies a performance target.
All six recordings report zero lost bytes and zero truncated stacks.

Production ST4 preparation repeatedly reaches custom-block output and bridge
calculations. Its controller preparation appears more often in the profiles
than the frozen direct evaluator. Repeated EMF iterator allocation also appears
in current injection, callback discovery and step/output traversal. These are
useful optimization targets, but guards, live inputs, controller history and
device membership must remain correct.

Custom-current bus lookup also differs: the frozen implementation retains bound
bus references with table/type and membership checks. Production uses the
ordinary lookup path. No isolated speed benefit is established for that cache.

## Sparse workspace: a missed adapter difference

The earlier audit correctly found equal triangular-solve kernels but missed
their different workspace handling. Frozen `solveEqn(boolean)` reuses an
`ordinarySolveBuffer`. Production allocates `double[2*n]` on each ordinary solve.
At dimension 2,000, the measured production path makes 32,274 such allocations.

An isolated prototype leases a private workspace, handles nested calls and
exceptions, preserves separate caller-visible primitive buffers, and falls back
to fresh arrays for solver subclasses. It reduces allocations to one per case,
reusing it 32,273 times: **1,032,736,000 bytes of array payload avoided**, excluding
object headers. This is counter-based evidence, not a sampled allocation estimate.

The final focused test run passes 22 of 22 tests, including repeated RHS values,
refactorization, dimension changes, primitive-buffer isolation, nested calls,
subclass behavior and failure recovery. An earlier test incorrectly assumed a
singular one-by-one matrix must throw; the final suite uses a deterministic
injected failure to exercise recovery instead.

Both timing arms use the same prototype class with reuse enabled or disabled,
32 warmups and seven measurements. They run sequentially with the same CPU
affinity, JVM configuration, fresh preparation and full recording. The comparison
is not randomized or interleaved, so small timing differences remain uncertain.

| Workspace mode | Complete-case median | Range | Median GC time |
| --- | ---: | ---: | ---: |
| Fresh allocation, control | 9.477286 s | 9.409644–9.605661 s | 131 ms |
| Reuse, candidate | 9.455463 s | 9.321621–9.943056 s | 132 ms |

The median difference is **0.021823 seconds (0.23%)**, with overlapping ranges.
The experiment establishes a large allocation reduction but no substantial
elapsed-time improvement. It does not explain the frozen/production gap and has
not been integrated into production.

All measured complete-case seconds, in order:

- Control: 9.6056606, 9.4772857, 9.4096437, 9.4892853, 9.4728367, 9.4281742, 9.5019163.
- Candidate: 9.4124093, 9.5977269, 9.5865213, 9.9430558, 9.3216214, 9.4136825, 9.4554630.

All 80 workspace audit/warmup/measurement cases preserve the full binary and
selected-channel recordings. The full recording SHA-256 is
`123b1cce4ad4d4087e7dc294b47031bb1bfc52436dd086d588e531d5936f675f`:
2,405 samples, 4,810 channels, including event-side duplicates. Equivalence to
the retained trajectory does not establish independent-reference accuracy.

The [evidence index](checkpoints/case2-execution-path-profile-2026-10-21.json)
identifies the private archive containing raw profiles, commands, source and
class overlays, test results, statuses, timing summaries and hashes. The
production path and selected working baseline remain unchanged.

The subsequent [network reduction probes](case2-network-reduction-probes-2026-10-21.md)
isolate empty-list traversal and bounded magnitude calculations. Neither shows
a substantial elapsed-time improvement; both remain experimental.
