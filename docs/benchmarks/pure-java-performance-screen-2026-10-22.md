# Pure Java performance screen

The eight-second goal remains open. Two isolated optimizations were tested
without changing production source: fusing sparse-solver output scattering into
backward substitution, and using a fixed preparation schedule for ST4B. Neither
demonstrates a sufficient, consistent improvement to promote.

All work uses the Java sparse backend. Full-case runs use one pinned logical
CPU, quarter-cycle network steps, 1e-5 voltage-correction tolerance, one ST1
local step, and complete recording. Existing experimental network acceleration
remains selected; these results do not qualify independent-reference accuracy
or six-case completion.

## Sparse output scattering

The candidate writes each completed row to the result buffer during backward
substitution, retaining the numerical arithmetic and factorization reuse.
Twelve sparse tests pass in each mode. Eight deterministic right-hand sides
match raw double bits on the captured final Case 1 factor.

After 32 warmup batches, seven measured pairs of 3,000 solves give medians of
0.081174 seconds for the control and 0.080357 for the candidate. The median
paired saving is 0.001160 seconds, with five wins. Each batch includes resetting
the right-hand side. Scaling that difference to 18,340 iterations suggests only
about 0.007 seconds; this is a rough estimate, not measured full-case savings.
Only the final factor and synthetic right-hand sides were timed. One full
control recording matches the baseline; the candidate has no full-trajectory
qualification. A reciprocal-factor alias uses the original path, but that
fallback has no dedicated test. The change is not promoted.

## Fixed ST4B preparation

The candidate evaluates the six standard blocks through explicit calls in the
existing order and uses typed standard output reads. It retains all feedback
passes, operand reads, state updates and limiters. Other graph shapes retain
the original node loop.

| Case | Control median, s | Candidate median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 9.523720 | 9.944845 | 0.107179 | 3/5 |
| 2 | 7.155694 | 6.574187 | 0.735742 | 4/5 |

Sixteen alternating warmups precede five order-alternating measured pairs per
case. Each complete interval includes fresh loading, preparation,
initialization, simulation, recording and writer closure. Compilation and GC
remain included. Ranges overlap substantially; the median of paired differences
and the difference of arm medians are distinct statistics. These measurements
do not establish a production regression relative to earlier separate runs.

All 52 recordings match the retained baseline byte for byte. Nine focused
tests cover zero/nonzero predictor and corrector stages, states before and
after output observation, events, activation, changing controls and limits,
custom blocks, observed machines, nonfinite inputs and reinitialization.
The optimized schedule executes 4,527,240 preparation passes in each Case 1
candidate run. Case 1 has no reliable gain, so the prototype is not promoted.

## Remaining work by phase

A separate baseline-only diagnostic uses twelve warmups and three measured
Case 1 runs. Network and integration timers exclude initialization, and event
recording inside integration is subtracted before computing exclusive totals.

| Timed region | Median seconds |
| --- | ---: |
| Complete case | 10.230202 |
| Network solves during simulation | 3.649467 |
| Integration excluding network and nested event recording | 5.646951 |
| Selected-channel sampling | 0.002275 |
| Selected serialization and full-network recording calls | 0.300658 |

The median remaining time is 0.626604 seconds, including setup, initialization,
writer closure, final status processing and loop overhead. Separate region
medians need not sum to the full median. All fifteen diagnostic recordings
match the baseline. Instrumentation and ongoing compilation remain included;
the 10.230202-second result is diagnostic attribution, not a replacement for
the uninstrumented performance baseline.

Integration outside the network is the larger measured region. The next audit
should split its controller evaluation, state advancement, activity/attachment
checks and other device updates before selecting another optimization.
Recording remains included; its measured write cost cannot alone explain the
remaining gap. A small sparse output-copy improvement cannot close it either.

The [evidence index](checkpoints/pure-java-performance-screen-2026-10-22.json)
identifies compact archives with sources/classes, commands, raw timings,
test reports, affinity, input/runtime identities and recording hashes. Complete
recordings remain explicit external dependencies. Supplied references and
existing unrelated worktree changes are preserved.

## Integration breakdown and direct PI dispatch

A further baseline-only audit divides integration into thirteen regions. Twelve
warmups precede three diagnostic runs; all fifteen recordings match the baseline.
The pending compact-state implementation is excluded from this runtime.

