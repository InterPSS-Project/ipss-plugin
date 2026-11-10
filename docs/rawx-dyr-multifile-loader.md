# RAWX and DYR Combined Import

`PSSEMultiFileLoader.loadDStab("case.rawx", "case.dyr")` now accepts
RAWX topology together with DYR models. The first file's `.rawx` extension is
case insensitive. Optional sequence, preparation IDV, and response BSG files
continue through the existing multi-file pipeline. Numeric version overrides
apply to positional RAW records; RAWX uses its named columns and metadata.

The loader imports topology directly into the caller's dynamic network rather
than copying an ACLF network afterward. `PSSEJsonDirectParser` now accepts a
`BaseAclfNetwork` and optional `AclfNetworkObjectFactory`; `parseInto` avoids the
ACLF-only return cast. Its existing no-argument `parse` API is preserved.
Internal network access uses the builder's base-network accessor, so saved
settings, generators, and transformer lookups also support dynamic networks.

Three-winding transformers must use a container matching the network type.
That container creates the star bus and winding branches. Creating an ACLF
container in a dynamic network previously introduced incompatible buses.
The builder now selects dynamic or sequence containers when appropriate and
still honors explicit factories. Star voltage is assigned after allocation
through the base-bus API, avoiding an ACLF-only cast in older engine allocation
methods while retaining supplied or default voltage.

DYR named-device headers accept padding inside quoted model names. This keeps
named converter targets from being parsed as bus numbers. Deferred REPCA1 and
UEL2C validation failures are recorded with their source locations rather than
aborting non-strict import before its report is finalized. Invalid parameters
are reported, with the documented REECA1 Pmax/Pmin swap now applied automatically
and audited. Strict import continues to reject incomplete coverage.

Synthetic coverage in `RawxDyrMultiFileLoaderTest` checks machine attachment,
network identity, concrete generator/load types, custom factories, dynamic
transformer star types, RAWX-only import, short-circuit import, unchanged ACLF
parsing, and deferred rejection under strict and non-strict policies.
`PsseDyrRecordReaderTest` checks padded direct and wrapped converter headers.

Private large-case validation and all private import reports belong exclusively
to `qa.largecase`. Successful loading does not imply complete model coverage or
a validated dynamic simulation; callers must inspect `getLastDynamicImportReport()`.

## Local preparation configuration

Supply local `.gnet` and `.idv` files alongside the static and dynamic inputs:

```java
var loader = new PSSEMultiFileLoader();
var context = loader.loadDStab(
    "case.rawx", "models.dyr", "case.gnet", "config/prepare.idv");
var preparation = loader.getLastCasePreparationReport();
var dynamics = loader.getLastDynamicImportReport();
```

Explicit preparation inputs execute in their supplied order before DYR
attachment. `IDEV 'relative/path.idv'` resolves relative to its containing file
and applies the child at that point in the command sequence. Repeated includes
retain source order; recursive includes and nesting beyond 32 files are rejected.
The loader does not launch a native application, compiler, shell or executable.

Supported import-time directives:

| Directive | Import effect |
|---|---|
| `GNET` | Convert each listed bus's active generators into constant-power negative loads; record intentional DYR skips. |
| `IDEV` | Read another local configuration file at that source position. |
| `BAT_BUS_CHNG_4` | Update supported bus type, scope, voltage and normal-limit fields. |
| `BAT_LOAD_CHNG_6` | Update electrical ZIP/DG data, status and per-load area/zone/owner selection. |
| `BAT_PLANT_DATA_4` | Set the local/remote plant voltage and participation for existing and later-created machines. |
| `BAT_MACHINE_DATA_4` | Create/update the electrical generator, including P/Q, limits, MBASE, source/transformer impedances and tap. |
| `BAT_PLMOD_REMOVE` | Preserve the established model-slot removal and intentional DYR-skip behavior. |
| `DYRE` | Schedule a local DYR reference after preparation, unless an explicit DYR input already selects the file. |

An explicit DYR input takes precedence over interactive `DYRE` references.
Multiple implicit DYR references are rejected as ambiguous. The normal optional
sequence and BSG inputs continue through their existing import stages.

Empty comma fields retain existing values, including explicit numerical zero
where supplied. Source MW/Mvar values use the system base; machine source and
transformer impedances retain their own source convention. Device lookup uses
native case-insensitive identifier matching while retaining the actual imported
spelling. Ambiguous case variants are rejected. Every applied/deferred/error
entry retains its source file, line, command and fields.

A `.gnet` file accepts a standard `GNET` block or a bare bus list. Both terminate
with `0`; repeated bus numbers do not create duplicate negative-load equivalents:

```text
GNET
101
102
0
```

When no preparation files are explicitly supplied, the loader searches for
`<static-stem>_gnet.idv`, `<static-stem>.gnet`, and the existing DYR-stem
`_gnet.idv` / `_MODREMOVE.idv` companions. Explicit preparation files take
precedence over discovery. To import precisely the selected RAW/RAWX and DYR
without reading neighboring preparation files:

