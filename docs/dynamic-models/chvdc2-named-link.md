# CHVDC2 named-link preparation and shared components

## Network-context rejection before transaction entry

The terminal owner validates both network-context timestamps before changing its
stage flags or opening a transaction. A nonfinite timestamp therefore cannot
leave a nested network trial behind or invalidate an otherwise valid pending
local predictor. This applies to caller-time and binary32 protection clocks;
selecting the latter does not make an invalid caller context acceptable.

Four negative controls fail on the prior implementation and pass with this
guard, including corrected-context retry after a ten-local-step predictor.
The runtime and terminal lifecycle checks pass. All six retained coarse fault
trajectories are byte-identical to the prior implementation, including both
integration methods and local modified Euler. Their six existing strict
reference failures remain unchanged: this validation fix does not establish
trajectory acceptance or alter the equations, clocks or substep policy.

## Default local integration for both methods

`Chvdc2TerminalPorts(binding)` and its clock-selecting overload now use ten local
steps per outer network interval. The explicit three-argument constructor accepts
counts from one through 128; one is retained for diagnostic controls. This is a
CHVDC2 policy, not a change to other devices or the network interval. Ten is an
operational default, not a claim that the strict reference gates now pass.

Both methods share the same prepared runtime, state arrays, limits and sampled
outer inputs. Staggered integration retains its existing local recurrence.
Modified Euler now predicts a complete local trajectory with the left boundary
inputs held, solves the outer predictor network, then replays the numerical
states from the left endpoint with linearly interpolated trial-endpoint inputs
and local predictor/corrector pairs. This interpolates integration inputs; it
never interpolates or realigns reference recordings. The outer protection preview
remains provisional, the protection clock advances once per network interval,
and the corrected network endpoint alone commits the model transaction.

Local steps do not change prepared coefficients, commutation-current samples or
network solution times. The corrector reuses two additional checkpoints and a
six-element input buffer allocated at construction. Warmed numerical/rollback
loops allocate zero bytes for both dynamic and algebraic lines. Terminal current
publication retains its existing immutable complex-object allocations.

The new runtime checks compare the corrected local trajectory with an explicit
manual local-step calculation using the same changing boundary inputs. Separate
checks cover cancellation, transaction rollback, stage diagnostics, one owner,
flat complete-network operation and outer clock ownership at two, four and ten
local steps. All 66 runtime/terminal checks pass.

Independent modified-Euler SMIB fault sweeps retain all samples and event-side
duplicates. All twelve runs complete, but all twelve strict reference gates fail.
Selected peak current errors at the `0.5 ms` outer interval are:

| Profile | Two local steps | Four local steps | Ten local steps |
| --- | ---: | ---: | ---: |
| Nominal dynamic line | 0.282348 A | 0.282347 A | 0.282347 A |
| Protected short, matched clock | 5.934159 A | 5.945106 A | 5.948029 A |
| Fast algebraic line | 0.966355 A | 0.895765 A | 0.889659 A |

The local-step limit does not eliminate the remaining model/network-boundary
mismatch. No gate is relaxed. The targeted CHVDC2/shared-solver regression run
contains 405 checks, 149 failures and two errors, with no skips. All 385 preceding
check identities retain their status; eight added lifecycle checks pass and the
twelve added reference gates remain failures. This is not a new full-repository
validation. Private sources, executable snapshots, reports and complete traces
are retained in `chvdc2-local-euler-20261104/validation.zip`.

### Coarse default-step and failed-corrector controls

Additional default-count controls run the complete fixed-angle fault at quarter-
and half-quarter-cycle network intervals with ten modified-Euler local steps.
Both trajectories complete, including event-side duplicate samples, but retain
peak current errors of `2.399257 A` and `1.202714 A`. These are essentially the
preceding one-local-step errors (`2.399192 A` and `1.202705 A`). Internal local
refinement therefore does not resolve this coarse outer-network/event discrepancy.
Keep the reference gates open; do not infer accuracy from successful completion.

For both dynamic and algebraic lines, an additional terminal regression executes
local prediction, the converged predictor network, local correction and a failed
corrector network. It requires exact restoration of states, protection histories,
clock and the previously published output object. Retrying the same outer interval
must advance the protection clock exactly once. These checks pass.

The isolated selection runs 70 checks: 68 pass and the two new independent
coarse reference gates fail; there are no errors or skips. Sources, executable
snapshots, complete trajectories and bounded logs are retained privately in
`chvdc2-default-local-20261104/validation.zip`. This expands default-step testing;
it neither changes equations nor qualifies the remaining coarse-step accuracy.

## Explicit terminal attachment in progress

`Chvdc2TerminalPorts` connects a prepared runtime to a pair of dynamic buses.
The rectifier port owns the shared numerical states; the inverter port publishes
only its terminal injection. The staggered solver recognizes this explicit
participant. The default solver uses its modified-Euler stages. Automatic
source import and complete reference-trajectory qualification remain unfinished.
Local integration is available for both methods as described below.

The pair calculates both currents once per network iteration and retains the
last converged currents for output access. Accepting a fresh electrical-angle
history does not recalculate the already published current. A converged
protection preview keeps that angle fixed while a changed protection regime
requests another numerical network pass. Predictor history stays provisional
through the corrector. RAML may change in its predictor preview; commutation
pickup/release waits until the corrected endpoint. A failed network solve restores this model's transaction;
it does not roll back other devices, bus voltages or the solver's global history.

The attached state owner exposes numerical coordinates, last-evaluated derivatives
and committed histories through `IntegrationStateAccess`; the companion exposes
empty diagnostics. Reads do not evaluate inputs or commit history. Static-link
identity, name, network membership and terminal ownership are guarded, and an
inactive link publishes zero at both terminals. A second runtime cannot attach to
the same owner. Changing prepared equipment requires a new preparation.

An ordinary `INITIAL` network solve revisits the previously accepted endpoint;
it refreshes voltage-dependent injections without accepting the electrical-angle
history again. An explicit external event still receives its own same-time
acceptance. Both methods retain one state owner, shared equations and reusable
numerical buffers. The terminal bridge publishes two immutable complex currents
per evaluation; it is not an allocation-free adapter.

Focused checks cover complete-network equilibrium under both methods, absence
of a double-counted static transfer, pure diagnostics, failed-network rollback,
accepted-output retention, endpoint idempotence and ownership guards. Independent
full-network fault runs now complete with every sample and both event sides. Each
clock is checked independently against its step recurrence: the reference uses
binary32 accumulation. Signals are paired by numerical step and event side;
there is no time shift, interpolation or removal of duplicate timestamps.

The initial terminal-attachment checkpoint produced:

| Network interval | Method | Maximum bus-voltage error (pu) | Maximum DC-current error (A) | Maximum terminal-power error (pu) |
| --- | --- | ---: | ---: | ---: |
| 0.5 ms | Modified Euler | 0.000002010 | 0.350302 | 0.00203230 |
| 0.5 ms | Staggered | 0.000001941 | 0.333831 | 0.00206151 |
| 0.25 ms | Modified Euler | 0.000002417 | 0.517190 | 0.00278729 |
| 0.25 ms | Staggered | 0.000002417 | 0.517187 | 0.00278728 |

Voltage passes the unchanged `1e-5 pu` gate. Current and power do not pass their
`0.01 A` and `1e-4 pu` gates; internal-state gates also remain open. Removing the
duplicate history acceptance has negligible effect on these errors. Earlier
reduced-network comparisons already show comparable current errors, and all of
their retained traces remain byte-identical after the runtime API refactor.
This attachment is therefore explicit experimental support, not complete model
qualification. Protected profiles, local integration, automatic import and the
remaining coupled trajectory errors still require work.

Regression validation: all focused runtime/lifecycle checks pass, and the
core-plugin module suite passes. The registered suite retains every previous
failure identity and adds only the four explicit terminal-reference failures
listed above. No failed reference gate is skipped or relaxed. Replacing the
test's global output-path setting with the repository build directory leaves
all four complete comparison CSVs byte-identical.

### Accepted left-endpoint rates at network events

The terminal owner now selects the staggered solver's accepted-endpoint rate
snapshot for a scheduled network discontinuity. The solver evaluates this RHS
after accepting the terminal's electrical and protection history. Its event
average therefore uses the converged left endpoint; the older integration-start
rate remains separate in the two-step formula. The companion owns no rates.
This reuses the existing cached derivative vector without an extra per-step RHS
evaluation. The additional event buffer is allocated once at initialization.

A coupled fault-clearing regression observes the left endpoint, right endpoint
and previous integration-start rates independently, then checks the resulting
voltage-filter coordinate against the staggered recurrence. Restoring the old
policy makes this test fail by `0.0001560502 pu`; the corrected policy passes.
The control also verifies that the two slopes differ, so an equilibrium test
cannot accidentally mask an ownership error.

The complete modified-Euler comparisons remain byte-identical. The coarse
staggered current maximum changes from `0.333831` to `0.334514 A`, and its maximum
power error changes from `0.00206151` to `0.00206500 pu`. The fine current maximum
remains approximately `0.517188 A`. This is a correction to integration-event
ownership, not a claim of improved reference agreement. The first post-event
measurement discrepancy and late-time reference plateaus remain separate open
issues; no precision, equation, time shift or acceptance limit was adjusted.

Validation of the event-rate change reruns the terminal/lifecycle and existing
coupled-reference classes, plus the core-plugin module suite. All lifecycle
checks pass; the earlier reduced-network traces remain byte-identical and their
failure identities are unchanged. The complete registered-suite baseline is
the preceding terminal-attachment checkpoint, not a fresh run for this change.

### Protected network endpoints and repeated convergence passes

Complete attached-network comparisons now include both retained RAML intervals
and the valid short-hold commutation profile under both integration methods.
They import the solved network afresh, use the entered controller settings and
fault impedance, and retain all states, terminal measurements and event sides.
Rejected reference profiles remain excluded from qualification.

These tests exposed a terminal-lifecycle defect: a preview could release the
inverter short, then another convergence pass or final publication retriggered
it at the same integration endpoint. The published flag consequently stayed
shorted throughout the disturbance, although the independent reference repeatedly
released it.

The terminal owner now latches each protection family's provisional transition
separately across the predictor/corrector transaction and its repeated network
passes. Final electrical publication refreshes the accepted angle and inputs
without making another protection decision. An independently accepted external
event can still transition at the same timestamp; a failed solve restores the
original states, flags, clocks and currents. The direct component acceptance API
retains its existing semantics. The new latch adds only scalar bookkeeping,
without another state array, expression evaluation or per-step allocation.

