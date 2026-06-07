package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.qsts.QstsControlMode;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsResult;
import org.interpss.threePhase.qsts.QstsStepContext;
import org.interpss.threePhase.qsts.QstsStudy;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.junit.jupiter.api.Test;

import com.interpss.core.threephase.IPhaseLoad;

public class OpenDssIeee13DailyQstsProfileTest {
	private static final String FEEDER_FOLDER = "testData/feeder/IEEE13";
	private static final double[] MY_PROFILE = {
			0.80, 0.80, 0.80, 0.80, 0.80, 0.80,
			0.80, 0.82, 0.84, 0.86, 0.88, 0.90,
			0.92, 0.94, 0.96, 0.98, 1.00, 0.98,
			0.96, 0.94, 0.92, 0.90, 0.86, 0.82
	};
	private static final double LOAD_POWER_TOLERANCE_KW = 1.0e-6;

	@Test
	void ieee13DailyModeAppliesDssLoadShapeFromFileAndLeavesUnboundLoadsAtBasePower() {
		OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
		assertTrue(parser.parseFeederData(FEEDER_FOLDER, "IEEE13DailyQstsProfile.dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));
		assertTrue(parser.getTimeSeriesData().getShapeRegistry().get("MyProfile") != null);

		QstsStudy study = OpenDSSQstsStudyFactory.from(parser);
		Complex load671Base = loadPower(study, "671");
		Complex load675aBase = loadPower(study, "675a");
		Complex load675bBase = loadPower(study, "675b");
		Complex load675cBase = loadPower(study, "675c");
		Complex load634aBase = loadPower(study, "634a");

		for(int i = 0; i < MY_PROFILE.length; i++) {
			study.getStateApplier().apply(new QstsStepContext(i, i, i, QstsMode.DAILY,
					1.0, 1.0, QstsControlMode.OFF));
			assertLoadPower(study, "671", load671Base.multiply(MY_PROFILE[i]));
			assertLoadPower(study, "675a", load675aBase.multiply(MY_PROFILE[i]));
			assertLoadPower(study, "675b", load675bBase.multiply(MY_PROFILE[i]));
			assertLoadPower(study, "675c", load675cBase.multiply(MY_PROFILE[i]));
			assertLoadPower(study, "634a", load634aBase);
		}

		QstsResult result = OpenDSSQstsStudyFactory.from(parser)
				.setMode(QstsMode.DAILY)
				.setNumberOfSteps(MY_PROFILE.length)
				.setStepSizeHours(1.0)
				.setControlMode(QstsControlMode.OFF)
				.setMaxControlIterations(0)
				.setMaxPowerFlowIterations(200)
				.setTolerance(1.0e-4)
				.run();
		assertTrue(result.isConverged());
		assertEquals(MY_PROFILE.length, result.getStepResults().size());
	}

	private static void assertLoadPower(QstsStudy study, String loadId, Complex expectedPower) {
		Complex power = loadPower(study, loadId);
		assertEquals(expectedPower.getReal(), power.getReal(),
				LOAD_POWER_TOLERANCE_KW, "P mismatch for Load." + loadId);
		assertEquals(expectedPower.getImaginary(), power.getImaginary(),
				LOAD_POWER_TOLERANCE_KW, "Q mismatch for Load." + loadId);
	}

	private static Complex loadPower(QstsStudy study, String loadId) {
		for(var state : study.getStateApplier().getLoadStateStore().states()) {
			if(state.getLoadId().equalsIgnoreCase(loadId)) {
				Complex3x1 power = ((IPhaseLoad) state.getLoad()).getInit3PhaseLoad();
				return add(add(power.a_0, power.b_1), power.c_2);
			}
		}
		assertTrue(false, "Missing load " + loadId);
		return Complex.ZERO;
	}

	private static Complex add(Complex left, Complex right) {
		Complex a = left == null ? Complex.ZERO : left;
		Complex b = right == null ? Complex.ZERO : right;
		return a.add(b);
	}
}
