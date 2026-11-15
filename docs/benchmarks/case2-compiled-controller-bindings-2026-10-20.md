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


## Revision-based network membership validation

The indexed voltage-history path now validates native bus membership once per
network solution, then uses the network revision to check whether that proof
still holds. Numerical convergence checks and voltage-dependent current updates
still execute at every required iteration. This changes structural validation,
not convergence tolerance, integration order or physical equations.

The fast path requires exact standard buses, a common standard network owner,
the native bus list and ordinary history values. Bus identifier changes now
invalidate the revision before and after assignment, including invalid names
and reentrant notifications. Existing membership and activity hooks remain
active. Exposing or installing the list's raw backing array permanently disables
revision reuse for that network because later array edits bypass list hooks.
Detached lists, custom implementations and escaped history views retain their
previous guarded path. `setRevisionVoltageValidationEnabled(false)` selects
full validation between network solves for controlled comparison.

Each enabled Case 2 run performs 2,405 full validations and skips 33,964 repeated
validations. It still checks every active bus voltage during convergence and
preserves invalid-iterate reporting and partial history writes.

The final-source trial uses six warmups, five measured alternating pairs and two
separate profiling runs. Each run builds a fresh network and includes power
flow, initialization and all 4,810 recorded channels. The process is pinned to
one CPU, with quarter-cycle integration and 1e-5 dynamic tolerance.

| Membership validation | Five complete-case times, seconds | Median, seconds |
|---|---|---:|
| Full validation | 14.5435, 15.1893, 14.5148, 14.4400, 14.5810 | 14.5435 |
| Revision guarded | 13.9929, 13.7258, 13.7843, 13.8284, 13.9170 | 13.8284 |

The median reduction is 4.92%. All eighteen complete traces match the preceding
production trace byte-for-byte: 2,405 finite samples, 4,810 channels and duplicate
fault application/clearing timestamps preserved. Matching repeated recordings
share storage through verified hard links.

All 113 targeted checks pass, including 87 core network checks, two multi-network
checks and 24 coupled solver/controller checks. New tests cover both convergence
norms, rename and activity changes, list edits, owner changes, custom callbacks,
reentrant notifications, raw-array access and delayed array mutation. The full
registered suite was not rerun.

The eight-second objective and supplied-reference accuracy remain unresolved.
This is implementation-equivalence evidence, not independent accuracy acceptance.
The confirmed reference uses separate tolerances: 1e-3 pu power mismatch on the
100 MVA base, 1e-5 pu startup voltage correction and 1e-4 pu dynamic voltage
correction. The performance trial deliberately uses the requested 1e-5 dynamic
tolerance; it must not be presented as a matched-reference numerical protocol.

Exact final sources, classes, test reports, profiles, producers and trace hashes
are retained privately under
`verification-artifacts/2026-10-21-revision-voltage-validation`.


## Native bus-list iterator specialization

The native bus list now uses a small iterator specialization for direct reads of
its size and modification counter. It inherits the ordinary element-read and
removal implementation. Membership remains live: there is no frozen list copy,
no changed device-update order and no numerical calculation change.
`setDirectBusIterationEnabled(false)` selects the ordinary iterator. Changing
the option does not invalidate iterators already created.

All 118 targeted tests pass, including five new iterator checks. They compare
ordinary and specialized traversal across 200 deterministic mutation sequences,
removal and exhaustion, raw backing-array edits, replacement and live option
changes. Existing network, convergence, current-buffer, multi-network and coupled
controller checks also pass. The full registered suite was not rerun.

An alternating five-pair trial, after six warmups, showed the following complete
case medians. Two additional profile runs were excluded from timing.

| Mode | Median complete-case seconds |
|---|---:|
| Ordinary iterator | 14.5129 |
| Specialized iterator | 13.9890 |

That 3.61% difference includes residual compilation, which varied substantially.
To test whether mode alternation distorted the result, each mode was then run
in a separate JVM with twelve fixed-mode warmups and six measured repetitions.

| Mode | Six complete-case times, seconds | Median, seconds | Median compilation, ms |
|---|---|---:|---:|
| Ordinary iterator | 14.0391, 14.1244, 13.9482, 13.9949, 14.0857, 14.2144 | 14.0624 | 194.5 |
| Specialized iterator | 13.7946, 13.7075, 13.7565, 13.5321, 13.6186, 14.0930 | 13.7320 | 196.0 |

The fixed-mode median reduction is 2.35%. Compilation and garbage collection remain
included; no estimated time was subtracted. Both trials use one pinned CPU,
quarter-cycle steps, 1e-5 dynamic tolerance and fresh networks. Timing includes
import, power flow, initialization and full recording. The comparison toggle is
applied after import; both modes share the new default during import. Iterator
counters exclude import and record 92,364 specialized traversals when enabled.

All 54 full recordings match the preceding production trace byte-for-byte:
2,405 finite samples, all 4,810 channels and both event-side duplicates. Equal
recordings share storage through verified hard links within each trial.

The eight-second objective remains unmet. These checks establish implementation
equivalence, not independent-reference accuracy; the quarter-cycle reference
mismatch remains unresolved. Compact exact sources, classes, tests, profiles,
producers and retained recording hashes are stored privately under
`verification-artifacts/2026-10-21-direct-bus-iteration`.

### Endpoint-refresh discriminator

A private solver overlay separately tested two possible reductions in repeated
device evaluation. Removing the second consecutive post-state endpoint refresh
preserves the complete Case 2 recording byte-for-byte. Skipping entry evaluation
when reusing a network endpoint changes the trajectory: maximum rotor difference
reaches 3.17988 degrees and maximum bus-voltage difference reaches 0.0141796 pu.
Entry evaluation therefore remains required in this workload.

All three diagnostic runs retain 2,405 finite samples, 4,810 channels and both
fault-event duplicates. Their cold timings are not performance qualification.
The duplicate post-state refresh is a promising candidate, but hidden-state and
general-model lifecycle equivalence still need testing. Neither refresh has been
removed from production. Exact overlay sources, comparison metrics and retained
recording hashes are stored privately under
`verification-artifacts/2026-10-21-endpoint-refresh-probe`.


## Fixed ST4B signal and parameter bindings

The compiled-controller option now also binds ST4B signal references and public
parameter readers once. Parameter values, limiter inputs and machine measurements
remain live. The ordinary feedback sweeps and rectifier observations retain their
order; this model does not opt into reordered output scheduling or output reuse.
Custom parameter-provider subclasses retain virtual dispatch.
`setCompiledSt4bBindingsEnabled(false)` selects live ST4B bindings while leaving
other compiled controllers unchanged. Configure before initialization; invalidating
the compiled ST4B structure requires solver reinitialization.