The coupled regression checks release retention through both methods, repeated
convergence passes, final publication, failed-event rollback and a distinct
same-time event. All complete protected simulations finish, but remain outside
the strict trajectory gates. In the commutation profile, maximum current error
changes from `328.794` to `107.075 A` for modified Euler and from `194.159` to
`126.640 A` for staggered integration. These improvements do not qualify the
remaining current, power or internal-state errors.

A separate protection replay isolates the remaining flag discrepancy. It uses
identical recorded physical inputs with two clocks: the captured accumulated
clock reproduces every source flag, while nominal double-precision accumulation
produces 121 differing samples. The latter flag sequence matches the complete
Java network run at every sample under both methods. The strict minimum-duration
comparison is sensitive to accumulated-clock rounding; equal nominal step sizes
do not guarantee equal transition samples. This diagnostic neither forces the
coupled run's states nor changes production precision or acceptance thresholds.
Reference clock policy and remaining continuous-state errors require separate
qualification.

Regression validation for this change includes 58 passing controller/runtime/
protection/lifecycle checks. The core plugin module completes with 486 tests,
no failures or errors and four skips. The complete registered suite runs 4,577
tests with 162 failures, one error and 13 skips. Its previous failure identities
remain unchanged; the six newly added protected-network reference cases fail
their retained strict gates. All 30 earlier reduced-network comparison traces
remain byte-identical. This is a lifecycle correction with explicit outstanding
accuracy failures, not full model acceptance.

### Explicit accepted protection clock

The terminal attachment now accepts an immutable `ProtectionClock` selection.
The existing constructor keeps `CALLER_TIME`. An explicitly selected `BINARY32`
clock starts from the prepared initial time, including negative startup time,
and adds the actual outer integration duration in single precision once per
accepted interval. Both integrators use the same clock owner. This follows the
independently observed timestamp recurrence; it does not round numerical states,
change integration weights, shift recorded samples or move scheduled events.

```java
var terminals = new Chvdc2TerminalPorts(binding,
        Chvdc2TerminalPorts.ProtectionClock.BINARY32);
terminals.attach();
```

The clock is provisional through predictor/corrector stages and repeated shared
state proposals. Failed convergence discards its tick; retrying starts from the
same accepted time. Same-time external events and repeated network passes do
not advance it. Reinitialization restores the prepared starting time. A clock
whose next increment is not representable fails explicitly with model rollback.
The option adds scalar storage and arithmetic without per-step allocation.
The solver's physical clock remains unchanged; the staggered solver's separate
participant-clock setting is not needed for this option.

Six new complete protected-network comparisons keep the original default-clock
controls and strict channel gates. In the valid short-hold commutation profile,
both methods now reproduce every reference short/release flag and transition
timestamp (clock error below `5.1e-13 s`). No recorded inputs or states drive the
coupled simulation. Maximum current error decreases from `107.075` to `61.093 A`
for modified Euler and from `126.640` to `69.809 A` for staggered integration.
Those continuous-state errors still fail the unchanged gates. The RAML physical
trajectories remain byte-identical between the two clock selections, separating
their unresolved measurement/integration discrepancy from clock precision.

This qualifies the explicit clock and commutation flag sequence for the retained
profile. It does not establish complete protected-current/power accuracy,
default-duration or refined commutation acceptance, or automatic source import.

All 18 terminal lifecycle checks pass, including negative startup time, once-only
clock accumulation, same-time events, reinitialization, failed-step retry and
unrepresentable-increment rollback under both methods. The core plugin module
passes 486 tests with four skips. Default-clock complete traces differ from the
retained preceding checkpoint only by floating-point roundoff, below `4e-12`
across all exported columns; the original trajectory thresholds remain unchanged.
The complete registered suite runs 4,589 tests with 168 failures, one error and
13 skips. All preceding failure identities remain; only the six new explicit-clock
protected trajectory cases add failures. Full comparisons, executable/source
snapshots and baseline-difference checks are retained in the private
`chvdc2-explicit-clock-20261103` validation archive.

### Commutation applies after the corrected line update

The valid short-hold capture first reports a short at `0.0505000129342 s`.
The line-current increment reaching that observation is only about `2.970 A`.
The former modified-Euler path applied commutation during its predictor preview,
so the corrector already used the newly shorted line circuit. Its current error
was `45.361 A` at the first shorted observation. Matching only the published
flag and timer did not expose this intra-interval ownership error.

The terminal owner now allows commutation pickup/release only in the corrected
endpoint preview. The preceding state update retains its original circuit; the
new regime requests a network resolve before the endpoint is published. RAML's
separate predictor preview and the direct component APIs are unchanged. Repeated
passes still latch each transition, and a failed endpoint still rolls back.
No new buffers, state equations, integration weights or clock policies are added.

With the independently qualified binary32 protection clock, the modified-Euler
first-short current error decreases from `45.361` to `2.654 A`. Its complete-run
maximum decreases from `61.093` to `19.294 A`; the maximum power error decreases
from `0.358529` to `0.161552 pu`. These remain above the unchanged strict gates.
Staggered trajectories are unchanged. With the unmatched caller-time clock,
the modified-Euler maximum instead changes from `107.075` to `110.158 A` and
the existing flag mismatch remains; that control is not trajectory-qualified.
Nominal and RAML physical trajectories are unchanged.
This evidence establishes the terminal owner's corrected-endpoint contract; it
does not identify the reference's internal substep/publication recurrence.

Focused tests derive the modified-Euler line-current update from its physical
voltage, resistance and inductance for both pickup and release. Restoring the
old predictor-commutation policy fails the release regression; the corrected
build passes all 65 controller/runtime/protection/terminal checks. The small
source-angle motion in this protected reference also invalidates a frozen-source
refinement assumption, so that attempted harness is retained as a rejected
diagnostic. Further numerical-refinement studies must retain the moving source
machines and distinguish outer history acceptance from local numerical steps.

The registered suite completes with 4,590 tests, 168 failures, one error and
13 skips. Its failing test identities match the preceding explicit-clock
baseline; the new pickup regression passes. The core plugin module completes
486 tests with no failures or errors and four skips. Fourteen of the sixteen
terminal comparison traces remain numerically unchanged; only the two
modified-Euler short-hold clock variants change. Full traces, baseline checks,
the failing negative control, and the rejected refinement harness are retained
in the private `chvdc2-corrected-commutation-20261103` validation archive.

### Protected full-network refinement with moving sources

A separate diagnostic retains the four dynamic source machines and refines the
entire numerical network/device grid while keeping coefficient preparation,
protection evaluation and electrical-history acceptance at the original
0.5 ms outer interval. It does not prescribe reference states or inputs.
The source angles move by approximately 0.00014--0.00048 degrees, so this test
does not use the rejected fixed-source circuit reduction.

| Numerical steps per outer interval | Modified-Euler maximum current error (A) | Staggered maximum current error (A) |
| --- | --- | --- |
| 1 | 19.294055 | 69.808659 |
| 2 | 19.117159 | 23.202012 |
| 4 | 19.073076 | 19.055435 |
| 10 | 19.060823 | 19.058004 |

All eight runs complete with matching protection flags and transition clocks.
Every reference observation and event-side duplicate is compared; separate
fine-grid traces retain every intermediate observation and source angle.
The one-step controls reproduce the maintained terminal path within
`3.2e-12` across the comparison columns. Repeating the matrix produces identical
comparison CSVs. The unchanged 0.01 A current gate still fails: ordinary
refinement alone does not close this independent-capture gap.

Two further diagnostic matrices distinguish numerical and input-boundary
questions. Suppressing only internal-transition derivative averaging changes
the ten-step staggered peak to 17.806227 A, but worsens the two- and four-step
controls. Holding only the DC-line driving voltages over each outer interval
instead gives ten-step errors of 6.287155 A and 9.169584 A for modified Euler
and staggered integration. Neither experiment passes the strict gates.
The improvement from holding voltage motivates an independent audit of the
line-input update boundary; it does not establish that sampled-voltage policy
as the reference contract. No production equation, averaging policy, default
step count or acceptance threshold is changed.

The test-only ports, solver registration copy and runtime overlay are removed
from both the source tree and compiled test output after the experiments.
Their source/executable snapshots, complete traces, failed first harness,
repeat checks and restoration checks are retained in the private
`chvdc2-outer-refinement-20261103` validation archive. This is full-network
refinement evidence, not qualification of a production local-substep API.

### Converter-current sampling for dynamic-line integration

The fixed-angle coarse controls also distinguish the current used in the
converter's commutation-voltage drop. A replay through `LccConverterBoundary`
and the prepared `Chvdc2DcLine` reconstructs converter voltage from recorded
AC voltage, constant firing angle, equipment and the replay's own current.
It never overwrites the current from later reference samples. Sampling the
commutation current once per outer interval gives these peak errors:

| Common-RHS replay, ten local steps | `1/240 s` | `1/480 s` |
| --- | ---: | ---: |
| Staggered, live commutation current | 0.773941 A | 0.384082 A |
| Staggered, sampled commutation current | 0.001240 A | 0.001152 A |
| Modified Euler, live commutation current | 0.874261 A | 0.434012 A |
| Modified Euler, sampled commutation current | 0.237713 A | 0.122319 A |

The staggered component gates pass; the modified-Euler gates remain failures.
The independent captures, all event duplicates and both cold repeats are the
same immutable coarse controls used by the preceding event-scope change.

Inside an explicitly bracketed outer interval, the coupled runtime now samples
only the commutation-current term supplied to dynamic-line integration.
Rectifier and inverter samples remain separate, including capacitive lines.
The line resistance continues to use the evolving line state. Firing angles,
AC voltages, protection shorting, published converter DC voltages, physical
currents and AC injections remain live. The algebraic-line mode is unchanged.
Without an outer bracket, the component retains its live-input equation path.

The implementation reuses the current samples and rollback buffers already
owned by the outer interval. It adds a scalar commutation-drop correction to
the live voltage calculation, avoiding duplicate trigonometric evaluations
and per-step allocation. Modified Euler uses this same input boundary in its
predictor and corrector. An inverter short still gives zero inverter line
voltage, and protection transitions retain their accepted-endpoint lifecycle.
Completion restores live line inputs; cancellation/rollback restores the samples.

