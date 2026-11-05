# TIOCR1 accepted-step runtime

TIOCR1 uses the same inverse-time curve, moving-element position, breaker timer
and physical trip actions with modified-Euler and staggered integration. These
are accepted-step histories, not trial differential coordinates. The staggered
solver explicitly admits this relay and its owned load-shedding port, checks
their attachment ownership, and leaves the existing accepted-step callback in
control of protection actions. It does not create a second relay integrator.

`afterStep(0)` is read-only. Previously a zero-duration call could reset a partly
advanced position when reset time was zero and current had fallen below pickup.
Invalid intervals are rejected without changing history. `IntegrationStateAccess`
exposes the existing named histories, an empty differential-coordinate map and
a shared not-evaluated derivative snapshot. Observation cannot advance timers.

Current measurement evaluates the selected terminal's branch-admittance equation
using primitive arithmetic. Both terminal voltages and admittances remain live;
the calculation avoids temporary complex products and sums. Tests compare it
with the original complex equation across both terminals and varied phasors.
No runtime speedup is claimed without a separate timing benchmark.

## Accepted protection and staggered history

Relay trips can change the network after the integration step, outside the
scheduled event handler. The relay now increments a monotonic action revision
only when it actually opens a branch or sheds additional load. The staggered
solver binds these relay owners during initialization and marks an input
discontinuity when their revision changes. The next interval
uses its existing discontinuity handling instead of extrapolating derivatives
across the previous topology. No extra network solve or controller state vector
is added. A monitoring-only operation does not dirty the matrix or trigger this
notification. Unrelated matrix changes are not treated as relay actions; switching
controllers retain their own event/history policies. Revision checks use direct
bindings and reusable primitive storage, with no per-step attachment scan.

Before this correction, the two operating SMIB controls had peak rotor-angle
errors near 0.005 degrees despite matching trip times. After the correction,
all three retained relay reference controls pass complete boundary comparisons,
including event-side duplicates, under staggered integration. The existing
modified-Euler comparisons remain in place.

The new staggered bounds are maximum absolute errors: voltage `4e-6` pu at the
source and `9e-7` pu at the adjacent bus, active power `3.6e-6` pu, reactive power
`2.6e-5` pu, rotor angle `9.5e-5` degrees, and speed deviation `4.2e-8` pu.
These tighter bounds apply to the qualified synthetic profiles only.

## Regression validation

Core validation passed with 486 tests and four existing skips; the selected
three-phase checks passed all 15 tests. The registered suite and the two newly
registered reference groups were executed in separate invocations, covering
3,625 tests with four unchanged baseline failures, no errors and 13 skips.
Evidence retention checks every previous baseline test case and the exact
failure messages, rather than comparing aggregate counts alone.

The relay lifecycle checks and both-method base reference comparisons pass.
The earlier broad matrix-dirty experiment caused a switching-compensator
regression; the final owner-specific action revision passes that test too.
Only the final revision is retained as the production implementation. Private
evidence includes the final runtime/source hashes, complete reports and the
unchanged independent capture identities.

## Reset and repeated pickup

Independent cold-repeated SMIB captures now cover a short fault that only partly
advances the moving element, recovery to zero, and a second fault that reaches
pickup. Monitoring and physical-trip configurations pass with both integration
methods. All nonnegative grid samples and event-side duplicates are compared;
the immutable captures also retain negative-time startup samples.

Reset is checked throughout the recovered interval, second pickup agrees within
one integration interval, and the physical trip agrees within `5e-6` seconds.
Voltage and active/reactive power additionally require full-reference-range
NRMSE below `0.001`. The new profiles use their own tightened peak-error bounds;
the earlier single-fault bounds remain unchanged. In particular, the repeated
fault has a staggered voltage peak error of `6.09e-6` pu, above the earlier
single-fault bound but below its new `6.7e-6` pu bound. No model parameters or
production equations were changed for this qualification.

## Remaining qualification

