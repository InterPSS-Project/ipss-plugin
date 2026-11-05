# Legacy wind-suite import verification

A synthetic SMIB import test confirms that the installed alternative reference
engine converts the legacy wind suite into generic WT3 models. Successful import
and simulation therefore do not establish exact legacy-model equation parity.

| Entered model | Observed imported runtime |
| --- | --- |
| GEWTG2 | WT3G2 |
| GEWTE2 | WT3E1 |
| GEWTT1 | WT3T1 |
| GEWTP2 | WT3P1 |
| GEWGD1 | Suite conversion reported; no separate auxiliary exported |
| GEWTA2 | Suite conversion reported; no separate auxiliary exported |
| GEWPLT2 | Suite conversion reported; no separate auxiliary exported |

The results agree with the vendor's [published conversion policy](https://www.powerworld.com/patch/october-21-2015).
They do not prove that auxiliary behavior was preserved or that every input field
participates in the converted equations. The newer vendor-specific GEWTG,
EXWTGE and WNDTGE interfaces are also distinct; their availability does not prove
legacy-interface equivalence.

## Reproducible discriminator

`tools/legacy_ge_powerworld_import_probe.py` selects a complete same-owner suite
from a supplied private DYR, stages it with the synthetic SMIB network, remaps only
the owner and plotting target, and preserves the other integer and constant
values. Original selected records, staged records and source hashes stay in the
private generated output. No source system names, records or instance statistics
belong in public documentation or commits.

The runner captures imported model/parameter readbacks immediately after dynamic
data loading, after power flow and after initialization. Automatic parameter
correction is disabled. The short fault simulation captures terminal voltage and
active/reactive power; it exercises the converted runtime only. Failed starts
remain failed evidence and cannot count as model rejections.

Cold repeats completed validation with no errors or warnings, initialized and
exported the complete short trajectory. The input DYR, pre-initialization and
post-initialization model readbacks, and full trajectory file were byte-identical.
Executable and artifact hashes are retained privately. This is reproducible
conversion evidence, not an acceptance result for any exact legacy model.

To repeat, run the probe through `tools/run_bounded.py` with a unique `--tag`,
private `--source-dyr`, the existing benchmark `--runner` and installed
`--executable`. The installed desktop license context is required. The probe
writes only its unique `target/legacy-ge-import-*` output and never overwrites an
existing run. Archive its complete evidence manifest with the selected records;
do not publish those private inputs.

## Consequence for implementation

The converted WT3 SMIB can test our explicit generic conversion path, if such a
path is added with an honest conversion status and documented field mappings.
Exact legacy implementation still requires authoritative equations, state and
output definitions, host bindings and an independent exact implementation.
Import acceptance alone must not promote these model identifiers to verified.

## Signed pitch-rate compatibility and converted-runtime comparison

