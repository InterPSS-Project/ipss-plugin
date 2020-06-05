package org.interpss.threePhase.basic;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;

import com.interpss.core.acsc.AcscLoad;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.dstab.DStabLoad;

public interface Load3Phase extends DStabLoad {
	
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
	 *  get three phase loads in pu
	 * @return
	 */
	public Complex3x1  get3PhaseLoad();
	
	
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

}
