# Exact numerical factor reuse and 25k performance follow-up

Core commit `74d19134c13d805fb76c3f8eaabc58e22609d3ba` retains one accepted numerical factorization in each
eligible retained NR preparation. The same Newton engine, polar/rectangular
equations, mutation validation, convergence settings and Jacobian refresh policy
remain in use. This checkpoint does not close the consolidation plan.

## Reuse contract

Every factorization request still receives the freshly assembled operator.
Reuse requires the same CSC owner, symbolic owner, exact column pointers, row
indices and finite values, together with the supported KLU configuration.
Signed zero is distinguished. A different RHS is allowed because the solve
still consumes its current values. A numerical miss refactors the independent
working factor state. Structure, ordering and owner/configuration changes discard
the entry. Certificate failures discard it before the existing checked retry.
Unsupported custom ordering callbacks are not captured.

The first accepted entry is retained, rather than a history of Newton iterates.
Its mutable factor arrays and solve scratch are independent of the working LU;
immutable index arrays are shared. Numerical metadata and KLU status/rank outputs
follow their associated factor state. Numerical misses cannot mutate the saved
factors. This is exact linear-operator reuse, with fresh RHS and corrections;
it does not reuse a prior solved voltage or relax the modified-Newton policy.

The general sparse backend defaults to disabled. `NrAssemblyPlan` enables it
for retained and fixed-operator NR preparation; fresh generic preparation keeps
its existing behavior. A per-matrix setter can disable or re-enable retention.
The feature applies to both coordinate formulations through the common backend.

## Regression and mutation verification

Full source run `nr-exact-factor-full-20261031` executes **7,990 identities:
7,883 pass, 91 skip, 11 fail and five error**. All **577 required checks pass**,
including 49 additions. Relative to the 7,941-identity standalone checkpoint,
there are no missing/newly failing/skipped identities, changed baseline failure
diagnostics, fork dumps or Java source changes after capture. The same 16
existing failures/errors remain. The full-tested and timed production classpaths
are byte-identical across all 108 entries. Nine benchmark-tool Python tests pass.

Seventeen backend cases cover conservative default, fresh RHS and inspection,
alternating operators, structural zero changes, signed zero, unnotified writes,
nonfinite rejection/recovery, equal-size pattern changes, ordering/configuration
and owner replacement, singular failure/recovery, pivot fallback metadata,
certificate retry, enable/disable, row materialization, RHS panels, concurrent
worker scratch and independent numerical copies. Thirty-two public NR cases
span AUTO/CACHED and polar/rectangular execution with RHS, tap, phase, base shunt,
switched-shunt B, SVC B, PV-to-PQ and constant-impedance-load mutations, including
notification-suppressed changes. Each compares against fresh generic execution
after first proving positive exact reuse on repeated fresh starts.

Initial fixture failures are retained in the local evidence. The rectangular
fixture required the Complex voltage setter to update both coordinate views;
the phase mutation required a phase-shifting branch. An independent exported
operator and SciPy full-step reference reproduce failure of the initial lower-Q
PV-to-PQ target from the chosen accepted state. The upper-Q target used in the
final test converges independently. This does not establish global infeasibility
of the lower target. No production benchmark input or tolerance was changed.

## Matched maintained-API timing

The same compiled driver compares the preceding committed standalone runtime
with this implementation in fresh JVMs, in before/after/after/before order.
IEEE118 has 50,000 total warmups and 10,000 measurements per process;
PEGASE9241 and 25k have 50 total warmups and 100 measurements. The required Java
initialization solve counts within warmup. CPU 2, affinity mask 4 and
`-Xms256m -Xmx2g` are verified. Timers include the maintained load-flow call.
Builds, intrusive profiling and tests do not overlap the timing batches.

| Case | Before mean ms | After mean ms | Mean reduction |
| --- | ---: | ---: | ---: |
| case118 | 0.04180775 | 0.03871864 | 7.39% |
| case9241pegase | 14.99182400 | 13.93323100 | 7.06% |
| activsg25k | 51.09313350 | 45.99122350 | 9.99% |

