# Three-Phase Interface Refactoring Plan

## 1. Problem Statement

The 3-phase network model has two independent inheritance branches:

```
BaseAcscNetwork
    ├── Static3PNetwork   (steady-state 3-phase, in ipss.core_EMF)
    │     uses Static3PBus, Static3PBranch
    └── BaseDStabNetwork
          └── DStabNetwork3Phase  (in ipss.plugin.3phase)
                uses DStab3PBus, DStab3PBranch
```

Consequences:
- `DistributionPowerFlowAlgorithmImpl` and `DistOpfModelDataExtractor` are locked to
  `DStabNetwork3Phase` / `DStab3PBus` / `DStab3PBranch` through casts, even though they
  only use static network data.
- `DStabNetwork3phaseImpl.formYMatrixABC()` duplicates Y-matrix assembly logic that also
  exists in `Static3PNetworkImpl`.
- Algorithms cannot run on `Static3PNetwork` without rewriting.
- No shared contract between the two 3-phase worlds.

## 2. Design Principle: Model-Algorithm Decoupling

The 3-phase network model is **generic** — it can represent any unbalanced 3-phase power
system, whether transmission or distribution. It knows nothing about what analysis will be
run on it.

**Specific algorithms are decoupled from the model.** Each algorithm defines its own
preconditions and applies to any `IThreePhaseNetwork` that satisfies them:

| Algorithm | Applies to | Precondition |
|---|---|---|
| Distribution Power Flow | 3-phase radial or meshed feeders | none |
| DistOPF (LinDistFlow) | 3-phase radial feeders | radial topology, one swing bus |
| 3-phase short circuit | any 3-phase network | none |
| Transmission 3-phase PF | meshed 3-phase transmission | none |
| 3-phase dynamic simulation | any 3-phase network | requires `DStabNetwork3Phase` (extends `BaseDStabNetwork`) |

The model does not encode "distribution" or "transmission." A `Static3PNetwork` or a
`DStabNetwork3Phase` can model either. The algorithm decides what it needs and validates
its own preconditions at runtime.

## 3. Module Responsibility

### ipss.core_EMF — Modeling + Default Algorithms

Contains the complete 3-phase modeling stack and default analysis algorithms:

```
com.interpss.core.abc                          ← Static3P model (existing)
    IThreePhaseNetwork, IThreePhaseBus, IThreePhaseBranch  ← NEW interfaces
    Static3PNetwork, Static3PBus, Static3PBranch           ← existing

com.interpss.threePhase.basic.dstab            ← DStab3P model (MOVED from plugin)
    DStab3PBus, DStab3PBranch, DStab3PLoad, DStab3PGen

com.interpss.threePhase.dynamic                ← 3-phase DStab network (MOVED from plugin)
    DStabNetwork3Phase, DStabNetwork3phaseImpl

com.interpss.threePhase.dynamic.model          ← Dynamic device models (MOVED from plugin)
    DynLoadModel1Phase, DynLoadModel3Phase, DynGenModel3Phase, ...

com.interpss.threePhase.powerflow              ← Distribution PF (MOVED from plugin)
    DistributionPowerFlowAlgorithm, DistributionPowerFlowAlgorithmImpl

com.interpss.threePhase.opf.dist               ← DistOPF (MOVED from plugin)
    DistOpfAlgorithm, DistOpfModelDataExtractor, constraint/objective/solver sub-packages

com.interpss.threePhase.util                   ← Shared utilities (MOVED from plugin)
    ThreePhaseObjectFactory, ThreePhaseUtilFunction, ThreeSeqLoadProcessor
```

### ipss.plugin.3phase — Co-Simulation and Integration

Contains external format parsers, multi-network co-simulation, and ODM integration:

```
org.interpss.threePhase.dataParser.opendss      ← OpenDSS import (STAYS)
    OpenDSSDataParser, OpenDSSLineParser, OpenDSSTransformerParser, ...

org.interpss.threePhase.odm                     ← ODM 3-phase mapper (STAYS)
    ODM3PhaseDStabParserMapper, AbstractODM3PhaseDStabParserMapper

org.interpss.multiNet                           ← Multi-network co-simulation (STAYS)
    MultiNetDStabSolverImpl, MultiNetDStabSimuHelper, ...
    multiNet.algo.powerflow, multiNet.equivalent

org.interpss.threeSeq.algo                      ← 3-sequence co-simulation (STAYS)
    DStab3SeqSolverImpl
```

