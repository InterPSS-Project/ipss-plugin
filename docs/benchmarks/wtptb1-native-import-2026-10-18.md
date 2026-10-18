# WTPTB1 / WTPTBU1 native import and bounded SMIB verification

Status: **LOADABLE, with partial independent verification**, not VERIFIED.
Approved native renewable coverage is now 19 loadable families and eight missing
implementations. This does not close the approved-model goal or imply full
Texas2k dynamic acceptance. The earlier [foundation](wtptb1-foundation-2026-10-18.md)
is retained as historical evidence, not overwritten.

## Exact native path

The PSS/E reader accepts precisely:

```text
IBUS 'WTPTB1' ID CON(1) ... CON(14) /
IBUS 'USRMDL' ID 'WTPTBU1' 104 0 0 14 3 1 CON(1) ... CON(14) /
```

All six legacy metadata integers and exact record counts are checked. Flat
WTPTBU1 aliases, incorrect wrappers, truncated/extra parameters, nonfinite
values and invalid limits are rejected. Two cold native legacy probes have
byte-identical full outputs to the standard record, with the same actual
14-CON/3-STATE/1-VAR allocation. This is a PSS/E 36.7 contract, not an assertion
about every older library release.

Deferred attachment permits WTPTB1 to precede its mechanical host. The supported
host requires REGCA1/REECA1, a drive train, WTARA1 and WTTQA1. Missing targets or
prerequisites are rejected, and duplicate/mixed A/B pitch controllers cannot
silently overwrite one another. Whole-stack GNET/model removal retains its
intentional-skip status. WTPTB1 is an additional approved family, not a new entry
in the unchanged 25-family Texas2k source inventory. No DYD input was added.

The wind stack routes **plant Pref**, not torque-output Pref, into the pitch
power-error path. It captures the current torque speed reference and turbine
speed atomically for each predictor/corrector endpoint. Existing WTPTA1 behavior
is retained. Three immutable semantic states are also exposed as `WTPTB1.*` on
the converter's state interface.

## Zero time, history and prerequisite fix

Native zero-time traces satisfy the clipped sum of the two PI outputs to within
float precision, even when pitch slew exceeds the declared lag rate limit.
Tp=0 therefore bypasses the lag and its rate limiter; it retains angle and PI
limits. The implementation does not substitute a small time constant. A
zero-duration call refreshes only this algebraic output, not PI history.

The opt-in two-step path is separate from default modified Euler. A constrained
zero derivative holds its state and clears outward derivative history, avoiding
spurious inward motion under a continuing outward command. Focused tests cover
freeze/release and history reset. Complete native opt-in precision parity is
still open; the coupled acceptance below uses default modified Euler.

The compatible native benchmark uses WTTQA1's documented all-zero power/speed
curve, meaning Wref=1. InterPSS previously rejected that valid native selection.
The exact all-eight-zero branch is now supported; ordinary curve validation is
not weakened. Source: Model Library section 26.1, printed page 902.

Invalid initial pitch/PI bounds remain explicit errors. A native probe with
speed-integral maximum below the initial pitch reports suspect initialization
and is retained as rejected evidence, not used to justify automatic expansion.

## Native profiles and coupled comparison

The extended audited matrix has ten profiles, each cold-repeated: baseline,
zero-time, active lower angle, active PI-state limits and moving speed reference,
each at 0.5 and 0.25 ms. Two further cold legacy runs prove record equivalence.
All 22 runs pass initialization and recognized log-quality checks, actual float
CON checks and input/output hashes. Each pair's complete CSV is identical.
The earlier independent active upper-angle profile remains unchanged.

An attempted direct Pref VAR perturbation was overwritten by the native model;
the six resulting flat-like traces are retained as ineffective discriminators,
not plant-reference-step evidence. The invalid-initialization probe is separate.

Public typed checkpoints retain every nonnegative input endpoint, duplicate
event sides, selected raw state/network rows, immediate post-event endpoints
and the extra fine terminal tick. The extended reference is split into small
Java data classes without changing recorded bytes or the normalization. Fixed
body fingerprints and a separately frozen identity bundle detect mutation:
`f90e7d7b29ae9661d89bcfa2740c089faa5e7f4dd976238d242204daf93091ba`.
Complete clocks, prehistory and raw outputs remain in the private archive.

Independent playback compares all three pitch states. Some float-state PI
residuals do not monotonically refine; they remain disclosed rather than being
described as second-order convergence. Native storage precision is not imposed
on default double-precision modified Euler.

