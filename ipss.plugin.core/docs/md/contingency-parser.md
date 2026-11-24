# reference simulator Contingency (`.con`) Parser — Implementation Summary

**Module:** `ipss.plugin.core`  
**Package:** `org.interpss.plugin.contingency.parser`  
**Test module:** `ipss.test.plugin.core`  
**Test package:** `org.interpss.core.contingency.parser`

---

## 1. Overview

This module parses reference simulator-format contingency definition (`.con`) files and maps them to InterPSS `AclfMultiOutage` objects for load-flow contingency analysis.

The workflow is a two-stage pipeline:

```
reference simulator .con file
      │
      ▼  ConFileParser  (network-independent)
ConContainer  ──  list of ConCase
      │
      ▼  ConToIpssMapper  (network-aware)
List<AclfMultiOutage>
```

A third utility class, `ConFileConverter`, filters a `ConContainer` down to only outage-relevant cases before passing them to the mapper.

---

## 2. Source Files

### 2.1 Data Model

| Class | Role |
|---|---|
| `ConContainer` | Top-level holder; contains a list of `ConCase` objects |
| `ConCase` | One contingency: label, category, and four event lists |
| `ConBranchEvent` | Branch/transformer open or close |
| `ConBusEvent` | Bus isolation (disconnect all incident branches) |
| `ConEquipEvent` | REMOVE / ADD / BLOCK for bus-attached equipment |
| `ConBusModEvent` | SET / CHANGE / INCREASE / DECREASE of a bus quantity |
| `ConEquipMoveEvent` | MOVE load/generation/shunt between two buses |

### 2.2 Enumerations (scoped type-safety)

| Enum | Used by | Values |
|---|---|---|
| `ConBranchAction` | `ConBranchEvent` | `DISCONNECT`, `CLOSE`, `DISCONNECT_3W_WINDING` |
| `ConEquipAction` | `ConEquipEvent` | `REMOVE`, `ADD`, `BLOCK` |
| `ConEquipType` | `ConEquipEvent`, `ConEquipMoveEvent` | `MACHINE`, `LOAD`, `ACTIVE_LOAD`, `REACTIVE_LOAD`, `SHUNT`, `SWSHUNT`, `GENERATION`, `DC_LINE` |
| `ConBusModAction` | `ConBusModEvent` | `SET`, `CHANGE`, `INCREASE`, `DECREASE` |

These four enums replaced the original monolithic `ConEventType` enum, which encoded both action and equipment dimensions in a single 25-value flat list.

### 2.3 Processing Classes

| Class | Role |
|---|---|
| `ConFileParser` | Regex-based line parser; produces a `ConContainer` with no network dependency |
| `ConToIpssMapper` | Translates each `ConCase` to an `AclfMultiOutage` using an `AclfNetwork` |
| `ConFileConverter` | Convenience filter: extracts only branch-outage cases from a `ConContainer` |

---

## 3. reference simulator Record Coverage

### Branch / Transformer
| reference simulator syntax | `ConBranchAction` | Notes |
|---|---|---|
| `DISCONNECT BRANCH FROM i TO j CKT c` | `DISCONNECT` | 2-terminal |
| `OPEN BRANCH FROM i TO j CKT c` | `DISCONNECT` | synonym |
| `TRIP LINE FROM i TO j CKT c` | `DISCONNECT` | synonym |
| `CLOSE BRANCH FROM i TO j CKT c` | `CLOSE` | 2-terminal |
| `DISCONNECT BRANCH FROM i TO j TO k CKT c` | `DISCONNECT` | 3W (all windings) |
| `CLOSE BRANCH FROM i TO j TO k CKT c` | `CLOSE` | 3W (all windings) |
| `DISCONNECT THREEWINDING AT BUS i TO BUS j TO BUS k` | `DISCONNECT_3W_WINDING` | single winding |

### Bus
| reference simulator syntax | Event class |
|---|---|
| `DISCONNECT BUS n` | `ConBusEvent` |

