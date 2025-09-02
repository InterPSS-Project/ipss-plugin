package org.interpss.multiNet.algo;

import static com.interpss.dstab.funcImpl.DStabFunction.BuiltBusState;
import static com.interpss.dstab.funcImpl.DStabFunction.BuiltMachineState;
import static com.interpss.dstab.funcImpl.DStabFunction.BuiltScriptDynamicBusDeviceState;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.math3.complex.Complex;
import org.interpss.multiNet.equivalent.NetworkEquivalent;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.model.DynLoadModel1Phase;
import org.interpss.threePhase.dynamic.model.DynLoadModel3Phase;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.algo.sc.ScBusModelType;
import com.interpss.core.net.Branch;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.datatype.DStabSimuEvent;
import com.interpss.dstab.mach.Machine;

public class TposseqD3phaseMultiNetDStabSolverImpl extends MultiNetDStabSolverImpl {
    private static final Logger log = LoggerFactory.getLogger(TposseqD3phaseMultiNetDStabSolverImpl.class);
	
	
	private List<String>  threePhaseSubNetIdList = null;
	private Hashtable<String,Complex3x1> dist2Trans3PhaseCurInjTable =null;
	private Hashtable<String,Complex> dist2TransEquivCurInjTable =null;
	private Hashtable<String,Complex> dist2TransTotalPowerTable =null;
	private  BaseDStabNetwork<?,?> transmissionNet = null;
	protected List< BaseDStabNetwork<?,?> > distNetList = null;
	private SubNetworkProcessor subNetProcessor = null;
	private Hashtable<String,Complex> lastStepTransBoundaryVoltTable;
	
	private Hashtable<String,NetworkEquivalent> netEquivTable = null;
	
	private Hashtable<String,Complex3x1> distNetNortonEquivCurrentTable = null;
	
	private boolean isTheveninEquiv = true;
	private boolean isDistNetSolvedByPowerflow = true;
	
	private final static Complex voltSourceImpedance = new Complex(0,0.00001);
	
	//threshold controlling when constant power loads will be converted to constant impedance loads
	private double loadModelVminpu = 0.85;

	public TposseqD3phaseMultiNetDStabSolverImpl(DynamicSimuAlgorithm algo, AbstractMultiNetDStabSimuHelper mNetSimuHelper) {
		super(algo, mNetSimuHelper);
		
		this.threePhaseSubNetIdList = this.multiNetSimuHelper.getSubNetworkProcessor().getThreePhaseSubNetIdList();
		
		this.subNetProcessor = mNetSimuHelper.getSubNetworkProcessor();
		
		this.transmissionNet = (BaseDStabNetwork<?, ?>) this.subNetProcessor.getExternalSubNetwork();
		
		try {
			if(transmissionNet ==null){
				
					throw new Exception ("Error: Transmission network object is null! Error in the SubNetworkProcessor");
				
			}
			// check if the transmission network is in the threePhaseNetList
			else if(threePhaseSubNetIdList.contains(transmissionNet.getId())){
				throw new Exception ("Error: Transmission network object should not be in the threePhaseNetIdList");
			}
			
			if (threePhaseSubNetIdList.size() != (this.subNetList.size() -1)){
				throw new Exception ("Error: threePhaseNetIdList size should be equal to this.subNetList.size() -1");
			}
			
			
		
		} catch (Exception e) {
			
			e.printStackTrace();
		}
		
		// populate the distribution system list
		distNetList = new ArrayList<>();
		
		for(BaseDStabNetwork<?, ?> net: this.subNetList){
			if(threePhaseSubNetIdList.contains(net.getId()))
			    this.distNetList.add(net);
		}
		
		dist2TransTotalPowerTable  = new Hashtable<>();
		dist2TransEquivCurInjTable = new Hashtable<>();
		this.dist2Trans3PhaseCurInjTable = new Hashtable<>();
		
		netEquivTable = new Hashtable<>();
		
		distNetNortonEquivCurrentTable = new Hashtable<>();
		
		lastStepTransBoundaryVoltTable = new Hashtable<>();
	}
	
	public void setTheveninEquivFlag(boolean useTheveninEquiv){
		this.isTheveninEquiv = useTheveninEquiv;
	}
	
	public void setDistNetSolvedByPowerflowFlag(boolean solveDistNetByPowerflow){
		this.isDistNetSolvedByPowerflow = solveDistNetByPowerflow;
	}
	
	@Override 
	public boolean initialization() {
		this.simuPercent = 0;
		this.simuTime = 0; //required for running multiple DStab simulations in a loop
		consoleMsg("Start T&D multi-SubNetwork initialization ...");

		if (!dstabAlgo.getNetwork().isLfConverged()) {
			log.error("Error: Loadflow not converged yet!");
			return false;
		}
		
		
		// network  initialization: mainly includes load model conversion and dynamic model initialization
		for(BaseDStabNetwork<?, ?> dsNet: this.subNetList){
			   boolean flag = true;
			   if(this.threePhaseSubNetIdList!=null && this.threePhaseSubNetIdList.contains(dsNet.getId())){    
				   flag = dsNet.initDStabNet();
			   }
			   else{
				   DStabNetwork3Phase dsNet3Phase = (DStabNetwork3Phase) dsNet;
				   flag = dsNet3Phase.initPosSeqDStabNet();
				
			   }
			   if (!flag){
					  log.error("Error: SubNetwork initialization error:"+dsNet.getId());
					  return false;
				}
			
		}
				
		// calculate the Thevenin equivalent impedances of the transmission subsystem for each distribution system
		
		if(this.isTheveninEquiv){
		    calculateTransmissionTheveninEquiv();
		    
		    
		    if(isDistNetSolvedByPowerflow){
		    	//TODO
		    	throw new UnsupportedOperationException();
		    } else
		        addTransNetTheveninEquivToDistYABCMatrix(false);
		}
		else{
			 if(!isDistNetSolvedByPowerflow){
				 // just to create the netEquivTable, not used it for solution
				 calculateTransmissionTheveninEquiv();
				 // use voltage source in the dist system dynamic simulation
				 addTransNetTheveninEquivToDistYABCMatrix(true);
			 }
		}
		
		// prepare the current injection table for the transmission system
		// step-1 calculate the total power transferred from the transmission system into each distribution system
		
		this.calculateDist2TransTotalPower();
		
		
		// step-2 convert the total power into current injections, and inject their responding negative values into the transmission system using net.setCustomBusCurrInjHashtable() method
						
	    this.calculateDist2TransCurInjection();
		
		// output initial states
		if (!procInitOutputEvent())
			return false;

	  	//System.out.println(net.net2String());
		consoleMsg("Initialization finished");
		return true;		
	}
	
	
	


