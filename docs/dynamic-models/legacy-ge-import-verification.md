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

The modified-Euler comparison retains every sample and both event-side duplicates.
It remains **not accepted** under fixed maximum-error gates of 0.001 pu voltage
and 0.1 MW/Mvar power:

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

Staggered integration currently rejects WT3G2 explicitly. Its converter and
attached electrical, turbine and pitch state bundle still need a shared
state/derivative/limiter/event owner before a second-method trajectory can be
claimed. The focused diagnostic preserves this rejection and never falls back to
another solver. Passing the signed-rate tests is not full model qualification.
