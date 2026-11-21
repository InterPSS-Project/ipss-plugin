# Cross-case preparation fixes and Case 1 profile

Cases 3 and 4 now complete coordinated preparation in **11 and 17 iterations**,
respectively, instead of reaching iteration 9,996 with unsettled shunts. Both
subsequently fail the dynamic network solution at **0.045833 seconds**, before
the fault. The eight-second complete-simulation objective remains unmet across
cases; this checkpoint fixes preparation and exposes the next failure.

## Changes and verification

Core commit `e7489688b` admits continuous shunts colocated with non-regulating,
fixed-Q generators into the coordinated NR path. Generator P and Q remain
physical injections. Shunt reactive corrections are separated from fixed
generator Q when committing susceptance, enforcing bounds and restoring control
state. Regulating, unresolved and unsupported generator configurations remain
outside this path. The native-PQ and discrete projection paths retain their
previous eligibility rules.

The preparation helper now reconciles voltage-dependent aggregate plant limits
using the requested power-mismatch tolerance. After an accepted electrical solve,
it projects an otherwise infeasible reactive allocation onto the physical plant
and shunt bounds only when the resulting nodal residual remains within that
tolerance. Physical bounds and voltage-control tolerances are unchanged. This
avoids repeating the same accepted electrical solve solely to demand a tighter
power balance than the caller requested. Existing exact allocations retain their
previous path.

**137 targeted tests pass**, covering fixed positive, zero and negative generator
Q, multiple generators, independent equivalent constant-power injections, local
and remote shunt control, both susceptance limits, repeated solves, eligibility
guards, existing tap and shunt transactions, and generator-priority allocation.
The three new core tests fail against the saved runtime. The new plant-allocation
regression fails with the old helper and passes with the correction. No existing
test assertions were removed or weakened.

Two complete ten-second audit repetitions per case confirm **byte-identical
recordings for Cases 1 and 2** against their respective retained baselines. These
are trajectory checks, not warmed performance qualifications. The requested
quarter-cycle step, `1e-5` dynamic voltage tolerance, `1e-3` load-flow power
tolerance and verified single-logical-CPU affinity remain in force.

| Case | Preparation iterations before / after netting | Dynamic audit |
| --- | ---: | --- |
| 1 | 15 / 0 | Completes; both recordings match previous baseline |
| 2 | 16 / 0 | Completes; both recordings match previous baseline |
| 3 | 11 / 0 | Network solution fails at 0.045833 s |
| 4 | 17 / 0 | Network solution fails at 0.045833 s |

The failing dynamic solves report voltage magnitude exceeding 10 pu at bus 1007
in Case 3 and bus 1002 in Case 4. These are numerical failure diagnostics, not a
claim that the supplied systems physically reach those voltages. Their full
ten-second computational times remain unavailable.

## Warmed Case 1 profile

A separate profiler run uses twelve complete warmups followed by three sampled
complete simulations. All repetitions retain identical full recordings. This
diagnostic does not replace the earlier unprofiled **11.486569-second** Case 1
median or establish a new speedup.

The 1,751 main-thread Java execution samples divide as follows using full stacks:

| Stack category | Samples | Share |
| --- | ---: | ---: |
| Device/controller updates outside network iteration | 828 | 47.3% |
| Network iteration and solve | 676 | 38.6% |
| Preparation | 47 | 2.7% |
| Recording | 22 | 1.3% |
| Other | 178 | 10.2% |

Sampling shares are not wall-time phase measurements and do not capture all
native I/O or waiting. The largest sampled leaf methods include the factored
complex triangular solve, custom bus-current injection, static-load eligibility
checks and controller-to-machine access. ST1 and PSS2A preparation/evaluation and
fixed CML signal reads are also prominent. These provide concrete targets for
the next optimization experiments; recording is not the leading sampled cost.

## Reproduction and remaining work

The [checkpoint index](checkpoints/fixed-generation-shunts-2026-10-21.json) retains
the complete main and benchmark sources, changed regression sources, test
results, audit commands and statuses, failed preparation diagnostics and the
profiler recording. Large trajectory files remain separately identified by hash.

```powershell
python tools/run_bounded.py --log target/fixed-shunt-source-build.log -- python tools/build_case2_source_checkpoint.py --checkpoint docs/benchmarks/checkpoints/fixed-generation-shunts-2026-10-21.json --output target/fixed-shunt-source-build
```

The clean source build succeeds, and all **3,869 generated classes** match the
effective classes used in the audits, with no missing or differing classes.
Full API/system-test compatibility and
independent-reference accuracy remain unqualified, as documented by the earlier
source-restoration checkpoint. The next required work is diagnosis of the early
dynamic failure in Cases 3–4 and reduction of Case 1's device and network costs.
