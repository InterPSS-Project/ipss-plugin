# Model lifecycle integration validation — 2026-11-04

The affected-model selection executes **426 tests: 371 pass, 55 fail, no errors
or skips**, across 47 class reports. XML totals match the completed Maven
summary. This remains partial qualification, not an all-tests-passing milestone.

Runtime source is `a16cca724`; subsequent suite registration is `1a66974a9`.
The explicit isolated core artifact is `1.4.8-reactivation-cache-candidate`,
from core `366a24d9a`. Defaults and core master remain unchanged.

## Exact comparison with the preceding complete run

The comparison includes only classes actually executed in this selection.
The preceding full-run archive contains 43 matching classes, with 396 tests
and 53 failures. All 53 failure identities remain unchanged: 47 in URGS3T
checks and six in WSIEG1 checks. No matching baseline test was removed and no
prior failure resolved.

The only new failures are the first two invocations of
`Wsieg1ReactivationTest.completeIndependentReactivationTrajectory`.
These are newly exercised reactivation gates, not previously passing checks
that regressed. The other 13 tests in that class pass.

Thirty additional test identities execute in this selection. These include new
lifecycle/import tests and previously existing SVS replay diagnostics that were
absent from the preceding full-run archive. They must not all be described as
new implementation coverage. Four classes are absent from that baseline:

- `Svsmo3t2MatrixReplayTest`
- `Svsmo3t2MssTripOrderingTest`
- `Tiocr1ExactLoadImportTest`
- `Wsieg1ReactivationTest`

All 40 SVSMO3T2 tests across eight classes pass, including the new peer-trip
boundary and rollback checks. Some existing extended tests explicitly retain
trajectory diagnostics; their passing execution does not qualify the documented
remaining coarse-step trajectory discrepancies.

## Evidence and limits

The completed bounded log, all 47 executed XML reports, exact failure-identity
comparison and class-level totals are retained privately at
`verification-artifacts/model-lifecycle-integrated-20261104`.
Archive SHA-256:
`cdc6002701e026119d355053111a016c285d488e83da9a8f1126aac25f8cb16d`.
The preceding full-run archive hash is
`cf521677704a3051c2aaece8ca3e5524df6756c7e8eb6bfd8183fd081ff4e88e`.
The explicit core artifact hash is
`fb0029906f5c956994a522411513cd8364f8d009af32621abb0eff8981697145`.

The public SVS reference archive contains only synthetic input records,
measurements and lifecycle observations. Exact tool provenance, probe source,
logs and rejected controls remain private. Every public archive entry was
checked for proprietary tool/version/vendor wording.

A separate performance follow-up should measure the per-controller ownership
snapshot on larger networks. Sharing that prepared registry must still detect
devices attached to previously empty buses. No unmeasured metadata optimization
was mixed into this validated numerical/lifecycle change.
