# PSS5C native full-order foundation

This slice adds the thirty-constant native PSS5C record and a separate
thirteen-state implementation of its published full-order equations.
It corrects a real schema gap: the reader previously accepted the 21-value
PowerWorld layout and had no native transducer/input-gain fields. The existing
typed PowerWorld implementation and its accepted benchmark limits remain
unchanged. Native trajectory acceptance is **not** established by this slice.

## Source contract

The installed model library, printed pages 105-107 (PDF 117-119), has identity
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
Its record contains thirty constants and **no selector**. The engine allocates
one internal ICON; allocation cardinality must not be mistaken for record
cardinality. The full allocation is 1 ICON / 30 CON / 13 STATE / 6 VAR.
The schema, diagram and notes were rendered and visually reviewed locally.
The sample table and recommended-value note differ by a factor of ten for
DL-I. The probes enter 0.001759 explicitly and retain its readback; the two
source values are not silently averaged or substituted.

The first nine constants specify two transducers and their gains:

```text
DL-I, AL-I, BL-I, KC1, TH, AH, BH, CH, KC2
```

The next sixteen contain four sets of gain, central frequency, upper and
lower bound. The last five are k1, k2, k3 and final upper/lower bounds.
The published transfer functions are

```text
Low/intermediate: (1 + DL-I*s) / (1 + BL-I*s + AL-I*s^2)
High:            CH*s^2 / ((1 + TH*s)*(AH*s^2 + BH*s + CH))

Each band: K * [(1+s/(k3*F))/(1+s/(k2*F))
               -(1+s/(k2*F))/(1+s/(k1*F))]
```

KC1 and KC2 multiply their respective band inputs. Four band limits precede
the final summed limit. Native limits swap if reversed, without changing
their signs. Documented zero-k and invalid active-frequency bypass rules are
represented explicitly. The current realization rejects non-bypassed
degenerate transducers instead of dividing by zero or pretending they are
implemented. Their reduced-order handling remains open.

The [PowerWorld PSS5C reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS5C.htm)
defines a separate 21-value parameter interface and its own corrections.
That implementation is retained through `addPss5c`; native import uses
`addPss5cNative`. The original typed input fixture is not rewritten into a
different dialect. The approved source table's blank native cell also remains
blank; installed-library support is separate evidence, not an invented source fact.

## Implementation and mathematical checks

`Pss5cNativeData` defensively stores exactly thirty finite constants.
`Pss5cNativeStabilizer` uses five companion transducer states and eight
parallel lag memories. Predictor and corrector slopes are sampled atomically
from complete thirteen-state endpoints. The shared integrator is unchanged.
Constant non-nominal speed initializes the sensor and band memories at their
derived equilibrium, without fitting a reference state. Observation methods
do not advance state, and the semantic state map is immutable.

The focused native model tests check strict attachment, rejection of 21/29/31
values without replacing a device, all constants, defensive copying,
predictor/corrector recurrences, equilibrium, sign-preserving limit swaps,
bypass rules and unsupported degenerate transducers. Low and high paths also
match independently evaluated published complex transfer functions with a
fixed 0.2-percent second-order numerical allowance, not an observed-error fit.
Four imported solver tests exercise flat and three-cycle faults at 0.5 and
0.25 ms using the existing core round-rotor generator. These solver checks are
not a substitute for independent trajectory acceptance.

## Repeatable references and unresolved discrepancies

`tools/pss5c_native_probe.py` records every allocated native state, all six
VARs, physical boundaries and output. Source-specific profile changes are
explicit: transducer coefficients, input gains, a single active band and
unit central frequencies. The exact inputs and readback constants are retained;
neither model parameters nor initial states are optimized.
`tools/check_pss5c_native_artifacts.py` checks exact schemas, readback precision,
run quality, all values, hashes, negative-time history, duplicate event rows
and cold-repeat equality. It reports raw-VAR sum differences as diagnostics,
not as tolerance-controlled conformance.

Nine clean profile pairs retain eighteen complete native runs: baseline flat,
baseline fault at two steps, changed transducers at two steps, changed gains
at two steps, very-low-only and unit-frequency discriminators. A zero-DL-I
profile produces NaNs and an export failure; its attempts remain rejected,
not substituted with successful data.

The initial assumption that the four raw band VARs can be summed to reproduce
VOTHSG failed. A proposed running-sum interpretation also failed the
single-band discriminator. The failed check and raw counterexamples are
retained; its bound was not widened. Raw-VAR meaning/timing and zero-gain
behavior remain unresolved.

`Pss5cNativeReplayProbe` replays the exact recorded speed through the Java
controller at readback precision, preserving every history/event row and
without injecting native states. `tools/analyze_pss5c_native_replay.py` reports
all-row maxima and their times, without generating acceptance envelopes.
Baseline output residuals are about 0.01626 pu at both steps, so they do not
show timestep refinement. Changing all band frequencies to unity reduces the
high-band residual from about 0.0208 to 1.2e-6 pu. This localizes a
frequency-convention/implementation discrepancy; it does not authorize
replacing the published 1/(kF) time constants with an empirically inferred
rule. A low-transducer discrepancy also remains.

No new native trajectory bounds or VERIFIED label are introduced. In
particular, the new semantic state coordinates are not represented as
certified aliases for the native engine's thirteen internal coordinates.

## Final regression and provenance

The final gates contain 123 focused tests, 427 core-plugin tests and 1,851
scoped SMIB/model/solver tests, with zero failures/errors and seven existing
skips across the latter two gates. All 79 offline checks pass. The generated
support/evidence documents match their generators; the previous typed PSS5C
comparison's error vectors and times are unchanged.

The isolated build uses `tmp/wtdtb1-powerworld-verify54b/pom.xml`, profiles
`isolated-verification,extended-tests`, offline cache
`target/repca-native-verification-m2-20261019` and core identity
`1.4.3-salient-refresh-20261019`. Integration tests use `-am`.
Run identifiers are `pss5c-native-focused-2`, `pss5c-native-units-1` and
`pss5c-native-smib-1`. The focused selection includes the native model and
SMIB classes, catalog tests, existing builder tests, typed PSS5C comparison
and evidence-inventory tests. Core units use `-Dtest=*`; the broad gate uses
the established complete SMIB suite and model/solver supplement.

The source audit reconciles 1,881 Java/build files and 139 unchanged existing
reference-resource parts. The private evidence seal is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-19-pss5c-native-foundation-final`.
It retains native pilots, eighteen clean runs, two rejected NaN attempts,
eight all-row replay exports, raw counterexamples, regression reports,
source snapshots, rendered source pages and dependency provenance.
Its hash inventory links the prior sealed slice. Text-fixture comparison
permits CRLF-to-LF normalization only; both raw forms remain retained.

## Remaining work

Follow-up: the [pole-contract diagnostic](pss5c-pole-contract-2026-10-19.md)
adds paired-state perturbation evidence and a permanent read-only eigenvalue
API. It does not change the equations or grant native trajectory acceptance;
the foundation results and open requirements below remain applicable.

- Resolve the native band-frequency and low-transducer discrepancies against
  authoritative equations and independent exact-tool discriminators.
- Establish raw VAR/output timing and all thirteen state-coordinate meanings.
- Implement and verify reduced-order transducers and remaining active
  limits, zero gains and lifecycle profiles.
- Add independently justified, all-channel trajectory acceptance after those
  discrepancies are resolved; keep the current diagnostics unaccepted.
- Continue the wider model-coverage plan. No full Texas2k dynamics are run here.
