package org.interpss.dstab.relay.impl;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.relay.IBusRelayModel;
import org.interpss.numeric.datatype.Triplet;

import com.interpss.common.util.IpssLogger;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

public abstract class BusRelayModel extends DynamicBusDeviceImpl implements IBusRelayModel {


	protected static final String OUT_SYMBOL_STATUS = "RelayStatus";
	
	// Triplet <voltage, time, fraction>
	protected List<Triplet> relaySetPoints = null;
	protected List<Boolean> relaySectionActionStatus = null;
	protected List<Boolean> relayTrippedStatus= null;
	protected List<Integer> underOverFlagList = null; // 0 for under, 1 for over
	
	protected double[] timerAry = null;
	
	protected boolean isAllBusLoad = false;
	protected boolean isRelayTripped = false;
	
	
	protected Hashtable<String, Object> states = null;
	
	protected double breakerTime = 0.0;
	protected double trippedFraction = 0.0;
	
	protected double timeStep = 0.0;
	protected double internalTimer = 0.0;
	
	protected int step_flag = 0;
	
	public BusRelayModel() {
		
	}
	public BusRelayModel(BaseDStabBus bus){
		this.setDStabBus(bus);
	
		this.relaySetPoints = new ArrayList<>();
		this.relaySectionActionStatus = new ArrayList<>();
		this.relayTrippedStatus = new ArrayList<>();
		this.underOverFlagList = new ArrayList<>();
		
		//add it to the associated bus dynamic device list
		bus.getDynamicBusDeviceList().add(this);
		
	}
	@Override
	public List<Triplet> getRelaySetPoints() {
		
		return this.relaySetPoints;
	}

	@Override
	public void setRelaySetPoints(List<Triplet> setPointList) {
		this.relaySetPoints = setPointList;
		
	}
	
	
	
	/**
	 * only reset the activated section, while keeping the timer of other sections.
	 */
	@Override
	public boolean reset() {
		
		//initialize the timers
		this.timerAry = new double[this.relaySetPoints.size()];
		for(int i = 0; i<this.relaySetPoints.size();i++){
			
			if(this.relaySectionActionStatus.get(i)) {
				timerAry[i] = 0.0;
				this.relaySectionActionStatus.set(i,false);
			}
		}
		return true;
	}
	
	
	
	@Override
	public boolean isActionTime(double time) {
		
		boolean isActionNow = false;
		//System.out.println("check isActionTime at "+time);
		// Triplet <voltage, time, fraction>, use under threshold (frequency/voltage) as default implementation 
		for(int i = 0; i<this.relaySetPoints.size();i++){
			if(!this.relayTrippedStatus.get(i) && relaySetPoints.get(i).getValue2()<timerAry[i]){
			
				this.relaySectionActionStatus.set(i, true);
				isActionNow = true;
			}
		}
		
		
		return isActionNow;
	}
	
	@Override
	public boolean initStates(BaseDStabBus<?,?> abus){
		
		
		// check the relaySetPoints 
		if (this.relaySetPoints == null || this.relaySetPoints.isEmpty()){
			IpssLogger.getLogger().severe("No relay SetPoint is defined");
			return false;
		}
	
	
		//initialize the timers
		this.timerAry = new double[this.relaySetPoints.size()];
		for(int i = 0; i<this.relaySetPoints.size();i++){
			timerAry[i] = 0.0;
			this.relaySectionActionStatus.add(false);
		}
		
		this.internalTimer = 0.0;
		
		return true;
	}
	
	@Override
	public boolean nextStep(double dt, DynamicSimuMethod method, int flag){
		this.timeStep = dt;
		this.step_flag = flag;
		if(method==DynamicSimuMethod.MODIFIED_EULER && flag==1) {
			this.internalTimer += dt;
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
		if(this.step_flag ==1) {
			if(action(getInternalTime())) {
				reset();
			}
		}
		return true;
	}
	
	public Hashtable<String, Object> getStates(Object ref) {
		states.put(OUT_SYMBOL_STATUS, this.isRelayTripped);
		return states;
	}
	
	@Override
	public double getTrippedFraction() {
		return this.trippedFraction;
	}
	
	@Override
	public double getBreakerTime() {
		
		return this.breakerTime;
	}
	@Override
	public void setBreakerTime(double TBreaker) {
		this.breakerTime = TBreaker;
		
	}
	
	@Override
	public double getInternalTime() {
		return this.internalTimer;
	}
	
	@Override
	public List<Boolean> isRelayTripped(){
		return this.relayTrippedStatus;
	}
	
	@Override
	public List<Integer> getUnderOverFlagList(){
		return this.underOverFlagList;
	}
}
