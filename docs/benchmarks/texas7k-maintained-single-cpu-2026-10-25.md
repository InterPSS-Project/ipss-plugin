# Texas7k maintained single-CPU milestone

The maintained Java runtime completes the full ten-second study in a warmed
**19.935-second median**, including recording and export. Five measurements
range from **19.485 to 20.716 seconds**, after four complete warmups in the
same JVM. This meets the 20-second median objective; it does not establish a
20-second upper bound for every run.

## Measured scope

Each invocation imports a fresh case, solves power flow, initializes all devices,
prepares controllers, simulates ten seconds, records the complete selected
channel set, and writes binary results, recovery tables and the final run
summary. The outer timer surrounds that entire invocation. Integrity hashing
and benchmark bookkeeping occur afterward. Residual JVM compilation within an
invocation remains included.

The JVM inherits affinity to logical CPU 6 before it starts. The launcher checks
mask 64 before and after execution. One active JVM processor, Serial GC, serial
device updates and disabled parallel row execution enforce the single-CPU
condition. The benchmark uses maintained core **1.4.6** and plugin **1.4.8**
artifacts, without prototype classpath overlays.

| Measured invocation | Complete outer time |
| --- | ---: |
| 1 | 20.716 s |
| 2 | 19.605 s |
| 3 | 20.091 s |
| 4 | 19.485 s |
| 5 | 19.935 s |

An earlier maintained batch, before the invalid-tolerance guard correction,
measured a 19.814-second median. Both batches are retained with their exact
executables; the table above reports the final validated build.

See the [checkpoint](checkpoints/texas7k-maintained-single-cpu-2026-10-25.json)
for unrounded measurements, exact runtime hashes and validation evidence.

## Maintained implementation

The explicit benchmark profile combines indexed numerical current buffers,
matched removal of auxiliary converter compensation from both matrix and RHS,
factor reuse across unchanged network operators, and checked reuse of accepted
INITIAL endpoints. Fresh network solves retain the configured convergence test.
Matrix revisions, changed injections, voltage, load, observers and owner vetoes
invalidate endpoint reuse. Ordinary dispatch and bounded network-mixing
safeguards remain available.

Typed IEEEST evaluation retains the existing numerical blocks and integration
order. Fixed I/Z rows use guarded numerical coefficients, and unobserved
rectangular voltage-angle caches can use the JDK angle kernel. The passive-bus
shortcut checks live device membership and preserves custom-bus dispatch.
Prepared recording suppresses unrequested state dictionaries while continuing
all numerical device updates. All of these paths are selected explicitly by
the supplied profile; general solver clients retain their fallback behavior.

Required frequency measurement is already the imported-case default. This run
retains **704 of 6,717 buses** because their models or requested outputs need
frequency. Unclassified consumers retain conservative measurement coverage.
Recording all bus voltages does not itself require calculating every bus's
frequency.

The first maintained trial exposed five zero-lag converters whose stored
commands were unnecessarily excluded from buffered current publication.
Qualification now accepts the two final, read-only command implementations;
replaceable algebraic callbacks retain ordinary dispatch. A regression test
checks repeated voltage-dependent output, unchanged states and derivatives,
and reconstruction of the original Norton current. The failed trial is
retained in the private evidence.

## Numerical and recording checks

All nine complete warmup and measured traces are byte-identical to the
qualified trajectory. Each retains **2,403 samples × 6,732 channels**:
all 6,717 bus-voltage magnitudes and angle, speed, electrical power, mechanical
power and field voltage for three selected machines. Physical/solver times
and both sides of event timestamps remain. The initialized inventory contains
630 generators: 463 synchronous machines and 167 converter bundles, with
463 exciters, 463 governors and 203 stabilizers. No active controller is removed.

The quarter-cycle time step, five-cycle fault at bus 111180 from 0.100 to
0.183333 seconds, 1e-4 pu dynamic network tolerance and 50-iteration ceiling
are unchanged. Every run uses 26,803 network iterations and 2,397 checked
endpoint reuses. Static loads retain current-P/impedance-Q conversion;
supplied netting and switched-shunt preparation remain intact.

The retained recovery table has 123 buses outside 2% of their initial voltage
at ten seconds; bus 150510 is the largest deviation, approximately 5.305%.
The performance milestone preserves this response and does not establish
universal voltage recovery after the fault.

All 6,717 voltage channels pass the configured prefault/fault-period limits
against each of two tighter internal controls. The worst fault-period
range-normalized voltage RMSE is below 0.000699; maximum absolute voltage
difference is below 0.001604 pu. The three recorded rotor-angle channels have
worst full-run RMSE below 0.000617 degrees and zero prefault difference.
Normalization uses each control channel's full ten-second range and preserves
event duplicates. These are internal convergence and nonregression checks.
Independent acceptance for the complete 7K network remains open.

Eight additional checks run against maintained classes without runtime
overlays: four independent converter profiles, 661,120 current-component
comparisons, 45,120 state/history comparisons with observer fallback, 180
endpoint invalidation checks, 600,051 angle-cache checks, 72,204 typed-controller
comparisons, live passive-bus callbacks and matrix-revision/factor invalidation.
The endpoint check found and prompted a finite-positive-tolerance guard fix;
the corrected source is covered by fresh validation and timing. The maintained
probe omits only a toggle for a removed prototype mode, retaining every actual
ownership, matrix, voltage, load, tolerance and event check.

Full validation passes with zero failures and zero errors:

| Suite | Tests | Existing skips |
| --- | ---: | ---: |
| Core | 3,109 | 0 |
| Plugin core | 452 | 4 |
| Three-phase plugin | 468 | 28 |
| Plugin integration | 3,327 | 54 |

The complete plugin reactor also builds the sample module successfully.
The evidence indexes reports written during each validation run; older XML
reports are retained separately from the executed-test counts.

## Reproduce

Use Java 21 and build core from its own repository directory, followed by the
plugin reactor from this repository. Both builds should run through
`tools/run_bounded.py` with a unique log path and `-DargLine=-Xmx2g`.
Then run:

```powershell
python tools/benchmarks/run_texas7k.py `
  --input 'C:/path/to/Texas7k_20210804_Plus2023' `
  --output 'target/texas7k-new-run' `
  --cpu 6 --warmups 4 --measurements 5 `
  --java 'C:/path/to/jdk-21/bin/java.exe'
```

The launcher automatically bounds console capture and checks the disk reserve.
It refuses existing output directories, verifies affinity and numerical coverage,
and records source references, input/dependency hashes, JVM options and loaded
implementation hashes. Its default classpath comes from the passing maintained
endpoint-recorder test report. The explicit settings are in
[`texas7k_single_cpu.json`](../../tools/benchmarks/texas7k_single_cpu.json).
The full trace occupies 129,464,040 bytes. Identical generated traces share
storage only after complete hash verification; supplied references are preserved.

The private checkpoint freezes the exact measured source and executable
classpath before subsequent builds, including first-party jars, directory
resources and indexed third-party dependencies. Earlier timing studies remain
historical evidence; this maintained-source batch supersedes them for the
20-second median objective.