An additional cold-repeated independent SMIB profile isolates remote static-load
shedding with all transfer-trip targets disabled. Both methods retain all lines,
shed the requested fraction once and pass complete voltage, power, rotor-angle
and speed trajectories. The active load follows constant current and the reactive
load constant impedance. All event-side duplicates are retained. Peak voltage
errors are `0.000183` pu for modified Euler and `2.66e-6` pu for staggered
integration; peak rotor-angle errors are below `5.1e-5` degrees. The reference
capture identity is pinned in `Tiocr1LoadOnlyReferenceTest`; this profile does
not establish dynamic-load or low-voltage shedding equivalence.

Remote static-load lifecycle checks now exercise constant-P, constant-I and
constant-Z active loads with constant-Z reactive load under both methods. They
verify physical removed power from the combined matrix change and live current
correction at changing terminal voltages. Repeated network rebuilds and duplicate
refresh calls preserve the accepted shed fraction exactly once. These checks
cover normal-voltage operation; low-voltage curve transitions, dynamic-load
targets and additional independent remote-action trajectories remain separate requirements.

These results qualify the base coupled boundary profiles, not every relay
configuration. Raw relay diagnostic current/position publication around branch-trip
event samples still needs an explicit observation-stage contract; the energized
load-only position is qualified separately below.
Protection coordination, additional reset/re-pickup settings, remote-action combinations, reconnection,
production-step and longer-duration cases remain on the implementation work list.
Both monitored ends now have independent monitoring-only trajectory captures with
distinct charging currents, as described below. Opposite-end physical actions
and additional terminal configurations remain separate requirements.

## Attachment ownership and atomic rejection

The relay binds to its original monitored terminal and network. Construction
validates every transfer branch and optional remote load host before attaching
any relay port. Network map identity is checked in addition to endpoint network
identity, so an unrelated object with the same bus or branch identifier cannot
be used as a target. These checks use direct map lookups, without network scans.

Initialization, trial callbacks, attribute refresh and accepted-step processing
reject detached or reparented targets before modifying histories or physical
outputs. Every configured target is checked before the first transfer trip or
load action; a missing later target cannot produce a partial trip. Restoring the
original attachment permits its normal lifecycle to continue. Same-network
reinitialization still resets protection history as before.

Both integration methods exercise cross-network targets with matching IDs,
reparenting, restoration and removal of a later transfer target. Existing
independent trajectory tests retain their original tolerances and samples.
This ownership correction does not change relay curves, pickup sequencing,
breaker timing, or extend the qualification scope listed above.

## Left-end current and energized relay position

The moving element uses the solved current at the beginning of its accepted
interval. Independent reconstruction from the complete load-only reference,
including both sides of every event, reproduces position within `1.86e-7`.
Substituting the right-end current produces a `0.002962` error. The reference
therefore distinguishes this input timing from a controller-equation change.

The ordinary predictor samples that current into one reusable scalar; the
corrector and observation methods cannot overwrite it. The staggered solver
samples the same input from its existing indexed relay bindings after the left
network solve and before advancing differential coordinates. The accepted
`afterStep` callback remains the only owner of position, breaker timers and
physical actions. Zero-duration calls do not consume or change the sample.
Operated relays skip unused integration-input measurements.
Standalone accepted-step calls without an integration-stage sample continue to
use their supplied network boundary.

Remote load shedding does not necessarily open the monitored branch. Its relay
position now remains latched at one when that branch stays energized, matching
every post-operation sample of the independent load-only reference. An action
that opens the monitored branch still clears the position. The previous
unconditional reset incorrectly produced a unit position error after shedding.

Both-method coupled position checks now require peak errors below `3e-5` for
modified Euler and `8e-7` for staggered integration; measured errors are
`2.72e-5` and `6.80e-7`, respectively. The tests also require the recorded-input
left-current reconstruction to pass and its right-current negative control to
fail. Physical boundary, trip-time and reset checks retain their original gates.
This does not qualify branch-trip observation ordering, dynamic-load actions,
reconnection or other configurations listed above.

## Independent terminal selection with charging current

Cold-paired monitoring-only captures select either end of the same synthetic
branch, with nonzero total line charging. The line remains connected. The
complete recorded phasors reconstruct the selected terminal current within
`4.59e-7` pu, whereas substituting the other terminal produces about `0.163` pu
error. Thus the test distinguishes terminal selection from reversing an
equivalent series current on a line without charging.

