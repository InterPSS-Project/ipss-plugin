# PSS6C/PSS7C compensation and canonical-filter verification

Status: source-backed corrections and bounded profile comparisons accepted.
Final scoped regression gates pass. This is not complete-model parity.

## Three distinct corrections

The installed source library's PDF pages 122-123 and 126-127 define the
compensated voltage and canonical filters. The equation panels were rendered
and visually checked. The [PowerWorld PSS6C diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_SVG/Generator/Stabilizer/Stabilizer%20PSS6C_0001.svg)
independently confirms the negative canonical input and cascaded integrators.
Source-document/diagram hashes are retained with the private evidence.

1. Both controllers used a current-base conversion for Xcomp, which is an
   impedance. That omits a voltage-base ratio when nameplate and bus nominal
   voltage differ. The calculation now converts Xcomp with the machine's
   impedance-base factor. Two initialized-machine guards fail before the fix.
   Direct physical phasors verify 13.2/16.5/19.8 kV nameplates on a 16.5 kV bus.
   This voltage-base result is analytical, not a native unequal-nameplate test.
2. PSS6C used `u - sum(states)` instead of the published
   `-u - sum(states)`, and fed upstream sums directly into later integrators.
   The corrected derivatives are `e/Ti1`, `x1/Ti2`, `Ki3*x2/Ti3` and
   `Ki4*x3/Ti4`. The same negative error feeds the output's K0 term.
   No gain, time constant, limiter or initialization value is fitted.
3. PSS7C's canonical and ramp differential equations remain unchanged.
   Its pure-lag ramp tails store T9 times their native published coordinate.
   Named-state observation now divides only those tails by T9. Lead-lag and
   unused coordinates retain their existing mapping; solver storage and keys
   are unchanged. Guards check observation purity at T9=0.05 and 0.2 seconds.

The PSS6C input sign was not guessed from a plotted curve: magnified panels
show both negative inputs at its canonical summing junction. PSS7C has a
positive direct input and negative feedback and must not inherit that sign.

## Independent profiles and actual scope

Eight new profiles cover both models, two physical machine MVA/compensation
settings and 0.5/0.25 ms steps, each regenerated twice cold. The two settings
are 50 MVA with Xcomp=0.2/Tcomp=0.02, and 200 MVA with
Xcomp=-0.2/Tcomp=0.1. These are distinct physical machine cases, not a claimed
base-equivalent network transformation. All other dynamic inputs are unchanged.

All sixteen runs pass native initialization/convergence checks. Every raw
channel/timestamp matches its cold repeat. Each profile retains 605/1,205 rows,
including negative history and event-side duplicates; no callback rows are
dropped. PSS6C exports 22 channels and PSS7C exports 31. The source generator is
the existing core GENROU. The event is a three-phase j0.2 pu Bus 1 fault from
0.05 to 0.10 seconds, followed by recovery through 0.30 seconds.

The public regression resources retain every raw row and exact RAW/DYR inputs
with fixed digests. The permanent auditor checks source/runtime/input/output
hashes, actual allocated ICON/CON/STATE/VAR counts, native stored-parameter
precision, both machine MVA bases and initial compensation/input meanings.
The largest independent initial compensation-state discrepancy is 2.79e-6.
The accumulated native clock is preserved, including a small stop-time error.

Recorded voltage phasors and P/Q are replayed through the production controller.
All ten/nineteen stabilizer states and Vstab are scored at every raw row.
Network measurements are forced inputs, not independently predicted outputs.
Bounds are fixed separately for every profile/channel after resolving the
structural discrepancies. Compensation-state and output maxima must decrease
under step halving; single-precision floors in other tiny coordinates are not
misclassified as a remaining structural discrepancy.

For the 50-MVA PSS6C profile, first-integrator replay error falls from the old
nonconvergent 0.002745 to 2.58e-7 at 0.5 ms and 6.63e-8 at 0.25 ms.
Vstab error falls from 0.02293 to 6.07e-5 and 3.03e-5. PSS7C's 50-MVA
pure-lag observation discrepancy, previously about 4.14e-4, becomes about
1e-7 without changing its integration equations.

The largest compensation-state residual is 0.023681 at the first clearing
callback and decreases to 0.012300 at half step. Raw event-side histories do
not provide the missing instantaneous solved event-post input to the replay.
These residuals are explicit, not removed by dropping rows or shifting time.

## Coupling, guards and remaining work

Eight strict-import InterPSS runs verify native field initialization, stationary
pre-fault named states, finite states/speed/field voltage and actual fault
voltage depression. These new runs are not independent full-network trajectory
acceptance. The older 100-MVA coupled reference tests retain their original
checkpoints, exclusions and tolerances. PSS6C's older maximum Vstab discrepancy
improves from 0.009389 to 0.000844, and Efd from 0.037626 to 0.006826;
network/first-callback differences remain. PSS7C's boundary trajectory is unchanged.

- [x] Resolve both initialized-machine base failures against physical phasors.
- [x] Resolve nonconvergent PSS6C canonical errors using both published diagrams.
- [x] Correct PSS7C observation coordinates without altering its equations.
- [x] Cover predictor/corrector stages, zero steps, reinitialization, immutable
  observation and explicit cascade discrimination.
- [x] Cold-repeat/audit sixteen native runs and score all eight complete replays.
- [x] Pass eight strict imported three-cycle fault profiles and the older
  unchanged-bound coupled reference comparisons.
- [x] Reject the deliberately restored old canonical/mapping behavior in three
  focused assertions; restore both production sources exactly afterward.
- [x] Pass 107 focused, 406 plugin-core (six existing optional skips) and
  1,579 scoped SMIB/model/solver checks (one existing diagnostic skip), with
  zero failures/errors. Eighteen offline artifact/initialization/language
  checks pass. Preserve the source/reference/report hashes for the scoped
  commit in private archive `2026-10-19-pss-compensation-canonical`.
- [ ] Expand other input selectors, active limits, lifecycle events and T9
  native profiles; close exact instantaneous event-side and coupled parity.

An early test lookup error, an uninitialized-machine exploratory point and a
Windows resource-sync limit are retained as setup diagnostics, not model proof.
The initial cascade guard also missed excitation through a positive transducer
lag; its test-only transducer is algebraically bypassed to expose the first
predictor. The later independent native and mutation guards verify the defect.
No full Texas2k dynamic simulation is run for this slice.

The final tested baseline is `100ffc099`. Its source audit matches 1,804
unchanged Java/POM files plus seven owned Java files, with no unexpected Java
sources. The published evidence distinguishes exploratory/setup failures,
the six-test mutation gate and final seven new model checks. The mutation
targets the kernel/replay subset before the additional coupled smoke method;
it is not independent validation of that later smoke method.
