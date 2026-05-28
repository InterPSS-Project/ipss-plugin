# Java DistOPF Implementation TODO

This checklist tracks implementation of Java-native DistOPF for InterPSS using the existing OPF collector/objective/solver-adapter style.

## Milestone 1: Project Structure

- [x] Create package `org.interpss.threePhase.opf.dist`.
- [x] Create subpackage `org.interpss.threePhase.opf.dist.constraint`.
- [x] Create subpackage `org.interpss.threePhase.opf.dist.objective`.
- [x] Create subpackage `org.interpss.threePhase.opf.dist.solver`.
- [x] Create subpackage `org.interpss.threePhase.opf.dist.util`.
- [x] Add `DistOpfAlgorithm`.
- [x] Add `DistOpfOptions`.
- [x] Add `DistOpfObjective`.
- [x] Add `DistOpfControlMode`.
- [x] Add `DistOpfStatus`.
- [x] Add factory method `createDistOpfAlgorithm(DStabNetwork3Phase net)`.
- [x] Add a minimal smoke test that constructs the algorithm from a three-phase network.

## Milestone 2: Shared Optimization Model

- [x] Decide whether `OpfConstraint` can be reused directly from `org.interpss.plugin.opf.constraint`.
- [x] If direct reuse is clean, document the dependency and use `OpfConstraint`.
- [x] N/A: direct `OpfConstraint` reuse is clean, so no separate `DistOpfConstraint` is needed.
- [x] Add sparse row helper methods for equality constraints.
- [x] Add sparse row helper methods for less-than constraints.
- [x] Add sparse row helper methods for greater-than constraints.
- [x] Add sparse row helper methods for bounded row constraints.
- [x] Add `DistOpfModel`.
- [x] Add `DistOpfVariableIndex`.
- [x] Add variable-index support for branch phase active power `Pij`.
- [x] Add variable-index support for branch phase reactive power `Qij`.
- [x] Add variable-index support for bus phase squared voltage `V2`.
- [x] Add variable-index support for DER active power `Pg`.
- [x] Add variable-index support for DER reactive power `Qg`.
- [x] Add variable-index support for curtailment variables.
- [x] Add variable lower-bound storage.
- [x] Add variable upper-bound storage.
- [x] Add unit tests for deterministic variable ordering.

## Milestone 3: Network Extraction

- [x] Add `DistOpfModelData`.
- [x] Add `DistOpfModelDataExtractor`.
- [x] Extract base MVA.
- [x] Extract voltage bases.
- [x] Identify swing/source bus.
- [x] Extract active buses.
- [x] Extract active branches.
- [x] Extract bus phase sets.
- [x] Extract branch phase sets.
- [x] Extract parent/child topology from the source bus.
- [x] Validate that the network is connected.
- [x] Validate that the v1 network is radial.
- [x] Extract per-branch 3x3 phase impedance matrices.
- [x] Extract fixed bus phase P loads.
- [x] Extract fixed bus phase Q loads.
- [x] Extract fixed capacitor Q injections.
- [x] Extract fixed regulator ratios.
- [x] Extract DER active-power limits.
- [x] Extract DER reactive-power limits.
- [x] Extract inverter apparent-power limits where available.
- [x] Extract bus phase voltage limits.
- [x] Extract branch thermal ratings where available.
- [x] Normalize extracted values to per-unit.
- [x] Exclude dynamic generator admittance contributions.
- [x] Exclude dynamic load admittance contributions.
- [x] Exclude induction motor equivalent admittance contributions.
- [x] Add extraction tests for a small three-phase feeder.
- [x] Add extraction tests for a feeder with missing phases.

## Milestone 4: Constraint Collectors

- [x] Add `IDistOpfConstraintCollector`.
- [x] Add `BaseDistOpfConstraintCollector`.
- [x] Add `DistPowerBalanceConstraintCollector`.
- [x] Add active-power balance equations for each non-source bus phase.
- [x] Add `DistReactivePowerBalanceConstraintCollector`.
- [x] Add reactive-power balance equations for each non-source bus phase.
- [x] Add fixed capacitor injection handling in reactive-power balance.
- [x] Add `DistVoltageDropConstraintCollector`.
- [x] Add voltage-drop equations for one-phase branches.
- [x] Add voltage-drop equations for two-phase branches.
- [x] Add voltage-drop equations for three-phase branches.
- [x] Add full phase-coupled R/X impedance terms.
- [x] Add `DistSwingVoltageConstraintCollector`.
- [x] Add source voltage equality constraints.
- [x] Add `DistVoltageLimitConstraintCollector`.
- [x] Add squared-voltage lower bounds.
- [x] Add squared-voltage upper bounds.
- [x] Add `DistDerLimitConstraintCollector`.
- [x] Fix DER variables when `DistOpfControlMode.NONE` is selected.
- [x] Enable active-power controls when `DistOpfControlMode.P` is selected.
- [x] Enable reactive-power controls when `DistOpfControlMode.Q` is selected.
- [x] Enable P/Q controls when `DistOpfControlMode.PQ` is selected.
- [x] Add `DistInverterCapabilityConstraintCollector`.
- [x] Add octagonal inverter capability approximation.
- [x] Add `DistBranchThermalLimitConstraintCollector`.
- [x] Add octagonal branch apparent-power approximation.
- [x] Add collector tests that verify sparse row coefficients.

