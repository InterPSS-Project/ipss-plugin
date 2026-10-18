# WTDTB1 source kernel: exact typed PowerWorld WTGT_B playback

Status: **bounded five-state playback conformance**, not native WTDTB1 or
full-network trajectory acceptance. Approved native renewable coverage remains
17 loadable and ten missing. This slice does not register a DYR model.

The verification workflow localized the discrepancy with recorded electrical
power instead of changing model gains, state initialization or torque equations
to fit a native trace. The unchanged kernel agrees closely with an independent
PowerWorld implementation of the published diagram. The non-converging PSS/E
mechanical difference described in the [foundation report](wtdtb1-foundation-2026-10-18.md)
remains unresolved; the new evidence does not prove its cause.

## Exact tool and model identity

PowerWorld Simulator Education/Evaluation 24, executable version 24.2026.9.3,
SHA-256 `06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c`.
The probe creates `Governor_WTGT_B` directly. It does not relabel another model
or reinterpret native six-CON WTDTB1 data as PowerWorld's eight-parameter record.
The [official WTGT_B documentation and diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20WTGT_B.htm)
define the explicit stiffness/base and initial-speed inputs used here.

Parameters in PowerWorld order:

`Ht=2.7, Hg=1.8, DShaft=.4, KShaft=341.09352810164825, MWCap=100, W0=1, Tp=.1, Damp=1.5`.

These map algebraically to the source kernel's native-order parameters
`H=4.5, DAMP=1.5, Htfrac=.6, Freq1=2 Hz, Dshaft=.4, Tp=.1` at 60 Hz.
PowerWorld's stiffness is `2*Ht*Hg*(2*pi*Freq1)^2/(Ht+Hg)`;
the kernel's shaft-angle-to-torque coefficient is that value divided by `2*pi*60`.
Both simulator parameters and diagram were inspected independently before replay.

The small public network contains REGCA1/REECA1 and a GENCLS infinite-bus source.
The plant controller is omitted to isolate drivetrain behavior. A three-phase
Bus 2 fault of j1 pu starts at .05 s and clears at .10 s (three 60-Hz cycles).
Automatic correction is disabled. Loaded and initialized WTGT_B exports are
byte-identical. Parameter comparison accounts for the export's six-significant-
digit stiffness formatting; it does not infer additional internal precision
from that formatted value. Existing user-owned simulator processes are not used.

## Reproducible raw artifacts

Each step was run twice cold, retaining inputs, runner, generated AUX, exact
model exports, logs, raw CSV, specification and manifest. Each coarse run has
2,003 samples, each fine run 4,003; all have eleven result channels and exactly
two duplicate event timestamps. No duplicate event side or raw sample is removed.

Raw CSV SHA-256:

- 0.5 ms: `ee33f5ec2ce0d9d0c2cd81e661055a643df145361d7b1dc2764a19ad48bed392`.
- 0.25 ms: `a350e905d33b630481f94091bc21f233bea8557faf3578b5ab468995a5925704`.

The audit verifies both raw cold-repeat hashes and the runner's separate
LF-canonical manifest hashes. Canonical hashing changes CRLF/CR to LF only;
canonical byte counts must not be presented as raw file sizes.
Complete artifacts are retained in the private archive
`2026-10-18-wtdtb1-powerworld-playback`: 804 files sealed and hash-verified.

## Observation and input mapping

Playback uses every raw endpoint's `TSMW/100` as electrical power. Default
modified Euler samples the previous endpoint at predictor and current endpoint
at corrector. Duplicate event rows change the power input for the next step but
do not advance the five continuous states. The mechanical filter remains a
dynamic state; its recorded output is not substituted as an input.

PowerWorld publishes `TurbineSpeed, ShaftAngle, GenSpeed, GenDeltaAngle, Pmech`.
Checkpoints reorder these to the kernel's five source coordinates:
`ShaftAngle, TurbineSpeed-1, GenSpeed-1, GenDeltaAngle, Pmech`.
There are 203 checkpoints at 5-ms spacing including both event sides.
Observation conversion does not alter the model's integration coordinates.

Maximum checkpoint errors, coarse then fine:

- Shaft angle: 1.030e-7, 4.357e-8 rad.
- Turbine speed deviation: 5.963e-8, 5.993e-8 pu.
- Generator speed deviation: 6.074e-8, 5.984e-8 pu.
- Rotor angle deviation: 6.573e-8, 2.900e-8 rad.
- Electrical power filter: 1.538e-7, 4.761e-8 pu.

Shaft, rotor-angle and filter errors decrease under step halving. The exported
absolute speeds have a roughly 6e-8 pu single-precision rounding floor near
1 pu; their errors are not claimed to halve. Permanent per-channel bounds are
`[1.2e-7,8e-8,8e-8,8e-8,2e-7]` coarse and
`[6e-8,8e-8,8e-8,4e-8,6e-8]` fine.
Separate exploratory all-endpoint comparisons were also below these bounds;
the permanent state acceptance scope is explicitly the 203 checkpoints.

Fixed SHA-256 fingerprints protect the embedded power/checkpoint bodies.
Negative guards reject using per-unit instead of Hz for Freq1 and deleting the
power filter. Repeated state observations must not advance the model. The test
is registered in CorePluginTestSuite and discovered by DynamicModelSmibTestSuite.

## Remaining work

Resolve the native PSS/E two-mass/interface discrepancy without fitting.
Independently exercise non-unit speed and model bases, one-mass/aerodynamic
branches, damping and Tp=0, as well as lifecycle transitions. Implement the exact
native/wrapper parser and builder attachment only with appropriate rejection
and full-solver SMIB tests. The model is not marked complete by this playback.

## Native discriminator follow-up (not adopted)

Separate generator- and turbine-speed perturbations localize a candidate
native-interface difference: the generator receives shaft-plus-damping feedback
consistent with a `wg/wt` multiplier, absent from the published torque-balance
diagram and PowerWorld implementation. This is a fixed equation discriminator,
not a fitted gain. Both perturbations reproduce identical full CSVs when cold-
repeated. Their first-response rates agree with this candidate within the native
float observation resolution; the published formula differs by over .001 pu/s
in both probes. A test-only variant reduces baseline native shaft replay
error from about 7.8e-5 to 1.3e-6 rad; remaining state, clock and integration
differences still need explanation. Production equations remain unchanged.

## Isolated regression acceptance

The frozen snapshot starts at `54b4c53c0` and adds only this slice's six scoped
files; it excludes concurrent uncommitted REPCA1 edits. All three Java source
files match that tested snapshot byte-for-byte.

- Focused replay, mutation and existing kernel/attachment tests: 16, no skips.
- Registered SMIB/catalog gate: 1,196, zero failures/errors, one diagnostic skip.
- Complete plugin-core unit gate: 150, zero failures/errors, six optional skips
  (four unavailable KLU-native checks and two external Texas7k AUX fixtures).
- Current core JAR SHA-256 remains
  `f7380328ff7b4d81686a5aae2c172d964013ae74deb81c964050a6a74db0d4ad`.
- No full Texas2k dynamic simulation was launched.

The final integration snapshot also includes the subsequently committed
REPCA1 absolute-reference correction `141978068`, without unrelated uncommitted
files. Its fresh gates pass: **1,196 SMIB/catalog checks** (one skip), and
**152 plugin-core units** (the same six optional skips), all with zero failures
or errors. The two extra units exercise that REPCA1 correction. Scoped source
files match the tested snapshot byte-for-byte. These final reports and source
identities are separately sealed in
`2026-10-18-wtdtb1-powerworld-merged-validation` and reference the immutable
PowerWorld artifact archive above.
