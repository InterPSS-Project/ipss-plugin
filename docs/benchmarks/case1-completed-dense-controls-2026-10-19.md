# Case 1 completed dense network controls

Both diagnostic dense interfaces complete the original ten-second bus-7249
fault with byte-identical trajectories: 20,005 rows and all 50 recorded
physical channels, including cold history and both event sides. Their three
cold snapshots have exactly equal parsed values. Snapshot JSON key order
differs between processes, so snapshot byte identity is not claimed.

The work interface avoids a repeated scan of the entire LU factor array.
Matrix and factor finiteness are checked once, and every right-hand side and
solution is still checked. The underlying factorization and triangular-solve
arithmetic remain unchanged. The earlier independent matrix controls and
this full-event equality support that specific optimization.

| Quantity against supplied reference | Standard sparse solver | Either dense interface | Limit |
| --- | ---: | ---: | ---: |
| Worst prefault bus-voltage NRMAE | 0.0000154562 | 0.0000184650 | 0.001 |
| Worst fault/postfault bus-voltage NRMAE | 0.000400016 | 0.000402347 | 0.015 |
| Worst fault/postfault terminal-voltage NRMAE | 0.000878609 | 0.000881246 | 0.015 |
| Worst prefault rotor maximum error, degrees | 0.00575997 | 0.00584708 | 0.02 |
| Worst full-window rotor RMSE, degrees | 2.187948 | 2.195310 | 0.2 |

All five bus-voltage and all five terminal-voltage channels pass both period
limits in each run. All five rotor channels pass the prefault limit and fail
the full-window limit. The largest dense rotor discrepancy is 7.031164 degrees
at unit 7189/1 at ten seconds. No angle offset is removed from acceptance.

Between sparse and dense results, the largest observed bus-voltage difference
is 0.0000388524 pu and the largest rotor-angle difference is 0.0293429 degrees.
Thus the approximately seven-degree error against the supplied reference is
not attributable solely to the sparse backend. An auxiliary bus-phase check
retains the raw principal-angle differences and separately reports their
circular difference, at most 0.0276725 degrees. This wrap treatment does not
change any rotor acceptance measurement.

The high-level dense interface takes 4,092.0 seconds, the work interface
2,789.4 seconds and the sparse control 1,344.7 seconds. These runs overlap
other work and use 0.5 ms integration plus diagnostic recording and a bridge
to the dense numerical library. Their timings are diagnostic observations;
they do not establish a production speedup or the 20-second target at a
quarter-cycle step.

Private evidence set `2026-10-19-case1-governor-boundary-and-dense-controls`
retains the complete runs, exact source and runtime hashes, comparisons,
structured cold equality and the paired trajectory hash. The initial overly
strict snapshot byte check was replaced with exact parsed-value comparison;
the CSV byte comparison remains strict. The six-case, 36-event accuracy goal,
direct bus-frequency comparison and production performance remain open.
