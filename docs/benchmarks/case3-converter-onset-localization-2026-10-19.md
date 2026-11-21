# Case 3 converter and network onset localization

The early Case 3 mismatch remains unresolved. New read-only observations narrow
the investigation without changing a model equation, initialization, integration
step or acceptance limit. They extend the preceding
[step-refinement controls](case3-startup-step-refinement-2026-10-19.md).

## Complete converter observations

The original 0.5 ms simulation was repeated through 0.1 seconds with state,
stored-rate, command and limiter observations for all 290 active converter
bundles. It retains the two negative startup samples and records 203 endpoints
per device, for 58,870 observation records. The physical output file is
byte-identical to the preceding coarse-step control across all 6,578 physical
channels. Diagnostic elapsed time is 69.8 seconds; this is not a production
runtime measurement.

| Observed mechanism through 0.1 seconds | Result |
|---|---|
| Accumulated network high-voltage correction | Zero for every retained solve |
| Decelerated-voltage low-voltage current scaling | Inactive |
| Terminal dip and post-dip hold timers | Inactive |
| Electrical Q-target, Q-PI and voltage-PI clipping | Inactive |
| Active and reactive current-command clipping | Inactive |
| Active-power-order input clipping | Inactive |
| Plant Q-PI clipping | Bus 2013/1, first sampled at 0.0625 s |
| Converter reactive-current recovery-rate clipping | Active on 221 devices at some point in the prefix |

Current-output limits and current-rate limits are distinct. The latter are
already active at 0.0005 seconds. Across the prefix, 15,771 device-step
observations have a clipped reactive-current rate. At 0.02 seconds, 62 of the
290 stored reactive rates are clipped; at 0.1 seconds, 198 are clipped.

Every positive-step converter derivative is independently reconstructed from
the preceding published command, the retained derivative-evaluation state,
entered time constant and initialized recovery direction. All 58,580 active
rates and all 58,580 reactive rates reproduce the stored values exactly. This
checks the implemented rate equation and recorded integration boundary. It
does not compare these internal rates with unrecorded reference states or
prove that the complete coupling sequence is correct.

The recovery direction is consequential when initialized Q is small. Of the
117 active converters whose original RAW Q, Qmin and Qmax are all zero, 112
initialize with positive computed Q and five with negative computed Q. This
inventory does not justify replacing their individual selectors or forcing
their continuous initial states to zero.

## Independent radial-network constraints

Three original wind bundles at buses 3055, 3056 and 3057 were replayed from
ordinary local cold states using their supplied terminal-voltage histories.
All active parallel transformers connect them to bus 3053. Their terminal
and connection-bus voltage magnitudes are independently recorded in the
supplied Case 3 observations.

Replayed P/Q and each original radial branch equation imply a connection-bus
voltage magnitude within 5.63e-6 pu of the supplied bus 3053 voltage. Replaying
the local voltage instead reproduces the coupled converter powers within
8.53e-14 MW and 2.14e-14 Mvar. These conditional consistency checks are much
smaller than the coupled voltage discrepancy, but do not constitute direct
reference converter P/Q acceptance: those power channels were not recorded.

A separate calculation reconstructs connection-bus voltage phasors from
24 observed radial synchronous-machine buses. Every online unit at a selected
terminal is observed, and the original branches, shunts and converted loads
are retained. Local reconstruction closes against the recorded local network
within 6.60e-7 pu.

Five independently reconstructed reference connections to bus 1071 agree
within 4.44e-7 pu over the complete prefix. Six other shared connection buses
also agree within 4.0e-7 pu. No phase shift or common-voltage fit is applied.
This supplies an additional network consistency check on the earlier terminal
phase reconstruction, although these remain reconstructed rather than directly
recorded bus-angle channels.

| Time | Bus 1071 voltage error, pu | Bus 1071 phase error, degrees |
|---|---:|---:|
| 0 s | -0.00000126 | 0.0000367 |
| 0.02 s | -0.00045995 | -0.0046718 |
| 0.05 s | -0.00387702 | 0.0534300 |
| 0.10 s | -0.00611048 | 0.3502719 |

Errors are local minus reconstructed reference. The reference in this table
uses the bus 1072 connection alone; the other four independently check it.

## Localization limits and remaining work

Reassembling the original passive network, solved shunts, machine Norton
terms and converted loads reproduces the retained dynamic matrix within
9.10e-13. Current-injection sensitivity against 76 available real-valued
voltage observations points toward the western bus-1000 region. Several
electrically similar candidate buses explain nearly the same spatial pattern,
so this is not a unique device identification. Diagnostic inferred P/Q values
are never applied to any trajectory.

An exploratory reactive-control linearization must include the active
recovery-rate branches. The retained 0.02-second reduced calculation does so,
but holds synchronous Norton sources, power orders and wind mechanical
controls fixed. Its small positive poles do not establish full-system
instability or explain the reference mismatch. An earlier unrestricted
exploration is excluded from those claims.

Private evidence set `2026-10-19-case3-converter-onset-localization` preserves
the completed run, exact runtime dependencies, original inputs, derivative
checks, radial replays, connection-voltage reconstructions, sensitivity
calculations and plots. No production model change follows from this slice.
The next investigation remains the early coupled network/controller boundary
in the western region. Full prefault and disturbance acceptance for all six
cases and 36 original events, direct bus-frequency comparison and the runtime
target remain open.
