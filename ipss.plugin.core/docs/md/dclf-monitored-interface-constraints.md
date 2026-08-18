# DCLF Monitored Interface Constraints

Module: `ipss.plugin.core` (JSON import/export, samples)  
Core API: `com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer`  
Facade: `com.interpss.core.algo.dclf.DclfContingencyLimitStudy`  
Definition: `com.interpss.monitor.definition` (`MonitoredInterfaceRecord`, `MonitoredBranchRecord`)  
Result: `com.interpss.monitor.result.DclfMonitoredConstraintResult`

Canonical architecture: `ipss-core/ipss.core_EMF/docs/md/monitor-architecture.md`  
INTERFACE samples & tests: `ipss-core/ipss.core_EMF/docs/md/notes/Monitoring-INTERFACE.md`

---

## Overview

The DCLF contingency analyzer supports monitored linear transmission constraints
for branch groups and interfaces (weighted MW expressions).

The constraint is evaluated as:

```text
C1 * postFlowMW(branch1) + C2 * postFlowMW(branch2) + ...  vs  limitMW
```

This matches the common market-modeling form used for branch-group and path
limits: a weighted linear expression over one or more monitored branches with a
right-hand-side MW limit.

This feature is additive. Existing branch overload monitoring still uses:

```java
ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(...)
```

Use the monitored interface constraint API when the monitored object is not a
single branch rating, but a weighted group limit:

```java
// Config-based convenience wrapper
ParallelDclfContingencyAnalyzer.executeMonitoredConstraintAnalysis(
        net, contingencies, interfaces, config, parallelism);

// Explicit overload / threshold form (also used by samples and tests)
ParallelDclfContingencyAnalyzer.performMonitoredConstraintAnalysis(
        net, contingencies, interfaces, overloadThreshold, dclfInclLoss, parallelism);

// Facade that compiles monitoring exceptions first
DclfContingencyLimitStudy.performMonitoredExpressionAnalysis(...);
```

For studies that also include branch thermal limits, flowgates, and/or
nomograms in one pass, use the mixed API instead — see
[DCLF Mixed Monitoring Constraints](dclf-mixed-monitoring-constraints.md).

---

## When to Use This Feature

Use monitored interface constraints for:

- Branch group limits, where multiple circuit flows share one MW limit.
- Directional path limits, where coefficients encode the path direction.
- Offline-derived shift-factor or outage-distribution-factor constraints.

Do not use this feature for:

- Ordinary single-branch thermal overload checks. Use monitored branch
  contingency analysis (`executeContingencyAnalysis`) or mixed
  `BranchMwLimitCheck`.
- Flowgates with contingency-bound tiered limits — use `FlowgateDclfAnalyzer`
  or mixed analysis (`FlowgateEffectiveLimitCheck`).
- Two-axis nomograms — use mixed analysis (`NomogramMwBoundaryCheck`) or define
  axes as interfaces and attach `NomogramRecord` in `DclfMonitoringConfigRecord`.
- Voltage, transient-stability, or reactive margin checks. This DCLF feature
  only evaluates linear MW flow expressions.
- Constraints that require nonlinear logic, RAS/SPS event simulation, or
  dynamic limit recalculation during the scan.

---

## Required Interface Definition

Each monitored interface requires:

| Field | Required | Meaning |
|---|---:|---|
| `id` | yes | Stable name for the branch group or interface. |
| `limit_mw` | yes | RHS limit in MW. `rating_mw` is accepted as a compatibility alias on JSON import. |
| `branches` | yes | One or more monitored branch terms. |
| branch identity | yes | Either `branch_id`, or `from_bus` + `to_bus` + `circuit`. |
| `coefficient` | no | Multiplier for the branch post-contingency MW flow. Defaults to `1.0`. |

Branch IDs must match InterPSS branch IDs exactly:

```text
fromBus->toBus(circuit)
```

Example:

```text
Bus2->Bus4(1)
```

