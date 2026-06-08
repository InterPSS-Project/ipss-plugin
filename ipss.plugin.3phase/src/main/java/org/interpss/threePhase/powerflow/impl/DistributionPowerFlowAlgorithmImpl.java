package org.interpss.threePhase.powerflow.impl;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.LongAdder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.IOException;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.powerflow.control.CapacitorBankControl;
import org.interpss.threePhase.powerflow.control.CapacitorControlData;
import org.interpss.threePhase.powerflow.control.RegulatorControlData;
import org.interpss.threePhase.powerflow.control.RegulatorTapControl;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPostSolveOutputMode;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.common.datatype.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.threephase.IBranch3Phase;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.IPhaseGen;
import com.interpss.core.threephase.IPhaseLoad;
import com.interpss.core.threephase.INetwork3Phase;
import com.interpss.core.threephase.Static3PLoad;
import com.interpss.core.threephase.Static3PXformer;
import com.interpss.core.threephase.Static3PhaseFactory;
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
import com.interpss.core.sparse.PrimitiveComplex3x3Equation;
import com.interpss.core.sparse.SparseEqnObjectFactory;

public class DistributionPowerFlowAlgorithmImpl implements DistributionPowerFlowAlgorithm{

	private INetwork3Phase distNet = null;

	private DistributionPFMethod pfMethod = DistributionPFMethod.Fixed_Point;
	private DistributionPostSolveOutputMode postSolveOutputMode = DistributionPostSolveOutputMode.FULL_BRANCH_CURRENTS;

	private double tol = 1.0E-6;
	private int    maxIteration = 20;
	private int    iterationCount = -1;
	private boolean radialNetworkOnly = true;
	private boolean pfFlag =false;
	private Hashtable<String,Complex3x1> busVoltTable =null;
	private boolean initBusVoltagesEnabled = true;
	private boolean isAllPowerFlowConverged = false;
	private boolean fixedPointFallbackUsed = false;
	private int fixedPointFallbackCount = 0;
	private double transformerAntiFloatAdmittance = 1.0E-6;
	private double maxFixedPointVoltageAbs = 10.0;
	private Hashtable<Integer, Complex3x1> swingBusVoltageBoundaryCurrent = new Hashtable<>();
	private List<RegulatorControlData> regulatorControls = Collections.emptyList();
	private boolean regulatorControlEnabled = false;
	private int maxRegulatorControlIterations = 20;
	private final RegulatorTapControl regulatorTapControl = new RegulatorTapControl();
	private List<CapacitorControlData> capacitorControls = Collections.emptyList();
	private boolean capacitorControlEnabled = false;
	private int maxCapacitorControlIterations = 20;
	private final CapacitorBankControl capacitorBankControl = new CapacitorBankControl();
	private boolean fixedPointYMatrixCacheEnabled = false;
	private ISparseEqnComplexMatrix3x3 fixedPointYMatrixCache = null;
	private BaseAclfNetwork<?, ?> fixedPointYMatrixCacheNetwork = null;
	private String fixedPointYMatrixCacheSignature = null;
	private ISparseEqnComplexMatrix3x3 fixedPointYMatrixValueUpdateCache = null;
	private BaseAclfNetwork<?, ?> fixedPointYMatrixValueUpdateCacheNetwork = null;
	private String fixedPointYMatrixSymbolSignature = null;
	private Object fixedPointYMatrixSymbolTable = null;
	private int fixedPointYMatrixSymbolicFactorizationCount = 0;
	private int fixedPointYMatrixNumericFactorizationCount = 0;
	private int fixedPointYMatrixValueUpdateCount = 0;
	private Map<String, RegulatorBranchAdmittance> fixedPointRegulatorBaseAdmittance = Collections.emptyMap();
	private Map<String, RegulatorBranchAdmittance> fixedPointRegulatorValueUpdateAdmittance = Collections.emptyMap();
	private boolean regulatorCompensationDiagnosticHeaderWritten = false;
	private int regulatorCompensationDiagnosticEval = 0;
	private Hashtable<Integer, Complex3x1> regulatorTapCompensationState = new Hashtable<>();

	private static final Logger log = LoggerFactory.getLogger(DistributionPowerFlowAlgorithmImpl.class);
	private static final FixedPointLoopProfile FIXED_POINT_PROFILE = new FixedPointLoopProfile();


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

	private IBranch3Phase sweepBranch(Branch branch) {
		if(branch instanceof IBranch3Phase) {
			return (IBranch3Phase) branch;
		}
		throw new UnsupportedOperationException("Forward/backward sweep requires IBranch3Phase branch operations: " + branch.getId());
	}

	private AcscBranch acscBranch(IBranch3Phase branch) {
		if(branch instanceof AcscBranch) {
			return (AcscBranch) branch;
		}
		throw new UnsupportedOperationException("Three-phase branch must also be an AcscBranch for power flow");
	}

	private boolean branchIsLine(IBranch3Phase branch) {
		return acscBranch(branch).isLine();
	}

	private boolean branchIsXfr(IBranch3Phase branch) {
		return acscBranch(branch).isXfr();
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

	private boolean isFinite(double[] value) {
		return value != null
				&& Double.isFinite(value[0])
				&& Double.isFinite(value[1])
				&& Double.isFinite(value[2])
				&& Double.isFinite(value[3])
				&& Double.isFinite(value[4])
				&& Double.isFinite(value[5]);
	}

	private double real(Complex value) {
		return value == null ? 0.0 : value.getReal();
	}

	private double imaginary(Complex value) {
		return value == null ? 0.0 : value.getImaginary();
	}

	private double rhsReal(Complex current, double boundary, Complex tapCompensation) {
		return real(current) - boundary - real(tapCompensation);
	}

	private double rhsImaginary(Complex current, double boundary, Complex tapCompensation) {
		return imaginary(current) - boundary - imaginary(tapCompensation);
	}

	private String format3x1(double[] value) {
		return "["
				+ formatComplex(value[0], value[1]) + ", "
				+ formatComplex(value[2], value[3]) + ", "
				+ formatComplex(value[4], value[5]) + "]";
	}

	private String formatComplex(double real, double imaginary) {
		return String.format(Locale.US, "%.12g+j%.12g", real, imaginary);
	}

	private Complex3x1 turnRatioTransformerCurrentVoltageDrop(IBranch3Phase branch, Complex3x1 current) {
		double[] fromRatios = transformerFromTurnRatios(branch);
		double[] toRatios = transformerToTurnRatios(branch);
		return turnRatioTransformerCurrentVoltageDrop(branch, current, fromRatios, toRatios);
	}

	private Complex3x1 turnRatioTransformerCurrentVoltageDrop(IBranch3Phase branch, Complex3x1 branchCurrent,
			double[] fromRatios, double[] toRatios) {
		Complex3x1 current = currentOrZero(branchCurrent);
		Complex3x3 zabc = branch.getZabc();
		Complex3x1 voltageDrop = new Complex3x1();
		voltageDrop.a_0 = zabc.aa.multiply(fromRatios[0] * toRatios[0]).multiply(current.a_0);
		voltageDrop.b_1 = zabc.bb.multiply(fromRatios[1] * toRatios[1]).multiply(current.b_1);
		voltageDrop.c_2 = zabc.cc.multiply(fromRatios[2] * toRatios[2]).multiply(current.c_2);
		return voltageDrop;
	}

	private Complex3x3 turnRatioFromBusVabc2ToBusVabcMatrix(IBranch3Phase branch) {
		return ratioMatrix(transformerToTurnRatios(branch), transformerFromTurnRatios(branch));
	}

	private Complex3x3 turnRatioToBusVabc2FromBusVabcMatrix(IBranch3Phase branch) {
		return ratioMatrix(transformerFromTurnRatios(branch), transformerToTurnRatios(branch));
	}

	private Complex3x3 ratioMatrix(double[] numerator, double[] denominator) {
		Complex3x3 matrix = new Complex3x3();
		matrix.aa = new Complex(numerator[0] / denominator[0], 0.0);
		matrix.bb = new Complex(numerator[1] / denominator[1], 0.0);
		matrix.cc = new Complex(numerator[2] / denominator[2], 0.0);
		return matrix;
	}

	private double[] transformerFromTurnRatios(IBranch3Phase branch) {
		if(branch.hasPhaseTurnRatio()) {
			return branch.getFromTurnRatioABC();
		}
		return new double[] {acscBranch(branch).getFromTurnRatio(), acscBranch(branch).getFromTurnRatio(), acscBranch(branch).getFromTurnRatio()};
	}

	private double[] transformerToTurnRatios(IBranch3Phase branch) {
		if(branch.hasPhaseTurnRatio()) {
			return branch.getToTurnRatioABC();
		}
		return new double[] {acscBranch(branch).getToTurnRatio(), acscBranch(branch).getToTurnRatio(), acscBranch(branch).getToTurnRatio()};
	}

	private boolean usesRegulatorTurnRatioFbsModel(IBranch3Phase branch) {
		return branchIsXfr(branch)
				&& isRegulatorBranch(branch)
				&& (branch.hasPhaseTurnRatio()
						|| Math.abs(acscBranch(branch).getFromTurnRatio() - 1.0) > 1.0E-10
						|| Math.abs(acscBranch(branch).getToTurnRatio() - 1.0) > 1.0E-10);
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
		boolean useRegulatorControls = this.regulatorControlEnabled && !this.regulatorControls.isEmpty();
		boolean useCapacitorControls = this.capacitorControlEnabled && !this.capacitorControls.isEmpty();
		if(!useRegulatorControls && !useCapacitorControls) {
			return powerflowWithoutRegulatorControls();
		}

		boolean converged = false;
		int maxControlIterations = Math.max(this.maxRegulatorControlIterations, this.maxCapacitorControlIterations);
		for(int controlIteration = 0; controlIteration <= maxControlIterations; controlIteration++) {
			converged = powerflowWithoutRegulatorControls();
			if(!converged) {
				return false;
			}
			boolean changed = false;
			if(useCapacitorControls) {
				changed = this.capacitorBankControl.apply(this.distNet, this.capacitorControls);
			}
			if(useRegulatorControls) {
				changed = this.regulatorTapControl.apply(this.distNet, this.regulatorControls) || changed;
			}
			if(!changed) {
				return true;
			}
		}
		log.error("Distribution controls did not settle within {} outer iterations", maxControlIterations);
		return this.isAllPowerFlowConverged = false;
	}

	private boolean powerflowWithoutRegulatorControls() {

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

				if(parentNetwork instanceof INetwork3Phase) {
					subNet = Static3PhaseFactory.eINSTANCE.createStatic3PNetwork();
				} else {
					throw new UnsupportedOperationException("The network should be a three-phase BaseAclfNetwork type");
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
		        	log.debug("The distribution network fixed-point power flow is converged.");
		        } else if(pfFlag) {
		        	log.debug("The distribution network power flow is converged.");
		        }

			 }
		else if(this.pfMethod==DistributionPFMethod.Forward_Backword_Sweep){

		        pfFlag =  FBSPowerflow();
		        if(pfFlag) {
		        	log.debug("The distribution network power flow is converged.");
		        }
			 }
		else{
			throw new UnsupportedOperationException("The power flow method is not supported yet:"+this.pfMethod);
		}

		distNet.setLfConverged(pfFlag);


		return pfFlag;
	}

	private boolean fixedPointPowerflow() {
		this.fixedPointFallbackUsed = false;
		boolean solved = fixedPointPowerflowAttempt();
		if(!solved && shouldRetryFixedPointWithoutCache()) {
			log.warn("Retrying fixed-point power flow with fresh Y-matrix after cached regulator compensation failure");
			boolean cacheEnabled = this.fixedPointYMatrixCacheEnabled;
			clearFixedPointYMatrixCache();
			this.fixedPointYMatrixCacheEnabled = false;
			this.fixedPointFallbackUsed = true;
			this.fixedPointFallbackCount++;
			try {
				solved = fixedPointPowerflowAttempt();
			}
			finally {
				this.fixedPointYMatrixCacheEnabled = cacheEnabled;
				clearFixedPointYMatrixCache();
			}
		}
		return solved;
	}

