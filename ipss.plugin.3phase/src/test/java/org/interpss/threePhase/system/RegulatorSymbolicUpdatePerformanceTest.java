package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.qsts.QstsBusVoltageSample;
import org.interpss.threePhase.qsts.QstsControlMode;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsResult;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

public class RegulatorSymbolicUpdatePerformanceTest {
	private static final double VOLTAGE_MAG_TOLERANCE = 1.0e-6;
	private static final double VOLTAGE_ANGLE_TOLERANCE = 1.0e-4;

	@Test
	void ieee123RegulatorQstsComparesMatrixRebuildModes() {
		SolvedMode fullRebuild = solveIeee123(Mode.FULL_REBUILD);
		SolvedMode symbolNewMatrix = solveIeee123(Mode.SYMBOL_REUSE_NEW_MATRIX);
		SolvedMode cacheReuse = solveIeee123(Mode.CACHE_REUSE);

		assertTrue(fullRebuild.result().isConverged());
		assertTrue(symbolNewMatrix.result().isConverged());
		assertTrue(cacheReuse.result().isConverged());
		assertVoltageParity(fullRebuild.result(), symbolNewMatrix.result());
		assertVoltageParity(fullRebuild.result(), cacheReuse.result());

		assertTrue(fullRebuild.algorithm().getFixedPointYMatrixSymbolicFactorizationCount()
						== fullRebuild.algorithm().getFixedPointYMatrixNumericFactorizationCount(),
				"Full rebuild mode should rebuild symbolic factorization for every numeric factorization");
		assertTrue(symbolNewMatrix.algorithm().getFixedPointYMatrixNumericFactorizationCount()
						> symbolNewMatrix.algorithm().getFixedPointYMatrixSymbolicFactorizationCount(),
				"Symbol reuse mode should run fewer symbolic factorizations than numeric factorizations");
		assertEquals(0, symbolNewMatrix.algorithm().getFixedPointYMatrixValueUpdateCount(),
				"New-matrix symbol reuse mode should not update matrix values in place");
		assertTrue(cacheReuse.algorithm().isFixedPointYMatrixCacheEnabled(),
				"Default optimized mode should enable invalidation-aware fixed-point cache");
		assertTrue(cacheReuse.algorithm().getFixedPointYMatrixNumericFactorizationCount()
						<= symbolNewMatrix.algorithm().getFixedPointYMatrixNumericFactorizationCount(),
				"Cache reuse should not run more numeric factorizations than symbol-reuse mode");
		assertTrue(cacheReuse.algorithm().getFixedPointYMatrixValueUpdateCount() > 0,
				"Cache reuse should update regulator tap admittance values in place");
	}

	private static SolvedMode solveIeee123(Mode mode) {
		String oldDisableSymbolReuse = System.getProperty("ipss.qsts.disableFixedPointSymbolReuse");
		String oldDisableValueUpdate = System.getProperty("ipss.qsts.disableFixedPointValueUpdate");
		try {
			setBooleanProperty("ipss.qsts.disableFixedPointSymbolReuse", mode.disableSymbolReuse);
			setBooleanProperty("ipss.qsts.disableFixedPointValueUpdate", mode.disableValueUpdate);

			OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
			assertTrue(parser.parseFeederData("testData/feeder/IEEE123", "IEEE123Master.dss"));
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));

			DistributionPowerFlowAlgorithm algorithm = ThreePhaseObjectFactory
					.createDistPowerFlowAlgorithm(parser.getStaticNetwork());
			long startNanos = System.nanoTime();
			QstsResult result = OpenDSSQstsStudyFactory.from(parser)
					.setPowerFlowAlgorithm(algorithm)
					.setMode(QstsMode.DAILY)
					.setNumberOfSteps(3)
					.setStepSizeHours(1.0)
					.setControlMode(QstsControlMode.STATIC)
					.setMaxControlIterations(20)
					.setMaxPowerFlowIterations(200)
					.setTolerance(1.0e-8)
					.run();
			long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
			System.out.println("QSTS IEEE123 regulator mode=" + mode
					+ ", converged=" + result.isConverged()
					+ ", elapsedMillis=" + elapsedMillis
					+ ", symbolicFactors=" + algorithm.getFixedPointYMatrixSymbolicFactorizationCount()
					+ ", numericFactors=" + algorithm.getFixedPointYMatrixNumericFactorizationCount()
					+ ", valueUpdates=" + algorithm.getFixedPointYMatrixValueUpdateCount()
					+ ", fallbackCount=" + algorithm.getFixedPointFallbackCount());
			return new SolvedMode(mode, result, algorithm, elapsedMillis);
		}
		finally {
			restoreProperty("ipss.qsts.disableFixedPointSymbolReuse", oldDisableSymbolReuse);
			restoreProperty("ipss.qsts.disableFixedPointValueUpdate", oldDisableValueUpdate);
		}
	}

	private static void assertVoltageParity(QstsResult expected, QstsResult actual) {
		Map<String, QstsBusVoltageSample> expectedVoltages = busVoltagesByKey(expected);
		Map<String, QstsBusVoltageSample> actualVoltages = busVoltagesByKey(actual);
		assertEquals(expectedVoltages.keySet(), actualVoltages.keySet());
		for(String key : expectedVoltages.keySet()) {
			QstsBusVoltageSample expectedSample = expectedVoltages.get(key);
			QstsBusVoltageSample actualSample = actualVoltages.get(key);
			assertEquals(expectedSample.getMagnitude(), actualSample.getMagnitude(),
					VOLTAGE_MAG_TOLERANCE, "Voltage magnitude mismatch for " + key);
			assertEquals(expectedSample.getAngleDegrees(), actualSample.getAngleDegrees(),
					VOLTAGE_ANGLE_TOLERANCE, "Voltage angle mismatch for " + key);
		}
	}

	private static Map<String, QstsBusVoltageSample> busVoltagesByKey(QstsResult result) {
		Map<String, QstsBusVoltageSample> samples = new LinkedHashMap<>();
		for(QstsBusVoltageSample sample : result.getBusVoltages()) {
			samples.put(sample.getStepIndex() + "|" + sample.getBusId() + "|" + sample.getPhase(), sample);
		}
		return samples;
	}

	private static void setBooleanProperty(String key, boolean value) {
		if(value) {
			System.setProperty(key, "true");
		}
		else {
			System.clearProperty(key);
		}
	}

	private static void restoreProperty(String key, String value) {
		if(value == null) {
			System.clearProperty(key);
		}
		else {
			System.setProperty(key, value);
		}
	}

	private enum Mode {
		FULL_REBUILD(true, true),
		SYMBOL_REUSE_NEW_MATRIX(false, true),
		CACHE_REUSE(false, false);

		private final boolean disableSymbolReuse;
		private final boolean disableValueUpdate;

		Mode(boolean disableSymbolReuse, boolean disableValueUpdate) {
			this.disableSymbolReuse = disableSymbolReuse;
			this.disableValueUpdate = disableValueUpdate;
		}
	}

	private record SolvedMode(Mode mode, QstsResult result,
			DistributionPowerFlowAlgorithm algorithm, long elapsedMillis) {
	}
}
