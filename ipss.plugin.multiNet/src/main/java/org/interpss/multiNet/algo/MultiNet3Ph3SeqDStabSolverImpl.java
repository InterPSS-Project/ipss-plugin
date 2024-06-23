package org.interpss.multiNet.algo;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.device.DynamicBranchDevice;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.mach.Machine;

public class MultiNet3Ph3SeqDStabSolverImpl extends MultiNetDStabSolverImpl {
	
	private List<String>  threePhaseSubNetIdList = null;
	private StringBuffer sb = new StringBuffer();

	public MultiNet3Ph3SeqDStabSolverImpl(DynamicSimuAlgorithm algo,
			AbstractMultiNetDStabSimuHelper mNetSimuHelper) {
		super(algo, mNetSimuHelper);
		
		this.threePhaseSubNetIdList = this.multiNetSimuHelper.getSubNetworkProcessor().getThreePhaseSubNetIdList();
		
	}
	
	@Override 
	public boolean initialization() {
		this.simuPercent = 0;
		this.simuTime = 0; //required for running multiple DStab simulations in a loop
		consoleMsg("Start multi-SubNetwork initialization ...");

		if (!dstabAlgo.getNetwork().isLfConverged()) {
			ipssLogger.severe("Error: Loadflow not converged yet!");
			return false;
		}
		
		// network  initialization: mainly includes load model conversion and dynamic model initialization
		for(BaseDStabNetwork<?,?> dsNet: this.subNetList){
			   boolean flag = true;
			   if(this.threePhaseSubNetIdList!=null && this.threePhaseSubNetIdList.contains(dsNet.getId())){    
				   flag = dsNet.initDStabNet();
			   }
			   else{
				   DStabNetwork3Phase dsNet3Phase = (DStabNetwork3Phase) dsNet;
				   flag = dsNet3Phase.initPosSeqDStabNet();
				
			   }
			   if (!flag){
					  ipssLogger.severe("Error: SubNetwork initialization error:"+dsNet.getId());
					  return false;
				}
			
		}
		// prepare the equivalents of the subnetworks
		this.multiNetSimuHelper.calculateSubNetTheveninEquiv();

		
		
		// prepare tie-line-and-boundary bus incidence matrix, Zl
		
		this.multiNetSimuHelper.prepareBoundarySubSystemMatrix();
		
		// output initial states
		if (!procInitOutputEvent())
			return false;

	  	//System.out.println(net.net2String());
		consoleMsg("Initialization finished");
		return true;		
	}
	
