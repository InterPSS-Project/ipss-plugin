# Three-Phase Interface Refactoring — TODO

Branch: `3phase-refactoring`
Plan: `docs/three-phase-interface-refactoring-plan.md`

## Phase A1: Enrich Existing Interfaces + Package Rename

- [x] Enrich `INetwork3Phase.java` with getBaseMva, getThreePhaseBusList, getThreePhaseBranchList, formYMatrixABCForPowerflow, getYMatrixABCForPowerflow
- [x] Enrich `IBus3Phase.java` with identity, voltage, admittance, load, and limit methods
- [x] Enrich `IBranch3Phase.java` with 3-phase-specific methods only (no identity/transformer — inherited from Branch hierarchy)
- [x] Enrich `ILoad3Phase.java` with getCode, getInit3PhaseLoad, getPhaseCode, getLoadConnectionType
- [x] Enrich `IGen3Phase.java` with getId, getPower3Phase, getMvaBase
- [x] Add impl stubs to `Static3PBusImpl`, `Static3PGenImpl`, `Static3PLoadImpl`
- [x] `getLoadConnectionType()` default = `THREE_PHASE_WYE`
- [x] Rename package `com.interpss.core.abc` → `com.interpss.core.threephase` in ipss.core_EMF (66 files)
- [x] Update imports in ipss.plugin.3phase (21 files)
- [x] Build ipss.core_EMF — compiles clean
- [x] Build ipss.plugin.3phase — compiles clean
- [x] Run tests — 22 pass, 4 pre-existing failures unrelated to changes

## Phase A2: Implement Interfaces on Existing Classes

- [ ] `Static3PNetworkImpl` — implement missing `INetwork3Phase` methods (getThreePhaseBusList, getThreePhaseBranchList, formYMatrixABCForPowerflow)
- [ ] `Static3PBusImpl` — fill in real implementations for stubs
- [ ] `DStab3PLoad` — verify `getLoadConnectionType()` is implemented
- [ ] `DStab3PGen` — verify `getPower3Phase(UnitType)` is implemented
- [ ] Verify: all existing tests pass

## Phase A3: Extract Shared Y-Matrix Logic

- [ ] Create `ThreePhaseYMatrixBuilder.java` in `com.interpss.core.threephase.util`
- [ ] Refactor `Static3PNetworkImpl.formYMatrixABC()` to delegate
- [ ] Refactor `DStabNetwork3phaseImpl.formYMatrixABC()` to delegate
- [ ] Refactor `DStabNetwork3phaseImpl.formYMatrixABCForPowerflow()` to delegate
- [ ] Verify: all existing tests pass with identical results

## Phase A4: Migrate DistributionPowerFlowAlgorithm

- [ ] Update `DistributionPowerFlowAlgorithm` interface — accept `INetwork3Phase`
- [ ] Replace `(DStab3PBus)` casts in `DistributionPowerFlowAlgorithmImpl`
- [ ] Replace `(DStab3PBranch)` casts in `DistributionPowerFlowAlgorithmImpl`
- [ ] Replace `BaseAclfNetwork` field with `INetwork3Phase`
- [ ] Verify: all power flow tests pass on `DStabNetwork3Phase`
- [ ] Add test: power flow on `Static3PNetwork`

## Phase A5: Migrate DistOPF Classes

- [ ] `DistOpfModelDataExtractor.extract()` — accept `INetwork3Phase`
- [ ] `DistOpfAlgorithmImpl` — accept `INetwork3Phase`
- [ ] `DistOpfResult.applySetpointsToNetwork()` — accept `INetwork3Phase`
- [ ] `DistOpfPowerFlowValidation` — accept `INetwork3Phase`
- [ ] `DistOpfResultMapper` — accept interface types
- [ ] Verify: all DistOPF tests pass on `DStabNetwork3Phase`
- [ ] Add test: DistOPF on `Static3PNetwork`

## Phase A6: Final Verification

- [ ] Full test suite across all modules
- [ ] Dynamic simulation end-to-end test (power flow → initDStab → solveNetEqn)
- [ ] Remove dead code exposed by refactoring
