# `org.interpss.script.gvy` — Groovy Script Adapter Architecture

## Purpose

The Groovy script adapter lets callers mutate a live InterPSS network from Groovy source — either an inline string or a `.gvy` file — without writing Java against the model API for every what-if change. It also exposes DC sensitivity analysis on the same live network via a bound `SenAnalysisAlgorithm`.

Typical uses:

- Adjust bus loads, branch impedance / status, network id, or contribute gen/load data after import
- Query bus sensitivities (e.g. dV/dQ via `SenAnalysisType.QVOLTAGE`) without leaving the script
- Drive study automation (batch parameter changes before load flow)
- Keep scenario edits as readable scripts next to case data

**Design note:** Scripts operate on the **same object instance** held by the processor. There is no copy-on-eval and no separate script-model layer. Binding exposes the network and sensitivity algorithm under fixed variable names (`aclfnet`, `senAlgo`); Groovy property syntax maps to JavaBean getters/setters on InterPSS EMF/Java objects.

## Package Layout

```
org.interpss.script.gvy
├── BaseGvyScriptProcessor       # Abstract base: shared imports + evaluate()
└── AclfNetGvyScriptProcessor    # ACLF binding: aclfnet, senAlgo

Related (outside package)
├── ipss.plugin.core/.../gvy/GvySample.java          # Sample main
└── ipss.test.plugin.core/.../gvy/GvyScriptEval_Test.java
└── ipss.test.plugin.core/script/*.gvy               # Script fixtures
```

## Dependency

| Artifact | Role |
|---|---|
| `org.apache.groovy:groovy` (4.0.x) | `GroovyShell`, `Binding` |
| `com.interpss:ipss.core.lib` | `AclfNetwork`, `SenAnalysisAlgorithm`, `SenAnalysisType`, `DclfAlgoObjectFactory` |
| `org.apache.commons.math3` | `Complex` — pre-imported for scripts via `GVY_IMPORTS` |

## Class Hierarchy

```
BaseGvyScriptProcessor (abstract)
  └── AclfNetGvyScriptProcessor   # binds AclfNetwork as "aclfnet",
                                  # SenAnalysisAlgorithm as "senAlgo"
```

Extension point for future processors (same pattern):

```
BaseGvyScriptProcessor
  ├── AclfNetGvyScriptProcessor      # aclfnet + senAlgo
  ├── AcscNetGvyScriptProcessor      # (future) acscnet
  └── DStabNetGvyScriptProcessor     # (future) dstabnet
```

## Architecture Overview

```
  ┌──────────────────────────────────────┐
  │  Caller                              │
  │  test / sample / desktop / CLI       │
  └───────────────┬──────────────────────┘
                  │ 1. load AclfNetwork (file adapter, etc.)
                  │ 2. new AclfNetGvyScriptProcessor(net)
                  │ 3. evaluate(groovyCode)  or  FileUtil.read → evaluate
                  ▼
  ┌──────────────────────────────────────┐
  │  AclfNetGvyScriptProcessor           │
  │  ┌────────────────────────────────┐  │
  │  │ Binding                        │  │
  │  │   "aclfnet" → AclfNetwork      │  │
  │  │   "senAlgo" → SenAnalysisAlgo  │  │
  │  └───────────────┬────────────────┘  │
  │                  │                     │
  │  GroovyShell(binding)                  │
  │                  │                     │
  │  evaluate(GVY_IMPORTS + groovyCode)    │
  └──────────────────┼─────────────────────┘
                     │ mutates / queries in place
                     ▼
              AclfNetwork (live model)
                  buses / branches /
                  contribute loads / gens
                     ▲
                     │ shares network
              SenAnalysisAlgorithm
                  calBusSensitivity(...)
```

## Core Components

### `BaseGvyScriptProcessor`

Owns the shared evaluation contract:

- Holds a protected `GroovyShell shell` (created by subclasses with a domain-specific `Binding`)
- Defines `GVY_IMPORTS` — text prepended to every script so callers need not import common types
- `evaluate(String groovyCode)` → `shell.evaluate(GVY_IMPORTS + groovyCode)` and returns the last expression value (or `null` when the script has no return)

Current shared imports:

```java
import org.apache.commons.math3.complex.Complex;
import com.interpss.core.algo.dclf.SenAnalysisType;
```

Scripts can therefore write `new Complex(r, x)` and `SenAnalysisType.QVOLTAGE` / `PANGLE` without local import statements.

### `AclfNetGvyScriptProcessor`

AC load-flow specialization:

1. Stores the `AclfNetwork` reference
2. Creates a `Binding` and registers:
   - `"aclfnet"` → the live `AclfNetwork`
   - `"senAlgo"` → `DclfAlgoObjectFactory.createSenAnalysisAlgorithm(aclfNet)` on the **same** network
3. Constructs `GroovyShell` with that binding
4. Exposes `getAclfNet()` for the caller to inspect/assert after eval

