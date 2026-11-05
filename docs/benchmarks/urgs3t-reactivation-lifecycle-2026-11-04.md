# URGS3T machine reactivation lifecycle

The independent synthetic reactivation capture establishes an engine lifecycle
requirement, not a governor-equation correction. The source generator guide
explicitly permits tripping by unit status zero and reclosing by returning status
one; the round-rotor generator uses this same convention. Exact source provenance
and the relevant pages are retained privately.

The experiment applies a governor reference increment at 0.05 s, trips the host
at 0.15 s, and requests status one at 0.20 s without initialization. A matched
control uses the same run boundaries but leaves the unit offline. Both have
independent cold repeats, complete governor coordinates/variables, generator
coordinates, references, power, excitation, speed, angle, and terminal voltage.
The actual recorded times and event duplicates are preserved.

## Observed contract

- The status request changes neither time nor any allocated governor or machine
  coordinate. It does not initialize the model.
- Offline governor coordinates continue with stored derivatives while physical
  current and mechanical power are zero. The first inactive advance uses
  `h * (1.5 * f_last - 0.5 * f_previous)`; subsequent advances use `h * f_last`.
  The maximum reconstructed coordinate increment residual is 4.151e-8 pu,
  consistent with stored-coordinate rounding. This distinguishes the first-step
  history from a simple frozen state or constant-rate advance throughout.
- The governor reference becomes zero during the trip and stays zero after
  reactivation. Fixed excitation also stays zero. Neither should be silently
  restored to its initial value.
- First resumed mechanical power is 0.554718614 pu, the retained engine state,
  rather than the held pretrip output 0.530936182 pu. Reconstructed terminal
  current magnitude is 0.509394711 pu. The offline control remains at zero.
- The machine's internal rotor and electromagnetic coordinates also advance
  while disconnected. Matching governor coordinates alone cannot qualify the
  resumed network trajectory.

All defined channels are identical between cold repeats. The fixture has no
voltage regulator: its unowned VREF channel has a 3.31e-38 cold difference in the
control pair. Those raw samples remain retained and are explicitly reported;
VREF is not used to infer an excitation-controller state.

## Engine contract

1. Add an explicit, opt-in disconnected-state lifecycle to prepared machine and
   controller participants. On trip, retain accepted coordinates, derivative
   history, and previous active status before suppressing network injections.
   Do not apply this behavior indiscriminately to all inactive dynamic devices.
2. Advance opted-in inactive coordinates exactly once per accepted network
   interval without evaluating live equations, refreshing measurements, updating
   limiter/deadband memory, or publishing nonzero physical output. Rejected trials
   and zero-time network/event callbacks must not advance them.
3. In `PsseStaggeredEulerSolver`, preserve the last two accepted derivatives and
   the first-disconnected-step marker. The observed recurrence above supplies a
   direct independent control. Keep this history separate from event-left rate
   refresh of active devices.
4. In the default modified-Euler path (`DefaultDynamicSimulationSolver` and its
   `PsseDStabSolver` base), add the same accepted-interval lifecycle dispatch.
   Decide explicitly whether the disconnected source compatibility contract
   uses the observed retained-rate recurrence for both methods. Ordinary active
   modified-Euler equations and predictor/corrector behavior must remain intact;
   the source samples do not establish a different modified-Euler recurrence.
5. On reactivation, keep retained coordinates, preserve the observed reference
   and fixed-excitation settings, refresh accepted physical outputs and network
   injection at the event boundary, then resume normal derivative evaluation.
   Rebuild network stamps/injections as needed without running initialization.
6. Qualify both methods with full boundary and state trajectories, first resumed
   current/output, repeated reads, a rejected trial/retry, zero-time callbacks,
   and the matched permanently-offline control. Keep the existing active-run
   accuracy gates unchanged; existing command-step discrepancies remain separate.

## Opt-in implementation and limitations

The plugin now provides `setHotReactivationMachineIds(Set<String>)` on both
solver entrypoints, configured before initialization. Select exact machine IDs.
An empty set preserves the existing inactive behavior. Eligibility is restricted
to prepared, active round-rotor machines with a source-record URGS3T governor,
zero speed/power deadbands, and no exciter or stabilizer. Only one selected host
is accepted, with controller-free classical surviving sources and no active bus
or branch dynamic devices. Unknown, duplicate, and
unsupported requested owners are rejected before initialization processing.

`reactivateMachineAtAcceptedEndpoint(id, algorithm.getSimuTime())` is a separate
hot API. Ordinary generation energization still initializes its device. The hot
API retains coordinates, rebuilds network stamps, and solves the reconnected
network. A failed solve restores statuses, voltage coordinates, machine scratch,
and the disconnected matrix; it can be retried. A duplicate accepted request at
the same time is inert. Invalid times, unprepared owners, and mid-stage calls are
rejected. The API does not implement arbitrary reconnection of other models. Surviving
classical-source accepted rates are captured before the status change, using the
existing staggered accepted-endpoint protocol; the network uses the existing
INITIAL solve stage. This restricted experimental scope is not a final general
architecture. Network-wide accepted-input transactions and rollback for composed
controllers/bus/branch devices require a separate implementation and independent
qualification before removing the restriction.

