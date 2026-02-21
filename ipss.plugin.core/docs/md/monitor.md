# PSS/E Monitored Element Support

Package: `org.interpss.plugin.monitor`  
Module: `ipss.plugin.core`

---

## Overview

The monitor package parses PSS/E **Monitored Element (`.mon`)** files and resolves the declared directives against a live `AclfNetwork`, producing concrete sets of network branch and bus IDs to monitor.

A `.mon` file specifies:

- Which branches/breakers (internal to a subsystem) and tie branches (crossing subsystem boundaries) are under thermal monitoring.
- Which buses or entire subsystems are under voltage monitoring.
- Named **interfaces** — user-defined groups of specific branches monitored as a single transfer corridor.

Subsystem membership is determined by delegating to `SubsystemFilter` from the `org.interpss.plugin.subsystem` package (see [psse-subsystem.md](psse-subsystem.md)).

---

## Package Contents

| Class | Role |
|---|---|
| `MonBranchEntry` | A single branch record or used inside a `MONITOR INTERFACE` block (from/to bus + circuit) |
| `MonFlowDirective` | A `BRANCHES`, `BREAKERS`, or `TIES` directive referencing a subsystem by name |
| `MonVoltageDirective` | A voltage-range directive: system-scope or single-bus scope |
| `MonInterface` | A `MONITOR INTERFACE … END` block: ID, MW rating, and list of `MonBranchEntry` |
| `MonElementContainer` | Top-level parsed container for a `.mon` file |
| `MonitoredElements` | Resolved result: thermal/tie branch IDs, voltage bus IDs, interface branch ID map |
| `MonFileParser` | Regex-based, line-by-line parser for `.mon` files |
| `MonFileConverter` | CLI utility: parses a `.mon` file and writes pretty-printed JSON (Gson) |
| `MonElementHelper` | Main API: resolves a `MonElementContainer` against `AclfNetwork` |

---

## Data Model

### MonBranchEntry

Represents one branch line for monitoring or used inside a `MONITOR INTERFACE` block.

```
fromBusNum : int     // PSS/E bus number of the from-terminal
toBusNum   : int     // PSS/E bus number of the to-terminal
ckt        : String  // circuit ID (e.g. "1", "2", "BK")
comment    : String  // optional inline comment (text after '/')
```

JSON example:
```json
{ "fromBusNum": 524, "toBusNum": 523, "ckt": "1", "comment": "BUS524 TO BUS523" }
```

### MonFlowDirective

Identifies a system-level monitoring directive.

```
type   : String   // "BRANCHES", "BREAKERS", or "TIES"
system : String   // subsystem label (must exist in the .sub file)
```

JSON examples:
```json
{ "type": "BRANCHES", "system": "Internal"  }
{ "type": "BREAKERS", "system": "Internal"  }
{ "type": "TIES",     "system": "External"  }
```

### MonVoltageDirective

Specifies a voltage-range monitoring rule.

```
scope  : String   // "SYSTEM" or "BUS"
system : String   // subsystem label (non-null when scope = SYSTEM)
busNum : Integer  // PSS/E bus number (non-null when scope = BUS)
vMin   : double   // lower voltage limit (pu)
vMax   : double   // upper voltage limit (pu)
```

JSON examples:
```json
{ "scope": "SYSTEM", "system": "Internal", "vMin": 0.95, "vMax": 1.05 }
{ "scope": "BUS",    "busNum": 532797,     "vMin": 0.99, "vMax": 1.03 }
```

Factory methods:
- `MonVoltageDirective.forSystem(system, vMin, vMax)`
- `MonVoltageDirective.forBus(busNum, vMin, vMax)`

### MonInterface

A named interface corridor with an MW rating and a list of constituent branches.

```
id        : String               // interface label (e.g. "NW_IMPORT")
ratingMW  : double               // thermal rating in MW
branches  : List<MonBranchEntry> // branches that form this corridor
```

JSON example:
```json
{
  "id": "xxx",
  "ratingMW": 1645.0,
  "branches": [
    { "fromBusNum": 524, "toBusNum": 523, "ckt": "1" },
    { "fromBusNum": 520, "toBusNum": 525, "ckt": "1" }
  ]
}
```

### MonElementContainer

Top-level container produced by `MonFileParser`.

```
sourceFile                    : String                    // absolute path of the parsed .mon file
monitoredFlowDirectives       : List<MonFlowDirective>    // BRANCHES/BREAKERS/TIES directives
monitoredBusVoltageDirectives : List<MonVoltageDirective> // voltage directives
interfaces                    : List<MonInterface>        // MONITOR INTERFACE blocks
```

### MonitoredElements

Resolved output from `MonElementHelper.resolve()`.

```
thermalBranchIds  : Set<String>               // internal branch IDs (both terminals in monitored system)
tieBranchIds      : Set<String>               // tie branch IDs (one terminal in, one out)
voltageBusIds     : Set<String>               // bus IDs for voltage monitoring
interfaceBranchIds: Map<String, Set<String>>  // interface ID → resolved network branch ID set
```

---

## `.mon` File Format

Recognised line patterns (case-insensitive). Lines starting with `/` are treated as comments.

### Flow monitoring (subsystem-based)
```
monitor branches in system <subsystem_name>
monitor breakers in system <subsystem_name>
monitor ties    from system <subsystem_name>
```

### Voltage monitoring
```
monitor voltage range system <subsystem_name> <vMin_pu> <vMax_pu>
monitor voltage range bus    <bus_number>     <vMin_pu> <vMax_pu>
```

