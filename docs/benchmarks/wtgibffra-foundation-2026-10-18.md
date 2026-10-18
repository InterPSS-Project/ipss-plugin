# WTGIBFFRA published-equation kernel foundation

Status: **kernel and partial native replay only; not loadable or fully verified**.
Approved native renewable loadability remains **20/27**, with seven unfinished
end-to-end implementations. No full Texas2k dynamics are required or executed.

## Implementation

`WtgibffraData` preserves one input ICON (frequency bus) and the exact grouped
45-CON order: deadband; six each of power, boost, deficit, rise, peak, fall and
recovery; frequency-filter time; cooldown. Six runtime-allocated native ICON
slots do not imply six input fields. Parameters and exposed arrays are immutable.

`WtgibffraModel` implements six floor-selected incident-power bands without
interpolation, captured machine-base incident power, a latched rise/peak/fall/
recovery sequence, cooldown, inactive callback before rearming and nonpositive
cooldown disabling subsequent responses. Invalid global ordering/entirely
unavailable columns disable the kernel; an unavailable selected band stays idle
without disabling other bands. Four published state coordinates and seventeen
derived VAR coordinates are observable; derived flags are not promised to be
identical to vendor callback storage.

The frequency lag retains explicit modified Euler. Predictor-only events and
captured parameters are rolled back before the corrected endpoint is committed.
The opt-in two-step path remains separate; zero-duration refresh consumes no
history. No integration-method replacement or trace-fitted parameter is used.

An independent constant-rate regression initially failed: after 200 x 0.5 ms
steps, a ramp could be infinitesimally below its mathematical endpoint and remain
in FALL for an extra step. Compensated ramp/clock addition and ULP-scale endpoint
equality fix this. Compensation is snapshotted/restored and cleared on reset.
Tests also prove this cannot become a timestep-sized early-transition deadband.

## Authoritative contracts and explicit discrepancies