If a branch is defined with `from_bus`, `to_bus`, and `circuit`, the same
branch ID is built internally.

Object type on violation results from the specialized path is always
`INTERFACE` (`MonitoringObjectType.INTERFACE`). The mixed-path check
`MonitoredExpressionMwLimitCheck` uses the same default.

---

## JSON Format

The top-level JSON key for interface-only files is `monitored_interfaces`.

```json
{
  "monitored_interfaces": [
    {
      "id": "PATH26_N-S",
      "limit_mw": 1400.0,
      "branches": [
        {
          "branch_id": "Bus2->Bus4(1)",
          "coefficient": 0.75
        },
        {
          "from_bus": "Bus3",
          "to_bus": "Bus4",
          "circuit": "1",
          "coefficient": -0.25
        }
      ]
    }
  ],
  "metadata": {
    "description": "User-defined monitored interface list for DCLF analysis"
  }
}
```

Import / export:

```java
List<MonitoredInterfaceRecord> interfaces =
    ContingencyFileUtil.importMonitoredInterfaceRecordsFromJson(file);

ContingencyFileUtil.exportMonitoredInterfaceRecordsToJson(file, interfaces);
```

Related JSON contracts (plugin `ContingencyFileUtil`):

| File purpose | Root key / API |
|---|---|
| Contingencies | `contingencies` |
| Monitored branches | `monitored_branches` |
| Monitored interfaces | `monitored_interfaces` / `importMonitoredInterfaceRecordsFromJson` |
| Full mixed monitoring config | branches + interfaces + flowgates + nomograms + exceptions / `importDclfMonitoringConfigFromJson` |

---

## Java Usage

```java
AclfNetwork net = ...;
List<? extends BaseContingency<DclfMonitoringBranch>> contingencies = ...;

File interfaceFile = new File("monitored-interfaces.json");
List<MonitoredInterfaceRecord> interfaces =
    ContingencyFileUtil.importMonitoredInterfaceRecordsFromJson(interfaceFile);

DclfContingencyConfig config = new DclfContingencyConfig();
config.setDclfInclLoss(true);
config.setOverloadThreshold(100.0);

ConcurrentLinkedQueue<DclfMonitoredConstraintResult> results =
    ParallelDclfContingencyAnalyzer.executeMonitoredConstraintAnalysis(
        net,
        contingencies,
        interfaces,
        config,
        8);

for (DclfMonitoredConstraintResult result : results) {
    System.out.printf(
        "%s under %s: pre=%.2f MW, shift=%.2f MW, post=%.2f MW, limit=%.2f MW, loading=%.2f%%%n",
        result.getConstraintId(),
        result.getContingencyId(),
        result.getPreValueMW(),
        result.getShiftedValueMW(),
        result.getPostValueMW(),
        result.getLimitMW(),
        result.getLoadingPercent());
}
```

With monitoring exceptions (INCLUDE / EXCLUDE / DEFAULT):

```java
ConcurrentLinkedQueue<DclfMonitoredConstraintResult> results =
    DclfContingencyLimitStudy.performMonitoredExpressionAnalysis(
        net,
        contingencies,
        interfaces,
        monitoringExceptions,
        100.0,
        false,
        1,
        solutionMethod,
        kluEndpointRhsBatchSize);
```

---

## Programmatic Definition

You can build the same interface in Java without JSON:

```java
MonitoredInterfaceRecord path = new MonitoredInterfaceRecord("PATH26_N-S", 1400.0);
path.addBranch(new MonitoredBranchRecord("Bus2->Bus4(1)", 0.75));
path.addBranch(new MonitoredBranchRecord("Bus3->Bus4(1)", -0.25));
```

The second constructor argument on `MonitoredInterfaceRecord` is the MW limit.
The second constructor argument on `MonitoredBranchRecord(branchId, coefficient)`
is the coefficient for that branch term.

---

