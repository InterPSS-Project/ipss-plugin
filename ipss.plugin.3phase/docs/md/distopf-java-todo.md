# Java DistOPF Implementation TODO

This checklist tracks implementation of Java-native DistOPF for InterPSS using the existing OPF collector/objective/solver-adapter style.

## Milestone 1: Project Structure

- [ ] Create package `org.interpss.threePhase.opf.dist`.
- [ ] Create subpackage `org.interpss.threePhase.opf.dist.constraint`.
- [ ] Create subpackage `org.interpss.threePhase.opf.dist.objective`.
- [ ] Create subpackage `org.interpss.threePhase.opf.dist.solver`.
- [ ] Create subpackage `org.interpss.threePhase.opf.dist.util`.
- [ ] Add `DistOpfAlgorithm`.
- [ ] Add `DistOpfOptions`.
- [ ] Add `DistOpfObjective`.
- [ ] Add `DistOpfControlMode`.
- [ ] Add `DistOpfStatus`.
- [ ] Add factory method `createDistOpfAlgorithm(DStabNetwork3Phase net)`.
- [ ] Add a minimal smoke test that constructs the algorithm from a three-phase network.

## Milestone 2: Shared Optimization Model

- [ ] Decide whether `OpfConstraint` can be reused directly from `org.interpss.plugin.opf.constraint`.
- [ ] If direct reuse is clean, document the dependency and use `OpfConstraint`.
- [ ] If direct reuse is not clean, create `DistOpfConstraint` with the same sparse-row shape.
- [ ] Add sparse row helper methods for equality constraints.
- [ ] Add sparse row helper methods for less-than constraints.
- [ ] Add sparse row helper methods for greater-than constraints.
- [ ] Add sparse row helper methods for bounded row constraints.
- [ ] Add `DistOpfModel`.
- [ ] Add `DistOpfVariableIndex`.
- [ ] Add variable-index support for branch phase active power `Pij`.
- [ ] Add variable-index support for branch phase reactive power `Qij`.
- [ ] Add variable-index support for bus phase squared voltage `V2`.
- [ ] Add variable-index support for DER active power `Pg`.
- [ ] Add variable-index support for DER reactive power `Qg`.
- [ ] Add variable-index support for curtailment variables.
- [ ] Add variable lower-bound storage.
- [ ] Add variable upper-bound storage.
- [ ] Add unit tests for deterministic variable ordering.

## Milestone 3: Network Extraction

- [ ] Add `DistOpfModelData`.
- [ ] Add `DistOpfModelDataExtractor`.
- [ ] Extract base MVA.
- [ ] Extract voltage bases.
- [ ] Identify swing/source bus.
- [ ] Extract active buses.
- [ ] Extract active branches.
- [ ] Extract bus phase sets.
- [ ] Extract branch phase sets.
- [ ] Extract parent/child topology from the source bus.
- [ ] Validate that the network is connected.
- [ ] Validate that the v1 network is radial.
- [ ] Extract per-branch 3x3 phase impedance matrices.
- [ ] Extract fixed bus phase P loads.
- [ ] Extract fixed bus phase Q loads.
- [ ] Extract fixed capacitor Q injections.
- [ ] Extract fixed regulator ratios.
- [ ] Extract DER active-power limits.
- [ ] Extract DER reactive-power limits.
- [ ] Extract inverter apparent-power limits where available.
- [ ] Extract bus phase voltage limits.
- [ ] Extract branch thermal ratings where available.
- [ ] Normalize extracted values to per-unit.
- [ ] Exclude dynamic generator admittance contributions.
- [ ] Exclude dynamic load admittance contributions.
- [ ] Exclude induction motor equivalent admittance contributions.
- [ ] Add extraction tests for a small three-phase feeder.
- [ ] Add extraction tests for a feeder with missing phases.

## Milestone 4: Constraint Collectors

- [ ] Add `IDistOpfConstraintCollector`.
- [ ] Add `BaseDistOpfConstraintCollector`.
- [ ] Add `DistPowerBalanceConstraintCollector`.
- [ ] Add active-power balance equations for each non-source bus phase.
- [ ] Add `DistReactivePowerBalanceConstraintCollector`.
- [ ] Add reactive-power balance equations for each non-source bus phase.
- [ ] Add fixed capacitor injection handling in reactive-power balance.
- [ ] Add `DistVoltageDropConstraintCollector`.
- [ ] Add voltage-drop equations for one-phase branches.
- [ ] Add voltage-drop equations for two-phase branches.
- [ ] Add voltage-drop equations for three-phase branches.
- [ ] Add full phase-coupled R/X impedance terms.
- [ ] Add `DistSwingVoltageConstraintCollector`.
- [ ] Add source voltage equality constraints.
- [ ] Add `DistVoltageLimitConstraintCollector`.
- [ ] Add squared-voltage lower bounds.
- [ ] Add squared-voltage upper bounds.
- [ ] Add `DistDerLimitConstraintCollector`.
- [ ] Fix DER variables when `DistOpfControlMode.NONE` is selected.
- [ ] Enable active-power controls when `DistOpfControlMode.P` is selected.
- [ ] Enable reactive-power controls when `DistOpfControlMode.Q` is selected.
- [ ] Enable P/Q controls when `DistOpfControlMode.PQ` is selected.
- [ ] Add `DistInverterCapabilityConstraintCollector`.
- [ ] Add octagonal inverter capability approximation.
- [ ] Add `DistBranchThermalLimitConstraintCollector`.
- [ ] Add octagonal branch apparent-power approximation.
- [ ] Add collector tests that verify sparse row coefficients.

