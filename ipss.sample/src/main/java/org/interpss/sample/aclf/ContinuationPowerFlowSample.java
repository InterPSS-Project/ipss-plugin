package org.interpss.sample.aclf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.cpf.CpfConfig;

/** Minimal fixed-target continuation power-flow example. */
public final class ContinuationPowerFlowSample {
	private ContinuationPowerFlowSample() {
	}

	public static void main(String[] args) throws Exception {
		IpssCorePlugin.init();
		AclfNetwork network = twoBusNetwork();
		if (!LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network).loadflow())
			throw new IllegalStateException("Base load flow did not converge");

		CpfConfig config = new CpfConfig()
				.setTargetLambda(0.10)
				.setMonitoredBusIds(java.util.List.of("Bus2"));
		var result = LoadflowAlgoObjectFactory
				.createContinuationPowerFlowAlgorithm(network, config)
				.runPV();

		System.out.printf("status=%s, maxLambda=%.4f, points=%d, Bus2 V=%.6f pu%n",
				result.status(), result.maxLambda(), result.points().size(),
				result.points().getLast().busStates().get("Bus2").voltageMagnitude());
	}

	private static AclfNetwork twoBusNetwork() {
		AclfNetwork network = CoreObjectFactory.createAclfNetwork();
		network.setBaseKva(100_000.0);

		var swing = CoreObjectFactory.createAclfBus("Bus1", network).orElseThrow();
		swing.setBaseVoltage(4_000.0);
		swing.setGenCode(AclfGenCode.SWING);
		swing.toSwingBus().setDesiredVoltMag(1.0, UnitType.PU);
		swing.toSwingBus().setDesiredVoltAng(0.0, UnitType.Deg);

		var load = CoreObjectFactory.createAclfBus("Bus2", network).orElseThrow();
		load.setBaseVoltage(4_000.0);
		load.setGenCode(AclfGenCode.NON_GEN);
		load.setLoadCode(AclfLoadCode.CONST_P);
		load.toLoadBus().setLoad(new Complex(0.5, 0.2), UnitType.PU);

		var branch = CoreObjectFactory.createAclfBranch();
		network.addBranch(branch, "Bus1", "Bus2");
		branch.setBranchCode(AclfBranchCode.LINE);
		branch.toLine().setZ(new Complex(0.01, 0.05), UnitType.PU, 4_000.0);
		return network;
	}
}
