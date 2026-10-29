package org.interpss.threePhase.system;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.powerflow.DistributionPostSolveOutputMode;
import org.interpss.threePhase.qsts.*;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QstsDeferredVoltageSyncTest {
    private record Solved(Map<String, Complex3x1> voltages, Map<String, Integer> taps,
                          Map<String, Boolean> capacitors) { }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("IEEE13", "IEEE13DailyQstsProfile.dss", QstsMode.DAILY, 1.0),
                Arguments.of("IEEE8500", "Master-InterPSS.dss", QstsMode.DAILY, 1.0),
                Arguments.of("IEEE8500", "Master-InterPSS-QSTS-PV-Duty.dss", QstsMode.DUTY, 120.0 / 3600),
                Arguments.of("Ckt24", "master_ckt24_yearly_interpss.dss", QstsMode.YEARLY, 1.0));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void deferredAndFullSamplingMatchAfterChangingLoadsAndRegulatorTaps(
            String feeder, String master, QstsMode mode, double interval) {
        Solved full = solve(feeder, master, mode, interval, QstsResultSamplingMode.FULL);
        Solved deferred = solve(feeder, master, mode, interval, QstsResultSamplingMode.NONE);
        assertEquals(full.voltages.keySet(), deferred.voltages.keySet());
        for(String bus : full.voltages.keySet()) {
            assertEquals(0.0, full.voltages.get(bus).subtract(deferred.voltages.get(bus)).absMax(),
                    1e-10, "Final bus voltage differs: " + bus);
        }
        assertEquals(full.taps, deferred.taps);
        assertEquals(full.capacitors, deferred.capacitors);
    }

    private Solved solve(String feeder, String master, QstsMode mode, double interval,
                         QstsResultSamplingMode sampling) {
        IpssCorePlugin.configureSparseSolver("csj");
        var parser = OpenDSSDataParser.forStaticNetwork();
        assertTrue(parser.parseFeederData("testData/feeder/" + feeder, master));
        assertTrue(parser.calcVoltageBases());
        assertTrue(parser.convertActualValuesToPU(1.0));
        var algorithm = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(parser.getStaticNetwork());
        algorithm.setPrimitiveVoltageStateEnabled(true);
        algorithm.setMinIteration(1);
        var result = OpenDSSQstsStudyFactory.from(parser).setPowerFlowAlgorithm(algorithm)
                .setMode(mode).setNumberOfSteps(24).setStepSizeHours(interval)
                .setControlMode(QstsControlMode.STATIC).setMaxControlIterations(100)
                .setTolerance(1e-8).setMaxPowerFlowIterations(1000)
                .setPostSolveOutputMode(DistributionPostSolveOutputMode.VOLTAGE_ONLY)
                .setResultSamplingMode(sampling).run();
        assertTrue(result.isConverged());
        assertEquals(24, result.getStepResults().size());
        Map<String, Complex3x1> voltages = new LinkedHashMap<>();
        for(var bus : parser.getStaticNetwork().getThreePhaseBusList()) {
            var voltage = bus.get3PhaseVotlages();
            voltages.put(bus.getId(), new Complex3x1(voltage.a_0, voltage.b_1, voltage.c_2));
        }
        Map<String, Integer> taps = new LinkedHashMap<>();
        for(var control : parser.getRegulatorControls()) taps.put(control.getId(), control.getTapPosition());
        Map<String, Boolean> capacitors = new LinkedHashMap<>();
        for(var control : parser.getCapacitorControls()) capacitors.put(control.getId(), control.isClosed());
        return new Solved(voltages, taps, capacitors);
    }
}
