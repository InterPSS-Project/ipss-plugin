# REECE typed data and local-PI foundation

Historical checkpoint: the subsequent [typed-runtime slice](reece-runtime-2026-10-18.md)
adds the eight-state runtime and corrects the integral limiter using independent
active-boundary native profiles. The inactive-limit tests recorded here could
not distinguish the earlier incorrect output-based integration freeze.

Status: **component foundation, not loadable or a complete REECE implementation**.
Approved native renewable coverage stays **21/27**. REECE1 remains one of the six
missing complete controller/import paths. No full Texas2k dynamics were run.

## Exact source/version contract

Installed reference simulator Model Library section 22.6, printed pp859-866 / physical
pp871-878, defines standard **REECE**: eight ICONs, 79 CONs, eight STATEs and
twenty VARs. The native diagram was rendered and inspected. Library PDF SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
Native DSMDL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.

The [WECC July 2025 memo](https://www.wecc.org/sites/default/files/documents/progress_report/2025/Memo_RES_Modeling_Updates_For_New_Plant_and_Electrical_Controls_072525.pdf)
defines the additions to REEC_D: local reactive PI, optional active PI and a
separate fault-current-priority selector. It requires positive Kpi when the
active PI is enabled. The [PowerWorld reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20REEC_E.htm)
provides an independent description. Its typed parameter listing and MVA-base
extension are not used as the native DYR order.

Native selectors are PF, V, Q, P, PQ priority, voltage compensation, active PI,
then fault PQ priority. QFLAG accepts 0/1/2. Kpp/Kpi are CON offsets 77/78, not
interleaved with Kqp/Kqi/Kvp/Kvi. The two ten-point VDL tables retain their
interleaved voltage/current order. Parameters are on connected machine MVA base.

Cold native probes accept standard `REECE` and audit all allocations and actual
parameters. Plain `REECE1` and `REECEU1` records with this layout produce
"Undefined Model" in this installed release, followed by absent WELEC data.
That does not disprove another release or a properly allocated user-model
wrapper. No legacy allocation or cross-version alias is guessed here.

## Production components

- `Reece1Data`: immutable eight-selector/79-CON contract, named parameter
  offsets, grouped VDL layout, finite/range/time checks and the active-PI gain
  requirement. Arrays are copied and equality/hash use values.
- `Reece1LocalPi`: the local QFLAG=2 and PEFLAG=1 PI blocks. It consumes prepared
  references, measured/filtered P/Q and explicit output limits; it is not an
  electrical-controller substitute. QFLAG 0/1 remain outer-controller paths.
- Both integrals use explicit modified Euler, conditional anti-windup and
  directional boundary handling. A proportional jump does not force the
  integral to track an output clamp. Voltage dip freezes integral states,
  not proportional output. Zero-duration refresh consumes no integration history.
- Current priority selects normal/fault flags without advancing a state. The
  actual current-limit and blocking network remains future controller work.
- Initialization derives the local integral contributions from solved Q/V and
  P and the initial error. Observations are immutable, semantic coordinates.

Fourteen unit tests cover field order/shape, flags, gains, limits, signed holds,
VDL termination, initialization, both Euler stages, freeze behavior, both limiter
directions and release, negative Iq bounds, bypasses, refresh and restart guards.
An initial test exposed that a rejected corrector could change the cached input;
the corrector protocol is now checked before changing observable state/input.

## Native partial profiles

Two timesteps (0.5/0.25 ms), each cold-run twice, have identical raw CSV bytes.
The public three-bus fixture uses REGCA1, native REECE and a fixed-grid GENCLS.
QFLAG=2 and PEFLAG=1 activate the new local loops; a +0.03 P-reference and +0.02
Q-reference step occurs at 0.1 s. Kqp=2/Kqi=5 and Kpp=2/Kpi=10 are retained
unchanged. Initialization reports O.K.; all raw channels are finite.

Every staged input/output digest, 79 actual CONs and eight ICONs is checked.
The committed compressed references preserve all nonnegative-time input rows and
both event sides. Inputs are filtered Pe, Q, prepared/ramped Pref (VAR5), external
Qref step, Iq bounds and voltage-dip flag. VAR15 alone is not the ramp output:
at the fine-step event VAR5 is 0.26475 before reaching 0.27. The comparison must
not silently skip or shift that sample. Current bounds remain inactive, so
replaying them cannot fit the PI trajectories.

Checkpoint comparisons include reactive PI STATE2, active PI STATE7 and the
positive reactive-current command. Initial states come from terminal Q/V and P,
not observed PI-state fitting. Native event/STORE startup differs from modified
Euler; the method is not replaced to reproduce that startup artifact.

Coarse/fine maximum errors are respectively:

- Reactive PI integral: 1.25012e-5 / 6.25058e-6 current PU.
- Active PI integral: 3.74962e-5 / 2.20353e-5 power PU.
- Reactive-current command: 1.24900e-5 / 6.24313e-6 current PU.

All maxima occur at the first advancing sample after the reference event. Tests
require error reduction on step halving and use channel-specific tightened
bounds in addition to the rate/precision budget. No event samples are excluded.
Raw/input/checkpoint bundle SHA-256:
`1f4500e9593ca544a24e7b7f3e5a4dd6b65e9e6b1987b041cc8269b7fff6c917`.
See the [reference contract](../../ipss.test.plugin.core/src/test/resources/reece-local-pi/README.md).

## Gates and next production work

Tested baseline is `639e50c90` plus this slice, including the committed ST1 local-
integration fix. The unrelated untracked WECC240/JAX comparison is excluded.
Core 1.4.3 JAR SHA-256:
`f7380328ff7b4d81686a5aae2c172d964013ae74deb81c964050a6a74db0d4ad`.

Focused validation passes fourteen unit and two native local-PI tests. The
final XML audit confirms 248 plugin-core tests (six optional skips) and 1,276
SMIB/catalog/staggered-solver tests (one diagnostic skip), with zero failures
or errors. These totals are separate gates, not additive unique test counts.
The 12 scoped source/resource files match the tested snapshot; 2,017 remaining
baseline Java/POM files match committed `639e50c90` after line-ending normalization.
The newly committed ST1 local-integration test is present in the baseline but
is not selected by this SMIB suite; its inclusion is not claimed by these counts.

Reproduction commands, from an isolated checkout of that baseline plus this slice:

```text
mvn -q -pl ipss.plugin.core -am test -Pextended-tests,isolated-verification -Dverification.run.id=reece-foundation-units-20261018-c --fail-at-end
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=reece-foundation-smib-20261018-d -Dtest=DynamicModelSmibTestSuite,DynamicModelCatalogTest,PsseStaggeredEulerSolverTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
```

Remaining work is the complete eight-state controller and exact versioned
catalog/parser/attachment path, reference ramp, PE-enabled power measurement,
QFLAG 0/1/2 branches, current limits/FRT selection, blocking and holds, compensation,
zero-Tpord bypass, linked anti-windup, lifecycle and coupled native/PowerWorld
profiles. These local profiles do not exercise freeze/limiter/FRT branches in
native reference simulator; those currently have unit-equation evidence only.

The local private evidence archive is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-reece-local-pi-foundation`.
It retains four successful native runs, both rejected standard-name attempts,
exact runnable probe/input files, all channels/logs/manifests, source diagrams,
replay generator/audit and final source/test evidence. Simulator-driving Python
is not force-added through repository ignore rules.
