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

### Startup boundary localization and rejected magnitude-check optimization

Two 20 ms audits preserve the existing 1 ms controls byte for byte while recording
network convergence and converter memory after each solve. All 96 active
converters have zero accumulated high-voltage correction throughout all 23
recorded solves. Thus that correction is not the cause of the first divergence,
although it remains relevant later in the trajectory.

The initial solution and first startup-history sample are identical. During the
second history step, the 1e-4 control stops after four iterations with a maximum
voltage correction of 9.00054e-5 pu. The 1e-5 control takes twelve iterations,
ending at 7.96634e-6 pu. Their time-zero bus magnitudes differ by up to
0.000210444 pu. This is an early network-endpoint difference, before the large
trajectory separation. The audit does not yet establish a corrected coupling
policy.

The supplied reference itself has substantial motion before the one-second
fault. Across nominal times in [0, 1), bus 3113 spans approximately 0.2513 pu and
machine 1051 unit 1 spans approximately 15.44 degrees. These are ranges within
the supplied trace, not comparison errors. A flat-prefault expectation must
therefore be distinguished from reproducing this particular reference. The
comparison continues to use the unchanged supplied trace, with nominal
millisecond alignment and retained duplicate event timestamps.

A separate numerical prototype skips full complex-magnitude calculations when
conservative bounds already determine convergence and exclude a new maximum.
Uncertain cases retain the exact existing scaled-magnitude arithmetic; custom
complex subclasses and the componentwise norm retain ordinary dispatch. It
passes 3,597,396 exact-value and bound checks, including subnormal values,
overflow, equality, neighboring floating-point values and exceptional values.

With twelve warmups and five measured fresh-network runs on one CPU, complete
times are 8.6957, 8.3764, 8.3927, 8.2668 and 8.2033 seconds. The 8.3764-second
median does not improve the preceding 8.2149-second candidate. All seventeen
full recordings remain byte-identical to the baseline, but the prototype is
not adopted. Numerical production code remains unchanged, and both the
eight-second target and quarter-cycle reference accuracy remain open.

The audits, analysis scripts and rejected prototype are retained privately under
`verification-artifacts/2026-10-21-startup-boundary-and-norm-trial`, with the prior
workflow evidence and raw recordings identified by hash.

## Fused sparse input permutation and shared voltage magnitude

The optional factor-row solver now prepares the combined symbolic permutation
and numerical pivot mapping once per factorization. Forward substitution reads
and scales each right-hand-side entry through that mapping, eliminating the
separate workspace-fill and pivot-swap passes. The row arithmetic, subtraction
order and diagonal division remain unchanged. Refactorization rebuilds the map.

This isolated change is committed in core as `a152ee2fd`. Both the compiled
prototype checks and the registered Maven tests pass all twelve sparse-solver
tests. They cover repeated right-hand sides, numerical and structural changes,
pivoting, independent and coupled blocks, dimension growth, fallback modes,
exceptional values and the optional reciprocal-diagonal mode. The first Maven
invocation stopped in a module without the selected tests; the corrected reactor
invocation ran the twelve tests with zero failures, errors or skips.

A separate prototype passes the already-computed solved-voltage magnitude from
the network's divergence check into the standard bus setter. Notification
listeners, custom buses, custom complex values and polar-coordinate handling
retain the existing dispatch. It passes 168,000 exact-value checks plus matching
notification histories and custom-dispatch counts. This setter change remains
experimental and is not included in the core commit.

All timing trials below use one CPU, fresh networks, ten simulated seconds,
quarter-cycle steps, 1e-5 dynamic tolerance and complete recording:

| Trial | Warmups | Five measured complete-case times, seconds | Median, seconds |
|---|---:|---|---:|
| Fused factor input | 12 | 8.5272, 8.0623, 8.2091, 8.1079, 8.0631 | 8.1079 |
| Shared voltage magnitude | 12 | 8.6075, 8.2042, 8.2198, 8.1970, 8.0852 | 8.2042 |
| Both, synchronous JVM compilation | 12 | 8.4791, 8.0165, 8.1322, 8.0852, 7.9475 | 8.0852 |
| Both, longer fixed warm-up | 24 | 8.1597, 8.3863, 8.2986, 8.0184, 7.8874 | 8.1597 |

