package org.interpss.threePhase.opf.dist.util;

public final class DistOpfLimitUtil {

	private static final double OPF_INFINITY = 1.0e19;

	private DistOpfLimitUtil() {
	}

	public static boolean hasFiniteLowerLimit(double lowerLimit) {
		return lowerLimit > -OPF_INFINITY;
	}

	public static boolean hasFiniteUpperLimit(double upperLimit) {
		return upperLimit < OPF_INFINITY;
	}
}
