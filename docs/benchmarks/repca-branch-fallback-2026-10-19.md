# REPCA1 branch fallback and measurement-source diagnostics

## Corrected behavior

The previously reviewed native contract, section 27.3/PDF pages 922–924,
requires generator-power feedback when the requested monitored branch is
absent. InterPSS previously threw during initialization for a missing edge
or circuit. Two new guards reproduce those initialization errors before the
correction. The source document hash and original contract are retained in
the [preceding plant slice](repca-native-plant-2026-10-19.md).

The native path now falls back to local generator P/Q while retaining the
configured regulated-bus voltage and frequency for compensation and control.
Entered selectors and parameters are not rewritten. A warning identifies the
missing branch and retained regulated bus. Historical typed constructors keep
their existing strict missing-branch behavior. An unknown regulated bus still
fails rather than silently becoming local control.

Measurement lookup clears old resolution flags on reinitialization. A guard
initializes with an absent circuit, adds that circuit, then reinitializes and
verifies that branch feedback replaces generator fallback. This checks
controller measurement resolution, not complete network restart or branch-trip
behavior during an active simulation.

The read-only `getMeasurementSource()` reports resolved network selection:
external input, monitored branch, generator feedback requested by zero
identifiers, or generator feedback caused by a missing branch. The existing
zero-identifier getter retains its meaning; `isUsingGeneratorPowerFallback()`
covers both fallback causes. Observations leave named states unchanged.

## Independent profiles

Seven profiles run at 0.5/0.25 ms, each twice cold, for 28 native runs:

- Missing edge and missing circuit: generator feedback with remote Bus 2 retained.
- Zero FROM, zero TO, zero circuit and all-zero branch identifiers: explicit
  generator feedback, again retaining remote Bus 2.
- Negative target bus: monitored feedback at the same origin, not generator
  fallback and not origin-direction reversal. This behavior is independently
  observed for the installed native implementation rather than inferred from
  its negative-FROM documentation.

All seven selectors, 27 constants and converter constants remain unchanged
after initialization. Native branch-flow auxiliary variables are unused/zero
in fallback cases but populated for the signed-target case. Recorded-input
replay therefore selects physical generator P/Q for fallback; it does not
mistake the unused branch-flow variables for measurements or infer selection
merely because a measured power happens to be zero.

Thirteen raw CSV repeat pairs match exactly. One signed-target fine-step pair
differs only in inactive electrical hold memory by 1.36e-19. Both raw files
and hashes remain preserved. All 56 channels, startup history, duplicate API
boundaries and both fault event sides remain in fixed-digest fixtures.

Every profile uses the same physical constants, remote bus, nonzero line-drop
compensation and three-cycle Bus 2 fault. Only branch identifiers change.
The fault is 100 ohm on the 230-kV/100-MVA base, from 0.05 to 0.10 s at 60 Hz;
the horizon is 0.5 s. No controller parameter, reference state or initial
operating point is fitted to a trajectory.

Separate staged and explicit-history comparisons cover every row and all seven
allocated native states. Fixed channel bounds use the first complete
localization result with a 12% margin rounded upward to two significant figures,
and a 1e-10 inactive/equilibrium floor. Existing 30-profile bounds are unchanged.
The largest new history error is approximately 1.03e-5 pu; the largest staged
error is approximately 0.001475 pu and decreases under step halving. The
default solver and control equations are unchanged.

All fourteen profiles also pass strict four-record import, initialization,
measurement-source assertions and finite coupled SMIB execution. Coupled grid
traces and all-row reference projections remain diagnostics only: nearest-grid
projection does not resolve instantaneous event-side alignment. No complete
coupled or independent PowerWorld acceptance is claimed. PowerWorld's branch
validation rules are a separate dialect, not a reason to remove native fallback.

## Reproducibility and gates

The existing native probe now exposes its runner to the fallback wrapper.
Its original configuration/input-generation functions and mode list are
AST-identical to the preceding committed version. The original frozen probe
and all 60 previous raw native runs remain intact. New manifests freeze the
wrapper and all runner dependencies alongside each new run. The preliminary
pilot is retained separately from the 28 matrix runs.

The isolated snapshot checks 1,838 unchanged Java/POM files plus five owned
Java changes against committed baseline `48058ffec`. The intervening shared
grid-forming integration commit is included in the final tests; unrelated
untracked full-system tests are excluded and preserved. The pinned current-core JAR
remains `7867e200c66314e299430bfcc41a21b320121d57c89014b5e61562a1b8e28945`;
both older frozen dependency JARs remain unchanged.

Terminal processes and XML reports confirm 118 focused, 410 plugin-core and
1,724 scoped SMIB/model/solver tests, with zero failures/errors, six existing
optional unit skips and one existing SMIB diagnostic skip. Eight offline probe
tests and six wording-policy tests pass. No full Texas2k dynamics are run.
Earlier 97/410/1,703 runs are retained but do not stand in for the latest-baseline
gates. The broader gate includes the committed shared-integration checkpoint
tests, not a claim of complete grid-forming trajectory acceptance.

Sources, before-fix errors, raw repeats, all-row state/coupled diagnostics,
dependency hashes and reports are retained in the ignored private evidence set
`verification-artifacts/2026-10-19-repca-branch-fallback` with a hash inventory.

## Remaining TODO

- [x] Missing edges/circuits and all explicit zero-identifier forms.
- [x] Retained remote measurement and unchanged native input readback.
- [x] Signed-target discriminator and read-only measurement-source diagnostics.
- [x] Controller reinitialization after the missing circuit becomes available.
- [x] Bounded all-row seven-state replay and scoped current-baseline regressions.
- [ ] Complete coupled event-side/current/command and PowerWorld acceptance.
- [ ] Active-simulation topology changes, full restart/trip/reconnect behavior,
  further invalid/signed identifiers, bases, aliases and other converter hosts.
- [ ] Further zero-time, threshold, freeze/release and limiter profiles.

This extends partial native evidence; it does not mark REPCA1 or the overall
coverage plan VERIFIED or complete.