### Dependency After Restructuring

```
ipss.core_EMF (self-contained: model + algorithms)
    ↑
ipss.plugin.3phase (co-simulation, imports core_EMF)
    ↑
ipss.test.plugin.core / ipss.plugin.3phase tests
```

`ipss.plugin.3phase` no longer contains any modeling or algorithm code. It depends on
`ipss.core_EMF` for all 3-phase network objects and algorithms.

## 4. Target Architecture

```
                    IThreePhaseNetwork (generic 3-phase model interface)
                    IThreePhaseBus     (generic 3-phase bus interface)
                    IThreePhaseBranch  (generic 3-phase branch interface)
                         ↑
            implements    |    implements
                         |
              Static3PNetwork        DStabNetwork3Phase
              Static3PBus            DStab3PBus (also extends BaseDStabBus)
              Static3PBranch         DStab3PBranch (also extends DStabBranch)
                                     (also extends BaseDStabNetwork → keeps all DStab API)
```

Algorithms depend on the generic model:

```
DistPowerFlow  ──→                        ──→  3-phase Short Circuit
                        IThreePhaseNetwork
DistOPF       ──→                        ──→  Transmission 3-phase PF
                              ↑
                   DStabNetwork3Phase (also BaseDStabNetwork)
                              ↑
                     3-phase Dynamic Simulation
```

All of the above lives in `ipss.core_EMF`. Co-simulation in `ipss.plugin.3phase` uses
these classes but does not define them.

Key properties:
- `DStabNetwork3Phase` still extends `BaseDStabNetwork`. No DStab API is lost.
- Power flow results are stored directly on `DStab3PBus` objects through
  `IThreePhaseBus.set3PhaseVoltages()` — same object, no copying.
- `IThreePhaseNetwork` is not distribution-specific. It is a generic unbalanced 3-phase
  network model that works for both transmission and distribution.

## 5. Interface Definitions

### 5.1 IThreePhaseNetwork

**Location:** `com.interpss.core.abc` in `ipss.core_EMF`
**Extends:** none (standalone interface, not tied to EMF EObject)

```java
package com.interpss.core.abc;

import java.util.List;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;

public interface IThreePhaseNetwork {

    double getBaseMva();

    List<? extends IThreePhaseBus> getThreePhaseBusList();
    List<? extends IThreePhaseBranch> getThreePhaseBranchList();

    // Y-matrix including dynamic device contributions (for DStab)
    ISparseEqnComplexMatrix3x3 formYMatrixABC() throws IpssNumericException;
    ISparseEqnComplexMatrix3x3 getYMatrixABC();

    // Y-matrix for power flow only (no dynamic devices)
    ISparseEqnComplexMatrix3x3 formYMatrixABCForPowerflow() throws IpssNumericException;
    ISparseEqnComplexMatrix3x3 getYMatrixABCForPowerflow();
}
```

### 5.2 IThreePhaseBus

**Location:** `com.interpss.core.abc` in `ipss.core_EMF`

```java
package com.interpss.core.abc;

import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.LimitType;

public interface IThreePhaseBus {

    // --- Identity ---

    String getId();
    boolean isActive();
    boolean isSwing();
    boolean isGen();
    boolean isLoad();
    double getBaseVoltage();

    // --- Voltages (power flow writes, everyone reads) ---

    Complex3x1 get3PhaseVoltages();
    void set3PhaseVoltages(Complex3x1 vabc);

    // --- Bus admittance ---

    Complex3x3 getYiiAbc();              // includes dynamic devices
    Complex3x3 getYiiAbcForPowerflow();  // static only

    // --- Load data (read by power flow and OPF) ---

    Complex3x1 get3PhaseTotalLoad();     // total 3-phase load

    List<? extends IThreePhaseLoadAdapter> getThreePhaseLoadList();
    List<? extends IThreePhaseGenAdapter> getThreePhaseGenList();

    // --- Limits (read by power flow violation check and OPF) ---

    LimitType getVLimit();
}
```