The full InterPSS solver passes flat, three-cycle Bus 2 fault (.05-.10 s) and
generator-trip state-holding checks at both timesteps. The fault comparison
scores pitch/PI states, turbine and generator speed, speed reference, Pord,
three nearby voltages and P/Q. All scored continuous states include both event sides.
Representative complete-endpoint maximum differences, coarse/fine:

- Pitch: 0.0011433 / 0.0005730 degrees.
- Speed integral: 5.6391e-5 / 2.7446e-5 degrees.
- Compensation integral: 1.8407e-4 / 9.2426e-5 degrees.
- Generator-speed deviation: 2.1990e-6 / 1.1102e-6 pu.
- Bus 1/2/3 voltage: about 4.7e-5 / 1.7e-5 / 3.3e-6 pu at either step.

Permanent tests retain separate channel bounds and require the pitch/PI and
generator-speed errors to decrease under step halving. Voltage residuals are
not claimed to converge. Two instantaneous native network-post rows remain
unaccepted because the regular InterPSS exporter provides the pre-side endpoint
there: voltage errors reach about .057 pu. These rows are retained and reported,
not silently discarded or counted as matched network trajectories.

## PowerWorld benchmark artifact

The separate typed WTGPT_B benchmark uses the
[PowerWorld model](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20WTGPT_B.htm),
including its explicit 100-MVA base and documented parameter order. Its WTGT_B
drive train follows the published convention, not the native WTDTB1 feedback
dialect. All fifteen pitch parameters and eight mechanical parameters are
exported before and after initialization and remain unchanged. Automatic
correction is disabled.

Four cold runs (two at each step) preserve runnable specifications, AUX, staged
DYR, logs and all nine output channels including three named pitch states.
Each pair has identical complete raw CSV bytes. Raw SHA-256:

- 0.5 ms: `e7b69f2bfa53cdf090a726c31cc589a8e52539e892bb76d242068eff0fefea51`.
- 0.25 ms: `d6910393e91394e3d259d59ed6f3a467658b066b9e303d9530b53d9e474654de`.

The auditor checks both raw and runner LF-canonical identities explicitly;
canonical hashes normalize only CRLF/CR to LF. No sample, channel or event side
is removed. This establishes a repeatable benchmark artifact, not yet complete
InterPSS/PowerWorld pitch-input playback or coupled cross-tool parity.

## Final scoped regression and retained evidence

The tested snapshot is committed plugin `364fa29fa` plus this slice. It retains
the previously committed controller-stage fix. Core 1.4.3 JAR SHA-256 is
`f7380328ff7b4d81686a5aae2c172d964013ae74deb81c964050a6a74db0d4ad`.
Unrelated untracked WECC240/JAX code is not included.

- Plugin-core units: **178 tests, zero failures/errors, six optional skips**.
- Registered SMIB/model, catalog and staggered-solver gate: **1,233 tests,
  zero failures/errors, one existing diagnostic skip**.

Both final commands exit zero; totals come from fresh Surefire XML:

```text
mvn -q -pl ipss.plugin.core -am test -Pextended-tests,isolated-verification -Dverification.run.id=wtptb1-native-import-units-20261018-j --fail-at-end
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=wtptb1-native-import-smib-20261018-k -Dtest=DynamicModelSmibTestSuite,DynamicModelCatalogTest,PsseStaggeredEulerSolverTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
```

The earlier unit gate exposed an accidental catalog placement in the Texas2k
list, changing its count from 25 to 26. The descriptor was moved to the additional
approved-model list; the original source-inventory assertion was retained, not
changed to bless the error. The failed gate remains diagnostic evidence.

The ignored private archive
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-wtptb1-native-import`
retains complete accepted/rejected native probes, PowerWorld cold runs, inputs,
scripts, raw traces, source hashes, final XML/logs and six InterPSS SMIB exports.
The earlier sealed foundation archive remains the provenance for its unchanged
profiles. No full Texas2k dynamics were executed for this slice.

## Remaining acceptance

- Exact instantaneous network-post observation and comparison.
- Effective native plant-reference steps and additional base/host combinations.
- Independently referenced reconnect/restart and zero-time full-stack profiles.
- Complete native opt-in solver storage precision and active-limit recurrence.
- PowerWorld recorded-input and full coupled comparisons for the same declared
  dialect, plus active branches beyond the recorded baseline.

Source identities remain the same as the foundation: native Model Library SHA
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`,
native DSMDL SHA
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`,
PowerWorld executable SHA
`06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c`.
