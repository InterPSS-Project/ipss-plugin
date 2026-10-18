# WTPTB1 pitch-controller foundation and native playback

Status: **source kernel with bounded native input-playback evidence**, not a
loadable native model or complete verification. The approved renewable count
remains 18 loadable families and nine missing import/runtime integrations.

## Frozen source contract

PSS/E 36.7 Model Library section 24.4, printed pages 892-894 (physical PDF
pages 904-906), defines fourteen CONs, three STATEs and one VAR. Source PDF
SHA-256: `40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The table and diagram were extracted and the diagram visually inspected.
The standard record is:

```text
IBUS 'WTPTB1' ID Kiw Kpw Kic Kpc Kcc Tp ThetaMax ThetaMin
    RThetaMax RThetaMin ThetaCMax ThetaCMin ThetaWMax ThetaWMin /
```

No legacy WTPTBU1 allocation is accepted yet. In particular, a source-list
mapping is not sufficient to invent a USRMDL header or permit an arbitrary
flat alias. No parser descriptor or dummy LOADABLE entry was added.

The user-requested independent diagram is
[PowerWorld WTGPT_B](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20WTGPT_B.htm).
Its fifteen parameters include a separate MVABase and use a different order;
it must not be parsed as the fourteen-CON native record. Its limit correction
and minimum-time-constant options are not native equation definitions.

## Kernel equations and observations

The new `Wtptb1Data` preserves the native parameter order and validates finite
values, positive servo time, ordered bounds and signed rate limits. The new
`Wtptb1Model` implements:

- Power error from electrical Pord minus **plant Pref**, not torque-controller
  output Pref. Speed error uses turbine speed minus the **current torque speed
  reference**, plus Kcc times power error.
- Independent speed and compensation integral-state limits. PI output clipping
  is separate: a clipped proportional-plus-integral output does not freeze an
  interior integral state.
- A pitch lag driven by the sum of the two clipped PI outputs, followed by
  derivative-rate and output-angle constraints. The sum is not prematurely
  clipped to the final angle bounds.
- Directional cross anti-windup: at the upper/lower pitch-angle boundary, each
  integral's outward derivative is held independently; inward derivatives remain
  active. A rate limit alone does not freeze the upstream integrators.
- Explicit modified-Euler predictor and corrector with distinct input endpoints,
  reinitialization and pure immutable named-state observations.

Initialization derives states from the declared initial aerodynamic pitch:
pitch and speed integral equal Theta0; compensation integral is zero. It does
not fit initial states to a trace or silently expand invalid initial limits.
Initialization-limit expansion, if required for native compatibility, needs its
own discriminating profile before the importer is enabled.

Tp=0 is deliberately rejected by this foundation. Native zero-time runs
initialize successfully and are retained as diagnostics; their algebraic/rate
semantics are not silently approximated with a small time constant.

## Independent native artifacts

Sixteen completed PSS/E 36.7 three-bus runs cover eight step/profile pairs:
baseline at 0.5 and 0.25 ms, upper-angle, lower-angle-configured, narrow PI
limits, slow rate limits, zero-time and no-event flat. Every profile is repeated
twice cold. Each pair's complete CSV is byte-identical. All sixteen runs pass
the native initialization check. All declared input/output hashes, native
14-CON/3-STATE/1-VAR allocation and exact float CON values are audited.
The first failed indexing probe is retained separately: the documented torque
API selector is WTRQ, not WTORQ. It is not counted as a completed reference.

The native stack is REGCA1/REECA1, WTDTB1, WTARA1, WTPTB1 and WTTQA1. REECA1
PFLAG=0 avoids double application of a torque-derived speed reference. No
physical gain or curve is changed to fit InterPSS. Native model-library SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.

The public typed reference includes baseline at both timesteps and the active
upper-angle profile. It retains every nonnegative input endpoint, both event
sides, 5-ms state checkpoints, the first two post-event endpoints and the fine
run's extra terminal tick. Complete clocks, negative prehistory, P/Q, voltage,
machine speed and raw STATE channels remain in the private artifact archive.
The frozen identity bundle is
`20a54e3b8f42c396a012dbd2b3ca1db239ca65619e7c38dcfca453d4ec5ce993`.

Replay integrates at the declared native timestep, not the drifting serialized
float clock. All three state coordinates are compared without fitting. Initial
observed maximum errors across complete endpoints are:

- Baseline coarse: pitch 1.520e-5 degrees, speed integral 1.153e-5 degrees,
  compensation integral 2.327e-6 degrees.
- Baseline fine: pitch 4.353e-6 degrees, speed integral 1.425e-5 degrees,
  compensation integral 5.854e-7 degrees.
- Upper-angle coarse: pitch 6.318e-5 degrees, speed integral 1.967e-5 degrees,
  compensation integral 5.218e-5 degrees.

The permanent checkpoint gate uses separate channel bounds, requires the
baseline pitch and compensation errors to improve under step halving, and
checks that the upper profile actually reaches its native angle limit. Small
float-state speed-integral errors do not monotonically refine; no convergence
claim is made for that coordinate. Raw diagnostic comparisons are not erased.

The lower-angle-configured fault never reaches its lower limit, and the narrow
PI profile does not saturate both integral states. Those profiles cannot prove
active lower-angle or integral-limit parity. Ten focused equation tests cover
the intended local branches; they are not substitutes for independent profiles.

## Completed regression and artifact retention

The final isolated snapshot includes committed plugin `4e5405ce8` plus this
foundation. Core 1.4.3 JAR SHA-256 is
`f7380328ff7b4d81686a5aae2c172d964013ae74deb81c964050a6a74db0d4ad`.

- Ten new focused source-equation tests pass without skips.
- Full plugin-core unit gate: **171 tests, zero failures/errors, six optional skips**.
- Registered SMIB/model, catalog and staggered-solver gate: **1,220 tests,
  zero failures/errors, one existing diagnostic skip**. The three new native
  playback/integrity checks are discovered and executed.

Final commands, from the isolated snapshot root:

```text
mvn -q -pl ipss.plugin.core -am test -Pextended-tests,isolated-verification -Dverification.run.id=wtptb1-merged-units-20261018-d --fail-at-end
mvn -q test -Pextended-tests,isolated-verification -Dverification.run.id=wtptb1-merged-smib-20261018-e -Dtest=DynamicModelSmibTestSuite,DynamicModelCatalogTest,PsseStaggeredEulerSolverTest -Dsurefire.failIfNoSpecifiedTests=false --fail-at-end
```

Both exit zero; totals are counted from fresh Surefire XML. The earlier clean
snapshot also passed 170 units and 1,220 SMIB/model tests before the committed
controller update was included. No full Texas2k simulation was run.

Raw profiles, all 36,082 native samples, frozen runnable probes, typed generator,
source/diagram identities, final source bytes and XML/logs are retained under
the ignored private archive
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-wtptb1-foundation`.
The source/equation and reference checks do not establish native import or
coupled-controller correctness, and the generated support matrix remains unchanged.

## Remaining model work

- Freeze and independently exercise the legacy WTPTBU1 wrapper before adding
  aliases. Implement strict parsing, deferred host attachment and removal rules.
- Wire the actual plant Pref and dynamic torque speed reference atomically
  through the wind stack; preserve existing WTPTA1 behavior.
- Resolve Tp=0, initialization-limit accommodation and staged/opt-in solver
  behavior against independent native profiles.
- Exercise lower-angle and both PI integral limits with discriminating inputs,
  including outward hold and inward release at both timesteps.
- Add moving torque-speed-reference and plant-reference profiles; the baseline
  uses the documented all-zero power/speed curve and therefore Wref=1.
- Add a repeatable exact typed PowerWorld WTGPT_B artifact with explicit MVA
  conversion and no automatic correction; keep dialects separate.
- Run coupled InterPSS flat, three-cycle fault and trip/restart SMIB checks.
  Playback alone does not establish coupled-network or lifecycle conformance.
