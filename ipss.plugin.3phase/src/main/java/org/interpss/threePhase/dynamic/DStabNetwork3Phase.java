package org.interpss.threePhase.dynamic;

import java.util.Hashtable;

import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;

import com.interpss.core.abc.INetwork3Phase;
import com.interpss.dstab.BaseDStabNetwork;

public interface DStabNetwork3Phase extends INetwork3Phase, BaseDStabNetwork<DStab3PBus, DStab3PBranch>{
	

	
	/**
	 * initialize the three-phase DStab network based on the positive sequence power flow result, 
	 * assuming the three-phase are well-balanced. Specially, the phase shift involved in 
	 * transformers modeled in three-phase  while neglected by the positive sequence modeling has been considered. 
	 * @return true if there is no error
	 */
	public boolean initThreePhaseFromLfResult();
	
	
	/**
	 * This function is to enable the three-phase network object to 
	 * solve the positive sequence network; this requires the three-phase
	 * network object is created with three-sequence data. 
	 * @return
	 */
	public boolean solvePosSeqNetEqn();
	
	public boolean initPosSeqDStabNet();
	
	public Hashtable<String, Complex3x1> get3phaseCustomCurrInjTable();
	
	public void set3phaseCustomCurrInjTable(Hashtable<String, Complex3x1> new3PhaseCurInjTable);
	
	public void setLoadModelConverted(boolean status);
	
	public boolean isLoadModelConverted();


}
