# REGFMA1: native schema and initialization-base verification

Later follow-up: [native limiter SMIB verification](regfma1-native-limiters-2026-10-17.md)
adds active P/Q and terminal-voltage-PI current-limit profiles, corrects native
voltage-integral limiting, and expands the references to fifteen datasets.
The counts and remaining-work list below describe this earlier schema slice.

## Scope and source contract

This slice separates native PSS/E 36.7 REGFMA1 input from the older Texas2k
nineteen-value exchange and the full typed PowerWorld-compatible interface.
It does not establish complete model or Texas2k system acceptance.

The user subsequently narrowed this work to SMIB/model verification. Full
Texas2k dynamic runs are no longer required for this slice or the revised
coverage goal. Their already-completed results below are retained honestly;
the later running system regression was stopped and its opt-ins restored.

The installed PSS/E 36.7 Model Library, printed pages 807–810 (PDF pages
819–822, one-based), defines two ICONs and eighteen CONs. The consulted PDF
SHA-256 is `40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`;
the model DLL SHA-256 is
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
The small three-bus reference runs fit the installed Xplore case-size limit.

Native ordering after the machine ID is:

```text
Vflag QVflag
Tpf Tqf Tvf Imax Emax Emin Pmax Pmin Qmax Qmin
mp mq Kpp Kip Kpq Kiq Kpv Kiv
```

Native coupling impedance is the generator's machine-base RAW ZSOURCE, not
an extra DYR pair or the legacy default j0.15. Nonzero implicit generator
transformer impedance is rejected rather than silently discarded. Native
validation rejects nonfinite values, invalid selectors, inverted limits and
zero source impedance. PowerWorld-only automatic reactance flooring and
limit swapping remain outside the native path; see the distinct
[PowerWorld REGFM_A1 reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGFM_A1.htm).

The catalog now advertises twenty values and retains nineteen as a documented
legacy variant. The direct parser can pin either format. Its default accepts
both exact cardinalities; a truncated twenty-value record cannot be diagnosed
as such from cardinality alone unless the caller pins native format. Legacy
input still uses QVflag=0, Re=0 and Xe=0.15, with an explicit compatibility
message. This is not native-schema evidence for the supplied legacy cases.

## Actual bug and production changes

REGFMA1 initialization previously read a generator impedance-base multiplier
before the core network-matrix path initialized that multiplier. At 50 MVA on
a 100-MVA network, initialization used one instead of two. The later matrix
construction then changed the physical injection without changing the states.
The failing-before-fix regression measured a complex-power error of
0.11180339887498958 pu. The fix initializes the multiplier through the existing
core base-conversion function before calculating the internal source voltage.
Guards cover 50, 100 and 200 MVA and repeat the later network conversion.
No physical parameter, controller gain or old comparison bound was fitted.

Two pure, immutable observation maps expose the nine native STATE coordinates
and eight native VAR quantities without changing integration coordinates or
existing named-state keys. The unused voltage-PI coordinate is zero for
Vflag=0. Native VAR 1 retains Edroop **before** the transient current limit;
terminal current is on generator MVA base. Fixed Vref is reported
only for QVflag=0. Four native selector combinations distinguish these rules.

## Independent references and comparison

The public synthetic RAW uses a 50-MVA converter with ZSOURCE=0.015+j0.23,
two network lines and an effectively infinite GENCLS source. There are no
loads or hidden transformer corrections. All profiles retain the same
eighteen control constants. Fault profiles apply a three-phase j1.0-pu fault
at Bus 2 from 0.05 to 0.10 s: three cycles at 60 Hz. This deliberately mild
baseline profile exercises voltage response, not active current or P/Q limits.
Two additional profiles change only Imax from 1.5 to 0.5 pu. They activate the
current limit throughout the integrated in-fault endpoints and release it
after clearing, at 0.5-ms and 0.25-ms steps. P/Q limiters remain inactive.

Twenty completed native runs pass the permanent run-quality auditor. Nine
selected datasets each have two cold runs from identical script revisions,
RAW and DYR inputs, with identical full CSV hashes. Two older flat runs remain
supplemental evidence; the initial failed API-allocation probe is diagnostic,
not an oracle. Each completed run retains its exact local probe script.

Public checkpoints embed all 24 channels: nine states, eight VARs, three bus
voltages, terminal P/Q, terminal voltage and speed deviation. Their expected
digests are fixed independently of the bytes being tested, with positive and
mutation-negative tests. Four flat profiles check every InterPSS endpoint
against the native initial point. Five fault profiles use nineteen explicit
native checkpoints, including event boundaries and adjacent endpoints.
Private audits compare every regular endpoint (601 at 0.5 ms; 1,201 at
0.25 ms), rather than just those public checkpoints.