	@Override 
	public boolean networkSolutionStep() throws DStabSimuException {
		
       boolean netSolConverged = true;
		
		//System.out.println(" simu time = "+time);
		
		for(int i=0;i<maxIterationTimes;i++){
			
			netSolConverged = true;
			
			// The first  step of the multi-subNetwork solution is to solve each subnetwork independently without current injections from the 
			// connection tie-lines
			for(BaseDStabNetwork<?,?>dsNet: subNetList){
				
				DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
				
				if(this.threePhaseSubNetIdList!=null && this.threePhaseSubNetIdList.contains(dsNet.getId())){
					
					// make sure there is no current injection at the boundary
					dsNet3Ph.set3phaseCustomCurrInjTable(null);
					dsNet3Ph.solveNetEqn();
					
				}
				else{
				
					// make sure there is no current injection at the boundary
					dsNet.setCustomBusCurrInjHashtable(null);
					dsNet.setCustom3SeqBusCurrInjHashtable(null);
					// solve net equation
					
					//NOTE: all subnetworks are modeled in three-phase, in order to solve the positive-sequence network
					// the solvePosSeqNetEqn() method of the DStabNetwork3Phase object should be called.
					if (dsNet3Ph.solvePosSeqNetEqn()){
						
						//TODO need to save the three-sequence bus voltage, such that it can be used for updating the 
						// Vth in the following step.
						for(BaseDStabBus bus:dsNet.getBusList()){
							bus.setThreeSeqVoltage(new Complex3x1(new Complex(0,0), bus.getVoltage(), new Complex(0,0)));
						}
						
					}else
						throw new DStabSimuException("Exception in dstabNet.solvePosSeqNetEqn() : "+dsNet.getId());
				}
				
			}  //end for-subnetwork loop
			
		
			 /*
			  *  In the 2nd step, first  solve the boundary tie-line subsystem to determine the current flows
			  *  in the tie-lines. Subsequently, feed the tie-line current back to the subsystems and 
			  *  perform the subsystem network solution again. Last, summing up the bus voltages calculated
			  *  in these two steps to determine the final bus voltages, based on the superposition theory.
			  *  
			  *  V = Vinternal + Vexternal
			  */
			
				  
				  // fetch the boundary bus voltages and form the Thevenin equivalent source arrays
				 
				  this.multiNetSimuHelper.updateSubNetworkEquivSource();
	
				  
				  // solve the tie-line subsystems, to determine the currents flowing through the tie-lines.
				  this.multiNetSimuHelper.solveBoundarySubSystem();
				  
				  //solve all the SubNetworks With only Boundary Current Injections
				  this.multiNetSimuHelper.solveSubNetWithBoundaryCurrInjection();
				  
				  for(BaseDStabNetwork<?, ?> dsNet: subNetList){
					for ( Bus busi : dsNet.getBusList() ) {
						BaseDStabBus bus = (BaseDStabBus)busi;
						if(bus.isActive()){
							
							if(i>=1){
								 boolean boolflag = NumericUtil.equals(bus.getVoltage(),voltageRecTable.get(bus.getId()),this.converge_tol);
								 netSolConverged = netSolConverged && boolflag;
								
							}
							voltageRecTable.put(bus.getId(), bus.getVoltage());
						}
					}
				  }
				  
			
			  if(i>0 && netSolConverged) {
				  IpssLogger.getLogger().fine(getSimuTime()+","+"multi subNetwork solution in the nextStep() is converged, iteration #"+(i+1));
				  break;
			  }
	
		  } // for maxIterationTimes loop
			
		return true;
		
	}
	
