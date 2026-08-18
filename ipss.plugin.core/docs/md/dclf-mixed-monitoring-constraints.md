# DCLF Mixed Monitoring Constraints

Module: `ipss.plugin.core` (JSON import / samples)  
Core API: `com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer`  
Definition / check / result: `com.interpss.monitor` (`definition`, `check`, `result`)

Canonical architecture: `ipss-core/ipss.core_EMF/docs/md/monitor-architecture.md`

---

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

For branch/interface studies without flowgates or nomograms, use the 3-arg
constructor (empty flowgates/nomograms):

```java
DclfMonitoringConfigRecord monitoringConfig = new DclfMonitoringConfigRecord(
        monitoredBranches,
        monitoredInterfaces,
        monitoringExceptions);
```

## JSON Import

Plugin helper `ContingencyFileUtil.importDclfMonitoringConfigFromJson(file)`
loads a full `DclfMonitoringConfigRecord` (branches, interfaces, flowgates,
nomograms, exceptions). Interface-only JSON:

```java
List<MonitoredInterfaceRecord> interfaces =
        ContingencyFileUtil.importMonitoredInterfaceRecordsFromJson(file);
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

Check implementations (in `com.interpss.monitor.check`):

| Check | Object type | Role |
|-------|-------------|------|
| `BranchMwLimitCheck` | `BRANCH` | Per-branch thermal MW vs rating |
| `MonitoredExpressionMwLimitCheck` | `INTERFACE` (default) | Weighted interface MW limit |
| `FlowgateEffectiveLimitCheck` | `FLOWGATE` | Expression + contingency-scoped selected limit |
| `NomogramMwBoundaryCheck` | `NOMOGRAM` | Two-axis linear boundary |

## Islanding Policy For OPEN Actions

When a contingency includes branch `OPEN` actions, use
`DclfContingencyConfig.setIslandingTreatment(...)` to choose how topology
islanding is handled:

```java
DclfContingencyConfig config = new DclfContingencyConfig();
config.setIslandingTreatment(DclfIslandingTreatment.ANCHORED_COMPENSATE_ONE_BUS);
```

Supported policies:

- `SKIP`: mark the islanded contingency by omission from DCLF violation results.
- `ANCHORED_COMPENSATE_ONE_BUS`: for a simple non-reference one-bus island,
  keep one island boundary line as an anchor, solve the nonsingular anchored
  topology, then compensate the final anchor-line removal with PTDFs. Larger
  islands fall through to topology replay. This is the default production
  policy.
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
    anchored-compensate based on config policy

if larger island or exact replay is required:
    topology replay on the changed network
```

The local topology screen is deliberately seeded only from opened branch
endpoints and overlays CLOSE actions in the same contingency, so it avoids a
full-network island scan for every contingency. If the bounded local screen
returns `unknown`, the sparse sensitivity path is still used and the existing
`[E - PTDF]` singularity check remains the algebraic guard. If the screen finds
a local island that cannot use anchored one-bus compensation, the replay path
expands that local hit into the complete island bus set before solving.

See [DCLF Contingency Analysis](https://github.com/interpss/core/blob/master/ipss.core_EMF/docs/algo_impl/dclf-contingency-analysis.md) for the detailed
multi-line outage decision diagram and islanding treatment notes.

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

## Samples and Tests

| Class / test | Location | Coverage |
|--------------|----------|----------|
| `MonInterfaceAclf5BusSample` | `ipss-core` `sample.mon_interface` | 5-bus; interface-only config via 3-arg ctor; `performMixedConstraintAnalysis` → INTERFACE violation |
| `IEEE14_MinitoredInterface_Sample` | `ipss.plugin.core` `org.interpss.mon_interface` | IEEE14; `performMonitoredConstraintAnalysis` (interface-only specialized path) |
| `IEEE14_SensHelper_SampleCase` | `ipss.plugin.core` `org.interpss` | Shared IEEE14 fixture for plugin sample + tests |
| `DclfMixedConstraintAnalysisTest` | `ipss.test.core` | Mixed run: INTERFACE + FLOWGATE in one pass |
| `DclfMonitoredConstraintTest` | `ipss.test.plugin.core` | Weighted interface CA; exceptions; JSON import of full monitoring config |
| `FlowgateDclfAnalyzerTest` | `ipss.test.plugin.core` | Flowgate-only analyzer path |

Best mixed-path entry points:

1. Core sample: `MonInterfaceAclf5BusSample` (mixed API, interface config)
2. Core test: `DclfMixedConstraintAnalysisTest#mixedConstraintAnalysisChecksInterfacesAndFlowgatesInOneRun`
3. Plugin JSON / exceptions: `DclfMonitoredConstraintTest`

```bash
# Core mixed sample
mvn -pl ipss.test.core exec:java -Dexec.mainClass=sample.mon_interface.MonInterfaceAclf5BusSample

# Core mixed test
mvn -pl ipss.test.core test -Dtest=DclfMixedConstraintAnalysisTest#mixedConstraintAnalysisChecksInterfacesAndFlowgatesInOneRun

# Plugin interface + JSON + flowgate suite
mvn -pl ipss.test.plugin.core test -Dtest=DclfMonitoredConstraintTest,FlowgateDclfAnalyzerTest
```

Detailed INTERFACE catalog: `ipss-core/ipss.core_EMF/docs/md/notes/Monitoring-INTERFACE.md`.

## Related Documentation

- [DCLF Contingency Analysis](https://github.com/interpss/core/blob/master/ipss.core_EMF/docs/algo_impl/dclf-contingency-analysis.md) — islanding / multi-outage solver policy
- [DCLF Monitored Interface Constraints](dclf-monitored-interface-constraints.md) — interface-only JSON and specialized CA path
- Core architecture: `ipss-core/ipss.core_EMF/docs/md/monitor-architecture.md`
- INTERFACE samples & tests: `ipss-core/ipss.core_EMF/docs/md/notes/Monitoring-INTERFACE.md`
