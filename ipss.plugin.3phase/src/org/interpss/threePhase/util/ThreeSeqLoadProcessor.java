package org.interpss.threePhase.util;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.Bus3Phase;

import com.interpss.core.acsc.SequenceCode;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabBus;

public class ThreeSeqLoadProcessor {
	
	public static Complex3x3 getEquivLoadYabc(BaseDStabBus<?,?> bus){

		Complex loadEquivY1 =  new Complex(1.0,0).divide(bus.getEquivZ1());
		Complex loadEquivY2 =  new Complex(1.0,0).divide(bus.getEquivZ2());
		Complex loadEquivY0 =  new Complex(1.0,0).divide(bus.getEquivZ0());

		return Complex3x3.z12_to_abc( new Complex3x3(loadEquivY1,loadEquivY2,loadEquivY0));
	}
	
	public static void initEquivLoadY120(BaseDStabBus<?,?> bus){
	    bus.initSeqEquivLoad(SequenceCode.POSITIVE);
	    bus.initSeqEquivLoad(SequenceCode.NEGATIVE);
	    bus.initSeqEquivLoad(SequenceCode.ZERO);

	}

}
