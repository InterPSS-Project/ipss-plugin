package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Eq1Ed1Machine;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.mach.SalientPoleMachine;
import com.interpss.dstab.mach.impl.DynamicMachineImpl;
import com.interpss.dstab.util.sample.SampleDStabCase;

/** Equation-level conformance checks for the shared core GENSAL model. */
public class GensalConformanceTest extends TestSetupBase {
    private static final double TOL = 1.0e-8;

    @ParameterizedTest
    @CsvSource({"0.98, 0", "1.02, 0", "0.98, 2", "1.02, 2"})
    void nativeMechanicalStagesConvertMechanicalPowerAndDampingToTorque(
            double speed, double psseDamping) throws Exception {
        BaseDStabNetwork<?, ?> network = SampleDStabCase.createDStabTestNet();
        SalientPoleMachine machine = new DStabNetworkBuilder(network).addGensal(
                "Gen", "G1", 100.0, 1.0, 5, .05, .07, 3.5, psseDamping,
                1.8, 1.7, .3, .2, .1, 0, 0);
        machine.setSpeed(speed);
        machine.setAngle(.4);
        machine.setPm(.8);
        machine.setPe(.6);
        double step = .0005;
        double damping = psseDamping * (speed - 1);
        double first = ((.8 - damping) / speed - .6) / 7;
        double predicted = speed + step * first;
        double predictedAngle = .4 + step * (speed - 1) * 2 * Math.PI * network.getFrequency();
        assertTrue(machine.nextStepMechanical(step, DynamicSimuMethod.MODIFIED_EULER, network, 0));
        assertEquals(predicted, machine.getSpeed(), 1.e-14);
        assertEquals(predictedAngle, machine.getAngle(), 1.e-14);
        double second = ((.8 - psseDamping * (predicted - 1)) / predicted - .6) / 7;
        assertTrue(machine.nextStepMechanical(step, DynamicSimuMethod.MODIFIED_EULER, network, 1));
        assertEquals(speed + .5 * step * (first + second), machine.getSpeed(), 1.e-14);
        assertEquals(.4 + .5 * step * (speed + predicted - 2)
                * 2 * Math.PI * network.getFrequency(), machine.getAngle(), 1.e-14);
    }

    @Test
    void salientPoleStatesFieldCurrentTorqueAndNortonInjectionMatchReference() throws Exception {
        BaseDStabNetwork<?, ?> network = SampleDStabCase.createDStabTestNet();
        SalientPoleMachine machine = new DStabNetworkBuilder(network).addGensal(
                "Gen", "G1", 100.0, 1.0,
                8.0, 0.03, 0.05,
                4.0, 2.0,
                1.80, 1.70, 0.30, 0.25, 0.15,
                10.0, 20.0);
        BaseDStabBus<?, ?> bus = network.getDStabBus("Gen");
        bus.initStates();
        assertTrue(machine.initStates(bus));

        // GENSAL has Eq', one d-axis damper state, and one q-axis damper
        // flux; it deliberately has no q-axis transient Ed' state.
        assertFalse(machine instanceof Eq1Ed1Machine);

        Complex expectedTerminalCurrent = bus.getContributeGen("G1").getGen()
                .divide(bus.getVoltage()).conjugate();
        Complex actualTerminalCurrent = machine.getIgen()
                .subtract(bus.getVoltage().multiply(machine.getYgen()));
        assertEquals(expectedTerminalCurrent.getReal(), actualTerminalCurrent.getReal(), 1.0e-7);
        assertEquals(expectedTerminalCurrent.getImaginary(), actualTerminalCurrent.getImaginary(), 1.0e-7);

        var idq = machine.getIdq();
        var vdq = machine.getVdq();
        assertEquals(-machine.getPsiq11() - machine.getRa() * idq.d
                + machine.getXq11() * idq.q, vdq.d, 1.0e-7);
        assertEquals(machine.getPsid11() - machine.getXd11() * idq.d
                - machine.getRa() * idq.q, vdq.q, 1.0e-7);
        assertEquals(machine.getPsid11() * idq.q - machine.getPsiq11() * idq.d,
                machine.getPe(), 1.0e-7);
        assertFieldCurrent(machine);

        bus.setVoltage(new Complex(0.97, -0.025));
        machine.getIgen();
        idq = machine.getIdq();
        double eq1 = machine.getEq1();
        double psikd = machine.getPsikd();
        double psiq11 = machine.getPsiq11();
        double xl = machine.getXl();
        double xpp = machine.getXd11();
        double tempD = (machine.getXd1() - xpp) / Math.pow(machine.getXd1() - xl, 2)
                * (-psikd - (machine.getXd1() - xl) * idq.d + eq1);
        double xadIfd = eq1 * (1.0 + core(machine).getSatruationFactor(eq1))
                + (machine.getMachData().getXd() - machine.getXd1()) * (idq.d + tempD);
        assertEquals(xadIfd, machine.calculateIfd(MachineIfdBase.EXCITER), 1.0e-7);
        double dEq1 = (machine.getEfd() - xadIfd) / machine.getTd01();
        double dPsikd = (-psikd - (machine.getXd1() - xl) * idq.d + eq1)
                / machine.getTd011();
        double dPsiq11 = (-psiq11 + (xpp - machine.getXq()) * idq.q)
                / machine.getTq011();

        double dt = 0.001;
        assertTrue(core(machine).nextStepElectrical(
                dt, DynamicSimuMethod.MODIFIED_EULER, network, 0));
        assertEquals(eq1 + dEq1 * dt, machine.getEq1(), TOL);
        assertEquals(psikd + dPsikd * dt, machine.getPsikd(), TOL);
        assertEquals(psiq11 + dPsiq11 * dt, machine.getPsiq11(), TOL);

    }

    private static void assertFieldCurrent(SalientPoleMachine machine) {
        var idq = machine.getIdq();
        double xl = machine.getXl();
        double tempD = (machine.getXd1() - machine.getXd11())
                / Math.pow(machine.getXd1() - xl, 2)
                * (-machine.getPsikd() - (machine.getXd1() - xl) * idq.d + machine.getEq1());
        double xadIfd = machine.getEq1()
                * (1.0 + core(machine).getSatruationFactor(machine.getEq1()))
                + (machine.getMachData().getXd() - machine.getXd1()) * (idq.d + tempD);
        assertEquals(xadIfd, machine.calculateIfd(MachineIfdBase.EXCITER), 1.0e-7);
        assertEquals(xadIfd / (machine.getXdAdjusted() - xl),
                machine.calculateIfd(MachineIfdBase.MACHINE), 1.0e-7);
    }

    private static DynamicMachineImpl core(SalientPoleMachine machine) {
        return (DynamicMachineImpl) machine;
    }
}