## Milestone 5: Objective Collectors

- [ ] Add `BaseDistOpfObjectiveCollector`.
- [ ] Add `CurtailmentMinObjectiveCollector`.
- [ ] Add curtailment minimization objective coefficients.
- [ ] Add `GenMaxObjectiveCollector`.
- [ ] Add generation maximization as negative minimization coefficients.
- [ ] Add `TargetSubstationPObjectiveCollector`.
- [ ] Add positive and negative P target deviation variables.
- [ ] Add target substation P objective coefficients.
- [ ] Add `TargetSubstationQObjectiveCollector`.
- [ ] Add positive and negative Q target deviation variables.
- [ ] Add target substation Q objective coefficients.
- [ ] Add `LossMinObjectiveCollector`.
- [ ] Verify ojAlgo convex QP support before enabling quadratic loss objective.
- [ ] If QP support is insufficient, add documented linear loss approximation.
- [ ] Add objective-vector unit tests.

## Milestone 6: Solver Adapter

- [ ] Add `DistOpfSolver`.
- [ ] Add `DistOpfSolverResult`.
- [ ] Add `OjAlgoDistOpfSolver`.
- [ ] Configure `OjAlgoDistOpfSolver` as the default solver for small systems.
- [ ] Convert sparse equality constraints to ojAlgo input.
- [ ] Convert sparse inequality constraints to ojAlgo input.
- [ ] Convert variable lower bounds to ojAlgo input.
- [ ] Convert variable upper bounds to ojAlgo input.
- [ ] Convert linear objective coefficients to ojAlgo input.
- [ ] Map ojAlgo optimal status to `DistOpfStatus.OPTIMAL`.
- [ ] Map ojAlgo infeasible status to `DistOpfStatus.INFEASIBLE`.
- [ ] Map ojAlgo unbounded status to `DistOpfStatus.UNBOUNDED`.
- [ ] Preserve solver message in `DistOpfSolverResult`.
- [ ] Compute maximum constraint residual after solve.
- [ ] Add infeasibility diagnostics where available.
- [ ] Add LP solver tests using a tiny hand-built model.

## Milestone 7: Result Handling

- [ ] Add `DistOpfResult`.
- [ ] Add bus phase voltage result records.
- [ ] Add branch phase P/Q flow result records.
- [ ] Add DER P/Q setpoint result records.
- [ ] Add objective value field.
- [ ] Add solver status field.
- [ ] Add warning list.
- [ ] Add constraint residual summary.
- [ ] Add binding-constraint summary where available.
- [ ] Add `applySetpointsToNetwork(DStabNetwork3Phase net)`.
- [ ] Ensure `solve()` does not mutate the network.
- [ ] Add tests proving setpoints are only applied when explicitly requested.

## Milestone 8: Power-Flow Validation

- [ ] Add `DistOpfPowerFlowValidation`.
- [ ] Add option `validateWithPowerFlow`.
- [ ] Apply result setpoints to a copied or explicitly supplied network.
- [ ] Run fixed-point three-phase power flow after OPF.
- [ ] Record power-flow convergence status.
- [ ] Record power-flow iteration count.
- [ ] Compare LinDistFlow bus voltages against solved power-flow voltages.
- [ ] Report maximum voltage difference.
- [ ] Report voltage-limit violations after fixed-point PF.
- [ ] Report branch-limit violations after fixed-point PF.
- [ ] Add validation tests for a small feeder.

## Milestone 9: Test Systems and Acceptance Tests

- [ ] Add unit tests for variable indexing.
- [ ] Add unit tests for sparse constraint row creation.
- [ ] Add unit tests for network extraction.
- [ ] Add unit tests for each constraint collector.
- [ ] Add unit tests for each objective collector.
- [ ] Add no-control OPF test on a small feeder.
- [ ] Add Q-control voltage correction test.
- [ ] Add P-curtailment voltage correction test.
- [ ] Add thermal-limit binding test.
- [ ] Add infeasible OPF test.
- [ ] Add post-OPF fixed-point PF validation test.
- [ ] Add comparison fixture for IEEE 13-bus from GRIDAPPSD/distopf if licensing and data layout are acceptable.
- [ ] Add larger feeder validation after IEEE 13-bus passes.

## Milestone 10: Documentation and Examples

- [ ] Add Java usage example for running DistOPF on a `DStabNetwork3Phase`.
- [ ] Add Java usage example for applying setpoints and running fixed-point PF validation.
- [ ] Add Java usage example using the existing `org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser`.
- [ ] Document supported objectives.
- [ ] Document supported control modes.
- [ ] Document v1 limitations.
- [ ] Document expected solver dependency.
- [ ] Document how DistOPF differs from existing DC OPF.
- [ ] Document why dynamic-model Y-matrix contributions are excluded.

## Milestone 11: Future Work

- [ ] Add mixed-integer capacitor control.
- [ ] Add mixed-integer regulator tap control.
- [ ] Add battery P/Q controls.
- [ ] Add battery state-of-charge constraints.
- [ ] Add multi-period schedules.
- [ ] Add nonlinear branch-flow OPF.
- [ ] Add meshed-network support if required.
- [ ] Add additional OpenDSS parser coverage only if DistOPF validation exposes missing feeder features.
- [ ] Add CIM import parity.
- [ ] Add OR-Tools solver adapter.
- [ ] Add additional solver adapters as needed.