	private boolean fixedPointPowerflowAttempt() {
		ISparseEqnComplexMatrix3x3 yMatrix = null;
		BaseAclfNetwork<? extends BaseAclfBus<? extends AclfGen, ? extends AclfLoad>, ? extends AclfBranch> distNet = aclfNetwork();

		try {
			long start = FIXED_POINT_PROFILE.start();
			yMatrix = fixedPointYMatrix(distNet);
			FIXED_POINT_PROFILE.addMatrix(FIXED_POINT_PROFILE.elapsed(start));
		} catch (IpssNumericException e) {
			log.warn("Fixed-point power-flow Y-matrix factorization failed", e);
			return false;
		}

		this.pfFlag = false;
		this.iterationCount = -1;
		PrimitiveComplex3x3Equation primitiveMatrix = yMatrix instanceof PrimitiveComplex3x3Equation
				? (PrimitiveComplex3x3Equation) yMatrix : null;
		long busCacheStart = FIXED_POINT_PROFILE.start();
		FixedPointBusCache busCache = FixedPointBusCache.from(distNet, this.swingBusVoltageBoundaryCurrent);
		FIXED_POINT_PROFILE.addMatrixBusCache(FIXED_POINT_PROFILE.elapsed(busCacheStart));
		FIXED_POINT_PROFILE.addAttempt();

		for (int i = 0; i < this.maxIteration; i++) {
			this.iterationCount = i;
			FIXED_POINT_PROFILE.addIteration();
			long start = FIXED_POINT_PROFILE.start();
			saveBusVoltages(busCache);
			FIXED_POINT_PROFILE.addSaveVoltages(FIXED_POINT_PROFILE.elapsed(start));

			try {
				if(primitiveMatrix != null) {
					start = FIXED_POINT_PROFILE.start();
					primitiveMatrix.clearPrimitiveRhs();
					FIXED_POINT_PROFILE.addRhsClear(FIXED_POINT_PROFILE.elapsed(start));
					start = FIXED_POINT_PROFILE.start();
					setPowerflowCurrentInjections(primitiveMatrix, busCache);
					FIXED_POINT_PROFILE.addCurrentInjection(FIXED_POINT_PROFILE.elapsed(start));
					start = FIXED_POINT_PROFILE.start();
					setSwingBusVoltageRhs(primitiveMatrix, busCache);
					FIXED_POINT_PROFILE.addSwingRhs(FIXED_POINT_PROFILE.elapsed(start));
					start = FIXED_POINT_PROFILE.start();
					primitiveMatrix.solvePrimitiveRhs(true);
					FIXED_POINT_PROFILE.addSolve(FIXED_POINT_PROFILE.elapsed(start));
				} else {
					start = FIXED_POINT_PROFILE.start();
					yMatrix.setB2Zero();
					FIXED_POINT_PROFILE.addRhsClear(FIXED_POINT_PROFILE.elapsed(start));
					start = FIXED_POINT_PROFILE.start();
					setPowerflowCurrentInjections(yMatrix, busCache);
					FIXED_POINT_PROFILE.addCurrentInjection(FIXED_POINT_PROFILE.elapsed(start));
					start = FIXED_POINT_PROFILE.start();
					setSwingBusVoltageRhs(yMatrix, busCache);
					FIXED_POINT_PROFILE.addSwingRhs(FIXED_POINT_PROFILE.elapsed(start));
					start = FIXED_POINT_PROFILE.start();
					yMatrix.solveEqn();
					FIXED_POINT_PROFILE.addSolve(FIXED_POINT_PROFILE.elapsed(start));
				}
			} catch (IpssNumericException e) {
				log.warn("Fixed-point power-flow equation solve failed", e);
				return false;
			}

			start = FIXED_POINT_PROFILE.start();
			VoltageUpdateResult voltageUpdate = primitiveMatrix == null
					? updateSolvedBusVoltagesAndMismatch(yMatrix, busCache)
					: updateSolvedBusVoltagesAndMismatch(primitiveMatrix, busCache);
			if(!voltageUpdate.valid) {
				log.warn("Fixed-point power-flow equation solve produced invalid bus voltages");
				return false;
			}
			FIXED_POINT_PROFILE.addVoltageUpdate(FIXED_POINT_PROFILE.elapsed(start));

			start = FIXED_POINT_PROFILE.start();
			double maxVoltageMismatch = maxSwingVoltageMismatch(busCache, voltageUpdate.maxMismatch);
			FIXED_POINT_PROFILE.addMismatch(FIXED_POINT_PROFILE.elapsed(start));
			if(i > 0 && maxVoltageMismatch <= this.getTolerance()) {
				log.debug("Distribution fixed-point power flow converged, iterations={}", i);
				updateFixedPointPostSolveOutputs();
				this.pfFlag = true;
				FIXED_POINT_PROFILE.addConvergedAttempt();
				break;
			}
		}

		if(!this.pfFlag) {
			log.warn("Fixed-point power-flow did not converge within " + this.maxIteration
					+ " iterations");
			return false;
		}

		FIXED_POINT_PROFILE.printSummary();
		return this.pfFlag;
	}

	private boolean shouldRetryFixedPointWithoutCache() {
		return this.fixedPointYMatrixCacheEnabled
				&& this.regulatorControlEnabled
				&& !this.regulatorControls.isEmpty()
				&& !this.fixedPointRegulatorBaseAdmittance.isEmpty();
	}

	private void updateFixedPointPostSolveOutputs() {
		if(this.postSolveOutputMode == DistributionPostSolveOutputMode.VOLTAGE_ONLY) {
			return;
		}

		if(this.postSolveOutputMode == DistributionPostSolveOutputMode.FULL_BRANCH_CURRENTS) {
			long start = FIXED_POINT_PROFILE.start();
			syncPositiveSequenceVoltages();
			FIXED_POINT_PROFILE.addSequenceSync(FIXED_POINT_PROFILE.elapsed(start));
			start = FIXED_POINT_PROFILE.start();
			updateBranchCurrentsFromSolvedVoltages();
			FIXED_POINT_PROFILE.addBranchCurrent(FIXED_POINT_PROFILE.elapsed(start));
			start = FIXED_POINT_PROFILE.start();
			calcSwingBusGenPower();
			FIXED_POINT_PROFILE.addSwingPower(FIXED_POINT_PROFILE.elapsed(start));
			return;
		}

		long start = FIXED_POINT_PROFILE.start();
		calcSwingBusGenPowerFromSolvedVoltages();
		FIXED_POINT_PROFILE.addSwingPower(FIXED_POINT_PROFILE.elapsed(start));
	}

	private ISparseEqnComplexMatrix3x3 fixedPointYMatrix(
			BaseAclfNetwork<? extends BaseAclfBus<? extends AclfGen, ? extends AclfLoad>, ? extends AclfBranch> distNet)
			throws IpssNumericException {
		long start = FIXED_POINT_PROFILE.start();
		String signature = fixedPointYMatrixSignature(distNet);
		String symbolSignature = fixedPointYMatrixSymbolSignature(distNet);
		FIXED_POINT_PROFILE.addMatrixSignature(FIXED_POINT_PROFILE.elapsed(start));
		if(this.fixedPointYMatrixCacheEnabled
				&& this.fixedPointYMatrixCache != null
				&& this.fixedPointYMatrixCacheNetwork == distNet
				&& signature.equals(this.fixedPointYMatrixCacheSignature)) {
			return this.fixedPointYMatrixCache;
		}

		boolean reuseSymbolTable = fixedPointSymbolReuseEnabled()
				&& this.fixedPointYMatrixSymbolTable != null
				&& symbolSignature.equals(this.fixedPointYMatrixSymbolSignature);
		ISparseEqnComplexMatrix3x3 yMatrix = null;
		boolean updatedExistingValues = false;
		if(!this.fixedPointYMatrixCacheEnabled
				&& fixedPointValueUpdateEnabled()
				&& reuseSymbolTable
				&& canUpdateFixedPointYMatrixValues(distNet, symbolSignature)) {
			yMatrix = this.fixedPointYMatrixValueUpdateCache;
			updatedExistingValues = updateFixedPointYMatrixRegulatorValues(yMatrix, distNet);
		}
		if(yMatrix == null) {
			start = FIXED_POINT_PROFILE.start();
			yMatrix = formYMatrixABCForPowerflow(distNet);
			FIXED_POINT_PROFILE.addMatrixAssembly(FIXED_POINT_PROFILE.elapsed(start));
			start = FIXED_POINT_PROFILE.start();
			applyRegulatorSeriesPaddingToFixedPointYMatrix(yMatrix, distNet);
			FIXED_POINT_PROFILE.addMatrixRegulatorPadding(FIXED_POINT_PROFILE.elapsed(start));
			start = FIXED_POINT_PROFILE.start();
			applySwingBusVoltageBoundary(yMatrix);
			FIXED_POINT_PROFILE.addMatrixSwingBoundary(FIXED_POINT_PROFILE.elapsed(start));
		}
		if(reuseSymbolTable) {
			start = FIXED_POINT_PROFILE.start();
			yMatrix.getSparseEqnComplex().setSymbolTable(this.fixedPointYMatrixSymbolTable);
			FIXED_POINT_PROFILE.addMatrixSymbolTableReuse(FIXED_POINT_PROFILE.elapsed(start));
		}
		start = FIXED_POINT_PROFILE.start();
		yMatrix.factorization(!reuseSymbolTable, Constants.Matrix_LU_Tolerance);
		long factorizationNanos = FIXED_POINT_PROFILE.elapsed(start);
		FIXED_POINT_PROFILE.addMatrixFactorization(factorizationNanos);
		if(reuseSymbolTable) {
			FIXED_POINT_PROFILE.addMatrixNumericFactorization(factorizationNanos);
		}
		else {
			FIXED_POINT_PROFILE.addMatrixSymbolicFactorization(factorizationNanos);
		}
		this.fixedPointYMatrixNumericFactorizationCount++;
		if(updatedExistingValues) {
			this.fixedPointYMatrixValueUpdateCount++;
		}
		if(!reuseSymbolTable) {
			this.fixedPointYMatrixSymbolicFactorizationCount++;
			this.fixedPointYMatrixSymbolTable = yMatrix.getSparseEqnComplex().getSymbolTable();
			this.fixedPointYMatrixSymbolSignature = symbolSignature;
		}

		if(this.fixedPointYMatrixCacheEnabled) {
			this.fixedPointYMatrixCache = yMatrix;
			this.fixedPointYMatrixCacheNetwork = distNet;
			this.fixedPointYMatrixCacheSignature = signature;
			this.fixedPointRegulatorBaseAdmittance = regulatorBranchAdmittance(distNet,
					regulatorTapCompensationSeriesResistancePaddingPu() > 0.0);
		}
		else {
			this.fixedPointYMatrixValueUpdateCache = yMatrix;
			this.fixedPointYMatrixValueUpdateCacheNetwork = distNet;
			this.fixedPointRegulatorValueUpdateAdmittance = regulatorBranchAdmittance(distNet);
		}
		return yMatrix;
	}

	private void applyRegulatorSeriesPaddingToFixedPointYMatrix(ISparseEqnComplexMatrix3x3 yMatrix,
			BaseAclfNetwork<?, ?> distNet) {
		if(!this.fixedPointYMatrixCacheEnabled
				|| !this.regulatorControlEnabled
				|| this.regulatorControls.isEmpty()
				|| regulatorTapCompensationSeriesResistancePaddingPu() <= 0.0) {
			return;
		}
		for(RegulatorControlData control : this.regulatorControls) {
			IBranch3Phase branch = findRegulatorBranch(distNet, control.getBranchName());
			if(branch == null) {
				continue;
			}
			AclfBranch aclfBranch = (AclfBranch) branch;
			RegulatorBranchAdmittance padded = new RegulatorBranchAdmittance(branch,
					aclfBranch.getFromBus().getSortNumber(), aclfBranch.getToBus().getSortNumber(), true);
			yMatrix.addToA(padded.yff.subtract(branch.getYffabc()),
					padded.fromSortNumber, padded.fromSortNumber);
			yMatrix.addToA(padded.yft.subtract(branch.getYftabc()),
					padded.fromSortNumber, padded.toSortNumber);
			yMatrix.addToA(padded.ytf.subtract(branch.getYtfabc()),
					padded.toSortNumber, padded.fromSortNumber);
			yMatrix.addToA(padded.ytt.subtract(branch.getYttabc()),
					padded.toSortNumber, padded.toSortNumber);
		}
	}

	private boolean fixedPointSymbolReuseEnabled() {
		return !Boolean.getBoolean("ipss.qsts.disableFixedPointSymbolReuse");
	}

	private boolean fixedPointValueUpdateEnabled() {
		return !Boolean.getBoolean("ipss.qsts.disableFixedPointValueUpdate");
	}

	private boolean canUpdateFixedPointYMatrixValues(BaseAclfNetwork<?, ?> distNet, String symbolSignature) {
		if(this.fixedPointYMatrixValueUpdateCache == null
				|| this.fixedPointYMatrixValueUpdateCacheNetwork != distNet
				|| !symbolSignature.equals(this.fixedPointYMatrixSymbolSignature)
				|| this.fixedPointRegulatorValueUpdateAdmittance.isEmpty()) {
			return false;
		}
		for(RegulatorBranchAdmittance base : this.fixedPointRegulatorValueUpdateAdmittance.values()) {
			AclfBranch branch = (AclfBranch) base.branch;
			if(((BaseAclfBus<?, ?>) branch.getFromBus()).isSwing()
					|| ((BaseAclfBus<?, ?>) branch.getToBus()).isSwing()) {
				return false;
			}
		}
		return true;
	}

