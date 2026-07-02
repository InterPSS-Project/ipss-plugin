# PowerWorld AUX to InterPSS Contingency JSON Converter Plan

## Goal

Add a converter that reads PowerWorld AUX contingency definitions and emits the
InterPSS branch contingency JSON format consumed by `ContingencyFileUtil`.

The key behavior is grouping: every supported AUX `CTGElement` with the same
contingency id/name must be emitted as a separate JSON contingency record with
the same `name`. That preserves the group at the current InterPSS JSON layer,
where grouped branch outages are represented as repeated flat records with a
shared contingency name.

## Current InterPSS JSON Contract

The existing JSON structure is:

```json
{
  "contingencies": [
    {
      "name": "CTG_1",
      "element_type": "Branch",
      "action_type": "OPEN",
      "from_bus": "Bus1001",
      "to_bus": "Bus1064",
      "circuit": "1",
      "from_bus_area": "1",
      "to_bus_area": "1",
      "base_kv": 115.0,
      "pre_contingency_flow_mw": 0.0
    }
  ],
  "metadata": {
    "total_count": 1,
    "created_date": "2026-07-01T00:00:00",
    "description": "PowerWorld AUX contingency conversion"
  }
}
```

Relevant existing classes:

- `org.interpss.plugin.contingency.definition.BranchContingencyRecord`
- `org.interpss.plugin.contingency.definition.json.ContingencyJson`
- `org.interpss.plugin.contingency.definition.json.ContingencyListJson`
- `org.interpss.plugin.contingency.util.ContingencyFileUtil`
- `org.interpss.plugin.contingency.util.DclfContingencyHelper`

## Scope

Implement a conservative first version focused on branch contingency actions:

- Parse PowerWorld AUX `Contingency` records to discover contingency ids/names.
- Parse PowerWorld AUX `CTGElement` records.
- Convert branch open and branch close actions to InterPSS JSON.
- Group by the AUX contingency id/name by assigning the same JSON `name` to
  every converted branch action in that group.
- Report unsupported or unresolved elements without silently converting them.

Out of scope for the first version:

- Generator, load, shunt, bus, interface, substation, and remedial actions.
- Network validation or branch lookup during pure file conversion.
- Monitoring JSON generation.
- Changes to DCLF runtime semantics beyond preserving same-name grouped records.

## Proposed Package Layout

Add a new AUX converter package next to the existing `.con` parser/mapper:

```text
ipss.plugin.core/src/main/java/org/interpss/plugin/contingency/aux_fmt/
  AuxContingencyConverter.java
  AuxConversionOptions.java
  AuxConversionReport.java
  AuxUnsupportedElement.java
  bean/
    AuxContingency.java
    AuxCtgElement.java
    AuxBranchAction.java
  parser/
    AuxContingencyParser.java
  mapper/
    AuxToBranchContingencyMapper.java
```

Rationale:

- Keeps PowerWorld AUX concerns separate from PSS/E `.con` parsing.
- Mirrors the existing `con_fmt` structure without coupling to `.con` syntax.
- Lets parser tests and mapper tests stay independent.

## Parsing Strategy

PowerWorld AUX files are table/block oriented. The parser should support the
common forms used for contingency definitions:

```text
DATA (Contingency, [fields...])
{
  ...
}

DATA (CTGElement, [fields...])
{
  ...
}
```

Implementation details:

1. Tokenize the file into AUX data blocks by detecting `DATA (...)` headers and
   collecting rows until the matching closing brace.
2. Parse the field list from each block header and map row values by field name.
3. Preserve quoted strings, escaped quote characters, numeric tokens, and empty
   tokens.
4. Treat field names case-insensitively.
5. Retain input line numbers for diagnostics.

Useful parser behavior:

- Ignore comments and blank lines outside values.
- Emit a warning for unknown `DATA` blocks, not an error.
- Fail fast on malformed `Contingency` or `CTGElement` blocks only when required
  fields are missing.