	@Override public void beforeStep(double time)  throws DStabSimuException {
		// Process dynamic events
		if (!dstabAlgo.isDisableDynamicEvent()) {
			applyDynamicEvent(time);
		}
		
		// whenever there is an event, need to update the [Zl] matrix of the boundary subsystem
		if( hasDynEvent(time)){
			
			
			if(this.isTheveninEquiv){
				calculateTransmissionTheveninEquiv();
			   //step-2 update the distribution systems Y matrix with their transmission system Thevenin equivalents
			    if(this.isDistNetSolvedByPowerflow){
			    	//TODO
			    	//updateDistNetTheveninEquivImpedance();
			    }
			    else{
			    	
			    	addTransNetTheveninEquivToDistYABCMatrix(false);
			    }
			}
			else{ // voltage source
				if(!this.isDistNetSolvedByPowerflow){
					
				}
			}
			
		}
	}
   
	@Override
	public boolean networkSolutionStep() throws DStabSimuException {
		 
		boolean netSolConverged = true;
		
		
		//If use Thevenin Equivalent to represent Transmission system in distribution system
		// first update the network equivalent using the last step solution results
		// Otherwise the following two steps can be skipped
//		if(this.isTheveninEquiv){
//			// step-1 calculate the transmission system Thevenin equivalent voltage (the impedance part is already calculated during initialization() or the beforeStep())
//			updateTransNetTheveninEquivSource();
//			
//			// step-2 update the distribution systems current injection table
//			for(DStabilityNetwork dsNet: this.distNetList){
//				
//				DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
//				
//				// get source bus Id
//				List<String> boundaryList = subNetProcessor.getSubNet2BoundaryBusListTable().get(dsNet.getId());
//				String sourceId = boundaryList.get(0);
//				
//			    // update the Norton equivalent current injection at the boundary
//				
//				dsNet3Ph.get3phaseCustomCurrInjTable().put(sourceId, this.distNetNortonEquivCurrentTable.get(sourceId));
//				
//				
//			}
//		}
//		
		//TODO for testing only
		// maxIterationTimes = 1;
		for(int i=0;i<maxIterationTimes;i++){ 
			
		
			
			if(this.isTheveninEquiv){
				// step-1 calculate the transmission system Thevenin equivalent voltage (the impedance part is already calculated during initialization() or the beforeStep())
				updateTransNetTheveninEquivSource(false);
				
				// step-2 update the distribution systems current injection table
				for(BaseDStabNetwork<?, ?> dsNet: this.distNetList){
					
					DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
					
					// get source bus Id
					List<String> boundaryList = subNetProcessor.getSubNet2BoundaryBusListTable().get(dsNet.getId());
					String sourceId = boundaryList.get(0);
					
				    // update the Norton equivalent current injection at the boundary
					
					dsNet3Ph.get3phaseCustomCurrInjTable().put(sourceId, this.distNetNortonEquivCurrentTable.get(sourceId));
					
					
				}
			}
			else{ // use voltage source
				if(!this.isDistNetSolvedByPowerflow){ // for dynamic simulation only
					
				
					// step-1 calculate the transmission system Thevenin equivalent voltage (the impedance part is already calculated during initialization() or the beforeStep())
					updateTransNetTheveninEquivSource(true);
					
					// step-2 update the distribution systems current injection table
					for(BaseDStabNetwork<?, ?> dsNet: this.distNetList){
						
						DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
						
						// get source bus Id
						List<String> boundaryList = subNetProcessor.getSubNet2BoundaryBusListTable().get(dsNet.getId());
						String sourceId = boundaryList.get(0);
						
					    // update the Norton equivalent current injection at the boundary
						
						dsNet3Ph.get3phaseCustomCurrInjTable().put(sourceId, this.distNetNortonEquivCurrentTable.get(sourceId));
			       }
				}
			}
					
				
			// step-3 solve the distribution systems
			
			
			for(BaseDStabNetwork<?, ?> dsNet: this.distNetList){
				
				DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
				
				if(this.isTheveninEquiv){
					
					// two simulation options here: 1) run power flow; 2) run dynamic simulation
					
					if(this.isDistNetSolvedByPowerflow)
						throw new UnsupportedOperationException();
					
					else // run dynamic simulation 
				       dsNet3Ph.solveNetEqn();
					
				} 
				// When T->D passing the boundary bus voltages, only power flow for distribution systems will be considered
				else{ 
					
					// use voltage source as equivalent, which means fixing the distribution source voltage in this step
					
					if(this.isDistNetSolvedByPowerflow){
						
						List<String> boundaryList = subNetProcessor.getSubNet2BoundaryBusListTable().get(dsNet3Ph.getId());
						String sourceId = boundaryList.get(0);
						
						String  transBoundaryBusId = "";
						
						if(sourceId.contains("Dummy")){
							   transBoundaryBusId = sourceId.replace("Dummy", "");
						   }
						   else
							   transBoundaryBusId = sourceId+"Dummy";
						
						// get the transmission bus voltage
						Complex volt = this.transmissionNet.getBus(transBoundaryBusId).getVoltage();
						
						Complex3x1 vabc = new Complex3x1();
						vabc.b_1 = volt;
						
						vabc = vabc.toABC();
						
						if(vabc ==null)
							throw new Error("dist net, source bus volt is null: "+dsNet.getId());
						
						// System.out.println("dist net, source bus volt: "+dsNet.getId()+","+ vabc.toString());
						
						// update the distribution source bus voltage
						DStab3PBus sourceBus3Ph = (DStab3PBus)dsNet3Ph.getBus(sourceId);
						
						sourceBus3Ph.set3PhaseVotlages(vabc);
						sourceBus3Ph.setVoltage(volt);
						
					
						
						DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(dsNet);
	
						Boolean solFlag = distPFAlgo.powerflow();
						
						if(!solFlag){
							throw new DStabSimuException("Error occured, distribution power flow diverged! Distnet Id:"+dsNet.getId());
						}
					} 
					// dynamic simulation with voltage source as equivalent
					else{
						dsNet3Ph.solveNetEqn();
					}
					
				}
			
				
			}
			 
			 // step-4 calculate the total power transferred from the transmission system into each distribution system
			this.calculateDist2TransTotalPower();
			
			// step-5 convert the total power into current injections, and inject their responding negative values into the transmission system using net.setCustomBusCurrInjHashtable() method
			this.calculateDist2TransCurInjection();	
			
			// step-6 solve the transmission network network solution
			this.transmissionNet.setCustomBusCurrInjHashtable(this.dist2TransEquivCurInjTable);
			
			if(transmissionNet instanceof DStabNetwork3Phase)
				((DStabNetwork3Phase)this.transmissionNet).solvePosSeqNetEqn();
			else
				this.transmissionNet.solveNetEqn();
	       
			
			// step-7 check if the transmission network solution results of the last two steps converge wrt the tolerance
			// if so, exit the loop; otherwise, continue the iteration until the maximum iteration
			for ( BaseDStabBus busi : transmissionNet.getBusList() ) {
				
				if(busi.isActive()){
					
					if(i>=1){
						if(!NumericUtil.equals(busi.getVoltage(),lastStepTransBoundaryVoltTable.get(busi.getId()),this.converge_tol))
							netSolConverged =false;
					}
					lastStepTransBoundaryVoltTable.put(busi.getId(), busi.getVoltage());
				}
			 }
			
			
			
		     if(i>0 && netSolConverged) {
				  log.debug(getSimuTime()+","+"multi subNetwork solution in the nextStep() is converged, iteration #"+(i+1));
				  break;
			 }
	
		  } // for maxIterationTimes loop
			
		return netSolConverged;
	}
	
