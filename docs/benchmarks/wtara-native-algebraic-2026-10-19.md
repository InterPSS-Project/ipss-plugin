# WTARA1 algebraic boundaries and partial native reference evidence

## Equation and implementation scope

The source contract has two constants (Ka and initial pitch in degrees), no
integrated states, and one constant VAR for initialized mechanical power.
The installed source, section 25.3, PDF page 912/printed 900, was visually
reviewed; its document SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The official [PowerWorld diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_PDF/Generator/Others/Aerodynamic%20Model%20WTGAR_A.pdf)
independently confirms the product structure:

```text
Pm = Pm0 − Ka × theta × (theta − theta0)
aerodynamic_torque = Pm / turbine_speed
```

Angles here are degrees, not radians. This is not a squared pitch deviation.
There is no undocumented zero-power clamp. The power-domain InterPSS model
already implements this equation; its shared mechanical boundary performs
the speed division. No physical equation or integration method changes.
The [ANDES source](https://github.com/CURENT/andes/blob/master/andes/models/renewable/wtara1.py)
has its own radians-based pitch service and interface conventions; the old
bounded coupled benchmark is not silently promoted to native conformance.

The production correction rejects NaN/infinite constants, initial mechanical
power and pitch input before changing outputs. Two new tests fail before
those checks. A read-only initial-power accessor exposes the physical input,
not an invented dynamic state. Existing class names and historical Java files
are preserved under the wording-policy exceptions.

## Cold reference matrix

`python tools/wtara_native_probe.py nonzero coarse a` is an example command
with the native reference runtime configured. Eight profiles at 0.5/0.25 ms,
each twice cold, cover flat, a three-cycle fault, pitch steps/return,
nonzero initial pitch with excursions above/below it, doubled gain, negative
mechanical power, nonunit speed and zero gain. The two constants read back
unchanged after initialization, and native allocation is exactly 2 CON,
0 STATE and 1 VAR. All 32 runs complete initialization/convergence checks;
all sixteen CSV pairs match exactly. Fixtures retain exact runnable RAW/DYR
inputs and every raw sample of all 22 channels.

PowerWorld's [validation page](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Aerodynamic%20Model%20WTGAR_A.htm)
rejects zero Ka. The native zero-gain profile is therefore evidence for the
native convention, not a claim that PowerWorld accepts that parameter set.

## Input-driver and observation contract

The aerodynamic model has no states to force. A zero-gain upstream pitch
fixture supplies declared input steps at nominal 0.15, 0.25 and 0.35 s.
Its output/constant-control states and integration memory are changed through
the documented response-test API. No aerodynamic, converter or drive-train
state is overwritten. The first attempt incorrectly reset the driver's
integration memory to zero, collapsing the imposed pitch; it was canceled
and retained as a failed harness attempt. A separate allocation diagnostic
records the API's unused-state response code rather than hiding it.

Direct aerodynamic torque, pitch and turbine-speed channels avoid treating
the downstream mechanical-power VAR as a simultaneous aerodynamic output.
At each zero-time input-change row, the pitch display changes immediately,
but aerodynamic torque remains exactly the preceding cached value. Replay
therefore performs no new algebraic evaluation on that row. A permanent guard
requires the native torque to be bit-identical to its preceding value;
the next positive-time sample evaluates the changed pitch. All rows remain
scored and labeled as evaluated/cached; none is dropped or shifted in time.
The earlier incomplete-boundary-channel attempt is retained separately.

Replay initialization derives physical Pm0 from exported initial Pe, turbine
speed and documented mechanical damping, and checks the constant VAR. In
the nonunit-speed case Pm0 is about 0.23264 pu rather than electrical Pe=0.24.
The test compares both power and torque on every raw row. It distinguishes
the degree/product equation from squared-deviation and radians alternatives,
and verifies negative power without adding a clamp. Float-level residuals
have profile-specific bounds; no parameter, curve or state is fitted. Maximum
observed errors are 5.44358e-8 pu power and 5.47668e-8 pu torque. Neither
channel is hidden by a single broad whole-stack tolerance.

## Coupled scope and remaining work

Sixteen strict seven-record imports use the full small-network solver and
the declared upstream pitch drivers. All-row diagnostic traces compare three
bus voltages, P/Q, generator speed, pitch, aerodynamic power and torque.
Their interpolation is explicit diagnostic alignment, not exact
instantaneous event-side acceptance. The retained diagnostics include up to
9 degrees of input-display discrepancy, about 0.0054 pu power/torque
differences, and 0.0571 pu fault-event voltage discrepancy. These are not
accepted by the much tighter algebraic replay bounds. The existing fault-event-side,
torque-curve and whole-stack differences remain open. PowerWorld execution,
additional base/lifecycle profiles and complete coupled parity are still
required before complete-family acceptance.

The generated inventory records sixteen native-partial, three bounded-ANDES
and eight native-recorded families. None is marked VERIFIED. No full Texas2k
dynamic simulation is required or run in this slice.

## TODO and validation

Final gates pass 53 focused tests, 410 plugin-core units and 1,656 scoped
SMIB/model/solver tests, with zero failures/errors. Six plugin optional tests
and one existing SMIB recovery diagnostic remain skipped. Four offline probe
guards and six wording-policy checks pass. The isolated build audits 1,823
unchanged Java/build files and eight scoped Java files, without unrelated
untracked sources. Core dependency SHA-256 remains
`50569fa3d1c9972229478b40759b45358c38f615c358dc3be8889e3e8ab8854c`;
the earlier frozen dependency cache is unchanged.

- [x] Preserve two failing-before-fix finite-input guards.
- [x] Preserve 32 cold runs and sixteen exact pairs with all raw samples.
- [x] Compare direct power/torque and verify cached API-boundary semantics.
- [x] Keep unaccepted whole-stack comparisons separate from algebraic acceptance.
- [x] Finish final focused, plugin-core and scoped SMIB regression gates.
- [x] Seal 1,456 source/reference/report evidence files in the ignored private
  archive and include the implementation, tests, public fixtures and document
  updates in one scoped commit. Reverify the inventory after this status refresh.
- [ ] Finish independent PowerWorld and complete-family coupled/lifecycle profiles.