Matched complete-network runs use the previous committed runtime with the new
test matrix as a control, then rebuild the working runtime. Coefficients,
network intervals, source machines, clocks and channel gates are unchanged:

| Profile | Method / local steps | Previous peak current error | Sampled peak current error |
| --- | --- | ---: | ---: |
| Fixed angle, quarter cycle | Staggered / 10 | 0.784727 A | 0.016931 A |
| Fixed angle, half-quarter cycle | Staggered / 10 | 0.389821 A | 0.009242 A |
| Fixed angle, 0.5 ms | Staggered / 10 | 0.094272 A | 0.020073 A |
| Fixed angle, 0.25 ms | Staggered / 10 | 0.051169 A | 0.051317 A |
| Fixed angle, quarter cycle | Modified Euler / 1 | 2.265314 A | 2.399192 A |
| Fixed angle, half-quarter cycle | Modified Euler / 1 | 1.163485 A | 1.202705 A |
| Protected short, matched clock | Staggered / 10 | 11.016815 A | 9.472245 A |
| Protected short, matched clock | Modified Euler / 1 | 6.999057 A | 5.919170 A |
| Nominal dynamic line, 0.5 ms | Staggered / 10 | 0.129918 A | 0.129506 A |

Improvement is not uniform. All complete-model reference gates remain open;
the half-quarter-cycle current channel passing alone does not qualify its
other states/power channels. The expanded matrix also retains the earlier
single-step fast algebraic-line nonconvergence as an explicit error. No
precision reduction, trace alignment change, tolerance relaxation or default
local-count change is included.

Both-method unit checks cover distinct capacitor-terminal samples, zero/nonzero
measurement lag, live voltage/angle changes and injections, interval completion,
predictor cancellation, whole-transaction rollback and protected corrector
ownership. All 58 runtime/terminal lifecycle checks pass. The private
`chvdc2-line-current-sampling-20261104` evidence directory retains the matched
previous-runtime executable, complete baseline traces and current validation.

Registered regression validation covers 5,846 checks: 201 failures, two errors
and 63 skips. All 5,834 preceding test identities retain their status. The
12 added checks comprise four passing checks and eight failing reference
trajectory gates; these failures remain visible rather than being skipped or
relaxed. The explicit completion selection runs 71 checks with no failures and five existing
skips, including the strengthened separate-terminal sampling regression.

The matched isolated matrix compares 38 unique profiles. All 16 profiles
required to remain unchanged have zero numerical trace difference. The broad
suite's fast algebraic control differs from the isolated control even though
this correction does not execute in algebraic mode. Its original report is
retained separately; isolated runs provide the matched numerical comparison.
Broad and isolated test statuses agree. Follow-up numerical sensitivity controls
are described below; neither execution scope establishes full-model qualification.

### Fast algebraic-line numerical sensitivity

The archived broad and isolated executions use the same model source and test
matrix. Of 38 unique profile comparisons, 37 have a maximum current
variation below `1e-8 A`. The exception is the fast algebraic-line profile with
one staggered local step at a `0.5 ms` network interval. The first recorded
boundary difference is about `2.22e-15 pu` in reactive power. Current differences
exceed `1e-8 A` at `0.019 s`, reach `0.011319 A` before the fault, and reach
`1.660961 A` over the complete run. This growth begins before any fault event.

An isolated repeat of the existing 12-profile local-step matrix with
`-XX:TieredStopAtLevel=1` reproduces one of the archived trajectories exactly for
each profile, including the sensitive one-step trajectory. Other test classes
are therefore not required to produce the observed variation. This is evidence
of numerical sensitivity to execution conditions, rather than proof of leaked
controller history or a specific faulty JVM intrinsic.

For the same fast algebraic profile, broad/isolated current variation falls to
`8.30e-12 A`, `7.28e-12 A`, and `8.30e-12 A` with two, four, and ten local steps,
respectively. All samples and event duplicates are compared without shifting,
resampling, or tolerance relaxation. The local-step controls support investigating
the one-step recurrence's stability before treating its exact trajectory as a
regression fingerprint. They do not qualify the reference trajectory: the run reports eleven reference failures and one
existing nonconvergence error. The latter retains its incomplete trajectory. Modified Euler was not rerun in this
compilation-tier diagnostic and its preceding failures remain open.

Private evidence is retained in `chvdc2-jit-stability-20261104/validation.zip`,
including the comparison script, all twelve recorded traces (including the explicitly incomplete run), test report and bounded
execution log. Production equations, arithmetic functions, JVM defaults and
local-step defaults are unchanged. A stable admissibility rule for the algebraic
controller/line recurrence and full reference accuracy remain required.

### Scheduled network events across local steps

Cold-repeated fixed-angle controls at network intervals of `1/240 s` and
`1/480 s` distinguish event derivative handling from an input delay. Converter
angles, prepared coefficients and taps remain fixed. All samples, including
the application and clearing duplicates, are retained. Two independent
component replays use production equations: the dynamic DC line driven by
recorded converter voltages, and the AC-voltage measurement driven by recorded
terminal voltage.

For the first outer interval after a scheduled network event, the staggered
reference is consistent with averaging each local right-side derivative with
the **same accepted left-side derivative**. The local right derivative changes
with the local state. Averaging the physical voltage input instead gives a
different damping coefficient and is rejected by the coarse controls.
The older two-step integration history remains separate from the event slope.

`LocalStaggeredEulerParticipant.averageNetworkEventRatesThroughoutLocalInterval()`
is an explicit opt-in. CHVDC2 enables it; other participants retain their
existing first-local-slope policy. The shared solver stores the fixed event
rate in a reusable buffer, applies the participant's per-coordinate selection,
and consumes this policy for one outer interval only. One local step is
unchanged. Production precision, network step, coefficients, recording and
network-solution count are unchanged.

| Isolated replay, ten local steps | `1/240 s` peak error | `1/480 s` peak error |
| --- | ---: | ---: |
| Staggered AC measurement | 0.000001423 pu | 0.000002855 pu |
| Modified-Euler AC measurement | 0.000083183 pu | 0.000055748 pu |
| Staggered DC current | 0.001906 A | 0.004725 A |
| Modified-Euler DC current | 0.242868 A | 0.125428 A |

The four staggered component gates pass. The four modified-Euler gates remain
explicit failures; this isolated common-RHS replay does not implement or qualify
multirate modified-Euler network coupling. No state is reset after initialization.

Internal protection transitions retain their separate first-local-step policy.
An attempted extension of whole-interval averaging to those transitions raised
the protected ten-local-step current error to 97.40 A and was rejected. With
the scheduled-network-only correction, nominal full-network peak current error
falls from 0.297274 A to 0.129918 A. Protected peak current error changes from
10.721076 A to 11.016815 A; protection flags still match. The fast algebraic-line
control changes from 0.555660 A to 0.557460 A. These complete trajectories still
fail their unchanged current/power/state gates and do not qualify the model.

Network regressions cover application, non-equilibrium clearing, subsequent
ordinary intervals and separate two-step history at two, four and ten local
steps. All 29 terminal lifecycle and 17 shared-solver checks pass. Default
one-step trajectories in the isolated comparison remain byte-identical to the frozen local-participant
checkpoint, including the modified-Euler controls. The private
`chvdc2-coarse-line-event-20261104` archive retains the raw cold captures,
probe scripts, rejected protection policy and validation results.

The broader Maven selection plus explicit completion of registered classes
executes 5,834 checks: 193 failures, two errors and 63 skips. All 4,622 checks
from the retained registered baseline keep exactly the same outcome. The extra
coverage includes the four new modified-Euler component failures above, the
previously added modified-Euler fast-line gate, three static RAWX failures and
an additional dynamic-load failure. The last four were outside that retained
baseline and are not classified as regressions or as passing checks. The
core-plugin module contributes 486 checks with no failures/errors and four
skips. Full model and full-suite acceptance remain open.

### Explicit local staggered integration

`LocalStaggeredEulerParticipant` lets an already supported participant request
local numerical updates inside one network interval. The shared solver owns
the reusable state, trial, rate and derivative-history arrays; the model does
not implement a second copy of the staggered equations. Its first local slope
retains the accepted-event policy. Later local slopes use the shared RHS and
the explicit scheduled-event scope described above; limiter projections clear
only the affected history coordinate.
The local count is captured during initialization and checked before advancing
any model. There is no automatic step-count selection or coefficient retuning.

CHVDC2 opts into this contract through an explicit constructor argument:

```java
var ports = new Chvdc2TerminalPorts(binding,
        Chvdc2TerminalPorts.ProtectionClock.CALLER_TIME, 4);
ports.attach();
algorithm.setSolver(new StaggeredEulerSolver(algorithm));
```

The count is bounded to `[1,128]`; existing constructors retain one. The prepared
network interval stays fixed. Current transducers and algebraic-line inputs
sample once per outer interval, physical dynamic-line states advance locally,
and protection/accepted-network clocks advance only at the outer boundary.
No extra network solves or measurement records are inserted. Modified Euler
retains its existing one-step terminal path; requesting this staggered-only
setting through its numerical callback is rejected rather than silently ignored.
Multirate predictor/corrector network coupling remains separate work.

The reverse-current failure in the older reduced-circuit diagnostic is an
unstable numerical trajectory, not evidence for negative conducting current.
Its single-step staggered states depart from equilibrium before the fault,
while the independent current remains flat. A fresh full-network discriminator
uses independently solved equipment and all moving source machines. Its
one-step fast algebraic-line run instead fails after the fault, at 58 ms.
Local refinement completes the same outer sample grid:

| Local steps | Fast algebraic-line result | Peak current error (A) | Protected dynamic-line peak current error (A) |
| --- | --- | --- | --- |
| 1 | Stops at 58 ms | Incomplete trajectory | 69.808659 |
| 2 | Complete | 11.789979 | 25.693890 |
| 4 | Complete | 2.575491 | 16.403109 |
| 10 | Complete | 0.555660 | 10.721076 |

These are 0.5 ms network intervals and a half-second fault comparison. The
completed runs retain all 1,005 samples and both event-side duplicates. The
refined fast-line runs have at most 0.002503 A prefault current error; protected
runs retain zero flag mismatches with the explicitly matched protection clock.
The current, power and controller-state accuracy gates remain unchanged and
fail. This establishes numerical stability improvement, not complete-model
acceptance or a qualified default count. In particular, production-step and
longer-duration qualification remain open.

