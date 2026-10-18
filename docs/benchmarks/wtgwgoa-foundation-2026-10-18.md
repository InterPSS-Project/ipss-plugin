# WTGWGOA / WTGWGOAU weak-grid option foundation

Status: **kernel foundation with partial native playback evidence**, not LOADABLE
or VERIFIED. Native renewable coverage remains **19 of 27 loadable families**;
WTGWGOAU remains one of the eight incomplete implementations. No parser alias,
dummy attachment or support-matrix entry is added by this slice.

## Source and exact contract

The installed reference simulator Model Library, section 28.1, printed pages 937–940
(physical PDF pages 949–952), defines eight CONs, three STATEs and three VARs.
Its block diagram was visually inspected. Model Library SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
DSMDL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.

CON order is Vwgo, Pwgo1, rpw1, Pwgo2, rpw2, Thold, eps, Tfltr.
STATE order is filtered terminal voltage, first recovery-ramp increment,
second recovery-ramp increment. Ramp states are increments, not absolute Pref.
Power levels, time constants and hysteresis must be nonnegative; ramp rates
must be positive. Pwgo2 <= Pwgo1 disables the option rather than invalidating
or swapping those parameters.

The standard native record is:

```text
IBUS 'WTGWGOA' ID Vwgo Pwgo1 rpw1 Pwgo2 rpw2 Thold eps Tfltr /
```

Two cold native runs also accept the legacy allocation:

```text
IBUS 'USRMDL' ID 'WTGWGOAU' 109 0 0 8 3 3 CON(1) ... CON(8) /
```

Their complete CSV identities equal the standard baseline. This proves the
tested native wrapper, not an implemented InterPSS import path.

The option captures the plant reference on entering the low-voltage profile.
It holds Pwgo1 while filtered voltage is below recovery threshold Vwgo+eps,
ramps toward Pwgo2 at rpw1, holds for Thold, then ramps toward the captured
reference at rpw2. A changing live plant output does not move that target.
Finishing returns to the live plant input. A target below Pwgo1 is not raised.
The no-plant native profile uses the fixed initial reference.

Native observations resolve two documentation/storage ambiguities: the idle
timer VAR is 999 and its active value is absolute hold-start time; the stored
WGO Pref VAR initializes to zero and retains the last limited reference after
completion. It is not the actual bypass output. Disabled native profiles freeze
the initialized voltage STATE and retain zero VARs.

## Explicit published/native convention distinction

The [PowerWorld WTGWGO_A documentation](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Pref%20Controller%20WTGWGO_A.htm)
describes restarting recovery on another fault. The tested native reference simulator
profiles instead continue an already active recovery. Three separate cold-paired
discriminators apply the second fault during first ramp, hold and second ramp.
Actual terminal and filtered voltages fall below the trigger threshold in all
three, so lack of restart is not explained by an inactive voltage trigger.

The typed default `new WtgwgoaModel(data)` retains the published restart rule.
`WtgwgoaModel.forNativePsse367(data)` explicitly selects the observed native
continuation rule. Neither dialect is silently substituted for the other.
The input/output coupling for a future importer must declare that convention.

## Implementation and focused coverage

`WtgwgoaData` preserves the eight native parameters. `WtgwgoaModel` supplies
initialization, both recovery ramps, hysteresis, hold timer, captured target,
disabled/no-increase branches and immutable semantic named-state observations.
Modified Euler accepts distinct old/predicted endpoint inputs and rolls back
predictor-only discrete transitions before committing corrected state. Ramp
rates remain constant over the sampled mode interval rather than being halved
by a provisional endpoint phase transition. Zero-duration refreshes do not
advance differential states or explicit two-step integration history.

Fourteen unit tests exercise equilibrium, strict threshold equality, target
capture, both ramps, zero hold, target below intermediate power, no increase,
all three repeated-fault phases, disabled behavior, dialect separation,
predictor/corrector stages, predictor rollback, reinitialization and invalid data.

## Independent recorded-input evidence

Twenty-two native runs form eleven exact cold pairs, including the legacy
wrapper. Ten unique public checkpoint profiles retain every nonnegative input
endpoint, event-post duplicates and selected unmodified STATE/VAR observations:
baseline at 0.5/0.25 ms, three second-fault phases, disabled option, zero filter,
zero hold, fault power above initial reference and no plant controller.
Full prehistory and every raw channel remain in the private artifact archive.
The input is actual terminal voltage plus live REPCA output STATE6 (or fixed
initial reference); the captured WGO VAR is not incorrectly replayed as a live
plant output. Native CON round-trip, 8/3/3 allocation, initialization/log quality,
finite ordered samples, manifest hashes and complete cold-pair CSV identities
are audited before generating checkpoints.