All 40,800 measured samples remain. Final voltage files are byte-identical
before/after; independent original-Ybus residual and voltage-reference checks
pass. Actual cached execution, owned completion, one plan and retained symbolic
factors are verified. After the first solve, each subsequent solve hits the
initial exact operator once, while later Newton matrices still refactor normally.

Separate one-warmup/one-measurement 25k observations are **[113.7658, 107.8875]
ms before** and **[105.7792, 107.8444] ms after**, again in balanced fresh JVMs.
They retain identical voltage files and accuracy checks. These small samples do
not restore the historical 86-90 ms startup result. The earlier 88.64-to-201.47 ms
increase was a real consolidation regression under the same warmup protocol;
see the [historical diagnosis](nr-25k-regression-diagnosis-2026-10-30.md).

## Direct RustPower comparison

The regression-tested runtime is compared directly with the pinned RustPower
KLU runtime in three fresh processes per implementation, alternating order by
fork. The harness requires separate small and large cohorts. Warmups and
measurement counts match the warmed protocol above, including 50/100 for 70k.
All 121,800 measured samples are retained; the ratio is InterPSS/RustPower pooled
mean time and the requirement is <=1.2 for each case.

The qualified fixed-control canonical nodal models, input hashes and initial
voltages are unchanged. The 25k case uses compensated flat start; 70k uses the
qualified saved-state start with the required PV/reference setpoints. Tolerance
is 1e-6 pu, with a 20-iteration limit. RustPower reports five iterations for 25k
and six for 70k; InterPSS reports six for each. This compares complete maintained
nodal solves, with each implementation's iteration policy, rather than equal-
iteration kernels or a full controllable-device model. See the
[large-case qualification](rustpower-large-cases-2026-10-30.md).

| Case | RustPower ms | InterPSS cached ms | Ratio | <=1.2 |
| --- | ---: | ---: | ---: | --- |
| case39 | 0.01251163 | 0.01446777 | 1.156 | Pass |
| case118 | 0.02895085 | 0.03790217 | 1.309 | Fail |
| case9241pegase | 12.75403233 | 13.76508333 | 1.079 | Pass |
| activsg25k | 40.64319867 | 47.07143133 | 1.158 | Pass |
| activsg70k | 220.20290167 | 220.43152933 | 1.001 | Pass |

Four cases meet <=1.2; IEEE118 remains above the target at 1.309.
Accuracy, positive cached/exact
reuse coverage, actual factorization work, complete repeat identities, input,
source and runtime hashes, and before/after process affinity pass the audit.
The benchmark records actual symbolic analyses, full factorizations, numerical
refactors and pivot fallbacks separately from engine factorization requests.
Small-case external warmup counts are verified from executed source and launch
flags; that validation schema lacks separate warmup-counter fields.

`initialFactorOwnedArrayBytes` measures owned array payload only. It excludes
object headers, shared immutable indices and referenced KLU metadata; it is
not total retained heap. All measured JVMs retain the 2 GiB heap bound, including
70k. The recorded used-heap observation is taken after the case, not at peak.
The checkpoint retains per-process counters and memory observations.

## Evidence and remaining work

Local evidence roots are `ipss.test.plugin.core/target/nr-exact-factor-reuse-20261031/`
and `ipss.test.plugin.core/target/nr-exact-factor-rustpower-20261031/`.
They retain all samples, all-bus voltage outputs, source snapshots/patches,
runtime fingerprints, full XML identity/diagnostic audit, earlier failed fixture
attempts and the independent transition reference. The
[machine-readable checkpoint](checkpoints/nr-exact-factor-reuse-2026-10-31.json)
records hashes, complete external summaries and actual-work/memory counters.
Specialized coefficient/capability contracts, complete coverage and interaction
closure, final generic/frozen comparisons, startup recovery and all-five
performance qualification remain open. This checkpoint makes no new dynamic
trajectory acceptance claim.