The modified-Euler control for the same fast algebraic-line profile completes
all samples with 8.715657 A peak current error and retains its accuracy failure.
Each completed locally refined staggered run makes 2,005 network-solution API
calls, independent of whether it uses two, four or ten local steps. This count
does not equate network iterations or sparse solves across the trajectories.

Local lifecycle tests check a single outer clock, flat operating points,
rejected partial intervals and rollback of numerical states, samples and model
histories after a failed final network solve. Model rollback does not restore
the enclosing solver's other models or multistep buffers: a failed whole-network
step still requires coherent solver reinitialization. Automatic import remains
disabled pending full trajectory qualification.

The focused lifecycle/shared-solver checks pass, including the warmed local
numerical and rollback callbacks allocating zero objects at four and ten steps.
The full registered run executes 4,622 checks: 184 fail, two error and 13 skip.
Every one of the 4,590 checks shared with the retained prior registered run has
the same pass/fail/error/skip status. New comparisons preserve their failed
trajectory gates, including the one-step fast-line failure. The registered
evidence archive retains the executed individual reports and tested binaries;
the empty suite-wrapper XML is not used to infer success. Private archive
`chvdc2-local-participant-20261103/registered-suite.zip` has SHA-256
`201f3e54da2211e5a21bb97f82b14b6c349ee913aa30f2ef50c6dba272a1e691`.
The final duration/zero-duration guards and additional modified-Euler fast-line
control are checked in the subsequent focused run, separately from that full
registered run.

Final focused validation executes 142 checks: all 108 lifecycle/shared-solver
checks pass; the independent trajectory class has one passing discriminator,
32 failed accuracy checks and the expected one-step fast-line error. The
plugin-core module run executes 486 checks with no failures or errors and four
existing skips. All twenty default network CSVs reproduce the committed
sampling baseline byte-for-byte in the isolated final run; the full-suite
context differs by at most `3.2e-12` across those comparison columns. Local
controls repeat with the complete outer grid, including the retained incomplete
one-step negative control. Source snapshots, repeated complete traces, failure
reports and file hashes are retained in the private
`chvdc2-local-participant-20261103/validation.zip` archive (SHA-256
`5f2e65e44cb86e4506d6cedfd0f8e0fe3ee6e420cfb71f7c442eace08e70901e`).

### Current-transducer sampling at the outer interval

The coupled runtime now samples each lagged current transducer's input when
`beginIntegrationInterval(H)` opens. Local controller/line steps use these two
samples until the interval completes. Physical line currents, converter
voltages, AC injections and zero-lag current observations remain live. The
samples are included in reusable transaction and predictor checkpoints;
rollback restores the interval and its progress. Both terminal integration
paths bracket their numerical update with this interval contract. Sampling is
independent of local-step count and does not change prepared coefficients; the
explicit staggered scheduler above uses the same contract.

Independent recorded-current replay through the production transducer equations
supports this sampling boundary. It initializes once, retains every sample and
both event-side duplicates, and keeps double-precision state storage. The
ten-local-step peak measurement errors are:

| Independent profile | Modified Euler (pu) | Staggered (pu) |
| --- | --- | --- |
| Nominal, 0.5 ms outer interval | 0.000005007 | 0.000001202 |
| Nominal, 0.25 ms outer interval | 0.000005945 | 0.000005945 |
| Protected, 0.5 ms outer interval | 0.000739292 | 0.000008063 |

The protected modified-Euler replay still fails the unchanged `1e-5` measurement
gate. Its difference persists with local refinement; a different numerical
method must not be disguised by a fitted delay or reference-state resets.
`Chvdc2CurrentSamplingReferenceTest` records one-step negative controls and the
ten-step comparisons, and is registered in the main test suite. Five of its six
reference checks pass. This is a transducer discriminator, not complete-model
qualification.

The complete moving-source network comparison gives:

| Profile and method | Previous peak current error (A) | Sampled-current error (A) |
| --- | --- | --- |
| Protected, matched clock, modified Euler | 19.294055 | 6.999057 |
| Protected, matched clock, staggered | 69.808659 | 69.808659 |
| Nominal, 0.5 ms, modified Euler | 0.350302 | 0.291692 |
| Nominal, 0.25 ms, modified Euler | 0.517190 | 0.517235 |
| Rectifier protection, 0.5 ms, modified Euler | 6.230865 | 6.242078 |
| Rectifier protection, 0.25 ms, modified Euler | 1.841856 | 1.829883 |

Staggered one-step trajectories stay unchanged because their derivative
evaluation already uses the left endpoint. All twenty complete network
comparisons repeat byte-for-byte; their trajectory gates remain open. The
matched-clock protected runs retain zero flag mismatches. Default caller-time
and independent binary32 clock policies remain distinct.

The held-AC-boundary diagnostic with ten joint local steps reduces protected
current error from 18.571 to 2.942 A for modified Euler and from 17.259 to
6.907 A for staggered integration. That diagnostic supplies recorded AC inputs;
it must not be reported as a full-network result. It also remains outside the
current gate. Fixed-angle controls do not improve, so the remaining line event
and publication ordering must still be resolved.

All 67 focused lifecycle checks pass, including distinct capacitor-terminal
samples, current resampling, zero-lag bypass, rollback, live injections and
zero-allocation warmed integration in both methods. Complete traces, diagnostic
source, rejected precision/input controls and test reports are retained in the
private `chvdc2-current-sampling-20261103/validation.zip` archive (SHA-256
`47d88a2f4841ac679fea9a7c896d1be920ef8ef2de8109387a1c85a09c06d17e`).
The full maintained model run has 342 checks, 113 failures and one error, with
no skips. The prior committed runtime was rerun against the 309 unchanged
checks: both versions have the same 112 failures and one error, with no
pass/fail/error status changes. The additional failure is the newly exposed
protected modified-Euler transducer gate described above. The existing error
is the rejected reverse-current algebraic boundary. After restoring and
rebuilding the working runtime, all twenty network CSVs reproduce the saved
current-version traces exactly. This audit uses only classes actually executed
by the recorded commands, excluding stale report files. Its private
`regression-validation.zip` SHA-256 is
`de5eab02162cae0ebbbd42ced1f65c3aefe3d273d66bcd8924051b076c2c0aaf`.
Automatic attachment and full trajectory acceptance remain unqualified.

### Fixed-angle line controls and preparation checks

Independent controls now distinguish line integration from angle-controller
motion. Zero angle-rate limits alone do not freeze the inverter angle: its
moving upper bound still projects the state. A separate fixed-angle profile
also disables the two gains driving that bound and disables commutation
protection. The line resistance, inductances, capacitance and moving classical
source models remain unchanged. Both cold repeats have identical complete CSVs
at 0.5 ms and 0.25 ms, including both event-side duplicates.

The first control exposed a separate static preparation difference. With DC
tap adjustment enabled and a 30-degree static extinction target, the reference
selects inverter tap 0.9625; the local load-flow path selects 0.95625. The
resulting initial current and firing angles differ before integration starts.
This profile cannot qualify the line dynamics. It is retained as a rejected
preparation control rather than corrected by importing recorded dynamic states.

The probe now records an explicit DC tap-adjustment option. A second independent
control disables this adjustment in both solvers. The comparison harness
matches that option, solves the network afresh, and checks solved tap, DC voltage
and current against the captured equipment before starting dynamics. Existing
captures retain their enabled policy. All previous terminal trajectories remain
within `3.2e-12` of the retained baseline comparison columns.

| Fixed-tap network interval | Modified-Euler peak current error (A) | Staggered peak current error (A) |
| --- | --- | --- |
| 0.5 ms | 0.285612 | 0.271096 |
| 0.25 ms | 0.145520 | 0.138333 |

These complete coupled runs preserve every sample and use the production
shared equations. Their voltage gates pass; the unchanged 0.01 A current gate
and other continuous-state gates still fail. The current peaks occur immediately
after application or clearing, and approximately halve with the interval.
They must not be confused with the earlier preparation offset.

A recorded-voltage scalar replay further separates event inputs and storage.
Ten local two-step updates with the event-side driving voltage averaged over
the first outer interval reproduce this fixed-angle control to 0.001221 A and
0.001832 A when each local current store emulates binary32. Keeping double
precision instead leaves 0.036045 A and 0.108808 A, respectively; rounding only
the outer store does not remove that drift. These are diagnostic storage
controls, not a recommendation to reduce production precision.

The same policy does **not** qualify the general model: independent nominal
moving-angle replays retain 0.156494 A and 0.076843 A, and the protected control
retains 30.893799 A. Replacing fresh firing angles with their previous accepted
values worsens those controls. Thus neither a generic input hold nor an added
one-step angle delay is established. Continue with joint controller/line stage
ownership and distinguish published terminal voltages from integration inputs.
No production equations, precision, local-step default or tolerance are changed.

The private `chvdc2-line-input-20261103` archives retain both repeatable fixed-angle
families, the unsuccessful zero-rate isolation, rejected fine protected captures,
complete coupled comparisons, scalar controls and executable source snapshots.
The focused lifecycle checks pass; full terminal trajectory acceptance remains
open. The finer protected reference itself reports network nonconvergence and
is retained as rejected evidence, not used as an accuracy oracle.

## Preparation from the solved network

`Chvdc2NetworkPreparation.prepare(network, parameters, H, initialTime)` resolves
the exact named static link and prepares an independently owned, initialized
`Chvdc2CoupledRuntime`. Both integration methods use this same runtime. The API
does not attach terminal devices, disable the static link or mutate the solved
network. It is the preparation boundary for the forthcoming network attachment.

Preparation validates the solved-network flag, unique active link ownership,
converter orientation, terminal ownership, physical units, equal bridge counts,
positive consistent DC currents and the ohmic voltage drop. Unsupported
capacitor-commutated equipment and converter resistance are rejected explicitly.
It uses the network MVA base, converter primary voltage, transformer ratios,
fixed taps, commutation reactances and network frequency. The entered outer
interval `H` determines capacitor suppression once; local integration does not
reprepare coefficients. Later edits to static equipment do not alter this
runtime: prepare a new binding after resolving the changed network.

The initial current reference is the solved line current; it is not recomputed
from a live static power schedule during dynamics. Initial auxiliary input is
zero. The binding preserves the solved operating point, outer interval and
prepared physical coefficients. Source auxiliary routing and subsequent command
ownership remain explicit attachment work.

