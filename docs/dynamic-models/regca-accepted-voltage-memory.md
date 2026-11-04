# Accepted voltage memory for converter network equations

REGCA1 now offers an explicit `ACCEPTED_BOUNDARY` voltage-memory policy shared
by the default modified-Euler and staggered methods. Instantaneous behavior
remains the default. Enabling optional network memory without selecting this
policy retains the existing `PER_NETWORK_SOLVE` behavior.

Configure before initialization:

```java
converter.setNetworkIterationHighVoltageEnabled(true);
converter.setNetworkVoltageMemoryPolicy(
    Regca1Model.NetworkVoltageMemoryPolicy.ACCEPTED_BOUNDARY);
```

The policy separates committed saved voltage and accepted terminal voltage from
trial network output. `prepareIntegrationStep` records only the physical clock;
it does not sample voltage. Initialization seeds the committed values. A
positive integration interval or external event prepares one trial recurrence
from the committed values. Predictor, corrector, repeated INITIAL and other
network solves reuse that trial coordinate. Their current evaluations still
refresh voltage-dependent injections and algebraic corrections.

A converged event commits through `NetworkEventInput`; a successful positive
step commits through `afterStep`. Repeated notifications at the same boundary
do not advance history again. Failed network trials restore working outputs and
leave committed values unchanged. The same prepared interval can retry its
network solve. This does not add rollback of bus voltages or differential states
to the simulation engine. Invalid integration requests are rejected before
marking a step pending. Configuration changes are rejected during an unfinished
step, network solve, or converged event awaiting acceptance.

Inactive event notifications do not commit history. Reinitialization clears
pending clocks, trial markers and accepted-event identities and seeds the new
operating point. Disable/re-enable cleanup leaves differential states and
stored derivatives untouched. Reactivation after an unfinished integration
interval requires lifecycle recovery/reinitialization; arbitrary inactive-bus
history reconstruction is not claimed.

`getAcceptedVoltageMemoryState()` exposes the committed saved voltage, accepted
terminal voltage, last step/event clocks and pending-step status. The existing
network-memory diagnostic continues to expose the working output. No controller
equation, current limit, frame transformation, input schema or numerical
integration method changes.

## Complete coupled comparisons

The flat, fault and active upstream reactive-limit controls each retain all
1,205 independent rows, including negative startup samples and duplicate event
timestamps. Both methods are tested without endpoint reuse. A separate reuse
control checks the same accepted-memory recurrence. Power-flow initialization
and absolute plant reference offsets come from the local solved model, not
from reference initial values. Every state, command, physical P/Q, bus-voltage
magnitude and bus angle is checked; no event samples are excluded.

| Worst absolute error across the three profiles | Default method | Staggered method |
| --- | ---: | ---: |
| Terminal voltage (pu) | 0.000267690 | 0.000122261 |
| Physical active power (pu) | 0.000353866 | 0.0000663626 |
| Physical reactive power (pu) | 0.00100712 | 0.000120530 |
| Accepted reactive-current command (pu) | 0.00101878 | 0.000110724 |
| Saved voltage versus reference (pu) | 0.000257392 | 0.000120807 |

The local saved-voltage recurrence agrees with the local accepted terminal
samples within 2e-14 for both methods, including events. Residual differences
against reference saved voltage follow the coupled voltage-trajectory error,
not extra numerical commits. The earlier per-solve policy produced saved-voltage
errors of 0.07923 pu in the default method and 0.007989 pu in staggered integration.
The new policy requires neither a manual event-left callback nor endpoint reuse.

New coupled gates are explicit per-channel physical-unit bounds, separate from
the existing recorded-input replay and original coupled-fault gates, which are
unchanged. The default physical P/Q bounds are 5e-4 / 1.2e-3 pu; staggered bounds
are 1e-4 / 2e-4 pu. Voltage bounds at the terminal are 4e-4 / 1.5e-4 pu. The tests
also enforce 50 maximum network iterations and the existing 1e-4 voltage
correction tolerance. All startup and event-side samples remain included.

## Compatibility, cost and remaining scope

Two instantaneous-mode controls and four matching legacy optional-memory
controls produce byte-identical full traces before and after the implementation.
The new policy is opt-in. Optional memory continues to disable incompatible
network acceleration and uncompensated-current paths; no eligibility shortcut
is introduced. Numerical call counts and zero acceleration mixes are retained
in the validation log. These small controls do not establish a whole-system
runtime improvement or justify enabling the policy by default.

The extended-profile regression run passed 85 tests in 12 classes with no
failures, errors or skips. After adding the cleanup guard and ensuring full-network
plant offsets use local initialization, final follow-ups passed six and eight
tests respectively, also without failures or skips. Those counts overlap; they
are not summed as unique tests. The new public tests are
`RegcaAcceptedVoltageMemoryTest` (five contract tests) and
`ReeccPlantNetworkConformanceTest` (two coupled tests covering seven runs).

Private source snapshots, exact default-off parity, full trajectories and
executed test logs are sealed under
`verification-artifacts/regca-accepted-voltage-memory-20261104`. Native reference
captures remain immutable in their earlier manifests. High-voltage active
correction profiles, broader model combinations and system/runtime qualification
remain required before any default-policy change.
