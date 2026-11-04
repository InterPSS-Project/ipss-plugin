# Parallel model integration validation

The combined REECC1, TIOCR1 and composite-load implementation wave passes
222 tests with no failures, errors or skips. This includes shared renewable
controller regressions, both integration methods, relay physical-action
recording and complete reference trajectories for omitted distribution elements.
Existing accuracy thresholds are unchanged.

The registered suite now includes the breaker/transfer reference test and the
partly collapsed composite full-removal test. REECC1's optional two-step path
and its remaining qualification boundaries are documented in
[the controller report](reecc1-two-step-integration.md).

This focused result does not qualify the entire branch for merging. The full
registered suite at `771b92e33` reports 4,809 tests with 215 failures, two errors
and 13 skips. Its failing identities are unchanged from the previous full run
(4,756 tests). No failures are newly introduced or resolved by this wave;
the remaining model and system gates must be resolved.

The full run was compiled before the later motor-rearm and early relay-preflight
changes, which have separate focused validations. Its 719 executed test-class
reports and bounded log are retained at
`verification-artifacts/full-model-wave-20261104`, SHA-256
`d7a9aa03e1a7795ae0b77b0743de9541b538c862805b3913e1811994d0ae89a4`.

Private evidence is retained at
`verification-artifacts/parallel-model-wave-222-20261104`.
The archive contains the bounded run log and all selected test XML reports.
Tested source is `48fa1151e` plus the two suite registrations.
Archive SHA-256:
`dc6d2a86e11db892a967d3fc6a6b4d9d3bd5d3d399416c1582582c61cac22e1e`.

## Network convergence diagnostic

An explicit test-only option, `ipss.smoke.certifiedNetworkAcceleration=true`,
installs the existing bounded-acceleration solver in the imported-system smoke
fixture. The option is off by default. Each accepted network call is checked
against its original unmixed voltage correction, 1e-4 pu tolerance and
50-iteration cap. A mixed update alone cannot satisfy the certificate.

All smoke scenarios complete with this option enabled. Together with the shared
helper's battery-controller and endpoint-recording regressions, ten tests pass
without skips. The system scenarios actually use bounded mixing; no certificate
exceeds the iteration cap or voltage tolerance.

A fresh run with the option absent reproduces the same two default-path
failures. Their final voltage corrections are approximately 1.151e-4 and
1.609e-4 pu at the iteration cap. This isolates a convergence limitation; it does
not establish reference-trajectory accuracy, a speedup or grounds for changing
the production default. The registered default-path failures remain visible.

Private evidence archives are retained at
`verification-artifacts/system-certified-counted-20261104` (SHA-256
`07eb605e6e591f02cdd05f47a2dd460e36faebb8c6b420a04872e325f345b36e`)
and `verification-artifacts/system-default-control-20261104` (SHA-256
`d81fa2183a9a00de876a453f9d44c7f76d1f1e9f28a02b4db458ee69e269e48b`).

## Relay target ownership

The prepared static-load target is now retained by object identity. Removing it
or replacing it with a different object under the same ID rejects a pending
action before relay history, transfer branches or shedding stamps change.
Restoring the original object permits one accepted action. Independent complete
trajectories also qualify multiple and duplicate transfer targets under both
methods. The multiple-target test is registered in the main suite.

The combined relay/composite selection passes 120 tests with no failures,
errors or skips. These are actual executed XML test counts. Shared preflight is
exercised through TIOCR1's load port; this does not establish separate LDS3/LVS3
runtime qualification. No-match selectors are not evidence of coverage.

Private evidence is at
`verification-artifacts/relay-target-ownership-integration-20261104`, SHA-256
`b3017c67700c331759b87aa56d2c1419ccac0cdf3fafbefae547c0e1cb0c1549`.

## Repeated motor reconnection

The induction-motor protection now rearms reconnection on each new undervoltage
trip. Both integration methods complete the second reclose, which previously
remained blocked by the first reconnection's latch. Seven integrated lifecycle
tests pass, including independent repeated-fault captures, warm state copies
and read-only observations. The test is registered in the main suite.

