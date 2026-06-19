# SPP Effective Limits Gap Analysis

Date checked: 2026-06-18  
SPP portal page: `https://portal.spp.org/pages/effective-limits`  
SPP file source: `effective-limits`

---

## Source Summary

The SPP Effective Limits page is a public file-browser page under:

```text
Public / Integrated Marketplace / Real-Time Balancing Market / Effective Limits
```

The page summary says it provides effective limit information associated with binding constraints.

The page publishes daily CSV files:

```text
RTBM-EFFLIMIT-<YYYY><MM><DD>.csv
```

The 2026 file-browser hierarchy observed on 2026-06-18 was:

```text
/2026/01/By_Day
/2026/02/By_Day
/2026/03/By_Day
```

Representative file inspected:

```text
/2026/03/By_Day/RTBM-EFFLIMIT-20260331.csv
```

That file had 16,700 data rows, 283 intervals, and 72 distinct constraint names.

---

## SPP CSV Columns

| Column | Meaning from SPP page |
|---|---|
| `Interval` | Minute-ending of the real-time solution in Central Time. |
| `GMTIntervalEnd` | Minute-ending of the real-time solution in GMT. |
| `Constraint Name` | Constraint name from the Market Clearing Engine. Usually the flowgate name. If not a defined flowgate, this is the branch name from the model. |
| `Constraint Type` | Observed values include `FG`, `M2M`, and page-described `MA` / `MCE` types. |
| `NERCID` | Existing defined NERC ID if available. |
| `TLR Level` | Severity of relief requested. Observed values included `CME` and `3A`. |
| `State` | Activated, Binding, or Breached. |
| `Shadow Price` | RTBM market shadow price for the constraint. |
| `Monitored Facility` | Line, transformer, or multi-element constraint activated/binding/breached in the solution. |
| `Contingent Facility` | Line or transformer outage causing the monitored facility to bind/breach; `BASE` means no contingent element. |
| `Source Limit` | System operating limit as defined in FAC-011-3. |
| `Realtime Effective Limit` | Real-time value used to maintain reliable BES performance under TOP-001-5. |
| `Initial Effective Limit` | Historical effective limit initially used for ideal reliable BES performance. |

---

## Observed 2026 Patterns

From `RTBM-EFFLIMIT-20260331.csv`:

| Category | Count |
|---|---:|
| Rows | 16,700 |
| Distinct intervals | 283 |
| Distinct constraint names | 72 |
| `FG` rows | 13,831 |
| `M2M` rows | 2,869 |
| `ACTIVATED` rows | 15,330 |
| `BINDING` rows | 1,050 |
| `BREACHED` rows | 320 |
| Line monitored-facility rows | 10,640 |
| Transformer monitored-facility rows | 4,362 |
| Multi-element monitored-facility rows | 1,698 |
| `BASE` contingent-facility rows | 1,698 |
| Non-`BASE` contingent-facility rows | 15,002 |

Example rows:

```text
FG, monitored line, contingent branch:
CORNAPTERSUN / LN CORNTP4 - NAPLES1 / OKGE CSWS:SUNNYSDE TERRY_RD:345:2:

FG, multi-element, base case:
GGS / Multi-Element Constraint / BASE

M2M, monitored transformer, contingent transformer:
FTTXFRFTTXFR / XFMR FTTHOMP - FTTHOMP / XFFTTHOMPKU1A1345/1WAUE
```

---

## Mapping to Current Implementation

### Covered Directly

The new monitored interface constraint feature covers the core mathematical form needed for multi-element or branch-group limits:

```text
sum(coefficient_i * postFlowMW_i) <= limitMW
```

This can represent SPP rows where:

- The monitored facility can be mapped to one or more InterPSS branch IDs.
- The coefficients are known or can be assigned.
- The applicable RHS is known for the run.
- The contingent facility can be mapped to an InterPSS outage branch or multi-outage definition.

For a single monitored line or transformer, the same API can be used as a one-term interface:

```json
{
  "id": "CORNAPTERSUN",
  "limit_mw": 190.95,
  "branches": [
    {
      "branch_id": "BusA->BusB(1)",
      "coefficient": 1.0
    }
  ]
}
```

### Partially Covered

Existing branch-overload contingency analysis can compute post-contingency branch overloads, but it compares against the branch object's rating. It does not directly use SPP's per-row `Realtime Effective Limit`.

To reproduce SPP-style rows, use the monitored interface API even for one branch, because it accepts an explicit `limit_mw`.

### Not Covered Yet

| SPP feature | Current gap |
|---|---|
| Time-varying 5-minute limits | `MonitoredInterfaceRecord` stores one fixed `limitMW`. A caller can rebuild records per interval, but there is no native time-series limit schedule. |
| `BASE` contingent facility | Current monitored-constraint API is contingency-scan oriented. It does not return base-case-only interface violations when no outage is applied. |
| Multi-element composition from SPP CSV | The CSV says `Multi-Element Constraint` but does not include the branch list or coefficients. Those must come from another source before InterPSS can model it. |
| SPP facility-name parsing | SPP names like `OKGE CSWS:SUNNYSDE TERRY_RD:345:2:` are not yet parsed/mapped to InterPSS branch IDs. |
| M2M semantics | `M2M` can be treated as a linear constraint if facilities and limits are mapped, but market-to-market settlement/coordination semantics are not modeled. |
| Constraint state and shadow price | InterPSS can calculate flows and violations, but it does not reproduce RTBM `ACTIVATED` / `BINDING` / `BREACHED` state or shadow price. |
| Source/Realtime/Initial limit selection | The API accepts one `limitMW`; it does not encode all three SPP limit fields or a policy for selecting among them. |
| TLR metadata | `TLR Level` is not represented in result objects. |

