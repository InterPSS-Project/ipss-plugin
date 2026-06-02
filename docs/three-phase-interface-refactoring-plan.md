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
preconditions and applies to any `INetwork3Phase` that satisfies them:

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
com.interpss.core.threephase                        ← 3-phase interfaces + Static3P model
    INetwork3Phase, IBus3Phase, IBranch3Phase       ← generic 3-phase interfaces
    ILoad3Phase, IGen3Phase                          ← load/gen adapters
    Static3PNetwork, Static3PBus, Static3PBranch     ← Static3P implementations
    LoadConnectionType, PhaseType, Load3PhaseType    ← enums

com.interpss.threePhase.basic.dstab            ← DStab3P model (MOVED from plugin)
    DStab3PBus, DStab3PBranch, DStab3PLoad, DStab3PGen

com.interpss.threePhase.dynamic                ← 3-phase DStab network (MOVED from plugin)
    DStabNetwork3Phase, DStabNetwork3phaseImpl

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

## 4. Target Architecture

```
                    INetwork3Phase (generic 3-phase model interface)
                    IBus3Phase     (generic 3-phase bus interface)
                    IBranch3Phase  (generic 3-phase branch interface)
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
                        INetwork3Phase
DistOPF       ──→                        ──→  Transmission 3-phase PF
                              ↑
                   DStabNetwork3Phase (also BaseDStabNetwork)
                              ↑
                     3-phase Dynamic Simulation
```

Key properties:
- `DStabNetwork3Phase` still extends `BaseDStabNetwork`. No DStab API is lost.
- Power flow results are stored directly on `DStab3PBus` objects through
  `IBus3Phase.set3PhaseVotlages()` — same object, no copying.
- `INetwork3Phase` is not distribution-specific. It is a generic unbalanced 3-phase
  network model that works for both transmission and distribution.
- `IBranch3Phase` only declares 3-phase-specific methods. Identity methods (getId,
  isActive, isXfr, getFromTurnRatio, getToTurnRatio, getRatingMva1, getFromBus,
  getToBus) are inherited from the Branch/AclfBranch hierarchy — not redeclared to
  avoid Java return-type conflicts (e.g. Branch.getCircuitNumber() returns String
  while IBranch3Phase would have returned int).

## 5. Interface Definitions (Phase A1 — Complete)

All interfaces are in `com.interpss.core.threephase` in `ipss.core_EMF`.
They extend `EObject` (EMF requirement). Each interface contains ALL method contracts
directly — no secondary interface hierarchy.

### 5.1 INetwork3Phase

```java
package com.interpss.core.threephase;

public interface INetwork3Phase extends EObject {
    double getBaseMva();
    List<? extends IBus3Phase> getThreePhaseBusList();
    List<? extends IBranch3Phase> getThreePhaseBranchList();

    ISparseEqnComplexMatrix3x3 formYMatrixABC() throws IpssNumericException;
    ISparseEqnComplexMatrix3x3 getYMatrixABC();
    ISparseEqnComplexMatrix3x3 formYMatrixABCForPowerflow() throws IpssNumericException;
    ISparseEqnComplexMatrix3x3 getYMatrixABCForPowerflow();

    boolean run3PhasePowerflow();
}
```

### 5.2 IBus3Phase

```java
package com.interpss.core.threephase;

public interface IBus3Phase extends EObject {
    // Identity (compatible with Bus hierarchy — same return types)
    String getId();
    boolean isActive();
    boolean isSwing();
    boolean isGen();
    boolean isLoad();
    double getBaseVoltage();

    // Voltages (power flow writes, everyone reads)
    Complex3x1 get3PhaseVotlages();
    void set3PhaseVotlages(Complex3x1 vabc);

    // Bus admittance
    Complex3x3 getYiiAbc();               // includes dynamic devices
    Complex3x3 getYiiAbcForPowerflow();   // static only

    // Load data (read by power flow and OPF)
    Complex3x1 get3PhaseTotalLoad();
    List<? extends ILoad3Phase> getThreePhaseLoadList();
    List<? extends IGen3Phase> getThreePhaseGenList();

    // Limits (read by power flow violation check and OPF)
    LimitType getVLimit();
}
```

### 5.3 IBranch3Phase

```java
package com.interpss.core.threephase;

public interface IBranch3Phase extends EObject {
    // 3-phase specific only — identity/transformer methods inherited from Branch/AclfBranch
    PhaseCode getPhaseCode();
    void setZabc(Complex3x3 zabc);
    void setZabc(Complex z1, Complex z2, Complex z3);
    Complex3x3 getZabc();
    Complex3x3 getBranchYabc();
    Complex3x3 getYffabc();
    Complex3x3 getYttabc();
    Complex3x3 getYftabc();
    Complex3x3 getYtfabc();
    double getXfrRatedKVA();
}
```

