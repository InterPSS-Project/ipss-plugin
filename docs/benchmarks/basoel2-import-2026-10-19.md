# BASOEL2 documented-contract record import

## Scope

The runtime catalog and strict reader now accept exactly three integer selectors
and twelve real constants for BASOEL2. Deferred attachment permits the limiter
record before or after its exciter. The bounded host is an existing AC1C
summation port; missing, disabled, takeover and occupied ports remain rejected.
No new machine implementation is introduced: the SMIB fixture uses core GENROU.

The reader explicitly selects `POWERWORLD_DOCUMENTED`; its import report names
that policy and states that reference-tool/coupled acceptance remains open.
This implements the requested PowerWorld/WECC documented contract, not behavioral
emulation of every installed reference-tool version. Typed construction still
requires an explicit timer policy and retains both documented policies.

Parameter order is:

```
OELflag VHzflag InputFlag
TF Kp Ki Ishort Tshort Imedium Tmedium Icontinuous Tcontinuous TRVHz KVHz Vmin
```

The extra twelve internal integer work slots reported by one reference tool are
not entered parameters. Fourteen, sixteen and twenty-seven parameter records
are rejected; fractional selectors, unsupported input selectors, nonfinite
constants and negative time constants/delays are also rejected. Preprocessing
removal skips the complete stack before model validation, including malformed
records. No sibling dynamic-data dialect is consulted.

## Verification

Twenty-two strict-import tests cover distinct field ordering, multiline records,
host ordering, invalid data, missing targets, ownership, parser reuse and both
preprocessing removal modes. Sixteen imported flat/fault SMIB profiles cover
eight configurations at 0.5 and 0.25 ms. They retain full event-side observations
and are checked byte-for-byte against the typed PowerWorld-policy path.
The three-phase fault lasts three 60-Hz cycles, from 0.05 to 0.10 seconds;
simulation continues to 0.5 seconds. Both stationary profiles include the
initially active V/Hz case. This equivalence check verifies import wiring, not
independent simulator agreement.

The source/reference contracts and independent diagnostics remain in the
[PowerWorld benchmark report](basoel2-powerworld-2026-10-19.md) and
[typed-runtime report](basoel2-runtime-2026-10-19.md). This slice changes parser
and inventory behavior only, not continuous equations or integration methods.

Final gates: 250 focused tests, 431 core unit tests and 2,202 registered scoped
SMIB/model tests, zero failures/errors and seven pre-existing skips, plus 247
offline checks. All sixteen imported CSVs are byte-identical to the corresponding
typed outputs at every sample/channel. Seventy prior trajectory exports and
twelve portable reference resources are unchanged. The evidence seal is
`2026-10-19-basoel2-import` under the private verification-artifact directory.

Earlier attempts are retained: one test incorrectly expected an empty strict
input to succeed, and one core gate saw the old generated matrix in the isolated
snapshot. Correcting the test expectation and synchronizing generated documents
resolved these without changing model equations or weakening acceptance.

## Remaining work

- [x] Implement exact standard record cardinality and field order.
- [x] Declare documented timer policy in successful import reports.
- [x] Exercise the imported runtime on flat and three-cycle-fault SMIB profiles.
- [ ] Resolve physical-input/filter and export-phase discrepancies against the independent reference.
- [ ] Resolve remaining reference initialization and threshold counterexamples.
- [ ] Establish full coupled acceptance on every channel at RMSE < 0.001;
  preserve peak and event-side errors, all samples, and consistent per-unit bases.
- [ ] Broaden compatible hosts only after their measurement and summation contracts are proved.

`LOADABLE` is not `VERIFIED`. Native-tool semantic parity and full coupled
trajectory acceptance are not claimed. No full Texas2k dynamics are run.
