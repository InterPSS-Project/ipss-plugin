# Current-assembly instrumentation resolution audit

This diagnostic does not establish a new optimization or reliable exclusive component costs. It shows that per-bus timing is too intrusive for the short operations in the current-assembly loop. No production source or default changes.

The isolated overlay samples every 64th network-equation call, preserving bus traversal, generator/device calls, live static-load calculations and all numerical operations. Twelve complete warmups precede three diagnostic runs per case; sampling offsets rotate through 0, 21 and 42. The same process uses one pinned logical CPU, pure Java sparse solving, 1/240-second steps, 1e-5 voltage-correction tolerance, one ST1 local step and the existing acceleration baseline. Fresh loading/preparation, initialization, full recording and writer closure remain in the complete-case timer.

| Raw sampled interval, milliseconds | Case 1 | Case 2 |
| --- | ---: | ---: |
| Entire sampled assembly loop | 84.6647 | 92.6586 |
| Generator/script section, including empty checks | 20.0713 | 19.6041 |
| Other-device section, including empty checks | 11.4815 | 12.6388 |
| Static-load section, including empty checks | 17.9403 | 19.7415 |
| Empty-clock-pair scale for one section | 10.6598 | 12.2412 |

These are sums only for sampled calls, followed by medians across three runs; they are not full-run seconds. The empty-clock-pair scale multiplies a separate 100,000-pair calibration by the section timer count. It is an indicator of measurement resolution, not a correction that can safely be subtracted. The timer-instrumented methods can have different inlining and optimization, and deterministic sampling can alias network phases. Independent medians need not add.

The other-device interval is close to the clock-cost scale. Generator and static-load intervals contain additional work, but these data do not support assigning precise uninstrumented seconds or ranking small optimizations. In particular, extrapolating the entire sampled interval by 64 would multiply instrumentation overhead and give a misleading assembly cost. The earlier coarse network timing remains the appropriate evidence for the roughly one-second assembly region. A lower-perturbation execution-sampling profile should precede another fine-grained rewrite.

All 30 complete recordings match the retained baseline byte for byte, including every full-network channel, sample and event-side duplicate. Network iteration counts remain 18,340 and 20,202. This audit starts before loading and includes initialization: equation-call counts are two above the outer iteration count, unlike the earlier post-initialization audit, whose count is one above it. The sampled section visit counts independently match 2,000 buses per sampled equation call.

No new independent-reference accuracy or broad-case performance qualification is established. Cases 3-6, accuracy recovery and the eight-second objective remain unresolved. The failed measurement-resolution hypothesis is retained so it is not repeated or mistaken for a performance result. Sources/classes, commands, raw counters, clock calibrations, affinity and runtime/input/output identities are retained; full recordings remain external dependencies. Supplied references are preserved.