---

## Recommended Next Implementation Steps

1. Done: add a base-case monitored constraint evaluation path.

   Needed for rows where `Contingent Facility = BASE`, especially multi-element constraints like `GGS`.

2. Done: add a constraint definition model that separates identity, monitored terms, contingencies, and limit schedule.

   Implemented in `ipss-core` under `com.interpss.core.algo.dclf`:

   | Class | Role |
   |---|---|
   | `com.interpss.core.algo.dclf.definition.FlowgateConstraintRecord` | SPP-style flowgate record: monitored terms, contingency ref, effective limits, and market/reliability metadata. |
   | `com.interpss.core.algo.dclf.definition.FlowgateContingencyRef` | `BASE`, single open-branch outage, or multi open-branch outage. |
   | `com.interpss.core.algo.dclf.definition.FlowgateLimitSet` | `Source Limit`, `Realtime Effective Limit`, and `Initial Effective Limit` with a selection policy. |
   | `com.interpss.core.algo.dclf.result.FlowgateViolationResult` | Flowgate result with pre/shift/post values, selected limit, and loading percent. |
   | `com.interpss.core.algo.dclf.FlowgateDclfAnalyzer` | Executes base-case and contingency-aware flowgate checks. |

   The plugin module keeps only deprecated compatibility shims for existing plugin-side callers.

3. Add an SPP Effective Limits CSV adapter.

   The adapter should parse the CSV rows, but it also needs a facility-name resolver because SPP's facility strings are not InterPSS branch IDs.

4. Add facility-name mapping support.

   This is the hardest practical gap. The implementation needs either:

   - a mapping table from SPP facility names to InterPSS branch IDs,
   - a parser keyed to the imported network naming convention, or
   - source model metadata that preserves SPP/market facility names.

5. Add time-series limit execution.

   The daily files are interval-based. A production workflow should run the same network/contingency definitions against each interval's effective limits and produce interval-keyed results.

---

## Bottom Line

The current implementation supports the linear monitored-constraint math needed for SPP-style branch-group or nomogram limits. It now also has a flowgate abstraction that binds a monitored expression to a specific contingency and selected effective limit.

It does not yet fully support SPP Effective Limits as a data product. The remaining missing pieces are:

- SPP CSV ingestion.
- SPP facility-name to InterPSS branch-ID resolution.
- SPP CSV-driven interval execution over `Source Limit`, `Realtime Effective Limit`, and `Initial Effective Limit`.
- Automated interpretation of market metadata such as `M2M`, state, shadow price, TLR level, and NERC ID.

---

## PowerWorld Comparison

PowerWorld public help does not appear to expose a first-class object named `flowgate`. Its closest native concepts are:

| PowerWorld concept | Public help source | InterPSS mapping |
|---|---|---|
| Interface | `https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Interface_Information.htm` | A weighted monitored expression over one or more elements. |
| Interface element | `https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Interface_Element_Information.htm` | A monitored term. PowerWorld supports branch, area-to-area, zone-to-zone, DC line, injection group, generator, load, multi-section line, nested interface, and contingency-conditioned interface elements. |
| Nomogram | `https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Nomogram_Information_Dialog.htm` | A two-interface piecewise-linear limit boundary. InterPSS models this as a nomogram with two monitored-interface axes and one or more linear MW boundary constraints. |
| Limit monitoring settings | `https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Limit_Monitoring_Settings.htm` | Global policy for which element limits are monitored. InterPSS currently receives the monitored records explicitly. |
| Limit group | `https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Limit_Group_Dialog.htm` | Rating-set and reporting-policy selection. PowerWorld distinguishes normal and contingency rating sets for branches, interfaces, and bus pairs. |
| Contingency monitoring exceptions | `https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Monitoring_Exceptions.htm` | Per-contingency include/exclude/default rules. InterPSS now has a DCLF monitoring-exception policy for branch, interface, flowgate, and nomogram checks. |
| Custom monitors | `https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Custom_Monitors.htm` | Generic contingency-time monitoring of object fields with optional pre/post filters and change thresholds. This is broader than SPP flowgate support and is not required for the first flowgate implementation. |
| ATC analysis | `https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/ATC_Analysis.htm` | Uses PTDFs, LODFs, contingency analysis, and limit monitoring settings in the background. This confirms that the InterPSS DCLF flowgate implementation is aligned with standard transfer/contingency screening mechanics. |

### What PowerWorld Confirms

The current InterPSS flowgate design is on the right abstraction boundary:

- PowerWorld treats an interface as a named collection of monitored terms, not just a single line.
- Interface terms can have direction/weighting and can include nested interfaces or injection-based terms.
- Contingency analysis uses the same monitored-interface concept as a post-contingency violation target.
- ATC combines PTDF, LODF, contingency analysis, and limit monitoring, which matches the DCLF flowgate engine shape.
- Nomograms are modeled separately from interfaces because they need a two-axis piecewise-linear boundary.

### Additional Gaps Suggested by PowerWorld

