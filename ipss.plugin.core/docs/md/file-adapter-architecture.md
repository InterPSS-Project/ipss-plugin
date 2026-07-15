# `org.interpss.fadapter` — File Adapter Architecture

## Purpose

The `org.interpss.fadapter` package provides a unified framework for importing power system network data from industry-standard file formats and exporting simulation results back to those formats. It bridges external data representations (PSS/E, IEEE CDF, MATPOWER, etc.) with the InterPSS in-memory `AclfNetwork` model.

## Package Layout

```
org.interpss.fadapter
├── IpssCustomAdapter          # Base interface: getName(), getDescription()
├── IpssFileAdapter            # Core adapter interface: load/save + FileFormat/Version enums
├── impl/
│   ├── IpssFileAdapterBase    # Abstract base with ODM-backed load pipeline
│   ├── IpssInternalFormat_in  # InterPSS internal text format → AclfNetwork
│   └── IpssInternalFormat_out # AclfNetwork → InterPSS internal text format
├── IpssInternalFormat         # InterPSS proprietary text format (read + write)
├── PTIFormat                  # PSS/E RAW (v26–v36)
├── IeeeCDFFormat              # IEEE Common Data Format
├── MatpowerFormat             # MATPOWER .m case files
├── UCTEFormat                 # UCTE-DEF (European grid)
├── GEFormat                   # GE PSLF
├── BPAFormat                  # BPA (DStab loadflow + dynamics)
├── PWDFormat                  # PowerWorld Data
├── psse/
│   ├── export/
│   │   ├── PSSEJSonExporter          # Orchestrator: filter + update + write PSSE JSON
│   │   └── psse/
│   │       ├── BasePSSEJSonUpdater   # Abstract base: field-position lookup, bus ID extraction
│   │       ├── PSSEJSonBusUpdater    # Updates bus VM/VA from loadflow results
│   │       ├── PSSEJSonGenUpdater    # Updates generator data
│   │       ├── PSSEJSonLoadUpdater   # Updates load data
│   │       ├── PSSEJSonAclineUpdater # Updates AC line flow data
│   │       ├── PSSEJSonXformerUpdater        # Updates transformer data
│   │       ├── PSSEJSonFixedShuntUpdater     # Updates fixed shunt data
│   │       ├── PSSEJSonSwitchedShuntUpdater  # Updates switched shunt data
│   │       ├── PSSEJSonSwitchingDeviceUpdater# Updates switching device data
│   │       ├── PSSEJSonFactsDeviceUpdater    # Updates FACTS device data
│   │       ├── PSSEJSonDc2TLCCUpdater        # Updates 2-terminal LCC HVDC data
│   │       └── PSSEJSonDc2TVSCUpdater        # Updates 2-terminal VSC HVDC data
│   ├── monitor/
│   │   ├── MonFileParser        # Parses PSS/E .mon files into MonElementContainer
│   │   ├── MonFileConverter     # CLI: .mon → JSON
│   │   ├── MonElementContainer  # Top-level parsed result (flow/voltage directives, interfaces)
│   │   ├── MonFlowDirective     # BRANCHES / BREAKERS / TIES directive
│   │   ├── MonVoltageDirective  # Voltage range directive (SYSTEM or BUS scope)
│   │   ├── MonInterface         # MONITOR INTERFACE block (id, rating, branch list)
│   │   ├── MonBranchEntry       # Single branch in an interface (from/to bus, ckt)
│   │   ├── MonElementHelper     # Resolves MonElementContainer against live AclfNetwork
│   │   └── MonitoredElements    # Resolution result: thermal/tie branch IDs, voltage bus IDs, interface maps
│   └── subsystem/
│       ├── SubFileParser        # Parses PSS/E .sub files into SubsystemContainer
│       ├── SubFileConverter     # CLI: .sub → JSON
│       ├── SubsystemContainer   # Top-level parsed result (record with source file + subsystems list)
│       ├── Subsystem            # SYSTEM/SUBSYSTEM block: label, type, JoinGroups, skip buses
│       ├── JoinGroup            # JOIN block: AND-ed selection criteria
│       ├── SelectionGroup       # Typed criteria: areas, zones, owners, buses, KV ranges
│       └── SubsystemFilter     # Resolves subsystem definitions against live AclfNetwork → bus/branch ID sets
```

## Class Hierarchy

```
IpssCustomAdapter (interface)
  └── IpssFileAdapter (interface)
        └── IpssFileAdapterBase (abstract class)
              ├── PTIFormat          (PSS/E v26–v36)
              ├── IeeeCDFFormat      (IEEE CDF + CDF-Ext1)
              ├── MatpowerFormat     (MATPOWER)
              ├── UCTEFormat         (UCTE-DEF)
              ├── GEFormat           (GE PSLF)
              ├── PWDFormat          (PowerWorld)
              ├── BPAFormat          (BPA, overrides multi-file load for DStab)
              └── IpssInternalFormat (InterPSS internal text, overrides load + save)
```

