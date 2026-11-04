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

### Measured reference branch endpoints

A new cold pair adds only passive bus-voltage/angle and branch-power observations
to the retained synthetic profile. All 2,005 original rows and channel values are
exactly unchanged; the two expanded captures are identical. Their logs pass
initialization and contain no reported network nonconvergence. The probe, complete
outputs, logs, repeated-file hashes and independent audit are pinned by private
`regca-hv-phasors-20261104/manifest.json`, SHA-256
`62320f2ff351f96855f4a509621406a44d11a2fa98dcfa17f24b994636e19231`.

The measured terminal phasors now establish a 0.00272490 pu maximum difference
between published converter current and branch-end current, without assuming an
internal source voltage. Reconstructed branch power agrees with independently
recorded branch power within 1.44e-6 pu across every row, including the fault.
The converter output equation agrees within 7.90e-8 pu across every row. At the
largest high-voltage current difference, published reactive power differs from
branch reactive power by 0.00328360 pu. Thus published output-equation agreement
and joint network-current convergence are distinct properties in the independent
reference too. This does not prove the two implementations have identical internal
iteration order or stopping behavior.

The public `RegcaReferenceHighVoltageCurrentTest` verifies manifest/file integrity,
complete cold-repeat and original-channel equality, phasor-to-branch-power agreement,
and the output equation. Its residual assertion establishes the counterexample to
joint certification; it is not a relaxed trajectory gate. Together with the existing
memory, network and plant tests, 21 tests across five classes pass with the extended
profile, zero failures, errors or skips. Correction trajectory errors reported above
remain unresolved, and no final callback or model equation is removed.

A causal correction would need evidence of the input voltage and correction before
each reference network iteration, the stopping decision, and the distinction between
the last solved injection and final published output. Endpoint observations alone
do not identify that sequence. The retained clean acceleration-0.4 fault pair offers
a distinct, not yet rerun accepted-boundary control for whether the mismatch follows
voltage-memory weighting; however, it cannot by itself identify an internal stop
rule. Earlier conditional-network probes already exercise forced-state iteration
budgets, so repeating those or equivalent step-size refinements would not supply
the missing evidence. Any next production change needs a source-defined lifecycle
discriminator rather than a fitted iteration count or threshold.

### Acceleration-0.4 weighting discriminator

The retained clean acceleration-0.4 cold pair has now been exercised. Its complete
2,005-row capture remains repeat-identical, with 97 active correction samples.
Both the 0.4 and 0.8 reference profiles satisfy the same published-current equation
within the unchanged 1e-7 pu ceiling. Reconstructing the recorded single-precision
multiply/add recurrence gives saved voltage within 5e-12 pu (CSV decimal rounding)
for every row in both profiles. This is a reference storage identity, not a change
to production precision. Independent analytic tests exercise both weights under
both production integration schemes, keeping double arithmetic and distinguishing
voltage weighting from the separate correction gain.

The complete coupled simulations were attempted under the unchanged 50-iteration
ceiling and 1e-4 pu voltage-change tolerance. Both fail network convergence:

| Method | Failed stage / step start | Iterations | Final voltage change |
|---|---|---:|---:|
| Modified Euler | Predictor / 0.1035 s | 50 | 0.000119079 pu |
| Staggered | Direct / 0.1045 s | 50 | 0.000106077 pu |

No complete Java trajectory is available for this weighting profile, so no full-run
error or acceptance is claimed. The earlier accuracy limits and failures remain.
Failed diagnostic runs are preserved separately from the 23 passing equation,
lifecycle and previously qualified coupled tests across five public classes.

Complete iteration traces for each failing solve show a slowly contracting coupled
voltage/correction update. Saved voltage is about 1.2083 pu. Although terminal voltage
has fallen to about 1.1894 pu, the decelerated voltage is still 1.20075--1.20084 pu,
so the positive correction increment remains active. Successive voltage changes
contract by 0.94354--0.94378 over the final ten iterations. The final physical current
residual is 0.00119079 pu for modified Euler and 0.00106077 pu for staggered.

For this zero-active-power, purely reactive synthetic network, the actual linear
solve reduces to

`Vnext = (Iq_source + 10*V + 1/0.21) / (10 + 1/0.21)`.

The 10 pu term is the converter's existing compensation admittance; the 0.21 pu
path comprises both 0.1 pu branches and the source's 0.01 pu reactance. This reduced
solve reproduces every recorded iteration of the failing solves within 4e-13 pu.
Together with `D = (1-a)*Vs + a*V`,
`Cnext = C + Khv*max(D-Volim,0)` and `Iq_source = Iq_state-Cnext/D`, the local
voltage/correction Jacobian has a dominant eigenvalue of approximately 0.9434,
consistent with the measured contraction. No fitted gain or stopping threshold is
used in this calculation.

Thus the failure is not explained by an inaccurate sparse solve or an incorrect
accepted-voltage recurrence. The slow component is the mutable correction inside
the iteration. Applying generic voltage-only mixing would change the sequence of
correction increments. A justified solver improvement needs an explicit transactional
iteration-state contract for both voltage and correction, with admissible limits,
rollback and a certified residual. It must also establish whether accelerating that
coupled numerical process preserves the model's intended finite-stop output, rather
than merely reaching a different member of its possible retained-correction states.
The reference's internal iteration inputs and stopping decisions remain unobserved;
endpoint records cannot establish those semantics. No speculative mixing, changed
correction gain, larger iteration ceiling or new default is introduced here.

The complete failing traces, diagnostic source, contraction summary and passing
contract-test log are pinned in private `regca-hv-half-contraction-20261104`, manifest
SHA-256 `74b1d85ac705519963e3ec43289c04439cd25aef63830ca9cd2683d987201cfc`.
Earlier failed executions remain in `regca-hv-half-weighting-20261104`.