These are not required to reproduce the inspected SPP Effective Limits rows, but they are useful future extensions if InterPSS should support a PowerWorld-like monitoring model:

| Gap | Why it matters |
|---|---|
| Rating-set selection by study context | PowerWorld has normal and contingency rating sets for lines, interfaces, and bus pairs. InterPSS flowgates currently select among Source, Realtime Effective, and Initial limits, but not named rating sets. |
| Per-contingency monitoring exceptions | PowerWorld can include or exclude specific branches/interfaces for one contingency. InterPSS now has a first-pass DCLF exception policy for branch, interface, flowgate, and nomogram objects. |
| Custom monitors with pre/post filters | PowerWorld can monitor arbitrary object fields during contingency analysis. InterPSS flowgates currently monitor MW expressions only. |
| Full nomogram breakpoints | PowerWorld nomograms are convex piecewise-linear boundaries over two interfaces. InterPSS can approximate this with multiple linear constraints, but does not yet have a dedicated nomogram breakpoint record. |
| Non-branch interface terms | PowerWorld interfaces may include area/zone ties, DC lines, injection groups, generators, loads, and nested interfaces. InterPSS flowgates currently support branch terms. |
| Interface contingency element actions | PowerWorld can use an interface as a contingency action, opening/closing or changing interface flow by opening participating elements. InterPSS flowgate contingencies currently cover base and open-branch outage references. |

Recommended implementation priority after the current SPP work:

1. Add an optional `ratingSetId` or `limitGroupId` to the flowgate limit model if imported cases expose multiple ratings.
2. Extend `MonitoringObjectType` only if workflows need non-DCLF objects beyond branch, interface, flowgate, and nomogram.
3. Add a dedicated `NomogramConstraintRecord` only when we need exact two-interface breakpoint limits, rather than simple branch-group/linear-interface limits.
4. Generalize monitored terms beyond branches only after the DCLF model exposes reliable IDs and flow/injection evaluators for those object types.

---

## RTO Reality Check: SPP, MISO, ERCOT

The generalized interface model should support area/zone/interface terms eventually, but public RTO data suggests that the first production implementation should prioritize physical monitored facilities, monitored interfaces, multi-element constraints, and contingency references.

### SPP Effective Limits

Source checked:

```text
https://portal.spp.org/pages/effective-limits
https://portal.spp.org/file-browser-api/download/effective-limits?path=/2026/03/By_Day/RTBM-EFFLIMIT-20260331.csv
```

Representative file checked:

```text
/2026/03/By_Day/RTBM-EFFLIMIT-20260331.csv
```

Observed monitored-facility patterns:

| Monitored facility pattern | Rows |
|---|---:|
| `LN ...` | 10,640 |
| `XFMR ...` | 4,362 |
| `Multi-Element Constraint` | 1,698 |

No monitored-facility values in this representative file contained `AREA`, `ZONE`, `INTF`, `INTERFACE`, or `HUB`. The practical SPP pattern is therefore:

```text
constraint name + monitored line/transformer/multi-element expression + contingent facility or BASE + effective limits
```

### MISO Market Reports

Sources checked:

```text
https://www.misoenergy.org/markets-and-operations/real-time--market-data/market-reports/
https://docs.misoenergy.org/marketreports/M2M%20Flowgate%20List_M2M%20Flowgate%20List%20Readers%20Guide.pdf
https://docs.misoenergy.org/marketreports/Real-Time%20Binding%20Constraints_Real-Time%20Binding%20Constraints%20Readers%20Guide.pdf
https://docs.misoenergy.org/marketreports/Binding%20Constraints%20Supplemental%20Readers%20Guide.pdf
```

MISO's M2M Flowgate List reader guide defines a flowgate as a pre-identified constraint comprising one or more monitored transmission facilities and optionally one or more contingency facilities.

The Real-Time Binding Constraints reader guide says the branch-name field may contain the monitored facility, interface, device, or transformer. Monitored interfaces are prefixed with `INTF:`. It also lists branch types:

```text
LN, XF, ZBR, DC
```

The Binding Constraints Supplemental reader guide adds structured mapping fields:

```text
Constraint Type
Flowgate Name
Device Type
Key1 / Key2 / Key3
Direction
From Area
To Area
From Station
To Station
From KV
To KV
```

Important design reading: MISO uses `From Area` and `To Area` as descriptive/model mapping fields for the monitored device or transformer. The guide does not present `AREA` as a common top-level monitored-term type in these reports. The report-level practical terms are line, transformer, phase shifter, unit, zero-impedance branch, HVDC, and explicitly tagged interface.

### ERCOT / Texas

Sources checked:

```text
https://www.ercot.com/gridinfo/transmission
https://www.ercot.com/mp/data-products/data-product-details?id=NP6-86-CD
```

ERCOT's public transmission page links the `SCED Shadow Prices and Binding Transmission Constraints` report. Its description says the report shows:

```text
contingency name
overloaded element details
element name
from/to station name and kV level
shadow price
penalty / max shadow price
overloaded element limit and flow value pairs
```

This is also physical-facility centered. ERCOT public report descriptions do not show area/zone interface terms as primary published constraint terms. ERCOT may use Generic Transmission Constraints or operational interfaces internally, but the public binding-constraint data product is exposed as contingency plus overloaded element details and limits.

### Implementation Implication

Recommended monitored-term priority:

