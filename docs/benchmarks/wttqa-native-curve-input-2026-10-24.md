# WTTQA1 native speed-curve input correction

The delayed response came from an extra power filter in the native-record
speed-curve path. In the nonunit SMIB profile, the earlier runtime evaluated
`curve(filtered_power)` and then applied the speed-reference lag. Independent
reference histories instead establish `curve(raw_electrical_power)` at that
junction. The reference power-filter state still exists and remains the input
to the power-error branch.

The discriminator holds the record constants, initial conditions, boundary
inputs, fault timestamps and integration method fixed. The 20 ms power filter
and 100 ms speed-reference filter retain their entered values. The reference
curve diagnostic agrees with the raw-power curve within 4.77e-8 pu; using the
reference filtered-power state instead produces a 0.008688 pu discrepancy.
The corrected speed-reference state also converges toward the reference when
the integration step is halved. No parameter fitting or time shift is used.
Separate preserved fault traces for selectors 0, 1 and 2 confirm the same raw
curve junction: residuals are below 3.90e-8 pu, while the filtered-input
witnesses differ by 0.0102 to 0.0110 pu.

## Runtime scope

`Wttqa1Model.fromNativeRecord` selects the observed raw-power input convention.
The direct DYR importer uses this factory through
`DStabNetworkBuilder.addWttqa1FromNativeRecord`. The existing typed constructor
and builder API retain the published filtered-power cascade. This explicitly
records the source-diagram/executable distinction identified in the
[earlier partial-evidence checkpoint](wttqa-speed-feedback-2026-10-19.md).
The historical ANDES trajectory fixture explicitly selects its original typed
torque convention, alongside its existing typed drivetrain and pitch choices;
its trajectory limits and reference data are unchanged.

The correction covers complete-step, predictor/corrector, two-step history and
zero-time algebraic evaluation. `getCurveOutput` exposes the last stage's
algebraic diagnostic without changing a state. The three integration-state
names and all sixteen record fields are unchanged. Both power-control selectors
continue to use filtered electrical power in their torque-error calculation.

## All-row recorded-input comparison

Maximum absolute errors against the same immutable references:

| Quantity | Earlier 0.5 ms | Corrected 0.5 ms | Corrected 0.25 ms |
|---|---:|---:|---:|
| Curve output, pu speed | 0.00868807 | 4.76840e-8 | 4.76840e-8 |
| Speed-reference state, pu | 8.10923e-4 | 8.89117e-6 | 4.49773e-6 |
| Torque-integrator state, pu | 2.53629e-5 | 1.35201e-6 | 8.76562e-6 |
| Filtered-power state, pu | 8.43295e-5 | 8.43295e-5 | 4.33318e-5 |

The corrected speed-reference maxima occur just after fault clearing at
0.1005001 s and 0.1002496 s. Curve-output errors remain below 6e-8 pu on all
rows. The fine-grid torque-integrator residual grows near the end of the
reference run; it is retained and bounded independently rather than hidden by
an assertion about uniform timestep convergence.

Both curve profiles now have individual state/diagnostic gates. The eight
default-curve profiles retain their earlier, unchanged channel bounds.
Every 1,005/2,005-row export, startup sample and duplicate event timestamp is
retained. Model initialization uses physical power and shaft speed, not injected
reference states.

## Validation and limits

Five permanent conformance tests check staged derivatives, all stepping paths,
zero-time bypass, the retained power-error filter, typed convention isolation,
strict import and all ten reference profiles. The focused SMIB/import/system
selection passes 26 tests without failures, errors or skips, including the
complete Texas7k ten-second flat and five-cycle fault gates.

The final complete reactor passes 4,108 tests with 86 existing skips and zero
failures/errors: 442 core, 468 three-phase and 3,284 integration tests including
skips. All 408 offline checks and fifteen additional topology-selected fault
cases pass. The historical comparison fixture passes its original trajectory
limits. Source inventories, full reports, both earlier failed validation runs,
raw cold-paired references and all-row replay traces are retained in the private
`verification-artifacts/2026-10-24-wttqa-native-curve-input` checkpoint.

The existing cold-paired references and their digests remain unchanged. This
resolves the curve-input convention discrepancy; it does not qualify the entire
wind stack, every limiter/lifecycle branch or independent full-network
disturbance trajectories. The model remains classified as partial native
evidence rather than blanket `VERIFIED`.