Both imported terminal configurations pass modified-Euler and staggered SMIB
fault comparisons, retaining every nonnegative grid sample and both sides of
fault application and clearing. The original negative-time samples remain in
the immutable reference archive. Voltage and active/reactive power each require
full-reference-range NRMSE below `0.001`, alongside the existing physical peak
limits. These checks do not alter model equations or source parameters.

| Complete terminal comparison | Modified Euler | Staggered |
| --- | ---: | ---: |
| Maximum current error, pu | 0.000522 | 0.000000869 |
| Maximum moving-element position error | 0.0000194 | 0.000000290 |

New current bounds are `5.8e-4` and `1e-6` pu, and position bounds are `2.2e-5`
and `3.3e-7`, respectively. Tests pin the reference archive and require exact
cold-repeat data identity. This closes independent monitoring-terminal
selection coverage at the tested interval; it does not qualify opposite-end
physical actions, dynamic-load shedding, reconnection or production-step timing.

## Reinitializing load-target ownership

Every preparation clears the previous static-load contribution before resolving
the current target. If a static source has been replaced by a dynamic current
port, shedding now changes that dynamic load alone. Previously the relay also
removed its cached static admittance, double-counting the action.

A both-method regression reproduces the defect before the fix and checks the
dynamic power reduction, unchanged network stamp and absence of stale static
current correction after voltage changes. The focused relay and reference suite
passes without changing trajectory tolerances. This preparation fix does not
establish composite-load shedding support: expanded internal components and
their equivalent distribution network require model-owned capacity changes.

## Partial composite-load action

The remote load port now resolves an expanded `CMLDBLU1` owner by its original
source-load identity during initialization. A singleton source or whole-owner
wildcard is supported; selecting one source from an aggregate is rejected.
Relay wildcard actions on a collapsed parent/internal bus are also rejected
until the unrelated static residual can be allocated without counting owned
components twice. The source-owner API remains distinct from a whole-bus relay.
The dynamic matrix does not yet exist at that preparation stage, so live-matrix
and owner validity are checked again before the accepted action. Failed target
preparation cannot silently become a successful relay action.

The action calls the model's capacity operation once, rather than searching for
internal leaf IDs or removing an obsolete parent static-load stamp. That operation
changes demand and the verified distribution equivalent together. The composite
also updates the bus's cached positive-sequence shunt admittance, preventing fault
clearing or another matrix rebuild from restoring the old compensation.

Complete shed/no-shed comparisons retain startup evidence and all three pairs
of event-side samples: fault application, relay operation and fault clearing.
The internal `VAR29` reference is a prepared-voltage diagnostic: on the relay
action's pre-solve row it already uses changed feeder parameters with the previous
solved boundary. The recorder retains physical `Load.V` separately from
`Load.PreparedV`; it does not change physical voltage to match that diagnostic.
Independent reconstruction of the mixed-boundary reference row agrees within
`4.1e-7` pu. Both physical and diagnostic samples remain in the complete recording.

The partial-action comparison passes both integration methods. Maximum
range-normalized RMSE across external voltage/power and internal voltage
observations is `3.28e-5` for modified Euler and `5.36e-6` for staggered integration.
Peak rotor-angle errors are below `4.9e-5` degrees. The matched no-action control
also passes. Regression envelopes require normalized error below `5e-5` and
`8e-6`, respectively, with unchanged full reference ranges and all event samples.

## Owned composite disconnection

Full-capacity removal also passes complete independent reference comparisons
under both integration methods when the composite owns a distinct transformer,
feeder and internal-bus subtree. The operation removes those branch stamps,
deactivates owned internal devices and buses, and leaves their last physical
observations frozen. Parent attachments and active external faults are preserved;
subsequent matrix rebuilds retain the disconnected topology.

The preparation and action checks reject foreign internal attachments and
unsupported collapsed topologies before mutation. Full-trip references retain
all relay and fault event samples. Full-trip voltage/power normalized errors are
below the same `5e-5` modified-Euler and `8e-6` staggered regression envelopes,
with rotor-angle peak error below `6e-5` degrees. Restoration remains unsupported.

## Opposite-terminal physical actions and pre-trip recording

