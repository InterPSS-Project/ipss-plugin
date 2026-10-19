# PSS5C band-pole contract and permanent local linearization

## Outcome and scope

The installed reference engine's band-state responses are much closer to
reciprocal raw-field scaling, `-k/F`, than to the published frequency scaling,
`-k*F`. This is a release-specific diagnostic finding, not an accepted new
equation or a universal statement about simulator implementations.

Production PSS5C equations, native reader, shared integrator, existing reference
files and trajectory tolerances are unchanged. Native trajectory acceptance
remains **open**: the previous exact-speed replay discrepancy of about
0.01626 pu has not been resolved by this slice.

The new `Pss5cLocalLinearizer` is a permanent read-only diagnostic for the
published full-order controller. This slice is SMIB-only; no full Texas2k
dynamic simulation is run.

## Source contract

The [PowerWorld PSS5C reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS5C.htm)
describes all four band-frequency fields in hertz. The native thirty-constant
foundation and its private source pages retain the published transfer-function
contract separately from the typed twenty-one-value builder.

The source document SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The installed model-library SHA-256 is
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
Exact executable/release provenance is in private evidence manifests. All
eighteen new reference batches use the same engine/document identities.

## Discriminator and repeatability

`tools/pss5c_native_pole_probe.py` stages the existing tiny SMIB network and
exact thirty-constant record. Each case independently initializes and solves
the network, reads back all constants and the 1/30/13/6 internal allocation,
and completes startup history before making a perturbation.

The documented state-space response API applies one signed perturbation to
STATE and its integration storage, with initial derivative zero. This is an
explicit state-space experiment, not a trajectory comparison with reference
states injected into InterPSS. Each batch contains a control and positive and
negative perturbations of eight band states: seventeen independent resets.
All constants are rechecked unchanged after each case.

Four raw-field profiles (original, all-one, all-half and all-two) run at
0.0001 and 0.00005 seconds. An additional original-profile pair halves the
perturbation from 1e-6 to 5e-7. All nine pairs reproduce response JSON, staged
network, dynamic records and probe bytes exactly: 306 independent cases.
All initialization logs pass, with no network nonconvergence or nonfinite
states. The initial five-case pilot is retained separately, not counted as
a cold pair.

For each adjacent band-state pair, signed responses form the two-column
sensitivity matrix S. The injected matrix must be the identity. The estimator
uses the last two transitions after startup:

```
M = S4 * inverse(S3)
lambda = log(eigenvalue(M)) / actual_clock_interval
```

Eigenvalues do not depend on an invertible mixing/rescaling of these two
coordinates. A synthetic mixed-coordinate counterexample tests this property.
Missing pairs, mismatched clocks, unrecorded cross-state injections, singular
responses, nonfinite values, changed raw files and escaping manifest paths
are rejected; no coordinate transform, pseudoinverse or time shift is fitted.
This does not establish the meaning of every internal state or eliminate
possible coupling outside the observed band subspace.

Every startup observation, complete binary history and raw log is retained.
Using a late transition for pole diagnosis is not permission to drop early or
event-side rows from trajectory acceptance. The API does not expose an
authoritative state-derivative channel here; measured rates are discrete
response estimates, not exact derivatives.

## Observed counterexample

At the original very-low raw field near 0.01, the two measured fine-step rates
are approximately -688.02 and -573.44 per second. Published `-k*F` gives
approximately -0.06883 and -0.05736; reciprocal scaling gives approximately
-688.30 and -573.60. At raw field 9, observed fine-step rates are approximately
-0.76408 and -0.63672, versus published -61.947 and -51.624.

At raw field one the hypotheses coincide. Changing all four raw fields to
one-half or two produces the inverse trend across all four bands, at both
steps. Half-amplitude runs retain the same distinction. The fastest pole
approaches the reciprocal hypothesis as the step is halved; slow poles are
single-precision/finite-difference limited, so monotonic refinement is not
claimed for every rate.

