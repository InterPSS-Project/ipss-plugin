# REPCGFMC1U import and coupled SMIB verification

## Scope and status

This slice adds strict native DYR import and dedicated REGFMC1U attachment for
the fifteen-state hybrid plant controller. It builds on the immutable
[foundation](repcgfmc-native-contract-2026-10-18.md) and
[kernel](repcgfmc-kernel-2026-10-18.md) evidence. Runtime family coverage rises
from 24/27 to **25/27**; REPCCU and HPLNTD remain unimplemented. Runtime
availability is not complete independent model verification.

No full Texas2k dynamics are required or run for this slice. Its input inventory
remains a coverage requirement, with small-network verification as requested.

## Implementation contract

- Accept only the documented REPCGFMC1U user wrapper: allocation
  `107 0 7 54 15 20`, seven mixed-type ICONs and 54 CONs. The circuit identifier
  must be quoted. Reject extra/missing fields, invalid allocation, fractional
  integer selectors, nonfinite constants and an inferred unsuffixed alias.
- Resolve the plant after converter records, including reversed record order.
  Attach only to REGFMC1U, without replacing an existing device or plant.
  Honor generator-model removal and explicit record removal.
- Read physical branch P/Q on system base, then convert once to plant base.
  Convert absolute plant Pref/Qref once to machine-base offsets. Route absolute
  voltage and frequency references as offsets from initial voltage and unity.
- Resolve both stored branch directions. Negative first-bus identifiers request
  receiving-end flow per the source contract. A missing measurement circuit
  selects terminal voltage, generator P/Q and zero loss compensation. A found
  but open branch reports zero flow. Frequency bus zero or not found selects
  the effective remote measurement bus.
- Sample parent/child commands atomically at the old and predicted modified-
  Euler endpoints. Advance the plant only once per stage, then refresh
  algebraic outputs. Preserve all eleven existing converter state keys and
  expose fifteen plant states under the `REPCGFMC1U.` prefix.

Unit tests discriminate non-unit machine/plant bases, signed flow with reversed
branch storage, a nonunity transformer tap, frequency-bus fallback and the
predictor/corrector endpoint ordering. Transformer checks compare the attachment
with the core branch-flow API; they are not independent transformer-trajectory
acceptance.

## Independent trajectories

Nine imported coupled profiles compare three bus voltages, physical active and
reactive power, converter speed deviation, all eleven converter states and all
fifteen plant states. Profiles cover flat/fault/trip at 0.5 and 0.25 ms, an
alternate plant MVA base at both steps and a missing-circuit fallback flat run.
The fault lasts three 60-Hz cycles; each trajectory ends at 0.3 seconds.

Five new datasets have ten cold native runs with byte-identical pairs. Four
earlier foundation datasets are reused without modification. Across the three
plant slices there are 46 accepted native runs forming 23 cold pairs, including
the separate kernel replay profiles. Raw data, inputs and hashes are retained;
the embedded resources remain independently hash-checked.

Observed coupled physical maximum errors before tolerance margins:

| Profile | V1 pu | V2 pu | V3 pu | P system pu | Q system pu | Speed deviation |
|---|---:|---:|---:|---:|---:|---:|
| Flat/base/fallback | 2.58e-7 | 2.84e-6 | 5.97e-8 | 2.12e-7 | 3.11e-5 | 1.6e-18 |
| Fault, 0.5 ms | 7.56e-6 | 4.78e-6 | 4.16e-7 | 6.43e-5 | 7.73e-5 | 1.06e-6 |
| Fault, 0.25 ms | 4.56e-6 | 3.28e-6 | 2.68e-7 | 6.40e-5 | 4.97e-5 | 1.06e-6 |
| Trip, both steps | 6.17e-6 | 6.16e-6 | 3.19e-7 | 2.12e-7 | 3.11e-5 | 1.3e-18 |

The comparison declares individual fixed per-profile/channel limits, generally
25 percent above the measured maximum with a small numerical floor. It retains
initial reactive-state offsets rather than fitting them away. Flat terminal
voltage drift is required below 1e-7 pu. Trip tests require all 26 states to
freeze and physical injection to become zero; a retained internal reference is
not interpreted as continued injection.

Two negative-time history samples and duplicate event rows remain in raw
references. Regular endpoint comparisons account for them explicitly but do
not establish instantaneous post-callback network parity. This remains an open
acceptance item, not a silently discarded successful comparison.

## Rejected measurement oracle

The native negative-first-bus discriminator reported an undefined measurement
branch and silently used terminal fallback: effective loss coefficients became
zero and the remote-voltage state equaled terminal voltage. Therefore those
completed runs, despite convergence, are **rejected as receiving-end measurement
oracles**. Another attempt stopped during initialization. Keep all attempts as
diagnostics, separate from the five accepted datasets.

A positive-first-bus, absent-circuit discriminator independently confirms the
terminal fallback path. Signed receiving-end behavior follows the source
contract and has direct core-flow tests, but independent signed-branch and
PowerWorld trajectory acceptance remain open. A convergence label alone must
never certify the intended measurement routing.

## Remaining acceptance work

- Independently establish signed receiving-end and transformer trajectories.
- Compare instantaneous pre/post-event callback sides explicitly.
- Extend coupled zero-time block and reconnect/reinitialization lifecycle tests.
- Resolve or bound the documented floating-point frequency floor and FFR
  derivative-history/timer differences without changing published equations.
- Implement REPCCU and HPLNTD and close the broader plan's outstanding profiles.

Final acceptance gates: 78 focused checks, 318 plugin-core tests (six optional
skips), and 1,373 registered SMIB/model/solver checks (one existing diagnostic
skip), with zero failures or errors. The last gate includes the SMIB suite and
supplemental catalog, static-load voltage-curve, staggered solver and runtime-
plan tests. Earlier diagnostic-only runs are not acceptance gates.

The source audit matches 25 owned Java/fixture/resource files to the tested
snapshot and checks 2,061 other baseline Java/POM files against Git. It verifies
ten accepted new native runs, all five cold pairs, nine finite 33-column
comparison traces and the 1,021-file foundation and 1,079-file kernel archives.
It independently rejects the four signed-branch attempts described above.
The private evidence archive is `2026-10-18-repcgfmc-native-import`.

The tested baseline includes plugin `c6cdf42ca` and its installed core execution-
plan update. Core jar SHA-256 is
`e302580561dfd6a4b6c203fe910bdd082aa1ee212f67e20334a36998781ba187`.
That jar was installed before all three acceptance gates; it is not the earlier
kernel slice's core jar. The eight changed jar entries concern CML expression
evaluation/runtime plans. The earlier dependency remains preserved in its
immutable archive, and this slice archives the exact current dependency.
