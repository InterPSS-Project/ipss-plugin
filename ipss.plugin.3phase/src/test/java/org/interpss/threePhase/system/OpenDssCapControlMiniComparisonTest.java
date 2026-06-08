package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.control.CapacitorControlData;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.core.threephase.IPhaseLoad;
import com.interpss.core.threephase.Static3PBus;
import com.interpss.core.threephase.Static3PNetwork;

public class OpenDssCapControlMiniComparisonTest {
	private static final String FEEDER_FOLDER = "testData/feeder/OpenDSSCapControlMini";
	private static final String REFERENCE_RESOURCE =
			"opendss-reference/capcontrol-mini-dss-python-capacitor-reference.csv";
	private static final double TERMINAL_KVAR_TOLERANCE = 2.0;

	@Test
	void capacitorControlStatesAndTerminalKvarMatchDssPythonMiniCases() throws IOException {
		for(CapacitorReference reference : readReferences()) {
			OpenDSSStaticDataParser parser = OpenDSSDataParser.forStaticNetwork();
			assertTrue(parser.parseFeederData(FEEDER_FOLDER, reference.masterFile));
			assertTrue(parser.calcVoltageBases());
			assertTrue(parser.convertActualValuesToPU(1.0));
			List<CapacitorControlData> controls = parser.getCapacitorControls();
			assertEquals(1, controls.size(), reference.caseId);

			Static3PNetwork network = parser.getStaticNetwork();
			DistributionPowerFlowAlgorithm powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(network);
			powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
			powerFlow.setInitBusVoltageEnabled(true);
			powerFlow.setMaxIteration(100);
			powerFlow.setTolerance(1.0e-8);
			powerFlow.setCapacitorControls(controls);
			powerFlow.setCapacitorControlEnabled(true);
			assertTrue(powerFlow.powerflow(), "Power flow failed for " + reference.caseId
					+ ", iterations=" + powerFlow.getIterationCount());

			CapacitorDevice capacitor = findCapacitor(network, reference.capacitorId);
			assertNotNull(capacitor, "Missing capacitor " + reference.capacitorId);
			Complex3x1 solvedPower = capacitor.load.get3PhaseLoad(capacitor.bus.get3PhaseVotlages());
			double totalQKvar = total(solvedPower).getImaginary() * network.getBaseKva() / 3.0;
			boolean closed = Math.abs(totalQKvar) > 1.0;

			assertEquals(reference.closed, closed, "Capacitor state mismatch for " + reference.caseId);
			assertEquals(reference.totalQKvar, totalQKvar, TERMINAL_KVAR_TOLERANCE,
					"Capacitor kvar mismatch for " + reference.caseId);
		}
	}

	private static CapacitorDevice findCapacitor(Static3PNetwork network, String capacitorId) {
		for(Static3PBus bus : network.getBusList()) {
			for(IPhaseLoad load : bus.getPhaseLoadList()) {
				if(load.getId().equalsIgnoreCase(capacitorId)) {
					return new CapacitorDevice(bus, load);
				}
			}
		}
		return null;
	}

	private static Complex total(Complex3x1 power) {
		return value(power.a_0).add(value(power.b_1)).add(value(power.c_2));
	}

	private static Complex value(Complex value) {
		return value == null ? Complex.ZERO : value;
	}

	private static List<CapacitorReference> readReferences() throws IOException {
		InputStream stream = OpenDssCapControlMiniComparisonTest.class.getClassLoader()
				.getResourceAsStream(REFERENCE_RESOURCE);
		assertNotNull(stream, "Missing resource " + REFERENCE_RESOURCE);
		List<CapacitorReference> references = new ArrayList<CapacitorReference>();
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line = reader.readLine();
			while((line = reader.readLine()) != null) {
				if(line.trim().isEmpty()) {
					continue;
				}
				String[] columns = line.split(",");
				references.add(new CapacitorReference(columns[0], columns[1], columns[2],
						Boolean.valueOf(columns[3]).booleanValue(), Double.valueOf(columns[4]).doubleValue()));
			}
		}
		return references;
	}

	private static class CapacitorReference {
		private final String caseId;
		private final String masterFile;
		private final String capacitorId;
		private final boolean closed;
		private final double totalQKvar;

		private CapacitorReference(String caseId, String masterFile, String capacitorId,
				boolean closed, double totalQKvar) {
			this.caseId = caseId;
			this.masterFile = masterFile;
			this.capacitorId = capacitorId;
			this.closed = closed;
			this.totalQKvar = totalQKvar;
		}
	}

	private static class CapacitorDevice {
		private final Static3PBus bus;
		private final IPhaseLoad load;

		private CapacitorDevice(Static3PBus bus, IPhaseLoad load) {
			this.bus = bus;
			this.load = load;
		}
	}

}
