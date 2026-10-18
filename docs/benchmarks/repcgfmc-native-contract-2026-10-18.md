# REPCGFMC1U native contract and initialization foundation

Status: typed contract and algebraic initialization only. There is no dynamic
kernel, DYR import handler or network attachment yet. Approved renewable runtime
coverage remains 24/27; HPLNTD, REPCCU and REPCGFMC1 remain missing runtimes.

## Source and exact allocation

The reference simulator Model Library section 27.5, printed pages 926-935
(PDF pages 938-947), defines `REPCGFMC1U`, not an inferred standard-name alias.
The allocation is `107 0 7 54 15 20`: seven mixed-type ICONs, 54 CONs, fifteen
states and twenty VARs. The circuit identifier is a quoted character ICON, not
an integer flag. The native probe checks every numeric ICON and float-CON after
initialization and preserves the quoted circuit in its exact input artifact.

The source PDF SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`;
the model-library binary SHA-256 is
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
Source diagrams and loss-estimation equations were visually reviewed. Exact
reference-tool provenance remains in the private manifests.

The controller has four separate outputs: GFL active/reactive power references
and GFM voltage/frequency references. It is not a REPCA1 substitute.
[PowerWorld's model reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Plant%20Controller%20REPCGFM_C1.htm)
is separate evidence: its nonzero flag interpretation and independently corrected
loss coefficients must not be imported into the native contract.

## Implemented foundation

`Repcgfmc1Data` retains all 54 constants in source order and the seven typed
configuration fields. It retains raw values while exposing the documented
frequency-filter minimum of two timesteps and zero-integral/zero-proportional
gain initialization protection. FFR is enabled only when its native flag equals
one. Signed first-bus configuration is retained for future arriving-flow
measurement; this slice does not claim an implemented network measurement path.

`Repcgfmc1Initialization` takes explicitly directed system-base measurements and
returns plant-base targets, loss coefficients, P/Q integral biases, voltage
integral initialization and voltage-reference correction. A zero plant-MVA
parameter selects machine MVA; otherwise plant MVA is used. Both loss coefficients
must be nonpositive before native automatic estimation applies. Missing-branch
fallback uses generator P/Q and terminal voltage, with both losses disabled.
Voltage compensation uses target complex power as current under the published
unity-voltage convention, not division by measured voltage. Disabled voltage
reference diagnostics retain their native zero convention.

Proportional-only voltage control must be capable of producing the initial
reactive target within its voltage-error and reactive-output bounds. The example
gain of two cannot sustain this particular operating point: its required error
exceeds 0.05 pu. Both native attempts were rejected by the initialization-quality
gate. A separately declared gain of eight gives a feasible profile without
adjusting an equation or fitting a trace. The implementation rejects this
infeasible initialization instead of silently changing a gain or limit.

## Independent reference profiles

Sixteen accepted native runs form eight byte-identical cold pairs. The three-bus
testbed has 100-MVA system base, a 50-MVA REGFMC1U converter, a near-infinite-inertia
GENCLS reference and two 230-kV branches. The monitored branch is Bus 2 to Bus 3.
Every export retains 83 channels plus time: all inverter and plant states/VARs,
three bus voltages/angles and converter P/Q/angle/speed.

Profiles cover ordinary flat operation, a Bus 2 three-phase j1-pu fault from
0.05 to 0.10 seconds at 0.5/0.25-ms steps, joint automatic loss estimation,
missing-branch fallback, a 100-MVA plant base, proportional-only voltage control
and terminal-voltage selection. Raw history and both event sides are retained:
605/1,205 samples, two negative-time history rows and two duplicate API/event rows.
The accepted stationary profiles have no observed terminal-voltage drift.

Native input with an entirely unspecified branch stopped during initialization
before producing the required quality marker or manifest; it is not accepted
reference evidence. A specified but nonexistent Bus 1-to-3 branch exercises the
documented fallback successfully. Both attempts remain in the private archive.

The tests compare fourteen initialization quantities and replay loss/compensated-
voltage algebra for every raw sample, including duplicate event rows. They do
not replay fifteen dynamic states or prove independent coupled InterPSS/native
plant-controller trajectories. Initial channel bounds are separate from the
dynamic fault-reference availability claim.

The proportional-only native initialization refresh rounds the voltage error
before multiplying it by the gain. Its refreshed Q target differs from the
initial measurement-filter state by approximately 1.91e-7 pu. Initialization
therefore compares the intended targets against their initial measurement states,
and separately retains the refreshed-output residual. The largest compensated-
voltage algebraic residual is approximately 1.155e-7 pu during the fault;
initialization has a comparable 1.157e-7-pu refreshed diagnostic residual in the
proportional-only profile. Per-channel limits distinguish these float-coordinate
effects from the much smaller loss-equation residuals. No parameter or initial
state is fitted to a trajectory.

## Acceptance and remaining work

All scoped gates pass on shared-branch baseline `18414693e`: 53 focused tests,
307 plugin-core tests (six optional skips) and 1,359 registered SMIB/model/solver
tests (one existing diagnostic skip), with zero failures or errors. This includes
the newly committed exciter corrections, not an earlier isolated baseline.

The source audit verifies 23 scoped source/resource files against the tested
snapshot and the remaining 2,059 baseline Java/POM files against the commit.
Every accepted native file hash, all eight byte-identical cold pairs, native
initialization/convergence quality and the unchanged core dependency are checked.
Rejected probe/initialization attempts and unsuccessful preliminary test gates
are preserved rather than replaced with the final passing reports.

The local private reproducibility bundle is
`verification-artifacts/2026-10-18-repcgfmc-foundation/`. It contains the tested
sources, full gate reports, exact native probes/inputs/logs/raw outputs, rendered
source diagrams, validation summary and SHA-256 inventory. Embedded resources
retain the complete accepted CSVs and raw DYR bytes, with hashes checked in Java.

- [x] Exact typed schema, defensive storage, finite-value guards and base rules.
- [x] Native initialization and every-sample algebraic comparisons.
- [x] Cold-paired native flat/fault and initialization discriminator artifacts.
- [ ] Fifteen-state kernel, all selectors/limits, zero-time and integration stages.
- [ ] FFR activation, duration, ramp-down, re-arm and trip/reconnect semantics.
- [ ] Exact reader, measurement direction/fallback, transformer branch and bases.
- [ ] Four-output REGFMC1U routing and independent coupled SMIB acceptance.
- [ ] Additional native flag/time-correction profiles and independent PowerWorld
  artifacts where supported; no complete model-verification claim yet.

No full Texas2k dynamic simulation is included. Its native source-model inventory
remains a coverage requirement.
