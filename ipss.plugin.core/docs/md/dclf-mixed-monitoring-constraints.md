# DCLF Mixed Monitoring Constraints

`ParallelDclfContingencyAnalyzer` supports mixed contingency-monitoring
constraints through `performMixedConstraintAnalysis(...)`.

Use this entry point when a study has more than one monitoring object type in
the same run:

- normal branch MW limits
- monitored interface / monitored expression MW limits
- flowgate effective limits
- nomogram MW boundary limits
- monitoring exceptions

The mixed API uses `DclfMonitoringConfigRecord` as the monitoring definition:

```java
DclfMonitoringConfigRecord monitoringConfig = new DclfMonitoringConfigRecord(
        monitoredBranches,
        monitoredInterfaces,
        flowgates,
        nomograms,
        monitoringExceptions);

ConcurrentLinkedQueue<DclfMwLimitViolationResult> violations =
        ParallelDclfContingencyAnalyzer.performMixedConstraintAnalysis(
                aclfNet,
                contingencyList,
                monitoringConfig,
                dclfConfig,
                parallelismLevel);
```

## Execution Model

The mixed API does not run separate contingency studies for each constraint
type. It performs one DCLF setup and computes each contingency post-flow vector
once. The same `preFlowMw[]` and `postFlowMw[]` arrays are then evaluated by
the configured limit checks:

```text
ParallelDclfContingencyAnalyzer
  -> calculate base DCLF once
  -> compile configured checks once
  -> for each contingency, compute postFlowMw[] once
  -> evaluate branch/interface/flowgate/nomogram checks
```

This keeps the expensive contingency solve shared while preserving separate
semantics for each monitoring type.

## Why Flowgates Are Not Just Interfaces

`FlowgateConstraintRecord` is structurally a monitored expression, but it has
additional semantics that should not be lost:

- object type is `FLOWGATE`
- limit comes from `FlowgateLimitSet`
- contingency applicability comes from `FlowgateContingencyRef`
- result metadata preserves fields such as `constraintType`, `nercId`, and
  `limitSelection`

For this reason, mixed analysis evaluates flowgates through
`FlowgateEffectiveLimitCheck`, not by flattening them into ordinary monitored
interfaces.

## Contingency IDs

For single open-branch contingencies, mixed monitoring uses the canonical
flowgate-style contingency ID:

```text
OPEN:<branchId>
```

Example:

```text
OPEN:Bus2->Bus3(1)
```

Use this ID in flowgate contingency references and monitoring exceptions when
the constraint should apply to that outage.

## Results

The mixed API returns `DclfMwLimitViolationResult`. Each result includes:

- `checkId`
- `contingencyId`
- `monitoredObjectType`
- `monitoredObjectId`
- `preValue`
- `postValue`
- `limitValue`
- optional metadata

Use `getMonitoredObjectType()` to dispatch results by type, for example
`BRANCH`, `INTERFACE`, `FLOWGATE`, or `NOMOGRAM`.
