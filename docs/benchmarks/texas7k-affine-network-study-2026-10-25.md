# Texas7k affine network-iteration study

The latest matched control has a **20.796-second whole-run median** on one
logical CPU. Neither new affine-network batch establishes a speedup. The
20-second objective remains open, and these runtime variants remain experimental.
The separately committed [default frequency selection](bus-frequency-selection-2026-10-25.md)
retains 704 required measurements out of 6,717 buses.

## Complete warmed measurements

Each fresh network runs ten simulated seconds with the quarter-cycle step,
five-cycle bus-111180 fault, 1e-4 pu network convergence tolerance and
50-iteration ceiling. Whole time includes import, power flow, initialization,
controller preparation, complete in-memory recording, binary export and
recovery tables. All 2,403 samples and 6,732 channels remain, including both
event sides. Device updates are serial; OS affinity is verified as CPU 6 only,
with one active JVM processor and Serial GC.

| Matched batch | Full warmups shared by both modes | Control median / range | Candidate median / range |
| --- | ---: | ---: | ---: |
| Affine RHS and reciprocal triangular kernel | 6 | 20.862 / 20.707–25.403 s | 21.545 / 20.936–22.337 s |
| Cached publication magnitudes and empty-device shortcut | 8 | 20.796 / 19.703–21.149 s | 21.264 / 21.249–21.684 s |

Every mode has three measurements in the same JVM, with alternating warmups
and interleaved measurements. Residual compilation stays included in the
reported times. Cold runs and the separately instrumented CPU profile are
excluded. The ranges overlap; an isolated sub-20-second control measurement
does not establish a reproducible 20-second result.

In the second batch, median network time is 11.951 seconds for the control
and 12.302 seconds for the candidate. Device/measurement integration is
6.281 and 6.333 seconds respectively. Reducing iteration counts has not
reduced enough work per complete run.

## Algorithm and work accounting

The full real current Jacobian is frozen between matrix changes. For physical
network matrix `M`, current `I(v)` and frozen derivative `J`, the candidate
solves `(M-J) vNext = I(v)-J*v`. This avoids a separate sparse `M*v` residual
product. The full real Jacobian includes non-holomorphic current dependence;
a complex-linear-only approximation increased work and was rejected.

The physical network solve still checks the fresh voltage correction against
the unchanged convergence tolerance. A small preconditioned step alone never
declares convergence. Fresh-solve minimum iterations, voltage-dependent current
refresh, integration stages and accepted-endpoint invalidation remain enforced.

The full affine run uses 17,555 outer iterations, compared with 26,803 for the
control. It also makes 17,547 preconditioner solves, 4,800 physical-map
certificates and eight ordinary initial solves: **22,355 linear updates**.
Outer iterations therefore are not a count of all sparse solves. Three
Jacobian factorizations and three physical-matrix factorizations cover the
initial network, fault application and clearing. The real factor has dimension
13,434 and 276,028 nonzeros.

The faster numerical kernel skips division by a proven unit lower diagonal
and uses finite upper-diagonal reciprocals, retaining ordinary division when
a reciprocal is nonfinite. Publication caches exact computed magnitudes.
Its raw certificate is stage-scoped and single use. A later contract refinement
keeps candidate and raw magnitude buffers separate so an invalid candidate
cannot corrupt a valid raw fallback.

The exact standard-bus empty-device shortcut still updates generators first.
Custom buses and nonempty lists keep their existing dispatch. Its combined
timing batch does not establish an independent benefit for that shortcut.

An additional variant tests the physical criterion before another Jacobian
solve. It passes the numerical gates but makes 9,541 raw certificates and
falls back more often: 12,343 preconditioner solves plus 9,541 raw certificates
and 3,781 ordinary solves, or 25,665 linear updates. It was not selected for
another warmed timing qualification.

## Numerical and lifecycle checks

All 6,717 bus-voltage channels pass both configured periods against two
tighter internal controls. For the affine/reciprocal result, worst fault and
postfault range-normalized RMSE is **0.0003075**, maximum voltage difference
is **0.0002738 pu**, and worst full-run RMSE among the three recorded rotor
angles is **0.0007422 degrees**. Prefault rotor differences are zero. These
are internal convergence comparisons; complete independent network-reference
acceptance remains open.

The cached-magnitude and empty-device variants retain the complete affine
recording byte for byte. The earlier-certificate variant also passes both
controls; its worst recorded rotor-angle RMSE is about 0.001882 degrees.

- Full-Jacobian algebra checks exercise 100 random matrices and 49,172
  assertions, including conjugate-voltage dependence. Maximum root error is
  1.111e-15.
- Lifecycle checks cover fresh current consumption, unchanged differential
  states, stale stage/matrix rejection, atomic invalid-candidate rejection and
  single-use raw publication. The final earlier-certificate probe passes
  87 assertions, including fallback after an invalid candidate.
- Four existing independent converter profiles pass with unchanged model
  equations, checkpoint tolerances and limiter coverage. Both active
  preconditioning and ordinary fallback are exercised.
- The empty-device behavior probe passes 24 assertions covering generator
  dispatch, list attachment/removal, activation, custom buses and script failure
  propagation.

The retained sampling profile identifies triangular solves, current-row
evaluation, voltage publication and bus/device traversal as remaining work.
Its instrumentation triggers substantial compilation; sample counts are not
elapsed CPU seconds, and its whole duration is not a warmed benchmark result.

The [evidence checkpoint](checkpoints/texas7k-affine-network-study-2026-10-25.json)
indexes tested source and bytecode snapshots, classpath layers, bounded logs,
affinity/compiler/CPU records, full recordings, comparisons and failed launch
attempts. Older inherited `source_commit` fields contain a command placeholder;
the manifest hashes provide executable provenance. Identical generated traces
share storage only after hash verification. Supplied references are preserved.
