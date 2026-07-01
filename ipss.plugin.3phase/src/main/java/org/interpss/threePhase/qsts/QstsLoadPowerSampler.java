package org.interpss.threePhase.qsts;

import org.interpss.numeric.datatype.Complex3x1;

import com.interpss.core.threephase.AclfLoad3Phase;
import com.interpss.core.threephase.IBus3Phase;

public final class QstsLoadPowerSampler {
	private QstsLoadPowerSampler() {
	}

	public static Complex3x1 solvedPower(AclfLoad3Phase load, IBus3Phase bus) {
		if(load == null) {
			return zero();
		}
		Complex3x1 voltage = bus == null ? null : bus.get3PhaseVotlages();
		Complex3x1 power = voltage == null ? load.getInit3PhaseLoad() : load.get3PhaseLoad(voltage);
		return power == null ? zero() : copy(power);
	}

	private static Complex3x1 copy(Complex3x1 value) {
		return new Complex3x1(value.a_0, value.b_1, value.c_2);
	}

	private static Complex3x1 zero() {
		return new Complex3x1();
	}
}
