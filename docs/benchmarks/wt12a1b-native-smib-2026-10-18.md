# WT12A1B active-stall controller: bounded SMIB evidence

Status: bounded native controller replay and SMIB regression accepted for this
slice, not complete-model verification. Exact raw runs, source snapshot, reports
and digests are retained under the private verification-artifact directory
`2026-10-18-wt12a1b-native-smib`; the scoped commit is recorded in Git history.

## Exact source and implementation boundary

The standard model has fourteen constants, ordered TR, rmax, rmin, T0, Pmin,
Pset, vt1, t1, vt2, t2, vt3, t3, vt4, t4. Its explicit-machine-ID wrapper is
WT12A1U_B with allocation 105 0 1 14 3 2 and one entered selector. The standard
model allocates an internal ICON too; that allocation is not a fifteenth
user-entered parameter. The ID-less legacy wrapper remains a compatibility
extension, not an exact native record.

The installed model-library diagram (pages 909–911; source digest retained in
private manifests) identifies state 0 as filtered terminal voltage, state 1 as
the ramp integrator and state 2 as the output lag. The table's state ordering
does not match the diagram; observed native channels match the diagram.
Independent public sources are the
[PowerWorld WT1P_B model](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20WT1P_B.htm)
and the [WECC second-generation wind model document](https://www.powerworld.com/files/WECC-Second-Generation-Wind-Turbine-Models-012314.pdf).

The importer now uses tokenized record parameters rather than raw wrapper
positions, so an explicit machine ID no longer shifts allocation parsing.
Exact standard/wrapper paths select native electrical initial-reference power;
their ramp/output states still seed from initial mechanical power. Typed and
ID-less compatibility paths retain the published mechanical reference. No gains
or initial states are fitted to trajectories.

A voltage dip can start another fixed-duration reduction window if low voltage
persists after the prior interval. The previous recovery-only rearming latch
prevented that. Zero-duration observation calls refresh algebraic bypasses
without advancing timers or losing pending integration stages. Repeated
predictors, unpaired correctors and inconsistent stage timesteps are rejected.

## Independent evidence

Six profiles (flat, three-cycle fault, zero filter, below power threshold,
rate-limited recovery and repeated fault) run at 0.5 and 0.25 ms. All twelve
datasets were independently cold-generated twice. All 27 raw channels and
duplicate history/event samples are retained: 4,002 rows at the coarse step,
8,003 at the fine step. The extra fine-step endpoint is retained, not dropped.
Endpoint queries number 2,001 and 4,001. Internal ICON remains zero and is not
interpreted as the diagram's switching signal.

Ten positive-filter datasets replay all three controller states with separate
per-profile/channel bounds. The voltage-filter first-event error falls from
0.002597 to 0.001329 pu on step halving. Fault ramp/output errors fall from
0.000293/0.000150 to 0.000148/0.0000764 pu. Near-flat native float-storage
floors are nonconvergent: fine-step ramp/output errors reach approximately
0.0000436/0.0000495 pu. No artificial state quantization is added.

The two entered-zero-filter runs do not behave as an algebraic native bypass;
their maximum voltage-state discrepancy is about 0.436 pu. They are explicit
diagnostics, not accepted parity. Twelve coupled runs and standard/wrapper
equivalence tests verify attachment, finite states and reduction activation;
they do not establish independent full-network trajectory agreement.

PowerWorld fault, zero-filter and sustained-dip profiles also have two steps
and two cold repeats (twelve runs), without auto-correction or overrides. All
fourteen constants round-trip unchanged through initialization. The exported
eight-channel trajectories retain all 2,003/4,003 samples. Changing only TR to
zero changes only the voltage-filter state: every other exported channel is
identical. This differs from the filtered-trigger interpretation. These traces
are behavioral diagnostics, not a reason to change the shared published model.
The fault is j0.1 system pu; its native equivalent is j10 ohm on the fixture.

An early probe rejected its own incorrect internal-ICON allocation expectation;
an early PowerWorld input omitted the machine ID and used the wrong impedance
unit. Neither exploratory attempt counts as accepted model evidence.

## Checklist

- [x] Fix explicit-ID wrapper parsing and distinguish initialization dialects.
- [x] Test repeated reduction, zero-time observations and integration stages.
- [x] Pass sixteen integration parser/controller/replay/SMIB checks and five
  plugin tokenizer checks in the first focused reactor run (21 total).
- [x] Audit all 24 native runs, input constants, hashes and cold equality.
- [x] Audit all twelve PowerWorld runs, parameter round-trip and TR discriminator.
- [x] Complete registered SMIB/model/solver regression: 1,551 tests, one existing
  diagnostic skip, zero failures/errors. Plugin-core: 401 tests, six optional
  skips, zero failures/errors. Six new offline probe checks pass.
- [x] Prove targeted tests reject incorrect import allocation, mechanical-only
  native reference and recovery-only rearming. Three deliberate mutations fail;
  every mutated source is restored and the complete Java/POM baseline audited.
- [x] Pass the final restored-source gate: 42 tests, no skips/failures/errors.
  Audit all 1,789 unchanged Java/POM baseline files and ten owned Java sources.
- [x] Retain raw evidence/reports with final source hashes for the scoped
  implementation commit. Preserve the first broad run's two stale inventory
  count/document failures separately from the clean final 1,551-test run.
- [ ] Resolve native zero-TR callbacks, float-state floors, complete coupled
  parity, further threshold/rate boundaries and trip/reconnect behavior.

The older WT12A1 profile is a different model and is not used as evidence here.
No full Texas2k dynamic simulation is required or run for this slice.