The exported WT3P1 record retains a negative pitch-rate entry. The official
[parameter table](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20WT3P%20and%20WT3P1.htm)
identifies WT3P `Pirat` and WT3P1 `RThetaMax` as the pitch-rate limit in degrees per
second. The latter is the eighth numerical DYR field and the corresponding AUX
field. The [documented runtime policy](https://www.powerworld.com/patch/december-7-2015-3)
uses the absolute magnitude for WT3P1. This is an explicit converted-interface
policy, not a change to native parameter validation.

`Wt3p1Data.RateLimitPolicy.SIGNED_MAGNITUDE` retains the entered signed value and
prepares its magnitude for the symmetric numerical limiter. The existing
nine-argument constructor remains strictly nonnegative. Positive and negative
compatibility inputs produce identical predictor/corrector histories, while
negative native input remains rejected. Existing positive native-reference
controls continue to pass.

The separate [negative minimum-pitch initialization policy](https://www.powerworld.com/patch/may-11-2016)
chooses zero initial pitch. The current coupled initialization already starts at
zero for this profile; no initialization equations were changed in this slice.

The reference engine exported an unambiguous converted DYR, then timed out before
completing the augmented export script. That failure is retained separately; the
comparison uses the previously completed, repeatable reference trajectory. The
zero-inertia reference source is represented through the existing infinite-machine
API, without a large-inertia approximation. A typed pitch attachment selects only
the documented compatibility policy. Physical terminal power is measured from
voltage and net injected current, avoiding cached controller measurements.

The earlier modified-Euler diagnostic used the RAW source impedance as its
converter host reactance. It retained every sample and both event-side
duplicates, but was **not accepted** under fixed maximum-error gates of 0.001 pu
voltage and 0.1 MW/Mvar power:

| Channel | Initial absolute error | Maximum absolute error |
| --- | ---: | ---: |
| Terminal voltage | 0.000198815 pu | 0.0294884 pu |
| Infinite-source bus voltage | 0 pu | 0.0000277582 pu |
| Active power | 0 MW | 5.35636 MW |
| Reactive power | 0.200000 Mvar | 33.1528 Mvar |

These are errors of the converted generic combination, not legacy-model errors.
`tools/compare_legacy_ge_converted_smib.py` checks complete sample count, finite
channels, timestamp/event-side ordering and the fixed gates; it fails on a rejected
comparison. No time shifts, fitted parameters or discarded event rows are used.

The converted WT3G2, WT3E1, WT3T1 and WT3P1 bundle now runs the complete
diagnostic under both modified Euler and staggered integration, with all
samples and event-side duplicates retained. The source record's published
`XEQ` field is applied to the synthetic generator host for this conversion
comparison. The equivalent reactance inferred independently from the reference
initial voltage, reactive power and converter state agrees with that field.
The original RAW source impedance does not represent the converted host
reactance in this diagnostic.

With that source-derived host reactance, both terminal-voltage channels pass
the unchanged 0.001 pu maximum-error limit under both methods; the largest
terminal-voltage error is below 0.000747 pu. Active- and reactive-power
maximum errors remain above their unchanged 0.1 MW/Mvar limits, reaching about
1.793 MW and 0.624 Mvar. The complete converted comparison is therefore still
rejected. This supports the converted network-interface mapping only; it does
not establish exact legacy-model equivalence or qualify the remaining power
response.

An independent synthetic input control changed only the entered generator
reactive power from 0 to 1 Mvar. The complete reference output CSV was
byte-identical, including the initial 0.2 Mvar output and both event sides.
Thus the entered RAW reactive-power field does not determine the remaining
initial offset in this preparation path. The control does not identify which
initialization or network rule produces that offset. Private input/output
evidence and the exact byte audit are retained at
`verification-artifacts/legacy-ge-qg-discriminator-20261104/validation.zip`,
SHA-256 `5edc5ac69578e39e91c363f65b507df1323898419e254df8495e57d5dd90a303`.

An additional synthetic-case stage export isolates the preparation rule. The
reference importer exposes a finite −0.2 to +0.2 Mvar reactive range immediately
after RAW load, before any dynamic-model record is read. Its load flow moves the
unit to the +0.2 Mvar limit and leaves the terminal voltage at approximately
0.998947 pu. Our RAW importer instead gives the unity-power-factor wind unit a
zero-width range and classifies its bus as PQ. In the converted diagnostic only,
restoring the observed finite range and PV bus type before load flow matches
initial voltage within 2.3e−9 pu and initial reactive power within 3e−9 Mvar.
This does not establish a general RAW-import rule for other wind units.

With the corrected synthetic-case power flow and the recompiled candidate core,
the complete 303-sample comparison still fails the unchanged 0.1 MW/Mvar gates.
Both methods pass the 0.001 pu voltage gate. The maximum errors are:

| Method | Terminal voltage | Active power | Reactive power |
| --- | ---: | ---: | ---: |
| Modified Euler | 0.000770 pu | 1.787 MW | 0.649 Mvar |
| Staggered | 0.000770 pu | 1.787 MW | 0.649 Mvar |

The largest P/Q mismatch is on the fault-clearing event side. The isolated
startup correction therefore does not qualify the converted transient response
or the original legacy model. The private stage exports, all comparison rows,
reference trajectory and focused test log are retained at
`verification-artifacts/legacy-ge-raw-q-range-20261104/validation.zip`,
SHA-256 `e48b086036adec2501fb9794e9981c642f1541501e156096a6d13062cfa7924d`.

## Converted numerical-settings discriminator

A separate converted-model SMIB comparison changed only the reference engine's
integration method from RK2 to Euler, then separately tightened its network
convergence tolerance from 1e-4 to 1e-5 pu. The model records, fault, 0.5 ms
interval and all recorded states were identical. Both runs retained every
sample and both sides of each event; exported options confirm the settings.

At the fault-clearing event-right sample, Euler changed reference active
power by 0.2410 MW and reactive power by 0.00163 Mvar. The active-power gap
against the existing modified-Euler converted-model run decreased from 1.7866
to 1.5456 MW, while the reactive-power gap remained about 0.65 Mvar. Tightening
the reference network tolerance changed clearing P/Q by less than the exported
precision. Neither setting meets the unchanged 0.1 MW/Mvar gates, and neither
supports exact legacy-model equivalence.

The next discriminator is a side-matched comparison of converter current and
command states at fault clearing, using the already recorded reference states
and a corresponding local trace. Do not fit a model parameter or change the
legacy import status based on this converted-model setting trial. Complete
private inputs, option readbacks and output traces are retained in
`verification-artifacts/legacy-ge-settings-20261105/validation.zip`, SHA-256
`b032da3914b0cdd7cef6944100f0fc18ae74351db260d2481cb6e07e136895c6`.