### 5.3 IThreePhaseBranch

**Location:** `com.interpss.core.abc` in `ipss.core_EMF`

```java
package com.interpss.core.abc;

import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.core.acsc.PhaseCode;

public interface IThreePhaseBranch {

    // --- Identity ---

    String getId();
    boolean isActive();
    int getCircuitNumber();
    PhaseCode getPhaseCode();

    // --- Impedance / admittance ---

    Complex3x3 getZabc();
    Complex3x3 getYftabc();
    Complex3x3 getYtfabc();

    // --- Transformer ---

    boolean isXfr();
    double getFromTurnRatio();
    double getToTurnRatio();
    double getRatingMva1();
    double getXfrRatedKVA();

    // --- Connected buses ---

    IThreePhaseBus getFromBus();
    IThreePhaseBus getToBus();
}
```

### 5.4 IThreePhaseLoadAdapter

Thin read-only adapter for load data needed by OPF and power flow.
Does not replace `DStab3PLoad` or `Static3PLoad` — just a view.

```java
package com.interpss.core.abc;

import org.interpss.numeric.datatype.Complex3x1;
import com.interpss.core.aclf.AclfLoadCode;

public interface IThreePhaseLoadAdapter {

    AclfLoadCode getCode();
    Complex3x1 getInit3PhaseLoad();
    PhaseCode getPhaseCode();
    LoadConnectionType getLoadConnectionType();
}
```

### 5.5 IThreePhaseGenAdapter

Thin read-only adapter for DER data needed by OPF.

```java
package com.interpss.core.abc;

import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;

public interface IThreePhaseGenAdapter {

    String getId();
    Complex3x1 getPower3Phase(UnitType unit);
    double getMvaBase();
}
```

## 6. Existing Interfaces to Update

These interfaces already exist in `com.interpss.core.abc` and need to extend the
new contracts:

| Existing Interface | Change |
|---|---|
| `INetwork3Phase` | Add `extends IThreePhaseNetwork` |
| `IBus3Phase` | Add `extends IThreePhaseBus` (add missing methods) |
| `IBranch3Phase` | Add `extends IThreePhaseBranch` (add missing methods) |
| `ILoad3Phase` | Add `extends IThreePhaseLoadAdapter` |
| `IGen3Phase` | Add `extends IThreePhaseGenAdapter` |

## 7. Code Move: ipss.plugin.3phase → ipss.core_EMF

### 7.1 Model Classes (org.interpss.threePhase.basic.dstab)

All files move from `ipss.plugin.3phase` to `ipss.core_EMF`, keeping the same
package names:

| Source (ipss.plugin.3phase) | Target (ipss.core_EMF) |
|---|---|
| `org/interpss/threePhase/basic/dstab/DStab3PBus.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/dstab/impl/DStab3PBusImpl.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/dstab/DStab3PBranch.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/dstab/impl/DStab3PBranchImpl.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/dstab/DStab3PLoad.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/dstab/impl/DStab3PLoadImpl.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/dstab/DStab3PGen.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/dstab/impl/DStab3PGenImpl.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/dstab/DStab1PLoad.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/dstab/impl/DStab1PLoadImpl.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/dstab/DStab3W3PBranch.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/dstab/impl/DStab3W3PBranchImpl.java` | same package in core_EMF |

### 7.2 Network Classes (org.interpss.threePhase.dynamic)

| Source (ipss.plugin.3phase) | Target (ipss.core_EMF) |
|---|---|
| `org/interpss/threePhase/dynamic/DStabNetwork3Phase.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/impl/DStabNetwork3phaseImpl.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/DynamicEventProcessor3Phase.java` | same package in core_EMF |

### 7.3 Dynamic Device Models (org.interpss.threePhase.dynamic.model)

| Source (ipss.plugin.3phase) | Target (ipss.core_EMF) |
|---|---|
| `org/interpss/threePhase/dynamic/model/DynLoadModel1Phase.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/DynLoadModel3Phase.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/DynGenModel3Phase.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/IDynamicModel1Phase.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/IDynamicModel3Phase.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/DynamicModel1Phase.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/DynamicModel3Phase.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/PVDistGen3Phase.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/MachModel_DER_A_v4.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/SinglePhaseACMotor.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/InductionMotor3PhaseAdapter.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/DStabGen3PhaseAdapter.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/model/impl/*.java` | same package in core_EMF |
| `org/interpss/threePhase/dynamic/algo/DynamicEventProcessor3Phase.java` | same package in core_EMF |

