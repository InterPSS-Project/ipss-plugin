# `org.interpss.fadapter` — File Adapter Architecture

## Purpose

The `org.interpss.fadapter` package provides a unified framework for importing power system network data from industry-standard file formats and exporting simulation results back to those formats. It bridges external data representations (PSS/E, IEEE CDF, MATPOWER, etc.) with InterPSS in-memory models (`AclfNetwork`, `AcscNetwork`, `DStabilityNetwork`).

**Design note:** All import paths use *direct file-to-model* parsers. The IEEE ODM (Open Data Model) XML intermediate layer has been removed. Format adapters call parsers that populate network objects via shared builders—no JAXB schema, no ODM mappers.

## Package Layout

```
org.interpss.fadapter
├── IpssCustomAdapter              # Base interface: getName(), getDescription()
├── IpssFileAdapter                # Core adapter interface: load/save + FileFormat/Version enums
├── impl/
│   ├── IpssFileAdapterBase        # Abstract base: metadata + load() template (subclass implements)
│   ├── IpssInternalFormat_in      # InterPSS internal text → AclfNetwork
│   └── IpssInternalFormat_out     # AclfNetwork → InterPSS internal text
├── IpssInternalFormat             # InterPSS proprietary text format (read + write)
├── PTIFormat                      # PSS/E RAW facade → PSSEDirectParser
├── IeeeCDFFormat                  # IEEE CDF facade → IeeeCDFDirectParser
├── MatpowerFormat                 # MATPOWER facade → MatpowerDirectParser
├── UCTEFormat                     # UCTE-DEF facade → UCTEDirectParser
├── GEFormat                       # GE PSLF facade → GEPslfDirectParser
├── BPAFormat                      # BPA facade → BPADirectParser
├── PWDFormat                      # PowerWorld facade → PWDDirectParser
├── builder/
│   ├── AclfNetworkBuilder         # Shared ACLF object construction (buses, branches, HVDC, FACTS, …)
│   ├── AcscNetworkBuilder         # Sequence / short-circuit data construction
│   └── DStabNetworkBuilder        # Dynamic machines, exciters, governors
├── psse/
│   ├── PSSEDirectParser           # PSS/E RAW (v26–v36) → AclfNetwork via AclfNetworkBuilder
│   ├── PSSEJsonDirectParser       # PSS/E RAWX/JSON → AclfNetwork
│   ├── PSSEAcscDirectParser       # PSS/E sequence (.seq) → AcscNetworkBuilder
│   ├── PSSEDStabDirectParser      # PSS/E dynamics (.dyr) → DStabNetworkBuilder
│   ├── PSSEMultiFileLoader        # Convenience: LF + seq + dyn multi-file load
│   ├── PSSEDataRec                # Tokenized RAW record helper
│   ├── bean/
│   │   └── PSSESchema             # Gson beans for PSSE JSON export (copied from former ODM)
│   ├── export/
│   │   ├── PSSEJSonExporter       # Orchestrator: filter + update + write PSSE JSON
│   │   └── psse/
│   │       ├── BasePSSEJSonUpdater
│   │       ├── PSSEJSonBusUpdater / Gen / Load / Acline / Xformer / …
│   │       ├── PSSEJSonDc2TLCCUpdater / Dc2TVSCUpdater
│   │       └── …
│   ├── monitor/                   # PSS/E .mon file parse + resolve
│   └── subsystem/                 # PSS/E .sub file parse + filter
├── ieeecdf/
│   └── IeeeCDFDirectParser
├── matpower/
│   └── MatpowerDirectParser
├── ucte/
│   └── UCTEDirectParser
├── ge/
│   └── GEPslfDirectParser
├── bpa/
│   └── BPADirectParser
└── pwd/
    └── PWDDirectParser
```

## Class Hierarchy

