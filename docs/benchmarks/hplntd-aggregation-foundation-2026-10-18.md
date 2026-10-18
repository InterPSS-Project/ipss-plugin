# Hybrid plant data and multi-aggregate dispatch foundation

## Status

This slice implements the exact typed data contracts and a reusable, immutable
multi-unit aggregation/dispatch service. It does **not** implement the plant's
thirteen-state controller, the downstream two-state lags, production parsing,
or network attachment. Runtime family presence therefore stays **26/27**;
HPLNTD remains the family without a loadable runtime.

The approved-list spelling is HPLNTD. The installed native contract is the
`HPLNTDU` bus user model plus per-unit `REAXDU` interfaces. Those names and
wrappers are retained explicitly; no standard-name alias or dummy catalog
entry is added to increase the coverage count.

## Frozen source contract

The Model Library section 36.4, printed pages 1023-1035, and section 27.2,
printed pages 907-909, were read and their diagrams/weight equations visually
reviewed. Source SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
Executable/library provenance is retained in the private native manifests.

- `HPLNTDU`: `USRBUS`, type 504, allocation 35 ICON / 61 CON / 13 STATE / 45 VAR.
- `REAXDU`: `USRMDL`, type 107, allocation 9 ICON / 8 CON / 2 STATE / 7 VAR.
- Downstream relative VAR links are one-based: 37, 38, 39, 42, 43, 44, 45 in
  the frozen exact interface. Invalid links remain identifiable, not remapped.
- Plant parameters use plant MVA base, except MSS thresholds which use Mvar.
  Unit parameters and received raw signals use machine MVA base.
- Upper-voltage freeze, frequency rate limits, branch-not-found ignore rules,
  enabled/disabled Q output and multi-unit membership distinguish this model
  from REPCC. It must not be implemented by aliasing a REPCC runtime.

PowerWorld represents the related REPC_D as a bus model with downstream
parameters combined into that model, rather than the separate native interface.
Its layout must not be spliced into the native wrapper schemas.
[PowerWorld REPC_D documentation](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Bus%20REPC_D.htm)

## Implemented aggregation

`HplntdData` and `ReaxdData` retain exact typed fields and defensive arrays.
Basic flags, layouts, finite values and bounds are checked. The downstream
effective time is max(entered time, 2*step), without rewriting the entered
constant. Controller selector/initialization policies are not inferred from
these data holders.

`HplntdAggregation` calculates an atomic snapshot for a fixed set of device
identities. It permits live eligibility/ratings/weights to change, rejects
duplicate/replaced identities and numerical overflow, and currently requires
nonnegative participation factors. The service has no static registry or
network side effects.

For eligible units, with rating Mi and sharing factors Kzi/Kwi:

```
onlineMVA = sum(Mi)
plantMVA  = configuredMVA > 0 ? configuredMVA : onlineMVA
weightPi  = Kzi*Mi / sum(Kzj*Mj)
weightQi  = Kwi*Mi / sum(Kwj*Mj)
rawPi     = deltaPplant * plantMVA / Mi
rawQi     = deltaQplant * plantMVA / Mi
deltaPi   = rawPi * weightPi
deltaQi   = rawQi * weightQi
```

The raw input and weighted increment are separate values; callers must not
apply both or multiply the sharing factor twice. Physical MW/Mvar increments
sum to the requested plant increment when that axis has participants.
Initial plant references are computed once from downstream initial commands
and stay fixed numerically when a unit trips or returns. An ignored unit
receives an explicit no-command indication, not an instruction to zero its
held outputs. Zero participation on one axis does not ignore the other axis.

The service's calculated MVA is the actual eligible-device sum. The native
allocator initially puts a configured manual base into its calculated-base
plot slot instead; the observation adapter distinguishes that plot value from
the physical device-rating sum. Pause views clear calculated reporting fields
without changing the held plant base, initial references or unit identities.

## Independent small-case evidence

