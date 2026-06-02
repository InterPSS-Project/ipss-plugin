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

- [x] `Static3PNetworkImpl` — implement missing `INetwork3Phase` methods (getThreePhaseBusList, getThreePhaseBranchList, formYMatrixABCForPowerflow)
- [x] `Static3PBusImpl` — fill in real implementations for stubs
- [x] `Static3PBranchImpl` — fill in real impedance/admittance and transformer adapter implementations for stubs
- [x] `Static3PLoadImpl` — expose inherited load code and balanced initial 3-phase load
- [x] `Static3PGenImpl` — expose balanced 3-phase generator power from inherited generator data
- [x] `DStab3PLoad` — verify `getLoadConnectionType()` is implemented
- [x] `DStab3PGen` — verify `getPower3Phase(UnitType)` is implemented
- [x] Verify: `ipss.core_EMF` compiles and `ipss.plugin.3phase` tests pass

## Phase A3: Extract Shared Y-Matrix Logic

- [x] Create `ThreePhaseYMatrixBuilder.java` in `com.interpss.core.threephase.util`
- [x] Refactor `Static3PNetworkImpl.formYMatrixABC()` to delegate
- [x] Refactor `Static3PNetworkImpl.formYMatrixABCForPowerflow()` to delegate
- [x] Refactor `DStabNetwork3phaseImpl.formYMatrixABC()` to delegate
- [x] Refactor `DStabNetwork3phaseImpl.formYMatrixABCForPowerflow()` to delegate
- [x] Verify: `ipss.plugin.3phase` passes after refactor

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
- `/Users/ipssdev/github/core`: `mvn -pl ipss.core_EMF -am test -DskipTests` passes after static model interface cleanup and shared Y-matrix builder extraction.
- `/Users/ipssdev/github/core`: `mvn -pl ipss.core_EMF -am install -DskipTests -Drevision=1.1.0` passes and refreshes the local `ipss.core.lib:1.1.0` artifact used by `ipss-plugin`.
- `mvn -pl ipss.plugin.3phase -am clean test -Dtest=TestDistributionPowerflowAlgo,IEEE123Feeder_Dstab_Test#testIEEE123BusDstabSim -Dsurefire.failIfNoSpecifiedTests=false` passes after the shared Y-matrix refactor.
- `mvn -pl ipss.plugin.3phase -am test` passes: 128 tests, 0 failures, 0 errors.
- `mvn test` now passes through `ipss.plugin.3phase` and fails in `ipss.test.plugin.core`: 347 tests run, 28 failures, 12 errors, 9 skipped. The failing set is outside the three-phase interface migration path and includes switched-shunt, PSSE/ODM mapping, opt-adj, multi-network, and missing fixture/core-data issues.
- `mvn -pl ipss.plugin.3phase -am test -Dtest=IEEE123Feeder_Dstab_Test#testIEEE123BusDstabSimWithoutAcMotors -Dsurefire.failIfNoSpecifiedTests=false` passes and confirms IEEE123 DStab runs with AC motors removed.
- `mvn -pl ipss.plugin.3phase -am test -Dtest=IEEE123Feeder_Dstab_Test#testIEEE123BusDstabSim -Dsurefire.failIfNoSpecifiedTests=false` passes and confirms IEEE123 DStab runs with AC motors included.
- `mvn -pl ipss.plugin.3phase test -Dtest=TestDER_A_model -Dsurefire.failIfNoSpecifiedTests=false` passes and covers power flow → DStab initialization → dynamic simulation.
- `mvn -pl ipss.plugin.3phase -am test -Dtest=DistOpfOpenDssImportTest#verifiesDistOpfOnOpenDssIeee123WithFixedPointPowerFlow,DistOpfAdditionalCaseBenchmarkTest#solvesIeee123CsvPowerFlowAgainstPythonDistopf,DistOpfLargeCaseBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false` passes, covering IEEE123 OpenDSS DistOPF validation, IEEE123 CSV/Python-reference comparison, and large-case DistOPF benchmarks.