All eighty complete recordings are byte-identical to the preceding numerical
baseline. The shared-magnitude result is too close to the preceding 8.2149-second
control to establish a repeatable improvement. Neither synchronous compilation
nor longer warm-up establishes an eight-second median: the final trial still
records 268, 460, 75, 48 and 53 milliseconds of compilation during its measured
cases. These costs remain included, as do setup, power flow, initialization and
recording. The fastest individual repetitions are not used as the acceptance
statistic.

The eight-second objective, production integration of the broader experimental
runtime and quarter-cycle reference accuracy remain open. Sources, raw test
reports, numerical plans, complete timing records and hashes are retained under
`verification-artifacts/2026-10-21-sparse-publication-trials`.

### Shared parameter accessors remove repeated executable specialization

A separate three-case diagnostic enabled JVM class-loading and compilation
logging. The second and third fresh cases loaded 220 and 181 additional classes;
the class-load log attributes almost all of this activity to generated method
handle forms. Compiled public-parameter binding used a distinct bound method
handle for each controller instance. Repeated fresh-network construction therefore
created executable accessors even after the model classes had been warmed.

Public instance-double getters are now shared by declaring class and field,
using a class-scoped cache of unbound handles. Each evaluation supplies its own
controller receiver, so parameter writes remain visible and owners cannot share
parameter values accidentally. Custom field selection remains a binding-time
operation. Static fields, inaccessible declarations and incompatible owners
retain the previous binding failure/fallback behavior. The cache does not retain
controller instances.

The same instrumented diagnostic now loads 40 and one additional classes in its
second and third cases, respectively. All six before/after recordings are
byte-identical to the baseline. Instrumented times are excluded from performance
qualification. The initial prototype required an additional incompatible-owner
check; the corrected implementation passes 16,006 focused checks and all 35
registered CML tests, including three new parameter-access regression tests.
The corrected implementation and tests are committed in core as `fb503be81`.

Two complete timing trials add the corrected shared getter to the preceding
fused-input/shared-magnitude candidate and retain synchronous JVM compilation:

| Fixed warmups | Five measured complete-case times, seconds | Median, seconds |
|---:|---|---:|
| 12 | 8.1879, 8.1064, 7.9640, 7.7281, 7.6388 | 7.9640 |
| 24 | 7.9189, 8.1505, 7.7336, 7.9700, 7.7502 | 7.9189 |

All 46 complete recordings match exactly: 2,405 rows, 4,810 finite channels and
both sides of both fault events. Both trials use one CPU and the requested
quarter-cycle step and 1e-5 dynamic tolerance. Each repetition constructs a fresh
network and includes power flow, initialization, ten-second dynamics and full
recording. Remaining compilation and garbage-collection costs are included.

An eight-second median is now demonstrated for this experimental numerical
baseline. It is not an upper bound for every run: one of the final five
repetitions takes 8.1505 seconds. These results do not resolve the baseline's
independent reference-accuracy failures. Integrating the other experimental
components into the production runtime, their lifecycle/fallback coverage and
quarter-cycle reference accuracy remain required work; the overall objective
is not marked complete.

## Production current buffers and lifecycle validation

Core now provides an opt-in buffered network-current path. Standard machine
implementations and supported plugin current providers explicitly declare that
their current evaluation cannot observe deferred matrix rows. The provider
contract replaces the experimental class-name list. Algebraic converter bypass,
custom providers, custom matrix implementations and observed devices retain
ordinary publication.

The buffer reuses numerical storage and existing factors, retains untouched
rows, and publishes every solved row before voltage callbacks. Failure during
assembly or solving restores the same partially assembled right-hand side as
ordinary dispatch, including the original current objects. Dirty backend factors
are rebuilt even when the network-level dirty flag was not set.

