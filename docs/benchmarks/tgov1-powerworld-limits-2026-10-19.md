# TGOV1 PowerWorld active limits and machine-convention localization

## Scope and runnable artifacts

This follow-up adds eight completed PowerWorld runs: upper/lower governor
reference increments and inward release at 0.5/0.25 ms, each repeated twice
cold. `tools/tgov1_powerworld_limit_probe.py upper coarse a` is the corresponding
example command from a fresh checkout/output namespace. Existing output
directories are refused rather than overwritten. It reuses the public RAW,
the exact seven-constant DYR from the [native valve-limit slice](tgov1-active-valve-limits-2026-10-19.md)
and the existing hidden-process runner. No overrides or automatic correction
are requested. Native and vendor data readers are not combined or changed.

The [documented generator event interface](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Transient_Stability_Event_Dialog.htm)
provides governor-setpoint changes. The action is
`CHANGEBY Governor_Setpoint 0.1 pu`, reversed at 0.20 s after application at
0.05 s; the lower profile reverses both signs. An initial successful pilot
logs the reference moving from approximately 0.025 to 0.125 and back, consistent
with the native speed-load command rather than a 0.1 pu mechanical-power step.
The closed-loop response and sustained valve bounds are checked as well.

All fourteen channels and 1,003/2,003 rows repeat byte-for-byte within each
pair. The raw exports include both occurrences at each command event.
Channels comprise two bus voltages, P/Q, both machines' angle/speed, two
governor states and four GENROU electrical states. Fixed-digest public numeric
projections preserve every value and event occurrence without resampling.
The raw importer independently checks the entire export hash, metadata and
all values against those projections for all eight files.

Exact executable, input, runner, script, specification, reference and report
hashes are retained privately. The final reference fixtures and profile
commands do not depend on private export files to execute in CI.

## Controller replay and limiter-stage differences

Recorded speed replay uses the reference's initial mechanical boundary and
the declared governor commands. This is explicitly forced input, not predicted
machine/network acceptance. PowerWorld's first governor state is physical
turbine output; the second is valve position. Reheater storage and damped
mechanical output are derived and labeled as such, not claimed as additional
independently exported channels. All raw rows are scored, including both event
sides. Existing native replay bounds and historical comparisons are unchanged.

Maximum replay errors for valve and turbine output are:

- Upper, 0.5 ms: 5.34143504e-7 and 5.20792694e-5 pu.
- Upper, 0.25 ms: 0.00390102642 and 0.00130035157 pu.
- Lower, 0.5 ms: 0.01062186944 and 0.00356620030 pu.
- Lower, 0.25 ms: 8.13399534e-8 and 1.30327861e-5 pu.

These errors are not monotonically reduced by halving the step: limiter
crossing phase matters. A permanent scalar equation guard reproduces the
projected modified-Euler predictor/corrector behavior. Starting just inside
a bound, the projected predictor suppresses the outward corrector derivative;
the corrected endpoint may remain briefly inside before the next step latches
the bound. Both sustained limiting and immediate inward release are checked.
The trace retains these crossing errors rather than excluding their samples.
Profile-specific bounded replay is not a claim of identical integration stages
or exhaustive limiter accuracy. No implicit corrector is introduced.

## Coupled discrepancy: an independently documented machine convention

The unmodified native realization retains maximum upper-profile errors of
about 0.001442 pu at Bus 1 and 0.98694 Mvar in reactive power, nearly unchanged
under step halving. Labeling this as a TGOV1 gain problem would be incorrect.