All 44 focused tests pass. The three new checks cover coupled machine/controller
states and history with and without a stabilizer, fault application and clearing,
live references and limits, activation changes, custom parameter providers, and
structural invalidation/reinitialization. Existing parser, PI, coupled solver,
local-ST1 and independent ST4B fixture checks pass. The complete registered suite
and six-case gates were not rerun.

Each mode ran in a separate JVM with twelve warmups and six measured repetitions.
Both use final production classes, one pinned CPU, quarter-cycle steps and 1e-5
dynamic tolerance. Timing includes fresh import, power-flow preparation,
initialization, dynamics and full recording; compilation and collection time
remain included. No solver or model overlays are present in these timing runs.

| Mode | Six complete-case times, seconds | Median, seconds | Median compilation, ms |
|---|---|---:|---:|
| Live ST4B bindings | 13.7850, 13.6838, 13.9211, 13.6668, 13.5635, 13.9480 | 13.7344 | 198.0 |
| Fixed ST4B bindings | 13.3213, 13.0829, 13.2810, 12.9777, 13.0219, 13.2146 | 13.1487 | 211.0 |

The median reduction is 4.26%. All 36 full
recordings match the preceding production trace byte-for-byte: 2,405 finite
samples, all 4,810 channels and both event-side duplicate timestamps. Counters
confirm that all 64 ST4B models use fixed bindings only in the enabled mode.
Identical recordings share storage through verified hard links.

The eight-second objective and independent-reference accuracy remain unresolved.
Exact final sources, classes, test reports, producers, numerical settings and
retained full-recording hashes are stored privately under
`verification-artifacts/2026-10-21-st4b-fixed-bindings`.

### Endpoint refresh: internal-state evidence

Separate private overlays inspected the consecutive post-state evaluations.
The second evaluation changes machine electrical caches, controller inputs and
derivatives; it is not a no-op. At the accepted endpoint, removing that evaluation
preserves the full recording and all audited groups except one pooled input-buffer
group. All audited frame sizes are zero there; those old inputs are overwritten
before an active frame reads them.

A second candidate replaces the first complete evaluation with a machine
electrical-only pass, followed by the ordinary complete evaluation. It preserves
the full recording and every audited accepted-state fingerprint in Case 2. This
is evidence for separating electrical-state synchronization from controller
evaluation, not proof that arbitrary models or intermediate observers are safe.
Neither candidate changes production refresh behavior in this slice.

Both state audits inspect a discovered object graph. The accepted-state audit
also reads current immutable records and governor transport histories, but other
untraversed or replaced objects and intermediate callbacks remain outside its
proof. The raw audit's structural-link flags compare with discovery, not with the
immediately preceding evaluation. The electrical candidate resumed in a separate
JVM after both baselines completed and a storage-link permission failure stopped
the wrapper. These diagnostic timings do not qualify as performance measurements.


## Native controller membership cache

Native machine controller lookup now retains the ordered membership of its
standard containment list. Controller types and activity remain live; this is
not a cached type-to-controller result. List edits invalidate the snapshot before
notification callbacks observe the changed list. Custom type getters and custom
returned lists retain ordinary iteration, including fail-fast mutation behavior
and one call to the controller-list getter. Exposing or installing raw backing
storage permanently disables membership snapshots because retained arrays can
be changed without a list hook. The live comparison control is
`MachineImpl.setControllerMembershipCachingEnabled(false)`.

All 72 targeted checks pass: five new membership tests, 37 existing core network
state/lifecycle tests, 26 coupled solver/controller tests and four reference
checkpoint-integrity tests. Mutation coverage includes 10,000 deterministic
sequences, reordered duplicate types, direct and reflective type writes,
reparenting, notification callbacks, iterator/sublist edits, retained raw-array
changes, custom getter side effects and live enable/disable changes. The full
registered suite and six-case gates were not rerun.

Separate fixed-mode JVMs each run twelve warmups and six measured repetitions.
Both retain fixed ST4B bindings and all preceding production optimizations,
one pinned CPU, quarter-cycle steps, 1e-5 dynamic tolerance, fresh setup and full
recording. The lookup toggle is applied after import, so both modes include the
same import using the new default; counters exclude import. No solver/model
overlays are present. Two extra profiles follow the enabled-mode measurements
and are excluded from the timing samples below.

| Mode | Six complete-case times, seconds | Median, seconds | Median compilation, ms |
|---|---|---:|---:|
| Ordinary membership traversal | 13.3223, 13.0100, 13.1088, 12.8529, 12.8777, 13.1127 | 13.0594 | 209.0 |
| Cached membership traversal | 12.9453, 12.6361, 12.7494, 12.4823, 12.4997, 12.8195 | 12.6927 | 209.5 |

The median reduction is 2.81%.
All 435 imported machine objects receive the selected policy;
the enabled mode records 27,843,511 cached lookups per complete run
after import, while the disabled mode records zero. All 38 complete recordings
retain the preceding production trace byte-for-byte: 2,405 finite samples,
4,810 channels and both event-side duplicates. Identical recordings use verified
hard links to share storage.

The two post-measurement profiles attribute 52.6 to 57.1% of main-thread
execution samples to network solution and 40.3 to 45.2% to other integration
work. Triangular sparse solves alone account for about 11%; repeated bus/current
traversal, voltage scans and controller input preparation remain significant.
These are sampled execution shares, not wall-time phase measurements or an
estimate of recording I/O cost. The next larger gains need to address those
data/evaluation paths rather than only the factorized sparse solve.

The eight-second objective remains unmet, and this unchanged trajectory does not
resolve independent-reference accuracy. Final sources, classes, tests, producers,
post-measurement profiles, settings and retained full-recording hashes are stored
privately under `verification-artifacts/2026-10-21-controller-membership-final`.


## Short-expression scalar experiment

A private candidate evaluates fixed expressions with up to four operands in
scalar Java temporaries. It reads every live operand in source order before
arithmetic and retains the original signs, multiplication/division order,
positive-zero placeholders and summation order. Longer expressions retain the
existing evaluator. No model equations or integration stages change.

The standalone arithmetic probe passes 262,080 comparisons against the original
expression evaluator, including every operator shape through six operands,
signed zero, nonfinite inputs, recursive reads and throwing readers. Of those,
16,320 arithmetic comparisons exercise the short-expression path; the remainder
cover the longer-expression fallback. This is not a controller lifecycle or
system acceptance gate.

Two separate JVMs each perform twelve warmups and six measured runs on one
pinned CPU. Fresh setup, all recording, compilation and garbage collection are
included. The protocol remains ten simulated seconds, quarter-cycle integration
and 1e-5 dynamic tolerance.

