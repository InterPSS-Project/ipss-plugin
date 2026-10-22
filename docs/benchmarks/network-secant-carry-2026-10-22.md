# Network secant-history carry experiment

The seed-only candidate is not promoted. It reduces network iterations by 4.0% in Case 1 and 1.6% in Case 2, but increases worst reference rotor-angle and power errors. A warmed timing sweep was not run: these numerical/accuracy results do not justify advancing this version as an accuracy-preserving optimization. The eight-second objective remains open.

The pure Java prototype retains only secant pairs formed within a successful network solve. It clears the previous raw residual/output link at the next solve boundary, so it never forms a secant across two different fixed-point maps. The final variant uses old columns for one initial proposal, then discards them and builds subsequent history entirely within the current solve. Reinitialization, event boundaries, matrix identity or dirty state, changed bus identity/order/status/slot mapping, failures, nonfinite values and failed eligibility clear or invalidate retained history. Ineligible mixing still follows the existing renewable-correction guard. This is an experimental numerical method, not a qualified general callback contract.

All stopping decisions retain the original fresh, unmixed complex-voltage correction check at 1e-5 pu. Voltage-dependent injections refresh on every iteration. Sparse factorization reuse, controller integration, quarter-cycle steps, one ST1 step and complete recording remain unchanged. Retained-history coefficient checks use consistent Euclidean norms for the secant and current residual; the original coefficient bound remains for within-solve history. Every proposal retains the finite-value and four-times-maximum-residual displacement bounds. These proposal checks do not relax the stopping tolerance.

## Iteration diagnostics

| Variant | Case 1 iterations | Case 2 iterations |
| --- | ---: | ---: |
| Control | 18,340 | 20,202 |
| Raw coefficient bound, history retained | 18,318 | 20,199 |
| Mixed-norm scaling (superseded) | 18,209 | 20,180 |
| Consistent-norm scaling, history retained | 17,876 | 20,100 |
| Consistent-norm scaling, initial proposal only | 17,598 | 19,882 |

The raw-bound version accepted only 18 and 2 carried proposals. The intermediate mixed-norm version compared a secant vector norm with the maximum residual component; a retained 64-coordinate regression fails before the consistent-norm correction and passes afterward. The final variant accepts 2,400 and 2,397 carried proposals. Its largest accepted fresh corrections are 9.989289954e-06, 9.999168925e-06 pu. Retained columns do not guarantee useful acceleration: iteration savings remain modest despite frequent proposal acceptance.

## Supplied-reference comparison

Each variant has one cold control and one cold candidate run for each case, all pinned to one logical CPU. They are execution/convergence audits, not warmed performance measurements. Controls reproduce the frozen baseline byte for byte. The authoritative current plans and runner both apply the bus-6307 event in both cases; the event-identity guard passes before scoring.

The final variant and its control are compared against all 5,191 matched Case 1 channels and 4,501 Case 2 channels in the voltage, bus-angle, rotor-angle and machine active/reactive-power families. Every nonnegative actual sample is scored. Reference interpolation stays within event segments, matching both sides of duplicated fault-application/clearing timestamps. No angle offset is fitted. Voltage/power normalization uses each reference channel's full ten-second range. Earlier candidate variants receive convergence audits only, not independent-reference accuracy claims.

| Worst full-run reference RMSE | Case 1 control | Case 1 seed-only | Case 2 control | Case 2 seed-only |
| --- | ---: | ---: | ---: | ---: |
| Rotor angle, degrees | 9.9213065 | 10.036527 | 91.970523 | 92.075484 |
| Bus voltage, pu | 0.019194505 | 0.019193154 | 0.045226986 | 0.045270923 |
| Active power, MW | 56.877771 | 59.057666 | 112.78126 | 112.7997 |
| Reactive power, Mvar | 45.089514 | 45.236903 | 127.82826 | 128.73968 |

Worst rotor-angle RMSE between the candidate and internal control is 0.268278 degrees in Case 1 and 0.754125 degrees in Case 2. Those internal differences are diagnostic only; the supplied references remain the accuracy authority. Both control and candidate already fail the independent-reference contract, so maintaining the network stopping tolerance alone cannot establish dynamic accuracy. Full per-channel, prefault and fault/postfault errors and pass counts are retained.

Thirteen final focused tests pass, covering independent modes, rank-deficient history, reset/nonfinite/ineligible behavior, no cross-map differences, changed residual scale, multi-coordinate norm consistency, rejected proposals, fresh stopping checks, dirty matrices, event reset, solve failure and bus deactivation/reactivation. The norm correction also has a separate failing-before/passing-after regression. General custom callback qualification, model-data mutations not reflected in network lifecycle, and six-case acceptance remain open. An initial test-fixture compile failure used the wrong bus-status setter and ran no simulation; its source and logs are retained.

Sixteen full recordings across the four audited variants are preserved. Only verified duplicate generated controls share storage; supplied references and retained baselines are unchanged. Compact evidence contains source/classes, tests, commands, raw status, all-channel errors, affinity, input/runtime/reference identities and recording hashes. Large recordings remain explicit external dependencies. No production source was changed.
