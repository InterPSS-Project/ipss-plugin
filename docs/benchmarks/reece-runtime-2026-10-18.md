# REECE typed runtime and native limiter correction

Date: 2026-10-18. Status: typed eight-state runtime with bounded native
recorded-input evidence, not complete native import or model acceptance.
Approved native renewable loadability remains **21/27**.

## Source and implementation

The installed PSS/E 36.7 Model Library, section 22.6 (printed pages 859-866),
defines standard-name REECE: eight ICONs, 79 CONs, eight STATEs and 20 VARs.
Model Library SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
Native DSMDL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
The [foundation report](reece-local-pi-foundation-2026-10-18.md) records source
diagrams and independent WECC/PowerWorld references. Vendor/version layouts
remain separate; no guessed REECE1/REECEU1 aliases or wrappers are introduced.

`Reece1Model` implements the eight-state controller through the existing typed
converter interface. It includes voltage and active-power measurement, active
reference ramp before the optional power PI, QFLAG 0/1/2 paths, voltage/droop
compensation, PFLAG speed multiplication, Paux before voltage division, two
priority selectors, current circle/VDL limits, signed reactive hold, active
hold and delayed unblock. Predictor/corrector stages use modified Euler.
Zero-duration algebraic refresh does not consume corrector history or advance
timers. Initialization and restart derive states from terminal P/Q/V; named
state maps are immutable. Inactive PI/filter storage remains inactive.

Two important corrections were established rather than hidden by tolerances:

1. In voltage-control mode Qmax/Qmin constrain Vext **before** adding the
   configured Vbias. Native Vbias=.03 and nonzero droop compensation expose
   the difference from absorbing the bias into a different limiter coordinate.
2. Native PI integral-state limits are independent of output clipping. In the
   cascaded Q branch, STATE2 reaches Vmax even while Kqp times error is positive.
   In the local-Q and power branches, STATE2 reaches the moving Iq limit and
   STATE7 reaches Pmax. The foundation's output-based integration freeze was
   therefore wrong at active bounds. The shared `Reece1LocalPi` rule is corrected
   and reused by the runtime; its formerly inactive-limit native profiles still
   pass. A proportional-output limit alone no longer freezes an inward/allowed
   integral rate. Voltage-dip freezing remains state-only.

## Native campaign and comparison scope

Twenty new cold native runs produce ten branch/timestep datasets, in addition
to the four foundation runs reused by two full-runtime replay datasets.
Every accepted run reports initial conditions O.K.; exact allocations, selectors,
declared versus native single-precision CONs, raw outputs and file hashes are
audited. All used channels and timestamps repeat exactly. Two raw pairs differ
only in unused VAR2 when PFFLAG=0, an uninitialized PF-angle slot. Both raw files
are retained, and the difference is explicitly declared rather than silently
normalized away.

Runtime replays consume only terminal P/Q/V and external reference steps.
They retain every nonnegative-time sample and both event sides. No current-limit,
filtered measurement, prepared reference or PI-state oracle is fed into the
controller. Initialization uses physical P/Q/V, not observed STATE fitting.
Frozen decoded resources and raw-run fingerprints are checked permanently.

The original local-Q/power-PI branch reduces the maximum Pord error from
3.5812e-4 to 1.5121e-4 pu and Ipcmd error from 3.5458e-4 to 1.4971e-4 pu when
halving the step. Local-Q and power-integral errors also decrease. Native
single-precision filter/integral accumulation creates separate residual floors;
not every channel improves monotonically. In the cascaded-Q fine profile,
the voltage PI output accumulates a larger precision residual than at the
coarse step. Per-channel bounds must retain this distinction.

