# Case 2 rectangular network and controller output efficiency

The updated target is **10 seconds for ten simulated seconds**, using **one CPU
core**, a **1e-5 maximum-component voltage tolerance**, and **quarter-cycle steps
(1/240 second)**. The target remains unachieved. The current experimental candidate
takes **17.6160 and 17.7465 seconds** in two fresh-process measurements; its median
is **17.6812 seconds**. Production code remains unchanged.

## Measurements

| Candidate | Integration seconds | Network seconds |
| --- | ---: | ---: |
| Previous fully sequential candidate | 22.8559 | 12.1193 |
| Fixed machine field-current observation | 22.7140 | 12.2883 |
| Deferred angle, still polar network | 23.0011 | 12.4086 |
| Rectangular network after initialization | 18.7316 | 8.0855 |
| Typed stabilizer output dispatch | 18.5317 | 8.0366 |
| Scalar current and empty-list guards | 18.5620 | 7.9785 |
| Forward stabilizer output traversal | 17.6160 | 7.8065 |
| Fresh-process repeat | 17.7465 | 7.8719 |

All runs retain **2,400 time steps, 4,800 network calls and 38,819 network
iterations**. Windows process-affinity mask **1** is checked before and after
simulation. Java reports one available processor, uses SerialGC and launches zero
simulation worker threads. Integration timing excludes initialization and ordinary
trace serialization. Profiled and full-channel diagnostic runs are excluded from
the table; individual small timing differences are not established improvements.

## Changes and constraints

The network switches to its existing rectangular coordinates after initialization.
Bus voltage magnitude is refreshed at every network update; angle conversion is
deferred until observed. This removes repeated polar conversion and reconstruction
inside the network iteration. The guarded experimental path uses exact built-in
bus and complex-number classes, and keeps eager setters when notifications are
required. Coordinate changes and direct angle setters remain observable.

The standard stabilizer graph now recognizes its forward dependency order. When
the graph is already in that order, a single algebraic output traversal replaces
repeated identical settling traversals. Integration still captures inputs, advances
the existing kernels, and performs both subsequent preparation passes. Graphs
without the recognized order retain the existing output loop. Typed output reads,
finite static-current arithmetic, empty device-list guards, and field-current reuse
within fixed machine-input scopes are also retained in this experiment.

These changes do not loosen tolerance, skip nonlinear injection refreshes, drop
samples or channels, change the integration step, or introduce parallel workers.
General graph mutation, arbitrary block subclasses, attachment changes and failure
lifecycle handling still need production safeguards and registered tests before
the combined experiment can be promoted.

## Numerical checks

The full comparison checks **2,405 samples × 4,810 channels = 11,568,050 values**,
including negative history and both event sides. All values remain finite. Against
the retained production-equivalent polar control, maximum absolute differences are:

| Quantity | Maximum difference |
| --- | ---: |
| Bus voltage | 8.238e-14 pu |
| Bus angle | 1.481e-11 degrees |
| Machine rotor angle | 4.321e-11 degrees |
| Generator active power | 1.282e-9 MW |
| Generator reactive power | 9.615e-10 Mvar |

The coordinate change introduces roundoff differences. Each subsequent candidate,
including the repeat and full export, preserves the selected rectangular-control
trace byte for byte. This establishes numerical nonregression against the retained
implementation; it does not establish acceptance against supplied references or
resolve existing initialization disagreements.

A focused deferred-angle check passes **4,268 exact floating-point assertions**,
covering direct setters, coordinate changes, signed zero, extreme and nonfinite
inputs, reflective reads and notification history. An independent two-process
stabilizer check compares **1,000 stages with two observations per stage**, including
zero-time stages, both integration flags and clipped outputs. State, input and
output observations match byte for byte between the old and new output paths.

## Evidence and remaining work

The private evidence manifest is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-20-case2-rectangular-single-core/manifest.json`.
It retains the twelve candidate sources, producers, focused checks, runtime hashes,
affinity records and full comparison summary. Large traces remain external retained
dependencies; supplied references are unchanged.

The median still needs approximately **43.4% less integration time** to reach the
updated ten-second target. Network work takes about 7.8 seconds and the remainder
about 9.9 seconds. Further work must address both paths and complete production
integration. Parallel timings and tolerance changes do not satisfy this goal.
