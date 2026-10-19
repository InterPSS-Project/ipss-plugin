# ESST1A zero-time observation and bypass coverage

## Source contract

The native eighteen-constant, two-selector record allocates five exciter
states even when particular blocks are bypassed. Its documented Tf is strictly
positive. The [PowerWorld ESST1A reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST1A%20and%20ESST1A_GE.htm)
additionally documents simulator-specific minimum-time corrections. Those
corrections are not imported into the native contract by analogy.

Six declared profiles independently set Tr=0, Ta=0, Tc=Tb=0, Tc1=Tb1=0,
or Kf=0, and combine those changes in an all-bypass profile. Tf remains one
second in every profile. The other constants, generator and network are
unchanged from the unlimited field-current SMIB reference. A +0.02 pu Vref
step at 0.05 s provides excitation without forcing any state. The run ends
at 0.5 s; timesteps are 0.5/0.25 ms. No full-system dynamics are used.

## Observation defect

The generic CML delay block correctly computes K*u algebraically when its
time constant is zero. Its integration storage then remains at initialization.
The ESST1A published-state observer previously returned that dormant storage
for both the transducer and regulator. The failing-before coupled regression
records a live zero-time sensed voltage of 1.000000050645354 pu while the
snapshot still reports 1.0 pu.

The observer now selects the live algebraic output for those running zero-time
blocks and retains integration storage for positive-time blocks. Selection
uses runtime block parameters, not mutable configuration fields. The generic
state map, controller inputs/outputs, integration and physical equations are
unchanged. Bypassed lead-lag published storage remains zero, and a disabled
feedback branch retains zero output/storage with positive Tf.

Native pilot readbacks independently show that zero-time measured voltage is
the algebraic bus voltage, bypassed lead-lag storage is zero and Kf=0 feedback
storage is zero. Zero-time regulator output can update before the exported
field-voltage channel on an event side; those rows must remain separate.

All twelve raw CSV pairs reproduce byte-for-byte. Independent live STATE
readbacks agree with exported channels within 4.92e-12 pu, with complete
negative history and both duplicate interval boundaries retained. The live
native endpoint is recorded explicitly: floating-clock accumulation can stop
slightly short of the requested 0.5 s. Comparisons retain that actual endpoint,
not a fabricated sample at the nominal stopping time.

Every raw row and all 25 non-time channels have a retained coupled diagnostic.
The unaligned algebraic event-side regulator differences reach approximately
0.1 pu for Ta=0 and 1.6 pu for the all-bypass case. Feedback differences reach
about 8.89e-6 pu and several fine-step regulator errors increase rather than
shrink. Those results are open numerical/event-side gaps, not accepted by
increasing a bound. The observation correction does not claim to resolve them.

## Verification TODO

- [x] Capture failing-before observation regression and verify the correction
  with existing ESST1A/washout tests (13 tests, no failures/errors).
- [x] Add coupled bypass/disabled-feedback equation and nonmutating observation
  guards, including edits to public configuration after initialization.
- [x] Complete twelve independent profiles, each repeated cold, with live
  endpoint, exact input/readback and all-file hash checks.
- [x] Publish lossless histories and run complete registered focused/core/SMIB
  gates against a source-reconciled snapshot.
- [x] Seal source, dependencies, raw repeats, diagnostics and terminal reports;
  commit only the tested slice.
- [ ] Establish complete event-side trajectory acceptance, auxiliary-limiter
  and lifecycle coverage; resolve the existing longer coupled-state gaps.

Full coupled traces and all-channel comparisons are diagnostic until explicit
channel-specific acceptance has been established. Finiteness or successful
import alone does not make this model completely verified. No existing
trajectory bound is widened by this observation correction.

Final gates pass 63 focused, 410 plugin-core (six existing skips) and 1,774
scoped SMIB/model/solver tests (one existing skip), with zero failures/errors.
All 48 offline probe/wording tests pass. Source reconciliation covers 1,858
Java/POM files and 42 fixture parts. The private seal is
`2026-10-19-esst1a-bypass-observation`; it retains the failing-before observation
test, rejected nominal-clock assumption, raw repeats, complete coupled traces,
all-row/all-channel diagnostics and exact corrected dependency provenance.
