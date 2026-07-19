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
| BPA | `BPA` | `BPAFormat` | `BPADirectParser` | IPF card types B/L/T/E/A; `/MVA_BASE` currently hardcoded 100 MVA; R/TP/+ stubs skipped; LF from first file only (no `.swi`) |
| PowerWorld | `PWD` | `PWDFormat` | `PWDDirectParser` | Legacy `DATA (…)` AUX; field aliases for BusNomVolt/LoadSMW/BusNum:1/LineC; BRANCH transformers via BranchDeviceType; concise headers / XFAuto controls not supported |
| InterPSS Internal | `IpssInternal` | `IpssInternalFormat` | `_in` / `_out` | Read + write |
| PSS/E sequence | — | `PSSEMultiFileLoader` | `PSSEAcscDirectParser` | `.seq` overlay |
| PSS/E dynamics | — | `PSSEMultiFileLoader` | `PSSEDStabDirectParser` | `.dyr` overlay |
| PSS/E JSON export | — | `PSSEJSonExporter` | — | Filter + update from results |
| PSS/E .sub | — | `SubFileParser` | — | Subsystem → bus/branch sets |
| PSS/E .mon | — | `MonFileParser` | — | Monitored elements → ID sets |

## Tests and Samples (`ipss.test.plugin.core`)

Integration tests, builder unit tests, and runnable samples for `org.interpss.fadapter` live in the **`ipss.test.plugin.core`** Maven module (not in `ipss.plugin.core`). A smaller parser-focused unit test also exists under `ipss.plugin.core/src/test/java/org/interpss/fadapter/`.

### Module layout

```
ipss.test.plugin.core/
├── src/test/java/org/interpss/
│   ├── CorePluginTestSetup.java       # @BeforeAll: IpssCorePlugin.init()
│   ├── CorePluginTestSuite.java       # Main JUnit suite (adapter + builder + downstream)
│   └── core/adapter/                  # ~78 adapter-related test classes
│       ├── builder/                   # Aclf / Acsc / DStab builder unit tests
│       ├── ieee/                      # IEEE CDF
│       ├── internal/                  # IpssInternalFormat
│       ├── matpower/                  # MATPOWER + large Pegase/RTE cases
│       ├── psse/raw/aclf|acsc|dstab/  # PSS/E RAW, sequence, dynamics
│       ├── psse/json/aclf/            # RAWX import + JSON export
│       ├── pwd/, ge/, bpa/, ucte/     # Other format facades
│       └── CoreAdapterTestSuite.java  # Smaller IEEE + internal subset
├── src/main/java/sample/              # Runnable main() examples (load → simulate → export)
└── testData/                          # Fixture files (paths relative to module CWD)
    ├── adpter/                        # Primary adapter fixtures
    │   ├── psse/v{29..36}/, json/
    │   ├── ieee_format/, matpower/, pwd/, bpa/, ge/, ucte/
    │   └── …
    └── psse/                          # Large cases, contingency, monitored-branch JSON
```

Tests assume the working directory is the `ipss.test.plugin.core` module root so paths like `testData/adpter/psse/v30/IEEE9Bus/ieee9.raw` resolve correctly.

### Test infrastructure

| Class | Role |
|-------|------|
| `CorePluginTestSetup` | Base class: calls `IpssCorePlugin.init()` once; exposes `msg` hub and `create2BusSystem()` helper |
| `CorePluginTestSuite` | Broad regression suite — includes builder tests, format adapters, PSSE JSON, MATPOWER, large nets, DStab/Acsc |
| `CoreAdapterTestSuite` | Narrow suite: IEEE CDF + `IpssInternalFormat` smoke tests |
| `PSSEAdapterTestSuite` | Fast PSS/E RAW v30–v36 subset: 5-bus, IEEE9, v31–v36 matrix, version gates, Bus0/auto-version, switched shunt |
| `BPAAdapterTestSuite` | Fast BPA subset: sample LF (IEEE9 / Test009), card gates (B/L/T/A/E, R/TP/+ skip, OMIB), regional `07c_0615_notBE` smoke |
| `PWDAdapterTestSuite` | Fast PowerWorld AUX subset: IEEE14, object gates (BUS/GEN/LOAD/BRANCH/SHUNT/AREA/ZONE), SixBus PS-xfr DCLF, XfrControl topology |

