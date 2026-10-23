# PSS2A numerical-frame screen

The isolated pure Java prototype loads standard PSS2A physical states and live parameters into reusable primitive arrays at each integration stage. It evaluates the input equations and first-order updates in those arrays, then restores the authoritative block inputs, states and predictor derivatives. The terminal lead-lag retains its existing integration method and hidden predictor history; the existing final preparation publishes consistent output inputs. No state or parameter cache survives across stages.

Eligibility requires the exact model, child controllers, blocks and wrappers, unique block ownership, live unlimited first-order blocks, and no model/child notification adapters. Custom classes, aliased graphs and limiter changes use the original evaluator. Replaced bindings take the original ordered failure path. The model-defined optional washout bypass and the ordinary zero-time washout remain distinct. Arithmetic order, signed zero and predictor/corrector scratch ordering are preserved.

Twelve focused tests pass. They compare all stored top-level and compound inputs, states, derivatives, terminal predictor history and scratch arrays before and after output observation. Coverage includes cascade orders, zero and nonzero steps, negative/tiny/zero/nonfinite time constants, external restoration, live parameter and limiter changes, aliases, custom blocks and wrappers, notification adapters, stale bindings and repeated transitions between the frame and fallback. NaN payloads are canonicalized.

| Component fleet | Baseline median, ms | Frame median, ms | Faster pairs |
| --- | ---: | ---: | ---: |
| 16 | 23.8831 | 15.3149 | 7/7 |
| 333 | 34.8006 | 27.2884 | 7/7 |

Component fixtures run 32 alternating warmup batches and seven order-alternating measured pairs on one pinned logical CPU. Each batch includes signal assignment, full controller stepping, observation and checksum accumulation. Both arms have identical checksums. The 333-controller fixture matches Case 1 instance count but uses varied small-system parameters and deterministic inputs; its saving is not a full-run performance claim.

| Complete case | Baseline median, s | Frame median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.842679 | 8.907306 | -0.064627 | 1/5 |
| 2 | 6.398558 | 6.270345 | 0.128213 | 3/5 |

Each complete-case comparison uses 16 alternating warmup runs followed by five order-alternating measured pairs in one JVM. Affinity is verified as one logical CPU before and after execution. Runs use Java sparse solving, 1/240-second network steps, 1e-5 pu voltage-correction tolerance, one ST1 step and the existing network acceleration. Timing includes fresh input parsing, power-flow preparation, initialization, startup history, simulation, all recording and writer closure, including compilation and GC within that interval. Recording comparisons and hard-link deduplication occur outside the timed interval.

All 52 warmed-sweep recordings and two initial candidate recordings match the retained baseline byte for byte: full-network binary, selected CSV and channel metadata, including event-side duplicates. Network iterations are unchanged.

| Case | Models using frame | Controller stages using frame | Network iterations |
| --- | ---: | ---: | ---: |
| 1 | 333 | 2,513,491 | 18,340 |
| 2 | 103 | 778,557 | 20,202 |

This version is not promoted: Case 1 is slower in four of five pairs. Case 2's smaller median does not establish a benefit across cases. The eight-second complete-run target is not achieved across the requested scope. Equality with the retained executable preserves its behavior; it does not resolve the outstanding independent-reference accuracy failures or qualify Cases 3–6. The original scope remains open.

The private archive retains exact source/class files, tests, scripts, commands, raw timings, coverage counts, CPU affinity, runtime identities and recording hashes. Complete recordings remain explicit external dependencies. Supplied references and unrelated production edits are unchanged.
