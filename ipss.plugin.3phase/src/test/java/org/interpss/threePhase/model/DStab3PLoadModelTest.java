package org.interpss.threePhase.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.junit.jupiter.api.Test;

import com.interpss.core.threephase.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;

public class DStab3PLoadModelTest {

	@Test
	public void threePhaseDeltaLoadProducesBalancedLineCurrents() {
		DStab3PLoad load = new DStab3PLoadImpl();
		load.setCode(AclfLoadCode.CONST_P);
		load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_DELTA);
		load.set3PhaseLoad(new Complex3x1(new Complex(1.0, 0.0),
				new Complex(1.0, 0.0), new Complex(1.0, 0.0)));

		Complex3x1 current = load.getEquivCurrInj(new Complex3x1(
				new Complex(1.0, 0.0),
				new Complex(-0.5, -Math.sqrt(3.0) / 2.0),
				new Complex(-0.5, Math.sqrt(3.0) / 2.0)));

		assertEquals(1.0, current.a_0.abs(), 1.0e-12);
		assertEquals(1.0, current.b_1.abs(), 1.0e-12);
		assertEquals(1.0, current.c_2.abs(), 1.0e-12);
		assertEquals(0.0, current.a_0.add(current.b_1).add(current.c_2).abs(), 1.0e-12);
	}
}
