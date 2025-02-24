package org.interpss.dstab.dynLoad;


import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.DynamicBusDevice;

/**
 * A representation of the model object '<em><b>DER_A</b></em>' in positive sequence.
 */
public interface DER_A_PosSeq extends DynamicBusDevice {
	
	public boolean initStates(BaseDStabBus<?,?> bus);
	
	public boolean calcIpIq(int flag);
	
	public boolean updateInternalVoltage();
	
	public boolean calcNortonEquivalent();
	
	public void freqTripLogic();
	
	public void voltageTripLogic();
	
	public double firstOrderTF(double inputVal, double previousOutput, double time_const, double deltaTime);

	public double finddYdX(double input, double currOut, double time_const);
	
	public double deadband(double inputVal, double deadband_low, double deadband_high);
	
	public double rate_limiter(double inputVal, double previousOutput, double dmax, double dmin, double deltaTime);
	
	public void updateIdIqMax();
	
	@Override
	public Hashtable<String, Object> getStates(Object ref);
	
	public void enableVoltVarControl();
	
	public void disableVoltVarControl();
	
	public void enablePowerFreqControl();
	
	public void disablePowerFreqControl();
	
	@Override 
 	public Object getOutputObject();
	
	@Override
	public boolean nextStep(double dt, DynamicSimuMethod method, int flag);
	
	@Override
    public boolean updateAttributes(boolean netChange);
	
	public void update_rrpwr(double Ip);
	
	public void incrementCurrSimuTime(double deltaTime);
	
	public Complex getPosSeqIpq();
	
	public Complex  getPosSeqNortonCurSource();
	
	public void setPosSeqNortonCurSource(Complex Igen);
	
	public void setNortonCurSource(Complex Igen);
	
	public Complex getNortonCurSource();
	
	public void setPosSeqIpq(Complex ipq_pos);
	
	public Complex getPosSeqVt();
	
	public double getCurrSimuTime();
	
	public void setPosSeqGenPQ(Complex genPQ);
	
	public Complex getPosSeqGenPQ();
	
	public void setPowerFactor(double pf);
	
	public void setDebugMode(boolean set);
	
	public void setPQFlag(int flag);
	
	public void setGenToDebug(String busName);
	
	public double getPowerFactor();
	
	public Complex getInitGenPQ();
	
	public void setXe(double Xe);
	
	public void setVoltageRecoveryFrac(double frac);
	
	public void writeToCSV(boolean set);
} 
