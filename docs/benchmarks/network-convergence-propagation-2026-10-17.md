# Dynamic network convergence: propagation and diagnostics

## Scope and corrected contract

Baseline: plugin `f3d139f89`, core `e886019ce`. The model equations and case
inputs are unchanged. The former core discarded the boolean result of all three
modified-Euler network solves. Exhausting the iteration budget could therefore
advance differential states, emit an endpoint and advance time despite failing
the network convergence check. `performSimulationTo()` separately ignored failed
steps and could return success after failure.

The corrected default integration path requires convergence at INITIAL,
PREDICTOR and CORRECTOR network stages. A failed stage stops the step before
another integration stage, endpoint output or clock advancement. The public
step API returns false, marks the dynamic network and load-flow operating point
invalid, and blocks retries until successful reinitialization. **This is not a
rollback**: model states or event topology may already be partly advanced.
Reload/reestablish the operating point and initialize; do not merely increase
the budget and retry the failed mutable state. Both simulation entry points stop
at their first failed step. Exception causes are retained with stage/time context.

The existing componentwise convergence rule is preserved:
`abs(deltaVr) < 1e-4` and `abs(deltaVi) < 1e-4`. The metric below is a voltage
fixed-point update, **not a KCL/current residual or proof of an exact mathematical
root**. Core's general iteration limit remains 20. Zero/one-iteration budgets
cannot claim success without the required successive-iterate convergence check.
Nonfinite voltages and failed network-equation solves are rejected explicitly.

The opt-in staggered-Euler solver was inspected separately: its override already
throws on a false network result. Its numerical method/tolerance are unchanged.
Inherited modified-Euler wrappers also enforce false returns from custom network
solver implementations; detailed bus telemetry is supplied by the default solver.

## Permanent inspection API

The algorithm exposes its configured solver through an additive Java accessor,
without introducing a serialized EMF feature. The interface default preserves
binary compatibility for third-party implementations and reports unsupported
access explicitly; the standard implementation returns the configured solver.

```java
var solver = (DStabSolverImpl) algorithm.getSolver();
solver.setMaxIterationTimes(500); // numerical cap; can be set before initialization
boolean ok = algorithm.solveDEqnStep(true);
var diagnostic = solver.getLastNetworkSolutionDiagnostics();
```

The immutable last-solve record includes stage, step-start and physical network
evaluation times, iterations/budget, unchanged voltage tolerance, maximum
component voltage change, leading bus, old/new complex voltage, success and
reason. It retains no unbounded history and is cleared after successful
initialization. Before a solve, or for an overriding solver that does not supply
telemetry, no default diagnostic may be available. The record describes the last
network solve, not every possible later controller failure. Unknown/unmeasured
metrics and invalid-voltage diagnostics must not be mistaken for finite KCL data.

## Failures exposed on real cases

The diagnostic probes use fresh case loads for each budget and preserve only
completed endpoint-v2 frames on failure. Failed iterates are labeled separately
in logs; they are never exported as accepted endpoints.

| Case | Budget | First rejected stage/time | Leading bus | Maximum component voltage change |
|---|---:|---|---|---:|
| Three-bus native-Iolim fault | 20 | INITIAL, 0.10 s clearing | Bus1 | 1.34217e-4 |
| Texas2k Case 6, fault Bus1016 | 20 | INITIAL, 0.05 s onset | Bus4128 | 3.91067e-4 |
| Texas2k Case 5, fault Bus1011 | 20 | INITIAL, 0.05 s onset | Bus1046 | 4.32610e-4 |
| Texas2k Case 5, fault Bus1011 | 200 | CORRECTOR, evaluation 0.0833333 s | Bus1012 | 1.29629e-4 |

All exceed the unchanged 1e-4 tolerance. The final row's step-start/clock is
0.0791667 s: the predicted/corrected states are already partial, demonstrating
why automatic retry is unsafe. A leading bus identifies the largest remaining
network update; it does not prove that its generator or parameters are wrong.
In particular, the first Texas2k failures precede differential advancement at
fault onset, so they must not be attributed to a first bad dynamic derivative.

Increasing only the allowed iteration count produces complete, budget-independent
responses at the same tolerance:

| Probe | Successful budgets | Final fault-bus V (pu) | Identical complete voltage CSV SHA-256 |
|---|---|---:|---|
| Case 5 weak grid | 400, 500, 800 | 1.03031842226 | `696520e80aa795fe4880d5c8858d5c977895d6aeedb924e33e44ad987b619338` |
| Case 6 GFM terminal | 200, 400 | 1.68147241035 | `4ed61e1edb2707070385f4d519562e0df61ae06e469dbb42bbcb4ddb5460e1d1` |