This localizes an important contract discrepancy independently of a guessed
band-state coordinate. It does not decide whether the cause is native field
units, documentation, initialization conversion or installed implementation.
No automatic reciprocal decoder or production `k/F` change is introduced.

## Permanent analysis API

```java
var local = Pss5cLocalLinearizer.analyze(pss5c.getData());
var a = local.stateMatrix();
var b = local.inputVector();
var modes = local.eigenvalues();
```

The snapshot supplies thirteen stable state names, the analytical continuous-
time A/B matrices, complex eigenvalues, eight band poles and a bypass flag.
Arrays are defensively copied and lists immutable. The analysis never attaches
to or changes the running controller. Unsupported/nonfinite matrices are
rejected rather than regularized. Disabled band memories and whole-controller
bypass report frozen modes matching runtime behavior.

This is an internal-controller diagnostic, including potentially unobservable
modes. It does not close exciter, machine or network feedback, calculate output-
limiter sensitivity, or prove whole-system stability. Stable internal poles
are not a model-verification badge. An unstable-coefficient test confirms that
the API reports positive-real modes without repairing the input parameters.

Nine focused tests reconstruct every actual runtime predictor/corrector
coordinate at nonzero states, verify sensor/band spectra and scaling, prove
inactive limits cannot alter A/B, and cover defensive copies, frozen memories,
bypass, invalid matrices and unstable coefficients. The test class is also
registered permanently in `CorePluginTestSuite`.

## Regression and reproducibility

The tested source is committed baseline
`625aa7bbe58407268da456c0cc368e3aa027967c` plus the read-only linearizer, its
tests and suite registration. Concurrent uncommitted governor/parser changes
in the shared checkout are excluded from the isolated source snapshot; these
results do not certify those unrelated edits.

The isolated build uses `tmp/wtdtb1-powerworld-verify54b/pom.xml`, profiles
`isolated-verification,extended-tests`, offline cache
`target/repca-native-verification-m2-20261019` and core identity
`1.4.3-salient-refresh-20261019`. The core jar SHA-256 remains
`21519010c48bd265678743df7e98d3dcea530f5ceda1ccd7921d123d7f6b5333`.
Integration selections use `-am`. Final XML reports confirm:

| Run identifier | Tests | Failures/errors | Existing skips |
|---|---:|---:|---:|
| `pss5c-poles-focused-final-1` | 132 | 0 / 0 | 0 |
| `pss5c-poles-units-final-1` | 427 | 0 / 0 | 6 |
| `pss5c-poles-smib-final-1` | 1,860 | 0 / 0 | 1 |

All 89 offline checks pass, as do the wording guard, previous dialect audits
and retained native replay diagnostics. An initial sandboxed offline attempt
hit temporary-directory permission errors; the complete authoritative rerun
passed outside that restriction. No test assertion was weakened.

The source audit reconciles 1,883 Java/build files and 139 unchanged resource
parts. Compiled native data/runtime and typed runtime classes match the prior
foundation byte-for-byte; all four generated native flat/fault CSVs and the
typed comparison's error vectors/times are unchanged. The new main analysis
class is identical across all three final builds.

The private seal destination is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-19-pss5c-pole-contract`.
It retains raw reference histories, pilot, final regression reports,
source/dependency identity, fixed-hypothesis diagnostics and the previous
foundation evidence inventory. Existing archives and pinned core jars are
preserved.

## Remaining work

- Resolve the exact native frequency-field convention and decide an explicit,
  source-backed compatibility policy before changing production equations.
- Resolve low-transducer behavior, raw VAR/output timing and all thirteen
  internal-state observation coordinates.
- Implement and verify reduced-order transducers, zero gains, remaining
  active limit branches and lifecycle profiles.
- Obtain independently justified all-channel coupled trajectory acceptance.
  No acceptance bound is widened and no measured-error-derived gate is added.
- Continue the wider model-coverage plan; this diagnostic slice is not complete
  PSS5C verification or completion of the overall plan.