Scripts address the network through `aclfnet` and sensitivity through `senAlgo` (lowercase binding keys) — not the Java field names.

**Lifetime:** `senAlgo` is created once at processor construction and reuses the bound network. Call `senAlgo` after structural edits if you need sensitivities that reflect the updated topology/parameters (the algorithm reads the live `AclfNetwork`).

## Data Flow

### Inline script

```
AclfNetwork net
       │
       ▼
AclfNetGvyScriptProcessor(net)     // Binding: aclfnet = net, senAlgo = SenAnalysis(...)
       │
       ▼
evaluate("aclfnet.getBus('Bus14').loadP = 0.18;")
       │
       ▼
net.getBus("Bus14").getLoadP() == 0.18   // same instance
```

### Sensitivity query (inline)

```
evaluate("""
  dVdQ = senAlgo.calBusSensitivity(SenAnalysisType.QVOLTAGE, 'Bus14', 'Bus14')
  return 'Bus14 dV/dQ: ' + dVdQ
  """)
       │
       ▼
senAlgo.calBusSensitivity(QVOLTAGE, ...) on live aclfnet
```

`SenAnalysisType.QVOLTAGE` returns **dV/dQ** (B″ path). Self-bus dQ/dV is the reciprocal when needed: `1.0 / dVdQ`.

### Script file (`.gvy`)

```
FileUtil.readFileAsString("script/ieee14_adjBus14.gvy")
       │
       ▼
evaluate(groovyCode)   // same Binding / shell as inline
       │
       ▼
AclfNetwork updated in place
```

File loading is **outside** the processor (`FileUtil` or equivalent). The processor only evaluates strings. That keeps I/O optional and testable.

## Binding Contract (ACLF)

| Binding name | Java type | Meaning |
|---|---|---|
| `aclfnet` | `com.interpss.core.aclf.AclfNetwork` | Live ACLF network under study |
| `senAlgo` | `com.interpss.core.algo.dclf.SenAnalysisAlgorithm` | DC sensitivity / PTDF–LODF API on the same network |

Scripts may introduce local variables (`bus`, `load`, `branch`, `dVdQ`, …). Those live in the Groovy script scope for that evaluation; they are not required binding entries.

### Groovy ↔ Java property mapping

Groovy property assignment uses JavaBean conventions on InterPSS objects, for example:

| Script | Effect |
|---|---|
| `aclfnet.id = 'Modified'` | `net.setId("Modified")` |
| `bus.loadP = 0.18` | `bus.setLoadP(0.18)` |
| `load.loadCP = new Complex(p, q)` | `load.setLoadCP(...)` |
| `branch.z = new Complex(r, x)` | `branch.setZ(...)` |
| `branch.status = false` | deactivates branch (`isActive()` → false) |
| `senAlgo.calBusSensitivity(SenAnalysisType.QVOLTAGE, injId, busId)` | dV(bus)/dQ(inj) |

Method calls (`getBus`, `getBranch`, `getContributeLoad`, `calBusSensitivity`) are ordinary Java API calls from Groovy.

## Usage Patterns

### 1. Construct and evaluate (from tests / samples)

```java
AclfNetwork net = CorePluginFactory
    .getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
    .load("ipss.plugin.core/testData/adpter/ieee_format/Ieee14Bus.ieee")
    .getAclfNet();

AclfNetGvyScriptProcessor gvyProcessor = new AclfNetGvyScriptProcessor(net);

Object result = gvyProcessor.evaluate("aclfnet.id = 'Modified';");
// net.getId() == "Modified"
```

When the process cwd is the `ipss-plugin` repo root (typical IDE launch), prefix fixtures with `ipss.plugin.core/` (or `ipss.test.plugin.core/`). Tests that set the working directory to the module root can keep the shorter `testData/...` form.

### 2. Multi-statement script (text block)

```java
String groovyCode = """
    bus = aclfnet.getBus('Bus14');
    load = bus.getContributeLoad('Bus14-L1');
    load.loadCP = new Complex(0.18, 0.07);
    """;
gvyProcessor.evaluate(groovyCode);
```

### 3. Bus dV/dQ sensitivity via `senAlgo`

```java
String groovyCode = """
    dVdQ = senAlgo.calBusSensitivity(SenAnalysisType.QVOLTAGE, 'Bus14', 'Bus14')
    return 'Bus14 dV/dQ: ' + dVdQ
    """;
Object result = gvyProcessor.evaluate(groovyCode);
```

`SenAnalysisType` is available from `GVY_IMPORTS`; `senAlgo` comes from the processor binding. See also core usage guide `SenAnalysisAlgorithm_usage_guide.md` (`PANGLE` / `QVOLTAGE`, PTDF, LODF).

### 4. External `.gvy` fixture

```java
String groovyCode = FileUtil.readFileAsString("script/ieee14_adjBranch1_2.gvy");
gvyProcessor.evaluate(groovyCode);
```