1. `BRANCH` / physical AC line and transformer terms.
2. `INTERFACE` / named nested monitored expression.
3. `MULTI_ELEMENT` / branch-group expression with coefficients.
4. `DC_LINE`, `PHASE_SHIFTER`, and `ZERO_IMPEDANCE_BRANCH` if imported model support is available.
5. `GENERATOR` / `UNIT` only for MISO-style supplemental mappings or PowerWorld-compatible custom interfaces.
6. `AREA_TIE` and `ZONE_TIE` as derived convenience terms, not core first-pass terms.

Monitoring exceptions are still worth adding, because they are a study-control mechanism and not tied to whether the monitored object is a branch, interface, or area. The exception key should be generic:

```text
contingencyRef + monitoredObjectType + monitoredObjectId + INCLUDE/EXCLUDE/DEFAULT
```

But the first supported `monitoredObjectType` values should be:

```text
FLOWGATE
INTERFACE
BRANCH
```

---

## Extensible Contingency Violation Checks

The flowgate work is one instance of a broader requirement: contingency analysis should allow different violation or constraint checks to be plugged into the same contingency execution path.

Today, monitored-constraint analysis is specialized around this shape:

```text
contingency -> post-contingency branch flows -> weighted MW expression -> limit check
```

That is correct for branch overloads, monitored interfaces, and most SPP/MISO flowgate rows. It is too narrow for:

- full nomogram breakpoint checks,
- voltage high/low checks,
- islanding or disconnected-bus checks,
- base-case violation re-reporting policy,
- PowerWorld-style custom monitors,
- reporting changes even when a hard limit is not exceeded,
- per-contingency include/exclude/default monitoring exceptions.

### Proposed Core API

Add a small provider interface in `ipss-core`, under the DCLF contingency package:

```java
public interface ContingencyViolationCheck<R extends ContingencyViolationResult> {
    String getId();

    void compile(ContingencyCheckCompileContext context);

    List<R> evaluateBase(ContingencyCheckContext context);

    List<R> evaluateContingency(ContingencyCheckContext context);
}
```

The contexts should expose the reusable solved state, not force each check to rebuild DCLF:

```java
public final class ContingencyCheckContext {
    private final AclfNetwork network;
    private final ContingencyAnalysisAlgorithm dclfAlgorithm;
    private final BaseContingency<DclfMonitoringBranch> contingency;
    private final double[] preBranchFlowMw;
    private final double[] postBranchFlowMw;
    private final DclfContingencyConfig config;
    private final MonitoringExceptionPolicy monitoringExceptionPolicy;
}
```

Common result shape:

```java
public interface ContingencyViolationResult {
    String getCheckId();
    String getViolationType();
    String getContingencyId();
    String getMonitoredObjectType();
    String getMonitoredObjectId();
    double getLimitValue();
    double getPreValue();
    double getPostValue();
    double getViolationAmount();
    Map<String, String> getMetadata();
}
```

### Built-in Checks

Initial built-ins should be:

| Check | Purpose |
|---|---|
| `BranchThermalLimitCheck` | Existing branch overload screening. |
| `MonitoredExpressionLimitCheck` | Generalized interface / branch-group / flowgate expression checks. |
| `FlowgateLimitCheck` | Wrapper that binds monitored expression, contingency ref, Source/Realtime/Initial limit selection, and metadata. |
| `NomogramLimitCheck` | Later: convex piecewise-linear two-interface boundary. |
| `VoltageLimitCheck` | Later: AC contingency or DC-compatible voltage proxy checks. |
| `IslandViolationCheck` | Later: disconnected island / reserve-deficiency style reporting. |
| `CustomFieldMonitorCheck` | Later: PowerWorld-style custom monitor for arbitrary fields and pre/post filters. |

### Monitoring Exceptions in the Check Pipeline

Monitoring exceptions should run before each check evaluates:

```text
all configured checks
  -> apply contingency-specific monitoring exceptions
  -> evaluate enabled monitored objects
  -> emit typed violation results
```

The exception decision should be generic:

```java
public enum MonitoringExceptionStatus {
    INCLUDE,
    EXCLUDE,
    DEFAULT
}
```

and keyed by:

```text
contingency id
monitored object type
monitored object id
optional check id
status
```

This supports examples such as:

```text
Exclude FLOWGATE GGS for contingency X
Include BRANCH BusA->BusB(1) for contingency Y
Use default monitoring for INTERFACE NorthExport
```

### Execution Shape

The existing fast DCLF path should remain the execution engine. The extensibility point should be at the result collection stage:

```text
1. Build/solve base DCLF once.
2. Compile all violation checks once.
3. For each contingency, compute post-contingency state.
4. Build ContingencyCheckContext.
5. Run registered checks.
6. Collect typed violation results.
```

For performance, checks should be compiled into index-based evaluators where possible. For example, a monitored expression should compile branch IDs into branch-flow indexes once, then use arrays during contingency evaluation.

### Performance Contract

The generalized violation-check API must not replace the current efficient simulation path. It should be a thin layer around the existing post-contingency arrays.

Current efficient path:

```text
base DCLF solve
  -> fast open-branch contingency update
  -> post-contingency branch-flow array
  -> collect violations
```

Target generalized path:

```text
base DCLF solve
  -> compile checks once
  -> fast open-branch contingency update
  -> post-contingency state arrays
  -> run compiled checks over arrays
  -> collect only violations
```

The important rule is that extension happens after the numerical update, not inside it.