| Evaluator | Six complete-case times, seconds | Median, seconds |
|---|---|---:|
| Existing production | 13.0109, 12.7276, 12.8811, 12.5824, 12.5718, 12.9036 | 12.8043 |
| Scalar candidate | 14.9825, 12.7636, 12.6898, 12.6240, 12.6265, 12.8130 | 12.7267 |

The median difference is only 0.61%, with overlapping samples. The candidate's
first measured run includes 2,138 ms of compilation; that run is retained.
All 36 full recordings have the canonical production hash: 2,405 finite samples,
4,810 channels and both event-side duplicates. Identical repetitions use verified
hard links. The candidate is not promoted because this experiment does not show
a convincing complete-run improvement. Production engine sources are unchanged;
the full registered suites were not rerun for this private experiment.

Custom CML blocks already implement numerical equations in Java. The remaining
expression machinery primarily connects their inputs and outputs. A larger
architectural candidate should combine prebound signal wiring and direct Java
equations for a controller, while preserving stage-specific input sampling,
limiter and integration history, live parameters and named-state diagnostics.
Such a kernel needs one authoritative equation definition and explicit lifecycle
tests; duplicating independent generic and optimized equations would create a
maintenance risk.

Private sources, executable probes, full-recording hashes, numerical plans and
timing evidence are retained under
`verification-artifacts/2026-10-21-short-expression-experiment`.
The eight-second objective and independent-reference accuracy remain unresolved.


## Typed PSS2A integration stages

PSS2A now has a typed stage evaluator for its standard fixed controller graph.
The block equations and numerical state storage remain in the existing Java
blocks. Core `tools/generate_pss2a_stage_inputs.py` generates direct input wiring
from the model annotations, retaining operand-read and floating-point reduction
order. Run its `--check` option after editing the model or generated evaluator.
Live gains, setpoints and machine measurements remain live.

The core `CmlCompiledStage` contract separates input preparation/capture,
advancement and capture release. The controller owns phase guards, nested-stage
rejection, invalidation and failure cleanup. The default retains two output
preparations. The PSS2A evaluator uses one only for its recognized acyclic graph
with distinct, pure blocks. Shared blocks retain repeated preparation; custom
getters, variable expression limits and changed wiring retain generic evaluation.
Structural edits require invalidation and rebuilding at an idle boundary.
The model enables this evaluator when its compiled runtime plan is requested;
it does not enable controller-graph compilation for every solver automatically.

The final production rebuild passed 92 focused tests: 45 engine tests and
47 plugin tests, with no failures, errors or skips. Eight new tests cover stage
phases and failures, invalidation, direct predictor/corrector comparisons,
coupled faults, live controls, custom-provider fallback and shared-block ordering.
The generated-input and neutral-language checks pass.

The broader registered core-plugin suite plus the new stage tests discovers
2,618 tests: 2,600 pass, five fail, nine are skipped and four abort. The five
failing test methods match the retained baseline, including the existing
six-case flat-start failure; there are no new failing methods. This does not
establish successful six-case initialization or independent trajectory accuracy.

Separate production JVMs each perform twelve warmups and six measured runs on
one verified CPU. Both use fresh setup, quarter-cycle steps, 1e-5 dynamic
tolerance and ten simulated seconds. Complete recording and writer close,
compilation and garbage collection are included. There are no solver or model
class overlays in these final measurements. Two additional candidate profiles
run afterward and are excluded from the timing samples.

| Evaluation | Six complete-case times, seconds | Median, seconds |
|---|---|---:|
| Generic compiled graph | 12.8985, 12.6692, 12.8043, 12.6549, 12.5769, 12.8127 | 12.7367 |
| Typed PSS2A stage | 12.0969, 11.9115, 11.9014, 11.8140, 11.6897, 11.9495 | 11.9064 |

The paired median reduction is 6.52%.
All 38 complete recordings retain the canonical production hash, with 2,405
finite samples, 4,810 channels and both event-side duplicates. Each enabled
run uses 103 typed PSS2A evaluators and records 989,933 compiled stage calls.
Identical repetitions share storage only after complete byte comparison.
An earlier attempt stopped during warmup because hard-link permission was
unavailable; it supplies no measured timing samples.

The two post-measurement profiles attribute 58.0–61.1% of main-thread execution
samples to network solution and 36.7–38.5% to other integration work. Static-load
complex division, injection-device traversal, voltage scans and sparse solves
remain prominent. These are sampled execution shares, not wall-time phase
measurements or an estimate of recording I/O cost. Further controller work alone
is unlikely to close the remaining approximately 3.9-second gap.

The eight-second objective remains unmet. Equality with the performance control
does not resolve the existing independent-reference accuracy gap. This slice
does not establish six-case or full-system acceptance. Reproducible sources,
classes, test logs, numerical settings, profiles and full-recording hashes are
retained privately under `verification-artifacts/2026-10-21-pss-compiled-stage`.

## Electrical endpoint refresh timing discriminator

A subsequent private solver overlay replaces the first of two post-state full
evaluations with machine-electrical staging, then retains the second full
evaluation and the network solve. The production solver is unchanged. This
experiment includes the committed typed PSS2A stages and uses the same CPU,
fresh setup, quarter-cycle step, 1e-5 tolerance and complete-recording protocol.

After twelve warmups, its six complete-case times are 11.3834, 10.8544, 11.0556,
10.7924, 10.9755 and 11.0403 seconds: median **11.0079 seconds**, 7.55% below the
preceding 11.9064-second production measurement. Two profiles follow, excluded
from the six timing samples. All twenty new full recordings are byte-identical
to the twenty retained compiled-stage production recordings, including all
channels and both event-side duplicates.

This measures the cost of the extra evaluation; it does not qualify the new
ordering for general use. Current hidden-state/history and custom-callback
equivalence must be checked before enabling it in production. The broad suite
reported above used the production solver, not this overlay. The profiles place
63.3–63.7% of main-thread execution samples in network solution, with static-load
division, device traversal, voltage scans and sparse solves still prominent.
The eight-second objective remains unmet even for this experimental path.

The candidate source, classes, producer, settings, profiles and recording hashes
are retained under `verification-artifacts/2026-10-21-endpoint-refresh-timing`.

## Reconciliation with the earlier approximately 8.9-second result

The retained `current-buffer-guard-cache-quarter-warm-20261021` run has a median
of **8.8255475 seconds**, from 8.8855, 8.8438, 8.8255, 8.6700 and 8.6899 seconds.
The newer compiled-stage production median is **11.90644155 seconds**, a real
**3.08089405-second gap** against that experimental implementation. The earlier
result did include fresh setup and full recording. Both use twelve warmups,
CPU affinity mask 1, one active processor, serial GC, synchronous compilation,
ten simulated seconds, quarter-cycle steps and 1e-5 dynamic tolerance.

