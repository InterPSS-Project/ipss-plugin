package org.interpss.threePhase.powerflow.impl;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.common.datatype.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.threephase.IBranch3Phase;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.INetwork3Phase;
import com.interpss.core.threephase.Static3PXformer;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.funcImpl.AclfNetHelper;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.sparse.impl.csj.CSJSparseEqnComplexMatrix3x3Impl;

public class DistributionPowerFlowAlgorithmImpl implements DistributionPowerFlowAlgorithm{

	private INetwork3Phase distNet = null;

	private DistributionPFMethod pfMethod = DistributionPFMethod.Fixed_Point;

	private double tol = 1.0E-6;
	private int    maxIteration = 20;
	private int    iterationCount = -1;
	private boolean radialNetworkOnly = true;
	private boolean pfFlag =false;
	private Hashtable<String,Complex3x1> busVoltTable =null;
	private boolean initBusVoltagesEnabled = true;
	private boolean isAllPowerFlowConverged = false;
	private boolean fixedPointFallbackUsed = false;
	private double transformerAntiFloatAdmittance = 1.0E-6;
	private double maxFixedPointVoltageAbs = 10.0;
	private Hashtable<Integer, Complex3x1> swingBusVoltageBoundaryCurrent = new Hashtable<>();

	private static final Logger log = LoggerFactory.getLogger(DistributionPowerFlowAlgorithmImpl.class);


	public DistributionPowerFlowAlgorithmImpl(){
		busVoltTable = new Hashtable<>();

	}

    public DistributionPowerFlowAlgorithmImpl(INetwork3Phase net){
		this.distNet = net;
		busVoltTable = new Hashtable<>();
	}

	@SuppressWarnings("unchecked")
	private BaseAclfNetwork<? extends BaseAclfBus<? extends AclfGen, ? extends AclfLoad>, ? extends AclfBranch> aclfNetwork() {
		if(this.distNet instanceof BaseAclfNetwork) {
			return (BaseAclfNetwork<? extends BaseAclfBus<? extends AclfGen, ? extends AclfLoad>, ? extends AclfBranch>) this.distNet;
		}
		throw new UnsupportedOperationException("Distribution power flow requires a three-phase network that also extends BaseAclfNetwork");
	}

	private IBus3Phase threePhaseBus(BaseAclfBus<?, ?> bus) {
		if(bus instanceof IBus3Phase) {
			return (IBus3Phase) bus;
		}
		throw new UnsupportedOperationException("The bus object is not a three-phase type: " + bus.getId());
	}

	private IBranch3Phase threePhaseBranch(AclfBranch branch) {
		if(branch instanceof IBranch3Phase) {
			return (IBranch3Phase) branch;
		}
		throw new UnsupportedOperationException("The branch object is not a three-phase type: " + branch.getId());
	}

	private DStab3PBranch sweepBranch(Branch branch) {
		if(branch instanceof DStab3PBranch) {
			return (DStab3PBranch) branch;
		}
		throw new UnsupportedOperationException("Forward/backward sweep requires DStab3PBranch branch operations: " + branch.getId());
	}

	private Complex3x1 currentOrZero(Complex3x1 current) {
		return current != null ? current : new Complex3x1();
	}

	private Complex3x1 finiteCurrentOrZero(Complex3x1 current) {
		if(current == null || !isFinite(current.a_0) || !isFinite(current.b_1) || !isFinite(current.c_2)) {
			return new Complex3x1();
		}
		return current;
	}

	private boolean isFinite(Complex value) {
		return value != null
				&& Double.isFinite(value.getReal())
				&& Double.isFinite(value.getImaginary());
	}

	private Complex3x1 turnRatioTransformerCurrentVoltageDrop(DStab3PBranch branch, Complex3x1 current) {
		double[] fromRatios = transformerFromTurnRatios(branch);
		double[] toRatios = transformerToTurnRatios(branch);
		return turnRatioTransformerCurrentVoltageDrop(branch, current, fromRatios, toRatios);
	}

	private Complex3x1 turnRatioTransformerCurrentVoltageDrop(DStab3PBranch branch, Complex3x1 branchCurrent,
			double[] fromRatios, double[] toRatios) {
		Complex3x1 current = currentOrZero(branchCurrent);
		Complex3x3 zabc = branch.getZabc();
		Complex3x1 voltageDrop = new Complex3x1();
		voltageDrop.a_0 = zabc.aa.multiply(fromRatios[0] * toRatios[0]).multiply(current.a_0);
		voltageDrop.b_1 = zabc.bb.multiply(fromRatios[1] * toRatios[1]).multiply(current.b_1);
		voltageDrop.c_2 = zabc.cc.multiply(fromRatios[2] * toRatios[2]).multiply(current.c_2);
		return voltageDrop;
	}

	private Complex3x3 turnRatioFromBusVabc2ToBusVabcMatrix(DStab3PBranch branch) {
		return ratioMatrix(transformerToTurnRatios(branch), transformerFromTurnRatios(branch));
	}

	private Complex3x3 turnRatioToBusVabc2FromBusVabcMatrix(DStab3PBranch branch) {
		return ratioMatrix(transformerFromTurnRatios(branch), transformerToTurnRatios(branch));
	}

	private Complex3x3 ratioMatrix(double[] numerator, double[] denominator) {
		Complex3x3 matrix = new Complex3x3();
		matrix.aa = new Complex(numerator[0] / denominator[0], 0.0);
		matrix.bb = new Complex(numerator[1] / denominator[1], 0.0);
		matrix.cc = new Complex(numerator[2] / denominator[2], 0.0);
		return matrix;
	}

	private double[] transformerFromTurnRatios(DStab3PBranch branch) {
		if(branch.hasPhaseTurnRatio()) {
			return branch.getFromTurnRatioABC();
		}
		return new double[] {branch.getFromTurnRatio(), branch.getFromTurnRatio(), branch.getFromTurnRatio()};
	}

	private double[] transformerToTurnRatios(DStab3PBranch branch) {
		if(branch.hasPhaseTurnRatio()) {
			return branch.getToTurnRatioABC();
		}
		return new double[] {branch.getToTurnRatio(), branch.getToTurnRatio(), branch.getToTurnRatio()};
	}

	private boolean usesRegulatorTurnRatioFbsModel(DStab3PBranch branch) {
		return branch.isXfr()
				&& isRegulatorBranch(branch)
				&& (branch.hasPhaseTurnRatio()
						|| Math.abs(branch.getFromTurnRatio() - 1.0) > 1.0E-10
						|| Math.abs(branch.getToTurnRatio() - 1.0) > 1.0E-10);
	}

	@Override
	public boolean orderDistributionBuses(boolean radialOnly) {
		Queue<Bus> onceVisitedBuses = new  LinkedList<>();
		BaseAclfNetwork<?, ?> net = aclfNetwork();

		// find the source bus, which is the swing bus for radial feeders;
		for(BaseAclfBus<?,?> b: net.getBusList()){
			    b.setIntFlag(0); // reset it as this will be used below.
				if(b.isActive() && b.isSwing()){
					onceVisitedBuses.add(b);
				}
		}

		//make sure all internal branches are unvisited
		for(AclfBranch bra:net.getBranchList()){
			bra.setBooleanFlag(false);
		}

		// perform BFS and set the bus sortNumber
		BFS(onceVisitedBuses);


		net.setBusNumberArranged(true);


		return true;
	}



    private void BFS (Queue<Bus> onceVisitedBuses){
    	int orderNumber = 0;
		//Retrieves and removes the head of this queue, or returns null if this queue is empty.
	    while(!onceVisitedBuses.isEmpty()){
			Bus  startingBus = onceVisitedBuses.poll();
			startingBus.setSortNumber(orderNumber++);
			startingBus.setBooleanFlag(true);
			startingBus.setIntFlag(2);

			if(startingBus!=null){
				  for(Branch connectedBra: startingBus.getBranchIterable()){
						if(connectedBra.isActive() && !connectedBra.isBooleanFlag()){
								Bus findBus = connectedBra.getOppositeBus(startingBus);

								//update status
								connectedBra.setBooleanFlag(true);

								//for first time visited buses
								if(findBus.getIntFlag()==0){
									findBus.setIntFlag(1);
									onceVisitedBuses.add(findBus);

								}
						}
				 }

			}

	      }
	}