Required implementation constraints:

| Constraint | Reason |
|---|---|
| Compile IDs to integer indexes once before contingency scanning | Avoid branch-ID lookups in the inner loop. |
| Store coefficients, limits, thresholds, and monitor indexes in primitive arrays | Avoid per-contingency object traversal and boxing. |
| Reuse existing `preFlowMw` and `shiftedFlowMw` arrays | Avoid recomputing post-contingency branch flows per check. |
| Run only enabled checks | Avoid paying for nomogram or other advanced MW checks when the study only needs branch/flowgate checks. |
| Keep fast-path specialization for common checks | Branch thermal and monitored-expression checks should use direct loops, not reflective or dynamic dispatch per monitored term. |
| Emit result objects only for violations or requested monitored values | Avoid allocating result rows for every contingency/check/object combination. |
| Apply monitoring exceptions during compile or per-contingency prefiltering | Avoid checking exception maps for every term in every expression. |

### Compiled Check Shape

The runtime check should not look like this in the inner loop:

```java
for (MonitoredTermRecord term : expression.getTerms()) {
    value += term.getCoefficient() * findBranch(term.getBranchId()).getPostFlow();
}
```

It should look like this:

```java
for (int i = 0; i < monitorIndexes.length; i++) {
    value += coefficients[i] * postFlowMw[monitorIndexes[i]];
}
```

That is already close to the current `CompiledMonitoredConstraint` pattern. The generalized interface should preserve this pattern and broaden what can be compiled, not introduce a high-level object callback per term.

### Recommended Engine Split

Use two layers:

1. `ContingencySimulationEngine`

   Owns numerical work:

   ```text
   DCLF solve
   outage application
   Woodbury / sparse equation / KLU batch path
   pre/post flow arrays
   ```

2. `ContingencyViolationCheck`

   Owns result interpretation:

   ```text
   branch loading
   monitored expression loading
   flowgate effective limit
   nomogram boundary
   monitoring exception policy
   ```

The simulation engine should know nothing about SPP, MISO, PowerWorld, flowgate metadata, or custom monitor semantics. It should only provide arrays and topology/status context.

### Dispatch Strategy

Avoid one virtual method call per monitored object in the hottest path. Use check-level dispatch:

```text
for each contingency:
  compute post-state arrays once
  for each enabled compiled check:
    check.evaluate(context, results)
```

This means the virtual dispatch count is proportional to:

```text
contingencies * enabled_check_types
```

not:

```text
contingencies * monitored_objects * monitored_terms
```

For branch thermal and monitored-expression checks, `evaluate()` should contain tight loops over primitive arrays.

### Monitoring Exception Cost Control

Monitoring exceptions should be compiled into per-contingency masks or filtered check lists:

```text
contingency id -> enabled monitor indexes
```

Do not perform string-key exception lookup for every monitor term. Recommended policy:

1. Build a global enabled-monitor bitmap at compile time.
2. For contingencies with no exceptions, reuse the global bitmap.
3. For contingencies with exceptions, build a small override bitmap or filtered index list.
4. During evaluation, iterate the enabled index list.

This keeps the normal no-exception case at current speed.

### Backward Compatibility and Fast Path

The existing methods should continue to select the current optimized implementation:

```java
ParallelDclfContingencyAnalyzer.performMonitoredConstraintAnalysis(...)
FlowgateDclfAnalyzer.executeFlowgateAnalysis(...)
```

Internally, they can delegate to the generalized pipeline only when the enabled checks can be compiled into the same fast representation. If a caller enables a more advanced MW check, such as nomogram boundary checks, the engine can use the generic collector path while still reusing the same post-contingency arrays.

In other words:

```text
simple branch/interface/flowgate checks -> current fast path
mixed advanced checks -> same simulation path + extra compiled check collectors
unsupported non-MW checks -> outside this DCLF pipeline
```

### Compatibility Strategy

Keep existing APIs as convenience wrappers:

```java
performMonitoredConstraintAnalysis(...)
executeFlowgateAnalysis(...)
```

Internally, these should create a `DclfContingencyLimitStudy` with the appropriate built-in MW limit check. That prevents existing callers from breaking while allowing new checks to share the same engine.

This DCLF extension intentionally excludes voltage and reactive checks. Keep the scope to MW flow/limit checks:

```text
BRANCH_THERMAL_MW
MONITORED_EXPRESSION_MW
FLOWGATE_EFFECTIVE_LIMIT
NOMOGRAM_MW_BOUNDARY
```

### Implementation To-Do List

Status: completed for the DCLF-only MW violation-check extension on 2026-06-19. The implementation is intentionally scoped to MW flow/limit checks and does not add voltage, reactive, or generic object-field monitoring.

Phase 1: Core DCLF check abstractions

- [x] Add `DclfContingencyLimitCheck<R extends DclfLimitViolationResult>` in `ipss-core`.
- [x] Add `DclfLimitCheckCompileContext` with network, base DCLF algorithm, branch index map, base MVA, and configuration.
- [x] Add `DclfLimitCheckContext` with contingency id, contingency object, `preFlowMw[]`, `postFlowMw[]`, base MVA, and compiled monitoring-exception policy.
- [x] Add `DclfLimitViolationResult` as the common result interface for MW limit violations.
- [x] Keep the API minimal: no voltage, no reactive power, no generic object-field monitor in this DCLF path.

Phase 2: Compile-time monitored expression model

