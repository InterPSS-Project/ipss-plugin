# Cases 4 and 5: current prefault checks

The same frozen numerical runtime used for the completed Case 2 comparison was
applied to the original Cases 4 and 5. Case 4 completes the prefault second.
Case 5 stops on a network-convergence failure after its recorded 0.771-second
endpoint. Both remain outside the prefault accuracy limits.

The original input files, computed converted operating points, 0.5 ms step,
two cold-history samples, I-P/Z-Q loads and ordinary model initialization are
retained. Dynamic network tolerance remains 1e-4 and the iteration ceiling is
500. Runtime classes and inputs are hash-checked before and after execution.
Concurrent diagnostic elapsed times are not production performance evidence.

## Complete supplied prefault coverage

All six original events contribute their physical observations. Only their
identical common prefault histories are combined; postfault trajectories remain
separate. Case 4 has 233 distinct physical channels and seven identical overlap
checks; Case 5 has 191 channels and 49 identical overlap checks. Each occurrence
uses its own event's full ten-second reference range.

The new scoring applies the voltage limit to supplied machine terminal-voltage
channels as well as explicitly named bus-voltage channels. Earlier diagnostic
comparisons reported terminal voltage without applying that acceptance check.
This closes a coverage gap without changing any tolerance or trajectory.

| Worst observed prefault metric | Case 4, through 1.0 s | Case 5, through 0.771 s |
|---|---:|---:|
| Bus-voltage NRMAE | 0.0386181 at bus 4081 | 0.0452449 at bus 6142 |
| Machine terminal-voltage NRMAE | 0.0404634 at 4052/1 | 0.0468321 at 6297/1 |
| Rotor maximum error, degrees | 12.2300813 at 2057/1 | 5.1027938 at 5382/1 |
| Generator-speed proxy NRMAE | 0.0458679 at 6073/1 | 0.0630106 at 7378/1 |

None of the 30 event-channel occurrences in any of these four groups passes
its prefault limit over the stated interval. The 30 machine occurrences cover
29 distinct machines in Case 4 and 23 in Case 5. The required limits remain
0.001 for voltage/frequency NRMAE and 0.02 degrees for prefault rotor maximum.
Generator speed remains a proxy, not direct bus-frequency evidence.

Across supplied bus and machine terminal voltages, the largest instantaneous
errors are 0.0434819 pu at bus 2067, 0.644 seconds, in Case 4 and 0.0268189 pu
at bus 6237, 0.771 seconds, in the Case 5 prefix. These peaks are distinct from
the channels with the largest normalized mean error.

## Initial agreement and error development

At time zero, after the two retained history steps, the largest supplied
voltage differences are 5.34214e-6 pu in Case 4 and 4.67869e-6 pu in Case 5.
The largest rotor differences are 0.000708966 and 0.00107063 degrees.
These small initial differences do not establish subsequent model correctness.

| Diagnostic prefix | Case 4 worst bus-voltage NRMAE | Case 5 worst bus-voltage NRMAE | Case 4 rotor maximum, degrees | Case 5 rotor maximum, degrees |
|---|---:|---:|---:|---:|
| Through 0.02 s | 0.000655382 | 0.000300812 | 0.000709405 | 0.00107164 |
| Through 0.10 s | 0.00414434 | 0.00266390 | 0.0183551 | 0.0604480 |

Thus accuracy differences develop well before the later Case 5 convergence
failure. Increasing that iteration ceiling alone would not correct the already
recorded earlier trajectory.

Stored first-history derivative access covers all 403 active devices in each
case: 290 converter bundles and 113 synchronous machines. Case 4 includes two
salient machines; Case 5 has only round-rotor machines. The inventories contain
7,268 and 7,269 reported rate entries, including aliases, with no coverage gaps.
All 93 active wind bundles' power-order startup rates agree with the entered
Pflag, initialized speed and time-constant equations; the largest residuals are
2.66e-15 and 1.78e-15. Other renewable rates are at most 2.67e-13 in absolute
value. Nonzero power-order startup rates are retained, not forced to equilibrium.

## Case 5 convergence failure and replay limitation

The Case 4 run performs 48,557 iterations over 2,002 network calls, with a
maximum of 52. Case 5 performs 45,995 iterations over 1,545 calls, including
the failed call. Its final attempted solve reaches 500 iterations with a
maximum voltage change of 0.109719 pu at bus 1046, versus the 1e-4 tolerance.

The recorder writes only after a successful integration step. Its last row,
at 0.771 seconds, belongs to the preceding converged solve. The failure occurs
on the next attempted step, whose solver clock still reads 0.771 seconds.
All 1,545 physical rows, including both cold-history rows, are retained and
compared; no failed network iterate is used as an accepted endpoint. The
separate optional renewable-state gzip lacks a closing marker after the
exception. It is preserved as an incomplete diagnostic and is not used for
acceptance metrics or claims of complete evolving-state coverage.

A three-converter replay at 1016/1, 1016/2 and 1046/1 uses the recorded local
voltage phasors and ordinary initialization. Initial states agree within
2.78e-17. Its two 1016 active-power channels agree within 1.14e-13 MW, but
reactive-power fidelity fails: the largest residual is 519.521 Mvar at 1046/1.
The final active-power residual there is 31.4642 MW. This replay omits inner
network voltage-memory corrections and cannot establish the coupled controller
states or the cause of the failed solve. The negative control establishes that
terminal-voltage-only replay is insufficient at this boundary; no recovery
timer, limiter or production equation is changed on its basis.

## Retained evidence and remaining work

Private evidence set `2026-10-19-case45-current-prefault` retains the exact
inputs, frozen runtime hashes, full physical outputs, failed-run logs, cold
state/rate inventories, original event exports, per-channel comparisons and
inspected plots. Newly generated metadata is losslessly compressed and its
uncompressed hashes are verified. The unchanged Case 2 physical-error plots
are also retained. No production source or acceptance limit changes in this
slice; no new external reference simulation is used.

The next localization must preserve the coupled network-memory boundary and
explain errors that already appear in the first 0.1 seconds. The complete
six-case, 36-event accuracy objective, direct bus-frequency comparison and
runtime requirement remain unproved.
