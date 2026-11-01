# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

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

## Generated-output storage

- Run long simulations, diagnostic sweeps, and verbose builds through
  `python tools/run_bounded.py --log target/<unique-run>.log -- <command> ...`.
  It retains 2 GiB of free disk space and caps console capture at 16 MiB;
  exceeding either limit stops the command with a failure status.
- Do not redirect unbounded command output directly to a file. Keep Java test
  heaps bounded on this workstation, for example `-DargLine=-Xmx2g` for Maven.
- Benchmark trace writers enforce a 512 MiB per-file limit and a 2 GiB free-space
  reserve. Keep full comparison samples and event-side duplicates; never silently
  reduce sampling or discard channels to make a run fit. Larger exports require
  an explicit reviewed budget and enough space.
- Keep optional full-network and state diagnostics off unless the current
  comparison needs them. Avoid copying entire private evidence trees into
  temporary build directories; reference or selectively stage needed files.
- Preserve supplied references and files listed by evidence manifests. Cleanup
  may remove old, regenerable outputs only after checking that they are neither
  active nor retained dependencies. Do not add automatic deletion of references.

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

- Use neutral benchmark/reference-simulator language in all documentation and new Java files; do not name the proprietary native simulator or its release in prose. Existing Java files at policy baseline `f3b43863fd11e2da3c88195e5dbc5a2d30130ab2` are exempt. Preserve class names, packages, APIs, protocol identifiers, and resource paths. Keep exact tool/version provenance in the existing private evidence manifests rather than public prose.
- Before delivering documentation or new Java files, run `python tools/check_neutral_benchmark_language.py`. Keep documentation-generator output neutral too. The separate wording CI check enforces this policy without requiring a simulator or Maven.

- DistOPF uses static power-flow network model only — never include dynamic simulation Y-matrix contributions
- OPF variable indexing is centralized in `DistOpfVariableIndex` — collectors look up column numbers, never hard-code index arithmetic
- Three-phase data uses per-unit on feeder base; `OpenDSSUnitConverter` handles OpenDSS → PU conversion
- Tests in `ipss.plugin.3phase` use JUnit 5; tests in `ipss.test.plugin.core` also use JUnit 5
- Test resources (DSS files, CSV fixtures) live under `src/test/resources/` or `testData/`
