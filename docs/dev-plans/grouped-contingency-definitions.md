# Dev Plan: Grouped Contingency Definitions in ipss.plugin.core

## Summary
Implement grouped contingency definitions inside `ipss.plugin.core` as the plugin-side canonical model for persisted and imported branch-outage contingencies. The implementation stays under `ipss.plugin.core/src/main/java/org/interpss/plugin/contingency/` and keeps the existing `BranchContingencyRecord` flat record as a legacy adapter type for current AUX, CON, and old JSON compatibility.

This plan intentionally does not introduce `com.interpss.desktop.*` packages or Desktop UI/service classes. Desktop callers can consume these plugin APIs later, but the work in this repository is limited to plugin-core models, JSON schema support, import adapters, and runtime mapping helpers.

## Target Packages and Classes

### New grouped model
Add the plugin-owned grouped model under `org.interpss.plugin.contingency.definition`:

- `ContingencyDefinition`: grouped contingency with `name`, `List<ContingencyAction> actions`, and optional `Map<String, String> metadata`.
- `ContingencyAction`: one outage action with `objectType`, `actionType`, `objectId`, optional `extUID`, and optional `Map<String, String> metadata`.
- `ContingencyObjectType`: enum with initial value `BRANCH`.
- `ContingencyActionType`: enum with initial value `OPEN`.

Keep `org.interpss.plugin.contingency.definition.BranchContingencyRecord` as the legacy flat branch-outage record. Mark new plugin APIs so grouped definitions are preferred, but do not remove or break existing callers that still use `BranchContingencyRecord`.

### JSON DTOs
Add grouped JSON DTOs under `org.interpss.plugin.contingency.definition.json`:

- `ContingencyDefinitionListJson` with root field `contingency_definitions` and optional `metadata`.
- `ContingencyDefinitionJson` with `name`, `actions`, and optional `metadata`.
- `ContingencyActionJson` with `object_type`, `action_type`, `object_id`, optional `extUID`, and optional `metadata`.

Keep the existing flat DTOs as legacy import/export adapters:

- `ContingencyListJson`
- `ContingencyJson`

## JSON Schema
Make grouped JSON the preferred plugin-core schema for new exports:

```json
{
  "contingency_definitions": [
    {
      "name": "AE11113",
      "actions": [
        {
          "object_type": "BRANCH",
          "action_type": "OPEN",
          "object_id": "Bus110001->Bus110041(1)",
          "extUID": "line_110001_110041_1_138kv"
        }
      ]
    }
  ]
}
```

Existing flat JSON with root `{ "contingencies": [...] }` remains supported for import and for legacy callers that explicitly request flat export.

## Conversion and Adapter APIs

### `org.interpss.plugin.contingency.util.ContingencyDefinitionAdapter`
Add a new adapter utility for pure model conversion:

- `fromBranchRecords(List<BranchContingencyRecord>)`: group records with the same nonblank `name` into one `ContingencyDefinition`.
- Blank or missing `BranchContingencyRecord.name` values become one single-action definition per branch record.
- `toBranchRecords(List<ContingencyDefinition>)`: flatten supported grouped definitions back to `BranchContingencyRecord` for legacy APIs.
- `flattenBranchIds(List<ContingencyDefinition>)`: return branch ids from supported branch-open actions for Fast N-2 and other candidate-list consumers.
- Reject or report unsupported `objectType` / `actionType` values consistently with existing importer behavior.

### `org.interpss.plugin.contingency.util.ContingencyFileUtil`
Extend the existing file utility without removing legacy methods:

- Add `importContingencyDefinitionsFromJson(File)`.
- Add `exportContingencyDefinitionsToJson(File, List<ContingencyDefinition>)`.
- Update `importContingenciesFromJson(File)` to remain a legacy adapter by loading grouped files through `importContingencyDefinitionsFromJson(File)` and flattening with `ContingencyDefinitionAdapter.toBranchRecords(...)`.
- Keep `exportContingenciesToJson(File, List<BranchContingencyRecord>)` for legacy flat export only.
- Detect grouped vs flat JSON by root fields: prefer `contingency_definitions`; fallback to legacy `contingencies`.

## Runtime Mapping APIs

### DC contingency mapping
Extend or add helper APIs under `org.interpss.plugin.contingency.util`:

- Preferred helper: `DclfMultiOutageContingencyHelper.createDclfMultiOutageContListFromDefinitions(List<ContingencyDefinition>)`.
- Preserve `DclfMultiOutageContingencyHelper.createDclfMultiOutageContList(List<BranchContingencyRecord>)` as a legacy adapter.
- One multi-action `ContingencyDefinition` maps to one `com.interpss.core.contingency.dclf.DclfMultiOutage`.
- A single-action definition may map to a one-branch `DclfMultiOutage`; keep `DclfContingencyHelper` available for callers that still require `DclfBranchOutage`.

### AC contingency mapping
Add plugin-core mapping from grouped definitions to AC runtime outages:

