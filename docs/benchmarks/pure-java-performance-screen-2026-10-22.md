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