| Retained run | Complete-case median, s | Setup median, s | Simulation and recording median, s |
|---|---:|---:|---:|
| Earlier combined experimental runtime | 8.8255 | 0.3631 | 8.3857 |
| Current compiled-stage production runtime | 11.9064 | 0.3181 | 11.4992 |

Phase medians are computed separately and need not sum to the complete-case
median. The slowdown is in simulation and recording, not increased preparation.
The complete binary recordings have the same SHA-256. A fresh all-value audit
also finds zero difference over 2,405 finite samples and 4,810 channels, retaining
both event-side duplicates. This does not establish independent-reference accuracy.

The earlier executable loaded a stack of experimental classes ahead of production
classes: direct ST1, ST4B and PSS2A evaluators, scalar static-load/current assembly,
primitive current handoff, and other solver/publication optimizations. The current
production run does not load those overlays. In particular, direct ST4B stage and
output evaluation and scalar static-load assembly have not been carried over in
full. Its ordinary current-publication default also differs from the earlier
buffered path. Several other experimental gains have since been integrated with
lifecycle checks, and this report records that production progression from
approximately 24.9 seconds to 11.9 seconds.

This baseline change must not be presented as surpassing the earlier fast result.
The classpath and code-path audit identifies implementation differences, but
does not assign an exact fraction of the 3.08 seconds to each one; that requires
controlled component comparisons. Retained measurements, settings, effective
classpath and all-channel comparison results are recorded privately under
`verification-artifacts/2026-10-21-performance-baseline-audit`.


## Typed ST4B stage and observation preparation

The standard ST4B graph now binds its input wiring directly to the initialized
runtime wrappers. Core `tools/generate_st4b_stage_inputs.py` generates the wiring
from annotations and provides `--check` to detect drift. The existing Java
blocks remain the authoritative equations and state storage. Function instances
are bound from runtime wrappers, which can differ from annotation field values.

The engine has an optional observation-preparation capability, disabled by
default. ST4B opts in while retaining the original algebraic convergence loop,
both post-stage preparations, ordered feedback sweeps, live rectifier reads,
stored bridge voltage, and predictor/corrector history. Nested observations do
not release pending stage captures. Changed equations, custom blocks, custom
limits and controller subclasses retain generic evaluation. Fixed structure
requires invalidation before rebuilding.

All 99 focused tests pass: 46 engine tests and 53 plugin tests. New coverage
compares predictor and corrector stages, repeated voltage-dependent observations,
state/derivative history, shared-block ordering, live controls, fault coupling,
structural invalidation, fallback and observation failure recovery. Generated
wiring and neutral-language checks pass. The broader registered suite plus both
typed-stage test classes has the same five failing methods as the retained
baseline; its exact counts and failure details are retained with the evidence.
This does not establish successful six-case initialization or independent
trajectory accuracy.

Separate production JVMs each perform twelve warmups and six measured runs on
one verified CPU, with fresh setup, ten simulated seconds, quarter-cycle steps,
1e-5 dynamic tolerance and complete recording. Both keep typed PSS2A enabled.
There are no solver or model class overlays. Two additional candidate profiles
follow and are excluded from the measured repetitions.

| Evaluation | Six complete-case times, seconds | Median, seconds |
|---|---|---:|
| Generic ST4B preparation | 12.0741, 11.7258, 12.0624, 11.7143, 11.7079, 11.9375 | 11.8317 |
| Typed ST4B stage and observation preparation | 11.6627, 11.5535, 11.6521, 11.3358, 11.4117, 11.5956 | 11.5746 |

The paired median reduction is **2.17%**,
or **0.2571 seconds**.
All 38 full recordings retain the canonical binary hash: 2,405 finite samples,
4,810 channels and both event-side duplicates. Each enabled run uses 64 typed
ST4B evaluators and 615,104 compiled integration-stage calls. The fresh control
median reproduces the earlier production result within ordinary run variation.

This recovers only a small part of the gap to the earlier combined experimental
runtime. Main-thread execution samples still place 55.6–60.6% in network
solution; static-load complex division, device traversal, voltage scans and
sparse triangular solves remain prominent. These are sampled execution shares,
not wall-time phase measurements or recording-I/O timing. Current assembly and
the remaining experimental paths require controlled integration and validation.

The eight-second objective remains unmet, and matching the performance control
does not resolve the independent-reference accuracy gap. Sources, classes,
producer settings, test logs, profiles and recording hashes are retained under
`verification-artifacts/2026-10-21-st4b-compiled-stage`.


## Recovered fast-stack checkpoint

The earlier approximately 8.9-second result was not preserved as a single
runnable source commit. Component commits and private experiment archives were
saved, but the command also depended on mutable build directories. The reporting
commit `63038f1ea` contained benchmark documentation, not the entire executable.

A fresh replay with current unshadowed engine classes failed before simulation:
the current bus implementation expected an efficient-voltage API absent from the
old network overlay. Restoring the engine base from `9e479a4f5` resolved that
incompatibility. The recovered replay freezes all 5,287 effective directory
classes before execution; its unshadowed plugin dependencies come from the
current build. It is a recovered composite runtime, not a claim that the exact
historical source checkout was recovered.

With twelve warmups and five measured runs, complete-case times are
8.8609, 8.4059, 8.6535, 8.5163 and 8.3183 seconds: median **8.516267 seconds**.
Single-CPU affinity, ten simulated seconds, quarter-cycle steps, 1e-5 tolerance,
fresh setup and complete recording are verified. All seventeen full recordings
have the same canonical hash as production, with 2,405 samples, 4,810 channels
and both event-side duplicates. This establishes recoverable experimental
performance; it does not establish independent-reference accuracy.

The gap to the newly measured 11.5746-second production path is 3.0583 seconds.
The missing implementation pieces include scalar static-load/current assembly,
primitive current transfer, complete direct stabilizer output evaluation and
ST4B bridge reuse within an evaluation scope. The latest ST4B change restored
typed wiring while retaining live bridge recomputation, so it did not restore
the complete earlier ST4B fast path. Exact per-component timing attribution
still requires controlled comparisons of this recovered stack.

The private `verification-artifacts/2026-10-21-recovered-fast-stack` snapshot
now archives effective classes, classpath resources, available experimental
sources, commands and channel metadata, with external dependencies and large
input/output files identified by hashes. The checked-in checkpoint index is
`checkpoints/case2-recovered-fast-2026-10-21.json`. It requires that private
snapshot and its dependencies; it is not a standalone production source commit.
The eight-second production objective remains open.

## Isolated checks of the recovered performance gap

Four separate JVMs each perform twelve warmups and five measured complete-case
runs. Each retains one verified CPU, fresh setup, ten simulated seconds,
quarter-cycle steps, 1e-5 dynamic tolerance and full recording. The control
repeats the recovered composite runtime with a switchable bridge implementation;
each other configuration disables one optimization group. These experiments
change private overlays, not production defaults.

