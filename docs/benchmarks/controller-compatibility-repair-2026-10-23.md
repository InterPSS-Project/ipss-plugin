# Retained solver compatibility repair

The subsequent [full merge-validation rerun](full-merge-validation-2026-10-23.md)
includes all newly added tests in the full reactor. Core and offline checks pass;
the same three plugin accuracy gates still prevent the requested merges.

The restored fast solver now passes its general convergence contracts and the
full core suite. Obsolete optimization-specific tests were retired; the removed
voltage-history table and compiled controller implementations were not restored.
The full plugin build reaches execution and has three previously documented
accuracy failures. The requested milestone gate remains failed: neither main
branch was merged, and no milestone commits were created.

## Repairs

- Keep fused voltage convergence for ordinary, arranged engine networks. Use
  the earlier ordinary history path for proxy/custom networks, unarranged bus
  slots and custom history tables. Preserve both convergence norms, minimum
  iteration counts, callback sequencing and invalid-voltage diagnostics.
- Replace four stale core feature-toggle tests with numerical and lifecycle
  contracts for current-buffer publication, bus iteration, voltage coordinates
  and ZIP loads. Remove the three tests dedicated to the absent history table
  and six plugin tests dedicated to removed compiled-controller implementations.
  Existing model equations, trajectory tests and limiter checks remain active.
- Configure ST1 storage, boundary reuse and fixed scheduling per instance;
  configure PSS2A numeric frames through an explicit constructor argument in
  tests. The production defaults and integration stages remain unchanged.
- Keep the retained solver's fixed rectangular network policy explicit in the
  reference fixture. Report that policy accurately and reject an explicit
  request for the retired polar-network toggle before applying configuration.
- Normalize mixed whitespace/comma case headers before reading their system
  base. Reject a nonfinite or nonpositive base. Two delimiter fixtures now
  retain their 100 MVA base and finite admittances rather than propagate NaNs.
- Permit the previously verified GENQEC-family T'qo=0/X'q=Xq branch during
  startup validation. Other-family, invalid-time and zero-subtransient checks
  remain active. See the [branch evidence](genqec-salient-branch-2026-10-19.md).
- Give IEEE39 benchmark exports a unique temporary directory for each test.
  This preserves the output guard and avoids overwriting retained exports.
- Tighten the small VSC parity fixture's load-flow solve to 1e-12, matching its
  existing tight comparison assertions. Production tolerances and those
  assertions are unchanged.

An additional independent defect affected five full-suite sensitivity tests.
Their parallel requests shared a sparse factor's mutable solve workspace. A
concurrent cache map did not protect factor preparation or the numerical solve.
The common sensitivity entry points now serialize those operations on their
solver instance, covering both cached implementations and the uncached solver.
Other solver instances remain independent. This change is outside the transient
simulation path.

The regression uses eight workers and real sparse solves, with an instrumented
workspace boundary. All six variants fail on the preceding artifact with eight
simultaneous entries; all pass after the repair with one entry at a time. The
five original sensitivity failures also pass in the subsequent full plugin run.

## Validation

Both full reactors used fresh isolated output directories, offline Maven,
bounded logs and a 2 GiB test heap. The plugin run enabled `extended-tests`.
No accuracy assertions were relaxed and no new tests were skipped.

| Run/module | Tests | Failures | Errors | Existing skips |
| --- | ---: | ---: | ---: | ---: |
| Full core | 3,096 | 0 | 0 | 0 |
| Plugin core | 435 | 0 | 0 | 4 |
| Three-phase plugin | 468 | 0 | 0 | 28 |
| Plugin integration module | 3,259 | 3 | 0 | 54 |
| Final focused core contracts | 43 | 0 | 0 | 0 |
| Final focused plugin checks | 48 | 0 | 0 | 0 |

The focused core run includes all 24 earlier convergence/lifecycle contracts,
two real-network fused/fallback comparisons, eleven replacement numerical
contracts and six concurrent-sensitivity regressions. The focused plugin run
also includes three new parameter-boundary tests added after the full reactor's
test compilation, plus the original three salient-branch conformance tests.

The three remaining full-build failures are:

1. `CGMESCasP4AclfSmokeStubTest.testP4_ReliCapSvedala_AclfVsSv`:
   voltage agreement is 68/86, or 79.07%, below its 85% floor.
2. `MatpowerCase8387PegaseTest.testCase8387PegasePqNonDivergentNormalControlPowerFlow`:
   the PQ solve does not meet its 0.001 pu mismatch requirement within 50
   iterations.
3. `Texas2kSixCaseDynamicSmokeTest.allSixCaseStacksInitializeAndHold`:
   Case 1's Bus1051 complex-voltage drift exceeds 0.0002 pu in the no-event run.
   The separate six-case fault and generator-trip tests pass.

These failures were already recorded in the
[October 19 full-stack validation](generator-model-preprocessing-2026-10-19.md).
They remain enforced. A smaller-step diagnostic did not resolve the flat-run
failure, and that experimental fixture change was reverted. Complete trajectory
accuracy against the supplied references remains unresolved.

## Complete recorded runtime check

The quiet CPU-6 check used sixteen alternating warmups and ten measured runs
per case, producing five candidate measurements. Each simulation covers ten
seconds with a quarter-cycle network step, 1e-5 network tolerance, one ST1 local
step and the pure Java sparse solver. Timers include preparation, initialization,
simulation and complete recording; source compilation is outside the timer.

| Case | Earlier quiet median | Repair-check median | Candidate range |
| --- | ---: | ---: | ---: |
| 1 | 8.909746 s | 9.037235 s | 8.929366–9.082259 s |
| 2 | 6.429334 s | 6.641334 s | 6.299947–6.756369 s |

These checks used the Maven-built main classes before the final sensitivity
workspace guard. The deltas from the earlier cohort are +1.43% and +3.30%; this
is not an exact reproduction of the historical best timings or a controlled
attribution of the difference to a source change. The earlier cohort used the
historical direct compiler recipe. All 52 complete recordings match the retained
checkpoint, including every binary sample, selected CSV value and channel map.

A final source-only build recompiles all 1,836 core and 885 plugin main Java
files with the historical `-g` recipe, including the final workspace guard.
Two complete runs per case match all three retained recording artifacts. These
four runs verify the final source state; they make no warmed timing claim.
Only explicitly pinned benchmark support classes are reused. No archived
production engine classes are on that verification classpath.

Agreement with the performance checkpoint establishes regression equivalence,
not independent reference accuracy. The original performance tags, selected
checkpoint, backups and stash are preserved. Their source replay still requires
the corresponding tagged sources; its source checks were not bypassed to accept
the new uncommitted repair.

## Evidence and merge status

The [repair index](checkpoints/controller-compatibility-repair-2026-10-23.json)
pins source patches and inventories, retired test sources, full Surefire XML,
bounded logs/statuses, the failing-before/passing-after concurrency checks,
exact runtime snapshots and complete recording hashes. Earlier failed-build
and test-relevance evidence remains separately retained.

After verification, only newly generated binary/CSV duplicates are replaced
with pointers to the retained identical originals. Channel maps, event-side
samples, supplied references and evidence dependencies are preserved.

The repaired changes remain available for review. Milestone commits and merges
are held because the user made them conditional on passing both full suites.
