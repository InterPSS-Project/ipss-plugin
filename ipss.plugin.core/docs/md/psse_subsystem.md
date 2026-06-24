# PSS/E Subsystem Definition Support

Package: `org.interpss.plugin.subsystem`  
Module: `ipss.plugin.core`

---

## Overview

The subsystem package parses PSS/E **Subsystem Definition (`.sub`)** files and resolves named subsystems to sets of bus and branch IDs in a live `AclfNetwork`. This is a prerequisite for resolving monitored-element directives defined in `.mon` files (see [psse-monitor.md](psse-monitor.md)).

A PSS/E `.sub` file defines named groups of buses selected by area, zone, owner, bus number, or base-kV. The resolved bus sets are used to classify network branches as *internal* (both terminals in a subsystem) or *ties* (exactly one terminal in).

---

## Package Contents

| Class | Role |
|---|---|
| `SelectionGroup` | Flat list of selection criteria: areas, zones, owners, buses, kV values/range |
| `JoinGroup` | A `JOIN … END` block — label + one `SelectionGroup` (criteria are AND-ed) |
| `Subsystem` | A named `SYSTEM/SUBSYSTEM` block with JOIN groups, direct selection, and skip-buses |
| `SubsystemContainer` | Top-level parsed container (Java record): source file path + list of `Subsystem` |
| `SubFileParser` | Regex-free, line-by-line parser for `.sub` files |
| `SubFileConverter` | CLI utility: parses a `.sub` file and writes pretty-printed JSON (Gson) |
| `SubsystemFilter` | Main API: resolves subsystem names to bus/branch ID sets against `AclfNetwork` |

---

## Data Model

### SelectionGroup

Holds all selection criteria for one matching context (either a `JOIN` block or the direct body of a `SYSTEM` block). All populated criteria must be satisfied simultaneously (AND logic).

```
areas   : List<Integer>   // area numbers
zones   : List<Integer>   // zone numbers
owners  : List<Integer>   // owner numbers
buses   : List<Integer>   // explicit bus numbers
kvs     : List<Double>    // explicit single base-kV values
kvMin   : Double          // base-kV lower bound (null = no range filter)
kvMax   : Double          // base-kV upper bound
```

Ranges in the `.sub` file (e.g. `AREAS 1 5`) are expanded into individual entries at parse time.

### JoinGroup

```
label     : String          // optional label from JOIN 'label', may be null
selection : SelectionGroup  // criteria inside the JOIN ... END block
```

### Subsystem

```
label           : String           // from SYSTEM 'label'
type            : String           // "SYSTEM" or "SUBSYSTEM"
joinGroups      : List<JoinGroup>  // JOIN ... END blocks (OR-ed)
directSelection : SelectionGroup   // criteria outside any JOIN (OR path)
skipBuses       : List<Integer>    // explicitly excluded bus numbers
```

A bus matches the subsystem if it satisfies **any** JOIN group OR the direct selection, and is **not** in `skipBuses`.

### SubsystemContainer (record)

```
sourceFile  : String           // absolute path of the parsed .sub file
subsystems  : List<Subsystem>  // all SYSTEM/SUBSYSTEM definitions in order
```

---

## PSS/E `.sub` File Format

Supported constructs (case-insensitive, `/` starts an inline comment):

```
SYSTEM 'Internal'           / or SUBSYSTEM 'label'
    AREA 40
    AREAS 50 55            / expands to 50, 52, ..., 55
    ZONE 12
    ZONES 10 15  
    OWNER 7
    OWNERS 1 5
    BUS 1001
    BUSES 1003 1020
    KV 345.0                 / explicit base-kV match
    KVRANGE 100 500          / base-kV in [100, 500]
    SKIP BUS 1019

    JOIN
        AREA 52
        KVRANGE 65 999
    END
END
```

Multiple `SYSTEM` blocks in one file are all parsed and stored.

---

## JSON Representation

`SubFileConverter` serialises the parsed data to JSON using Gson. Example:

```json
{
  "sourceFile": "/data/sub_input.sub",
  "subsystems": [
    {
      "label": "Internal",
      "type": "SYSTEM",
      "joinGroups": [
        {
          "label": null,
          "selection": {
            "areas": [515],
            "zones": [],
            "owners": [],
            "buses": [],
            "kvs": [],
            "kvMin": 65.0,
            "kvMax": 999.0
          }
        }
      ],
      "directSelection": {
        "areas": [51],
        "zones": [],
        "owners": [],
        "buses": [],
        "kvs": [],
        "kvMin": null,
        "kvMax": null
      },
      "skipBuses": [1001]
    }
  ]
}
```

---

## SubFileConverter (CLI)

Converts a `.sub` file to JSON. Output is written alongside the input file (same name, `.json` extension) unless an explicit output path is given.

```bash
# From the ipss.plugin.core module directory
mvn exec:java \
  -Dexec.mainClass="org.interpss.plugin.subsystem.SubFileConverter" \
  -Dexec.args="input.sub output.json"
```

---

## SubsystemFilter API

### Construction

```java
// Option 1: parse a .sub file directly
SubsystemFilter filter = SubsystemFilter.parse(Paths.get("sub_input.sub"));

// Option 2: load a pre-converted JSON file
SubsystemFilter filter = SubsystemFilter.load(Paths.get("sub_input.json"));

// Option 3: wrap an already-parsed container
SubsystemContainer container = new SubFileParser().parse(subPath);
SubsystemFilter filter = new SubsystemFilter(container);
```

### Resolving bus IDs for a single subsystem

```java
Set<String> busIds = filter.getBusIds(net, "Internal");
```

Returns the set of `AclfBus.getId()` strings for all active buses that match the named subsystem.

### Resolving multiple subsystems at once

```java
Set<String> busIds    = new HashSet<>();
Set<String> branchIds = new HashSet<>();
filter.populate(net, List.of("Internal", "A"), busIds, branchIds);
```

`populate` also fills `branchIds`: a branch is included if either of its terminals is in the matched bus set.

### Listing available subsystem names

```java
List<String> labels = filter.getSubsystemLabels();
```

---

## Bus Matching Logic

For each active, non-skipped bus, `SubsystemFilter` checks whether it matches any subsystem in the requested label set:

```
for each selected Subsystem:
    for each JoinGroup in subsystem:
        if busMatchesGroup(bus, joinGroup.selection) → MATCH
    if directSelection is not empty:
        if busMatchesGroup(bus, directSelection) → MATCH

busMatchesGroup(bus, group):
    if group.areas is non-empty → bus.area.number must be in areas
    if group.zones is non-empty → bus.zone.number must be in zones
    if group.buses is non-empty → bus.number must be in buses
    if group.kvMin/kvMax or kvs set → bus.baseVoltage/1000 must satisfy at least one
    → all active filters must pass (AND)
```

---

## Dependencies

| Library | Usage |
|---|---|
| `com.google.gson:gson:2.13.1` | JSON serialisation / deserialisation |
| `org.slf4j:slf4j-api` | Logging |
| `com.interpss.core:AclfNetwork` | Network model resolved at runtime |

No Jackson annotations are used; Gson serialises field names as-is.
