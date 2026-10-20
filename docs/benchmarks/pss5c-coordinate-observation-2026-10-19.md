# PSS5C coordinate observation and fine-step output diagnostics

## Outcome and limits of the finding

This slice adds a permanent read-only companion/cascade observation API and
fixes a diagnostic replay issue: duplicate boundary rows must update the
instantaneous input without advancing controller states. Production PSS5C
equations, integration coordinates, shared solver and established trajectory
bounds are unchanged.

Six cold-paired native SMIB profiles and six repeated exact-input replay pairs
localize an important remaining discrepancy. For the tested **nonzero band
gains**, recorded machine stabilizer output equals the sum of the low,
intermediate and high raw band variables, without the very-low variable.
This fixed three-band identity holds exactly in float32 arithmetic across
every new raw row, not through a fitted gain, offset or time shift.

It is **not a universal replacement equation**. The earlier profile with
three zero band gains contradicts both simple three- and four-band sums.
Therefore zero-gain behavior remains a separate open issue. The published
InterPSS four-band sum is retained, and complete native trajectory acceptance
remains open.

## Source-derived coordinate algebra

The original full-order high transfer function is

```
CH*s*s / ((1+TH*s)*(AH*s*s+BH*s+CH))
```

The existing solver stores companion position x, rate x' and acceleration x''.
An equivalent cascade realization has r, z and w:

```
z = x'/CH
w = x''/CH
r = x + AH*w + BH*z

r' = (speedDeviation-r)/TH
z' = w
w' = (r' - CH*z - BH*w)/AH

x = r - AH*w - BH*z
x' = CH*z
x'' = CH*w
```

