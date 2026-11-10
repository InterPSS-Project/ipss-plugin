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
