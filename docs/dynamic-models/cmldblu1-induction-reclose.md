# Composite induction-motor repeated undervoltage recovery

The accepted protection update rearmed its trip flag after the first recovery
but left the corresponding reconnection flag latched. A later voltage dip
could therefore trip the same fraction again without allowing its second
reconnection. Each new trip now rearms only its own reconnection latch. The two
protection stages remain independent; ordinary timer order and electrical
integration are unchanged.

An independent Motor A profile contains two faults, with first-stage
undervoltage shedding and reconnection enabled. The source fraction follows
`1 -> 0.75 -> 1 -> 0.75 -> 1`, at approximately 0.055, 0.110, 0.205 and 0.260 s.
Before the change, both methods produced only the first three actions and ended
at 0.75. After the change they perform all four actions and end at 1. Both-stage
synthetic history tests additionally preserve the latch through copies taken
while tripped and after the first recovery. Repeated observation does not change
protection history.

## Timing limitation of the initial latch-only slice

The initial latch-only slice fixed recurrence, not exact action timing. It still
acts one 0.5 ms interval after each source action because it checks the stored
timer before accumulating the current accepted interval. The lifecycle test
allows one physical interval plus the measured source clock representation
allowance. It retains every source row and reports action times; it does not
shift waveforms or remove edges. Strict pointwise fraction equality was unmet at those action edges. The separate
accepted-stage correction below resolves that discrepancy for the qualified profiles.

Complete recordings are retained under both methods for enabled and disabled
controls. Disabled recordings are numerically unchanged before/after the fix;
enabled recordings are unchanged before the newly restored second recovery.
Diagnostic whole-trajectory voltage/power NRMSE is at most `1.585e-4`, and rotor
angle RMSE is below `8.75e-5` degrees for these profiles. These measurements do
not establish complete composite-model or protection timing qualification.

## Independent evidence

The source records all allocated states and variables in two complete channel
parts. Every enabled cold pair is byte-identical. Disabled raw captures contain
unassigned protection and absent-motor diagnostic storage, including large
finite values; all originals remain retained. A separately labelled cold pair
uses the documented variable-setting API to initialize diagnostic storage
before model initialization. Its two complete parts reproduce byte-for-byte.
All electrical states, physical boundaries, active motor observations and
online fraction match the original disabled captures exactly. Only explicitly
inactive/unassigned slots differ. Clearing is a reference-quality discriminator,
not a production simulation change or an assertion of equivalence for other
models or profiles.

Private archive `cmldblu1-motor-reclose-20261104/captures.zip` SHA-256:
`2c3f16467dfa0a11b315897ba04bd8030cf7cd3bea348fabc2f1f83b456da7ca`.
It contains all twelve original/cleared captures, exact inputs, runnable probes,
source provenance and the clearing characterization. Tests keep source startup
samples in the archive and compare all nonnegative simulation samples,
including every fault-side duplicate. `CompositeMotorRecloseTest` exercises the
independent action sequence, cold-reference integrity, diagnostic-clearing
isolation, both protection stages, warm history copies and read-only observation.


## Accepted history and physical application

An enabled/disabled source pair establishes three distinct stages without using
reported power as the sole oracle. The published protection fraction first
differs at 0.055 s. All three bus-voltage magnitudes and angles remain identical
to the disabled control at that sample and first differ at 0.0555 s. The motor's
five electrical/mechanical states first differ at 0.056 s. The line-voltage
phasors also independently determine the incoming current by network KCL. Thus
publishing the fraction and applying it electrically are not the same operation.

Legacy composite motors now accumulate the accepted interval before checking
protection deadlines. They publish `Fuv` at that accepted boundary and apply the
previous published fraction through `Fonline` at the next accepted boundary.
The generic typed motor and external-protection scheduling remain unchanged.
Combining external protection controls with the legacy composite scheduling
policy is rejected before history changes. Re-enabling capacity after a fully
shed legacy motor is also rejected; that restoration has no source qualification.
No extra integration step or fitted delay parameter is introduced. Connectivity
uses the applied fraction under this policy. A separate capacity action scales
the currently applied fraction instead of prematurely consuming pending
protection. Existing state copies preserve both fractions and the selected
policy. Zero-duration callbacks do not consume pending actions; invalid accepted
intervals reject without changing history.

`CompositeMotorRecloseTest` now requires the exact per-action sequence and exact
pointwise fraction equality at every original sample, with no shifted edges.
Full voltage/P/Q/speed range-normalized bounds are `2e-4`; the rotor-angle peak
bound is `2e-4` degrees. The existing good physical waveforms are preserved by
separating the stages. The observed maximum normalized boundary error remains
`1.585e-4`; the maximum rotor-angle peak is below `1.73e-4` degrees across the
qualified partial-trip controls.

Additional paired references verify zero-delay healthy operation, zero-delay
fault/recovery, and a voltage dip cleared before its pickup deadline. Voltage
eligibility is required even when a time threshold is zero. Both methods match
every sampled protection fraction. The flat source makes two continuation calls
without changing the network; the test explicitly retains their duplicate
observations of the same accepted state without advancing any history. Flat
voltages have maximum error below `6e-8` pu and generator P/Q below `7.4e-7` pu.
Their near-zero reference range is unsuitable for the disturbed-profile
normalization, so the flat control uses explicit absolute bounds while reporting
the raw normalized diagnostics separately.

The exact equality boundary of second-stage timer reset and complete motor
shutdown/restart are not independently qualified by this slice. An exploratory
100% trip profile stopped progressing inside the source at its first full
reinitialization at 0.110 s. Its partial output and log are retained as a rejected
capture, not used as a passing trajectory. No general restoration claim follows
from partial reconnection tests.

`CompositeMotorProtectionTimingTest` retains the independent three-stage
voltage/state discriminator, complete zero/cancelled controls, and a capacity
action while protection is pending, including warm-copy continuation.
Private timing archive SHA-256:
`049a8562c7501471918cb23008bc1c586a42a1812478ee85936a2cc7535a9d23`.