| Integration region | Median seconds |
| --- | ---: |
| Initial endpoint evaluation | 1.233290 |
| Pre-network evaluation | 1.151798 |
| Post-network evaluation | 1.259266 |
| Electrical preparation | 0.472700 |
| State and participant capture | 0.236497 |
| Global state advancement | 0.192221 |
| Local ST1 advancement | 0.296319 |
| Renewable advancement | 0.161828 |
| Bus predictor | 0.129149 |
| Endpoint acceptance and bus corrector | 0.273131 |
| Participant state advancement | 0.019627 |
| Participant completion | 0.039666 |
| Attachment checks | 0.036465 |

The three ordinary evaluation medians sum to approximately 3.64 seconds, versus
0.43 seconds for state capture and global state advancement. This comparison
identifies the larger opportunity; it does not justify skipping evaluations.
Earlier audits demonstrate changing stabilizer electrical-power inputs between
these stages. The complete diagnostic median is 10.259546 seconds, including
instrumentation and compilation. It is not an uninstrumented timing result.

A read-only code-cache snapshot during warmup shows over 98 MiB free in each
compiled-method heap and no code-cache-full or stopped-compilation events.
Cache exhaustion was absent at that snapshot; this is not continuous monitoring.

An isolated ST4B follow-up bypasses generic block dispatch for the two PI blocks,
calling their existing Java predictor/corrector methods directly. It changes
neither equations nor preparation order. Nine focused tests pass, and all 52
complete recordings match the baseline with unchanged network iteration counts.

| Case | Control median, s | Direct PI median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.933141 | 8.974112 | -0.040971 | 2/5 |
| 2 | 6.249666 | 6.263710 | 0.128054 | 3/5 |

Sixteen alternating warmups precede five measured pairs per case, on one pinned
CPU with complete recording and writer closure. The candidate executes
3,018,160 direct PI calls in each Case 1 run. Its early favorable pairs do not
persist across the complete screen, so it is not promoted. One earlier attempt
finished simulation but failed on a misnamed diagnostic counter; that attempt
is retained separately and excluded from the completed-run count.

The [evidence index](checkpoints/pure-java-performance-screen-2026-10-22.json)
also retains these two audits. The next optimization needs to reduce work inside
model evaluation, with explicit dependency and lifecycle checks. State-array
copying, code-cache capacity and this small dispatch change do not explain or
resolve the remaining gap. Production source, numerical settings and the full
goal remain unchanged.

## Tagged first-order kernel screen

An isolated Java candidate combines the exact Delay, Filter, Washout and PI
kernel implementations into one final class, selecting the derivative equation
by a fixed block-kind integer. It preserves live parameter reads, arithmetic
order, predictor/corrector stages, scratch-array writes and state restoration.
Non-windup blocks retain their original update methods; custom subclasses and
other block families retain their existing kernels.

Five focused tests pass in each mode, including comparison against a separately
named copy of the original implementation. They cover limits, zero/negative/
positive time constants, zero/nonzero steps, state restoration, trial/commit,
scratch buffers, residuals, custom callbacks and special values. Finite and
signed-zero paths compare raw bits; special-value checks canonicalize NaNs.

The component test suggests a substantial dispatch improvement: 1.6 million
mixed advances take median 0.0060192 seconds in the control and 0.0029939 seconds
in the candidate. A follow-up that observes an output after every advance also
favors the candidate, 0.0084695 versus 0.0051590 seconds. Both use 32 warmup
batches and seven measured pairs on one pinned CPU. These synthetic workloads
do **not** predict the complete simulation result.

| Case | Control median, s | Tagged median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.865224 | 9.101596 | -0.236373 | 1/5 |
| 2 | 6.197149 | 6.328946 | -0.176821 | 1/5 |

Each case has sixteen alternating full-run warmups and five order-alternating
measurement pairs in one JVM. Timing includes fresh setup, initialization,
ten-second simulation, full recording and writer closure, including compilation
and GC. The Java solver, one-CPU affinity, quarter-cycle steps, 1e-5 network
tolerance, one ST1 step and existing experimental network acceleration remain
unchanged. All 52 recordings match the retained baseline byte for byte, with
18,340 network iterations in Case 1 and 20,202 in Case 2.

