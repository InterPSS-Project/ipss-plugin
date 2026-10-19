# ESST1A washout coordinate and controlled-input verification

## Corrected observation, unchanged equations

The previous published-state snapshot returned CML washout storage directly.
That happened to agree with the native allocation when Tf=1, hiding a scale
error at other time constants. A new regression fails before the correction:
at Tf=0.5, 0.120130209830255 pu was expected but 0.240260419660511 was reported.

For positive Tf, the two realizations are:

```
CML storage       c = (Kf/Tf) * low_pass(VE, Tf)
native storage    x = Kf * low_pass(VE, Tf) = Tf*c
feedback output  VF = (Kf*VE - x)/Tf
```

`getNativeStateSnapshot()` now multiplies the live washout block's stored
coordinate by its running time constant. It does not use a possibly edited
configuration field. The generic named-state keys/storage, transfer function,
initialization, current/voltage limits and predictor/corrector are unchanged.
The [published ESST1A diagram](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST1A%20and%20ESST1A_GE.htm)
defines the transfer function; native state scaling is distinguished by direct
state readback at three time constants, not inferred from that diagram alone.

## Controlled source discriminator

Six profiles use Tf=0.5/1/2 s at 0.5/0.25 ms. Each runs twice cold. All twelve
runs pass native initialization and convergence checks, and all six raw CSV
pairs match byte-for-byte. The generator and other exciter constants remain
unchanged, except for a declared 2 pu regulator ceiling. Field-current limiting
and terminal-field derating are disabled. A +0.2 pu voltage-reference step at
0.05 s drives the regulator to its ceiling; no machine/controller state is forced.
These are three-second SMIB reference-step runs, not fault or full-system runs.

All generator/exciter states and physical channels, negative history and both
interval boundaries are retained. Live STATE and DELT reads at four endpoints
are recorded independently of channel export. Every live state agrees with
its matching recorded channel within 5e-12 pu. Reading live states does not
change the pilot CSV hashes. This rules out a channel-decoding explanation
for the observed endpoint values.

Initial native washout storage is Kf*Efd for all three Tf values. With regulator
and field outputs fixed at 2 pu, its steady target is 2*Kf, not 2*Kf/Tf.
The former unscaled assumption produces approximately 0.199/0.0766 pu errors
at Tf=0.5/2; those failed discriminators remain private counterexamples.

## Analytic equation and method checks

From 0.5 s onward, the imported InterPSS cases have constant regulator,
pre-field-limit and field outputs at 2 pu, with an outward saturation margin.
The unforced state is compared with the exact first-order solution starting
from that segment's own initial boundary:

```
x(t) = 2*Kf + (x(t0)-2*Kf)*exp(-(t-t0)/Tf)
z = h/Tf
modified-Euler recurrence factor = 1-z+z*z/2
global error bound = |x(t0)-2*Kf|*z*z / (6*(1-z/2)) + 2e-12
```

The bound follows by telescoping the local exponential Taylor remainder; it
is not fitted to the observed trace. Every constant-segment step also checks
the explicit modified-Euler recurrence within 4e-15 pu, distinguishing it
from an implicit corrector. The feedback-output identity is checked separately.
The maximum analytic error is about 2.14e-9 pu and decreases approximately
fourfold when the timestep is halved. Initial published-state comparisons and
pure observation/configuration-edit guards cover all three time constants.

The native constant-input state still differs from its analytic solution by
up to approximately 2.30e-5 pu and does not refine uniformly. Live state reads
confirm the discrepancy is not introduced by CSV decoding. Earlier rounded-
stage/end and coefficient-realization discriminators do not establish its
cause. Native internal staging/numerical fidelity remains unresolved; this
result does not justify fitting a time constant, changing the physical equation
or broadening the existing three-second coupled-state acceptance.

## Dependency and acceptance scope

The latest committed plugin baseline adds two salient electrical-refresh guards.
Both fail against the preceding pinned core. A separate versioned verification
dependency now incorporates exactly the baseline's three hash-identified
corrected salient class files. All other jar entries and protected older caches
remain unchanged. The observation regression and both new baseline guards pass
with this dependency. Exact source/class/jar identities and the installation
recipe remain in the private evidence archive; this is not an upstream release claim.

- [x] Capture failing-before nonunit-Tf observation evidence.
- [x] Correct only the published observation coordinate and retain generic keys.
- [x] Generate six exact cold pairs and independent live-state/step readbacks.
- [x] Verify the controlled InterPSS equation, analytic bound and explicit stages.
- [x] Retain native numerical and rejected-coordinate counterexamples without fitting.
- [x] Finish final registered SMIB/core gates and the source/evidence seal.
- [ ] Resolve native internal numerical/staging behavior and the longer coupled-state gaps.
- [ ] Add zero-time, auxiliary-limiter, selector and lifecycle profiles before complete-family acceptance.

Final reports contain 58 focused tests, 410 plugin-core tests (six existing
skips), and 1,769 scoped SMIB/model/solver tests (one existing skip), with no
failures or errors. Offline probe and wording guards are included in the
private evidence seal along with the failing-before observation regression,
older-core baseline failures, exact corrected dependency, complete cold runs,
lossless public fixtures, diagnostic traces and tested-source reconciliation.
No existing acceptance tolerance was widened.

No full Texas2k dynamics are run. This closes a published-state observation bug
and adds controlled equation evidence; it is not complete ESST1A or GENSAE
verification. The private evidence set is `2026-10-19-esst1a-washout-coordinate`.