The initialization comparison solves the captured synthetic RAW afresh and
uses entered controller constants, without importing recorded dynamic states
or terminal voltages. Both retained source time grids pass the existing initial
state and electrical boundary gates: maximum terminal-voltage error is
`3.26266e-8 pu`, DC-current error `0.00250244 A`, power error `1.25123e-5 pu`,
and firing-angle state error `1.35125e-5 degrees`. These results qualify initial
preparation only. Fixed-terminal equilibrium checks exercise both integration
methods and both algebraic and inductive line modes. Full coupled fault
acceptance, production local integration and automatic attachment remain open.

Validation for this preparation slice: the focused tests pass, including the
independent initialization controls; the core-plugin module suite has no
failures. The registered suite retains exactly the previous failure identities.
All retained coupled CHVDC2 comparison traces are byte-identical, including the
explicitly incomplete coarse algebraic run. This slice changes preparation,
not controller equations, integration precision or trajectory acceptance limits.

The named-link reader now recognizes standard `CHVDC2` and legacy
`USRDCL` / `CHVDC2U1` records. It preserves their distinct identities, quoted
link names, source locations and original parameters. The legacy allocation
header is checked before reading the flags and constants. Invalid records are
rejected with source locations; correctly decoded records remain explicitly
unsupported for automatic dynamic attachment.

`Chvdc2Parameters` owns immutable source arrays and named constant slots.
Validation checks finite values, documented positive quantities, nonnegative
times/physical line coefficients, ordered limits and voltage breakpoints.
Zero-valued optional coefficients and the nonpositive commutation-failure
disable setting are preserved. No timestep correction or dialect alias is
silently applied.

## Shared electrical component

`Chvdc2DcLine` implements the physical DC T-circuit using SI units. With line
capacitance present, its coordinates are rectifier current, capacitor voltage
and inverter current. With zero capacitance it stores one series current.
With line dynamics disabled, current is evaluated algebraically from the latest
terminal voltages.

For positive capacitance the equations are:

```text
dIr/dt = (Vr - Vc - R*Ir/2) / (L/2)
dVc/dt = (Ir - Ii) / C
dIi/dt = (Vc - Vi - R*Ii/2) / (L/2)
```

For zero capacitance, `dI/dt = (Vr - Vi - R*I)/L`. These equations satisfy
terminal input power minus resistive loss equals the rate of stored magnetic
and electric energy. The source diagram's printed first-order gain requires
care: a resistance-valued current/voltage gain is dimensionally inconsistent.
The implementation uses the physical circuit equations above; exact native
line-state equivalence remains a separate qualification requirement.

Effective inductance includes entered line/smoothing inductances and the
documented commutation contribution. Millihenries, microfarads and commutation
reactances are converted once during preparation. Coefficients remain fixed
across local integration stages. The network-interval overload described below
implements source capacitance suppression. Additional fast-time preparation and
local-step selection are not inferred from that suppression decision.

Modified Euler and shared staggered integration use the same state storage and
RHS. Numerical arrays are reused. Explicit prediction cancellation and ownership
guards prevent mixing a pending local predictor with shared integration.
Measurements refresh at network trials without advancing states. Named-state
maps and derivative snapshots are optional observations outside the hot path.

## Evidence and remaining work

The synthetic reference probe loads both record forms with the expected
allocation. The original coupled profiles fail native initialization and are
retained as rejected diagnostics, not trajectory acceptance. Fixed inverter
gamma and current-control discriminators did not resolve that failure. A
captured pre-start converted boundary retains its solved DC operating point;
the large deviation appears during dynamic startup.

A separate stiff-source network, using unchanged controller constants, passes
native initialization. Both record forms have repeatable flat controls; the
legacy form also has repeatable rectifier-fault and inverter-fault captures.
The earlier direct-VAR modulation command was overwritten by the reference
model: its auxiliary input remained zero. That capture is a negative control,
not evidence of auxiliary-path excitation. The probe now rejects unexcited
modulation attempts explicitly. All channels and event-side duplicates are preserved.
This provides usable independent evidence without claiming that the original
coupled startup failure is fixed. The probe selects this control explicitly
with `--network stiff`. Full controller/network equivalence is not established
by reference generation alone.

