package org.ipss.multiNet.algo;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static com.interpss.dstab.funcImpl.DStabFunction.BuiltBusState;
import static com.interpss.dstab.funcImpl.DStabFunction.BuiltMachineState;
import static com.interpss.dstab.funcImpl.DStabFunction.BuiltScriptDynamicBusDeviceState;

import java.util.Hashtable;
import java.util.List;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.exp.InterpssException;
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
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.datatype.DStabSimuEvent;
import com.interpss.dstab.device.DynamicBranchDevice;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.mach.Machine;

public class MultiNetDStabSolverImpl extends DStabSolverImpl {
	
	protected AbstractMultiNetDStabSimuHelper multiNetSimuHelper = null;
	protected List<DStabilityNetwork> subNetList = null;

	public MultiNetDStabSolverImpl(DynamicSimuAlgorithm algo, AbstractMultiNetDStabSimuHelper mNetSimuHelper) {
		super(algo, IpssCorePlugin.getMsgHub());
		this.multiNetSimuHelper = mNetSimuHelper;
		this.subNetList = this.multiNetSimuHelper.getSubNetworkProcessor().getSubNetworkList();
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
			
				if (!dsNet.initDStabNet()){
					  ipssLogger.severe("Error: SubNetwork initialization error:"+dsNet.getId());
					  return false;
				}
			
		}
		// calculate the Thevenin equivalent of each subsystem only 
		// after the networks have been initialized
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
	
	/**
	 * perform a full step of simulation by solving the differential 
	 * eqn 
	 * 
	 * @param updateTime indicator to update the simuTime field
	 */
	@Override
	public boolean solveDEqnStep(boolean updateTime){
		try{
			
		boolean hasEvent = hasDynEvent(simuTime);
		if(hasDynEvent(simuTime)){
			for(DStabilityNetwork dsNet: subNetList){
				
				// Solve DEqn for all dynamic bus devices
				for (DStabBus bus : dsNet.getBusList()) {
				  //only the measurements of active buses will be output. 
				   if(bus.isActive())
				     output(bus, simuTime, true);
			     }
			}
		}
		
		
		// performing actions before solving DEqn, for example, applying dynamic event
		beforeStep(simuTime);

		
		// solving DEqn
		nextStep(simuTime, dstabAlgo.getSimuStepSec(), dstabAlgo.getSimuMethod());

		
		if (updateTime) {
			    
			// output simulation results
			++outCnt;
            for(DStabilityNetwork dsNet: subNetList){
				
				// Solve DEqn for all dynamic bus devices
			   for (DStabBus bus : dsNet.getBusList()) {
				   //only the measurements of active buses will be output. 
				    if(bus.isActive())
				         output(bus, simuTime, outCnt >= outPerSteps);
			   }
			}

				// increase simulation time
				simuTime += dstabAlgo.getSimuStepSec();



				if (outCnt >= outPerSteps)
					outCnt = 0;
		}
		// performing actions after solving QEqn
		afterStep(simuTime);
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	
	@Override public void beforeStep(double time)  throws DStabSimuException {
		// Process dynamic events
		if (!dstabAlgo.isDisableDynamicEvent()) {
			applyDynamicEvent(time);
		}
		
		// when every there is an event, need to update the [Zl] matrix of the boundary subsystem
		if( hasDynEvent(time)){
			
			this.multiNetSimuHelper.prepareBoundarySubSystemMatrix();
		}
	}
   
	
	@Override 
	public void nextStep(double time, double dt, DynamicSimuMethod method)  throws DStabSimuException {
		 
		boolean netSolConverged = true;
		//maxIterationTimes =1;
		for(int i=0;i<maxIterationTimes;i++){ 
			 
			// The first  step of the multi-subNetwork solution is to solve each subnetwork independently without current injections from the 
			// connection tie-lines
			for(DStabilityNetwork dsNet: subNetList){
				
				// make sure there is no current injection at the boundary
				dsNet.setCustomBusCurrInjHashtable(null);
				
				// solve net equation
				if (!dsNet.solveNetEqn())
					throw new DStabSimuException("Exception in dstabNet.solveNetEqn()");
				
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
	
	@Override public boolean procInitOutputEvent() {
		try {
			for(DStabilityNetwork dsNet: subNetList){
				
				// Solve DEqn for all dynamic bus devices
				for (DStabBus bus : dsNet.getBusList()) {

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
			ipssLogger.severe(e.toString());
			return false;
		}
	}
	


}