The experimental eligibility cache missed observers attached after its first
inventory. The production implementation caches membership only. Generator and
bus-device list edits invalidate that layout; replacement, movement and status
changes use the existing inventory hooks. Observer attachment and changeable
provider capabilities are checked on every solve. Unknown subclasses do not
inherit unconditional eligibility. Uninitialized load profiles that delegate to
custom load calculations also retain ordinary dispatch.

Initial validation walked all device lists repeatedly. Three complete timing
trials retain the preceding optimized candidate and change its safety checks:

| Safety-check implementation | Warmups | Median complete-case time, seconds |
|---|---:|---:|
| Live list scans | 12 | 9.3173 |
| Cached membership with live observer checks | 12 | 8.8255 |
| Cached membership, validation during buffer copy | 12 | 8.6704 |

Each trial has five measured fresh-network repetitions, one CPU, ten simulated
seconds, quarter-cycle steps, 1e-5 dynamic tolerance and all-channel recording.
All 51 recordings retain the earlier optimized baseline exactly. The safer
candidate does not achieve the eight-second target.

A separate alternating comparison uses freshly compiled production classes
without experimental class overlays. Six warmups precede five measured pairs:

| Current publication | Five complete-case times, seconds | Median, seconds |
|---|---|---:|
| Ordinary | 24.9191, 24.5936, 24.7687, 24.9803, 24.9260 | 24.9191 |
| Buffered | 25.1756, 26.9818, 25.4261, 25.3502, 26.7987 | 25.4261 |

Every repetition includes fresh setup, initialization, simulation and recording.
Remaining compilation and collection costs are included. The buffer alone does
not establish a speedup, so ordinary publication remains the production default;
`setBufferedCurrentAssemblyEnabled(true)` explicitly selects the new path.
Faster scalar assembly and device evaluation still require production integration.

All sixteen production recordings are byte-identical, with 36,370 buffered solves
in each enabled case. Comparing the freshly built production trajectory with the
optimized candidate gives maximum differences of 4.2633e-14 pu in bus voltage
and 1.5462e-11 degrees in rotor angle. These are implementation comparisons;
independent reference-accuracy failures remain unresolved.

The joint Maven reactor compiles both repositories against current sources
without replacing cached dependency jars. Its final run finds 112 focused tests:
111 pass, one opt-in three-phase export is skipped, and none fail or error.
Ten new buffer regressions cover late observers, membership and capability
changes, default selection, dirty factors, inactive rows, failure publication,
custom loads, subclass fallback and buffer resizing. Existing network,
conversion, model, recording and three-phase checks also pass.

Across the three optimized trials, the initial two-run production control and
the sixteen-run production comparison, all 69 recordings have 2,405 finite rows,
4,810 channels and both sides of both fault events. Repeated-output hard-link
attempts were initially denied, leaving duplicate files. Verified deduplication
replaced 132 identical generated files with hard links, reclaiming 6.78 GB while
preserving every path and sample. Supplied references are unchanged. The paired
producer now stops if verified storage reuse fails before another repetition.

Sources, exact executed producers, compiled candidates, raw test reports,
numerical plans, timing records and hashes are retained privately under
`verification-artifacts/2026-10-21-current-buffer-integration`.

## Production ST1 local equations

The staggered solver now uses a compiled local ST1 plan for supported standard
machine and controller configurations. Input expressions come from the existing
model wrappers. The plan resolves their readers and dependencies once, retains
the established equation order, and evaluates the ten local substeps in reusable
numerical arrays. State, input and derivative storage is restored before returning
to the solver, preserving ordinary named-state observations.

Physical measurements are held only inside the local integration stage.
Parameters and limits are captured again at each stage entry. The output is still
published before the final algebraic refresh. Discontinuity averaging, limiter
projection and retained derivative history follow the ordinary recurrence.

Custom blocks, wrappers, limits, machines and unsupported stabilizers retain the
ordinary path. Late machine or bus observers also select ordinary evaluation.
Structural invalidation requires solver reinitialization; the compiled plan does
not silently continue with an obsolete state layout. The solver option
`setCompiledLocalSt1Enabled(false)` provides an explicit ordinary-evaluation control.