The candidate is **not promoted**: it loses four of five pairs in both cases.
The output-observation component test does not reproduce the slowdown, so output
dispatch alone is not an established explanation. Further work must measure
the integrated model-evaluation path rather than infer whole-simulation savings
from the kernel fixture. Case 1 remains above eight seconds; Cases 3–6 and
independent-reference accuracy remain unqualified. Production sources and
supplied references are unchanged by this experiment.

The [evidence index](checkpoints/pure-java-performance-screen-2026-10-22.json)
retains the compact archive, raw paired timings, tests and recording audit.

## Typed PSS2A input assignments

A subsequent candidate separates standard PSS2A input assignments into typed
call sites. Compound-filter propagation and custom callbacks remain intact;
no preparation passes or state updates are removed. Three focused tests pass
in each mode, covering live parameters, zero/positive time constants, internal
compound states, nonfinite inputs, custom input callbacks and replaced bindings.

| Case | Control median, s | Typed median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.953387 | 8.931192 | 0.037438 | 3/5 |
| 2 | 6.291398 | 6.326838 | -0.091827 | 1/5 |

The screen again uses sixteen alternating warmups, five measured pairs per
case, one pinned CPU, Java sparse solving, quarter-cycle steps and 1e-5 network
tolerance. Complete recording and writer closure remain timed. All 52 recordings
match the baseline, with unchanged network iteration counts. Each run verifies
that standard model plans selected the requested mode. Two compile-only harness
failures are retained separately and contribute no timing observations.

The small, variable differences do not establish a reliable improvement, so
this candidate is not promoted. The immediate PSS-to-exciter observation boundary
was also investigated in an [earlier endpoint-scope screen](checkpoints/pss-endpoint-scope-screen-2026-10-22.json).
The previously stated plan overlooked that retained experiment. Any further
endpoint-scope work must identify its substantive difference from that prototype
and measure the full result; removing state-comparison overhead alone is not
evidence of a speedup.
The [evidence index](checkpoints/pure-java-performance-screen-2026-10-22.json)
retains this screen. Production sources and supplied references are unchanged;
Case 1 and the full goal remain unqualified.

## Prepared endpoint observation follow-up

This follow-up combines a zero-time PSS evaluation and immediately held output
in one explicit call, then releases the scope before governor or machine updates.
It retains every derivative/state calculation and signal sample. It differs
from the earlier endpoint prototype in API and eligibility checks and uses one
ST1 step instead of ten. It is not the first investigation of this opportunity.

Seven focused tests pass, including stored values before output observation,
compound internal states, subsequent correctors, live inputs, nonfinite values,
custom callbacks, observer fallback, nested scopes, binding failure and
reinitialization. The earlier prototype's 53 tests are not claimed as coverage
of this revised implementation.

| Case | Control median, s | Candidate median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.951211 | 8.905927 | -0.083393 | 1/5 |
| 2 | 6.505872 | 6.557751 | -0.061465 | 1/5 |

Sixteen alternating warmups precede five measured pairs per case on one pinned
CPU, with Java sparse solving, quarter-cycle steps, 1e-5 tolerance and complete
recording/writer closure included. All 52 recordings match the baseline, and
network iteration counts remain unchanged. Counters confirm positive prepared
output reuse in every candidate and none in controls.

The candidate loses four of five pairs in both cases and is not promoted.
Eligibility-check overhead is a possible explanation, not a measured attribution.
Custom machine/controller-lookup overrides at the changed scope boundary also
need broader lifecycle qualification before production use; supplied-case parity
does not prove that behavior. Production sources and supplied references remain
unchanged. The eight-second goal and independent-reference accuracy remain open.

The [evidence index](checkpoints/pure-java-performance-screen-2026-10-22.json)
retains this follow-up and links earlier screens. Before selecting another
experiment, consult the [device-performance history](dynamic-device-profile-2026-10-21.md):
controller-reference binding, zero-time PSS kernels, imported-machine Norton
source caching, typed PSS inputs and endpoint-output reuse have all been tried.
A changed name or current baseline alone does not justify repeating a screen.

## Network component attribution

