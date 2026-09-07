package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Reeca1Data;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Repca1Data;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;

import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Public reduced-network reproducer for an aggregate REGCA1/REECA1/REPCA1 Q/V mode. */
class RenewableAggregateQvModeTest extends CorePluginTestSetup {
    private static final double STEP = 1.0 / 240.0;

    @Test
    void aggregateProfileIsStationaryAndRecoversFromThreeCyclePoiFault() throws Exception {
        RunResult aggregate = run(30, false);
        RunResult faulted = run(30, true);

        System.out.printf(java.util.Locale.ROOT,
                "Renewable aggregate Q/V mode: thirtyPlants=%.9g faultMin=%.9g "
                        + "faultFinal=%.9g%n",
                aggregate.maximumVoltageDrift(),
                faulted.minimumPoiVoltage(), faulted.finalPoiVoltage());
        assertTrue(aggregate.maximumVoltageDrift() < 1.0e-6,
                "aggregate flat-run drift " + aggregate.maximumVoltageDrift());
        assertTrue(faulted.minimumPoiVoltage() < .2,
                "three-cycle POI fault must depress voltage");
        assertTrue(faulted.finalPoiVoltage() > .9,
                "aggregate renewable voltage must recover after clearing");
    }

    private static RunResult run(int plantCount, boolean withFault) throws Exception {
        DStabilityNetwork network = DStabObjectFactory.createDStabilityNetwork();
        network.setBaseKva(100000.0);
        AclfNetworkBuilder topology = new AclfNetworkBuilder(network);
        topology.setNetworkInfo("renewable-qv-mode", "renewable-qv-mode", 100000.0,
                OriginalDataFormat.PSSE);
        topology.addBus("Grid", "Infinite grid", 1L, 230000.0, 1.0, 0.0,
                null, null, null);
        topology.setSwingBus("Grid", 1.0, 0.0);
        topology.addContributeGen("Grid", "1", true, 0, 0, 100, 1.0,
                0, 0, 0, 0, new Complex(0, .01), null, 0, null, 0, 0);
        topology.addBus("Poi", "Common POI", 2L, 230000.0, 1.0, 0.0,
                null, null, null);
        topology.setNonGenBus("Poi");
        topology.addLine("Poi", "Grid", "1", new Complex(.01, .20), Complex.ZERO,
                null, null, 0, 0, 0, true);

        for (int i = 1; i <= plantCount; i++) {
            String bus = "Plant" + i;
            topology.addBus(bus, bus, 100L + i, 230000.0, 1.0, 0.0,
                    null, null, null);
            topology.setPQBus(bus, .01, 0.0, 0.0, 0.0);
            topology.addContributeGen(bus, "1", true, .01, 0.0, 100.0, 1.0,
                    1.0, -1.0, 1.0, 0.0, new Complex(0, .10), null,
                    0.0, null, 0.0, 0.0);
            topology.addLine(bus, "Poi", "1", new Complex(.005, .10), Complex.ZERO,
                    null, null, 0, 0, 0, true);
        }

        DStabNetworkBuilder dynamics = new DStabNetworkBuilder(network);
        dynamics.addInfiniteMachine("Grid", "1");
        for (int i = 1; i <= plantCount; i++) {
            String bus = "Plant" + i;
            dynamics.addRegca1(bus, "1", regcaData());
            dynamics.addReeca1(bus, "1", reecaData());
            dynamics.addRepca1(bus, "1", repcaData());
        }

        DynamicSimuAlgorithm algorithm = DStabObjectFactory.createDynamicSimuAlgorithm(network);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(withFault ? .5 : 1.0);
        algorithm.setOutPutPerSteps(1);
        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(new String[] {"Poi"});
        algorithm.setSimuOutputHandler(monitor);
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "reduced-network load flow");
        if (withFault) {
            network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                    "Poi", network, SimpleFaultCode.GROUND_3P,
                    new Complex(0, 1.0e-4), null, .05, .05),
                    "ThreeCycleFault@Poi");
        }
        assertTrue(algorithm.initialization(), "reduced-network dynamic initialization");

        Map<String, Double> initialVoltage = new LinkedHashMap<>();
        network.getBusList().forEach(bus -> initialVoltage.put(bus.getId(), bus.getVoltageMag()));
        assertTrue(algorithm.performSimulation(), "reduced-network no-event simulation");
        double maximumDrift = initialVoltage.entrySet().stream()
                .mapToDouble(entry -> Math.abs(network.getBus(entry.getKey()).getVoltageMag()
                        - entry.getValue()))
                .max().orElseThrow();
        var poiVoltage = monitor.getBusVoltTable().get("Poi");
        double minimumPoiVoltage = poiVoltage.values().stream()
                .mapToDouble(value -> value.value).min().orElseThrow();
        double finalPoiVoltage = poiVoltage.get(poiVoltage.size() - 1).value;
        return new RunResult(maximumDrift, minimumPoiVoltage, finalPoiVoltage);
    }

    private static Regca1Data regcaData() {
        return new Regca1Data(1, .02, 10, .9, .5, 1.22, 1.2, .8,
                .4, -1.3, .02, .7, 0, 0, .8);
    }

    private static Reeca1Data reecaData() {
        return new Reeca1Data(0, 0, 1, 1, 0, 0,
                .85, 1.15, .02, 0, 0, 5, 1.1, -1.1, 0, 0, 0, .5,
                .02, .436, -.436, 1.1, .9, 1.3, 2.1, 2.8, 2.9, 0, .02,
                99, -99, 1, 0, 1.3, .03,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Repca1Data repcaData() {
        return new Repca1Data(0, 0, 0, "0", 1, 1, 0,
                .02, 7, 4.1, 0, 1.48, .7, 0, 0, 0,
                1, -1, 0, 0, 1, -1, .2, .2, .02,
                0, 0, 1, -1, 2, 0, .1, 20, 0, 0);
    }

    private record RunResult(double maximumVoltageDrift, double minimumPoiVoltage,
            double finalPoiVoltage) { }
}