Cold-paired charging-line captures now exercise physical transfer trip and
ten-percent remote load shedding from each monitored terminal. The full 0.5-second
trajectories retain all 1,004 nonnegative samples, including application, relay
operation and clearing duplicates. Both methods pass the existing `0.001`
full-reference-range voltage/power NRMSE and `0.01`-degree rotor-angle RMSE gates.
The largest voltage/power NRMSE is `0.0000311` for modified Euler and `0.00000386`
for staggered integration. The existing terminal-position envelopes also pass:
maximum errors are `0.0000194` and `0.000000290`, respectively.

After the combined regression reproduced these results, the new physical-action
tests additionally enforce tighter voltage/power NRMSE envelopes of `5e-5`
(modified Euler) and `8e-6` (staggered), and rotor-angle RMSE below `5e-5` degrees.
The original acceptance limits remain explicit; these additional envelopes
protect the measured accuracy of this synthetic action fixture.

The benchmark recorder previously obtained the pre-trip row in `beforeStep`,
after the preceding accepted callback had already opened the branch and cleared
the relay position. It now retains relay observations immediately before that
callback and uses them only when the physical-action revision changes. The
post-action sample retains the live values. No physical states or actions change;
the existing four complete trajectories differ only in the three relay fields
on their single pre-action row. A frozen old recorder fails all four new tests
with pre-trip position zero instead of one. The focused suite passes 36 tests
without failures, errors or skips.

Raw diagnostic slots remain distinct from physical quantities. The reference's
breaker memory contains `9999` before pickup, an absolute operation deadline
while pending, and zero after opening; the runtime exposes elapsed breaker time.
Its recorded current is zero on the opening row but resumes a terminal-admittance
diagnostic on subsequent rows even though the branch is open. The physical
current accessor correctly stays zero. The test retains and explicitly exposes
these differences rather than treating those raw slots as interchangeable.
Complete diagnostic-current publication, additional breaker delays, multiple
transfer targets and reconnection remain separate qualification work.

The immutable paired captures are retained in
`testData/private/verification-artifacts/tiocr-physical-terminal-20261104/captures.zip`,
SHA-256 `76deeda96324b72911d7b6525e352656cb245e478dda1cbc62365886d0cee07c`.

## Zero-delay and separate transfer-target boundaries

Eight additional cold captures cover zero and 0.02-second breaker delays,
opening either the monitored circuit or its parallel circuit. Paired captures
are identical. Both integration methods retain every sample and operation side;
the transfer target alone opens, the requested load fraction is applied once,
and the monitored position stays at one when its circuit remains energized.

Zero delay reveals a boundary that a pre-callback snapshot cannot represent:
the accepted callback first reaches pickup and then opens the branch. Its
pre-action position is one, whereas the earlier snapshot was approximately
0.99145. `Tiocr1RelayModel.ActionSnapshot` now captures current, position and
elapsed breaker time after the accepted timer/pickup update, before network
mutation. It is published only after a physical action commits, with that
action's revision. Repeated reads and rejected callbacks preserve the same
immutable snapshot; reinitialization clears it. The snapshot is allocated only
on a physical action. Normal integration steps do not allocate snapshots.

The snapshot also retains the internal operation latch at that same boundary.
`isOperated()` becomes true after pickup/breaker conditions are satisfied, even
in monitoring-only mode; it does not mean that a physical action has committed.
Consequently the pre-action observation has `RELAY.Operated = 1` while its
physical revision and network topology remain pre-action. The post-action row
advances the physical revision. This distinction follows the runtime lifecycle,
not an inferred meaning for a reference diagnostic slot. Monitoring-only tests
explicitly require the latch to be true with revision zero and no action snapshot.

The recorder combines this exact action snapshot with the retained pre-action
topology/status observations. The old recorder fails all eight new controls:
zero-delay position is premature, or nonzero-delay elapsed time is one interval
short. The physical runtime equations, trip times and integrated trajectories
remain unchanged to roundoff (maximum observed rotor difference below `1e-12`
degrees). These observations do not reinterpret raw diagnostic deadline/current
slots as physical quantities.