Case 5 uses up to 280 iterations in the recorded final-stage diagnostics; Case 6
uses 46. These maxima cover the recorded completed final stages, not all stages.
Successful execution under the new guard separately establishes that every
called network stage met its convergence rule. The tiny REGCA1 probe also has
identical complete state/boundary exports at budgets 200 and 400:
`e96338b81534a7d95c0839cc576110352cb9b6708b4b0c6fe96c705d8df01c62`.

The old Case 5 endpoint was 1.121556 pu; its network iterations had not been
enforced. The severe GFM response persists near 1.68 pu even with converged
updates and extra budget independence. Convergence enforcement does not close
the REGCA1 iterative-HV or complete-system physical acceptance gaps.

## PSS/E numerical default

The PSS/E loader's solver now allows 500 iterations, stopping immediately when
the existing criterion is met. Core's general default and all physical model
parameters, integration steps and tolerances remain unchanged. The 500 ceiling
provides bounded headroom beyond the observed 280; it is not a target iteration
count, gain adjustment, curve fit or relaxed acceptance threshold. Callers can
override it on the configured solver. Non-convergence still stops the run.

Original RAW/DYR, GNET and MODREMOVE preparation, 60-Hz three-cycle events and
the 1/240-s production step are preserved. The probes and complete raw outputs
are retained privately under `2026-10-17-network-convergence`. The earlier
[Iolim report](regca-iolim-direction-2026-10-17.md) remains historical evidence
of the model-coordinate fix, not proof of converged system solves.

## Convergence-enforced timestep follow-up

The original reviewed matrix code is also invoked with in-memory settings and
fresh tags at h/2, without changing the full regression's configuration. Both
selected events complete at the default 500 ceiling. At h/4, Case 5 rejects a
CORRECTOR solve at evaluation 0.0958333333 s, step-start 0.0947916667 s:
Bus1012 still changes by 1.11547135359e-4 after 500 iterations. No incomplete
endpoint or successful matrix summary is exported. The failed log is preserved.

Fresh voltage-only probes at budgets 800 and 1,600 both complete Case 5 at h/4
with identical full exports (SHA-256
`9fbbf60baad701b433d8d04427ce343bc53a9e415f703f83e0f972e1b9d7589e`).
Its recorded final stages use up to 614 iterations. Case 6 h/4 completes at 500.
The 500 default is therefore not a universal guarantee: explicit numerical
settings must remain inspectable and overridable, and exhaustion must fail.

| Event | Final V, h | Final V, h/2 | Final V, h/4 | Max V difference h→h/2 / h/2→h/4 on h grid |
|---|---:|---:|---:|---:|
| Case 5 weak grid | 1.03031842226 | 1.08525075664 | 1.07609585649 | 0.112575 / 0.0993343 |
| Case 6 GFM terminal | 1.68147241035 | 1.68216275990 | 1.68495291013 | 0.0416794 / 0.0192820 |

Alignment uses physical time and sample kind; all six participating traces have
unique such keys. The h/2 matrix exports all 94/137 channels. Maximum h-to-h/2
speed differences over the three monitored machines are 0.00365077 pu (Case 5)
and 0.00156548 pu (Case 6). The successful h/4 probes contain **fault-bus voltage
only**, not h/4 machine-speed or controller-state acceptance. The full h/4 matrix
did not finish; do not describe these probes as its successful replacement.
Error reduction in one voltage statistic does not establish full trajectory
convergence, independent accuracy or acceptable GFM behavior.

## Verification status

Failing-before evidence: the two new stage/clock tests fail against the old core
(5 tests, 2 failures), and the new `performSimulationTo` test independently fails
before that entry-point fix. All eleven focused core tests subsequently pass.
The complete core suite/local install passes **2,918 tests, no skips/failures/errors**.
This is a locally rebuilt core 1.4.3, not a newly published artifact or version.
Corrected core commit: **`12c231df5`** (five scoped solver/API/test files).
Its installed JAR SHA-256 is
`b7f1f26be41ba8bf08d2037a1e51b0e800ec73266ffdb1d4e366b6766faf523e`;
the complete core log SHA-256 is
`cfa6da0049be9ffed5f1bb3a9877936235c4913d3daca767959c6145c9d7bdf8`.
The plugin dependency version is not bumped to suggest an unavailable release.
Build the corrected core sources locally before reproducing this plugin slice;
the public dependency guards intentionally fail against the older unpatched core.

