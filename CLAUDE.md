# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

**Prerequisites:** Java 21, Maven. Run `sh maven.sh` once to install local dependencies (ipss-core, ieee-odm jars from `../ipss-common/ipss.lib`).

```bash
# Full build (no tests)
mvn clean install -DskipTests

# Run core test suite
mvn -pl ipss.test.plugin.core test -Dtest=CorePluginTestSuite

# Run a single test class
mvn -pl ipss.plugin.3phase test -Dtest=DistOpfApiTest

# Run a single test method
mvn -pl ipss.plugin.3phase test -Dtest="DistOpfApiTest#testSolveWithCurtailmentMin"

# Build only one module
mvn -pl ipss.plugin.3phase -am clean install -DskipTests
```

Surefire is configured with `failIfNoTests=false` so specifying a non-matching test name won't fail the build.

## Repository Structure

Multi-module Maven project (`com.interpss:ipss.plugin`, version `${revision}` currently 1.1.0).

| Module | Purpose |
|---|---|
| `ipss.plugin.core` | Core algorithms: ACLF network, OPF framework, DCLF contingency, DStab stability, data exchange adapters |
| `ipss.plugin.3phase` | Three-phase distribution: DistOPF solver, OpenDSS parser, three-phase power flow. Depends on `ipss.plugin.core` |
| `ipss.test.plugin.core` | Integration tests for core (IEEE standard systems). Main suite: `org.interpss.CorePluginTestSuite` |
| `ipss.sample` | Usage examples and custom solver demonstrations |
| `ipss.plugin.py` | Py4J bridge for Python interoperability (not in active Maven modules) |

## Architecture

### Dependency Chain
```
ipss-core (external) → ipss.plugin.core → ipss.plugin.3phase
                         ↑
ieee-odm (external) ─────┘
```

- **ipss-core** (`com.interpss:ipss.core.lib`): Power system engine — network objects, buses, branches, base algorithms
- **ieee-odm** (`org.ieee.odm`): IEEE Open Data Model schema for data exchange (PSSE, IEEE CDF formats)
- **EMF** (Eclipse Modeling Framework): Used for complex data structures in ipss-core

### ipss.plugin.core Key Packages

- `org.interpss.plugin.aclf` — AC load flow network implementation
- `org.interpss.plugin.opf` — Optimal power flow framework: constraint collectors (`IConstraintCollector`), objective collectors, solver adapters (ojAlgo, LP Solve, Apache)
- `org.interpss.plugin.optadj` — Network optimization (ATC, generation dispatch)
- `org.interpss.plugin.contingency` — DCLF contingency analysis, parallel contingency solver
- `org.interpss.plugin.result` — Result handling, data frames (DFlib), JSON/CSV/Parquet export
- `org.interpss.plugin.exchange` — Format adapters (PSSE, etc.)

### ipss.plugin.3phase Key Packages

- `org.interpss.threePhase.dataParser.opendss` — OpenDSS file parser: `OpenDSSDataParser` as entry point, device-specific parsers (`OpenDSSLineParser`, `OpenDSSTransformerParser`, `OpenDSSLoadParser`, `OpenDSSRegulatorParser`, `OpenDSSCapacitorParser`)
- `org.interpss.threePhase.opf.dist` — Distribution OPF: `DistOpfAlgorithm` (main API), model data extraction, constraint/objective collectors, solver adapters (ojAlgo, Apache LP, OR-Tools)
- `org.interpss.threePhase.powerflow` — Three-phase distribution power flow (fixed-point method)
- `org.interpss.threePhase.basic` — Three-phase network primitives (`DStab3PLoad`)

## Key Design Patterns

**Collector/Solver pattern** (used in both OPF and DistOPF):
1. Extract model data from network into an immutable snapshot
2. Constraint collectors build sparse constraint rows (`OpfConstraint`)
3. Objective collectors fill the cost vector
4. Solver adapters (ojAlgo, OR-Tools, Apache) solve the LP/QP
5. Results mapped back to network objects

**DistOPF data flow**: `DStabNetwork3Phase` → `DistOpfModelDataExtractor` → `DistOpfModelData` → constraint/objective collectors → `DistOpfModel` → solver → `DistOpfResult` → optional `applySetpointsToNetwork()` → fixed-point power flow validation

**OpenDSS import flow**: `OpenDSSDataParser.parseFeederData(dir, masterFile)` → `getDistNetwork()` returns `DStabNetwork3Phase`

## Conventions

- DistOPF uses static power-flow network model only — never include dynamic simulation Y-matrix contributions
- OPF variable indexing is centralized in `DistOpfVariableIndex` — collectors look up column numbers, never hard-code index arithmetic
- Three-phase data uses per-unit on feeder base; `OpenDSSUnitConverter` handles OpenDSS → PU conversion
- Tests in `ipss.plugin.3phase` use JUnit 5; tests in `ipss.test.plugin.core` also use JUnit 5
- Test resources (DSS files, CSV fixtures) live under `src/test/resources/` or `testData/`
