# WT3E1 shared integration states

WT3E1 exposes its existing ten numerical coordinates through
`StaggeredEulerParticipant` and `IntegrationStateAccess`. Ordinary modified Euler
and the shared interface use the same derivative equations and limit branches.
The shared solver owns its integration buffers and two-step derivative history;
the controller does not introduce a second equation implementation.

## State and stage contract

- `readStaggeredState` copies coordinates in the published named-state order.
- `evaluateStaggered` evaluates the existing RHS and refreshes algebraic commands
  using the current terminal voltage. It never advances a state or accepted
  history. Repeated evaluation is permitted.
- `applyStaggeredState` validates the complete candidate before publishing its
  commands and coordinates. The surrounding solver supplies trial or restored
  states; no controller timer or discrete-history commit is required.
- An outstanding ordinary predictor excludes shared evaluation/application.
  Ordinary correctors must match the predictor interval. `cancelPrediction`
  restores the original state and commands; initialization clears pending stages.
- Diagnostic snapshots retain the states at the last rate evaluation and those
  rates. Reading diagnostics does not run the equations. The ten named-state
  keys remain unchanged.

The speed-reference curve is prepared once from immutable parameters. Its
interpolation and floating-point operation order are unchanged. Existing state
and derivative records remain; this slice does not claim zero allocation.

## Validation boundary

`Wt3e1SharedStateTest` checks shared Heun against ordinary modified Euler across
all reactive and voltage-limit selector combinations, repeated read-only rates,
independent sensor and limiter equations, invalid-candidate rejection, stage
ownership, cancellation and reinitialization. A two-step constant-input sensor
control is checked against the analytic exponential response.

The existing independent WT3E1 fault SMIB remains a modified-Euler checkpoint
regression with its original tolerances. It is not a new full-trajectory
qualification. Full coupled staggered operation requires the converter host to
register the electrical controller before evaluating converter commands and to
preserve mechanical-input ordering. That coupled attachment and independent
both-method trajectory qualification remain separate work.

This generic WT3E1 interface does not implement or qualify the legacy GEWTE2
model. Any explicit generic conversion must retain that distinction.
