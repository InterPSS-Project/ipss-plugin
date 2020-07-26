package org.interpss.threePhase.basic.acsc;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;

import com.interpss.core.abc.IBranch3Phase;
import com.interpss.core.abc.IXformer3Phase;
import com.interpss.core.acsc.adpter.AcscXformer;

public interface Acsc3PXformer extends IXformer3Phase, AcscXformer{
	
	    public void set3PBranch(IBranch3Phase ph3Branch);
	    
	    public void setZabc(Complex3x3 Zabc);
	    
	    public void setZabc(Complex Za, Complex Zb, Complex Zc);
		
		public Complex3x3 getZabc() ;
		
		public Complex3x3 getYabc() ;
		
		public Complex3x3 getYffabc();
		
		public Complex3x3 getYttabc();
	    
        public Complex3x3 getYftabc();
		
		public Complex3x3 getYtfabc();
		
		/**
		 * The mapping matrix relating Vabc of to bus to the Vabc of from bus
		 * @return
		 */
		Complex3x3 getLVBusVabc2HVBusVabcMatrix();
		
		/**
		 * The mapping matrix relating current flowing into LVBus to the Vabc of HVBus
		 * @return
		 */
		Complex3x3 getLVBusIabc2HVBusVabcMatrix();
		
		/**
		 * The mapping matrix relating relating Vabc of LVBus to the current (Iabc) into HVBus
		 * @return
		 */
		Complex3x3 getLVBusVabc2HVBusIabcMatrix();
		
		/**
		 * The mapping matrix relating Iabc of LVBus to the current (Iabc) into HVBus
		 * @return
		 */
		Complex3x3 getLVBusIabc2HVBusIabcMatrix();
		
		/**
		 * The mapping matrix relating  Vabc of HVBus to the Vabc of LVBus
		 * @return
		 */
		Complex3x3 getHVBusVabc2LVBusVabcMatrix();
		
		/**
		 * The mapping matrix relating  Iabc of LVBus to the Vabc of LVBus
		 * @return
		 */
		Complex3x3 getLVBusIabc2LVBusVabcMatrix();

	
	

}