Native float timestamps are mapped to the nearest declared integration tick,
with a half-step uniqueness check. Two initialization-history rows are
excluded. Repeated flat-run segment boundaries are compared to the same
endpoint. For fault runs the first pre-event sample is compared; the two
instantaneous event-post samples remain explicitly unmatched and retained.
They are not averaged into a supposedly accepted trajectory.

| Profile | Maximum Bus 1 voltage error (pu) | Maximum Q-filter state error (device pu) |
|---|---:|---:|
| Direct voltage, 0.5 ms | 2.4616688e-5 | 0.00260376371 |
| Direct voltage, 0.25 ms | 1.29906985e-5 | 0.00136809113 |
| Terminal-voltage PI, 0.5 ms | 3.940976e-5 | 0.0026593319 |
| Active Imax, 0.5 ms | 1.39783135e-5 | 0.00147387448 |
| Active Imax, 0.25 ms | 1.08932063e-5 | 0.000772330571 |

All four flat profiles have InterPSS drift below 1e-10 over 0.3 s; their
maximum native state difference is approximately 2.37e-6, consistent with
native initialization precision. The PI fault exercises the voltage-integrator
state. Most direct-fault errors decrease about 1.9 times when both simulators'
steps are halved. This supports a timestep/event-discontinuity explanation for
the residuals, not a claim of exact equality or complete limiter conformance.

The active-limit discriminator caught an observation-only bug invisible to
the original inactive-limit profiles: native VAR L+1 retains pre-limit Edroop,
whereas the initial InterPSS snapshot reported the limited equivalent Norton
voltage. Its full-trajectory discrepancy was 0.0501657 pu. A focused guard
failed before the fix (1.0933542 expected versus 0.9593312 observed). Correcting
the observation, without changing injection or integration equations, reduces
the VAR error to 4.33424e-5 pu at 0.5 ms and 2.24307e-5 pu at 0.25 ms.
Active-limit state errors reduce about 1.9 times with step halving; voltage
errors reduce only 1.28 times, so uniform second-order convergence is not
claimed. Existing trajectory bounds remain unchanged; new profiles have
separate channel-specific bounds.

The frozen active-limit probe also replays from a fresh isolated input tree.
The replay wrapper stages the exact frozen script at its expected relative
location and copies the archived RAW into the corresponding fixture path;
no source fixture or earlier run is overwritten. RAW, DYR and complete CSV
hashes reproduce exactly, and the replay passes the permanent run-quality
auditor. This replay is additional to the twenty reference-generation runs.

## Regression checkpoint

- Focused parser, native trajectory, existing legacy/PowerWorld trajectory and
  catalog tests: **36 tests, zero failures/errors/skips**.
- Follow-up inventory, native parser/trajectory and catalog run: **25 tests,
  zero failures/errors/skips**. The private inventory still requires exactly
  nineteen values for the five original REGFMA1 records, and separately asserts
  that the catalog accepts the legacy variant. A public guard checks native
  primary count twenty, accepted nineteen/twenty, and rejected eighteen/twenty-one.
- Final focused run including active Imax, native snapshot guard, existing
  legacy/PowerWorld comparisons and inventory: **41 tests, zero failures,
  errors or skips**.
- Core integration commit `a036d9a78`, tree
  `ff84999e32cdd248dea839e23f61298836cc3a98`:
  **2,945 tests, zero failures/errors/skips**.
- Isolated local core coordinate: `1.4.3-regfma-native`, JAR SHA-256
  `92eb956923032a25a41eff624e39fe7836fab5f50673e50ed652ec91559f632b`.
- Initial full plugin run: **2,747 tests, three failures, zero errors and
  91 skips; exit one**. The clean verification checkout is based
  on `7554d1c21` plus this slice, with strict flat and topology-fault opt-ins
  enabled. Concurrent reference-runner/report edits and the unrelated JAX test
  are excluded. This run precedes the inventory correction and active-limit
  observation fix. All fifteen topology fault runs complete; this is execution
  evidence, not cross-tool physical acceptance.
- Final SMIB/model-unit suite: **1,150 tests, zero failures/errors, one skip;
  exit zero**. The skipped test is the existing opt-in
  `RegcaPositiveQRecoveryDiagnosticTest`; it is not presented as accepted.
  Report inspection confirms no Texas2k, WECC or IEEE39 test classes executed.
- Complete plugin-core unit regression: **129 tests, zero failures/errors,
  six skips; exit zero**. Four skips concern the optional KlusolveX native
  solver, and two concern optional AUX converter fixtures, not REGFMA1 parity.

Reproduce the selected model gate in the clean verification checkout:

```text
mvn -q test -Pextended-tests,isolated-verification
  -Dverification.run.id=regfma-native-smib-suite-20261017
  -Dipss.core.version=1.4.3-regfma-native
  -Dtest=DynamicModelSmibTestSuite,DynamicModelCatalogTest
  -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
```