## Data Flow

### Import Pipeline (ODM-based formats)

Most format adapters follow the same two-stage pipeline through the IEEE Open Data Model (ODM):

```
External File (.raw, .ieee, .m, ...)
       │
       ▼
 ┌─────────────────────────────┐
 │  ODMObjectFactory           │  Creates a format-specific IODMAdapter
 │    .createODMAdapter(format)│  based on ODMFileFormatEnum
 └──────────────┬──────────────┘
                │
                ▼
 ┌─────────────────────────────┐
 │  IODMAdapter                │  Parses the raw file into an
 │    .parseInputFile(filepath)│  AclfModelParser (XML schema)
 └──────────────┬──────────────┘
                │
                ▼
 ┌─────────────────────────────┐
 │  ODMAclfParserMapper        │  Maps the ODM XML model into
 │    .map2Model(parser, ctx)  │  a SimuContext → AclfNetwork
 └──────────────┬──────────────┘
                │
                ▼
         SimuContext / AclfNetwork  (ready for simulation)
```

This pipeline is implemented in `IpssFileAdapterBase.loadByODMTransformation()`. Each concrete adapter simply specifies the `ODMFileFormatEnum` value:

| Adapter Class     | ODMFileFormatEnum   |
|-------------------|---------------------|
| `PTIFormat`       | `PsseV26`–`PsseV36` |
| `IeeeCDFFormat`   | `IeeeCDF`, `IeeeCDFExt1` |
| `MatpowerFormat`  | `MatPower`          |
| `UCTEFormat`      | `UCTE`              |
| `GEFormat`        | `GePSLF`            |
| `PWDFormat`       | `PWD`               |
| `BPAFormat`       | `BPA`               |

### Import Pipeline (Internal Format)

`IpssInternalFormat` bypasses ODM entirely. It reads a line-oriented text file directly into `AclfNetwork` objects via `IpssInternalFormat_in.loadFile()`:

```
InterPSS Text File
       │
       ▼
 IpssInternalFormat_in.loadFile(reader)
       │  Parses: BusInfo, SwingBusInfo, PVBusInfo, PQBusInfo,
       │          CapacitorBusInfo, BranchInfo, XformerInfo
       ▼
 AclfNetwork  (directly populated)
```

### Export Pipeline (PSSE JSON)

`PSSEJSonExporter` writes simulation results back to PSS/E JSON format:

```
AclfNetwork (with loadflow results)  +  PSSESchema (original JSON)
       │
       ▼
 PSSEJSonExporter.filterAndUpdate(busIdSet)
       │
       ├─→ PSSEJSonBusUpdater.filter() + update()     — VM, VA
       ├─→ PSSEJSonGenUpdater.filter() + update()     — generator output
       ├─→ PSSEJSonLoadUpdater.filter() + update()    — load values
       ├─→ PSSEJSonAclineUpdater.filter() + update()  — line flows
       ├─→ PSSEJSonXformerUpdater.filter() + update()
       ├─→ PSSEJSonSwitchedShuntUpdater.filter() + update()
       ├─→ PSSEJSonFixedShuntUpdater.filter() + update()
       ├─→ PSSEJSonFactsDeviceUpdater.filter() + update()
       ├─→ PSSEJSonSwitchingDeviceUpdater.filter() + update()
       ├─→ PSSEJSonDc2TLCCUpdater.filter() + update()
       └─→ PSSEJSonDc2TVSCUpdater.filter() + update()
       │
       ▼
 PSSEJSonExporter.export(filename)  →  JSON file on disk
```

Each updater extends `BasePSSEJSonUpdater`, which provides:
- A `positionTable` (`LinkedHashMap<String, Integer>`) mapping PSSE field names to column positions
- `getBusIdFromDataList()` to extract bus IDs from the tabular JSON data
- Subclasses implement `filter(Set<String> busIdSet)` to remove out-of-scope records and `update()` to overwrite values from the simulation network

## PSS/E Auxiliary File Support

### Subsystem Definitions (`.sub` files)

The `psse.subsystem` sub-package parses PSS/E subsystem definition files that define groups of buses by area, zone, owner, bus number, and KV range.

**Parse → Filter flow:**

```
.sub file  →  SubFileParser.parse()  →  SubsystemContainer
                                              │
                                              ▼
                                        SubsystemFilter
                                              │
                               .getBusIds(net, "label")
                                              │
                                              ▼
                                        Set<String> busIds
```

