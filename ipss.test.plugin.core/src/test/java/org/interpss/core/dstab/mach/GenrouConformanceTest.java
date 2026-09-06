package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.mach.RoundRotorMachine;
import com.interpss.dstab.mach.impl.DynamicMachineImpl;
import com.interpss.dstab.util.sample.SampleDStabCase;

/** Equation-level conformance checks for the shared core GENROU model. */
public class GenrouConformanceTest extends TestSetupBase {
    private static final double TOL = 1.0e-8;

    @Test
    void quadraticSaturationPassesSpecifiedPointsAndExtrapolates() throws Exception {
        RoundRotorMachine machine = createMachine(3.0, 10.0, 20.0).machine();

        assertEquals(0.10, core(machine).getSatruationFactor(1.0), TOL);
        assertEquals(0.20, core(machine).getSatruationFactor(1.2), TOL);

        double x = Math.sqrt(1.2 * 0.20 / 0.10);
        double a = (1.2 - x) / (1.0 - x);
        double b = 0.10 / Math.pow(1.0 - a, 2);
        assertEquals(0.0, core(machine).getSatruationFactor(a - 0.05), TOL);
        assertEquals(b * Math.pow(1.10 - a, 2) / 1.10,
                core(machine).getSatruationFactor(1.10), TOL);
        assertEquals(b * Math.pow(1.40 - a, 2) / 1.40,
                core(machine).getSatruationFactor(1.40), TOL);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 3.0})
    void zeroSaturationAndDampingCombinationsRemainWellDefined(double psseD) throws Exception {
        MachineFixture fixture = createMachine(psseD, 0.0, 0.0);
        RoundRotorMachine machine = fixture.machine();

        assertEquals(0.0, core(machine).getSatruationFactor(0.80), TOL);
        assertEquals(0.0, core(machine).getSatruationFactor(1.00), TOL);
        assertEquals(0.0, core(machine).getSatruationFactor(1.40), TOL);
        initialize(fixture);
        assertTrue(Double.isFinite(machine.calculateIfd(MachineIfdBase.MACHINE)));
    }

    @Test
    void stateEquationsFieldCurrentAndNortonInjectionMatchPublishedGenrou() throws Exception {
        MachineFixture fixture = createMachine(3.0, 10.0, 20.0);
        RoundRotorMachine machine = fixture.machine();
        BaseDStabBus<?, ?> bus = fixture.bus();
        initialize(fixture);

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

        // Displace terminal voltage so all four state residuals are observable.
        bus.setVoltage(new Complex(0.98, 0.02));
        machine.getIgen();
        idq = machine.getIdq();

        double eq1 = machine.getEq1();
        double psikd = machine.getPsikd();
        double psikq = machine.getPsikq();
        double ed1 = machine.getEd1();
        double xpp = machine.getXd11();
        double xl = machine.getXl();
        double psiq = -ed1 * (xpp - xl) / (machine.getXq1() - xl)
                - psikq * (machine.getXq1() - xpp) / (machine.getXq1() - xl);
        double psid = eq1 * (xpp - xl) / (machine.getXd1() - xl)
                + psikd * (machine.getXd1() - xpp) / (machine.getXd1() - xl);
        double tempD = (machine.getXd1() - xpp) / Math.pow(machine.getXd1() - xl, 2)
                * (-psikd - (machine.getXd1() - xl) * idq.d + eq1);
        double tempQ = (machine.getXq1() - xpp) / Math.pow(machine.getXq1() - xl, 2)
                * (-psikq + (machine.getXq1() - xl) * idq.q + ed1);
        double se = core(machine).getSatruationFactor(Math.hypot(psiq, psid));
        double xadIfd = eq1 + se * psid
                + (machine.getMachData().getXd() - machine.getXd1()) * (idq.d + tempD);

        double dEq1 = (machine.getEfd() - xadIfd) / machine.getTd01();
        double dPsikd = (-psikd - (machine.getXd1() - xl) * idq.d + eq1)
                / machine.getTd011();
        double dPsikq = (-psikq + (machine.getXq1() - xl) * idq.q + ed1)
                / machine.getTq011();
        double dEd1 = (-ed1 + (machine.getXq() - machine.getXq1()) * (idq.q - tempQ)
                + se * psiq * (machine.getXq() - xl)
                        / (machine.getMachData().getXd() - xl))
                / machine.getTq01();

        double dt = 0.001;
        assertTrue(((DynamicMachineImpl) machine).nextStepElectrical(
                dt, DynamicSimuMethod.MODIFIED_EULER, fixture.network(), 0));
        assertEquals(eq1 + dEq1 * dt, machine.getEq1(), TOL);
        assertEquals(psikd + dPsikd * dt, machine.getPsikd(), TOL);
        assertEquals(psikq + dPsikq * dt, machine.getPsikq(), TOL);
        assertEquals(ed1 + dEd1 * dt, machine.getEd1(), TOL);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 3.0})
    void mechanicalPredictorUsesInertiaAndPsseDampingUnits(double psseD) throws Exception {
        MachineFixture fixture = createMachine(psseD, 0.0, 0.0);
        RoundRotorMachine machine = fixture.machine();
        initialize(fixture);

        machine.setSpeed(1.01);
        machine.setPm(1.10);
        machine.setPe(0.80);
        double speed0 = machine.getSpeed();
        double angle0 = machine.getAngle();
        double frequency = fixture.network().getFrequency();
        double dampingPu = machine.getD() * 0.01 * frequency;
        double dSpeed = 0.5 * (machine.getPm() - machine.getPe()
                - dampingPu * (speed0 - 1.0)) / machine.getH();
        double dAngle = (speed0 - 1.0) * 2.0 * Math.PI * frequency;

        double dt = 0.002;
        assertTrue(((DynamicMachineImpl) machine).nextStepMechanical(
                dt, DynamicSimuMethod.MODIFIED_EULER, fixture.network(), 0));
        assertEquals(speed0 + dSpeed * dt, machine.getSpeed(), TOL);
        assertEquals(angle0 + dAngle * dt, machine.getAngle(), TOL);
        assertEquals(psseD, dampingPu, TOL);
    }

    private static void assertFieldCurrent(RoundRotorMachine machine) {
        var idq = machine.getIdq();
        double xpp = machine.getXd11();
        double xl = machine.getXl();
        double psiq = -machine.getEd1() * (xpp - xl) / (machine.getXq1() - xl)
                - machine.getPsikq() * (machine.getXq1() - xpp) / (machine.getXq1() - xl);
        double psid = machine.getEq1() * (xpp - xl) / (machine.getXd1() - xl)
                + machine.getPsikd() * (machine.getXd1() - xpp) / (machine.getXd1() - xl);
        double tempD = (machine.getXd1() - xpp) / Math.pow(machine.getXd1() - xl, 2)
                * (-machine.getPsikd() - (machine.getXd1() - xl) * idq.d + machine.getEq1());
        double xadIfd = machine.getEq1()
                + core(machine).getSatruationFactor(Math.hypot(psiq, psid)) * psid
                + (machine.getMachData().getXd() - machine.getXd1()) * (idq.d + tempD);

        assertEquals(xadIfd, machine.calculateIfd(MachineIfdBase.EXCITER), 1.0e-7);
        assertEquals(xadIfd / (machine.getXdAdjusted() - xl),
                machine.calculateIfd(MachineIfdBase.MACHINE), 1.0e-7);
    }

    private static void initialize(MachineFixture fixture) {
        fixture.bus().initStates();
        assertTrue(fixture.machine().initStates(fixture.bus()));
    }

    private static DynamicMachineImpl core(RoundRotorMachine machine) {
        return (DynamicMachineImpl) machine;
    }

    private static MachineFixture createMachine(double psseD, double se100, double se120)
            throws Exception {
        BaseDStabNetwork<?, ?> network = SampleDStabCase.createDStabTestNet();
        RoundRotorMachine machine = new DStabNetworkBuilder(network).addGenrou(
                "Gen", "G1", 100.0, 1.0,
                8.0, 0.03, 0.40, 0.05,
                5.0, psseD,
                1.80, 1.70, 0.30, 0.55, 0.25, 0.15,
                se100, se120);
        return new MachineFixture(network, network.getDStabBus("Gen"), machine);
    }

    private record MachineFixture(
            BaseDStabNetwork<?, ?> network,
            BaseDStabBus<?, ?> bus,
            RoundRotorMachine machine) {
    }
}