Consulted installed PSS/E 36.7 Model Library section 29.1, printed pages 942-949
(physical pages 954-961), and previously inspected source diagrams. Library PDF
SHA-256: `40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
Independent published sources:

- [WECC memo](https://www.wecc.org/sites/default/files/documents/products/2024/Memo_RES_Modeling_Updates_021623_Rev27b.pdf),
  equation p30, WTG diagrams pp32-33 and REECD Paux connection p12;
  SHA-256 `10f1c4c2d965314f189d7383a260a2d14d1ebcdeb7ae97d056fce91f75447ebe`.
- [PowerWorld WTGIBFFR_A](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Paux%20Controller%20WTGIBFFR_A.htm).
  Its typed ordering/base extensions are not native DYR aliases.

The published minimum recovery is
`max(0, boost/deficit*(Trise + 2*Tpeak + Tfall) - Tfall)`; the effective duration
is the maximum of that and the user duration. Previous cold native discriminators
report approximately 0.52 s where this equation gives 0.70 s, then complete
automatic/too-short recovery in one sample. The kernel preserves the published
balance, not that discrepancy. Sufficient user recovery (1.2 s here) avoids it.
These observations are not a claim that every vendor/version behaves alike.

Paux is an active-power increment into REECD after its power lag and **before**
the voltage divider. It is not a weak-grid absolute Pref or a REECA1 port.
The prerequisite REECD correction is committed in `972fed2ba`.

## Independent native references

Seven profiles, each cold-run twice, have byte-identical raw CSV outputs:
long recovery at 0.5/0.25 ms, zero selected-band deficit, zero peak hold,
persistent underfrequency, power below P1 and unavailable selected-band boost.
They retain **40,013 nonnegative-time input rows and 3,769 checkpoints**.
All raw channels are finite; all nonnegative native timestamps are strictly
increasing. Every native CON is checked against the declared float32 value,
and all staged input/output file digests and model allocations are audited.

The privately archived native probe uses a public three-bus RAW, REGCA1/REECD and a
PLBVF1 frequency waveform. Each native log reports initial conditions O.K.
See the [reproduction/encoding contract](../../ipss.test.plugin.core/src/test/resources/wtgibffra-kernel/README.md).
Full input tokens are losslessly gzip/Base64 encoded, not resampled; checkpoints
retain native float timestamps and all four STATEs/seventeen VARs. Selection
includes every 5 ms, the last sample and four rows either side of every native
mode transition. Negative initialization history remains in the sealed raw data.
Replay uses nominal timestep and both recorded endpoint inputs, with FDEV3+1.

Frozen raw/input/checkpoint bundle SHA-256:
`1cc1cbb37532a2e576f166c551b7d556ba05be861bcfcaae2e20b4b8c8ebf8f6`.
Native DSMDL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.

The ignored-data pair is deliberately excluded: invalid ordering logs "Model
ignored", its four STATEs remain zero, but two unused VAR slots differ by up to
1.85e-43 between cold runs. Native status remains -1 while the typed kernel uses
semantic DISABLED (-3). No-rearm/automatic-recovery probes also remain diagnostic,
not accepted trajectory references. All eighteen new runs remain in the archive.

## Partial comparison meaning

Permanent tests require identical phase sequences, bounded cumulative timing
error, and all 21 state/VAR channels in sustained matching modes. Native float
constants and callback scheduling can add a sampled tick at successive handoffs;
the second persistent cycle makes that accumulation visible. The timing gate
allows one tick per handoff plus 0.1 ms float-clock allowance. Ramp error is
bounded analytically by slope times the separately bounded phase-start lag plus
one tick and a float-accumulation budget. Inputs, times and gains are not fitted.

Continuous frequency STATE is checked at **every checkpoint**, including callback
windows. Discontinuous resets/flags retain raw maxima in test output; those
windows are excluded only from sustained state/VAR comparisons. Typical raw
reset differences reach 0.024/0.036/0.012 pu and mode-code differences reach 6;
they are **not** accepted by a broad amplitude tolerance.

For the long-recovery coarse/fine profiles, sustained rise/fall/recovery maxima
are approximately 6.003e-5/9.001e-5/2.712e-6 and
3.006e-5/4.510e-5/1.400e-6 pu. The largest retained frequency STATE error across
profiles is approximately 2.62e-6 pu. Captured-power error stays below 4.18e-7 pu.
These describe the retained checkpoints, not an exhaustive all-sample/full-model
acceptance claim. Exact callback parity and automatic recovery remain open.

## Verification and remaining work

The tested scope is committed baseline `972fed2ba` plus this slice. Concurrent
uncommitted ST1/parser and WECC240/JAX work is excluded, preserved and unstaged.
Core 1.4.3 JAR SHA-256:
`f7380328ff7b4d81686a5aae2c172d964013ae74deb81c964050a6a74db0d4ad`.

- Focused: 22 kernel unit tests and three partial native replay/integrity tests.
- Plugin-core regression: 227 tests; six optional skips; zero failures/errors.
- Registered SMIB/model/catalog/solver gate: 1,254 tests; one existing diagnostic
  skip; zero failures/errors. Both final Maven commands exit zero; totals are
  read from fresh Surefire XML.

```text
mvn -q -pl ipss.plugin.core -am test -Pextended-tests,isolated-verification -Dverification.run.id=wtgibffra-foundation-units-20261018-g --fail-at-end
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=wtgibffra-foundation-smib-20261018-h -Dtest=DynamicModelSmibTestSuite,DynamicModelCatalogTest,PsseStaggeredEulerSolverTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
```

Sealed private evidence is under
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-wtgibffra-foundation-final`:
eighteen new native runs, raw cold-pair audits, exact probe/generator, source
documents, rejected focused gates, final XML/logs and tested scoped sources.
The prior six recovery discriminators remain in the sealed REECD Paux archive;
their audit is retained here as well. Neither old archive is overwritten.
Repository policy keeps simulator-driving Python programs in the private archive;
the native probe is not force-added through the ignore rules.

The first mistyped selector matched no tests and is not evidence. The subsequent
failing constant-ramp and too-tight single-cycle timing gates are retained with
their fixes/diagnosis; no previously accepted model envelope was relaxed.

Next production slice: strict native catalog/parser, deferred REGCA/REGCB +
REECD Paux attachment, remote/local/missing-frequency-bus handling, duplicate and
malformed records, GNET/model removal, initialization/trip/reconnect, and coupled
SMIB references at two steps. Legacy wrapper allocation must be proved, not
guessed. Alternate bands/base profiles and repeatable PowerWorld artifacts also
remain open. Neither this kernel nor the prerequisite Paux fix closes those gaps.
