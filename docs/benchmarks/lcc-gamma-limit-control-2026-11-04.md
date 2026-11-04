# Standard-converter minimum-angle control

The inverter voltage controller previously clipped its firing angle while retaining
an infeasible scheduled DC voltage. Initialization then passed that voltage to the
rectifier, which incorrectly selected its minimum-angle/current-margin regime.
The isolated controls reproduce this independently of the network parser.

The standard-converter path now recomputes current and voltage when minimum gamma
binds, preserving the selected power or current order until the rectifier's own
limit binds. Maximum gamma is a tap-adjustment objective, not a firm clipping
limit. Enabled taps continue to use the existing discrete grid.

Automatic minimum-gamma ownership is separate from caller-selected firing-angle
control. A feasible voltage target or restored tap authority releases only the
automatic limit. Explicit mode selection clears ownership, including a same-mode
setter call. Cold initialization restores voltage intent before reevaluating the
limit. State copies and coupled-control rollback preserve the distinction.
Invalid angle/tap inputs are rejected before the new path mutates live state.

The new core interface methods have compatibility defaults: inactive ownership,
and an explicit unsupported exception if an older custom implementation is asked
to activate it. The plugin evidence was tested against the separately versioned
core artifact `1.4.8-lcc-gamma-final`, built from core commit `f5d6ec07b`.
Shared release artifacts were not replaced.

## Evidence and limits

- Eight independent cold captures cover both power-metering ends with converter
  taps enabled and locked. Each pair has identical retained numerical results.
- All four reference gates fail on baseline core `1.4.8` and pass on the candidate.
  The gates check both converter DC currents, DC voltages and taps, plus inverter
  angle. They do not claim complete AC power or rectifier-angle parity.
- All 51 focused core tests pass, including activation, release, current-order
  independence, explicit control, cold initialization, copy/restore, transaction
  rollback, invalid input and existing coupled-control regressions.
- Capacitor-commutated converters are excluded from this new path. Their source
  equations and external-transformer interpretation differ. The original full
  sample assertions remain unchanged and still fail identically on the candidate.
  The installed reference environment rejects those full inputs at table-capacity
  limits, so those attempted solves cannot justify replacing their expected values.
- Nonzero converter resistance still produces a smaller AC-power/rectifier-angle
  discrepancy in the isolated controls. This remains separate model work.

Private evidence directory: `testData/private/lcc-gamma-limit-20261104`.

| Artifact | SHA-256 |
| --- | --- |
| `reference.zip` | `5c6177d0a66ebaaf7035851f9c5ca49c117aad1ff277a27d38606ba007b291b9` |
| `validation.zip` | `929ea4d95178b200397cd61a81a24cd58c7e3bc0015a710e15c63496d6c1f9b3` |

The archives retain prepared inputs, original capture programs, all numerical
outputs, rejected full-input attempts, source provenance, artifact hashes and
before/after logs. `NodebreakerConverterAuditTest` reads the immutable reference
archive. `tools/lcc_static_control_reference_probe.py` can capture a prepared
converter input without changing its data; run it through the bounded launcher.
The broader sample gate remains open.
