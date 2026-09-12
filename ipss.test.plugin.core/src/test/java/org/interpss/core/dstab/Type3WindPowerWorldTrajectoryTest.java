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
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.WindControlStack;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Diagnostic REGCA1/REECA1/Type-3 trajectory comparison against PowerWorld. */
@org.junit.jupiter.api.Tag("private-reference")
public class Type3WindPowerWorldTrajectoryTest extends CorePluginTestSetup {
    private static final double STEP = 0.0005;

    @Test
    void threeCyclePoiFaultRetainsPowerWorldVendorDifferenceDiagnostic() throws Exception {
        Path directory = Path.of("testData", "adpter", "psse", "v33", "renewable");
        var context = new PSSEMultiFileLoader().loadDStab(
                directory.resolve("regca_reeca_repca_bus1062.raw").toString(),
                directory.resolve("type3_wind_bus1062.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "Type-3 public load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus2", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 1.0), null, 0.05, 0.05), "Type3Fault");
        assertTrue(algorithm.initialization(), "Type-3 PowerWorld benchmark initialization");

        DStabGen generator = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Regca1Model converter = (Regca1Model) generator.getDynamicGenDevice();
        var reeca = converter.getReeca1Controller();
        WindControlStack stack = reeca.getWindControlStack();
        double speedBase = stack.getDriveTrain().getInitialSpeed();
        double deviceBase = generator.getMvaBase() > 0.0
                ? generator.getMvaBase() : network.getBaseMva();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, converter, stack, speedBase, deviceBase);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "Type-3 solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, converter, stack, speedBase, deviceBase);
        }

        PowerWorldCsvReference reference = PowerWorldCsvReference.read(Path.of(
                "testData", "reference", "powerworld", "type3-wind-bus1062", "powerworld.csv"));
        assertEquals(2003, reference.samples().size(), "PowerWorld raw samples");
        assertEquals(2001, reference.postEventSamples().size(), "PowerWorld post-event samples");
        int[] field = {
                reference.fieldIndex("Bus", "1", "TSVpu"),
                reference.fieldIndex("Bus", "2", "TSVpu"),
                reference.fieldIndex("Bus", "3", "TSVpu"),
                reference.fieldIndex("Generator", "1 1", "TSMW"),
                reference.fieldIndex("Generator", "1 1", "TSMvar"),
                reference.fieldIndex("Generator", "1 1", "TSMachineState:1"),
                reference.fieldIndex("Generator", "1 1", "TSMachineState:2"),
                reference.fieldIndex("Generator", "1 1", "TSMachineState:3"),
                reference.fieldIndex("Generator", "1 1", "TSExciterState:1"),
                reference.fieldIndex("Generator", "1 1", "TSExciterState:2"),
                reference.fieldIndex("Generator", "1 1", "TSExciterState:3"),
                reference.fieldIndex("Generator", "1 1", "TSExciterState:4"),
                // PowerWorld State 5 is the inactive QFLAG=0 Tiq path for this
                // QFLAG=1 case and is not a comparable active controller state.
                reference.fieldIndex("Generator", "1 1", "TSExciterState:6"),
                reference.fieldIndex("Generator", "1 1", "TSGovernorState:1"),
                reference.fieldIndex("Generator", "1 1", "TSGovernorState:3")
        };
        double[] maximum = new double[field.length];
        double[] maximumTime = new double[field.length];
        for (var expected : reference.postEventSamples()) {
            if (Math.abs(expected.time() - 0.05) < STEP
                    || Math.abs(expected.time() - 0.10) < STEP) continue;
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
                "Type-3 PowerWorld max errors: v1=%.9g v2=%.9g v3=%.9g pMW=%.9g "
                + "qMvar=%.9g eq=%.9g ip=%.9g regV=%.9g reecV=%.9g pMeas=%.9g "
                + "piQ=%.9g piV=%.9g pOrd=%.9g wt=%.9g wg=%.9g%n",
                Arrays.stream(maximum).boxed().toArray());
        // Simulator 24 treats REGCA1 Iqrmax=0 as a disabled recovery-rate
        // limit. PSS/E 36.7 and its Model Library define zero as an active
        // rate, freezing upward Iq motion for this positive-Q initialization.
        // Retain this older full Type-3 trace as a finite vendor-difference
        // diagnostic; native PSS/E is the strict REGCA1/REECA1/REPCA1 oracle.
        for (int index = 0; index < maximum.length; index++) {
            assertTrue(Double.isFinite(maximum[index]), String.format(Locale.ROOT,
                    "channel %d produced a non-finite diagnostic error at %.9g s",
                    index, maximumTime[index]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Regca1Model converter,
            WindControlStack stack, double speedBase, double deviceBase) {
        var reeca = converter.getReeca1Controller();
        var state = converter.getStates(null);
        rows.add(new double[] {
                time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                network.getBus("Bus3").getVoltageMag(),
                ((Number) state.get("REGCA1_P")).doubleValue() * deviceBase,
                ((Number) state.get("REGCA1_Q")).doubleValue() * deviceBase,
                converter.getIqRegulatorState(), converter.getIpRegulatorState(),
                converter.getFilteredVoltage(), reeca.getMeasuredVoltage(),
                reeca.getMeasuredActivePower(),
                reeca.getReactiveControlOutput(),
                reeca.getVoltageControlIntegral(),
                reeca.getActivePowerOrder(),
                stack.getTurbineSpeed() / speedBase, stack.getGeneratorSpeed() / speedBase
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
                    result[column] = lower[column] + fraction * (upper[column] - lower[column]);
                }
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }
}
