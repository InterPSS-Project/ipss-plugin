# Three-second GENSAE, ESST1A and TGOV1 recovery evidence

## Scope and source contract

Follow-up: [controlled-input washout verification](esst1a-washout-coordinate-2026-10-19.md)
corrects the published coordinate at nonunit Tf and independently checks the
InterPSS analytic solution and explicit stages. These Tf=1 recovery profiles
are unchanged. Their four diagnostic state gaps remain unresolved; the new
constant-input checks do not confer coupled-trajectory acceptance here.

This follow-up composes the existing generator, exciter and governor engines
without changing their production equations or integration method. Nine
configurations at 0.5/0.25 ms give eighteen profiles, cold-generated twice:
flat, three-cycle fault, current-derated field ceiling, IEEEVC compensation,
50/200 MVA, zero turbine damping, upper-valve limiting and lower-valve limiting.
The fault remains at Bus 1 from 0.05 to 0.10 s, with j0.2 pu impedance.
The three-second response includes both recovery speed directions in every
fault profile. These are SMIB runs, not full Texas2k dynamics.

Native source-library pages 257-258 and 481 specify the exciter/governor
diagrams and allocations. GENSAE has twelve constants/five states; ESST1A
has two selectors/eighteen constants/five states; TGOV1 has seven constants,
two states and one reference variable. Generator/controller constants are
guarded before and after initialization and execution. The
[PowerWorld governor reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20TGOV1%20and%20TGOV1D.htm)
has additional rating/zero-time conventions; these are not inserted into the
seven-constant native input. Positive governor times remain T1=0.04/T3=0.3 s,
T2=0.1 s and R=0.05. Generator MVA is not confused with a turbine-rating extension.

The original valve-limit profile reaches only the upper bound: 65/128 raw
rows at the two steps. It is preserved unchanged. A separate minimum-only
discriminator reaches the lower bound on 1,696/3,394 rows. Both branches also
activate in the independently imported InterPSS runs. No parameter is fitted
to reduce a trajectory error.

## What is accepted, and what is diagnostic

All eighteen cold pairs match byte-for-byte. Every raw history, interval and
event-side row is retained, with all twelve states and seventeen other fields.
The calculated grid brackets the exact exported endpoint; positive times are
not clamped or extrapolated. Initialization has separate fixed bounds for all
twenty-eight non-time fields.

Eight coupled-state channels have separate fixed profile bounds: the three
electrical generator coordinates, speed deviation, measured voltage, both
lead-lag coordinates and regulator output. Seven dominant channels have
explicit timestep-refinement checks. The smaller second lead-lag residual is
bounded but is not claimed to refine uniformly. Maximum speed error decreases
from 3.61e-6 to 1.96e-6 pu across the two-step matrix.

Four coupled states remain **diagnostic, not accepted**: rotor angle,
washout storage, governor valve and governor reheater storage. Their raw-row
maxima and timestamps are retained. In particular, washout storage reaches
4.84e-5 pu discrepancy and does not consistently improve at the smaller step.
This is not hidden by adding a broad acceptance tolerance.

Recorded-speed governor replay localizes its two storage coordinates and
mechanical output independently of machine feedback. All raw rows have fixed
profile-specific replay bounds; some small residuals do not refine uniformly.
The instantaneous mechanical-output port independently matches the published
native storage/damping equation within 1.6e-7 pu; the production port replay
remains within 1.94e-7 pu. Unlike the cached exciter
field port, this governor port uses the same-row speed. Applying the exciter's
history convention to it is a retained counterexample.

Every nonnegative native timestamp is reproduced by repeated binary32 clock
addition. The accumulated clock displacement reaches 1.27e-4/3.05e-4 s.
An explicitly separate fixed-step-index diagnostic does not remove the angle
floor. Likewise, double and rounded-stage/end washout replays do not remove
its discrepancy. Neither clock drift nor simple quantization alone is claimed
as the cause. Those discriminators are evidence for further diagnosis, not
replacement production equations or accepted time shifts.

## Repeatable artifacts and efficient storage

The raw native CSVs, runnable probe snapshots, complete logs and exact inputs
remain immutable in the private evidence archive. Public fixtures use a small
versioned binary32 container, gzip and bounded base64 parts. Before packing,
every scalar must reproduce its original twelve-significant-digit text.
`NativeFloatCsvCodec` reconstructs that exact text with ties-to-even rounding;
the existing original CSV SHA-256 must then match. No downsampling, smoothing,
precision fitting or event-row deletion is used. Header, dimensions, trailing
data, nonfinite values, signed zeros and decimal-rounding edges have guards.
The representation reduces this matrix from 234 encoded parts to 144.

Two fresh compatibility runs reproduce the earlier GENROU raw CSV hash after
the shared runner gains explicit duration and governor-variable support.
Existing short-profile bounds and production models are unchanged.

## TODO and verification status

- [x] Add eighteen cold-paired composed three-second profiles and both valve branches.
- [x] Preserve all raw rows, state allocations, parameter readback and runnable inputs.
- [x] Gate eight supported state channels, all initial fields and explicit governor replays.
- [x] Prove exact compact CSV reconstruction, not merely numerical similarity.
- [x] Retain clock, history and precision discriminators without changing production equations.
- [x] Finish the registered core/SMIB gates and source reconciliation for this slice.
- [ ] Resolve the nonshrinking washout, angle and coupled governor-state discrepancies.
- [ ] Establish complete instantaneous coupled voltage/P/Q/EFD/torque parity.
- [ ] Extend selector, auxiliary-limiter, zero-time and trip/reconnect coverage.
- [ ] Implement GENSAE checkpoint-history reconstruction and independent composed PowerWorld execution.

This slice is partial independent evidence, not VERIFIED or complete-family
acceptance. Exact runtime/source provenance belongs to the private
`2026-10-19-gensae-composed-recovery` evidence seal.

Final gates pass 48 focused, 410 plugin-core (six existing optional skips) and
1,760 scoped SMIB/model/solver tests (one existing diagnostic skip), with zero
failures/errors, plus 35 offline probe and six wording tests. Source reconciliation
checks 1,854 Java/POM files and 144 selected fixture parts. Unrelated worktree
changes remain excluded. These green regression gates do not close the four
explicitly diagnostic state gaps above.
