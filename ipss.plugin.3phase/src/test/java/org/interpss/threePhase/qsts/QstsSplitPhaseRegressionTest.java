package org.interpss.threePhase.qsts;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.interpss.IpssCorePlugin;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QstsSplitPhaseRegressionTest {
	@ParameterizedTest
	@ValueSource(strings = {"split_a", "split_b", "split_c"})
	void sampledWindingVoltagesMatchStoredReferences(String name) throws Exception {
		IpssCorePlugin.init();
		Path fixtures = Path.of("src/test/resources/cim/extensions");
		var parser = OpenDSSDataParser.forStaticNetwork();
		parser.setPreserveSpecifiedLineImpedance(true);
		assertTrue(parser.parseFeederData(fixtures.toString(), name + ".dss"));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1));
		var result = OpenDSSQstsStudyFactory.from(parser).setNumberOfSteps(1).setTolerance(1e-10).run();
		assertTrue(result.isConverged());
		var samples = result.getStep(0).getBusVoltages();
		for(String row : Files.readAllLines(fixtures.resolve(name + "-voltage.csv")).stream().skip(1).toList()) {
			String[] fields = row.split(",");
			String phase = List.of("A", "B", "C").get(Integer.parseInt(fields[1]) - 1);
			double expected = Math.hypot(Double.parseDouble(fields[2]), Double.parseDouble(fields[3]))
					/ (parser.getStaticNetwork().getBus(fields[0]).getBaseVoltage() / Math.sqrt(3));
			var sample = samples.stream().filter(value -> value.getBusId().equals(fields[0])
					&& value.getPhase().equals(phase)).findFirst().orElseThrow();
			assertEquals(expected, sample.getMagnitude(), 1e-6, name + " " + fields[0] + "." + fields[1]);
		}
		assertEquals(2, samples.stream().filter(sample -> sample.getBusId().equals("secondary")).count());
	}

    @Test
    void cPrimaryWithTwoSecondaryNodesRetainsThirtyKwAndTenKvar() {
        IpssCorePlugin.init();
        var parser = OpenDSSDataParser.forStaticNetwork();
        assertTrue(parser.parseFeederData("src/test/resources/opendss-regression", "split-phase.dss"));
        assertTrue(parser.calcVoltageBases());
        assertTrue(parser.convertActualValuesToPU(1));
        var result = OpenDSSQstsStudyFactory.from(parser).setNumberOfSteps(1).setTolerance(1e-10).run();
        assertTrue(result.isConverged());
        var step = result.getStep(0);
        assertEquals(List.of("A", "B"), step.getBusVoltages().stream()
                .filter(sample -> sample.getBusId().equals("secondary"))
                .map(QstsBusVoltageSample::getPhase).toList());
        assertEquals(List.of("A", "B"), step.getLoadPowers().stream()
                .map(QstsDevicePowerSample::getPhase).toList());
        double powerBase = parser.getNetworkBaseKva() / 3;
        assertEquals(30, step.getLoadPowers().stream().mapToDouble(QstsDevicePowerSample::getP).sum() * powerBase, 1e-6);
        assertEquals(10, step.getLoadPowers().stream().mapToDouble(QstsDevicePowerSample::getQ).sum() * powerBase, 1e-6);
        var voltage = parser.getStaticNetwork().getBus("secondary").get3PhaseVotlages();
        assertEquals(voltage.a_0.abs(), step.getBusVoltages().stream()
                .filter(sample -> sample.getBusId().equals("secondary") && sample.getPhase().equals("A"))
                .findFirst().orElseThrow().getMagnitude(), 1e-12);
        assertTrue(voltage.a_0.abs() > .98 && voltage.a_0.abs() < 1);
        assertTrue(voltage.b_1.abs() > .98 && voltage.b_1.abs() < 1);
        var secondaryPower = step.getBranchPowers().stream()
                .filter(sample -> sample.getBusId().equals("secondary")).toList();
        assertEquals(List.of(2, 3), secondaryPower.stream().map(QstsBranchPowerSample::getTerminal).toList());
        for(var sample : secondaryPower) {
            assertEquals(-15, sample.getActivePowerKw(), 1e-6);
            assertEquals(-5, sample.getReactivePowerKvar(), 1e-6);
        }
    }
}