	@Override
	public boolean initBusVoltages() {


			for(BaseAclfBus b: aclfNetwork().getBusList()){
					IBus3Phase bus3P = threePhaseBus(b);

					if(b.isSwing()) {
						bus3P.set3PhaseVotlages(getSwingBusThreePhaseVoltages(b.getVoltageMag(), b.getVoltageAng(UnitType.Deg)));
					} else if(b.isGenPV()) {
						bus3P.set3PhaseVotlages(getPVBusThreePhaseVoltages(b.getVoltageMag()));
					} else {
						bus3P.set3PhaseVotlages(getUnitThreePhaseVoltages());
					}

			}

		return true;
	}

	private Complex phaseShiftCplxFactor(double shiftDeg){
			return new Complex(Math.cos(shiftDeg/180.0d*Math.PI),Math.sin(shiftDeg/180.0d*Math.PI));
	}

	private Complex3x1 getUnitThreePhaseVoltages(){
		return new Complex3x1(new Complex(1,0),new Complex(-Math.sin(Math.PI/6),-Math.cos(Math.PI/6)),new Complex(-Math.sin(Math.PI/6),Math.cos(Math.PI/6)));
	}

	private Complex3x1 getPVBusThreePhaseVoltages(double Vset){
		return new Complex3x1(new Complex(Vset,0),new Complex(-1*Vset*Math.sin(Math.PI/6),-1*Vset*Math.cos(Math.PI/6)),new Complex(-1*Vset*Math.sin(Math.PI/6),Vset*Math.cos(Math.PI/6)));
	}

	private Complex3x1 getSwingBusThreePhaseVoltages(double Vset, double angleDeg){
		return new Complex3x1(new Complex(Vset,0).multiply(phaseShiftCplxFactor(angleDeg)),
				new Complex(-1*Vset*Math.sin(Math.PI/6),-1*Vset*Math.cos(Math.PI/6)).multiply(phaseShiftCplxFactor(angleDeg)),
				new Complex(-1*Vset*Math.sin(Math.PI/6),Vset*Math.cos(Math.PI/6)).multiply(phaseShiftCplxFactor(angleDeg)));

	}

	@Override
	public boolean powerflow() {

		 this.isAllPowerFlowConverged = true;
		 BaseAclfNetwork<? extends BaseAclfBus<? extends AclfGen, ? extends AclfLoad>, ? extends AclfBranch> distNet = aclfNetwork();
		 deactivateBusesOnlyInFloatingPhaseComponents(distNet);

		//step-1. check if there is any island in the system
		 AclfNetHelper helper = new AclfNetHelper(distNet);
		 List<String> islandBusList = helper.calIslandBuses();

		 // turn off single islanded bus
		 for(String busId: islandBusList) {
			 BaseAclfBus bus = (BaseAclfBus) distNet.getBus(busId);
			 if(bus.isActive() && bus.nActiveBranchConnected()==0) {
				bus.setStatus(false);
			}
		 }

		//step-2a. if the system is one single island, run it as a single Network
		if(islandBusList.isEmpty()) {
			this.isAllPowerFlowConverged = powerflow_singleNet(distNet);
		}
		// step-2b. otherwise, create subnetworks for the islands and run power flow for each island
		else {
			 final BaseAclfNetwork originNetwork = distNet;
			 List<BaseAclfNetwork>  subNetworkList = createSubNetworkList(distNet, islandBusList);



			 for(BaseAclfNetwork subnet: subNetworkList) {

				    // check swing generator, if not assign one
				    helper = new AclfNetHelper(subnet);
					if (!helper.checkSwingRefBus()) {
						helper.assignSwingBusTurnOffIslandBus();
					}

				    // run power flow for each single island (as a subnetwork)
					if(!powerflow_singleNet(subnet)) {

						log.error("Power flow does not converge in subnetwork #"+subnet.getId());
						return this.isAllPowerFlowConverged = false;
					}
		     }
			 //set the parent network load flow status as converged
			 this.setNetwork(originNetwork);
			 aclfNetwork().setLfConverged(true);
		}

		return this.isAllPowerFlowConverged;
	}

	private void deactivateBusesOnlyInFloatingPhaseComponents(BaseAclfNetwork distNet) {
		int nodeCount = distNet.getNoBus() * 3;
		List<List<Integer>> graph = new ArrayList<>(nodeCount);
		for(int i = 0; i < nodeCount; i++) {
			graph.add(new ArrayList<>());
		}

		for(AclfBranch branch: (List<AclfBranch>) distNet.getBranchList()) {
			if(branch.isActive()) {
				IBranch3Phase branch3P = threePhaseBranch(branch);
				int from = branch.getFromBus().getSortNumber();
				int to = branch.getToBus().getSortNumber();
				addPhaseConnectivity(graph, from, to, branch3P.getYftabc());
				addPhaseConnectivity(graph, to, from, branch3P.getYtfabc());
			}
		}

		boolean[] floatingNode = new boolean[nodeCount];
		boolean[] connectedNode = new boolean[nodeCount];
		boolean[] seen = new boolean[nodeCount];
		for(BaseAclfBus bus: (List<BaseAclfBus>) distNet.getBusList()) {
			if(!bus.isActive()) {
				continue;
			}
			for(int phase = 0; phase < 3; phase++) {
				int start = bus.getSortNumber() * 3 + phase;
				if(seen[start] || graph.get(start).isEmpty()) {
					continue;
				}
				List<Integer> component = collectPhaseComponent(graph, seen, start);
				boolean hasSwing = containsSwingBusPhase(component, distNet);
				for(int node : component) {
					connectedNode[node] = true;
					floatingNode[node] = !hasSwing;
				}
			}
		}

		int inactiveBusCount = 0;
		int inactiveBranchCount = 0;
		for(BaseAclfBus bus: (List<BaseAclfBus>) distNet.getBusList()) {
			if(!bus.isActive() || bus.isSwing()) {
				continue;
			}
			boolean hasConnectedPhase = false;
			boolean hasSwingConnectedPhase = false;
			for(int phase = 0; phase < 3; phase++) {
				int node = bus.getSortNumber() * 3 + phase;
				hasConnectedPhase = hasConnectedPhase || connectedNode[node];
				hasSwingConnectedPhase = hasSwingConnectedPhase || (connectedNode[node] && !floatingNode[node]);
			}
			if(hasConnectedPhase && !hasSwingConnectedPhase) {
				bus.setStatus(false);
				inactiveBusCount++;
			}
		}
		for(AclfBranch branch: (List<AclfBranch>) distNet.getBranchList()) {
			if(branch.isActive()
					&& (!branch.getFromBus().isActive() || !branch.getToBus().isActive())) {
				branch.setStatus(false);
				inactiveBranchCount++;
			}
		}
		if(inactiveBusCount > 0 || inactiveBranchCount > 0) {
			log.info("Turned off buses only connected to floating phase components before power flow: buses="
					+ inactiveBusCount + ", branches=" + inactiveBranchCount);
		}
	}