The focused relay/composite matrix passes 58 tests with no failures, errors or
skips. New reference tests keep voltage/power NRMSE below `5e-5` for modified
Euler and `8e-6` for staggered integration, rotor-angle RMSE below `5e-5` degrees,
and the existing terminal-position envelopes. Multiple simultaneous transfer
targets, reconnection and additional delay/curve combinations remain unqualified.
The independent controls are retained in
`testData/private/verification-artifacts/tiocr-delay-transfer-20261104/captures.zip`,
SHA-256 `47903792e52b8409d98d809c11f5209c06e69eff3986ecaeaf70a6d5d7dce07f`.

## Multiple transfer targets and static-target preflight

A three-circuit synthetic corridor now qualifies simultaneous opening of two
transfer targets while the third circuit stays connected. A second configuration
repeats the first target in the third transfer slot. Both have cold-repeat captures;
the duplicate-target and distinct-target reference trajectories are byte-identical.
The corresponding Java trajectories are also byte-identical under each method.
All samples and action sides are retained, both requested branches open once,
and the remote load fraction and physical action revision advance only once.
The same strict trajectory and position envelopes used above pass. Maximum
voltage/power NRMSE is `3.09e-5` for modified Euler and `3.43e-6` for staggered
integration; maximum rotor-angle RMSE is `2.45e-5` degrees.

The target audit also exposed a separate atomic-preflight defect. A prepared
single-ID static load could be removed or replaced before operation, yet the
relay still opened its transfer branches and applied the cached static-load
contribution. Preparation now retains that load object's identity and stage
preflight requires the same object to remain attached. Rejection occurs before
relay history, branch status, matrix, shedding fraction or action snapshot
changes. Restoring the original object permits the normal action. This change
does not claim new wildcard or dynamic-target ownership coverage.

Both-method negative controls fail on the old runtime and pass after the fix.
Repeated accepted callbacks preserve the committed matrix, shed fraction,
revision and immutable snapshot. The focused group passes 66 tests with no
failures, errors or skips. Four complete valid trajectories remain unchanged
apart from roundoff below `3.6e-15` across recorded fields. Reconnection and other
unqualified curve/delay combinations remain outside this result.

Paired source and reference evidence is retained in
`testData/private/verification-artifacts/tiocr-multiple-transfer-20261104/captures.zip`,
SHA-256 `05760ac11b534f31068cd01d5ad7d71c75a53add35c19b24d54537b747c7b483`.

A further connected-corridor control trips three distinct targets in one
accepted action while a fourth circuit remains energized. Two cold captures
are byte-identical. Both integration methods match all 1,004 nonnegative
samples and the three event-side duplicates, and preserve the pre-action
relay observation, one load-shed revision and all four physical branch
statuses. Maximum voltage/power NRMSE is `3.10367e-5` for modified Euler and
`3.39591e-6` for staggered integration; rotor-angle RMSE is `3.83556e-5`
and `2.04603e-5` degrees, respectively. The unchanged strict envelopes are
`5e-5` voltage/power NRMSE for modified Euler, `8e-6` for staggered, and
`5e-5` degrees for rotor angle.

Opening all three circuits without a surviving path is a separate island
control. The reference de-energizes both buses at the action while the current
network solver retains energized island voltages. That topology difference
remains open; the connected-corridor result does not qualify full islanding.
The paired connected-corridor evidence is retained in
`testData/private/verification-artifacts/tiocr-fourth-survivor-20261104/captures.zip`,
SHA-256 `1c4087c09e497647464aa72d552a3bf546ed833ee0e4ccaefa1c5b23e8ce0440`.

## Prepared target lifecycle

Single-ID static targets retain their active status and nominal load definition
(code, CP/CI/CZ and distributed-generation settings) as part of preparation.
Removing, replacing, reparenting, disabling or directly editing that source
requires preparation again; a pending relay action rejects stale preparation
before changing timers, transfers, matrix stamps or accepted snapshots. Restoring
the prepared definition permits retry. Bus-voltage changes remain valid inputs.

