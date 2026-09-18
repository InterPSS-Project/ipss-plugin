package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.EmbeddedTrajectoryReference;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.controller.cml.annotate.AbstractChildAnnotateController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.annotate.AnnotateGovernor;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Full-solver GENROU + native TGOV1 comparison against Independent. */
public class Tgov1IndependentSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void threeCycleFaultMatchesIndependentBoundaryMachineAndGovernorStates() throws Exception {
        verify("TGOV1", "SMIB_v33_genrou_tgov1.dyr", "smib-genrou-tgov1");
    }

    @Test
    void nativeTgov1dDeadbandMatchesIndependent() throws Exception {
        verify("TGOV1D", "SMIB_v33_genrou_tgov1d.dyr", "smib-genrou-tgov1d");
    }

    @Test
    void everyAnnotatedCmlModelFamilyInheritsNamedStateDiscovery() {
        assertTrue(ICMLStateProvider.class.isAssignableFrom(AnnotateExciter.class));
        assertTrue(ICMLStateProvider.class.isAssignableFrom(AnnotateGovernor.class));
        assertTrue(ICMLStateProvider.class.isAssignableFrom(AnnotateStabilizer.class));
        assertTrue(ICMLStateProvider.class.isAssignableFrom(AbstractChildAnnotateController.class));
    }

    private static void verify(String model, String dyr, String artifact) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve(dyr).toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "GENROU + " + model + " load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "GENROU + " + model + " initialization");

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        PsseTGov1SteamTurGovernor governor =
                (PsseTGov1SteamTurGovernor) machine.getGovernor();
        assertTrue(governor.getNamedStates().containsKey("t1DelayBlock"));
        var t1Wrapper = governor.getFieldWrapperList().stream()
                .filter(wrapper -> wrapper.getFieldName().equals("t1DelayBlock"))
                .findFirst().orElseThrow();
        assertSame(t1Wrapper.getField(),
                governor.getNamedStateBlocks().get("t1DelayBlock"));
        assertEquals(governor.getNamedStates().get("t1DelayBlock"),
                governor.getNamedState("t1DelayBlock"));
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, governor,
                initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "GENROU + " + model + " solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, governor,
                    initialRelativeAngle);
        }

        EmbeddedTrajectoryReference reference = EmbeddedTrajectoryReference.embedded(artifact);
        assertTrue(!reference.samples().isEmpty());
        assertTrue(!reference.postEventSamples().isEmpty());
        int[] field = {
                reference.fieldIndex("Bus", "1", "TSVpu"),
                reference.fieldIndex("Bus", "2", "TSVpu"),
                reference.fieldIndex("Generator", "1 1", "TSMW"),
                reference.fieldIndex("Generator", "1 1", "TSMvar"),
                reference.fieldIndex("Generator", "1 1", "TSRotorAngle"),
                reference.fieldIndex("Generator", "1 1", "TSSpeed"),
                reference.fieldIndex("Generator", "1 1", "TSMachineState:3"),
                reference.fieldIndex("Generator", "1 1", "TSMachineState:4"),
                reference.fieldIndex("Generator", "1 1", "TSMachineState:5"),
                reference.fieldIndex("Generator", "1 1", "TSMachineState:6"),
                reference.fieldIndex("Generator", "1 1", "TSGovernorState:1"),
                reference.fieldIndex("Generator", "1 1", "TSGovernorState:2")
        };
        int referenceAngle = reference.fieldIndex("Generator", "2 1", "TSRotorAngle");
        int referenceSpeed = reference.fieldIndex("Generator", "2 1", "TSSpeed");
        var initial = reference.postEventSamples().get(0);
        double initialReferenceAngle = initial.value(field[4]) - initial.value(referenceAngle);
        double[] maximum = new double[field.length];
        double[] maximumTime = new double[field.length];
        for (var expected : reference.postEventSamples()) {
            if (Math.abs(expected.time() - 0.05) < STEP
                    || Math.abs(expected.time() - 0.10) < STEP) continue;
            double[] row = interpolate(actual, expected.time());
            double[] referenceValues = new double[field.length];
            for (int index = 0; index < field.length; index++) {
                referenceValues[index] = expected.value(field[index]);
            }
            referenceValues[4] -= expected.value(referenceAngle) + initialReferenceAngle;
            referenceValues[5] -= expected.value(referenceSpeed);
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - referenceValues[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = expected.time();
                }
            }
        }
        System.out.printf(Locale.ROOT,
                model + " Independent max errors: v1=%.9g v2=%.9g pMW=%.9g qMvar=%.9g "
                + "angleDeg=%.9g speed=%.9g eqp=%.9g psiDp=%.9g psiQpp=%.9g "
                + "edp=%.9g turbinePower=%.9g valvePosition=%.9g%n",
                Arrays.stream(maximum).boxed().toArray());
        System.out.println(model + " Independent max-error times: " + Arrays.toString(maximumTime));
        double[] tolerance = {
                3.2e-4, 1.1e-4, 0.075, 0.21, 0.014, 5.8e-6,
                3.2e-5, 1.4e-4, 1.05e-4, 7.0e-5, 8.0e-6, 3.2e-5
        };
        String[] label = {
                "Bus1 V", "Bus2 V", "P MW", "Q Mvar", "relative angle",
                "relative speed", "Eqp", "PsiDp", "PsiQpp", "Edp",
                "turbine power", "valve position"
        };
        for (int index = 0; index < maximum.length; index++) {
            assertTrue(maximum[index] < tolerance[index], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", label[index], maximum[index],
                    maximumTime[index], tolerance[index]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            RoundRotorMachine machine, Machine referenceMachine,
            PsseTGov1SteamTurGovernor governor, double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex terminalCurrent = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(terminalCurrent.conjugate());
        rows.add(new double[] {
                time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal() * 100.0, power.getImaginary() * 100.0,
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle()
                        - initialRelativeAngle),
                machine.getSpeed() - referenceMachine.getSpeed(), machine.getEq1(),
                machine.getPsikd(), machine.getPsikq(), machine.getEd1(),
                governor.getTurbinePower(), governor.getValvePosition()
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