	private List<BaseAclfNetwork> createSubNetworkList(BaseAclfNetwork parentNetwork, List<String> list){
		List<BaseAclfNetwork> subNetList = new ArrayList<>();

		for (Object bus : parentNetwork.getBusList()) {
				// mark swing bus with intFlag = 0
			   BaseAclfBus aclfBus = (BaseAclfBus) bus;
			   if(aclfBus.isActive()) {
				   if(!list.contains(aclfBus.getId())) {
					   list.add(aclfBus.getId());
				   }
			   }

		}

		int subNetIdx =0;
		for( String busId:list){
		    BaseAclfBus source = (BaseAclfBus) parentNetwork.getBus(busId);
			if(source.isActive() && !source.isBooleanFlag()){

				// for each iteration back to this layer, it means one subnetwork search is finished; subsequently, it is going to start
				// searching a new subnetwork. Thus, a new subnetwork object needs to be created first.
				BaseAclfNetwork subNet = null;

				if(parentNetwork instanceof DStabNetwork3Phase) {
					subNet = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
				} else {
					throw new UnsupportedOperationException("The network should be either  DStabNetwork3Phase or DStabilityNetwork type!");
				}
				subNet.setId("SubNet-"+(subNetIdx+1));

				subNetList.add(subNet);

				subNet.addBus(source);

				DFS(parentNetwork, subNet,busId);
				subNetIdx++;
			}
		}

		return subNetList;
	}

	private boolean DFS(BaseAclfNetwork _net, BaseAclfNetwork _subNet, String busId) {
		boolean isToBus = true;

		Bus source = _net.getBus(busId);

		source.setBooleanFlag(true);


		//System.out.println("BusId, Name, kV: "+busId+","+source.getName()+","+source.getBaseVoltage()*0.001);

		for (Branch bra : source.getBranchIterable()) {

		  if (bra.isActive() && !bra.isGroundBranch() && bra instanceof AclfBranch) {
			isToBus = bra.getFromBus().getId().equals(busId);
			String nextBusId = isToBus ? bra.getToBus().getId() : bra.getFromBus().getId();

			if(_subNet.getBus(nextBusId)==null){
				BaseAclfBus bus = (BaseAclfBus) _net.getBus(nextBusId);
				_subNet.addBus(bus);
			}

			if (!bra.isBooleanFlag() ) { // fromBusId-->buId
				_subNet.addBranch(bra, bra.getFromBus().getId(), bra.getToBus().getId() , bra.getCircuitNumber());

				bra.setBooleanFlag(true);

			    //DFS searching
			    DFS(_net,_subNet,nextBusId);

				}
			}
		}

	    return true;
	}

	private boolean powerflow_singleNet(BaseAclfNetwork distNet) {
		this.setNetwork(distNet);
		//step-1 order the network
		 pfFlag = orderDistributionBuses(radialNetworkOnly);


		//step-2 initialize bus voltage
		if(!pfFlag) {
			try {
				throw new Exception("Error in odering the distribution buses");
			} catch (Exception e) {
				log.error("Error in ordering the distribution buses", e);
			}
		} else{
			if(this.initBusVoltagesEnabled) {
				pfFlag = this.initBusVoltages();
			}
		}

		if(!pfFlag) {
			try {
				throw new Exception("Error in iniitalizing the three-phase voltages of distribution buses");
			} catch (Exception e) {
				log.error("Error in initializing the three-phase voltages of distribution buses", e);
			}
		}
		//step-3 apply the selected distribution power flow solver.
		if(this.pfMethod==DistributionPFMethod.Fixed_Point){

		        pfFlag = fixedPointPowerflow();
		        if(pfFlag && !this.fixedPointFallbackUsed) {
		        	log.info("The distribution network fixed-point power flow is converged.");
		        } else if(pfFlag) {
		        	log.info("The distribution network power flow is converged.");
		        }

			 }
		else if(this.pfMethod==DistributionPFMethod.Forward_Backword_Sweep){

		        pfFlag =  FBSPowerflow();
		        if(pfFlag) {
		        	log.info("The distribution network power flow is converged.");
		        }
			 }
		else{
			throw new UnsupportedOperationException("The power flow method is not supported yet:"+this.pfMethod);
		}

		distNet.setLfConverged(pfFlag);


		return pfFlag;
	}

	private boolean fixedPointPowerflow() {

		ISparseEqnComplexMatrix3x3 yMatrix = null;
		this.fixedPointFallbackUsed = false;
		BaseAclfNetwork<? extends BaseAclfBus<? extends AclfGen, ? extends AclfLoad>, ? extends AclfBranch> distNet = aclfNetwork();

		try {
			yMatrix = formYMatrixABCForPowerflow(distNet);
			applySwingBusVoltageBoundary(yMatrix);
			yMatrix.factorization(Constants.Matrix_LU_Tolerance);
		} catch (IpssNumericException e) {
			log.warn("Fixed-point power-flow Y-matrix factorization failed", e);
			return false;
		}

		this.pfFlag = false;
		this.iterationCount = -1;

		for (int i = 0; i < this.maxIteration; i++) {
			this.iterationCount = i;
			saveBusVoltages();

			try {
				yMatrix.setB2Zero();
				setPowerflowCurrentInjections(yMatrix);
				setSwingBusVoltageRhs(yMatrix);
				yMatrix.solveEqn();
			} catch (IpssNumericException e) {
				log.warn("Fixed-point power-flow equation solve failed", e);
				return false;
			}

			if(!updateSolvedBusVoltages(yMatrix)) {
				log.warn("Fixed-point power-flow equation solve produced invalid bus voltages");
				return false;
			}

			double maxVoltageMismatch = calcMaxVoltageMismatch();
			if(i > 0 && maxVoltageMismatch <= this.getTolerance()) {
				System.out.println("\n\nDistribution fixed-point power flow converged, iterations = "+i+"\n");
				updateBranchCurrentsFromSolvedVoltages();
				calcSwingBusGenPower();
				this.pfFlag = true;
				break;
			}
		}

		if(!this.pfFlag) {
			log.warn("Fixed-point power-flow did not converge within " + this.maxIteration
					+ " iterations");
			return false;
		}

		return this.pfFlag;
	}

	private ISparseEqnComplexMatrix3x3 formYMatrixABCForPowerflow(BaseAclfNetwork distNet) throws IpssNumericException {
		ISparseEqnComplexMatrix3x3 yMatrix = new CSJSparseEqnComplexMatrix3x3Impl(distNet.getNoBus());

		for(BaseAclfBus bus: (List<BaseAclfBus>) distNet.getBusList()) {
			if(bus.isActive()) {
				int i = bus.getSortNumber();
				Complex3x3 yii = threePhaseBus(bus).getYiiAbcForPowerflow();

				if(!bus.isSwing()) {
					// replace zero diagonal entries with 1.0 to avoid singularity
					// for partial-phase buses (e.g., 1-ph or 2-ph connections)
					double yiiMinTolerance = 1.0E-8;
					if(yii.aa.abs() < yiiMinTolerance) yii.aa = new Complex(1.0, 0);
					if(yii.bb.abs() < yiiMinTolerance) yii.bb = new Complex(1.0, 0);
					if(yii.cc.abs() < yiiMinTolerance) yii.cc = new Complex(1.0, 0);
				}

				yMatrix.setA(yii, i, i);
			}
		}

		for(AclfBranch branch: (List<AclfBranch>) distNet.getBranchList()) {
			if(branch.isActive()) {
				IBranch3Phase branch3P = threePhaseBranch(branch);
				int i = branch.getFromBus().getSortNumber();
				int j = branch.getToBus().getSortNumber();
				yMatrix.addToA(branch3P.getYftabc(), i, j);
				yMatrix.addToA(branch3P.getYtfabc(), j, i);
				addTransformerAntiFloatAdmittance(yMatrix, branch);
			}
		}
		addFloatingPhaseComponentAntiFloatAdmittance(yMatrix, distNet);
		addNonSwingBusAntiFloatAdmittance(yMatrix, distNet);

		return yMatrix;
	}

	private void addNonSwingBusAntiFloatAdmittance(ISparseEqnComplexMatrix3x3 yMatrix, BaseAclfNetwork distNet) {
		Complex3x3 antiFloatYii = Complex3x3.createUnitMatrix()
				.multiply(new Complex(this.transformerAntiFloatAdmittance, 0.0));
		for(BaseAclfBus bus: (List<BaseAclfBus>) distNet.getBusList()) {
			if(bus.isActive() && !bus.isSwing()) {
				yMatrix.addToA(antiFloatYii, bus.getSortNumber(), bus.getSortNumber());
			}
		}
	}