### Equipment (REMOVE / ADD)
| reference simulator syntax | `ConEquipAction` | `ConEquipType` |
|---|---|---|
| `REMOVE MACHINE id FROM BUS n` | `REMOVE` | `MACHINE` |
| `REMOVE UNIT id FROM BUS n` | `REMOVE` | `MACHINE` (synonym) |
| `REMOVE LOAD id FROM BUS n` | `REMOVE` | `LOAD` |
| `REMOVE SHUNT id FROM BUS n` | `REMOVE` | `SHUNT` |
| `REMOVE SWSHUNT [id] FROM BUS n` | `REMOVE` | `SWSHUNT` |
| `ADD MACHINE id TO BUS n` | `ADD` | `MACHINE` |
| `ADD LOAD id TO BUS n` | `ADD` | `LOAD` |
| `ADD SHUNT id TO BUS n` | `ADD` | `SHUNT` |
| `ADD SWSHUNT [id] TO BUS n` | `ADD` | `SWSHUNT` |
| `BLOCK TWOTERMDC id` | `BLOCK` | `DC_LINE` (busNum = -1) |

### Bus Modification
| reference simulator keyword | `ConBusModAction` |
|---|---|
| `SET` | `SET` |
| `CHANGE` | `CHANGE` |
| `INCREASE` | `INCREASE` |
| `DECREASE` / `REDUCE` | `DECREASE` |

### Load / Generation Move
| reference simulator syntax | `ConEquipType` |
|---|---|
| `MOVE r MW LOAD FROM BUS i TO BUS j` | `LOAD` |
| `MOVE r MW GENERATION FROM BUS i TO BUS j` | `GENERATION` |
| `MOVE r MVAR SHUNT FROM BUS i TO BUS j` | `SHUNT` |
| `MOVE r MW ACTIVE LOAD FROM BUS i TO BUS j` | `ACTIVE_LOAD` |
| `MOVE r MVAR REACTIVE LOAD FROM BUS i TO BUS j` | `REACTIVE_LOAD` |

---

## 4. Field Naming Conventions

All event classes store reference simulator bus references as **`int busNum`** (integer, as written in the file), consistent with the parser data model:

| Field | Type | Appears in |
|---|---|---|
| `busNum` | `int` | `ConBusEvent`, `ConBusModEvent`, `ConEquipEvent` |
| `fromBusNum` / `toBusNum` | `int` | `ConBranchEvent`, `ConEquipMoveEvent` |
| `thirdBusNum` | `int` | `ConBranchEvent` (3W only) |

**Rationale:** The parser is network-independent. Storing integer bus numbers (verbatim from the file) keeps the parser free of any dependency on how the loaded network assigns String IDs. The translation to InterPSS String IDs (`"Bus1001"` or `"1001"` depending on the loader) is deferred to `ConToIpssMapper`.

---

## 5. Mapper: ID-Based Network Lookup

`ConToIpssMapper` resolves bus and branch objects using InterPSS String IDs:

1. **`findBusByNumber(int busNum)`** — scans `net.getBusList()` for a bus whose `.getNumber()` matches. This scan is necessary because the String ID prefix (`"Bus"` or empty) depends on the loader used.

2. **`findBranchByBusNumbers(int from, int to, String ckt)`** — resolves both bus objects first, then calls `net.getBranch(ToBranchId.f(fromBus.getId(), toBus.getId(), ckt))` for an O(1) hash-map lookup. Both orientations (A→B and B→A) are tried.

3. **`find3WBranchByBusNumbers`** — resolves bus objects, then matches by comparing `Set<String>` of bus IDs against each `Aclf3WBranch` in `net.getSpecialBranchList()`.

4. **`mapBusDisconnect`** — after resolving the `AclfBus` object, uses object-identity comparison (`bra.getFromBus() == bus`) for the incident-branch loop.

---

## 6. Test Coverage

| Test class | Tests | Scope |
|---|---|---|
| `ConFileParser_Test` | 30 | Parser: one test per reference simulator record type |
| `ConToIpssMapper_Test` | 11 | Mapper: branch open/close, bus disconnect, miss/warning cases |
| **Total** | **41** | All passing |

---

## 7. Key Design Decisions