That checkpoint established recurring actions, not exact protection timing;
the subsequent correction is described below. See the
[motor report](cmldblu1-induction-reclose.md) for scope and retained controls.
Private integrated evidence is at
`verification-artifacts/motor-reclose-integration-20261104`, SHA-256
`e202a00e8c0730eaef51e5f628673222cd83b71825c5227ae461936f985696f0`.

## Early shared relay preflight

The subsequent relay change validates prepared target identity, static definition,
status and parent ownership before advancing shared accepted-step timers or
transfer actions. Legitimate dynamic power evolution remains allowed. Actual
LDS3BL/LVS3BL callback tests now cover invalid targets, restored retries and
idempotence on networks prepared by both solvers. These are lifecycle tests,
not independent equation qualification of those relay families.

The combined relay, composite and motor selection passes 137 tests with no
failures, errors or skips. Private evidence is retained at
`verification-artifacts/relay-lifecycle-integration-20261104`.

## Attached battery plant-controller replay

The registered REECC1/REPCA replay checks both integration paths against every
recorded sample, including duplicate boundaries. The retained capture index and
each artifact are hash-checked, and cold-repeat output equality is asserted.
Accepted two-step commands and provisional algebraic commands remain separately
observable. Analytic remote-sensing checks do not imply independent remote
qualification: the reference environment normalizes the requested remote
selector to local sensing.

The integrated replay and shared renewable-controller selection passes 58 tests
without failures, errors or skips. Full attached-plant network feedback remains
a separate gate. See [the qualification report](reecc1-plant-and-remote-sensing.md).
Private validation is retained at
`verification-artifacts/reecc-plant-integration-20261104`.

## Composite motor publication and application stages

The subsequent legacy composite policy separates accepted protection publication
from electrical application at the following boundary. Independent voltage,
angle and current-balance controls establish this ordering; it is not a timestamp
shift. Sampled protection fractions now match exactly, with physical trajectories
unchanged from the rearm-only version. Zero-delay healthy-voltage and cancelled
pickup controls pass. Warm copies retain pending history, capacity changes use
the applied fraction, and unsupported external-protection combinations reject
before mutation. Generic motor scheduling remains unchanged.

Low-voltage relay comparisons also pass under both methods using the inherited
elliptic load-current curve; an intentionally wrong curve fails the low-voltage
controls. The combined selection passes 155 tests, including six generic motor
state tests, with no failures, errors or skips. The older
`TestInductionMotorModel` has commented test annotations and is not included in
that count. Full motor restart and the second protection stage's exact equality
boundary remain unqualified.

Private evidence is retained at
`verification-artifacts/protection-timing-integration-20261104`, SHA-256
`8e5c75eeb551d73eb907020f937f9fae5e5fde8ebf2a05a1945f0aee28471051`.

## Accepted converter event inputs

REGCA now refreshes P/Q inputs after the event network solution is accepted,
using the existing event-input callback. Publication is atomic after finite
checks; converter and controller states, timers and numerical history do not
advance. Both solvers exercise the actual imported-device callback, and failed
event solves do not publish inputs. See the
[event-power report](regca-accepted-event-power.md).

The broad renewable/plant-controller and motor audit selection reports 323 tests:
322 pass and one opt-in positive-Q diagnostic is skipped. No failures or errors
remain in this selection. The first attempt exposed an obsolete module test
that expected an active power-filter state with PFFLAG=0; its expectation now
matches the previously verified disconnected branch. No production equation
was changed to satisfy that assertion. The initial failure log is retained.

This also includes the attached plant's reactive lower-limit replay, with
unchanged existing replay ceilings. Full network event residuals remain separate
from replay qualification. Private validation is retained at
`verification-artifacts/converter-event-integration-20261104`, SHA-256
`4b24e9ea6f2fc349f6e73a85ba1cb5f4a4fbfd2b76386bcea0f30b6b62f3645d`.

A default-path system smoke recheck still reports the same two network
nonconvergence failures, at the same stages/times and 50-iteration cap. Their
residuals change because accepted event inputs now affect the trajectory; this
is not an assertion of full-system reference accuracy. The no-event test passes.
Evidence is retained at
`verification-artifacts/converter-event-system-smoke-20261104`.

## Reference run segmentation

