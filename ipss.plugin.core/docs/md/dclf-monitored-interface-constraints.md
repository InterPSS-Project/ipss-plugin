# DCLF Monitored Interface Constraints

Module: `ipss.plugin.core`  
Primary API: `org.interpss.plugin.contingency.ParallelDclfContingencyAnalyzer`  
Definition package: `org.interpss.plugin.contingency.definition`  
Result package: `org.interpss.plugin.contingency.result`

---

## Overview

The DCLF contingency analyzer supports monitored linear transmission constraints for branch groups, interfaces, and nomograms.

The constraint is evaluated as:

```text
C1 * postFlowMW(branch1) + C2 * postFlowMW(branch2) + ... <= limitMW
```

This matches the common market-modeling form used for nomogram and branch-group limits: a weighted linear expression over two or more monitored flowgates with a right-hand-side limit.

This feature is additive. Existing branch overload monitoring still uses:

```java
ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(...)
```

Use the monitored interface constraint API when the monitored object is not a single branch rating, but a weighted group limit:

```java
ParallelDclfContingencyAnalyzer.executeMonitoredConstraintAnalysis(...)
```

---

## When to Use This Feature

Use monitored interface constraints for:

- Branch group limits, where multiple circuit flows share one MW limit.
- Nomograms, where the monitored value is a linear expression of branch flows.
- Directional path limits, where coefficients encode the path direction.
- Offline-derived shift-factor or outage-distribution-factor constraints.

Do not use this feature for:

- Ordinary single-branch thermal overload checks. Use monitored branch contingency analysis.
- Voltage, transient-stability, or reactive margin checks. This DCLF feature only evaluates linear MW flow expressions.
- Constraints that require nonlinear logic, RAS/SPS event simulation, or dynamic limit recalculation during the scan.

---

## Required Interface Definition

Each monitored interface requires:

| Field | Required | Meaning |
|---|---:|---|
| `id` | yes | Stable name for the branch group, interface, or nomogram. |
| `limit_mw` | yes | RHS limit in MW. `rating_mw` is accepted as a compatibility alias. |
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

If a branch is defined with `from_bus`, `to_bus`, and `circuit`, the same branch ID is built internally.

---

## JSON Format

The top-level JSON key is `monitored_interfaces`.

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

Import it with:

```java
List<MonitoredInterfaceRecord> interfaces =
    ContingencyFileUtil.importMonitoredInterfaceRecordsFromJson(file);
```

Export it with:

```java
ContingencyFileUtil.exportMonitoredInterfaceRecordsToJson(file, interfaces);
```

This JSON contract is separate from the existing files:

| File purpose | Root key |
|---|---|
| Contingencies | `contingencies` |
| Monitored branches | `monitored_branches` |
| Monitored interfaces / nomograms | `monitored_interfaces` |

---

## Java Usage

```java
AclfNetwork net = ...;
List<DclfBranchOutage> contingencies = ...;

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

---

## Programmatic Definition

You can build the same interface in Java without JSON:

```java
MonitoredInterfaceRecord path = new MonitoredInterfaceRecord("PATH26_N-S", 1400.0);
path.addBranch(new MonitoredBranchRecord("Bus2->Bus4(1)", 0.75));
path.addBranch(new MonitoredBranchRecord("Bus3->Bus4(1)", -0.25));
```

The second constructor argument on `MonitoredInterfaceRecord` is the MW limit. The second constructor argument on `MonitoredBranchRecord` is the coefficient for that branch term.

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

The comparison is directional. If the limit applies in the opposite direction, use negative coefficients or define a second interface with reversed signs.

---

## Practical Requirements

Before running the analysis, verify:

1. The network has been loaded with branch IDs that match the interface definition.
2. Each interface branch is active in the DCLF model.
3. Coefficients are in the same orientation as the branch post-flow sign convention.
4. `limit_mw` is positive for normal limit-percent reporting.
5. The interface expression and limit use MW, not per-unit.
6. The contingency list has valid DCLF outage objects with current outage pre-flows.

If a configured branch is not active or cannot be resolved in the DCLF monitor set, the analyzer logs a warning and skips that branch term. If all terms in an interface are skipped, that interface is skipped.

---

## Current Scope

Supported:

- Open-branch outage fast path.
- KLU endpoint RHS batching when enabled.
- Generic core contingency fallback for non-fast DCLF contingency shapes.
- JSON import/export for monitored interface definitions.
- Programmatic Java definition.

Not yet included:

- A DataFrame adapter for `DclfMonitoredConstraintResult`.
- PSS/E `.mon` interface coefficient parsing.
- Dynamic, time-varying RHS limits.
- AC power-flow validation of interface violations.

Test coverage:

```bash
mvn -pl ipss.test.plugin.core -am test \
  -Dtest=org.interpss.plugin.contingency.dclf.DclfMonitoredConstraintTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```
