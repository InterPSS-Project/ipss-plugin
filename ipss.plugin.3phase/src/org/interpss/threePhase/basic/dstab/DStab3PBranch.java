package org.interpss.threePhase.basic.dstab;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.static3P.Static3PXformer;

import com.interpss.core.abc.IBranch3Phase;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.dstab.DStabBranch;

public interface DStab3PBranch extends IBranch3Phase, DStabBranch{
	
	// phase code is currently used in the transformer; for lines, Zabc reflects the connection phases
	public void setPhaseCode(PhaseCode phCode);

	public PhaseCode getPhaseCode();
	
    //public void setZabc(Complex3x3 Zabc);
    
    //public void setZabc(Complex Z1, Complex Z2, Complex Z0);
	
	//public Complex3x3 getZabc() ;
	
	//public Complex3x3 getBranchYabc() ;
	
	public Complex3x3 getFromShuntYabc();
	public Complex3x3 getToShuntYabc();
	
	public void setFromShuntYabc( Complex3x3 fYabc) ;
	
	public void setToShuntYabc( Complex3x3 tYabc) ;
	

	//public Complex3x3 getYffabc();
	
	//public Complex3x3 getYttabc();
	
	//public Complex3x3 getYftabc();
	
	//public Complex3x3 getYtfabc();
	
	/**
	 *  Return the current flowing from the "from" bus to the "to" bus, measured at the from bus side
	 */
	public Complex3x1 getCurrentAbcAtFromSide();
	
	public void setCurrentAbcAtFromSide(Complex3x1 IabcFromBus);
	
	/**
	 * Return the current flowing into the "to" bus
	 * Note: the branch current direction is defined with "from->to" as positive at both ends
	 * @return
	 */
	public Complex3x1 getCurrentAbcAtToSide();
	
	/**
	 * Note: the branch current direction is defined with "from->to" as positive at both ends
	 * @param IabcToBus
	 */
	public void setCurrentAbcAtToSide(Complex3x1 IabcToBus);
	
	public Static3PXformer to3PXformer();
	
	/**
	 * The mapping matrix relating Vabc of to bus to the Vabc of from bus
	 * @return
	 */
	Complex3x3 getToBusVabc2FromBusVabcMatrix();
	
	/**
	 * The mapping matrix relating current flowing into ToBus to the Vabc of frombus
	 * @return
	 */
	Complex3x3 getToBusIabc2FromBusVabcMatrix();
	
	/**
	 * The mapping matrix relating relating Vabc of ToBus to the current (Iabc) into fromBus
	 * @return
	 */
	Complex3x3 getToBusVabc2FromBusIabcMatrix();
	
	/**
	 * The mapping matrix relating Iabc of ToBus to the current (Iabc) into fromBus
	 * @return
	 */
	Complex3x3 getToBusIabc2FromBusIabcMatrix();
	
	/**
	 * The mapping matrix relating  Vabc of fromBus to the Vabc of toBus
	 * @return
	 */
	Complex3x3 getFromBusVabc2ToBusVabcMatrix();
	
	/**
	 * The mapping matrix relating  Iabc of Tobus to the Vabc of toBus
	 * @return
	 */
	Complex3x3 getToBusIabc2ToBusVabcMatrix();


	public void setXfrRatedKVA(double kva1);
	
	public double getXfrRatedKVA();

}
