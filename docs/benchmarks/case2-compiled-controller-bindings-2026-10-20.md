# Compiled controller bindings and Case 2 performance

The controller compiler can now bind selected signals directly to numerical
storage while retaining the equations in the existing model definition.
`CMLSymbolMapper.FixedExpression` accepts a compilation-time resolver whose
readers remain live. Returning no override retains the existing binding.
Function inputs use the same resolver, so selector arguments can read local
state buffers without a second handwritten selector equation.

Names are resolved once, and the existing operand-read and arithmetic order
remain unchanged. Parameters remain live. The owner must rebuild bindings after
structural changes and limit any sampled-input reuse to the prescribed numerical
stage. Existing callers keep their current behavior.

## ST1 architecture experiment

The ST1 candidate now compiles each block's existing input expression instead
of maintaining a second switch containing those equations. Its high-value
selector calls the existing function implementation. Block-output references
read the candidate's local numerical buffers, while machine voltage and
stabilizer inputs retain their existing stage-scoped measurement policy.
The primitive block kernels and local integration recurrence are unchanged.

Four exciter/stabilizer combinations exercise live setpoints, limiter modes,
discontinuities, enable/disable transitions and retained integration history.
All 480 observations, including states, inputs, outputs and derivatives, are
bit-for-bit identical to the preceding handwritten candidate.

The complete Case 2 comparison uses the
[confirmed reference settings](texas2k-case2-reference-numerics-2026-10-20.json),
calculated startup conversion, corrected continuous-shunt controls and 1 ms
integration. All 10,005 rows and 4,810 physical channels are byte-for-byte
identical to the preceding recovered candidate. The binary trace SHA-256 is
`fb7ee68fbffe95db3e2427a36e7a44e60a4b0b19a882f713235d96195e01376b`.
Independent reference scoring retains the preceding result: every voltage
channel and full-window rotor channel passes, while bus 1051 unit 1 still
exceeds the prefault rotor limit. Direct bus-frequency coverage remains absent.

The generic binding API is implemented in core. The specialized ST1 integration
remains an experimental runtime overlay; its production lifecycle and structural
invalidation integration are unfinished. This experiment establishes equation
and history equivalence, not completion of the controller architecture.

## Requested quarter-cycle performance discriminator

The separate timing run uses 1e-5 dynamic voltage tolerance, a 1/240-second step,
ordinary startup, fresh network construction for every repetition, and complete
recording. It does not use the separate startup-conversion calculation and is
not qualified against the supplied 1 ms reference trajectory.

The operating system pins the JVM to one logical CPU. Affinity is checked before
and after execution; the JVM is configured for one processor and serial garbage
collection. The timer surrounds the complete case call, including import,
power flow, initialization, startup history, simulation, all recording and status
output. Compilation warmup occurs in the same JVM with fresh networks.

| Candidate | Warmup runs | Measured case times, seconds | Median |
|---|---:|---|---:|
| Bound equations, older experimental parameter access | 3 | 10.4745, 10.2084, 9.5007 | 10.2084 |
| Bound equations, current direct parameter bindings | 6 | 9.3042, 9.6329, 8.9469 | 9.3042 |

Both parameter access and warmup duration changed between these measurements;
the difference cannot be attributed entirely to either change. The first
attempt stopped during output deduplication and supplies no valid measured
timing. The completed runs retain all output samples. Verified identical files
share storage where hard links are available.

Every repetition records 2,405 rows and 4,810 channels, including both sides of
fault application and clearing. All nine complete traces in the second run are
identical and finite. The eight-second target is still missed by 1.3042 seconds
at the median. These timings do not establish quarter-cycle reference accuracy.

## Verification and remaining work

All 32 focused CML tests pass. New checks cover nested function bindings, live
parameter updates, compilation-only resolution, operand ordering, special
floating-point values and reentrant reads. The local core build and install
also succeed. The complete registered suite was not repeated for this API
extension; the existing failure status recorded in the
[accuracy recovery report](case2-accuracy-recovery-2026-10-20.md) remains open.

Remaining work includes integrating the specialized runtime with the production
controller lifecycle, profiling the remaining single-CPU cost, and qualifying
ordinary startup and quarter-cycle trajectories. Specialized stabilizer and
other exciter graphs also need the same single-definition treatment. The
performance objective and the remaining accuracy gates remain open.

Compact sources, compiled overlays, plans, measurements and verification reports
are retained privately under
`verification-artifacts/2026-10-20-compiled-controller-bindings`. Supplied
references and complete large traces are retained as hash-identified dependencies.

## Follow-up profile and rejected storage experiments

A subsequent profile of the same bound-controller candidate collected 452
main-thread execution samples: 245 in network solution (54.2%), 182 in device
integration (40.3%), nine in recording (2.0%), and 16 in setup or other work
(3.5%). These are sampled execution shares, not complete wall-time shares.
The profiled run was slower under instrumentation and is excluded from timing
qualification. Allocation sampling was not used to estimate per-case allocation.

The largest sampled leaf was the factored sparse triangular solve, with 84
samples (18.6%). Network instrumentation counted 36,369 iterations across 2,405
network calls, or 15.12 iterations per call. The existing factorization reuse
remains in place. Repeated voltage-dependent injections are still required on
each nonlinear iteration.

Two isolated storage changes were tested against the preceding 9.3042-second
candidate, each with six warmups and three measured fresh-network runs in one
JVM, pinned to one CPU, with full recording:

| Experiment | Measured complete-case times, seconds | Median | Decision |
|---|---|---:|---|
| Setter-invalidated rectangular voltage cache for standard dynamic buses | 9.8730, 9.6900, 9.0326 | 9.6900 | Not adopted |
| Separate real and imaginary arrays for sparse factor rows and work buffers | 9.3897, 9.8107, 9.1163 | 9.3897 | Not adopted |

Neither trial demonstrated a speed improvement. They remain private experiments;
production numerical code was not changed. The cache passed 32 exact-bit checks,
including setter notifications, reflective model updates, coordinate changes,
signed zero, and custom getters. The factor layout passed 12 sparse-solver tests,
including repeated right-hand sides, refactorization, pivoting, independent
blocks, structural changes, dimension growth, and fallback behavior.

All nine full traces from each experiment are byte-identical to the preceding
quarter-cycle candidate: 2,405 rows and 4,810 finite channels, retaining both
sides of both fault events. This establishes equivalence to that candidate,
not reference accuracy. The best established median remains 9.3042 seconds;
the eight-second objective and quarter-cycle accuracy qualification remain open.

The profile and rejected experiments are retained privately under
`verification-artifacts/2026-10-20-network-layout-trials`, with the previous
controller-binding evidence as a dependency.
