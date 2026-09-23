# `org.interpss.script.gvy` — Groovy Script Adapter Architecture

## Purpose

The Groovy script adapter lets callers mutate a live InterPSS network from Groovy source — either an inline string or a `.gvy` file — without writing Java against the model API for every what-if change.

Typical uses:

- Adjust bus loads, branch impedance / status, network id, or contribute gen/load data after import
- Drive study automation (batch parameter changes before load flow)
- Keep scenario edits as readable scripts next to case data

**Design note:** Scripts operate on the **same object instance** held by the processor. There is no copy-on-eval and no separate script-model layer. Binding exposes the network under a fixed variable name (`aclfnet` today); Groovy property syntax maps to JavaBean getters/setters on InterPSS EMF/Java objects.

## Package Layout

```
org.interpss.script.gvy
├── BaseGvyScriptProcessor       # Abstract base: shared imports + evaluate()
└── AclfNetGvyScriptProcessor    # ACLF binding: aclfnet → AclfNetwork

Related (outside package)
├── ipss.plugin.core/.../gvy/GvySample.java          # Sample main
└── ipss.test.plugin.core/.../gvy/GvyScriptEval_Test.java
└── ipss.test.plugin.core/script/*.gvy               # Script fixtures
```

## Dependency

| Artifact | Role |
|---|---|
| `org.apache.groovy:groovy` (4.0.x) | `GroovyShell`, `Binding` |
| `com.interpss:ipss.core.lib` | `AclfNetwork` and related model types |
| `org.apache.commons.math3` | `Complex` — pre-imported for scripts via `GVY_IMPORTS` |

## Class Hierarchy

```
BaseGvyScriptProcessor (abstract)
  └── AclfNetGvyScriptProcessor   # binds AclfNetwork as "aclfnet"
```

Extension point for future processors (same pattern):

```
BaseGvyScriptProcessor
  ├── AclfNetGvyScriptProcessor      # aclfnet
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
  │  └───────────────┬────────────────┘  │
  │                  │                     │
  │  GroovyShell(binding)                  │
  │                  │                     │
  │  evaluate(GVY_IMPORTS + groovyCode)    │
  └──────────────────┼─────────────────────┘
                     │ mutates in place
                     ▼
              AclfNetwork (live model)
                  buses / branches /
                  contribute loads / gens
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
```

### `AclfNetGvyScriptProcessor`

AC load-flow specialization:

1. Stores the `AclfNetwork` reference
2. Creates a `Binding` and registers it as `"aclfnet"`
3. Constructs `GroovyShell` with that binding
4. Exposes `getAclfNet()` for the caller to inspect/assert after eval

Scripts address the network exclusively through `aclfnet` (lowercase), matching the binding key — not the Java field name.

## Data Flow

### Inline script

```
AclfNetwork net
       │
       ▼
AclfNetGvyScriptProcessor(net)     // Binding: aclfnet = net
       │
       ▼
evaluate("aclfnet.getBus('Bus14').loadP = 0.18;")
       │
       ▼
net.getBus("Bus14").getLoadP() == 0.18   // same instance
```

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

Scripts may introduce local variables (`bus`, `load`, `branch`, …). Those live in the Groovy script scope for that evaluation; they are not required binding entries.

### Groovy ↔ Java property mapping

Groovy property assignment uses JavaBean conventions on InterPSS objects, for example:

| Script | Effect |
|---|---|
| `aclfnet.id = 'Modified'` | `net.setId("Modified")` |
| `bus.loadP = 0.18` | `bus.setLoadP(0.18)` |
| `load.loadCP = new Complex(p, q)` | `load.setLoadCP(...)` |
| `branch.z = new Complex(r, x)` | `branch.setZ(...)` |
| `branch.status = false` | deactivates branch (`isActive()` → false) |

Method calls (`getBus`, `getBranch`, `getContributeLoad`) are ordinary Java API calls from Groovy.

## Usage Patterns

### 1. Construct and evaluate (from tests / samples)

