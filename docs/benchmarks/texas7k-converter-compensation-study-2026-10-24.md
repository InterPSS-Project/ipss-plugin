# Texas7k converter compensation and convergence study

Removing a matched converter Norton compensation pair reduces the full
ten-second run from 130,955 to 31,599 network iterations without changing
the physical network equation. After three complete warmups, the cached
prototype measures a 26.580-second median across three whole-run measurements
(26.481–33.019 s). Recording and serialization are included, and every
completed trace matches exactly. Timing variability remains material; the
20-second objective is open. Production runtime code is unchanged.

## Matched compensation terms

The converter current implementation adds `Uc V` to its physical current,
while the network matrix contains the same auxiliary admittance `Uc`.
Consequently, `A V = Iphysical(V) + Uc V + Ifixed` can be solved as
`(A - Uc) V = Iphysical(V) + Ifixed`. Both terms must be removed together.
Physical voltage-dependent current is still refreshed on every iteration.
Synchronous-machine admittances, loads, shunts and fixed residual currents
retain their existing representation.

The isolated prototype exposes an explicit converter compensation port and
builds a separate numerical matrix. Its factorization is reused until the
matrix owner, matrix revision or compensation coefficients change. In this
case, 167 converter sources qualify and three auxiliary factorizations occur:
prefault, fault applied and fault cleared. There are no factorization fallbacks.
The ordinary published network matrix retains its original representation.

A rejected first version inspected only the dirty flag. Event preprocessing
had already cleared that flag, so the first version incorrectly retained
prefault factors through the fault. Its fast timing and disturbance trajectory
are invalid. The corrected version checks a monotonic matrix revision even
after preprocessing has factorized the original matrix. A focused cache check
also covers replacement by a different matrix with the same revision.

The cached variant builds a descriptor list at the membership boundary and
checks live activity, port identity, capability and electrical coefficients at
each network call. It avoids repeatedly traversing all bus objects for this
work. Arbitrary observers and unknown device types retain ordinary dispatch;
iteration-dependent converter high-voltage memory disables compensation removal.
General mutation and
exception-cleanup contracts still require qualification before production use.

## Convergence and numerical comparison

Benchmark settings remain ordinary modified Euler, 1/240 s, 1e-4 pu voltage
correction, two minimum iterations and a maximum of 50. Calls remain 7,200;
the largest iteration count in the new run is six, compared with 27 in the
ordinary run. No native sparse solver is used.

The new stopping path does not reproduce the ordinary 1e-4 trace exactly.
Using that trace as an accuracy oracle would be misleading: the two paths
accumulate different stopping errors. A separate internal convergence study
compares both equations at 1e-7. The ordinary formulation needs depth-three
Anderson acceleration to complete within the same 50-iteration ceiling.
It takes 96,494 iterations; the auxiliary-free formulation takes 50,141.
Their maximum bus-voltage difference is 1.4255e-6 pu, and the worst recorded
rotor-angle RMSE is 9.3823e-6 degrees.

Against that tighter ordinary-equation control:

| Ten-second result | Maximum voltage difference | Worst recorded angle RMSE |
| --- | ---: | ---: |
| Ordinary formulation, 1e-4 | 0.0394275 pu | 0.0312936 degrees |
| Auxiliary-free formulation, 1e-4 | 0.000710302 pu | 0.000394254 degrees |

For all 6,717 voltage channels, normalization uses each tighter control
channel's full ten-second range. Prefault maximum NRMSE is 2.19e-12;
fault-and-postfault maximum NRMSE is 0.000578017. No channel exceeds the
respective 0.001 and 0.005 thresholds. The postfault period includes both
event-side samples at fault application. Only three machine rotor angles
are recorded in this full-network study; their prefault difference is zero.

These are internal convergence comparisons, not independent full-network
reference acceptance. Unaccelerated tighter controls exhaust the 50-iteration
budget at 1e-6 and 1e-7. A depth-one accelerated 1e-8 control also fails.
Those partial outputs and failure diagnostics are retained.

