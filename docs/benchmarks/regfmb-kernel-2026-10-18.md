# REGFMB1U fourteen-state kernel and native replay

Status: typed kernel with partial native verification; not a loadable generator
device. Baseline `d8c6982b8`. Approved native renewable loadability stays 22/27.
The [foundation](regfmb-native-contract-2026-10-18.md) records exact native schema,
source hashes, diagrams and original eight cold runs. No guessed aliases are added.

## Implementation

Regfmb1Kernel implements fourteen states: power-frequency droop filter, bounded
VSM speed, scaled damping filter, bounded relative internal angle, P measurement,
PLL integral/angle, d-axis current measurement, Q/V measurements, q-axis current
measurement, bounded voltage integral and upper/lower current-angle integrators.
Initialization derives from terminal phasor/current on machine MVA base. The
kernel has selector/reference routing, zero-time bypasses, modified-Euler stages,
pure observations, immutable semantic named states and restart/history clearing.
The preceding algebraic boundary handles physical current limiting. No existing
model or CML solver changes. There is no DynamicGenDevice adapter yet.

## Native discriminators

Twenty-two additional native runs form eleven byte-identical cold pairs. Nine
datasets cover current-droop voltage input, power-droop voltage input and storage
with PLL-selected frequency feedback/P-priority. Each group has a flat profile
and fault profiles at 0.5/0.25 ms. Two further datasets excite the PLL before a
low-voltage fault. All raw history and duplicate event sides remain preserved.
The latter two prove the PLL branch only, not full low-voltage trajectory parity.

Three exact-version details have permanent independent guards:

1. The washout stores the low-pass of D2 times speed, not unscaled speed. Equal
   poles and reciprocal-droop/D2 values expose STATE2 = -STATE0 in the baseline.
2. Native current-droop initialization is Vref = Vinv - mq*Iqinv with native
   negative dq reactive current. The plus sign in the printed initialization
   note disagrees with the diagram and measured VAR11.
3. Below VPLLfrz the proportional input is removed: VAR2 equals retained STATE5,
   not the previous total PI output. Nonzero pre-fault PLL output distinguishes
   these rules. Integral/angle then freeze, except native integration-history
   carryover on the first endpoint.

Source fingerprints remain the foundation's native 36.7 ModelLibrary.pdf and
DSMDL.DLL hashes. No vendor dialects, gains or initial states are averaged/fitted.

## Replay and numerical-method boundary

Thirteen full-kernel datasets (four original plus nine selectors) replay only
physical terminal voltage magnitude/angle and P/Q, converted once to machine
base. No native state, PI output or current bound is fed back into the model.
All fourteen states are compared at every nonnegative sample, including both
instantaneous event sides. Initial states derive from physical equilibrium.

Native PSS/E uses STORE/staggered-Euler history, not the kernel's default
modified Euler. A discriminator proves the first disturbed Q filter advances by
3/4 of an Euler increment, versus almost one increment for modified Euler. No
event is dropped or input/state shifted to hide that difference. The initially
too-small development envelope failed at this first event step and is retained.

For the original fault, maximum Pmeas error is 1.30166e-4/6.67428e-5 pu,
Qmeas 0.00163104/0.000836989 pu, Iqmeas 0.00180600/0.000926206 pu and PLL angle
3.34734e-5/1.75982e-5 radians at 0.5/0.25 ms. With PLL-selected droop, Pdroop
error is 4.18013e-4/2.26855e-4 pu. All first eleven states improve at half step
for each fault selector group; voltage-integral/constant-bound precision floors
are separate. Tightened per-state/per-step bounds and logs retain every maximum
and timestamp. These are bounded method-difference comparisons, not exact native
numerical-method or independently coupled system acceptance.

## Gates, artifacts and open work

Final XML audit: 44 focused checks without skips, 283 plugin-core tests (six
optional skips), and 1,318 registered SMIB/model tests (one existing diagnostic
skip), all with zero failures/errors. Seventeen scoped source/resource files
match the isolated tested snapshot; remaining tracked baseline Java/POM files
match HEAD after declared line-ending normalization. All five new native-runtime
checks are confirmed in full-suite XML. Twenty-two new native runs pass the
existing log/CSV quality gate, eleven cold pairs match byte-for-byte, and all
865 files of the preceding raw-reference archive pass SHA-256 checks. Unrelated
untracked WECC240/JAX work remains excluded. No full Texas2k dynamics are run.
Core 1.4.3 SHA-256:
`f293ca958f363b814681f76bf310447d826fb5996e3bacb95aa5789a6a4b33e7`.
The dependency fingerprint matches before and after the gates.
Private archive: `testData/private/verification-artifacts/2026-10-18-regfmb-kernel`.

Seven unit methods cover equilibrium across 48 selector combinations,
predictor/corrector equations, observation/history purity, bypasses, large-input
bounds, restart/invalid inputs and excited PLL freezing. Independent native
coverage is narrower. FFlag-off dynamics, nonzero voltage proportional gain,
active angle/PI limits, angle fallback edge, negative power, zero-time native
storage, PLL recovery/history and plant-input profiles need expansion. Implement
strict native wrapper parsing, a device adapter, plant reference routing and
coupled flat/fault/trip checks before changing the family to LOADABLE/accepted.
