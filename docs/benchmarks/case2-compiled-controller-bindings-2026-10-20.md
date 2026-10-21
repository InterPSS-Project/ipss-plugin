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

## Direct network phase timing and device-access trials

Direct phase timers refine the sampling result above. In the final warmed,
single-CPU instrumented case, 36,371 linear network calls spent:

| Network phase | Seconds |
|---|---:|
| Current-injection assembly | 2.0601 |
| Custom-current addition | 0.0010 |
| Sparse solve, including equation-vector conversion | 1.5130 |
| Voltage publication and convergence observation | 0.8138 |

The counters include initialization and event-side linear solves. Custom-current
entries were visited only 1,718 times across the entire case; compensation is
disabled after initialization in this workload. Consequently, the sampled
custom-current leaf must not be interpreted as its elapsed-time cost. Assembly
is the largest directly measured network phase. Instrumentation adds overhead;
the 9.8796-second complete profiled run is excluded from performance qualification.

Two further device-access experiments retained the preceding numerical workload:

| Experiment | Measured complete-case times, seconds | Median | Decision |
|---|---|---:|---|
| Read stored machine derivatives without constructing complete solver-state snapshots | 9.6704, 9.5342, 9.0092 | 9.5342 | Not adopted |
| Reuse rotor-angle sine and cosine while continuing to refresh terminal currents and electrical power | 9.6058, 9.3883, 8.7994 | 9.3883 | Not adopted |

Both use six warmups and three measured repetitions on one CPU, with full
recording and fresh networks. Neither demonstrates an improvement over the
9.3042-second baseline median. All eighteen complete traces are byte-identical
to that baseline. The derivative checks pass for round-rotor and salient-pole
machines over disturbed trajectories and special stored values, and confirm
that reads leave solver history unchanged. An initial test launch used the wrong
fixture working directory; both tests pass after correcting that launch.
Rotation checks pass 200,000 exact-bit comparisons against the preceding
transform implementation, with changing voltage, angle and scale, reflective
angle updates, special values, and repeated changes of the cached angle.

Production numerical code remains unchanged. The next implementation target is
the current-assembly/solver interface: reusable numerical right-hand-side storage
for validated standard devices and static loads, with live voltage-dependent
injections, preserved summation order, and compatible behavior for custom devices
and matrix observers. Removing complex-object round trips must preserve inactive
rows, custom-current behavior, matrix-vector visibility and event ordering.
At this stage it was not yet implemented or qualified. The eight-second objective,
production runtime integration and quarter-cycle reference accuracy remain open.

These trials and the phase profile are retained privately under
`verification-artifacts/2026-10-20-device-access-trials`.

## Primitive current-buffer experiments

Two subsequent prototypes assemble eligible network currents into a reusable
interleaved numerical vector and solve it using the existing factorization.
They preserve untouched rows and publish the complete solved vector before
voltage observers run. Custom current tables, unsupported devices and custom bus
classes retain the existing interface. Live voltage-dependent injections and
the original arithmetic and summation order are preserved.

The second prototype also evaluates standard static-load power and current into
the numerical buffer, avoiding intermediate complex objects. Public power
queries continue to calculate and return their normal results. Exceptional
values and custom complex subclasses retain the original fallback calculations.

| Experiment | Measured complete-case times, seconds | Median |
|---|---|---:|
| Primitive current handoff | 9.2817, 9.4986, 8.8871 | 9.2817 |
| Primitive handoff and scalar static-load evaluation | 9.1725, 9.3885, 8.9708 | 9.1725 |

Each trial uses six warmups, three measured fresh-network runs on one CPU, the
same quarter-cycle step and 1e-5 dynamic tolerance, and full recording. The
second median is 0.1316 seconds below the earlier 9.3042-second baseline, but
the timing ranges overlap. These measurements do not establish a repeatable
performance improvement. Neither prototype is adopted into production.

Each run exercises 36,370 primitive solves and 55,100,550 static-only row writes.
All eighteen complete recordings are byte-identical to the preceding baseline:
2,405 rows, 4,810 finite channels, and both event-side duplicates. Each prototype
also passes 19,998 exact-bit static-current comparisons against the ordinary
dispatcher, two matching exceptional-input checks, and generator/custom-bus
fallback checks. The ordinary dispatcher itself matches a separately executed
preceding implementation over all test observations.