### 7.4 Distribution Power Flow (org.interpss.threePhase.powerflow)

| Source (ipss.plugin.3phase) | Target (ipss.core_EMF) |
|---|---|
| `org/interpss/threePhase/powerflow/DistributionPowerFlowAlgorithm.java` | same package in core_EMF |
| `org/interpss/threePhase/powerflow/DistributionPFMethod.java` | same package in core_EMF |
| `org/interpss/threePhase/powerflow/impl/DistributionPowerFlowAlgorithmImpl.java` | same package in core_EMF |
| `org/interpss/threePhase/powerflow/impl/DistPowerFlowOutFunc.java` | same package in core_EMF |

### 7.5 DistOPF (org.interpss.threePhase.opf.dist)

All sub-packages move together:

| Source (ipss.plugin.3phase) | Target (ipss.core_EMF) |
|---|---|
| `org/interpss/threePhase/opf/dist/DistOpfAlgorithm.java` | same package in core_EMF |
| `org/interpss/threePhase/opf/dist/impl/*.java` (2 files) | same package in core_EMF |
| `org/interpss/threePhase/opf/dist/model/*.java` (12 files) | same package in core_EMF |
| `org/interpss/threePhase/opf/dist/constraint/*.java` (14 files) | same package in core_EMF |
| `org/interpss/threePhase/opf/dist/objective/*.java` (7 files) | same package in core_EMF |
| `org/interpss/threePhase/opf/dist/solver/*.java` (7 files) | same package in core_EMF |
| `org/interpss/threePhase/opf/dist/util/*.java` (1 file) | same package in core_EMF |
| `org/interpss/threePhase/opf/dist/validation/*.java` (2 files) | same package in core_EMF |

### 7.6 Utilities (org.interpss.threePhase.util)

| Source (ipss.plugin.3phase) | Target (ipss.core_EMF) |
|---|---|
| `org/interpss/threePhase/util/ThreePhaseObjectFactory.java` | same package in core_EMF |
| `org/interpss/threePhase/util/ThreePhaseUtilFunction.java` | same package in core_EMF |
| `org/interpss/threePhase/util/ThreeSeqLoadProcessor.java` | same package in core_EMF |
| `org/interpss/threePhase/util/ThreePhaseAclfOutFunc.java` | same package in core_EMF |

### 7.7 Helper Classes in basic/

| Source (ipss.plugin.3phase) | Target (ipss.core_EMF) |
|---|---|
| `org/interpss/threePhase/basic/IEEEFeederLineCode.java` | same package in core_EMF |
| `org/interpss/threePhase/basic/LineConfiguration.java` | same package in core_EMF |

## 8. Code That Stays in ipss.plugin.3phase

These packages remain in `ipss.plugin.3phase` — they are co-simulation and external
integration code:

### 8.1 OpenDSS Data Parser

```
org.interpss.threePhase.dataParser.opendss    ← STAYS
org.interpss.threePhase.dataParser             ← STAYS
```

- `OpenDSSDataParser` and all device parsers
- `OpenDSSUnitConverter`
- These import external format data and create `DStabNetwork3Phase` objects.
  After the move, they will reference the classes from `ipss.core_EMF` instead of
  local packages. No package name change needed — they import by class name.

### 8.2 ODM Integration

```
org.interpss.threePhase.odm                    ← STAYS
```

- `ODM3PhaseDStabParserMapper`
- `AbstractODM3PhaseDStabParserMapper`

### 8.3 Multi-Network Co-Simulation

```
org.interpss.multiNet.algo                     ← STAYS
org.interpss.multiNet.algo.powerflow           ← STAYS
org.interpss.multiNet.equivalent               ← STAYS
org.interpss.threeSeq.algo                     ← STAYS
```