```
IpssCustomAdapter (interface)
  └── IpssFileAdapter (interface)
        └── IpssFileAdapterBase (abstract class)
              ├── PTIFormat          → PSSEDirectParser
              ├── IeeeCDFFormat      → IeeeCDFDirectParser
              ├── MatpowerFormat     → MatpowerDirectParser
              ├── UCTEFormat         → UCTEDirectParser
              ├── GEFormat           → GEPslfDirectParser
              ├── PWDFormat          → PWDDirectParser
              ├── BPAFormat          → BPADirectParser
              └── IpssInternalFormat → IpssInternalFormat_in / _out
```

Format facade classes are thin: they construct a direct parser, call `parse()`, and wrap the result in `SimuContext`. `IpssFileAdapterBase.load()` throws unless overridden—there is no shared ODM load path anymore.

`getODMModelParser()` remains on `IpssFileAdapter` as a **deprecated** default that always returns `null`.

## Architecture Overview

```
                    ┌─────────────────────────┐
                    │  Entry points           │
                    │  IpssAdapter (DSL)      │
                    │  CorePluginFactory      │
                    │  Format facades         │
                    │  PSSEMultiFileLoader    │
                    └───────────┬─────────────┘
                                │
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                 ▼
     DirectParsers        Multi-file          Export
     (format-specific)    (PSSE LF+seq+dyn)   (PSSE JSON)
              │                 │                 │
              ▼                 ▼                 ▼
     NetworkBuilders     Acsc/DStab builders  PSSESchema +
     (Aclf / Acsc /      applied in order     Updaters
      DStab)
              │                 │
              └────────┬────────┘
                       ▼
              AclfNetwork / AcscNetwork /
              DStabilityNetwork (+ SimuContext)
```

## Data Flow

### Import Pipeline (Direct Parsers — all industry formats)

```
External File (.raw, .rawx, .ieee, .m, …)
       │
       ▼
 ┌─────────────────────────────┐
 │  Format DirectParser        │  Tokenizes / reads native format
 │  (e.g. PSSEDirectParser)    │  No ODM XML intermediate
 └──────────────┬──────────────┘
                │
                ▼
 ┌─────────────────────────────┐
 │  Network Builder            │  Creates InterPSS objects with
 │  AclfNetworkBuilder         │  primitive/typed parameters only
 │  (Acsc / DStab as needed)   │
 └──────────────┬──────────────┘
                │
                ▼
         AclfNetwork / SimuContext  (ready for simulation)
```

| Format facade     | Direct parser            |
|-------------------|--------------------------|
| `PTIFormat`       | `PSSEDirectParser`       |
| — (DSL only)      | `PSSEJsonDirectParser`   |
| `IeeeCDFFormat`   | `IeeeCDFDirectParser`    |
| `MatpowerFormat`  | `MatpowerDirectParser`   |
| `UCTEFormat`      | `UCTEDirectParser`       |
| `GEFormat`        | `GEPslfDirectParser`     |
| `PWDFormat`       | `PWDDirectParser`        |
| `BPAFormat`       | `BPADirectParser`        |

### Import Pipeline (Internal Format)

`IpssInternalFormat` bypasses the shared builders. It reads a line-oriented text file via `IpssInternalFormat_in.loadFile()`:

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

### Multi-File PSS/E Load (ACSC / DStab)

`PSSEMultiFileLoader` replaces the former ODM multi-file path:

```
LF (.raw)  →  PSSEDirectParser.parseInto(AcscNet | DStabNet)
                 │
                 ├─ optional .seq  →  PSSEAcscDirectParser + AcscNetworkBuilder
                 └─ optional .dyr  →  PSSEDStabDirectParser + DStabNetworkBuilder
                 │
                 ▼
         AcscNetwork  or  SimuContext(DStabilityNetwork + DynamicSimuAlgorithm)
```

Typical calls:

```java
PSSEMultiFileLoader loader = new PSSEMultiFileLoader(33);

// Short-circuit: LF only, or LF + sequence
AcscNetwork acsc = loader.loadAcsc("case.raw", "case.seq");

// Dynamics: [LF], [LF, .dyr], or [LF, .seq, .dyr]
SimuContext ctx = loader.loadDStab("case.raw", "case.seq", "case.dyr");
```

