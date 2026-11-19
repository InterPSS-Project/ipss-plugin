# Cold generator-base integration controls — 2026-11-04

The focused run executes **33 tests: 31 pass, two fail, no errors or skips**.
Plugin source was clean `428c44d36` at launch, with isolated core `7f2a4056a`
and explicit artifact `1.4.8-cold-generator-base-candidate`.

| Selection | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| IEEE9 dynamic tests | 9 | 0 | 0 | 0 |
| SVS trip ordering and stamp boundary | 9 | 0 | 0 | 0 |
| WSIEG1 reactivation | 15 | 2 | 0 | 0 |

The IEEE9 generator energization check that failed in the preceding full run
now passes. Both remaining failures are the existing first and second WSIEG1
reactivation trajectory invocations. Their complete failure messages and
assertion stack traces are byte-identical to the retained
`full-ordinary-event-shared-svs-20261104` baseline. The reported state maxima,
limits and low-pressure power NRMSE are unchanged. No new failure identity
appears in the selected classes. This comparison concerns assertion traces;
it does not independently claim equality of every simulated trajectory sample.

Actual XML counts agree with the completed run. No rerun was performed for this
audit. The narrow selection does not replace full compatibility validation or
resolve the remaining WSIEG1 model gates. No tolerance or expectation changed.

The bounded log, all four XML reports, source files pinned to the launch commit,
POMs, fixture settings and exact failure comparison are archived privately at
`verification-artifacts/cold-base-integrated-controls-20261104`.
Archive SHA-256:
`333f758fc4bd5a8b51fafe97e47bacadea6ccdab99aa965557054a9214541d5f`.
Comparison baseline archive SHA-256:
`e2f60baa5406b2784b654fbfbd1aab78c2fb3eaf0c518ab19f876bfff2f84e56`.
Loaded core artifact SHA-256:
`38e8e89ae416e8563646f179c1e0309f266ea4f7ee2ab25f6b2b27e506683149`.

## Complete registered validation

The completed full run at plugin source `fbc98ce57` and the same corrected core
executes 4,959 tests: 228 failures, two errors and 13 skips across 749 XML
reports. Compared with the preceding ordinary-cache full run, the IEEE9 cold
energization regression is the sole resolved failure. There are no new failures,
removed tests or new skips. All 13 added controls pass: eight initial-bound
controls, one release-stage discriminator and four overlapping-protection
controls. The remaining 230 failed/error identities and messages are unchanged.

This result qualifies the regression correction, not completion of the model
work list. Existing independent trajectory and runtime-error gates still prevent
full acceptance and main-branch promotion.

The bounded log, complete XML, source provenance and exact identity comparison
are retained privately at
`verification-artifacts/full-cold-base-integrated-20261104/validation.zip`,
SHA-256 `adb977db6fea7485196d500ca3e30b3a0cc207f335d8b37dd56ca12f411ad6ee`.
Subsequent recorder/comparison-only changes receive separate focused validation;
they are not included in this pinned full-run claim.
