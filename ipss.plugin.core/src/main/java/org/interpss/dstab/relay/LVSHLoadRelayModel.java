package org.interpss.dstab.relay;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Triplet;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.dstab.dynLoad.DynLoadModel;

public class LVSHLoadRelayModel extends DynamicBusDeviceImpl implements BusRelayModel {

	private static final String OUT_SYMBOL_STATUS = "RelayStatus";
	// Triplet <voltage, time, fraction>
	protected List<Triplet> relaySetPoints = null;
	protected List<Boolean> relaySectionActionStatus = null;
	protected double[] timerAry = null;
	
	protected boolean isAllBusLoad = false;
	protected boolean isRelayTripped = false;
	
	protected String loadId = "";
	
	private Complex initLoadPQ = null;
	
	private String extended_id = "";
	
	private Hashtable<String, Object> states = null;
	
	

	
	public LVSHLoadRelayModel(DStabBus bus, String loadId){
		this.setDStabBus(bus);
		this.loadId = loadId;
		
		this.relaySetPoints = new ArrayList<>();
		this.relaySectionActionStatus = new ArrayList<>();
		
		//check the loadId, use PSS/E naming convention
		if(loadId.equals("*")){
			this.isAllBusLoad = true;
		}
		
		//add it to the associated bus dynamic device list
		bus.getDynamicBusDeviceList().add(this);
		
		this.extended_id = "LVSHRelay_"+bus.getId()+"_"+loadId;
		
		this.states = new Hashtable<>();
		
		this.states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, this.extended_id);
	}
	@Override
	public List<Triplet> getRelaySetPoints() {
		
		return this.relaySetPoints;
	}

	@Override
	public void setRelaySetPoints(List<Triplet> setPointList) {
		this.relaySetPoints = setPointList;
		
		
	}

	@Override
	public boolean action(double time) {
		if(isActionTime(time)){ 
		    applyLoadSheddingAction();
		    IpssLogger.getLogger().info("LVSH load relay at time = "+time);
		    this.isRelayTripped = false;
		}
		return true;
	}
	
	@Override
	public boolean reset() {
		
		//initialize the timers
		this.timerAry = new double[this.relaySetPoints.size()];
		for(int i = 0; i<this.relaySetPoints.size();i++){
			timerAry[i] = 0.0;
			this.relaySectionActionStatus.set(i, false);
		}
		return true;
	}
	
	
	
	@Override
	public boolean isActionTime(double time) {
		// Triplet <voltage, time, fraction>
		for(int i = 0; i<this.relaySetPoints.size();i++){
			if(this.relaySetPoints.get(i).getValue2()<timerAry[i]){
				this.isRelayTripped = true;
				this.relaySectionActionStatus.set(i, true);
				
			}
		}
		
		return this.isRelayTripped;
	}
	
	@Override
	public boolean initStates(BaseDStabBus<?,?> abus){
		
		// check the relaySetPoints 
		if (this.relaySetPoints == null || this.relaySetPoints.isEmpty()){
			IpssLogger.getLogger().severe("No relay SetPoint is defined");
			return false;
		}
		
		// get the initLoadPQ
		this.initLoadPQ = new Complex(0,0);
		double vmag = this.getDStabBus().getVoltageMag();
		if(this.getDStabBus().getContributeLoadList().size()>0){
			if(this.isAllBusLoad){
				for(AclfLoad load: this.getDStabBus().getContributeLoadList()){
					this.initLoadPQ = this.initLoadPQ.add(load.getLoad(vmag));
				}
			}
			else{
				for(AclfLoad load: this.getDStabBus().getContributeLoadList()){
					if(load.getId().equals(this.loadId))
					     this.initLoadPQ = load.getLoad(vmag);
				}
			}
		}
	
		
		
		//initialize the timers
		this.timerAry = new double[this.relaySetPoints.size()];
		for(int i = 0; i<this.relaySetPoints.size();i++){
			timerAry[i] = 0.0;
			this.relaySectionActionStatus.add(false);
		}
		
		return true;
	}
	
	@Override
	public boolean nextStep(double dt, DynamicSimuMethod method, int flag){
		
	    double vmag = this.getDStabBus().getVoltage().abs();
	    
		for(int i = 0; i<this.relaySetPoints.size();i++){
			
			// Triplet <voltage, time, fraction>
			if(this.relaySetPoints.get(i).getValue1()<vmag){
				//reset timer
				timerAry[i] = 0.0;
			}
			else{
				//update the timer
				timerAry[i] = timerAry[i] +dt;
			}
		}
		
	
		return true;
	}
	
	// no contribution to the bus
	@Override
	public Object getOutputObject() {
		
		return new Complex(0);
		
	}
	
	@Override
	public boolean updateAttributes(boolean netChange) {
		return true;
	}
	
	public Hashtable<String, Object> getStates(Object ref) {
		states.put(OUT_SYMBOL_STATUS, this.isRelayTripped);
		return states;
	}
	
	private void applyLoadSheddingAction() {
		
			double maxTripFraction = 0.0;
			for(int i = 0; i<this.relaySetPoints.size();i++){
				if(this.relaySectionActionStatus.get(i)){ // check the trip action status
					// Triplet <voltage, time, fraction>
					if(this.relaySetPoints.get(i).getValue3()>maxTripFraction){
						maxTripFraction = this.relaySetPoints.get(i).getValue3();
					}
					//reset the timer
					this.timerAry[i] = 0.0;
				}
			}
			
			if(this.isAllBusLoad){
				 // check if there is any dynamic load model
		    	   if(this.getDStabBus().getDynLoadModelList()!=null && !this.getDStabBus().getDynLoadModelList().isEmpty()){
		    		   for(DynLoadModel dynLoad: this.getDStabBus().getDynLoadModelList()){
		    			   if(dynLoad.isActive()){
		    				  
		    				   dynLoad.changeLoad(-maxTripFraction);// add "minus" for load shedding;
		    				   
		    				   
		    			   }
		    		   }
		    	   }
		       
		
		        // process static loads, represented by netLoadResults. Need to change the system Ymatrix by updating Yii of the corresponding bus
		    	   
		    	   if(this.getDStabBus().calNetLoadResults().abs()>0){
		    		   double initVoltMag = this.getDStabBus().getInitVoltMag();
		    		   
		    		
		    		   Complex deltaPQ = this.getDStabBus().calNetLoadResults().multiply(-maxTripFraction); // add "minus" for load shedding;
		    		   Complex deltaYii = deltaPQ.conjugate().divide(initVoltMag*initVoltMag);
		    		   
		    		   int sortNum = this.getDStabBus().getSortNumber();
		    		   
		    		   DStabilityNetwork net = (DStabilityNetwork) this.getDStabBus().getNetwork();
		    		   
		    		   net.getYMatrix().addToA(deltaYii, sortNum, sortNum);
		    		   net.setYMatrixDirty(true);
		    	   }
		    	  
		    }// consider individual load model
			else{
				 // check if there is any dynamic load model
				   boolean isDynLoad = false;
		    	   if(this.getDStabBus().getDynLoadModelList()!=null && !this.getDStabBus().getDynLoadModelList().isEmpty()){
		    		   for(DynLoadModel dynLoad:this.getDStabBus().getDynLoadModelList()){
		    			   if(dynLoad.getId().equals(this.loadId))
		    				   isDynLoad= true;
		    			   
		    			       dynLoad.changeLoad(-maxTripFraction);// add "minus" for load shedding;
	    				   
		    			   }
		    	   }
		    	   
		    	   if(!isDynLoad){
		    		   
		    		   
		    		   double initVoltMag = this.getDStabBus().getInitVoltMag();
		    		   
		    		   Complex deltaPQ = this.initLoadPQ.multiply(-maxTripFraction);
		    		   Complex deltaYii = deltaPQ.conjugate().divide(initVoltMag*initVoltMag);
		    		   
		    		   int sortNum = this.getDStabBus().getSortNumber();
		    		   
		    		   DStabilityNetwork net = (DStabilityNetwork) this.getDStabBus().getNetwork();
		    		   
		    		   net.getYMatrix().addToA(deltaYii, sortNum, sortNum);
		    		   net.setYMatrixDirty(true);
		    	   }
		    	  
		    		   
		}
		
	}

	

	
}