- `MultiNetDStabSolverImpl`, `MultiNetDStabSimuHelper`
- `T3seqD3phaseMultiNetDStabSolverImpl`, `TposseqD3phaseMultiNetDStabSolverImpl`
- `MultiNetDynamicEventProcessor`
- `NetworkEquivalent`
- `DStab3SeqSolverImpl`

### 8.4 Tests

All test code stays in `ipss.plugin.3phase/src/test/`. Tests reference classes that
have moved to `ipss.core_EMF` via Maven dependency. No test logic changes needed.

## 9. Shared Utility: ThreePhaseYMatrixBuilder

**Location:** `com.interpss.core.abc.util` in `ipss.core_EMF`

Extract the duplicated Y-matrix assembly logic from both
`Static3PNetworkImpl` and `DStabNetwork3phaseImpl` into one utility.

```java
package com.interpss.core.abc.util;

public class ThreePhaseYMatrixBuilder {

    /**
     * Build 3-phase ABC Y-matrix.
     * @param buses active buses with YiiAbc data
     * @param branches active branches with Yft/Ytf data
     * @param includeDynamicDevices true for DStab, false for power flow / static
     */
    public static ISparseEqnComplexMatrix3x3 build(
            List<? extends IThreePhaseBus> buses,
            List<? extends IThreePhaseBranch> branches,
            boolean includeDynamicDevices) { ... }

    /**
     * Build power-flow-only Y-matrix (no dynamic device contributions).
     */
    public static ISparseEqnComplexMatrix3x3 buildForPowerflow(
            List<? extends IThreePhaseBus> buses,
            List<? extends IThreePhaseBranch> branches) { ... }
}
```

After extraction:
- `Static3PNetworkImpl.formYMatrixABC()` delegates to `ThreePhaseYMatrixBuilder.build(buses, branches, false)`
- `DStabNetwork3phaseImpl.formYMatrixABC()` delegates to `ThreePhaseYMatrixBuilder.build(buses, branches, true)`
- `DStabNetwork3phaseImpl.formYMatrixABCForPowerflow()` delegates to `ThreePhaseYMatrixBuilder.buildForPowerflow(buses, branches)`

## 10. Maven Dependency Changes

### ipss.core_EMF (pom.xml)

Add dependencies that were previously only needed by `ipss.plugin.3phase`:

```xml
<!-- OR-Tools for DistOPF -->
<dependency>
    <groupId>com.google.ortools</groupId>
    <artifactId>ortools-java</artifactId>
    <version>9.15.6755</version>
</dependency>

<!-- JUnit 5 for any core tests -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.11.4</version>
    <scope>test</scope>
</dependency>
```

Note: OR-Tools is a large native dependency. Consider making DistOPF solver adapters
optional (keep `ORToolsDistOpfSolver` behind a profile or optional dependency) if the
native library overhead is a concern for `ipss.core_EMF` consumers that don't need OPF.

### ipss.plugin.3phase (pom.xml)

- Remove `ipss.plugin.core` dependency if all code now depends on `ipss.core_EMF` transitively
- Keep `ipss.plugin.core` dependency if plugin-specific code still references it
- OR-Tools dependency moves to `ipss.core_EMF`

## 11. Two-Stage Approach

**Stage A — Refactor in place (this document).** All code stays in its current
modules and packages. We add interfaces, implement them on existing classes, and
migrate algorithms to use the interfaces. Each step is independently testable.

**Stage B — Move code (separate effort, after Stage A is stable).** Move model
and algorithm classes from `ipss.plugin.3phase` to `ipss.core_EMF` per Section 7.
Since all code already uses interfaces by then, the move is mechanical — copy files,
delete originals, update Maven dependencies.

This separation ensures:
- Every phase in Stage A compiles and passes tests on its own
- No phase mixes refactoring with file moves
- If a phase breaks something, the cause is obvious (only interface/impl changes,
  not file relocations)
- Stage B is a low-risk mechanical move after the architecture is proven

## 12. Stage A: Refactoring Phases

### Phase A1: Define Interfaces (ipss.core_EMF only, no behavior change)

**Files to create in ipss.core_EMF:**
- `com/interpss/core/abc/IThreePhaseNetwork.java`
- `com/interpss/core/abc/IThreePhaseBus.java`
- `com/interpss/core/abc/IThreePhaseBranch.java`
- `com/interpss/core/abc/IThreePhaseLoadAdapter.java`
- `com/interpss/core/abc/IThreePhaseGenAdapter.java`