The permanent suite selects model builders, machine/CML unit tests and
independent small-network/SMIB comparisons. Its exclusions prevent discovery
of full Texas2k, WECC and IEEE39 dynamic classes. It does not convert partial
profile coverage into complete model acceptance. The unit-only module command
is `mvn -q -pl ipss.plugin.core -am test` with the same profile/core overrides.
Core validation is `mvn -q install --fail-at-end -Drevision=1.4.3-regfma-native`.

Concurrent plugin commit `7d341fb79` adds an operating-point reference runner
and report only; it is absent from the clean `7554d1c21` verification baseline.
The model/parser/test files in this slice match that tested checkout. No
unrelated runner or untracked JAX changes are included in this slice.

The initial full run exposed an inventory-test assertion that
confused the catalog's primary native count with its accepted legacy variant;
the focused correction above fixes that assertion without changing import
behavior or the exact reviewed source inventory. It also reports failures in
the strict one-second drift gate and short no-event smoke. For example, Case 1
has worst strict-gate voltage drift 0.0162662 pu at Bus 1023 and speed drift
2.21803e-4 pu at Bus 1075; Case 2 has 0.103325 pu at Bus 3113 and 0.00216965 pu
at Bus 5206. These are not accepted as reasonable flat runs. They must remain
visible while the native wind startup contract is diagnosed; neither old
stationary-flat results nor these mild REGFMA1 references resolve them.

The completed strict-gate ranking files identify the following largest
within-InterPSS changes over one second. These are **not** InterPSS–PSS/E
errors and do not by themselves identify a defective leading model.

| Case | Worst voltage bus / drift (pu) | Worst synchronous speed unit / drift (pu) | Leading controller change |
|---|---|---|---|
| 1 | 1023 / 0.0162662 | 1075:1 / 0.000221803 | REECA1 3098:1 iqcmd, 0.835888 |
| 2 | 3113 / 0.103325 | 5206:1 / 0.00216965 | REECA1 3086:1 vIntegral, 1.29282 |
| 3 | 1026 / 0.177153 | 1051:1 / 0.00775328 | REECA1 2029:1 vIntegral, 1.29950 |
| 4 | 1026 / 0.231708 | 4095:1 / 0.0150129 | REECA1 2029:1 vIntegral, 1.29269 |
| 5 | 2042 / 0.145284 | 5262:1 / 0.0119442 | REECA1 1033:1 vIntegral, 1.30045 |
| 6 | 2091 / 0.134437 | 4192:5 / 0.0171106 | REECA1 2029:1 vIntegral, 1.28863 |

Next localization must compare the first native/interpreted derivatives and
matched controller inputs at these units, not change gains or freeze initial
states to force a stationary run. Only Case 6 contains legacy REGFMA1 records;
the Case 1–5 failures cannot exercise this slice's REGFMA1 runtime branch.

The ignored private `2026-10-17-regfma1-native-schema/native-evidence` archive
contains 2,673 files with a verified SHA-256 inventory. It freezes native runs,
their scripts, diagnostic comparisons, failing-before-fix logs, model/test
source and the initial focused build. Additional sealed, hash-verified archive
sections preserve `core-validation` (1,629 files),
`first-full-before-inventory-fix` (1,346 files), `active-current-follow-up`
(91 files), and `final-smib-validation` (666 files). The latter includes the
final SMIB/unit reports and restored full-system-disabled settings.

The three subsequent incomplete full-system attempts are preserved as logs,
not counted as completed regressions: one had an input-staging failure, one
was stopped when the native observation bug was discovered, and the last was
stopped at the user's SMIB-only scope revision. The final completed SMIB gate
contains both fixes and the two additional active-limit references.

Native replay instructions: use the archived
`replay_regfma_native_reference.ps1` with `-Python`, `-ReferenceDirectory` and a
new `-ReplayDirectory`. Configure that Python environment for PSS/E 36.7 first.
The wrapper validates the archived hashes before staging, refuses an existing
destination, and requires unchanged model-library and output hashes afterward.
The tested example uses `regfma1-native-v0q0-current-limit-a` as its reference.

## Remaining acceptance work

- Compare active native P/Q limiter branches, both boundary directions,
  plant-controller input excursions, and lifecycle events independently.
- Add an active-current-limit profile with terminal-voltage PI enabled; the
  new active-limit references here use Vflag=0, QVflag=0 only.
- Resolve exact instantaneous event-post comparisons. Nominal integrated
  endpoints are not a substitute for those instantaneous observations.
- Complete the approved-model independent SMIB evidence inventory and missing
  profiles. Full Texas2k fault/drift investigation is separate deferred work,
  not a prerequisite for this user-revised SMIB goal.
