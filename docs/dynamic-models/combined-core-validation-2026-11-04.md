# Combined core integration validation — 2026-11-04

The isolated combined core restores the documented EDCLF equation and retains
the converter corrections. Its full core suite passes 3,803 tests with no
failures or errors and 16 skips. Core source is `42fedc595`, based on
`8bc4f8e20`; the explicit artifact is `1.4.8-lcc-edclf-qualified`.
Default dependencies and core master remain unchanged.

## Complete registered plugin suite

Plugin source `20c3c00ce` executes 4,906 tests across 741 class reports:
4,665 pass, 226 fail, two error and 13 skip. Actual XML totals match the
completed Maven summary. This is not an all-tests-passing milestone.

An exact class/method/failure-kind comparison against the retained preceding
candidate run confirms:

- Both unchanged IEEE14 EDCLF assertions now pass, as do the two new independent
  published-equation checks.
- Staggered reactivation invocation `[3]` now passes; the other reactivation
  trajectory failures remain.
- Two newly registered finite-resistance strict checks, invocations `[3]` and
  `[4]`, fail. Their thresholds remain unchanged.
- No baseline test identity was removed. Twelve test identities were added;
  there are no other new or resolved failure identities.

The full log and all XML reports are retained privately under
`verification-artifacts/full-lcc-edclf-integrated-20261104`.
Archive SHA-256:
`cf521677704a3051c2aaece8ca3e5524df6756c7e8eb6bfd8183fd081ff4e88e`.
The manifest records the exact comparison against the preceding archive.

## Default compatibility modules

The subsequent default module runs do not enable extended/private test profiles.
They use the same explicit core artifact and a fresh isolated output profile.

| Module | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| Plugin core | 494 | 1 | 0 | 4 |
| Three-phase | 468 | 0 | 0 | 28 |

The core-module failure is the repository configuration-policy check: two
fixtures used global settings. It is separate from numerical accuracy.
The first reactor stopped at this failure. A second reactor retained the same
failure while allowing the three-phase tests to execute; its successful reactor
exit must not be interpreted as a passing core-module test suite.
The later JSON configuration correction requires its separately reported check.

Root source advanced from `20c3c00ce` to timer correction `051e675ce` around
compatibility compilation, with unrelated test registration uncommitted.
An exact source snapshot at compilation was not captured, so these module
results are not claimed as a clean-commit reproduction. Both policy scans
preceded the JSON correction. The complete registered run above has its own
fixed source provenance and is unaffected by this distinction.

Compatibility logs, both core-module executions and all three-phase XML reports
are retained privately under
`verification-artifacts/compatibility-lcc-edclf-integrated-20261104`.
Archive SHA-256:
`778471fd8af71aec756b4511866ac0613f106ad712c07fba97fe17c10278c790`.

The loaded core artifact SHA-256 is
`3cf1c01a7fef77150f51662c80d478258f729fde5cb5f6848f40a6d1018743ed`.
No tolerance, assertion, model default or iteration cap was relaxed.