Component tests cover exact record fields and malformed input, source-array
ownership, integration lifecycle, analytic second-order convergence of RL and
RLC responses under both methods, instantaneous energy balance, algebraic
voltage refresh and coefficient units. These do not establish complete SMIB
qualification. The public [HVDC benchmark report](https://restservice.epri.com/publicdownload/000000003002010755/0/Product)
provides an additional independent test-system specification for that next step.

Still required: exact legacy auxiliary routing; complete current-controller
trajectory qualification, VDCOL, calculated inverter angle bounds and RAML;
commutation-failure histories; line-state/source preparation equivalence;
network ownership and attachment; complete both-method SMIB states, boundary
trajectories, events and longer-duration gates. Neither form is classified as
loadable or verified by this change.

Exact source provenance, rejected captures, valid stiff-source captures and
validation evidence are retained in the private `chvdc2-foundation-20261102`
evidence directory. The rejected and valid reference archives are separate.

## Shared current-controller component

`Chvdc2CurrentControls` supplies four primitive numerical coordinates: rectifier
PI integral, rectifier firing angle, inverter PI integral and inverter firing
angle. These map to source states 4, 6, 5 and 7 respectively. It shares one RHS
and reusable state/rate buffers between modified Euler and the staggered
participant contract. Parameter reciprocals are prepared once; warmed component
tests observe no per-step allocation. It is not yet attached to the network.

Measured currents are on rated-current base. Current orders and auxiliary
inputs enter in amperes and are converted once per input refresh. The inverter
error includes the entered current margin. For either terminal the PI output
is bounded before calculating the firing-angle lag error; that error is rate
limited before division by the angle time constant. Both integrals enforce
their position limits with outward derivative blocking. Tests distinguish
this sum clamp from clamping only the PI state or final firing angle.

Input and calculated-bound refresh do not commit states. The caller owns
measurement filters, current orders, auxiliary routing and calculated angle
bounds. With RAML disabled, the rectifier uses its entered minimum; the RAML
diagnostic VAR is not a replacement for that minimum. Integration ownership
guards reject shared stepping during a pending local predictor. Initialization
uses solved angles without cancelling a nonzero measured current error.

### Independent replay and precision discriminator

Twelve stiff-source fault profiles cover both terminals, nominal parameters,
narrow rectifier angle limits and active rate limits at 0.5 and 0.25 ms. Each
was cold-generated twice with identical complete CSV output. All source states,
boundary channels, startup rows and both event-side duplicates remain in the
immutable private `chvdc2-controller-20261102/captures.zip` archive. The archive
hash is enforced by the registered replay test. Entered and initialized
constants are checked for equality.

Each profile is replayed through both methods using the recorded measured
currents, current orders and inverter angle bound. This is a prescribed-input
component experiment, not a coupled simulation. The provisional 0.002-degree
per-coordinate gate remains unchanged: all 24 full replays fail on rectifier
coordinates. Inverter coordinates remain below that gate. The worst rectifier
PI error is 0.014922 degrees; halving the step increases several discrepancies.

The reference rectifier PI state has long stationary intervals despite a
nonzero input error. Most increments occur in multiples of ten binary32 ULPs.
A separate diagnostic applies the published PI equation with ten binary32
sub-updates and linearly interpolated recorded measurements. Across all twelve
profiles it reproduces the reference PI within eight ULPs (the largest observed
error is 0.000026703 degrees); a single binary32 update remains a negative
control. This strongly supports an internal stepping/roundoff explanation,
but does not establish the full reference execution algorithm or validate
unobserved internal inputs.

Production precision, entered gains and integration equations are unchanged by
this diagnostic. Neither the precision discriminator nor passing component
tests replace the unresolved full-replay gates. Complete attachment, all
controller/protection states, coupled SMIB trajectories and long-duration
qualification remain required.

## Shared measurement and current-order component

`Chvdc2OrderControls` implements the measured DC currents and AC voltages,
asymmetric DC-voltage filters, piecewise DC VDCOL curve, optional AC-voltage
current-limit integrators, inverter order/error lags and calculated inverter
maximum firing angle. Its ten coordinates correspond to source states
0, 1, 2, 3, 8, 9, 10, 11, 12 and 13. Both integration methods use the same
primitive state buffers and RHS; reciprocals, unit conversions and the VDCOL
segment slope are prepared once. Warmed state and output kernels allocate no
per-step objects in the component test.

Terminal currents and orders enter in amperes, DC voltages in kV, and AC
voltages on bus base. The entered ratings establish DC current/voltage pu.
The inverter angle calculation scales its measured AC voltage by converter
transformer ratio divided by tap, then applies the documented denominator
floor and cosine limits. The error lag excludes the inverter current margin;
that margin belongs to the downstream current PI.

The AC current-limit states initialize at their upper limits. The prepared AC
voltage reference is the minimum of the entered reference and the two initial
terminal voltages, with entered data unchanged. The low selector combines the
DC curve, current reference and enabled AC limit separately for each terminal.
Upward and downward DC-voltage filtering use their respective entered times.

Fully zero-time measurement/order filters expose algebraic observations without
committing numerical storage. A zero down-time does not remove the asymmetric
DC filter's dynamic coordinate: its positive up-time still owns state. Its
published state therefore remains stored until an explicit integration/apply
operation accepts the downward projection. Predictor cancellation restores
numerical storage; input refresh and getters cannot perform that commit.

### Independent order-control checks and remaining timing work

Separate cold-repeated captures activate DC VDCOL and both AC-voltage limiters.
Zero-time filter captures preserve their entered zeros through native
initialization. These extend the earlier nominal and PI-limit profiles to
twenty-two complete recorded-boundary controls. Every sample/channel and both
event-side duplicates remain in the private archives; reference hashes and cold
equality are enforced by the registered test.

The analytic unit checks pass for both methods, including exponential
convergence, asymmetric voltage filtering, piecewise current orders, active
AC limits, algebraic observations, invalid-input atomicity, predictor ownership
and warmed allocation. Thirteen of the forty-four full prescribed-input
replays pass all unchanged channel gates. This includes both methods for the
rectifier zero-filter fault and staggered integration for the inverter
zero-filter fault. The latter modified-Euler run retains a maximum DC-filter
voltage discrepancy of 0.00013113 pu against the 0.0001 pu gate.

Finite-lag profiles still expose reference timing and precision differences.
In the active inverter DC-limit control, applying the documented static curve
to the reference's same-row voltage-filter state differs from its recorded
current-order VAR by up to 50.33 A at 0.5 ms, and 25.85 A at 0.25 ms. Thus those
two channels cannot be assumed to represent the same internal evaluation
stage. A separate source-only diagnostic retains this distinction and checks
the initial static bases. Observation-stage ownership still requires
qualification; no production time shift is applied to improve the comparison.
Full replay errors remain recorded without relaxing gates or changing precision.

This component does not supply RAML, commutation-failure histories, converter
network injection or complete model attachment. Coupled SMIB, local substep
and observation-stage qualification remain necessary. The private
`chvdc2-orders-20261102` archives retain the active-limit and zero-filter
captures separately.

## Signed conducting-converter boundary

`LccConverterBoundary` prepares bridge/transformer geometry once and exposes
the firing-angle electrical boundary needed by the shared CHVDC2 runtime.
For positive line current, its terminal voltages are

```
Vr = E0r * cos(alphaR) - Rcr * Ir
Vi = -E0i * cos(alphaI) + Rci * Ii
```

Here `E0` includes the live AC voltage, bridge count, transformer ratio and tap;
`Rc = 3 * bridges * Xcomm / pi`. AC active power into each terminal is `Vr*Ir`
and `-Vi*Ii`. Signed DC voltage allows temporary rectifier inversion. The
overlap equation supplies reactive power and inverter extinction angle. An
infeasible overlap is rejected; the conducting kernel does not invent a
commutation-failure or blocking circuit.

The existing `Cdc6tConverterBoundary` delegates its unchanged positive-voltage
API to this shared kernel. Both interfaces use the same reactive-power equation,
including the analytic cancellation that keeps reactive power finite at zero
DC active power. All numerical calls use caller-owned buffers, validate before
publishing outputs, and refresh voltage-dependent quantities at every call.
No controller state, clock, history or integration step is advanced here.

The bridge-plus-inductive-line test feeds trial line current back through both
converters at each integration stage. Modified Euler and staggered integration
both converge at second order to the independent RL analytic response. The
test also checks instantaneous AC active-power balance against line resistance
and the derivative of stored magnetic energy. Signed voltage, zero-current,
zero-active-power, lossless power factor, infeasible-trial atomicity and warmed
allocation checks pass. Existing current-order converter reference gates remain
unchanged and pass after sharing the equations.

### Independent electrical observations

All complete samples from the earlier cold-repeated controller/order captures
are evaluated with recorded firing angles, currents and terminal voltages.
Initial boundaries at both terminals pass, as do rectifier P/Q trajectories
under the 0.00001 pu gates. The full comparison files retain both startup
history and fault-side duplicate rows, including the unresolved inverter errors.

The instantaneous-angle comparison shows that inverter reference channels do not always satisfy simultaneous
`P = -Vi*Ii`: the largest same-row discrepancy is approximately 0.38334 MW.
The firing-angle kernel's largest recorded-input inverter P/Q differences are
approximately 0.38331 MW and 0.54103 Mvar. This is evidence that channel
publication and network evaluation stages require investigation; it does not
establish the correct coupled trajectory. The accepted-extinction-history
experiment below resolves the measured-input AC power discrepancy through an
explicit evaluation/commit order. Complete coupled SMIB acceptance remains open.

Targeted protection probes now exercise RAML and commutation-failure settings
with explicit fault reactance and static extinction angle recorded in each
manifest. The RAML controls cold-repeat at both reference steps. The initial
high-threshold commutation profiles lose native network convergence. A separate
default-threshold control with lower initial extinction margin also activates
commutation failure but loses network convergence later. All are retained as
rejected evidence in the private `chvdc2-converter-20261102/protection-captures.zip`
archive, alongside the complete valid RAML controls. These observations do not
yet qualify a production protection state machine or automatic attachment.

The focused electrical-boundary run passes all 45 checks. The registered suite
runs 4,413 checks with the same 115 pre-existing failures, no errors and 13 skips;
failure identities match the retained preceding checkpoint. The core-plugin
module runs 486 checks with no failures or errors and four skips. The private
`chvdc2-converter-20261102/validation.zip` retains executable/source hashes,
complete comparison traces, logs and reports. These regression results preserve
the unresolved model gates rather than treating them as accepted behavior.

## Accepted protection and extinction-angle history

`Chvdc2Protection` adds one shared RAML washout coordinate, prepared reciprocal
time, bounded minimum-angle output, and explicitly committed RAML and
commutation-failure histories. Modified Euler and staggered integration use the
same scalar RHS. The numerical coordinate is the washout lag; after the RAML
switch releases, that memory decays while the output returns to the entered
minimum. Entered settings remain unchanged when preparing the RAML voltage
threshold from the initial rectifier voltage.

The owner supplies filtered rectifier voltage, terminal inverter voltage and
the newly calculated extinction angle. Only `acceptBoundary(time, sequence)`
may update the protection modes or clocks. Sequence numbers distinguish accepted
events sharing a timestamp; repeated acceptance requires identical inputs.
Falling below the RAML threshold triggers its hold. Release requires both a
strictly expired hold and voltage strictly above the threshold. Commutation
failure uses a strict angle trigger, strict minimum duration and inclusive
voltage-recovery threshold; nonpositive failure angle disables it. One accepted
boundary makes at most one short/release transition.

Trial input refresh, derivative evaluation, zero-duration integration and
observation do not commit histories. A preallocated transaction checkpoint
restores states, inputs, flags, timestamps, accepted extinction angle and event
revision together after a rejected boundary. Predictor cancellation restores
the numerical coordinate. The event owner supplies a stored left-side RHS for
staggered discontinuities; this is distinct from the solver's older two-step
rate history. Named state/derivative/history maps allocate only when requested;
the warmed numerical and transactional update checks allocate no per-step objects.

### Inverter AC power uses committed extinction history

The retained controls identify a specific evaluation order: inverter AC power
uses the previously accepted extinction angle with the current terminal voltage
and current. The newly calculated extinction angle is then committed for the
next boundary. `acceptedInverterExtinctionDegrees()` exposes that history;
ordinary input refresh leaves it fixed through all network trials. Acceptance
advances it exactly once, including a separately accepted event-side boundary
at unchanged physical time.

This order reconstructs every inverter P/Q sample in the normal controller and
current-order controls under the unchanged 0.00001 pu electrical gates. The
same order passes those gates during the valid short-duration commutation
control under both integration methods. The contemporaneous-angle calculation
remains a negative control. There is no trace interpolation, timestamp offset,
changed model coefficient or fitted filter. These are recorded-input boundary
checks; the complete runtime still must calculate and own its fresh extinction
angle and network state.

### Independent protection checks and remaining qualification

Both methods reproduce every short/release flag and transition timestamp in the
cold-repeated valid 0.5 ms commutation control. That control uses an explicitly
entered short hold. The default-duration control remains nonconvergent even
with an explicit network ceiling of 50; its tolerance is unchanged. The refined
short-hold control also fails native network convergence. These failed runs
remain rejected, so neither default-duration nor refinement acceptance is claimed.

The valid source shorting flag sets line-side inverter voltage to zero while
retaining line current. Its recorded AC injection remains nonzero and agrees
with the adjacent AC source powers. Therefore a complete electrical owner must
qualify the distinction between the imposed line-side short and the accepted-
angle AC interface; resetting current or replacing AC power with zero is not
supported by these captures.

All four full RAML replays still fail the strict angle/state gates. The largest
washout-state discrepancy is about 0.71461 degrees and the sampled minimum-angle
discrepancy reaches 35 degrees around switch activation. At the source's
activation row the published filtered-voltage state has not yet crossed its
prepared threshold. This identifies a stage-ownership question; no anticipatory
threshold, fitted delay or shifted trace is used to hide it. Both complete
samples and event-side duplicates remain in the comparisons. Joint controller,
protection and network execution, local substeps, RAML observation timing and
complete model attachment remain required.

The private `chvdc2-protection-20261102/captures.zip` retains the valid repeated
commutation control and the rejected iteration/refinement controls. The earlier
RAML archive remains immutable. Source/kernel fingerprints, complete replays
and regression reports are retained in the accompanying validation archive.

The focused protection/converter run executes 70 checks with four RAML replay
failures and no errors. The registered suite executes 4,452 checks with 119
failures, no errors and 13 skips: all 115 preceding failure identities remain,
and only the four explicit RAML replay gates are new. The core-plugin module
executes 486 checks with no failures or errors and four skips. These results
qualify the accepted-history and electrical reconstruction scope above; they
do not qualify the complete protected model.

## Joint controller state ownership and RAML limit separation

`Chvdc2ControlAssembly` connects the existing measurement/current-order, current-PI
and protection components directly. Its fifteen numerical coordinates follow
the source controller-state order. Modified-Euler prediction/correction and
the shared staggered participant use the same equations, dependency order,
prepared constants and underlying state storage. There is no second set of
controller equations, expression lookup or per-step object allocation.

The electrical owner supplies current, voltage, current reference, the common
auxiliary signal and fresh extinction angle. Current controls receive the
measurement and order values from the same numerical stage. A modified-Euler
corrector therefore sees predicted measurements rather than stale values from
before the predictor. State projection applies measurements and the RAML
coordinate before evaluating dependent firing-angle bounds.

Protection acceptance remains explicit. `acceptBoundary` accepts a converged
boundary; `evaluatePredictorProtection` evaluates a protection branch inside an
open predictor trial. Canceling that predictor restores its numerical states,
inputs, protection clocks and modes, accepted extinction history and event
revision together. A pending predictor does not publish its discontinuity
revision or fresh extinction angle to the electrical boundary. An independent
outer transaction can roll back completed local stages and a pending predictor.
Exact rollback bypasses limiter projection, since a saved state may precede
the first numerical application of a newly accepted bound. Trial state and
history diagnostics remain inspectable; observation does not accept a trial.

The assembly does not choose a default number of local steps or advance a
network clock implicitly. Its caller selects the integration interval and
acceptance sequence. Both-method analytic tests verify second-order convergence
of the connected measurement/current-integral equations. Separate tests cover
simultaneous correction, equilibrium, dependent-write failure rollback,
predictor and transaction ownership, moving bounds and warmed zero-allocation
updates.

### RAML changes the angle floor, not the PI memory

Independent RAML captures show the rectifier PI state remaining near 32.7 degrees
while the moving firing-angle floor rises above it. The PI continues to integrate
current error under its entered bounds. The firing-angle coordinate follows the
RAML floor. Applying that moving floor to both coordinates incorrectly rebased
the PI state by approximately seven degrees and retained that error after RAML
released.

`Chvdc2CurrentControls` now uses the entered rectifier minimum for the PI
coordinate and its anti-windup test. The moving RAML minimum still constrains
the PI sum sent to the angle lag and the firing-angle coordinate. The two cold-
repeated independent controls check the PI increments and the angle-floor
response; both integration methods also exercise activation and release in
focused equation tests. The inverter limits and entered constants are unchanged.

### Complete joint replays remain diagnostic

The joint reference tests evolve all fifteen controller states continuously
from initialization. They use recorded physical terminal inputs, held during
each explicitly configured ten-step local cycle, and retain every startup and
event-side sample. A candidate publication order records the final local
derivative evaluation before its state update; an event-left observation has
zero duration. No recorded controller state is reset after initialization.
This is a test of stage ordering, not an accepted network execution contract.

The PI-limit correction reduces the largest persistent rectifier PI discrepancy
from about 7.27 degrees to 0.00450 degrees. All four joint replays still fail
strict state/output gates. The first post-event voltage-filter state differs
from the reference, changing RAML pickup/release timing; at the coarser step
this produces a large one-sample firing-angle difference. The small PI remainder
also needs qualification without changing production precision. Internal
electrical-stage inputs, event integration and publication ownership must be
resolved before full coupled SMIB acceptance or automatic attachment.

The focused checkpoint executes 82 checks with 28 failures: 24 preceding
current-controller replay gates and the four new joint replay gates. The
equation, ownership, allocation and independent PI-limit controls pass. The
registered suite executes 4,471 checks with 123 failures, no errors and 13 skips;
all 119 preceding failure identities remain unchanged, and only the four new
joint replay gates are added. The core-plugin module executes 486 checks with
no failures or errors and four skips. The private
`chvdc2-joint-controls-20261102/validation.zip` retains the source/executable
fingerprints, complete before/after diagnostic traces, reports and reference
archive hashes. Full model qualification remains open.

## Coupled controller and DC-line runtime

`Chvdc2CoupledRuntime` owns the controller coordinates, DC-line coordinates and
electrical acceptance history together. Both modified Euler and staggered
integration use the same right-hand side. The prepared converter geometry
calculates firing-angle terminal voltages and fresh extinction margin from live
AC voltages and DC currents. Terminal current injections refresh with every
network trial. Reusable primitive buffers cover numerical stages, boundary
evaluation and nested rollback; warmed numerical and electrical updates allocate
no objects in the focused tests.

The runtime supports the algebraic line, the inductive line and the three-state
capacitive T-circuit. Tests check a flat initialized boundary, conversion of
power to signed current injections, line loss/energy balance, both-method
responses, commutation shorting without deletion of stored current, and atomic
failure/predictor/outer-transaction rollback. Zero terminal AC voltage and reverse
DC current remain rejected rather than assigned an unqualified conducting
equivalent. Source capacitance preparation is described below; full capacitive
trajectory qualification remains open.

### Electrical publication is distinct from a numerical protection stage

A predictor can evaluate a protection transition without publishing a new
extinction angle to the AC interface. Finishing its corrector retains that
electrical history until explicit boundary acceptance. Protection mode and its
clock can therefore participate in the numerical trial without advancing the
AC publication twice.

`readAcceptedBoundary` returns the power sample evaluated using the held angle,
even after fresh extinction margin has been accepted for the next boundary.
Repeated acceptance of an unchanged sequence is exactly idempotent. Changing
phasors, coordinates or time requires a new sequence; an older electrical epoch
cannot rewind a completed protection stage. All acceptance snapshots are
included in rollback. Regression tests first reproduced changed inverter power
on repeated acceptance and now preserve the complete published sample exactly.

### Coupled independent trajectories remain unaccepted

The new comparisons integrate all controller and line states continuously from
the initialized operating point. The synthetic AC system is reduced to two
source equivalents with its specified source/branch impedances. Reference source
angles are checked to stay fixed. Fault admittance and live converter injections
determine terminal voltage; no recorded voltage or state trajectory is imposed
after initialization. Raw negative startup samples and both event-side duplicates
remain in every comparison.

Both rectifier- and inverter-side faults are checked at 0.5 and 0.25 ms under
both integration methods. The eight comparisons still fail unchanged strict
state and power gates. Across these profiles, bus-voltage error is below
0.0000024 pu, line-current error reaches 0.520 A, and rectifier firing-angle error
reaches 0.00542 degrees. Voltage agreement alone does not qualify the converter.

The first post-event reference sample retains its previous current-measurement
state while line current and AC-voltage measurement have begun moving. The
coupled numerical schemes produce different first increments. Reducing the
network interval does not remove every discrepancy. Event sampling, internal
controller/line sequencing and published-state timing remain to be established;
no coefficient, precision, time shift or gate was fitted to these traces.

This runtime is not automatically attached by the importer. Full production
network attachment, protected/capacitive coupled trajectories, startup and
event sequencing, and complete independent SMIB acceptance remain required.

The registered checkpoint executes 4,493 tests with 131 failures, no errors and
13 skips. All 123 preceding failure identities remain; the only additions are
the eight new coupled-reference comparisons. All fourteen coupled-runtime tests
pass, including repeated-acceptance negative controls and rollback. The
core-plugin module executes 486 tests with no failures or errors and four skips.
The private `chvdc2-coupled-runtime-20261102/validation.zip` retains complete
coupled traces, the reference-only event-increment diagnostic, source/executable
fingerprints, regression reports and immutable reference-archive hashes.

## Network-interval capacitance preparation

The `Chvdc2Parameters.prepareDcLine` overload with `networkIntervalSeconds`
applies the source rule once, before the line state storage is allocated:

```text
fn = 1 / (2*pi*sqrt(L*C))
ignore capacitor dynamics only when line dynamics are enabled, C > 0,
and 3*fn*H > 10
```

Here `H` is the outer network interval, and `L` includes the prepared commutation
inductance. Equality retains the capacitor. Suppression changes the numerical
line from three coordinates to one series-current coordinate; it retains the
inductive dynamics and leaves all entered parameters unchanged. Invalid network
intervals and capacitance unit conversion underflow are rejected. The original
overload without an interval remains physical-only preparation for explicit
equation use; it does not assume a network interval.

Local modified-Euler or staggered steps use the resulting immutable coefficients.
Refining those local steps cannot silently restore a capacitor that was
suppressed for the outer interval. A changed network interval requires explicit
new preparation. The documented frequency criterion is a model-preparation
rule, not a stability certificate for the chosen integration interval.

Independent cold repeats check the suppressed and retained modes. The tiny-
capacitance control at the coarser network interval matches the complete earlier
zero-capacitance reference byte-for-byte, including every channel and event-side
sample. With the finer network interval, that same entered capacitor remains
in the initialized state vector, but the reference subsequently becomes
nonfinite after the fault. Its raw output, full CSV and rejection manifest are
retained; only its initialization/preparation decision is used as evidence.
The capture tool now records nonfinite results before returning failure, rather
than losing the diagnostic at its pre-export assertion.

A larger retained capacitor completes independent fault runs at both network
intervals with unchanged entered data. Coupled tests now evolve all eighteen
controller/line coordinates and compare capacitor voltage and both line
currents. All fourteen coupled comparisons remain unaccepted: the six additional
mode comparisons fail strict trajectory gates, while the four independent
preparation checks pass. The retained-capacitor profiles reach 0.631 A current
error, 0.00592 degrees rectifier-angle error and 119.6 V internal capacitor-voltage
error. These are separate from AC bus-voltage error. The original eight coupled
trajectories remain numerically unchanged.

The private `chvdc2-capacitor-20261102/captures.zip` preserves both cold repeats,
the rejected controls and exact probe provenance. Full coupled state/event
sequencing, protected trajectories and production network attachment remain
required before either record form can be classified as fully supported.

The focused run executes 54 checks with fourteen coupled-reference failures and
no errors. The registered suite executes 4,508 checks with 137 failures, no
errors and 13 skips: all 131 preceding failure identities remain, and only the
six additional coupled-mode comparisons are new. The core-plugin module
executes 486 checks with no failures or errors and four skips. The accompanying
private `chvdc2-capacitor-20261102/validation.zip` retains source/executable
fingerprints, complete new traces, regression reports and exact equality checks
against all eight preceding coupled trajectories.

## Independent run-boundary clock checks

The capture tool can now change the entered measurement lag and record public
state, variable, voltage and time snapshots at explicit run boundaries. A
separate split-run control pauses immediately after the first faulted step and
the first cleared step. Every original sample, event side and additional pause
observation remains in the immutable private capture archive. The installed
scripting environment cannot import its documented derivative module; derivative
access is explicitly unavailable, rather than inferred from finite differences.
The failed API probes are retained separately from completed captures.

With a 1 ms measurement lag, independent cold repeats establish these properties
at 0.5 and 0.25 ms network intervals:

- Reading public snapshots leaves the complete ordinary-run CSV byte-identical.
- Public state arrays at run return match the recorded states at the same
  physical time, including event-side duplicates. Fault application and clearing
  alone do not advance those states or refresh the published variables and bus
  measurements before the next run call.
- Pausing after the first faulted step leaves all previously recorded states
  unchanged, but changes the states after resuming. The maximum change in the
  rectifier AC-voltage measurement state is 0.0002797842 pu at the coarser step
  and 0.00009000301 pu at the finer step. These are controller-filter differences,
  not bus-voltage accuracy errors.

`Chvdc2RunBoundaryReferenceTest` checks these properties against both cold
repeats, verifies all retained file hashes, and compares common samples by time
and occurrence. Extra pause observations stay in the archive. The small summary
reports use bounded writers in unique run directories, so repeating the test
preserves earlier evidence without an output collision. These reports do not
substitute for the complete captures.

This evidence rules out a simple CSV state-publication delay at run return and
shows that run segmentation affects numerical history. It does not identify the
internal integration recurrence or justify introducing a fitted half-step, input
interpolation, precision change or extra filter into the Java equations. A faster
measurement-lag discriminator also fails to support the earlier input-ramp
hypothesis. Reconcile accepted input/history stages before changing either shared
integration path; a pause/resume diagnostic must not silently replace the
continuous reference run used for trajectory acceptance.

The private `chvdc2-boundary-clock-20261102/captures.zip` contains the uninstrumented,
observation-only and split-run controls, exact scripts, manifests and failed API
attempts. Both new clock checks pass. No production equation or accuracy gate is
changed, and complete coupled trajectories remain unaccepted.

## Live published measurements and numerical coordinates

`Chvdc2ControlAssembly.readPublishedStates` and
`Chvdc2CoupledRuntime.readPublishedStates` now expose the low-level order
controller's algebraic observations through the joint owners. With zero entered
measurement time, measured rectifier/inverter currents and AC voltages follow
their current terminal inputs immediately, including zero-duration event sides.
The coupled accessor refreshes voltage-dependent inputs and appends the line's
numerical coordinates. Both accessors write into caller-owned reusable arrays.

`readStaggeredState`, `getIntegrationStates` and rollback checkpoints continue to
represent numerical storage. Reading published values does not project or
advance that storage, accept a protection/electrical epoch, change the frozen
accepted power sample, or replace derivative history. Observations are also pure
during a modified-Euler predictor and after transaction rollback. The warmed
both-method integration/allocation checks now include published-state reads and
still allocate no per-step objects. Recording remains explicit; numerical updates
do not create an observation map or array when diagnostics are disabled.

Independent zero-measurement-time captures at both network intervals verify all
four algebraic measurement channels under both Java integration methods. The
comparison prescribes the recorded terminal inputs, retains every sample and
event side, and checks that repeated observations leave coordinates and histories
unchanged. Maximum observation error is below 0.0000001 pu. This qualifies the
memoryless observation boundary, not the remaining controller or coupled-network
trajectories. The complete coupled comparison now uses published observations
for recording and continues to use numerical coordinates for integration.

Separate independent controls disable DC-line dynamics while retaining a positive
measurement lag. The first rectifier AC-voltage measurement increment is exactly
the same as in the dynamic-line control, although the DC-current response differs
substantially. The first current-measurement state remains unchanged in both
controls. Therefore the filter's first-step timing cannot be attributed to line
inductance. The algebraic line's electrical publication and accepted input/history
stages still need reconciliation; do not replace them with an inferred delay.

An equation-only reconstruction using the previous recorded terminal voltages
and current firing angles matches the algebraic DC current within 0.003 A on
positive-time intervals. Using same-time voltages instead reaches about 0.58 A
error. Both original time grids and duplicate observations are retained. This
localizes an electrical-input sampling boundary; it does not establish the full
controller integration recurrence or the update policy at zero-time event sides.

The private `chvdc2-measurements-20261102/captures.zip` retains both cold repeats of
the bypass and line-mode controls. `Chvdc2MeasurementReferenceTest` separates the
four both-method observation checks from the two reference-only line-mode
discriminators. Full protected/capacitive trajectories and automatic network
attachment remain open.

## Coupled line-mode and numerical-refinement controls

The coupled comparison now reads line-dynamics flags from each reference manifest
and exercises the algebraic line as well as the dynamic line with bypassed
measurements. Recorded inputs do not drive the evolving Java states or terminal
voltages. Both methods retain all reference samples and event-side duplicates.
A separate control uses ten numerical steps per recorded interval, solves the AC
circuit at each stage, and preserves the outer interval for coefficient
preparation. This is whole-system refinement, not a claim that the reference uses
the same local clock.

- With line dynamics disabled, coarse single-step staggered integration becomes
  unstable before the fault and reaches the unsupported reverse-current boundary
  at about 0.0375 s. Ten steps complete that trajectory under both methods. The
  failed run remains an error; its partial trace, completed-sample count, stopping
  time and exception are retained explicitly.
- Refinement does not remove the algebraic line's event-side mismatch: maximum
  current difference remains about 65.3 A and voltage difference about 0.0003664
  pu. Away from event times, the refined current difference still reaches roughly
  9.4 to 15.8 A. Acceptance includes every event sample. Electrical input and
  publication stages need investigation separately from numerical stability.
- With dynamic line current and zero measurement lag, both methods complete the
  refined runs, but current differences remain about 0.356 A at the coarser
  interval and 0.481 A at the finer interval. Bypassing the measurement lag does
  not establish coupled agreement.

A retained equation diagnostic also locates a late-time precision limitation in
the original fine-interval references. Both fault directions end with unchanged
measured-current, rectifier PI/angle and line-current coordinates for more than
0.2 s, despite an interior rectifier PI derivative of approximately 0.07 degrees
per second. These samples are not equilibria of the continuous equations. A
binary32 increment using one tenth of the outer interval is absorbed by rounding
at those stored PI values; an outer-interval increment is representable. This is
consistent with the earlier precision discriminator, but does not identify the
complete internal recurrence. Production arithmetic and gates are unchanged.

The existing numerical/history and allocation checks pass. All new coupled
trajectory checks remain unaccepted. The private
`chvdc2-coupled-modes-20261102/validation.zip` retains comparisons, the explicitly
incomplete trajectory, refinement settings, plateau diagnostic and regression
evidence. Resolve electrical sampling, local integration ownership, protected
and capacitive trajectories, and automatic attachment before full qualification.

## Sampled algebraic current and accepted integration intervals

The coupled owner now retains algebraic DC current as accepted numerical history.
Network trials and observations refresh the AC power and phasor-current
transformations using live terminal voltages, but do not implicitly resample that
DC current. On completion of a numerical interval, the owner uses the interval's
sampled AC voltage magnitudes and the updated firing angles to publish the next
current. The dynamic-line equations and storage are unchanged.

Modified Euler publishes current after the corrector, using the predictor-start
voltage sample. Shared staggered state proposals remain history-pure:
`applyStaggeredState` may be used for trial states and derivative probes, while
`completeStaggeredStep` publishes current once for the accepted step. Zero-duration
completion does not advance history. Repeated or mismatched positive completion
is rejected. Numerical coordinates, sampled current, pending proposals and
interval progress all participate in transaction rollback.

`beginIntegrationInterval(H)` and `completeIntegrationInterval()` let local
integration steps retain one outer electrical sample. Completion requires local
durations to sum to H and occurs before the final network solve. Without this
explicit bracket, each completed numerical step is one electrical interval.
These calls do not alter coefficient preparation or choose a default substep
count. Production attachment and local-step orchestration remain unfinished.

Independent prescribed-input/state component replays retain every observation
and both event-side duplicates. At outer intervals of 0.5 and 0.25 ms, maximum
current errors are 0.008035 and 0.007552 A, respectively, within the existing
0.01 A current gate. A separate raw-angle reconstruction stays below 0.0031 A;
the component owner additionally projects firing angles to its calculated
limits. These checks qualify the sampled-current component boundary, not the
evolving coupled trajectories.

The complete coupled comparisons use no prescribed reference states or boundary
inputs. With ten local steps and one outer electrical sample, their results are:

| Outer interval | Integration method | Maximum voltage error (pu) | Maximum current error (A) |
| --- | --- | --- | --- |
| 0.5 ms | Modified Euler | 0.000004266 | 0.8923 |
| 0.5 ms | Staggered | 0.000003194 | 0.5532 |
| 0.25 ms | Modified Euler | 0.000003603 | 0.6682 |
| 0.25 ms | Staggered | 0.000003093 | 0.6682 |

These replace the preceding checkpoint's roughly 65.3 A algebraic-line mismatch.
The refined voltage gate passes, but the unchanged 0.01 A coupled-current gate
still fails. Single-step coarse staggered integration still reaches an
unsupported reverse-current boundary before the fault; its partial trajectory
and exception are retained. All 22 dynamic-line comparison traces, including
zero-measurement-time refinement controls, remain byte-identical to the preceding
checkpoint. Numerical/history checks cover both integration methods and show
zero warmed per-step allocation, including explicit interval completion.

The largest remaining normalized internal-state error in these refined controls
is the rectifier AC-voltage measurement immediately after fault application.
It persists under both integration methods. Resolve that measurement/integration
boundary separately from the independently checked algebraic-current sampling;
the late-time current discrepancy must also retain the reference plateau
diagnostic described above.

The private `chvdc2-sampled-line-20261102/validation.zip` retains the component
replays, complete and explicitly incomplete coupled comparisons, executable and
source snapshots, and regression evidence. Full trajectory qualification,
protected algebraic-line behavior, production local-step ownership and automatic
network attachment remain open. No acceptance threshold or production precision
was changed.

Registered regression validation ran 4,545 tests: 152 failures, one error and 13
skips, with exactly the same failing/error test identities as the preceding
checkpoint. All eleven added checks pass. The core plugin module ran 486 tests
with no failures or errors and four skips. An earlier broad attempt hit the
generated-output free-space guard; its reports are retained separately and are
not used as regression evidence. Inactive generated outputs were relocated with
hash verification and original-path junctions before the completed retry.

## Same-time event calls and the measurement equation

Independent cold controls now make one or two `RUN` calls with the current
physical time immediately after fault application and clearing. At both retained
network intervals, every complete output CSV is byte-identical to the ordinary
continuous baseline. The calls preserve time, all public states and variables,
and bus voltages. They neither add observations nor perform the post-event
network evaluation. Cold repeats also reproduce the boundary snapshots exactly.

These calls therefore cannot be used to repair the remaining first-step filter
discrepancy. They are distinct from pausing after a positive integration interval,
which the preceding run-boundary controls show can change subsequent history.
The source operation manual describes pre-event and post-event derivative/network
stages at pause/resume boundaries. That description does not expose the complete
internal controller recurrence, and does not justify a fitted time shift.

Review of the installed model diagram confirms a first-order terminal-voltage
measurement with the entered measurement time constant. No additional input
delay is shown. The current shared measurement equation is consistent with that
diagram. Investigation must retain the distinction between equation conformance
and the still-unaccepted event/integration trajectory; no extra delay, gain,
precision rule or parameter correction was introduced.

`Chvdc2RunBoundaryReferenceTest` checks both refresh counts, both cold repeats and
both time grids against the unchanged baseline, using complete CSV equality and
public boundary snapshots. The private
`chvdc2-event-refresh-20261103/captures.zip` retains the raw outputs, exact capture
scripts, manifests, diagram review and document fingerprints. These are reference
lifecycle discriminators, not acceptance of either Java integration method or of
the complete model. Production local integration, protected trajectories and
automatic attachment remain open.

The focused lifecycle, component-measurement and run-boundary selection passes
all 37 checks with no skips. The new checks belong to the existing registered
run-boundary test class. Production Java is unchanged in this slice; the preceding
registered-suite failure baseline is retained, not represented as a fresh full
suite run. Capture and validation evidence are retained separately in the private
event-refresh artifact directory.