Immutable reference-bundle SHA-256:
`b64d4bf29365f705fa92e9bac53a5b42b7586b0fd20d4b08a9f1a4e52770c235`.
Mode labels are inferred from raw STATE/VAR values, not exported internal flags.
Four permanent reference tests check integrity/mutation rejection, all ten
profiles, disabled/no-increase branches and the published/native discriminator.

This is deliberately **partial hybrid timing and sustained-overlap evidence**.
Filtered voltage is checked at every checkpoint, including callback windows.
Phase sequence and transition times are checked separately (3.1 nominal steps).
Discontinuous ramp resets, timer changes and bypass handoffs within those
windows retain and print their raw errors; they are not accepted pointwise.
Representative baseline maxima at coarse/fine step:

- Voltage filter: 3.3863e-4 / 1.7377e-4 pu.
- First-ramp increment during sustained matching phases: 1.0017e-4 / 5.0772e-5 pu.
- Second-ramp increment during sustained matching phases: 5.0042e-5 / 7.6056e-5 pu.
- Raw callback mismatches can reach about 0.08 pu in ramp coordinates,
  998.67 s in the sentinel timer and 0.0233 pu in captured-reference handoff.

Those discontinuities prevent a complete native trajectory-conformance claim.
Second-ramp residual does not monotonically refine; no general convergence
claim is made. Sustained ramp bounds separate step sampling from a conservative
float-storage roundoff budget: one maximum-range float ULP per nominal step.
An earlier provisional fine-step bound failed at 7.6056e-5 versus 7.5e-5; that
failure is retained. The roundoff budget is explicit, not an equation/gain fit
or a wide tolerance accepting the callback discontinuities. Default kernel
storage remains double precision.

## PowerWorld diagnostic is not accepted benchmark evidence

The first PowerWorld 24 probe initializes and solves, but times out exporting
the guessed WGO state fields; parameter exports are empty. Its six boundary
channels do not establish a repeatable complete-model benchmark. The rejected
run is retained. The PowerWorld-only REPCA monitored branch is explicitly
specified separately from native inputs. Automatic correction is disabled.
SimulatorAuto COM is not registered on this installation; no registration or
installation change was made. Correct AUX field/table discovery and cold
regeneration remain open.

## Regression and next acceptance steps

The tested baseline is committed plugin `87ef8a0eb` plus this foundation.
Core 1.4.3 JAR SHA-256:
`f7380328ff7b4d81686a5aae2c172d964013ae74deb81c964050a6a74db0d4ad`.
Unrelated WECC240/JAX work is excluded. Fresh Surefire XML records:

- Plugin-core units: **195 tests, zero failures/errors, six optional skips**.
- Registered SMIB/model, catalog and staggered-solver gate: **1,237 tests,
  zero failures/errors, one existing diagnostic skip**.

Both final commands exit zero:

```text
mvn -q -pl ipss.plugin.core -am test -Pextended-tests,isolated-verification -Dverification.run.id=wtgwgoa-foundation-units-20261018-d --fail-at-end
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=wtgwgoa-foundation-smib-20261018-e -Dtest=DynamicModelSmibTestSuite,DynamicModelCatalogTest,PsseStaggeredEulerSolverTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
```

The ignored private archive
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-wtgwgoa-foundation`
retains all 22 accepted native cold runs, the early diagnostic pilot, rejected
PowerWorld export, raw traces, inputs/probes, checkpoint generator and audit,
failed provisional gate, exact scoped sources and final XML/logs. A SHA-256
inventory seals the retained files. Existing archives are not overwritten.
No full Texas2k dynamics are required or executed for this slice.

- [ ] Implement strict standard/legacy DYR schema, catalog and deferred host
  attachment, with malformed/duplicate/removal accounting tests.
- [ ] Route the weak-grid Pref into electrical controllers and, for Type 3,
  torque and pitch, including optional plant-controller operation.
- [ ] Verify initialization, named-state monitoring, disabled/trip/reconnect
  lifecycle and integration stages through the full InterPSS solver.
- [ ] Add coupled SMIB flat and three-cycle fault comparisons at both steps;
  distinguish published and native repeated-fault conventions explicitly.
- [ ] Resolve callback/handoff timing and complete channel-by-channel native
  acceptance rather than counting matching-mode playback as full conformance.
- [ ] Finish PowerWorld model/field discovery, parameter round-trip and cold
  artifacts; run published restart comparisons without mixing vendor inputs.
