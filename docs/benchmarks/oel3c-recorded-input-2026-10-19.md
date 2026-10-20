# OEL3C recorded-input conformance and reference initialization discrepancy

## Outcome

The production OEL3C kernel passes the user-specified RMSE < 0.001 criterion
against six exact native recorded-measurement cases, covering Efd/Ifd/Vfe
selections at 0.0005 and 0.00025 s. No production equation, pickup, initial
integrator state, integration method or old regression bound is changed.
This verifies the tested zero-filter kernel realization, not complete network,
measurement-route, takeover or all-parameter coverage. No full Texas2k dynamics
are run.

The PowerWorld discrepancy has an independently reproduced initialization
counterexample: entered pickups 0.4, 0.5 and 0.6 produce identical complete raw
trajectories, despite different matching parameter readbacks. The observed
active-branch equation uses a reference consistent with the initialized
measurement instead of the entered pickup. That behavior is not substituted
into InterPSS or accepted as an exact native-model oracle.

## Native playback contract

Six portable complete CSV fixtures preserve all 9,030 representative raw rows,
including two initialized-history rows, both event occurrences and the original
terminal observations. Six exact cold pairs underlie the fixtures. Original
CSV bytes, source dynamic inputs, parameter readbacks and hashes are retained.
The playback test is automatically discovered by the scoped SMIB suite.

For these zero-TF cases, recorded scaled measurement divided by entered scale
is the replay input. The kernel initializes its integral to the source-defined
zero, not to a fitted recorded state. Nominal protocol timesteps drive the
predictor/corrector; history and duplicate event observations do not advance
integration. Raw clocks and every row remain in the comparison. The measured
input channel is a play-in/self-consistency check, not proof of physical-input
routing through the machine or exciter.

Each of six channels is scored separately: scaled input, integral, reference,
error, gained error and proportional contribution. Native reference is 0.5 in
every raw row. The largest integral RMSE is about 1.82e-6 pu. The largest
proportional RMSE is about 4.61e-4 pu; it falls to 3.26e-4 pu at the finer step.
The proportional peak remains about 0.0146 pu at a switch crossing. That peak
and its timestamp are printed, not hidden by dropping samples or shifting
clocks. RMS acceptance does not establish a 0.001 pu pointwise envelope.

Total limiter output is not independently exported in these native fixtures;
the test therefore does not claim an independent total-output comparison by
reconstructing an oracle from its own state equations.

## PowerWorld pickup discriminator

The [published OEL3C diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Over%20Excitation%20Limiter%20OEL3C.htm)
places entered pickup before the exponent and error summation. On an active,
unclipped proportional branch, the recorded integral/output/measurement imply:

`reference^K1 = scaledInput^K1 + (output - integral)/(Kp * gain)`.

The new audit evaluates the entered-reference equation directly and reports
that inferred quantity only where observable. It also tests the separately
declared initialized-measurement hypothesis without replacing the model data.
Inactive and clipped observations cannot certify the reference identity.

In the existing cases, the inferred reference is near 0.4890948832 for nominal
Efd/Vfe and 0.368588537 for Ifd, rather than 0.5. The initialized-measurement
hypothesis has output residuals below 6e-9 pu. The entered-reference equation
does not match. This holds at both timesteps and is separate from the nominal
Efd selector's previously established measurement mismatch.

Two predeclared Vfe profiles change only pickup, to 0.4 and 0.6. Each was cold
regenerated twice, producing four complete 22-channel runs and 4,012 raw rows.
All model parameters and options round-trip without correction. Both profiles
are byte-identical to the existing pickup-0.5 CSV. Portable discriminator
metadata links that existing whole raw resource, records both original-byte
and newline-normalized hashes, and preserves all readbacks. No duplicate
trajectory is synthesized or edited.

This establishes an observable reference-initialization discrepancy, not the
reference tool's internal source-code mechanism. Keep these comparisons
diagnostic. Changing native pickup to imitate the reference tool would hide
the disagreement rather than implement the published model.

## Test-configuration compatibility

A concurrently committed storage guard caused the existing core policy gate
to fail because it read JVM-wide settings. The narrow compatibility correction
reads the same positive 64-bit budgets from the existing per-module immutable
JSON configuration. Defaults and storage enforcement remain unchanged. New
checks cover immutable overrides, large budgets and invalid values; the
configuration policy is not weakened.

The latest plugin also requires newer engine preprocessing APIs. Verification
uses a separately pinned matching engine and isolated dependency cache, leaving
all earlier jars/caches unchanged. Earlier compiler/policy failures remain in
their bounded logs and are not counted as passing gates.

## Remaining work

Final gates: 106 focused, 431 core and 2,058 scoped SMIB/model tests, zero
failures/errors and seven existing skips, plus 215 offline checks. The new
six-profile replay gate is included in both focused and broad discovery. The
storage-policy correction is covered by nine guard checks and the unchanged
repository configuration-policy gate. All earlier reference fixtures and
protected engine pins remain unchanged.

- [x] Exact native zero-filter recorded-input RMSE acceptance, with all history
  and event rows retained and pointwise maxima reported.
- [x] Reproduce the reference pickup discrepancy with pickup-only cold pairs.
- [x] Reject metadata-only/reference-identity success in permanent diagnostics.
- [ ] Reconcile any remaining closed-loop host, physical-input and observation
  coordinates independently of this pure-kernel result.
- [ ] Extend exciter hosts, takeover and other parameter/filter/limiter profiles.
- [ ] Complete the remaining approved models in the development plan.