## Evaluation Semantics

For each contingency:

1. The analyzer computes DCLF base-case flows.
2. The contingency is applied using the configured DCLF solution method.
3. Each interface term uses the branch post-contingency MW flow:

```text
postFlowMW = preFlowMW + shiftedFlowMW
```

4. The interface value is calculated:

```text
interfaceMW = sum(coefficient_i * postFlowMW_i)
```

5. A result is returned when:

```text
100 * interfaceMW / limitMW >= overloadThreshold
```

With the default `overloadThreshold` of `100.0`, a result means:

```text
interfaceMW >= limitMW
```

The comparison is directional. If the limit applies in the opposite direction,
use negative coefficients or define a second interface with reversed signs.

`DclfMonitoredConstraintResult` stores pre, shifted, and post MW
(`post = pre + shifted`), the limit, and the EMF contingency object. Check id
is `MONITORED_EXPRESSION_MW`.

---

## Practical Requirements

Before running the analysis, verify:

1. The network has been loaded with branch IDs that match the interface definition.
2. Each interface branch is active in the DCLF model.
3. Coefficients are in the same orientation as the branch post-flow sign convention.
4. `limit_mw` is positive for normal limit-percent reporting.
5. The interface expression and limit use MW, not per-unit.
6. The contingency list has valid DCLF outage objects with current outage pre-flows.

If a configured branch is not active or cannot be resolved in the DCLF monitor
set, the analyzer logs a warning and skips that branch term. If all terms in an
interface are skipped, that interface is skipped.

---

## Samples and Tests

| Class / test | Location | Coverage |
|--------------|----------|----------|
| `IEEE14_MinitoredInterface_Sample` | `ipss.plugin.core` `org.interpss.mon_interface` | IEEE14; OPEN `Bus2->Bus3(1)`; weighted `IEEE14_BG`; `performMonitoredConstraintAnalysis` |
| `IEEE14_SensHelper_SampleCase` | `ipss.plugin.core` `org.interpss` | Shared IEEE14 fixture |
| `MonInterfaceAclf5BusSample` | `ipss-core` `sample.mon_interface` | 5-bus via **mixed** API with interface-only config (related, not this specialized path) |
| `DclfMonitoredConstraintTest` | `ipss.test.plugin.core` | Weighted post-flow CA; study facade parity; INCLUDE/EXCLUDE/DEFAULT; JSON import |

```bash
# Plugin interface suite
mvn -pl ipss.test.plugin.core test -Dtest=DclfMonitoredConstraintTest

# Matching sample scenario
# IDE: IEEE14_MinitoredInterface_Sample (cwd ipss.plugin.core)
```

---

## Current Scope

Supported:

- Open-branch outage fast path.
- KLU endpoint RHS batching when enabled.
- Generic core contingency fallback for non-fast DCLF contingency shapes.
- JSON import/export for monitored interface definitions.
- Programmatic Java definition.
- Monitoring exceptions via `DclfContingencyLimitStudy` /
  `performMonitoredConstraintAnalysis(..., monitoringExceptions, ...)`.

Not yet included:

- A DataFrame adapter for `DclfMonitoredConstraintResult`.
- PSS/E `.mon` interface coefficient parsing.
- Dynamic, time-varying RHS limits.
- AC power-flow validation of interface violations.

---

## Related Documentation

- [DCLF Mixed Monitoring Constraints](dclf-mixed-monitoring-constraints.md) — branches + interfaces + flowgates + nomograms in one run
- [DCLF Contingency Analysis](https://github.com/interpss/core/blob/master/ipss.core_EMF/docs/algo_impl/dclf-contingency-analysis.md) — islanding / multi-outage solver policy
- Core architecture: `ipss-core/ipss.core_EMF/docs/md/monitor-architecture.md`
- INTERFACE samples & tests: `ipss-core/ipss.core_EMF/docs/md/notes/Monitoring-INTERFACE.md`