`DisconnectedStateParticipant` separates unprojected coordinate access, connected
rate observation, disconnection, and reactivation publication. The prepared
`MachineReactivationLifecycle` stores numerical values in reusable primitive
arrays. Only active accepted endpoints sample rates, with machine scratch and
physical outputs restored afterward. Offline intervals use retained rates and
never call active equations. Inactive state trials are applied after the INITIAL
network solve and before the endpoint solve, committed only after successful
integration, and restored on failure. This source-lifecycle AB2-first/constant-rate
policy is deliberately shared by both methods; it is not described as ordinary
modified-Euler integration. Active integration formulas remain unchanged.

Reactivation has two distinct observations: the event-side mechanical power is
the prior accepted output (0.530936182 pu); the following integration step exposes
the retained engine output (0.554718614 pu). Offline observable variables remain
held while allocated coordinates advance. Reconnection releases that hold.

Full independent trajectory gates remain strict. All four method/control runs
pass voltage and P/Q range-NRMSE below 0.001 and rotor-angle RMSE below 0.01 deg.
Three full controller-coordinate comparisons still fail; the staggered reactivation comparison now passes:

| Method | Reactivation maximum valve error | Offline-control maximum valve error |
|---|---:|---:|
| Modified Euler | 9.059e-4 pu | 2.475e-4 pu |
| Staggered | 2.294e-6 pu | 1.713e-5 pu |

The staggered offline engine and average coordinates also exceed 5e-6 pu
(1.323e-5 and 5.600e-6 pu). No thresholds, samples, or channels were removed.
These state discrepancies remain qualification failures, separate from passing
physical boundary and atomic-lifecycle controls. The option is experimental and
is not enabled by default.

The original focused test set contained 36 passing lifecycle/default-off/shared-state
controls and four failing full-reference comparisons. The subsequent trip-order
validation ran 22 tests: 19 passed and three strict reference comparisons remain
failing. No reference gate was relaxed. It covers the
first inactive recurrence, later constant increments, zero/rejected trials,
invalid/mid-stage requests, a deliberately failed reclose with voltage mutation,
exact rollback of physical statuses and matrix action, retry, duplicate requests,
and unchanged default frozen-trip behavior.

## Trip publication diagnosis

The test harness previously published the next accepted governor output before
tripping its primary host. The source sequence first finishes the pre-trip run,
then trips that host; the offline model does not participate in resumed-run
publication. Publication for surviving hosts and ordinary run boundaries remains
unchanged. This is a harness ordering correction, not a swing/governor equation
change or substitution of a predictor scratch derivative for an accepted rate.

In the staggered control, premature publication changes mechanical power from
0.530698356 to 0.530936007 pu before the held mechanical rate is sampled. The
resulting speed derivative changes from 0.002137305 to 0.002155870 pu/s. An
independent negative control checks that the difference is exactly
`deltaPm / (speed * 2H)` and that the outage speed difference is the corresponding
first AB2 plus 99 constant-rate advances. Both methods satisfy these identities
to 3e-14. The supplied source's observed held derivative is 0.002137254 pu/s.

With the correct trip order, the staggered reactivation trajectory's worst valve
error falls from 8.605e-6 to 2.294e-6 pu, and its worst speed error falls to
2.605e-8 pu. All unchanged state and physical trajectory gates pass for this run.

`tools/audit_urgs3t_reactivation_states.py` separates pre-trip, disconnected and
post-request errors and independently replays the identity-curve fixture using
its recorded speed/reference inputs and unchanged constants:

- Seeding the recorded reactivation coordinates gives a staggered post-request
  valve error of 1.636e-7 pu. Including the unrounded disconnected interval gives
  2.405e-6 pu. No fitted gain, time shift, or state rounding is used.
- The permanently-offline binary64 replay, initialized from source rows, still
  accumulates valve/engine/average differences of 1.696e-5, 1.306e-5 and 5.366e-6
  pu. This reproduces most of the remaining offline discrepancy independently
  of the Java controller or network. It is consistent with accumulated stored
  coordinate rounding; these strict reference failures remain visible.
- Prescribed accepted-input modified-Euler replay still has a 9.146e-4 pu valve
  difference at reactivation, while the staggered replay is below 2e-7 pu there.
  The large first resumed-step discrepancy therefore persists with supplied
  boundary inputs and is a finite-step integration-method difference. The earlier
  modified-Euler command-step failure also precedes the trip.

These controls do not claim complete machine-state or global solver equivalence.
They identify and fix the premature publication, while keeping unresolved method
and numerical agreement requirements separate.

## Reproduction

Run `python tools/check_urgs3t_reactivation_contract.py <extracted-capture-root>`.
The checker verifies every manifest dependency, both cold pairs, unchanged
coordinates at the status request, all inactive increments, preserved duplicate
boundaries, resumed mechanical power, and the offline control.

Retained private archive:
`verification-artifacts/urgs3t-reactivation-contract-20261104/captures.zip`
(802,378 bytes), SHA-256
`e2508035a2f7d912f6596cdc71849d986b79e1189efe7bf5956402ffdda24e18`.
It contains all four complete 1,006-row captures and their acquisition manifests,
logs, inputs, source-page provenance, and audit. Earlier captures without the
reference channels remain retained as acquisition controls.
