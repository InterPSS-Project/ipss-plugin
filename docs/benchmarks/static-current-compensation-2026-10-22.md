# Scalar static-load compensation screen

This isolated pure Java candidate computes the stamped-admittance compensation in scalar locals and passes those values directly to the existing current-division helper. It avoids a temporary compensation object for ordinary finite values. It retains arithmetic order, one live static-load calculation, the stored total-load value, voltage-dependent injections, network iterations and all recording. Custom bus/complex classes and exceptional arithmetic retain the original operations. No controller, limiter, integrator or tolerance changes.

| Case | Control median, s | Candidate median, s | Median paired saving, s | Faster pairs |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.9129126 | 9.0461443 | -0.1332317 | 2/5 |
| 2 | 6.2341738 | 6.1711277 | -0.0083114 | 2/5 |

Case 1 loses 0.133232 seconds at the median paired difference and remains above eight seconds. Case 2 has a lower independent candidate median, but its median paired difference is a loss of 0.008311 seconds; the candidate wins only two of five pairs in each case. The small component improvement does not establish a dependable complete-run improvement. Avoiding this one temporary compensation object does not close the performance gap.

The candidate is not promoted by this screening report. Raw pair differences and timings remain in the evidence package; an arm median and a median paired difference are different statistics. The full eight-second objective, including broader-case accuracy and performance, remains open.

Each case has sixteen alternating warmup simulations and five order-alternating measured pairs in one JVM pinned to one logical CPU. Complete-case timing includes fresh loading/preparation, initialization, simulation, full recording, writer closure, compilation and garbage collection inside the timed case. Quarter-cycle steps, 1e-5 network voltage-correction tolerance, one ST1 step, Java sparse solving and the existing network acceleration remain selected. All 52 complete recordings match the retained baseline byte for byte, including event-side duplicates. The harness verifies the selected compensation mode on every imported bus.

The focused probe performs 50,973 exact-value and stored-load comparisons, including random ZIP combinations, threshold neighbors, signed zero, subnormal and large values, infinities/NaNs, both compensation settings, load-change factors, custom complex operations and custom bus dispatch. NaN comparison is canonical, not payload-bit equality. Component timing uses 512 load buses, 1,000 passes per batch, 32 warmups and seven alternating pairs; all checksums agree. Its median times are 0.0108393 seconds for control and 0.0103074 for the candidate. This component result is not a complete-run speedup.

The first compilation failed because the probe used an overly broad network interface; the casts were corrected. The first component timing fixture left static loads in the network matrix and bypassed the current-assembly path. Its zero-checksum timings are invalid and excluded. The corrected fixture disables that setting and asserts a nonzero finite checksum. Both failed/invalid attempts and corrected sources are retained.

Source/classes, commands, raw measurements, affinity, runtime/input identities and recording hashes are retained. Large recordings remain explicit external dependencies. Equal baseline recordings establish no new independent-reference accuracy. Production source and supplied references are unchanged.