Four small-network model/stabilizer combinations retain all 480 observations
bit for bit while setpoints, limiter inputs, parameters and enable status change.
The comparison includes every observed block input, state, output, derivative
and local integration-history entry. Further tests cover late observers,
invalid output buffers, custom limits, structural invalidation, reinitialization
and the existing compiled-graph option. The focused set contains 45 passing tests.

An alternating production comparison uses six warmups followed by five measured
pairs. Each repetition builds a fresh Case 2 network, pins the JVM to one CPU,
uses quarter-cycle steps and 1e-5 dynamic tolerance, and records all channels for
ten simulated seconds. Setup, power flow, initialization, recording, remaining
compilation and garbage collection are included.

| ST1 local evaluation | Five complete-case times, seconds | Median, seconds |
|---|---|---:|
| Ordinary | 24.4510, 25.0526, 24.5460, 24.7914, 24.6043 | 24.6043 |
| Compiled | 21.6211, 21.7813, 22.8525, 21.9302, 21.6239 | 21.7813 |

The median reduction is 11.47%. Every enabled case executes 86,472 compiled local
advances. All sixteen recordings match the preceding production trace exactly:
2,405 finite samples, 4,810 channels and both sides of both fault events.
Verified hard links retain every recording path while using one physical binary
recording. These measurements use current production classes without experimental
runtime overlays. The eight-second target is still unmet.

The registered core plugin suite plus the focused checks records 2,650 test
executions, including 24 repeated selections: 2,632 pass, thirteen skip, three
fail assertions and two error. All five failure
reasons reproduce with the pre-change ST1 and solver classes from `601b2b890`:
two static power-flow comparisons, two fixed-shunt import checks, and Case 1's
bus-1051 flat-start voltage drift. The broad gate therefore remains failed.
Full reference accuracy and the remaining six-case accuracy gates are still open.

### Existing compiled-graph mode and the remaining production cost

A second alternating trial keeps compiled local ST1 evaluation enabled and
changes the existing `setCompiledControllerGraphsEnabled` option. Six warmups
precede five measured pairs; two further runs collect profiles and are excluded
from the timing statistics.

| Controller graph bindings | Five complete-case times, seconds | Median, seconds |
|---|---|---:|
| Live | 22.1264, 21.6340, 22.1848, 22.0457, 21.8581 | 22.0457 |
| Compiled | 19.1461, 18.8149, 19.8853, 19.3080, 18.8270 | 19.1461 |

This option reduces the paired median by another 13.15%. All eighteen complete
recordings, including the two profiled cases, remain byte-identical to the same
production trace. The option remains explicitly selected before initialization;
the default graph-binding setting is unchanged. The production local optimization
and this configuration still do not reach eight seconds.

The warmed compiled-graph profile contains 937 main-thread execution samples:
531 in network solution, 387 in other integration work, six in recording and
thirteen in setup or other work. These are sampled execution shares, not elapsed
time allocations. The triangular solve accounts for 61 leaf samples. Repeated
angle calculation accounts for 92 leaf samples, including 84 reached through
bus-voltage publication. Of 89 string-hash leaf samples, 85 are voltage-table
lookups or writes inside network iteration; another 29 leaf samples are hash-table
entry allocation in that same path. Indexed convergence storage and guarded
voltage publication are therefore concrete remaining production targets.

The combined evidence covers 34 full recordings. Supplied references are unchanged.
Sources, compiled classes, exact producers, timing records, profiles, registered
test reports, baseline failure controls and hashes are retained privately under
`verification-artifacts/2026-10-21-production-st1-local-plan`.


## Indexed voltage-history convergence

The network solver now binds bus-order slots to entries in its existing protected
voltage-history table. A guarded standard-model pass takes the table monitor once
and reads and updates those entries directly. It preserves the convergence norm,
tolerance, bus order, both voltage reads, iteration count, partial writes before
an invalid iterate, and device callback boundaries. The original table remains
the source of truth for subclass and callback readers.

