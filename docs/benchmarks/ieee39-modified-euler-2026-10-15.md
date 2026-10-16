# IEEE39 with strict modified Euler

The production default remains the modified-Euler predictor/corrector (Heun)
method. No Adams–Bashforth recurrence or benchmark-specific event-history blending is added
to that path. The accuracy correction is in annotated CML controller stage
sampling; it does not change model parameters, gains, limits or initial states.

## Accuracy correction

Previously, each controller block advanced before downstream blocks sampled
its output. That mixed old and predicted states within a predictor stage, and
predicted and corrected states within a corrector stage. The two-state example
`x'=-x, y'=x-y`, with initial state `(1,0)`, demonstrated first-order convergence:
halving the step from 0.02 to 0.01 reduced its error by a factor of 2.0033.

The corrected controller propagates algebraic inputs with the dynamic state
vector held fixed, captures each block's input and variable limits, then
advances the blocks. Nested annotated controllers share the preparation pass.
Algebraic outputs and feedback inputs are refreshed afterward without changing
physical states or overwriting predictor derivatives. Composite ramp filters
use the same simultaneous sampling internally. Custom blocks outside the
standard CML adapter retain their existing integration protocol.

For the analytical example at step 0.1, the predictor is `(0.9,0.1)` and the
corrector is `(0.905,0.09)`. The corrected implementation passes these exact
stage checks and the second-order step-halving check. Additional tests cover
composite-filter feedthrough and preservation of predictor derivatives during
algebraic refresh. This verifies controller-stage accuracy, not second-order
convergence of every model, limiter and switching event in an entire network.

## Matched native comparison

Both programs use a **0.00025 s step and 1e-6 network tolerance**, with 100
maximum network iterations. The unchanged IEEE39 RAW/DYR, constant-impedance
loads, 50 ms frequency filter and 5 s flat/fault profiles are the same as the
[native benchmark](ieee39-benchmark-2026-10-15.md). The bus-17 fault lasts from 1.0 to
1.05 s. All 246 comparable channels are scored, including both instantaneous
event sides; no initial offsets are removed. Native reference hashes are
checked before comparison. The archived native cold repeat remains unchanged.

All flat-case channels pass. In the fault case, **228 of 246 channels pass**;
five regulator-output channels and thirteen machine-power channels exceed
0.001 pu. Every bus voltage and frequency, governor state, machine
electromagnetic state, rotor angle and speed channel meets the requested
threshold. This is **not full trajectory agreement with the benchmark**.

| Fault-case quantity | Before correction: maximum error | After correction: maximum error | Channels above limit |
|---|---:|---:|---:|
| Bus voltage (pu) | 0.000148837 | 0.000148293 | 0 / 39 |
| Bus frequency (Hz) | 0.003615933 | 0.003615933 | 0 / 39 |
| Exciter states (pu) | 0.015098244 | 0.015098383 | 5 / 36 |
| Governor states (model coordinates) | 0.000248857 | 0.000247586 | 0 / 37 |
| Rotor angle (rad) | 0.000168793 | 0.000103595 | 0 / 10 |
| Machine electromagnetic states (pu) | 0.000470371 | 0.000470371 | 0 / 36 |
| Machine outputs (pu) | 0.002882478 | 0.002882477 | 13 / 39 |
| Machine speed deviation (pu) | 0.00000409317 | 0.00000409317 | 0 / 10 |

All 18 channels reach their largest error at the first step after fault
application or clearing. There is also a regulator-ceiling crossing difference:
machine 38 has a 0.00725742 pu error at 1.03025 s as the regulator reaches its
5.2 pu upper limit. These samples remain in the acceptance calculation.
Improving Heun's smooth-state accuracy does not reproduce the distinct
finite-step history rule used by the benchmark reference.

## Regression scope and reproduction

### Requested 4.333 ms comparison

The additional run uses exactly **0.004333 s** in both programs, retaining the
1e-6 network tolerance, 100 network iterations and all original acceptance
bounds. At this case's 60 Hz base frequency, an exact quarter cycle would be
1/240 s = 4.166667 ms; this run uses the explicitly requested 4.333 ms value.

To preserve a constant step and identical event timing, each requested profile
time is rounded to the nearest integration step in both simulators:

| Point | Requested (s) | Executed (s) | Step index |
|---|---:|---:|---:|
| Fault application | 1.0 | 1.000923 | 231 |
| Fault clearing | 1.05 | 1.048586 | 242 |
| End | 5.0 | 5.000282 | 1154 |

