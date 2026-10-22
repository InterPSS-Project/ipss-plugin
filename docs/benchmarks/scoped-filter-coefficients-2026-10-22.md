# Stage-scoped filter coefficient screen

This pure Java prototype computes the standard filter coefficients `k * (1 - t1 / t2)` and `k * t1` once at controller-stage entry. Numerical reads inside that stage reuse the values without parameter-bit comparisons. Scope exit restores ordinary live evaluation. Parameters changed between stages remain authoritative, and arithmetic order is unchanged. Unlike the earlier per-read cache, this uses an explicit lifetime within a complete controller step. It removes no preparation, derivative, state, limiter, output or network work.

Scope compilation requires the exact standard model, child controllers, blocks and wrapper classes, with no aliasing among top-level and compound states. Each stage checks live NoLimit types and absence of model/child notification adapters before entering. Other graphs retain the ordinary path. All scopes close in a finally block, including partial entry and stale-binding failures. The prototype public block scope API requires callers to exclude parameter mutation and custom callbacks until exit; it is not a general safe wrapper around arbitrary user code and is not promoted as a production API.

| Controller instances per arm | Stages per batch | Control median, ms | Scoped median, ms | Median paired saving, ms | Faster pairs |
| --- | ---: | ---: | ---: | ---: | ---: |
| 16 | 80,000 | 23.8080 | 23.5180 | 0.1842 | 6/7 |
| 333 | 79,920 | 34.0916 | 33.3387 | 0.7253 | 7/7 |

Both component fixtures retain identical checksums across all repetitions. Each has 32 alternating warmups and seven order-alternating measured pairs in one JVM pinned to one logical CPU. The stage includes input-signal assignment, the entire controller step, output observation and checksum accumulation. States and derivatives are reset before each timed batch. Both arms use the candidate-capable implementation with scope use selected at plan construction.

The larger fixture matches the active Case 1 PSS2A instance count, but it uses small-system model fixtures with varied ramp-filter time constants and deterministic signals. It does not recreate the actual fleet parameters, measurement sampling, machine/exciter interaction or full network working set. Its roughly 2% controller-only reduction is insufficient evidence for the eight-second complete-run target; the result is not extrapolated into a full-simulation speedup.

Thirteen focused tests pass. They compare stored inputs, states, derivatives, scratch arrays, compound internal states and lead-lag predictor history before and after output observation; vary cascade order, zero/nonzero time constants and steps, inputs, live parameters and external state restoration; and exercise limiter changes, aliases, custom compound/wrapper behavior, model adapters, nonfinite values and binding replacement. Dedicated lifecycle cases verify cleanup after failure, partial entry when another owner holds a scope, changed parameters on reentry and rejection of custom or limited blocks. NaNs are canonicalized rather than payload-bit compared.

One complete candidate run for each of Cases 1 and 2 matches the retained baseline recording byte for byte, including all channels and event-side duplicates. Both use a pinned logical CPU, Java sparse solving, 1/240-second steps, 1e-5 voltage-correction tolerance, one ST1 step and the existing network acceleration. These are cold compatibility checks, not warmed performance qualification. Their startup timing and printed warmup label are not used as benchmark results.

| Case | Models using scopes | Scoped controller stages | Network iterations |
| --- | ---: | ---: | ---: |
| 1 | 333 | 2,513,491 | 18,340 |
| 2 | 103 | 778,557 | 20,202 |

The candidate remains isolated. No warmed full-simulation sweep or production change follows from the small component benefit. Baseline-recording equality does not establish independent-reference accuracy or broader-case qualification. The full eight-second objective remains open.

Sources/classes, focused tests, commands, raw component measurements, scope counts, affinity, runtime/input identities and recording hashes are retained. Complete recordings remain explicit external dependencies. Supplied references and unrelated production edits are unchanged.
