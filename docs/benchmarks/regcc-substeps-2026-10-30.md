# REGCC local integration and current PI limits

The runtime now separates the prepared network interval `H` from the local
integration interval `h`. It also applies the independently observed bounds to
the current PI outputs and stored integrals. **One local step remains the
default:** the coupled comparisons below do not justify promoting a larger count.

This extends the [original REGCC checkpoint](regcc-model-2026-10-28.md).
Loadability, equation evidence and complete trajectory acceptance remain separate.

## Configuration and stage lifecycle

```java
// Obtain the attached converter before algorithm initialization.
converter.setLocalSubsteps(4);
algorithm.setSimuStepSec(1.0 / 240.0);
algorithm.initialization();
```

`RegccModel.setLocalSubsteps` accepts powers of two from 1 through 128, before
initialization. No CPU placement or native sparse-solver option enters the model.
The default imported-network solver prepares `H` before state initialization.
Typed callers must call `configureIntegrationStep(H)` to prepare the same contract.

Prepared coefficients use `max(Te, 2*H)` and the two auxiliary lags use `4*H`.
Advancing a shorter interval no longer silently changes those coefficients.
`getNetworkStep`, `getLocalStep`, `getEffectiveTe` and `getRecoveryTime` expose them.
Reconfiguration between a predictor and its corrector is rejected.

The one-step path retains the existing modified-Euler arithmetic. With multiple
steps, the predictor advances local Euler trials using the initial sampled
network input. The corrector restarts from committed coordinates and applies
local modified-Euler pairs, interpolating voltage and commands between the two
sampled outer endpoints at each local stage time. No additional network solves
occur. The nine coupled coordinates, physical current, PLL transforms and limits
are refreshed at each local stage. Upstream electrical-controller history advances
once per outer predictor/corrector stage. The last local evaluation remains
available through the named derivative diagnostics.

Endpoint interpolation is an explicit numerical policy. It differs from holding
one algebraic input over the entire network interval. The analytic voltage-filter
ramp test checks local stage timing, while the held-input test checks equality
with explicit local predictor/corrector pairs. Event-side samples remain separate.
This change does not add REGCC support to the optional staggered solver.

## Independent limiter discriminator

The installed model documentation and nine-state mapping were reviewed, and
four integral-boundary perturbations were each regenerated cold twice. Both
stored current integrals clip to `[-Imax, Imax]`. The runtime prevents an outward
derivative at a reached bound and permits recovery toward the interior.

A second discriminator uses a high-gain fault trace, with `Kip=5`, `Kii=70`,
`Te=0.01 s`. All three-bus boundary channels and published states/variables were
retained. On 177 smooth central-difference triples inside the fault interval:

| Candidate equation | Derivative RMS residual (pu/s) |
|---|---:|
| Internal voltage from unlimited current PI outputs | 7.495594 |
| Internal voltage from independently clipped current PI outputs | 0.009463 |
| Reactive integral with the ordinary `Kii*error` equation | 0.003832 |
| Reactive integral with output-error back-calculation | 52.466828 |

The runtime therefore clips each PI output to `[-Imax, Imax]` before calculating
the source-voltage target. These axis bounds are separate from the physical
current vector's P/Q-priority limit. Inside the integral-state bounds, its rate
remains `Kii*error`; output clipping does not inject back-calculation.