Most adapter tests extend `CorePluginTestSetup` and load cases via one of:

- `IpssAdapter.importAclfNet(path).setFormat(...).setPsseVersion(...).load().getImportedObj()`
- `CorePluginFactory.getFileAdapter(FileFormat, Version).load(path).getAclfNet()`
- Direct parser: `new PSSEDirectParser(ver).parse(path)`, `new PSSEJsonDirectParser().parse(path)`
- Multi-file: `new PSSEMultiFileLoader(ver).loadAcsc(...)` / `.loadDStab(...)`

### Running tests

From the `ipss-plugin` repo root:

```bash
# Full core plugin regression (includes fadapter integration tests)
mvn -pl ipss.test.plugin.core test -Dtest=CorePluginTestSuite

# Smaller adapter-only subset
mvn -pl ipss.test.plugin.core test -Dtest=CoreAdapterTestSuite

# Fast PSS/E RAW v30–v36 DirectParser / adapter subset
mvn -pl ipss.test.plugin.core test -Dtest=PSSEAdapterTestSuite

# Fast BPA DirectParser / adapter subset
mvn -pl ipss.test.plugin.core test -Dtest=BPAAdapterTestSuite
mvn -pl ipss.test.plugin.core test -Dtest=BPADirectParser_CardGate_Test,BPASampleTestCases

# Fast PowerWorld AUX DirectParser / adapter subset
mvn -pl ipss.test.plugin.core test -Dtest=PWDAdapterTestSuite
mvn -pl ipss.test.plugin.core test -Dtest=PWDDirectParser_ObjectGate_Test,PWDIEEE14BusTestCase

# Builder unit tests (Aclf)
mvn -pl ipss.test.plugin.core test -Dtest=AclfNetworkBuilderCoreTest

# Single format integration test
mvn -pl ipss.test.plugin.core test -Dtest=MatpowerFormatTest
mvn -pl ipss.test.plugin.core test -Dtest=PSSE_IEEE9Bus_Test
mvn -pl ipss.test.plugin.core test -Dtest=PSSEDirectParser_VersionGate_Test
mvn -pl ipss.test.plugin.core test -Dtest=PSSEV31_v36_Sample_Test
mvn -pl ipss.test.plugin.core test -Dtest=IEEE9_Dstab_Adapter_Test
mvn -pl ipss.test.plugin.core test -Dtest=PSSEJSon_IEEE9Bus_FAdapter_Test

# Parser unit test in ipss.plugin.core (PSS/E v36 label metadata)
mvn -pl ipss.plugin.core test -Dtest=PSSEV36RawLabelMetadataMapperTest
```

### Test coverage by component

#### Network builder unit tests (`core/adapter/builder/`)

| Package | Test classes | What they verify |
|---------|--------------|------------------|
| `builder/aclf/` | `AclfNetworkBuilderCoreTest`, `BranchTest`, `AdjDeviceTest`, `HvdcTest`, `3WAndFinalizeTest` | Bus/gen/load codes, branches, tap/PS controls, HVDC, 3W xfr, `finalizeNetwork()` |
| `builder/acsc/` | `AcscNetworkBuilderCoreTest`, `BranchTest`, `FinalizeTest` | Sequence Z, grounding, branch zero-seq, finalize |
| `builder/dstab/` | `DStabNetworkBuilderMachineTest`, `ExciterTest`, `GovernorTest` | GENROU/GENSAL, IEEET1/TGOV1, machine attachment |

Fixtures: `DStabBuilderTestFixture`, `AcscBuilderTestFixture`.

#### Format integration tests (`core/adapter/`)

