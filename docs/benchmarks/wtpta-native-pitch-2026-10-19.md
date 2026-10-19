# WTPTA1 native pitch equations, input ports and history evidence

## Native contract and corrected gaps

The native record has ten constants, three states (pitch, speed integral,
compensation integral) and one plant-reference VAR. Angles/rates are degrees.
The installed source, section 24.3, PDF pages 902–903/printed 890–891, was
visually reviewed. Its document SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The [PowerWorld expanded diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_PDF/Generator/Stabilizer/Stabilizer%20WTGPT_A.pdf)
was also visually inspected. Its explicit PI-output bounds must not silently
be combined with the native-A implementation's raw PI sums. Independent
PowerWorld execution remains a separate acceptance task.

The previous native reader selected the legacy typed realization, which
froze integral motion based on the complete PI output, clipped individual
PI outputs and clipped the summed command before the pitch servo. The wind
coordinator also supplied torque-controller electrical reference as the pitch
power reference, and retained an initialized rather than moving speed reference.

The new explicit native-record path uses:

```text
power_error = electrical_power_order − plant_power_reference
speed_error = turbine_speed − torque_speed_reference + Kcc × power_error
speed_PI = speed_integral + Kpw × speed_error
compensation_PI = compensation_integral + Kpc × power_error
pitch_rate = limit((speed_PI + compensation_PI − pitch) / Tp, rate_min, rate_max)
```

Integral storage has its own declared bounds. Pitch-angle cross anti-windup
blocks only outward integral/angle motion and permits inward release. The
individual PI sums and their combined command remain raw. At exactly zero
Tp, pitch is an angle-limited algebraic output, not a rate-limited state.
The native state interface preserves pitch/speed-integral/compensation order;
existing named-state keys remain unchanged. Only two coordinates are active
integration states when pitch is algebraic.

## Shared implementation without conflating revisions

The existing revision-B engine is reused with an explicit native-A selector
and an immutable parameter view. Revision B keeps its original PI-output
clipping, validation, state behavior and public data record. Native A keeps
the exact ten-value input, including literal zero rate values without invented
replacement constants; further native zero-bound profiles remain open. An attempted unmodified revision-B substitution fails
the independent native-A reference: this counterexample remains permanent.

The direct typed constructor preserves its historical behavior. Historical
ANDES and PowerWorld diagnostic tests explicitly retain that pitch convention
and their original parameters/bounds. Those checks do not establish native-A
conformance. The native builder selects `fromNativeRecord`, and the coordinator
passes independent plant and moving speed-reference inputs at both staged
endpoints and in the opt-in history path. Its native complete-step API uses
the same staged coordinator. Default modified Euler is not replaced.

Optional observation hooks report actual evaluation states and retained
history rates. Tests cover pure immutable observations, reinitialization,
zero-duration refresh, native-state order, PI versus output bounds,
outward freeze/inward release, both input endpoints and event rebasing.
Finite parameter guards do not modify valid constants or expand bounds.

## Independent matrix and failed attempts

`python tools/wtpta_native_probe.py order-upper coarse a` is an example with
the configured native runtime. Ten profiles at 0.5/0.25 ms, each twice cold,
give forty completed runs and twenty byte-identical CSV pairs. They cover
flat/fault, upper/lower power-order inputs, a narrow upper angle bound,
initial lower angle, active rate bounds, moving speed reference, zero lag,
and zero lag combined with a 1000-fold smaller rate bound. All ten entered
constants read back unchanged as their exact native binary32 values.
All 24 channels and every history, fault-side and API row are retained.
Fixed-digest fixtures contain complete CSVs and runnable RAW/DYR inputs.

The failed decimal-readback attempt is preserved: a fixed 1e-7 decimal check
was inappropriate for native storage of 23.72. The final check is exact
binary32 equality, not a relaxed parameter tolerance. An attempted reference
VAR step was overwritten by native ownership and did not perturb the input;
that canceled batch is retained as a harness diagnostic, not active coverage.
The final order driver changes only the upstream electrical-order filter's
state/history through its response-test API and records the actual input.
No WTPTA1 state is forced or initialized from an observed controller state.