### Interface blocks
```
MONITOR INTERFACE '<interface_id>' RATING <mw> MW
MONITOR BRANCH FROM BUS <from_num> TO BUS <to_num> CKT <circuit_id>
...
END
```

Inline comments (text after `/`) on `MONITOR BRANCH` lines are captured in `MonBranchEntry.comment` and typically contain bus names for human readability.

### Minimal example
```
/ ---- Flow monitoring ----
monitor branches in system Internal
monitor ties    from system Internal

/ ---- Voltage monitoring ----
monitor voltage range system Internal    0.95 1.05
monitor voltage range bus    537      0.99 1.03

/ ---- Interface ----
MONITOR INTERFACE 'xxx' RATING 1645.0 MW
MONITOR BRANCH FROM BUS 524 TO BUS 523 CKT 1 
MONITOR BRANCH FROM BUS 520 TO BUS 525 CKT 1
END
```

---

## API: `MonFileConverter` (CLI)

Parses a `.mon` file and writes pretty-printed JSON to disk.

```bash
mvn -pl ipss.plugin.core compile exec:java \
    -Dexec.mainClass="org.interpss.plugin.monitor.MonFileConverter" \
    -Dexec.args="input.mon output.json"
```

If only one argument is provided the output path defaults to the input path with `.mon` replaced by `.json`.

---

## API: `MonElementHelper`

The main resolution class. Requires a `SubsystemFilter` (for subsystem-to-bus-ID lookup) and the target `AclfNetwork`.

### Construction

```java
SubsystemFilter subFilter = SubsystemFilter.parse(Paths.get("sub_input.sub"));
// -- or from a pre-converted JSON --
SubsystemFilter subFilter = SubsystemFilter.load(Paths.get("sub_input.json"));

MonElementHelper helper = new MonElementHelper(subFilter, net);
```

### Resolving a `.mon` file

```java
MonElementContainer mon = new MonFileParser().parse(Paths.get("mon_input.mon"));
MonitoredElements result = helper.resolve(mon);

Set<String>              thermalBranches = result.getThermalBranchIds();
Set<String>              tieBranches     = result.getTieBranchIds();
Set<String>              voltageBuses    = result.getVoltageBusIds();
Map<String, Set<String>> ifaces          = result.getInterfaceBranchIds();
```

### Chaining from existing containers

```java
// If MonElementContainer was deserialized from JSON (Gson)
Gson gson = new Gson();
MonElementContainer mon = gson.fromJson(
        Files.readString(Paths.get("mon_input.json")), MonElementContainer.class);
MonitoredElements result = helper.resolve(mon);
```

---

## Resolution Rules

### 1. BRANCHES / BREAKERS directive

A branch is added to `thermalBranchIds` when **both** terminals belong to the named subsystem (internal branch).

```
fromBus ∈ sysIds  AND  toBus ∈ sysIds  →  thermalBranchIds
```

### 2. TIES directive

A branch is added to `tieBranchIds` when **exactly one** terminal belongs to the named subsystem (XOR condition).

```
fromBus ∈ sysIds  XOR  toBus ∈ sysIds  →  tieBranchIds
```

### 3. Voltage directive (SYSTEM scope)

All active buses returned by `SubsystemFilter.getBusIds(net, systemLabel)` are added to `voltageBusIds`.

### 4. Voltage directive (BUS scope)

A single active bus whose PSS/E bus number matches `busNum` is added to `voltageBusIds`.

### 5. Interface branches

For each `MonBranchEntry(fromBusNum, toBusNum, ckt)` in an interface block, a linear scan of `AclfNetwork.getBranchList()` finds the matching branch by bus number pair (both orientations checked) and circuit ID. The resolved network branch ID is stored in `interfaceBranchIds` under the interface label. Unresolved entries emit a SLF4J `WARN`.

---

## MonFileParser: Line Processing

The parser maintains a **single state variable** (`currentIface`) to track whether it is inside an open `MONITOR INTERFACE … END` block.

| Line pattern | Action |
|---|---|
| Starts with `/` | Skipped (comment) |
| `END` | Closes `currentIface`, adds it to the list |
| `MONITOR BRANCH …` | Adds `MonBranchEntry` to `currentIface` (ignored if no open block) |
| `MONITOR INTERFACE …` | Opens new `MonInterface`; closes previous one if still open (safety) |
| `MONITOR VOLTAGE RANGE BUS …` | Appends `MonVoltageDirective` (BUS scope) |
| `MONITOR VOLTAGE RANGE SYSTEM …` | Appends `MonVoltageDirective` (SYSTEM scope) |
| `MONITOR BRANCHES/BREAKERS IN SYSTEM …` | Appends `MonFlowDirective` |
| `MONITOR TIES FROM SYSTEM …` | Appends `MonFlowDirective` (type = TIES) |
| anything else | Silently skipped |

Text after the first `/` on a data line is stripped as an inline comment before pattern matching. The captured comment is preserved in `MonBranchEntry.comment`.

---

## Dependencies

| Library | Scope | Purpose |
|---|---|---|
| `org.interpss.plugin.subsystem` | compile | `SubsystemFilter` for bus-set resolution |
| `com.google.gson:gson:2.13.1` | compile | JSON serialisation (`MonFileConverter`) |
| `org.slf4j:slf4j-api` | compile | Logging (warnings for unresolved branches/subsystems) |
| `com.interpss:ipss.core` | compile | `AclfNetwork`, `AclfBranch`, `AclfBus` |