## Required AUX Fields

Because AUX exports can vary, field matching should use aliases.

For contingency identity:

- Preferred id/name fields: `Name`, `Contingency`, `CTGName`, `CTGLabel`,
  `ContingencyName`.
- If both an id and display label exist, use the id as the JSON `name` and store
  the label in diagnostics only.

For `CTGElement` grouping:

- Preferred parent fields: `Contingency`, `CTGName`, `ContingencyName`, `Name`.
- Every element must resolve to a parent contingency id/name.

For branch elements:

- Action/type fields: `Action`, `CTGAction`, `ElementType`, `ObjectType`,
  `DeviceType`.
- From bus fields: `BusNum`, `BusNumFrom`, `FromBus`, `FromBusNum`.
- To bus fields: `BusNumTo`, `ToBus`, `ToBusNum`.
- Circuit fields: `Circuit`, `CircuitID`, `Ckt`, `ID`.

The exact alias list should be finalized against the provided sample AUX file
once it is available in the workspace.

## Mapping Rules

Supported mappings:

| AUX meaning | InterPSS JSON |
|---|---|
| Open/trip/disconnect branch or line | `element_type = "Branch"`, `action_type = "OPEN"` |
| Close/connect branch or line | `element_type = "Branch"`, `action_type = "CLOSE"` |

Field mapping:

- `name`: parent contingency id/name, identical for all elements in the group.
- `element_type`: `Branch`.
- `action_type`: normalized `OPEN` or `CLOSE`.
- `from_bus`: bus id string.
- `to_bus`: bus id string.
- `circuit`: circuit id, defaulting to `1` only if the AUX row omits it.
- `from_bus_area`, `to_bus_area`: omit or leave `null` unless present in AUX.
- `base_kv`: use AUX value when present; otherwise `0.0`.
- `pre_contingency_flow_mw`: use AUX value when present; otherwise `0.0`.

Bus id normalization should be configurable:

- Default: prefix numeric bus numbers with `Bus`, matching existing PSS/E-based
  JSON examples such as `Bus1001`.
- Option: preserve source bus ids exactly for users whose InterPSS network ids
  do not use the `Bus` prefix.

## Grouping Semantics

Example AUX concept:

```text
Contingency: CTG_A
  CTGElement: open branch 1001-1064-1
  CTGElement: open branch 1002-1007-1
```

Expected InterPSS JSON:

```json
{
  "contingencies": [
    {
      "name": "CTG_A",
      "element_type": "Branch",
      "action_type": "OPEN",
      "from_bus": "Bus1001",
      "to_bus": "Bus1064",
      "circuit": "1"
    },
    {
      "name": "CTG_A",
      "element_type": "Branch",
      "action_type": "OPEN",
      "from_bus": "Bus1002",
      "to_bus": "Bus1007",
      "circuit": "1"
    }
  ]
}
```

This plan does not introduce a nested JSON schema. It preserves grouping using
the existing repeated-name convention so existing import/export utilities keep
working.

## Public API

Add a network-aware file-to-file API. The converter should always receive the
imported `AclfNetwork` so PowerWorld `Object` references can be resolved by
branch id, branch name, branch `extUID`, or RAW label tags before emitting JSON:

```java
AclfNetwork net = IpssAdapter.importAclfNet(rawFile)
    .setFormat(FileFormat.PSSE)
    .setPsseVersion(PsseVersion.PSSE_36)
    .load()
    .getImportedObj();

AuxConversionReport report = new AuxContingencyConverter(net)
    .convert(inputAuxFile, outputJsonFile, AuxConversionOptions.defaultOptions());
```

Add an in-memory API to create the InterPSS contingency record list directly
from AUX without writing JSON:

```java
AuxConversionReport report = new AuxConversionReport();
List<BranchContingencyRecord> records = new AuxContingencyConverter(net)
    .importContingencyRecords(inputAuxFile, AuxConversionOptions.defaultOptions(), report);
```

