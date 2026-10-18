# WTDTB1 / WTDTBU1 foundation: incomplete model

This slice adds a published-diagram kernel and typed wind-stack support, not an
accepted native implementation. Neither identifier is registered in the DYR
importer/catalog. Approved renewable coverage remains **17 loadable, ten missing**.
No native trajectory tolerance has been relaxed to accept the remaining errors.

## Frozen source and record contract

PSS/E 36.7 Model Library, physical pages 897-898 (printed 885-886), SHA-256
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The native diagram was rendered and visually inspected. Standard data order is
`H DAMP Htfrac Freq1 Dshaft Tp`: six CON, five STATE, three VAR. Freq1 is Hz.
States are shaft twist angle, turbine speed deviation, generator speed deviation,
rotor angle deviation and electrical-power measurement filter. VARs are
aerodynamic power, initial slip and aerodynamic-model flag.

The installed simulator accepts the legacy USRMDL allocation
`WTDTBU1 103 0 0 6 5 3`. Its entire baseline CSV equals the standard WTDTB1 CSV.
The rejected allocation using model type 15 is retained as failed evidence;
it is not an accepted alternate schema. Flat legacy aliases are not inferred.

The independent [PowerWorld WTGT_B description](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20WTGT_B.htm)
and linked SVG were inspected. That dialect has eight inputs
`Ht Hg DShaft KShaft MWCap W0 Tp Damp`; it must not be read as the six-CON
native record. Its published torque-balance diagram agrees with the kernel.
No new PowerWorld runtime trajectory is claimed by this slice.

## Implemented foundation

- Five source-diagram coordinates, two-/one-mass branches, filtered electrical
  power emulation, and the documented aerodynamic fallback with inactive fifth state.
- Explicit modified-Euler predictor/corrector and opt-in two-step stepping.
- Immutable semantic state observations and reinitialization of integration history.
- A common drivetrain interface preserving the existing WTDTA1 API.
- Typed wind-stack selection: pitch/torque controllers are ignored for emulation
  without aerodynamics and reactivated for the aerodynamic fallback.
- A Tp=0 zero-duration update refreshes electrical power without advancing mechanics.

Thirteen focused tests cover these branches and source-diagram equations. They
do not prove native trajectory parity. Non-unit initial speed, mode changes and
full lifecycle/native attachment still require independent profiles.

## Native reference audit

Native PSS/E 36.7, DSMDL library SHA-256
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
The small network has REGCA1/REECA1, WTDTB1 and a GENCLS infinite-bus source.
Baseline constants are `4.5 1.5 .6 2 .4 .1`; one-mass uses zero fraction/frequency,
and aerodynamic profiles add WTARA1. A three-cycle fault is applied at 0.05 s
and cleared at 0.10 s. Reference output retains every raw sample/event side.

Six profiles (two-mass, one-mass and aerodynamic, each at 0.5 and 0.25 ms)
were generated twice cold. All six pairs have identical complete CSV hashes.
The audit verifies every manifest input/output hash, layout, finite values,
monotonic clocks, 14 channels and both duplicate event timestamps. Raw sample
counts are 2,005 at 0.5 ms and 4,006 at 0.25 ms, including prehistory.
Actual final native clocks are 1.00001871586 and 1.00022315979 s, respectively;
no assumption of an exact decimal output clock is made.

Immutable diagnostics and audit are retained in the private verification archive
`2026-10-18-wtdtb1-foundation`. The failed DSTATE-query probe is excluded from
accepted reference profiles because the API returned errors. The state-kick,
deep-fault and zero/damping discriminators are diagnostics, not accepted parity.

## Unresolved discrepancy

Recorded-power replay of the unmodified published equations gives two-mass
maximum shaft-angle errors of approximately 7.78e-5 rad at 0.5 ms and 7.79e-5 rad
at 0.25 ms. Generator-speed errors are approximately 1.79e-6 and 1.80e-6 pu.
The filter error approximately halves (1.77e-5 to 8.88e-6 pu), but the mechanical
errors do not. The aerodynamic branch has a similar non-converging mechanical
error with its fifth state correctly inactive.

Changing to two-step integration, rounding committed states, emulating native
single-precision STATE/STORE recurrence, and applying a test-only shaft
torque-to-power conversion did not resolve the discrepancy. None justifies a
production equation change. Distinguish interface measurements, native stage
sampling and actual model-version equations before accepting a trajectory.

## Regression snapshot

Baseline plugin `d38ccf047`; current installed core 1.4.3 includes compound-block
access required by that baseline. Core JAR SHA-256:
`f7380328ff7b4d81686a5aae2c172d964013ae74deb81c964050a6a74db0d4ad`.
The older pinned `1.4.3-regfma-native` JAR cannot compile this newer plugin
baseline; that compile failure is not a model-test result.

- Focused kernel/stack tests: 13, zero failures/errors/skips.
- Registered SMIB/catalog gate: 1,193, zero failures/errors, one diagnostic skip.
- Complete plugin-core unit gate: 150, zero failures/errors, four optional skips.
- No full Texas2k dynamic simulation was launched.

Remaining acceptance: resolve native mechanics; independently verify non-unit
initialization and lifecycle; run the exact PowerWorld dialect; implement strict
parser/builder attachment and rejection tests; add native full-solver SMIB
comparisons; rerun gates before declaring the model loadable or complete.
