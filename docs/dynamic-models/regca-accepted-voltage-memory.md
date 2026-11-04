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

## Active high-voltage correction follow-up

The opt-in policy also passes analytic active-correction lifecycle checks for both
integration schemes. A network trial accumulates the documented power-coordinate
correction, divides it by decelerated voltage when publishing reactive current,
and retains it only after convergence. Failed trials restore the previous output;
reads do not advance history; a subsequent inactive solve starts with zero working
correction. These checks establish equation and lifecycle behavior, not independent
trajectory accuracy.

Two distinct accepted event solves at the same timestamp were tested at 0.7 and
0.6 pu. Saved voltage advances only once, while terminal input becomes the final
0.6 pu value. The next physical step uses that final input. Repeated acceptance
notifications are idempotent and completed event trials do not block configuration
changes. The existing production implementation passes these checks unchanged.

A retained, repeatable fault-recovery capture exercises the high-voltage correction
in 105 samples. The comparison retains all 2,005 rows, including two startup rows
and both event-side duplicates. The source clock is mapped to nominal step ordinals;
its accumulated drift is retained in the exported comparison. The Java runs keep
50 iterations and the existing 1e-4 voltage-change tolerance. The archived reference
used a 200-iteration ceiling; this is provenance, not permission to increase the
Java ceiling. No reference channels, integration method, defaults or limits changed.

| Quantity | Modified Euler maximum / NRMSE | Staggered maximum / NRMSE |
|---|---:|---:|
| Terminal voltage (pu) | 0.00320676 / 0.00020164 | 0.00181731 / 0.00005464 |
| Reactive power (pu) | 0.02142648 / 0.00095264 | 0.01224941 / 0.00027194 |
| Current-regulator state (pu) | 0.00618547 / 0.00114940 | 0.00021917 / 0.00009562 |
| Working correction (pu) | 0.02199208 / 0.00460053 | 0.01051027 / 0.00220425 |

NRMSE uses each complete reference channel's range. The correction channel still
fails a 0.001 criterion in both methods; these diagnostic executions are not an
accuracy acceptance. The staggered trace closely matches the reference until the
correction approaches deactivation, where the network iteration sequence diverges.

Read-only observers around the final-output callback leave all reported trajectory
error maxima unchanged. For active high-voltage samples, the maximum current residual
against the measured Java branch-end phasors changes from 0.00099920 to 0.00327577 pu
in modified Euler, and from 0.00099977 to 0.00339702 pu in staggered integration.
The callback changes current by up to 0.00248336 pu. The solver's voltage-change
certificate is not a joint final-current certificate.

The archived reference output satisfies its correction/current equation within
7.90e-8 pu, but lacks the receiving-bus phasor needed to measure its corresponding
branch-current residual. An assumed internal-source voltage cannot substitute for
that missing measurement. The public interface flowchart separates network
iterations from time advancement and warns that implementation details differ.
Neither this diagram nor the present residual diagnostic justifies deleting the
final evaluation. Additional measured branch-end reference channels are the next
discriminator; no production equation change is made here.

Validation: 19 tests across three public classes pass with the extended profile
(7 accepted-memory, 10 iteration lifecycle, 2 coupled plant tests). A separate
private diagnostic test executes both complete high-voltage trajectories; its
successful execution does not imply the numerical errors pass. The private
`regca-active-hv-lifecycle-20261104` manifest pins the complete traces, diagnostic
source, logs and metrics; SHA-256
`5619dc008fb2997a28d82505a067884498c9d290d7352e760e3af5651de56d44`.
