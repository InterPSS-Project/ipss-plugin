package org.interpss.threePhase.dynamic.algo;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.model.DynLoadModel3Phase;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.common.msg.IpssMessage;
import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.core.acsc.fault.AcscBranchFault;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.algo.defaultImpl.DynamicEventProcessor;
import com.interpss.dstab.datatype.DStabSimuTimeEvent;
import com.interpss.dstab.devent.DynamicSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;
import com.interpss.dstab.devent.GenerationEnergizationEvent;
import com.interpss.dstab.devent.GenerationTripEvent;
import com.interpss.dstab.devent.LoadChangeEvent;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.dynLoad.DynLoadModel;

public class DynamicEventProcessor3Phase extends DynamicEventProcessor {
	
	protected DStabNetwork3Phase net = null;
	/**
	 * process network dynamic event
	 * 
	 * @param eventMsg network dynamic event
	 * @return false if there is any issue during event handling process 
	 */
	@Override public boolean onMsgEventStatus(IpssMessage eventMsg) {
		if (eventMsg instanceof DStabSimuTimeEvent) {
			DStabSimuTimeEvent dEventMsg = (DStabSimuTimeEvent) eventMsg;
			if (dEventMsg.getType() == DStabSimuTimeEvent.ProessDynamicEvent) {
				
				this.net = (DStabNetwork3Phase) dEventMsg.getDStabNetData();
				
				double t = dEventMsg.getTime();
				if (hasAnyEvent(t)) {
					
					//System.out.println("dynamic event at: "+t);
					/*
					 * We always start from a full Y-matrix without any fault. At
					 * any point, if there is an event, we apply all current
					 * active events to re-construct the Y-matrix.
					 */

					// apply those events which result in changing Y-matrix,
					// such as turn-off a branch
					for (DynamicSimuEvent dEvent : net.getDynamicEventList()) {
						if (dEvent.hasEvent()) {
							applyDynamicEventBefore(dEvent, t);
						}
					}

					// Rebuild the Ymatrix to no-fault condition, which means
					// all events are cleared.
					//net.setYMatrix(net.formYMatrix(SequenceCode.POSITIVE, false));
					try {
						net.formYMatrixABC();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					
					
					ipssLogger.fine("Reset Ymatrix for event applying");

					// apply those events which result in adding z to Y-matrix,
					// such as applying fault Z
					for (DynamicSimuEvent dEvent : net.getDynamicEventList()) {
						if (dEvent.hasEvent()) {
							try{
								applyDynamicEventAfter(dEvent, t);
							} catch (IpssNumericException e) {
								ipssLogger.severe(e.toString());
								return false;
							}
						}
					}
					
					// System.out.print(net.getYMatrixABC().getSparseEqnComplex().toString());

					// publish Y matrix change event
					// someone may be interested in the change event, for
					// example, user acceptance testing.
					if (net.getNetChangeListener() != null)
						net.getNetChangeListener().onMsgEvent(
								new DStabSimuTimeEvent(	DStabSimuTimeEvent.YMatrixChangeEvent,
										net.getYMatrixABC(), t));
					
					//TODO output the state first
					

					
					// solve network and update bus voltage
					//TODO no need any more as network solution is solved at the beginning in the nextStep, or just right after this 
					net.solveNetEqn();  
				}
			}
		}
		return true;
	}
	
	@Override
	protected boolean hasAnyEvent(double t) {
		boolean has = false;
		for (DynamicSimuEvent dEvent : net.getDynamicEventList()) {
			if (dEvent.hasEventAt(t)) {
				has = true;
			}
		}
		return has;
	}

	@Override public void onMsgEvent(IpssMessage eventMsg) {
		throw new InterpssRuntimeException("Method not applicable");
	}
	
	// apply event before building the Y-matrix
	@Override
	protected void applyDynamicEventBefore(DynamicSimuEvent e, double t) {
		
		// for branch fault, we first take the branch at the fault point
		// it may put back in service if there is a reclosure.
		if (e.getType() == DynamicSimuEventType.BRANCH_FAULT) {
			// The fault branch will not participate in the calculation
			AcscBranchFault fault = e.getBranchFault();
			fault.getFaultBranch().setStatus(false);
		} else if (e.getType() == DynamicSimuEventType.BRANCH_OUTAGE) {
			// The fault branch will not participate in the calculation
			e.getBranchDynamicEvent().getBranch().setStatus(false);
		}
		else if(e.getType() == DynamicSimuEventType.GENERATION_TRIP) {
			GenerationTripEvent tripEvent = (GenerationTripEvent) e.getBusDeviceDynamicEvent();
			tripEvent.getBus().getContributeGen(tripEvent.getGenDeviceId()).setStatus(false);
			tripEvent.getDynamicBusDevice().setStatus(false);
			tripEvent.getBus().resetSeqEquivLoad();
		
		}
		
		else if(e.getType() == DynamicSimuEventType.GENERATION_ENERGIZATION) {
			
			
			GenerationEnergizationEvent connectEvent = (GenerationEnergizationEvent) e.getBusDeviceDynamicEvent();
			connectEvent.getBus().getContributeGen(connectEvent.getGenDeviceId()).setStatus(true);
			connectEvent.getDynamicBusDevice().setStatus(true);
			connectEvent.getDynamicBusDevice().initStates();
			
		}

		if (e.isActive()) {
			
			if (e.getType() == DynamicSimuEventType.BRANCH_RECLOSURE) {
				AcscBranchFault fault = e.getBranchFault();
				fault.getFaultBranch().setStatus(true);
			}
		}
	}
	
	
	// apply event after after building the Y-matrix
	@Override
	protected void applyDynamicEventAfter(DynamicSimuEvent e, double t) throws IpssNumericException {
		// active indicates applying the event. We only modify the Y matrix when
				// applying an event
				if (e.isActive()) {
					if (e.getType() == DynamicSimuEventType.BUS_FAULT) {
						AcscBusFault fault = e.getBusFault();
						BaseAcscBus bus = fault.getBus();
						
						int i = bus.getSortNumber();
						
						Complex ylarge =  NumericConstant.LargeBusZ;
						
						
						//Need to determine the yfaultABC based on the fault type
			            //TODO assuming the SLG fault is applied on phase A
					
						//net.getYMatrix().addToA(y, i, i);
						Complex3x3 yfaultABC = new Complex3x3();
						if(fault.getFaultCode()==SimpleFaultCode.GROUND_LG){
							Complex yfault = ylarge;
							if(fault.getZLGFault()!=null){
								if(fault.getZLGFault().abs()>1.0E-7)
								     yfault = new Complex(1.0,0).divide(fault.getZLGFault());
							}
							yfaultABC.aa = yfault ;
							net.getYMatrixABC().addToA(yfaultABC, i, i);
						}
						//TODO need to check how to model LL, phase B to C
						else if(fault.getFaultCode()==SimpleFaultCode.GROUND_LL){
							//Complex3x3 yii = net.getYMatrixABC().getA(i, i);
							
							// Based on Ifa = 0;
							//          Ifb = (VFb-VFc)/Zf
							//          Ifc = (-VFb+VFc)/Zf
							
							//Thus   Yfault = [0 0 0; 0 1, -1; 0 -1 1]/Zf
							
							Complex yfault = ylarge;
							if(fault.getZLLFault()!=null){
								if(fault.getZLLFault().abs()>1.0E-7)
								     yfault = new Complex(1.0,0).divide(fault.getZLLFault());
							}
							
							
							yfaultABC.bb = yfault;
							yfaultABC.bc = yfault.multiply(-1);
							yfaultABC.cb = yfault.multiply(-1);
							yfaultABC.cc = ylarge;
							
							
							net.getYMatrixABC().addToA(yfaultABC, i, i);
							
						}
	                    else if(fault.getFaultCode()==SimpleFaultCode.GROUND_LLG){
	                    	yfaultABC.aa = ylarge;
	                    	yfaultABC.bb = ylarge;
							net.getYMatrixABC().addToA(yfaultABC, i, i);
	                    	
						}
                        else if(fault.getFaultCode()==SimpleFaultCode.GROUND_3P){
                        	yfaultABC.aa = ylarge;
                        	yfaultABC.bb = ylarge;
                        	yfaultABC.cc = ylarge;
                        	
                        	net.getYMatrixABC().addToA(yfaultABC, i, i);
                        	
						}
						
					}
					/*
					 * Apply the branch fault to the Y-matrix
					 */
					else if (e.getType() == DynamicSimuEventType.BRANCH_FAULT) {
						 throw new UnsupportedOperationException();
					}
					
					
					else if (e.getType() == DynamicSimuEventType.LOAD_CHANGE) {
						// For load change, adjust the bus z using the new load value
						LoadChangeEvent eLoad = (LoadChangeEvent) e
								.getBusDynamicEvent();
						
						double factor = eLoad.getChangeFactor();
						DStab3PBus bus = (DStab3PBus) eLoad.getBus();
						if(factor < 0) {
							factor = Math.max(factor, -1.0-bus.getAccumulatedLoadChangeFactor());
						}
						
						if(Math.abs(factor)>1.0E-4) {
							
			
				    	    if(bus.getDynamicBusDeviceList()!=null && !bus.getDynamicBusDeviceList().isEmpty()){
				    		   for(DynamicBusDevice dynDevice: bus.getDynamicBusDeviceList()){
				    			   if(dynDevice instanceof DynLoadModel  && dynDevice.isActive()){
				    				  
				    				   ((DynLoadModel)dynDevice).changeLoad(factor);
				    				   
				    			   }
				    			   
				    			   if(dynDevice instanceof DynLoadModel3Phase  && dynDevice.isActive()){
					    				  
				    				   ((DynLoadModel3Phase)dynDevice).changeLoad(factor);
				    				   
				    			   }
				    		   }
				    	   }
				       
				
				        // process static loads, represented by netLoadResults. Need to change the system Ymatrix by updating Yii of the corresponding bus
				    	   
				    	   if(bus.getNetLoadResults().abs()>0){
				    		   double initVoltMag = bus.getInitVoltMag();
				    		   
				    		   Complex deltaPQ = bus.getNetLoadResults().multiply(factor);
				    		   Complex deltaLoadEquivY1 = deltaPQ.conjugate().divide(initVoltMag*initVoltMag);
				    		   
				    		   Complex deltaLoadEquivY2 =deltaLoadEquivY1; //assuming Y1 = Y2;
				    		   
				    		   Complex deltaLoadEquivY0 = new Complex(0.0); // assuming delta connection, ZO = inf, Y0 = 0;
				    		   
				    		   int sortNum = bus.getSortNumber();
				    		   
				    		   Complex3x3 deltaEquivYabc = Complex3x3.z12_to_abc( new Complex3x3(deltaLoadEquivY1,deltaLoadEquivY2,deltaLoadEquivY0));
				    		   
				    		   ((DStabNetwork3Phase)bus.getNetwork()).getYMatrixABC().addToA(deltaEquivYabc, sortNum, sortNum);	
				    		   //System.out.println("Change Ymatrix for load shedding");
				    	   }
				    	    
				    	    if(bus.get3PhaseNetLoadResults() != null && bus.get3PhaseNetLoadResults().abs() >0.0){
				    	    
				    	    	 int sortNum = bus.getSortNumber();
					   			 Complex3x1 initVoltABC = bus.get3PhaseInitVoltage();
					   			 double va = initVoltABC.a_0.abs();
					   			 double vb = initVoltABC.b_1.abs();
					   			 double vc = initVoltABC.c_2.abs();
					   			 
					   			 Complex ya = bus.get3PhaseNetLoadResults().a_0.conjugate().divide(va*va);
					   			 Complex yb = bus.get3PhaseNetLoadResults().b_1.conjugate().divide(vb*vb);
					   			 Complex yc = bus.get3PhaseNetLoadResults().c_2.conjugate().divide(vc*vc);
				   			 
				   			     ((DStabNetwork3Phase)bus.getNetwork()).getYMatrixABC().addToA(new Complex3x3(ya,yb,yc).multiply(factor), sortNum, sortNum);	
				   			    
				    	    }
			    	  
					   }
						
					}//end of load change
	 
			}// end of e.isActive()
	}

}