**Data model:**
- `SubsystemContainer` — top-level record: source file path + list of `Subsystem`
- `Subsystem` — a SYSTEM/SUBSYSTEM block containing `JoinGroup`s, a `directSelection`, and skip buses
- `JoinGroup` — a JOIN block whose criteria are AND-ed (area + zone + KV range = intersection)
- `SelectionGroup` — typed criteria lists: areas, zones, owners, buses, KV values/ranges
- `SubsystemFilter` — resolves definitions against a live `AclfNetwork`: JOIN groups are OR-ed within a subsystem; skip buses are excluded after matching

### Monitored Elements (`.mon` files)

The `psse.monitor` sub-package parses PSS/E monitored element files that define which branches and buses to monitor for thermal/voltage violations and transmission interfaces.

**Parse → Resolve flow:**

```
.mon file  →  MonFileParser.parse()  →  MonElementContainer
                                              │
                                              ▼
                              MonElementHelper(subFilter, net)
                                     .resolve(mon)
                                              │
                                              ▼
                                       MonitoredElements
                                         ├── thermalBranchIds
                                         ├── tieBranchIds
                                         ├── voltageBusIds
                                         └── interfaceBranchIds (Map<interfaceId, Set<branchId>>)
```

**Directive types:**
- `MonFlowDirective` — `MONITOR BRANCHES/BREAKERS IN SYSTEM <name>` or `MONITOR TIES FROM SYSTEM <name>`
- `MonVoltageDirective` — `MONITOR VOLTAGE RANGE SYSTEM <name> <vMin> <vMax>` or `MONITOR VOLTAGE RANGE BUS <num> <vMin> <vMax>`
- `MonInterface` — `MONITOR INTERFACE '<name>' RATING <mw> MW` with a list of `MonBranchEntry` records

**Resolution rules (`MonElementHelper`):**
- BRANCHES/BREAKERS: both terminals must belong to the named subsystem (internal branches)
- TIES: exactly one terminal belongs to the named subsystem (cross-boundary branches via XOR)
- Voltage SYSTEM: all active buses in the subsystem
- Voltage BUS: the single bus matching the bus number
- Interface branches: resolved by from/to bus number + circuit ID (both orientations checked)

## Factory / Entry Points

### `CorePluginFactory.getFileAdapter(FileFormat, Version)`

The primary entry point for creating adapter instances. Maps `FileFormat` enum values to concrete adapter classes:

```java
AclfNetwork net = CorePluginFactory
    .getFileAdapter(IpssFileAdapter.FileFormat.PSSE, IpssFileAdapter.Version.PSSE_30)
    .load("path/to/file.raw")
    .getAclfNet();
```

### Direct Instantiation

Format adapters can also be constructed directly:

```java
PTIFormat adapter = new PTIFormat(IpssFileAdapter.Version.PSSE_35, msgHub);
SimuContext ctx = adapter.load("path/to/file.raw");
```

### CLI Converters

Both `SubFileConverter` and `MonFileConverter` provide `main()` entry points for command-line conversion to JSON:

```bash
# Convert .sub to JSON
java org.interpss.fadapter.psse.subsystem.SubFileConverter input.sub output.json

# Convert .mon to JSON
java org.interpss.fadapter.psse.monitor.MonFileConverter input.mon output.json
```

## Supported File Formats

| Format | FileFormat Enum | Adapter Class | Notes |
|--------|----------------|---------------|-------|
| PSS/E RAW | `PSSE` | `PTIFormat` | Versions 26–36 via `Version` enum |
| IEEE CDF | `IEEECDF` | `IeeeCDFFormat` | Standard + Extended-1; post-load cleanup of empty gen/load |
| MATPOWER | `MATPOWER` | `MatpowerFormat` | `.m` case files |
| UCTE-DEF | `UCTE` | `UCTEFormat` | European grid exchange format |
| GE PSLF | `GE_PSLF` | `GEFormat` | GE Positive Sequence Load Flow |
| BPA | `BPA` | `BPAFormat` | Multi-file DStab load: LF + dynamics via `load(ctx, filepathAry, ...)` |
| PowerWorld | `PWD` | `PWDFormat` | PowerWorld Data format |
| InterPSS Internal | `IpssInternal` | `IpssInternalFormat` | Line-oriented text; supports both read and write |
| PSS/E JSON | — | `PSSEJSonExporter` | Export-only: filter + update PSSE JSON with simulation results |
| PSS/E .sub | — | `SubFileParser` | Parse-only: subsystem definitions → bus/branch filtering |
| PSS/E .mon | — | `MonFileParser` | Parse-only: monitored elements → thermal/voltage/interface sets |
