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
import org.interpss.threePhase.qsts.control.QstsControlCompensationPolicy;
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
		SolvedMode symbolValueUpdate = solveIeee123(Mode.SYMBOL_REUSE_VALUE_UPDATE);
		SolvedMode directCompensationPadded = solveIeee123(Mode.DIRECT_COMPENSATION_RPAD_1E_6);

		assertTrue(fullRebuild.result().isConverged());
		assertTrue(symbolNewMatrix.result().isConverged());
		assertTrue(symbolValueUpdate.result().isConverged());
		assertTrue(directCompensationPadded.result().isConverged());
		assertVoltageParity(fullRebuild.result(), symbolNewMatrix.result());
		assertVoltageParity(fullRebuild.result(), symbolValueUpdate.result());
		assertVoltageParity(fullRebuild.result(), directCompensationPadded.result());

		assertTrue(fullRebuild.algorithm().getFixedPointYMatrixSymbolicFactorizationCount()
						== fullRebuild.algorithm().getFixedPointYMatrixNumericFactorizationCount(),
				"Full rebuild mode should rebuild symbolic factorization for every numeric factorization");
		assertTrue(symbolNewMatrix.algorithm().getFixedPointYMatrixNumericFactorizationCount()
						> symbolNewMatrix.algorithm().getFixedPointYMatrixSymbolicFactorizationCount(),
				"Symbol reuse mode should run fewer symbolic factorizations than numeric factorizations");
		assertEquals(0, symbolNewMatrix.algorithm().getFixedPointYMatrixValueUpdateCount(),
				"New-matrix symbol reuse mode should not update matrix values in place");
		assertTrue(symbolValueUpdate.algorithm().getFixedPointYMatrixValueUpdateCount() > 0,
				"Value-update mode should update existing sparse matrix values");
		assertTrue(directCompensationPadded.algorithm().getFixedPointFallbackCount() <= 1,
				"1e-6 padded direct compensation should avoid repeated fallback on IEEE123");
	}

	private static SolvedMode solveIeee123(Mode mode) {
		String oldDisableSymbolReuse = System.getProperty("ipss.qsts.disableFixedPointSymbolReuse");
		String oldDisableValueUpdate = System.getProperty("ipss.qsts.disableFixedPointValueUpdate");
		String oldSeriesRPad = System.getProperty("ipss.qsts.regulatorCompensationSeriesRPadPu");
		try {
			setBooleanProperty("ipss.qsts.disableFixedPointSymbolReuse", mode.disableSymbolReuse);
			setBooleanProperty("ipss.qsts.disableFixedPointValueUpdate", mode.disableValueUpdate);
			setNullableProperty("ipss.qsts.regulatorCompensationSeriesRPadPu", mode.seriesRPadPu);

			OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
			assertTrue(parser.parseFeederData("testData/feeder/IEEE123", "IEEE123Master.dss"));
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));

			DistributionPowerFlowAlgorithm algorithm = ThreePhaseObjectFactory
					.createDistPowerFlowAlgorithm(parser.getStaticNetwork());
			long startNanos = System.nanoTime();
			QstsResult result = OpenDSSQstsStudyFactory.from(parser)
					.setPowerFlowAlgorithm(algorithm)
					.setControlCompensationPolicy(mode.controlCompensationPolicy())
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
			restoreProperty("ipss.qsts.regulatorCompensationSeriesRPadPu", oldSeriesRPad);
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

	private static void setNullableProperty(String key, String value) {
		if(value == null) {
			System.clearProperty(key);
		}
		else {
			System.setProperty(key, value);
		}
	}

	private enum Mode {
		FULL_REBUILD(true, true, null, false),
		SYMBOL_REUSE_NEW_MATRIX(false, true, null, false),
		SYMBOL_REUSE_VALUE_UPDATE(false, false, null, false),
		DIRECT_COMPENSATION_RPAD_1E_6(false, false, "0.000001", true);

		private final boolean disableSymbolReuse;
		private final boolean disableValueUpdate;
		private final String seriesRPadPu;
		private final boolean regulatorCompensationEnabled;

		Mode(boolean disableSymbolReuse, boolean disableValueUpdate, String seriesRPadPu,
				boolean regulatorCompensationEnabled) {
			this.disableSymbolReuse = disableSymbolReuse;
			this.disableValueUpdate = disableValueUpdate;
			this.seriesRPadPu = seriesRPadPu;
			this.regulatorCompensationEnabled = regulatorCompensationEnabled;
		}

		private QstsControlCompensationPolicy controlCompensationPolicy() {
			return regulatorCompensationEnabled
					? new QstsControlCompensationPolicy(true, true, true)
					: new QstsControlCompensationPolicy();
		}
	}

	private record SolvedMode(Mode mode, QstsResult result,
			DistributionPowerFlowAlgorithm algorithm, long elapsedMillis) {
	}
}