- [x] Introduce a compiled expression object: `CompiledDclfMonitoredExpression`.
- [x] Compile branch IDs to `int[] monitorIndexes`.
- [x] Store coefficients in `double[] coefficients`.
- [x] Store limit/threshold values in primitive fields.
- [x] Validate inactive or missing branches during compile, not during every contingency.
- [x] Preserve existing `MonitoredInterfaceRecord` and `FlowgateConstraintRecord` builders as compatibility inputs.

Phase 3: Built-in checks

- [x] Implement `BranchMwLimitCheck` for simple branch thermal MW checks.
- [x] Implement `MonitoredExpressionMwLimitCheck` for weighted branch-group/interface constraints.
- [x] Implement `FlowgateEffectiveLimitCheck` as a wrapper around monitored expressions plus `FlowgateLimitSet` selection and metadata.
- [x] Implement `NomogramMwBoundaryCheck` as a compiled two-axis linear MW boundary.
- [x] Do not add `VoltageLimitCheck` to the DCLF pipeline.

Phase 4: Monitoring exceptions

- [x] Add `MonitoringExceptionRecord`.
- [x] Add `MonitoringExceptionStatus` with `INCLUDE`, `EXCLUDE`, and `DEFAULT`.
- [x] Add `MonitoringObjectType` with `BRANCH`, `INTERFACE`, `FLOWGATE`, and `NOMOGRAM`.
- [x] Add `MonitoringExceptionPolicy` that compiles exception rules once and supports check-specific or object-wide overrides.
- [x] Make the no-exception case reuse the current fast analyzer path with no per-monitor exception lookup.
- [x] Apply exceptions before each check evaluates monitored objects.

Phase 5: Engine integration

- [x] Add `DclfContingencyLimitStudy` as the compatibility orchestration class for monitored-expression checks.
- [x] Compile built-in advanced checks before evaluation.
- [x] Reuse existing `ParallelDclfContingencyAnalyzer` fast open-branch outage path when no monitoring exceptions are supplied.
- [x] Insert new check evaluation around the same pre/post MW array representation used by monitored constraints.
- [x] Keep check dispatch at check-type level, not per monitored object.
- [x] Allocate result objects only for violations.

Phase 6: Compatibility wrappers

- [x] Keep `ParallelDclfContingencyAnalyzer.performMonitoredConstraintAnalysis(...)`.
- [x] Keep `FlowgateDclfAnalyzer.executeFlowgateAnalysis(...)`.
- [x] Add `DclfContingencyLimitStudy.performMonitoredExpressionAnalysis(...)` so callers can add exceptions without replacing the current analyzer.
- [x] Add `FlowgateDclfAnalyzer.executeFlowgateAnalysis(..., monitoringExceptions, ...)` for flowgate exception support.
- [x] Adapt existing result classes from `DclfLimitViolationResult` so current callers do not need to change.

Phase 7: Tests and benchmarks

- [x] Add tests proving old monitored-interface results match the new check path.
- [x] Add tests proving old flowgate results and exception behavior.
- [x] Add tests for `INCLUDE`, `EXCLUDE`, and `DEFAULT` monitoring exceptions.
- [x] Add tests for branch thermal checks and nomogram boundary checks with monitoring exceptions.
- [x] Verify branch/interface/flowgate no-exception studies keep the existing fast path.
- [x] Run targeted DCLF tests and `CorePluginTestSuite`.

Phase 8: Documentation and examples

- [x] Document the DCLF-only scope: MW limits and monitored expressions only.
- [x] Add JSON examples for monitoring exceptions.
- [x] Add Java examples for monitoring exceptions and compiled checks.
- [x] Add a migration note: current APIs remain supported, new check API is for advanced studies.

### Monitoring Exception Example

There is no required file format in the core API; callers can import this shape, or any equivalent source, into `MonitoringExceptionRecord` objects. In `ipss.plugin.core`, `ContingencyFileUtil.importDclfMonitoringConfigFromJson(...)` now supports a single monitoring config file that embeds monitored branches, monitored interfaces, flowgates, nomograms, and monitoring exceptions together:

