# Ordinary generator-cache compatibility validation — 2026-11-04

Both default compatibility modules pass against isolated core `2c92a6a70`,
artifact `1.4.8-ordinary-generator-cache-candidate`, with plugin source
`361323039c752d2cf367d4d65a733f19527cdb24` verified clean at launch.

| Module | Tests | Failures | Errors | Skips | Class reports |
|---|---:|---:|---:|---:|---:|
| Plugin core | 494 | 0 | 0 | 4 | 78 |
| Three-phase | 468 | 0 | 0 | 28 | 91 |

All test identities and all skip identities match the retained preceding
compatibility baseline. No tests were added or removed, and there are no new
skips. The audit compares actual executed XML reports, not only reactor status.

The run used a separate isolated build profile, a bounded log and a 2 GiB Java
test heap. It did not enable extended/private test profiles. These module results
do not replace the complete registered suite or qualify its
remaining model-reference gates. No runtime code, default dependency, test
threshold or test selection was changed during this validation.

Complete output, all 169 XML reports and exact identity comparisons are retained
privately at `verification-artifacts/compatibility-ordinary-cache-20261104`.
Archive SHA-256:
`ce5326e4b6e3b57a7fa9f5bc145a1c080c6d90b161c741030bd3b1ea10517115`.
Baseline archive SHA-256:
`e7a920d90f43b0204cc1aea1b2e9cd2534478167300e85c376430186f716e191`.
Loaded core artifact SHA-256:
`5cdef34678d1c2c758e0b488518cfbafc074c79432039dcf95c9184891df732e`.

## Complete registered suite

The subsequently completed registered run executes 4,946 tests with 229
failures, two errors and 13 skips. All 228 previously failed/error identities
remain. The added failures comprise two known WSIEG reactivation comparisons
and one regression in the existing IEEE9 cold generator-energization test.
There are 40 additional executed identities and none removed.

The energization regression prevents promotion of this core candidate. The
unchanged test passes against the preceding candidate. Investigation identified
an initially inactive generator whose impedance-base multiplier had not been
prepared before refreshing the generator aggregate; later matrix preparation
cannot repair the already cached equivalent. A correction requires independent
initially inactive, unequal-base controls and the unchanged integrated test.

The complete bounded log, all 746 XML reports, source/artifact identities and
trajectory comparisons are retained in the private archive
`verification-artifacts/full-ordinary-event-shared-svs-20261104/validation.zip`,
SHA-256 `e2f60baa5406b2784b654fbfbd1aab78c2fb3eaf0c518ab19f876bfff2f84e56`.
Passing compatibility modules do not establish that this candidate is ready
for the main branch.
