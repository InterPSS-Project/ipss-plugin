# Composite electronic-load full recovery

The legacy composite maps its electronic-load recovery coefficient to exactly
one. Its voltage fraction is consequently memoryless:

`minimum + 1 * (available - minimum) = available`.

The shared algebraic component now evaluates that case directly. It avoids
updating the running minimum and performing a subtract/add cancellation on
every network-current query. `available` is zero below the lower voltage
threshold, one above the upper threshold and linear between them. The
electronic P/Q observations and current injection remain fresh for each trial
voltage. No differential states, additional integration stages or adapters are
introduced; modified Euler and staggered integration use the same component.

The eligibility condition is exact and uses immutable model data. A recovery
coefficient below one retains the existing distinct policy. This change does
not qualify that policy's trial-history commit/discard behavior. The cached
electronic fraction remains a derived observation, not a new accepted-history
state. No whole-simulation speedup is claimed for this arithmetic reduction.

## Independent checks

Isolated whole-electronic SMIB controls omit transformer and feeder. They
exercise partial voltage reduction, complete dropout and repeated recovery.
Each reference is cold-repeated with all recorded fields identical. Both
integration methods preserve complete recording, event-side duplicates and
the explicitly checked accumulated reference clock.

Across the complete half-second controls at 0.5 ms steps:

| Metric | Worst result |
| --- | ---: |
| Bus-voltage range-normalized RMSE | 0.00004348 |
| Electronic P/Q range-normalized RMSE | 0.00003223 |
| Rotor-angle RMSE, degrees | 0.00018365 |

The registered SMIB tests also check generator active/reactive power and speed
against their full reference ranges. The existing 0.001 NRMSE and 0.0002-degree
angle gates are unchanged. Unit checks cover arbitrary voltage query order,
full restoration, partial-recovery compatibility, fresh current and load
scaling. All six SMIB checks and three new unit checks pass.

Private reference archive SHA-256:
`e9127105c27bd2f060155f3df301009b96e3c106fcb8d876c6d74b653edcc83d`.

## Represented-distribution extension

Additional whole-electronic controls retain the transformer and feeder at
their prepared settings. Both methods exercise a partial-voltage ramp and
deep dropout, with repeated recovery. The tests verify the expanded topology
and compare both internal load-bus and low-voltage-bus magnitudes against the
independent measurements, as well as the existing terminal, machine and
electronic P/Q channels. All raw fields repeat exactly across cold captures.

| Metric over represented-distribution controls | Worst result |
| --- | ---: |
| External bus-voltage NRMSE | 0.00004356 |
| Internal bus-voltage NRMSE | 0.000008518 |
| Electronic P/Q NRMSE | 0.00004186 |
| Rotor-angle RMSE, degrees | 0.00010132 |

The same accuracy gates remain in force. The whole-electronic coverage
contains ten both-method controls; unavailable distribution evidence skips
only its additional controls, preserving the original tests. With both
archives present all ten execute. No production equations or integration
settings changed for this extension.

Private distribution reference archive SHA-256:
`e8a6b99b00d4fd63465475fb134a00a83726ae940b1404974ccfb0775181c372`.

These controls qualify the whole full-recovery electronic component with
omitted or represented distribution in this scope. Additional
[static/electronic mixture controls](cmldblu1-static-electronic-interface.md)
cover omitted distribution. Motor mixtures, represented-distribution
static/electronic mixtures, tap-control actions, long-duration and
production-step controls remain open; the complete composite remains `LOADABLE`.
