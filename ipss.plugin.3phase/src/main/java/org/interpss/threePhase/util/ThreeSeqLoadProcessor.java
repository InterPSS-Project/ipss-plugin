package org.interpss.threePhase.util;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;

import com.interpss.core.acsc.SequenceCode;
import com.interpss.dstab.BaseDStabBus;

public class ThreeSeqLoadProcessor {

	public static Complex3x3 getEquivLoadYabc(BaseDStabBus<?,?> bus){

		Complex loadEquivY1 = admittance(bus.getEquivZ1());
		Complex loadEquivY2 = admittance(bus.getEquivZ2());
		Complex loadEquivY0 = admittance(bus.getEquivZ0());

		return Complex3x3.z12_to_abc( new Complex3x3(loadEquivY1,loadEquivY2,loadEquivY0));
	}

	private static Complex admittance(Complex impedance) {
		if (impedance == null || impedance.abs() == 0.0 || impedance.isNaN() || impedance.isInfinite()) {
			return Complex.ZERO;
		}

		Complex y = new Complex(1.0,0).divide(impedance);
		return y.isNaN() || y.isInfinite() ? Complex.ZERO : y;
	}

	public static void initEquivLoadY120(BaseDStabBus<?,?> bus){
	    bus.initSeqEquivLoad(SequenceCode.POSITIVE);
	    bus.initSeqEquivLoad(SequenceCode.NEGATIVE);
	    bus.initSeqEquivLoad(SequenceCode.ZERO);

	}

}
