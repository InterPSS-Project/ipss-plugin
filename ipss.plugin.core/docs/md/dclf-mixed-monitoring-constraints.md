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

## Islanding Policy For OPEN Actions

When a contingency includes branch `OPEN` actions, use
`DclfContingencyConfig.setIslandingTreatment(...)` to choose how topology
islanding is handled:

```java
DclfContingencyConfig config = new DclfContingencyConfig();
config.setIslandingTreatment(DclfIslandingTreatment.BOUNDARY_COMPENSATE_ONE_BUS);
```

Supported policies:

- `SKIP`: mark the islanded contingency by omission from DCLF violation results.
- `BOUNDARY_COMPENSATE_ONE_BUS`: for a one-bus island, use pre-contingency
  boundary flows and PTDFs on the reference-connected network to approximate
  the post-contingency monitor flows. Larger islands fall through to topology
  replay. This is the default production policy because the common islanding
  impact is local and avoids full 70k-bus post-topology DCLF solves.
- `FULL_DCLF_REPLAY`: apply the topology change and solve DCLF on the changed
  topology. Use this when exact replay is explicitly required; it is much
  slower for large systems because it refactors/solves the changed network for
  each replay contingency.

The intended runtime decision tree is:

```text
if no OPEN actions:
    normal path

if OPEN actions:
    local topology screen from the opened branch endpoints

if topology says no island:
    normal mixed-contingency path

if simple one-bus island:
    boundary-compensate based on config policy

if larger island or user chooses full DCLF fallback:
    topology replay on the changed network
```

The local topology screen is deliberately seeded only from opened branch
endpoints and overlays CLOSE actions in the same contingency, so it avoids a
full-network island scan for every contingency. If the local screen cannot
prove a nearby island, the sparse sensitivity path is used; if it finds a local
island and the policy is `FULL_DCLF_REPLAY`, the replay path expands that local
hit into the complete island bus set before solving.

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
