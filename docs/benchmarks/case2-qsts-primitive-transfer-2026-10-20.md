# Reusing QSTS data transfer in the dynamic solver

The three-phase QSTS implementation already contains the main design needed to
reduce engine-to-solver conversion work: reusable primitive right-hand-side and
solution buffers, cached bus/device offsets, and explicit voltage synchronization.
The scalar dynamic experiment below reuses its existing complex sparse solver.
It does not change equations, convergence tolerance, or the integration scheme.

## Existing implementation

In `DistributionPowerFlowAlgorithmImpl`, the fixed-point loop selects
`PrimitiveComplex3x3Equation` when supported. The array extension,
`PrimitiveComplex3x3ArrayEquation`, exposes interleaved real/imaginary storage.
The JKLU adapter delegates to the same scalar complex solver used by dynamics.

The relevant paths are:

- `setPowerflowCurrentInjectionsDirectFast`: cached load/generator arrays and
  offsets accumulate directly into the primitive RHS. Device currents are
  recalculated from the current voltage array.
- `solvePrimitiveRhs`: reusable RHS and result buffers avoid packing matrix-row
  objects. It copies RHS to the result buffer and reuses an existing
  factorization; this is not a completely copy-free interface.
- `PrimitiveFixedPointState`: retains primitive voltages and saved magnitudes,
  compares solved values directly, and adopts the result buffer as the current
  voltage array.
- `syncBusVoltages`: materializes voltage objects at explicit observation
  boundaries. QSTS restricts deferred synchronization to selected execution
  modes and synchronizes observed control buses and final results.

`QstsStudy` enables deferred synchronization only with no result sampling,
no inverter controls, no delayed control queue, and voltage-only postprocessing.
The primitive state also has an eligibility check for internal regulator control.
`QstsDeferredVoltageSyncTest` covers final voltages, regulator taps and capacitor
states under changing loads. These restrictions are part of the design, not
optional details to discard when adapting it.

## Scalar dynamic experiment

The experimental overlay starts from the verified voltage-object reuse candidate.
It writes freshly evaluated dynamic injection currents directly into the existing
JKLU primitive RHS, solves, and publishes every solved matrix row before any bus
voltage update or convergence observer. Existing bus updates and iteration
callbacks remain in place. Ordinary matrix-row aliases still observe the solved
values at each completed solve boundary.

Eligibility is restricted to the exact standard network and bus classes, the
exact JKLU matrix implementation, a full active bus set, and no custom injection
tables. Unsupported configurations retain the original path. The experiment is
for the closed benchmark runtime: bypassing intermediate RHS row publication is
not yet a general contract for arbitrary dynamic devices that inspect the matrix
during current assembly. Production defaults are unchanged.

The focused check passes 12,000 exact floating-point observations across repeated
RHS changes and a matrix-value change/refactorization. It checks buffer identity,
RHS preservation, held row aliases, actual fast-path execution, and fallbacks for
inactive buses, custom current tables, custom bus classes, and explicit disable.

## Warmed Case 2 results

One full warm-up followed by three fresh-case runs in the same JVM produced:

| Measured run | Integration, s | Network, s | Runner elapsed, s |
| --- | ---: | ---: | ---: |
| 1 | 11.1704 | 6.1098 | 26.9839 |
| 2 | 11.3411 | 6.1907 | 25.8940 |
| 3 | 11.3372 | 6.3173 | 25.1160 |

The median is **11.3372 seconds**, compared with **11.2901 seconds** in the
[preceding control series](case2-voltage-runtime-experiments-2026-10-20.md).
The overlapping ranges show no meaningful improvement from this limited
transfer adaptation. It is not promoted into production. The ten-second target
remains open.

Every run uses one logical CPU, no simulation worker threads, a 1/240-second
step, and a 1e-5 maximum-component network tolerance. Each performs 2,400 steps,
4,800 network calls and 38,819 timed network iterations. The new path executes
38,846 solves per case including initialization. The warm-up integration takes
15.7529 seconds and is excluded from the measured median. Compilation is outside
the timers; integration excludes channel recording, while runner elapsed includes
case setup and output.

All four complete traces match the retained control byte for byte: 4,810 channels,
2,405 samples and 11,568,050 finite measurements per run. Event-side duplicates,
channel metadata, selected-channel traces and initial compensation records match.
This proves numerical nonregression against that control; independent-reference
accuracy acceptance remains open.

Sources and runtime classes were freshly compiled and hash-checked. An initial
runner compilation failed because its compile classpath omitted the overlay; it
produced no simulation results. The corrected harness includes the overlay at
compile time and runtime. Both logs are retained. Compact evidence is stored
privately under `2026-10-20-case2-qsts-primitive-transfer-v2`. After verification,
the four identical generated full traces share immutable storage with the control,
avoiding 371,739,528 duplicate bytes. Supplied references are unchanged.

## Next integration boundary

A production scalar primitive-equation capability should describe buffer layout,
ownership, lifetime, matrix-change invalidation, and when ordinary row objects are
synchronized. It should retain an ordinary-equation fallback rather than require
every provider to implement the fast path immediately.

The larger QSTS saving requires primitive device current accumulation and
primitive voltage/convergence state together. Merely exposing arrays leaves
complex injection objects and solved bus objects in the hot loop. Dynamics must
refresh voltage-dependent injections on every nonlinear iteration and present
current voltages to controller measurements, iteration callbacks, and recorders.
Those observation boundaries must be explicit before deferring synchronization.
The dynamic component-wise convergence rule must remain unchanged; the QSTS
magnitude-only mismatch calculation is not a substitute.
