# REECA1 native bias, current-table and state-replay evidence

## Corrected production behavior

The native electrical record has six selectors, 45 constants, six allocated
states and nine auxiliary storage variables. The installed model-library
section 22.2, PDF pages 852–856/printed 840–844, was visually reviewed.
Document SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The [PowerWorld description](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20REEC_A.htm)
and expanded diagram provide an independent published modeling reference;
independent PowerWorld execution remains open.

The native reader now explicitly selects the native-record convention:

- With PFflag=1, Vflag=0 and Qflag=1, the voltage bias is calculated as
  initial measured voltage minus initial reactive power. The ignored entered
  bias must not introduce a false initial voltage error. The old typed
  constructor preserves its historical convention.
- Current tables accept two mandatory pairs followed by unused zero-voltage
  pairs. Only voltage coordinates must increase; current ordinates may decrease.
  Previously, these valid curves were silently disabled, changing active and
  reactive current priority/headroom. Literal optional zero terminates a table;
  this is not an approximate-zero threshold.
- Read-only native state observations use the published six-slot order:
  voltage filter, PF power filter, reactive PI integral, voltage PI integral,
  reactive-current lag, power-order lag. Inactive reserved slots are zero.
  Legacy named-state aliases remain unchanged; the integration-state interface
  selects only active differential coordinates and does not alter their storage.

Four boundary guards failed against the preceding implementation. The corrected
guards cover calculated-bias equilibrium, both priorities with two-point curves,
and a four-point decreasing-current curve. Additional guards check defensive
observations, unchanged control memory and immutable active-state maps.
The reactive-current lag still freezes during a voltage dip: the native
trajectory and PowerWorld diagram support retaining that behavior.

## Repeatable independent matrix

Thirteen local profiles at 0.5/0.25 ms, each twice cold, give 52 completed
reference runs. These cover flat reactive control, flat PF/voltage control,
direct Q, cascaded Q/V, direct voltage with zero/nonzero entered bias, PF Q,
PF voltage, positive/negative post-dip hold, both current priorities and a
decreasing four-point current curve. Disturbances are three-cycle faults on
Bus 2, applied at 0.05 s and cleared at 0.10 s on a 60-Hz three-bus network.
The declared 100-ohm fault is converted to network per-unit using the actual
230-kV, 100-MVA base; it is not a one-pu fault.

All electrical selectors/constants and converter constants read back unchanged
after initialization, using exact binary32 parameter equality. The fixed
converter reactive recovery-rate bounds are +100/−100 in every profile, so
the electrical controller is not masked by a zero recovery bound. These are
declared benchmark inputs, not fitted case parameters. There is no plant,
torque, pitch or mechanical controller in this isolated fixture.

Twenty-four local pairs have byte-identical complete CSVs. Two coarse pairs
(`q` and `descending`) differ only in VAR6, inactive stored active-current
hold memory, by at most 1.36e-19. Both original outputs and hashes are retained;
no row/channel is removed or silently normalized to manufacture exactness.
All 33 channels and negative-time, duplicate run-boundary and event-side rows
remain in fixed-digest fixtures. Machine channels PREF_INPUT/QEXT_INPUT are
controller inputs, not current-command outputs.

The frozen local probe predates the remote diagnostic extension. Its source
hash is preserved and its configuration/input generation is checked against
the current probe for every local run; unchanged-input equivalence is not
inferred from the filename alone.

## Replay versus integration-method and network diagnostics

All six states are replayed on every row, initialized only from physical
P/Q/V, never from observed controller storage. Separate fixed bounds cover
the default staged path and the existing explicit two-step history API.
Bounds were set from the first complete replay with a 12% margin rounded
upward to two significant figures, and a 1e-10 floor for inactive/equilibrium
coordinates. This did not change any existing benchmark tolerance.

The history replay's largest error is approximately 9.23e-6. Default staged
voltage-filter errors peak near the first fault step at approximately 0.001443
and 0.000741 pu for coarse/fine steps. These are different integration histories,
not evidence for changing physical parameters or the default solver. An
independent first-fault increment guard verifies the native three-quarter-step
history coefficient. Default integration is unchanged.

Every local profile also runs strict import, initialization and a coupled
0.5-second small-network simulation. Coupled traces and all-row reference
projections are retained as diagnostics, not complete trajectory acceptance:
nearest declared-grid projection preserves every reference row but does not
resolve instantaneous event-side alignment. Finite execution is not a parity
claim.

## Remote measurement counterexample

Four completed remote-request diagnostics retain entered remote Bus 2 and
initialized local selector zero. Their complete trajectories are byte-identical
to the corresponding local-voltage profiles. Earlier attempts in both Q/V and
direct-voltage modes failed the unchanged-selector guard and remain preserved.
These runs do not establish remote measurement behavior and are excluded from
the 26 accepted local replay profiles. InterPSS's remote capability must not
be changed to local fallback merely to reproduce this rejected reference input.

## Validation and remaining TODO

- [x] Source contract, strict three-record import, initialization, native bias,
  valid table boundaries, state observations and all-row six-state replay.
- [x] Cold local repeats and explicit inactive-memory repeatability difference.
- [x] Both timestep histories and first-increment discriminator; no default
  integration replacement or parameter fitting.
- [x] Local flat/fault SMIB executions; preserve coupled diagnostics separately.
- [ ] Complete coupled channel/event-side acceptance, including current commands
  and converter/network callback ordering.
- [ ] Independent remote-bus execution with unchanged initialized selectors.
- [ ] Reference/rate changes, active PI limits and anti-windup release, zero-time
  branches, nonzero dip reference, upper-voltage excursions and threshold equality.
- [ ] Further machine-base, wind/plant composition, trip/reconnect/reinitialization
  and independent PowerWorld execution profiles.

Final terminal processes and XML reports confirm 55 focused, 410 plugin-core
and 1,679 scoped SMIB/model/solver tests, with zero failures/errors, six existing
optional unit skips and one existing SMIB diagnostic skip. Four offline probe
tests and six wording-policy tests also pass. The isolated snapshot matches
1,829 unchanged Java/POM files and the eight owned Java changes; unrelated
untracked tests and documentation edits are excluded. The pinned core dependency
has SHA-256 `50569fa3d1c9972229478b40759b45358c38f615c358dc3be8889e3e8ab8854c`.

Sources, raw repeat pairs, failed attempts, diagnostic traces, document renders
and test reports are retained in the ignored private evidence directory
`verification-artifacts/2026-10-19-reeca-native-electrical`, with a full hash
inventory. This slice remains partial native evidence, not VERIFIED. No full
Texas2k dynamic simulation is part of this work.