| Recovered-runtime configuration | Complete-case median, s | Difference from fresh control, s |
|---|---:|---:|
| All recovered paths enabled | 8.5102 | 0.0000 |
| ST4B bridge reuse disabled | 8.9989 | +0.4887 |
| Scalar static-load power/current arithmetic disabled | 9.2061 | +0.6960 |
| Cached current-device traversal and primitive current transfer disabled | 8.2016 | -0.3086 |

The fresh control reproduces the preceding 8.5163-second recovery. Bridge reuse
and scalar load arithmetic each show a measurable benefit in this runtime.
Disabling the current traversal/transfer group is faster in this test; therefore
the missing primitive transfer path cannot be credited with explaining the
production slowdown. Scalar load arithmetic remains enabled in that test.
The scalar-only test retains cached traversal and primitive transfer.

These are conditional differences from separate JVM runs, not additive phase
costs or confidence bounds. The complete three-second production gap is not yet
attributed. Other implementation differences include the direct stabilizer
output path, cached network bus traversal, and production lifecycle/fallback
checks. Their individual costs have not been isolated here.

All 68 complete binary recordings have the canonical hash: 2,405 samples,
4,810 channels and both event-side duplicates. Both load-flow iteration counts
remain unchanged (16 before netting, zero after). The enabled transfer variants
each report 36,370 buffered current solves; the disabled variant reports zero.
This validates measured-output equivalence for this case, not independent
reference accuracy or general correctness of every experimental path.

The checkpointing failure was preserving component commits and benchmark
reports without a complete immutable source snapshot of the integrated fast
runtime. The recovered executable archive now pins the effective classes and
resources, but it remains a composite experimental artifact. It does not turn
the earlier 8.9-second state into a standalone source commit or establish the
eight-second production objective.

Commands, switchable sources/classes, all repetitions, affinity checks and
recording hashes are retained under
`verification-artifacts/2026-10-21-fast-stack-ablations`. The checked-in evidence
index is `checkpoints/case2-fast-stack-ablations-2026-10-21.json`.

## Production scalar static-load arithmetic

Core commit `65425fa8d` integrates scalar static-load power and current
arithmetic into the ordinary production bus implementation. The standard path
retains the original multiply/add and scaled complex-division order, updates
`staticTotalLoad` on every evaluated load call, and preserves the low-voltage
cutoff and stamped-admittance compensation. Eligibility is restricted to
standard bus/network implementations and finite standard complex values.
Custom classes, zero-ZIP fallback and nonfinite arithmetic keep the ordinary
path. Replacing ZIP values invalidates the cached eligibility decision.

The scalar mode is enabled by default and can be disabled between evaluations
with `setScalarStaticLoadEvaluationEnabled(false)`. No primitive current
transfer or cached device traversal is enabled by this change.

All 37 focused tests pass. New tests compare exact floating-point bits and
stored load history across voltage sweeps, both current curves, load shedding,
live ZIP replacement, runtime toggling, zero loads, nonfinite/overflow cases,
low-voltage cutoff, stamped compensation and custom complex arithmetic. The
existing independent 204-point load-curve fixture and coupled controller tests
also pass.

The broader registered suite plus both compiled-controller test classes finds
2,624 tests: 2,606 pass, nine are skipped, four abort and the same five fail as
the retained baseline. Both failing-method identities and failure messages
match; this does not claim a clean overall suite or resolved six-case startup.

Both timing JVMs load production classes, perform twelve warmups and six
measured runs on one verified CPU, and include fresh setup and full recording
for ten simulated seconds at quarter-cycle steps and 1e-5 dynamic tolerance.
Only the scalar-load mode differs; typed ST4B and PSS2A stages stay enabled.

| Production configuration | Six complete-case times, seconds | Median, seconds |
|---|---|---:|
| Scalar load arithmetic disabled | 11.8247, 11.6265, 11.6283, 11.4757, 11.3386, 11.6171 | 11.6218 |
| Scalar load arithmetic enabled | 11.2077, 11.0903, 11.0071, 11.1783, 11.2775, 11.2891 | 11.1930 |

The median reduction is **0.4288 seconds (3.69%)**. All 36 complete binary
recordings retain the canonical hash: 2,405 finite samples, 4,810 channels and
both event-side duplicates. Compilation and garbage-collection time remain
included in measured case time; their per-run values are retained in the
evidence. This production gain is smaller than the isolated recovered-runtime
scalar arithmetic gain and must not be substituted for that experiment.

The eight-second production objective and independent-reference accuracy
remain unresolved. The source checkpoint is saved before timing; commands,
sources/classes, full-recording hashes, test results and dependency hashes are
retained under `verification-artifacts/2026-10-21-scalar-static-load-production`.
The paired source and evidence index is
`checkpoints/case2-scalar-static-load-production-2026-10-21.json`.

## Typed PSS2A observation preparation

Core commit `d81a45fef` and plugin commit `a23b4c022` extend the compiled
PSS2A evaluator to output observations. Integration preparation and dependency
scheduling visit the two input branches in different orders, so the evaluator
retains two separately compiled orders. Observations use the existing pure,
acyclic dependency order. No signal name lookup or graph sorting occurs in the
observation loop.

The engine retains its phase guards, output-expression evaluation, nonfinite
handling and fallback paths. An opt-in scheduled-observation hook never advances
or releases captured integration history. Changed ordering without an exact
typed schedule, shared blocks, custom blocks and unsupported graphs retain
generic evaluation. PSS2A observation preparation is enabled by default and can
be selected before plan compilation with
`setCompiledObservationPreparationEnabled(...)`.

All 41 focused tests pass. New coverage verifies that the typed path is used,
preserves pending predictor/corrector captures, follows observation ordering,
handles shared-block fallback, retains live inputs and recovers from nonfinite
observations. Engine tests cover nested capture, phase restoration and failed
observations. Generated stage wiring and neutral-language checks pass.

The broader registered suite plus both compiled-controller test classes finds
2,626 tests: 2,608 pass, nine are skipped, four abort and the same five fail as
the retained baseline. Both failing-method identities and failure messages
match. The two additional passing tests cover the new observation behavior;
the existing failures and independent-reference accuracy remain unresolved.

Both production JVMs use twelve warmups and six measured runs on one verified
CPU, including fresh setup and full recording for ten simulated seconds at
quarter-cycle steps and 1e-5 dynamic tolerance. Both retain scalar static-load
arithmetic and typed ST4B/PSS2A integration stages. Only typed PSS2A observation
preparation differs.