The pass validates bus identity, identifiers, membership, native list behavior,
standard bus/network types, and ordinary complex values before writing. Public
table mutations invalidate bindings. Exposing a mutable table view permanently
selects ordinary access, because an iterator or entry can mutate the table later.
Custom tables and unsupported models retain the original convergence loop.
`setIndexedVoltageRecordsEnabled(false)` selects ordinary lookup explicitly.

An earlier per-access candidate preserved complete recordings but provided no
measured gain: its paired median was 0.63% slower. Its access checks and locks
were still in the inner loop. That implementation was removed. The batch
candidate reduced a separate paired median from 19.1694 to 18.1864 seconds.

The final-source trial uses the original plain hash table as its control, six
warmups and five measured pairs. Every repetition creates a fresh Case 2 network,
uses one verified CPU, quarter-cycle steps and 1e-5 dynamic tolerance, and retains
all 4,810 channels. Setup, power flow, initialization, recording, remaining JIT
compilation and garbage collection are included.

| Voltage history | Five complete-case times, seconds | Median, seconds |
|---|---|---:|
| Original table | 18.9546, 19.4371, 18.9748, 19.1409, 19.0335 | 19.0335 |
| Indexed batch | 17.8657, 18.0908, 18.7435, 17.7647, 17.7152 | 17.8657 |

The final median reduction is 6.14%. All sixteen final recordings are
byte-identical to the preceding production trace: 2,405 finite samples and both
sides of both fault events. Each indexed run exercises 145,476,000 direct entry
reads/writes. Verified hard links retain every output path without duplicating
the large binary recording within a trial.

All 70 executed targeted regressions pass: 43 core convergence, lifecycle and
current-buffer checks; three multi-network checks; and 24 controller-coupling
checks. These cover both convergence norms, iteration-limit diagnostics, repeated
solves, public table mutations, escaped views, changed bus layouts, serialization,
custom callback fallback and the first invalid iterate. This is a targeted gate;
the complete registered suite was not rerun for this change.

The preliminary batch profile contains 826 main-thread execution samples: 480 in
network solution, 329 in other integration, three in recording, and fourteen in
setup or other work. Angle calculations and controller stage preparation remain
substantial costs. Sample counts describe the profile, not measured wall-time
allocations.

The eight-second objective remains unmet. Equality with the prior quarter-cycle
trace establishes implementation equivalence; it does not resolve the independent
reference-accuracy failures documented above. The reference numerical protocol
and supplied files remain unchanged.

Exact producers, sources, compiled classes, profiles, regression reports and
complete-recording hashes are retained privately under
`verification-artifacts/2026-10-21-indexed-voltage-history`.


## Deferred rectangular-voltage publication

The standard dynamic bus can now retain the immutable rectangular voltage from
the sparse solve and calculate its polar angle when an observer requests it.
This avoids repeated angle evaluation and complex-object allocation during
network iterations. Magnitude validation and row-by-row publication order remain
unchanged. Custom bus/network types, custom complex values and notification
callbacks retain ordinary publication. Scalar writes invalidate cached values;
EMF inspection, serialization through state snapshots, formatting and late
observers see the ordinary polar state.

The staggered solver exposes `setUseRectangularNetworkVoltage(true)` before
initialization. This option defaults to false and is reported in the numerical
plan. Rectangular coordinates remove a polar round trip, so choosing them is an
explicit numerical option. The core optimization applies only to eligible
rectangular buses and can separately be disabled with
`setEfficientVoltagePublicationEnabled(false)`.

An initial timing attempt left the network in polar coordinates and exercised
zero optimized publications. It was stopped and is excluded from performance
claims. The qualifying trial explicitly checks rectangular coordinates and
nonzero optimized publications before timing proceeds.

Six warmups precede five alternating measured pairs. Each run creates a fresh
network and includes setup, power flow, initialization and complete recording of
4,810 channels. The JVM is pinned to one CPU; integration uses quarter-cycle
steps and 1e-5 dynamic tolerance. Two additional profile runs are excluded from
the timing medians.

