# Case 2 direct-controller execution experiment

The target is **15 seconds for ten simulated seconds**, at a 1e-5 maximum-component
network voltage tolerance and a quarter-cycle step (1/240 second). It remains
unachieved. The experimental combined candidate took **34.9361
seconds** for integration, including **17.7864 seconds** in network
solution. The current production control took 37.1794 seconds and the preceding
network-only candidate took 36.3253 seconds. These are single fresh-process timing
observations; repeated unprofiled timings are still needed to establish the saving.

## What was tested

An isolated ST1 evaluator replaces string-based input resolution with direct
signal access during the ten local substeps. It retains the initialized block
order, operand-read and floating-point arithmetic order, three algebraic passes,
existing numerical kernels, non-windup handling, and block state storage. The
candidate also includes the previously checked network traversal and buffer
changes. It does not change tolerance, endpoint solves, integration steps,
physical parameters, output sampling, or voltage-dependent current refreshes.

This is **experimental code, not a production implementation**. It assumes the
initialized standard graph remains fixed, rejects replaced block objects and
variable limit expressions, and does not yet provide the general CML fallback
and lifecycle coverage needed for production. No model is newly classified as
verified by this experiment.

## Numerical evidence

The full-network comparison matches all **2,405 samples × 4,810 channels =
11,568,050 physical values byte for byte**, including history and fault-side
duplicates. Both runs use 4,800 network calls and 38,819 iterations. The selected
channels also match in the profile and controller-timer runs. The decompressed
full-trace SHA-256 is
`8b47e58bc57c6b003ad44648d16f2abb8ddc4cda5aa7e62a89f000a00343a2d4`.

This proves same-tolerance Case 2 numerical nonregression only. It does not prove
acceptance against the supplied reference or resolve its prefault equilibrium.
Full export, profiling, and instrumented diagnostic timings are excluded from
the unprofiled performance claim.

## Remaining cost

The fresh sample profile still attributes 8.36% of leaf samples to compiled CML
symbol resolution, 7.09% to triangular solves, 5.81% to angle arithmetic, 5.65%
to CML stage preparation, and 5.22% to custom bus-current assembly. Sample
percentages are diagnostic indicators, not isolated wall-time measurements.

An additional diagnostic measures inclusive time by controller class:

| Controller | Instances | Stage calls | Stage seconds | Output seconds |
| --- | ---: | ---: | ---: | ---: |
| Ieee1992PSS2AStabilizer | 103 | 989,933 | 5.272 | 2.510 |
| IEEE2005ST4BExciter | 64 | 615,104 | 1.946 | 1.035 |
| IEEE1981ST1Exciter | 36 | 345,996 | 1.203 | 0.001 |
| IeeeSteamTCDRGovernor | 15 | 144,165 | 0.251 | 0.132 |
| Ieee1968Type1Exciter | 3 | 28,833 | 0.082 | 0.026 |
| Ieee1992PSS2AStabilizer$CustomExciter | 206 | 0 | 0.000 | 2.366 |

These timers include initialization and nested calls. The columns overlap and
must not be added. They identify the next controller families to investigate;
they are not alternative runtime measurements. ST1 local direct calls bypass
these generic stage timers.

The private evidence manifest is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-20-case2-st1-direct/manifest.json`.
It archives compact sources, plans, hashes, logs, and summaries while retaining
large traces and the profile by hash without copying them. All runs used bounded
logs and retained the two-GiB disk reserve.
