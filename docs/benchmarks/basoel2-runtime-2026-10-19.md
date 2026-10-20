# BASOEL2 explicit-dialect summation runtime

## What is implemented

The typed BASOEL2 kernel now has an AC1C summation runtime, physical measurement
boundary and builder attachment. It reuses the core round-rotor machine.
Callers must explicitly choose one of the two published timer policies:

```java
builder.addBasoel2(busId, generatorId, data,
    Basoel2Kernel.TimerSemantics.POWERWORLD_DOCUMENTED);
```

`WECC_2026_DOCUMENTED` is also available. No default silently selects a
simulator dialect. The native reader still rejects BASOEL2, and its catalog
descriptor remains absent. The approved-list loadable limiter count remains
four; this slice is not complete native BASOEL2 coverage.

The runtime reads machine Efd, machine field current on the exciter base, or
exciter Vfe according to the exact typed selector. Frequency comes from the
terminal bus, not shaft speed. The live exciter voltage reference supplies both
PI limits. AC1C makes its initial reference available before auxiliary
initialization, then compensates the summation contribution. An initially
active V/Hz limiter therefore does not introduce a fictitious field-voltage step.

The existing exclusive OEL port contract is reused without renaming its public
API. Duplicate ownership, disabled/takeover ports, missing physical current and
changed owners are rejected. The auxiliary contributes no load or network
current. Other excitation hosts and takeover remain unsupported.

## Endpoint and network lifecycle

Modified-Euler predictor/corrector stages advance continuous state only. Timer
memory changes once in `afterStep`, after a successful continuous step. Zero-time
calls, repeated observations and network iterations cannot advance it. Disabling
an unaccepted predictor or corrector restores the saved state and inputs.
Failed network iteration restores sampled algebraic boundaries. These bounded
lifecycle checks do not establish every system-level trip/reconnection profile.

## SMIB coverage

Eight profiles at two timesteps and both explicit policies cover an inactive
flat run, an initially active V/Hz flat run, Efd/Ifd/Vfe faults, V/Hz response,
disabled outputs and a zero input filter. Three-phase faults last three cycles
on the 60-Hz system. All 32 profiles use the core GENROU machine and retain
exact pre/post-fault observations, controller states, nearby voltages, both
speeds, relative angle in radians, P/Q and exciter observations.

These tests prove the declared typed policies initialize and couple to the
network; finite fault traces are not independent trajectory acceptance. The
user's RMSE < 0.001 per-channel requirement remains the criterion for future
exact reference comparisons. Existing stricter bounds are unchanged.

## Why automatic native import is still withheld

Twenty-two controlled cold runs retain 24,156 raw rows, explicit state-step
interventions and every exported channel. Both outputs are disabled, or the
OEL gains are zero, so the experiments isolate timer logic without limiter
feedback into the plant. They are diagnostic response tests, not physical
coupled benchmarks or fitted initial conditions.

The reference tool's disabled branch freezes its sensed input and timer logic.
Its long-exposure cooldown is not the published capped-memory countdown.
Unexpired exposure, partial cooldown and threshold hysteresis require separate
branches. Non-grid delay probes at two timesteps distinguish endpoint from
nearest-step expiry hypotheses. Neither phase-machine hypothesis reproduces
every retained profile, so neither is promoted into production native parsing.
No raw clock is shifted, no sample is removed and no controller parameter is fit.

Opaque timer-storage cold differences remain visible in the raw exports.
The normal shared-producer control run remains byte-identical after adding the
explicit diagnostic-only path. Earlier reference archives remain immutable.

## Remaining TODOs

Final gates pass: 180 focused, 431 core and 2,132 scoped SMIB/model tests,
zero failures/errors and seven existing skips, plus 235 offline checks.
The 32 typed SMIB exports repeat byte-for-byte between focused and broad runs,
retaining 48,080 rows per gate and 48 exact post-fault observations. Source
reconciliation covers 1,909 Java/POM files. The earlier 28 exports and both
preceding evidence seals (1,362 and 1,500 files) retain their hashes.
No trajectory tolerance is relaxed. The private `2026-10-19-basoel2-runtime`
archive retains raw references, reports, dependency provenance and an inventory.

- [x] Add explicit-policy AC1C runtime, physical boundaries and typed builder API.
- [x] Test initialization, initially active V/Hz equilibrium and bounded lifecycle.
- [x] Run the 32 flat/fault SMIB profiles with full event observations.
- [x] Retain controlled native timer counterexamples without silently choosing a dialect.
- [ ] Resolve native timer expiry, set/reset and disabled-state semantics exactly.
- [ ] Add exact native schema/import after that contract is demonstrated.
- [ ] Establish per-channel independent coupled acceptance, including physical bases.
- [ ] Extend supported host and trip/reconnection profiles.

No full Texas2k dynamic simulation is run.