| Production observation preparation | Six complete-case times, seconds | Median, seconds |
|---|---|---:|
| Generic dependency schedule | 11.2826, 11.1736, 11.3503, 11.1366, 11.1600, 11.2923 | 11.2281 |
| Typed equivalent schedule | 10.9640, 10.5436, 10.9487, 10.4793, 10.5693, 10.8751 | 10.7222 |

The median reduction is **0.5059 seconds (4.51%)**. Each candidate run reports
1,077,075 typed PSS2A observation preparations. All 36 full binary recordings
have the canonical hash, preserving 2,405 samples, 4,810 channels and both
event-side duplicates. The configuration counter covers 435 attached PSS
objects; an initial post-run check incorrectly expected the 103 bound-machine
count. The checker was corrected using the retained completed runs, with no
simulation rerun or changes to timings. The original checker and correction
record are preserved with the evidence.

The eight-second goal remains unmet, and this output-equivalence result does
not resolve independent-reference accuracy. Commands, source checkpoints,
classes, counters, tests and recording/dependency hashes are retained under
`verification-artifacts/2026-10-21-pss-observation-production`.
The paired source and evidence index is
`checkpoints/case2-pss-observation-production-2026-10-21.json`.

## ST4B bridge input arithmetic and remaining runtime gap

Plugin commit `1cbb68db2`, paired with core commit `d81a45fef`, reuses the
ST4B bridge input calculation only when its live inputs have identical bits.
The phasor cache keys on gain and angle; the voltage calculation keys on the
phasor, terminal voltage, terminal current, reactance and current gain.
Every evaluation still reads terminal voltage, terminal current and field
current in the existing order. Field current, rectifier evaluation, limits
and the stored bridge output remain live. Nonfinite inputs and custom complex
implementations retain ordinary evaluation.

This differs from the recovered experimental bridge cache, which reused a
broader result within an evaluation scope. Restoring typed block wiring alone
did not restore that cache. The input-checked implementation keeps the existing
measurement callbacks and parameter-sampling boundaries.

All 21 focused tests pass, including five new tests for unchanged inputs,
individual key changes, live field current and limits, signed zero, nonfinite
values, custom arithmetic and getter side effects. Coupled stage tests compare
reuse against disabled reuse while checking state and stored-output behavior.

Both ordinary production JVMs perform twelve warmups and six measured runs on
one verified CPU. Fresh setup and full recording are included for ten simulated
seconds, quarter-cycle steps and 1e-5 dynamic tolerance. Only bridge input reuse
differs between them.

| Production bridge input reuse | Six complete-case times, seconds | Median, seconds |
|---|---|---:|
| Disabled | 10.9116, 10.6432, 11.0447, 10.5410, 10.4899, 10.8917 | 10.7675 |
| Enabled | 10.7909, 10.3501, 10.4498, 10.3713, 10.3750, 10.7988 | 10.4124 |

The paired reduction is **0.3551 seconds (3.30%)**. Each enabled run reports
7,534,228 bridge input reuses and 7,995,520 phasor reuses. The complete recording
hash remains identical to the performance control.

The remaining complete-case gap to the recovered composite runtime's
8.5163-second median is **1.8961 seconds**. Separately calculated phase medians
locate the difference in simulation plus recording:

| Phase | Recovered composite, s | Current production, s |
|---|---:|---:|
| Setup and initialization | 0.3544 | 0.3463 |
| Simulation and output | 8.0715 | 10.1086 |

Phase medians need not sum to the complete-case median. Both paths use sixteen
power-flow iterations before netting and zero afterward. This comparison does
not yet isolate the remaining difference among network traversal, controller
evaluation, lifecycle checks and recording. Earlier single-feature experiments
cannot be added as an explanation of the whole gap. In particular, disabling
the experimental primitive current transfer group was faster in its isolated
comparison.

There was no complete runnable source commit for the original approximately
8.9-second result. Component commits and reports omitted the effective mutable
build-directory overrides. Commit `e2e0a892f` now indexes the recovered immutable
executable snapshot, but it is not the missing historical source checkpoint.
New production checkpoints identify paired core/plugin commits and retained
runner, dependency and recording hashes. The checked-in
`tools/benchmark-reactor/pom.xml` builds both source repositories together to
avoid unintentionally loading an older installed core artifact.

The broader registered suite and explicit controller tests find 2,631 tests:
2,613 pass, nine are skipped, four abort and the same five fail as the retained
baseline. Failing-method identities and failure messages both match. All 36
recordings have the canonical hash and retain 2,405 finite samples, 4,810 channels
and both event-side duplicates. The paired source and evidence index is
`checkpoints/case2-st4-bridge-input-production-2026-10-21.json`; private evidence
is retained under `verification-artifacts/2026-10-21-st4-bridge-input-production`.

The eight-second goal and independent-reference accuracy remain unresolved.

## Machine frame trigonometry reuse

Two fresh warmed execution profiles of the preceding production checkpoint put
59.6% and 61.1% of main-thread execution samples in network solution. Machine
current evaluation remains prominent. These are sampled execution shares, not
wall-time phase measurements. Both diagnostic recordings retain the canonical
trace; their instrumented times are excluded from qualifying performance.

Core commit `54f6a5118`, paired with plugin commit `ed0ed59bc`, adds a
per-machine cache of sine and cosine keyed by the exact rotor-angle bits.
Terminal voltage, current, electrical power, and controller states are still
evaluated on every call. The forward and reverse transformations retain the
existing arithmetic order and allocate fresh result vectors. Changing voltage
or machine base factors therefore remains immediately visible at a fixed angle.
Custom complex readers keep the ordinary path, and nonfinite angles are not
reused. `setFrameTransformReuseEnabled(...)` selects the control path; reuse is
enabled by default.

All 36 focused tests pass: four new exact-arithmetic and machine lifecycle
tests plus 32 existing machine/controller checks. New coverage includes random
and extreme angles, signed zero, nonfinite recovery, custom reader order,
fresh result storage, repeated network iterations with changing voltage, live
base-factor changes and both round-rotor and salient-pole integration states.

Both production JVMs perform twelve warmups and six measured complete-case
runs on one verified CPU. Fresh setup and full recording remain included for
ten simulated seconds at quarter-cycle steps and 1e-5 dynamic tolerance. Only
machine frame reuse differs; the previous load and controller optimizations
remain enabled in both configurations.

| Machine frame reuse | Six complete-case times, seconds | Median, seconds |
|---|---|---:|
| Disabled | 10.7788, 10.8262, 10.6078, 10.5196, 10.4686, 10.9062 | 10.6933 |
| Enabled | 10.3527, 10.2144, 10.1681, 9.8992, 9.9791, 10.2192 | 10.1913 |