```java
var loader = new PSSEMultiFileLoader()
    .setAutoDiscoverCasePreparationFiles(false);
```

That switch affects discovery only; explicit preparation files still apply.
The supplied object factory is honored for new generators and GNET loads too.
RAWX preserves per-load scope metadata, so zone-changing IDVs and subsystem
dynamic-load selectors refer to the same source records as positional RAW.

### Scope of IDV support

This is import-time electrical preparation, not a general interactive-script
interpreter. `CASE`, solve/conversion commands, output redirection, `ALTR` menu
answers, snapshots, saves and compiler commands are reported as **DEFERRED**.
They do not reload the network, run load flow, change simulation timestep/network
acceleration, write application files or execute code. Inspect the preparation
report alongside the dynamic-model report.

Node-breaker section changes, emergency voltage-limit changes and unrecognized
`BAT_` commands fail with source-located diagnostics. Ownership fractions,
machine source-only flags, load scaling/interruptible flags and type metadata
without corresponding engine fields remain visible in the command report;
they are not claimed as applied runtime behavior. Supported electrical fields
are applied to existing objects without replacing their concrete types.

Synthetic tests cover source ordering, blank/zero values, GNET balance,
new-machine creation before DYR attachment, inherited scope, native identifier
matching, local filename quoting, cycle/missing-target failures, discovery
precedence, deferred operations, and factory preservation. Private package
validation and its native identifier discriminator remain in `qa.largecase`.
## Explicit dynamic-parameter corrections

Use `setDynamicModelParameterOverrides(record -> replacements)` on the loader
or direct dynamic parser for reviewed source-data corrections. `replacements`
is a `Map<Integer,String>` indexed in `record.parameters()`; those indices include
allocation fields for wrapped records, which cannot themselves be overridden.
The default is an empty map. Identity and field count stay fixed, effective data
still undergo normal model validation, and each changed value appears alongside
the original source location in the import report. No source file is rewritten.
REECA1 inverted finite Pmax/Pmin are automatically swapped after explicit
overrides. This applies to strict import too and requires no override callback.
Warnings and report entries distinguish automatic corrections from explicit
overrides and retain both when they occur in sequence. Ordered/equal bounds
stay unchanged; nonfinite limits fail validation. The typed model constructor
continues to require ordered limits.
PLNTBU1 retains its entered selector: exactly 1 enables active-power control;
other values select the native inactive path. Its unused frequency bounds remain
metadata and are not evaluated. A warning/report explains nonbinary values;
an explicit override is unnecessary to reproduce the source behavior. Enabled
limits and reactive controls still undergo validation. See the
[compatibility notes](dynamic-models/native-import-compatibility.md#explicit-corrections-and-auxiliary-battery-hosts).
# Missing-parent disposition

The combined loader ignores genuinely absent dynamic parents by default and
retains each affected source record as `SKIPPED_MISSING_HOST`. The report includes
the original disposition, source lines, bus/device identity, and the absent
parent. `setIgnoreMissingHosts(false)` restores failure reporting for coverage
audits. This is an import policy, separate from power-flow configuration.

Parent resolution runs after deferred attachment. A present host with an
unsupported port remains a rejection. Validation exceptions are not eligible
for missing-parent conversion: only explicit target failures and unsuccessful
attachment lookups can become skips. Invalid cardinality, invalid limits and
runtime errors therefore remain visible even when a parent is also absent.

Embedded protection generator IDs and branch circuit IDs are trimmed just like
ordinary record identities. Quoted padding must not create false missing targets.
Converter machines are available before dependent electrical and wind controls;
REGCB hosts accept the shared electrical/auxiliary and wind connections where
implemented. Detailed import success does not assert trajectory qualification.

## Dynamic run scripts

Use `setRunConfigurationFiles` for run scripts, separately from electrical
preparation inputs. Source settings apply before explicit JSON overrides;
conversion and runtime coverage reports remain available. See
[dynamic run configuration](dynamic-run-config-idv.md).


## Plant reactive-control ownership

RAW/RAWX `RMPCT` is common plant data repeated in machine records. Readers
retain the last entered plant value, including a record for an inactive unit,
in the existing bus's optional `plantMvarControlPFactor` (fractional units).
Individual machine factors remain available as source/device data. Summing
repeated values would incorrectly give a two-machine plant twice the weight
and change that weight on a unit trip. Generic builders with no plant setting
retain their per-generator aggregation.

RAW/RAWX exporters write the common value on every generator record, including
source-template updates, so reordering cannot revive a superseded plant value.
IDV plant commands accept percentages and convert to engine fractions exactly
once; omitted fields retain the existing common setting. Core state/JSON and
fast copies preserve the optional value; old snapshots keep the default absent.
The field is an engine-state extension outside EMF structural/XMI serialization.

`PlantParticipationImportExportTest` covers repeated and reordered values,
inactive final records, RAW versions 30/34/35/36, RAWX, sparse templates, and
IDV blank/zero fields. These data-contract checks do not certify whole-system
dynamic trajectory agreement or production ideal-link dynamics.