	private boolean updateFixedPointYMatrixRegulatorValues(ISparseEqnComplexMatrix3x3 yMatrix,
			BaseAclfNetwork<?, ?> distNet) {
		Map<String, RegulatorBranchAdmittance> updatedAdmittance = new LinkedHashMap<>();
		boolean changed = false;
		for(RegulatorBranchAdmittance previous : this.fixedPointRegulatorValueUpdateAdmittance.values()) {
			IBranch3Phase branch = previous.branch;
			AclfBranch aclfBranch = (AclfBranch) branch;
			if(!aclfBranch.isActive()) {
				continue;
			}
			Complex3x3 deltaYff = branch.getYffabc().subtract(previous.yff);
			Complex3x3 deltaYft = branch.getYftabc().subtract(previous.yft);
			Complex3x3 deltaYtf = branch.getYtfabc().subtract(previous.ytf);
			Complex3x3 deltaYtt = branch.getYttabc().subtract(previous.ytt);
			if(deltaYff.absMax() > 0.0 || deltaYft.absMax() > 0.0
					|| deltaYtf.absMax() > 0.0 || deltaYtt.absMax() > 0.0) {
				yMatrix.addToA(deltaYff, previous.fromSortNumber, previous.fromSortNumber);
				yMatrix.addToA(deltaYft, previous.fromSortNumber, previous.toSortNumber);
				yMatrix.addToA(deltaYtf, previous.toSortNumber, previous.fromSortNumber);
				yMatrix.addToA(deltaYtt, previous.toSortNumber, previous.toSortNumber);
				changed = true;
			}
			updatedAdmittance.put(aclfBranch.getId(), new RegulatorBranchAdmittance(branch,
					aclfBranch.getFromBus().getSortNumber(), aclfBranch.getToBus().getSortNumber()));
		}
		this.fixedPointRegulatorValueUpdateAdmittance = updatedAdmittance.isEmpty()
				? Collections.emptyMap() : Collections.unmodifiableMap(updatedAdmittance);
		return changed;
	}

	private Map<String, RegulatorBranchAdmittance> regulatorBranchAdmittance(BaseAclfNetwork<?, ?> distNet) {
		return regulatorBranchAdmittance(distNet, false);
	}