## Milestone 5: Objective Collectors

- [x] Add `BaseDistOpfObjectiveCollector`.
- [x] Add `CurtailmentMinObjectiveCollector`.
- [x] Add curtailment minimization objective coefficients.
- [x] Add `GenMaxObjectiveCollector`.
- [x] Add generation maximization as negative minimization coefficients.
- [x] Add `TargetSubstationPObjectiveCollector`.
- [x] Add positive and negative P target deviation variables.
- [x] Add target substation P objective coefficients.
- [x] Add `TargetSubstationQObjectiveCollector`.
- [x] Add positive and negative Q target deviation variables.
- [x] Add target substation Q objective coefficients.
- [x] Add `LossMinObjectiveCollector`.
- [x] Defer ojAlgo convex QP verification until a true quadratic loss objective is added.
- [x] If QP support is insufficient, add documented linear loss approximation.
- [x] Add objective-vector unit tests.

## Milestone 6: Solver Adapter

- [x] Add `DistOpfSolver`.
- [x] Add `DistOpfSolverResult`.
- [x] Add `OjAlgoDistOpfSolver`.
- [x] Configure `OjAlgoDistOpfSolver` as the default solver for small systems.
- [x] Convert sparse equality constraints to ojAlgo input.
- [x] Convert sparse inequality constraints to ojAlgo input.
- [x] Convert variable lower bounds to ojAlgo input.
- [x] Convert variable upper bounds to ojAlgo input.
- [x] Convert linear objective coefficients to ojAlgo input.
- [x] Map ojAlgo optimal status to `DistOpfStatus.OPTIMAL`.
- [x] Map ojAlgo infeasible status to `DistOpfStatus.INFEASIBLE`.
- [x] Map ojAlgo unbounded status to `DistOpfStatus.UNBOUNDED`.
- [x] Preserve solver message in `DistOpfSolverResult`.
- [x] Compute maximum constraint residual after solve.
- [x] Add infeasibility diagnostics where available.
- [x] Add LP solver tests using a tiny hand-built model.

## Milestone 7: Result Handling

- [x] Add `DistOpfResult`.
- [x] Add bus phase voltage result records.
- [x] Add branch phase P/Q flow result records.
- [x] Add DER P/Q setpoint result records.
- [x] Add objective value field.
- [x] Add solver status field.
- [x] Add warning list.
- [x] Add constraint residual summary.
- [x] Add binding-constraint summary where available.
- [x] Add `applySetpointsToNetwork(DStabNetwork3Phase net)`.
- [x] Ensure `solve()` does not mutate the network.
- [x] Add tests proving setpoints are only applied when explicitly requested.

## Milestone 8: Power-Flow Validation

- [x] Add `DistOpfPowerFlowValidation`.
- [x] Add option `validateWithPowerFlow`.
- [x] Apply result setpoints to a copied or explicitly supplied network.
- [x] Run fixed-point three-phase power flow after OPF.
- [x] Record power-flow convergence status.
- [x] Record power-flow iteration count.
- [x] Compare LinDistFlow bus voltages against solved power-flow voltages.
- [x] Report maximum voltage difference.
- [x] Report voltage-limit violations after fixed-point PF.
- [x] Report branch-limit violations after fixed-point PF.
- [x] Add validation tests for a small feeder.

## Milestone 9: Test Systems and Acceptance Tests

- [x] Add unit tests for variable indexing.
- [x] Add unit tests for sparse constraint row creation.
- [x] Add unit tests for network extraction.
- [x] Add unit tests for each constraint collector.
- [x] Add unit tests for each objective collector.
- [x] Add no-control OPF test on a small feeder.
- [x] Add Q-control voltage correction test.
- [x] Add P-curtailment voltage correction test.
- [x] Add thermal-limit binding test.
- [x] Add infeasible OPF test.
- [x] Add post-OPF fixed-point PF validation test.
- [ ] Add comparison fixture for IEEE 13-bus from GRIDAPPSD/distopf if licensing and data layout are acceptable.
- [ ] Add larger feeder validation after IEEE 13-bus passes.

## Milestone 10: Documentation and Examples

- [x] Add Java usage example for running DistOPF on a `DStabNetwork3Phase`.
- [x] Add Java usage example for applying setpoints and running fixed-point PF validation.
- [x] Add Java usage example using the existing `org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser`.
- [x] Document supported objectives.
- [x] Document supported control modes.
- [x] Document v1 limitations.
- [x] Document expected solver dependency.
- [x] Document how DistOPF differs from existing DC OPF.
- [x] Document why dynamic-model Y-matrix contributions are excluded.

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