| Rectangular publication | Five complete-case times, seconds | Median, seconds |
|---|---|---:|
| Ordinary | 17.7214, 17.6098, 17.6335, 17.8332, 17.9127 | 17.7214 |
| Deferred | 16.0707, 15.8292, 16.9524, 15.8900, 15.7853 | 15.8900 |

The median reduction is 10.33%. Each enabled run performs 72,742,000 guarded
publications. All eighteen rectangular recordings are byte-identical, with
2,405 finite samples and both sides of both fault events preserved. Hard links
retain every output path without storing eighteen copies of the binary trace.

Against the prior polar production recording, all samples pass the declared
equivalence budget of `1e-10 + 1e-12 * abs(prior value)`. Maximum differences are
4.27e-14 pu bus voltage, 1.01e-11 degrees bus angle, and 1.55e-11 degrees rotor
angle. No offset fitting, resampling or channel removal is applied. This is an
implementation-equivalence check, not independent-reference acceptance.

All 97 targeted tests pass: 68 core checks, two multi-network checks and 27
plugin checks. They cover scalar coordinate changes, signed zero and nonfinite
values, late and muted observers, state snapshots, fallback dispatch, repeated
solves, concurrent read-only access and coupled machine trajectories. The full
registered suite was not rerun for this slice.

The enabled profile contains 767 main-thread execution samples: 357 in network
solution, 390 in other integration, three in recording and seventeen in setup
or other work. Collection traversal, triangular solves and controller-stage
preparation remain prominent. These sample counts are not wall-time allocations.

The eight-second objective remains unmet. Supplied-reference accuracy remains
unresolved; reproducing the previous quarter-cycle trajectory does not repair
its error. Reference settings remain separately recorded as 0.1 MVA power-flow
mismatch, 1e-5 pu converted-startup voltage correction and 1e-4 pu dynamic
voltage correction. They are not interchangeable convergence criteria.

Exact producers, final sources, compiled classes, profiles, regression reports
and full-recording hashes are retained privately under
`verification-artifacts/2026-10-21-deferred-voltage-publication`.


## Immediate compiled child-output reuse

Nested controller preparation previously prepared a child graph and then called
its output getter, which could repeat the same input preparation. The compiled
runtime now proves when that immediately prepared output is already settled:
the controller declares pure getters, every block supports pure output refresh,
the graph is acyclic, preparation order matches dependency order, and blocks
are distinct. Static protocols, feedback, shared blocks, reordered dependencies
and overridden output getters retain ordinary evaluation.

The reusable result is scoped to the immediate child observation. Independent
output reads still refresh live measurements and parameters; integration states
and derivative history stay in their existing blocks. There are no handwritten
replacement model equations. Frozen plans also inspect their prebound block
membership in original order instead of repeatedly walking wrapper collections.
Time constants remain live in those checks.

`setPreparedOutputReuseEnabled(false)` selects ordinary child observation on an
idle controller tree. The staggered solver exposes the corresponding
`setPreparedControllerOutputReuseEnabled(false)` before initialization. Reuse is
enabled by default but requires a compiled graph and the capabilities above.
Output evaluation errors retain the ordinary zero-output fallback; settings
applied before annotation initialization propagate to subsequently compiled
children. Structural changes still require explicit plan invalidation.

The final trial uses six warmups, five measured alternating pairs and two separate
profile runs. Each repetition creates a fresh network and records all 4,810
channels at quarter-cycle steps with 1e-5 dynamic tolerance on one pinned CPU.
Setup, initialization, recording, remaining compilation and garbage collection
are included in the complete-case times.

| Child output evaluation | Five complete-case times, seconds | Median, seconds |
|---|---|---:|
| Ordinary | 15.8776, 16.5269, 15.7704, 15.8654, 15.6520 | 15.8654 |
| Immediate reuse | 14.7382, 14.5910, 14.9270, 14.6746, 14.5761 | 14.6746 |

The final median reduction is 7.51%. The preliminary trial independently
measured 7.58%. Each enabled run reuses 10,247,898 child outputs.
All eighteen final recordings are byte-identical to the previous rectangular
production trace: 2,405 finite samples, with both sides of both fault events.
The preliminary trial also preserves all eighteen complete recordings exactly.
Verified hard links avoid duplicating large binary files within each trial.