### Export Pipeline (PSSE JSON)

`PSSEJSonExporter` writes simulation results back into a `PSSESchema` object (Gson beans live under `org.interpss.fadapter.psse.bean`, not ieee-odm):

```
AclfNetwork (with loadflow results)  +  PSSESchema (original JSON)
       │
       ▼
 PSSEJSonExporter.filterAndUpdate(busIdSet)
       │
       ├─→ PSSEJSonBusUpdater.filter() + update()     — VM, VA
       ├─→ PSSEJSonGenUpdater / Load / Acline / Xformer / …
       ├─→ PSSEJSonDc2TLCCUpdater / Dc2TVSCUpdater
       └─→ …
       │
       ▼
 PSSEJSonExporter.export(filename)  →  JSON file on disk
```

Each updater extends `BasePSSEJSonUpdater`, which provides:
- A `positionTable` (`LinkedHashMap<String, Integer>`) mapping PSSE field names to column positions
- `getBusIdFromDataList()` using local prefix `"Bus"` (no ODM `IODMModelParser` dependency)
- Subclasses implement `filter(Set<String> busIdSet)` and `update()` from the live network

## Network Builders

Builders isolate model-construction logic from format parsing. Parsers only tokenize and call builder methods with primitives/`Complex`/enums.

### `AclfNetworkBuilder`

Shared ACLF construction used by all direct parsers (and by PSS/E when parsing into an existing `AcscNetwork` / `DStabilityNetwork`):

- Network metadata, areas, zones, owners, Xfr Z-table
- Buses (swing / PV / PQ), contribute gens & loads, shunt Y
- Switched shunts, SVC
- Lines, breakers, 2W/3W transformers, PS transformers + tap/angle controls
- LCC / VSC HVDC, FACTS, switching devices
- Flow interfaces
- `finalizeNetwork()`

### `AcscNetworkBuilder`

Sequence (short-circuit) overlays on an `AcscNetwork` / `BaseAcscNetwork`:

- Generator positive / negative / zero sequence Z
- Load sequence data
- Branch and transformer zero-sequence / grounding
- `finalizeAcscNetwork()`

### `DStabNetworkBuilder`

Dynamic models attached to a `DStabilityNetwork`:

- Machines: GENCLS, GENROU/GENROE, GENSAL/GENSAE, GENTPF/GENTPJ, …
- Exciters: IEEET1, IEEEX1, EXST1, EXAC1, … (some types fall back to IEEET1)
- Governors: IEEEG1, TGOV1, GAST, IEESGO, …

## PSS/E Auxiliary File Support

### Subsystem Definitions (`.sub` files)

The `psse.subsystem` sub-package parses PSS/E subsystem definition files that group buses by area, zone, owner, bus number, and KV range.

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

- `SubsystemContainer` — source file path + list of `Subsystem`
- `Subsystem` — SYSTEM/SUBSYSTEM block: `JoinGroup`s, `directSelection`, skip buses
- `JoinGroup` — JOIN criteria AND-ed (area ∩ zone ∩ KV, …)
- `SelectionGroup` — typed criteria lists
- `SubsystemFilter` — JOIN groups OR-ed within a subsystem; skip buses excluded after match

### Monitored Elements (`.mon` files)

The `psse.monitor` sub-package parses monitored-element files for thermal/voltage monitoring and interfaces.

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
                                         └── interfaceBranchIds
```

**Resolution rules (`MonElementHelper`):**
- BRANCHES/BREAKERS — both terminals in the named subsystem
- TIES — exactly one terminal in the subsystem (XOR)
- Voltage SYSTEM — all active buses in the subsystem
- Voltage BUS — single bus by number
- Interface branches — from/to bus number + circuit ID (both orientations)

## Factory / Entry Points

### `IpssAdapter` (preferred DSL)

```java
AclfNetwork net = IpssAdapter.importAclfNet("case.raw")
    .setFormat(IpssAdapter.FileFormat.PSSE)
    .setPsseVersion(IpssAdapter.PsseVersion.PSSE_33)  // or PSSE_JSON for .rawx
    .load()
    .getImportedObj();