Adding the final zero-lag/rate discriminator changed only the probe's declared
mode list and its parameter-selection branch. The first 36 immutable runs
retain their original frozen probe, and the four new runs retain the new one.
Both source digests and the exact source-only extension are audited; current
configuration and input generation reproduce all forty frozen inputs.

## Equations versus integration history

The before-fix native-path test fails across the original eighteen profiles;
the revision-B substitution also fails. Native conditional derivative
witnesses distinguish raw from clipped PI sums without fitting gains or
injecting controller states. Sixteen final profiles have bounded three-state
staged modified-Euler replay on every raw row, with profile/channel bounds.
Their largest errors are about 4.79e-5 degrees pitch, 2.08e-5 degrees speed
integral and 1.05e-6 degrees compensation integral. Float floors do not all
decrease monotonically under timestep halving.

Four abrupt order-input profiles retain a **modified-Euler method
counterexample**, including approximately 0.00125-degree pitch discrepancies.
These rows are not dropped and their prior 2e-4 provisional threshold is not
widened to accept them. A separate explicit native-precision/two-step replay
uses the existing converter configuration and scores all four profiles with
tighter channel bounds. Initial pitch and speed reference are physical input
values; no recorded pitch/integral state is injected.

Two independent witnesses explain that path. The first native compensation
increment after an input API change is three quarters of the origin-rate
step, not a complete modified-Euler step. On first pitch-angle cross-freeze,
the integral derivative becomes zero but STORE retains the preceding
half-step contribution: the next STATE removes it. Anchoring STORE at STATE
incorrectly retains a 5.82e-4-degree offset in the upper coarse profile.
Selecting native precision alone does not fix it; that failed run is retained.
The native-A cross-freeze correction retains this recurrence while preserving
revision-B behavior. Final upper PI-state errors are below 5.1e-12 and
2.4e-10 degrees; lower-profile pitch errors are at most 6.16e-5 degrees.

The zero-lag trajectory is byte-identical when rate bounds change from ±10
to ±0.01 degrees/s, although native slew exceeds 0.43 degrees/s. This proves
the active rate-bypass branch rather than relying on an inactive limiter.

## Coupled scope and remaining work

All twenty profiles import seven exact records and initialize through the
native builder. Sixteen unforced flat/fault profiles run through the full
small-network solver. Eleven coupled metrics include all three pitch states,
nearby voltages, terminal P/Q, speed and controller input signals. These are
explicit all-row interpolation diagnostics, not complete instantaneous
event-side trajectory acceptance. The four forced-order profiles have exact
import/initialization plus independent input replay, not an unforced coupled
comparison with mismatched physical inputs.

Full coupled event-side parity, independent PowerWorld execution, positive
lower-bound initialization, further base/limit/lifecycle profiles and complete
family acceptance remain open. No new full Texas2k dynamics are run. The
generated inventory now has seventeen native-partial, two bounded-ANDES and
eight recorded-profile families; none is marked VERIFIED.

## Validation TODO

Final gates pass 63 focused, 410 plugin-core and 1,671 scoped SMIB/model/solver
tests with zero failures/errors. Six existing optional plugin tests and one
existing SMIB recovery diagnostic remain skipped. Four offline probe guards
and six language-policy checks pass. The isolated source audit covers 1,821
unchanged Java/build files and thirteen scoped Java files. A concurrent
documentation-only commit is recorded separately; it changes no tested code.
Core dependency SHA-256 is unchanged at
`50569fa3d1c9972229478b40759b45358c38f615c358dc3be8889e3e8ab8854c`.

- [x] Retain failing-before native-A and revision-B-substitution tests.
- [x] Audit forty cold runs, twenty exact pairs, all raw rows and both probe versions.
- [x] Verify sixteen staged and four explicit native-history state profiles.
- [x] Retain all four modified-Euler event-history counterexamples.
- [x] Verify source-backed limiter, zero-time and cross-freeze history witnesses.
- [x] Pass 63 focused tests and 410 plugin-core units (six optional skips).
- [x] Finish the complete scoped SMIB/model/solver regression gate.
- [x] Seal 1,703 source/reference/report evidence files in the ignored private
  archive and include implementation, tests, public fixtures and documentation
  in one scoped commit. Reverify hashes after this final status refresh.
- [ ] Close complete-family coupled/PowerWorld and remaining initialization/lifecycle profiles.