Seven exact datasets have fourteen cold native runs; every paired RAW, DYR,
probe and complete raw CSV is byte-identical. Each run has 88 channels plus
time, both downstream units, all thirteen plant states, both two-state unit
vectors and every interface VAR. Initial quality and network convergence pass.
The two-unit four-bus testbed has 50- and 100-MVA units on a 100-MVA system.
Real weights are 1 and 2; reactive weights are 2 and 1. The automatic plant
base is 150 MVA, with weighted sums 250 and 200 MVA.

Profiles cover automatic-base flat run; reference steps at 0.05 s at two
timesteps; unit 4 trip at 0.10 s at both timesteps; and two matched explicit
200-MVA flat inputs differing only in pause scheduling. Runs end at 0.20 s.

Six accepted algebraic-interface profiles retain every raw row, initialization
history and pause/resume duplicates. Six 36-column paired traces compare
sixteen base/reference/input/weight quantities, using separate channel bounds.
The largest dispatch-input error is below 1.50e-8 pu; weight errors are below
1.20e-8; initial-reference residuals are below 7e-13. Reported MVA values agree
exactly. Bounds were tightened from the diagnostic gate to approximately ten
percent above each observed maximum, with small exact/inactive floors.

These are **recorded-input algebraic comparisons**, not an InterPSS dynamic
simulation of HPLNTDU/REAXDU. Plant controller outputs are reference inputs to
the dispatch comparison. Underlying lag/controller trajectories are preserved
as evidence but are not claimed to have been independently implemented.
After trip, the ignored unit's interface VARs remain held. Its unused native
lag states continue moving; output hold is not falsely equated to state freeze.

## Explicit-base pause counterexample

With identical data specifying 200 MVA and downstream ratings summing to
150 MVA, the native paused run changes its held plant base from 200 to 150 MVA
at the first resume, while initial Pref0/Qref0 stay fixed. This produces a
no-reference-change active command difference exceeding 0.05 pu by 0.20 s.
The configured CON remains 200; no physical parameter or initial state is
modified between the matched cases.

The twice-cold control with no intermediate pauses remains exactly at 200 MVA
with zero command increments. This isolates the observed reset to pause/resume
handling. A permanent counterexample test and eight-column diagnostic trace
retain the mismatch; the production aggregation follows the documented fixed
manual base rather than copying that behavior. The failing initial blanket
comparison is preserved, not erased or converted into loose acceptance.

## Remaining TODOs

- Implement the thirteen-state plant kernel, independent initialization and
  STATE/VAR observations; do not reuse REPCC unless each differing equation
  and selector is handled explicitly.
- Implement the two-state downstream lag, active limits, ignored-unit output
  semantics and timestep corrections, with independent discriminators.
- Add exact production wrappers, deferred binding, unique ownership and a
  stage-coherent shared plant advance that does not run once per downstream unit.
- Add actual coupled multi-unit comparisons for references, frequency, low/high
  voltage freeze/recovery, faults, trip/restart and pause/resume at two steps.
- Verify zero/negative sharing combinations, zero rating/no members, base-load
  behavior, missing branches/links and MSS unit conversion/physical switching.
- Independently resolve the explicit-base native pause reset and obtain an
  activating PowerWorld comparison. The current slice does not certify either.

No full Texas2k dynamic simulation is required or run.

## Validation and archive

The final gates on plugin Java baseline `40eb7b164` pass with zero failures or
errors: 41 focused checks, 356 plugin-core tests (six optional skips), and
1,429 selected SMIB/model/solver tests (one existing diagnostic skip).
The subsequent `d2bab9e50` commit changes only unrelated documentation and
does not change that tested Java/POM baseline.

The source audit matches all 23 owned Java/resource files to the tested
snapshot and all remaining baseline Java/POMs to Git. It checks every file
in the fourteen native manifests, all seven exact cold CSV pairs, the six
accepted 36-column paired traces, the preserved eight-column counterexample,
generated inventory text and the previously sealed REPCC evidence archive.
The private archive is `2026-10-18-hplntd-aggregation-foundation`; diagnostic
failures and raw independent traces are retained without overwriting them.

All report classpaths use the isolated frozen companion core `38e269ecf`,
SHA-256 `fbd9f015887b51ba438a538120cff00d0a724e76fd017674ec28fb0a924ab125`.
No shared dependency installation is replaced.
