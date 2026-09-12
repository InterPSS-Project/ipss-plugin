package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.PowerWorldCsvReference;
import org.interpss.dstab.renewable.Regfma1Data;
import org.interpss.dstab.renewable.Regfma1Model;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.pwd.PWDDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Direct REGFM_A1 nine-state comparison against a reproducible PowerWorld run. */
public class Regfma1PowerWorldTrajectoryTest extends CorePluginTestSetup {
    private static final double STEP = 0.0005;

    @Test
    void threeCycleTerminalFaultMatchesPowerWorldStates() throws Exception {
        DStabilityNetwork network = DStabObjectFactory.createDStabilityNetwork();
        new PWDDirectParser(new AclfNetworkBuilder(network)).parseInto(
                Path.of("testData", "adpter", "pwd", "regfma1_smib.aux").toString());
        network.setBypassDataCheck(true);

        var dynamics = new DStabNetworkBuilder(network);
        Regfma1Model model = dynamics.addRegfma1("Bus3", "1", new Regfma1Data(
                0, .02, .02, .02, 2, 1.2, 0, 1, 0, 1, -1,
                .01, .05, .01, .1, 3, 20, 0, 6));
        dynamics.addInfiniteMachine("Bus1", "1");

        var algorithm = DStabObjectFactory.createDynamicSimuAlgorithm(network);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "REGFMA1 benchmark load flow");
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus3", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 1.0), null, .05, .05), "Regfma1Fault");
        assertTrue(algorithm.initialization(), "REGFMA1 PowerWorld benchmark initialization");
        System.out.printf(Locale.ROOT,
                "REGFMA1 initial: v3=%.9g v2=%.9g v1=%.9g p=%.9g q=%.9g "
                + "angle=%.9g e=%.9g%n",
                network.getBus("Bus3").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(),
                network.getBus("Bus1").getVoltageMag(),
                model.getActivePower(), model.getReactivePower(), model.getAngle(),
                model.getInternalVoltage());

        List<double[]> actual = new ArrayList<>();
        boolean[] currentLimitSeen = new boolean[2];
        record(actual, algorithm.getSimuTime(), network, model, currentLimitSeen);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "REGFMA1 solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, model, currentLimitSeen);
        }
        assertTrue(currentLimitSeen[1], "trajectory must also exercise the unlimited region");

        PowerWorldCsvReference reference = PowerWorldCsvReference.read(Path.of(
                "testData", "reference", "powerworld", "regfma1-bus1062", "powerworld.csv"));
        int[] field = {
                reference.fieldIndex("Bus", "3", "TSVpu"),
                reference.fieldIndex("Bus", "2", "TSVpu"),
                reference.fieldIndex("Bus", "1", "TSVpu"),
                reference.fieldIndex("Generator", "3 1", "TSMW"),
                reference.fieldIndex("Generator", "3 1", "TSMvar"),
                reference.fieldIndex("Generator", "3 1", "TSMachineState:1"),
                reference.fieldIndex("Generator", "3 1", "TSMachineState:2"),
                reference.fieldIndex("Generator", "3 1", "TSMachineState:3"),
                reference.fieldIndex("Generator", "3 1", "TSMachineState:4"),
                reference.fieldIndex("Generator", "3 1", "TSMachineState:5"),
                reference.fieldIndex("Generator", "3 1", "TSMachineState:6"),
                reference.fieldIndex("Generator", "3 1", "TSMachineState:7"),
                reference.fieldIndex("Generator", "3 1", "TSMachineState:8"),
                reference.fieldIndex("Generator", "3 1", "TSMachineState:9")
        };
        double[] maximum = new double[field.length];
        double[] maximumTime = new double[field.length];
        for (var expected : reference.postEventSamples()) {
            if (Math.abs(expected.time() - .05) < STEP
                    || Math.abs(expected.time() - .10) < STEP) continue;
            double[] row = interpolate(actual, expected.time());
            for (int column = 0; column < field.length; column++) {
                double error = Math.abs(row[column + 1] - expected.value(field[column]));
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = expected.time();
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "REGFMA1 PowerWorld max errors: v3=%.9g v2=%.9g v1=%.9g pMW=%.9g "
                + "qMvar=%.9g angle=%.9g intE=%.9g pMeas=%.9g qMeas=%.9g "
                + "vMeas=%.9g pHi=%.9g pLo=%.9g qHi=%.9g qLo=%.9g%n",
                Arrays.stream(maximum).boxed().toArray());

        double[] tolerance = {
                2.4e-3, 3.8e-3, 5.2e-3, .35, 1.05,
                1.5e-3, 2.0e-5, 3.0e-3, 1.0e-2, 2.2e-3,
                1.0e-9, 1.0e-9, 1.0e-9, 1.0e-9
        };
        for (int index = 0; index < maximum.length; index++) {
            assertTrue(maximum[index] <= tolerance[index], String.format(Locale.ROOT,
                    "channel %d max error %.9g at %.9g s exceeds %.9g",
                    index, maximum[index], maximumTime[index], tolerance[index]));
        }
    }

    private static void record(List<double[]> rows, double time, DStabilityNetwork network,
            Regfma1Model model, boolean[] currentLimitSeen) {
        model.getOutputObject();
        if (model.isCurrentLimited()) currentLimitSeen[0] = true;
        else currentLimitSeen[1] = true;
        rows.add(new double[] {
                time,
                network.getBus("Bus3").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(),
                network.getBus("Bus1").getVoltageMag(),
                model.getActivePower() * 100.0,
                model.getReactivePower() * 100.0,
                model.getAngle(), model.getVoltageIntegral(),
                model.getMeasuredActivePower(), model.getMeasuredReactivePower(),
                model.getMeasuredVoltage(), model.getActiveUpperLimitIntegral(),
                model.getActiveLowerLimitIntegral(), model.getReactiveUpperLimitIntegral(),
                model.getReactiveLowerLimitIntegral()
        });
    }

    private static double[] interpolate(List<double[]> rows, double target) {
        for (int index = 0; index < rows.size(); index++) {
            double[] lower = rows.get(index);
            if (Math.abs(lower[0] - target) < 1.0e-9) return lower;
            if (index + 1 < rows.size() && rows.get(index + 1)[0] > target) {
                double[] upper = rows.get(index + 1);
                double fraction = (target - lower[0]) / (upper[0] - lower[0]);
                double[] result = new double[lower.length];
                result[0] = target;
                for (int column = 1; column < result.length; column++) {
                    result[column] = lower[column]
                            + fraction * (upper[column] - lower[column]);
                }
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }
}
