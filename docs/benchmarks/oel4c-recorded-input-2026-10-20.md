# OEL4C permanent measured-input comparisons and corrector expiry fix

## What changed

Twelve whole PowerWorld CSVs are now portable permanent resources. Gzip/base64
encoding preserves every original byte; raw and line-ending-canonical SHA-256
values are separate. The fixtures retain all 21 channels and 18,032 representative
rows, including both occurrences of fault-event timestamps. The index records
both cold runs, exact model readbacks, source/input/output hashes, and the
100-MVA machine base with default reactive maximum 1 pu. No reference trace is
replaced, resampled or fitted.

Two permanent test classes verify complete reference integrity/channel meanings
and replay measured terminal reactive power through the actual OEL4C kernel.
The kernel receives only Q, original parameters and the declared default Qmax;
it does not receive reference timer/mode/output or integral states. Its integral
and timer start from their model-defined zero initialization.

Each replay reports output pu, controlled-error pu and elapsed seconds separately.
Every channel has all-row, regular, pre-event and post-event RMSE, sample counts,
maximum error, peak time and peak event side. Empty event groups explicitly have
zero samples, not acceptance evidence. The controlled-error serialization gate
remains 1e-6; output/timer numerical acceptance is independently scored against
the user-specified RMSE < 0.001. Failed output/timer comparisons remain failed.

## Real implementation defect localized and fixed

The enforcing timer could expire from a predictor that crossed the PI upper
boundary, even when the corrected PI output remained negative. The following
analytic regression failed against the original kernel:

```
Qref=1, delay=0.01, Kp=0, Ki=1, Vmin=-1
After overload and initial recovery: integral=-0.001, elapsed=0.02
Next recovery error=+0.15, step=0.01:
    predictor output=0
    corrected output=-0.00025
    expected retained elapsed=0.03
    old retained elapsed=0
```

The accepted recovery-history decision now uses the corrected PI output. The
regression was run red before the production change and green afterward. Core
integration and PI equations, all constants, initial states and timestamps are
unchanged. No shared CML class is modified.

## Results and remaining discrepancies

Controlled-error conformance passes all twelve cases, worst RMSE approximately
3.59e-8 pu. The analytic accepted-state bug is fixed, but eight of twelve cases
still fail at least one all-row output/timer RMSE criterion. Four cases (the flat
and lower-saturation profiles at both steps) pass all three numerical quantities.
This is not full coupled or whole-model acceptance.

All regular-row output RMSE values are below approximately 5.05e-5 pu. The largest
output differences occur at zero-time fault-event rows: for example, the delayed
case's clearing export holds its previous negative PI output while its controlled
error has already changed sign. The replay's algebraic output is recomputed from
the changed input. Both rows and their differences remain in the all-row score;
the regular-row result is localization only, not a replacement acceptance test.

The integral-only timer still fails during recovery expiry. Fine-step timer RMSE
decreases from approximately 0.00515 to 0.00364 seconds with the corrector fix;
the coarse-step value remains approximately 0.00731 seconds. Output integration
and expiry/export conventions still need independent investigation. No timestep
shift, parameter fit, wider tolerance or sample removal is used.

The [foundation report](oel4c-foundation-2026-10-20.md) retains the earlier results
and source contract. Native runtime/record import and physical host coupling are
not implemented by this slice; OEL4C remains absent from the loadable catalog.

## Remaining TODO

- [x] Preserve all twelve original references as permanent portable fixtures.
- [x] Add full measured-Q replay and per-channel/event-side diagnostics.
- [x] Fix predictor-based premature expiry with a red/green analytic regression.
- [ ] Resolve remaining output export-phase and integral-only expiry discrepancies.
- [ ] Add bounded host attachment with machine-base/Qmax conversion.
- [ ] Add strict record import and ownership/preprocessing tests.
- [ ] Run typed/imported flat and three-cycle-fault SMIB comparisons on core GENROU.
- [ ] Close all required independent coupled channel gates at RMSE < 0.001.

No full Texas2k dynamics are run.

Final gates: 292 focused, 435 core and 2,244 registered scoped SMIB/model tests,
zero failures/errors and seven existing skips, plus 253 offline checks. Only the
OEL4C kernel production class changes; all 86 earlier limiter exports and the
24 raw cold-reference runs remain unchanged. Evidence is retained in the private
`2026-10-20-oel4c-recorded-input` seal, including the original failed analytic
regression, corrected pilot and every per-channel report.
