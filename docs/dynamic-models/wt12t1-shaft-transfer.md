# WT12T1 generator-side shaft transfer

The two-mass generator equation needs the speed normalization observed in the
reference implementation. Define actual per-unit speeds `wt` and `wg` and
`Tshaft = Kshaft * phi + Dshaft * (wt - wg)`. The verified boundary is:

```text
dwt/dt = (Paero/wt - Tshaft) / (2*Ht)
dwg/dt = (Tshaft*wg/wt - Pe/wg - DAMP*(wg-1)) / (2*Hg)
dphi/dt = omegaBase * (wt-wg)
```

The generator-side `wg/wt` factor was missing. It equals one at equilibrium,
so flat initialization does not expose the error. During a torsional response,
the omission behaves like a different relative-speed contribution. Changing
the entered shaft-damping value would conceal the missing normalization.

The correction applies the factor to the combined spring and shaft-damping
term in the generator equation. It retains the turbine equation, electrical
power-to-torque conversion, machine damping, stiffness, state coordinates and
modified-Euler history. The existing low-speed denominator guard is reused.
The one-mass path is unchanged.

The block diagram was treated as a hypothesis. Independent reference runs
perturbed shaft angle, turbine speed and generator speed in both directions,
used three small steps, repeated the original experiment in a fresh process,
and checked zero/doubled shaft damping and zero machine damping. A second
fixture used different machine and mechanical parameters. The normalization
explains the generator acceleration across these discriminators without
fitting constants, initial states or clocks. All original native samples are
retained, including the state-edit history startup intervals.

The new unequal-speed predictor regression fails on the preceding equation.
All 22 focused parser, initialization, integration-stage and independent
wind/motor trajectory checks pass after correction without relaxing limits.
Ten-second component fault comparisons at identical event times show step
convergence after the correction. This qualifies the tested equation boundary;
full-system fault trajectories and unqualified mechanical/angle observation
mappings require their separate acceptance checks. No catalog-wide VERIFIED
status is inferred.

## Uncontrolled turbine torque and mechanical-power output

For WT1G1 and WT2G1 with WT12T1, the reference simulator holds the initialized
aerodynamic torque when no aerodynamic controller is attached. If `Pa0`
and `wt0` are the initialized power and speed, the held input is
`Ta0 = Pa0 / wt0`. The instantaneous aerodynamic power is
`Ta0 * wt`. An attached aerodynamic controller or an explicit power
input supplies power directly. The two modes share the same stage
integration and do not advance any additional controller state.

The native PMEC output is `(Kshaft * phi + Dshaft * (wt - wg)) * wg`
in two-mass mode and the aerodynamic input power in one-mass mode.
The machine publishes this value after shaft initialization and after
each mechanical stage. The two-mass output uses the physical shaft
torque before the generator equation applies its speed normalization.
WT1G1 and WT2G1 without a drive train retain their initialized mechanical
power. No load-flow or entered dynamic constants are modified.

Native source-matched Type 1 and independent Type 2 traces confirm the
shaft power formula within 1.56e-6 machine pu. A native one-mass case
matches its aerodynamic power channel exactly. For the uncontrolled
turbine, measured aerodynamic torque stays constant within 1.30e-7 pu
in these probes while aerodynamic power varies with turbine speed.

## GEWT2MU1 generator shaft torque

GEWT2MU1 uses the shared mechanical integrator with the generator-side
shaft speed normalization disabled. An initialized native GE fixture
with a synthetic two-mass allocation was perturbed in both directions
at the shaft angle and both speeds. At the 20-microsecond step, the RMS
generator torque-equation residual is 2.98e-4 pu for direct shaft torque
and 4.92e-3 pu for the normalized WT12T1 equation. The smaller-step
probes agree on the choice within the resolution of native states.
WT12T1 retains its independently verified speed ratio. GEWT2MU1's
explicit aerodynamic input remains a power input.
