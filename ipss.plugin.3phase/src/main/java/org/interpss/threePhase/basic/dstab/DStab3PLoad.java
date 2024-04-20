package org.interpss.threePhase.basic.dstab;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;

import com.interpss.core.abc.ILoad3Phase;
import com.interpss.core.abc.LoadConnectionType;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.dstab.DStabLoad;

public interface DStab3PLoad extends ILoad3Phase, DStabLoad {
	
	public void setPhaseCode (PhaseCode phCode);
	
	public PhaseCode getPhaseCode ();
	/**
	 * calcuate the Yabc from Y120
	 * @param y1  equivalent positive sequence admittance of the load which is not represented by dynamic load
	 * @param y2  equivalent negative sequence admittance of the load which is not represented by dynamic load
	 * @param y0  equivalent zero sequence admittance of the load which is not represented by dynamic load. This is zero by default
	 */
	public void initEquivYabc(Complex y1, Complex y2, Complex y0);
	/**
	 * get three phase load equivalent 3x3 admittance matrix in pu
	 * @return
	 */
	public Complex3x3 getEquivYabc();
	
	/**
	 * get the initial load at nominal voltage level
	 * @return
	 */
	public Complex3x1 getInit3PhaseLoad();
	
	/**
	 *  get three phase loads in pu
	 * @return
	 */
	public Complex3x1  get3PhaseLoad(Complex3x1 vabc);
	
	
	/**
	 * 
	 * @param threePhaseLoad  three phase loads in pu
	 * @return
	 */
	public void  set3PhaseLoad(Complex3x1 threePhaseLoad);
	
	
	/**
	 * 
	 * @param phase
	 * @return
	 */
	public Complex   getPhaseLoad(PhaseCode phase);
	
	public void  setPhaseLoad(Complex phaseLoad, PhaseCode phase);
	
	/**
	 * calculate the equivalent current injection of the load, used in power flow solution
	 * @param vabc
	 * @return
	 */
	public Complex3x1 getEquivCurrInj(Complex3x1 vabc);
	
	public LoadConnectionType getLoadConnectionType();
	
	public void setLoadConnectionType(LoadConnectionType threePhaseWye);
	
	public Complex getInit3PhaseTotalLoad(); 
	
	/**
	 * set the nominal KV level of the load
	 * @param ratedkV
	 */
	public void setNominalKV(double ratedkV);
	
	/**
	 * return the nominal KV level of the load
	 * @return
	 */
	public double getNominalKV();

}
