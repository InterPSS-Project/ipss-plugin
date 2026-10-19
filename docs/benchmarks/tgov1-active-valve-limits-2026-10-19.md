# TGOV1 active valve limits and read-only reheater storage

## Source contract and scope

This slice adds sustained upper/lower valve-limit and inward-release evidence
for the existing TGOV1 controller. It preserves the native seven-constant
reader, shared CML implementation, core GENROU engine and modified-Euler
integration. The production change is only a read-only observation accessor;
no controller equation, gain, limit, initial-state rule or solver is changed.

The installed model library, PDF page 481 (printed page 469), specifies seven
constants in the order R, T1, Vmax, Vmin, T2, T3, Dt, with positive T1/T3,
two state allocations and one reference VAR. The parameter table and diagram
were rendered and visually checked. The private evidence pins the consulted
document to SHA-256
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The [PowerWorld TGOV1/TGOV1D documentation](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20TGOV1%20and%20TGOV1D.htm)
independently identifies the valve bounds, T2/T3 turbine block and damping,
and documents limit expansion when the initial valve is outside its bounds.
That expansion is not exercised by these interior-initialization profiles.
The PowerWorld turbine-rating extension is not appended to the native reader.

## Repeatable native profiles

`tools/tgov1_limit_probe.py` creates four small two-bus profiles and runs each
twice cold in separate processes. The unmodified public RAW and GENROU are
reused. Every profile uses:

```text
R=.05, T1=.04, Vmax=1.2, Vmin=.2, T2=.1, T3=.3, Dt=.5
```

The external governor-reference command changes by +0.1 or -0.1 pu at
nominal 0.05 s and reverses that increment at 0.20 s. Runs finish at 0.5 s,
at 0.5 and 0.25 ms. No fault or parameter change is used to force saturation.
Each direction sustains its physical bound for more than fifty raw samples
and subsequently leaves it inward. All seven entered constants are read back
unchanged after initialization in all eight runs.

Coarse references retain 1,005 rows and fine references 2,005 rows, each with
ten channels, two negative-time initialization rows and two repeated-time
event samples. Both cold exports match byte-for-byte for every profile.
The probe records actual floating-point event times separately from the
nominal requested times. Complete raw outputs, logs, entered/initialized
constants, scripts and native executable/library identities stay private;
fixed-digest public numerical fixtures preserve every raw row.

The stored model reference VAR does not change during the external command
increments. It cannot be misused as the complete stepped input. Replay uses
the declared command events and their observed duplicate-time boundaries,
with no fitted time shift, gain or optimized input waveform.

## State coordinate and permanent observation API

The CML turbine block stores an internal output coordinate x. Its physical
turbine output y and unnormalized storage S satisfy:

```text
S = T3*x
y = (T2/T3)*valve + S/T3
Pmech = y - Dt*(speed - 1)
```

The allocated reference state is S, not physical output y or raw CML x.
The forward identity above is checked independently against every native row;
maximum residuals are below 1.85e-7 pu. This coordinate follows the published
lead-lag realization, rather than a fitted scaling of plotted traces.

`PsseTGov1SteamTurGovernor.getReheaterStorage()` exposes T3 times the runtime
CML registry state. Existing `getTurbinePower()` and named-state keys keep their
original meanings. Repeated reads are checked for exact equality and cannot
change the integration state. No reflection, reintegration or state mutation
is used to produce this observation.

## Replay and small-network results

Every raw state/output row, including history and duplicate event rows, is
compared. The controller replay explicitly uses the reference's initial
mechanical boundary and recorded speed; it is not a prediction of native
machine initialization or network voltage. Predictor/corrector inputs use the
recorded endpoint speeds. No row is removed or resampled.

Maximum absolute errors for valve, storage and mechanical output are:

- Upper, 0.5 ms: 0.00609374675; 7.16591234e-6; 0.00204511843 pu.
- Upper, 0.25 ms: 0.00390065008; 1.79475165e-6; 0.00129752379 pu.
- Lower, 0.5 ms: 0.01069798895; 7.18639880e-6; 0.00354954252 pu.
- Lower, 0.25 ms: 0.00308592289; 1.81524863e-6; 0.00103210276 pu.

Every channel decreases by more than 35 percent under step halving. Largest
valve errors occur at the command event or limit crossing. Native event/history
and projected predictor/corrector behavior are not made identical by changing
the model or replacing modified Euler. Initial exploratory bounds were
tightened to separate profile/channel bounds after the full replay; existing
trajectory tolerances remain unchanged.

Four additional strict-import coupled runs use their own solved operating
points, without forcing native states. They check finite states/speed/output,
physical valve bounds, sustained limiting and release at both steps. Their
traces are separate from the forced-input replay. They are not claimed as
independent coupled-network trajectory acceptance.

## Checklist and remaining work

- [x] Visually check the native table/diagram and consult the official
  independent governor reference; retain precise private source provenance.
- [x] Cold-repeat all four native profiles; verify every raw value, layout,
  completed initialization, entered parameters and post-initialization readback.
- [x] Add a stable read-only storage observation and preserve all existing APIs.
- [x] Verify full-row replay, coordinate identity, both bounds, inward release,
  immutable observations and per-channel timestep refinement.
- [x] Register the seven new checks in the core suite and permanent SMIB gate.
- [x] Pass 20 focused model/builder checks and four offline probe guards.
- [x] Pass 410 plugin-core tests (six existing optional skips) and 1,616 scoped
  SMIB/model/solver tests (one existing diagnostic skip), with zero
  failures/errors and terminal exit zero. Six language-policy checks also pass.
- [x] Retain scoped source/reference/trace/report hashes for the implementation
  commit in private archive `2026-10-19-tgov1-active-valve-limits`.
- [ ] Add independent coupled and PowerWorld active-limit trajectories, further
  damping/base profiles, initialization outside bounds, zero-time validation,
  deadband variants and lifecycle cases before complete-family verification.

No full Texas2k dynamic simulation is run.

Validation uses baseline `656e57b78` and the frozen isolated core dependency.
The snapshot audit matches 1,813 unchanged Java/POM files and four owned Java
files, with no unexpected sources. Removing the accessor from the source
reproduces the baseline controller exactly after newline normalization. The
unrelated working documentation and untracked comparison test are excluded.