```

`IpssAdapter.parsePsseVersion(filename)` reads the REV field from the first RAW line. `FileFormat.IEEE_ODM` is deprecated and throws if selected.

### `CorePluginFactory.getFileAdapter(FileFormat, Version)`

```java
AclfNetwork net = CorePluginFactory
    .getFileAdapter(IpssFileAdapter.FileFormat.PSSE, IpssFileAdapter.Version.PSSE_30)
    .load("path/to/file.raw")
    .getAclfNet();
```

ODM mapper factory methods have been removed from `CorePluginFactory`.

### Direct Instantiation

```java
AclfNetwork net = new PSSEDirectParser(35).parse("case.raw");
AclfNetwork jsonNet = new PSSEJsonDirectParser().parse("case.rawx");
```

### CLI Converters

```bash
java org.interpss.fadapter.psse.subsystem.SubFileConverter input.sub output.json
java org.interpss.fadapter.psse.monitor.MonFileConverter input.mon output.json
```

## Supported File Formats

| Format | FileFormat Enum | Facade / Entry | Direct parser | Notes |
|--------|-----------------|----------------|---------------|-------|
| PSS/E RAW | `PSSE` | `PTIFormat`, `IpssAdapter` | `PSSEDirectParser` | Versions 26–36 |
| PSS/E RAWX/JSON | — | `IpssAdapter` (`PSSE_JSON`) | `PSSEJsonDirectParser` | Import via DSL |
| IEEE CDF | `IEEECDF` | `IeeeCDFFormat` | `IeeeCDFDirectParser` | Standard + Ext1 via Version |
| MATPOWER | `MATPOWER` | `MatpowerFormat` | `MatpowerDirectParser` | `.m` case files |
| UCTE-DEF | `UCTE` | `UCTEFormat` | `UCTEDirectParser` | European exchange format |
| GE PSLF | `GE_PSLF` | `GEFormat` | `GEPslfDirectParser` | |
| BPA | `BPA` | `BPAFormat` | `BPADirectParser` | Multi-file: LF from first file via direct parser |
| PowerWorld | `PWD` | `PWDFormat` | `PWDDirectParser` | |
| InterPSS Internal | `IpssInternal` | `IpssInternalFormat` | `_in` / `_out` | Read + write |
| PSS/E sequence | — | `PSSEMultiFileLoader` | `PSSEAcscDirectParser` | `.seq` overlay |
| PSS/E dynamics | — | `PSSEMultiFileLoader` | `PSSEDStabDirectParser` | `.dyr` overlay |
| PSS/E JSON export | — | `PSSEJSonExporter` | — | Filter + update from results |
| PSS/E .sub | — | `SubFileParser` | — | Subsystem → bus/branch sets |
| PSS/E .mon | — | `MonFileParser` | — | Monitored elements → ID sets |

## Migration Notes (ODM Removed)

| Former ODM path | Current path |
|-----------------|--------------|
| `IODMAdapter` + `ODMAclfParserMapper` | `*DirectParser` + `AclfNetworkBuilder` |
| `ODMAcscParserMapper` / `ODMDStabParserMapper` | `PSSEAcscDirectParser` / `PSSEDStabDirectParser` + builders |
| `org.ieee.odm.adapter.psse.bean.PSSESchema` | `org.interpss.fadapter.psse.bean.PSSESchema` |
| `IODMModelParser.BusIdPreFix` | Local `"Bus"` prefix in `BasePSSEJSonUpdater` |
| `CorePluginFactory` ODM mapper factories | Removed; use direct parsers / `IpssAdapter` |
| `IpssFileAdapter.getODMModelParser()` | Deprecated; returns `null` |
| `IpssAdapter.FileFormat.IEEE_ODM` | Deprecated; load throws |
