# Coupled reactive-limit release investigation

Core commit `8b1b0ed77` replaces the fixed-neighbor estimate used to admit a
reactive-limit release with the coupled polar Newton electrical response.
Physical parameter values and reactive bounds are unchanged. A positive finite
voltage sensitivity admits a transactional trial only when the predicted target
Q lies within the existing admission margin. Failed trials retain the existing
rollback behavior. The predictor does not change voltages while querying them.

This fixes a concrete Case 3 error. The old estimate rejected bus 5170 because
it assumed adjacent voltages could not respond. The supplied reference requires
7.267192 Mvar at 1.0 pu, within the generator's limits. The new candidate reaches
7.267151 Mvar at 1.0 pu in 14 preparation iterations, with no unsettled controls.
Across all 2,000 buses the worst cold voltage difference is 0.0000113015 pu,
compared with the previous 0.0155448 pu error. The first physical cold row is
compared directly; no fitted time or angle offset is applied.

| Case | Preparation iterations before netting | Short-run outcome |
|---|---:|---|
| 1 | 15 | Completes 0.1 s; recordings byte-identical to control |
| 2 | 16 | Completes 0.1 s; recordings byte-identical to control |
| 3 | 14 | Completes 0.1 s using the isolated four-substep renewable diagnostic |
| 4 | 15 | Existing integration failure at 0.0458333 s remains |
| 5 | — | Existing preparation failure remains |
| 6 | 22 | Existing integration failure at 0.05 s remains; failure prefix byte-identical |

Cases 1, 2 and 6 have identical prepared bus voltages/angles and generator P/Q.
The serialized preparation JSON differs in field order, so those comparisons
use parsed values. All probes use one CPU, a quarter-cycle network step and
1e-5 dynamic voltage convergence tolerance; these cold short runs are not
warmed ten-second timing evidence. No claim of reaching the eight-second goal
is made.

## Admission-margin issue at the first checkpoint

At commit `8b1b0ed77`, the candidate was **not qualified for benchmark promotion**. The Case 4
reference comparison exposes an existing admission margin that subtracts the
power-flow tolerance from the physical Q range. At bus 2100, the 0.001 pu
tolerance becomes a 0.1 Mvar release margin. The generator's reference Q is
31.850538 Mvar, only 0.099462 Mvar below its 31.95 Mvar maximum. The coupled
estimate therefore keeps this bus at its limit, whereas the old approximate
estimate happened to permit the release.

This changes the worst Case 4 cold voltage error from approximately 0.00000496
pu to 0.000051367 pu at bus 2100. The margin must distinguish numerical
admission uncertainty from a physical control deadband; reducing it merely to
fit this case would not establish a general fix. Add an independently solvable
near-bound regression and verify nonlinear feasibility and anti-cycling before
changing the rule.

## Nonlinear acceptance follow-up

Core commit `dd6a03d6e` separates the release admission margin from the AC
residual tolerance. The physical Q range now retains only the existing numerical
interior guard of 1e-10 pu. A provisional release is accepted only when both the
AC residual and the calculated reactive output satisfy their respective checks.
If Q remains outside its permitted range, the transaction keeps ownership of the
solve and receives another electrical correction, even below the ordinary AC
tolerance. It cannot be skipped by the settled-voltage waiting branch or
interleaved with an HVDC outer update. The configured tolerance stays at 0.001 pu.

An independently solvable three-bus series network demonstrates why both changes
are necessary. Its linear prediction is 0.05 pu Q, and its exact nonlinear value
is 0.0505 pu. With Qmax=0.05055 pu, subtracting the residual tolerance incorrectly
rejects the feasible release. Removing that margin alone accepts an insufficiently
settled value of 0.05076734 pu, above Qmax. The complete change refines the solution
and admits the feasible release. With Qmax=0.05025 pu, the nonlinear trial is
rejected within the iteration budget. The existing conservative policy restores
the original fixed-Q state and blocks that release for the solve; it does not
search for a solution at the opposite bound.

Case 4 now prepares in 17 iterations. Bus 2100 reaches 31.85052464 Mvar at 1.02 pu,
versus the supplied 31.85053825 Mvar reference. Across all 2,000 buses, the largest
cold voltage error returns to 4.9585166e-6 pu at bus 3051, down from 5.1367001e-5 pu.
The largest cold rotor-angle difference is 5.6380247e-5 degrees. These are cold
point comparisons, not complete trajectory acceptance.