A registered source-contract test confirms that adding a RUN boundary at the
same physical time changes the reference relay's contact history and advances
its scheduled trip by one interval, while current and other physical channels
remain identical. The source-contract and Java accepted-history selection
passes 41 tests. Java zero-duration integration remains unchanged; this evidence
does not justify artificial clock advancement or full relay reference parity.
The motor threshold audit likewise bounds the observed comparison but does not
claim equality with an inaccessible internal voltage coordinate.

Private source-contract integration evidence is retained at
`verification-artifacts/relay-source-contract-integration-20261104`, SHA-256
`3a2bc4a2c5ea9425909402099ca1ecd613be914d3c1398df9172cf4d0e4aa39a`.

## Composite motor overlap coverage

The validator now warns when the two motor trip fractions sum above one,
without changing parameters or runtime equations. Both the warning tests and
strict trajectory comparisons are registered in the suite. A registration
compile failure was corrected by making the validation test class public.

The focused integrated selection executes 43 tests: 40 pass, three fail, and
none error or skip. The failures cover both overlapping-fraction trajectories
and the modified-Euler exact-zero control's rotor-angle peak. These are newly
registered qualification gaps, not a runtime change introduced by the warning.
The independent overlap capture exhibits negative remaining motor fractions;
neither silently clamping that evidence nor implementing negative motor load
is justified. Existing accuracy thresholds remain unchanged.

Private evidence, including the initial compilation failure, is retained at
`verification-artifacts/motor-overlap-integration-20261104`, SHA-256
`73f89ce734fb1e74776cc7037a1ae8c13b5919f03c76a081f7b4fa5adf939040`.

## Converter memory and DC reference contracts

Two further registered source-contract selections pass 27 tests with no
failures, errors or skips. The converter selection explicitly enables the
extended profile, so both plant replay and the saved-voltage recurrence check
execute alongside network lifecycle tests. This is diagnostic evidence, not
qualification of a new production memory policy.

The DC control adds a zero-time continuation after clearing. Its complete
output and published states match the uninterrupted capture exactly. This
rules out that API entry alone as the observed protection timing cause; it
does not reveal internal sensing or callback stages, and no protection clock
or production equation was changed.

Private integrated evidence is retained at
`verification-artifacts/memory-and-dc-contract-integration-20261104`, SHA-256
`3015ea5aa2241b6cca69c3db90dbe84382a8d115452de1a722ce7242b2896f01`.

## Optional accepted-boundary converter memory

The [accepted-boundary policy](regca-accepted-voltage-memory.md) is integrated
with shared event/step history under both methods. Trial network solves do not
commit memory, and repeated solves cannot introduce extra history updates.
The existing instantaneous and legacy per-solve policies remain unchanged;
six full-trace opt-out controls match exactly. No default or solver change is
included.

The integrated renewable regression executes 320 tests: 319 pass and one
opt-in positive-Q diagnostic is skipped, with no failures or errors. Both new
public test classes are registered in the main suite. This focused result does
not replace the earlier full-suite failures or qualify this policy as a global
default. In particular, high-voltage correction and broader system/runtime
qualification remain open.

Private executed reports and the skipped-test identity are retained at
`verification-artifacts/accepted-memory-integrated-renewables-20261104`, SHA-256
`f1d2e6a0f29041dee76caa11d59a4801c68083f5cac335a6ec645d7ce647b202`.

## Full extended-suite recheck

At `e57235ef3`, the registered suite with the extended profile executes 4,879
tests across 734 class reports: 4,643 pass, 221 fail, two error and 13 skip.
Every previously failing identity remains. Six additional failing identities
are explained by expanded coverage: three registered motor-overlap profiles
and three load-flow tests absent from the retained baseline reports. The latter
reproduce in isolation with both installed core dependency versions; they are
not established as converter regressions. Their expected values were not changed.

The new converter memory tests pass in this full run. Nevertheless, the branch
is not ready to merge: the complete suite and several independent model
qualification gates remain unresolved. No main-branch merge was performed.

Complete executed reports and failure-identity comparison are retained at
`verification-artifacts/full-accepted-memory-20261104`, archive SHA-256
`ff487b2ba024a62abee322468a9c30e11abf4f24582c5dd481fa69b964cf13e7`.
The same directory retains separate isolated load-flow controls.