Twelve fresh-case warmups precede three measurements per case on one pinned logical CPU. The existing Java baseline uses 1/240-second network steps, 1e-5 voltage-correction tolerance, one ST1 step and experimental network acceleration. Full recording and writer closure remain timed. No production source changes.

| Region, seconds | Case 1 | Case 2 |
| --- | ---: | ---: |
| Complete case (instrumented) | 9.026617 | 6.505862 |
| Current assembly | 1.213837 | 1.146658 |
| Custom-current hook | 0.000374 | 0.000445 |
| Sparse solve with retained factors | 0.722714 | 0.781695 |
| Solved voltage publication and convergence observation | 0.459390 | 0.485539 |
| Acceleration eligibility and calculation | 0.541306 | 0.610407 |
| Accelerated voltage publication | 0.321248 | 0.360403 |
| Refactorization after initialization | 0.001970 | 0.001662 |

The network equation counters are 18,341 and 20,203. Outer solver iteration counters are 18,340 and 20,202. The direct event-processing network solve explains the additional call. Both cases factor twice after initialization; ordinary iterations reuse the factorization. The timing of those refactorizations is too small to explain the remaining performance gap.

All 30 recordings match the complete retained baseline byte for byte. No channels, samples or event duplicates are removed. These timer-instrumented measurements identify work; they are not new uninstrumented performance results or independent-reference accuracy acceptance. Existing six-case and accuracy limitations remain.

The first summary calculated network_other_s by subtracting all component timers from the outer networkSolutionStep timer. That remainder is invalid: the component timers include a direct event-processing solve that the outer wrapper excludes. The raw value is retained with this correction and is not used in conclusions. Similarly, the earlier integration-excluding-network region still contains that direct event solve. Component regions in the table are disjoint, but the outer network/integration fields must not be combined with them as exclusive categories. Initialization is outside the component counters and inside the complete-case time. Independent medians need not sum.

Current assembly is the largest measured network component. Solved-voltage publication and accelerated-voltage publication together are also material. The next distinct candidate should investigate whether the intermediate solved voltage must be written to every bus before a successful acceleration step immediately replaces it. This differs from the existing deferred-angle setter, which still writes both voltages. Any staged publication requires exact standard classes, observer/callback compatibility, live injection refresh at the next iteration, original convergence checks, correct publication on acceptance/failure, and comparison of full trajectories. This audit does not authorize skipping either publication in production.

Compact source/classes, commands, raw runs, scope correction, affinity, input/runtime/output hashes are retained. Complete recordings remain explicit external dependencies. Supplied references are preserved. The eight-second goal remains open.

## Staged voltage publication

The isolated Java candidate holds solved bus voltages while the original convergence check and eligible acceleration calculation run. A successful acceleration publishes its final voltage once. Convergence, rejected acceleration, iteration exhaustion and errors flush the pending solved values. The network matrix rows remain current. Voltage-dependent injections are refreshed at the next iteration. No integration stages or nonlinear checks are removed.

The path requires the exact standard network, matrix and bus classes, rectangular coordinates and no bus/network notification observers. Eligibility is rechecked after current assembly and solving. The solver uses an explicit contract that its acceleration-eligibility supplier is side-effect free and independent of bus voltage. Ordinary supplier registration disables staging. Custom complex values flush the pending prefix before ordinary handling. These are prototype APIs, not promoted production defaults.

Nine focused tests pass. They cover raw coordinates and signed zeros, reusable publication scopes, nested-scope rejection, standard-class/observer/coordinate guards, matrix-row visibility, notification order, both convergence norms, iteration callbacks, unchanged diagnostics, generic eligibility fallback, rejected acceleration, iteration exhaustion, thrown eligibility, divergent-prefix publication, changed factors, inactive/reactivated buses and observers attached during iteration callbacks. Custom complex fallback has source inspection, not a dedicated focused regression.

| Case | Control median, s | Candidate median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.845055 | 9.022781 | -0.188708 | 0/5 |
| 2 | 6.250584 | 6.481023 | -0.196025 | 0/5 |

Sixteen alternating warmups precede five order-alternating measured pairs in each case. All runs use one pinned logical CPU, the Java sparse backend, quarter-cycle steps, 1e-5 voltage-correction tolerance, one ST1 step and existing experimental network acceleration. Fresh case setup, initialization, simulation, full recording, writer closure, compilation and GC remain included. Controls use the same executable overlay with staging disabled; they retain its small no-op scope cleanup cost.