	private Map<String, RegulatorBranchAdmittance> regulatorBranchAdmittance(BaseAclfNetwork<?, ?> distNet,
			boolean padded) {
		if(!this.regulatorControlEnabled || this.regulatorControls.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, RegulatorBranchAdmittance> admittanceByBranchId = new LinkedHashMap<>();
		for(RegulatorControlData control : this.regulatorControls) {
			IBranch3Phase branch = findRegulatorBranch(distNet, control.getBranchName());
			if(branch == null) {
				continue;
			}
			AclfBranch aclfBranch = (AclfBranch) branch;
			admittanceByBranchId.put(aclfBranch.getId(), new RegulatorBranchAdmittance(branch,
					aclfBranch.getFromBus().getSortNumber(), aclfBranch.getToBus().getSortNumber(), padded));
		}
		return admittanceByBranchId.isEmpty() ? Collections.emptyMap()
				: Collections.unmodifiableMap(admittanceByBranchId);
	}

	private IBranch3Phase findRegulatorBranch(BaseAclfNetwork<?, ?> network, String branchName) {
		for(AclfBranch branch : (List<AclfBranch>) network.getBranchList()) {
			if(branch.isActive() && branch instanceof IBranch3Phase
					&& (branch.getName().equals(branchName) || branch.getId().equals(branchName))) {
				return (IBranch3Phase) branch;
			}
		}
		return null;
	}

	private String fixedPointYMatrixSignature(BaseAclfNetwork<?, ?> distNet) {
		StringBuilder builder = new StringBuilder();
		builder.append(System.identityHashCode(distNet)).append('|')
				.append(distNet.getNoBus()).append('|');
		for(BaseAclfBus<?, ?> bus : (List<BaseAclfBus<?, ?>>) distNet.getBusList()) {
			if(bus.isActive() && bus.isSwing()) {
				builder.append(bus.getId()).append(':').append(bus.getSortNumber()).append(':')
						.append(threePhaseBus(bus).get3PhaseVotlages()).append(';');
			}
		}
		return builder.toString();
	}

	private String fixedPointYMatrixSymbolSignature(BaseAclfNetwork<?, ?> distNet) {
		StringBuilder builder = new StringBuilder();
		builder.append(System.identityHashCode(distNet)).append('|')
				.append(distNet.getNoBus()).append('|');
		for(BaseAclfBus<?, ?> bus : (List<BaseAclfBus<?, ?>>) distNet.getBusList()) {
			if(bus.isActive()) {
				builder.append("B:")
						.append(bus.getSortNumber()).append(':')
						.append(bus.isSwing()).append(';');
			}
		}
		for(AclfBranch branch : (List<AclfBranch>) distNet.getBranchList()) {
			if(branch.isActive()) {
				IBranch3Phase branch3P = threePhaseBranch(branch);
				builder.append("R:")
						.append(branch.getId()).append(':')
						.append(branch.getFromBus().getSortNumber()).append("->")
						.append(branch.getToBus().getSortNumber()).append(':')
						.append(branch3P.getPhaseCode()).append(':')
						.append(branch.isXfr()).append(':')
						.append(branch.isLine()).append(';');
			}
		}
		return builder.toString();
	}

	private ISparseEqnComplexMatrix3x3 formYMatrixABCForPowerflow(BaseAclfNetwork distNet) throws IpssNumericException {
		long start = FIXED_POINT_PROFILE.start();
		ISparseEqnComplexMatrix3x3 yMatrix =
				new SparseEqnObjectFactory().createSparseEqnComplex3x3(distNet.getNoBus());
		FIXED_POINT_PROFILE.addMatrixSparseCreate(FIXED_POINT_PROFILE.elapsed(start));

		for(BaseAclfBus bus: (List<BaseAclfBus>) distNet.getBusList()) {
			if(bus.isActive()) {
				int i = bus.getSortNumber();
				start = FIXED_POINT_PROFILE.start();
				Complex3x3 yii = threePhaseBus(bus).getYiiAbcForPowerflow();
				FIXED_POINT_PROFILE.addMatrixBusAdmittance(FIXED_POINT_PROFILE.elapsed(start));

				if(!bus.isSwing()) {
					// replace zero diagonal entries with 1.0 to avoid singularity
					// for partial-phase buses (e.g., 1-ph or 2-ph connections)
					double yiiMinTolerance = 1.0E-8;
					if(yii.aa.abs() < yiiMinTolerance) yii.aa = new Complex(1.0, 0);
					if(yii.bb.abs() < yiiMinTolerance) yii.bb = new Complex(1.0, 0);
					if(yii.cc.abs() < yiiMinTolerance) yii.cc = new Complex(1.0, 0);
				}

				start = FIXED_POINT_PROFILE.start();
				yMatrix.setA(yii, i, i);
				FIXED_POINT_PROFILE.addMatrixSparseInsertion(FIXED_POINT_PROFILE.elapsed(start));
			}
		}

		for(AclfBranch branch: (List<AclfBranch>) distNet.getBranchList()) {
			if(branch.isActive()) {
				IBranch3Phase branch3P = threePhaseBranch(branch);
				int i = branch.getFromBus().getSortNumber();
				int j = branch.getToBus().getSortNumber();
				start = FIXED_POINT_PROFILE.start();
				Complex3x3 yft = branch3P.getYftabc();
				Complex3x3 ytf = branch3P.getYtfabc();
				FIXED_POINT_PROFILE.addMatrixBranchAdmittance(FIXED_POINT_PROFILE.elapsed(start));
				start = FIXED_POINT_PROFILE.start();
				yMatrix.addToA(yft, i, j);
				yMatrix.addToA(ytf, j, i);
				FIXED_POINT_PROFILE.addMatrixSparseInsertion(FIXED_POINT_PROFILE.elapsed(start));
				start = FIXED_POINT_PROFILE.start();
				addTransformerAntiFloatAdmittance(yMatrix, branch);
				FIXED_POINT_PROFILE.addMatrixAntiFloat(FIXED_POINT_PROFILE.elapsed(start));
			}
		}
		start = FIXED_POINT_PROFILE.start();
		addFloatingPhaseComponentAntiFloatAdmittance(yMatrix, distNet);
		addNonSwingBusAntiFloatAdmittance(yMatrix, distNet);
		FIXED_POINT_PROFILE.addMatrixAntiFloat(FIXED_POINT_PROFILE.elapsed(start));

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

	private void setPowerflowCurrentInjections(ISparseEqnComplexMatrix3x3 yMatrix, FixedPointBusCache busCache) {
		long start = FIXED_POINT_PROFILE.start();
		Map<Integer, Complex3x1> regulatorTapCompensation = regulatorTapCompensationCurrents();
		boolean hasTapCompensation = !regulatorTapCompensation.isEmpty();
		FIXED_POINT_PROFILE.addCurrentInjectionRegulator(FIXED_POINT_PROFILE.elapsed(start));
		for(FixedPointBus bus : busCache.nonSwingBuses) {
				IBus3Phase bus3P = bus.bus3P;
				FIXED_POINT_PROFILE.addCurrentInjectionBus();
				start = FIXED_POINT_PROFILE.start();
				Complex3x1 curInj = calc3PhEquivCurInjProfiled(bus);
				FIXED_POINT_PROFILE.addCurrentInjectionCalc(FIXED_POINT_PROFILE.elapsed(start));
				long rhsStart = FIXED_POINT_PROFILE.start();
				start = FIXED_POINT_PROFILE.start();
				if(!isFinite(curInj.a_0) || !isFinite(curInj.b_1) || !isFinite(curInj.c_2)) {
					log.warn("Invalid fixed-point current injection at bus " + bus.id
							+ ", sortNumber=" + bus.sortNumber + ", iabc=" + curInj
							+ ", vabc=" + bus3P.get3PhaseVotlages());
				}
				FIXED_POINT_PROFILE.addCurrentRhsFinite(FIXED_POINT_PROFILE.elapsed(start));
				start = FIXED_POINT_PROFILE.start();
				Complex3x1 boundaryCurrent = bus.boundaryCurrent;
				Complex3x1 tapCompensation = hasTapCompensation
						? regulatorTapCompensation.get(bus.sortNumber) : null;
				FIXED_POINT_PROFILE.addCurrentRhsLookup(FIXED_POINT_PROFILE.elapsed(start));
				start = FIXED_POINT_PROFILE.start();
				if(!bus.boundaryCurrentFinite) {
					log.warn("Invalid fixed-point swing-boundary current at bus " + bus.id
							+ ", sortNumber=" + bus.sortNumber + ", iabc=" + boundaryCurrent);
				}
				FIXED_POINT_PROFILE.addCurrentRhsBoundaryFinite(FIXED_POINT_PROFILE.elapsed(start));
				start = FIXED_POINT_PROFILE.start();
				Complex3x1 rhs;
				if(boundaryCurrent == null && tapCompensation == null) {
					rhs = curInj;
				}
				else {
					rhs = new Complex3x1(
							new Complex(rhsReal(curInj.a_0, bus.boundaryAReal, tapCompensation == null ? null : tapCompensation.a_0),
									rhsImaginary(curInj.a_0, bus.boundaryAImaginary, tapCompensation == null ? null : tapCompensation.a_0)),
							new Complex(rhsReal(curInj.b_1, bus.boundaryBReal, tapCompensation == null ? null : tapCompensation.b_1),
									rhsImaginary(curInj.b_1, bus.boundaryBImaginary, tapCompensation == null ? null : tapCompensation.b_1)),
							new Complex(rhsReal(curInj.c_2, bus.boundaryCReal, tapCompensation == null ? null : tapCompensation.c_2),
									rhsImaginary(curInj.c_2, bus.boundaryCImaginary, tapCompensation == null ? null : tapCompensation.c_2)));
				}
				FIXED_POINT_PROFILE.addCurrentRhsCompose(FIXED_POINT_PROFILE.elapsed(start));
				start = FIXED_POINT_PROFILE.start();
				yMatrix.setBi(rhs, bus.sortNumber);
				FIXED_POINT_PROFILE.addCurrentRhsWrite(FIXED_POINT_PROFILE.elapsed(start));
				FIXED_POINT_PROFILE.addCurrentInjectionRhs(FIXED_POINT_PROFILE.elapsed(rhsStart));
		}
	}

	private void setPowerflowCurrentInjections(PrimitiveComplex3x3Equation yMatrix, FixedPointBusCache busCache) {
		long start = FIXED_POINT_PROFILE.start();
		Map<Integer, Complex3x1> regulatorTapCompensation = regulatorTapCompensationCurrents();
		boolean hasTapCompensation = !regulatorTapCompensation.isEmpty();
		FIXED_POINT_PROFILE.addCurrentInjectionRegulator(FIXED_POINT_PROFILE.elapsed(start));
		for(FixedPointBus bus : busCache.nonSwingBuses) {
				IBus3Phase bus3P = bus.bus3P;
				FIXED_POINT_PROFILE.addCurrentInjectionBus();
				start = FIXED_POINT_PROFILE.start();
				double[] curInj = calc3PhEquivCurInjPrimitiveProfiled(bus);
				FIXED_POINT_PROFILE.addCurrentInjectionCalc(FIXED_POINT_PROFILE.elapsed(start));
				long rhsStart = FIXED_POINT_PROFILE.start();
				start = FIXED_POINT_PROFILE.start();
				if(!isFinite(curInj)) {
					log.warn("Invalid fixed-point current injection at bus " + bus.id
							+ ", sortNumber=" + bus.sortNumber + ", iabc=" + format3x1(curInj)
							+ ", vabc=" + bus3P.get3PhaseVotlages());
				}
				FIXED_POINT_PROFILE.addCurrentRhsFinite(FIXED_POINT_PROFILE.elapsed(start));
				start = FIXED_POINT_PROFILE.start();
				Complex3x1 boundaryCurrent = bus.boundaryCurrent;
				Complex3x1 tapCompensation = hasTapCompensation
						? regulatorTapCompensation.get(bus.sortNumber) : null;
				FIXED_POINT_PROFILE.addCurrentRhsLookup(FIXED_POINT_PROFILE.elapsed(start));
				start = FIXED_POINT_PROFILE.start();
				if(!bus.boundaryCurrentFinite) {
					log.warn("Invalid fixed-point swing-boundary current at bus " + bus.id
							+ ", sortNumber=" + bus.sortNumber + ", iabc=" + boundaryCurrent);
				}
				FIXED_POINT_PROFILE.addCurrentRhsBoundaryFinite(FIXED_POINT_PROFILE.elapsed(start));
				if(boundaryCurrent == null && tapCompensation == null) {
					start = FIXED_POINT_PROFILE.start();
					FIXED_POINT_PROFILE.addCurrentRhsCompose(FIXED_POINT_PROFILE.elapsed(start));
					start = FIXED_POINT_PROFILE.start();
					yMatrix.setPrimitiveRhs3x1(bus.sortNumber,
							curInj[0], curInj[1],
							curInj[2], curInj[3],
							curInj[4], curInj[5]);
					FIXED_POINT_PROFILE.addCurrentRhsWrite(FIXED_POINT_PROFILE.elapsed(start));
				}
				else {
					start = FIXED_POINT_PROFILE.start();
					double aReal = curInj[0] - bus.boundaryAReal
							- real(tapCompensation == null ? null : tapCompensation.a_0);
					double aImaginary = curInj[1] - bus.boundaryAImaginary
							- imaginary(tapCompensation == null ? null : tapCompensation.a_0);
					double bReal = curInj[2] - bus.boundaryBReal
							- real(tapCompensation == null ? null : tapCompensation.b_1);
					double bImaginary = curInj[3] - bus.boundaryBImaginary
							- imaginary(tapCompensation == null ? null : tapCompensation.b_1);
					double cReal = curInj[4] - bus.boundaryCReal
							- real(tapCompensation == null ? null : tapCompensation.c_2);
					double cImaginary = curInj[5] - bus.boundaryCImaginary
							- imaginary(tapCompensation == null ? null : tapCompensation.c_2);
					FIXED_POINT_PROFILE.addCurrentRhsCompose(FIXED_POINT_PROFILE.elapsed(start));
					start = FIXED_POINT_PROFILE.start();
					yMatrix.setPrimitiveRhs3x1(bus.sortNumber,
							aReal, aImaginary,
							bReal, bImaginary,
							cReal, cImaginary);
					FIXED_POINT_PROFILE.addCurrentRhsWrite(FIXED_POINT_PROFILE.elapsed(start));
				}
				FIXED_POINT_PROFILE.addCurrentInjectionRhs(FIXED_POINT_PROFILE.elapsed(rhsStart));
		}
	}

	private Map<Integer, Complex3x1> regulatorTapCompensationCurrents() {
		if(!this.fixedPointYMatrixCacheEnabled || !this.regulatorControlEnabled
				|| this.regulatorControls.isEmpty()
				|| this.fixedPointRegulatorBaseAdmittance.isEmpty()) {
			this.regulatorTapCompensationState.clear();
			return Collections.emptyMap();
		}
		Hashtable<Integer, Complex3x1> compensationByBus = new Hashtable<>();
		for(RegulatorBranchAdmittance base : this.fixedPointRegulatorBaseAdmittance.values()) {
			IBranch3Phase branch = base.branch;
			AclfBranch aclfBranch = (AclfBranch) branch;
			if(!aclfBranch.isActive()) {
				continue;
			}
			Complex3x1 fromVoltage = phaseMaskedValue(
					threePhaseBus((BaseAclfBus<?, ?>) aclfBranch.getFromBus()).get3PhaseVotlages(),
					base.phaseMask);
			Complex3x1 toVoltage = phaseMaskedValue(
					threePhaseBus((BaseAclfBus<?, ?>) aclfBranch.getToBus()).get3PhaseVotlages(),
					base.phaseMask);
			Complex3x1 fromDeltaCurrent = branch.getYffabc().subtract(base.yff).multiply(fromVoltage)
					.add(branch.getYftabc().subtract(base.yft).multiply(toVoltage));
			Complex3x1 toDeltaCurrent = branch.getYtfabc().subtract(base.ytf).multiply(fromVoltage)
					.add(branch.getYttabc().subtract(base.ytt).multiply(toVoltage));
			writeRegulatorCompensationDiagnostic(base, branch, fromVoltage, toVoltage,
					fromDeltaCurrent, toDeltaCurrent);
			addCompensationCurrent(compensationByBus, base.fromSortNumber,
					phaseMaskedValue(fromDeltaCurrent, base.phaseMask));
			addCompensationCurrent(compensationByBus, base.toSortNumber,
					phaseMaskedValue(toDeltaCurrent, base.phaseMask));
		}
		compensationByBus = dampRegulatorTapCompensation(compensationByBus);
		this.regulatorCompensationDiagnosticEval++;
		return compensationByBus;
	}

	private Complex3x1 calc3PhEquivCurInjProfiled(FixedPointBus bus) {
		IBus3Phase bus3P = bus.bus3P;
		if(!FIXED_POINT_PROFILE.enabled()) {
			return bus3P.calc3PhEquivCurInj();
		}
		long start = FIXED_POINT_PROFILE.start();
		Complex3x1 current = new Complex3x1();
		FIXED_POINT_PROFILE.addCurrentCalcInit(FIXED_POINT_PROFILE.elapsed(start));

		start = FIXED_POINT_PROFILE.start();
		Complex3x1 voltage = bus3P.get3PhaseVotlages();
		FIXED_POINT_PROFILE.addCurrentCalcVoltage(FIXED_POINT_PROFILE.elapsed(start));

		start = FIXED_POINT_PROFILE.start();
		List<? extends IPhaseLoad> loads = bus.phaseLoads;
		FIXED_POINT_PROFILE.addCurrentCalcLoadList(FIXED_POINT_PROFILE.elapsed(start));
		for(IPhaseLoad load : loads) {
			FIXED_POINT_PROFILE.addCurrentCalcLoad();
			start = FIXED_POINT_PROFILE.start();
			Complex3x1 loadCurrent = load.getEquivCurrInj(voltage);
			FIXED_POINT_PROFILE.addCurrentCalcLoadCurrent(FIXED_POINT_PROFILE.elapsed(start));
			start = FIXED_POINT_PROFILE.start();
			current = current.add(loadCurrent);
			FIXED_POINT_PROFILE.addCurrentCalcLoadAdd(FIXED_POINT_PROFILE.elapsed(start));
		}

		start = FIXED_POINT_PROFILE.start();
		List<? extends IPhaseGen> generators = bus.phaseGenerators;
		FIXED_POINT_PROFILE.addCurrentCalcGenList(FIXED_POINT_PROFILE.elapsed(start));
		for(IPhaseGen gen : generators) {
			FIXED_POINT_PROFILE.addCurrentCalcGen();
			start = FIXED_POINT_PROFILE.start();
			Complex3x1 power = gen.getPower3Phase(UnitType.PU);
			FIXED_POINT_PROFILE.addCurrentCalcGenPower(FIXED_POINT_PROFILE.elapsed(start));
			if(power != null) {
				start = FIXED_POINT_PROFILE.start();
				Complex3x1 genCurrent = power.divide(voltage).conjugate();
				FIXED_POINT_PROFILE.addCurrentCalcGenCurrent(FIXED_POINT_PROFILE.elapsed(start));
				start = FIXED_POINT_PROFILE.start();
				current = current.add(genCurrent);
				FIXED_POINT_PROFILE.addCurrentCalcGenAdd(FIXED_POINT_PROFILE.elapsed(start));
			}
		}
		return current;
	}

	private double[] calc3PhEquivCurInjPrimitiveProfiled(FixedPointBus bus) {
		double[] current = bus.currentInjectionValues;
		current[0] = 0.0;
		current[1] = 0.0;
		current[2] = 0.0;
		current[3] = 0.0;
		current[4] = 0.0;
		current[5] = 0.0;

		long start = FIXED_POINT_PROFILE.start();
		Complex3x1 voltage = bus.bus3P.get3PhaseVotlages();
		FIXED_POINT_PROFILE.addCurrentCalcVoltage(FIXED_POINT_PROFILE.elapsed(start));

		start = FIXED_POINT_PROFILE.start();
		List<? extends IPhaseLoad> loads = bus.phaseLoads;
		FIXED_POINT_PROFILE.addCurrentCalcLoadList(FIXED_POINT_PROFILE.elapsed(start));
		for(IPhaseLoad load : loads) {
			FIXED_POINT_PROFILE.addCurrentCalcLoad();
			start = FIXED_POINT_PROFILE.start();
			if(load instanceof Static3PLoad staticLoad) {
				staticLoad.addEquivCurrInj(voltage, current);
				FIXED_POINT_PROFILE.addCurrentCalcLoadCurrent(FIXED_POINT_PROFILE.elapsed(start));
			}
			else {
				Complex3x1 loadCurrent = load.getEquivCurrInj(voltage);
				FIXED_POINT_PROFILE.addCurrentCalcLoadCurrent(FIXED_POINT_PROFILE.elapsed(start));
				start = FIXED_POINT_PROFILE.start();
				addCurrent(current, loadCurrent);
				FIXED_POINT_PROFILE.addCurrentCalcLoadAdd(FIXED_POINT_PROFILE.elapsed(start));
			}
		}

		start = FIXED_POINT_PROFILE.start();
		List<? extends IPhaseGen> generators = bus.phaseGenerators;
		FIXED_POINT_PROFILE.addCurrentCalcGenList(FIXED_POINT_PROFILE.elapsed(start));
		for(IPhaseGen gen : generators) {
			FIXED_POINT_PROFILE.addCurrentCalcGen();
			start = FIXED_POINT_PROFILE.start();
			Complex3x1 power = gen.getPower3Phase(UnitType.PU);
			FIXED_POINT_PROFILE.addCurrentCalcGenPower(FIXED_POINT_PROFILE.elapsed(start));
			if(power != null) {
				start = FIXED_POINT_PROFILE.start();
				Complex3x1 genCurrent = power.divide(voltage).conjugate();
				FIXED_POINT_PROFILE.addCurrentCalcGenCurrent(FIXED_POINT_PROFILE.elapsed(start));
				start = FIXED_POINT_PROFILE.start();
				addCurrent(current, genCurrent);
				FIXED_POINT_PROFILE.addCurrentCalcGenAdd(FIXED_POINT_PROFILE.elapsed(start));
			}
		}
		return current;
	}

	private void addCurrent(double[] current, Complex3x1 value) {
		if(value == null) {
			return;
		}
		current[0] += real(value.a_0);
		current[1] += imaginary(value.a_0);
		current[2] += real(value.b_1);
		current[3] += imaginary(value.b_1);
		current[4] += real(value.c_2);
		current[5] += imaginary(value.c_2);
	}

	private Hashtable<Integer, Complex3x1> dampRegulatorTapCompensation(
			Hashtable<Integer, Complex3x1> calculatedByBus) {
		double gamma = regulatorTapCompensationDampingFactor();
		if(gamma >= 1.0) {
			this.regulatorTapCompensationState = calculatedByBus;
			return calculatedByBus;
		}
		Hashtable<Integer, Complex3x1> dampedByBus = new Hashtable<>();
		for(Integer sortNumber : calculatedByBus.keySet()) {
			Complex3x1 calculated = calculatedByBus.get(sortNumber);
			Complex3x1 previous = this.regulatorTapCompensationState.get(sortNumber);
			Complex3x1 damped = previous == null ? calculated.multiply(gamma)
					: previous.add(calculated.subtract(previous).multiply(gamma));
			dampedByBus.put(sortNumber, damped);
		}
		this.regulatorTapCompensationState = dampedByBus;
		return dampedByBus;
	}

	private double regulatorTapCompensationDampingFactor() {
		String configured = System.getProperty("ipss.qsts.regulatorCompensationDamping");
		if(configured == null || configured.isBlank()) {
			return 0.25;
		}
		try {
			double gamma = Double.parseDouble(configured);
			if(Double.isFinite(gamma) && gamma > 0.0) {
				return Math.min(1.0, gamma);
			}
		}
		catch(NumberFormatException e) {
			log.warn("Ignoring invalid regulator compensation damping factor: " + configured);
		}
		return 0.25;
	}

	private double regulatorTapCompensationSeriesResistancePaddingPu() {
		String configured = System.getProperty("ipss.qsts.regulatorCompensationSeriesRPadPu");
		if(configured == null || configured.isBlank()) {
			return 0.0;
		}
		try {
			double rPad = Double.parseDouble(configured);
			if(Double.isFinite(rPad) && rPad > 0.0) {
				return rPad;
			}
		}
		catch(NumberFormatException e) {
			log.warn("Ignoring invalid regulator compensation series resistance padding: " + configured);
		}
		return 0.0;
	}

	static Complex3x1 dampCompensationCurrentForTesting(Complex3x1 previous,
			Complex3x1 calculated, double gamma) {
		return previous == null ? calculated.multiply(gamma)
				: previous.add(calculated.subtract(previous).multiply(gamma));
	}

	private void addCompensationCurrent(Hashtable<Integer, Complex3x1> compensationByBus,
			int sortNumber, Complex3x1 current) {
		Complex3x1 existing = compensationByBus.get(sortNumber);
		compensationByBus.put(sortNumber, existing == null ? current : existing.add(current));
	}

	private void writeRegulatorCompensationDiagnostic(RegulatorBranchAdmittance base,
			IBranch3Phase branch, Complex3x1 fromVoltage, Complex3x1 toVoltage,
			Complex3x1 fromDeltaCurrent, Complex3x1 toDeltaCurrent) {
		String csvPath = System.getProperty("ipss.qsts.regulatorCompensationCsv");
		if(csvPath == null || csvPath.isBlank()) {
			return;
		}
		AclfBranch aclfBranch = (AclfBranch) branch;
		double[] fromRatios = transformerFromTurnRatios(branch);
		double[] toRatios = transformerToTurnRatios(branch);
		double deltaYAbsMax = Math.max(
				Math.max(branch.getYffabc().subtract(base.yff).absMax(),
						branch.getYftabc().subtract(base.yft).absMax()),
				Math.max(branch.getYtfabc().subtract(base.ytf).absMax(),
						branch.getYttabc().subtract(base.ytt).absMax()));
		StringBuilder builder = new StringBuilder();
		if(!this.regulatorCompensationDiagnosticHeaderWritten) {
			builder.append("eval,branch,phase,from_bus,to_bus,from_ratio_a,from_ratio_b,from_ratio_c,")
					.append("to_ratio_a,to_ratio_b,to_ratio_c,delta_y_abs_max,")
					.append("from_v_abs_max,to_v_abs_max,from_icomp_abs_max,to_icomp_abs_max,")
					.append("from_icomp_a,from_icomp_b,from_icomp_c,to_icomp_a,to_icomp_b,to_icomp_c\n");
			this.regulatorCompensationDiagnosticHeaderWritten = true;
		}
		builder.append(String.format(Locale.US,
				"%d,%s,%s,%s,%s,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%s,%s,%s,%s,%s,%s%n",
				this.regulatorCompensationDiagnosticEval,
				aclfBranch.getId(),
				branch.getPhaseCode(),
				aclfBranch.getFromBus().getId(),
				aclfBranch.getToBus().getId(),
				fromRatios[0], fromRatios[1], fromRatios[2],
				toRatios[0], toRatios[1], toRatios[2],
				deltaYAbsMax,
				complex3x1AbsMax(fromVoltage),
				complex3x1AbsMax(toVoltage),
				complex3x1AbsMax(fromDeltaCurrent),
				complex3x1AbsMax(toDeltaCurrent),
				formatComplex(fromDeltaCurrent.a_0),
				formatComplex(fromDeltaCurrent.b_1),
				formatComplex(fromDeltaCurrent.c_2),
				formatComplex(toDeltaCurrent.a_0),
				formatComplex(toDeltaCurrent.b_1),
				formatComplex(toDeltaCurrent.c_2)));
		try {
			Files.writeString(Path.of(csvPath), builder.toString(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch(IOException e) {
			log.warn("Failed to write regulator compensation diagnostic CSV " + csvPath, e);
		}
	}

	private double complex3x1AbsMax(Complex3x1 value) {
		return Math.max(Math.max(complexAbs(value.a_0), complexAbs(value.b_1)), complexAbs(value.c_2));
	}

	private double complexAbs(Complex value) {
		return value == null ? 0.0 : value.abs();
	}

	private String formatComplex(Complex value) {
		if(value == null) {
			return "0+j0";
		}
		return String.format(Locale.US, "%.12g+j%.12g", value.getReal(), value.getImaginary());
	}

	private void setSwingBusVoltageRhs(ISparseEqnComplexMatrix3x3 yMatrix, FixedPointBusCache busCache) {
		for(FixedPointBus bus : busCache.swingBuses) {
			yMatrix.setBi(bus.bus3P.get3PhaseVotlages(), bus.sortNumber);
		}
	}

	private void setSwingBusVoltageRhs(PrimitiveComplex3x3Equation yMatrix, FixedPointBusCache busCache) {
		for(FixedPointBus bus : busCache.swingBuses) {
			Complex3x1 vabc = bus.bus3P.get3PhaseVotlages();
			yMatrix.setPrimitiveRhs3x1(bus.sortNumber,
					real(vabc.a_0), imaginary(vabc.a_0),
					real(vabc.b_1), imaginary(vabc.b_1),
					real(vabc.c_2), imaginary(vabc.c_2));
		}
	}

	private VoltageUpdateResult updateSolvedBusVoltagesAndMismatch(ISparseEqnComplexMatrix3x3 yMatrix,
			FixedPointBusCache busCache) {
		double maxMismatch = 0.0;
		for(FixedPointBus bus : busCache.nonSwingBuses) {
				Complex3x1 vabc = yMatrix.getX(bus.sortNumber);

				if(isValidFixedPointVoltage(vabc)){
					updateFixedPointVoltage(bus.bus3P, vabc);
					maxMismatch = Math.max(maxMismatch, bus.voltageMismatch(vabc));
				} else {
					log.warn("Fixed-point solve produced invalid voltage at bus " + bus.id
							+ ", sortNumber=" + bus.sortNumber + ", vabc=" + vabc);
					return VoltageUpdateResult.invalid();
				}
		}
		return VoltageUpdateResult.valid(maxMismatch);
	}

	private VoltageUpdateResult updateSolvedBusVoltagesAndMismatch(PrimitiveComplex3x3Equation yMatrix,
			FixedPointBusCache busCache) {
		double maxMismatch = 0.0;
		for(FixedPointBus bus : busCache.nonSwingBuses) {
				Complex3x1 vabc = yMatrix.getPrimitiveSolved3x1(
						bus.sortNumber, bus.bus3P.get3PhaseVotlages());

				if(isValidFixedPointVoltage(vabc)){
					updateFixedPointVoltage(bus.bus3P, vabc);
					maxMismatch = Math.max(maxMismatch, bus.voltageMismatch(vabc));
				} else {
					log.warn("Fixed-point solve produced invalid voltage at bus " + bus.id
							+ ", sortNumber=" + bus.sortNumber + ", vabc=" + vabc);
					return VoltageUpdateResult.invalid();
				}
		}
		return VoltageUpdateResult.valid(maxMismatch);
	}

	private void updateFixedPointVoltage(IBus3Phase bus3P, Complex3x1 vabc) {
		if(bus3P instanceof DStab3PBus) {
			bus3P.set3PhaseVotlages(vabc);
			return;
		}
		Complex3x1 existing = bus3P.get3PhaseVotlages();
		if(existing == null) {
			bus3P.set3PhaseVotlages(vabc);
			return;
		}
		existing.a_0 = vabc.a_0;
		existing.b_1 = vabc.b_1;
		existing.c_2 = vabc.c_2;
	}

	@Override
	public void syncPositiveSequenceVoltages() {
		for(BaseAclfBus bus: aclfNetwork().getBusList()) {
			if(bus.isActive() && !bus.isSwing()) {
				IBus3Phase bus3P = threePhaseBus(bus);
				bus.setVoltage(positiveSequenceVoltage(bus3P));
			}
		}
	}

	private Complex positiveSequenceVoltage(IBus3Phase bus3P) {
		if(bus3P instanceof DStab3PBus dstabBus) {
			return dstabBus.getThreeSeqVoltage().b_1;
		}
		return bus3P.get3PhaseVotlages().to012().b_1;
	}

	private boolean isValidFixedPointVoltage(Complex3x1 vabc) {
		return isFinite(vabc.a_0) && isFinite(vabc.b_1) && isFinite(vabc.c_2)
				&& vabc.absMax() <= this.maxFixedPointVoltageAbs;
	}

	private void saveBusVoltages(FixedPointBusCache busCache) {
		for(FixedPointBus bus : busCache.activeBuses) {
			bus.saveVoltageSnapshot();
		}
	}

	private void saveBusVoltages() {
		for(BaseAclfBus bus: aclfNetwork().getBusList()) {
			if(bus.isActive()) {
				IBus3Phase bus3P = threePhaseBus(bus);
				this.busVoltTable.put(bus.getId(), Complex3x1.valueOf(bus3P.get3PhaseVotlages()));
			}
		}
	}

	private double maxSwingVoltageMismatch(FixedPointBusCache busCache, double maxMis) {

		for(FixedPointBus bus : busCache.swingBuses) {
			maxMis = Math.max(maxMis, bus.voltageMismatch());
		}

		return maxMis;
	}

	private void updateBranchCurrentsFromSolvedVoltages() {
		for(AclfBranch branch: aclfNetwork().getBranchList()) {
			if(branch.isActive()) {
				IBranch3Phase branch3P = sweepBranch(branch);
				Complex3x1 vabcF = threePhaseBus((BaseAclfBus<?, ?>) branch.getFromBus()).get3PhaseVotlages();
				Complex3x1 vabcT = threePhaseBus((BaseAclfBus<?, ?>) branch.getToBus()).get3PhaseVotlages();
				branch3P.setCurrentAbcAtFromSide(branch3P.getYffabc().multiply(vabcF)
						.add(branch3P.getYftabc().multiply(vabcT)));
				branch3P.setCurrentAbcAtToSide(branch3P.getYttabc().multiply(vabcT)
						.add(branch3P.getYtfabc().multiply(vabcF)));
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
					List<IBranch3Phase> unvisitedBranches = new ArrayList<>();
					for (Branch bra: bus.getBranchIterable()){
						IBranch3Phase bra3P = sweepBranch(bra);
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
						IBranch3Phase upStreamBranch = sweepBranch(distNet.getBranch(upStreamBranchId));

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
						if(upStreamBranch != null && acscBranch(upStreamBranch).getFromBus().getId().equals(bus.getId())){

							//calculate and set the upstream branch current
							upStreamBranch.setCurrentAbcAtFromSide(busSelfEquivCurInj3Ph.subtract( sumOfBranchCurrents));

							upStreamBus = (BaseAclfBus) acscBranch(upStreamBranch).getToBus();

							//calculate the voltages at the upstream end
							//NOTE: For, current flowing through the branch, the direction from bus -> to bus  is regarded as positive;
							Complex3x1 vabc = null;
							Complex3x1 iabc = null;


							// line
							if(branchIsLine(upStreamBranch)){
								vabc = upStreamBranch.getToBusVabc2FromBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusVabcMatrix().multiply(upStreamBranch.getCurrentAbcAtFromSide().multiply(-1)));

								//calculate the current injection at the upstream end

								iabc= upStreamBranch.getToBusVabc2FromBusIabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusIabcMatrix().multiply(upStreamBranch.getCurrentAbcAtFromSide().multiply(-1)));

							}

							// transformer
							else if (branchIsXfr(upStreamBranch)){
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

	                        upStreamBus = (BaseAclfBus) acscBranch(upStreamBranch).getFromBus();

	                        //calculate the bus voltage at the upstream end
							Complex3x1 vabc = null;
							Complex3x1 iabc = null;

							// line
							if(branchIsLine(upStreamBranch)){
								vabc =	upStreamBranch.getToBusVabc2FromBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusVabcMatrix().multiply(upStreamBranch.getCurrentAbcAtToSide()));

	                            //calculate the current injection at the upstream end


								iabc = upStreamBranch.getToBusVabc2FromBusIabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusIabcMatrix().multiply(upStreamBranch.getCurrentAbcAtToSide()));
							}

							// transformer
							else if (branchIsXfr(upStreamBranch)){
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
												+ acscBranch(upStreamBranch).getId() + ", name=" + acscBranch(upStreamBranch).getName()
												+ ", from=" + acscBranch(upStreamBranch).getFromBus().getId()
												+ ", to=" + acscBranch(upStreamBranch).getToBus().getId()
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
							IBranch3Phase bra3Phase = sweepBranch(bra);

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
									if(branchIsLine(bra3Phase)){

									   vabc =  bra3Phase.getFromBusVabc2ToBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).subtract(
											bra3Phase.getToBusIabc2ToBusVabcMatrix().multiply(currentOrZero(bra3Phase.getCurrentAbcAtToSide())));
									}
									else if (branchIsXfr(bra3Phase)){
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

									if(branchIsLine(bra3Phase)){
										vabc =  bra3Phase.getFromBusVabc2ToBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
												bra3Phase.getToBusIabc2ToBusVabcMatrix().multiply(currentOrZero(bra3Phase.getCurrentAbcAtFromSide())));
									}
									else if (branchIsXfr(bra3Phase)){
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
				log.debug("Distribution power flow converged, iterations={}", i);
				calcSwingBusGenPower();
				break;
			}


		}



		return this.pfFlag;


	}

	private boolean isPartialPhaseRegulatorTransformer(IBranch3Phase branch) {
		return branchIsXfr(branch)
				&& branch.getPhaseCode() != null
				&& branch.getPhaseCode() != PhaseCode.ABC
				&& isRegulatorBranch(branch);
	}

	private boolean processParallelPartialRegulatorBackward(BaseAclfBus bus, IBus3Phase bus3P,
			Complex3x1 busSelfEquivCurInj3Ph, Complex3x1 sumOfBranchCurrents,
			List<IBranch3Phase> unvisitedBranches, Hashtable<String, Integer> backwardUpdatedPhaseMask) {
		if(unvisitedBranches.size() <= 1) {
			return false;
		}

		IBranch3Phase firstBranch = unvisitedBranches.get(0);
		if(!isPartialPhaseRegulatorTransformer(firstBranch)) {
			return false;
		}

		boolean busIsFromSide = acscBranch(firstBranch).getFromBus().getId().equals(bus.getId());
		BaseAclfBus upStreamBus = (BaseAclfBus) (busIsFromSide ? acscBranch(firstBranch).getToBus() : acscBranch(firstBranch).getFromBus());
		for(IBranch3Phase branch : unvisitedBranches) {
			if(!isPartialPhaseRegulatorTransformer(branch)) {
				return false;
			}
			boolean sameSide = busIsFromSide == acscBranch(branch).getFromBus().getId().equals(bus.getId());
			BaseAclfBus branchUpstreamBus = (BaseAclfBus) (busIsFromSide ? acscBranch(branch).getToBus() : acscBranch(branch).getFromBus());
			if(!sameSide || !branchUpstreamBus.getId().equals(upStreamBus.getId())) {
				return false;
			}
		}

		Complex3x1 totalCurrentAtBus = busIsFromSide
				? busSelfEquivCurInj3Ph.subtract(sumOfBranchCurrents)
				: sumOfBranchCurrents.subtract(busSelfEquivCurInj3Ph);

		for(IBranch3Phase branch : unvisitedBranches) {
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

	private boolean isPartialPhaseLine(IBranch3Phase branch) {
		return branchIsLine(branch)
				&& branch.getPhaseCode() != null
				&& branch.getPhaseCode() != PhaseCode.ABC;
	}

	private void mergeUpstreamPartialBranchVoltage(BaseAclfBus upStreamBus, IBranch3Phase upStreamBranch,
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

	private boolean isRegulatorBranch(IBranch3Phase branch) {
		String id = acscBranch(branch).getId() == null ? "" : acscBranch(branch).getId().toLowerCase();
		String name = acscBranch(branch).getName() == null ? "" : acscBranch(branch).getName().toLowerCase();
		String fromBusId = acscBranch(branch).getFromBus() == null ? "" : acscBranch(branch).getFromBus().getId().toLowerCase();
		String toBusId = acscBranch(branch).getToBus() == null ? "" : acscBranch(branch).getToBus().getId().toLowerCase();
		return id.contains("reg")
				|| name.contains("reg")
				|| fromBusId.endsWith("r")
				|| toBusId.endsWith("r");
	}

	private int branchPhaseMask(IBranch3Phase branch) {
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

	private Complex3x3 copyComplex3x3(Complex3x3 source) {
		Complex3x3 copy = new Complex3x3();
		copy.aa = source.aa;
		copy.ab = source.ab;
		copy.ac = source.ac;
		copy.ba = source.ba;
		copy.bb = source.bb;
		copy.bc = source.bc;
		copy.ca = source.ca;
		copy.cb = source.cb;
		copy.cc = source.cc;
		return copy;
	}

	private Complex3x3 paddedRegulatorAdmittance(IBranch3Phase branch, Complex3x3 source) {
		double rPad = regulatorTapCompensationSeriesResistancePaddingPu();
		if(rPad <= 0.0) {
			return copyComplex3x3(source);
		}
		Complex3x3 zabc = branch.getZabc();
		Complex scaleA = seriesPaddingScale(zabc.aa, rPad);
		Complex scaleB = seriesPaddingScale(zabc.bb, rPad);
		Complex scaleC = seriesPaddingScale(zabc.cc, rPad);
		Complex3x3 padded = new Complex3x3();
		padded.aa = source.aa.multiply(scaleA);
		padded.ab = source.ab.multiply(scaleA);
		padded.ac = source.ac.multiply(scaleA);
		padded.ba = source.ba.multiply(scaleB);
		padded.bb = source.bb.multiply(scaleB);
		padded.bc = source.bc.multiply(scaleB);
		padded.ca = source.ca.multiply(scaleC);
		padded.cb = source.cb.multiply(scaleC);
		padded.cc = source.cc.multiply(scaleC);
		return padded;
	}

	private Complex seriesPaddingScale(Complex zPhase, double rPad) {
		if(zPhase == null || zPhase.abs() <= 0.0) {
			return Complex.ZERO;
		}
		Complex padded = zPhase.add(new Complex(rPad, 0.0));
		if(padded.abs() <= 0.0) {
			return Complex.ZERO;
		}
		return zPhase.divide(padded);
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
						IBranch3Phase bra3P = sweepBranch(bra);
						// all visited branches are on the downstream side, and there should be only one upstream branch


							if(bra.getFromBus().getId().equals(bus.getId())){


							   sumOfBranchCurrents= sumOfBranchCurrents.add(finiteCurrentOrZero(bra3P.getCurrentAbcAtFromSide()));
							}
							else{


								sumOfBranchCurrents= sumOfBranchCurrents.add(finiteCurrentOrZero(bra3P.getCurrentAbcAtToSide()).multiply(-1.0));
							}
					}

			    }

				Complex3x1 seqCurrent = sumOfBranchCurrents.to012();
				log.debug("Source node 3 sequence current into network: {}", seqCurrent);

				Complex posGenPQ = bus3p.get3PhaseVotlages().to012().b_1.multiply(seqCurrent.b_1.conjugate());
				if(bus.getContributeGenList().size()>0){
				   bus.getContributeGenList().get(0).setGen(posGenPQ);
				   log.debug("Source node positive sequence power into network: {}", posGenPQ);

				}
			}
		}
	}

	private void calcSwingBusGenPowerFromSolvedVoltages() {
		for(BaseAclfBus<? extends AclfGen, ? extends AclfLoad> bus: aclfNetwork().getBusList()){
			if(bus.isActive() && bus.isSwing()){
				IBus3Phase bus3p = threePhaseBus(bus);
				Complex3x1 sumOfBranchCurrents = bus3p.calcLoad3PhEquivCurInj().multiply(-1);
				Complex3x1 swingVoltage = bus3p.get3PhaseVotlages();

				for (Branch bra: bus.getBranchIterable()){
					if(bra.isActive()){
						IBranch3Phase bra3P = sweepBranch(bra);
						if(bra.getFromBus().getId().equals(bus.getId())){
							Complex3x1 toVoltage = threePhaseBus((BaseAclfBus<?, ?>) bra.getToBus())
									.get3PhaseVotlages();
							sumOfBranchCurrents = sumOfBranchCurrents.add(
									finiteCurrentOrZero(bra3P.getYffabc().multiply(swingVoltage)
											.add(bra3P.getYftabc().multiply(toVoltage))));
						}
						else{
							Complex3x1 fromVoltage = threePhaseBus((BaseAclfBus<?, ?>) bra.getFromBus())
									.get3PhaseVotlages();
							sumOfBranchCurrents = sumOfBranchCurrents.add(
									finiteCurrentOrZero(bra3P.getYttabc().multiply(swingVoltage)
											.add(bra3P.getYtfabc().multiply(fromVoltage))).multiply(-1.0));
						}
					}
			    }

				Complex3x1 seqCurrent = sumOfBranchCurrents.to012();
				log.debug("Source node 3 sequence current into network: {}", seqCurrent);

				Complex posGenPQ = bus3p.get3PhaseVotlages().to012().b_1.multiply(seqCurrent.b_1.conjugate());
				if(bus.getContributeGenList().size()>0){
				   bus.getContributeGenList().get(0).setGen(posGenPQ);
				   log.debug("Source node positive sequence power into network: {}", posGenPQ);

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
	public DistributionPostSolveOutputMode getPostSolveOutputMode() {
		return this.postSolveOutputMode;
	}

	@Override
	public void setPostSolveOutputMode(DistributionPostSolveOutputMode mode) {
		this.postSolveOutputMode = mode == null
				? DistributionPostSolveOutputMode.FULL_BRANCH_CURRENTS : mode;
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
	public void setRegulatorControls(List<RegulatorControlData> controls) {
		this.regulatorControls = controls == null ? Collections.emptyList() : controls;
	}

	@Override
	public List<RegulatorControlData> getRegulatorControls() {
		return this.regulatorControls;
	}

	@Override
	public void setRegulatorControlEnabled(boolean enabled) {
		this.regulatorControlEnabled = enabled;
	}

	@Override
	public boolean isRegulatorControlEnabled() {
		return this.regulatorControlEnabled;
	}

	@Override
	public void setCapacitorControls(List<CapacitorControlData> controls) {
		this.capacitorControls = controls == null ? Collections.emptyList() : controls;
	}

	@Override
	public List<CapacitorControlData> getCapacitorControls() {
		return this.capacitorControls;
	}

	@Override
	public void setCapacitorControlEnabled(boolean enabled) {
		this.capacitorControlEnabled = enabled;
	}

	@Override
	public boolean isCapacitorControlEnabled() {
		return this.capacitorControlEnabled;
	}

	@Override
	public boolean isFixedPointFallbackUsed() {
		return this.fixedPointFallbackUsed;
	}

	@Override
	public int getFixedPointFallbackCount() {
		return this.fixedPointFallbackCount;
	}

	@Override
	public void setFixedPointYMatrixCacheEnabled(boolean enabled) {
		if(this.fixedPointYMatrixCacheEnabled != enabled) {
			clearFixedPointYMatrixCache();
		}
		this.fixedPointYMatrixCacheEnabled = enabled;
	}

	@Override
	public boolean isFixedPointYMatrixCacheEnabled() {
		return this.fixedPointYMatrixCacheEnabled;
	}

	@Override
	public void clearFixedPointYMatrixCache() {
		this.fixedPointYMatrixCache = null;
		this.fixedPointYMatrixCacheNetwork = null;
		this.fixedPointYMatrixCacheSignature = null;
		this.fixedPointYMatrixValueUpdateCache = null;
		this.fixedPointYMatrixValueUpdateCacheNetwork = null;
		this.fixedPointRegulatorBaseAdmittance = Collections.emptyMap();
		this.fixedPointRegulatorValueUpdateAdmittance = Collections.emptyMap();
		this.regulatorTapCompensationState.clear();
	}

	private void clearFixedPointYMatrixSymbolCache() {
		this.fixedPointYMatrixSymbolSignature = null;
		this.fixedPointYMatrixSymbolTable = null;
	}

	@Override
	public int getFixedPointYMatrixSymbolicFactorizationCount() {
		return this.fixedPointYMatrixSymbolicFactorizationCount;
	}

	@Override
	public int getFixedPointYMatrixNumericFactorizationCount() {
		return this.fixedPointYMatrixNumericFactorizationCount;
	}

	@Override
	public int getFixedPointYMatrixValueUpdateCount() {
		return this.fixedPointYMatrixValueUpdateCount;
	}

	@Override
	public INetwork3Phase getNetwork() {

		return this.distNet;
	}

	@Override
	public void setNetwork(INetwork3Phase net) {
		if(this.distNet != net) {
			clearFixedPointYMatrixCache();
		}
		this.distNet = net;

	}

	public void setNetwork(BaseAclfNetwork<?, ?> net) {
		if(this.distNet != net) {
			clearFixedPointYMatrixCache();
		}
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

	private static class FixedPointBusCache {
		private final List<FixedPointBus> activeBuses;
		private final List<FixedPointBus> nonSwingBuses;
		private final List<FixedPointBus> swingBuses;

		private FixedPointBusCache(List<FixedPointBus> activeBuses,
				List<FixedPointBus> nonSwingBuses,
				List<FixedPointBus> swingBuses) {
			this.activeBuses = activeBuses;
			this.nonSwingBuses = nonSwingBuses;
			this.swingBuses = swingBuses;
		}

		static FixedPointBusCache from(BaseAclfNetwork<?, ?> network,
				Map<Integer, Complex3x1> boundaryCurrentBySortNumber) {
			List<FixedPointBus> activeBuses = new ArrayList<>();
			List<FixedPointBus> nonSwingBuses = new ArrayList<>();
			List<FixedPointBus> swingBuses = new ArrayList<>();
			for(BaseAclfBus<?, ?> bus : (List<BaseAclfBus<?, ?>>) network.getBusList()) {
				if(!bus.isActive()) {
					continue;
				}
				FixedPointBus fixedPointBus = new FixedPointBus(bus,
						boundaryCurrentBySortNumber.get(bus.getSortNumber()));
				activeBuses.add(fixedPointBus);
				if(bus.isSwing()) {
					swingBuses.add(fixedPointBus);
				}
				else {
					nonSwingBuses.add(fixedPointBus);
				}
			}
			return new FixedPointBusCache(
					Collections.unmodifiableList(activeBuses),
					Collections.unmodifiableList(nonSwingBuses),
					Collections.unmodifiableList(swingBuses));
		}
	}

	private static class VoltageUpdateResult {
		private final boolean valid;
		private final double maxMismatch;

		private VoltageUpdateResult(boolean valid, double maxMismatch) {
			this.valid = valid;
			this.maxMismatch = maxMismatch;
		}

		private static VoltageUpdateResult valid(double maxMismatch) {
			return new VoltageUpdateResult(true, maxMismatch);
		}

		private static VoltageUpdateResult invalid() {
			return new VoltageUpdateResult(false, Double.NaN);
		}
	}

	private static class FixedPointBus {
		private final String id;
		private final int sortNumber;
		private final IBus3Phase bus3P;
		private final List<? extends IPhaseLoad> phaseLoads;
		private final List<? extends IPhaseGen> phaseGenerators;
		private final Complex3x1 boundaryCurrent;
		private final boolean boundaryCurrentFinite;
		private final double boundaryAReal;
		private final double boundaryAImaginary;
		private final double boundaryBReal;
		private final double boundaryBImaginary;
		private final double boundaryCReal;
		private final double boundaryCImaginary;
		private final double[] currentInjectionValues = new double[6];
		private double oldAReal;
		private double oldAImaginary;
		private double oldBReal;
		private double oldBImaginary;
		private double oldCReal;
		private double oldCImaginary;

		private FixedPointBus(BaseAclfBus<?, ?> bus, Complex3x1 boundaryCurrent) {
			this.id = bus.getId();
			this.sortNumber = bus.getSortNumber();
			this.bus3P = (IBus3Phase) bus;
			this.phaseLoads = this.bus3P.getPhaseLoadList();
			this.phaseGenerators = this.bus3P.getPhaseGenList();
			this.boundaryCurrent = boundaryCurrent;
			this.boundaryCurrentFinite = boundaryCurrent == null
					|| (finiteValue(boundaryCurrent.a_0) && finiteValue(boundaryCurrent.b_1)
							&& finiteValue(boundaryCurrent.c_2));
			this.boundaryAReal = realValue(boundaryCurrent == null ? null : boundaryCurrent.a_0);
			this.boundaryAImaginary = imaginaryValue(boundaryCurrent == null ? null : boundaryCurrent.a_0);
			this.boundaryBReal = realValue(boundaryCurrent == null ? null : boundaryCurrent.b_1);
			this.boundaryBImaginary = imaginaryValue(boundaryCurrent == null ? null : boundaryCurrent.b_1);
			this.boundaryCReal = realValue(boundaryCurrent == null ? null : boundaryCurrent.c_2);
			this.boundaryCImaginary = imaginaryValue(boundaryCurrent == null ? null : boundaryCurrent.c_2);
		}

		private void saveVoltageSnapshot() {
			Complex3x1 voltage = this.bus3P.get3PhaseVotlages();
			this.oldAReal = realValue(voltage == null ? null : voltage.a_0);
			this.oldAImaginary = imaginaryValue(voltage == null ? null : voltage.a_0);
			this.oldBReal = realValue(voltage == null ? null : voltage.b_1);
			this.oldBImaginary = imaginaryValue(voltage == null ? null : voltage.b_1);
			this.oldCReal = realValue(voltage == null ? null : voltage.c_2);
			this.oldCImaginary = imaginaryValue(voltage == null ? null : voltage.c_2);
		}

		private double voltageMismatch() {
			Complex3x1 voltage = this.bus3P.get3PhaseVotlages();
			if(voltage == null) {
				return 0.0;
			}
			return voltageMismatch(voltage);
		}

		private double voltageMismatch(Complex3x1 voltage) {
			if(voltage == null) {
				return 0.0;
			}
			return Math.max(
					phaseMismatch(voltage.a_0, this.oldAReal, this.oldAImaginary),
					Math.max(
							phaseMismatch(voltage.b_1, this.oldBReal, this.oldBImaginary),
							phaseMismatch(voltage.c_2, this.oldCReal, this.oldCImaginary)));
		}

		private static double phaseMismatch(Complex value, double oldReal, double oldImaginary) {
			double realDiff = realValue(value) - oldReal;
			double imaginaryDiff = imaginaryValue(value) - oldImaginary;
			return Math.hypot(realDiff, imaginaryDiff);
		}

		private static boolean finiteValue(Complex value) {
			return value == null
					|| (Double.isFinite(value.getReal()) && Double.isFinite(value.getImaginary()));
		}

		private static double realValue(Complex value) {
			return value == null ? 0.0 : value.getReal();
		}

		private static double imaginaryValue(Complex value) {
			return value == null ? 0.0 : value.getImaginary();
		}
	}

	private static class FixedPointLoopProfile {
		private static final String PROFILE_PROPERTY = "ipss.fixedpoint.profile";
		private static final boolean ENABLED = Boolean.getBoolean(PROFILE_PROPERTY);

		private final LongAdder attempts = new LongAdder();
		private final LongAdder convergedAttempts = new LongAdder();
		private final LongAdder iterations = new LongAdder();
		private final LongAdder matrixNanos = new LongAdder();
		private final LongAdder matrixSignatureNanos = new LongAdder();
		private final LongAdder matrixBusCacheNanos = new LongAdder();
		private final LongAdder matrixAssemblyNanos = new LongAdder();
		private final LongAdder matrixSparseCreateNanos = new LongAdder();
		private final LongAdder matrixBusAdmittanceNanos = new LongAdder();
		private final LongAdder matrixBranchAdmittanceNanos = new LongAdder();
		private final LongAdder matrixSparseInsertionNanos = new LongAdder();
		private final LongAdder matrixAntiFloatNanos = new LongAdder();
		private final LongAdder matrixRegulatorPaddingNanos = new LongAdder();
		private final LongAdder matrixSwingBoundaryNanos = new LongAdder();
		private final LongAdder matrixSymbolTableReuseNanos = new LongAdder();
		private final LongAdder matrixFactorizationNanos = new LongAdder();
		private final LongAdder matrixSymbolicFactorizationNanos = new LongAdder();
		private final LongAdder matrixNumericFactorizationNanos = new LongAdder();
		private final LongAdder saveVoltagesNanos = new LongAdder();
		private final LongAdder rhsClearNanos = new LongAdder();
		private final LongAdder currentInjectionNanos = new LongAdder();
		private final LongAdder currentInjectionRegulatorNanos = new LongAdder();
		private final LongAdder currentInjectionCalcNanos = new LongAdder();
		private final LongAdder currentInjectionRhsNanos = new LongAdder();
		private final LongAdder currentInjectionBuses = new LongAdder();
		private final LongAdder currentCalcInitNanos = new LongAdder();
		private final LongAdder currentCalcVoltageNanos = new LongAdder();
		private final LongAdder currentCalcLoadListNanos = new LongAdder();
		private final LongAdder currentCalcLoadCurrentNanos = new LongAdder();
		private final LongAdder currentCalcLoadAddNanos = new LongAdder();
		private final LongAdder currentCalcLoads = new LongAdder();
		private final LongAdder currentCalcGenListNanos = new LongAdder();
		private final LongAdder currentCalcGenPowerNanos = new LongAdder();
		private final LongAdder currentCalcGenCurrentNanos = new LongAdder();
		private final LongAdder currentCalcGenAddNanos = new LongAdder();
		private final LongAdder currentCalcGens = new LongAdder();
		private final LongAdder currentRhsFiniteNanos = new LongAdder();
		private final LongAdder currentRhsLookupNanos = new LongAdder();
		private final LongAdder currentRhsBoundaryFiniteNanos = new LongAdder();
		private final LongAdder currentRhsComposeNanos = new LongAdder();
		private final LongAdder currentRhsWriteNanos = new LongAdder();
		private final LongAdder swingRhsNanos = new LongAdder();
		private final LongAdder solveNanos = new LongAdder();
		private final LongAdder voltageUpdateNanos = new LongAdder();
		private final LongAdder mismatchNanos = new LongAdder();
		private final LongAdder sequenceSyncNanos = new LongAdder();
		private final LongAdder branchCurrentNanos = new LongAdder();
		private final LongAdder swingPowerNanos = new LongAdder();

		boolean enabled() {
			return ENABLED;
		}

		long start() {
			return ENABLED ? System.nanoTime() : 0L;
		}

		long elapsed(long start) {
			return ENABLED ? System.nanoTime() - start : 0L;
		}

		void addAttempt() {
			if(ENABLED) attempts.increment();
		}

		void addConvergedAttempt() {
			if(ENABLED) convergedAttempts.increment();
		}

		void addIteration() {
			if(ENABLED) iterations.increment();
		}

		void addMatrix(long nanos) {
			if(ENABLED) matrixNanos.add(nanos);
		}

		void addMatrixSignature(long nanos) {
			if(ENABLED) matrixSignatureNanos.add(nanos);
		}

		void addMatrixBusCache(long nanos) {
			if(ENABLED) matrixBusCacheNanos.add(nanos);
		}

		void addMatrixAssembly(long nanos) {
			if(ENABLED) matrixAssemblyNanos.add(nanos);
		}

		void addMatrixSparseCreate(long nanos) {
			if(ENABLED) matrixSparseCreateNanos.add(nanos);
		}

		void addMatrixBusAdmittance(long nanos) {
			if(ENABLED) matrixBusAdmittanceNanos.add(nanos);
		}

		void addMatrixBranchAdmittance(long nanos) {
			if(ENABLED) matrixBranchAdmittanceNanos.add(nanos);
		}

		void addMatrixSparseInsertion(long nanos) {
			if(ENABLED) matrixSparseInsertionNanos.add(nanos);
		}

		void addMatrixAntiFloat(long nanos) {
			if(ENABLED) matrixAntiFloatNanos.add(nanos);
		}

		void addMatrixRegulatorPadding(long nanos) {
			if(ENABLED) matrixRegulatorPaddingNanos.add(nanos);
		}

		void addMatrixSwingBoundary(long nanos) {
			if(ENABLED) matrixSwingBoundaryNanos.add(nanos);
		}

		void addMatrixSymbolTableReuse(long nanos) {
			if(ENABLED) matrixSymbolTableReuseNanos.add(nanos);
		}

		void addMatrixFactorization(long nanos) {
			if(ENABLED) matrixFactorizationNanos.add(nanos);
		}

		void addMatrixSymbolicFactorization(long nanos) {
			if(ENABLED) matrixSymbolicFactorizationNanos.add(nanos);
		}

		void addMatrixNumericFactorization(long nanos) {
			if(ENABLED) matrixNumericFactorizationNanos.add(nanos);
		}

		void addSaveVoltages(long nanos) {
			if(ENABLED) saveVoltagesNanos.add(nanos);
		}

		void addRhsClear(long nanos) {
			if(ENABLED) rhsClearNanos.add(nanos);
		}

		void addCurrentInjection(long nanos) {
			if(ENABLED) currentInjectionNanos.add(nanos);
		}

		void addCurrentInjectionRegulator(long nanos) {
			if(ENABLED) currentInjectionRegulatorNanos.add(nanos);
		}

		void addCurrentInjectionCalc(long nanos) {
			if(ENABLED) currentInjectionCalcNanos.add(nanos);
		}

		void addCurrentInjectionRhs(long nanos) {
			if(ENABLED) currentInjectionRhsNanos.add(nanos);
		}

		void addCurrentInjectionBus() {
			if(ENABLED) currentInjectionBuses.increment();
		}

		void addCurrentCalcInit(long nanos) {
			if(ENABLED) currentCalcInitNanos.add(nanos);
		}

		void addCurrentCalcVoltage(long nanos) {
			if(ENABLED) currentCalcVoltageNanos.add(nanos);
		}

		void addCurrentCalcLoadList(long nanos) {
			if(ENABLED) currentCalcLoadListNanos.add(nanos);
		}

		void addCurrentCalcLoadCurrent(long nanos) {
			if(ENABLED) currentCalcLoadCurrentNanos.add(nanos);
		}

		void addCurrentCalcLoadAdd(long nanos) {
			if(ENABLED) currentCalcLoadAddNanos.add(nanos);
		}

		void addCurrentCalcLoad() {
			if(ENABLED) currentCalcLoads.increment();
		}

		void addCurrentCalcGenList(long nanos) {
			if(ENABLED) currentCalcGenListNanos.add(nanos);
		}

		void addCurrentCalcGenPower(long nanos) {
			if(ENABLED) currentCalcGenPowerNanos.add(nanos);
		}

		void addCurrentCalcGenCurrent(long nanos) {
			if(ENABLED) currentCalcGenCurrentNanos.add(nanos);
		}

		void addCurrentCalcGenAdd(long nanos) {
			if(ENABLED) currentCalcGenAddNanos.add(nanos);
		}

		void addCurrentCalcGen() {
			if(ENABLED) currentCalcGens.increment();
		}

		void addCurrentRhsFinite(long nanos) {
			if(ENABLED) currentRhsFiniteNanos.add(nanos);
		}

		void addCurrentRhsLookup(long nanos) {
			if(ENABLED) currentRhsLookupNanos.add(nanos);
		}

		void addCurrentRhsBoundaryFinite(long nanos) {
			if(ENABLED) currentRhsBoundaryFiniteNanos.add(nanos);
		}

		void addCurrentRhsCompose(long nanos) {
			if(ENABLED) currentRhsComposeNanos.add(nanos);
		}

		void addCurrentRhsWrite(long nanos) {
			if(ENABLED) currentRhsWriteNanos.add(nanos);
		}

		void addSwingRhs(long nanos) {
			if(ENABLED) swingRhsNanos.add(nanos);
		}

		void addSolve(long nanos) {
			if(ENABLED) solveNanos.add(nanos);
		}

		void addVoltageUpdate(long nanos) {
			if(ENABLED) voltageUpdateNanos.add(nanos);
		}

		void addMismatch(long nanos) {
			if(ENABLED) mismatchNanos.add(nanos);
		}

		void addSequenceSync(long nanos) {
			if(ENABLED) sequenceSyncNanos.add(nanos);
		}

		void addBranchCurrent(long nanos) {
			if(ENABLED) branchCurrentNanos.add(nanos);
		}

		void addSwingPower(long nanos) {
			if(ENABLED) swingPowerNanos.add(nanos);
		}

		void printSummary() {
			if(ENABLED) {
				System.out.println(summary());
			}
		}

		private String summary() {
			long iterationCount = Math.max(1L, iterations.sum());
			long injectionBusCount = Math.max(1L, currentInjectionBuses.sum());
			long loadCount = Math.max(1L, currentCalcLoads.sum());
			long genCount = Math.max(1L, currentCalcGens.sum());
			return "\nFixed-point PF profile"
					+ "\n  attempts=" + attempts.sum()
					+ ", converged_attempts=" + convergedAttempts.sum()
					+ ", iterations=" + iterations.sum()
					+ "\n  matrix_ms=" + ms(matrixNanos)
					+ "\n  matrix_breakdown_ms signature=" + ms(matrixSignatureNanos)
					+ ", bus_cache=" + ms(matrixBusCacheNanos)
					+ ", assembly=" + ms(matrixAssemblyNanos)
					+ ", regulator_padding=" + ms(matrixRegulatorPaddingNanos)
					+ ", swing_boundary=" + ms(matrixSwingBoundaryNanos)
					+ ", symbol_reuse=" + ms(matrixSymbolTableReuseNanos)
					+ ", factorization_call=" + ms(matrixFactorizationNanos)
					+ "\n  matrix_assembly_deep_ms sparse_create=" + ms(matrixSparseCreateNanos)
					+ ", bus_admittance=" + ms(matrixBusAdmittanceNanos)
					+ ", branch_admittance=" + ms(matrixBranchAdmittanceNanos)
					+ ", sparse_insertion=" + ms(matrixSparseInsertionNanos)
					+ ", anti_float=" + ms(matrixAntiFloatNanos)
					+ "\n  matrix_factorization_call_ms symbolic_or_new_pattern=" + ms(matrixSymbolicFactorizationNanos)
					+ ", numeric_reuse_pattern=" + ms(matrixNumericFactorizationNanos)
					+ "\n  save_voltages_ms=" + ms(saveVoltagesNanos)
					+ ", rhs_clear_ms=" + ms(rhsClearNanos)
					+ ", current_injection_ms=" + ms(currentInjectionNanos)
					+ ", swing_rhs_ms=" + ms(swingRhsNanos)
					+ "\n  current_injection_breakdown_ms regulator=" + ms(currentInjectionRegulatorNanos)
					+ ", calc=" + ms(currentInjectionCalcNanos)
					+ ", rhs=" + ms(currentInjectionRhsNanos)
					+ ", buses=" + currentInjectionBuses.sum()
					+ "\n  current_calc_deep_ms init=" + ms(currentCalcInitNanos)
					+ ", voltage=" + ms(currentCalcVoltageNanos)
					+ ", load_list=" + ms(currentCalcLoadListNanos)
					+ ", load_current=" + ms(currentCalcLoadCurrentNanos)
					+ ", load_add=" + ms(currentCalcLoadAddNanos)
					+ ", loads=" + currentCalcLoads.sum()
					+ ", gen_list=" + ms(currentCalcGenListNanos)
					+ ", gen_power=" + ms(currentCalcGenPowerNanos)
					+ ", gen_current=" + ms(currentCalcGenCurrentNanos)
					+ ", gen_add=" + ms(currentCalcGenAddNanos)
					+ ", gens=" + currentCalcGens.sum()
					+ "\n  current_rhs_deep_ms finite=" + ms(currentRhsFiniteNanos)
					+ ", lookup=" + ms(currentRhsLookupNanos)
					+ ", boundary_finite=" + ms(currentRhsBoundaryFiniteNanos)
					+ ", compose=" + ms(currentRhsComposeNanos)
					+ ", write=" + ms(currentRhsWriteNanos)
					+ "\n  solve_ms=" + ms(solveNanos)
					+ ", voltage_update_ms=" + ms(voltageUpdateNanos)
					+ ", mismatch_ms=" + ms(mismatchNanos)
					+ "\n  sequence_sync_ms=" + ms(sequenceSyncNanos)
					+ ", branch_current_ms=" + ms(branchCurrentNanos)
					+ ", swing_power_ms=" + ms(swingPowerNanos)
					+ "\n  per_iteration_ms save_voltages=" + msPerIteration(saveVoltagesNanos, iterationCount)
					+ ", rhs_clear=" + msPerIteration(rhsClearNanos, iterationCount)
					+ ", current_injection=" + msPerIteration(currentInjectionNanos, iterationCount)
					+ ", swing_rhs=" + msPerIteration(swingRhsNanos, iterationCount)
					+ ", solve=" + msPerIteration(solveNanos, iterationCount)
					+ ", voltage_update=" + msPerIteration(voltageUpdateNanos, iterationCount)
					+ ", mismatch=" + msPerIteration(mismatchNanos, iterationCount)
					+ "\n  current_injection_per_iteration_ms regulator="
					+ msPerIteration(currentInjectionRegulatorNanos, iterationCount)
					+ ", calc=" + msPerIteration(currentInjectionCalcNanos, iterationCount)
					+ ", rhs=" + msPerIteration(currentInjectionRhsNanos, iterationCount)
					+ "\n  current_calc_deep_per_iteration_ms init="
					+ msPerIteration(currentCalcInitNanos, iterationCount)
					+ ", voltage=" + msPerIteration(currentCalcVoltageNanos, iterationCount)
					+ ", load_list=" + msPerIteration(currentCalcLoadListNanos, iterationCount)
					+ ", load_current=" + msPerIteration(currentCalcLoadCurrentNanos, iterationCount)
					+ ", load_add=" + msPerIteration(currentCalcLoadAddNanos, iterationCount)
					+ ", gen_list=" + msPerIteration(currentCalcGenListNanos, iterationCount)
					+ ", gen_power=" + msPerIteration(currentCalcGenPowerNanos, iterationCount)
					+ ", gen_current=" + msPerIteration(currentCalcGenCurrentNanos, iterationCount)
					+ ", gen_add=" + msPerIteration(currentCalcGenAddNanos, iterationCount)
					+ "\n  current_rhs_deep_per_iteration_ms finite="
					+ msPerIteration(currentRhsFiniteNanos, iterationCount)
					+ ", lookup=" + msPerIteration(currentRhsLookupNanos, iterationCount)
					+ ", boundary_finite=" + msPerIteration(currentRhsBoundaryFiniteNanos, iterationCount)
					+ ", compose=" + msPerIteration(currentRhsComposeNanos, iterationCount)
					+ ", write=" + msPerIteration(currentRhsWriteNanos, iterationCount)
					+ "\n  current_injection_per_bus_us calc="
					+ usPerBus(currentInjectionCalcNanos, injectionBusCount)
					+ ", rhs=" + usPerBus(currentInjectionRhsNanos, injectionBusCount)
					+ "\n  current_calc_per_device_us load_current="
					+ usPerBus(currentCalcLoadCurrentNanos, loadCount)
					+ ", load_add=" + usPerBus(currentCalcLoadAddNanos, loadCount)
					+ ", gen_power=" + usPerBus(currentCalcGenPowerNanos, genCount)
					+ ", gen_current=" + usPerBus(currentCalcGenCurrentNanos, genCount)
					+ ", gen_add=" + usPerBus(currentCalcGenAddNanos, genCount);
		}

		private String ms(LongAdder nanos) {
			return String.format(Locale.US, "%.3f", nanos.sum() / 1_000_000.0);
		}

		private String msPerIteration(LongAdder nanos, long iterationCount) {
			return String.format(Locale.US, "%.6f", nanos.sum() / 1_000_000.0 / iterationCount);
		}

		private String usPerBus(LongAdder nanos, long busCount) {
			return String.format(Locale.US, "%.6f", nanos.sum() / 1_000.0 / busCount);
		}
	}

	private class RegulatorBranchAdmittance {
		private final IBranch3Phase branch;
		private final int fromSortNumber;
		private final int toSortNumber;
		private final int phaseMask;
		private final Complex3x3 yff;
		private final Complex3x3 yft;
		private final Complex3x3 ytf;
		private final Complex3x3 ytt;

		private RegulatorBranchAdmittance(IBranch3Phase branch, int fromSortNumber, int toSortNumber) {
			this(branch, fromSortNumber, toSortNumber, false);
		}

		private RegulatorBranchAdmittance(IBranch3Phase branch, int fromSortNumber, int toSortNumber,
				boolean padded) {
			this.branch = branch;
			this.fromSortNumber = fromSortNumber;
			this.toSortNumber = toSortNumber;
			this.phaseMask = branchPhaseMask(branch);
			this.yff = padded ? paddedRegulatorAdmittance(branch, branch.getYffabc())
					: copyComplex3x3(branch.getYffabc());
			this.yft = padded ? paddedRegulatorAdmittance(branch, branch.getYftabc())
					: copyComplex3x3(branch.getYftabc());
			this.ytf = padded ? paddedRegulatorAdmittance(branch, branch.getYtfabc())
					: copyComplex3x3(branch.getYtfabc());
			this.ytt = padded ? paddedRegulatorAdmittance(branch, branch.getYttabc())
					: copyComplex3x3(branch.getYttabc());
		}
	}

}