```java
AclfNetwork net = CorePluginFactory
    .getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
    .load("testData/adpter/ieee_format/Ieee14Bus.ieee")
    .getAclfNet();

AclfNetGvyScriptProcessor gvyProcessor = new AclfNetGvyScriptProcessor(net);

Object result = gvyProcessor.evaluate("aclfnet.id = 'Modified';");
// net.getId() == "Modified"
```

### 2. Multi-statement script (text block)

```java
String groovyCode = """
    bus = aclfnet.getBus('Bus14');
    load = bus.getContributeLoad('Bus14-L1');
    load.loadCP = new Complex(0.18, 0.07);
    """;
gvyProcessor.evaluate(groovyCode);
```

### 3. External `.gvy` fixture

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

`Complex` is available because `BaseGvyScriptProcessor` prepends `GVY_IMPORTS`.

## Evaluation Semantics

| Concern | Behavior |
|---|---|
| Mutation | In-place on the bound network |
| Return value | Last Groovy expression / explicit `return`; often unused for mutation scripts |
| Imports | Always prefixed with `GVY_IMPORTS` |
| Shell lifetime | One `GroovyShell` per processor instance; reusable across multiple `evaluate` calls |
| Isolation | No sandbox; scripts have full access to the bound Java objects |
| Errors | Groovy compile/runtime exceptions propagate to the caller |

Reuse one processor for a sequence of scripts against the same network (as in `GvyScriptEval_Test`) so binding stays consistent.

## Test Coverage Map

| Test | Verifies |
|---|---|
| `GvyScriptEval_Test.bus14testCase` | Inline: id, bus `loadP`, contribute `loadCP`, branch `z` |
| `GvyScriptEval_Test.bus14ScriptFileTestCase` | `.gvy` files: Bus14 load CP; Branch 1–2 `z` + status off |
| `GvySample` (sample main) | End-to-end smoke: plain GroovyShell + ACLF processor after LF |

Fixtures live under `ipss.test.plugin.core/script/`.

## Extension Guide

To add a processor for another network type:

1. Subclass `BaseGvyScriptProcessor`
2. Accept the target network in the constructor
3. `binding.setVariable("<name>", network)` — document the binding name as part of the public contract
4. `this.shell = new GroovyShell(binding)`
5. Optionally extend `GVY_IMPORTS` (or a subclass-specific import block) if scripts need more default types
6. Add tests mirroring `GvyScriptEval_Test` (inline + file)

Keep binding names stable and lowercase (e.g. `aclfnet`) so scripts remain portable across hosts (tests, CLI, desktop).

## Relationship to File Adapters

```
External case file
       │
       ▼
org.interpss.fadapter  (import)     →  AclfNetwork
       │
       ▼
org.interpss.script.gvy (adjust)    →  same AclfNetwork
       │
       ▼
Loadflow / contingency / export
```

File adapters **create** the model; Groovy adapters **edit** it after load. They are complementary, not overlapping:

- `fadapter` — format parsing / builders
- `script.gvy` — runtime scripting against the built model

## Design Constraints & Caveats

- **No transactional rollback** — a failed mid-script leave earlier mutations applied
- **Thread safety** — one processor / network per thread; `GroovyShell` and network are not synchronized
- **Security** — treat `.gvy` content like executable code; only run trusted scripts
- **Contribute vs aggregate model** — script examples for contribute loads assume `net.isContributeGenLoadModel()` (as in IEEE14 tests)
- **Property names** — prefer documented JavaBean names (`loadCP`, `z`, `status`); typos fail at Groovy runtime

## Source Index

| Path | Role |
|---|---|
| `ipss.plugin.core/.../script/gvy/BaseGvyScriptProcessor.java` | Shared evaluate + imports |
| `ipss.plugin.core/.../script/gvy/AclfNetGvyScriptProcessor.java` | ACLF binding |
| `ipss.plugin.core/src/sample/java/org/interpss/gvy/GvySample.java` | Runnable sample |
| `ipss.test.plugin.core/.../gvy/GvyScriptEval_Test.java` | Unit coverage |
| `ipss.test.plugin.core/script/ieee14_adjBus14.gvy` | Load adjustment fixture |
| `ipss.test.plugin.core/script/ieee14_adjBranch1_2.gvy` | Branch z/status fixture |