The [official machine description](https://www.powerworld.com/files/GENROU-GENSAL-GENTPF-GENTPJ.pdf),
PDF page 5/printed page 4, was rendered and visually checked. Its stator
interface multiplies subtransient flux by rotor speed while leaving source
reactance unscaled. The native core Flux0 realization intentionally uses unity
scaling. A test-only discriminator changes that boundary and supplies the
published swing equation, reusing the core GENROU electrical states/solver.
Every numeric machine parameter and pole count is copied unchanged; a fresh
governor uses the same seven constants before first initialization. No state
fit, physical parameter change or native-reader alias is introduced. The
adapter is restricted to the validated zero-stator-resistance/zero-machine-
damping profiles, and is not installed in production or silently selected.

The consulted machine source SHA-256 is
`68d859bd78ba60c672346e3ee43be60c1db3dcb849c456fd1c3da17fe2288ca2`.
The first adapter attempt incorrectly reused an initialized controller binding;
its nonfinite-output failure is retained. The final discriminator constructs
fresh objects before initialization and guards exact parameter copying.

Matched-convention maximum Bus 1 voltage and reactive-power errors are:

- Upper, 0.5 ms: 4.66446907e-7 pu and 0.000452121752 Mvar.
- Upper, 0.25 ms: 4.43587302e-7 pu and 0.000389947422 Mvar.
- Lower, 0.5 ms: 4.92499828e-7 pu and 0.000349313687 Mvar.
- Lower, 0.25 ms: 4.24373468e-7 pu and 0.000363457060 Mvar.

Twelve metrics are scored separately: V1/V2, P/Q, relative speed/angle,
two governor states and four machine electrical states. Reference-machine
channels define the relative coordinate, not an extra fitted offset or timing
shift. All event occurrences remain scored. Maximum relative-speed error is
1.27301e-7 pu and maximum relative-angle error 0.000238668 degrees.
Permanent channel-specific bounds lock in the matched-convention profiles;
the native-convention traces remain explicit counterexamples, not accepted
cross-tool parity. The default production machine is unchanged.

## Export diagnostics and remaining work

The SimAuto automation class is not registered on this host; no installation
or registration was changed. The supported AUX runner completes all eight
reference runs. Two separate 90-second export diagnostics do not complete:
generator-field metadata export and full DYR readback. The latter writes a
matching pre-initialization DYR but does not return to the script, so it never
reaches post-initialization readback. Both timeout manifests, partial files,
scripts and progress markers are retained. They are not counted as successful
readback or trajectory references.

- [x] Complete eight cold runs and verify exact pair/projection equality.
- [x] Verify two controller states, derived observations and active limiting.
- [x] Preserve and explain non-monotone limiter-phase errors with a local guard.
- [x] Localize the coupled voltage/Q floor using an official-source convention,
  unchanged machine parameters and the core electrical engine.
- [x] Register matched-convention coupled bounds and retain unmatched diagnostics.
- [x] Finish broad regression: 15 focused checks, 410 plugin-core checks
  (six existing skips), and 1,621 scoped SMIB/model/solver checks (one existing
  skip), with zero failures or errors. Four offline probe guards and six
  language-policy tests pass as well.
- [x] Freeze raw references, diagnostics, final comparison traces, source
  snapshots and reports in the private slice archive with a SHA-256 inventory.
  Deliver the slice as a scoped commit, preserving unrelated worktree changes.
- [ ] Complete successful post-initialization PowerWorld parameter readback.
- [ ] Extend the explicit convention adapter to additional validated model
  profiles before making it a shared benchmark facility; do not change native
  imports globally to match a different simulator convention.
- [ ] Close further base, damping, initialization-outside-bounds, zero-time,
  deadband and lifecycle cases before complete TGOV1-family verification.

No full Texas2k dynamic simulation is run, and no production machine or
controller equation or existing trajectory tolerance is changed.

The isolated verification snapshot matches 1,815 unchanged Java/POM files
and all four owned Java files. The tested implementation baseline is
`695b31fc63d038ff799bf31656039be89d6df49a`; the intervening commit
`dde7633891d60e6d71cb55e7d0dd62aa08891176` changes only an unrelated benchmark
document. Existing native acceptance equations, inputs and bounds are audited
unchanged. The frozen core dependency and final report classpaths are hashed
with the private evidence rather than relying on the shared dependency cache.