Dynamic targets retain object and parent ownership, including the selected
membership for wildcard targets. Their live state, voltage-dependent power and
capacity changes are not frozen. Replacement or reparenting requires preparation;
ordinary capacity changes remain compatible with subsequent shedding. Composite
capacity validation also runs before accepted-step timer and transfer callbacks.
This does not establish wildcard static-definition mutation or reconnection support.

The focused group now passes 76 tests with no failures, errors or skips. Ten new
parameter invocations fail on the previous implementation and pass with these
checks. Four exercise the actual LDS3BL/LVS3BL accepted callbacks on networks
prepared with each solver; these are ownership and atomicity controls, not new
independent equation qualification for those relay families. Six exercise TIOCR
static-definition changes and dynamic replacement/reparenting, including valid
voltage and capacity changes after restoration. All 26 complete valid trajectory
files retain every row and event-side sample and compare exactly numerically.

## Low-voltage static-load action

Independent cold pairs now cover the same deep fault at either end of the
synthetic corridor. The load-bus fault reduces its voltage to `0.3333` pu and
operates the load-only relay while voltage remains below the `0.5` pu
constant-current reduction knee. The opposite-end fault leaves the load above
that knee (`0.7481` pu), providing a location control. Both cold pairs are
byte-identical across complete native recordings.

The recorder inherits initialization from the production solver; post-initialization
readback confirms elliptic current reduction for both integration methods. No
configuration loss or relay equation defect was found. Four new comparisons
retain all 1,004 nonnegative rows, including exact pre/post shedding observations.
They preserve the original `0.001` voltage/power NRMSE and `0.01`-degree angle
RMSE gates, with additional strict envelopes: `6e-5` voltage/power NRMSE for
modified Euler, `5e-6` for staggered integration, and `9e-5` degrees angle RMSE.
Complete relay-position peak errors remain below `8e-5` and `3e-7`, respectively.

The measured worst voltage/power NRMSE is `5.18e-5` for modified Euler and
`3.98e-6` for staggered integration; worst angle RMSE is `8.17e-5` degrees.
Deliberately substituting the generic sine reduction after initialization fails
both load-bus profiles (`1.52e-4` and `1.47e-4` voltage NRMSE), while both
above-knee location controls pass. The final runtime retains elliptic reduction.
This qualifies a deep-fault crossing and recovery with load shedding, not every
near-threshold curve point, reset constant, protection coordination or reconnection.
The earlier independent partial-reset/second-pickup controls remain unchanged.

The paired evidence is retained in
`testData/private/verification-artifacts/tiocr-low-voltage-20261104/captures.zip`,
SHA-256 `023e428361fb35e40ede14293dcb361293e02d242e21aab402cff10bbed71285`.

## Broader exact-reference parity: run segmentation limitation

New cold pairs vary zero-current reset time between `0` and `0.4` seconds,
include an operating relay, and introduce a run boundary during a fault with
no topology change. The no-event split adds a second row at `0.0550000221` seconds
and advances contact position from `0.234860420` to `0.258940876` while current
and all physical network measurements remain unchanged. Every common physical
sample is identical; the extra row is explicitly retained. The scheduled
breaker deadline advances by another nominal `0.0005`-second interval.

Thus the extra contact update is not uniquely a physical-event rule. Adding
zero-time state advancement to the Java event handler would encode reference
run segmentation into the physical integration history. The runtime remains
unchanged, including read-only zero-time callbacks. An independent source-contract
regression pins the complete cold captures, checks the extra row and all common
physical samples, and verifies the deadline shift. It does not treat this source
behavior as a runtime requirement.

The unresolved full-trajectory diagnostics are retained separately: four
monitoring comparisons fail the complete contact-history gate (peak differences
`0.4804` for immediate reset and `0.02262` for the slower reset); two operating
comparisons fail the physical action clock (`0.224498883` seconds in the reference
versus `0.225` seconds in Java). A flat first-segment probe also contains native
pre-zero contact evolution and additional run-boundary samples, so both full-row
comparisons remain unqualified. No samples were removed and no time or state
offsets were applied to pass these checks.

The flat probe's ordinary interval slope corresponds to approximately `1800.017`
seconds to close at `1.05` times pickup, supporting the existing interpolation
between the threshold anchor and first entered curve point. This is a component
observation; it does not establish complete history parity with the segmented
reference run.