	public void diffEqnIntegrationStep(double t, double dt, DynamicSimuMethod method, int flag) throws DStabSimuException{

	 super.diffEqnIntegrationStep(t, dt, method, flag);
	  
	  //TODO update the distribution bus equivalent loads based on the dynamic load power, 
	  //if the source bus voltage is used for T&D interfacing
	  for(BaseDStabNetwork<?, ?> dsNet: this.distNetList){
			
			DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
			
			if(!this.isTheveninEquiv){
				updateDistNetBusLoads(dsNet3Ph,loadModelVminpu);
			}
	  }
	  
		
	}
	
//
//	@Override 
//	public void nextStep(double time, double dt, DynamicSimuMethod method)  throws DStabSimuException {
//		 
//		boolean netSolConverged = true;
//		
//		
//		//If use Thevenin Equivalent to represent Transmission system in distribution system
//		// first update the network equivalent using the last step solution results
//		// Otherwise the following two steps can be skipped
////		if(this.isTheveninEquiv){
////			// step-1 calculate the transmission system Thevenin equivalent voltage (the impedance part is already calculated during initialization() or the beforeStep())
////			updateTransNetTheveninEquivSource();
////			
////			// step-2 update the distribution systems current injection table
////			for(DStabilityNetwork dsNet: this.distNetList){
////				
////				DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
////				
////				// get source bus Id
////				List<String> boundaryList = subNetProcessor.getSubNet2BoundaryBusListTable().get(dsNet.getId());
////				String sourceId = boundaryList.get(0);
////				
////			    // update the Norton equivalent current injection at the boundary
////				
////				dsNet3Ph.get3phaseCustomCurrInjTable().put(sourceId, this.distNetNortonEquivCurrentTable.get(sourceId));
////				
////				
////			}
////		}
////		
//		//TODO for testing only
//		// maxIterationTimes = 1;
//		for(int i=0;i<maxIterationTimes;i++){ 
//			
//		
//			
//			if(this.isTheveninEquiv){
//				// step-1 calculate the transmission system Thevenin equivalent voltage (the impedance part is already calculated during initialization() or the beforeStep())
//				updateTransNetTheveninEquivSource(false);
//				
//				// step-2 update the distribution systems current injection table
//				for(BaseDStabNetwork<?, ?> dsNet: this.distNetList){
//					
//					DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
//					
//					// get source bus Id
//					List<String> boundaryList = subNetProcessor.getSubNet2BoundaryBusListTable().get(dsNet.getId());
//					String sourceId = boundaryList.get(0);
//					
//				    // update the Norton equivalent current injection at the boundary
//					
//					dsNet3Ph.get3phaseCustomCurrInjTable().put(sourceId, this.distNetNortonEquivCurrentTable.get(sourceId));
//					
//					
//				}
//			}
//			else{ // use voltage source
//				if(!this.isDistNetSolvedByPowerflow){ // for dynamic simulation only
//					
//				
//					// step-1 calculate the transmission system Thevenin equivalent voltage (the impedance part is already calculated during initialization() or the beforeStep())
//					updateTransNetTheveninEquivSource(true);
//					
//					// step-2 update the distribution systems current injection table
//					for(BaseDStabNetwork<?, ?> dsNet: this.distNetList){
//						
//						DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
//						
//						// get source bus Id
//						List<String> boundaryList = subNetProcessor.getSubNet2BoundaryBusListTable().get(dsNet.getId());
//						String sourceId = boundaryList.get(0);
//						
//					    // update the Norton equivalent current injection at the boundary
//						
//						dsNet3Ph.get3phaseCustomCurrInjTable().put(sourceId, this.distNetNortonEquivCurrentTable.get(sourceId));
//			       }
//				}
//			}
//					
//				
//			// step-3 solve the distribution systems
//			
//			
//			for(BaseDStabNetwork<?, ?> dsNet: this.distNetList){
//				
//				DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
//				
//				if(this.isTheveninEquiv){
//					
//					// two simulation options here: 1) run power flow; 2) run dynamic simulation
//					
//					if(this.isDistNetSolvedByPowerflow)
//						throw new UnsupportedOperationException();
//					
//					else // run dynamic simulation 
//				       dsNet3Ph.solveNetEqn();
//					
//				} 
//				// When T->D passing the boundary bus voltages, only power flow for distribution systems will be considered
//				else{ 
//					
//					// use voltage source as equivalent, which means fixing the distribution source voltage in this step
//					
//					if(this.isDistNetSolvedByPowerflow){
//						
//						List<String> boundaryList = subNetProcessor.getSubNet2BoundaryBusListTable().get(dsNet3Ph.getId());
//						String sourceId = boundaryList.get(0);
//						
//						String  transBoundaryBusId = "";
//						
//						if(sourceId.contains("Dummy")){
//							   transBoundaryBusId = sourceId.replace("Dummy", "");
//						   }
//						   else
//							   transBoundaryBusId = sourceId+"Dummy";
//						
//						// get the transmission bus voltage
//						Complex volt = this.transmissionNet.getBus(transBoundaryBusId).getVoltage();
//						
//						Complex3x1 vabc = new Complex3x1();
//						vabc.b_1 = volt;
//						
//						vabc = vabc.toABC();
//						
//						if(vabc ==null)
//							throw new Error("dist net, source bus volt is null: "+dsNet.getId());
//						
//						// System.out.println("dist net, source bus volt: "+dsNet.getId()+","+ vabc.toString());
//						
//						// update the distribution source bus voltage
//						DStab3PBus sourceBus3Ph = (DStab3PBus)dsNet3Ph.getBus(sourceId);
//						
//						sourceBus3Ph.set3PhaseVotlages(vabc);
//						sourceBus3Ph.setVoltage(volt);
//						
//					
//						
//						DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(dsNet);
//	
//						Boolean solFlag = distPFAlgo.powerflow();
//						
//						if(!solFlag){
//							throw new DStabSimuException("Error occured, distribution power flow diverged! Distnet Id:"+dsNet.getId());
//						}
//					} 
//					// dynamic simulation with voltage source as equivalent
//					else{
//						dsNet3Ph.solveNetEqn();
//					}
//					
//				}
//			
//				
//			}
//			 
//			 // step-4 calculate the total power transferred from the transmission system into each distribution system
//			this.calculateDist2TransTotalPower();
//			
//			// step-5 convert the total power into current injections, and inject their responding negative values into the transmission system using net.setCustomBusCurrInjHashtable() method
//			this.calculateDist2TransCurInjection();	
//			
//			// step-6 solve the transmission network network solution
//			this.transmissionNet.setCustomBusCurrInjHashtable(this.dist2TransEquivCurInjTable);
//			
//			if(transmissionNet instanceof DStabNetwork3Phase)
//				((DStabNetwork3Phase)this.transmissionNet).solvePosSeqNetEqn();
//			else
//				this.transmissionNet.solveNetEqn();
//	       
//			
//			// step-7 check if the transmission network solution results of the last two steps converge wrt the tolerance
//			// if so, exit the loop; otherwise, continue the iteration until the maximum iteration
//			for ( BaseDStabBus busi : transmissionNet.getBusList() ) {
//				
//				if(busi.isActive()){
//					
//					if(i>=1){
//						if(!NumericUtil.equals(busi.getVoltage(),lastStepTransBoundaryVoltTable.get(busi.getId()),this.converge_tol))
//							netSolConverged =false;
//					}
//					lastStepTransBoundaryVoltTable.put(busi.getId(), busi.getVoltage());
//				}
//			 }
//			
//			
//			
//		     if(i>0 && netSolConverged) {
//				  IpssLogger.getLogger().fine(getSimuTime()+","+"multi subNetwork solution in the nextStep() is converged, iteration #"+(i+1));
//				  break;
//			 }
//	
//		  } // for maxIterationTimes loop
//			
//			
//			
//			 /*
//			  * Third step : with the network solved, the bus voltage and current injections are determined, it is time to solve the dynamic devices using 
//			  * integration methods
//			  *  x(t+deltaT) = x(t) + dx_dt*deltaT 
//			  */
//			  
//		  for(BaseDStabNetwork<?, ?> dsNet: subNetList){  
//			// Solve DEqn for all dynamic bus devices
//				for (Bus b : dsNet.getBusList()) {
//					if(b.isActive()){
//						DStabBus bus = (DStabBus)b;
//						for (DynamicBusDevice device : bus.getDynamicBusDeviceList()) {
//							// solve DEqn for the step. This includes all controller's nextStep() call
//							if(device.isActive()){
//								if (!device.nextStep(dt, method)) {
//									throw new DStabSimuException("Error occured, Simulation will be stopped");
//								}
//							}
//						}
//						
//						// Solve DEqn for generator 
//						if(bus.getContributeGenList().size()>0){
//							for(AclfGen gen:bus.getContributeGenList()){
//								if(gen.isActive()){
//									Machine mach = ((DStabGen)gen).getMach();
//									if(mach!=null && mach.isActive()){
//									   if (!mach.nextStep(dt, method)) {
//										  throw new DStabSimuException("Error occured when solving nextStep for mach #"+ mach.getId()+ "@ bus - "
//									                   +bus.getId()+", Simulation will be stopped!");
//									   }
//									}
//								}
//							}
//						}
//						
//						//TODO Solve DEqn for dynamic load, e.g. induction motor
//					}
//				}// bus-loop
//
//				// Solve DEqn for all dynamic branch devices
//				for (Branch b : dsNet.getBranchList()) {
//					DStabBranch branch = (DStabBranch)b;
//					for (DynamicBranchDevice device : branch.getDynamicBranchDeviceList()) {
//						// solve DEqn for the step. This includes all controller's nextStep() call
//						if (!device.nextStep(dt, method)) {
//							throw new DStabSimuException("Error occured, Simulation will be stopped");
//						}
//					}
//				} 
//			  
//				
//			  // The network solution and integration steps ends here, the following is mainly to record or update some intermediate variables
//			  
//			  
//			  // update the dynamic attributes and calculate the bus frequency
//		     //  for(DStabilityNetwork dsNet: subNetList){
//			
//				for ( Bus busi : dsNet.getBusList() ) {
//					DStabBus bus = (DStabBus)busi;
//					if(bus.isActive()){
//						
//						// update dynamic attributes of the dynamic devices connected to the bus
//						 try {
//							bus.updateDynamicAttributes(false);
//						} catch (InterpssException e) {
//						
//							e.printStackTrace();
//						}
//						 // calculate bus frequency
//						 if (!bus.nextStep(dt, method)) {
//								throw new DStabSimuException("Error occured, Simulation will be stopped");
//							}
//					}
//				}
//		    	
//		 } // for subNetwork loop
//		  
//		  
//		  //TODO update the distribution bus equivalent loads based on the dynamic load power, 
//		  //if the source bus voltage is used for T&D interfacing
//		  for(BaseDStabNetwork<?, ?> dsNet: this.distNetList){
//				
//				DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
//				
//				if(!this.isTheveninEquiv){
//					updateDistNetBusLoads(dsNet3Ph,loadModelVminpu);
//				}
//		  }
//		  
//			
//			
//		// back up the states	
//			for(BaseDStabNetwork<?, ?> dsNet: subNetList){
//			 // backup the states
//			 for (Bus b :  dsNet.getBusList()) {
//					if(b.isActive()){
//						DStabBus bus = (DStabBus)b;
//						// Solve DEqn for generator 
//						if(bus.getContributeGenList().size()>0){
//							for(AclfGen gen:bus.getContributeGenList()){
//								if(gen.isActive()){
//									Machine mach = ((DStabGen)gen).getMach();
//									if(mach!=null && mach.isActive()){
//									  mach.backUpStates();
//									}
//								}
//							}
//						}
//						
//					}
//			  }
//		}
//	
//	}
	