The paired reduction is **0.5020 seconds (4.69%)**. Each enabled run reports
23,857,412 transform-cache hits. The fresh control is slower than the previous
session's 10.4124-second result; the improvement above uses the paired control,
not that historical value. Compilation and collection remain inside measured
case time. The production median remains **2.1913 seconds above the objective**.

This output-preserving performance change does not resolve the remaining
independent-reference accuracy gap. The eight-second goal remains open.

The final source checkpoint is core `bdff8b2e9` with plugin `ed0ed59bc`.
The follow-up corrects an overly strict raw-NaN-sign assertion and removes
trailing whitespace; all eleven affected runtime class files are byte-identical
to the timed `54f6a5118` implementation. Finite values, infinities and signed
zeros retain exact-bit assertions; invalid results must agree in NaN
classification. The original rejected gate remains in the private evidence.

The completed broader gate finds 2,635 tests: 2,617 pass, nine are skipped,
four abort and the same five fail as the preceding baseline. Both failure
identities and messages match. All 36 timed-run recordings have the canonical
hash, with 2,405 finite samples, 4,810 channels and both event-side duplicates.
Sources, commands, profiles, focused reports, both broad gates, bytecode
identity checks and recording hashes are retained under
`verification-artifacts/2026-10-21-machine-frame-production-v2`.
The paired source/evidence index is
`checkpoints/case2-machine-frame-production-2026-10-21.json`.

## Guarded electrical endpoint preparation

Plugin commit `287326d65`, paired with core `bdff8b2e9`, separates the first
post-integration electrical preparation from the final controller evaluation.
The solver previously evaluated the complete device stack twice before the
network solve. Eligible standard ST1/ST4B stacks now refresh machine electrical
caches in the first pass and evaluate their controllers in the second pass.
Renewable and grid-forming preparation remains in both passes. Numerical
equations, integration step, network tolerance and recording are unchanged.

Eligibility requires standard machine classes, an appropriate compiled exciter
path, and either no governor or GGOV1. Attached stabilizers require the
compiled standard PSS2A graph.
Mechanical-power and voltage-derivative input selections, relevant observers,
unsupported controller stacks, shared ST4B blocks and custom limits retain the
original preparation. ST1 eligibility also requires that its compiled local
advance actually ran in the current step. Reinitialization clears bindings and
the preparation counter. The pre-initialization switch
`setElectricalEndpointPreparationEnabled(false)` retains the control path.

An unrestricted diagnostic prepass measured 9.5366 seconds against 10.2099
seconds for its control. That result does not qualify the unrestricted change
for general use. The guarded source implementation was measured separately,
with twelve warmups and six repetitions in each JVM, one verified CPU, fresh
setup, ten simulated seconds, quarter-cycle steps, 1e-5 dynamic tolerance and
complete recording:

| Guarded preparation | Six complete-case times, seconds | Median, seconds |
|---|---|---:|
| Disabled | 10.1681, 9.9963, 10.1853, 9.9453, 9.8329, 10.1326 | 10.0645 |
| Enabled | 9.9386, 9.6532, 9.7501, 9.5304, 9.5090, 9.7492 | 9.7012 |

The paired reduction is **0.3633 seconds (3.61%)**. Each enabled run replaces
211,376 controller preparations, covering 88 eligible machines across 2,402
steps. All 36 recordings retain the canonical hash, 2,405 finite samples,
4,810 channels and both event-side duplicates. The production result remains
**1.7012 seconds above the eight-second objective**.

All 46 focused tests pass, including coupled state/history comparisons through
faults, reference and limiter changes, governor participation, status changes,
observer fallback, live stabilizer selection and reinitialization. The broader
registered suite plus explicit controller tests finds 2,645 tests: 2,627 pass,
nine skip, four abort and the same five fail as the preceding baseline, with
matching failure methods and messages.

A separate read-only accepted-state audit discovers 164,206 numeric slots and
12,919 fingerprint groups. Both repeated control and guarded preparation have
zero differing groups across 2,402 steps. This audit includes current immutable
records and complex values, while excluding the network/bus/algorithm objects,
synthetic callbacks, listed diagnostic counters and the configuration switch.
It supplements the coupled tests and full recordings; it is not a proof of
arbitrary custom callback equivalence or independent-reference accuracy.

The source/evidence index is
`checkpoints/case2-guarded-endpoint-production-2026-10-21.json`. The private
`verification-artifacts/2026-10-21-guarded-endpoint-production` archive retains
the producers, changed sources/classes, tests, diagnostics and timing metadata.
The manifest pins all effective directory-class hashes, external dependencies
and retained input/output files. Large recordings remain referenced by hash.
The performance objective and independent-reference accuracy remain open.

Two follow-up warmed profiles of the same implementation place 58.9% and
63.3% of main-thread execution samples in network solution. Sparse triangular
solves account for 11.4–12.4% of leaf samples, machine current output for
9.5–11.4%, and indexed voltage-convergence scanning for 6.0–6.8%. Device-list
iteration remains visible. These are sampled execution shares, not wall-time
phase costs. They make current assembly and convergence scanning concrete
targets for the next optimization; controller-only work cannot explain the
entire remaining cost.

Profile times are excluded from the qualifying timings above. All fourteen
profile-producer recordings, including twelve warmups, retain the canonical
hash. The follow-up profile manifest is retained separately under
`verification-artifacts/2026-10-21-guarded-endpoint-profile` and linked from
the same checkpoint index; its effective directory classes match the timed
implementation.

## Scalar voltage convergence scan

Core commit `7a9808da4`, paired with plugin `3119e08c3`, reads validated
rectangular bus coordinates directly during convergence scanning. It avoids
temporary complex differences while preserving the existing norm arithmetic,
inclusive magnitude threshold, strict component threshold, first-invalid-bus
behavior, history publication and leading-bus identity. Polar coordinates,
unvalidated tables and diagnostic alert callbacks retain the ordinary path.

Both production JVMs perform twelve warmups and six measured runs on one
verified CPU, with fresh setup, ten simulated seconds, quarter-cycle steps,
1e-5 dynamic tolerance and complete recording:

| Scalar scan | Six complete-case times, seconds | Median, seconds |
|---|---|---:|
| Disabled | 9.9553, 9.6043, 10.3844, 9.7382, 9.4208, 9.7011 | 9.7196 |
| Enabled | 9.6718, 9.4978, 9.7473, 9.5447, 9.4230, 9.7505 | 9.6082 |

The paired median reduction is **0.1114 seconds (1.15%)**. The individual
timings overlap; this is a small measured improvement, not a confidence bound
or an explanation of the remaining difference from the recovered executable.
All 36 recordings have the canonical binary hash, retaining 2,405 finite
samples, 4,810 channels and both event-side duplicates.

