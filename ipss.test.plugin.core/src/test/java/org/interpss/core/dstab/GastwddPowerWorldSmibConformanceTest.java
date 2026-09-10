package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.PowerWorldCsvReference;
import org.interpss.dstab.control.gov.psse.gastwd.PsseGastwddGovernor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Full-solver GENROU + native GASTWDD comparison against PowerWorld. */
public class GastwddPowerWorldSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void threeCycleFaultMatchesPowerWorldBoundaryMachineAndGovernorStates() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_gastwdd.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "GENROU + GASTWDD SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "GENROU + GASTWDD SMIB initialization");

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        PsseGastwddGovernor governor = (PsseGastwddGovernor) machine.getGovernor();
        assertEquals("GASTWDD", governor.getName());
        assertEquals(9, governor.getNamedStates().size());
        assertEquals(governor.getTemperatureCommand(), governor.getNamedState("Temp Control"));
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, governor,
                initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "GENROU + GASTWDD solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, governor,
                    initialRelativeAngle);
        }

        PowerWorldCsvReference reference = PowerWorldCsvReference.read(Path.of(
                "testData", "reference", "powerworld", "smib-genrou-gastwdd", "powerworld.csv"));
        assertEquals(2003, reference.samples().size(), "PowerWorld raw samples");
        assertEquals(2001, reference.postEventSamples().size(), "PowerWorld post-event samples");
        int[] field = new int[20];
        field[0] = reference.fieldIndex("Bus", "1", "TSVpu");
        field[1] = reference.fieldIndex("Bus", "2", "TSVpu");
        field[2] = reference.fieldIndex("Generator", "1 1", "TSMW");
        field[3] = reference.fieldIndex("Generator", "1 1", "TSMvar");
        field[4] = reference.fieldIndex("Generator", "1 1", "TSRotorAngle");
        field[5] = reference.fieldIndex("Generator", "1 1", "TSSpeed");
        for (int index = 0; index < 4; index++)
            field[index + 6] = reference.fieldIndex("Generator", "1 1", "TSMachineState:" + (index + 3));
        for (int index = 0; index < 10; index++)
            field[index + 10] = reference.fieldIndex("Generator", "1 1", "TSGovernorState:" + (index + 1));
        int referenceAngle = reference.fieldIndex("Generator", "2 1", "TSRotorAngle");
        int referenceSpeed = reference.fieldIndex("Generator", "2 1", "TSSpeed");
        var initial = reference.postEventSamples().get(0);
        double initialPowerWorldAngle = initial.value(field[4]) - initial.value(referenceAngle);
        double[] maximum = new double[field.length];
        double[] maximumTime = new double[field.length];
        for (var expected : reference.postEventSamples()) {
            if (Math.abs(expected.time() - 0.05) < STEP
                    || Math.abs(expected.time() - 0.10) < STEP) continue;
            double[] row = interpolate(actual, expected.time());
            double[] powerWorld = new double[field.length];
            for (int index = 0; index < field.length; index++) powerWorld[index] = expected.value(field[index]);
            powerWorld[4] -= expected.value(referenceAngle) + initialPowerWorldAngle;
            powerWorld[5] -= expected.value(referenceSpeed);
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - powerWorld[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = expected.time();
                }
            }
        }
        System.out.println("GASTWDD PowerWorld max errors: " + Arrays.toString(maximum));
        System.out.println("GASTWDD PowerWorld max-error times: " + Arrays.toString(maximumTime));
        double[] tolerance = {
                3.2e-4, 1.1e-4, 0.115, 0.215, 0.022, 9.0e-6, 3.2e-5, 1.4e-4,
                1.6e-4, 1.05e-4, 4.2e-5, 6.2e-4, 4.6e-4, 8.5e-5, 2.5e-5,
                1.0e-12, 3.6e-4, 5.2e-3, 8.5e-5, 1.0e-12
        };
        String[] label = {
                "Bus1 V", "Bus2 V", "P MW", "Q Mvar", "relative angle", "relative speed",
                "Eqp", "PsiDp", "PsiQpp", "Edp", "power transducer", "valve positioner",
                "fuel system", "radiation shield", "thermocouple", "temperature control",
                "turbine dynamics", "PID 1", "PID 2", "inactive state 10"
        };
        for (int index = 0; index < maximum.length; index++)
            assertTrue(maximum[index] < tolerance[index], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", label[index], maximum[index],
                    maximumTime[index], tolerance[index]));
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, RoundRotorMachine machine,
            Machine referenceMachine, PsseGastwddGovernor governor, double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        rows.add(new double[] {
                time, network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal() * 100.0, power.getImaginary() * 100.0,
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle() - initialRelativeAngle),
                machine.getSpeed() - referenceMachine.getSpeed(), machine.getEq1(), machine.getPsikd(),
                machine.getPsikq(), machine.getEd1(), governor.getPowerTransducer(),
                governor.getValvePosition(), governor.getFuelFlow(), governor.getRadiationShield(),
                governor.getThermocouple(), governor.getTemperatureCommand(), governor.getTurbineDynamics(),
                governor.getPidIntegralState(), governor.getSpeedCommand(), 0.0
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
                for (int column = 1; column < result.length; column++)
                    result[column] = lower[column] + fraction * (upper[column] - lower[column]);
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }
}
