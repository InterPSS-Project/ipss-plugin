# OEL3C independent input dialect and hybrid-switch diagnostics

## Outcome and scope

The imported OEL3C runtime is unchanged. This slice adds repeatable PowerWorld
artifacts, exact cross-dialect input/readback guards, six additional matched-load
SMIB profiles and a permanent all-row proportional-switch diagnostic. It does
not claim complete OEL3C or AC1C trajectory acceptance. No model constants,
states, clocks, curves or established acceptance bounds are fitted or widened.
No full Texas2k dynamic simulation is run.

## A real input-format bug in the benchmark setup

The native OEL3C record orders its values as:

`input, exponent, pickup, scale, TF, gain, integralTime, Kp, Imax, Imin, Omax, Omin`.

The PowerWorld reader follows the [documented PowerWorld parameter order and selectors](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Over%20Excitation%20Limiter%20OEL3C.htm):

`input, pickup, scale, TF, exponent, gain, integralTime, Kp, Imax, Imin, Omax, Omin`.

Its documented input selectors are 0=Ifd, 1=Efd and 2=Vfe, whereas the native record uses
1=Efd, 2=Ifd and 3=Vfe. Passing the unmodified native record into that reader
shifted the physical constants and selected an invalid input. A completed run
alone therefore was not valid benchmark evidence.

The new runner retains the native source and creates a distinct explicitly
translated target input. It changes no other model record and applies neither
automatic correction nor parameter overrides. Whole-model readback before and
after initialization verifies every OEL3C constant/selector, all 21 AC1C
constants and both native flags, and all GENROU physical constants. Wrong
selector/order and missing models are rejected by permanent tests.

PowerWorld's native option export verifies impedance loads and RK2 in every
run. Separate InterPSS profiles explicitly use impedance loads. The older
constant-power profiles remain separate for their corresponding reference;
they are not mixed into this comparison.

The first attempted generic DYR export timed out. A subsequent native model
export completed but revealed the untranslated-record error. Both rejected
pilots are retained. Final runs export native model/options data; export or
input-format failures are not reclassified as model-equation failures.

## Repeatability and portable evidence

Three physical-input fault profiles at two timesteps (0.0005 and 0.00025 s),
each twice cold, produce twelve clean runs and six exact whole-file CSV pairs.
All 18,036 raw rows and twelve channels are retained, including metadata and
both event occurrences. Validation reports zero errors and warnings. Model and
solver/load options round-trip without correction.

Portable full raw CSV fixtures are stored under
`ipss.test.plugin.core/src/test/resources/oel3c-powerworld-reference/` as
deterministic gzip/base64 text. `index.json` contains complete native and
translated dynamic inputs, raw hashes, timestep/count metadata and an explicit
false trajectory-acceptance label. Offline tests verify hashes, all numerical
values, schema translation and duplicate event rows. The original logs,
executable provenance, readbacks and failed pilots remain in private evidence.

## Proportional-switch diagnosis without changing the equation

The permanent `tools/audit_oel3c_switching.py` checks every row of all six prior
fault trajectories against the strict source equation:

`P = Kp * gainedError if integral < 0, otherwise 0`.

Both simulator traces obey that equation exactly at their recorded states.
There are 9,030 reference rows: twelve history rows, twenty-four event
occurrences, three explicitly held terminal observations, six non-event rows
on different switch branches, and 8,985 same-branch rows. None is dropped.

For the field-current profile, the regular same-branch proportional maximum
falls from about 7.36e-5 to 3.55e-5 pu when the timestep halves; for Vfe it falls
from about 3.48e-5 to 1.77e-5 pu. The much larger pointwise maxima occur when one
trace has crossed zero and the other has not. A finite jump amplitude can
persist in a maximum norm while the switch-time interval shrinks. The audit
retains both the original linearly interpolated output error and algebraic
reevaluation from interpolated states, and reports switch brackets on the
unmodified raw clocks. It does not align or fit switch times.

This explains the proportional counterexample as a branch/timing issue in the
tested trajectories; it is not proof that every integration-stage or event
boundary is correct. No artificial delay, altered zero threshold or modified
integrator was added to the production model.

## New coupled counterexamples remain open

### Updated acceptance criterion and diagnostic status

The user specifies **RMSE < 0.001 for each new coupled comparison channel**.
Use documented per-unit bases for voltage, current, speed and controller states;
convert MW/Mvar to per unit on the common 100 MVA network base and relative
rotor angle to radians. Do not average errors across channels or replace
existing regression bounds. Report peak errors and both fault-event sides
separately, retaining all raw rows. Missing or semantically mismatched channels
remain unverified rather than passing by omission.

