# PSS5C proper high-transducer reductions

## Outcome and explicit evidence boundary

The thirty-constant model now implements the two proper degree-two reductions
of the published high-transducer transfer function, both independently and
combined with the already supported first-order low transducer. Strict native
parsing, initialization, modified-Euler stages, named states, input feedthrough
and analytical A/B diagnostics are covered.

This is **published-equation support**, not a claim of complete native
trajectory parity. In particular, the installed reference engine's AH-zero
response has fewer active internal memories than the literal published
denominator. That discrepancy is retained, not fitted away or hidden by
substituting the reference response for the source equations.

## Source and derivation

The unchanged source diagram is retained in the native foundation archive,
printed pages 106-107, document SHA-256
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
It specifies

```
Hhigh(s) = CH*s*s / ((1 + TH*s)*(AH*s*s + BH*s + CH))

a1 = TH + BH/CH
a2 = (AH + TH*BH)/CH
a3 = TH*AH/CH
```

With CH positive, either `TH=0, AH>0`, or `AH=0, TH>0, BH>0` gives a positive
a2 and zero a3. The exact proper realization is

```
dx/dt = v
dv/dt = (speedDeviation - x - a1*v)/a2
highSignal = (speedDeviation - x - a1*v)/a2
```

The instantaneous speed coefficient is `1/a2`. It enters the high-band input
and its B-vector terms, not just the displayed output. Predictor and corrector
use their respective complete endpoints. Both position and rate initialize
at a zero high-output equilibrium for constant non-nominal speed.

`highPosition` and `highRate` retain the active companion coordinates.
`highAcceleration` remains an inactive frozen coordinate for these reductions;
the algebraic acceleration is available through `getHighInput()`, not that
frozen memory key. All thirteen named-state keys are preserved. The full-order
high path and first-order low path keep their existing realization.

If both leading high factors vanish, or AH and BH both vanish, the denominator
degree is below the squared numerator degree. These improper reductions remain
explicitly unsupported: no input differentiator, epsilon, fitted time constant
or silent bypass is invented. Negative leading coefficients and CH zero also
remain outside the supported reduced-order cases. Positive TH/AH never trigger
an implicit order switch based on timestep.

The separate typed PowerWorld model is unchanged. Its twenty-one-value input
does not expose the native transducer coefficients and is not used as proof
of the zero-coefficient native profiles.

## Four cold reference pairs

The installed engine/library identity is unchanged from the preceding slice;
the library SHA-256 is
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
Exact version, wrapper and Python identities stay in private manifests.

Each high profile changes exactly one of thirty entered constants: TH or AH
to zero. Both profiles run at 0.0001 and 0.00005 seconds, each cold-repeated
twice. There are 88 independent initialized tiny-SMIB state-space cases:
control plus signed perturbations of all five transducer storage coordinates
per batch. Entered/read-back constants, allocation, all thirteen states, six
raw variables, binary channels and complete startup histories are retained.
The two members of every pair reproduce response JSON, network, dynamic
records and staged probe bytes exactly.

The diagnostic distinguishes three observations without fitting coordinates:

- Erased: a signed perturbation leaves no later state or variable response.
- Frozen-unobserved: only the injected storage coordinate remains constant;
  it has no response in other states or variables.
- Active: neither of the above.

Only an observed two-coordinate active subspace is passed to the invariant
two-state transition estimator. Three active coordinates cannot be reduced by
choosing whichever pair looks closest to an expected pole. Singular matrices
are rejected, never pseudoinverted. These are short discrete-response
diagnostics, not certified raw-state aliases or all-channel trajectory gates.

For TH zero, raw offset 2 is frozen and unobserved; offsets 3/4 are active.
Their measured poles are approximately `-79.99809, -0.99961` at the coarse step
and `-79.99898, -0.99973` at the fine step. The source poles are `-80, -1`.
This supports the denominator reduction but does not prove output/coordinate
or complete coupled trajectory equivalence.

For AH zero, raw offsets 3/4 are erased; only offset 2 is active in these
experiments. Its measured decay is near `-1` at both steps, whereas the source
degree-two denominator has poles `-1` and `-0.98765432`. Thus this installed
engine's zero-AH handling must be resolved before native acceptance. We do not
claim a universal simulator defect or infer an undocumented bypass policy
from this observation alone. Source equations and numerical compatibility
remain separate contracts.

## Tests and retained gaps

Twenty-two new controller tests cover both native-reader branches, independent
stage equations, all-coordinate A/B reconstruction for four low/high order
combinations, source-derived sinusoidal magnitude **and phase**, equilibrium,
both high/final output limits, inactive-limiter invariance and explicit
unsupported cases. The analytic frequency tests retain the existing family's
fixed 0.2% numerical allowance; no measured native error generates a bound.
The test class is registered permanently in the core suite.

Sixteen additional imported SMIB runs combine both high reductions with full
and first-order low sensors, flat and three-cycle three-phase fault profiles,
and two integration steps. They retain separate exports and frozen-coordinate
assertions. Eight new offline counterexamples guard frozen/erased/active
classification, refusal to select convenient pairs, missing responses,
nonfinite observations, clocks and singular transitions.

Remaining requirements include the AH-zero compatibility contract, native
frequency-field convention, full state/raw-VAR observation semantics,
static-low/zero-gain/lifecycle branches and independently justified all-channel
coupled trajectory acceptance. Complete PSS5C verification and the wider
model-coverage plan remain unfinished. No full Texas2k dynamics are run.

## Reproducibility

The final source baseline is `233f66dcd9213bbb7f520d60165baf80c83c3d65`
plus this slice. A concurrent governor fix advanced HEAD after the initial
focused/core runs and during the initial broad run. Those preliminary reports
are retained, not used to certify the new checkout. The final isolated build
is resynchronized and all three gates rerun against the committed governor
fix as well as this slice. Unrelated uncommitted files remain excluded.
Integration selections use `-am`; profiles are
`isolated-verification,extended-tests`, core identity
`1.4.3-salient-refresh-20261019`, with jar SHA-256
`21519010c48bd265678743df7e98d3dcea530f5ceda1ccd7921d123d7f6b5333`.
The private seal destination is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-19-pss5c-high-transducer`.
It retains exact raw histories, reports, sources, dependency provenance,
diagnostics and the previous evidence inventory. Previous artifacts and
all pinned core jars are preserved. Final XML reports confirm:

| Final run | Tests | Failures/errors | Existing skips |
|---|---:|---:|---:|
| `pss5c-high-focused-final-1` | 181 | 0 / 0 | 0 |
| `pss5c-high-units-final-1` | 427 | 0 / 0 | 6 |
| `pss5c-high-smib-final-1` | 1,912 | 0 / 0 | 1 |

All 105 offline checks, wording guards and retained native/dialect audits pass.
The reconciled source corpus contains 1,885 Java/build files and 139 unchanged
resource parts. Source comparison permits CRLF-to-LF normalization only; raw
forms are retained. The governor, governor-data, builder and parser classes
are demonstrably rebuilt from the concurrent commit, identical across final
builds, and different from the preliminary build. Its three newly added
startup cases execute in the final broad gate.

All eight prior full/first-order-low coupled CSVs are byte-identical. Sixteen
new high-reduction exports are kept separately. The typed PSS5C compiled
runtime and comparison error vectors/times are unchanged. No existing bound
is widened and no reference history is replaced.