All 52 complete recordings match the retained baseline byte for byte. No samples, channels or event-side duplicates are removed. Iteration counts remain 18,340 and 20,202. Per-candidate publication counters are:

| Case | Staged scopes | Solved voltages published | Intermediate writes discarded |
| --- | ---: | ---: | ---: |
| 1 | 18,340 | 9,616,000 | 27,064,000 |
| 2 | 20,202 | 9,616,000 | 30,788,000 |

This screen is not a production promotion. Supplied-case recording equality establishes no new independent-reference accuracy or six-case performance acceptance. Arbitrary concurrent access is outside the single-threaded prototype contract. Further qualification would also need a dedicated custom-complex fallback test and a lifecycle API that cannot be misused by an ordinary external observer. The eight-second objective remains open.

The prior network timing audit motivated this distinct candidate: avoiding an intermediate bus write differs from deferring angle computation while still performing both writes. Full raw times and paired differences are retained; independent arm medians and the median of paired savings are different statistics. Sources/classes, commands, tests, affinity, input/runtime hashes and complete-recording hashes are preserved in compact evidence; large recordings remain explicit external dependencies. No production sources or supplied references were changed.

## Matrix-backed staged publication follow-up

The revision does not establish a useful end-to-end improvement. Its median
paired saving is only 0.007406 seconds in Case 1 (three wins in five pairs),
and it loses 0.106010 seconds in Case 2 (two wins). The preceding buffered
version loses all five pairs in both cases. Neither version is promoted.
Together, the two screens retain 104 identical full recordings and eighteen
successful focused-test executions. The eight-second objective remains open.

The follow-up uses the solved matrix rows as pending voltage storage instead of copying voltages and magnitudes into temporary arrays. The existing convergence check and eligible acceleration calculation run before publication. A successful acceleration publishes its final voltage once. Convergence, rejected acceleration, iteration exhaustion and errors flush the pending solved values. The network matrix rows remain current. Voltage-dependent injections are refreshed at the next iteration. No integration stages or nonlinear checks are removed.

The path requires the exact standard network, matrix and bus classes, rectangular coordinates and no bus/network notification observers. Network eligibility is checked after current assembly and solving. Bus eligibility is checked in publication order; before any unsupported bus getter or observer runs, the preceding staged prefix is published and the remainder uses ordinary dispatch. This avoids a separate full bus scan. The solver uses an explicit contract that its acceleration-eligibility supplier is side-effect free and independent of bus voltage, and cannot mutate the solved matrix or membership. Ordinary supplier registration disables staging. Custom complex values flush the pending prefix before ordinary handling. Flush recomputes magnitudes from the unchanged standard matrix values using the original magnitude operation. These are prototype APIs, not promoted production defaults.

Nine focused tests pass. They cover raw coordinates and signed zeros, reusable publication scopes, nested-scope rejection, standard-class/observer/coordinate guards, matrix-row visibility, notification order, both convergence norms, iteration callbacks, unchanged diagnostics, generic eligibility fallback, rejected acceleration, iteration exhaustion, thrown eligibility, divergent-prefix publication, changed factors, inactive/reactivated buses and observers attached during iteration callbacks. Custom complex fallback has source inspection, not a dedicated focused regression.

| Case | Control median, s | Candidate median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.909406 | 8.862637 | 0.007406 | 3/5 |
| 2 | 6.327662 | 6.401398 | -0.106010 | 2/5 |

Sixteen alternating warmups precede five order-alternating measured pairs in each case. All runs use one pinned logical CPU, the Java sparse backend, quarter-cycle steps, 1e-5 voltage-correction tolerance, one ST1 step and existing experimental network acceleration. Fresh case setup, initialization, simulation, full recording, writer closure, compilation and GC remain included. Controls use the same executable overlay with staging disabled; they retain its small no-op scope cleanup cost.

All 52 complete recordings match the retained baseline byte for byte. No samples, channels or event-side duplicates are removed. Iteration counts remain 18,340 and 20,202. Per-candidate publication counters are:

| Case | Staged scopes | Solved voltages published | Intermediate writes discarded |
| --- | ---: | ---: | ---: |
| 1 | 18,340 | 9,616,000 | 27,064,000 |
| 2 | 20,202 | 9,616,000 | 30,788,000 |

This screen is not a production promotion. Supplied-case recording equality establishes no new independent-reference accuracy or six-case performance acceptance. Arbitrary concurrent access is outside the single-threaded prototype contract. Further qualification would also need a dedicated custom-complex fallback test and a lifecycle API that cannot be misused by an ordinary external observer. The eight-second objective remains open.

The preceding buffered-publication candidate lost all five pairs in both cases. This revision removes the temporary arrays and separate bus guard scan; it tests whether that bookkeeping explains the loss. It remains distinct from deferred angle computation, which still performs both voltage writes. Full raw times and paired differences are retained; independent arm medians and the median of paired savings are different statistics. Sources/classes, commands, tests, affinity, input/runtime hashes and complete-recording hashes are preserved in compact evidence; large recordings remain explicit external dependencies. No production sources or supplied references were changed.

## Prepared compound-cascade screen

The isolated Java candidate removes two redundant input-propagation passes inside the standard compound filter. The complete controller already prepares all inputs before advancing states and prepares them again afterward. The candidate advances the compound child states from those prepared inputs, preserving their order and original Euler methods. States, derivatives, scratch arrays, limiter equations, all ordinary controller preparation, network iterations and recording remain intact.

Qualification requires the exact standard model/child/block graph, no aliased nodes or internal cascade states, and live NoLimit types. Changed limiter types fall back immediately. Physical child parameters and externally restored states remain authoritative. The final guarded revision additionally excludes custom field-wrapper classes, whose getters can observe the intermediate compound inputs.

| Case | Control median, s | Candidate median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.868742 | 8.877094 | 0.009446 | 3/5 |
| 2 | 6.289325 | 6.336847 | 0.032272 | 3/5 |

The small, variable savings do not establish a useful end-to-end improvement. Both candidate arm medians are slightly slower; each candidate wins three of five pairs. The median of paired differences differs from the difference of arm medians. This prototype is not promoted, and the eight-second goal remains open.

Sixteen alternating warmups precede five order-alternating measured pairs per case on one pinned logical CPU. The Java sparse backend, 1/240-second network step, 1e-5 voltage-correction tolerance, one ST1 step and prior experimental network acceleration are unchanged. Fresh loading, preparation, initialization, simulation, full recording, closure, compilation and GC remain inside timing. All 52 timed-screen recordings match the retained baseline byte for byte.

| Case | Exercised models | Compound integration stages | Omitted child visits in two propagation passes |
| --- | ---: | ---: | ---: |
| 1 | 333 | 2,513,491 | 25,134,910 |
| 2 | 103 | 778,557 | 7,785,570 |

A guarded follow-up confirms five physical compound states in every exercised model. The raw input files each contain 435 PSS2A records, all with M=5 and N=1; raw counts include records not exercised by the dynamic run and are not runtime model counts. Thus the measured small benefit is not explained by empty cascades. Counts do not measure time saved.

Eight initial focused tests pass. They cover cascade orders including zero order, zero/nonzero time constants and steps, predictor/corrector stages, complete stored inputs/states/derivatives before output refresh, internal compound storage, scratch arrays, output history, external restoration, live child parameters, limiter changes, aliases, custom blocks, nonfinite inputs and replaced bindings. Special-value comparisons canonicalize NaNs; they are not a claim about NaN payload preservation. An initial harness variable-name collision failed compilation and ran no simulation; it is retained separately.

Further qualification found that a custom field-wrapper getter can observe intermediate compound inputs. A retained failing-before/passing-after regression compares its actual observation log. Exact-wrapper guards fix that difference. Ten tests then pass, including ordinary model-adapter observations. The guarded build also passes four complete Case 1/Case 2 recording checks with unchanged coverage and iterations. These four cold compatibility runs are not timing qualification of the guarded source. The timing table describes the preceding prototype; the extra guards run at model-plan compilation, but their performance is not assumed free.

