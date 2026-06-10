package org.interpss.core.aclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.impl.LfNrProfiler;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;
import com.interpss.core.sparse.solver.klu.KLUSolverProfiler;

@Tag("large")
public class AclfOpenEiJkluPerformanceTest extends CorePluginTestSetup {
	private static final String OPEN_EI_RAW =
			"testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW";

	@Test
	public void compareCsjAndJkluForOpenEiAclf() throws Exception {
		assumeTrue(Boolean.getBoolean("interpss.largeAclfTests"),
				"Set -Dinterpss.largeAclfTests=true to run the OpenEI ACLF benchmark");

		int warmups = Math.max(0, Integer.getInteger("interpss.aclfOpenEiWarmups", 1));
		int repeats = Math.max(1, Integer.getInteger("interpss.aclfOpenEiRepeats", 3));
		String previousProfile = System.getProperty(KLUSolverProfiler.ENABLED_PROPERTY);
		String previousNrProfile = System.getProperty(LfNrProfiler.ENABLED_PROPERTY);
		System.setProperty(KLUSolverProfiler.ENABLED_PROPERTY, "true");
		System.setProperty(LfNrProfiler.ENABLED_PROPERTY, "true");

		System.out.println("case,solver,repeat,warmup,buses,branches,importMs,loadflowMs,totalMs,"
				+ "converged,maxP,maxQ,cscBuildMs,analyzeMs,factorMs,refactorMs,solveMs,"
				+ "cscBuildCount,analyzeCount,factorCount,refactorCount,solveCount,matrixN,matrixNnz,"
				+ "nrMismatchMs,nrAdjustmentMs,nrCoreStepMs,nrJMatrixMs,nrRhsMs,nrSolveMs,"
				+ "nrUpdateVoltageMs,nrResetMs,nrIterations,nrRebuilds");

		try {
			for (int repeat = 0; repeat < warmups + repeats; repeat++) {
				boolean warmup = repeat < warmups;
				int reportedRepeat = warmup ? 0 : repeat - warmups + 1;
				printRun("OpenEI", "CSJ", reportedRepeat, warmup, runOnce(false));
				printRun("OpenEI", "JAVA_KLU", reportedRepeat, warmup, runOnce(true));
			}
		} finally {
			if (previousProfile == null)
				System.clearProperty(KLUSolverProfiler.ENABLED_PROPERTY);
			else
				System.setProperty(KLUSolverProfiler.ENABLED_PROPERTY, previousProfile);
			if (previousNrProfile == null)
				System.clearProperty(LfNrProfiler.ENABLED_PROPERTY);
			else
				System.setProperty(LfNrProfiler.ENABLED_PROPERTY, previousNrProfile);
			SparseEqnSolverProvider.useCSJ();
		}
	}

	private static RunResult runOnce(boolean javaKlu) throws Exception {
		if (javaKlu)
			SparseEqnSolverProvider.useJavaKlu();
		else
			SparseEqnSolverProvider.useCSJ();

		KLUSolverProfiler.reset();
		LfNrProfiler.reset();
		long importStart = System.nanoTime();
		AclfNetwork net = loadOpenEi();
		long importNs = System.nanoTime() - importStart;

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		AclfMethodType method = AclfMethodType.valueOf(System.getProperty("interpss.aclfOpenEiMethod", "NR"));
		algo.setLfMethod(method);
		algo.setTolerance(0.0001);

		long loadflowStart = System.nanoTime();
		boolean converged = algo.loadflow();
		long loadflowNs = System.nanoTime() - loadflowStart;
		assertTrue(converged, "OpenEI ACLF should converge");

		com.interpss.core.datatype.Mismatch mismatch = net.maxMismatch(method);
		return new RunResult(
				net.getNoBus(),
				net.getNoBranch(),
				importNs,
				loadflowNs,
				converged,
				Math.abs(mismatch.maxMis.getReal()),
				Math.abs(mismatch.maxMis.getImaginary()),
				KLUSolverProfiler.snapshot(),
				LfNrProfiler.snapshot());
	}

	private static AclfNetwork loadOpenEi() throws InterpssException {
		return IpssAdapter.importAclfNet(OPEN_EI_RAW)
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33)
				.load()
				.getImportedObj();
	}

	private static void printRun(String caseName, String solver, int repeat, boolean warmup, RunResult result) {
		KLUSolverProfiler.Snapshot profile = result.profile;
		LfNrProfiler.Snapshot nrProfile = result.nrProfile;
		System.out.println(caseName + "," + solver + "," + repeat + "," + warmup
				+ "," + result.buses
				+ "," + result.branches
				+ "," + format(ms(result.importNs))
				+ "," + format(ms(result.loadflowNs))
				+ "," + format(ms(result.importNs + result.loadflowNs))
				+ "," + result.converged
				+ "," + format(result.maxP)
				+ "," + format(result.maxQ)
				+ "," + format(ms(profile.cscBuildNs))
				+ "," + format(ms(profile.analyzeNs))
				+ "," + format(ms(profile.factorNs))
				+ "," + format(ms(profile.refactorNs))
				+ "," + format(ms(profile.solveNs))
				+ "," + profile.cscBuildCount
				+ "," + profile.analyzeCount
				+ "," + profile.factorCount
				+ "," + profile.refactorCount
				+ "," + profile.solveCount
				+ "," + profile.n
				+ "," + profile.nnz
				+ "," + format(ms(nrProfile.mismatchNs))
				+ "," + format(ms(nrProfile.adjustmentNs))
				+ "," + format(ms(nrProfile.coreStepNs))
				+ "," + format(ms(nrProfile.jMatrixNs))
				+ "," + format(ms(nrProfile.rhsNs))
				+ "," + format(ms(nrProfile.solveNs))
				+ "," + format(ms(nrProfile.updateVoltageNs))
				+ "," + format(ms(nrProfile.resetNs))
				+ "," + nrProfile.iterationCount
				+ "," + nrProfile.rebuildCount);
	}

	private static double ms(long nanos) {
		return nanos / 1_000_000.0;
	}

	private static String format(double value) {
		return String.format(java.util.Locale.US, "%.3f", value);
	}

	private static final class RunResult {
		final int buses;
		final int branches;
		final long importNs;
		final long loadflowNs;
		final boolean converged;
		final double maxP;
		final double maxQ;
		final KLUSolverProfiler.Snapshot profile;
		final LfNrProfiler.Snapshot nrProfile;

		RunResult(
				int buses,
				int branches,
				long importNs,
				long loadflowNs,
				boolean converged,
				double maxP,
				double maxQ,
				KLUSolverProfiler.Snapshot profile,
				LfNrProfiler.Snapshot nrProfile) {
			this.buses = buses;
			this.branches = branches;
			this.importNs = importNs;
			this.loadflowNs = loadflowNs;
			this.converged = converged;
			this.maxP = maxP;
			this.maxQ = maxQ;
			this.profile = profile;
			this.nrProfile = nrProfile;
		}
	}
}
