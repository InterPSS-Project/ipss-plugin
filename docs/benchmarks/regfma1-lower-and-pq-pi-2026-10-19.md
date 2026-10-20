# REGFMA1 lower integral and simultaneous power/voltage PI investigation

## Status: equation/stage verification and bounded partial trajectory evidence

The source baseline is plugin `bb39bd873a460dd827b06071fe343702beb02e55`.
Production model equations, gains, integration methods and established bounds
are unchanged. No full Texas2k dynamic simulation is run. Complete model
acceptance, coarse default-method parity and instantaneous event-post parity
are not claimed.

The installed source diagram, printed pages 808-809, was reviewed again,
including a fresh rendering of the voltage-controller page. Source document
SHA-256 is `40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The voltage integral has its own Emin/Emax bounds, independent of the
proportional-plus-integral output limiter. Outward integration freezes at an
integral bound, while inward integration remains permitted.

## New repeatable reference profiles

The existing public three-bus, 50-MVA converter fixture is retained. Both
profiles use Vflag=QVflag=1, the original gains and source impedance, a
0.05-0.10-s disturbance, and a 0.3-s endpoint. Each runs at 0.5 ms and
0.25 ms, twice cold. No plant-controller substitution is used.

- Lower-integral discriminator: Emax=1.1, Emin=1.075; a temporary capacitive
  shunt at Bus 2 is represented by impedance -j500 ohm, or -j5 pu on the
  230-kV/100-MVA base. This is a voltage-raising shunt, not a three-phase fault.
  The solved initial internal voltage is above the declared lower bound.
- Simultaneous P/Q and voltage-PI discriminator: Pmax/Pmin=0.405/0.395 and
  Qmax/Qmin=0.3/0.2, with the existing j100-ohm three-phase Bus 2 fault for
  three cycles. The limits bracket the initial solved P/Q point.

All eight final runs pass initialization and recognized run-quality guards.
All four cold pairs have identical input, CSV, captured-state and executable
probe/helper bytes. Every log and binary output is independently hashed;
startup log differences are retained rather than requiring log byte equality.
Actual/final constants are checked exactly against the float32 representation
of entered decimal data, including the two selectors and 2/18/9/8 allocation.

Each coarse trace has 605 rows and each fine trace has 1,205: all 24 channels,
two initialization-history rows and two duplicated event-side rows are kept.
The four paired profiles therefore retain 7,240 raw rows across eight runs.
The lower integral reaches its exact float32 bound and later releases inward;
all four P/Q integrals activate with terminal-voltage PI enabled. These are
reference activity and repeatability claims, not complete model verification.

The executable generator and auditor are `tools/regfma_limit_probe.py` and
`tools/check_regfma_limit_artifacts.py`. Twelve synthetic negative-control
tests initially passed; the final set expands to sixteen, including rehashed
nonfinite data, removed history, missing saturation and missing inward release.
Synthetic fixtures are explicitly not independent simulator data.
Four fixed-digest, losslessly compressed CSV fixtures preserve every raw row
under `src/test/resources/regfma-limits` in the integration-test module.

## Rejected and interrupted attempts

The first process-isolated launch stalled before staging simulator inputs and
was explicitly cancelled after process inspection. Its console log remains.
A separate normal-permission pilot then rejected an invalid test setup:
Emin=1.09 exceeded the solved initial internal voltage, approximately 1.0772,
and the native log reported suspect initialization. That pilot, its manifest,
log, binary and CSV remain separate. It is not scored or promoted to a clean
reference. The corrected lower bound is declared below the initial solved
voltage; initialization is not repaired by changing or fitting model states.

An initial isolated Java launch failed snapshot synchronization because Python
used the wrong text encoding, followed by a denied build launch. That is not a
valid test gate. The next launch uses explicit UTF-8, verifies synchronization
success, and retains a fresh run ID. Initial offline tests also hit restricted
temporary-directory permissions; the normal-permission rerun passes all twelve.
No failed bootstrap is counted as a model test pass.

## InterPSS comparison: failing evidence retained

`regfma-limits-focused-2` completes 34 integration-module tests: 33 pass, one
fails, no errors or skips; fourteen catalog tests separately pass in the reactor.
The new test executes both integration paths on all four profiles.
It reuses the established terminal-voltage and P/Q family envelopes, declared
before inspecting these errors, and does not widen them. All regular endpoints
are compared. History and instantaneous event-post rows remain in the fixture
and a row-status export, explicitly unscored rather than silently discarded.
This protocol is not exact instantaneous-event or initialization-history parity.

The failed parameterized case is the coarse simultaneous-P/Q/voltage-PI
profile using default modified Euler. Its voltage-integral maximum error is
7.8783061668e-6 pu at raw time 0.124500162899 s, beyond the unchanged 7e-6
family bound. The Qmax-integral maximum is 4.3668623856e-4 pu at
0.158999726176 s, also beyond the established envelope. The failure message
stores the first 24 violations; the printed per-channel maxima cover all
channels and must be reviewed rather than treating those first rows as the
complete failure list.

At the half step, those maxima become 2.6773457833e-6 and 1.3325412662e-4 pu.
The matched-method coarse values are 2.5470946563e-6 and 9.8299262352e-6 pu.
The matched-method fine values are 1.4503524466e-6 and 4.2546593856e-6 pu.
Lower-bound/release cases pass the existing envelopes in both methods and at
both steps. This localizes the next investigation toward integration/coupling,
but does not by itself prove the equations correct. No physical parameter or
solver default is changed to fit.

## Recorded-input diagnosis and permanent method-specific checks

The test-only `RegfmaLimitReplayProbe` feeds exactly the recorded physical P,
Q and terminal-voltage inputs, using the declared device/system base conversion.
It does not substitute native states, optimize inputs, adjust initial states,
change clocks or add a production injection setter. Reflection is restricted to
the two cached input fields in this test utility; production Norton consistency
is unchanged. The raw input endpoint on each event side is retained without
advancing states on a duplicate timestamp. All history rows are exported and
labelled without integration. The declared fixed step, not a fitted raw-time
shift, controls every real predictor/corrector step.

At every endpoint, separately written equations calculate all nine derivatives
and both projected modified-Euler state vectors. Four standalone replays and
four registered fixture replays have maximum equation disagreement at most
1.34e-15 and zero recurrence disagreement. The coarse simultaneous-loop Qmax
integral discrepancy still reaches 4.1398294613e-4 pu under the same recorded
inputs; its half-step value is 1.2601003998e-4 pu. Thus the dominant discrepancy
does not require different network feedback. These checks prove the implemented
source equations and stage recurrence on the exercised paths, not a universal
continuous-time error bound or complete native numerical equivalence.

The first new test incorrectly assumed that a combined-controller profile in
different integration methods must share an existing profile's trajectory-error
envelope at the same coarse step. That is not a published numerical guarantee.
The final protocol makes distinct, executable claims instead of widening that
envelope or modifying the model to imitate another method:

- Four matched-method profiles must satisfy the unchanged envelopes at every
  regular endpoint, at both steps.
- Default modified Euler retains and exports both coarse profiles and their
  exact per-channel maxima. The coarse simultaneous-loop comparison remains
  explicitly outside the envelope, not accepted. At the half step every channel
  must meet the unchanged envelope; every coarse out-of-envelope channel must
  improve, with no fitted reduction ratio or new numerical allowance.
- Every default-method replay must satisfy independently reconstructed
  equations and both stage recurrences. Six additional blockwise cases test
  lower/upper outward freeze, inward release, and algebraic output saturation
  with an interior, still-integrating state.

The original failed run and compiled failing test are retained. A passing final
gate does not change that run's failure or grant coarse default parity. The new
fixtures and test are registered in both normal and scoped SMIB discovery.
Actual coupled trajectories are exported separately from the all-row reference
classification. No established test, reference byte or bound is replaced.

## Final gates and reproducibility

Final XML reports show 56 focused tests (42 integration-module and 14 catalog),
427 plugin-core tests, and 1,978 scoped SMIB/model/solver tests, with zero
failures/errors. The focused gate has no skips; six existing optional core
skips and one existing opt-in SMIB diagnostic remain. These are overlapping
gates, not a count of distinct tests. All 143 offline checks pass. The permanent
Java quality auditor independently accepts all eight final references and
rejects the suspect-initialization pilot.

Run identifiers are `regfma-limits-focused-3`, `regfma-limits-units-1` and
`regfma-limits-smib-1`. The isolated build uses `-am` for integration tests,
offline dependencies, the `isolated-verification,extended-tests` profiles and
core `1.4.3-salient-refresh-20261019`. Its jar SHA-256 remains
`21519010c48bd265678743df7e98d3dcea530f5ceda1ccd7921d123d7f6b5333`.
Source reconciliation covers 1,890 Java/POM files, 139 unchanged earlier
fixture parts and four exact new raw-CSV resources. The running REGFMA1 model,
data and associated compiled classes match the prior tested build byte-for-byte;
the fifteen earlier checkpoint datasets/tests remain unchanged. Concurrent
documentation-only commits are reconciled separately from this source baseline.

The ignored private archive is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-19-regfma1-lower-pq-pi`.
It retains runnable inputs/probes, all eight final references, both pilots,
quality audits, standalone and registered replay outputs, actual coupled
trajectories, every raw-row classification, original failed reports and compiled
test, final reports, source/dependency identities and a SHA-256 inventory.
It also verifies and links the preceding 1,306-file evidence inventory without
overwriting it. The first failed test's compiled class is preserved rather
than representing the subsequently edited source as its original source.

When storage was tight, only a completed prior test-build directory was
losslessly NTFS-compressed. All 5,382 file hashes were checked unchanged;
185,420,218 logical bytes occupy 161,398,998 bytes afterward. Subsequent system
free-space recovery is not attributed to that compression. No evidence,
protected checkout/cache, pinned dependency or unrelated file was deleted or
moved. Full-system dynamic opt-ins remain disabled.

## Remaining work

- Coarse default-method trajectory parity remains outside the recorded
  envelope. Do not call the method-specific recurrence/refinement evidence
  complete transient acceptance. Further numerical-error analysis is required
  before changing production-step guidance or granting broader acceptance.
- Keep complete instantaneous-event, plant/lifecycle, zero-time, typed
  PowerWorld and broader model-coverage requirements open.
