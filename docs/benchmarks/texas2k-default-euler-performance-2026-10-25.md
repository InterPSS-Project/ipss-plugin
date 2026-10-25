# Texas2k Cases 1 and 2: default modified-Euler performance

The recent optimized profile reduces complete warmed runtime by **27.6% in
Case 1** and **39.3% in Case 2** using the ordinary modified-Euler solver.
Both cases retain complete recording and export. These results do not yet
qualify the profile's accuracy as a new production default.

## Complete-run measurements

| Case | Optimizations disabled: median / range | Optimized profile: median / range | Median reduction |
| --- | ---: | ---: | ---: |
| 1 | 13.841 / 12.531–14.525 s | 10.022 / 8.955–11.342 s | 27.6% |
| 2 | 9.659 / 9.403–10.867 s | 5.863 / 5.707–7.295 s | 39.3% |

Each profile has four complete warmups followed by five measured runs. Every
invocation imports a fresh case, prepares power flow, initializes devices and
controllers, integrates ten simulated seconds, records all channels and closes
the output writers, including binary, selected CSV and run metadata. Hashing
and verified duplicate sharing happen afterward. Residual JIT compilation
remains included; it is not subtracted from the case timer.

All four cohorts run sequentially on logical CPU 6 only, with OS affinity mask
64 verified before and after each JVM. Java 21, a 2 GiB heap, one active JVM
processor, Serial GC, `-Xbatch` and common inlining settings are identical.
Profiles use separate JVMs because current-handoff selection is static at
class initialization. Case 1 runs disabled then optimized; Case 2 reverses
that order. This is a controlled multi-JVM comparison, not an interleaved
same-JVM trial.

The later [current-handoff on/off study](texas2k-current-handoff-2026-10-25.md)
isolates this property. In the current implementation it also gates packed
iteration, converter-compensation removal and endpoint reuse. The combined
profile improves timing; the isolated handoff shows no measured speed benefit.

## Numerical and workload settings

The user-selected method is ordinary `MODIFIED_EULER`, using the default
imported-case solver rather than the staggered solver. Both profiles use:

- A quarter-cycle step, `1/240 s`, and the saved five-cycle bus-6307 fault
  from 1.000 to 1.083333 seconds.
- A `1e-5` pu complex voltage-correction criterion, two minimum iterations
  for fresh solves and a maximum of 50.
- Ordinary renewable modified-Euler integration with double-precision states
  and no iteration-voltage-memory extension.
- Current-P/impedance-Q load conversion and the same solved-netting and
  generator-priority power-flow preparation, at 0.001 pu mismatch tolerance.
- Existing compatible controller compilation, supported direct controller
  kernels and machine-frame reuse in both arms. Unsupported controller
  compilation retains its compatible implementation.
- All 2,405 samples, including two negative-time startup samples and both
  fault event sides. Case 1 records 6,190 channels; Case 2 records 4,810.

The comparison uses the same maintained core 1.4.6/plugin 1.4.8 source with
the recent profile disabled or enabled. The disabled arm explicitly retains
all-bus frequency measurement. The enabled arm selects required measurements
and the maintained numerical-buffer, converter-compensation and endpoint paths.
It does not rebuild an older source release. Earlier staggered-solver timings,
including the historical fastest results, are not directly comparable.

The inherited `machine_measurement_stage` status field reports a legacy
staggered-only option and is unused by this runner. Actual measurement and
integration follow the ordinary modified-Euler stages. Likewise, the old
`reuse_network_endpoint` field does not select the new checked certificate;
its actual coverage is reported as `accepted_endpoint_reuses`.

## What actually helped

| Work counter or attribution | Case 1 disabled → optimized | Case 2 disabled → optimized |
| --- | ---: | ---: |
| Network iterations | 45,405 → 20,559 | 52,766 → 22,732 |
| Largest fresh network iteration count | 18 → 6 | 21 → 8 |
| Median network region | 6.440 → 3.230 s | 6.114 → 2.793 s |
| Median device integration region | 5.255 → 5.089 s | 2.098 → 1.731 s |
| Checked INITIAL endpoint reuses | 0 → 2,399 | 0 → 2,399 |
| Auxiliary converter sources | 0 → 96 | 0 → 96 |
| Auxiliary factorizations | 0 → 3 | 0 → 3 |
| Measured frequency buses | 2,000 → 2,000 | 2,000 → 2,000 |

The network formulation, fewer redundant solves and indexed numerical path
account for the principal measured work reduction. The three auxiliary
factorizations cover initial, faulted and cleared operators; ordinary matrix
factor reuse already existed. Region medians are separate statistics and do
not form an exact additive decomposition of the whole-run medians.

The frequency selector conservatively retains every bus because an existing
PSS2A helper is unclassified. Passive-bus skipping therefore also records zero
skips. These cases demonstrate no benefit from reduced frequency coverage;
the 7K measurement reduction must not be extrapolated to them. Both cases use
PSS2A stabilizers, so the new typed IEEEST stage is not the explanation for
their timing improvement.

All 36 warmup/measured recordings are complete and repeat exactly within
their profile. Each profile also matches its separate complete pilot. Full
before/after channel comparisons preserve the original timestamps and sample
kinds, with no offsets, rotation, resampling or event-side removal.

## Accuracy limits and tighter controls

The finite-tolerance trajectories differ. Same-tolerance before/after worst
rotor-angle RMSE is 0.8004 degrees in Case 1 and 0.3203 degrees in Case 2;
peak bus-voltage differences are 0.008882 and 0.023385 pu respectively.
These differences alone do not identify which formulation is more accurate.

Separate complete controls run both formulations at `1e-7`, with the same
method and time step. Their mutual worst rotor-angle RMSE is 0.009666 degrees
for Case 1 and 0.002159 degrees for Case 2. Comparing the measured `1e-5`
profiles against the tighter ordinary-formulation control gives:

| Case | Disabled rotor-angle RMSE | Optimized rotor-angle RMSE | Disabled fault-period voltage NRMSE | Optimized fault-period voltage NRMSE |
| --- | ---: | ---: | ---: | ---: |
| 1 | 0.87544° | 0.07578° | 0.015236 | 0.006304 |
| 2 | 0.34316° | 0.02548° | 0.022522 | 0.001305 |

Voltage normalization uses each tighter control channel's full ten-second
range; the fault period starts at application and includes both event sides.
All 2,000 bus-voltage channels and all 333/103 rotor-angle channels are compared.

The optimized profile is closer to these internal controls, but both optimized
rotor-angle RMSE values exceed the existing 0.01-degree target. Case 1 also
exceeds the 0.005 fault-period voltage NRMSE target. Internal convergence
comparisons do not establish independent reference accuracy. The timing gain
does not approve this `1e-5` profile as an accuracy-qualified replacement.
No production source or default settings are changed by this study.

The [evidence checkpoint](checkpoints/texas2k-default-euler-performance-2026-10-25.json)
records all measured times, compiler counters, exact settings, source and
dependency provenance, runner source/bytecode, CPU affinity, full recordings,
tighter controls and failed development attempts. Identical generated outputs
share storage only after complete byte/hash verification; supplied references
are preserved.