## Subsequent lifecycle checks

At `799ee00e3`, 19 converter lifecycle/network tests pass without failures,
errors or skips. The additional controls cover a distinct second event at the
same timestamp and active high-voltage correction rollback. They confirm that
the last accepted terminal voltage is retained without advancing saved history
twice. No production equation changed. Active high-voltage reference accuracy
remains a separate, incomplete gate.

Private integrated evidence is retained at
`verification-artifacts/active-memory-boundaries-integrated-20261104`, SHA-256
`f10a784d0a802ebf1053404de32d43d419eff2db85bcab344060876f1b2ca4b0`.

The independently repeated [URGS3T reactivation captures](../benchmarks/urgs3t-reactivation-lifecycle-2026-11-04.md)
also pass their complete source-contract checker after integration. They prove
inactive coordinate advancement and retained-state reactivation in the reference,
not implementation parity. Engine lifecycle implementation and both-method
trajectory qualification remain required.

The subsequent measured-branch converter capture adds receiving-end phasors
and independent branch P/Q while preserving every original channel exactly.
Its source-contract check establishes that published converter output is not
a joint network-residual certificate; removing the final evaluation is not
justified by the output discrepancy alone. The integrated selection passes
21 tests without failures, errors or skips. Correction-channel trajectory
qualification remains open under both methods.

Private evidence is retained at
`verification-artifacts/active-memory-phasor-integrated-20261104`, SHA-256
`83e8e6a9fd83667ce7ceb6e041ca3fb64c1a527306905cbd69031e42fc035c0b`.

The weighting extension passes 23 integrated contract/regression tests with no
failures, errors or skips. This does not turn the separate acceleration-0.4
coupled diagnostics into passing trajectories: both methods exceed the unchanged
50-iteration cap. Complete traces identify slow contraction of the coupled
voltage/correction iteration. An exact scalar reduction also exhibits multiple
admissible fixed points, so residual certification alone cannot establish that
acceleration preserves the history-selected correction. No production mixing,
parameter, tolerance or iteration-limit change was made.

Private integrated weighting evidence is retained at
`verification-artifacts/converter-weighting-integrated-20261104`, SHA-256
`14a7c16dc5b15eadcee55684954d1ff5eb48f50b1bd3933dc1f457522b3fe130`.

## Experimental reactivation integration

The opt-in retained-state lifecycle is integrated under both solvers. Its
current eligibility is explicitly limited to one selected fixed-excitation
round-rotor/URGS3T host with controller-free classical surviving sources.
Broader composed-network event preparation and rollback remain unfinished.
Ordinary cold energization and default disconnected behavior are unchanged.

The registered integrated selection executes 76 tests: 65 pass and 11 fail,
with no errors or skips. Seven failures are existing trip-state identities;
four are newly registered reactivation state gates. No prior failure identity
in this selection is resolved or replaced. Physical V/P/Q and rotor-angle
gates pass, but full trajectory qualification is not claimed. Test visibility
was corrected after the initial suite-registration compilation failure.

Evidence is retained at `verification-artifacts/urgs-reactivation-integrated-20261104`,
SHA-256 `f99103efe9ba31d8cbac081348a34f0e6eacbc24844f23770c66ac9558624827`.

## Isolated core converter candidate

Core commit `f5d6ec07b` remains on its isolated feature branch. Its full core
suite reports 3,801 tests with 16 unchanged skips and no failures/errors; all
baseline test identities remain covered. Four independent converter controls
also pass after plugin integration using explicit artifact
`1.4.8-lcc-gamma-final`. Neither core master nor the plugin's default dependency
has been changed. The new reference test is registered for combined validation
with the candidate; the baseline core does not satisfy these new gates.

The standard-converter correction does not implement capacitor commutation or
close the original full-sample failures. Those expected values remain unchanged.
See the [qualification report](../benchmarks/lcc-gamma-limit-control-2026-11-04.md).
Integrated evidence with the actual loaded artifact hash is retained at
`verification-artifacts/lcc-gamma-candidate-integrated-20261104`, SHA-256
`01c9f1a9c8e6b51e69f0d1f18ea528fd089e911161bd531ef15063f2b979907d`.