**Files to update in ipss.core_EMF (interface extends only):**
- `INetwork3Phase.java` — add `extends IThreePhaseNetwork`
- `IBus3Phase.java` — add `extends IThreePhaseBus`
- `IBranch3Phase.java` — add `extends IThreePhaseBranch`
- `ILoad3Phase.java` — add `extends IThreePhaseLoadAdapter`
- `IGen3Phase.java` — add `extends IThreePhaseGenAdapter`

**Verification:** Build both modules. All existing tests pass.
No algorithm behavior has changed.

### Phase A2: Implement Interfaces on Existing Classes

All classes stay in their current modules and packages.

**ipss.core_EMF:**
- `Static3PNetworkImpl` — add missing `IThreePhaseNetwork` methods.
  Add `formYMatrixABCForPowerflow()` (delegates to `formYMatrixABC`).
- `Static3PBusImpl` — add missing `IThreePhaseBus` methods.
- `Static3PBranchImpl` — add missing `IThreePhaseBranch` methods.

**ipss.plugin.3phase:**
- `DStabNetwork3Phase` — add `extends IThreePhaseNetwork` to interface.
- `DStab3PBus` — add `extends IThreePhaseBus` to interface.
- `DStab3PBranch` — add `extends IThreePhaseBranch` to interface.
- `DStab3PLoad` — add `extends IThreePhaseLoadAdapter`.
- `DStab3PGen` — add `extends IThreePhaseGenAdapter`.

**Verification:** All existing tests pass. Both `Static3PNetwork` and
`DStabNetwork3Phase` now formally implement `IThreePhaseNetwork`.

### Phase A3: Extract Shared Y-Matrix Logic

**ipss.core_EMF:**
- Create `ThreePhaseYMatrixBuilder` in `com.interpss.core.abc.util`.

**ipss.core_EMF:**
- `Static3PNetworkImpl.formYMatrixABC()` — delegate to builder.

**ipss.plugin.3phase:**
- `DStabNetwork3phaseImpl.formYMatrixABC()` — delegate to builder.
- `DStabNetwork3phaseImpl.formYMatrixABCForPowerflow()` — delegate to builder.

**Verification:** All existing tests pass with identical results. Y-matrix
assembly is now in one place.

### Phase A4: Migrate DistributionPowerFlowAlgorithm

All files stay in `org.interpss.threePhase.powerflow` in `ipss.plugin.3phase`.

- `DistributionPowerFlowAlgorithm` interface — accept `IThreePhaseNetwork`.
- `DistributionPowerFlowAlgorithmImpl` — replace all `(DStab3PBus)` casts with
  interface method calls on `IThreePhaseBus`. Replace all `(DStab3PBranch)` casts
  with interface method calls on `IThreePhaseBranch`.

This is the largest refactor. The power flow impl currently has ~50+ casts.
Each cast site is replaced with an interface call.

**Verification:** All existing distribution power flow tests pass on
`DStabNetwork3Phase`. New test: run power flow on `Static3PNetwork` to verify
interface completeness.

### Phase A5: Migrate DistOPF Classes

All files stay in `org.interpss.threePhase.opf.dist` in `ipss.plugin.3phase`.

- `DistOpfModelDataExtractor.extract()` — change parameter from
  `DStabNetwork3Phase` to `IThreePhaseNetwork`. Replace concrete type references
  with interface calls.
- `DistOpfAlgorithmImpl` — change field and constructor to `IThreePhaseNetwork`.
- `DistOpfResult.applySetpointsToNetwork()` — change parameter to
  `IThreePhaseNetwork`.
- `DistOpfPowerFlowValidation` — change parameters to `IThreePhaseNetwork`.

**Verification:** All DistOPF tests pass on `DStabNetwork3Phase`.
New test: DistOPF on `Static3PNetwork` to verify interface completeness.

### Phase A6: Verification

- Full test suite across all modules.
- Verify dynamic simulation end-to-end (power flow → initDStab → solveNetEqn
  on the same `DStabNetwork3Phase` object).
- Remove dead code exposed by the refactoring.