	private void addFloatingPhaseComponentAntiFloatAdmittance(ISparseEqnComplexMatrix3x3 yMatrix,
			BaseAclfNetwork distNet) {
		int nodeCount = distNet.getNoBus() * 3;
		List<List<Integer>> graph = new ArrayList<>(nodeCount);
		for(int i = 0; i < nodeCount; i++) {
			graph.add(new ArrayList<>());
		}

		for(AclfBranch branch: (List<AclfBranch>) distNet.getBranchList()) {
			if(branch.isActive()) {
				IBranch3Phase branch3P = threePhaseBranch(branch);
				int from = branch.getFromBus().getSortNumber();
				int to = branch.getToBus().getSortNumber();
				addPhaseConnectivity(graph, from, to, branch3P.getYftabc());
				addPhaseConnectivity(graph, to, from, branch3P.getYtfabc());
			}
		}

		boolean[] seen = new boolean[nodeCount];
		int adjustedComponentCount = 0;
		int adjustedNodeCount = 0;
		for(BaseAclfBus bus: (List<BaseAclfBus>) distNet.getBusList()) {
			if(!bus.isActive()) {
				continue;
			}
			for(int phase = 0; phase < 3; phase++) {
				int start = bus.getSortNumber() * 3 + phase;
				if(seen[start] || graph.get(start).isEmpty()) {
					continue;
				}
				List<Integer> component = collectPhaseComponent(graph, seen, start);
				if(!containsSwingBusPhase(component, distNet)) {
					adjustedComponentCount++;
					adjustedNodeCount += component.size();
					for(int node : component) {
						addPhaseAntiFloatAdmittance(yMatrix, node / 3, node % 3);
					}
				}
			}
		}
		if(adjustedComponentCount > 0) {
			log.info("Added anti-floating admittance to " + adjustedNodeCount
					+ " phase nodes in " + adjustedComponentCount
					+ " phase components not connected to a swing bus");
		}
	}

	private void addPhaseConnectivity(List<List<Integer>> graph, int fromSort, int toSort, Complex3x3 y) {
		Complex[][] values = {
				{y.aa, y.ab, y.ac},
				{y.ba, y.bb, y.bc},
				{y.ca, y.cb, y.cc}
		};
		for(int fromPhase = 0; fromPhase < 3; fromPhase++) {
			for(int toPhase = 0; toPhase < 3; toPhase++) {
				if(values[fromPhase][toPhase] != null && values[fromPhase][toPhase].abs() > 1.0e-12) {
					int fromNode = fromSort * 3 + fromPhase;
					int toNode = toSort * 3 + toPhase;
					graph.get(fromNode).add(toNode);
					graph.get(toNode).add(fromNode);
				}
			}
		}
	}

	private List<Integer> collectPhaseComponent(List<List<Integer>> graph, boolean[] seen, int start) {
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		List<Integer> component = new ArrayList<>();
		seen[start] = true;
		queue.add(start);
		while(!queue.isEmpty()) {
			int node = queue.remove();
			component.add(node);
			for(int next : graph.get(node)) {
				if(!seen[next]) {
					seen[next] = true;
					queue.add(next);
				}
			}
		}
		return component;
	}

	private boolean containsSwingBusPhase(List<Integer> component, BaseAclfNetwork distNet) {
		for(int node : component) {
			BaseAclfBus bus = busBySortNumber(distNet, node / 3);
			if(bus.isSwing()) {
				return true;
			}
		}
		return false;
	}

	private BaseAclfBus busBySortNumber(BaseAclfNetwork distNet, int sortNumber) {
		for(BaseAclfBus bus: (List<BaseAclfBus>) distNet.getBusList()) {
			if(bus.getSortNumber() == sortNumber) {
				return bus;
			}
		}
		throw new IllegalArgumentException("No bus for sort number " + sortNumber);
	}

	private void addPhaseAntiFloatAdmittance(ISparseEqnComplexMatrix3x3 yMatrix, int busSortNumber, int phase) {
		Complex3x3 y = new Complex3x3();
		Complex antiFloat = new Complex(this.transformerAntiFloatAdmittance, 0.0);
		if(phase == 0) {
			y.aa = antiFloat;
		}
		else if(phase == 1) {
			y.bb = antiFloat;
		}
		else {
			y.cc = antiFloat;
		}
		yMatrix.addToA(y, busSortNumber, busSortNumber);
	}

	private void addTransformerAntiFloatAdmittance(ISparseEqnComplexMatrix3x3 yMatrix, AclfBranch branch) {
		if(!branch.isXfr() || !(branch instanceof AcscBranch)) {
			return;
		}

		AcscBranch acscBranch = (AcscBranch) branch;
		Complex3x3 antiFloatYii = Complex3x3.createUnitMatrix()
				.multiply(new Complex(this.transformerAntiFloatAdmittance, 0.0));

		boolean hasFloatingWinding = isFloatingTransformerWinding(
				acscBranch.getFromGrounding().getXfrConnectCode(),
				acscBranch.getFromGrounding().getGroundCode())
				|| isFloatingTransformerWinding(
				acscBranch.getToGrounding().getXfrConnectCode(),
				acscBranch.getToGrounding().getGroundCode());

		if(hasFloatingWinding) {
			yMatrix.addToA(antiFloatYii, branch.getFromBus().getSortNumber(), branch.getFromBus().getSortNumber());
			yMatrix.addToA(antiFloatYii, branch.getToBus().getSortNumber(), branch.getToBus().getSortNumber());
		}
	}

	private boolean isFloatingTransformerWinding(XFormerConnectCode connectCode, BusGroundCode groundCode) {
		return connectCode == XFormerConnectCode.DELTA
				|| connectCode == XFormerConnectCode.DELTA11
				|| (connectCode == XFormerConnectCode.WYE && groundCode == BusGroundCode.UNGROUNDED);
	}

	private void applySwingBusVoltageBoundary(ISparseEqnComplexMatrix3x3 yMatrix) {
		Complex3x3 zero = new Complex3x3();
		Complex3x3 unit = Complex3x3.createUnitMatrix();
		this.swingBusVoltageBoundaryCurrent.clear();
		BaseAclfNetwork<? extends BaseAclfBus<? extends AclfGen, ? extends AclfLoad>, ? extends AclfBranch> distNet = aclfNetwork();

		for(BaseAclfBus bus: distNet.getBusList()) {
			if(bus.isActive() && bus.isSwing()) {
				IBus3Phase swingBus = threePhaseBus(bus);
				int swingSortNumber = bus.getSortNumber();
				for(int row = 0; row < distNet.getNoBus(); row++) {
					if(row != swingSortNumber) {
						Complex3x3 yToSwing = yMatrix.getA(row, swingSortNumber);
						Complex3x1 compensation = yToSwing.multiply(swingBus.get3PhaseVotlages());
						Complex3x1 existing = this.swingBusVoltageBoundaryCurrent.get(row);
						this.swingBusVoltageBoundaryCurrent.put(row, existing == null ? compensation : existing.add(compensation));
						yMatrix.setA(zero, row, swingSortNumber);
					}
					yMatrix.setA(zero, swingSortNumber, row);
				}
				yMatrix.setA(unit, swingSortNumber, swingSortNumber);
			}
		}
	}

