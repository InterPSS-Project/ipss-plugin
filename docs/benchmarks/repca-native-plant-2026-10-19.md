# REPCA1 native measurements, limits and seven-state evidence

## Source contract and corrections

The native record has seven selectors, including a character circuit ID,
27 constants, seven allocated states and nine auxiliary variables. The
installed library section 27.3, PDF pages 922–924/printed 910–912, was
visually reviewed. Document SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The [PowerWorld description](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Plant%20Controller%20REPC_A.htm)
is an independent published reference, not a substitute for exact native
execution. Its branch-validation requirements differ from native fallback
behavior; independent PowerWorld execution remains open.

The native builder now selects explicit native-record behavior for supported
converter hosts. The historical typed constructors remain separate:

- Literal zero Rc/Xc means no compensation. It must not select the monitored
  line's impedance, as the historical ANDES convention does.
- Native compensation forms current from monitored branch power divided by
  the regulated-bus voltage. It does not use the branch-origin voltage when
  that terminal differs from the regulated bus.
- A negative monitored FROM identifier reverses power at that same terminal;
  it does not select the opposite terminal or look up a negative-numbered bus.
- Absolute active PI/output limits are translated around the physical initial
  electrical reference before returning incremental commands. For example,
  Pmin/Pmax of 0.235/0.245 around initial P=0.24 permit −0.005/+0.005 increments.
  Initialization-only expansion includes the physical operating point; entered
  parameters are not fitted or rewritten.

Two new boundary tests failed before correction. Further native witnesses
cover signed origin, remote compensation current, immutable observations and
nonfinite measurement rejection. A historical ANDES test explicitly retains
its incremental-limit convention and unchanged original bounds. Its previous
post-change error crossing is retained as a dialect discriminator, not solved
by widening the tolerance.

## Measurement and observation interfaces

The same staged and explicit-history equations now accept immutable physical
measurement records for conditional replay. Initialization takes physical
output references separately from branch/remote feedback. No controller state
is injected, forced or initialized from an observed native state.

The read-only native observer reports voltage filter, Q filter, reactive PI,
reactive lead-lag, active power filter, active PI and active lag in native
seven-slot order. Inactive reserved slots are zero. Native absolute references
are reconstructed only at this observation boundary; stored incremental
integration coordinates and existing named-state keys remain unchanged.
Active-state/derivative snapshots exclude inactive and bypassed coordinates.
The default integration method is unchanged.

## Repeatable reference and replay matrix

Fifteen profiles at 0.5/0.25 ms, each twice cold, give 60 completed native runs.
They cover flat Q/V, disturbed Q/V, monitored-branch Q, remote voltage,
zero/nonzero line-drop compensation, reactive droop, opposite branch terminal,
negative FROM direction, integrator freezing, narrow Q/P bounds and disabled
frequency control. All selectors and all 27 constants read back unchanged
after initialization; the circuit ID is read through the character API.
The converter constants also remain unchanged as exact binary32 values.

Twenty-five complete CSV pairs are byte-identical. Five coarse pairs differ
only in inactive auxiliary storage by at most 1.36e-19: electrical hold memory,
plus unused plant output/frequency memory in the disabled-frequency profile.
Both raw files and hashes are retained. No row or channel is silently removed
or normalized to manufacture exact repeatability.

All 56 channels and every negative-time, duplicate run-boundary and event-side
row remain in fixed-digest fixtures. Four-record imports use the existing
converter/electrical controller and an infinite generator, without wind or
mechanical controllers. Three-cycle faults run from 0.05 to 0.10 s at Bus 2
on the 60-Hz network. The declared 100-ohm impedance is converted using the
230-kV, 100-MVA base. Fixed converter reactive recovery bounds are ±100 in
every profile to expose electrical/plant behavior, not fitted case parameters.

All seven states have separate per-profile staged and history replay bounds,
set after the first corrected replay with a 12% margin rounded upward to two
significant figures and a 1e-10 inactive/equilibrium floor. No existing bound
was widened. Every replay row and the time of each channel maximum are retained.

The nonzero-compensation discriminator is structural: using origin voltage
gives a history error around 0.00144 pu at both steps. At initialization it
predicts 0.9964725887 pu instead of native 0.9959977865 pu. Regulated-voltage
current predicts 0.9959977952 pu and reduces the complete coarse history error
to about 1.03e-5 pu. The largest corrected history-state error across the
matrix is about 1.53e-5 pu. Default staged event-step errors are larger and
decrease under step halving; they are not concealed by replacing the solver.

Every profile also passes strict import, initialization and a finite coupled
0.5-second SMIB execution. Coupled grid traces and all-row reference projections
are diagnostic only. Nearest declared-grid projection retains both event sides
but does not resolve instantaneous discontinuity alignment. These are not
complete coupled trajectory acceptance or machine-base validation claims.

## Latest-baseline verification and evidence

A concurrent plugin update introduced shared-core load-matrix APIs. Final
tests therefore use a new isolated runtime, not the older frozen dependency.
It combines the pinned base JAR with exactly three sealed core classes from
core commit `08fb54173`; all other JAR entry contents are proved unchanged.
The assembled core SHA-256 is
`7867e200c66314e299430bfcc41a21b320121d57c89014b5e61562a1b8e28945`.
Earlier frozen caches remain unchanged. The new option is not enabled for
these load-free SMIB cases; its ten synthetic matrix/current tests also pass.

The isolated plugin sources match the current baseline and eight owned Java
changes, with 1,832 unchanged Java/POM files checked. Final terminal processes
and XML reports confirm 91 focused, 410 plugin-core and 1,697 scoped
SMIB/model/solver tests, with zero failures/errors, six existing optional unit
skips and one existing SMIB diagnostic skip. Four offline probe tests and six
wording-policy tests also pass. No full Texas2k dynamic simulation was run.

Raw repeats, rejected harness attempts, old/new dependencies, source renders,
source hashes, state/coupled diagnostics and reports are retained in the ignored
private evidence set `verification-artifacts/2026-10-19-repca-native-plant`.
The fixture-export command-length failures and initial character-selector API
mistake are tooling diagnostics, not model failures or accepted native runs.

## Remaining TODO

- [x] Exact native selector/constant readback and cold repeatability audit.
- [x] Literal compensation, regulated-voltage current, signed-origin feedback
  and absolute active-limit corrections with preserved typed conventions.
- [x] All-row seven-state staged/history replay and independent current witness.
- [x] Strict imports and small-network flat/fault diagnostics at both timesteps.
- [x] Current plugin/core regression gates and source/dependency provenance.
- [ ] Complete coupled event-side/current/command acceptance and precision floors.
- [ ] Missing-branch fallback, further signed identifiers and invalid-input behavior.
- [ ] Further base/PU declarations, converter hosts, aliases and wind/plant composition.
- [ ] Zero-time, deadband/threshold equality, further limiter and freeze/release,
  trip/reconnect/reinitialization and independent PowerWorld execution profiles.

The renewable inventory now has nineteen native-partial and eight native-recorded
families. No family is ANDES-only, but none is VERIFIED. The remaining coverage
plan is still active; finite execution and partial replay are not completion.
