# REPCC physical network boundary

## Implemented scope

`RepccNetworkBoundary` resolves voltage/frequency buses, two independent branch
measurements and ten named fixed-shunt bindings. It translates network powers
and currents from system base to machine base exactly once. It also applies
accepted shunt actions to the physical device status, bus conductance, cached
positive-sequence equivalent and live Y matrix. This is a typed integration
boundary, **not** an automatically attached REPCC runtime or DYR import path.
Approved-family runtime coverage remains 25/27.

Source: installed Model Library section 27.4, especially notes 1 and 3–8, and
the visually reviewed main diagram. The document SHA-256 remains
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The PDF review was used to distinguish main-branch fallback from independent
voltage/frequency fallback and to retain signed measured-end flow semantics.

The boundary preserves these distinctions:

- A missing main branch selects explicit converter-local machine-base P/Q and
  complex current. It does not overwrite the selected regulated bus.
- Missing/zero regulated-bus identifiers fall back to the terminal. Missing/zero
  frequency-bus identifiers fall back to the resolved regulated bus.
- Signed first-bus identifiers select outgoing/arriving flow at that same end;
  reversed branch storage does not change the requested measurement end.
- Branch current is calculated from the branch admittance blocks and both end
  voltages, not by dividing P/Q by the measured-end voltage. It stays defined
  at a zero-voltage fault. An inactive branch supplies zero power/current.
- Switching Q uses its own configured branch and machine-base conversion;
  a missing switching branch disables switching rather than borrowing main Q.
- Fixed-shunt nominal G+jB is supplied explicitly on system base, including
  initially open devices. Core named compensators retain B but not individual
  G, so aggregate bus conductance is not guessed as a device property.
- Single-step nominal B, unique named devices and unchanged external status are
  checked before dispatch. Repeated endpoint times cannot apply a switch twice.

## Cached-admittance bug found and fixed

The initial physical-switch test changed the live diagonal correctly, but a
later matrix rebuild restored the old shunt when static loads were included in
Y. That path retained the initialized bus equivalent. Changing the physical
compensator status and bus G alone did not update that cache.

Core commit `38e269ecf` adds
`BaseAcscBus.applyPositiveSequenceShuntAdmittanceChange(delta)`. An initialized
equivalent is adjusted by the system-base G+jB increment. An uninitialized
equivalent reads the physical records normally on first construction. Loads,
generator source terms and operating-point voltage are not reinitialized at
the disturbance voltage. The boundary separately updates physical records and
the live matrix, then marks its factorization dirty.

Three new engine tests cover cached/uncached operation, opening/reclosure,
repeated rebuilds, a non-unit initial voltage followed by depressed voltage,
changes before cache initialization and invalid inputs. The complete engine
gate passes **2,973 tests, zero failures/errors/skips**, across 819 reports.
The plugin integration test independently requires persistence after rebuild.

Build this boundary against core commit `38e269ecf` or newer containing this
API; an older same-version jar does not contain the new method. Verification
uses a separate private Maven repository, leaving the previous accepted jar
and shared dependency installation unchanged. The tested new jar SHA-256 is
`fbd9f015887b51ba438a538120cff00d0a724e76fd017674ec28fb0a924ab125`.
No production dependency version is silently changed.

## Direct measurement references versus controller plot timing

An initial comparison incorrectly treated switching plot `VAR13` as a fresh
measurement simultaneous with every plotted bus voltage. Across shunt and
fault event sides, that comparison differs by as much as about 1.82 machine pu.
The main P/Q plot fields agree within approximately 2.51e-7/3.39e-6 pu.
This diagnostic is retained explicitly; it is not hidden by a loose tolerance
or by adding a fitted measurement delay to the implementation.

Six new native datasets query both physical branch-end powers and bus-voltage
phasors directly after each accepted run endpoint. Negative and positive
switching profiles and the held-fault discharge discriminator are each run at
0.5 ms and 0.25 ms and cold-repeated: twelve native runs. All six direct-query,
input, probe and physical-status pairs are byte-identical. Every sample is
preserved in hashed compressed resources. The added queries leave the original
59-channel trajectory intact; its raw file remains separately preserved.

The boundary is checked against every direct-query sample at both branch ends
and on the selected switching branch. Maximum machine-base errors for
P23/Q23/P32/Q32/Qmss are respectively:
`2.39271e-7, 3.38753e-6, 2.39271e-7, 3.28849e-6, 3.38753e-6` pu.
The explicit per-channel bounds are
`2.64e-7, 3.73e-6, 2.64e-7, 3.62e-6, 3.73e-6` pu.
Input voltage/API export precision remains intact; no reference is rewritten.

Eight additional recorded-voltage profiles reproduce every physical shunt status
and all five events per profile. Every endpoint checks the live admittance
increment, and every switching event forces a matrix rebuild and requires the
same diagonal. Duplicate endpoint callbacks cannot apply a second increment.
The initial API snapshot has negative integration-history time; it is retained
as history, while controller clocks start at zero. No history sample is advanced
as a real simulation endpoint.

The held fault lasts 220 ms and is an observability stress, not a three-cycle
coupled-plant acceptance case. Fresh branch queries and controller plot values
are different observation points. Their full callback relationship still
requires verification when attaching the plant to the solver.

## Remaining model work

- Retain individual fixed-shunt G at import and supply the explicit nominal
  bindings. This slice does not infer missing per-device conductance.
- Compose the thirteen-state kernel and boundary, initialize downstream Q/V
  command coordinates, and route absolute outputs as correct controller inputs.
- Add strict standard-native import, non-overwriting device attachment and an
  accepted-endpoint callback with the correct network/event-side ordering.
- Complete disabled-loop, initialization and selector/limit evidence, generator
  lifecycle checks and independent coupled SMIB three-cycle faults at both steps.
- Resolve the separately documented native discharge violation and inactive
  PowerWorld switching. Neither is relabeled as successful lockout parity.

The shunt kernel and immutable evidence from earlier slices are unchanged.
No full Texas2k dynamic simulation is required or run.

## Final gates and sealed evidence

Final plugin gates pass with zero failures/errors: **42 focused checks, 344
plugin-core tests (six optional skips), and 1,394 registered SMIB/model/solver
checks (one existing diagnostic skip)**. Eight boundary tests cover physical
measurements, fallbacks, current at zero voltage, validation, switching and
matrix persistence. Every report classpath uses the new frozen engine jar.

The audit matches ten owned source/resource files and 2,087 other baseline
Java/POM files to the tested snapshot, checks all twelve native manifests and
every reference resource, validates the three engine sources against commit
`38e269ecf`, and rechecks all six earlier sealed plant archives. Five complete
plot-output pairs are byte-identical; one differs only in an inactive recovery
timer below 1e-18 seconds. All direct-query/input/probe/status pairs are exact.
Both raw plot files are retained, without changing parameters or tolerances.

The audited plugin baseline is `b93548450`; its concurrent documentation change
does not alter tested code. Private archive
`2026-10-18-repcc-network-boundary` retains source snapshots, gates, the engine
jar and 819 engine reports, twelve native runs, diagrams and development
attempts. Earlier failed comparisons and the missing-resource staging attempt
remain development evidence, not passing gates or independent acceptance.
