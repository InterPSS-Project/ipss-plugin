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

Its input selectors are 0=Ifd, 1=Efd and 2=Vfe, whereas the native record uses
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

The matched-load PowerWorld diagnostics retain all twelve channels and both
event sides. Bus-voltage and speed differences are relatively small, but some
AC1C internal signals do not approach zero when the timestep halves. For the
Efd profile, the field-voltage maximum is about 0.00881 pu at both steps; for
Ifd it is about 0.0389 pu, and for Vfe about 0.00550 pu. These exceed the old
representative AC1C field-voltage envelope and are not accepted by enlarging it.

The Efd-profile PowerWorld internal voltage stays below 1.314 pu, so its
rectified field voltage cannot reach the 0.5/0.3333 pickup threshold in the
published limiter equations. The counterexample therefore exists in a profile
whose OEL should remain inactive. The next discriminator must separate the
AC1C host, machine/network coupling and tool dialect before blaming OEL3C.

Absolute rotor-angle output also has a reference-frame difference; this export
does not contain the second generator's angle needed for the relative-angle
conversion. It remains explicitly diagnostic, not an accepted physical-angle
comparison. Exact instantaneous event-post snapshots and internal OEL state
channels remain additional evidence requirements.

## Regression evidence

Final regression: 83 focused, 431 core and 2,043 scoped SMIB/model/solver
tests, zero failures/errors and seven existing skips, plus 173 offline checks.
All 22 SMIB exports repeat exactly between focused and broad regression runs.
Source reconciliation covers 1,899 Java/POM files and 139 unchanged earlier
fixture parts. Production class hashes are unchanged from the prior runtime
slice. Passing diagnostic/fixture gates does not accept the coupled mismatch.

## Remaining TODO

- Localize the non-converging inactive-OEL AC1C host/coupling difference using
  recorded-input replay and source-backed discriminator profiles.
- Export the reference generator angle and internal limiter states; complete
  relative-angle and exact instantaneous event-side comparisons.
- Add coupled per-channel acceptance only after the physical and observation
  contracts are reconciled. The current fixtures prove repeatability and exact
  parameterization, not full cross-tool agreement.
- Extend supported exciter hosts and finish the remaining approved model rows.

The [runtime slice](oel3c-runtime-2026-10-19.md) and
[development plan](../dynamic-model-coverage-development-plan.md) keep those
requirements separate from loadability and source-equation checks.