| Decision | Rationale |
|---|---|
| 4 scoped enums instead of 1 monolithic `ConEventType` | Compiler-enforced type safety; invalid combos (e.g. `ConBranchAction` on a `ConEquipEvent`) become impossible |
| Parser stores `int busNum`, not `String busId` | Parser is network-independent; ID format varies by loader |
| Mapper uses `net.getBranch(ToBranchId.f(...))` | O(1) hash lookup instead of O(n) branch scan; uses authoritative InterPSS object IDs |
| `ConBusEvent` has no action field | It always means DISCONNECT; carrying a redundant field would add noise |
| `UNIT` treated as synonym for `MACHINE` | reference simulator allows both; stored as `ConEquipType.MACHINE` |
| `REDUCE` treated as synonym for `DECREASE` | reference simulator BUS_MOD section allows both spellings |
| `REACTIVE` checked before `ACTIVE` in `ConEquipMoveEvent.equipTypeOf()` | "ACTIVE" is a substring of "REACTIVE"; wrong order would misclassify reactive load |

---

## 8. Running a typed AC contingency study

Use the same load-flow profile for the base solve and the outage analyzer.
The minimum JSON profile inherits the supported settings from the imported
RAW or RAWX case and uses Core defaults for settings the case omits.

```json
{"schemaVersion": 1}
```

This example reads a version-35 RAW case and a contingency file. Set the
parser version to match the input file. The analyzer takes a converged base,
runs each outage on an isolated copy, and returns results in input order.
Give each outage a distinct, nonblank ID.

```java
import java.nio.file.Path;

import com.interpss.core.LoadflowAlgoObjectFactory;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.plugin.aclf.config.AclfRunConfigRec;
import org.interpss.plugin.contingency.AclfContingencyConfig;
import org.interpss.plugin.contingency.ParallelAclfContingencyAnalyzer;
import org.interpss.plugin.contingency.con_fmt.mapper.ConToIpssMapper;
import org.interpss.plugin.contingency.con_fmt.parser.ConFileParser;
import org.interpss.plugin.contingency.result.AclfContingencyResultRec;

var base = new PSSEDirectParser(35).parse("network.raw");
var profile = AclfRunConfigRec.loadAclfRunConfig("aclf.json");
var baseSolver = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(base);
profile.applyTo(baseSolver);
if (!baseSolver.loadflow()) {
    throw new IllegalStateException("Base load flow stopped with "
            + baseSolver.getTerminationReport().status());
}

var definitions = new ConFileParser().parse(Path.of("outages.con"));
var outages = new ConToIpssMapper(base).map(definitions);
var options = new AclfContingencyConfig();
options.setLoadflowProfile(profile);

boolean useParallel = false;
boolean adaptiveRecovery = true;
var analyzer = new ParallelAclfContingencyAnalyzer<AclfContingencyResultRec>(base);
var batch = analyzer.analyzeOutages(outages, options, useParallel, adaptiveRecovery);
batch.getCAResults().forEach((id, result) ->
        System.out.printf("%s %s %s%n", id, result.getTerminationReport().status(),
                result.reportStatus()));
```

### Control settings and precedence

1. Core initializes the solver from the network and its imported settings.
2. Fields explicitly present in the shared JSON override those settings.
   Optional control families are subject to the effective settings and
   each device's eligibility and limits.
3. Explicit `AclfContingencyConfig` setters take precedence for outages.
   For example, `setTolerance(0.003)` uses per unit and affects only the
   outage attempts. `setApplyAdjustAlgo(false)` holds adjustments for
   the outage study.

The analyzer applies the shared profile to each fresh solve or recovery
attempt. `setLoadflowProfile` copies the profile when called. Pass
`adaptiveRecovery=true` to enable Core's locked seed and selective recovery
where the outage and control state qualify.

Under the shared RAW policy for polar Newton, eligible continuous SVCs
and generator remote-voltage controls can participate inside Newton.
Transformer taps and switched shunts use outer adjustments by default.
Recovery can selectively admit continuous shunts. Full-Newton tap control
is an explicit profile option.

### Reading results

Use `getTerminationReport()` for the original solver status, residuals,
and unsettled controls, and `getDiagnostics()` on the batch for outage
application and recovery details. A `CONTROLS_UNSETTLED` result satisfies
the electrical equations and still has enabled control targets outside their
criteria. Studies that require full settlement can check
`getTerminationReport().controlsSettled()` after electrical convergence.

`reportStatus()` applies the contingency report criteria. An eligible
iteration-limit or non-divergent stop can be `TREATED_AS_CONVERGED` for
reporting if the auxiliary equations and residual thresholds pass. The
default residual thresholds are 0.5 MW or Mvar at the worst bus and
5 MVA for the sum of bus apparent mismatches. The original termination
and residuals remain available for review.