Remaining completion criteria are explicit:

- Resolve the startup/run-segmentation observation contract before requiring
  exact reference contact history for every reset constant. Preserve the
  elapsed-time integration contract unless an independent specification requires
  a separate, explicit compatibility behavior.
- Verify current-knot equality and immediately adjacent values, threshold
  equality, and saturation independently if exhaustive curve-boundary support
  is required; the current probes cover representative intervals and reset cases.
- Qualify any additional protection coordination or reconnection behavior only
  against an exact supported source contract. No reconnection was introduced.

These are bounded qualification limits, not evidence of a new relay equation
fix. Full open diagnostics and five byte-identical cold pairs are retained in
`testData/private/verification-artifacts/tiocr-reset-contract-20261104/`.
The capture archive SHA-256 is
`df3281f92bf68e00e6b34fc397c9fc7f6490a570b3e6e2069adec0afbc720933`.


## Exact native load IDs versus typed wildcards

Independent cold pairs now distinguish an exact composite source ID from an
absent `*` target, using a collapsed composite plus an unrelated static load
and fixed shunt. The exact-ID action removes 25% of composite capacity and
preserves the unrelated impedance load. The `*` record instead emits
`invalid load specified` at operation: its contact closes, but neither load
is shed. Each pair has identical complete CSV output; event-side duplicates
remain retained. External current/power reconstruction confirms the unchanged
static impedance demand within 1.9e-6 pu active power after fault clearing.

The original generic capture quality predicate missed that action-time message.
Those wildcard captures are retained as rejected-target evidence, not accepted
trajectory references, irrespective of their original manifest status. The
model-specific capture wrapper now treats the diagnostic as a quality failure.

The native importer previously exempted `*` from exact load-existence checks
and passed it to the typed whole-owner runtime. Import now rejects that
interpretation before attaching either relay or load port. If a literal load
named `*` or `#` exists, the diagnostic explicitly identifies an unsupported
exact symbolic target because that runtime could not distinguish it from its
typed wildcard API. The literal-target extension below supersedes this restriction
for existing exact identities. Load-bus-zero/no-load actions remain supported;
typed wildcard constructors remain an explicit application choice.

These observations do not qualify native whole-bus wildcard shedding or solve
typed collapsed-wildcard static-residual allocation. Those scopes remain
separate. No composite equations, accepted-event scheduling, physical bounds
or accuracy thresholds change in this import correction.

Focused validation passes 19 tests with zero failures/errors/skips,
including import atomicity, symbolic literal diagnostics, typed API preservation,
no-load actions and existing both-method relay/composite checks. Private evidence
is in `tiocr-exact-target-20261104`; archive SHA-256 values:

- `captures.zip`: `c75503f9a87b2b8cef7532726138fc6434df7b4c88daba35336bef593c493d25`
- `validation.zip`: `11f77da83e7dd454d2b4a16a7bfef0ee2440eb3124d89c88bc68f4e19f82967f`


## Retained reference quality audit

The model-specific archive audit covers 69 retained native logs and CSVs,
including the original monitor, operating and current-load controls and the
later relay/composite action profiles. Sixty-seven pass capture-quality
prerequisites. Only the two already rejected absent-`*` target controls fail;
no additional claimed qualification depends on an invalid load action, missing
positive initialization check, network nonconvergence or incomplete/nonfinite
channel rows in this retained inventory.

`tools/verify_tiocr_reference_quality.py` records archive/log/CSV hashes,
original manifest status, recorded row counts and event-side duplicates. It
uses specific failure diagnostics rather than rejecting arbitrary warnings.
A closed relay contact or historical `complete` manifest cannot override an
invalid-action diagnostic. Seven unit tests cover those false positives, benign
messages, initialization, channel completeness, finite values, time ordering, measurement-free headers and streamed archive hashing.
These checks verify row/channel prerequisites. Without an expected-horizon
schema they cannot prove that a requested run horizon was completed.