Native prepared Pref changes during the zero-time event callback; this runtime's
ramp advances only with time. Its event-post discrepancy is .0300 pu at 0.5 ms
and .02475 pu at 0.25 ms. It is explicitly **unaccepted**, not an eighth parity
channel and not erased by event exclusion. Downstream first-step errors remain
included. Large reference/current-limit excursions have a larger truncation
envelope and require a separate step-halving gate, not the small-step envelope.
The initially same-row negative-excursion Iq comparison gave 0.0403049 pu at
0.5 ms and 0.0203248 pu at 0.25 ms. A native-only equation discriminator
localized this primarily to callback publication: VAR14 uses the previous
callback's Q and the current limit formed from previous-callback Ipcmd, before
the newly exported terminal-Q measurement. Its native equation residual falls
from 0.0402798 pu with same-row Q to approximately 1e-7 pu with the proved
callback inputs. The permanent two-step discriminator guards that contract.
Command observations now refresh only that algebraic input, assert that every
state/reference remains unchanged, then restore the current endpoint. Dynamic
integration still uses both endpoints, with no state time shift or excluded
event. This removes the main measurement-publication error: Iq error becomes
0.00142985/0.000739728 pu at the two steps. Native prior-Ipcmd headroom feedback
versus the runtime's instantaneous algebraic limit remains an open callback
convention difference, rather than a fitted delay in the model equations.
Pord error falls from 0.0125617 to 0.00652231 pu. These remain bounded partial
profiles, not complete-model parity; all maxima/timestamps are retained.
The negative excursion does not prove that the lower power-integral limit is
active; that boundary currently has unit-equation evidence only.

## Tests and evidence

Final XML audit: 47 focused tests, 266 plugin-core tests (six optional skips),
and 1,284 registered SMIB/catalog/staggered-solver tests (one diagnostic skip),
all with zero failures or errors. Counts are separate gates, not additive unique
test counts. Tested baseline is `890e887f0` plus this slice. Twenty-two scoped
source/resource files match the tested snapshot; 2,021 remaining baseline
Java/POM files match that commit after line-ending normalization. The now-
committed static-current-load solver/runner/test work is included, and its small
SMIB regression is explicitly selected. The unrelated untracked JAX comparison
is excluded and left untouched. The existing ST1 local-integration test is not
selected by this SMIB suite.

Core 1.4.3 JAR SHA-256:
`f293ca958f363b814681f76bf310447d826fb5996e3bacb95aa5789a6a4b33e7`.
The archive check detected the local JAR had been rebuilt since the foundation;
final gates were rerun with this fingerprint checked before and after the test
window. The previous unsealed archive attempt is retained as rejected evidence.
Final gate commands from the isolated checkout:

```text
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=reece-runtime-focused-20261018-w -Dtest=Reece1ModelTest,Reece1LocalPiTest,ReeceRuntimeNativePartialConformanceTest,ReeceTypedSmibIntegrationTest,EmbeddedTrajectoryCoverageInventoryTest,PsseStaticCurrentLoadVoltageCurveTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
mvn -q -pl ipss.plugin.core -am test -Pextended-tests,isolated-verification -Dverification.run.id=reece-runtime-units-20261018-x --fail-at-end
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=reece-runtime-smib-20261018-y -Dtest=DynamicModelSmibTestSuite,DynamicModelCatalogTest,PsseStaggeredEulerSolverTest,PsseStaticCurrentLoadVoltageCurveTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
```

Typed three-bus tests exercise equilibrium, a three-cycle Bus2 fault and a
generator trip/reinitialization at both steps. Those tests establish coupling
and bounded finite behavior, not independent coupled trajectory acceptance.
No full Texas2k dynamic simulation is run.

The private archive is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-reece-runtime-f293ca95`.
It retains scripts/inputs, raw logs/channels/manifests, both cold runs, rejected
focused attempts, frozen reference generation/audits and tested source/XML.
Simulator-driving Python remains local under the repository's ignore policy.

## Remaining work

- Exact native standard/wrapper catalog, parser and deferred attachment routes;
  do not map the unsupported release names by guessing their layouts.
- Resolve native prepared-reference callback semantics and instantaneous event
  publication/current-limit feedback without altering integration to fit a trace.
- Independent coupled native/PowerWorld flat/reference/fault trajectories,
  additional bases and non-unit speed linkage to physical wind mechanics.
- Native lower power-integral saturation/release, active voltage-PI limiting
  and linked anti-windup, FRT-priority changes, blocking/holds and lifecycle
  branch profiles. Existing unit tests do not substitute for those references.
- Minimum-time-constant/version policies and all initialization-limit branches.

No row is promoted to VERIFIED by this slice.