```json
{
  "monitored_branches": [
    {
      "from_bus": "Bus2",
      "to_bus": "Bus4",
      "circuit": "1",
      "from_bus_area": "A",
      "to_bus_area": "B",
      "base_kv": 138.0,
      "pre_contingency_flow_mw": 50.0
    }
  ],
  "monitored_interfaces": [
    {
      "id": "PATH26_N-S",
      "limit_mw": 1400.0,
      "branches": [
        {
          "branch_id": "Bus2->Bus4(1)",
          "coefficient": 0.75
        }
      ]
    },
    {
      "id": "PATH26_S-N",
      "limit_mw": 1300.0,
      "branches": [
        {
          "branch_id": "Bus3->Bus4(1)",
          "coefficient": -0.25
        }
      ]
    }
  ],
  "flowgates": [
    {
      "id": "CORNAPTERSUN",
      "constraint_type": "FG",
      "nerc_id": "5107",
      "tlr_level": "CME",
      "market_state": "BINDING",
      "monitored_facility_name": "LN CORNTP4 - NAPLES1",
      "contingent_facility_name": "OKGE CSWS:SUNNYSDE TERRY_RD:345:2:",
      "contingency_ref": {
        "type": "SINGLE_BRANCH_OPEN",
        "outage_branch_id": "Bus2->Bus5(1)"
      },
      "limits": {
        "source_limit_mw": 201.0,
        "realtime_effective_limit_mw": 190.95,
        "initial_effective_limit_mw": 194.97,
        "selection_policy": "REALTIME_EFFECTIVE_LIMIT"
      },
      "branches": [
        {
          "branch_id": "Bus2->Bus4(1)",
          "coefficient": 1.0
        }
      ]
    }
  ],
  "nomograms": [
    {
      "id": "GGS",
      "axis_a_id": "PATH26_N-S",
      "axis_b_id": "PATH26_S-N",
      "constraints": [
        {
          "id": "GGS_LIMIT_01",
          "coefficient_a": 0.6,
          "coefficient_b": 0.4,
          "limit_mw": 900.0
        }
      ]
    }
  ],
  "monitoring_exceptions": [
    {
      "contingency_id": "cont:Bus2->Bus3(1)",
      "object_type": "INTERFACE",
      "object_id": "PATH26_N-S",
      "status": "EXCLUDE"
    },
    {
      "contingency_id": "cont:Bus2->Bus3(1)",
      "object_type": "FLOWGATE",
      "object_id": "CORNAPTERSUN",
      "check_id": "FLOWGATE_EFFECTIVE_LIMIT",
      "status": "INCLUDE"
    },
    {
      "contingency_id": "BASE",
      "object_type": "NOMOGRAM",
      "object_id": "GGS_FACET_01",
      "status": "DEFAULT"
    }
  ]
}
```

The older JSON entry points remain available for current workflows:

- `importContingenciesFromJson(...)`
- `importMonitoredBranchRecordsFromJson(...)`
- `importMonitoredInterfaceRecordsFromJson(...)`

Java usage for monitored-interface studies:

```java
ConcurrentLinkedQueue<DclfMonitoredConstraintResult> results =
        DclfContingencyLimitStudy.performMonitoredExpressionAnalysis(
                net,
                contingencies,
                monitoredInterfaces,
                monitoringExceptions,
                100.0,
                false,
                threadCount,
                DclfContingencySolutionMethod.SparseEqnSolve,
                0);
```

Java usage for flowgate studies:

```java
List<FlowgateViolationResult> results =
        FlowgateDclfAnalyzer.executeFlowgateAnalysis(
                net,
                flowgates,
                monitoringExceptions,
                config,
                threadCount);
```

For advanced compiled checks, build the branch index once and evaluate against the same DCLF MW arrays:

```java
BranchMwLimitCheck branchCheck = new BranchMwLimitCheck(branchIds, 100.0);
branchCheck.compile(new DclfLimitCheckCompileContext(
        net, dclfAlgo, branchIndexById, net.getBaseMva(), config));

NomogramMwBoundaryCheck nomogramCheck =
        new NomogramMwBoundaryCheck(nomograms, 100.0);
nomogramCheck.compile(new DclfLimitCheckCompileContext(
        net, dclfAlgo, branchIndexById, net.getBaseMva(), config));
```

Migration note: existing callers can keep using `ParallelDclfContingencyAnalyzer.performMonitoredConstraintAnalysis(...)` and `FlowgateDclfAnalyzer.executeFlowgateAnalysis(...)`. Use the new overloads and check interfaces only when a study needs monitoring exceptions, explicit branch checks, flowgate effective-limit metadata, or nomogram MW boundaries.

### Large-Case Performance Validation

The generalized monitored-expression facade was checked against the current analyzer on ACTIVSg25K and ACTIVSg70K using the same KLU endpoint RHS batched DCLF path. The benchmark uses explicit one-term monitored expressions with a high limit so result allocation does not dominate the timing.
It also packages synthetic flowgate and nomogram definitions onto the same test systems:

- `flowgate`: contingency-aware `FlowgateConstraintRecord` rows built from the monitored-expression pool and grouped over a bounded set of outage contingencies.
- `nomogram-base`: compiled `NomogramMwBoundaryCheck` records built from pairs of monitored expressions and evaluated on the base DCLF MW array.

Command:

```bash
mvn -pl ipss.test.plugin.core test \
  -Dtest=DclfViolationCheckLargeCasePerformanceTest \
  -Dinterpss.largeViolationCheckPerf=true \
  -Dinterpss.violationCheckPerfInclude70k=true \
  -Dinterpss.violationCheckPerfRepeats=2 \
  -Dinterpss.violationCheckPerfWarmups=1 \
  -Dinterpss.violationCheckPerfMaxCont=6000 \
  -Dinterpss.violationCheckPerfMaxMon=5000 \
  -Dinterpss.violationCheckPerfMaxFlowgates=500 \
  -Dinterpss.violationCheckPerfMaxFlowgateContingencies=50 \
  -Dinterpss.violationCheckPerfMaxNomograms=5000 \
  -Dinterpss.violationCheckPerfParallelism=4 \
  -Dinterpss.violationCheckPerfRhsBatchSize=64
```

For ACTIVSg70K the run used the existing contingency JSON file:

```text
/Users/ipssdev/Downloads/ACTIVSg70k/ACTIVSg70k_filtered_contingencies.json
```

That file imported `41,682` contingencies; the benchmark used the first `6,000`.

Measured rows from 2026-06-19:

