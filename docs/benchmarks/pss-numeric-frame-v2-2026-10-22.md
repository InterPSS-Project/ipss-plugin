# PSS2A numerical-frame final-preparation screen

The isolated pure Java prototype loads standard PSS2A physical states and live parameters into reusable primitive arrays at each integration stage. It evaluates the input equations and first-order updates in those arrays, then restores the authoritative block inputs, states and predictor derivatives. The terminal lead-lag retains its existing integration method and hidden predictor history; final input preparation also runs inside the numerical frame before publishing the resulting block state. The standard compound block then refreshes its internal inputs from those published states. This second version removes the return to the generic graph for final preparation. No state or parameter cache survives across stages.

Eligibility requires the exact model, child controllers, blocks and wrappers, unique block ownership, live unlimited first-order blocks, and no model/child notification adapters. Custom classes, aliased graphs and limiter changes use the original evaluator. Replaced bindings take the original ordered failure path. The model-defined optional washout bypass and the ordinary zero-time washout remain distinct. Arithmetic order, signed zero and predictor/corrector scratch ordering are preserved.

Twelve focused tests pass. They compare all stored top-level and compound inputs, states, derivatives, terminal predictor history and scratch arrays before and after output observation. Coverage includes cascade orders, zero and nonzero steps, negative/tiny/zero/nonfinite time constants, external restoration, live parameter and limiter changes, aliases, custom blocks and wrappers, notification adapters, stale bindings and repeated transitions between the frame and fallback. NaN payloads are canonicalized.

| Component fleet | Baseline median, ms | Frame median, ms | Faster pairs |
| --- | ---: | ---: | ---: |
| 16 | 23.7762 | 14.2754 | 7/7 |
| 333 | 35.1525 | 26.8627 | 7/7 |

Component fixtures run 32 alternating warmup batches and seven order-alternating measured pairs on one pinned logical CPU. Each batch includes signal assignment, full controller stepping, observation and checksum accumulation. Both arms have identical checksums. The 333-controller fixture matches Case 1 instance count but uses varied small-system parameters and deterministic inputs; its saving is not a full-run performance claim.

| Complete case | Baseline median, s | Frame median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.835110 | 8.596508 | 0.238602 | 4/5 |
| 2 | 6.653322 | 6.519984 | 0.304158 | 3/5 |

Each complete-case comparison uses 16 alternating warmup runs followed by five order-alternating measured pairs in one JVM. Affinity is verified as one logical CPU before and after execution. Runs use Java sparse solving, 1/240-second network steps, 1e-5 pu voltage-correction tolerance, one ST1 step and the existing network acceleration. Timing includes fresh input parsing, power-flow preparation, initialization, startup history, simulation, all recording and writer closure, including compilation and GC within that interval. Recording comparisons and hard-link deduplication occur outside the timed interval.

All 52 warmed-sweep recordings and two initial candidate recordings match the retained baseline byte for byte: full-network binary, selected CSV and channel metadata, including event-side duplicates. Network iterations are unchanged.

| Case | Models using frame | Controller stages using frame | Network iterations |
| --- | ---: | ---: | ---: |
| 1 | 333 | 2,513,491 | 18,340 |
| 2 | 103 | 778,557 | 20,202 |

The second version is integrated as an opt-in controller implementation: set `-Dipss.experiment.pssNumericFrame=true` before constructing the controller plan. It remains disabled by default. Case 1 improves in four of five measured pairs; Case 2 improves in three of five, with overlapping ranges. This is a modest measured improvement, not a consistently faster result in every repetition. The eight-second complete-run target is not achieved across the requested scope. Equality with the retained executable preserves its behavior; it does not resolve the outstanding independent-reference accuracy failures or qualify Cases 3-6. The original scope remains open.

Repository-source validation passes 15 tests with the option disabled and the same 15 with it enabled, including the existing lead-lag and small-system independent conformance tests. Five runtime classes rebuilt without debug metadata are byte-for-byte identical to the measured second prototype. An initial attempt to include the older compiled-stage test could not compile because it references APIs absent from the current branch; that test is not claimed as passed. Validation uses isolated compilation against the retained matching runtime, not a full Maven reactor build. The private archive retains exact source/class files, tests, scripts, commands, raw timings, coverage counts, CPU affinity, runtime identities and recording hashes. Complete recordings remain explicit external dependencies. Supplied references and unrelated production edits are unchanged. Only the opt-in PSS2A frame, its dispatch and its focused tests are integrated.
