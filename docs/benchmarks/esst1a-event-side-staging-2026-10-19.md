# ESST1A event-side and observation-stage diagnostics

## Exact event sides, unchanged model equations

The earlier bypass comparison interpolated on time alone. That assigns the
post-step value to both raw samples at the reference-step boundary. A reusable
immutable `EventSideTrajectory` now selects BEFORE/AFTER explicitly and
interpolates only between adjacent continuous segments. It rejects extrapolation,
unordered/nonfinite/schema-inconsistent histories and ambiguous extra sides.
Initialization history is an explicit initialized-state query, not a hidden
endpoint clamp. Tests preserve immutability and both sides of each jump.

Twelve existing native histories retain every raw row and all 25 non-time
channels. Coupled SMIB traces now include separate snapshots before and after
the nominal 0.05 s event. The two independently identified native boundary
rows map to those event sides; the raw timestamp, query timestamp, row index
and chosen side are all retained. All other samples use the recorded time.
No reference file, parameter, state or existing acceptance bound is changed.

## Reference assignment is not algebraic settling

The native and InterPSS reference setters do not immediately advance allocated
dynamic states. Eight targeted native runs read all ten allocated states
immediately after the API call. In all four cold pairs those states and the
clock are unchanged from the preceding interval endpoint. Every CSV is also
byte-identical to the corresponding earlier bypass history: the added reads
do not change the simulation.

The native exported post-reference boundary appears when the next interval
resumes, not during that direct API read. Its zero-time regulator jump is
H*delta(Vref), where H is the forward algebraic feedthrough. The cached field
voltage and continuous allocated states remain unchanged across that boundary.
That exported stage is not equivalent to a completely settled algebraic loop.

InterPSS already provides state-preserving algebraic evaluation through
`getOutput()`. The new diagnostic calls that existing operation explicitly;
it does not add an integration step or change the setter/runtime. Repeated
reads must return the same settled output without changing generic storage.
With inactive limits and frozen dynamic states, the published feedback loop gives:

```
H = Ka * (Tc/Tb) * (Tc1/Tb1), when Ta=0
    (a bypassed lead-lag contributes one; positive Ta gives H=0)
beta = Kf/Tf
delta(Va)_settled = H/(1 + H*beta) * delta(Vref)
```

For the zero-regulator profile H=5 and beta=0.1. A 0.02 pu step therefore
gives a settled 0.0666667 pu jump; the native resumed boundary records about
0.1 pu. For all-bypass H=80 and beta=0, both algebraic jumps are 1.6 pu.
Native jumps are checked using the recorded reference increment and declared
gain, with a binary32/CSV-rounding allowance. InterPSS jumps are independently
checked against the complete closed-loop equation. No gain or state is fitted.

## What improves, and what remains open

The artificial approximately 1.6 pu all-bypass comparison error disappears
when before/after sides are distinguished. The remaining regulator maxima are
approximately 8.95e-4/4.84e-4 pu at the coarse/fine steps, away from the event.
Reference-channel differences fall from approximately 0.02 pu to 4.12e-8 pu.
The zero-regulator boundary retains approximately 0.0333331 pu difference
between the exported native stage and the settled algebraic stage. This is
explicit staging evidence, not a reason to alter physical feedback equations.
The subsequent field-voltage and nonshrinking numerical residuals still prevent
complete trajectory acceptance.

- [x] Preserve the original time-only comparison as an archived counterexample.
- [x] Add immutable event-side sampling and independent native/closed-loop
  equation guards without changing production models.
- [x] Retain every raw row/channel and both coupled event snapshots.
- [x] Distinguish direct API readback from resumed export using four exact cold
  pairs, all unchanged from the prior numeric references.
- [x] Finish full focused/core/scoped-SMIB gates and the source/evidence seal.
- [ ] Resolve native export-stage compatibility and subsequent numerical and
  lifecycle gaps before complete model acceptance.

No full Texas2k dynamics are run. These are diagnostic and local equation
checks; they do not label the entire generator/exciter family VERIFIED.

Final gates pass 70 focused, 410 plugin-core (six existing skips) and 1,781
scoped SMIB/model/solver tests (one existing skip), with zero failures/errors.
All 50 offline probe/wording tests pass. Source reconciliation covers 1,861
Java/POM files and 42 unchanged fixture parts. The private seal
`2026-10-19-esst1a-event-side` includes the eight additional observation runs,
all-channel diagnostics and terminal reports, and pins the previously sealed
1,480-file raw-reference evidence by its complete checked inventory.
The rejected forward-only jump assumption remains a counterexample; the
accepted local calculation includes the full washout feedback term.