Run `python tools/test_verify_tiocr_reference_quality.py` for the unit checks.
Run `python tools/verify_tiocr_reference_quality.py --report target/tiocr-quality.json`
for the read-only archive audit. Its current exit status is 1 because the
inventory deliberately retains the two rejected-target controls. The report
identifies those captures individually; they are not deleted or silently
excluded. No existing trajectory gate is disabled or relaxed. Passing capture
quality alone does not prove the correct physical action or model trajectory;
the existing boundary/KCL and full-reference tests remain required.

Private `tiocr-reference-quality-audit-20261104/audit-v2.zip` retains the complete
hash-indexed inventory, verifier source, unit-test output and audit exit status.
SHA-256: `11dc21f1f46f9174405d184f37001e9c2d605ff32fc1b71c070a4a8ca7de8d68`.

## Exact symbolic load identities

Imported TIOCR1 records now select an exact load identity through
`Tiocr1RelayModel.forExactLoad`. Literal `*` and `#` IDs are supported when that
load exists. Absent symbolic targets still fail before attachment; import does
not expand them to whole-bus commands. Existing typed constructors retain their
explicit wildcard behavior.

The prepared load port stores the selection policy once. Static and dynamic
target discovery, ownership checks and capacity actions use that same policy.
An exact source cannot select an individual member of a composite aggregate:
the existing singleton-owner restriction remains enforced even when the source
ID is a symbol. No per-step allocation or alternate integration equations are
introduced.

Independent SMIB captures rename only the load and relay target among `1`, `*`
and `#`, first alone and then alongside an unrelated load. All cold pairs repeat
exactly, and all target variants have identical complete channel files. The
selected load loses ten percent of its capacity; the peer retains its full
constant-current P and constant-impedance Q capacity. Positive initialization
and action-log quality checks pass. Each capture retains all 1,006 rows,
including startup and event duplicates.

Both-method tests retain the complete nonnegative comparison grid, require
bit-identical ordinary/symbolic Java trajectories with the peer present, and
enforce the existing 0.001 range-normalized voltage/P/Q and 0.01-degree rotor
RMSE gates. Original captures remain immutable in private
`tiocr-literal-targets-20261104/captures.zip`, SHA-256
`01876cab0f0c9a14ae2b9c688953d19d5868136c8e80b6e12ec793df8f9f8c11`.
This exact-identity extension does not resolve typed collapsed-wildcard
static-residual allocation or broaden composite source ownership.

The focused import, peer-load, composite and lifecycle selection passes 23
tests. The broader relay/parser regression selection passes 114 tests, with
zero failures, errors or skips. The new both-method controls are registered in
the core plugin suite. These checks use the corrected isolated core candidate;
default dependency versions are unchanged.


## Typed collapsed-parent wildcard allocation

Typed whole-bus `*` and `#` targets now support a fully collapsed composite with
an independently attributable static remainder. Imported symbolic load IDs
remain exact identities; this change does not grant wildcard semantics to input
records or individual selection inside a multi-source composite.

Relay initialization precedes the engine's subtraction of dynamic demand from
the gross bus load. The relay therefore uses the composite's recorded pre-leaf
parent allocation: unrelated static power and independent shunt. It does not
capture gross composite demand a second time. The composite capacity action
scales its own leaves and compensation shunts once, retaining its fixed shunt
under the existing partial-capacity contract. The relay's persistent matrix
ledger and voltage-dependent current correction apply only to the static
remainder; optional staged-relay shunt shedding excludes composite-owned shunts.

A foreign dynamic load at the collapsed parent remains unsupported because its
initial allocation has not yet been established at relay preparation. Changed
static source definitions or membership also reject the action before relay
position, capacity or matrix mutation. General dynamic-remainder allocation and
restoration are not introduced.

The focused controls exercise both integration methods, two successive quarter-
capacity actions, current-P/impedance-Q voltage dependence, gross/leaf/residual
balance, independent versus owned shunts, matrix rebuild and idempotent ledger
reapplication. A separate staged-relay control enables independent-shunt
shedding. These are typed API ownership and physical-balance checks, not a claim
that the reference interface accepts whole-bus wildcard records. The earlier
collapsed-wildcard rejection noted above is superseded only for this scope.

Validation: 116 focused tests passed without failures, errors or skips; the final
three ownership controls also passed after adding the explicit rebuilt-ledger
identity. No trajectory threshold or sample handling changed.
