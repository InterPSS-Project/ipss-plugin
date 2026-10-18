# REEC_D Paux units and algebraic-update correction

Status: **partial independently referenced REECD profile**, not complete REECD
or WTGIBFFRA verification. Renewable loadability remains **20/27**, with seven
missing implementations. This prerequisite was found while tracing the new
WTGIBFFRA controller's connection; no placeholder WTGIBFFRA runtime is added.

## Source-backed defect and correction

The [WECC model memo, Figure 3, page 12](https://www.wecc.org/sites/default/files/documents/products/2024/Memo_RES_Modeling_Updates_021623_Rev27b.pdf)
places Paux after the Pord lag but before conversion from power to current:

```text
Ipcmd_before_limits = (Pord + Paux) / max(Vmeas, 0.01)
```

`Reecd1Model` instead computed `Pord/Vmeas + Paux` in both its dynamic and
initial command paths. That gave the auxiliary input current units. The new
native non-unit-voltage discriminator settles near 0.811 pu and applies
Paux=0.1 pu: the old formula misses native Ipcmd by about 0.0233 pu, whereas
the power-before-divider expression agrees within about 2e-8 pu at the checked
settled sample. Both calculation sites now use the source equation. Neither
Pord state nor its initialization, ramp, power limits or time constant is fitted
or changed to absorb Paux.

Paux is an algebraic input. After initialization, `setAuxiliaryPower` therefore
refreshes current commands and limits without advancing states, timers or dip
history. It retains the last pre-limit reactive request so releasing P-priority
headroom restores the requested Q rather than retaining a previously clipped
command. Active hold and blocking logic still apply. Six focused unit tests
cover both input signs at non-unit voltages, unchanged zero input, P-priority
limits, final current clipping, state-neutral updates and headroom release.

## Native references and exact scope

Eight final reference simulator runs form four cold pairs at 0.5/0.25 ms:

- Recorded-input profile: public three-bus RAW with native REGCA1/REECD and
  PLBVF1. Voltage drops from 1.0 to 0.8 pu at the source, then Paux steps to
  0.1 pu at 0.15 s. Every terminal-voltage and auxiliary-input endpoint is frozen
  for replay through the InterPSS controller.
- Coupled profile: the same converter/electrical controls with stiff GENCLS,
  no playback source, and the same auxiliary-power step. The full InterPSS
  solver runs this matched three-bus case, not a Texas2k system.

The auditor verifies native 77-CON/six-ICON values, seven-STATE/twenty-VAR
allocation, valid initialization, finite ordered channels, all manifest hashes
and identical complete cold-pair CSV bytes. The public native fixture is copied
from the coupled probe's exact DYR. Full output, scripts, PLB files and logs are
retained privately. Earlier campaign-labelled/parameter-uninstrumented runs are
diagnostic only, not the accepted final reference matrix.

Initialization uses solved physical P/Q/V from the negative-time prehistory.
The PLBVF1 source refreshes the network at time zero, so using that refreshed
boundary as an initialization condition would create a spurious Pord mismatch.
No internal native state is substituted for a calculated InterPSS state.

Public reference-bundle SHA-256:
`ec3c8a44f4679534c2dfe1c17a5783f4850ef39cdf51c920be5aa7559589c251`.
The separate definition fingerprint also covers names, profile kind, timestep,
initial P/Q/V, sample counts and event-row allocation, using fixed IEEE-754
bit-string normalization:
`9577b86353ace5af91bf4e12cc0e1377d8dbff1cb255e4597fb372c55ff5c4a3`.

Both sides of the Paux update are retained and compared. At this input step,
the converter current lag remains continuous while the electrical-controller
command changes algebraically. No immediate-post row is dropped or hidden in
a broad tolerance. Native positive-Q command is compared against the negative
of the InterPSS REEC/REGCA interface command; this is an explicit sign convention,
not a fitted transform.

The selected QFLAG=0 profile exposes two inactive-storage differences: native
voltage-PI storage and bypassed compensation-filter storage are zero, whereas
InterPSS retains an unused integral/algebraic coordinate. Their raw values are
preserved but not called matched native STATEs. Active voltage measurement,
reactive lag, Pord, current commands and coupled V1/V2/V3/P/Q have separate
acceptance bounds. Other REECD selectors, active PI branches, limits and
lifecycle profiles remain open.

## WTGIBFFRA investigation retained separately

The installed Model Library section 29.1, printed pages 942–949 (physical PDF
954–961), defines one input ICON, 45 CONs, four STATEs and 17 VARs. The native
runtime allocates six ICON slots; that does not turn them into six DYR inputs.
The block diagram and connection diagram were visually reviewed. WTGIBFFRA
connects to an electrical Paux port, not the weak-grid Pref path. Native REECA1's
diagram lacks that port; a future importer must not invent an attachment there.

Three additional cold native pairs exercise a short frequency pulse with
automatic recovery, a longer user recovery and a too-short user recovery.
The profile continues after frequency recovers during the first ramp. However,
automatic/too-short recovery does not reproduce the published energy-balanced
profile in the tested DSMDL: the example's published calculation is about
0.70000001 s, while native VAR11 reports about 0.51999998 s and the recovery
integrator reaches its upper bound in its first recovery sample. Supplying
1.2 s produces a finite-duration recovery instead. These are reproducible
discrepancies, not accepted normative behavior or fitted replacement equations.
WTGIBFFRA kernel, native import/coupling and full verification remain TODOs.

Source identities:

- reference simulator Model Library:
  `40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
- DSMDL:
  `c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
- Downloaded WECC memo, revision 27b:
  `10f1c4c2d965314f189d7383a260a2d14d1ebcdeb7ae97d056fce91f75447ebe`.

## Accepted active-path results and final regression

At the selected checkpoints, coarse/fine maxima are:

- Recorded-input Vmeas: 2.4815e-3 / 1.2251e-3 pu.
- Recorded-input Ipcmd: 6.3746e-4 / 3.1431e-4 pu.
- Recorded-input reactive lag/command: 1.6650e-4 / 8.3252e-5 pu.
- Coupled Ipcmd: 2.0202e-6 / 1.6534e-6 pu.
- Coupled Bus 1/2/3 voltage: 8.6946e-6 / 6.0847e-6 / 7.1102e-7 pu
  at coarse step; 9.7979e-6 / 6.0550e-6 / 6.1575e-7 pu at fine step.
- Coupled P: 5.9437e-4 / 3.0535e-4 pu on machine base.
- Coupled Q: 9.3679e-7 / 1.0280e-6 pu on machine base.

The new bounds were tightened after the clean independent comparison. Permanent
tests require recorded-input Vmeas/reactive-lag/current errors and coupled P
error to reduce on step halving. Other channel residuals are not claimed to
converge. The early test-helper compile error and the diagnostic initialization
comparison are retained; no previously accepted trajectory bound was relaxed.

The tested snapshot is committed plugin `c8890ae1f` plus this slice, including
that committed reference-runner reporting update but excluding unrelated
untracked WECC240/JAX work. Core 1.4.3 JAR SHA-256:
`f7380328ff7b4d81686a5aae2c172d964013ae74deb81c964050a6a74db0d4ad`.

- Plugin-core units: **205 tests, zero failures/errors, six optional skips**.
- Registered SMIB/model, catalog and staggered-solver gate: **1,251 tests,
  zero failures/errors, one existing diagnostic skip**.

Both final commands exit zero; totals are read from fresh Surefire XML:

```text
mvn -q -pl ipss.plugin.core -am test -Pextended-tests,isolated-verification -Dverification.run.id=reecd-paux-units-20261018-f --fail-at-end
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=reecd-paux-smib-20261018-g -Dtest=DynamicModelSmibTestSuite,DynamicModelCatalogTest,PsseStaggeredEulerSolverTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
```

The ignored private archive
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-reecd-paux-native`
retains all 23 native campaign runs (eight final Paux references, six recovery
discriminators and nine earlier diagnostics), exact probes/generators/audits,
source diagrams, two InterPSS coupled exports, scoped sources and final XML/logs.
The canonical Paux audit explicitly records the primary model's actual 77 CONs
and six ICONs separately from the generic campaign manifest's WTG inputs.
SHA-256 inventory verification seals the archive without replacing old evidence.
No full Texas2k dynamics were executed and no complete-model acceptance is implied.
