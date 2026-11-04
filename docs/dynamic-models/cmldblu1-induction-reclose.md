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

## Explicit remaining timing qualification

This slice fixes recurrence, not exact action timing. The implementation still
acts one 0.5 ms interval after each source action because it checks the stored
timer before accumulating the current accepted interval. The lifecycle test
allows one physical interval plus the measured source clock representation
allowance. It retains every source row and reports action times; it does not
shift waveforms or remove edges. Strict pointwise fraction equality remains
unmet at those action edges and is a separate source-stage investigation.

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