All 41 focused tests pass. Initial testing exposed an object-identity difference
for efficient voltage publication; the final implementation preserves the
ordinary path's cached-object identity as well as its numerical values.
The broader registered suite plus explicit controller and convergence tests
finds 2,669 tests: 2,651 pass, nine skip, four abort and the same five fail as
the preceding baseline, with matching failure methods and messages.
The source/evidence index is
`checkpoints/case2-scalar-voltage-production-2026-10-21.json`; its private
archive pins the changed source/classes, all effective directory-class hashes,
external dependencies, producers, tests and retained input/output hashes.
The eight-second objective and independent-reference accuracy remain open.

## Source-current arithmetic reuse: not adopted

A switchable experiment reused the voltage-independent Norton-source arithmetic
for the round-rotor and salient-pole machine paths. Raw-bit keys covered both
internal-voltage components, conductance, susceptance and angle. Terminal current,
electrical power, field-current measurements and the base-factor getter remained
live; the scaled output retained a fresh object. Nonfinite results bypassed reuse.

All 36 focused tests passed, including exact arithmetic, signed zero and nonfinite
inputs, getter order, live voltage/base changes, reinitialization and integrated
machine states. Each timing JVM used twelve warmups and six measured complete-case
runs with the same one-CPU, 10-second, quarter-cycle, 1e-5 settings and full recording.

| Source arithmetic reuse | Six complete-case times, seconds | Median, seconds |
|---|---|---:|
| Disabled | 9.7539, 9.5013, 9.7558, 9.4065, 9.3621, 9.7191 | 9.6102 |
| Enabled | 9.8020, 9.4793, 9.7077, 9.5292, 9.3877, 9.7883 | 9.6184 |

The enabled median was 0.0082 seconds slower, despite 9,892,693 reuse hits per
case. This comparison demonstrates no performance benefit. All 36 recordings
retained the canonical hash. The candidate was removed, leaving core `7a9808da4`
as the production implementation; the experiment is retained as a source patch
and compiled-class snapshot rather than an adopted source commit.

The index `checkpoints/case2-norton-source-probe-2026-10-21.json` pins the failed
experiment's sources, runtime hashes, producers, tests and timing evidence.
After removing it, all 44 targeted rebuild tests pass and all 5,272 effective
runtime-class hashes match the saved scalar-scan production checkpoint. The
restoration reports and hash checks are retained alongside the experiment.
The result rules out this particular arithmetic cache as a useful next step.
It does not show that the entire machine-current evaluation is inexpensive.

## Controller-scoped terminal-current samples: not adopted

A second experiment sampled terminal current lazily within one compiled ST4B
output or integration evaluation. It cleared the sample at the scope boundary
and around stabilizer callbacks. Standard machine, data, bus and controller-graph
checks excluded custom readers, observers, shared blocks and custom limits.
Field current and bridge limits remained live. The candidate was switchable and
disabled by default.

All 33 focused tests passed, including coupled fault trajectories, live controls,
direct predictor/corrector state checks, shared-block fallback, missing machine
data, observer fallback and a mutating stabilizer with nested output calls.
A pre-hardening full-case audit found zero differing groups across 164,463 numeric
slots and 12,922 accepted-history groups. Its exact source/classes are retained
separately; the final implementation additionally checks missing machine data.

The hardened implementation was measured in separate JVMs with twelve warmups
and six repetitions each, one verified CPU, fresh setup, ten simulated seconds,
quarter-cycle steps, 1e-5 tolerance and complete recording:

| Scoped current sample | Six complete-case times, seconds | Median, seconds |
|---|---|---:|
| Disabled | 9.8346, 9.6578, 9.8362, 9.6020, 9.6186, 9.8923 | 9.7462 |
| Enabled | 10.3025, 10.1101, 10.3299, 10.0799, 10.0839, 10.1269 | 10.1185 |

Despite avoiding 2,810,980 complete terminal-current evaluations per case, the
candidate was **0.3723 seconds slower (3.82%)**. All 36 qualifying recordings
retained the canonical hash. The preliminary control and state-audit recordings
also match it. Reducing call count did not reduce total runtime in this design;
the additional cost has not been isolated to a specific guard or JVM effect.
The candidate was removed, and the 9.6082-second production checkpoint remains
the accepted implementation.

`checkpoints/case2-st4-measurement-probe-2026-10-21.json` indexes the source patch,
compiled classes, exact pre-hardening snapshot, state audit, tests, commands,
timings and retained recording hashes. This experiment does not achieve the
eight-second objective or establish independent-reference accuracy.
After removal, all 29 targeted rebuild tests pass. The complete inventory of
5,272 effective runtime classes matches the saved production checkpoint, with
no differing or extra classes. The index includes the restoration evidence.


## Direct stabilizer evaluator comparison and selected working baseline

Two private PSS2A comparisons replace output evaluation or combined stage/output
evaluation on the current engine, retaining setup, network tolerance, CPU affinity
and full recording. The output-only pair has
medians of 9.6027 seconds disabled and 9.5813 seconds enabled. The combined
stage/output pair has medians of 9.6912 seconds disabled and 9.5461 seconds
enabled. Each uses twelve warmups and six measured repetitions. Run ranges
overlap; these measurements do not establish recovery of the remaining second.
The combined path executes 778,454 direct stages and 865,596 direct output
evaluations per case. All 72 timed-protocol recordings have the canonical hash.
The candidates remain private diagnostics and are not adopted. All 5,272
production directory classes remain identical to the scalar-scan checkpoint.
See `checkpoints/case2-pss-direct-evaluation-probe-2026-10-21.json`.

The frozen recovered executable is now selected as the working performance
baseline, as requested. The repeatable runner verifies the archived runtime,
case inputs and Maven dependencies, then uses only the extracted frozen runtime
and those dependencies. No mutable build directory is on the runtime classpath.
The current production source remains available separately; this selection does
not create the missing historical source checkout or qualify reference accuracy.

```powershell
python tools/run_bounded.py --log target/fast-checkpoint-replay.log -- python tools/replay_case2_fast_checkpoint.py --output target/fast-checkpoint-replay
```

Use a new output/log name for each replay. The private archive and retained
dependencies are required. Staging is limited to 256 MiB and requires 3 GiB free;
the bounded runner maintains the existing disk and log limits. Every channel and
event-side duplicate is retained; identical repeated recordings use hard links.

The fresh archive-only replay measured 9.0264, 8.3822, 8.5732, 8.5065 and 8.4001
seconds after twelve warmups: **8.5065 seconds median**. Fresh setup and full
recording remain included, with one CPU, ten simulated seconds, quarter-cycle
steps and 1e-5 network voltage-correction tolerance. All seventeen full recordings
match the canonical hash. The eight-second goal remains unmet by about 0.51
seconds. Selection and replay evidence are indexed in
`checkpoints/case2-working-performance-baseline.json`.