	@Override public boolean procInitOutputEvent() {
		try {
			for(BaseDStabNetwork<?, ?> dsNet: subNetList){
				
				// Solve DEqn for all dynamic bus devices
				for (BaseDStabBus<? extends DStabGen, ? extends DStabLoad> bus : dsNet.getBusList()) {

					if(bus.isActive()){ // only the active bus will be consider, otherwise errors will occur
						if (bus.isMachineBus()) {
							for(AclfGen gen: bus.getContributeGenList()){
							//consider active or in-service machine only
							 if(gen.isActive() && ((DStabGen)gen).getMach()!=null){
								  Machine mach = ((DStabGen)gen).getMach();
								  Hashtable<String, Object> states = BuiltMachineState.f(mach, 0.0, this.dstabAlgo);
								  procOutputEvent(DStabSimuEvent.PlotStepMachineStates, states);
								  procOutputEvent(DStabSimuEvent.TimeStepMachineStates, states);
							 }
						  }
						}
						if (bus.getScriptDynamicBusDevice() != null) {
				            Hashtable<String, Object> states = BuiltScriptDynamicBusDeviceState.f(bus, 0.0, this.dstabAlgo);
				            procOutputEvent(DStabSimuEvent.TimeStepScriptDynamicBusDeviceStates, states);
							procOutputEvent(DStabSimuEvent.PlotStepScriptDynamicBusDeviceStates, states);
						}
						//else {
				            Hashtable<String, Object> states = BuiltBusState.f(bus, 0.0, this.dstabAlgo);
				            procOutputEvent(DStabSimuEvent.TimeStepBusStates, states);
							procOutputEvent(DStabSimuEvent.PlotStepBusStates, states);
						//}
					 }
			}
		  }
			procOutputEvent(DStabSimuEvent.EndOfSimuStep, null);
			return true;
		} catch (InterpssException e) {
			log.error(e.toString());
			return false;
		}
	}
	
