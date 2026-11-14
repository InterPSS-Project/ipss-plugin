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

The speed-reference curve is prepared once from immutable parameters. The
curve is used for shaft initialization and during every integration mode.

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

## Bypassed voltage-control reference initialization

When terminal-voltage control is bypassed, the Vref integrator directly supplies
the converter's Eq command. It now starts at the solved converter Eq, preserving
the initial current and its steady command. For enabled voltage control, its
existing terminal-voltage initial coordinate remains unchanged. Both branches
have explicit initialization tests, and the private converted-model reference
checks the bypass coordinate against its recorded Vref state.

The complete generic converted-suite voltage and power comparison passes its
unchanged strict gates under both numerical methods after this and the
separate WT3G2 terminal-angle selection. The
[before/after evidence](legacy-ge-import-verification.md#converted-terminal-angle-and-bypass-reference-correction)
retains all samples, event sides and controller states. This does not qualify
legacy electrical-controller equations.


## Configured outer voltage measurement

WT3E1 resolves an entered nonzero remote bus when the controller initializes.
Its initial voltage reference and outer voltage-sensor input use the voltage
at that bus. The Mvar-to-voltage integrator, the inner voltage-error equation
and the conversion of the active-power order to current use terminal voltage.
Local compensation remains in the local sensor path. A missing configured
bus is reported at initialization.

The two-bus reference discriminator varied the remote and terminal voltage
during a fault while retaining both measured channels. The initial outer
sensor matched the configured remote bus and the inner integrator matched
the machine terminal. Selecting local regulation moved the outer sensor to
the terminal. Changing the local compensation parameter with remote
regulation left the reference controller states and electrical trajectories
unchanged. The focused regression varies the remote voltage while holding
terminal voltage fixed. It fails on the old implementation, and the
existing local-control and integration tests pass after the correction.

## Full-speed pickup and rated-power interpolation

WT3E1 forms its speed reference from electrical power on the online plant
rating. Below the entered full-speed pickup Pwp, the final linear segment
runs from the speed at 60% rated power to the speed at 100% rated power.
At and above Pwp, the reference is set to the full-speed value. In the
previous implementation Pwp was also the endpoint of the interpolation,
which raised the speed reference early and changed the initial shaft
speed and torque command.

A reference-simulator dispatch sweep exercises this boundary with three Pwp
values. A 10-second component replay with the native terminal voltage as
input verifies the shaft speed on the original operating point. The
focused test verifies initialization on both sides of Pwp and in the
lower segments using its own speed-curve data.
