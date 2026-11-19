# Texas7k numerical network-iteration workspace

An isolated numerical workspace reduces one complete warmed ten-second run
to 57.135 seconds while preserving the ordinary control trace byte for byte.
The matched indexed control measures 103.787 seconds. Both paths receive a
full warmup in the same JVM before measurement, on logical CPU 6.
This is one unbalanced pair, not a stable median or a general speedup claim.
The 20-second whole-run objective remains unmet.

## Numerical storage and publication

Static-only rows evaluate voltage-dependent current from numerical voltage
and magnitude arrays throughout the nonlinear iteration. Their ZIP settings,
load-change factors, voltage curves and stamped-admittance coefficients are
captured at the network-call boundary. Current remains voltage-dependent and
is recalculated on every iteration.

Rows with attached devices retain live dispatch and publish their bus voltage
before the next iteration's callbacks and current evaluation. Known source
classes and observer-free objects are required. Full matrix and bus values
are published before convergence callbacks and before the network call returns.
The convergence calculation uses numerical values with the existing arithmetic,
norm, stopping test and leading-bus tie order.

The factorization remains reused between network changes. Integration stays
ordinary modified Euler at 1/240 s, with 1e-4 pu voltage-correction tolerance,
two minimum iterations and the 50-iteration ceiling. The implementation is
Java-only; no native solver, parameter changes or disabled controls are used.

## Full recording and timing

Every run retains all 6,717 bus-voltage channels, 15 selected machine channels
and all 2,403 sample boundaries, including both event sides. The complete
16,176,996 float64 values, sample times and event kinds match the ordinary
control hash. Calls and iteration counts are unchanged: 7,200 calls, 130,955
iterations, and an observed maximum of 27 iterations in one call.

| Phase | Whole run | Network |
| --- | ---: | ---: |
| Indexed control warmup | 93.783 s | 61.642 s |
| Numerical workspace warmup | 65.040 s | 39.246 s |
| Numerical workspace measured | 57.135 s | 38.363 s |
| Indexed control measured | 103.787 s | 76.633 s |

Whole-run timing includes import, preparation, initialization, simulation,
recording and serialization. A separate cold workspace run measures 71.162 s.
The separately profiled run takes 85.589 s; that instrumented time is excluded
from unprofiled performance comparisons. Timing variability remains material.

All eleven complete traces retained in this study have the exact ordinary
control hash. Generated identical binaries share storage after their timed
export and hash verification. This saves 517,856,160 physical bytes without
removing paths, channels or samples; supplied references are untouched.

## State-history and fallback checks

A three-bus coupled fault fixture checks both convergence norms, changed
exciter references, changed load settings and live observer attachment/removal.
It compares complete mechanical/electrical machine histories, controller
states and stored derivatives, and bus measurements at every accepted step.

The workspace passes 14,880 exact state/value comparisons. All 60 steps with
an attached bus observer use ordinary dispatch; the workspace resumes after
observer removal. This fixture explicitly uses the exact core classical
machine alongside its round-rotor machine. The imported compensated classical
subclass retains ordinary dispatch. These are protocol and implementation
equivalence tests, not independent reference-model qualification.

## Fixed Norton-source experiment

A second prototype reuses the voltage-independent Norton source of the
supported synchronous machines within one network call. It restores their
terminal-current and electrical-power calculations at the original final
input voltage before publishing the accepted endpoint. Voltage-dependent
renewable and static-load currents remain live.

This passes the same 14,880 state/history comparisons and full trace identity,
but it does not improve the measured performance. After a complete warmup,
three whole-run measurements are 67.532, 68.931 and 63.291 seconds, with a
67.532-second median. These are a separate JVM experiment. The buffer-only
workspace remains the preferred experimental baseline. Pure-reader diagnostic
reuse counts change in the fixed-source experiment; no production API is promoted.

## Remaining cost and implementation limits

The bounded profile contains 3,692 execution samples. Triangular solve is the
leaf location in 943 samples; network work appears in 2,580 samples and
differential/device integration in 956. Network work remains the dominant
cost after removing most static-row object publication. Current assembly,
voltage access, observer checks and collection traversal remain visible.
Allocation sample weights are not treated as exact byte counts by source site.

The next work is to reduce triangular-solve kernel cost and fuse or prebind
remaining numerical row passes. Parallel work should operate on numerical
storage with explicit dependencies and ownership; the earlier object-row
parallel experiments did not improve whole-run time.

These are closed-case prototypes. General within-call mutation, unknown
callback/read dependencies, failure publication and cleanup require explicit
contracts and permanent regression coverage before a production API is added.
Production sources and defaults remain unchanged. Full-recording identity is
against the ordinary implementation, not independent full-network disturbance
acceptance; the separate optimized-integration accuracy gap remains open.

The [checkpoint](checkpoints/texas7k-packed-network-study-2026-10-24.json)
indexes private source and compiled overlays, reproducible drivers, full
traces, state-check results, affinity records, bounded profile and integrity
hashes. The complete evidence index verifies 29 retained private files and
68 external artifacts.