	@Override
	public void diffEqnIntegrationStep(double t, double dt, DynamicSimuMethod method, int flag) throws DStabSimuException{

		 /*
		  * Third step : with the network solved, the bus voltage and current injections are determined, it is time to solve the dynamic devices using 
		  * integration methods
		  *  x(t+deltaT) = x(t) + dx_dt*deltaT 
		  */
		

	  for(BaseDStabNetwork<?, ?> dsNet: subNetList){  
		// Solve DEqn for all dynamic bus devices
			for (Bus b : dsNet.getBusList()) {
				if(b.isActive()){
					BaseDStabBus<? extends DStabGen, ? extends DStabLoad> bus = (BaseDStabBus)b;
					
					// calculate bus frequency
					 if (!bus.nextStep(dt, method, flag)) {
							throw new DStabSimuException("Error occured, Simulation will be stopped");
					}
					 
					for (DynamicBusDevice device : bus.getDynamicBusDeviceList()) {
						// solve DEqn for the step. This includes all controller's nextStep() call
						if(device.isActive()){
							if (!device.nextStep(dt, method, flag)) {
								throw new DStabSimuException("Error occured, Simulation will be stopped");
							}
						}
					}
					
					// Solve DEqn for generator 
					if(bus.getContributeGenList().size()>0){
						for(AclfGen gen:bus.getContributeGenList()){
							if(gen.isActive()){
								Machine mach = ((DStabGen)gen).getMach();
								if(mach!=null && mach.isActive()){
								   if (!mach.nextStep(dt, method, flag)) {
									  throw new DStabSimuException("Error occured when solving nextStep for mach #"+ mach.getId()+ "@ bus - "
								                   +bus.getId()+", Simulation will be stopped!");
								   }
								}
							}
						}
					}
				
				}
			}// bus-loop

			// Solve DEqn for all dynamic branch devices
			for (Branch b : dsNet.getBranchList()) {
				DStabBranch branch = (DStabBranch)b;
				for (DynamicBranchDevice device : branch.getDynamicBranchDeviceList()) {
					// solve DEqn for the step. This includes all controller's nextStep() call
					if (!device.nextStep(dt, method, flag)) {
						throw new DStabSimuException("Error occured, Simulation will be stopped");
					}
				}
			} 
		  
			
		  // The network solution and integration steps ends here, the following is mainly to record or update some intermediate variables
		  
		  
		  // update the dynamic attributes and calculate the bus frequency
	     //  for(DStabilityNetwork dsNet: subNetList){
		   if(flag ==1) {
				for ( Bus busi : dsNet.getBusList() ) {
					BaseDStabBus bus = (BaseDStabBus)busi;
					if(bus.isActive()){
						// update dynamic attributes of the dynamic devices connected to the bus
						 try {
							bus.updateDynamicAttributes(false);
						} catch (InterpssException e) {
						
							e.printStackTrace();
						}
				
					}
				}
		  }
	    	
	 } // for subNetwork loop
		
		
	}
	
//	@Override 
//	public void nextStep(double time, double dt, DynamicSimuMethod method)  throws DStabSimuException {
//		 
//		boolean netSolConverged = true;
//		
//		//System.out.println(" simu time = "+time);
//		
//		for(int i=0;i<maxIterationTimes;i++){
//			
//			netSolConverged = true;
//			
//			// The first  step of the multi-subNetwork solution is to solve each subnetwork independently without current injections from the 
//			// connection tie-lines
//			for(BaseDStabNetwork<?,?>dsNet: subNetList){
//				
//				DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
//				
//				if(this.threePhaseSubNetIdList!=null && this.threePhaseSubNetIdList.contains(dsNet.getId())){
//					
//					// make sure there is no current injection at the boundary
//					dsNet3Ph.set3phaseCustomCurrInjTable(null);
//					dsNet3Ph.solveNetEqn();
//					
//				}
//				else{
//				
//					// make sure there is no current injection at the boundary
//					dsNet.setCustomBusCurrInjHashtable(null);
//					dsNet.setCustom3SeqBusCurrInjHashtable(null);
//					// solve net equation
//					
//					//NOTE: all subnetworks are modeled in three-phase, in order to solve the positive-sequence network
//					// the solvePosSeqNetEqn() method of the DStabNetwork3Phase object should be called.
//					if (dsNet3Ph.solvePosSeqNetEqn()){
//						
//						//TODO need to save the three-sequence bus voltage, such that it can be used for updating the 
//						// Vth in the following step.
//						for(BaseDStabBus bus:dsNet.getBusList()){
//							bus.setThreeSeqVoltage(new Complex3x1(new Complex(0,0), bus.getVoltage(), new Complex(0,0)));
//						}
//						
//					}else
//						throw new DStabSimuException("Exception in dstabNet.solvePosSeqNetEqn() : "+dsNet.getId());
//				}
//				
//			}  //end for-subnetwork loop
//			
//		
//			 /*
//			  *  In the 2nd step, first  solve the boundary tie-line subsystem to determine the current flows
//			  *  in the tie-lines. Subsequently, feed the tie-line current back to the subsystems and 
//			  *  perform the subsystem network solution again. Last, summing up the bus voltages calculated
//			  *  in these two steps to determine the final bus voltages, based on the superposition theory.
//			  *  
//			  *  V = Vinternal + Vexternal
//			  */
//			
//				  
//				  // fetch the boundary bus voltages and form the Thevenin equivalent source arrays
//				 
//				  this.multiNetSimuHelper.updateSubNetworkEquivSource();
//	
//				  
//				  // solve the tie-line subsystems, to determine the currents flowing through the tie-lines.
//				  this.multiNetSimuHelper.solveBoundarySubSystem();
//				  
//				  //solve all the SubNetworks With only Boundary Current Injections
//				  this.multiNetSimuHelper.solveSubNetWithBoundaryCurrInjection();
//				  
//				  for(BaseDStabNetwork<?, ?> dsNet: subNetList){
//					for ( Bus busi : dsNet.getBusList() ) {
//						BaseDStabBus bus = (BaseDStabBus)busi;
//						if(bus.isActive()){
//							
//							if(i>=4){
//								 boolean boolflag = NumericUtil.equals(bus.getVoltage(),voltageRecTable.get(bus.getId()),this.converge_tol);
//								 netSolConverged = netSolConverged && boolflag;
//								
//							}
//							voltageRecTable.put(bus.getId(), bus.getVoltage());
//						}
//					}
//				  }
//				  
//			
//			  if(i>0 && netSolConverged) {
//				  IpssLogger.getLogger().fine(getSimuTime()+","+"multi subNetwork solution in the nextStep() is converged, iteration #"+(i+1));
//				  break;
//			  }
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
//		  int flag  = 0;
//		  for(BaseDStabNetwork<?, ?> dsNet: subNetList){  
//			// Solve DEqn for all dynamic bus devices
//				for (Bus b : dsNet.getBusList()) {
//					if(b.isActive()){
//						BaseDStabBus<? extends DStabGen, ? extends DStabLoad> bus = (BaseDStabBus)b;
//						
//						// calculate bus frequency
//						 if (!bus.nextStep(dt, method, flag)) {
//								throw new DStabSimuException("Error occured, Simulation will be stopped");
//						}
//						 
//						for (DynamicBusDevice device : bus.getDynamicBusDeviceList()) {
//							// solve DEqn for the step. This includes all controller's nextStep() call
//							if(device.isActive()){
//								if (!device.nextStep(dt, method, flag)) {
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
//									   if (!mach.nextStep(dt, method, flag)) {
//										  throw new DStabSimuException("Error occured when solving nextStep for mach #"+ mach.getId()+ "@ bus - "
//									                   +bus.getId()+", Simulation will be stopped!");
//									   }
//									}
//								}
//							}
//						}
//						
//						//TODO Solve DEqn for dynamic load, e.g. induction motor
//						
//					}
//				}// bus-loop
//
//				// Solve DEqn for all dynamic branch devices
//				for (Branch b : dsNet.getBranchList()) {
//					DStabBranch branch = (DStabBranch)b;
//					for (DynamicBranchDevice device : branch.getDynamicBranchDeviceList()) {
//						// solve DEqn for the step. This includes all controller's nextStep() call
//						if (!device.nextStep(dt, method, flag)) {
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
//					BaseDStabBus bus = (BaseDStabBus)busi;
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
//						 if (!bus.nextStep(dt, method, flag)) {
//								throw new DStabSimuException("Error occured, Simulation will be stopped");
//							}
//					}
//				}
//		    	
//		 } // for subNetwork loop
//			
//			
//		// back up the states	
//			for(BaseDStabNetwork<?, ?> dsNet: subNetList){
//			 // backup the states
//			 for (Bus b :  dsNet.getBusList()) {
//					if(b.isActive()){
//						BaseDStabBus<? extends DStabGen, ? extends DStabLoad> bus = (BaseDStabBus<? extends DStabGen, ? extends DStabLoad>) b;
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
//			
//		// save the interface currents
//		 
//			Hashtable<String, Hashtable<String, Complex3x1>> subNetCurInjTable = 
//					      ((MultiNet3Ph3SeqDStabSimuHelper)this.multiNetSimuHelper).getSubNet3SeqCurrInjTable();
//			
//			
//			for(BaseDStabNetwork<?, ?> subNet: subNetList){
//				for(Entry<String,Complex3x1> e: subNetCurInjTable.get(subNet.getId()).entrySet()){
//	   				   Complex3x1 IinjAbc = Complex3x1.z12_to_abc(e.getValue());
//	   				   sb.append("t, "+time+", BusID,"+e.getKey()+",Ia=,"+IinjAbc.a_0.abs()+",Ib=,"+IinjAbc.b_1.abs()+",Ic=,"+IinjAbc.c_2.abs()+"\n");
//				}
//			}
//	
//	}
	
	public String getRecordResults(){
		return sb.toString();
	}
	

}
