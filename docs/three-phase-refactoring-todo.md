# Three-Phase Interface Refactoring — TODO

Branch: `3phase-refactoring`
Plan: `docs/three-phase-interface-refactoring-plan.md`

## Phase A1: Define Interfaces

- [ ] Create `IThreePhaseNetwork.java` in `com.interpss.core.abc` (ipss.core_EMF)
- [ ] Create `IThreePhaseBus.java` in `com.interpss.core.abc` (ipss.core_EMF)
- [ ] Create `IThreePhaseBranch.java` in `com.interpss.core.abc` (ipss.core_EMF)
- [ ] Create `IThreePhaseLoadAdapter.java` in `com.interpss.core.abc` (ipss.core_EMF)
- [ ] Create `IThreePhaseGenAdapter.java` in `com.interpss.core.abc` (ipss.core_EMF)
- [ ] Update `INetwork3Phase.java` — add `extends IThreePhaseNetwork`
- [ ] Update `IBus3Phase.java` — add `extends IThreePhaseBus`
- [ ] Update `IBranch3Phase.java` — add `extends IThreePhaseBranch`
- [ ] Update `ILoad3Phase.java` — add `extends IThreePhaseLoadAdapter`
- [ ] Update `IGen3Phase.java` — add `extends IThreePhaseGenAdapter`
- [ ] Verify: build ipss.core_EMF and ipss.plugin.3phase, all tests pass

## Phase A2: Implement Interfaces on Existing Classes

- [ ] `Static3PNetworkImpl` — implement missing `IThreePhaseNetwork` methods
- [ ] `Static3PBusImpl` — implement missing `IThreePhaseBus` methods
- [ ] `Static3PBranchImpl` — implement missing `IThreePhaseBranch` methods
- [ ] `DStabNetwork3Phase` — add `extends IThreePhaseNetwork`
- [ ] `DStab3PBus` — add `extends IThreePhaseBus`
- [ ] `DStab3PBranch` — add `extends IThreePhaseBranch`
- [ ] `DStab3PLoad` — add `extends IThreePhaseLoadAdapter`
- [ ] `DStab3PGen` — add `extends IThreePhaseGenAdapter`
- [ ] Verify: all existing tests pass

## Phase A3: Extract Shared Y-Matrix Logic

- [ ] Create `ThreePhaseYMatrixBuilder.java` in `com.interpss.core.abc.util`
- [ ] Refactor `Static3PNetworkImpl.formYMatrixABC()` to delegate
- [ ] Refactor `DStabNetwork3phaseImpl.formYMatrixABC()` to delegate
- [ ] Refactor `DStabNetwork3phaseImpl.formYMatrixABCForPowerflow()` to delegate
- [ ] Verify: all existing tests pass with identical results

## Phase A4: Migrate DistributionPowerFlowAlgorithm

- [ ] Update `DistributionPowerFlowAlgorithm` interface — accept `IThreePhaseNetwork`
- [ ] Replace `(DStab3PBus)` casts in `DistributionPowerFlowAlgorithmImpl`
- [ ] Replace `(DStab3PBranch)` casts in `DistributionPowerFlowAlgorithmImpl`
- [ ] Replace `BaseAclfNetwork` field with `IThreePhaseNetwork`
- [ ] Verify: all power flow tests pass on `DStabNetwork3Phase`
- [ ] Add test: power flow on `Static3PNetwork`

## Phase A5: Migrate DistOPF Classes

- [ ] `DistOpfModelDataExtractor.extract()` — accept `IThreePhaseNetwork`
- [ ] `DistOpfAlgorithmImpl` — accept `IThreePhaseNetwork`
- [ ] `DistOpfResult.applySetpointsToNetwork()` — accept `IThreePhaseNetwork`
- [ ] `DistOpfPowerFlowValidation` — accept `IThreePhaseNetwork`
- [ ] `DistOpfResultMapper` — accept interface types
- [ ] Verify: all DistOPF tests pass on `DStabNetwork3Phase`
- [ ] Add test: DistOPF on `Static3PNetwork`

## Phase A6: Final Verification

- [ ] Full test suite across all modules
- [ ] Dynamic simulation end-to-end test (power flow → initDStab → solveNetEqn)
- [ ] Remove dead code exposed by refactoring