Subsequent measurement evidence revises the host-model interpretation below. Removing
the limiter reduces the internal-voltage maximum difference from approximately
0.00881 to 0.000176 pu. The nominal Efd and Vfe reference runs have identical
raw trajectories despite different input-selector readbacks. Additional direct
channels show the nominal Efd run initialized with `StateTF = 0.4890948832`,
consistent with `0.3333 * Vfe`, while measured Efd is `1.105876207` pu.
The three extended-channel profiles have each been regenerated twice cold at
0.0005 s: three byte-identical pairs, 6,018 raw rows and 22 channels. Complete
raw CSV fixtures, metadata, source/translated inputs and hashes are preserved in
`src/test/resources/oel3c-measurement-reference/` in the integration-test module.
The new `--measurement-channels` probe option records the reference-generator
angle, Efd, Ifd, limiter output and both limiter states without changing physical
parameters. The field names were obtained from the installed tool's object-field
dictionary rather than guessed.

`tools/audit_oel3c_measurement.py` checks the initialized zero-TF measurement
against the three physical candidates. It rejects the nominal Efd case, labels
the Ifd initialization ambiguous because Efd equals Ifd there, and finds the Vfe
initialization consistent. None establishes complete dynamic routing. The fixed
1e-6 pu initialization/serialization check is separate from the user's 0.001
trajectory RMSE criterion; that larger bound must not admit a wrong signal.

Consequently, parameter readback alone does not establish the intended physical
measurement routing. The earlier inference that the limiter was inactive is
not established; these pilots do not justify changing AC1C equations or claiming
trajectory acceptance. Verify actual reference-tool input routing first.

The earlier twelve-channel fixtures and sealed evidence remain unchanged. A
complete simulation and a passing expected-rejection test are not physical
trajectory acceptance; the installed tool's runtime behavior may differ from
its documented input selector. Do not remap selectors based on one matching
trace, or alter the InterPSS model to reproduce an unresolved tool discrepancy.

The matched-load PowerWorld diagnostics retain all twelve channels and both
event sides. Bus-voltage and speed differences are relatively small, but some
AC1C internal signals do not approach zero when the timestep halves. For the
Efd profile, the field-voltage maximum is about 0.00881 pu at both steps; for
Ifd it is about 0.0389 pu, and for Vfe about 0.00550 pu. These exceed the old
representative AC1C field-voltage envelope and are not accepted by enlarging it.

The original interpretation assumed that the Efd-profile reference actually
used Efd. Its internal voltage stays below 1.314 pu, below the expected
0.5/0.3333 pickup threshold for that selection. Direct measurement evidence
above invalidates that assumption: inactivity and an AC1C host defect cannot
be inferred from the nominal selector label.

Absolute rotor-angle output also has a reference-frame difference; this export
does not contain the second generator's angle needed for the relative-angle
conversion. It remains explicitly diagnostic, not an accepted physical-angle
comparison. Exact instantaneous event-post snapshots and internal OEL state
channels remain additional evidence requirements.

## Regression evidence

The measurement follow-up passes 91 focused, 431 core and 2,043 scoped
SMIB/model tests, with zero failures/errors and seven existing skips, plus 183
offline checks. All 22 previous InterPSS SMIB exports repeat exactly, and the
production classes are unchanged. No full-network dynamics were run. These
new checks include expected rejection of incorrect physical routing, not
acceptance of that reference trajectory.

Earlier switch-slice regression: 83 focused, 431 core and 2,043 scoped SMIB/model/solver
tests, zero failures/errors and seven existing skips, plus 173 offline checks.
All 22 SMIB exports repeat exactly between focused and broad regression runs.
Source reconciliation covers 1,899 Java/POM files and 139 unchanged earlier
fixture parts. Production class hashes are unchanged from the prior runtime
slice. Passing diagnostic/fixture gates does not accept the coupled mismatch.

## Remaining TODO

- Verify actual limiter measurement routing with direct physical/state channels;
  then localize any remaining host/coupling difference using source-backed
  discriminator profiles. Do not assume the nominal Efd limiter is inactive.
- Export the reference generator angle and internal limiter states; complete
  relative-angle and exact instantaneous event-side comparisons.
- Apply the user-specified per-channel RMSE < 0.001 criterion after physical and
  observation contracts are reconciled. The current fixtures prove repeatability
  and parameter readback, not intended runtime routing or cross-tool agreement.
- Extend supported exciter hosts and finish the remaining approved model rows.

The [runtime slice](oel3c-runtime-2026-10-19.md) and
[development plan](../dynamic-model-coverage-development-plan.md) keep those
requirements separate from loadability and source-equation checks.