All six short probes were repeated. Cases 1, 2 and 6 retain byte-identical
recordings to the earlier coupled-predictor runs. Case 3 changes only at roundoff
scale relative to the margin-only candidate: maximum voltage difference
1.6431301e-14 pu and rotor-angle difference 8.7840846e-13 degrees. Its preparation
still takes 14 iterations and retains the isolated four-substep renewable
diagnostic. Case 5 preparation and Cases 4/6 early integration failures remain.
No new warmed ten-second timing result is claimed.

All 17 focused tests pass. The parent fails five of these tests, and the
margin-only candidate fails two. The broader selection passes 307 of 308 tests;
the same pre-existing VSC assertion below fails with unchanged expected/actual
values. Three changed production classes compile. The existing full Maven test
compilation limitation remains. The installed dependency jar was not replaced.
The [follow-up evidence index](checkpoints/reactive-release-refinement-2026-10-22.json)
retains the source, tests, comparisons and output hashes.

## Remaining initialization discrepancy

At this checkpoint, separate generator-Q discrepancies remained at bus 2065:
94.0449 Mvar in Case 3 and 98.2580 Mvar in Case 4. The subsequent
[default reactive-sharing correction](texas2k-reactive-sharing-2026-10-20.md#default-preparation-and-capability-tracking-october-22)
removes these large allocation errors. Voltage agreement alone does not validate
generator/shunt reactive-power allocation or dynamic initialization.

## Verification and limits

Eleven focused tests pass, including free and regulated neighboring buses,
minimum/maximum reactive releases, stiff-network rejection, invalid targets,
bit-preserving queries and an augmented zero-impedance connection. The latter
uses a fresh nonlinear solve after manually changing its active set; the live
solver normally rebuilds auxiliary mismatch within its transition lifecycle.

The broader selection compiles against freshly built core classes and runs 302
tests: 301 pass. The VSC legacy-versus-reduced comparison fails identically with
the old predictor: expected 0.024608710249774107, actual 0.02460871053658383.
Its assertion was not relaxed. The full Maven gate stops at test compilation
because `IndexedVoltageRecordTableTest`, `RevisionVoltageValidationTest` and
`ScalarVoltageScanTest` reference the absent `IndexedVoltageRecordTable` API.
Production compilation succeeds. These failures remain visible; there is no
full-suite acceptance claim.

The response matrix is currently factorized for every eligible admission query.
This avoids stale sensitivity reuse after a voltage, topology or active-set
change. A future cache must have solve-local invalidation and measured benefit.
No dynamic-network factorization policy is changed.

The [evidence index](checkpoints/network-reactive-limit-release-2026-10-22.json)
retains source, tests, logs, reference comparisons and hashes for the complete
short-run recordings. Full outputs remain in the generated-artifact store;
supplied references are preserved.

## Preparation limit deadband and Case 5 startup

The shared benchmark preparation now caps the reactive-limit tolerance factor
at one for both the initial and post-netting solves. It preserves a caller's
stricter factor and restores the original factor on success or failure. The
electrical tolerance remains 0.001 pu; the dynamic voltage-correction tolerance
remains 1e-5 pu and the network interval remains 1/240 second. The effective
reactive-limit tolerance is recorded in run status.

Previously the default factor of ten allowed a PV bus to require up to 0.01 pu
beyond a reactive limit before enforcing that limit. Final machine allocation
could then clip Q without resolving the changed power balance. In Case 5,
bus 5382 retained 1.04 pu while its physical machine allocation left a
0.7604673 Mvar deficit. Its machine and controller initial derivatives were near
roundoff, so a controller-equilibrium check alone did not expose this network
inconsistency. Enforcing the limit produces 1.0398735121 pu at that bus and
reduces its deficit to roundoff. The largest initial current residual across
the network falls from 0.0073121856 to 0.0001074055 pu, now at bus 1033.

The cold point comparison against the supplied Case 5 reference improves:

| Maximum absolute error | Previous preparation | Corrected preparation |
|---|---:|---:|
| Bus voltage, pu | 0.000159421 | 0.00000145241 |
| Generator Q, Mvar | 0.147939 | 0.00172481 |
| Rotor angle, degrees | 0.00504456 | 0.000487984 |
| Generator P, MW | 0.00519893 | 0.0104693 |

The active-power maximum is the balancing unit 7389/1. These are cold
initialization errors, not errors over the prefault trajectory. No measured
power or voltage was inserted into the solution.

All 42 focused tests pass. An independent lossless two-bus test enforces both
upper and lower reactive bounds with a 0.005 pu required-Q excess at a 0.001 pu
electrical tolerance. The old preparation leaves the bus regulating and fails
the physical-balance regression. Additional checks cover a stricter caller
factor, recorded effective tolerance, and restoration after a post-netting
exception. The negative control also fails the new reporting and in-solve
configuration assertions; those are distinct from the physical-balance failure.

Six single-core short probes retain complete recordings. Cases 1, 2, 3, 4 and 6
are byte-identical to the preceding checkpoint. Case 3 retains its earlier
experimental four local renewable steps. Cases 4 and 6 still fail at 0.0458333
and 0.05 seconds. Case 5 preparation still takes two solves before netting and
one after; the status field for iterations describes the last solve, not the
aggregate iteration count.

Case 5 with one renewable step still fails at 0.0333333 seconds. A separate
four-local-step diagnostic completes 0.1 seconds while keeping the quarter-cycle
network interval. All 4,919 comparable channels were scored against the supplied
reference. Its largest voltage error is 0.04013395 pu at bus 1007 and its largest
rotor-angle error is 0.2954107 degrees at 1050/1; worst voltage NRMSE is
0.04268944 using the full ten-second reference range. This remains far outside
acceptance. Local steps are not promoted by this preparation fix.

The initialization audit also has a small observable numerical effect: enabling
the retained audit changes recorded powers by at most 2.6006e-12 MW/Mvar over
the original short prefix. The failed byte-equality assertion and raw results
are retained. The four-step prior-preparation comparison uses that audited
diagnostic and is identified accordingly.

No new warmed ten-second timing or full-system accuracy acceptance is claimed.
The next dynamic investigation should follow the early renewable/network
response near bus 1007 and machine 1050/1, independently of the corrected
5382 operating point. The existing full-reactor compilation limitations above
remain; this slice used an isolated compatible runtime. The
[evidence index](checkpoints/preparation-reactive-limit-tolerance-2026-10-22.json)
records the tests, cold comparisons, short trajectories and reproduction inputs.

## Case 5 selector and integration diagnostics

With the corrected preparation above, a separate diagnostic varied the global
step and the reactive-current recovery selector of converters initialized at
exactly zero Q. The supplied cold measurements have tiny nonzero Q values for
many of these units. Their signs were used only in explicitly marked
counterfactual runs; solved P/Q, continuous states, and physical parameters
were not replaced with measurements. Of 117 inspected units, 72 selected the
negative branch, 39 the positive branch, and six remained zero. The existing
independent exact-zero probe still establishes that exact zero selects neither
recovery bound. This experiment does not justify adding an epsilon or choosing
a default sign in production.

All 4,919 comparable channels were scored against the supplied reference,
without fitted offsets. Maximum absolute errors over the available nonnegative
prefix are:

| Variant | Network step | Renewable local steps | Last time, s | Maximum voltage error, pu | Maximum rotor error, degrees |
|---|---:|---:|---:|---:|---:|
| Original selectors | 1/240 s | 1 | 0.0333333, failed | 0.329660 | 0.129103 |
| Original selectors | 1/240 s | 4 | 0.1 | 0.0401340 | 0.295411 |
| Original selectors | 1/960 s | 1 | 0.1 | 0.0327734 | 0.121325 |
| Measured-sign selectors | 1/240 s | 1 | 0.1 | 0.0321156 | 0.816907 |
| Measured-sign selectors | 1/240 s | 4 | 0.1 | 0.0217093 | 0.411995 |
| Measured-sign selectors | 1/960 s | 1 | 0.1 | 0.00200178 | 0.0225923 |

Changing selectors alone prevents the early failure but increases rotor error.
Combining measured signs with the finer network step improves agreement much
more than either alone. The combined arm still fails the prefault limits: worst
voltage NRMSE is 0.00153639 using the full ten-second reference range, and
maximum rotor error is 0.0225923 degrees. Its worst rotor RMSE over this short
prefix is 0.0118854 degrees. These are not ten-second acceptance results.

Cold binary records are identical between selector variants and their controls
at the same global step, including timestamps and all recorded physical values.
Across global steps, the startup duration differs: two history steps span
8.333333 ms at 1/240 second and 2.083333 ms at 1/960 second. The reference has
two milliseconds of negative-time records. Consequently the global-step test
also changes startup duration; it does not isolate truncation error alone.
The reference itself moves before fault application, so a flat response is not
the comparison target. No time shift was fitted to remove this difference.

The retained comparison includes a common 0.0333333-second window as well as
each available prefix. Samples remain on each run's actual grid, so RMSE
weighting differs between fine and quarter-cycle grids. No full-network
channels or startup samples were removed from the raw recordings.

These findings localize sensitivity to startup, recovery selection and network
coupling. They do not establish invalid physical parameters or authorize
automatic parameter corrections. No production code or default changed, and
no new warmed performance result is claimed. The next discriminator must
separate startup duration from step size while retaining independently solved
initial conditions. The [evidence index](checkpoints/case5-selector-step-localization-2026-10-22.json)
retains the commands, diagnostic harness, runtime hashes, complete short traces,
cold invariance checks and channel errors.

### Equal startup duration discriminator

A follow-up harness allows eight diagnostic startup steps, with the production
solver unchanged. Three fresh single-core runs at 1/960 second complete the
0.1-second prefix: a two-step measured-selector control and eight-step arms
with original or measured selectors. The two-step control reproduces both
the full binary recording and selected-channel CSV byte for byte.

Eight fine startup steps span 8.333333 ms, equal to two quarter-cycle steps.
For each selector policy, the eight-step and earlier two-step fine runs have
bit-identical physical values for all 99 shared sample ordinals, including
the cold sample and negative-time history. Their clock labels differ by
6.25 ms. No time shift was applied to the supplied-reference scoring.

| Fine-step variant | Startup duration, ms | Maximum voltage error, pu | Maximum rotor error, degrees | Worst rotor RMSE, degrees |
|---|---:|---:|---:|---:|
| Original selectors, two steps | 2.083333 | 0.0327734 | 0.121325 | 0.0402607 |
| Original selectors, eight steps | 8.333333 | 0.0403084 | 0.326378 | 0.222578 |
| Measured selectors, two steps | 2.083333 | 0.00200178 | 0.0225923 | 0.0118854 |
| Measured selectors, eight steps | 8.333333 | 0.0282004 | 0.445024 | 0.279314 |

Errors cover each full available 0.1-second nonnegative prefix. The eight-step
original-selector rotor maximum is at 1050/1 at 0.0802083 seconds; the
measured-selector maximum is at the same unit at 0.096875 seconds. All 4,919
comparable channels and their maximum-error times are retained. At equal
startup duration the fine measured-selector rotor maximum is still lower than
the quarter-cycle arm's 0.816907 degrees, so integration resolution also matters.
The earlier fine-step improvement cannot be attributed to integration
resolution alone: advancing further before time zero changes the comparison
substantially even with an identical underlying fine-step trajectory.

This result does not justify retiming a completed output. A distinct startup
interval would require an explicit integration lifecycle and independently
validated transition to the quarter-cycle runtime interval. The current solver
rejects a changed global step; global and local-ST1 histories use fixed-step
coefficients, while the single-precision renewable path retains a separate
STORE recurrence. Simply removing the fixed-step guard would be incorrect.
No production step policy, selector, physical parameter or acceptance criterion
changed. The eight-second full-run goal remains open, and these cold short
runs are not new performance measurements. The
[equal-startup evidence index](checkpoints/case5-equal-startup-duration-2026-10-22.json)
retains the harness, commands, runtime inventory, raw recordings and comparisons.

### Independent step-transition contract and startup prototype

A three-bus reference probe isolates the converter's filtered-voltage lag:
input 1 pu, time constant 0.02 seconds, forced initial state and integration
base 0.9 pu, and zero initial derivative history. It advances twice at 1 ms,
three times at 1/240 second, then three times at 1 ms. Two cold runs produce
identical channel files and state snapshots. This is a deliberately forced
scalar response, not a physical fault or a system acceptance result.

All eight observed states match the binary32 retained-base recurrence exactly:
advance STORE by `h*f`, then form STATE as `STORE + 0.5*h*f`, preserving the
previous STORE when h changes. Standard variable-step Adams--Bashforth gives
a 0.00878668 pu error at the step increase in this probe. Reducing the step
also causes a visible state change through the retained base. Therefore a
step change must not silently substitute a different recurrence. The production
`TwoStepHistory` binary32 path already has the observed behavior; the new
`TwoStepHistoryStepTransitionTest` locks all eight values and reset/replay
behavior without changing its implementation.

An isolated solver prototype allows one startup-to-runtime step change at time
zero. It keeps the existing arithmetic path when the step is unchanged. For
global and local ST1 histories, the experimental transition uses the retained-
base identity `x + 1.5*h_new*f - 0.5*h_old*f_previous`. The local ST1 path uses
its generic update for the transition and resumes the existing fast path on
later equal steps. Renewable binary32 history is unchanged. The scalar probe
directly verifies the converter lag; extending its identity to every controller
does not independently qualify all of those model transitions or lifecycle
events. The prototype remains outside production sources.

Twenty-two focused tests pass against both the retained production runtime and
the prototype with its transition option disabled. These include the new
scalar regression, existing solver guard tests, renewable history checks and
the existing small-system reference response. The fixed-quarter-cycle Case 5
control also reproduces both complete short recordings byte for byte with the
prototype. Five new Case 5 runs, including that control, retain all samples.
All four changed-startup arms have bit-identical cold physical values to the
prior control. Their main network step is 1/240 second, their voltage-correction
tolerance is 1e-5 pu, and each process has one logical CPU.

| Startup interval | Renewable local steps | Recovery selectors | Last time, s | Maximum voltage error, pu | Maximum rotor error, degrees |
|---|---:|---|---:|---:|---:|
| 1/240 s | 1 | Measured, diagnostic control | 0.1 | 0.0321156 | 0.816907 |
| 1 ms | 1 | Original | 0.0541667, failed | 0.897868 | 0.0378301 |
| 1 ms | 1 | Measured, diagnostic | 0.1 | 0.0101283 | 0.150658 |
| 1 ms | 4 | Original | 0.1 | 0.0323421 | 0.148341 |
| 1 ms | 4 | Measured, diagnostic | 0.1 | 0.00408214 | 0.0269595 |

Each row uses its full available nonnegative prefix; the failed row is not
comparable to a complete 0.1-second rotor maximum. Full ten-second reference
ranges remain the NRMSE denominators. With four local steps, the original-
selector arm's worst voltage NRMSE is 0.0314054; the measured-selector arm's is
0.00395686. Both fail the 0.001 prefault limit. The latter's worst rotor RMSE
over this prefix is 0.0184523 degrees, and its maximum exceeds the 0.002-degree
prefault limit. No timestamps, initial powers or physical parameters were
fitted to the reference.

The transition alone delays the original-selector failure but does not remove
it. Four local steps prevent that short failure, while the remaining large
original-selector error keeps independently derived converter initialization
and recovery selection as a priority. Measured selector signs remain diagnostic
inputs and are not a production initialization rule. No startup policy or local
step count is promoted, no full-system trajectory acceptance is claimed, and no
new warmed ten-second performance measurement was made. Existing full-reactor
compilation limitations remain; the tests used retained compatible dependencies.

The [reference/startup evidence index](checkpoints/reference-startup-transition-2026-10-22.json)
retains both independent cold probes, numerical alternatives, the regression
test, prototype sources, commands, runtime hashes, recordings and all-channel
comparisons. Exact reference-runtime provenance stays in the private archive.

### Conditional power-recovery arithmetic audit

The earlier five global-phase probes already locate the first small reactive
power signs in the converted-network solution. A further calculation holds
their observed pre/post conversion voltages fixed and compares ten arithmetic
variants: source-voltage difference, Norton-source difference and current
increment, with explicitly placed binary32 or double intermediates. This
separates candidate power-recovery arithmetic from another sparse-solver sweep;
it does not independently calculate the initial operating point.

No candidate reproduces all five reactive-power signs. The scalar binary32
Norton-source difference reproduces two exact P/Q pairs and four signs, but its
maximum Q error is 0.000190735 Mvar. That partial agreement is insufficient to
adopt its rounding policy or infer the remaining recovery selector. Source
construction, arithmetic order and the relationship between exported and
internal phasors remain unverified. Physical parameters and production
initialization are unchanged.

The calculation was replayed with byte-identical results. Its compact
[evidence index](checkpoints/conversion-power-arithmetic-2026-10-22.json)
retains the script, five input observations, provenance and all ten variants.
This adds no full-system simulation or timing result. Separately, inspection
of the retained warm Case 1 plan and harness confirms that initial Norton
compensation is removed before stepping; that configuration does not explain
the remaining performance gap.