In total, 56 complete recordings are retained and verified without dropping channels, samples or event-side duplicates. No production source or supplied reference changed. These parity checks establish no new independent-reference accuracy or six-case performance acceptance. Sources/classes, raw times, failing and passing tests, commands, affinity, input/runtime hashes, and recording hashes are retained. Large recordings remain explicit external dependencies of the compact archive.

## PSS endpoint eligibility cost audit

This instrumented diagnostic revisits the previously rejected prepared endpoint candidate. It measures its checks rather than removing them or changing controller behavior. It is not a new optimization or a production performance qualification.

Each case has eight alternating warmups and three alternating measurement pairs in one JVM pinned to one logical CPU. The quarter-cycle step, 1e-5 voltage-correction tolerance, single ST1 step, Java sparse solver, experimental network acceleration and complete recording are unchanged. All 28 recordings match the retained baseline byte for byte. Seven existing focused endpoint/preparation tests pass.

| Timed region (median seconds) | Case 1 | Case 2 |
| --- | ---: | ---: |
| Control ordinary output observations | 0.222763 | 0.063232 |
| Candidate remaining ordinary observations | 0.059820 | 0.012502 |
| Candidate freshly prepared observations | 0.057142 | 0.017349 |
| Candidate pre-step eligibility | 0.125202 | 0.043829 |
| Candidate post-step eligibility | 0.047844 | 0.014923 |
| Candidate zero-time predictor | 0.894904 | 0.279978 |

Pre-step eligibility includes the initial model eligibility check, exciter lookup, exact-class and observer checks. Post-step eligibility repeats the model/machine check after the zero-time predictor. These two intervals do not overlap. The predictor timer includes all original state/derivative work and preparation and is reported separately; the control predictor is not timed, so the rows cannot establish a total controller speedup. Ordinary-output timing includes all fixed-input observation calls, including local-controller paths. Fresh-output timing covers the candidate replacement calls. Per-region counts are retained in cost-summary.json.

Each interval includes a clock read and some instrumentation cost. The report also retains 100,000 empty timer pairs per run as a clock-cost indicator, but does not subtract that measurement: instrumentation changes inlining, cache behavior and optimization, so subtraction would not recover exact uninstrumented costs. Complete timings include instrumentation, status aggregation and calibration and are not an updated performance baseline. Independent region medians need not sum to a measured total.

The source is the previously screened endpoint candidate; its broader custom machine/controller-getter fallback limitation remains. This audit does not make it eligible for production. The initial build stopped at a harness-generation assertion before compilation or simulation; its files and failed log are retained. Sources, classes, focused tests, commands, raw statuses, affinity and runtime/input/output identities are retained. Supplied references and production source are unchanged. Independent-reference accuracy, Cases 3-6 and the full eight-second goal remain unresolved.

The audit narrows the next optimization target. In Case 1, the two instrumented eligibility regions total about 0.173 seconds, while all control ordinary output observations take about 0.223 seconds. In Case 2, those figures are about 0.059 and 0.063 seconds. These figures support eligibility overhead as a contributor to the rejected candidate's poor result, but do not isolate its uninstrumented causal effect. Removing checks alone offers a modest opportunity and does not explain the roughly 0.9-second Case 1 gap in recent uninstrumented screens. The zero-time predictor, including ordered preparation and state/derivative work, is the larger measured region (about 0.895 seconds in Case 1 and 0.280 in Case 2). Further controller work should target that region with equation and history parity, rather than repeating output-reuse variants or assuming an eligibility rewrite will meet the target. The figures do not justify omitting the predictor or its stored-derivative updates.

## Live algebraic predicate specialization

The candidate is not promoted: Case 1 saves a median 0.110492 seconds across paired runs (three wins in five), while Case 2 loses 0.090792 seconds (two wins). The results do not establish a dependable cross-case gain, and Case 1 remains above eight seconds. This test rules out treating a fixed live-classification predicate as a sufficient performance fix.

The isolated Java candidate replaces repeated generic node-classification loops with a fixed short-circuit predicate for the existing standard PSS2A graph. Every time-constant and compound-order read remains live and in the original order. It retains all preparation passes, state and derivative updates, limits, outputs, network iterations and recording. Custom classes or nonstandard preparation use the original predicate. Field replacement still fails the existing binding check.

