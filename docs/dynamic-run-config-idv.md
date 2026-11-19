# Dynamic run configuration and ordered local scripts

`DStabRunConfigRec` is schema version 1. Nullable settings preserve the current
run when omitted. Convert source scripts first, then overlay explicit JSON;
explicit false and numerical zero are preserved. Import/file-selection policies
remain outside the solver-settings record.

```java
var loader = new PSSEMultiFileLoader()
    .setRunConfigurationFiles(Path.of("setup.idv"), Path.of("run.idv"))
    .setDynamicRunConfigJson(Files.readString(Path.of("overrides.json")));
var context = loader.loadDStab("case.rawx", "models.dyr", "prepare.idv");
var conversion = loader.getLastRunConfigConversion();
var unapplied = loader.getLastRunConfigApplicationGaps();
```

The script converter supports ordered local includes, quoted filenames,
source comments, explicit initialization options, network limits/tolerance,
physical timestep, the documented parameter-change dialogue, run endpoints and
recording cadence, fault/clear/trip/disconnect/status event descriptions, and
registered governor-policy references. The loader adds those BSG references to
its normal Java policy-registration path, without requiring duplicate import
arguments or executing the named program. The result retains every command's
source, line, original text and disposition. Event descriptions are returned
as a plan; importing settings does not automatically install event handlers.
The private scenario runner retains its existing event installation path.

The standalone conversion entrypoint, using the normal plugin runtime classpath:

```text
java -cp <runtime-classpath> org.interpss.fadapter.psse.PsseIdvRunConfigConverter --idv setup.idv --idv run.idv --config-out dynamic-run.json --report-out conversion-report.json
```

Outputs use create-new semantics. Binary saved-case/snapshot restoration,
external compilation and unrecognized commands are reported as unsupported;
they are never executed or silently discarded. Reporting/channel/subsystem
selection commands remain output metadata, not a claim that every requested
channel is being recorded. Electrical preparation still uses the combined case
loader's separate ordered inputs.

## Qualified initialization policies

```json
{
  "schemaVersion": 1,
  "sourceImpedanceReconciliationEnabled": true,
  "activeStaticLoadModel": "CONST_I",
  "reactiveStaticLoadModel": "CONST_Z",
  "loadModelThresholds": {
    "minimumMw": 5.0,
    "minimumAbsPOverQ": 1.61,
    "minimumVoltagePu": 0.97
  }
}
```

Source-impedance reconciliation is explicit. At initialization it aligns the
in-memory generator source/positive-sequence impedance with the attached
synchronous machine's reactance, converting the machine and generator MVA bases
once. It preserves the source files and machine equations. Each changed or
retained source has an original/effective-value report. With the setting absent,
this new record-update policy does not run. Disabling it retains the entered
source records; the engine's established machine Norton stamp remains owned by
the dynamic machine and is not replaced by a mismatched static equivalent.

Thresholds are assignment-specific. Qualified subsystem composite assignments
screen their own source load using solved initial MW, abs(P/Q), and parent-bus
voltage before creating a distribution equivalent or motor. Equality is
admitted. A zero-Q load does not fail the ratio test. A screened assignment
retains its original source record and status; the remaining static power uses
the configured dynamic conversion. Its relay continues to target that source. Multiple source loads on one bus
are screened independently. Explicit legacy bus/load composite assignments do
not consume these subsystem cutoffs. Independent initialization discriminators
establish that distinction; a universal dynamic-load-disable rule would change
the source behavior. Thresholds are inactive when omitted.

Inspect `getLoadModelScreeningReport()` and
`getSourceImpedanceReconciliationReport()` on the attached solver after
initialization. These reports remain separate from DYR import coverage: an
attached assignment may intentionally use the static fallback at initialization.

## Conversion is separate from runtime equivalence

The source network-frequency-dependence switch changes line/source reactances,
shunts and reactive impedance loads. It is not equivalent to enabling bus-frequency
measurement. The source network acceleration factor is not assumed to be the
same iteration algorithm as InterPSS under-relaxation. Relative-angle selection
is an output convention; physical rotor states retain their coordinates.

These policies are retained in the JSON, and `applyTo` returns runtime gaps for
unqualified equivalents. `setRequireMatchedRunSettings(true)` rejects unsupported
script operations or runtime gaps before initialization. Do not label a case
fully matched merely because conversion or the numerical solve succeeds.
Explicit InterPSS numerical overrides remain identifiable in the effective config.

`DynamicRunConfigTest`, registered in `CorePluginTestSuite`, covers conversion,
includes/cycles, unknown commands, same-time event ordering, explicit JSON
precedence, each screening branch, legacy assignment exclusion, preserved ZIP
source records, physical I/Z static response and relay ownership, and enabled/disabled source-record reconciliation.
Private large-case inputs, native document/runtime provenance and initialization
outcomes stay exclusively in `qa.largecase`.

The machine-readable schema is `src/main/resources/schema/dstab-run-config.schema.json`
in `ipss.plugin.core`; a small example is in
`ipss.test.plugin.core/testData/run-config/dynamic-run.example.json`.

## Per-load characteristic precedence and static conversion

The importer resolves explicit bus/load characteristics first, then zone
composites, then area characteristics. This applies independently to each active
source load ID, including loads sharing one bus. Stable scope ordering makes the
result independent of the DYR record order. A default records shadowed targets
as `SKIPPED_LOAD_PRIORITY`; it never installs a second parallel injection. Two
conflicting explicit characteristics or overlapping zone composites are reported
as invalid input, rather than silently overriding one owner.

Admission is model-specific. `CMLDZNU2` consults the configured initialization
criteria before detaching any source or building a feeder/motor. A rejected
assignment uses the static conversion; a lower-priority area characteristic is
not reactivated. Explicit legacy `CMLDBLU1` and algebraic `IEELBL`/`IEELAR` do not
consume those composite cutoffs, as established by independent discriminators.
Unsupported model names remain import coverage failures; this sequence does not
make an unsupported composite dialect an alias of a supported model.

New networks created by `PSSEMultiFileLoader.loadDStab(files)` use constant
current for active power and constant impedance for reactive power. Above the
existing low-voltage current-curve transition, with `r=V/Vinitial`, this gives
`P=Pinitial*r` and `Q=Qinitial*r*r`. Source RAW/RAWX ZIP fields are preserved as
input data; they do not select the post-conversion static voltage law. Dynamic
motor/electronic components retain their own equations. Only the unassigned or
screened static remainder uses this network policy.

The importer disables whole-load admittance stamping for I/Z; otherwise even an
I-labelled active load would behave as impedance. Caller-supplied networks keep
their declared conversion. Explicit JSON `activeStaticLoadModel` and
`reactiveStaticLoadModel` override imported defaults; each accepts `CONST_P`,
`CONST_I`, or `CONST_Z`. The IDV converter emits the default I/Z pair explicitly
for reproducible JSON replay. A legacy impedance-only reference experiment must
declare Z/Z explicitly instead of changing production defaults or its tolerances.

After initialization, `getLoadModelAssignmentReport()` provides every original
source ID's candidate, applied model, status and screening reasons, including
ordinary static and inactive sources. `getLoadModelScreeningReport()` retains the
numerical criteria and solved initial values for composite assignments. Both
reports describe the initialization decision; neither certifies trajectory
parity for every supported load model.