Note: Methods like `getId()`, `isActive()`, `getCircuitNumber()`, `isXfr()`,
`getFromTurnRatio()`, `getToTurnRatio()`, `getRatingMva1()`, `getFromBus()`,
`getToBus()` are NOT in `IBranch3Phase` because they conflict with the
`Branch`/`AclfBranch` hierarchy (different return types, e.g. `Branch.getCircuitNumber()`
returns `String`). Algorithms access these through the concrete types or cast.

### 5.4 ILoad3Phase

```java
package com.interpss.core.threephase;

public interface ILoad3Phase extends EObject {
    AclfLoadCode getCode();
    Complex3x1 getInit3PhaseLoad();
    PhaseCode getPhaseCode();
    LoadConnectionType getLoadConnectionType();  // default: THREE_PHASE_WYE
}
```

### 5.5 IGen3Phase

```java
package com.interpss.core.threephase;

public interface IGen3Phase extends EObject {
    String getId();
    Complex3x1 getPower3Phase(UnitType unit);
    double getMvaBase();
}
```

## 6. Phase A1 Status — Complete

Phase A1 changes (all in `com.interpss.core.threephase` in `ipss.core_EMF`):

| File | Change |
|---|---|
| `INetwork3Phase.java` | Added getBaseMva, getThreePhaseBusList, getThreePhaseBranchList, formYMatrixABCForPowerflow, getYMatrixABCForPowerflow |
| `IBus3Phase.java` | Added getId, isActive, isSwing, isGen, isLoad, getBaseVoltage, getYiiAbcForPowerflow, get3PhaseTotalLoad, getThreePhaseLoadList, getThreePhaseGenList, getVLimit |
| `IBranch3Phase.java` | Added getPhaseCode, setZabc, getZabc, getBranchYabc, getYffabc, getYttabc, getYftabc, getYtfabc, getXfrRatedKVA (only 3-phase-specific methods) |
| `ILoad3Phase.java` | Added getCode, getInit3PhaseLoad, getPhaseCode, getLoadConnectionType |
| `IGen3Phase.java` | Added getId, getPower3Phase, getMvaBase |
| `Static3PBusImpl.java` | Added stubs for getYiiAbcForPowerflow, get3PhaseTotalLoad, getThreePhaseLoadList, getThreePhaseGenList |
| `Static3PGenImpl.java` | Added stub for getPower3Phase |
| `Static3PLoadImpl.java` | Added getLoadConnectionType returning THREE_PHASE_WYE |

Package renamed from `com.interpss.core.abc` to `com.interpss.core.threephase` across
both `ipss.core_EMF` and `ipss.plugin.3phase`.

**Verification:** Both repos compile. 22 tests pass (IEEE_13BusFeeder, DistOpfOpenDss,
TestDistributionPowerflowAlgo, ThreeBus_3Phase, DStab3PLoadModel, TestTposD3phaseDStab).
4 pre-existing test failures unrelated to changes.

## 7. Stage A: Remaining Phases

### Phase A2: Implement Interfaces on Existing Classes

**ipss.core_EMF:**
- `Static3PNetworkImpl` — add missing INetwork3Phase methods.
  Add `formYMatrixABCForPowerflow()` (delegates to `formYMatrixABC`).
- `Static3PBusImpl` — fill in real implementations for stubs added in A1.

**ipss.plugin.3phase:**
- `DStabNetwork3Phase` — already implements `INetwork3Phase`.
- `DStab3PBus` — already implements `IBus3Phase`.
- `DStab3PBranch` — already implements `IBranch3Phase`.
- `DStab3PLoad` — add `getLoadConnectionType()` if not already present.
- `DStab3PGen` — add `getPower3Phase(UnitType)` if not already present.

### Phase A3: Extract Shared Y-Matrix Logic

**ipss.core_EMF:**
- Create `ThreePhaseYMatrixBuilder` in `com.interpss.core.threephase.util`.
- `Static3PNetworkImpl.formYMatrixABC()` — delegate to builder.

**ipss.plugin.3phase:**
- `DStabNetwork3phaseImpl.formYMatrixABC()` — delegate to builder.
- `DStabNetwork3phaseImpl.formYMatrixABCForPowerflow()` — delegate to builder.

### Phase A4: Migrate DistributionPowerFlowAlgorithm

- `DistributionPowerFlowAlgorithm` interface — accept `INetwork3Phase`.
- `DistributionPowerFlowAlgorithmImpl` — replace concrete type casts with interface calls.

### Phase A5: Migrate DistOPF Classes

- `DistOpfModelDataExtractor.extract()` — change parameter from
  `DStabNetwork3Phase` to `INetwork3Phase`.
- `DistOpfAlgorithmImpl`, `DistOpfResult`, etc. — same migration.

### Phase A6: Verification

- Full test suite across all modules.
- Verify dynamic simulation end-to-end.

## 8. Stage B: Code Move (After Stage A is Stable)

Separate effort. Move model and algorithm classes from `ipss.plugin.3phase` to
`ipss.core_EMF`. Since all code already uses interfaces, the move is mechanical.
