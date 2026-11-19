# Texas7k accepted-endpoint reuse study

The fingerprint-checked experimental path measures 22.744 and 23.610 seconds
for the complete ten-second run, including recording and export. Its median
is 23.177 seconds in this small matched batch; the controls measure 25.135 and
23.351 seconds. The ranges overlap. The 20-second objective remains open,
and the complete experimental runtime has not been promoted to production.

## Production change

The imported-case solver no longer scans every generator for stabilizer
endpoint maintenance during an ordinary INITIAL stage. That scan previously
performed no endpoint operation unless an enabled event boundary was present.
CORRECTOR processing and enabled event-boundary initialization retain their
existing behavior. This removes redundant traversal without changing equations,
arithmetic, device history or integration stages.

The focused stabilizer and network-lifecycle tests pass: 32 tests, zero
failures, errors or skips. All four complete controls in the first matched
batch retain the prior full trajectory hash. The production core frequency
shortcut remains at `4b87c8724`; this study does not reinstall dependency-cache
jars or replace the frozen checkpoints.

## Experimental accepted-endpoint certificate

A successful CORRECTOR network solution already leaves an accepted voltage
for the next INITIAL stage. The prototype refreshes physical device outputs at
that voltage without advancing differential history, then records the voltage
and complete current-injection values. The next INITIAL stage can reuse that
endpoint only after an explicit owner assertion and these checks:

- No event boundary, changed physical time, convergence norm or membership.
- The same matrix object and monotonic matrix revision, with no dirty matrix.
- Unchanged voltage bits, current-injection bits and fixed-current inputs.
- Compatible tolerance and minimum-iteration certificate.
- Qualified source types, no live observers and no unsupported network memory.
- The same converter-compensation formulation and auxiliary matrix.

Fresh solves still require at least two iterations and retain the maximum of
50 and tolerance of 1e-4 pu. Reuse is reported separately as a zero-iteration
accepted endpoint. Fault application and clearing always solve afresh.
Untracked external mutation is outside the owner contract.

The complete run makes 7,200 network calls and reuses 2,397 INITIAL stages.
Iterations decrease from 31,599 to 26,803. The observed maximum remains six;
the event-safe converter formulation still needs three auxiliary factorizations
and reports zero compensation fallbacks. All numerical solvers are Java.

## Accuracy and recording

Every full run retains all 2,403 sample boundaries, 6,717 bus-voltage channels
and 15 channels from three selected machines: 16,176,996 float64 values.
Both event sides and solver times remain in the trace. The network step stays
at 1/240 s for ten seconds.

Reuse changes the trajectory slightly because it removes an extra INITIAL
polishing solve. It is therefore checked against the retained tighter internal
control rather than described as identical to the previous implementation.
For voltage, normalization uses each reference channel's full ten-second range.

| Check against the tighter internal control | Worst result |
| --- | ---: |
| Prefault voltage NRMSE | 2.197e-12 |
| Fault and postfault voltage NRMSE | 0.00069694 |
| Full-run voltage NRMSE | 0.00069346 |
| Peak voltage difference | 0.00160156 pu |
| Full-run rotor-angle RMSE, three recorded machines | 0.00061611 degrees |
| Prefault peak recorded rotor-angle difference | 0 degrees |

All 6,717 voltage channels satisfy the agreed period limits; the three recorded
rotor angles satisfy the 0.01-degree full-run RMSE limit. Complete independent
network-reference acceptance remains open. Four retained independent converter
checkpoint profiles also pass without changing their tolerances: two time
steps, each with auxiliary compensation enabled and removed.

The expanded invalidation probe passes 182 checks across both convergence
norms, including load and voltage changes, matrix revisions, invalid tolerances,
observers, owner vetoes, event boundaries and ownership-mode changes.

## Rejected closed-case shortcut

A second prototype skips the complete current fingerprint only when the owner
additionally certifies unchanged loads, fixed currents, setpoints and device
states between CORRECTOR and INITIAL. It rejects additional device types,
converter two-step history and controllers with accepted-step callbacks.
It still refreshes physical device outputs at the accepted voltage.

That narrower variant reproduces every recorded value from the checked reuse
path and passes the independent small-case checkpoints. It does not establish
a timing benefit and is rejected as the preferred path.

| Matched batch | Control measurements | Candidate measurements |
| --- | ---: | ---: |
| Fingerprint-checked endpoint reuse | 25.135 / 23.351 s | 22.744 / 23.610 s |
| Additional closed-case shortcut | 25.835 / 25.033 s | 24.533 / 28.177 s |

Each batch contains four complete warmups and four alternating measurements in
one JVM, using the same allowed logical processors 2, 4, 6 and 8. Compilation
counters and phase timings are retained. Small samples and cross-batch
variability do not support a stable speedup claim or a new performance record.

## Retained evidence

The public [checkpoint](checkpoints/texas7k-accepted-endpoint-study-2026-10-25.json)
indexes the private manifest, runnable probes, exact source and compiled-class
archives, logs, focused test reports, all-channel errors, complete traces,
affinity records and per-phase compilation counters. Its manifest hash is
`4ceb52f008148caed5269ad0c45c55fc17c80023f256a978b85d29ed7e1a3832`.

Only byte-identical generated traces share storage. The previous and reused
trajectories retain separate canonical files and hashes. Supplied references
are preserved. The prior baseline and its qualification remain documented in
the [device, frequency and recording study](texas7k-owned-update-study-2026-10-24.md).