	private void setPowerflowCurrentInjections(ISparseEqnComplexMatrix3x3 yMatrix) {
		for(BaseAclfBus bus: aclfNetwork().getBusList()) {
			if(bus.isActive() && !bus.isSwing()) {
				IBus3Phase bus3P = threePhaseBus(bus);
				Complex3x1 curInj = bus3P.calc3PhEquivCurInj();
				if(!isFinite(curInj.a_0) || !isFinite(curInj.b_1) || !isFinite(curInj.c_2)) {
					log.warn("Invalid fixed-point current injection at bus " + bus.getId()
							+ ", sortNumber=" + bus.getSortNumber() + ", iabc=" + curInj
							+ ", vabc=" + bus3P.get3PhaseVotlages());
				}
				Complex3x1 boundaryCurrent = this.swingBusVoltageBoundaryCurrent.get(bus.getSortNumber());
				if(boundaryCurrent != null
						&& (!isFinite(boundaryCurrent.a_0) || !isFinite(boundaryCurrent.b_1) || !isFinite(boundaryCurrent.c_2))) {
					log.warn("Invalid fixed-point swing-boundary current at bus " + bus.getId()
							+ ", sortNumber=" + bus.getSortNumber() + ", iabc=" + boundaryCurrent);
				}
				yMatrix.setBi(boundaryCurrent == null ? curInj : curInj.subtract(boundaryCurrent), bus.getSortNumber());
			}
		}
	}

	private void setSwingBusVoltageRhs(ISparseEqnComplexMatrix3x3 yMatrix) {
		for(BaseAclfBus bus: aclfNetwork().getBusList()) {
			if(bus.isActive() && bus.isSwing()) {
				IBus3Phase bus3P = threePhaseBus(bus);
				yMatrix.setBi(bus3P.get3PhaseVotlages(), bus.getSortNumber());
			}
		}
	}

	private boolean updateSolvedBusVoltages(ISparseEqnComplexMatrix3x3 yMatrix) {
		for(BaseAclfBus bus: aclfNetwork().getBusList()) {
			if(bus.isActive() && !bus.isSwing()) {
				IBus3Phase bus3P = threePhaseBus(bus);
				Complex3x1 vabc = yMatrix.getX(bus.getSortNumber());

				if(isValidFixedPointVoltage(vabc)){
					bus3P.set3PhaseVotlages(vabc);
					bus.setVoltage(vabc.to012().b_1);
				} else {
					log.warn("Fixed-point solve produced invalid voltage at bus " + bus.getId()
							+ ", sortNumber=" + bus.getSortNumber() + ", vabc=" + vabc);
					return false;
				}
			}
		}
		return true;
	}

	private boolean isValidFixedPointVoltage(Complex3x1 vabc) {
		return isFinite(vabc.a_0) && isFinite(vabc.b_1) && isFinite(vabc.c_2)
				&& vabc.absMax() <= this.maxFixedPointVoltageAbs;
	}

	private void saveBusVoltages() {
		for(BaseAclfBus bus: aclfNetwork().getBusList()) {
			if(bus.isActive()) {
				IBus3Phase bus3P = threePhaseBus(bus);
				this.busVoltTable.put(bus.getId(), Complex3x1.valueOf(bus3P.get3PhaseVotlages()));
			}
		}
	}

	private double calcMaxVoltageMismatch() {
		double maxMis = 0.0;

		for(BaseAclfBus bus: aclfNetwork().getBusList()) {
			if(bus.isActive()) {
				IBus3Phase bus3P = threePhaseBus(bus);
				Complex3x1 oldVolt = this.busVoltTable.get(bus.getId());
				if(oldVolt != null) {
					maxMis = Math.max(maxMis, bus3P.get3PhaseVotlages().subtract(oldVolt).absMax());
				}
			}
		}

		return maxMis;
	}

	private void updateBranchCurrentsFromSolvedVoltages() {
		for(AclfBranch branch: aclfNetwork().getBranchList()) {
			if(branch.isActive()) {
				DStab3PBranch branch3P = sweepBranch(branch);
				branch3P.setCurrentAbcAtFromSide(branch3P.calc3PhaseCurrentFrom2To());
				branch3P.setCurrentAbcAtToSide(branch3P.calc3PhaseCurrentTo2From());
			}
		}
	}

