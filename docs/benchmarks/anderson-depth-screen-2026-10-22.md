# Network history depth screen

The existing pure Java acceleration prototype was exercised at depths one, two, three and four. History still resets at each network solve, and every acceptance decision uses the original unaccelerated voltage correction at 1e-5 pu. No model equations, integration stages, quarter-cycle interval, ST1 step count, recording channels or sample cadence were changed. Production defaults remain unchanged.

| Case | Depth 1 iterations | Depth 2 | Depth 3 | Depth 4 |
| --- | ---: | ---: | ---: | ---: |
| 1 | 21,510 | 19,288 | 18,340 | 18,117 |
| 2 | 25,559 | 22,019 | 20,202 | 20,112 |

Depth four removes few iterations relative to three. Depth two was selected for warmed timing to test whether cheaper history processing compensates for its additional iterations. The initial depth screen is cold diagnostic evidence; its elapsed times are not used as performance qualification.

| Case | Depth 3 median (s) | Depth 2 median (s) | Median paired saving (s) | Depth 2 faster |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.980163 | 8.963578 | -0.178737 | 2/5 |
| 2 | 6.341496 | 6.369775 | -0.071167 | 2/5 |

Each case runs sixteen alternating warmups and five measured pairs with alternating order within pairs, in one JVM pinned to one logical CPU. Complete-case timing includes fresh parsing and preparation, initialization, ten simulated seconds, full recording, writer closure, and compilation/GC during the timed interval. All 52 timed recordings match their own audited depth trajectory byte for byte. Repeated generated recordings are hard-linked only after equality checks. Depth-three controls also reproduce the retained baseline.

| Case | Depth | Worst full-run rotor RMSE (degrees) | Worst fault/postfault voltage NRMAE |
| --- | ---: | ---: | ---: |
| 1 | 1 | 10.734593 | 0.381919 |
| 1 | 2 | 8.118979 | 0.381635 |
| 1 | 3 | 9.921307 | 0.381909 |
| 1 | 4 | 9.593580 | 0.381700 |
| 2 | 1 | 91.578518 | 0.275212 |
| 2 | 2 | 91.544939 | 0.275406 |
| 2 | 3 | 91.970524 | 0.275528 |
| 2 | 4 | 91.617351 | 0.275408 |

Reference scoring checks the bus-6307 event identity first and compares all 5,191 matching channels in Case 1 and 4,501 in Case 2. It retains event-pre/event-post duplicates and every nonnegative actual endpoint, interpolating reference values only within event segments. Negative startup history remains in the raw recordings. No offset or time shift is fitted. Voltage NRMAE uses mean absolute error divided by the full ten-second reference-channel range; rotor gates use the current absolute-error contract. P/Q and bus-angle errors are diagnostic. Direct bus frequency is absent from these recordings, so the complete acceptance contract remains untested even apart from the failing voltage/rotor gates.

Depth two improves the largest rotor RMSE in both cases, but all tested depths still fail independent-reference accuracy. The experiment is not promoted. The eight-second goal remains open across the original six cases; this screen qualifies neither Cases 3–6 nor a new production configuration.

The saved baseline was rechecked before the screen: all 3,980 selected directory class files matched the prior runtime identities. The compact archive retains commands, harness sources/classes, full per-channel error tables, raw timings, affinity checks and recording/input hashes. Full recordings and supplied references remain explicit dependencies; supplied files are unchanged.
