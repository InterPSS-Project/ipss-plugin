package org.ipss.multiNet.algo;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.util.NumericUtil;
import org.ipss.threePhase.dynamic.DStabNetwork3Phase;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.device.DynamicBranchDevice;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.mach.Machine;

public class MultiNet3Ph3SeqDStabSolverImpl extends MultiNetDStabSolverImpl {
	
	private List<String>  threePhaseSubNetIdList = null;

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
		
		// network initialization, initial bus sc data, transfer machine sc info to bus.
		for(DStabilityNetwork dsNet: this.subNetList){
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
	public void nextStep(double time, double dt, DynamicSimuMethod method)  throws DStabSimuException {
		 
		boolean netSolConverged = true;
		
		// retrieve the threePhaseSubNetwork
		
	
		for(int i=0;i<maxIterationTimes;i++){ 
			 
			// The first  step of the multi-subNetwork solution is to solve each subnetwork independently without current injections from the 
			// connection tie-lines
			for(DStabilityNetwork dsNet: subNetList){
				
				DStabNetwork3Phase dsNet3Ph = (DStabNetwork3Phase) dsNet;
				
				if(this.threePhaseSubNetIdList!=null && this.threePhaseSubNetIdList.contains(dsNet.getId())){
					
					// make sure there is no current injection at the boundary
					dsNet3Ph.set3phaseCustomCurrInjTable(null);
					dsNet3Ph.solveNetEqn();
					
				}
				else{
				
					// make sure there is no current injection at the boundary
					dsNet.setCustomBusCurrInjHashtable(null);
					
					// solve net equation
					
					//NOTE: all subnetworks are modeled in three-phase, in order to solve the positive-sequence network
					// the solvePosSeqNetEqn() method of the DStabNetwork3Phase object should be called.
					if (dsNet3Ph.solvePosSeqNetEqn()){
						
						//TODO need to save the three-sequence bus voltage, such that it can be used for updating the 
						// Vth in the following step.
						for(DStabBus bus:dsNet3Ph.getBusList()){
							bus.set3SeqVoltage(new Complex3x1(new Complex(0,0), bus.getVoltage(), new Complex(0,0)));
						}
						
					}else
						throw new DStabSimuException("Exception in dstabNet.solveNetEqn()");
				}
				
			}  //end for-subnetwork loop
			
		
			 /*
			  *  In the 2nd step, first  solve the boundary tie-line subsystem to determine the current flows
			  *  in the tie-lines. Subsequently, feed the tie-line current back to the subsystems and 
			  *  perform the subsystem network solution again. Last, summing up the bus voltages calculated
			  *  in these two steps to determine the final bus voltages, based on the superposition theory.
			  *  
			  *  V = Vinternal = Vexternal
			  */
			
				  
				  // fetch the boundary bus voltages and form the Thevenin equivalent source arrays
				 
				  this.multiNetSimuHelper.updateSubNetworkEquivSource();
	
				  
				  // solve the tie-line subsystems, to determine the currents flowing through the tie-lines.
				  this.multiNetSimuHelper.solveBoundarySubSystem();
				  
				  //solve all the SubNetworks With only Boundary Current Injections
				  this.multiNetSimuHelper.solveSubNetWithBoundaryCurrInjection();
				  
				  for(DStabilityNetwork dsNet: subNetList){
					for ( Bus busi : dsNet.getBusList() ) {
						DStabBus bus = (DStabBus)busi;
						if(bus.isActive()){
							
							if(i>=1){
								if(!NumericUtil.equals(bus.getVoltage(),voltageRecTable.get(bus.getId()),this.converge_tol))
									netSolConverged =false;
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
			
			
			
			 /*
			  * Third step : with the network solved, the bus voltage and current injections are determined, it is time to solve the dynamic devices using 
			  * integration methods
			  *  x(t+deltaT) = x(t) + dx_dt*deltaT 
			  */
			  
		  for(DStabilityNetwork dsNet: subNetList){  
			// Solve DEqn for all dynamic bus devices
				for (Bus b : dsNet.getBusList()) {
					if(b.isActive()){
						DStabBus bus = (DStabBus)b;
						for (DynamicBusDevice device : bus.getDynamicBusDeviceList()) {
							// solve DEqn for the step. This includes all controller's nextStep() call
							if(device.isActive()){
								if (!device.nextStep(dt, method)) {
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
									   if (!mach.nextStep(dt, method)) {
										  throw new DStabSimuException("Error occured when solving nextStep for mach #"+ mach.getId()+ "@ bus - "
									                   +bus.getId()+", Simulation will be stopped!");
									   }
									}
								}
							}
						}
						
						//TODO Solve DEqn for dynamic load, e.g. induction motor
					}
				}// bus-loop

				// Solve DEqn for all dynamic branch devices
				for (Branch b : dsNet.getBranchList()) {
					DStabBranch branch = (DStabBranch)b;
					for (DynamicBranchDevice device : branch.getDynamicBranchDeviceList()) {
						// solve DEqn for the step. This includes all controller's nextStep() call
						if (!device.nextStep(dt, method)) {
							throw new DStabSimuException("Error occured, Simulation will be stopped");
						}
					}
				} 
			  
				
			  // The network solution and integration steps ends here, the following is mainly to record or update some intermediate variables
			  
			  
			  // update the dynamic attributes and calculate the bus frequency
		     //  for(DStabilityNetwork dsNet: subNetList){
			
				for ( Bus busi : dsNet.getBusList() ) {
					DStabBus bus = (DStabBus)busi;
					if(bus.isActive()){
						
						// update dynamic attributes of the dynamic devices connected to the bus
						 try {
							bus.updateDynamicAttributes(false);
						} catch (InterpssException e) {
						
							e.printStackTrace();
						}
						 // calculate bus frequency
						 if (!bus.nextStep(dt, method)) {
								throw new DStabSimuException("Error occured, Simulation will be stopped");
							}
					}
				}
		    	
		 } // for subNetwork loop
			
			
		// back up the states	
			for(DStabilityNetwork dsNet: subNetList){
			 // backup the states
			 for (Bus b :  dsNet.getBusList()) {
					if(b.isActive()){
						DStabBus bus = (DStabBus)b;
						// Solve DEqn for generator 
						if(bus.getContributeGenList().size()>0){
							for(AclfGen gen:bus.getContributeGenList()){
								if(gen.isActive()){
									Machine mach = ((DStabGen)gen).getMach();
									if(mach!=null && mach.isActive()){
									  mach.backUpStates();
									}
								}
							}
						}
						
					}
			  }
		}
	
	}
	

}
