package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.sexs.SexsData;
import org.interpss.dstab.control.exc.psse.sexs.SexsExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Schema, equation, limiter, state, and solver coverage for SEXS. */
public class SexsExciterTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void parsesTheExactSixParameterRecord(@TempDir Path dir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = dir.resolve("sexs.dyr");
        Files.writeString(dyr, "1 'SEXS' '1' .1 10 100 .1 0 3 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        SexsExciter exciter = (SexsExciter) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);
        SexsData data = exciter.getData();
        assertEquals(.1, data.getTaOverTb(), TOL);
        assertEquals(10, data.getTb(), TOL);
        assertEquals(100, data.getK(), TOL);
        assertEquals(.1, data.getTe(), TOL);
        assertEquals(0, data.getEmin(), TOL);
        assertEquals(3, data.getEmax(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());

        var descriptor = DynamicModelCatalog.find("SEXS").orElseThrow();
        assertEquals(6, descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE, descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("SEXS")
                .orElseThrow().isImplementedExactly());

        Path extra = dir.resolve("sexs-extra.dyr");
        Files.writeString(extra, "1 'SEXS' '1' .1 10 100 .1 0 3 99 /\n");
        assertThrows(InterpssException.class, () -> new PSSEDStabDirectParser(
                DStabBuilderTestFixture.createWithMachine()).setStrictImport(true)
                .parseDynFile(extra.toString()));
    }

    @Test
    void twoStatesMatchThePublishedLeadLagAndRegulatorEquations() throws Exception {
        Fixture fixture = fixture(baseData());
        assertEquals(List.of("First integrator", "Second integrator"),
                new ArrayList<>(fixture.exciter.getNamedStates().keySet()));
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint() + .1);

        double ratio = .2;
        double input = 1.2 / 10.0 + .1;
        double[] state = {fixture.exciter.getFirstIntegratorState(),
                fixture.exciter.getSecondIntegratorState()};
        double dt = .0001;
        double maximum = 0.0;
        for (int n = 0; n < 2000; n++) {
            double oldFirst = state[0];
            double oldSecond = state[1];
            double firstDerivative0 = ((1.0 - ratio) * input - oldFirst);
            double predictedFirst = oldFirst + firstDerivative0 * dt;
            double secondDerivative0 = (10.0 * (ratio * input + predictedFirst)
                    - oldSecond) / .1;
            double predictedSecond = oldSecond + secondDerivative0 * dt;
            double firstDerivative1 = ((1.0 - ratio) * input - predictedFirst);
            state[0] = oldFirst + .5 * (firstDerivative0 + firstDerivative1) * dt;
            double secondDerivative1 = (10.0 * (ratio * input + state[0])
                    - predictedSecond) / .1;
            state[1] = oldSecond + .5 * (secondDerivative0 + secondDerivative1) * dt;
            step(fixture.exciter, fixture.machine, dt);
            maximum = Math.max(maximum,
                    Math.abs(state[0] - fixture.exciter.getFirstIntegratorState()));
            maximum = Math.max(maximum,
                    Math.abs(state[1] - fixture.exciter.getSecondIntegratorState()));
        }
        assertTrue(maximum < 1.0e-11, "SEXS two-state maximum error=" + maximum);
    }

    @Test
    void appliesBothNonWindupLimitsAndInitializationAccommodation() throws Exception {
        SexsData upperData = baseData();
        upperData.setEmax(1.3);
        Fixture upper = fixture(upperData);
        upper.exciter.setRefPoint(upper.exciter.getRefPoint() + 10.0);
        double highest = Double.NEGATIVE_INFINITY;
        for (int n = 0; n < 20000; n++) {
            step(upper.exciter, upper.machine, .000001);
            highest = Math.max(highest, upper.exciter.getOutput(upper.machine));
        }
        assertTrue(highest <= 1.3 && highest >= 1.2998,
                "upper non-windup boundary was not approached from below: " + highest);

        SexsData lowerData = baseData();
        lowerData.setEmin(1.1);
        Fixture lower = fixture(lowerData);
        lower.exciter.setRefPoint(lower.exciter.getRefPoint() - 10.0);
        double lowest = Double.POSITIVE_INFINITY;
        for (int n = 0; n < 20000; n++) {
            step(lower.exciter, lower.machine, .000001);
            lowest = Math.min(lowest, lower.exciter.getOutput(lower.machine));
        }
        assertTrue(lowest >= 1.1 && lowest <= 1.1002,
                "lower non-windup boundary was not approached from above: " + lowest);

        SexsData accommodatedData = baseData();
        accommodatedData.setEmax(1.0);
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        SexsExciter accommodated = builder.addExcSexs("Bus1", "1", accommodatedData);
        assertTrue(accommodated.initStates(machine.getDStabBus(), machine));
        assertEquals(1.2, accommodated.emax, TOL);
        assertEquals(1.2, accommodated.getOutput(machine), TOL);
    }

    @Test
    void rejectsInvalidParametersAndParticipatesInTheFullSolver() throws Exception {
        DStabNetworkBuilder invalidBuilder = DStabBuilderTestFixture.createWithMachine();
        SexsData invalid = baseData();
        invalid.setTb(0.0);
        assertNull(invalidBuilder.addExcSexs("Bus1", "1", invalid));
        invalid = baseData();
        invalid.setEmin(2.0);
        invalid.setEmax(1.0);
        assertNull(invalidBuilder.addExcSexs("Bus1", "1", invalid));

        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcSexs("Bus1", "1", baseData()));
        DynamicSimuAlgorithm algorithm = DStabObjectFactory
                .createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.005);
        algorithm.setTotalSimuTimeSec(.02);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private static Fixture fixture(SexsData data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        SexsExciter exciter = builder.addExcSexs("Bus1", "1", data);
        assertNotNull(exciter);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, exciter);
    }

    private static SexsData baseData() {
        SexsData data = new SexsData();
        data.setTaOverTb(.2);
        data.setTb(1.0);
        data.setK(10.0);
        data.setTe(.1);
        data.setEmin(-5.0);
        data.setEmax(5.0);
        return data;
    }

    private static void step(SexsExciter exciter, Machine machine, double dt) {
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
    }

    private record Fixture(Machine machine, SexsExciter exciter) { }
}
