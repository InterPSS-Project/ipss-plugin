# Case 1 voltage components and network tolerance

The remaining first-event rotor discrepancy is much more sensitive to the
terminal-voltage phase history than to the magnitude history in a conditional
machine replay. This result narrows the boundary investigation; it is not a
correction to the coupled simulation or proof of a particular network defect.

## Full-event tolerance control

Both coupled runs finish the original ten-second bus-7249 event with 20,005
rows. Only the dynamic network tolerance changes. Original parameters,
computed operating point, initialization tolerance, integration step, cold
history and event boundaries are retained. The cold snapshots are equal.

| Full-event measurement | Network tolerance 1e-6 | Network tolerance 1e-8 |
| --- | ---: | ---: |
| Worst prefault rotor maximum, degrees | 0.00575997 | 0.00574799 |
| Worst ten-second rotor RMSE, degrees | 2.18794772 | 2.19214554 |
| Worst fault/postfault bus-voltage NRMAE | 0.000400016 | 0.000402632 |
| Worst fault/postfault terminal-voltage NRMAE | 0.000878609 | 0.000883793 |
| Logged network calls | 20,004 | 20,004 |
| Logged network iterations | 253,621 | 440,128 |
| Mean iterations per call | 12.6785 | 22.0020 |

Tightening the tolerance increases iterations by 73.5% and slightly worsens
the largest rotor RMSE. All five bus-voltage and terminal-voltage channels
pass both period limits in each run. All five rotor channels pass prefault
and fail the full-window limit. The largest trajectory change between the
two runs is only 0.0140622 degrees in rotor angle and 0.0000147754 pu in
observed bus voltage. This control does not support tightening below 1e-6
as the remedy for this event's approximately seven-degree peak rotor error.

The observed runtimes are 1,288.2 and 1,437.4 seconds. They include concurrent
work, 0.5 ms integration and diagnostic recording. They do not establish the
quarter-cycle production runtime requirement.

## Full-event voltage-component controls

All five observed machines and their attached controllers are independently
initialized from the same computed point and integrated through ten seconds.
Original parameters, cold history and both sides of fault application and
clearing are retained. Each experiment compares 20,005 time rows. The
additional observations preserve the original 50 coupled channels exactly.

| Prescribed terminal boundary | Worst rotor RMSE over 0–10 seconds, degrees | Worst unit |
| --- | ---: | --- |
| Recorded local magnitude and phase | 2.18794772 | 7189/1 |
| Supplied magnitude, recorded local phase | 2.10927902 | 7190/1 |
| Recorded local magnitude, supplied phase | 0.15174745 | 7192/1 |
| Supplied magnitude and phase | 0.00790570 | 7146/1 |

The local-voltage reconstruction differs from the original coupled rotor
angles by at most 2.57219e-7 degrees through ten seconds. P and Q reconstruct
within 4.65004e-7 MW and 8.15966e-7 Mvar; electrical coordinates agree within
3.27707e-9. This bounds the conditional replay's numerical discrepancy well
below the original coupled/reference mismatch. The observation also leaves
all original 50 physical channels and all three cold/history snapshots exactly
unchanged through the full event.

The two mixed-boundary variants have equal cold snapshots to the local replay.
The comparison reuses the completed local and supplied-voltage replays rather
than integrating those same controls again. An independent coverage check
verifies all 100,025 machine rows in each of the four modes. The earlier
five-second controls are retained as intermediate evidence.

Changing only magnitude leaves most of the rotor discrepancy. Changing only
phase reduces it substantially. Mixed magnitude/phase histories are external
diagnostic inputs and are not self-consistent network solutions. In particular,
the supplied phase is reconstructed using supplied rotor and electrical
states. Its relationship to rotor angle is therefore not an independent
measurement of the upstream cause. No reconstructed phase is applied as a
correction in an autonomous simulation.

These conditional experiments do not satisfy the autonomous coupled-network
gate. Complete per-channel errors against both the supplied reference and the
recorded local trajectory are retained, along with the comparison figure.

The frozen runtime's frequency-measurement bytecode already contains phase
unwrapping and event-angle rebasing. No change to that logic is justified by
this inspection alone. Direct supplied bus-frequency measurements remain
unavailable, so its reference trajectory cannot be declared verified.

## Disk preservation

Eleven redundant CSV copies already covered by sealed evidence were
consolidated using hard links, releasing 394,124,891 logical duplicate bytes.
Every original path and SHA-256 content digest is preserved. Live control
directories and their declared input/runtime dependencies were excluded.
The content hashes were rechecked after all eleven replacements.

## Evidence and remaining work

Private evidence set `2026-10-19-case1-full-network-boundary-controls` retains
both completed coupled runs, all original-event comparisons, the full local
replay control, five-second and ten-second voltage-component experiments,
coverage checks, figures, exact producers and runtime hashes. It also retains
the duplicate-storage verification record. Earlier prefixes remain unchanged
in their separate sealed evidence sets.

The next investigation concerns the coupled phase/frequency response and
active-power feedback. The conditional boundary tests do not uniquely identify
an upstream device, justify a fitted phase correction, or establish a missing
passive-network term.

The six-case, 36-event acceptance goal, direct bus-frequency comparison and
quarter-cycle production runtime remain open. No production model equation,
original parameter, cold state or acceptance limit is changed in these tests.