Differentiating the inverse map produces the original companion equations;
the coefficients come only from the entered model. No state transform is
estimated from data. The low transducer's two companion coordinates are
unchanged. The source diagram is retained in the foundation archive, document
SHA-256 `40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.

`Pss5cTransducerCoordinates` provides immutable finite-valued `Companion` and
`Cascade` records, reversible conversions and an observation helper:

```java
var cascade = Pss5cTransducerCoordinates.observeCascade(pss5c);
var companion = Pss5cTransducerCoordinates.toCompanion(pss5c.getData(), cascade);
```

Observation never advances or replaces solver states. All existing thirteen
named-state keys remain intact. Reduced-order models are explicitly rejected
by this map because their observation coordinates differ; nonfinite inputs
and overflowing conversions are also rejected. The API is a source-equivalent
coordinate representation, not automatic certification of native raw-array
aliases, a parameter decoder or a whole-network stability assessment.

## Independent profiles and all-row preservation

The three requested profiles are flat, fault, and fault with the existing
nondefault transducer discriminator. Each runs at 0.00005 and 0.000025 seconds,
with two fresh native executions. The four frequency fields are explicitly
one in these diagnostic inputs, isolating the previously identified frequency
convention discrepancy. These are declared discriminator cases, not edits to
the original benchmark inputs or evidence of generic-frequency parity.

Fine-step permission is confined to an explicit diagnostic flag and the
PSS5C generator prefix; normal machine probes retain their original step
guards. All thirty constants and their readback precision are preserved.
The solver's low-order behavior is not replaced by a data-fitting correction.

Each coarse diagnostic contains 10,005 raw rows; each fine diagnostic contains
20,003. Every history contains two negative-time samples and two duplicate
segmented-run boundaries, including flat runs. Sample counts come from the
complete binary channel history, not a guessed nominal-step count. All twelve
raw runs contain 180,048 rows in total, with byte-identical channel CSVs,
staged inputs and API snapshots within each cold pair.

Native TIME advances by exact float32 addition. The observed last times are
approximately 0.500026762486 and 0.500004827976 seconds for a requested stop
of 0.5 seconds. The guard validates the negative-history anchor, every actual
float32 clock increment and the last API clock; it does not shift time,
truncate the end, discard duplicate rows or force an analytically guessed
sample count. An initial half-step endpoint assumption and a flat-run
duplicate-count assumption were rejected by this evidence and replaced with
the exact observable protocol, not a wider trajectory tolerance.

The replay consumes every recorded speed sample at its unshifted clock,
including both boundary sides. It exports all thirteen semantic states and,
on explicit request, the three equivalent high cascade coordinates. Both
executions of each replay produce identical CSV bytes. The new duplicate-row
fix updates algebraic feedthrough even if the input changes at the same
timestamp; a dedicated regression asserts that no state integration occurs.

## Observations and unresolved aggregate behavior

All sampled direct API states and variables match their corresponding binary
channel values exactly after float32 deserialization: 32 API snapshots in
each cold set, 64 across the twelve runs. The installed API documentation
also confirms machine channel 10 is the stabilizer output and channel 7 is
speed deviation. The discrepancy is not an incorrectly labelled machine
channel or lost export row.

Across the fine profiles, the raw low-variable identity
`state1 + DLI*state2` differs by at most about 8.2e-11 pu, and the raw high
identity `CH*state5` by about 7.1e-11 pu. These are reported residuals, not new
acceptance envelopes. All thirteen state channels are retained row-for-row;
each residual maximum records its channel, time and row.

For every new raw row with nonzero gains, the fixed left-associated float32
calculation below agrees numerically with recorded stabilizer output, treating
signed zero as numerically equal:

```
clip(float32(float32(VL + VI) + VH), Vstmin, Vstmax)
```

Adding VVL instead reproduces a discrepancy up to about 2.04e-4 pu during
the fault profiles. The exact-speed InterPSS replay retains a similar output
discrepancy, about 2.02e-4 to 2.04e-4 pu, despite low/high transducer output
differences around 1.7e-8 to 5.0e-8 pu. Halving the nominal step does not
remove that output bias. Monotonic refinement of every small residual is not
claimed, particularly with float32 accumulated clocks and arithmetic.

The retained earlier zero-gain discriminator is a necessary negative control:
VL/VI/VH variables are zero, VVL reaches about 1.13e-5 pu, but machine output
reaches about 0.03737 pu. Thus **both** aggregate hypotheses fail there.
Neither a universal three-band implementation nor a zero-gain bypass rule is
inferred from the nonzero profiles. Further zero-gain and source/version
semantics must be resolved before native trajectory acceptance. No reference-
specific output shortcut is added to the production model.

## Regression and reproducibility

Eighteen new Java tests cover both parameter sets, round trips, independent
derivative identities, read-only observations, unsupported orders, nonfinite
and overflowing values, negative history, exact endpoint integration,
duplicate-row feedthrough and rejected boundaries. The class is permanently
registered. Twelve offline counterexamples cover the fixed aggregate
hypotheses, zero-gain contradiction, both limits, source-only coefficient
maps, complete histories, nonfinite/missing/shifted replay, explicit profile
fields, isolated fine-step permissions and exact float32 clock recurrence.

The Java/POM baseline is `4f434dcc29b45db513f30b5db4d3f5256ca4ab4f` plus this
slice. Subsequent concurrent documentation-only commits do not change that
code; the source audit reconciles the final checkout rather than relying on
the old branch label. Unrelated files are preserved. The established isolated
build uses `-am`, the pinned offline cache and core identity
`1.4.3-salient-refresh-20261019`, jar SHA-256
`21519010c48bd265678743df7e98d3dcea530f5ceda1ccd7921d123d7f6b5333`.
The private seal destination is
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-19-pss5c-coordinate-observation`.
It retains full native and replay histories, the earlier single-band negative
control, source/dependency provenance, all reports and the prior inventory.

| Final run | Tests | Failures/errors | Existing skips |
|---|---:|---:|---:|
| `pss5c-observation-focused-1` | 199 | 0 / 0 | 0 |
| `pss5c-observation-units-1` | 427 | 0 / 0 | 6 |
| `pss5c-observation-smib-1` | 1,930 | 0 / 0 | 1 |

All 117 offline checks and wording guards pass. The source audit reconciles
1,887 Java/build files and 139 unchanged existing resource parts. The native
data/runtime classes, local linearizer and typed runtime are byte-identical
to the prior slice across all three builds. All twenty-four existing coupled
flat/fault CSVs and the typed comparison's error vectors/times are unchanged.
The new observer classes match across builds; replay classes match between
the focused and broad builds. The initial offline attempt caught a test-only
binary-literal equality mistake, corrected by asserting exact preservation of
the original input value, without changing a production equation or tolerance.

Remaining gaps are the native frequency convention, AH-zero and low-order
numerical policies, zero-gain/limit/lifecycle behavior, generalized raw-state
observation semantics and independent all-channel coupled acceptance. The
overall model-coverage goal remains active. Full Texas2k dynamics are not run.
