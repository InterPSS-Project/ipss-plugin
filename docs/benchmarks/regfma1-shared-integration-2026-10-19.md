# REGFMA1 shared integration and six-case startup follow-up

## Implementation and scope

REGFMA1 now participates in the shared staggered solver through its existing
state-buffer interface. The solver retains the nine-coordinate derivative
history and advances the source angle, measurement filters and bounded PI
integrals together with the synchronous machines. Network endpoint evaluation
refreshes terminal power and algebraic outputs without advancing differential
states. The ordinary predictor/corrector path remains available.

The model exposes immutable integration-state and evaluated-derivative
snapshots, including predictor, corrector and shared-solver stage labels.
Initialization resets the observation status. The benchmark audit now includes
REGFMA1 explicitly; previously its rows contained only bus identity and voltage.

The shared path supports the five original Case 6 units, which have no attached
plant controller. Attaching one is rejected before integration, as its shared
coupling remains unverified. These units use double-precision shared history;
the existing REGCA bundles retain their separately configured precision.
Neither source parameters nor original exchange records are rewritten.

## Independent model comparisons

The fifteen immutable profiles from the
[native limiter follow-up](regfma1-native-limiters-2026-10-17.md) are replayed
with both the default and shared methods. Every regular endpoint is compared
across all 24 state, auxiliary and physical channels: 12,015 endpoints per
method. Reference CSV digests must match the published checkpoint metadata.
Duplicate flat-run segment boundaries are also compared. Negative-time history
and instantaneous post-event rows remain preserved but unscored in this model
audit; their exact parity is not claimed.

| Profile at 0.5 ms | Default maximum terminal-voltage error (pu) | Shared maximum error (pu) |
|---|---:|---:|
| Direct-voltage fault | 2.46167e-5 | 3.85412e-7 |
| Voltage-PI fault | 3.94098e-5 | 3.52168e-7 |
| Active current limit, direct voltage | 1.39783e-5 | 9.62465e-6 |
| Active current limit, voltage PI | 2.89776e-5 | 6.86181e-6 |
| Four power-limit paths | 1.21937e-4 | 1.49804e-6 |
| Voltage-integral limit | 3.82820e-5 | 3.61329e-7 |

The worst shared terminal-voltage error across all fifteen profiles is
9.62465e-6 pu. The largest source-angle error is 5.28764e-6 radians.
All existing nineteen-checkpoint tests pass. Extending their unchanged bounds
to every regular endpoint exposes two failures among 360 profile/channel
checks: terminal-current magnitude in the direct-voltage current-limit profile
is 1.54413e-5 pu at 0.5 ms and 7.54519e-6 pu at 0.25 ms, versus bounds of
1.3e-5 and 6e-6. Tightening network convergence from 1e-6 to 1e-8 changes these
to 1.54408e-5 and 7.54508e-6; it does not resolve the residual. The limits are
unchanged and full model acceptance remains open.

The final focused run passes **82 tests, zero failures or skips**. It covers
both integration methods, parser/base behavior, fifteen archived model profiles,
independent legacy trajectories, shared-solver lifecycle, the two electrical
controller suites, static-load matrix behavior and read-only reporting.
New guards check retained event history, detached rate snapshots, independent
integral bounds, inactive units and attachment replacement. Both new classes
are registered in the core suite. This is not a fresh execution of that entire
suite. An earlier test incorrectly expected the high-level simulation API to
propagate an exception; that API catches it. The corrected guard checks the
solver directly, and both test attempts are retained.

## Original Case 6 and controller-refresh controls

Case 6 now completes a 1.2-second run at its original 0.5 ms step, retaining
all five grid-forming units, all 40 matched physical channels and 2,405 rows.
It uses the same original inputs, computed conversion, I-P/Z-Q loads, event
sides and full-ten-second normalization as the previous startup attempt.
This removes the unsupported-model stop, but does not establish agreement:

| Quantity | Worst prefault error through 1 s | Required limit |
|---|---:|---:|
| Bus voltage NRMAE | 0.0306921 | 0.001 |
| Generator-speed NRMAE | 0.00787252 | 0.001 |
| Rotor maximum absolute error | 24.4954 degrees | 0.02 degrees |

A separate 20-ms audit control retains all 45 grid-forming coordinates and
rates. Their largest cold-initialization derivative is below 8.59e-13. Its
43 rows and 40 physical channels are exactly equal to the corresponding longer
run prefix, proving that these observations do not perturb the calculation.
Over this short interval only, all fifteen required channels pass: worst
voltage NRMAE 0.000479862, speed NRMAE 2.79384e-6 and rotor maximum error
0.000614361 degrees. The subsequent prefault divergence remains unresolved.
Large early active-power-order rates in other renewable controllers are
diagnostic observations, not proof that their initialization is incorrect;
the native power/speed selector can itself produce nonzero initial rates.

Case 2 was separately rerun through 1.2 s against committed controller
snapshots `85d556253` and `98badd136`, with the production mixed-load matrix
option. Both reproduce all 4,810 physical channels and 1,205 rows exactly
against the preceding prefix. Inactive-state reporting changes are retained
in their snapshot differences. Neither refresh improves that early physical
trajectory; later-time equivalence has not been inferred from it.

The Case 6 integration run uses the frozen `85d556253` controller snapshot
plus the explicitly recorded grid-forming implementation. It does not include
the later plant-controller commit `98badd136` or concurrent working edits.
Sources, runtime hashes, commands, original references, failed controls and
comparisons are retained in the private evidence set
`2026-10-19-grid-forming-integration-and-controller-refresh`.

All six cases and all 36 disturbances remain required. The original acceptance
limits remain unchanged; full-system accuracy and the runtime target are open.