	private boolean FBSPowerflow(){
		/*
		 * 1. Backward sweep:  calculate the current injections of buses starting the most remote bus and the current flows in
		 *  all active lines and transformers, all the way up to the source bus.
		 *
		 * 2. convergence checking: ||deltaV|| < tolerance.
		 *
		 * 3. Forward  sweep:  update the voltages of the buses in the downstream  based on the voltages of the upstream bus and the current of the branch or transformer
		 *
		 */

		BaseAclfNetwork<? extends BaseAclfBus<? extends AclfGen, ? extends AclfLoad>, ? extends AclfBranch> distNet = aclfNetwork();

		for (int i=0;i<this.maxIteration;i++){
			this.iterationCount = i;

			for (Branch bra: distNet.getBranchList()){
				bra.setIntFlag(0);
			}

			for (Bus b: distNet.getBusList()){
				 b.setIntFlag(0);
			}

			saveBusVoltages();

			//-----------------------------------------------------------------------
			//Step-1 backward sweep step
			//-----------------------------------------------------------------------

			Hashtable<String, Integer> backwardUpdatedPhaseMask = new Hashtable<>();

			for(int sortNum =distNet.getNoBus()-1;sortNum>0;sortNum--){
				BaseAclfBus bus = (BaseAclfBus) distNet.getBus(sortNum);
				if(bus==null){
					throw new Error(" The bus sort num # "+sortNum +" returns null bus object in distribution #"+distNet.getId());

				}
				if(bus.isActive()){
					IBus3Phase bus3P = threePhaseBus(bus);


					// update the non-visited branch current based on the bus current injection
					// and all the currents of all other connected down-stream branches of this bus

					/*
					 * The line modeling
					 *
					 *   Vabc,m                                       Vabc,n
					 *   --|->Iabc,m--------|Zline|----------->Iabc,n---|----
					 *                |                 |
					 *             1/2ShuntY         1/2ShuntY
					 *                |                 |
					 *                _                 _
					 *
					 */

					Complex3x1 sumOfBranchCurrents = new Complex3x1();
					String upStreamBranchId = "";
					String upStreamBusId="";
					int unvisitedBranchNum = 0;
					List<DStab3PBranch> unvisitedBranches = new ArrayList<>();
					for (Branch bra: bus.getBranchIterable()){
						DStab3PBranch bra3P = sweepBranch(bra);
						// all visited branches are on the downstream side, and there should be only one upstream branch
						if(bra.isActive() && bra.getIntFlag() ==1){

							if(bra.getFromBus().getId().equals(bus.getId())){


							   sumOfBranchCurrents= sumOfBranchCurrents.add(currentOrZero(bra3P.getCurrentAbcAtFromSide()));
							}
							else{


								sumOfBranchCurrents= sumOfBranchCurrents.add(currentOrZero(bra3P.getCurrentAbcAtToSide()).multiply(-1.0));
							}
						}
						else if(bra.isActive() && bra.getIntFlag() ==0){

							 upStreamBranchId = bra.getId();
							 unvisitedBranches.add(bra3P);
							 unvisitedBranchNum +=1;
							 bra.setIntFlag(1);

						}

					}


					//Error in the searching
					if(bus.getBranchList().size()==1 && unvisitedBranchNum !=1 && !bus.isSwing()){
						throw new Error(" There must be only one 'upstream' unvisited branch for an active, non-swing bus:"+bus.getId());
					}

//					if(bus.getId().equals("Bus2")){
//						System.out.println("processing bus 2");
//					}

					//else {

						// consider the existing bus current injection into the network from generators, loads, shunt capacitors, etc.
						Complex3x1 busSelfEquivCurInj3Ph =bus3P.calc3PhEquivCurInj();

						if(processParallelPartialRegulatorBackward(bus, bus3P, busSelfEquivCurInj3Ph,
								sumOfBranchCurrents, unvisitedBranches, backwardUpdatedPhaseMask)) {
							continue;
						}

						// add the branch current flows to obtain the current injections
						DStab3PBranch upStreamBranch = sweepBranch(distNet.getBranch(upStreamBranchId));

						BaseAclfBus upStreamBus = null;

						/*
						 * The line modeling
						 *
						 *   Vabc,m                                       Vabc,n
						 *   --|->Iabc,m--------|Zline|----------->Iabc,n---|----
						 *                |                 |
						 *             1/2ShuntY         1/2ShuntY
						 *                |                 |
						 *                _                 _
						 *
						 */

						// update the upstream branch current and the upstream bus voltage
						if(upStreamBranch != null && upStreamBranch.getFromBus().getId().equals(bus.getId())){

							//calculate and set the upstream branch current
							upStreamBranch.setCurrentAbcAtFromSide(busSelfEquivCurInj3Ph.subtract( sumOfBranchCurrents));

							upStreamBus = (BaseAclfBus) upStreamBranch.getToBus();

							//calculate the voltages at the upstream end
							//NOTE: For, current flowing through the branch, the direction from bus -> to bus  is regarded as positive;
							Complex3x1 vabc = null;
							Complex3x1 iabc = null;


							// line
							if(upStreamBranch.isLine()){
								vabc = upStreamBranch.getToBusVabc2FromBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusVabcMatrix().multiply(upStreamBranch.getCurrentAbcAtFromSide().multiply(-1)));

								//calculate the current injection at the upstream end

								iabc= upStreamBranch.getToBusVabc2FromBusIabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusIabcMatrix().multiply(upStreamBranch.getCurrentAbcAtFromSide().multiply(-1)));

							}

							// transformer
							else if (upStreamBranch.isXfr()){
								Static3PXformer xfr3p = upStreamBranch.to3PXformer();
								if(usesRegulatorTurnRatioFbsModel(upStreamBranch)) {
									vabc = turnRatioFromBusVabc2ToBusVabcMatrix(upStreamBranch).multiply(bus3P.get3PhaseVotlages()).subtract(
											turnRatioTransformerCurrentVoltageDrop(upStreamBranch, upStreamBranch.getCurrentAbcAtFromSide()));
									iabc = upStreamBranch.getYttabc().multiply(vabc).add(
											upStreamBranch.getYtfabc().multiply(bus3P.get3PhaseVotlages()));
								}
								else {
									vabc = xfr3p.getLVBusVabc2HVBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
											xfr3p.getLVBusIabc2HVBusVabcMatrix().multiply(upStreamBranch.getCurrentAbcAtFromSide().multiply(-1)));

									iabc= xfr3p.getLVBusVabc2HVBusIabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
											xfr3p.getLVBusIabc2HVBusIabcMatrix().multiply(upStreamBranch.getCurrentAbcAtFromSide().multiply(-1)));
								}

							}


							upStreamBranch.setCurrentAbcAtToSide(iabc.multiply(-1.0));

							if(!upStreamBus.isSwing()){
								if(isPartialPhaseLine(upStreamBranch)) {
									mergeUpstreamPartialBranchVoltage(upStreamBus, upStreamBranch, vabc,
											backwardUpdatedPhaseMask);
								}
								else if(upStreamBus.getIntFlag()==0 || isPartiallyUpdated(upStreamBus, backwardUpdatedPhaseMask)){
								   threePhaseBus(upStreamBus).set3PhaseVotlages(vabc);
								   upStreamBus.setIntFlag(1);
								   backwardUpdatedPhaseMask.put(upStreamBus.getId(), 0b111);
								}
							}
						}
						else{
							upStreamBranch.setCurrentAbcAtToSide(sumOfBranchCurrents.subtract(busSelfEquivCurInj3Ph));

	                        upStreamBus = (BaseAclfBus) upStreamBranch.getFromBus();

	                        //calculate the bus voltage at the upstream end
							Complex3x1 vabc = null;
							Complex3x1 iabc = null;

							// line
							if(upStreamBranch.isLine()){
								vabc =	upStreamBranch.getToBusVabc2FromBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusVabcMatrix().multiply(upStreamBranch.getCurrentAbcAtToSide()));

	                            //calculate the current injection at the upstream end


								iabc = upStreamBranch.getToBusVabc2FromBusIabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusIabcMatrix().multiply(upStreamBranch.getCurrentAbcAtToSide()));
							}

							// transformer
							else if (upStreamBranch.isXfr()){
								Static3PXformer xfr3p = upStreamBranch.to3PXformer();

								if(usesRegulatorTurnRatioFbsModel(upStreamBranch)) {
									vabc =	turnRatioToBusVabc2FromBusVabcMatrix(upStreamBranch).multiply(bus3P.get3PhaseVotlages()).add(
											turnRatioTransformerCurrentVoltageDrop(upStreamBranch, upStreamBranch.getCurrentAbcAtToSide()));
								}
								else {
									try {
										vabc =	xfr3p.getLVBusVabc2HVBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
												xfr3p.getLVBusIabc2HVBusVabcMatrix().multiply(upStreamBranch.getCurrentAbcAtToSide()));
									} catch (NullPointerException e) {
										throw new Error("Transformer FBS matrix failed for branch "
												+ upStreamBranch.getId() + ", name=" + upStreamBranch.getName()
												+ ", from=" + upStreamBranch.getFromBus().getId()
												+ ", to=" + upStreamBranch.getToBus().getId()
												+ ", phase=" + upStreamBranch.getPhaseCode(), e);
									}
								}

	                            //calculate the current injection at the upstream end

								if(usesRegulatorTurnRatioFbsModel(upStreamBranch)) {
									iabc = upStreamBranch.getYffabc().multiply(vabc).add(
											upStreamBranch.getYftabc().multiply(bus3P.get3PhaseVotlages()));
								}
								else {
									iabc =  xfr3p.getLVBusVabc2HVBusIabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
											xfr3p.getLVBusIabc2HVBusIabcMatrix().multiply(upStreamBranch.getCurrentAbcAtToSide()));
								}


							}


							upStreamBranch.setCurrentAbcAtFromSide(iabc.multiply(1.0));


							if(!upStreamBus.isSwing()){
								if(isPartialPhaseLine(upStreamBranch)) {
									mergeUpstreamPartialBranchVoltage(upStreamBus, upStreamBranch, vabc,
											backwardUpdatedPhaseMask);
								}
								else if(upStreamBus.getIntFlag()==0 || isPartiallyUpdated(upStreamBus, backwardUpdatedPhaseMask)){
								   threePhaseBus(upStreamBus).set3PhaseVotlages(vabc);
								   upStreamBus.setIntFlag(1);
								   backwardUpdatedPhaseMask.put(upStreamBus.getId(), 0b111);
								}
							}

						}

					//}

				}
			}


			//-----------------------------------------------------------------------
			//Step-2 :forward sweep step
			//-----------------------------------------------------------------------

			Hashtable<String, Integer> regulatorUpdatedPhaseMask = new Hashtable<>();

			for(int sortNum2 = 0;sortNum2<distNet.getNoBus();sortNum2++){

				BaseAclfBus bus = (BaseAclfBus) distNet.getBus(sortNum2);

				if(bus.isActive()){
					// update the bus state, with intFlag =2 meaning this bus voltage has been updated
					bus.setIntFlag(2);
					IBus3Phase bus3P = threePhaseBus(bus);
					for(Branch bra:bus.getBranchIterable()){

						if(bra.isActive()){
							DStab3PBranch bra3Phase = sweepBranch(bra);

							BaseAclfBus downStreamBus = (BaseAclfBus) bra.getOppositeBus(bus);
							if(bus.getSortNumber() >= downStreamBus.getSortNumber()) {
								continue;
							}

							int branchPhaseMask = branchPhaseMask(bra3Phase);
							int downStreamRegulatorUpdatedPhaseMask =
									regulatorUpdatedPhaseMask.getOrDefault(downStreamBus.getId(), 0);
							if(downStreamBus.getIntFlag()<2
									|| (isPartialPhaseRegulatorTransformer(bra3Phase)
											&& (downStreamRegulatorUpdatedPhaseMask & branchPhaseMask) != branchPhaseMask)){
								Complex3x1 vabc = null;
								if(bra.isFromBus(bus)){

									 //calculate the bus voltage at the downstream end
									//  the current flow definition is align with the up/downstream definition
									//  which is the same as the definition in Dr.Kersting's book
									if(bra3Phase.isLine()){

									   vabc =  bra3Phase.getFromBusVabc2ToBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).subtract(
											bra3Phase.getToBusIabc2ToBusVabcMatrix().multiply(currentOrZero(bra3Phase.getCurrentAbcAtToSide())));
									}
									else if (bra3Phase.isXfr()){
										Static3PXformer xfr3p = bra3Phase.to3PXformer();
										if(usesRegulatorTurnRatioFbsModel(bra3Phase)) {
											vabc =  turnRatioFromBusVabc2ToBusVabcMatrix(bra3Phase).multiply(bus3P.get3PhaseVotlages()).subtract(
													turnRatioTransformerCurrentVoltageDrop(bra3Phase, bra3Phase.getCurrentAbcAtToSide()));
										}
										else {
											vabc =  xfr3p.getHVBusVabc2LVBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).subtract(
													xfr3p.getLVBusIabc2LVBusVabcMatrix().multiply(currentOrZero(bra3Phase.getCurrentAbcAtToSide())));
										}
									}

								}
								else{

									 //calculate the bus voltage at the downstream end


									//TODO   Positive current direction definition:
									//     upstream  |--<-Iabc,To-------Zline-----<--Iabc,from---| downstream
									//   because the current flow definition is opposite to the up/downstream definition
									//   the subtract() operation has been changed to add() in the following calculation
									//   also the current is measured at the downstream side, which is the fromside

									if(bra3Phase.isLine()){
										vabc =  bra3Phase.getFromBusVabc2ToBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
												bra3Phase.getToBusIabc2ToBusVabcMatrix().multiply(currentOrZero(bra3Phase.getCurrentAbcAtFromSide())));
									}
									else if (bra3Phase.isXfr()){
										Static3PXformer xfr3p = bra3Phase.to3PXformer();

										if(usesRegulatorTurnRatioFbsModel(bra3Phase)) {
											vabc =  turnRatioToBusVabc2FromBusVabcMatrix(bra3Phase).multiply(bus3P.get3PhaseVotlages()).add(
													turnRatioTransformerCurrentVoltageDrop(bra3Phase, bra3Phase.getCurrentAbcAtFromSide()));
										}
										else {
											vabc =  xfr3p.getHVBusVabc2LVBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
													xfr3p.getLVBusIabc2LVBusVabcMatrix().multiply(currentOrZero(bra3Phase.getCurrentAbcAtFromSide())));
										}
									}
								}

								if(isPartialPhaseRegulatorTransformer(bra3Phase)) {
									IBus3Phase downStreamBus3P = threePhaseBus(downStreamBus);
									downStreamBus3P.set3PhaseVotlages(mergeBranchPhaseVoltage(
											downStreamBus3P.get3PhaseVotlages(), vabc, branchPhaseMask,
											downStreamRegulatorUpdatedPhaseMask != 0));
									regulatorUpdatedPhaseMask.put(downStreamBus.getId(),
											downStreamRegulatorUpdatedPhaseMask | branchPhaseMask);
								}
								else {
									threePhaseBus(downStreamBus).set3PhaseVotlages(vabc);
									downStreamBus.setIntFlag(2);
								}
							}
						}
					}


				}

			}

			//-----------------------------------------------------------------------
			//Step-3 check convergence after voltage propagation.
			//-----------------------------------------------------------------------

			this.pfFlag = true;
			for(BaseAclfBus bus: distNet.getBusList()){
				if(bus.isActive() && i >= 1){
					IBus3Phase bus3P = threePhaseBus(bus);
					double mis = bus3P.get3PhaseVotlages().subtract(busVoltTable.get(bus.getId())).absMax();
					if(mis > this.getTolerance()){
						this.pfFlag = false;
					}
				 }
			}

			if(i > 0 && this.pfFlag) {
				System.out.println("\n\nDistribution power flow converged, iterations = "+i+"\n");
				calcSwingBusGenPower();
				break;
			}


		}



		return this.pfFlag;


	}

	private boolean isPartialPhaseRegulatorTransformer(DStab3PBranch branch) {
		return branch.isXfr()
				&& branch.getPhaseCode() != null
				&& branch.getPhaseCode() != PhaseCode.ABC
				&& isRegulatorBranch(branch);
	}

	private boolean processParallelPartialRegulatorBackward(BaseAclfBus bus, IBus3Phase bus3P,
			Complex3x1 busSelfEquivCurInj3Ph, Complex3x1 sumOfBranchCurrents,
			List<DStab3PBranch> unvisitedBranches, Hashtable<String, Integer> backwardUpdatedPhaseMask) {
		if(unvisitedBranches.size() <= 1) {
			return false;
		}

		DStab3PBranch firstBranch = unvisitedBranches.get(0);
		if(!isPartialPhaseRegulatorTransformer(firstBranch)) {
			return false;
		}

		boolean busIsFromSide = firstBranch.getFromBus().getId().equals(bus.getId());
		BaseAclfBus upStreamBus = (BaseAclfBus) (busIsFromSide ? firstBranch.getToBus() : firstBranch.getFromBus());
		for(DStab3PBranch branch : unvisitedBranches) {
			if(!isPartialPhaseRegulatorTransformer(branch)) {
				return false;
			}
			boolean sameSide = busIsFromSide == branch.getFromBus().getId().equals(bus.getId());
			BaseAclfBus branchUpstreamBus = (BaseAclfBus) (busIsFromSide ? branch.getToBus() : branch.getFromBus());
			if(!sameSide || !branchUpstreamBus.getId().equals(upStreamBus.getId())) {
				return false;
			}
		}

		Complex3x1 totalCurrentAtBus = busIsFromSide
				? busSelfEquivCurInj3Ph.subtract(sumOfBranchCurrents)
				: sumOfBranchCurrents.subtract(busSelfEquivCurInj3Ph);

		for(DStab3PBranch branch : unvisitedBranches) {
			int phaseMask = branchPhaseMask(branch);
			Complex3x1 branchCurrentAtBus = phaseMaskedValue(totalCurrentAtBus, phaseMask);
			Complex3x1 upstreamVoltage;
			Complex3x1 upstreamCurrent;

			if(busIsFromSide) {
				branch.setCurrentAbcAtFromSide(branchCurrentAtBus);
				if(usesRegulatorTurnRatioFbsModel(branch)) {
					upstreamVoltage = turnRatioFromBusVabc2ToBusVabcMatrix(branch).multiply(bus3P.get3PhaseVotlages()).subtract(
							turnRatioTransformerCurrentVoltageDrop(branch, branchCurrentAtBus));
				}
				else {
					Static3PXformer xfr3p = branch.to3PXformer();
					upstreamVoltage = xfr3p.getHVBusVabc2LVBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).subtract(
							xfr3p.getLVBusIabc2LVBusVabcMatrix().multiply(branchCurrentAtBus));
				}
				upstreamCurrent = branch.getYttabc().multiply(upstreamVoltage).add(
						branch.getYtfabc().multiply(bus3P.get3PhaseVotlages()));
				branch.setCurrentAbcAtToSide(phaseMaskedValue(upstreamCurrent.multiply(-1.0), phaseMask));
			}
			else {
				branch.setCurrentAbcAtToSide(branchCurrentAtBus);
				if(usesRegulatorTurnRatioFbsModel(branch)) {
					upstreamVoltage = turnRatioToBusVabc2FromBusVabcMatrix(branch).multiply(bus3P.get3PhaseVotlages()).add(
							turnRatioTransformerCurrentVoltageDrop(branch, branchCurrentAtBus));
				}
				else {
					Static3PXformer xfr3p = branch.to3PXformer();
					upstreamVoltage = xfr3p.getLVBusVabc2HVBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
							xfr3p.getLVBusIabc2HVBusVabcMatrix().multiply(branchCurrentAtBus));
				}
				upstreamCurrent = branch.getYffabc().multiply(upstreamVoltage).add(
						branch.getYftabc().multiply(bus3P.get3PhaseVotlages()));
				branch.setCurrentAbcAtFromSide(phaseMaskedValue(upstreamCurrent, phaseMask));
			}

			if(!upStreamBus.isSwing()) {
				IBus3Phase upStreamBus3P = threePhaseBus(upStreamBus);
				upStreamBus3P.set3PhaseVotlages(mergeBranchPhaseVoltage(
						upStreamBus3P.get3PhaseVotlages(), upstreamVoltage, phaseMask,
						upStreamBus3P.get3PhaseVotlages() != null));
				markBackwardUpdatedPhases(upStreamBus, backwardUpdatedPhaseMask, phaseMask);
			}
		}

		return true;
	}

	private boolean isPartialPhaseLine(DStab3PBranch branch) {
		return branch.isLine()
				&& branch.getPhaseCode() != null
				&& branch.getPhaseCode() != PhaseCode.ABC;
	}

	private void mergeUpstreamPartialBranchVoltage(BaseAclfBus upStreamBus, DStab3PBranch upStreamBranch,
			Complex3x1 branchVoltage, Hashtable<String, Integer> backwardUpdatedPhaseMask) {
		IBus3Phase upStreamBus3P = threePhaseBus(upStreamBus);
		int phaseMask = branchPhaseMask(upStreamBranch);
		upStreamBus3P.set3PhaseVotlages(mergeBranchPhaseVoltage(
				upStreamBus3P.get3PhaseVotlages(),
				branchVoltage,
				phaseMask,
				upStreamBus3P.get3PhaseVotlages() != null));
		markBackwardUpdatedPhases(upStreamBus, backwardUpdatedPhaseMask, phaseMask);
	}

	private boolean isPartiallyUpdated(BaseAclfBus bus, Hashtable<String, Integer> backwardUpdatedPhaseMask) {
		int phaseMask = backwardUpdatedPhaseMask.getOrDefault(bus.getId(), 0);
		return phaseMask != 0 && phaseMask != 0b111;
	}

	private void markBackwardUpdatedPhases(BaseAclfBus bus,
			Hashtable<String, Integer> backwardUpdatedPhaseMask, int phaseMask) {
		int updatedPhaseMask = backwardUpdatedPhaseMask.getOrDefault(bus.getId(), 0) | phaseMask;
		backwardUpdatedPhaseMask.put(bus.getId(), updatedPhaseMask);
		if(updatedPhaseMask == 0b111) {
			bus.setIntFlag(1);
		}
	}

	private boolean isRegulatorBranch(DStab3PBranch branch) {
		String id = branch.getId() == null ? "" : branch.getId().toLowerCase();
		String name = branch.getName() == null ? "" : branch.getName().toLowerCase();
		String fromBusId = branch.getFromBus() == null ? "" : branch.getFromBus().getId().toLowerCase();
		String toBusId = branch.getToBus() == null ? "" : branch.getToBus().getId().toLowerCase();
		return id.contains("reg")
				|| name.contains("reg")
				|| fromBusId.endsWith("r")
				|| toBusId.endsWith("r");
	}

	private int branchPhaseMask(DStab3PBranch branch) {
		PhaseCode phaseCode = branch.getPhaseCode();
		String phase = phaseCode == null ? "ABC" : phaseCode.toString();
		if("ABC".equals(phase)) {
			return 0b111;
		}
		if("A".equals(phase)) {
			return 0b001;
		}
		if("B".equals(phase)) {
			return 0b010;
		}
		if("C".equals(phase)) {
			return 0b100;
		}
		if("AB".equals(phase)) {
			return 0b011;
		}
		if("AC".equals(phase)) {
			return 0b101;
		}
		if("BC".equals(phase)) {
			return 0b110;
		}
		return 0b111;
	}

	private Complex3x1 phaseMaskedValue(Complex3x1 value, int phaseMask) {
		Complex3x1 masked = new Complex3x1();
		if((phaseMask & 0b001) != 0) {
			masked.a_0 = value.a_0;
		}
		if((phaseMask & 0b010) != 0) {
			masked.b_1 = value.b_1;
		}
		if((phaseMask & 0b100) != 0) {
			masked.c_2 = value.c_2;
		}
		return masked;
	}

	private Complex3x1 mergeBranchPhaseVoltage(Complex3x1 existingVoltage, Complex3x1 branchVoltage,
			int branchPhaseMask, boolean mergeWithExisting) {
		Complex zero = new Complex(0.0, 0.0);
		Complex3x1 merged = new Complex3x1(zero, zero, zero);
		if(mergeWithExisting && existingVoltage != null) {
			merged = Complex3x1.valueOf(existingVoltage);
		}
		if((branchPhaseMask & 0b001) != 0) {
			merged.a_0 = branchVoltage.a_0;
		}
		if((branchPhaseMask & 0b010) != 0) {
			merged.b_1 = branchVoltage.b_1;
		}
		if((branchPhaseMask & 0b100) != 0) {
			merged.c_2 = branchVoltage.c_2;
		}
		return merged;
	}

	public void calcSwingBusGenPower() {
		// update the swing bus generation output based on the converged power flow result

		for(BaseAclfBus<? extends AclfGen, ? extends AclfLoad> bus: aclfNetwork().getBusList()){
			if(bus.isActive() && bus.isSwing()){
				IBus3Phase bus3p = threePhaseBus(bus);
				Complex3x1 sumOfBranchCurrents = new Complex3x1();

				sumOfBranchCurrents = bus3p.calcLoad3PhEquivCurInj().multiply(-1);

				for (Branch bra: bus.getBranchIterable()){
					if(bra.isActive()){
						DStab3PBranch bra3P = sweepBranch(bra);
						// all visited branches are on the downstream side, and there should be only one upstream branch


							if(bra.getFromBus().getId().equals(bus.getId())){


							   sumOfBranchCurrents= sumOfBranchCurrents.add(finiteCurrentOrZero(bra3P.getCurrentAbcAtFromSide()));
							}
							else{


								sumOfBranchCurrents= sumOfBranchCurrents.add(finiteCurrentOrZero(bra3P.getCurrentAbcAtToSide()).multiply(-1.0));
							}
					}

			    }

				System.out.println("Source node 3 sequence current into network: "+sumOfBranchCurrents.to012());

				Complex posGenPQ = bus3p.get3PhaseVotlages().to012().b_1.multiply(sumOfBranchCurrents.to012().b_1.conjugate());
				if(bus.getContributeGenList().size()>0){
				   bus.getContributeGenList().get(0).setGen(posGenPQ);
				   System.out.println("Source node positive sequence power into network: "+posGenPQ.toString());

				}
			}
		}
	}



	@Override
	public DistributionPFMethod getPFMethod() {

		return this.pfMethod;
	}

	@Override
	public void setPFMethod(DistributionPFMethod method) {
		this.pfMethod = method;
	}

	@Override
	public void setTolerance(double tolerance) {
		this.tol = tolerance;

	}

	@Override
	public double getTolerance() {

		return this.tol;
	}

	@Override
	public void setMaxIteration(int maxIterNum) {
		this.maxIteration = maxIterNum;

	}

	@Override
	public int getMaxIteration() {

		return this.maxIteration;
	}

	@Override
	public int getIterationCount() {
		return this.iterationCount;
	}

	@Override
	public boolean isFixedPointFallbackUsed() {
		return this.fixedPointFallbackUsed;
	}

	@Override
	public INetwork3Phase getNetwork() {

		return this.distNet;
	}

	@Override
	public void setNetwork(INetwork3Phase net) {
		this.distNet = net;

	}

	public void setNetwork(BaseAclfNetwork<?, ?> net) {
		this.distNet = (INetwork3Phase) net;
	}

	@Override
	public void setInitBusVoltageEnabled(boolean enableInitBus3PhaseVolts) {
		this.initBusVoltagesEnabled = enableInitBus3PhaseVolts;

	}

	@Override
	public boolean isInitBusVoltageEnabled() {

		return this.initBusVoltagesEnabled;
	}


}
