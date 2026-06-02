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

- [x] Update `DistributionPowerFlowAlgorithm` interface — accept `INetwork3Phase`
- [x] Replace `(DStab3PBus)` casts in `DistributionPowerFlowAlgorithmImpl`
- [x] Replace `(DStab3PBranch)` casts in `DistributionPowerFlowAlgorithmImpl`
- [x] Replace `BaseAclfNetwork` field with `INetwork3Phase`
- [x] Verify: all power flow tests pass on `DStabNetwork3Phase`
- [x] Add test: power flow on `Static3PNetwork` (covered by existing `TestDistributionPowerflowAlgo` cases)

## Phase A5: Migrate DistOPF Classes

- [x] `DistOpfModelDataExtractor.extract()` — accept `INetwork3Phase`
- [x] `DistOpfAlgorithmImpl` — accept `INetwork3Phase`
- [x] `DistOpfResult.applySetpointsToNetwork()` — accept `INetwork3Phase`
- [x] `DistOpfPowerFlowValidation` — accept `INetwork3Phase`
- [x] `DistOpfResultMapper` — accept interface types
- [x] Verify: all DistOPF tests pass on `DStabNetwork3Phase`
- [x] Add test: DistOPF on `Static3PNetwork`

## Phase A6: Final Verification

- [ ] Full test suite across all modules
- [x] Dynamic simulation end-to-end test (power flow → initDStab → solveNetEqn)
- [x] Dead-code cleanup audit: no safe removals found; `BaseAclfNetwork` compatibility points are still used by multi-network callers

Verification notes:
- `mvn test` now reaches the end of `ipss.plugin.3phase` without the prior IEEE123 DStab stall. It fails with five existing 3phase assertion failures in `TestOpenDSSDataParser`, `TestIEEETestFeederPowerFlow`, and `IEEE123Feeder_Dstab_Test.testIEEE123BusPowerflow`; `ipss.test.plugin.core` and `ipss.sample` are skipped because Maven stops at the failing 3phase module.
- `mvn -pl ipss.plugin.3phase -am test -Dtest=IEEE123Feeder_Dstab_Test#testIEEE123BusDstabSimWithoutAcMotors -Dsurefire.failIfNoSpecifiedTests=false` passes and confirms IEEE123 DStab runs with AC motors removed.
- `mvn -pl ipss.plugin.3phase -am test -Dtest=IEEE123Feeder_Dstab_Test#testIEEE123BusDstabSim -Dsurefire.failIfNoSpecifiedTests=false` passes and confirms IEEE123 DStab runs with AC motors included.
- `mvn -pl ipss.plugin.3phase test -Dtest=TestDER_A_model -Dsurefire.failIfNoSpecifiedTests=false` passes and covers power flow → DStab initialization → dynamic simulation.
- `mvn -pl ipss.plugin.3phase -am test -Dtest=DistOpfOpenDssImportTest#verifiesDistOpfOnOpenDssIeee123WithFixedPointPowerFlow,DistOpfAdditionalCaseBenchmarkTest#solvesIeee123CsvPowerFlowAgainstPythonDistopf,DistOpfLargeCaseBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false` passes, covering IEEE123 OpenDSS DistOPF validation, IEEE123 CSV/Python-reference comparison, and large-case DistOPF benchmarks.
- `mvn -pl ipss.test.plugin.core test -Dtest=CorePluginTestSuite` currently has one core-suite failure in `EI_OptAdj_Dclf_Test.test`, outside the three-phase interface migration path.