The converter change additionally passes existing independent SMIB checkpoints
at 0.25 ms and 0.125 ms with unchanged tolerances, including an active current
limit. Both the original and cached variants exercise the new path, rebuild
three times, and report no fallback. This is checkpoint coverage, not full
event-post trajectory acceptance. The exact imported classical machine is
retained; a private capability override permits deferred publication after
auditing its inherited current getter.

## Whole-run measurements and recording

Every completed run retains 2,403 samples, all 6,717 bus voltages and 15 selected
machine channels: 16,176,996 float64 values plus physical time, solver time and
sample kind. Event-side duplicates are preserved. Direct evaluation is enabled
for the 307 ST4 controllers; there are no PSS2A controllers in this network.

| Prototype | Warmup | First measurement | Second measurement |
| --- | ---: | ---: | ---: |
| Event-safe compensation removal | 42.968 s | 39.566 s | 31.120 s |
| Cached converter descriptors | 42.055 s | 39.406 s | 27.741 s |

All runs use logical CPU 6 and the same bounded Java heap and JVM settings.
These initial batches contain two unprofiled measurements each. Work outside the
timed integration interval is still substantially slower in the first two
executions; that interval includes import, preparation and serialization.
The whole timing difference cannot be attributed solely to descriptor caching.

A subsequent batch separates three full warmups from three unprofiled
measurements of the same cached executable:

| Phase | Whole run | Integration interval | Network |
| --- | ---: | ---: | ---: |
| Warmup 1 | 41.605 s | 28.459 s | 14.023 s |
| Warmup 2 | 39.575 s | 26.971 s | 12.959 s |
| Warmup 3 | 26.904 s | 24.703 s | 12.118 s |
| Measurement 1 | 33.019 s | 24.663 s | 12.162 s |
| Measurement 2 | 26.580 s | 24.857 s | 12.078 s |
| Measurement 3 | 26.481 s | 25.349 s | 12.436 s |

The integration interval includes numerical stepping and in-memory sample
capture; the network column is a subset of it. The measured median is
26.580 s, with the complete 26.481–33.019 s range retained. The slowest
measurement adds most of its extra time outside integration. Separate phase
timers are needed to distinguish import, preparation, compilation and export
costs; this study does not attribute that outlier to one of them.

The bounded CPU profile has 1,600 execution samples: network work appears in
870 and differential integration in 549. The triangular solve accounts for
269 leaf samples. This suggests that both network overhead and controller
evaluation remain worthwhile targets. The 50.157-second instrumented run is
excluded from unprofiled timing comparisons. Allocation-sample weights are not
actual byte totals or reliable per-site allocation amounts.

## Other retained experiments

Separate experiments preserve every ordinary recorded value:

- Fusing convergence and voltage publication measures 56.501 s against a
  71.000 s matched control. Its small-system check passes 14,880 raw-bit
  state/history comparisons and 60 observer-fallback steps across both norms.
- Direct ST4/PSS2A graph evaluation passes 50,880 raw-bit state/history checks
  and 120 observer-fallback steps. A full-case pair measures 57.177 versus
  59.784 s; this is not a stable performance estimate.
- Separate real/imaginary factor storage ties the existing layout. Short-row
  specialization improves isolated solve batches by roughly 9–11%, but the
  full-case pair does not establish a network-time improvement.
- The symbolic audit finds only 349 eligible pure zero-injection buses; 339
  are leaves. This does not evaluate elimination of nonzero fixed-current
  buses and is not a numerical network-reduction acceptance result.

## Retained checkpoint

The [machine-readable checkpoint](checkpoints/texas7k-converter-compensation-study-2026-10-24.json)
indexes a private manifest with 112 retained files and 184 external artifact
references. It preserves source overlays, compiled classes, reproducers,
timing logs, bounded profiles, full traces, state checks and rejected controls.
All indexed hashes were checked after capture. Supplied references are unchanged.
Hash-identical generated traces share storage after timing and export; all
paths and samples remain available. This saves 647,320,200 physical bytes.
The checkpoint also indexes a separate manifest for the subsequent six-run
batch, including its exact runtime contract and affinity evidence. Those six
traces preserve the same hash and save another 129,464,040 physical bytes by
sharing verified storage.