- Add `org.interpss.plugin.contingency.util.AclfContingencyDefinitionHelper`.
- Map one supported `ContingencyDefinition` to one `com.interpss.core.contingency.aclf.AclfMultiOutage`.
- Use `AclfContingencyObjectFactory.createAclfBranchOutage(...)` for branch-open actions.
- Preserve existing `org.interpss.plugin.contingency.con_fmt.mapper.ConToIpssMapper` behavior and add grouped-definition output separately instead of replacing its current AC runtime mapping.

### LODF and Fast N-2 integration
The plugin-core LODF and Fast N-2 APIs currently consume core runtime objects or branch-id lists rather than Desktop service classes:

- Use `ContingencyDefinitionAdapter.flattenBranchIds(...)` for `com.interpss.core.algo.dclf.fastn2.FastN2CandidateRequest` outage candidates.
- If a plugin-core MLODF request object is added or exposed in this checkout, add a grouped-definition adapter there; otherwise keep this slice limited to branch-id flattening and `DclfMultiOutage` creation.
- Do not add adapters for Desktop-only LODF service request objects in this repo plan.

## AUX and CON Import Paths

### AUX
Update `org.interpss.plugin.contingency.aux_fmt.AuxContingencyConverter`:

- Add `importContingencyDefinitions(...)` returning `List<ContingencyDefinition>`.
- Add grouped JSON export as the default converter output via `ContingencyFileUtil.exportContingencyDefinitionsToJson(...)`.
- Keep `importContingencyRecords(...)` as a legacy adapter that flattens grouped definitions back to `BranchContingencyRecord` when needed.
- Preserve `AuxToBranchContingencyMapper` initially as the low-level branch action mapper, then group with `ContingencyDefinitionAdapter.fromBranchRecords(...)`.

### CON
Add grouped-definition conversion for `org.interpss.plugin.contingency.con_fmt` without removing AC runtime mapping:

- Add a mapper method or helper that converts supported `ConCase` branch-open actions into `ContingencyDefinition`.
- Keep `org.interpss.plugin.contingency.con_fmt.mapper.ConToIpssMapper` mapping to `AclfMultiOutage` for existing AC contingency use.
- Unsupported CON actions should be rejected or reported with the same policy as existing CON mapping.

## Implementation Slices

### Slice 1: Grouped model, JSON, and legacy adapters
- Add `ContingencyDefinition`, `ContingencyAction`, `ContingencyObjectType`, and `ContingencyActionType`.
- Add grouped JSON DTOs.
- Add `ContingencyDefinitionAdapter`.
- Extend `ContingencyFileUtil` with grouped import/export and legacy flat fallback.
- Tests:
  - New grouped JSON imports as one definition with multiple actions.
  - Old flat JSON with same-name records imports as one grouped definition.
  - Blank legacy names become one single-action definition per branch.
  - Grouped export writes `contingency_definitions`, not `contingencies`.

### Slice 2: Runtime helper mappings
- Add grouped-definition DC mapping through `DclfMultiOutageContingencyHelper`.
- Add grouped-definition AC mapping through `AclfContingencyDefinitionHelper`.
- Add branch-id flattening coverage for Fast N-2 candidate input.
- Tests:
  - One multi-action definition maps to one `DclfMultiOutage`.
  - One multi-action definition maps to one `AclfMultiOutage`.
  - Flattening returns all supported branch ids in deterministic order.

### Slice 3: AUX/CON importer integration
- Update `AuxContingencyConverter` to expose grouped import and default grouped JSON export.
- Add CON grouped-definition conversion for supported branch-open cases.
- Keep flat-record methods as legacy adapters.
- Tests:
  - AUX import converts same-name CTG elements into one grouped definition.
  - AUX default export writes grouped JSON.
  - CON import converts supported branch-open cases into grouped definitions.
  - Existing AUX/CON runtime mapping tests still pass.

## Validation Commands
Run focused tests after each slice before committing that slice:

```bash
mvn -q -pl ipss.plugin.core "-Dtest=ContingencyDefinitionAdapterTest,ContingencyFileUtilTest" test
mvn -q -pl ipss.plugin.core "-Dtest=DclfMultiOutageContingencyHelperTest,AclfContingencyDefinitionHelperTest" test
mvn -q -pl ipss.plugin.core "-Dtest=AuxContingencyConverterTest" test
```

Also run the existing AUX focused test after importer changes:

```bash
mvn -q -pl ipss.plugin.core "-Dtest=AuxContingencyConverterTest" test
```

If tests live in `ipss.test.plugin.core`, run them from that module instead of `ipss.plugin.core`:

```bash
mvn -q -pl ipss.test.plugin.core "-Dtest=ConToIpssMapper_Test" test
```

## Assumptions and Non-goals
- This repository owns the plugin-core implementation only; Desktop UI strings, project resource previews, and Desktop CLI updates belong in `ipss-desktop`.
- `BranchContingencyRecord` remains available as a legacy/plugin adapter type.
- `DclfMultiOutage` and `AclfMultiOutage` remain runtime execution objects, not persisted JSON models.
- V1 supports `BRANCH` + `OPEN`; broader object/action support can be added later without changing the grouped parent-child structure.
