# REECE local reactive PI and blocking entry

Status: native 36.7 QFLAG=2 moving-bound correction, with partial recorded-input
and coupled SMIB evidence. Baseline `724c4791f`; not complete model acceptance.

## Native contract and failing-before evidence

This reuses the converged cold-paired REGCB/REECE fault references from the
[native-import slice](reece-native-import-2026-10-18.md). Both complete raw
resources retain their original hashes, negative history and duplicate event
sides. No new native run, fitted state or substituted parameter is introduced.
Native library/document fingerprints and exact DYR/RAW provenance remain in
that report and its sealed private archive.

Native STATE2 is nonzero during the dip before blocking. When all four current
bounds become zero, STATE2 becomes exactly zero even though Voltage_dip remains
active. The current bounds also become zero on the endpoint at which filtered
voltage first falls below Vblkl, not one predictor later. Permanent native-only
assertions prove both facts at 0.5/0.25 ms.

The previous implementation froze both the integral derivative and projection
onto moving bounds. Its terminal-input replay PIQ error was 0.0463191 pu.
Allowing projection alone still failed: error 0.0455977 pu at 0.139000 s,
the first threshold-crossing endpoint. Both failing attempts are retained.

## Correction

For QFLAG=2, the integral derivative remains frozen during voltage dip, but
the stored PI state must still obey its moving Iq bounds. Immediate outside-
threshold detection now participates in the current-limit and converter-block
boundary. The existing latched block and delayed-unblock timer remain intact;
the change does not bypass the reconnect hold. Converter lag/internal-voltage
states remain retained, as required by the preceding hardware-blocking fix.

This slice does not generalize the evidence to QFLAG=1 cascaded-PI moving
bounds. No shared CML solver, timestep method, gain, initial condition, PI
coordinate, or proportional-current publication convention changes.

## Measured checks

Recorded-input replay uses only terminal P/Q/V and native DYR parameters, never
native filtered measurements, bounds or PI states as feedback. Every
nonnegative-time sample and both event sides remain included. Maximum PIQ
errors are 7.21375e-4 pu at 0.180999 s (0.5 ms) and 3.64464e-4 pu at 0.180749 s
(0.25 ms). Regression bounds are 9.4e-4/4.75e-4 pu with explicit step-halving
improvement. The previous tight active-PI bounds remain unchanged.

New unit checks exercise zero-time-constant blocking, state projection despite
freeze, release without resurrecting the discarded state, and a nonzero-filter
threshold crossing at each timestep.

The independent coupled diagnostic improves from the preceding slice:

| Maximum absolute error | Before, 0.5/0.25 ms | After, 0.5/0.25 ms |
|---|---|---|
| Bus 1 voltage (pu) | 0.00270804 / 0.00262849 | 0.00129043 / 0.000903259 |
| P (pu) | 0.0281026 / 0.0280380 | 0.0104451 / 0.00521055 |
| Q (pu) | 0.0173520 / 0.0166364 | 0.00883816 / 0.00600500 |
| PIQ | 0.0463305 / 0.0456403 | 0.000747843 / 0.000845621 |

The immutable diagnostic retains exact maxima/timestamps for every channel.
Native float-clock alignment and unmatched instantaneous
event-post rows retain the preceding declared normalization. These are diagnostic
trajectories, not accepted full conformance. In particular the coupled PIQ error
does not improve monotonically: current-limit feedback/publication and network
coupling still require independent discriminators.

## Gates and evidence

Final XML audit: 38 focused checks without skips, 269 plugin-core tests (six
optional skips), and 1,309 registered SMIB/model tests (one existing diagnostic
skip), all with zero failures/errors. Three scoped Java files match the isolated
tested snapshot; remaining tracked baseline Java/POM files match HEAD after
declared line-ending normalization. All 960 files of the reused raw native
archive pass SHA-256 checks. The unrelated untracked WECC240/JAX test is excluded.
No full Texas2k dynamics are run.
Core 1.4.3 SHA-256:
`f293ca958f363b814681f76bf310447d826fb5996e3bacb95aa5789a6a4b33e7`.
The dependency fingerprint matches before and after the gates.
Private archive:
`testData/private/verification-artifacts/2026-10-18-reece-reactive-blocking`.
It retains tested source/XML/logs, failing attempts, coupled diagnostics, the
exact core dependency, and hash-verified links to the earlier raw native runs.