	private void calculateTransmissionTheveninEquiv() {
	
		List<String> boundaryList = this.subNetProcessor.getSubNet2BoundaryBusListTable().get(this.transmissionNet.getId());
		
		for(String busId:boundaryList){
			NetworkEquivalent equiv = calTransmissionNetworkPosSeqTheveninEquiv(busId);
			netEquivTable.put(busId, equiv);
		}
		
		
	}
	
	
	private NetworkEquivalent calTransmissionNetworkPosSeqTheveninEquiv(String boundaryId){
		
		ISparseEqnComplex ymatrix = this.transmissionNet.getYMatrix();
		if(ymatrix==null){
			ymatrix = this.transmissionNet.formScYMatrix(SequenceCode.POSITIVE, ScBusModelType.LOADFLOW_VOLT, false);
			this.transmissionNet.setYMatrix(ymatrix);
			this.transmissionNet.setYMatrixDirty(true);
		}
		if(this.transmissionNet.isYMatrixDirty()){
			try {
				ymatrix.factorization(1.0E-10);
			} catch (IpssNumericException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// solve YV=I with only unit current injection at one boundary bus
		int dim = 1;
		
		NetworkEquivalent netEquiv = null;
		
		BaseDStabBus bus =this.transmissionNet.getBus(boundaryId);
		
		if(bus!=null){
			netEquiv = new NetworkEquivalent(dim);

			ymatrix.setB2Unity(bus.getSortNumber());
			try {
				ymatrix.solveEqn();
			} catch (IpssNumericException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			Complex zji= ymatrix.getX(bus.getSortNumber());
			
			//this is to fix a bug when zji = 0, which will cause issue when calculating the North current injection volt/zth, leading to infinite;
			if(zji.abs()<1.0E-6)
				zji = new Complex(0,1.0E-6);
				
			netEquiv.getComplexEqn().setAij(zji, 0, 0);  // zji = Vj/Ii
			
			Complex[][] zMatrix = new Complex[1][1];
			
			zMatrix[0][0] = zji;
			
			netEquiv.setMatrix(zMatrix);
				  
			
		}
		else
			throw new Error("no boundary bus not found in the transmission system # "+boundaryId);
		
		
		return netEquiv;
		
	}
	

	private void addTransNetTheveninEquivToDistYABCMatrix(boolean isVoltSource) {
		// TODO Auto-generated method stub
		 for (Entry<String, NetworkEquivalent> e : netEquivTable.entrySet()){
					 
					 String busId = e.getKey();
					 
					 NetworkEquivalent equiv = e.getValue();
					 Complex z = equiv.getMatrix()[0][0];
					 
					 if(isVoltSource){
						 z = voltSourceImpedance;
					 }
					 
					 System.out.println("----->add Thenvin impedance Z = "+z.toString());
					 
					 Complex y = new Complex(1.0).divide(z);
					 Complex3x3 y3x3= new Complex3x3(y,y,y);
					 
					 // find out the corresponding bus and the distribution system
					 String distBoundaryBusId = "";
					 
					   if(busId.contains("Dummy")){
						   distBoundaryBusId = busId.replace("Dummy", "");
					   }
					   else
						   distBoundaryBusId = busId+"Dummy";
					   
					  DStabNetwork3Phase distNet = (DStabNetwork3Phase) this.subNetProcessor.getSubNetworkByBusId(distBoundaryBusId);
					  // need to re-calcuate Yabc before adding the new Z, in case Thevenin impedance has been added before;
					  try {
						distNet.formYMatrixABC();
					} catch (Exception e1) {
						
						e1.printStackTrace();
					} 
					 // add the Yabc to the YMatrixABC
					 int sortNum = distNet.getBus(distBoundaryBusId).getSortNumber();
					 
					 distNet.getYMatrixABC().addToA(y3x3, sortNum, sortNum);
		 }
				
	}
	
	private void updateTransNetTheveninEquivSource(boolean isVoltSource){
		
		 this.distNetNortonEquivCurrentTable.clear();
	     
		 for (Entry<String, NetworkEquivalent> e : netEquivTable.entrySet()){
			 
			 String busId = e.getKey();
			 Complex volt = this.transmissionNet.getBus(busId).getVoltage();
			 
			 
			 Complex3x1 volt3ph = new Complex3x1();
			 
			 volt3ph.b_1 = volt;
			 volt3ph = volt3ph.toABC();
			 
			 
			 //calculate the positive sequence Thevenin voltage based on positive sequence current injections
			 
			 Complex currInj = this.dist2TransEquivCurInjTable.get(busId);
			 
			 NetworkEquivalent equiv = e.getValue();
			 
			 Complex z = equiv.getMatrix()[0][0];
			 
			 Complex vth_pos = volt.subtract(z.multiply(currInj));
			 
			 Complex3x1 vth3ph = new Complex3x1();
			 
			 vth3ph.b_1 = vth_pos;
			 
			 vth3ph = vth3ph.toABC();
			 
			 equiv.getSource3x1()[0] = vth3ph;
			 
			 // calculate Norton current injection for distribution systems
			 Complex3x1 nortonCurInj3ph = new Complex3x1(vth3ph.a_0.divide(z),vth3ph.b_1.divide(z),vth3ph.c_2.divide(z));
			 
			 if(isVoltSource){
				 nortonCurInj3ph = new Complex3x1( volt3ph.a_0.divide(voltSourceImpedance),volt3ph.b_1.divide(voltSourceImpedance),volt3ph.c_2.divide(voltSourceImpedance));
			 }
			 
			 String distBoundaryBusId = "";
			 
			   if(busId.contains("Dummy")){
				   distBoundaryBusId = busId.replace("Dummy", "");
			   }
			   else
				   distBoundaryBusId = busId+"Dummy";
			   
			
			 
			 this.distNetNortonEquivCurrentTable.put(distBoundaryBusId, nortonCurInj3ph);
			 
		 }
		 
		
	
	}
	
	private void calculateDist2TransTotalPower(){
		
		double transMVABase = this.transmissionNet.getBaseMva();
	
		for(BaseDStabNetwork<?, ?> distNet:this.distNetList){
			
		
			List<String> boundaryList = subNetProcessor.getSubNet2BoundaryBusListTable().get(distNet.getId());
			
			if(boundaryList.size()!=1){
				throw new Error(" Only one source bus for a distribution system is supported!");
			}
			else{
				DStab3PBus sourceBus = (DStab3PBus) distNet.getBus(boundaryList.get(0));
				
				Complex3x1 vabc_1 = sourceBus.get3PhaseVotlages();
				
				Complex3x1 currInj3Phase = new Complex3x1();
				
				
				
				for(Branch bra: sourceBus.getBranchList()){
					if(bra.isActive()){
						DStab3PBranch acLine = (DStab3PBranch) bra;
						
						Complex3x1 Isource = null;
						
						if(bra.getFromBus().getId().equals(sourceBus.getId())){
							DStab3PBus toBus = (DStab3PBus) bra.getToBus();
							Complex3x1 vabc_2 = toBus.get3PhaseVotlages();
							
							Complex3x3 Yft = acLine.getYftabc();
							Complex3x3 Yff = acLine.getYffabc();
							Isource = Yff.multiply(vabc_1).add(Yft.multiply(vabc_2));
							currInj3Phase = currInj3Phase.subtract(Isource );
						}
						else{
							DStab3PBus fromBus = (DStab3PBus) bra.getFromBus();
							Complex3x1 vabc_2 = fromBus.get3PhaseVotlages();
							
							Complex3x3 Ytf = acLine.getYtfabc();
							Complex3x3 Ytt = acLine.getYttabc();
							
							Isource = Ytt.multiply(vabc_1).add(Ytf.multiply(vabc_2));
							
							currInj3Phase = currInj3Phase.subtract(Isource);
						}
					}
				}
				
				//TODO this needs to be updated if actual values are used in the distribution system
				double distMVABase = distNet.getBaseMva();
				

//				
				DStab3PBus sourceBus3Ph = (DStab3PBus) sourceBus; 
				
				Complex totalPower = sourceBus3Ph.get3PhaseVotlages().dotProduct(currInj3Phase.conjugate()).divide(3.0).multiply(distMVABase/transMVABase);
				
				//System.out.println("Total power (on Transmission Network MVA Base) = "+totalPower.toString());
				this.dist2Trans3PhaseCurInjTable.put(sourceBus.getId(), currInj3Phase);
				
				this.dist2TransTotalPowerTable.put(sourceBus.getId(),totalPower);
		
			}
		}
	}
	
	private void calculateDist2TransCurInjection(){
		
		   for(Entry<String,Complex> e: this.dist2TransTotalPowerTable.entrySet()){
			   String distBoundaryBusId = e.getKey();
			   
			   String transBoundaryBusId = "";
			   if(distBoundaryBusId.contains("Dummy")){
				   transBoundaryBusId = distBoundaryBusId.replace("Dummy", "");
			   }
			   else
				   transBoundaryBusId = distBoundaryBusId+"Dummy";
			   
			   BaseDStabBus transBoundaryBus = this.transmissionNet.getBus(transBoundaryBusId);
			   
			   if(transBoundaryBus == null){
				   throw new Error("The tranmission network boundary bus is not found, ID: "+transBoundaryBusId);
			   }
			   else{

				   Complex transBusVolt = transBoundaryBus.getVoltage();
				   
				   Complex currInj = e.getValue().divide(transBusVolt).conjugate();
				   
				   this.dist2TransEquivCurInjTable.put(transBoundaryBusId, currInj);
	
			   }
			   
		   }
		
	}
	
	
	
	// currently it only supports single-phase A/C motor load model, and assume all other loads represented by constant impedance
	private boolean updateDistNetBusLoads(DStabNetwork3Phase distNet, double loadModelVminpu){
		
		//iterate over all dynamic load models, and update them by calling the nextStep() functions
		
		//obtain the dynamic load model total load, and update the bus total load accordingly.
		  // Stotal = Smotor+ (1-Frac_dyn)*initialTotalLoad
		  // here need to use the Bus3Phase.get3PhaseInitTotalLoad and getInit3PhaseVolages functions
		
		for( BaseDStabBus b : distNet.getBusList()) {
		
			if(b.isActive()){
				DStab3PBus bus3p = (DStab3PBus) b;
				Complex3x1 load3P = new Complex3x1();
				
				double phaseADynLoadPercentage = 0.0;
				double phaseBDynLoadPercentage = 0.0;
				double phaseCDynLoadPercentage = 0.0;
				
				if(bus3p.getPhaseADynLoadList().size()>0){
					for(DynLoadModel1Phase dynLdPhA: bus3p.getPhaseADynLoadList()){
						//TODO need to check the unit
					
						load3P.a_0 = load3P.a_0.add(dynLdPhA.getLoadPQ());
						
						phaseADynLoadPercentage +=dynLdPhA.getLoadPercent();
					}
				}
				
				if(bus3p.getPhaseBDynLoadList().size()>0){
					for(DynLoadModel1Phase dynLdPhB: bus3p.getPhaseBDynLoadList()){
						
						//TODO need to check the unit
						load3P.b_1 = load3P.b_1.add(dynLdPhB.getLoadPQ());
						
						phaseBDynLoadPercentage +=dynLdPhB.getLoadPercent();
						
					}
					
				
									
				}
				
				if(bus3p.getPhaseCDynLoadList().size()>0){
					for(DynLoadModel1Phase dynLdPhC: bus3p.getPhaseCDynLoadList()){
											
						//TODO need to check the unit
					
						load3P.c_2 = load3P.c_2.add(dynLdPhC.getLoadPQ());
						phaseCDynLoadPercentage +=dynLdPhC.getLoadPercent();
					}
					
				}
				
				if(bus3p.getThreePhaseDynLoadList().size()>0){
					for(DynLoadModel3Phase dynLd3Ph: bus3p.getThreePhaseDynLoadList()){
											
						//TODO need to check the unit
					
						load3P= load3P.add(dynLd3Ph.getPower3Phase(UnitType.PU));
						
						phaseADynLoadPercentage +=dynLd3Ph.getLoadPercent();
						phaseBDynLoadPercentage +=dynLd3Ph.getLoadPercent();
						phaseCDynLoadPercentage +=dynLd3Ph.getLoadPercent();
					}
					
				}
				
				
				
				if(bus3p.get3PhaseTotalLoad().absMax()>0.0 && (phaseADynLoadPercentage>0 ||
						phaseBDynLoadPercentage>0||phaseCDynLoadPercentage>0)){// There are dynamic loads
				    
					Complex3x1 initNonDynLoad3P = bus3p.get3PhaseNetLoadResults();
					
					bus3p.getThreePhaseLoadList().clear();
					
					//TODO here assume all loads are constant power loads
					bus3p.setLoadCode(AclfLoadCode.CONST_P);
					
			  		DStab3PLoad load1 = new DStab3PLoadImpl();
			  		
//			  		System.out.println("3phase dyn load = "+load3P.toString());
			  		
			  		
					load1.set3PhaseLoad(load3P.add(initNonDynLoad3P));// the new total load;
					
					//load1.setVminpu(loadModelVminpu);
					
					bus3p.getThreePhaseLoadList().add(load1);
					
					
				}
				
			} //end of if-active
		} // end of for-loop
		
		return true;
		
	}
	
	private void addTheveninEquivImpedanceToDistNet() throws InterpssException {
		// TODO Auto-generated method stub
		 for (Entry<String, NetworkEquivalent> e : netEquivTable.entrySet()){
					 
					 String busId = e.getKey(); // transmission Boundary Bus Id
					 
					 NetworkEquivalent equiv = e.getValue();
					 Complex z = equiv.getMatrix()[0][0];
					
					 Complex3x3 z120= new Complex3x3(z,z,z.multiply(2.5)); // assuming z0 = 2.5z1
					 
					 Complex3x3 zabc = z120.ToAbc();
					 
					 // find out the corresponding bus and the distribution system
					 String distBoundaryBusId = "";
					 
					   if(busId.contains("Dummy")){
						   distBoundaryBusId = busId.replace("Dummy", "");
					   }
					   else
						   distBoundaryBusId = busId+"Dummy";
					  
					   
					  DStabNetwork3Phase distNet = (DStabNetwork3Phase) this.subNetProcessor.getSubNetworkByBusId(distBoundaryBusId);
					  
					  
					  BaseDStabBus distBoundaryBus = distNet.getBus(distBoundaryBusId);
					  
					  distBoundaryBus.setGenCode(AclfGenCode.NON_GEN); // reset the boundary bus to a non-gen
					  
					   // define the Thevenin equivalent bus
					  String theveinEquivBusId = distBoundaryBusId+"-Thevenin";
					  
			
					  
					  DStab3PBus theveninBus = ThreePhaseObjectFactory.create3PDStabBus(theveinEquivBusId, distNet);
				  		// set bus name and description attributes
				 	  theveninBus.setAttributes("Thevein Bus of "+distNet.getId(), "");
				  		// set bus base voltage 
				 	  theveninBus.setBaseVoltage(distBoundaryBus.getBaseVoltage());
				  		// set bus to be a swing bus
				 	  theveninBus.setGenCode(AclfGenCode.SWING);
					
				 	 // add a new branch for representing the Thevenin equivalent
				 		
				 		DStab3PBranch bra23 = ThreePhaseObjectFactory.create3PBranch(theveinEquivBusId, distBoundaryBusId, "0", distNet);
						bra23.setBranchCode(AclfBranchCode.LINE);
						bra23.setZ( z);
						bra23.setHShuntY(new Complex(0, 0.));
						bra23.setZ0( z.multiply(2.5));
						bra23.setHB0(0.0);
		 }

	}
	
	private void updateDistNetTheveninEquivImpedance() {
		// TODO Auto-generated method stub
		 for (Entry<String, NetworkEquivalent> e : netEquivTable.entrySet()){
					 
					 String busId = e.getKey(); // transmission Boundary Bus Id
					 
					 NetworkEquivalent equiv = e.getValue();
					 Complex z = equiv.getMatrix()[0][0];
					
					 Complex3x3 z120= new Complex3x3(z,z,z.multiply(2.5)); // assuming z0 = 2.5z1
					 
					 Complex3x3 zabc = z120.ToAbc();
					 
					 // find out the corresponding bus and the distribution system
					 String distBoundaryBusId = "";
					 
					   if(busId.contains("Dummy")){
						   distBoundaryBusId = busId.replace("Dummy", "");
					   }
					   else
						   distBoundaryBusId = busId+"Dummy";
					  
					   // define the Thevenin equivalent bus
					  String theveinEquivBusId = distBoundaryBusId+"-Thevenin";
					  
					  //
					   
					  DStabNetwork3Phase distNet = (DStabNetwork3Phase) this.subNetProcessor.getSubNetworkByBusId(distBoundaryBusId);
					  
			//TODO
					
		 }
	}
	
	
	

}