This differs from the earlier rejected plan-time classification cache recorded in dynamic-device-profile-2026-10-21.md: no parameter values or algebraic classification results are cached here. The standard graph structure supplies typed getter call sites. It is also distinct from the previous zero-time derivative, input-dispatch, kernel-unrolling, endpoint-observation and compound-propagation experiments.

| Case | Control median, s | Candidate median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 9.0003972 | 8.8899057 | 0.1104915 | 3/5 |
| 2 | 6.2403531 | 6.3311450 | -0.0907919 | 2/5 |

Sixteen alternating warmups precede five order-alternating measured pairs per case in one JVM on one pinned logical CPU. Quarter-cycle steps, 1e-5 voltage-correction tolerance, one ST1 step, Java sparse solving and existing experimental network acceleration remain unchanged. Complete timing includes fresh loading/preparation, initialization, simulation, full recording, writer closure, compilation and GC. Independent arm medians and the median of paired savings are different statistics. No timer was inserted into the classification predicate.

All 52 complete recordings match the retained baseline byte for byte, including full-network output and event-side duplicates. Network iterations remain 18,340 and 20,202. The candidate reports 333 enabled model plans in Case 1 and 103 in Case 2. These are plan counts, not counts of predicate calls.

Seven focused tests pass: three existing preparation tests and four new tests covering every live time constant, compound zero order, zero/nonzero predictors and correctors, externally restored state, custom getter fallback and short-circuit read counts, special values, and stale-binding rejection. Stored inputs/states/derivatives, compound internal values, predictor history and scratch arrays are compared before output refresh and afterward. Special-value comparisons canonicalize NaNs rather than asserting payload-bit preservation.

This screening candidate is not promoted by the evidence package. Production source and supplied references are unchanged. Recording parity establishes no new independent-reference accuracy, and Cases 3-6 remain outside this timing screen. The complete eight-second objective remains open. Sources/classes, tests, commands, raw timings, affinity, runtime/input identities and recording hashes are retained. Large recordings remain explicit external dependencies.

## Current-assembly measurement resolution

A [sampled per-bus timer audit](current-assembly-components-2026-10-22.md) preserves all 30 complete Case 1/2 recordings and original iteration counts, but exposes significant timer overhead. Its fine-grained intervals are not reliable exclusive uninstrumented costs and must not be extrapolated into a speedup claim. No production change is promoted. The next attribution step should use lower-perturbation execution sampling; the broader accuracy and eight-second objective remain open.

## Execution sampling and immutable voltage sensing

The [execution-sampling profile](java-current-execution-profile-2026-10-22.md) preserves all 30 recordings and retains the raw flight recordings. Sampled leaf locations are not exclusive method costs. A separate [immutable-voltage magnitude screen](cml-voltage-magnitude-2026-10-22.md) checks a smaller controller-input opportunity with four focused tests and 52 complete recording comparisons. Neither evidence package itself promotes a production change or establishes independent-reference accuracy; the eight-second goal remains open.

## Renewable startup localization

The [stored-state and local-stability diagnostic](renewable-startup-local-stability-2026-10-22.md) reproduces Case 3/4 early failures without changing recorded prefixes and identifies unstable isolated discrete feedback modes at the quarter-cycle step. Eight/sixteen local renewable updates complete the fault-window probes but retain large supplied-reference errors. No production default or performance qualification follows; the full eight-second objective remains open.

## Case 3 renewable boundary replay

The [conditional terminal-voltage replay](case3-renewable-boundary-replay-2026-10-22.md) isolates a strong initial recovery-selector sensitivity across all 290 converters. A selector-only coupled quarter-cycle probe avoids the early failure but retains large reference errors. Finer local replay steps also worsen some outputs, so extra substeps are not promoted as an accuracy fix. Six replay runs, one coupled short run and their complete observations are retained; no production change or new timing claim follows.

## Local-update input and limiter history

The [Case 3 stage audit](case3-replay-step-boundary-2026-10-22.md) separates held-input and interpolated-input substeps. Both alter limiter-command timing and retained two-step history; held voltage reduces but does not remove the mismatch. Stored rate equations match exactly at the two observed devices, and the control recording is unchanged. Four new full-fleet conditional replays and a comparison figure are retained. No production fix or performance acceptance follows from this diagnostic.
