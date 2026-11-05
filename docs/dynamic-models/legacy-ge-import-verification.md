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