Three registered plugin guards cover all false-return stages through the public
algorithm, actual iteration exhaustion and the configurable PSS/E ceiling. The
focused plugin run passes **32 tests without skips**, including both native-Iolim
steps, zero-Q recovery, converter equations, endpoint alignment and reference
integrity. Existing comparison bounds and native reference bytes are unchanged.

### Synthetic-fixture corrections, not newly accepted fault recoveries

The first full reactor run finishes with **2,730 tests, three failures, zero
errors, 89 skips**. All three formerly passed by advancing through failed network
solves. Its complete output and fresh XML are preserved under `first-regression`.

The REECB1 builder smoke used a one-bus island containing only a grid-following
converter. Its very first network stage fails with a 0.02416-pu component change.
The fixture now connects that converter to an explicit infinite grid; no model
equation, limiter, reference checkpoint or acceptance tolerance was changed.

The two aggregate synthetic deep faults still fail with 500 iterations. They are
not merely short-budget failures. An analytical test now eliminates the passive
network from a copy of the actual faulted Y matrix, removing only converter
cancelling Norton admittances. Identical converter states permit a symmetric
terminal reduction `V = E + Z*(Ip - j Iq)*exp(j theta)` on their common 100-MVA
base. A conservative bound, including the high-voltage branch, is

`|V| <= (|E| + |Z|*(|IpState| + |IqState|))/(1 - |Z|*Khv)`.

For both rejected states this bound is below LVPnt0 and Volim, so every symmetric
candidate must have Ip=0 and inactive HV logic. Rotating by -theta then requires
`|real(Z)*Iq| <= |E|`. The actual failed states contradict that necessary condition:

| Synthetic profile | Rejected predictor time | `|R*Iq|` | `|E|` | Symmetric voltage upper bound |
|---|---:|---:|---:|---:|
| Five plants, 0.5-ms step | 0.054 s | 0.000745387445177 | 0.000499102350257 | 0.0177475424116 |
| Thirty plants, 1/240-s step | 0.0541667 s | 0.00135418926027 | 0.000499102438550 | 0.0315179073514 |

The tests now assert explicit rejection **and that analytical contradiction**,
rather than merely expecting any false return. The thirty-plant strict flat check
is retained. This proves neither global asymmetric-root nonexistence nor native
REGCA1 low-voltage/angle handling. The former recovery literals are withdrawn as
unconverged evidence; independent synthetic recovery acceptance remains open.
No fault impedance, collector resistance, current limit, gain, tolerance or step
was adjusted to make these profiles run. All 23 focused fixture/convergence checks
pass after these corrections.

The complete plugin reactor exits zero on 2026-10-17: **2,730 tests, 2,641
passed, 89 skipped, zero failures/errors**. Only XML written after the validated
run started (15:43:08 local) is counted. The unrelated untracked WECC240/JAX test
is excluded; its source is untouched. The command is:

```text
mvn -q test -Pextended-tests --fail-at-end
  -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest
  -Dsurefire.failIfNoSpecifiedTests=false
```

| Module | Tests | Passed | Skipped | Failures / errors |
|---|---:|---:|---:|---:|
| ipss.plugin.core | 129 | 125 | 4 | 0 / 0 |
| ipss.plugin.3phase | 468 | 440 | 28 | 0 / 0 |
| ipss.test.plugin.core | 2,133 | 2,076 | 57 | 0 / 0 |

All six strict one-second flats pass at 1/240 s: maximum voltage drift
3.12838644e-12 pu and speed drift 5.66213743e-14 pu. The six-case flat/fault/trip
smokes and all fifteen topology faults execute successfully with enforcement.
The skipped tests are not represented as passed independent/private gates.

The final log SHA-256 is
`e57c1f298834d5912867f86aeb0df9501e56e773f3a580211cbb6c80c38cb85c`.
The private `completed-regression` bundle preserves 688 hashed artifacts,
including fresh XML, enabled settings, complete traces, the tested core JAR and
exact tested core/plugin source snapshots. Source private opt-ins are restored
to false after archival; the archived configuration records the actual run.
Remaining HV/model/schema/provenance and independent full-system acceptance work
stays open. In particular, the two synthetic rejection guards are not successful
fault recoveries, and the complete h/4 selected matrix remains unaccepted.

All fifteen reviewed fault events already pass on the second guarded run. Their
complete trajectory hashes match the first guarded run byte-for-byte with the
same original inputs, step, end time and monitored channels. Relative to the
older `f3d139f89` baseline, all fifteen hashes change once convergence is enforced;
that before/after comparison is preserved separately. Repeatability of the new
converged execution is not independent physical accuracy.