The public [Dynawo PI implementation](https://github.com/dynawo/dynawo/blob/b1d50a690a9e5b3a76210b353a7cfd0aa495e664/dynawo/sources/Models/Modelica/Dynawo/NonElectrical/Blocks/Continuous/BaseClasses/BasePIAntiWindup.mo)
uses back-calculation. That wiring is useful comparison evidence, but the native
discriminator above rejects copying it into this model dialect.

The active-current integral's abrupt cross-coordinate transition is still
unexplained. The boundary perturbations reproduce it at both positive and
negative bounds: after clipping the active integral, the next reference sample
copies the preceding reactive integral. The implementation does not reproduce
that transition. The original Q-priority stress profile remains unqualified;
its maximum active-power discrepancy is now about 0.11438 pu. The two original
step-limited profiles and upstream electrical-controller history diagnostic
also retain their unqualified status.

## Fixed-coefficient 1/2/4/8 experiment

Three fault profiles were each generated twice at network grids of `H`, `H/8`
and `H/16`, giving eighteen complete reference runs. The boundary audit adds
eight runs; all thirteen cold pairs have identical input and CSV hashes.
The fault is applied at 0.05 s and cleared at 0.10 s: three cycles at 60 Hz.

The native fine-grid records explicitly enter the same prepared `Te` used at
`H=1/240 s`. Fixed current commands with `RateFlag=0` keep the two recovery-lag
states stationary, verified from every reference row. This avoids confounding
the native auxiliary lags' timestep dependence with the local-step comparison.
It does not qualify changing commands, `RateFlag=1` or a complete upstream chain.

All Java comparisons keep the network interval and solver settings fixed while
varying only local count. They record all nine converter coordinates, terminal
P/Q and voltage/angle at all three buses. Each full reference retains its ten
converter variables too; those variables are audit evidence, not additional Java
trajectory gates. Each fine record has 773 rows, including two negative-time
initialization samples. Every nonnegative row is compared: 771 rows and 17
channels, with duplicate event sides retained. Piecewise-linear interpolation
of Java outer-grid output supplies intermediate fine-grid values, so the error
includes output interpolation error. No time or angle offsets are fitted.

Maximum bus-1 voltage discrepancy against the `H/16` reference:

| Local steps | Nominal gains (pu) | High gains (pu) | Prepared Te floor (pu) |
|---:|---:|---:|---:|
| 1 | 0.001202 | 0.006815 | 0.001763 |
| 2 | 0.001643 | 0.009453 | 0.002383 |
| 4 | 0.001929 | 0.008144 | 0.002733 |
| 8 | 0.002117 | 0.008229 | 0.002959 |

For the high-gain one-step profile, fixing the PI output limit reduces the
maximum reactive-integral discrepancy from approximately **0.21958 to 0.01498 pu**
in this experiment. Full terminal P/Q, voltage/angle and state errors remain in
the checkpoint. This is an improvement, not complete trajectory acceptance.

Local substeps address the internal explicit integration. They do not replace
network-stage coupling or guarantee greater accuracy against another method.
The measured coupled results support retaining the one-step default; callers
can explicitly exercise larger counts for other parameter/network combinations.

A separate diagnostic reduces the coupled network interval to `H/16`, while
retaining the same prepared coefficients. With one local step, the maximum
bus-1 voltage errors become 0.000553, 0.000831 and 0.000659 pu respectively.
The high-gain reactive-integral discrepancy falls to 0.004304 pu. This confirms
that increasing local steps alone does not resolve the remaining outer-stage
coupling error; it does not establish complete model acceptance.

## Complete warmed small-system timings

Each profile uses four complete fresh-network warmups per count, followed by
five measured runs per count, interleaved in the same JVM. CPU 6 placement is a
launcher setting, with affinity checked before and after. The pure-Java runs
use Java 21 and a 2 GiB heap. The timer includes import, power flow, initialization,
ten simulated seconds, all 17 channels and CSV export (2,403 samples per run).
One-time application startup and comparison/JSON bookkeeping are outside it.
All 108 invocations completed; every recording is retained.

Whole-run median, with minimum–maximum in parentheses, in seconds:

| Local steps | Nominal gains | High gains | Prepared Te floor |
|---:|---:|---:|---:|
| 1 | 0.268 (0.240–0.520) | 0.244 (0.015–0.803) | 0.249 (0.023–0.611) |
| 2 | 0.365 (0.019–1.582) | 0.177 (0.032–0.248) | 0.494 (0.125–1.949) |
| 4 | 0.377 (0.091–0.620) | 0.239 (0.135–0.494) | 0.124 (0.022–0.269) |
| 8 | 0.252 (0.128–2.295) | 0.147 (0.101–0.373) | 0.374 (0.250–1.118) |

The ranges overlap heavily and vary far more than the expected local-step cost.
These small-case timings establish completion and recording scope; they do not
establish a speedup, explain the noise, or predict whole-system performance.

## Reproduction and validation

`tools/regcc_reference_probe.py` now supports the declared fixed-coefficient
grids, high-gain profile and integral perturbations. Use
`tools/check_regcc_substep_artifacts.py` to verify all pairs and stage the
lossless regression fixtures. The original fixture audit remains unchanged.
`tools/run_regcc_substep_study.py` launches the compiled `RegccSubstepStudy`
against the current reactor test classpath. Run through `tools/run_bounded.py`
and specify fresh output directories. `--cpu` is optional benchmark configuration.

The focused validation passes forty tests, including the new independently
recorded PI-equation check, 1/2/4/8 diagnostics, analytic stage checks, retained
bounded trajectories, catalog, configuration and manifest guards. Diagnostic
tests check completeness and finite state comparisons; they do not silently
promote profiles to trajectory-verified status.

The full reactor runs **4,282 tests, with 86 skips and four failures**. Plugin-core
and three-phase modules pass. The failing methods are unchanged from the
previous frozen-baseline audit: the endpoint snapshot fault-edge check, the
REECC1 coupled fault, Case 3's three-cycle fault and Case 5's generator trip.
The new REGCC checks pass. No full-suite pass or merge is claimed.

The [checkpoint](checkpoints/regcc-substeps-2026-10-30.json) retains complete
reference artifacts, runnable inputs/scripts, failed diagnostic attempts, every
timed recording, exact runtime/source hashes and validation results. Broader
reactor results are recorded there too.