| Area | Representative tests | Entry / parser exercised |
|------|---------------------|--------------------------|
| PSS/E RAW ACLF (v30–v36) | `PSSE_IEEE9Bus_Test`, `PSSE_5Bus_TestCase`, `PSSEV31_v36_Sample_Test`, `PSSEV31_v36_IEEE9_Test`, `PSSEDirectParser_VersionGate_Test`, `PSSE_Savnw_v33_Test`, `PSSE_5Bus_SwitchedShunt_Test`, `PsseVersionParserTest`, `PSSE_AutoVersion_Bus0_Regression_Test`, `Kundur_2Area_*_Test` | `IpssAdapter` / `PTIFormat` / `new PSSEDirectParser(n)` |
| PSS/E RAW version gates | Fixed shunt (v31+), DGEN (v34+), switched-shunt S/N/B + multi-ID (v35+), Z-table / skip-safety / series FACTS (v36), wrong-version force | `PSSEDirectParser_VersionGate_Test` |
| PSS/E RAWX | `PSSEJSon_IEEE9Bus_DSL_Test`, `PSSEJSon_IEEE9Bus_FAdapter_Test` | `PSSEJsonDirectParser` |
| PSS/E JSON export | `PSSEJSon_IEEE9Bus_FAdapter_Test`, `PSSEJSon_IEEE9Bus_BusSet_Test` | `PSSEJSonExporter` + `PSSEJSon*Updater` |
| PSS/E ACSC | `IEEE9Bus_Acsc_Test`, `IEEE39Bus_Acsc_Test`, `PSSE_Savnw_v33_Acsc_Test` | `PSSEMultiFileLoader.loadAcsc` |
| PSS/E DStab | `IEEE9_Dstab_Adapter_Test` | `PSSEMultiFileLoader.loadDStab` (`.raw` + `.seq` + `.dyr`) |
| Large PSSE | `PSSE_ACTIVSg2000Bus_Test`, `PSSE_ACTIVSg25kBus_Test` | `PSSEDirectParser` at scale |
| IEEE CDF | `IEEE14BusTest`, `IEEE118Bus_Test`, `IEEE300BusTest`, `IEEECommonFormat_CommaTest` | `IeeeCDFFormat` / `CorePluginFactory` |
| MATPOWER | `MatpowerFormatTest`, `MatpowerCase*PegaseTest`, `MatpowerCase*RteTest` | `MatpowerFormat` + large `.m` cases |
| UCTE | `UCTEFormatIEEE14BusTest`, `UCTEFormatAusPowerTest`, `UCTE2000CasesTest` | `UCTEFormat` |
| GE | `GESampleTestCases` | `GEFormat` / `GEPslfDirectParser` |
| PWD | `PWDIEEE14BusTestCase`, `PWDDirectParser_ObjectGate_Test`, `SixBus_DclfPsXfr_pwd`, `SixBus_XfrControl_pwd` (`PWDAdapterTestSuite`) | `PWDFormat` + `PWDDirectParser`; fixtures under `testData/adpter/pwd/` (+ `unit/` for SHUNT) |
| BPA | `BPASampleTestCases`, `BPADirectParser_CardGate_Test`, `Bpa07c_0615_Test`, `BpaO7CTest` (`BPAAdapterTestSuite`) | `BPAFormat` + `BPADirectParser`; fixtures under `testData/adpter/bpa/` (+ `unit/` for E / R-TP-skip) |
| Internal format | `IEEE14Test`, `Bus1824Test`, `Bus6384Test`, `Bus11856Test` | `IpssInternalFormat` round-trip |
| Compare / regression | `IEEE14JsonCompareTest`, `PSSE_ACTIVSg25kObjectCompareTest` | Load twice, `AclfNetJsonComparator` |

BPA IPF card coverage (`BPADirectParser`):

| Card | Meaning | Asserted by |
|------|---------|-------------|
| `B` / `BE` / `BQ` / `BS` | AC bus subtypes | `BPASampleTestCases`, `BPADirectParser_CardGate_Test` |
| `L` / `T` | Line / transformer | IEEE9 + Test009 gates |
| `E` | Equivalent branch | `unit/equiv_e_branch.dat` |
| `A` | Area interchange | `IEEE9_cn.dat` |
| `R` / `TP` / `+` | Unimplemented — skip-safe | `unit/skip_r_tp_plus.dat` |
| `/MVA_BASE` | Currently ignored (always 100 MVA) | same skip fixture |
| Regional | `07c_0615_notBE.dat` parse + LF | `Bpa07c_0615_Test`, `BpaO7CTest` |

PWD legacy AUX object coverage (`PWDDirectParser`):

