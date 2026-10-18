# REGFMC1U eleven-state kernel and native selector replay

Status: implemented kernel with bounded native recorded-voltage replay, not a
loadable device, complete native conformance or a coupled InterPSS SMIB run.
The approved-family inventory remains 23/27 loadable paths, four missing runtimes.
This slice follows the [native contract and hybrid-boundary foundation](regfmc-native-contract-2026-10-18.md).

## Source and implementation

The native contract remains PSS/E 36.7 Model Library section 21.9, printed
pages 820-828 / PDF 832-840. Its controller diagram and non-windup pseudocode
were visually rechecked. Source PDF SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`;
native DSMDL.DLL SHA-256 is
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
The exact allocation remains USRMDL REGFMC1U / 101 1 3 34 11 27. No standard
REGFMC1 alias or PowerWorld-specific extra CONs are inferred.

`Regfmc1Kernel` stores these eleven native states, in order:

1. Vmeas: terminal-voltage filter.
2. GfmVrefMeas: GFM voltage-reference filter.
3. GfmIqMeas: raw GFM reactive-current filter, positive for injected Q.
4. VoltageIntegral: limited voltage PI integral, separate from clipped PI output.
5. GfmInternalVoltage: internal GFM voltage magnitude.
6. SpeedMeas: absolute GFM speed filter when Fflag=1; unused zero when Fflag=0.
7. SpeedReferenceMeas: absolute reference-speed filter when Fflag=1; unused zero otherwise.
8. GfmPmeas: raw GFM real-power filter.
9. VsmSpeedDeviation: bounded speed deviation.
10. DampingFilter: low-pass storage of D2 times speed deviation, not unscaled speed.
11. GfmAngle: internal angle in the network frame, radians.

Initialization requires a feasible GFL operating point, zero within PI/speed
bounds, positive voltage and a positive declared nominal timestep. GFM starts
with zero P/Q and internal voltage equal to the solved terminal phasor. GFL
carries machine-base P/Q. An initial command/voltage-injection conflict is
rejected explicitly rather than silently offsetting Q or creating a nonstationary
GFM operating point. Native behavior for additional infeasible/non-default
initialization profiles still needs discriminators.

The kernel implements terminal/reference/branch measurements, voltage PI and
internal-voltage lag, filtered frequency droop, clipped damping, bounded inertia,
and angle integration. Te uses the native minimum of two nominal timesteps;
the other zero-time measurement blocks bypass without extra states. Both PI
integral and speed states block only outward motion at their limits and recover
inward. A clipped proportional-plus-integral output does not freeze an interior
integral. Getter calls are pure; zero-time refresh preserves predictor history.
Predictor/corrector steps must match. Reinitialization resets history/references.
Semantic states are exposed as an immutable `ICMLStateProvider` map.

Manual GFM P/Iq inputs remain distinct from GFL P/Q steps and plant V/speed
steps. Raw GFM current/power supply dynamic feedback; only the hybrid boundary
jointly scales both branches for network injection. No current-limit operation
is accumulated into controller state.

## Native selector and active-bound campaign

Twelve new cold runs add six immutable datasets at 0.5/0.25 ms, each repeated:

- `off`: Fflag=0, PQFlag=1, Vflag=0, D1=0.7 and Kf=0.35, with mild Bus 2 fault.
- `bounded`: Fflag=1, PQFlag=1, Vflag=1, D1=0.7 and Kf=0.35, with severe Bus 1
  fault; dE bounds +/-0.002, droop bounds +/-0.01, damping bounds +/-0.005 and
  speed bounds +/-0.00005 deliberately activate both bound directions.
- `frequency-off`: only Fflag changes from the original foundation parameters,
  with the same mild fault, isolating disabled frequency-state behavior.

Each profile applies manual GFM voltage/speed/P/Iq steps at 0.025 s:
0.01 pu / 0.002 pu speed / 0.1 pu power / 0.5 pu current; reverses them at
0.15 s; and restores zero at 0.225 s. Three-cycle faults remain 0.05-0.10 s,
with simulation end 0.30 s. These are declared inputs, not optimized play-in.
The public three-bus network, 50/100-MVA device/system bases and RAW source
impedance 0.015+j0.23 pu remain unchanged.

All twelve new runs pass native initialization and the existing log/CSV quality
audit, with identical cold-pair CSV hashes, exact allocation, ICONs and float
CON checks. Exports contain 608/1,208 samples and 48 channels plus time, including
two negative history rows and five duplicate-event rows. All native states,
VARs, bus voltage/angles, P/Q, angle and speed remain present. The earlier twelve
foundation runs and six raw CSVs remain unchanged. Together there are twelve
datasets from 24 accepted cold runs, not 24 different physical profiles.

## Permanent replay and independent discriminators

`Regfmc1NativeRuntimePartialConformanceTest` integrates from time zero using only
recorded terminal voltage/phase and the four declared manual input signals.
No native STATE is fed back into the kernel. All eleven states and physical
machine-base current/P/Q are checked at each regular endpoint and both event
sides. Negative history rows are retained in raw references but are not integrated
as forward simulation. Each state/channel has its own fixed bound with roughly
25% margin above the measured maximum; there is no parameter or initial-state
fitting. The suite exports repeatable comparison traces to
`target/regfmc-kernel-replay/` and prints maximum errors with their times.

Discriminators establish:

- With Fflag=0, native STATE5/6 initialize and remain zero, even while speed
  reference steps change. A separate Fflag-only profile rules out Vflag or
  priority as the cause. The implementation matches this native unused-state
  behavior rather than advancing the unused filters shown in the generic diagram.
- At the first manual-Iq step, native PI-integral motion is 0.75 of a full Euler
  increment, exposing native stored/staggered event history. Default InterPSS
  modified Euler is preserved; it is not replaced with an implicit corrector or
  tuned to match that event-history fraction.
- In the baseline mild fault, native absolute SpeedMeas remains exactly 1.0.
  Every expected single-step increment is below half of the corresponding float
  spacing at one, but double-precision integration of the same recorded speed
  input departs by more than 5e-6 pu. This is a directly tested rounding floor,
  not evidence that the ideal filter has zero response.
- Native bounded profiles hit both voltage-integral and speed bounds and activate
  the P-priority GFL cap. Unit tests separately check corrector equations,
  non-windup release, independent references, all eight selectors, zero-time
  paths, source/base geometry, pure observation and initialization/reset guards.

State errors dominated by event truncation decrease below 0.65 of their coarse
maximum on step halving. This is asserted for terminal voltage, branch Iq,
voltage PI and internal voltage in base profiles; and the applicable reference,
power, speed and angle states in off/bounded profiles. It is deliberately **not**
claimed for absolute-frequency rounding floors, tiny damping/angle floors or the
current-circle tangent. Smaller steps can make float locking worse.

## Measured scope and residuals

The mild-fault coarse/fine maximum filtered-Iq errors are 0.003414/0.001803 pu;
PI-integral errors 1.376e-5/3.426e-6 pu; internal-voltage errors
4.522e-6/1.283e-6 pu. Speed-deviation error remains approximately 8.31e-7 pu,
consistent with the independently established absolute-speed-filter floor.

The severe base fault has a 0.05208/0.02749-pu filtered-Iq error at the first
post-clearing step. It halves as expected for the retained event-history
difference. Its maximum physical-current errors are 0.003456/0.003484 pu, near
the **GFL current-circle tangent**, not the first clearing endpoint. Coarse/fine
maxima occur at 0.1075001/0.1072495 s respectively. Native/non-native filtered
voltage differ there by only 3.057e-4/7.695e-5 pu, but native Ip is closer to the
tangent in the fine case; the square-root radius amplifies the smaller input
error. A separate localization diagnostic recomputing only this algebraic path
with native filtered voltage reduces those current residuals to 4.423e-5/8.773e-6
pu. This diagnostic does **not** change or replace the actual independent-state
replay. The effect explains why a maximum-over-time current error need not halve.

The bounded selector profile's coarse/fine maximum physical-current errors are
6.230e-4/2.812e-4 pu. Its speed-deviation errors are 4.094e-6/2.066e-6 pu and
angle errors 2.738e-6/2.482e-6 rad. Absolute SpeedMeas reaches a 1.901e-5-pu
fine-step discrepancy, retained explicitly as a native float-floor limitation.
These are scoped regression envelopes, not complete native trajectory acceptance.

## Gates and next work

Final XML audit: 79 focused checks, 298 plugin-core tests (six optional skips)
and 1,338 registered SMIB/model checks (one existing diagnostic skip), zero
failures/errors. All eight new unit checks and five native-runtime checks run;
the six earlier native-contract checks remain registered and green. The final
focused/SMIB runs also enforce the regenerated coverage document.

Twelve scoped source/resource files match the isolated tested snapshot. All
2,049 remaining baseline Java/POM files match HEAD after declared CRLF/LF
normalization; unrelated documentation commits are preserved and the untracked
WECC240/JAX test is excluded. Core 1.4.3 SHA-256 remained unchanged before/after:
`f293ca958f363b814681f76bf310447d826fb5996e3bacb95aa5789a6a4b33e7`.
All 24 native runs pass current hash/quality/cold-pair checks. The prior
915-file sealed foundation archive is also reverified. Twelve finite comparison
traces, complete raw native inputs/logs/outputs and all eleven published states
are retained. Development logs include the initially incorrect unit-test
assumption that a voltage-reference change could not affect GFM P through
nonzero source resistance; that test was isolated rather than changing physics.

Private archive:
`testData/private/verification-artifacts/2026-10-18-regfmc-kernel`.
It contains exact tested sources, the native probes and generation/localization
scripts, all raw runs, comparison traces, rendered source pages, dependency JAR,
Maven XML/logs and a verified SHA-256 inventory. Simulator-driving programs stay
private under repository policy; immutable CSV resources are embedded publicly.

- [x] Eleven-state controller, initialization, stages, zero-time paths and state API.
- [x] Twelve two-step native recorded-voltage/manual-input replays with fixed
  per-state/channel bounds and event-side retention.
- [x] Cold-paired frequency-off, history/rounding and active-limit discriminators.
- [ ] Exact native DYR parser and non-overwriting device attachment.
- [ ] Machine/system-base Norton adapter, compatible plant routing and monitor API.
- [ ] Independently coupled InterPSS flat/fault/trip SMIB comparison, trip/reconnect.
- [ ] Native time-correction and non-default initialization profiles, further
  plant-reference/negative-power/limit-recovery profiles and independent
  PowerWorld acceptance where applicable; resolve remaining precision/callback
  differences before claiming complete verification.

No full Texas2k dynamic simulation is part of this slice. The wider coverage goal
remains active; a typed kernel without an adapter does not raise loadability.
