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

## Minimal engine work required next

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

No runtime semantics were changed by this evidence commit. In particular, the
existing frozen-disconnected-state implementation is not claimed to reproduce
hot reactivation. Implementing the lifecycle deliberately is the next task.

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