| Object / feature | Meaning | Asserted by |
|------|---------|-------------|
| `BUS` (+ `BusSlack` / `BusNomVolt`) | Buses, swing | `PWDIEEE14BusTestCase`, ObjectGate |
| `GEN` / `LOAD` | Injections (`GenVoltSet`, `LoadSMW`) | IEEE14 LF + ObjectGate |
| `BRANCH` | Lines; `BranchDeviceType=Transformer` + `LineTap`/`LinePhase` | SixBus gates |
| `TRANSFORMER` | Control-only overlay (skipped if no R/X) | indirect |
| `AREA` / `ZONE` | Area/zone maps | SixBus Base |
| `SHUNT` | Fixed shunt Y | `unit/shunt_2bus.aux` |
| `OWNER` / `PWCASEINFORMATION` / `CONTINGENCY` | Ignored — skip-safe | ObjectGate skip-safety |

#### PSS/E auxiliary files

`.sub` / `.mon` parsing and resolution are covered indirectly by downstream DCLF/contingency tests that consume JSON converted from `.sub`/`.mon` files. Dedicated API docs:

- [psse_subsystem.md](psse_subsystem.md) — `SubFileParser`, `SubsystemFilter`
- [psse_monitor.md](psse_monitor.md) — `MonFileParser`, `MonElementHelper`

Test fixtures for large-case monitoring/contingency JSON live under `testData/psse/v33/` and `testData/psse/v36/Texas2k/`.

### Runnable samples (`src/main/java/sample/`)

These are `main()` programs (not JUnit). Run from IDE or `exec:java` after building `ipss.test.plugin.core`.

| Sample | Package / class | Demonstrates |
|--------|-----------------|--------------|
| PSSE JSON bus-set export | `sample.psse.busset.PSSE_IEEE9Bus_BusSetSample` | `PSSEJsonDirectParser` → loadflow → topo bus set → `PSSEJSonExporter.filterAndUpdate` |
| IEEE CDF compare | `sample.compare.Ieee14JSonCompareSample` | `CorePluginFactory` + `IpssInternalFormat` / IEEE CDF + `AclfNetJsonComparator` |
| Result exchange | `sample.exchange.AclfResultExchangeIeee14Sample` | `CorePluginFactory.getFileAdapter(IEEECDF)` → loadflow → `AclfResultExchangeAdapter` |
| Contingency / DCLF | `sample.contingency.*`, `sample.dclf.*` | Large-case workflows after network import |
| ACLF large cases | `sample.aclf.ACTIVSg25kBus*` | PSSE large-network load + controls |

**Example — PSSE JSON export with bus filtering** (from `PSSE_IEEE9Bus_BusSetSample`):

```java
AclfNetwork net = new PSSEJsonDirectParser().parse("testdata/adpter/psse/json/ieee9.rawx");
// run loadflow, build busIdSet via AclfNetTopoHelper …
PSSESchema psseJson = new Gson().fromJson(
    new FileReader("testdata/adpter/psse/json/ieee9.rawx"), PSSESchema.class);
new PSSEJSonExporter(net, psseJson).filterAndUpdate(busIdSet).export("output/ieee9_busset.rawx");
```

**Example — multi-file DStab load** (from `IEEE9_Dstab_Adapter_Test`):

```java
IpssCorePlugin.init();
SimuContext ctx = new PSSEMultiFileLoader(30).loadDStab(
    "testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
    "testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
    "testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr");
BaseDStabNetwork dsNet = ctx.getDStabilityNet();
```

### Key test data paths

| Path under `testData/` | Contents |
|------------------------|----------|
| `adpter/psse/v30/IEEE9Bus/` | `ieee9.raw`, `ieee9.seq`, `ieee9_dyn_onlyGen.dyr` — canonical multi-file IEEE 9 |
| `adpter/psse/json/ieee9.rawx` | RAWX import + JSON export round-trip |
| `adpter/psse/v36/` | v36 labeled RAW (`ieee9_v36_labeled.raw`, Texas2k labeled) |
| `psse/v31/` … `psse/v36/` | Official `sample_vXX.raw`, ieee9 matrix, `sample_ztable_v36.raw` |
| `adpter/matpower/case*.m` | case9, case30, case118, Pegase/RTE large cases |
| `adpter/ieee_format/` | `Ieee14Bus.ieee`, `ieee39.ieee`, etc. |
| `adpter/pwd/`, `adpter/bpa/`, `adpter/ge/` | PowerWorld AUX, BPA, GE PSLF |
| `psse/v33/`, `psse/v36/Texas2k/` | ACTIVSg, OpenEI, monitored-branch / contingency JSON |

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