For DCLF analysis callers, add a convenience API to create InterPSS
`DclfMultiOutage` objects directly from AUX:

```java
ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
dclfAlgo.calculateDclf();

List<DclfMultiOutage> outages = new AuxContingencyConverter(net)
    .importDclfMultiOutages(inputAuxFile, dclfAlgo, AuxConversionOptions.defaultOptions());
```

`AuxConversionReport` should include:

- number of contingencies discovered
- number of CTG elements discovered
- number of branch records emitted
- unsupported element count
- skipped element count
- warnings with line numbers

## Command-Line Entry Point

Add a small CLI class under `src/main/java`:

```text
org.interpss.plugin.contingency.aux_fmt.AuxToContingencyJsonMain
```

Suggested usage:

```bash
mvn -pl ipss.plugin.core exec:java \
  -Dexec.mainClass=org.interpss.plugin.contingency.aux_fmt.AuxToContingencyJsonMain \
  -Dexec.args="--input C:/path/input.aux --output C:/path/contingencies.json --network C:/path/case.raw --psse-version auto --bus-id-mode prefix-bus"
```

CLI flags:

- `--input <file>`: required AUX file path.
- `--output <file>`: required InterPSS contingency JSON path.
- `--network <file>`: required PSS/E RAW file used to resolve branch objects.
- `--psse-version auto|29|30|31|32|33|34|35|36`: default `auto`.
- `--bus-id-mode prefix-bus|preserve`: default `prefix-bus`.
- `--unsupported warn|fail`: default `warn`.
- `--default-circuit <id>`: default `1`.

## Tests

Add focused JUnit 5 tests under:

```text
ipss.plugin.core/src/test/java/org/interpss/plugin/contingency/aux_fmt/
```

Test cases:

1. Parses `Contingency` and `CTGElement` blocks with quoted string values.
2. Converts one branch open action.
3. Converts branch close action.
4. Groups multiple CTG elements with the same contingency id by emitting the
   same JSON `name`.
5. Preserves source order: contingencies and elements are emitted in AUX order.
6. Defaults missing circuit id to `1`.
7. Supports `prefix-bus` and `preserve` bus id modes.
8. Warns or fails for unsupported element types based on `--unsupported`.
9. Writes JSON that round-trips through
   `ContingencyFileUtil.importContingenciesFromJson`.

Add one sample fixture:

```text
ipss.plugin.core/src/test/resources/contingency/aux/example_aux_ctg_definition.aux
```

Once the real sample file is available, copy a small non-sensitive subset into
test resources and keep the original file unchanged.

## Validation Commands

Run the focused tests:

```bash
mvn -pl ipss.plugin.core -Dtest=AuxContingencyParserTest,AuxToBranchContingencyMapperTest test
```

Run the core module tests if the focused tests pass:

```bash
mvn -pl ipss.plugin.core test
```

If Maven reports missing local dependencies, run the repository bootstrap first:

```bash
sh maven.sh
```

## Implementation Steps

1. Add parser beans and `AuxContingencyParser`.
2. Add mapper and conversion options.
3. Add converter facade that writes through `ContingencyFileUtil`.
4. Add CLI entry point.
5. Add parser and mapper fixtures/tests.
6. Validate JSON round-trip through existing InterPSS import utility.
7. Run focused Maven tests.
8. Test against the provided AUX file and record emitted/unsupported counts.

## Open Questions

1. Confirm the exact field names in the provided
   `example_aux_ctg_definition.aux`; the referenced Downloads path was not
   present when this plan was written.
2. Confirm whether output bus ids should default to `Bus123` or preserve raw AUX
   numeric ids for the target InterPSS network.
3. Confirm whether unsupported non-branch CTG elements should block conversion
   in production or only produce a report.
4. Confirm whether same-name grouped branch records are sufficient for every
   downstream DCLF path that will consume this JSON, because
   `DclfContingencyHelper` currently creates one `DclfBranchOutage` per flat
   record.
