package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1ExciterData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractStabilizer;
import com.interpss.dstab.mach.Machine;

public class PsseEsst1aExciterTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void directParserMapsAllTwentyPsseParameters(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("esst1a.dyr");
        Files.writeString(dyr, "1 'ESST1A' 1 1 1 0 0.1 -0.1 1.22 4.4 1 4.44 "
                + "472 0 5 -5 5 -5 0.17 0.2 1.1 0.3 0.4 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        IEEE1981ST1Exciter exciter = (IEEE1981ST1Exciter) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);
        IEEE1981ST1ExciterData d = exciter.getData();
        assertEquals(1, d.getUel()); assertEquals(1, d.getVos());
        assertEquals(0, d.getTr(), TOL); assertEquals(.1, d.getVimax(), TOL);
        assertEquals(-.1, d.getVimin(), TOL); assertEquals(1.22, d.getTc(), TOL);
        assertEquals(4.4, d.getTb(), TOL); assertEquals(1, d.getTc1(), TOL);
        assertEquals(4.44, d.getTb1(), TOL); assertEquals(472, d.getKa(), TOL);
        assertEquals(0, d.getTa(), TOL); assertEquals(5, d.getVamax(), TOL);
        assertEquals(-5, d.getVamin(), TOL); assertEquals(5, d.getVrmax(), TOL);
        assertEquals(-5, d.getVrmin(), TOL); assertEquals(.17, d.getKc(), TOL);
        assertEquals(.2, d.getKf(), TOL); assertEquals(1.1, d.getTf(), TOL);
        assertEquals(.3, d.getKlr(), TOL); assertEquals(.4, d.getIlr(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void texasProfileInitializesAndRemainsAtSolvedFieldVoltage() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(builder.getDStabNetwork());
        assertTrue(loadflow.loadflow());
        assertTrue(machine.initStates(builder.getDStabNetwork().getDStabBus("Bus1")));
        IEEE1981ST1Exciter exciter = builder.addExcEsst1a("Bus1", "1",
                1, 1, 0, .1, -.1, 1.22, 4.4, 1, 4.44,
                472, 0, 5, -5, 5, -5, .17, 0, 1, 0, 0);

        assertNotNull(exciter);
        assertSame(exciter, machine.getExciter());
        assertTrue(exciter.initStates(builder.getDStabNetwork().getDStabBus("Bus1"), machine));
        assertEquals(machine.getEfd(), exciter.getOutput(machine), 1.0e-8);
        for (int i = 0; i < 20; i++) {
            exciter.nextStep(.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
            exciter.nextStep(.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
        }
        assertEquals(machine.getEfd(), exciter.getOutput(machine), 1.0e-6);
    }

    @Test
    void uelCodesRouteToTheDocumentedThreeLocations() throws Exception {
        ExciterFixture errorInput = createLimiterFixture(1, 1, 0.0);
        double efd0 = errorInput.machine().getEfd();
        errorInput.exciter().setVuel(0.20);
        step(errorInput, 0.005);
        assertEquals(efd0 + 0.20, errorInput.exciter().getOutput(errorInput.machine()), 1.0e-8,
                "UEL=1 must add to the voltage-error summing junction");

        ExciterFixture gate1 = createLimiterFixture(2, 1, 0.0);
        double gate1Floor = gate1.machine().getEfd() + 0.25;
        gate1.exciter().setVuel(gate1Floor);
        assertEquals(gate1Floor, gate1.exciter().getUelGate1Output(), 1.0e-9,
                "UEL=2 must drive high-value gate 1 after the Vi limiter");
        step(gate1, 0.005);
        assertEquals(gate1Floor, gate1.exciter().getOutput(gate1.machine()), 1.0e-8);

        ExciterFixture gate2 = createLimiterFixture(3, 1, 0.0);
        double gate2Floor = gate2.machine().getEfd() + 0.30;
        gate2.exciter().setVuel(gate2Floor);
        assertEquals(gate2Floor, gate2.exciter().getOutput(gate2.machine()), 1.0e-9,
                "UEL=3 must drive high-value gate 2 after the regulator");
    }

    @Test
    void overExcitationLimiterIsTheFinalLowValueGate() throws Exception {
        ExciterFixture fixture = createLimiterFixture(1, 1, 0.0);
        double ceiling = fixture.machine().getEfd() - 0.15;
        fixture.exciter().setVoel(ceiling);
        assertEquals(ceiling, fixture.exciter().getOutput(fixture.machine()), 1.0e-9);
        fixture.exciter().clearVoel();
        assertEquals(fixture.machine().getEfd(), fixture.exciter().getOutput(fixture.machine()), 1.0e-9);
    }

    @Test
    void stabilizerFlagSelectsErrorOrPostGateSummingJunction() throws Exception {
        double stabilizerSignal = 0.20;
        ExciterFixture errorInput = createLimiterFixture(1, 1, stabilizerSignal);
        assertEquals(errorInput.machine().getEfd(), errorInput.exciter().getRegulatorOutput(), 1.0e-9,
                "VOS=1 must be incorporated before the regulator");

        ExciterFixture postGate = createLimiterFixture(1, 2, stabilizerSignal);
        assertEquals(postGate.machine().getEfd() - stabilizerSignal,
                postGate.exciter().getRegulatorOutput(), 1.0e-9,
                "VOS=2 must not be folded into the regulator state during initialization");
        assertEquals(postGate.machine().getEfd(), postGate.exciter().getPostRegulatorSignal(), 1.0e-9,
                "VOS=2 must be added after high-value gate 1");
        assertEquals(postGate.machine().getEfd(), postGate.exciter().getOutput(postGate.machine()), 1.0e-9);
    }

    private static ExciterFixture createLimiterFixture(int uel, int vos, double pssOutput)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(builder.getDStabNetwork());
        assertTrue(loadflow.loadflow());
        assertTrue(machine.initStates(builder.getDStabNetwork().getDStabBus("Bus1")));
        if (pssOutput != 0.0) {
            new FixedOutputStabilizer(machine, pssOutput);
        }
        IEEE1981ST1Exciter exciter = builder.addExcEsst1a("Bus1", "1",
                uel, vos, 0.0, 100.0, -100.0, 0.0, 0.0, 0.0, 0.0,
                1.0, 0.0, 100.0, -100.0, 100.0, -100.0,
                0.0, 0.0, 1.0, 0.0, 0.0);
        assertNotNull(exciter);
        assertTrue(exciter.initStates(builder.getDStabNetwork().getDStabBus("Bus1"), machine));
        assertEquals(machine.getEfd(), exciter.getOutput(machine), 1.0e-9);
        return new ExciterFixture(exciter, machine);
    }

    private static void step(ExciterFixture fixture, double dt) {
        assertTrue(fixture.exciter().nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                fixture.machine(), 0));
        assertTrue(fixture.exciter().nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                fixture.machine(), 1));
    }

    private record ExciterFixture(IEEE1981ST1Exciter exciter, Machine machine) { }

    private static final class FixedOutputStabilizer extends AbstractStabilizer {
        private final double output;

        FixedOutputStabilizer(Machine machine, double output) {
            super("fixed-pss", "Fixed PSS", "test");
            this.output = output;
            setMachine(machine);
        }

        @Override
        public double getOutput(Machine machine) {
            return output;
        }
    }
}
