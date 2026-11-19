# Texas7k recording and passive-bus traversal study

The best mode in this matched batch has a **20.481-second median outer
invocation time**, including final summary output. Its internal whole-run
median is 20.469 seconds. The 20-second objective remains open. All eighteen
complete recordings match the retained trajectory byte for byte.

## Matched whole-run results

One JVM runs nine complete warmups, followed by three interleaved measurements
per mode. Every fresh network includes import, power flow, initialization,
controller preparation, ten simulated seconds, complete recording, binary
export and recovery tables. Affinity is verified as CPU 6 only before and
after execution, with one active JVM processor, Serial GC and serial device
integration. Residual compilation remains included in the measured durations.

| Mode | Internal whole-run median | Outer invocation median | Internal measured range |
| --- | ---: | ---: | ---: |
| Required frequency measurements, ordinary recorder | 20.840 s | 20.842 s | 20.317–21.132 s |
| Prepared selected-channel recording | 20.837 s | 20.840 s | 20.318–21.116 s |
| Prepared recording and passive-bus shortcut | 20.469 s | 20.481 s | 19.697–20.819 s |

Preparing the selected channels suppresses unrequested state dictionaries
from 167 renewable devices; it does not suppress their numerical updates.
The whole-run median is essentially unchanged. No default recorder lifecycle
change is retained on the basis of this timing result. The existing explicit
`prepareMonitoring()` API remains available for fixed, standard channel sets.

The passive-bus shortcut avoids 28,862,400 no-op integration-stage visits.
It checks live membership at every stage and applies only to the exact standard
bus with no contributed generators, no bus devices and no frequency measurement.
Generator or device attachment immediately restores ordinary dispatch; custom
buses retain their methods. The shortcut remains an isolated experiment.

In the combined mode, median device/measurement integration is 6.136 seconds
versus 6.195 seconds for the control. Median network time also varies,
11.662 versus 11.858 seconds, despite identical iteration counts and numerical
results. The overlapping ranges do not establish a large causal whole-run gain.

## Numerical and recording contract

Every run retains **2,403 samples × 6,732 channels**: all 6,717 bus-voltage
magnitudes and five signals for three selected machines. Physical and solver
times and event-side duplicates remain. The quarter-cycle step, five-cycle
bus-111180 fault, 1e-4 pu network tolerance and 50-iteration ceiling are unchanged.
All modes use 26,803 network iterations, 2,397 fingerprint-checked accepted
INITIAL endpoint reuses, 704 frequency measurements and 203 typed IEEEST stages.

The standalone prepared-recorder and combined runs also retain the full
control recording exactly. Four existing independent converter profiles pass
unchanged for each setup, including active limiter coverage and fault application
and clearing. A separate traversal probe checks exact callback order and
multiplicity, live device/generator attachment and removal, activation and
custom-bus dispatch. Full-network trace identity proves nonregression for the
recorded channels; complete independent network-reference acceptance remains open.

## Rejected sparse-kernel trial

A separate pure-Java trial replaces fixed RHS row-scale divisions with cached
finite reciprocals and retains division for unsupported scales. On actual
initial, faulted and cleared factors, maximum scaled component difference is
7.772e-16. Kernel medians range from a small regression to about a 2.8% gain.
This does not establish a whole-run improvement. The trial is excluded from
all complete-system timings above.

The [evidence checkpoint](checkpoints/texas7k-prepared-recording-study-2026-10-25.json)
indexes source and bytecode snapshots, exact runtime dependencies, complete
traces, bounded logs, CPU/compilation and affinity records, component checks and
failed development attempts. Identical generated traces share storage only after
hash verification. Supplied references are preserved. No experimental runtime
path is promoted by this checkpoint.
