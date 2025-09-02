package org.interpss.multiNet.algo;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.common.msg.IpssMessage;
import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.sc.ScBusModelType;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.defaultImpl.DynamicEventProcessor;
import com.interpss.dstab.datatype.DStabSimuTimeEvent;
import com.interpss.dstab.devent.DynamicSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiNetDynamicEventProcessor extends DynamicEventProcessor {
    private static final Logger log = LoggerFactory.getLogger(MultiNetDynamicEventProcessor.class);

	BaseDStabNetwork<?, ?> net = null;
	protected AbstractMultiNetDStabSimuHelper simuHelper = null;
	
	
	public MultiNetDynamicEventProcessor(AbstractMultiNetDStabSimuHelper mNetSimuHelper){
		this.simuHelper = mNetSimuHelper;
	}
	
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
				
				this.net = dEventMsg.getDStabNetData();
				
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
					
					String  faultSubNetworkId = null;
					for (DynamicSimuEvent dEvent : net.getDynamicEventList()) {
						   
					        if (dEvent.getType() == DynamicSimuEventType.BUS_FAULT) {
						         AcscBusFault fault = dEvent.getBusFault();
						         BaseAcscBus bus = fault.getBus();
						         DStabilityNetwork faultSubNet = (DStabilityNetwork) bus.getNetwork();
						         ISparseEqnComplex ymatrix = faultSubNet.formScYMatrix(SequenceCode.POSITIVE, ScBusModelType.LOADFLOW_VOLT, false);
						         //TODO don't forget to set the ymatrix
						         faultSubNet.setYMatrix(ymatrix);
						         faultSubNet.setYMatrixDirty(true);
						        // System.out.println("Time :"+t+", ymatrix of network is rebuilted:"+faultSubNet.getId());
						        // System.out.println("ymatrix = \n"+ymatrix);
						         
						         faultSubNetworkId =faultSubNet.getId();
						  
					         }
					}
					        


					
					
					log.debug("Reset Ymatrix for event applying");

					// apply those events which result in adding z to Y-matrix,
					// such as applying fault Z
					for (DynamicSimuEvent dEvent : net.getDynamicEventList()) {
						if (dEvent.hasEvent()) {
							try{
								applyDynamicEventAfter(dEvent, t);
							} catch (IpssNumericException e) {
								log.error(e.toString());
								return false;
							}
						}
					}
					
					
					  // update the Thevenin equivalent impedance matrix Zth of all subNetwork
			         this.simuHelper.updateSubNetworkEquivMatrix(faultSubNetworkId);
					
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
		//reuse the DynamicEventProcessor implementation
		super.applyDynamicEventBefore(e, t);
		
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
						
						Complex z = NumericConstant.SmallScZ;
						
						Complex ylarge = ComplexFunc.div(1.0, z);
						
						if(fault.getFaultCode()==SimpleFaultCode.GROUND_LG){
							Complex ZfaultEquiv = fault.getZLGFault();
							((BaseDStabNetwork) bus.getNetwork()).getYMatrix().addToA(ZfaultEquiv, i, i);
						}
						
						else if(fault.getFaultCode()==SimpleFaultCode.GROUND_LL){
					
							throw new UnsupportedOperationException();
						}
	                    else if(fault.getFaultCode()==SimpleFaultCode.GROUND_LLG){
	                    
	                    	throw new UnsupportedOperationException();
						}
                        else if(fault.getFaultCode()==SimpleFaultCode.GROUND_3P){
      
                        	((BaseDStabNetwork) bus.getNetwork()).getYMatrix().addToA(ylarge, i, i);
                        	
                        	
						}
						
						//update the 
						
					}
					/*
					 * Apply the branch fault to the Y-matrix
					 */
					else if (e.getType() == DynamicSimuEventType.BRANCH_FAULT) {
						 throw new UnsupportedOperationException();
					}
					 
					}
	}

}