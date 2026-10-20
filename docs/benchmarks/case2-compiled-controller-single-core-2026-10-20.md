# Case 2 compiled controller experiments

The target remains **10 seconds for ten simulated seconds**, on **one CPU core**,
with **1e-5 maximum-component voltage tolerance** and **1/240-second steps**.
Two clean fresh-process measurements of the combined experimental candidate take
**15.1432 and 16.7642 seconds**;
their median is **15.9537 seconds**. The target is not achieved and production
code remains unchanged.

## Measurements

| Candidate | Integration seconds | Network seconds | Runner elapsed seconds |
| --- | ---: | ---: | ---: |
| jvm-c2 | 16.8467 | 7.9487 | 26.7180 |
| jvm-c2-columns | 16.3014 | 7.5707 | 26.4278 |
| global-st1-columns | 15.8670 | 7.5690 | 25.7227 |
| global-st1-columns-repeat | 15.3490 | 7.4295 | 25.1246 |
| pss-settled-child | 15.3000 | 7.3480 | 25.3906 |
| st1-typed-kernel-repeat | 15.1432 | 7.3100 | 25.7178 |
| st1-typed-kernel-repeat2 | 16.7642 | 8.0507 | 27.1902 |

Every listed run performs 2,400 steps, 4,800 network calls and 38,819 network
iterations. Process affinity is checked before and after execution with mask 1;
the JVM reports one processor and zero simulation worker threads. Integration
timing excludes initialization and ordinary trace serialization. Runner elapsed
time includes initialization and output work, but excludes Python preparation and
Java compilation. No warmup simulation is run. Full-channel diagnostics and
profiled runs are excluded from the table. Small individual timing differences
are within the observed variation and are not established independent speedups.

The JVM experiments use C2 compilation without tiered compilation. The columns
variants select the sparse library's existing column factorization/solve kernel.
The factorization is still reused; network iteration tolerance and lifecycle are
unchanged. Exact flags and dependency hashes are retained in private evidence.

## Controller changes

The ST1 global predictor at zero time now uses the existing direct evaluator.
The new specialized ST1 evaluator binds the standard block objects once, builds
separate control and feedback schedules, and dispatches typed block outputs and
zero-time derivatives. It preserves arithmetic order, existing block state storage,
non-windup kernels, and both post-advance preparation passes. Numerical parameters
remain live. An unexpected block class or a changed bound field is rejected by
this experimental fixed-structure path.

The PSS2A parent now reads the already-prepared output of a recognized forward
child. It avoids repeating that same child's input traversal solely to observe its
output, while preserving the finite-value check. Unrecognized child order retains
the previous evaluation path.

These are steps toward a compiled controller runtime, not a completed general
compiler. General structural mutation, custom subclasses and expression changes
still require explicit invalidation or compatibility handling before production
integration. The standard model equations and physical integration stages have
not been changed.

## Verification

Each ST1 and PSS2A focused check compares 1,000 stages, with two output observations
per stage. Predictor/corrector and positive/zero-time stages are covered. A stronger
ST1 check also varies live selector and limiter inputs and compares stored
derivatives. Every observed value is finite and the two implementations produce
identical state, derivative, input and output bytes in that check.

The complete export checks **2,405 samples × 4,810 channels = 11,568,050 values**.
Every compressed byte and end-of-file matches the preceding column-kernel control.
All channel samples and event sides are retained. That control's independently
parsed comparison against the earlier rectangular control has maximum differences
of 5.418e-14 pu bus voltage, 3.411e-11 degrees rotor angle, 1.200e-9 MW active power
and 5.624e-10 Mvar reactive power. These checks establish numerical nonregression;
they do not establish acceptance against supplied references or resolve prefault
initialization motion.

Two ordinary full exports hit the free-space reserve. Their incomplete generated
traces were removed after checking retention manifests; failure logs remain.
Completed generated text files were compressed with unchanged logical SHA-256
hashes. The successful streaming check compares every output byte before creating
an immutable storage link to the retained identical trace. Separate tests confirm
that changed, truncated and extended streams are rejected without retaining an
output link. Supplied references are unchanged.

## Remaining work

The latest diagnostic profile uses C2 and detailed compiled-code sampling. Its
integration stacks identify repeated network-participant discovery, bus/branch
traversal and sparse triangular solves as remaining targets. Iterator traversal
also appears in ordinary output recording, which is outside the integration
timing; total sample percentages must not be treated as integration percentages.
Compiler statistics increase by 2.396 seconds during the recorded loop, which
includes output work. The profile and its source hashes are retained in the
supplemental private `profile-manifest.json` beside the main evidence manifest.

Approximately 37.3% of the candidate integration time must still
be removed. Network work ranges from 7.3–8.1 seconds in these runs, so controller improvements
alone have limited room to meet the target. Further profiling and production
integration remain necessary. Full export and storage checks are diagnostic work,
not qualifying performance measurements.

The private evidence manifest is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-20-case2-compiled-controller/manifest.json`.
It retains the combined twelve-source candidate, producers, focused checks,
affinity records, runtime hashes and full comparison evidence. Linked trace files
must remain immutable.
