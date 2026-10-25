# Bus-frequency measurement selection

Bus frequency is a derived, filtered measurement, not an unknown required by
every passive network equation. The imported-case solver now defaults to
`BusFrequencyMode.REQUIRED`. Its conservative plan retains generator buses,
configured remote frequency inputs and requested outputs. Texas7k retains
**704 of 6,717 buses**, omitting **6,013 (89.5%)** unnecessary measurements.

## Default and compatibility

The selection currently qualifies the audited synchronous machine/controller
families and REGCA1 compositions used in this case. Remote IEEEST and PSS2A
inputs and REPCA1 measurement buses are included. Both configured PSS2A input
buses stay measured so changing an input selector cannot activate a stale
frequency signal. Initially inactive devices are included conservatively.

Unrecognized models, dynamic loads or branch devices, load-change events,
custom model fields, custom measurement blocks and observers retain all-bus
measurement. Undeclared output callbacks and script outputs do the same.
This preserves the existing behavior while further families are audited.

An ordinary `StateMonitor` declares its output dependencies. Custom output
handlers can implement `requiredBusFrequencyIds()`; its default `null` means
unknown and retains every bus. A complete declaration must cover indirect
frequency reads as well. Changing output dependencies after initialization
requires reinitialization. Structural model or signal-wiring changes also
require reinitialization; ordinary parameter writes retain their model contract.
An omitted block does not make `getFreq()` an on-demand calculation: callers
needing that signal must declare the bus before initialization or select `ALL`.

The legacy configuration remains available before initialization:

```java
solver.setBusFrequencyMode(DefaultDynamicSimulationSolver.BusFrequencyMode.ALL);
```

`getBusFrequencyReport()` reports active and measured bus counts and the
selection/fallback reason after initialization. Reinitialization restores
suppressed blocks before selecting the next run's dependencies. Core now
supports an explicitly absent measurement without reading the voltage angle
or advancing a filter; snapshot round trips preserve this absence. Generic
core factories keep their existing default measurement blocks.

This requires the paired core change, commit `f4d43571b`; rebuild/install core
before building the updated plugin.

## Validation

- All **3,109 core tests** pass, including optional measurement, filter history,
  output declaration and snapshot checks.
- All **36 focused plugin tests** pass. They cover local and remote selection,
  unknown/custom fallbacks, live input-selector coverage, an exact 2,000-step
  coupled converter fault comparison, required frequency histories,
  reinitialization and changed-output rejection.
- The registered supplied-case gate confirms the final production selector
  retains exactly **704** buses. It checks actual initialized objects, including
  inactive controllers and controllers that use direct Java kernels without
  constructing a CML graph.
- Every complete recording in the matched timing batch is byte-identical to
  the retained trajectory. A separate full run with the final selector also
  preserves that hash. All **2,403 samples × 6,732 channels** and event-side
  duplicates remain. This establishes nonregression for the recorded channels;
  independent complete-network reference acceptance remains open.

The first full trial retained every bus because fixed-command converter
controls were not yet classified. Subsequent development checks also caught
an overly conservative distinction between customized fields and inactive or
direct-kernel controllers. The final checks handle those cases explicitly;
the original measurement/history and numerical gates remain unchanged.
Failed attempts and corrected fixture/launch configurations are retained.

## Matched single-CPU timing

The frozen experimental whole-runtime path uses the same quarter-cycle step,
network tolerance, serial device updates and complete recording/export in both
modes. One JVM runs six alternating full warmups and three measurements per
mode. OS affinity is verified as CPU 6 only before and after execution.

| Measurement | Every bus | Selected buses |
| --- | ---: | ---: |
| Measured frequency buses | 6,717 | 704 |
| Median device/measurement integration | 8.697 s | 7.795 s |
| Median whole run | 25.713 s | 25.437 s |
| Whole-run range | 25.496–28.381 s | 25.364–26.185 s |

The device region saves **0.902 seconds (10.4%)**. The whole-run median changes
by **0.275 seconds (1.1%)**, with overlapping ranges and variation in unchanged
network work. This is not evidence of a large whole-run speedup. The 20-second
objective remains open.

Timings apply to the retained experimental runtime, not a qualification of
every current production runtime class. Selection-time custom-field/branch/
generator guards were completed after that batch; the final selector has
separate full-recording and production-gate checks. Cold trials are excluded
from warmed timing claims. No tolerance, integration stage, required channel
or sample rate was relaxed.

The [evidence checkpoint](checkpoints/bus-frequency-selection-2026-10-25.json)
indexes source/bytecode snapshots, bounded logs, passing reports, affinity and
compiler records, full traces and the private manifest. Identical generated
traces share storage only after hash verification; supplied references remain
preserved.
