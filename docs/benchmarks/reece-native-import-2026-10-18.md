# REECE native 36.7 import and SMIB slice

Status: loadable standard-name native REECE, with partial independent model
evidence. Not complete REEC_E alias coverage or complete trajectory acceptance.
Baseline: plugin `ae26dbfff`, core 1.4.3. No full Texas2k dynamics are run.

## Exact source/version boundary

The native 36.7 Model Library section 22.6 defines `REECE`: eight ICONs followed
by 79 CONs, eight STATEs and 20 VARs. The [typed-runtime report](reece-runtime-2026-10-18.md)
records the diagram/source hashes, independent native branch campaigns and
state-limited PI correction. Those equations and frozen references are reused;
this slice adds import/attachment and corrects the REGCB network blocking
boundary, without changing the REECE integration or PI equations.

Only the proved standard record is accepted: 87 values after bus/model/ID.
ICONs must be integer selectors; CONs retain exact native order and machine-
MVA-base units. No field defaults, extra fields or guessed USRMDL allocations
are accepted. `REECE1`, `REECEU1` and `REEC_E` are not parser aliases.

The approved-list source columns remain `REECEU1, REECE1`; they are not rewritten
to suggest those names were proved. The REEC_E family runtime mapping now points
to the installed release's standard name REECE, just as source names and runtime
canonical names are separately recorded for other families. Family runtime-path
presence is **22/27**, with five genuinely missing families: HPLNTD, REPCCU,
REGFMB1, REGFMC1 and REPCGFMC1. This count does not mean every source-listed name,
wrapper or release-specific record is supported. No model is promoted to VERIFIED.

## Implementation and guards

- Add the exact catalog descriptor and source-located strict parameter decoding.
- Defer electrical attachment until converters are present, before deferred
  REPCA attachment; source ordering does not select a different controller.
- Require an active generator/bus with REGCA or REGCB and an unused electrical
  controller port. Never overwrite a previously attached REEC variant.
- Reject duplicates, unsupported host types, missing targets and malformed
  selectors/constants. Retain per-record outcomes and strict-import failure.
- Respect GNET and model removal before queuing; repeated parser calls cannot
  replay an earlier rejected pending record.
- Verify system-to-machine-base conversion once on both converter hosts and
  source-order-independent REPCA attachment.

The public three-bus DYR is the original native cold-campaign input with declared
line-ending normalization only. Native CRLF SHA-256:
`d600f2a124d6a6ca4932423c70d702e4d5fb944dcc1d109255c68b1d2368690f`.
Canonical LF SHA-256:
`8784f51ac2601b13ea58563c6b0bbdefc40035603db68f7af88e2687212fdb2c`.
A permanent test reconstructs the original CRLF digest exactly. The first
incorrect fixture-hash assertion is retained, not mistaken for a model failure.

## Verification

Fifteen focused parser/builder tests cover the above contract. New native-import
full-solver tests reuse the same assertions as the typed fixture at 0.5/0.25 ms:
flat voltage drift below 1e-7 pu, a three-cycle fault with an actual voltage
dip, finite controller states/current commands, and trip-state freeze/restart.
REGCA uses Bus 2 j0.001 pu; REGCB uses Bus 1 j0.01 pu with normal PQ priority 1.
This establishes real import/initialization/coupling, not independently matched
coupled fault trajectories. Existing twelve native recorded-input datasets and
callback discriminators remain unchanged.

Ten new native runs retain raw logs, inputs, manifests and exports. Eight pass
the existing PsseRunQuality log/CSV audit, including four formal cold runs at
two steps with matched normal PQ priority. Two severe Bus 2 runs are rejected:
the API returned success but the logs reported network nonconvergence. An
unsuccessful experimental Newton solver was removed; no solver fallback ships.

The converged native REGCB fault shows exactly zero current outputs while REECE
is hardware-blocked, despite nonzero lag/internal-voltage states. Previously
InterPSS stopped only commands and still injected 1.2 pu current. A failing-before
regression proves this. An explicit controller blocking signal now gates physical
REGCB current and cancels Norton admittance without erasing states. Legacy
controllers default to unblocked; zero commands alone do not activate this gate.

Two immutable full raw native CSV resources check this boundary permanently.
Current channels are exactly zero; native network power cancellation residuals
are bounded separately at 3e-9 pu P and 1e-6 pu Q. The fine cold pair differs only
in inactive PF-angle storage EC_VAR2; both raw hashes remain recorded.

The coupled comparison retains negative history and instantaneous event-post
rows explicitly unmatched, and aligns remaining native float timestamps by
declared timestep tick, without fitting/interpolation. Maximum errors before
and after blocking correction are:

| Channel | Before, 0.5/0.25 ms | After, 0.5/0.25 ms |
|---|---|---|
| Bus 1 voltage (pu) | 0.17763 / 0.17771 | 0.0023102 / 0.0022556 |
| Q (pu) | 0.92695 / 0.92713 | 0.0173558 / 0.0167211 |
| P (pu) | 0.34653 / 0.34806 | 0.0650449 / 0.0608424 |
| Active PI state | 0.10375 / 0.10370 | 0.1031163 / 0.1031642 |

This is diagnostic improvement, not coupled-trajectory acceptance. The persistent
PI discrepancy needs a native callback/hold discriminator, not a looser tolerance.

Final XML gates: 95 focused tests (no skips), 266 plugin-core tests (six optional
skips), and 1,306 SMIB/model tests (one existing diagnostic skip), zero failures
or errors. Twenty scoped source/resource files match the isolated tested snapshot;
all other tracked baseline Java/POM files are audited against HEAD. This does
not include the unrelated WECC240/JAX test or concurrent Texas2k report edits.
Core JAR SHA-256:
`f293ca958f363b814681f76bf310447d826fb5996e3bacb95aa5789a6a4b33e7`.
Its fingerprint matched before and after final gates. The unrelated untracked
WECC240/JAX test is excluded from the isolated checkout.

Private evidence archive:
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-reece-native-import`.
It retains the tested source, generated inventories, native input identity,
prior raw reference provenance and exact dependency plus final XML/log evidence.

## Still open

Prove the source-listed release aliases/wrappers rather than inferring them.
Resolve native prepared-reference and prior-Ipcmd callback conventions, then
finish independently referenced coupled native/PowerWorld disturbances and
remaining saturation/release, linked PI, blocking/hold, speed/base and lifecycle
branches. The typed-runtime report retains the measured residuals and their
declared scope; a green import test does not supersede those limitations.
