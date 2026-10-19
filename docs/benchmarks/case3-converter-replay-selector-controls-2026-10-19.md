# Case 3 converter replay and recovery-selector controls

Case 3's early coupled discrepancy remains unresolved. A complete converter
replay checks local coupling fidelity, and three uniform recovery-selector
controls all worsen the observed errors. No control is adopted as an
initialization rule. These checks extend the
[converter onset investigation](case3-converter-onset-localization-2026-10-19.md).

## Replay of all active converter bundles

All 290 original active converter bundles were initialized independently from
their ordinary local cold terminal P/Q/V and replayed using the recorded local
terminal voltage. No reference states enter initialization. The replay retains
the two negative startup samples and the 0.5 ms step through 0.1 seconds:
203 records per bundle, 58,870 device records and 698,516 stored derivatives.
The original case has no active remote, monitored-branch or frequency-response
inputs in these replayed controllers.

| Compared quantity | Maximum absolute difference from coupled local run |
|---|---:|
| Generator active/reactive power | 3.41e-13 MW or Mvar |
| Integration state coordinates | 4.44e-16 |
| Stored state derivatives | 6.67e-14 |
| Published commands and control memory | 9.44e-16 |

The maximum initial-state difference is 4.44e-16. The complete replay takes
9.59 seconds, but prescribes network voltage and therefore is not a coupled
simulation performance measurement. It verifies that the local standalone and
coupled paths evolve consistently, including colocated generators. It does not
establish agreement with unrecorded reference converter states or prove the
reference network/controller scheduling contract.

## Recovery-direction discriminators

The cohort is defined before comparison: all 117 active converters with
original RAW Q, Qmin and Qmax equal to zero. Their ordinary computed initial
Q has 112 positive and five negative signs. The established rate equation
selects an upper bound for positive initial Q, a lower bound for negative Q,
and neither for exactly zero. Tiny computed Q is not rounded to zero by the
production model.

Four autonomous runs use the same original inputs, runtime, network tolerance,
0.5 ms step and startup history. The unchanged control reproduces the previous
physical output byte for byte across all 6,578 physical channels. The three
other controls change only the recovery selector for the entire predeclared
cohort. Cold continuous states and the first physical output row remain
bit-identical. No per-device selection is fitted to the reference.

| Recovery-selector control | Selectors changed | Worst voltage error, pu | Worst rotor error, degrees | Worst speed error, pu |
|---|---:|---:|---:|---:|
| Ordinary computed directions | 0 | 0.00656739 | 0.0436978 | 0.0000777681 |
| Uniform positive direction | 5 | 0.00935241 | 0.0498913 | 0.0000888199 |
| Uniform negative direction | 112 | 0.01689263 | 0.1149434 | 0.000205514 |
| Uniform unselected direction | 117 | 0.00893327 | 0.0496377 | 0.0000868264 |

These are maximum absolute errors over 0 through 0.1 seconds against all 240
supplied physical channels available in the combined Case 3 prefault record.
Each required quantity has 30 observed channels. The worst voltage is at bus
3054; the worst rotor and speed errors are at machine 1073/1. The voltage peaks
occur at 0.087, 0.0995, 0.1 and 0.0835 seconds, respectively; the rotor and speed
peaks occur at 0.1 seconds in every control. These are short-prefix errors,
not full-window NRMAE or rotor-RMSE acceptance results. Generator speed remains
a frequency proxy.

Uniformly replacing the computed directions does not resolve the mismatch.
The controls demonstrate consequential sensitivity but cannot identify the
unrecorded reference selectors. In particular, the unselected-direction run
does not justify forcing originally zero-Q devices to have exactly zero
computed Q at initialization.

## Evidence and remaining work

Private evidence set `2026-10-19-case3-converter-replay-selector-controls`
retains the exact producers, runtime hashes, original inputs, all cold
snapshots, complete prefix outputs, every observed-channel error, replay
comparisons and plots. The previous immutable archive supplies shared objects.
No production equation, parameter, initial state or acceptance limit changes.

Further localization must distinguish the early network feedback and
initialization arithmetic using independent constraints, rather than adopting
a uniform selector override. Full acceptance of all six cases and 36 original
events, direct bus-frequency comparison and the runtime target remain open.
