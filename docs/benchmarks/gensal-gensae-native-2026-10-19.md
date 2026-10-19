# Salient-pole native models: distinct saturation and five-state SMIB evidence

## Corrected runtime contract

The former reader treated GENSAE as an exact GENSAL alias. Native source PDF
pages 58-59 instead specify quadratic saturation on the d-axis only for
GENSAL, and exponential saturation on both axes for GENSAE. Both have twelve
constants and five states, but their native allocations swap the two flux
coordinates. The new identifier discriminator fails before the correction.

GENSAL retains the existing core salient-pole electrical solver unchanged.
GENSAE uses a three-electrical-coordinate adapter in `GensaeMachine`, retaining
core mechanical integration, machine/system-base conversion, Norton admittance
and voltage-compensation interface. It does not add an artificial fast state
or choose a small fictitious time constant to imitate a lower-order model.
The typed `addGensal` default is unchanged; `addGensae` and the native source
identifier select the new realization explicitly.

The [GENSAE reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENSAE.htm)
independently distinguishes the saturation function. The
[additive-saturation diagram, PDF page 6](https://www.powerworld.com/files/GENROU-GENSAL-GENTPF-GENTPJ.pdf)
identifies subtransient-flux magnitude and the q/d mutual-reactance ratio.
Native allocation/state semantics are taken from the native source library,
not inferred from an unrelated tool's allocation. Its hash and reviewed pages
are retained privately. The PowerWorld stator-speed convention is not silently
substituted for the native Flux0 network boundary.

## Electrical realization and observation

Let `e = E'q`, `k = psi_kd`, and `q = -core.psi_q''`, the positive native
q-flux coordinate. Define:

```
p = ((Xdpp-Xl)*e + (Xdp-Xdpp)*k)/(Xdp-Xl)
r = e-k-(Xdp-Xl)*Id
s = S1 * hypot(p,q)^(log(S12/S1)/log(1.2))
rho = (Xq-Xl)/(Xd-Xl)
field = e + (Xd-Xdp)*(Id + (Xdp-Xdpp)*r/(Xdp-Xl)^2) + s*p

de/dt = (Efd-field)/Tdop
dk/dt = r/Tdopp
dq/dt = ((Xq-Xqpp)*Iq-(1+s*rho)*q)/Tqopp
```

Zero/disabled saturation removes only the saturation terms. Initialization
derives subtransient-flux magnitude from solved `V+(Ra+j*Xdpp)*I`, then uses
`Xq_effective = Xqpp+(Xq-Xqpp)/(1+s*rho)` to obtain rotor angle. The electrical
states and field voltage are solved backwards from terminal P/Q/V. No state,
gain, reactance or time constant is fitted to a trajectory.

The adapter uses modified-Euler predictor/corrector stages and the core's
atomic network-callback current sample. Sampling current again after rotor
prediction changes the coupling convention and fails the independent
zero-saturation shared-limit guard. At zero saturation, both imported models
now match all eighteen recorded coupled channels through the fault within
1e-11. Separate tests check the explicit predictor and corrector formulas,
invalid stages/steps, reinitialization from a newly solved dispatch and exact
native parameter cardinality.

Both models expose fresh immutable named-state maps. Native q-flux is the
negative of the existing core storage coordinate; integration storage is not
changed for display. GENSAE exposes its three electrical derivatives without
advancing the model. Uninitialized derivative access is rejected explicitly.

## Reference matrix and accepted scope

Sixteen configurations at 0.5 and 0.25 ms give thirty-two native profiles,
each cold-generated twice. Both model families have flat/fault, saturation,
nonzero damping/resistance and 50/100/200 MVA coverage. GENSAE also has native
disabled-saturation flat/fault profiles. All constants are checked against
exact binary32 readback and remain unchanged through initialization/run.

Every raw negative-history and event sample is retained. Five states,
terminal P/Q, field voltage, mechanical power, rotor speed/angle, reference
machine speed/angle and both buses' voltage magnitudes/angles are available.
Power channels and mechanical power retain their distinct system/machine
bases. Flat runs and bounded state comparisons are not complete boundary or
lifecycle acceptance.

All thirty-two cold pairs are raw-exact. Separate fixed bounds cover every
state over every raw row and all seventeen initial channels. The original
twenty initial-profile bounds are unchanged; the twelve added profiles have
their own binary32/solved-network margins. Initial electrical-state errors
remain below 2.18e-7 pu and field-voltage error below 4.45e-7 pu. Native input
constants are binary32, whereas entered decimal values retain double precision
in InterPSS; these small residuals are not removed by changing parameters.

The maximum electrical-state residual decreases from 2.38e-3 to 1.25e-3 pu
under timestep halving. Maximum speed error decreases from 4.05e-6 to 2.18e-6
pu. Every significant electrical/speed residual has a separate convergence
guard; angle errors near native clock/serialization floors are bounded but
not required to decrease uniformly. Complete event-side phasor/power residuals
remain diagnostics, with each channel's maximum and timestamp retained.

The first incorrect leakage-flux realization and the later incorrectly
resampled-current realization are retained as counterexamples, with their
failed regression reports. They were corrected from the flux definition and
shared core stage convention, not by enlarging existing comparison bounds.
Original GENSAL and GENROU independent gates remain unchanged.

## TODO and acceptance limits

- [x] Distinguish the native identifiers, equations and five-state allocation orders.
- [x] Retain the core GENSAL default and reuse core mechanics/Norton infrastructure.
- [x] Prove the disabled-saturation shared limit and both integration stages.
- [x] Record direct native phasor boundaries and preserve failed discriminators.
- [x] Finish the expanded-matrix, per-state, step-halving and source/report integrity gates.
- [ ] Establish complete instantaneous event-side voltage/P/Q/torque acceptance.
- [ ] Add longer multi-swing SMIB recovery and composed exciter/governor profiles,
  including independent field-current normalization checks.
  - [x] Add twenty-two field-current and composed ESST1A profiles, active current
    limiting/ceiling discriminators, bounded states and explicit cached-gate replay.
    See the [follow-up evidence](gensae-field-esst1a-2026-10-19.md).
  - [ ] Extend beyond the short composed-exciter profiles to multi-swing and
    composed-governor/lifecycle coverage.
    - [x] Add eighteen three-second composed-governor profiles with separate
      upper/lower valve activation; retain the newly exposed four-state gaps.
      See [composed recovery](gensae-composed-recovery-2026-10-19.md).
    - [ ] Resolve those coupled-state gaps and add trip/reconnect coverage.
- [ ] Implement GENSAE checkpoint-history reconstruction before advertising it:
  the existing core snapshot captures its concrete built-in electrical solver,
  and the new custom adapter deliberately reports unsupported rather than
  silently reconstructing a different saturation model.
- [ ] Independently verify trip/reconnect and additional saturation regions.
- [ ] Execute the corresponding PowerWorld matrix with its own stator convention.

This slice is not VERIFIED or full-system acceptance. No full Texas2k dynamic
simulations are run. Native artifacts, exact scripts/inputs, dependency and
source hashes, counterexamples and terminal gate reports belong to the private
`2026-10-19-gensal-gensae-native` evidence seal. The broader coverage goal remains open.

Final gates pass 62 focused, 410 plugin-core (six existing optional skips) and
1,743 scoped SMIB/model/solver tests (one existing diagnostic skip), with zero
failures/errors, plus sixteen offline probe and six wording tests. Two fresh
GENROU compatibility runs retain the prior raw CSV hash after extraction of the
shared probe runner. Source reconciliation includes the latest committed
baseline without including unrelated worktree edits.
