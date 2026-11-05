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
