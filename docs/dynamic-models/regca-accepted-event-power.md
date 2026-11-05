# Accepted converter power after network events

REGCA1 now implements the existing `NetworkEventInput` contract. After a
scheduled network event converges, the default imported-network solver publishes
P/Q from the accepted terminal voltage and the converter's pure current-source
function, before the next controller step. Ordinary steps retain their existing
lifecycle. The callback does not advance regulator states, controller states,
derivative history, clocks or optional network-iteration voltage memory.

Previously the default integration path passed cached event-left P/Q into the
first post-event REECC/REPCA update. The two-step path already refreshed its
power input before stepping. The new callback uses the same source equations;
there is no certified retained current cache to substitute. With iteration
memory enabled, it reads the finalized stored correction and decelerated voltage
without advancing either. Nonfinite computed power is rejected before either
P or Q is overwritten. Inactive converters, generators, buses and uninitialized
models are ignored. Notification during an unfinished iteration-aware solve is
rejected.

The real imported converter is registered in the bus dynamic-device list. Tests
exercise actual solver callback dispatch at application and clearing for both
methods. Failed event solves dispatch no callback and preserve the converter's
published power, differential states and accepted iteration memory. Repeated
notifications, bypassed current regulators, invalid inputs, inactive devices
and pure observations have dedicated checks.

## Independent attached-plant discriminator

The complete flat and fault reference profiles contain 1,205 rows each, including
startup and both event sides. Comparing identical configured network-memory
runs before and after the callback gives:

| Maximum absolute error | Before | After |
| --- | ---: | ---: |
| Default reactive-current command | 0.00845453 | 0.00101886 |
| Default recorded active power | 0.0692548 | 0.00143801 |
| Default recorded reactive power | 0.0677173 | 0.000998321 |
| Two-step recorded active power | 0.0694302 | 0.000717216 |
| Two-step recorded reactive power | 0.0683331 | 0.000119588 |

Both complete flat traces remain identical. The two-step fault trace differs
only in P/Q at the two event-right rows; every controller state, command, voltage
and physical-current output remains identical. These controls establish the
stale-input defect without changing equations or choosing a different integrator.

The optional converter network-memory setting is explicit in this diagnostic;
it is not enabled globally by this change. Without that setting, instantaneous
low-voltage gain produces a separate approximately 0.015 pu event power mismatch.
Cached reported power and physical current-derived power are both retained in
the diagnostic traces. Full attached-plant trajectory acceptance remains separate
from this lifecycle correction, especially the remaining default-method event
transient errors. No old comparison ceiling or event exclusion was changed.

Validation: 76 focused tests passed with no failures, errors or skips, including
the three existing coupled REECC reference checks, attached-plant replay,
REECA boundary replay, REPCA controls, shared two-step integration and endpoint
recording. Private before/after traces, diagnostic source, logs, hashes and exact
parity results are retained in
`verification-artifacts/regca-accepted-event-power-20261104`.