Thus the fault lasts 47.663 ms (11 steps). This is a matched comparison between
simulators at the new step, not a pure step-convergence study of an identical
50 ms disturbance. Both instantaneous event sides remain in the error scoring.
All 18 native CSV files (normalized, original and power-flow exports across
three channel groups and two scenarios) match byte-for-byte on a cold repeat.

The flat case passes all 246 channels. The fault case passes 118 and fails 128
against the unchanged 0.001 state/voltage bounds and 0.01 Hz frequency bound.

| Quantity | Maximum absolute error | Channels above limit |
|---|---:|---:|
| Bus voltage (pu) | 0.001939983 | 35 / 39 |
| Bus frequency (Hz) | 0.049353739 | 36 / 39 |
| Exciter states (pu) | 0.184683139 | 21 / 36 |
| Governor states (model coordinates) | 0.001412454 | 3 / 37 |
| Rotor angle (rad) | 0.000420372 | 0 / 10 |
| Machine electromagnetic states (pu) | 0.005216345 | 10 / 36 |
| Machine outputs (pu) | 0.036337051 | 23 / 39 |
| Machine speed deviation (pu) | 0.000075864 | 0 / 10 |

The trajectories agree closely away from switching events, where larger
finite-step differences remain. Maximum bus-frequency error after 1.2 s is
0.001180396 Hz. The larger step therefore does not meet the requested full-run
parity bounds; network convergence to 1e-6 alone does not establish trajectory
agreement.

For diagnostic context, the separate opt-in staggered solver also runs at
4.333 ms. It has 56 failing fault channels: 36 frequency, 8 exciter and 12
machine-output channels. It is not substituted for the requested strict
modified-Euler default. At 0.25 ms that opt-in path still passes all 246 channels
in both cases after the controller-stage correction.

Full benchmarking metrics are under
`target/ieee39-accuracy/modified-euler-4333us/` and
`target/ieee39-accuracy/staggered-4333us/`. The archived comparison records the actual CSV hash, effective event times
and native repeat hashes.

### Test results

The full core build passes all 2,898 tests. The full plugin run completes 2,524
tests: 2,415 pass, 108 skip and one fails (the PSS3C native EFD comparison below).
No plugin test errors occur. These full-suite counts precede the additional
4.333 ms exporter cases; the expanded exporter is checked separately at both
steps: all eight exporter cases pass. The six event-alignment and
settings-validation tests pass. Export success checks finite trajectories and
network convergence; the separate parity comparison retains the failures above.

Core accuracy changes are committed as `74a7985dc`. Raw native outputs, both
InterPSS methods at both steps, comparison reports, exact benchmarking scripts and
155 verified file hashes are retained locally under
`ipss.test.plugin.core/testData/reference/ieee39-modified-euler-20261015/`.
This private reference directory follows the repository's existing ignore rule.

The six block-equation tests now sample the old and predicted endpoints before
their respective stage advances. The SEXS oracle uses the simultaneous
two-state Heun equations, replacing its former sequential state updates.
Their tolerances are unchanged. The internal motor-test checkpoint changes
from 0.59025943 to 0.59024405 pu, retaining its 1e-6 tolerance and exact stalled
state assertion. This is an internal checkpoint, not independent motor-model
acceptance evidence.

The PSS3C SMIB native comparison remains above its existing EFD tolerance:
0.011002099 pu at 0.0985001102 s against a 0.010000000 pu bound. Its reference,
step and tolerance are retained; this remaining regression is not hidden by
changing the expected trajectory or widening its bound.

The exporter now supports explicitly labeled `modified-euler` and
`psse-staggered-euler` comparisons through `Ieee39PsseBenchmarkTest` in
`src/test/resources/test-config/ipss.test.plugin.core.json`. Both are exercised;
the separate staggered path is not selected as the production default.

```sh
mvn -o -pl ipss.test.plugin.core -am test -Pextended-tests -Dtest=Ieee39PsseBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false
```

InterPSS outputs are under
`ipss.test.plugin.core/target/ieee39-psse-benchmark-step-0.000250-modified-euler-stage-consistent/`.
The full comparison is `target/ieee39-accuracy/modified-euler-stage-consistent/`.
The native comparator's `--require-acceptance` correctly exits unsuccessfully
for the default method's 18 remaining violations.