The prototype inventory guard is not a production lifecycle contract: it uses
an experimental device whitelist, and observer attachment after inventory
construction still needs invalidation coverage. Production integration would
require those guarantees and broader fallback tests. No numerical production
code changed in these trials. The eight-second target and quarter-cycle
reference accuracy remain unresolved; equivalence to an experimental baseline
does not establish reference acceptance.

Sources, compiled overlays, test results and measurements are retained privately
under `verification-artifacts/2026-10-20-primitive-current-trials`. Full generated
recordings and supplied inputs remain hash-identified retained dependencies.

## Combined workflow trial and accuracy discriminator, October 21

The next isolated prototypes cache ordered controller membership with list-change
invalidation, invalidate ST1 expression inputs when their dependencies are written,
and reuse the primitive current buffers described above. Live measurements,
integration stages and arithmetic order are preserved. Custom controller-type
getters retain ordinary dispatch. The revised membership path also preserves a
single call to an overridden controller-list getter.

The revised membership checks pass 92,237 assertions and observe 3,038 model
notifications. The ST1 checks retain all 480 recorded state, input, output,
derivative and history rows exactly, including limiter and enable/disable cases.
Polling dependency values was separately tested but did not improve timing;
write-driven invalidation is used in the combined trial.

Two fresh complete-workflow trials use twelve warmups and five measured runs,
one CPU, a fresh network per repetition, quarter-cycle integration, 1e-5 dynamic
tolerance and full recording:

| Trial | Complete-case seconds | Median, seconds |
|---|---|---:|
| Control | 9.3409, 9.2776, 9.3639, 9.2754, 9.2633 | 9.2776 |
| Combined prototypes | 8.5716, 8.2584, 8.2149, 8.1581, 8.0910 | 8.2149 |

The combined median is 11.5% lower but remains above eight seconds. All 34 full
recordings are byte-identical to the preceding performance baseline. Residual
JIT compilation and garbage collection remain included in elapsed time.
A further measurement-reuse trial completed 27 of 29 planned runs before the
bounded launcher stopped at its 8 MiB console limit. Its completed traces match,
but it has no qualified timing result. None of these prototypes is promoted to
production by this measurement.

### Accuracy takes precedence over this timing improvement

Scoring the ordinary-startup performance baseline against the supplied full
reference gives a worst full-window rotor RMSE of 102.808 degrees. Independently
recomputing converted startup improves the initial voltage agreement to
1.2553e-5 pu but does not repair quarter-cycle dynamics: worst full-window rotor
RMSE is 106.061 degrees. These timings therefore describe an inaccurate
trajectory and cannot establish the combined performance-and-accuracy objective.

The user-confirmed reference settings remain distinct: load flow uses 0.1 MVA
maximum bus mismatch (1e-3 pu on the 100 MVA base), converted startup uses 1e-5 pu
voltage correction, and dynamics use 1e-4 pu voltage correction. The settings
image does not establish the exact correction norm or iteration limits. The
1e-5 dynamic performance target is a separate experiment.

Prefault discriminators use the same computed converted point and optimized
implementation, changing only the indicated numerical settings:

| Integration step | Dynamic tolerance | Maximum voltage error, pu | Maximum rotor error, degrees |
|---|---:|---:|---:|
| 1 ms | 1e-4 | 0.000743132 | 0.0267988 |
| 1 ms | 1e-5 | 0.110184 | 10.2261 |
| Quarter cycle | 1e-4 | 0.126195 | 14.2907 |
| Quarter cycle, initialization compensation retained | 1e-5 | 0.138392 | 18.7496 |

The matched 1 ms/1e-4 control reproduces all 993 prefault rows and 4,810 channels
of the preceding recovered implementation byte for byte. This isolates the new
storage optimizations from the observed divergence in that configuration.
Tightening tolerance and increasing the step each independently disrupt the
prefault trajectory. Retaining initialization compensation does not restore it.
The quarter-cycle probes use phase-specific reference interpolation and are
diagnostic comparisons, not formal acceptance results.

The next investigation must resolve network/controller coupling and convergence
semantics. Iteration-count reductions are especially sensitive because the
converter high-voltage correction has iteration-local memory. The evidence does
not yet identify a valid production correction. The recovered 1 ms full-run
result still has one prefault rotor-limit failure, as recorded in the
[accuracy report](case2-accuracy-recovery-2026-10-20.md).

Scripts, compiled prototypes, numerical plans, test results and compact logs are
retained privately under `verification-artifacts/2026-10-21-workflow-runtime-trials`.
Full recordings and supplied inputs remain hash-identified retained dependencies.
