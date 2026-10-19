# WTTQA1 speed-feedback correction and partial native evidence

## Corrected equation

Speed control must increase electrical torque when generator speed exceeds its
reference. InterPSS had the opposite summing-junction sign. This slice changes
only that error sign in initialization, complete-step evaluation and the shared
staged/two-step derivative/output path:

```text
Tflag=0: error = generator_speed - speed_reference
Tflag=1: error = (plant_reference - filtered_electrical_power) / generator_speed
```

The power branch, physical parameters, curve implementation, current limits,
integration method and existing trajectory tolerances are unchanged. Two old
unit assertions that encoded the reversed sign are corrected. The test name
that attributed a native-selector calculation to another tool is made precise.

The installed source, section 26.1, PDF pages 914-915/printed pages 902-903,
was visually checked using the PDF skill. It defines one ICON, fifteen CONs,
three states and three VARs, with native selector zero for speed control and
one for power control. The source hash is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The [PowerWorld model page](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Pref%20Controller%20WTGTRQ_A.htm)
uses different selector labels and includes additional validation/selection
behavior. Those labels are not used to reinterpret the native ICON or infer
an untested cross-tool mapping.

Three new tests fail before the fix: complete-step feedback, both explicit
stages, and nonunit initialization bias. The existing power branch passes.
The failed report and original compiled test/model classes are retained.
Final guards also distinguish voltage-dip integrator freezing from freezing
the proportional torque output or the electrical-power filter.

## Independent reference matrix

`python tools/wttqa_native_probe.py speed coarse a` is an example command with
the native runtime configured. The public three-bus case uses REGCA1/REECA1,
WTDTA1, WTARA1, a zero-gain WTPTA1 observer and WTTQA1, with no plant-controller
alias. Electrical speed multiplication is explicitly disabled in the reference
input. The native wind operating mode is set through its checked API call.
Output directories are new-only; frozen scripts and dependencies are retained.

Five profiles run at 0.5/0.25 ms, each twice cold: flat, speed control, power
control, nonunit speed curve and a voltage-dip fault. Faults last three cycles
at 60 Hz, from nominal 0.05 to 0.10 s. All twenty runs pass native initial
conditions and convergence checks; all ten CSV pairs match byte-for-byte.
The entered fifteen constants and selector read back unchanged after startup.
Each raw export retains all twenty-three channels, history and event/API rows.
Fixed-digest public fixtures preserve complete outputs and exact RAW/DYR inputs.

Controller replay initializes from physical electrical power and shaft speed,
then uses recorded electrical power, speed, declared plant-reference input
and voltage-dip measurements. It does not inject recorded controller states.
All three torque states and the algebraic curve diagnostic are compared on
every raw row. Getters and named-state reads are pure. The pitch model's
`Pref` VAR is constant in these profiles: it is a plant-reference input, not
an independently exported torque-controller output, and is not mislabeled.

For the coarse speed profile, integrating the published speed error gives a
maximum native-integral residual of about 7.80e-7 pu. Reversing that sign gives
2.17e-5 pu. This independent witness supports the correction rather than only
updating self-consistent unit expectations.

## Accepted profiles and explicit counterexamples

Eight default-curve profiles have bounded three-state/curve replay. The maximum
filtered-power errors are 8.57008e-5/4.41988e-5 pu for the ordinary fault and
5.26784e-4/2.70060e-4 pu for the voltage-dip fault, at coarse/fine steps.
Filtered-power errors approximately halve, but integral float floors do not
converge uniformly: speed-mode maxima are 7.64966e-7/1.77723e-6 pu. These raw
errors remain scored with individual profile/channel bounds, not a fitted
gain, quantized production state or shifted event time.

The two nonunit curve profiles are **not accepted as native state parity**.
Their native curve diagnostic matches the curve of raw electrical power to
within 4.77e-8 pu, while the curve of the native filtered-power state differs
by 0.00868798 pu. The published diagram feeds the curve from filtered power.
InterPSS preserves that published cascade. A permanent discriminator retains
both witnesses; the full four-metric replay traces remain available without
passing them under widened bounds. The speed-reference state discrepancy is
about 0.000811 pu at both steps, consistent with a structural input difference.

All ten exact-import/full-solver profiles stay finite, but those executions
are attachment/coupling checks, **not independent whole-stack trajectory
acceptance**. The nonunit mechanical initialization and the other wind models
need their own exact comparison. No gain, initial controller state or limit
is adjusted to hide those gaps. Existing ANDES and PowerWorld Type-3 tests
pass their original bounds; their previous evidence scopes are unchanged.

The generated renewable inventory now records WTTQA1 as native partial:
fourteen families have partial native evidence, five retain only bounded ANDES
links, and eight have native recorded-profile links. None is marked VERIFIED.

## Checklist

- [x] Fix speed-error polarity and its matching initialization bias.
- [x] Retain three failing-before-fix guards and a separate native sign witness.
- [x] Preserve twenty cold runs and ten exact pairs, with eight bounded state
  profiles and two clearly classified source/executable curve counterexamples.
- [x] Check strict import, finite SMIB faults, named states and voltage-dip behavior.
- [x] Regenerate evidence documentation from its typed inventory.
- [x] Pass 78 focused checks, 410 plugin-core checks (six existing optional
  skips) and 1,633 scoped SMIB/model/solver checks (one existing diagnostic
  skip), with zero failures/errors, plus four offline probe and six wording checks.
- [x] Freeze source/reference/report evidence, including the failed initial
  curve comparison, both native input witnesses and original before-fix compiled
  classes, in the private slice archive with a SHA-256 inventory; deliver a
  scoped commit while preserving unrelated worktree changes.
- [ ] Resolve the native executable's raw-versus-published-filtered curve convention
  without silently changing the published model or reclassifying diagnostics.
- [ ] Add active torque-limit/release, turbine-base, zero-time, initialization and
  lifecycle profiles, complete coupled native trajectories and independently
  validated PowerWorld selector/artifact coverage.

No full Texas2k dynamic simulation is run. This fixes a real model defect and
adds partial evidence; it does not complete the overall coverage plan.

The isolated snapshot matches all eight owned Java files and 1,816 unchanged
Java/POM files. The implementation baseline is `0f3dc02bd`; the intervening
`d8593a5f5` commit changes only a separate benchmark document. A source-transform
guard proves the production edit contains only the three speed-error signs and
their comment. The frozen core dependency and report classpaths are checked
against the archived hashes rather than the shared dependency cache.
