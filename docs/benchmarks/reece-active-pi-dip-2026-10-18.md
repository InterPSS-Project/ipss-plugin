# REECE native active-PI voltage-dip correction

Status: a native 36.7 equation correction with bounded recorded-input evidence;
not complete coupled trajectory acceptance. Baseline `73bd45865`.

## Discriminator and source difference

The installed native 36.7 diagram (printed page 866) marks both active PI
STATE7 and power-order lag STATE5 as frozen during Voltage_dip. The executable
does not freeze STATE7. Four converged, cold-paired REGCB/REECE fault runs from
the [preceding slice](reece-native-import-2026-10-18.md) prove that distinction
at 0.5 and 0.25 ms. No new native campaign or new reference data is claimed.
The model-library and executable fingerprints remain those recorded there.

The permanent discriminator finds 132/265 positive STATE7 increments during
the dip while STATE5 is bit-for-bit unchanged. Their total growth exceeds
0.05 pu. Exported Pmeas and PI slopes straddle network callbacks: their maximum
same-row midpoint derivative residual is 0.06148/0.03121 pu/s. That derivative
calculation is retained as diagnostic output, not asserted as same-row equation
parity. The state growth itself disproves the printed freeze behavior.

## Implementation and replay

The explicitly native-version Reece1Model now integrates its active PI during
the dip, retaining integral bounds and inward release. Pord and reactive states
retain their existing freeze rules. No proportional gains, initial states,
integration method, reference timing or parameters are fitted or changed.
This does not claim that the PowerWorld/WECC dialect has the same exception.

Replay consumes only native terminal P/Q/V and the unchanged DYR parameters.
It initializes from terminal equilibrium, retains both event sides and all
nonnegative-time samples, and advances predictor/corrector with endpoint inputs.
No observed PI state, filtered measurement, current bound or prepared reference
is fed back into the implementation.

Before correction, maximum PI-state error is 0.1029503 pu at 0.218499 s.
After correction it is 3.96424e-5 pu at 0.152500 s (0.5 ms), and 1.02484e-5 pu
at 0.151999 s (0.25 ms). Regression bounds are 5.2e-5/1.4e-5 pu, with explicit
step-halving improvement. Separate unit tests cover upper/lower PI bounds,
outward blocking and inward release while Pord remains frozen.

The independent coupled diagnostic also improves PI-state error from
0.103116/0.103164 to 0.00134949/0.00108689 pu. Remaining coupled maxima are
P 0.0281026/0.0280380 pu, Q 0.0173520/0.0166364 pu, and Bus 1 voltage
0.00270804/0.00262849 pu. These do not establish full convergence or acceptance.
Reactive current-limit feedback and callback publication remain open.

## Gates and archive

Final XML audit: 35 focused checks without skips, 267 plugin-core tests (six
optional skips), and 1,308 registered SMIB/model tests (one existing diagnostic
skip), all with zero failures/errors. Four scoped Java files match the tested
snapshot; remaining tracked baseline Java/POM files match HEAD after declared
line-ending normalization. The new partial-conformance class is confirmed in
both focused and full-suite XML, not merely registered in a different suite.
All 960 files of the reused raw-run archive pass SHA-256 checks. The unrelated
untracked WECC240/JAX test remains excluded. No full Texas2k dynamics are run.
Core 1.4.3 SHA-256:
`f293ca958f363b814681f76bf310447d826fb5996e3bacb95aa5789a6a4b33e7`.
The dependency fingerprint matches before and after the gates.
The registered SMIB suite includes the new native discriminator/replay class.
Private evidence is retained under
`testData/private/verification-artifacts/2026-10-18-reece-active-pi-dip`, referencing
the preceding immutable raw-run archive rather than replacing its output.
