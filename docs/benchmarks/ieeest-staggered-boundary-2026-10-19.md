# IEEEST opt-in staggered integration boundary

## Implemented, without replacing the controller

The existing opt-in `StaggeredEulerSolver` now binds IEEEST. The failing-before
test rejects its unsupported controller; after the adapter is added, the same
GENROU/GENCLS/ESST1A/IEEEST SMIB binds both existing core machines and twenty
allocated integration coordinates. No substitute generator or duplicate filter
implementation is introduced. Default modified-Euler behavior is unchanged.

`IeeestStabilizer` implements the existing `StaggeredEulerParticipant` contract.
Its seven-coordinate port reads and updates the running CML blocks:

| Port index | Existing storage |
|---:|---|
| 0 | First filter output storage |
| 1 | First filter output rate |
| 2 | Second filter first storage |
| 3 | Second filter second storage, dormant in its first-order limit |
| 4 | First lead-lag storage |
| 5 | Second lead-lag storage |
| 6 | Terminal-filter storage |

These are integration coordinates, not a replacement for the existing five
named-block keys or the seven native observation slots. Evaluation refreshes
algebraic inputs at zero duration and reads slopes without advancing continuous
states, voltage memory or accepted clocks. A full second-order/numerator unit
discriminator checks all seven derivatives against the explicit equations,
including optimized/interpreted evaluation. Array offsets, finite-state writes,
failed reinitialization, inactive-state freezing, warm reactivation and changed
attachments are covered separately.

The solver owns derivative history and advances the vector with its existing
two-history rule. IEEEST does not silently select this rule when imported.
Select the solver explicitly before initialization:

```java
algorithm.setSolver(new StaggeredEulerSolver(algorithm));
algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
```

The method flag selects the underlying derivative API; the selected solver
owns the opt-in integration recurrence. The ordinary solver remains available
and all established reference fixtures and bounds remain unchanged.

## Event and endpoint contract

Mode 6 rebases voltage memory after a settled zero-time network event while
retaining the held derivative. It retains ordinary slope history rather than
applying the coordinator's general event average. The new default interface
policy remains `true` for every existing participant; IEEEST overrides it only
for mode 6. The native mode-5 voltage discriminator supports the ordinary event
average, while the mode-6 records support the separate sampled-memory rule.

The controller's voltage derivative updates after the final network solve and
algebraic refresh, before endpoint monitoring. It cannot advance during trial
evaluation. Explicit discontinuities and native network events use the same
settled-memory hook. Reactivation starts from the current slope, not stale
history; attachment changes still require reinitialization.

This establishes the port and lifecycle equations. It does not independently
verify every selector, denominator/numerator shape, bypass or trip profile.

## Complete retained comparison protocol

Twelve existing local/remote flat/fault/reference profiles are replayed at two
timesteps through the actual model graph. Their twenty-four prior cold runs,
all raw reference bytes, 139 fixture parts and existing acceptance gates remain
unchanged. Every raw row and all seven controller observation coordinates are
retained. Separate coupled runs retain every one of the 38 non-time channels:
machine, exciter and controller states, both memory VARs, nearby voltage/angle,
frequency, power, speed and field/controller outputs.

The comparison records raw time, step ordinal, event side, native integration
time and coordinator time separately. Each positive raw-clock increment must
match addition in the original binary32 clock. Negative history rows map to
the unadvanced initial equilibrium, and duplicated rows select the explicit
post-event INITIAL-network snapshot. Reference assignments retain their own
post-assignment snapshot. No sample is discarded or interpolated across a jump,
and there is no fitted time shift. The clock normalization follows the declared
step count, rather than treating accumulated binary32 clock roundoff as a model
phase correction.

## Diagnostics, not new trajectory acceptance

The matched-rule recorded-input Vstab discrepancy is at most 1.970e-5 pu,
compared with the default-method fault diagnostic near 0.1 pu. Coupled fault
Vstab discrepancies are local 4.113e-4/2.093e-5 and remote 4.468e-4/3.986e-4 pu
at coarse/fine steps. Across all coupled active profiles the largest Vstab
diagnostic is about 1.281e-3 pu. These observations localize an important
integration-stage contribution; they are not full-model acceptance.

Automatic generation of coupled acceptance envelopes from the observed errors
was rejected by safety review because it could normalize discrepancies. That
generator was removed without execution. No resulting bounds or helper are
committed, and provisional new-method residual limits were also removed.
All new matched/coupled residuals remain diagnostic. Existing accepted bounds
are not changed. Formal acceptance needs an independently specified tolerance
policy or equation/precision-derived bounds, not limits fitted to these runs.

## TODO

Final gates pass 143 focused, 427 core and 1,831 scoped SMIB/model/solver tests,
with zero failures/errors and seven existing skips, plus 59 offline checks.
Source reconciliation covers 1,875 Java/POM files and 139 unchanged fixture
parts. All forty established default-solver playback files are byte-identical
to the prior sealed version. The supported-model comment update produces the
same compiled solver bytes in the focused and final core builds.

- [x] Failing-before unsupported binding and actual CML state-port implementation.
- [x] Full-vector derivative equations, pure evaluation and optimization parity.
- [x] Initialization/status/activation/attachment lifecycle guards.
- [x] Retain all recorded-input and all-channel coupled rows with event sides.
- [x] Complete final core and scoped SMIB/model/solver gates and evidence seal.
- [ ] Establish independent trajectory criteria and accept or reject all new
  matched-method channels against those criteria.
- [ ] Broader selectors, pole/numerator/bypass branches and native lifecycle
  profiles; complete IEEEST acceptance under the development plan.

No full Texas2k dynamics are run. A green structural/regression gate does not
turn these diagnostic trajectories into a VERIFIED model.
