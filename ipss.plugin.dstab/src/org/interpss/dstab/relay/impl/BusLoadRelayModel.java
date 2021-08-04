package org.interpss.dstab.relay.impl;

import java.util.ArrayList;
import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.dynLoad.DynLoadModel;

public class BusLoadRelayModel extends BusRelayModel {


	protected boolean isAllBusLoad = false;

	protected String loadId = "";
	
	protected Complex initLoadPQ = null;

	
	public BusLoadRelayModel() {
		
	}
	
	public BusLoadRelayModel(BaseDStabBus bus, String loadId){
        this.setDStabBus(bus);
		
		this.relaySetPoints = new ArrayList<>();
		this.relaySectionActionStatus = new ArrayList<>();
		this.relayTrippedStatus = new ArrayList<>();
		
		this.loadId = loadId;
		
		//check the loadId, use PSS/E naming convention
		if(loadId.equals("*")){
			this.isAllBusLoad = true;
		}
		
		bus.getDynamicBusDeviceList().add(this);
	}
	
	@Override
	public boolean action(double time) {
		if(isActionTime(time)){ 
		    applyLoadSheddingAction();
		    IpssLogger.getLogger().info(String.format("UVLS load relay %s is activated at time = %f ",this.extendedDeviceId, time));
		    this.isRelayTripped = true;
		    
		    return true;
		}
		return false;
	}
	
	
	@Override
	public boolean initStates(BaseDStabBus<?,?> abus){
		
		// check the relaySetPoints 
		if (this.relaySetPoints.size()==0){
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
			this.relayTrippedStatus.add(false);
		}
		
		this.internalTimer = 0.0;
		
		return true;
	}
	
	
	
	
	public Hashtable<String, Object> getStates(Object ref) {
		states.put(OUT_SYMBOL_STATUS, this.isRelayTripped);
		return states;
	}
	
	protected void applyLoadSheddingAction() {
		
			double maxTripFraction = 0.0;
			for(int i = 0; i<this.relaySetPoints.size();i++){
				if(this.relaySectionActionStatus.get(i) && !this.relayTrippedStatus.get(i)){ // check the trip action status
					// Triplet <voltage, time, fraction>
					if(this.relaySetPoints.get(i).getValue3()>maxTripFraction){
						maxTripFraction = this.relaySetPoints.get(i).getValue3();
						this.relayTrippedStatus.set(i,true);
					}
				
				}
			}
			
			this.trippedFraction = maxTripFraction;
			
			this.getDStabBus().setAccumulatedLoadChangeFactor(this.getDStabBus().getAccumulatedLoadChangeFactor()-maxTripFraction);
			
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
		    	   
		    	   if(this.getDStabBus().getNetLoadResults().abs()>0){
		    		   double initVoltMag = this.getDStabBus().getInitVoltMag();
		    		   
		    		
		    		   Complex deltaPQ = this.getDStabBus().getNetLoadResults().multiply(-maxTripFraction); // add "minus" for load shedding;
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
