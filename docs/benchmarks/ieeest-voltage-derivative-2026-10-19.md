# IEEEST voltage-derivative memory: implementation and open output discrepancy

## Scope and source contract

Mode 6 now selects the voltage derivative at the local terminal bus or an
existing active remote bus. The native two-selector/seventeen-constant schema
is unchanged. Invalid modes and remote buses still reject rather than falling
back to another measurement. The pinned native table defines two VAR slots as
voltage memory and voltage derivative; source hash:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The [PowerWorld diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20IEEEST.htm)
is independent filter-chain context, not authority for the sampled derivative
update or an additional native input field.

Two-step discriminators establish the following observed sampling contract:

```
initialize: memory = selected bus voltage; derivative = 0
accepted endpoint: derivative = 0.8*derivative + 0.2*(voltage-memory)/step
                   memory = voltage
settled algebraic event: memory = voltage; derivative is preserved
```

The coefficient 0.2 is the same at 0.5 and 0.25 ms; it is not converted into a
fitted physical time constant. The controller holds the accepted derivative
during predictor/corrector integration. `DefaultDynamicSimulationSolver` updates it only after
the corrector network converges, before endpoint monitoring. INITIAL event
rebasing occurs after convergence, not after the first linear network iterate.
Direct network solves, trial predictor solves, duplicate accepted clocks and
observer reads cannot advance it. Other solver integrations must provide these
explicit endpoint/rebase callbacks; a voltage-input alias is not equivalent.
The shared integrator and existing five named-state block keys are unchanged.

## Evidence and acceptance boundary

Twelve profiles cover local/remote measurement, flat/three-cycle fault/smooth
reference disturbance, and two timesteps. Twenty-four cold runs converge with
unchanged parameter readbacks; every raw CSV pair is byte-identical. Each CSV
retains all eighteen allocated state channels, both defined memory VARs, bus
phasors/frequencies and boundary outputs: 39 columns and 1,005 or 2,005 rows.
Negative history and duplicate event rows are retained. Direct event API calls
leave states and both VARs unchanged; resumed settled event samples rebase only
the voltage memory. This observation-stage distinction is preserved.

Every voltage sample is replayed without fitting the input. Memory agrees at
1e-12; the largest held-derivative difference is 2.667e-7 pu/s. Per-profile
derivative bounds range from 8e-9 to 3e-7 for active profiles, with 1e-12 for
flat profiles. The raw sampled-recurrence residual is at most 1.054e-7 pu/s.

Smooth-reference Vstab maxima and bounds are:

| Measurement | Coarse maximum / bound | Fine maximum / bound |
|---|---:|---:|
| Local | 1.32291e-3 / 1.46e-3 | 6.95702e-4 / 7.7e-4 |
| Remote | 8.22058e-4 / 9.1e-4 | 4.25071e-4 / 4.7e-4 |

Smooth output errors must fall below 0.55 of the coarse error at the half step.
Flat output remains zero within 1e-12.

**Fault-output parity is not accepted.** Maximum Vstab discrepancies are local
0.0364282/0.0768012 and remote 0.1000001/0.0321308 pu at coarse/fine steps.
No broad output bound is introduced to make these comparisons pass. Full raw
fault playback remains in the diagnostics and fixtures.

## Localization, not a fitted model change

For these fixtures only (A2>0, A4=A5=A6=0), the equivalent observation coordinates
are first-filter `A2*rate+A1*output`, `A2*output`, the algebraic first-filter
output, second-filter output, `T2*leadLag1.storage`, `T4*leadLag2.storage` and
`T6*washout.storage`. Seven native/converted coordinates are recorded on every
raw row. `SecondOrderLagBlock.getRate()` is a pure observer, not a new state.
Other denominator/numerator shapes are not claimed to share these coordinates.

All seven observed-coordinate trajectories have separate per-profile bounds on
every raw row, including fault windows. Each active channel must independently
refine below 0.55 of its coarse error; fine bounds are 0.55 of the corresponding
coarse bound. Worst fault errors across the two measurement locations are:

| Observed coordinate | Coarse maximum | Fine maximum |
|---|---:|---:|
| First filter integrator | 2.95152e-4 | 1.56976e-4 |
| Second filter integrator | 3.602e-6 | 1.794e-6 |
| Algebraic filter slot | 2.11866e-3 | 1.05517e-3 |
| Second filter output | 1.64093e-3 | 8.15267e-4 |
| First lead-lag storage | 3.09756e-4 | 1.53682e-4 |
| Second lead-lag storage | 2.25736e-3 | 1.11791e-3 |
| Terminal storage | 1.24686e-2 | 6.17500e-3 |

These are native-coordinate units, not a common voltage-unit tolerance. This
accepts the declared state envelopes for this shape only; it does not accept
the unresolved clipped fault-output trace.

For example, the first two native coordinates satisfy
`x1' = input-y`, `x2' = x1-A1*y`, `y=x2/A2`, which gives the same published
second-order transfer function as the running realization. Recorded native
state increments closely follow a two-history extrapolated-slope recurrence;
the six coordinate-increment residuals are at most 1.471e-7. The native output
coordinate identity closes within 1.604e-6. These are localization observations,
not proof that numerical staging is the sole remaining cause. The production
integration method and physical block equations are not changed to fit a trace.

## TODO

The final focused gate passes 130 tests; the full plugin-core gate passes 420
tests with six existing skips. Both have zero failures/errors. All 59 offline
checks pass. Source reconciliation covers 1,873 Java/POM files and 44 lossless
fixture parts; older pinned dependency jars remain unchanged. The full scoped
SMIB/model/solver regression passes 1,818 tests with one existing skip and zero
failures/errors. A green regression gate
does not accept the explicitly diagnostic fault-output comparisons above.

- [x] Local/remote mode 6 loading, initialization and sampled derivative memory.
- [x] Predictor/corrector holding, event rebasing, pure/duplicate reads and
  end-to-end settled-endpoint callback tests.
- [x] Twelve cold-exact pairs, complete raw-row memory/derivative playback and
  bounded flat/smooth-output comparisons.
- [x] Seven observed-coordinate channels bounded separately on every raw row,
  with independent step refinement for all active fault/reference profiles.
- [x] Twelve coupled SMIB profiles with per-stage endpoint-recurrence checks
  and retained voltage/speed/memory/output diagnostics.
- [ ] Resolve fault-output numerical-stage discrepancy without widening bounds,
  fitting parameters or replacing the published transfer function.
  The [opt-in staggered boundary](ieeest-staggered-boundary-2026-10-19.md) now
  compares the actual graph under the matched integration rule; the smaller
  residuals remain diagnostic pending independent acceptance criteria.
- [x] The [atomic-corrector follow-up](ieeest-atomic-corrector-2026-10-19.md)
  adds failing simultaneous-vector regressions and fixes the first lag's rate
  correction independently of trace fitting. Fault-output parity remains open.
- [ ] Broader denominator/zero branches, machine bases, lifecycle and complete
  network-coupled trajectory acceptance beyond the current shape/state bounds.
- [ ] Complete model acceptance under the coverage plan.

This is a derivative-memory implementation slice, not a claim that the whole
IEEEST model or its fault trajectories are VERIFIED. Exact source/executable
provenance, raw repeats, pilot runs, failed-before import, reports and all-row
diagnostics are retained in the private `2026-10-19-ieeest-voltage-derivative`
evidence seal. No full Texas2k dynamics are run.