All 62 targeted tests pass: 40 core controller checks and 22 coupled plugin checks.
Coverage includes live inputs, gains and time constants, predictor/corrector
history, feedback and alias fallback, output overrides, output failures,
invalidation/recompilation, pre-initialization settings, discontinuities and
controller status changes. The complete registered suite was not rerun.

The enabled profile contains 708 main-thread execution samples:
332 in network solution,
358 in other integration,
3 in recording, and
15 in setup or other work.
These are sampling counts, not measured wall-time allocations.

The eight-second goal remains unmet, and matching the previous quarter-cycle
trace does not resolve its independent-reference accuracy failures. Further
work should extend dependency-aware scheduling to eligible parent controller
graphs and reduce the remaining network data-access cost, while preserving
physical input refresh and the declared integration lifecycle.

Exact final sources/classes, the preliminary executed core snapshot, producers,
regression reports, profiles and complete-recording hashes are retained privately
under `verification-artifacts/2026-10-21-prepared-child-output`.


## Nested parent output dependency scheduling

Compiled parent output schedules now include eligible child controllers. The
parent and child graphs must be acyclic and declare pure output behavior. Child
output getters must retain the inherited implementation. Shared blocks across
the tree, including internal compound-filter states, select the ordinary sweep
path. These checks run when compiling the graph rather than at each observation.

The standard compound ramp filter and final PSS2A lead-lag expose their existing
pure output behavior; their state-update equations, limiter logic and derivative
history remain unchanged. PSS2A opts into parent scheduling. Its selector inputs
and explicit fixed-input observation scope remain live at their existing stage
boundaries. Nested scheduling preserves the ordinary NaN failure signal when
the parent output is nonfinite.

`setNestedOutputSchedulingEnabled(false)` selects ordinary parent sweeps before
controller compilation. The staggered solver provides
`setNestedControllerOutputSchedulingEnabled(false)` before initialization.
Nested scheduling defaults to enabled but applies only to eligible compiled
graphs; unsupported and custom graphs retain their previous evaluation path.

The paired trial creates a fresh Case 2 network for each run, with six warmups,
five measured pairs and two separate profile runs. It uses one pinned CPU,
quarter-cycle integration, 1e-5 dynamic tolerance, and all 4,810 recorded channels.
Complete-case timing includes setup, power flow, initialization and recording.
All 103 stabilizer parent controllers use scheduling in enabled runs.

| Parent output evaluation | Five complete-case times, seconds | Median, seconds |
|---|---|---:|
| Ordinary sweeps | 14.9051, 15.7102, 14.8285, 15.0582, 14.7802 | 14.9051 |
| Dependency schedule | 14.4163, 14.4412, 14.6167, 14.3260, 14.3124 | 14.4163 |

The median reduction is 3.28%. All eighteen full recordings match the preceding
rectangular production trace byte-for-byte: 2,405 finite samples and both sides
of both fault events. Hard links preserve every recording path without storing
repeated copies of the binary trace within the trial.

All 70 targeted tests pass: 45 core controller checks and 25 plugin checks.
The coupled snapshots include stabilizer and child states, compound-filter
states, stored derivatives, solver integration history and exciter local history.
Additional checks cover nested feedback, cross-tree aliases, custom output
getters, nonfinite outputs, invalidation and pre-initialization configuration.
The full registered suite was not rerun.

The enabled profile contains 661 main-thread samples:
348 in network solution,
295 in other integration,
5 in recording and
13 in setup or other work.
Sampling counts are not wall-time allocations.

The eight-second objective remains unmet. Local trajectory equality does not
resolve independent-reference accuracy. The next substantial opportunity is
reducing repeated network membership validation and controller runtime dispatch
while keeping structural invalidation and physical-input refresh explicit.

Exact sources/classes, producers, targeted regression reports, profiles and full
recording hashes are retained privately under
`verification-artifacts/2026-10-21-nested-output-scheduling`.