Example fixture (`ieee14_adjBranch1_2.gvy`):

```groovy
fromBusId = "Bus1";
toBusId = "Bus2";
circuitId = "1";
r = 0.02;
x = 0.06;

branch = aclfnet.getBranch(fromBusId, toBusId, circuitId);
branch.status = false;
branch.z = new Complex(r, x);
```

`Complex` and `SenAnalysisType` are available because `BaseGvyScriptProcessor` prepends `GVY_IMPORTS`.

## Evaluation Semantics

| Concern | Behavior |
|---|---|
| Mutation | In-place on the bound network |
| Sensitivity | Queries via bound `senAlgo` against the same live network |
| Return value | Last Groovy expression / explicit `return`; often unused for mutation scripts |
| Imports | Always prefixed with `GVY_IMPORTS` (`Complex`, `SenAnalysisType`) |
| Shell lifetime | One `GroovyShell` per processor instance; reusable across multiple `evaluate` calls |
| Isolation | No sandbox; scripts have full access to the bound Java objects |
| Errors | Groovy compile/runtime exceptions propagate to the caller (e.g. `MissingPropertyException` if a type is not imported and not FQN) |

Reuse one processor for a sequence of scripts against the same network (as in `GvyScriptEval_Test` / `GvySample`) so binding stays consistent.

## Test Coverage Map

| Test | Verifies |
|---|---|
| `GvyScriptEval_Test.bus14testCase` | Inline: id, bus `loadP`, contribute `loadCP`, branch `z` |
| `GvyScriptEval_Test.bus14ScriptFileTestCase` | `.gvy` files: Bus14 load CP; Branch 1–2 `z` + status off |
| `GvySample` (sample main) | End-to-end smoke: plain GroovyShell + ACLF processor after LF, including Bus14 dV/dQ via `senAlgo` |

Fixtures live under `ipss.test.plugin.core/script/`. Sample case data: `ipss.plugin.core/testData/adpter/ieee_format/Ieee14Bus.ieee`.

## Extension Guide

To add a processor for another network type:

1. Subclass `BaseGvyScriptProcessor`
2. Accept the target network in the constructor
3. `binding.setVariable("<name>", network)` — document the binding name as part of the public contract
4. Optionally bind related algorithms (as `senAlgo` is for ACLF)
5. `this.shell = new GroovyShell(binding)`
6. Optionally extend `GVY_IMPORTS` (or a subclass-specific import block) if scripts need more default types
7. Add tests mirroring `GvyScriptEval_Test` (inline + file)

Keep binding names stable and lowercase (e.g. `aclfnet`, `senAlgo`) so scripts remain portable across hosts (tests, CLI, desktop).

## Relationship to File Adapters

```
External case file
       │
       ▼
org.interpss.fadapter  (import)     →  AclfNetwork
       │
       ▼
org.interpss.script.gvy (adjust / query)  →  same AclfNetwork (+ senAlgo)
       │
       ▼
Loadflow / contingency / export
```

File adapters **create** the model; Groovy adapters **edit** and **query** it after load. They are complementary, not overlapping:

- `fadapter` — format parsing / builders
- `script.gvy` — runtime scripting against the built model (mutations + sensitivity)

## Design Constraints & Caveats

- **No transactional rollback** — a failed mid-script leave earlier mutations applied
- **Thread safety** — one processor / network per thread; `GroovyShell` and network are not synchronized
- **Security** — treat `.gvy` content like executable code; only run trusted scripts
- **Contribute vs aggregate model** — script examples for contribute loads assume `net.isContributeGenLoadModel()` (as in IEEE14 tests)
- **Property names** — prefer documented JavaBean names (`loadCP`, `z`, `status`); typos fail at Groovy runtime
- **Sensitivity semantics** — `QVOLTAGE` is dV/dQ; do not confuse with dQ/dV used in AC voltage-control adjustment (`LfAdjSensitivity`)
- **Working directory** — relative case paths depend on cwd; prefer module-prefixed paths when launching from the repo root

## Source Index

| Path | Role |
|---|---|
| `ipss.plugin.core/.../script/gvy/BaseGvyScriptProcessor.java` | Shared evaluate + imports (`Complex`, `SenAnalysisType`) |
| `ipss.plugin.core/.../script/gvy/AclfNetGvyScriptProcessor.java` | ACLF binding (`aclfnet`, `senAlgo`) |
| `ipss.plugin.core/src/sample/java/org/interpss/gvy/GvySample.java` | Runnable sample (mutations + dV/dQ) |
| `ipss.test.plugin.core/.../gvy/GvyScriptEval_Test.java` | Unit coverage |
| `ipss.test.plugin.core/script/ieee14_adjBus14.gvy` | Load adjustment fixture |
| `ipss.test.plugin.core/script/ieee14_adjBranch1_2.gvy` | Branch z/status fixture |
