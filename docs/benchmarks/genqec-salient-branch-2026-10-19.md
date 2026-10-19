# GENQEC/GENQEJ salient-pole branch evidence

Status: exact branch equations and bounded recorded-input replay accepted for
this slice, not complete machine parity. Private source/run/report hashes are
retained under `2026-10-19-genqec-salient-branch`; the scoped verification commit
is recorded in Git history. No production machine equation is changed.

## Source contract and the rejected projection

The installed generator library describes the salient-pole branch as T'qo=0
and X'q=Xq. T''do and T''qo remain positive in these profiles. Native source
pages 48/51 and 53/55 were read, with the salient diagram visually checked.
The independent [PowerWorld GENQEC reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENQEC.htm)
also describes the zero-q-transient interpretation. This is not proof that
other zero-time or missing-winding configurations have been verified.

The diagram can suggest a q-axis observation coordinate scaled by
(X'q−X''q)/(X'q−Xl). Both native and PowerWorld traces instead report the
existing general coordinate. For the native scaled-quadratic initial point,
Psi'q is approximately 0.61156243 pu, unchanged by removing E'd. Applying the
suggested scaling would change the observation without improving the electrical
equations. That projection was deliberately inserted into the isolated snapshot;
the new raw-reference replay test rejected it. The exact original source was
restored. The runtime and all six named-state keys are retained unchanged.

## Profiles and independent comparisons

Four exact inputs cover GENQEC standard and GENQEJU wrapper records, each with
exponential and scaled-quadratic saturation. The only changes from the prior
profile factory are T'qo=0 and X'q=Xq. Each input runs the same three-cycle
j0.2 system-pu fault at 0.5 and 0.25 ms, cold-generated twice: sixteen native
runs. Every constant, selector, allocation, initialization log and raw sample
is hash-checked. All eighteen channels, including terminal voltage phasors,
six machine states and power/speed boundaries, are retained in 605/1,205 rows.
Negative history and both event duplicates are preserved.

The electrical replay uses the recorded terminal phasor, rotor angle/speed and
field voltage as inputs to the actual machine electrical solver. No substitute
machine equations are used. Four electrical states, saturation factor and P/Q
are independently scored. The two forced mechanical inputs are not claimed as
predicted-state acceptance. Every raw row is scored, including history and both
event sides, against fixed per-profile/channel bounds.

E'd is exactly zero in every native and InterPSS replay sample. The maximum
q-state error over all profiles falls from approximately 0.004863 to 0.002829 pu
on step halving; maximum P/Q errors fall from about 0.014575/0.031304 to
0.009049/0.017755 pu. The first post-fault callback dominates these q-axis and
current/power errors. Some E'q maxima occur at the first clearing callback.
The raw last pre-fault sample does not supply an instantaneous solved fault-post
phasor for the predictor. Step refinement is evidence of a bounded callback
residual, not a proof of second-order convergence or complete event-post parity.
No model parameter, state coordinate or input trace is fitted to remove it.

An independent equation test disables saturation and verifies the q-axis lag's
predictor and corrector separately to 1e-12, keeps E'd zero and checks zero-step
observation/state immutability. Eight imported full-solver profiles additionally
verify stationary pre-fault equilibrium, actual voltage depression, finite
states/power and the removed E'd state throughout the fault. Those coupled runs
are execution/equation evidence, not independent coupled trajectory acceptance.

Eight additional PowerWorld runs cover the two standard GENQEC saturation modes
at both steps and two cold repeats. All eleven channels, six machine states and
603/1,203 raw samples repeat exactly. Its E'd channel remains zero, with no
auto-correction or model overrides requested. These are repeatable diagnostics;
post-initialization parameter round-trip and complete cross-tool trajectory
acceptance remain open. GENQEJ has native references here, not new PowerWorld
trajectory evidence.

## Checklist

- [x] Freeze the exact documented salient branch and preserve native schemas.
- [x] Cold-repeat sixteen phasor/state references and eight commercial diagnostics.
- [x] Preserve eight immutable embedded profiles and seven replay channel bounds.
- [x] Verify all raw electrical samples, exact zero E'd and step refinement.
- [x] Verify unsaturated predictor/corrector, zero-step calls and immutable states.
- [x] Run all eight imported three-cycle fault profiles and pre-fault equilibrium.
- [x] Reject the deliberately incorrect observation projection; restore source.
- [x] Pass eighteen focused checks, 401 plugin-core tests (six optional skips)
  and 1,562 registered SMIB/model/solver checks (one existing diagnostic skip),
  with zero failures/errors. Five offline probe guards and six language-policy
  checks pass.
- [x] Audit all 1,800 unchanged Java/POM baseline files and four owned Java
  sources. Keep the shared machine bytecode identical to the previous slice.
  Preserve source/run/report hashes for the scoped verification commit and
  leave unrelated shared-checkout changes outside the tested/staged slice.
- [ ] Close complete coupled/native/PowerWorld trajectories, exact instantaneous
  event-post inputs, remaining missing-winding/time-zero configurations, larger
  compensation and lifecycle profiles.

An initial local replay attempt located the wrong snapshot directory; its report
is retained separately from the successful embedded-reference test. A native
bootstrap import-order attempt failed before creating a run directory. The
earlier non-phasor discovery run remains exploratory, not a cold-paired reference.
No full Texas2k dynamic simulation is run for this slice.
