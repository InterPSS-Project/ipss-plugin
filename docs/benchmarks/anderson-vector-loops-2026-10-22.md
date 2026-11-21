# Network acceleration vector-loop screen

The isolated pure Java candidate fuses history-column copying with the original ordered sum of squares, reuses that exact norm when the column has no preceding orthogonalization, and fuses normalization with the ordered right-hand-side dot product. A fixed rank-one/two/three correction path hoists history-array and coefficient lookups while preserving the original additions, including the initial zero. Higher ranks retain the original correction loop. All reorthogonalization, rank thresholds, history rotation, coefficient/rejection guards and convergence tests remain unchanged. This is distinct from the earlier QR-prefix reuse experiment: every required column is still recomputed.

| Case | Control median, s | Candidate median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.9009285 | 8.9614827 | -0.0999365 | 0/5 |
| 2 | 6.2121489 | 6.3023550 | -0.1513701 | 2/5 |

The candidate is rejected as a performance improvement: it loses all five Case 1 pairs (median loss 0.099937 seconds) and three Case 2 pairs (median loss 0.151370 seconds). Both candidate arm medians are slower. The synthetic component improvement does not carry through to this complete-run workload. These measurements do not isolate whether the difference comes from the distribution of accepted histories, compilation/inlining, or other runtime effects, so no causal attribution is made.

The candidate is retained as an isolated screen without a production change. Raw timings and paired differences remain in the evidence package. Independent arm medians and a median paired difference are different statistics. These measurements do not establish the complete eight-second objective or broader-case independent-reference accuracy.

Sixteen alternating warmup simulations precede five order-alternating measured pairs per case in one JVM pinned to one logical CPU. Complete timing includes fresh loading/preparation, initialization, simulation, full recording, writer closure, compilation and garbage collection within the timed case. Quarter-cycle steps, 1e-5 network voltage-correction tolerance, one ST1 step and Java sparse solving remain selected. The existing network acceleration is enabled in both arms. All 52 complete recordings match the retained baseline byte for byte, including event-side duplicates; the harness checks the actual mixer mode in every run.

The focused comparison makes 64,000 calls across vector lengths 1, 2, 17 and 255 and depths 1 through 8. Random, decaying/collinear, signed-zero, overflow and smooth histories include resets, ineligible calls, nonfinite input and nonfinite residual bounds. After every call it compares accept/reject status, history flags, output, previous vectors, all difference and QR storage, right-hand sides, coefficients and column order against an independent copy of the original implementation. NaNs are canonicalized; NaN payload identity is not asserted. The test output retains the old unused reused-columns label at zero; no prefix reuse is part of this candidate.

The component benchmark uses 4,000 scalar entries, depth three, 2,000 calls per batch, resets every seven calls, 32 alternating warmups and seven alternating measured pairs. All batches have the same checksum and 286 accepted proposals. The smooth synthetic sequence therefore does not represent the complete distribution of accepted ranks and histories in a network solve. Control/candidate median batch times are 0.0547208 / 0.0450283 seconds. Component savings must not be treated as a full-simulation speedup.

Sources/classes, commands, focused comparison output, raw measurements, affinity, runtime/input identities and recording hashes are retained. Full recordings remain explicit external dependencies. Production source and supplied references are unchanged.
