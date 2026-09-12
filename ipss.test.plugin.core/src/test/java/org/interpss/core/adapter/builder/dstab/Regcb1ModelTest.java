package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.renewable.Regcb1Data;
import org.interpss.dstab.renewable.Regcb1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Published schema, state, rate-limit, and current-priority checks for REGCB1. */
public class Regcb1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void flatAndWrappedRecordsResolveToTheSamePublishedModel() throws Exception {
        Regcb1Model flat = load("SMIB_v33_regcb1_psse36.dyr");
        Regcb1Model wrapped = load("SMIB_v33_regcbu1_psse36.dyr");

        assertEquals(data(0, 1), flat.getData());
        assertEquals(flat.getData(), wrapped.getData());
        assertArrayEquals(flat.getStateSnapshot(), wrapped.getStateSnapshot(), 1.0e-12);
        assertEquals(5, flat.getNamedStates().size());

        var descriptor = DynamicModelCatalog.find("REGCBU1").orElseThrow();
        assertEquals("REGCB1", descriptor.canonicalName());
        assertEquals(Set.of(9, 15), descriptor.recordSchema().acceptedParameterCounts());
    }

    @Test
    void initializationUsesTheFivePublishedCoordinatesAndIsStationary() throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        Regcb1Model model = builder.addRegcb1("Bus1", "1", data(0, 1));
        model.initializeWithSignals(1.0, .5, .2);
        double[] initial = model.getStateSnapshot();

        assertArrayEquals(new double[] {.5, .2, 1.0, .02, 1.008}, initial, 1.0e-12);
        step(model, .0005, 1.0, .5, -.2);
        assertArrayEquals(initial, model.getStateSnapshot(), 2.0e-12);
    }

    @Test
    void rateFlagChangesTheActiveLagCoordinateFromCurrentToPower() throws Exception {
        var currentBuilder = DStabBuilderTestFixture.createBuilder();
        Regcb1Model currentRate = currentBuilder.addRegcb1("Bus1", "1", data(0, 1));
        currentRate.initializeWithSignals(.8, .4, 0.0);

        var powerBuilder = DStabBuilderTestFixture.createBuilder();
        Regcb1Model powerRate = powerBuilder.addRegcb1("Bus1", "1", data(1, 1));
        powerRate.initializeWithSignals(.8, .4, 0.0);

        assertEquals(.5, currentRate.getStateSnapshot()[0], 1.0e-12);
        assertEquals(.4, powerRate.getStateSnapshot()[0], 1.0e-12);
        step(currentRate, .001, .8, 1.0, 0.0);
        step(powerRate, .001, .8, 1.0, 0.0);
        assertEquals(.5004, currentRate.getStateSnapshot()[0], 1.0e-12);
        assertEquals(.4004, powerRate.getStateSnapshot()[0], 1.0e-12);
    }

    @Test
    void priorityFlagSelectsWhichCommandSurvivesTheCurrentCircle() throws Exception {
        Regcb1Model pPriority = DStabBuilderTestFixture.createBuilder()
                .addRegcb1("Bus1", "1", unrestricted(1));
        pPriority.initializeWithSignals(1.0, 0.0, 0.0);
        for (int i = 0; i < 1000; i++) step(pPriority, .0005, 1.0, 2.0, 1.0);

        Regcb1Model qPriority = DStabBuilderTestFixture.createBuilder()
                .addRegcb1("Bus1", "1", unrestricted(0));
        qPriority.initializeWithSignals(1.0, 0.0, 0.0);
        for (int i = 0; i < 1000; i++) step(qPriority, .0005, 1.0, 2.0, 1.0);

        assertEquals(1.25, pPriority.getStateSnapshot()[0], 2.0e-9);
        assertEquals(0.0, pPriority.getStateSnapshot()[1], 2.0e-9);
        assertEquals(.75, qPriority.getStateSnapshot()[0], 2.0e-9);
        assertEquals(-1.0, qPriority.getStateSnapshot()[1], 2.0e-9);
    }

    @Test
    void enforcesMinimumTgAndDirectionalRecoveryLimits() throws Exception {
        Regcb1Model model = DStabBuilderTestFixture.createBuilder()
                .addRegcb1("Bus1", "1", new Regcb1Data(0, 1,
                        .0001, 0.0, .1, -.2, .3, 0.0, 1.25));
        model.initializeWithSignals(1.0, .5, .2);
        step(model, .001, 1.0, 1.0, -1.0);

        assertEquals(.002, model.getEffectiveTg(), 0.0);
        assertEquals(.5003, model.getStateSnapshot()[0], 1.0e-12);
        assertEquals(.2001, model.getStateSnapshot()[1], 1.0e-12);
    }

    @Test
    void rejectsWrongAllocationExtraFieldsAndInvalidSelectors(@TempDir Path tempDir)
            throws Exception {
        Path wrong = tempDir.resolve("wrong.dyr");
        Files.writeString(wrong,
                "1 'USRMDL' '1' 'REGCBU1' 101 1 2 7 4 8 " + parameters() + " /\n"
                        + "2 'GENCLS' '1' 99999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_regcb1_psse36.raw").toString(), wrong.toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1").getContributeGen("1");
        assertTrue(!(gen.getDynamicGenDevice() instanceof Regcb1Model));

        Path extra = tempDir.resolve("extra.dyr");
        Files.writeString(extra,
                "1 'REGCB1' '1' " + parameters() + " 99 /\n2 'GENCLS' '1' 99999 0 /\n");
        context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_regcb1_psse36.raw").toString(), extra.toString());
        gen = (DStabGen) context.getDStabilityNet().getBus("Bus1").getContributeGen("1");
        assertTrue(!(gen.getDynamicGenDevice() instanceof Regcb1Model));

        assertThrows(IllegalArgumentException.class,
                () -> new Regcb1Data(2, 1, .02, .01, 1, -1, .4, .01, 1.2));
    }

    private static Regcb1Model load(String dyr) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_regcb1_psse36.raw").toString(), CASE.resolve(dyr).toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1").getContributeGen("1");
        Regcb1Model model = assertInstanceOf(Regcb1Model.class, gen.getDynamicGenDevice());
        model.initializeWithSignals(1.0, .5, 0.0);
        return model;
    }

    private static void step(Regcb1Model model, double dt, double voltage,
            double ipcmd, double iqcmd) {
        assertTrue(model.nextStepWithSignals(dt, DynamicSimuMethod.MODIFIED_EULER, 0,
                voltage, ipcmd, iqcmd));
        assertTrue(model.nextStepWithSignals(dt, DynamicSimuMethod.MODIFIED_EULER, 1,
                voltage, ipcmd, iqcmd));
    }

    private static Regcb1Data data(int rateFlag, int priorityFlag) {
        return new Regcb1Data(rateFlag, priorityFlag, .021, .018, 70.0, -72.0,
                .40, .012, 1.25);
    }

    private static Regcb1Data unrestricted(int priorityFlag) {
        return new Regcb1Data(0, priorityFlag, .005, 0.0, 1000.0, -1000.0,
                1000.0, 0.0, 1.25);
    }

    private static String parameters() {
        return "0 1 0.021 0.018 70.0 -72.0 0.40 0.012 1.25";
    }
}