| Case | Contingencies | Monitors | Old path ms | New no-exception ms | No-exception overhead | New sparse-exception ms | Sparse-exception overhead |
|---|---:|---:|---:|---:|---:|---:|---:|
| ACTIVSg25K repeat 1 | 6000 | 5000 | 521.828 | 519.094 | -0.524% | 516.143 | -1.089% |
| ACTIVSg25K repeat 2 | 6000 | 5000 | 508.653 | 515.217 | 1.290% | 528.596 | 3.921% |
| ACTIVSg70K repeat 1 | 6000 | 5000 | 1602.811 | 1594.267 | -0.533% | 1577.384 | -1.586% |
| ACTIVSg70K repeat 2 | 6000 | 5000 | 1582.780 | 1572.510 | -0.649% | 1615.970 | 2.097% |

Packed flowgate and nomogram validation run from 2026-06-19:

| Case | Contingencies | Monitors | Supplemental check | Check count | Distinct flowgate contingencies | Elapsed ms | Results |
|---|---:|---:|---|---:|---:|---:|---:|
| ACTIVSg25K | 6000 | 5000 | flowgate | 500 | 50 | 1816.810 | 0 |
| ACTIVSg25K | 6000 | 5000 | nomogram-base | 4999 | n/a | 36.170 | 0 |
| ACTIVSg70K | 6000 | 5000 | flowgate | 500 | 50 | 6145.990 | 0 |
| ACTIVSg70K | 6000 | 5000 | nomogram-base | 4999 | n/a | 118.308 | 0 |

Implementation note: the no-exception case delegates directly to the existing fast analyzer. Monitoring exceptions are evaluated inside `ParallelDclfContingencyAnalyzer` after constraints are compiled, preserving one DCLF setup and one batched contingency scan. `MonitoringExceptionPolicy` uses nested maps so the hot path does not allocate lookup keys per monitored constraint.

Flowgate performance note: the current `FlowgateDclfAnalyzer` groups records by contingency reference and delegates each group to the monitored-constraint analyzer. Therefore runtime scales with distinct flowgate contingencies as well as the number of flowgate rows. A 500-flowgate stress run with 500 distinct contingency refs completed successfully but took about `17.5 s` on ACTIVSg25K and `55.6 s` on ACTIVSg70K; grouping the same 500 flowgate rows over 50 contingency refs reduced the run to the table above. If production imports contain thousands of flowgates with mostly unique contingencies, the next optimization should batch flowgate contingency groups through the same multi-contingency execution path rather than calling the analyzer once per group.

---

## Flowgate Implementation Shape

The implementation follows the common patterns observed in the SPP sample:

### `FG` with monitored line and contingent branch

SPP example pattern:

```text
Constraint Name: CORNAPTERSUN
Constraint Type: FG
NERCID: 5107
TLR Level: CME
State: ACTIVATED/BINDING/BREACHED
Monitored Facility: LN CORNTP4 - NAPLES1
Contingent Facility: OKGE CSWS:SUNNYSDE TERRY_RD:345:2:
Source Limit: 201
Realtime Effective Limit: 190.95
Initial Effective Limit: 194.97
```

Normalized InterPSS flowgate:

```java
FlowgateLimitSet limits = new FlowgateLimitSet(201.0, 190.95, 194.97);
limits.setSelectionPolicy(FlowgateLimitSelection.REALTIME_EFFECTIVE_LIMIT);

FlowgateConstraintRecord flowgate =
    FlowgateConstraintRecord.of(
        "CORNAPTERSUN",
        FlowgateContingencyRef.singleBranchOpen("outageBranchId"),
        limits);
flowgate.setConstraintType("FG");
flowgate.setNercId("5107");
flowgate.setTlrLevel("CME");
flowgate.setMonitoredFacilityName("LN CORNTP4 - NAPLES1");
flowgate.setContingentFacilityName("OKGE CSWS:SUNNYSDE TERRY_RD:345:2:");
flowgate.addBranch(new MonitoredBranchRecord("monitorBranchId", 1.0));
```

### `FG` with multi-element monitored expression and `BASE`

SPP example pattern:

```text
Constraint Name: GGS
Constraint Type: FG
Monitored Facility: Multi-Element Constraint
Contingent Facility: BASE
Source Limit: 1310
Realtime Effective Limit: 1244.5
Initial Effective Limit: 1270.7
```

Normalized InterPSS flowgate:

```java
FlowgateLimitSet limits = new FlowgateLimitSet(1310.0, 1244.5, 1270.7);

FlowgateConstraintRecord flowgate =
    FlowgateConstraintRecord.of("GGS", FlowgateContingencyRef.base(), limits);
flowgate.setConstraintType("FG");
flowgate.setMonitoredFacilityName("Multi-Element Constraint");
flowgate.setContingentFacilityName("BASE");
flowgate.addBranch(new MonitoredBranchRecord("branchA", 0.70));
flowgate.addBranch(new MonitoredBranchRecord("branchB", 0.30));
```

### Execute

```java
ConcurrentLinkedQueue<FlowgateViolationResult> results =
    FlowgateDclfAnalyzer.executeFlowgateAnalysis(net, flowgates, config, 8);
```

The analyzer:

- evaluates `BASE` records directly against base-case DCLF flows,
- groups non-base records by contingency,
- builds open-branch DCLF contingencies from `FlowgateContingencyRef`,
- delegates post-contingency monitored-expression solving to the existing DCLF monitored-constraint engine,
- returns `FlowgateViolationResult` with the selected limit policy preserved.
