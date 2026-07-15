package org.interpss.sample.aclf;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.interpss.IpssCorePlugin;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.cpf.CpfConfig;

/** Repeatable CPF benchmark for a PSS/E RAW case. */
public final class CpfBenchmark {
	private CpfBenchmark() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			throw new IllegalArgumentException(
					"Usage: CpfBenchmark <raw-file> <PSSE_30|...|PSSE_36> <monitored-bus> [target-lambda]");
		}
		IpssCorePlugin.init();
		double target = args.length > 3 ? Double.parseDouble(args[3]) : 0.01;
		AclfNetwork network = IpssAdapter.importAclfNet(args[0])
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.valueOf(args[1]))
				.load()
				.getImportedObj();
		var loadflow = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
		loadflow.setTolerance(1.0e-7);
		loadflow.getLfAdjAlgo().setApplyAdjustAlgo(false);
		if (!loadflow.loadflow())
			throw new IllegalStateException("Base load flow did not converge");
		freezeContinuousControls(network);
		var frozenLoadflow = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(network);
		frozenLoadflow.setTolerance(1.0e-7);
		frozenLoadflow.getLfAdjAlgo().setApplyAdjustAlgo(false);
		if (!frozenLoadflow.loadflow())
			throw new IllegalStateException("Frozen-control base load flow did not converge");

		long memoryBefore = usedMemory();
		AtomicLong sampledPeakMemory = new AtomicLong(memoryBefore);
		var result = LoadflowAlgoObjectFactory.createContinuationPowerFlowAlgorithm(network,
				new CpfConfig()
						.setTargetLambda(target)
						.setMonitoredBusIds(List.of(args[2]))
						.setMaxStoredPoints(32))
				.setPointListener(point -> sampledPeakMemory.accumulateAndGet(usedMemory(), Math::max))
				.runPV();
		long memoryDelta = usedMemory() - memoryBefore;
		long sampledPeakDelta = sampledPeakMemory.get() - memoryBefore;
		var metrics = result.performanceMetrics();
		System.out.printf(
				"buses=%d,status=%s,failure=%s,lambda=%.6f,points=%d,elapsedMs=%.3f,jacobianMs=%.3f,"
				+ "solveMs=%.3f,correctorMs=%.3f,captureMs=%.3f,linearSolves=%d,"
				+ "correctorIterations=%d,sampledPeakDeltaMiB=%.3f,endMemoryDeltaMiB=%.3f,"
				+ "maxResidual=%.3e%n",
				network.getNoBus(), result.status(), result.failureCause(), result.maxLambda(), result.points().size(),
				metrics.elapsedNanos() / 1.0e6, metrics.jacobianAssemblyNanos() / 1.0e6,
				metrics.linearSolveNanos() / 1.0e6, metrics.correctorNanos() / 1.0e6,
				metrics.pointCaptureNanos() / 1.0e6, metrics.linearSolves(),
				metrics.correctorIterations(), sampledPeakDelta / 1024.0 / 1024.0,
				memoryDelta / 1024.0 / 1024.0,
				result.points().stream().mapToDouble(point -> point.maxMismatch()).max().orElse(Double.NaN));
	}

	private static long usedMemory() {
		Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	private static void freezeContinuousControls(AclfNetwork network) {
		network.getBusList().forEach(bus -> {
			bus.getStaticVarCompensatorList().forEach(svc -> svc.setControlStatus(false));
			if (bus.isRemoteQBus())
				bus.getRemoteQBus().setControlStatus(false);
			if (bus.getGenRemoteVCtrlGroup() != null)
				bus.getGenRemoteVCtrlGroup().setControlStatus(false);
		});
	}
}