## 13. Stage B: Code Move (After Stage A is Stable)

Separate effort. Not detailed here — see Section 7 for the full file list.

Steps:
1. Copy files from `ipss.plugin.3phase` to `ipss.core_EMF`, same package names.
2. Delete originals from `ipss.plugin.3phase`.
3. Update `ipss.core_EMF/pom.xml` dependencies (OR-Tools, etc.).
4. Update `ipss.plugin.3phase/pom.xml` to depend on `ipss.core_EMF`.
5. Full test suite.

Risk is low because all code already uses interfaces after Stage A.
The move is mechanical file relocation.

## 14. File Count Summary (Stage A Only)

### ipss.core_EMF

| Category | New Files | Modified Files |
|---|---|---|
| Interfaces | 5 (`IThreePhase*`, adapters) | 5 (`INetwork3Phase`, `IBus3Phase`, `IBranch3Phase`, `ILoad3Phase`, `IGen3Phase`) |
| Y-matrix builder | 1 (`ThreePhaseYMatrixBuilder`) | 0 |
| Static3P impls | 0 | 3 (`Static3PNetworkImpl`, `Static3PBusImpl`, `Static3PBranchImpl`) |
| **Total** | **6** | **8** |

### ipss.plugin.3phase

| Category | Modified Files |
|---|---|
| 3P interfaces (add extends) | 5 (`DStabNetwork3Phase`, `DStab3PBus`, `DStab3PBranch`, `DStab3PLoad`, `DStab3PGen`) |
| DStab impl (Y-matrix delegate) | 1 (`DStabNetwork3phaseImpl`) |
| Power flow (interface migration) | 2 (`DistributionPowerFlowAlgorithm`, `DistributionPowerFlowAlgorithmImpl`) |
| DistOPF (interface migration) | ~5 (`DistOpfAlgorithmImpl`, `DistOpfModelDataExtractor`, `DistOpfResult`, `DistOpfPowerFlowValidation`, `DistOpfResultMapper`) |
| **Total** | **~13** |

### Test Changes

No test logic changes. All existing tests pass at every phase.

New tests to add:
- Power flow on `Static3PNetwork` (Phase A4 verification)
- DistOPF on `Static3PNetwork` (Phase A5 verification)

## 15. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| EMF-generated code may conflict with new interface methods | New interfaces are standalone (not EMF-generated). Existing `@generated` interfaces get `extends` added via `@generated NOT` blocks. |
| `List<DStab3PBus>` return types don't satisfy `List<? extends IThreePhaseBus>` | They do satisfy it — `DStab3PBus implements IThreePhaseBus`, so `List<DStab3PBus>` is assignable to `List<? extends IThreePhaseBus>`. |
| Power flow and OPF may need methods not yet in the interface | Interface can be extended incrementally. Start with the intersection, add methods as migration proceeds. Each phase is independently testable. |
| `getThreePhaseBusList()` on `DStabNetwork3Phase` returns `List<DStab3PBus>` from `BaseDStabNetwork.getBusList()` | Add a default method: `default List<? extends IThreePhaseBus> getThreePhaseBusList() { return (List) getBusList(); }` — safe because `DStab3PBus implements IThreePhaseBus`. |
| Dynamic simulation code accidentally broken | No DStab behavior changes in any phase. `DStabNetwork3Phase` never changes its `BaseDStabNetwork` parent. All dynamic simulation tests must pass at every phase. |
| Phase A4 power flow refactor is large (~50 casts) | Each cast replacement is mechanical. Replace, compile, test. If a method is missing from the interface, add it and recompile. |

## 16. Dependency Order (Stage A)

```
Phase A1 (interfaces)     ← no dependencies, pure addition
    ↓
Phase A2 (implement)      ← depends on A1 interfaces existing
    ↓
Phase A3 (Y-matrix)       ← depends on A2 implementations
    ↓
Phase A4 (power flow)     ← depends on A2
    ↓                        (independent of A3, can proceed in parallel)
Phase A5 (DistOPF)        ← depends on A2
    ↓                        (independent of A3/A4)
Phase A6 (verification)   ← depends on A4 + A5
```

Phase A4 and Phase A5 are independent of each other and can proceed in parallel.
