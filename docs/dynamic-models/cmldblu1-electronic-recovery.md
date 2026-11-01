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

These controls qualify the isolated full-recovery electronic component in
this scope. Mixed components, represented distribution, long-duration and
production-step electronic controls remain open; the complete composite
catalog status remains `LOADABLE`.
