package org.interpss.threePhase.basic;

import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;

import com.interpss.core.abc.LoadConnectionType;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.dstab.DStabLoad;

public interface Load1Phase extends DStabLoad {
	
	public void setPhaseCode (PhaseCode phCode);
	
	public PhaseCode getPhaseCode ();
	
	/**
	 * The Norton equivalent admittance. Used in power flow and short circuit analysis. 
	 * @return 
	 */
	public Complex3x3 getEquivYabc();
	
	/**
	 * calculate the equivalent current injection of the load, mainly used in power flow solution.
	 * @param vabc
	 * @return
	 */
	public Complex3x1 getEquivCurrInj(Complex3x1 vabc);
	
	/**
	 * set the load model type, such as constant PQ, constant current, constant impedance, etc.
	 * @param loadModelType
	 */
//	public void setLoadModelType(DistLoadType loadModelType);
//	
//	/**
//	 * get the load model type, such as constant PQ, constant current, constant impedance, etc.
//	 * @return
//	 */
//	public DistLoadType getLoadModelType();
	
	/**
	 * set the load connection type. The connection type could be single phase wye, single phase delta, three phase wye and three phase delta.
	 * @param loadConnectType
	 */
	public void setLoadConnectionType(LoadConnectionType loadConnectType);
	
	/**
	 * get the load connection type. The connection type could be single phase wye, single phase delta, three phase wye and three phase delta.
	 * @return
	 */
	public LoadConnectionType getLoadConnectionType();
	
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
	
	/**
	 *  Set the minimum voltage in pu, above which the original input load model (e.g., constant power) can be maintained,
	 *  Once the terminal voltage is lower than this value, the load will be represented by constant impedance
	 */
	public void setVminpu(double newVminpu);
	
	/**
	 *  Set the maximum voltage in pu to maintain the original input load model (e.g., constant power) 
	 */
	public void setVmaxpu(double newVmaxpu);
	
	/**
	 *  get the minimum voltage in pu to maintain the original input load model (e.g., constant power) 
	 *  If the voltage is above this value, the original input load model (e.g., constant power) can be maintained.
	 *  Once the terminal voltage is lower than this value, the load will be represented by constant impedance
	 */
	public double getVminpu();
	
	public double getVmaxpu();

}
