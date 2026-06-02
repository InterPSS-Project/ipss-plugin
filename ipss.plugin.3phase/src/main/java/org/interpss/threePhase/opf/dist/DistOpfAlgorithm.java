package org.interpss.threePhase.opf.dist;

public interface DistOpfAlgorithm {

	DistOpfAlgorithm setObjective(DistOpfObjective objective);

	DistOpfAlgorithm setControlMode(DistOpfControlMode controlMode);

	DistOpfAlgorithm setOptions(DistOpfOptions options);

	DistOpfResult solve();
}
