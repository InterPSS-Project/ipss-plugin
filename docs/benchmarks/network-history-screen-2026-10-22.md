# Network acceleration history screen

Increasing the acceleration history reduces network iterations substantially,
but does not consistently reduce complete simulation time. This implementation
remains experimental. Production convergence, controllers and recording are
unchanged; the eight-second objective and reference-accuracy contract are open.

## Method and checks

The depth-three prototype extends the [depth-one experiment](network-iteration-audit-2026-10-22.md)
with a small QR least-squares solve using modified Gram–Schmidt and
reorthogonalization. Numerical buffers are reused, with history reset at every
network solve. Coefficient and extrapolation bounds, finite-value checks and
the renewable accumulated-correction guard remain in place. These guards are
experimental safeguards, not proof of general model eligibility.

Every stopping decision still tests the original unaccelerated network voltage
correction against 1e-5 pu. Voltage-dependent injections refresh on every
iteration. The network interval remains 1/240 second, ten ST1 substeps remain,
and the sparse factorization is reused as before. The fixed-point formulation
is described in the [SUNDIALS mathematical documentation](https://sundials.readthedocs.io/en/v7.7.0/kinsol/Mathematics_link.html#anderson-acceleration).

All 47 focused tests pass. The additional tests cover independent contraction
modes, rank-deficient history, nonfinite-sample recovery, history resets and
inactive coordinates after a solve reset. They supplement the existing stopping,
minimum-iteration, lifecycle and controller tests; they do not establish full
reference accuracy or qualify arbitrary stateful callbacks.

## Complete matched-event runs

Both cases apply the bus-6307 event used by the supplied full-system reference.
The event-identity guard runs before computing reference errors. All channels
and both sides of event discontinuities are retained, with no fitted angle offset.

| Case | Ordinary network iterations | Depth three | Reduction | Largest accepted correction, pu |
| --- | ---: | ---: | ---: | ---: |
| 1 | 29,699 | 18,338 | 38.3% | 9.9923812e-6 |
| 2 | 36,369 | 20,197 | 44.5% | 9.9910723e-6 |

Each run has 2,405 network calls. The candidate applies 13,530 mixing updates in
Case 1 and 15,389 in Case 2. Neither complete run changes the renewable
accumulated high-voltage correction.

| Worst full-run reference RMSE | Case 1 ordinary | Case 1 candidate | Case 2 ordinary | Case 2 candidate |
| --- | ---: | ---: | ---: | ---: |
| Rotor angle, degrees | 12.182252 | 12.511335 | 102.746980 | 92.393615 |
| Bus voltage, pu | 0.01920410 | 0.01919249 | 0.04627669 | 0.04517909 |
| Active power, MW | 61.211437 | 63.511921 | 130.747676 | 112.711914 |
| Reactive power, Mvar | 46.841438 | 47.051111 | 158.523180 | 128.743297 |

Case 2 errors decrease, while Case 1 rotor and power errors increase. Neither
candidate satisfies the reference-accuracy contract. This remains a numerical
experiment, not an accuracy-preserving production optimization.

## Paired warmed timing

Both arms use the same executable overlay, with acceleration enabled or
disabled when constructing a fresh solver. Each case has twelve alternating
warmups followed by five measured pairs, alternating the order within pairs.
The complete interval includes fresh parsing, preparation, initialization,
startup, ten simulated seconds, full recording and writer closure. Compilation
and garbage collection are included; no observations or timing samples are
removed. Affinity is verified as one logical CPU before and after each process.

The per-iteration diagnostic lists and callback audit counters are absent from
the timed runtime. All 44 runs reproduce the complete binary, CSV and channel
metadata of their respective audited arm. Repeated generated recordings are
hard-linked only after verifying equality.

| Case | Ordinary median and range, s | Candidate median and range, s | Median paired reduction, s | Candidate faster |
| --- | ---: | ---: | ---: | ---: |
| 1 | 10.821170 (10.337044–11.152912) | 10.794781 (10.539955–11.659343) | -0.236006 | 2/5 |
| 2 | 7.359823 (7.316299–8.129235) | 7.488263 (7.158589–7.703818) | -0.128440 | 2/5 |

The small Case 1 difference between arm medians does not indicate a repeatable
gain: the candidate loses three paired measurements. Case 2's candidate median
is slower. Iteration count alone is therefore insufficient to predict runtime.

Both arms write to the same dedicated generated-artifact directory on D:.
Supplied references and previous retained C: outputs are unchanged. This avoids
pressure on C:'s two-GiB reserve; trace-size and log bounds remain enforced.
These paired results should not be treated as a direct hardware/storage or
event-equivalent comparison with older C: timings.

## Profile and publication follow-up

Two additional Case 1 runs were sampled after eight warmups, at a requested
five-millisecond execution-sample period. Of 1,151 main-thread samples with the
case runner on the stack, 70 (6.1%) include acceleration history calculations.
Another 412 are elsewhere in network solution, 160 in the direct ST1 path and
131 in PSS2A. These are approximate stack-sampling attributions, subject to
inlining and sampling bias, not exclusive wall-clock measurements. All ten
profile-run recordings match the candidate arm.

The profile identifies an avoidable cost in the prototype's publication path:
55 of its 81 `FastMath.atan` leaf samples pass through `setVoltage` directly
from the acceleration loop. That setter eagerly computes the voltage angle.
The existing ordinary network path instead uses `setVoltageWithDeferredAngle`,
whose guards retain normal behavior for polar coordinates, notification
listeners, custom buses and custom complex values.

An isolated follow-up reuses that existing guarded setter for accelerated
voltage publication. All 47 focused tests pass, as do 4,274 exact-double checks
plus notification-history and subclass-setter checks. Complete Cases 1 and 2
retain identical binary, CSV and channel metadata, iteration counts and mixing
counts. The timing table above measures the earlier eager-publication version;
the following screen measures the verified deferred-publication follow-up.
The underlying reference-accuracy limitations remain unchanged.

## Deferred-publication timing and coverage

The follow-up repeats the same twelve alternating warmups and five measured
pairs per case, retaining the single-CPU setting, complete recording, fresh
preparation and compilation/GC inside the timed interval. Its control disables
acceleration; both arms use the same executable overlay and D: storage.

| Case | Ordinary median and range, s | Deferred candidate median and range, s | Median paired reduction, s | Candidate faster |
| --- | ---: | ---: | ---: | ---: |
| 1 | 10.910843 (10.856055–11.795607) | 10.795280 (10.588565–10.859228) | 0.158592 | 5/5 |
| 2 | 7.455104 (7.448309–8.208470) | 6.757077 (6.623757–7.047913) | 0.751984 | 5/5 |

The candidate wins all five pairs in both cases. Relative differences between
arm medians are 1.1% for Case 1 and 9.4% for Case 2. Case 1's ranges still overlap
and compilation remains active, so this is a bounded screening result, not a
universal speedup guarantee. All 44 complete runs reproduce the full binary,
CSV and channel metadata of their respective audited arms. These are comparisons
against ordinary iteration, not paired comparisons against the earlier eager
acceleration variant; cross-screen differences cannot isolate setter cost alone.

A separate instrumented audit confirms that the fast-path predicate holds for
all 27,060,000 accelerated voltage publications in Case 1 and all 30,778,000 in
Case 2, with zero fallback calls. Both audit recordings remain byte-identical.
These counts establish coverage, not exclusive timing attribution.

For the candidate, median setup/initialization is 0.432 seconds in Case 1 and
0.334 seconds in Case 2; simulation plus complete recording is 10.359 and 6.387
seconds respectively. Stage medians need not sum to the full median. Case 1's
remaining gap therefore lies mainly in simulation work, rather than preparation.

Case 1 still exceeds eight seconds. The candidate retains its previously
measured reference errors, including worse Case 1 rotor/power errors than the
ordinary iteration control. Cases 3–4 still lack completed ten-second runs.
The prototype is retained for further investigation; production defaults remain
unchanged. The [follow-up evidence index](checkpoints/network-deferred-screen-2026-10-22.json)
preserves raw timings, phase measurements, coverage counts, full-recording
hashes, source/classes, commands and external recording dependencies.

## Cases 3 and 4

Both accelerated runs still fail at 0.045833333 seconds, before the fault.
Case 3 performs 373 network iterations and 58 mixing updates before failure;
Case 4 performs 363 and 65. Their final network calls fail the network equation
solve after 53 and 39 iterations respectively. These prefixes cannot qualify
ten-second accuracy or performance. Acceleration has not resolved the early
dynamic failures. Cases 5 and 6 were not tested with this prototype.

An initial Case 3 attempt stopped before simulation because its classpath mixed
incompatible ST1 runtime classes. It is retained as execution-error evidence
only. The corrected Cases 3–4 attempt uses the same compatible runtime as the
completed Cases 1–2 experiments.

The [evidence index](checkpoints/network-history-screen-2026-10-22.json) retains
sources/classes, tests, commands, paired measurements, reference comparisons,
affinity, failure diagnostics and recording dependencies. Large generated
recordings remain in the external artifact store, whose local location is
recorded privately in the archive.
