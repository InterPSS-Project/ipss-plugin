# TGOV1 machine-base and turbine-damping SMIB profiles

## Contract and scope

This slice adds native reference evidence for the existing seven-constant
TGOV1 controller at 50 and 200 MVA machine ratings, with turbine damping
Dt=0 and Dt=0.7. Both positive time constants remain T1=0.04 s and T3=0.3 s;
R=0.05, Vmax=1.2, Vmin=0.2 and T2=0.1 s are fixed. No production model,
parser, machine engine or existing comparison tolerance is changed.

The installed model-library table, PDF page 481/printed page 469, requires
positive T1/T3 and places the governor quantities on generator MVA base. The
rendered table and diagram were checked; the source SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The [PowerWorld model documentation](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20TGOV1%20and%20TGOV1D.htm)
has additional rating and zero-time handling. Those extensions are not appended
to the native input or used as native zero-time evidence.

Only the first generator's RAW MBASE field changes between rating profiles.
The independent SMIBs retain 50.001 MW initial active output. Therefore initial
mechanical power is approximately 1.00002 pu at 50 MVA and 0.250005 pu at
200 MVA. These are separate physical profiles, not an assertion that a change
of rating alone preserves the same physical machine trajectory. Machine
reactance/inertia parameters are not fitted or rescaled to match a trace.

## Runnable references and permanent checks

From a fresh checkout with the native runtime configured, an example command
is `python tools/tgov1_base_probe.py 50 damped fine a`. The optional fifth
argument `tight` selects the explicitly different network-solver profile, not
a model-parameter override. Existing output directories are refused.

Eight base/damping/timestep profiles run twice cold, at 0.5 and 0.25 ms.
Four additional runs repeat the zero-damping fine-step cases with network
tolerance 1e-8 and a 200-iteration ceiling. All twenty runs pass native initial
conditions and convergence checks. All ten CSV pairs match byte-for-byte;
machine MVA and all seven governor constants read back unchanged after
initialization. Exact executable/library and probe identities remain private.
The original default-tolerance probe snapshot and the later explicit-tolerance
extension are both retained rather than relabeling old runs with new code.

Each export has ten channels and 1,005/2,005 rows, including negative-time
history and both occurrences at two reference-command events. Fixed-digest
public fixtures preserve the entire CSV and exact runnable RAW/DYR inputs.
The external reference changes by 0.005 pu at nominal 0.05 s and reverses at
0.20 s. Both positive and negative speed deviations occur; valve limits remain
inactive, separating this evidence from the earlier active-limit slice.

The permanent registered test checks strict import and actual machine rating,
machine-base initial power, unchanged zero turbine-rating extension, both speed
signs, the native storage/output identity and pure named-state observations.
Recorded-speed replay compares valve opening, unnormalized reheater storage
and mechanical output on every raw row. The damping relation is checked
independently as `Pmech = turbine - Dt*(speed-1)` on machine base.

The coupled simulation predicts nine separate metrics: both bus voltages,
P/Q on the 100 MVA network base, speed deviation, rotor-angle change from each
simulation's own initial coordinate, mechanical output, valve and storage.
It uses the existing native core GENROU engine, without the PowerWorld-only
test adapter. No initial mechanical boundary is forced in the coupled test.
Replay's forced recorded boundary is explicitly a different experiment.

## Results and preserved discrepancies

Recorded-input valve and mechanical-output errors approximately halve with
the timestep; all three replay errors satisfy the permanent refinement check.
Maximum valve error is 0.000304760 pu at 0.5 ms and 0.000154350 pu at 0.25 ms.
The fine-step storage maximum is below 1.6e-7 pu. Channel-specific bounds
replace the initial diagnostic envelopes; no existing model bound is widened.

Across the coupled default and tight-network profiles, maximum Bus 1 voltage
error is 8.03958e-6 pu, maximum Bus 2 error is 1.05617e-5 pu, and maximum speed
error is 6.23403e-7 pu. Maximum rotor-angle-change error is 0.00114650 degrees.
P/Q maxima are below 0.011 MW and 0.0053 Mvar, respectively. These are bounded
profile comparisons, not proof of complete-model or arbitrarily fine accuracy.

The small network/angle error floor does not consistently improve under
timestep halving. Tightening native network tolerance alone does not remove it:
at 50 MVA, zero damping and the fine step, Bus 2 error changes from
1.05616e-5 to 1.04424e-5 pu; at 200 MVA it changes from 5.41866e-6 to
5.18101e-6 pu. This rules out that setting alone as an adequate explanation.
No gain, initial state or physical parameter is changed to hide this floor.
Further machine/network precision localization remains open.

The first comparison attempt stopped because the actual native fine-step
endpoint is about 0.50000936 s, beyond the requested 0.5 s. The test now
calculates enough whole steps to bracket that endpoint and interpolates at
the exact exported time. It does not discard, clamp or extrapolate the final
sample. Negative-time history uses the unforced initial equilibrium. Both the
original failed report and the corrected comparisons are retained.

## Slice checklist and remaining work

- [x] Preserve twenty cold references, ten exact pairs and thirty hashed fixtures.
- [x] Verify base, damping, history/event observations and recorded-input refinement.
- [x] Compare nine coupled metrics on every raw row at unchanged physical inputs.
- [x] Test and reject network tolerance alone as the explanation for the small floor.
- [x] Pass 21 focused checks, 410 plugin-core checks (six existing skips) and
  1,624 scoped SMIB/model/solver checks (one existing skip), with zero failures
  or errors, plus four offline probe guards and six language-policy checks.
- [x] Freeze references, final comparisons, source snapshots, diagnostics and
  reports in the private slice archive with a SHA-256 inventory; deliver a
  scoped commit without including unrelated worktree changes.
- [ ] Localize the residual machine/network precision floor independently.
- [ ] Add negative-power, initialization-outside-bounds, lifecycle and additional
  fault profiles; audit native rejection rules separately from typed zero-time
  and rating/deadband extensions before complete TGOV1-family verification.
- [ ] Complete the separate PowerWorld post-initialization parameter readback.

No full Texas2k dynamics are run. These checks extend model-specific evidence;
they do not close the overall approved-model coverage plan.

The isolated snapshot is audited against 1,818 unchanged Java/POM files and
all three owned Java files. Its implementation baseline is `e6b4d4191`;
the intervening `4c4502e22` commit changes only a separate benchmark document.
The fixed core dependency hash and final report classpaths are recorded with
the private source/reference/report evidence. The pre-existing unrelated
documentation edit and untracked comparison test remain outside this slice.
