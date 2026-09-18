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
import org.interpss.dstab.renewable.Dera1Data;
import org.interpss.dstab.renewable.Dera1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Published schema, state, control-path, and limiter checks for DERA1/DERAU1. */
public class Dera1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void flatAndWrappedRecordsResolveToTheSamePublishedModel() throws Exception {
        Dera1Model flat = load("SMIB_v33_dera1_psse36.dyr");
        Dera1Model wrapped = load("SMIB_v33_derau1_psse36.dyr");

        assertEquals(data(), flat.getData());
        assertEquals(flat.getData(), wrapped.getData());
        assertArrayEquals(flat.getStateSnapshot(), wrapped.getStateSnapshot(), 1.0e-12);
        assertEquals(10, flat.getNamedStates().size());

        var descriptor = DynamicModelCatalog.find("DERAU1").orElseThrow();
        assertEquals("DERA1", descriptor.canonicalName());
        assertEquals(Set.of(47, 53), descriptor.recordSchema().acceptedParameterCounts());
    }

    @Test
    void initializationIsStationaryAndCorrectsAnInvalidVoltageReference() throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        Dera1Model model = builder.addDera1("Bus1", "1", data());
        model.initializeWithSignals(1.04, .7164, .2705, 1.0);
        double[] initial = model.getStateSnapshot();

        assertTrue(model.nextStepWithSignals(.0005, DynamicSimuMethod.MODIFIED_EULER, 0,
                1.04, .7164, .2705, 1.0));
        assertTrue(model.nextStepWithSignals(.0005, DynamicSimuMethod.MODIFIED_EULER, 1,
                1.04, .7164, .2705, 1.0));
        assertArrayEquals(initial, model.getStateSnapshot(), 2.0e-12);
        assertEquals(0.0, model.getIqcmd(1.04, .7164, .2705, 1.0) - .2705 / 1.04,
                2.0e-12);
    }

    @Test
    void publishedPriorityFlagSelectsWhichCurrentSurvivesTheCircleLimit() throws Exception {
        var builder = DStabBuilderTestFixture.createBuilder();
        Dera1Model pPriority = builder.addDera1("Bus1", "1", withPriority(1));
        pPriority.initializeWithSignals(1.0, 1.0, .9, 1.0);
        assertEquals(1.0, pPriority.getIpcmd(1.0, 1.0, .9, 1.0), 1.0e-12);
        assertEquals(Math.sqrt(1.25 * 1.25 - 1.0),
                pPriority.getIqcmd(1.0, 1.0, .9, 1.0), 1.0e-12);

        var qBuilder = DStabBuilderTestFixture.createBuilder();
        Dera1Model qPriority = qBuilder.addDera1("Bus1", "1", withPriority(0));
        qPriority.initializeWithSignals(1.0, 1.0, .9, 1.0);
        assertEquals(.9, qPriority.getIqcmd(1.0, 1.0, .9, 1.0), 1.0e-12);
        assertEquals(Math.sqrt(1.25 * 1.25 - .9 * .9),
                qPriority.getIpcmd(1.0, 1.0, .9, 1.0), 1.0e-12);
    }

    @Test
    void frequencyDroopUsesDownAndUpCoefficientsOnTheirPublishedSides() throws Exception {
        var underBuilder = DStabBuilderTestFixture.createBuilder();
        Dera1Model under = underBuilder.addDera1("Bus1", "1", data());
        under.initializeWithSignals(1.0, .5, 0.0, 1.0);
        under.nextStepWithSignals(.01, DynamicSimuMethod.MODIFIED_EULER, 0,
                1.0, .5, 0.0, .995);
        under.nextStepWithSignals(.01, DynamicSimuMethod.MODIFIED_EULER, 1,
                1.0, .5, 0.0, .995);
        double underPi = under.getStateSnapshot()[6];

        var overBuilder = DStabBuilderTestFixture.createBuilder();
        Dera1Model over = overBuilder.addDera1("Bus1", "1", data());
        over.initializeWithSignals(1.0, .5, 0.0, 1.0);
        over.nextStepWithSignals(.01, DynamicSimuMethod.MODIFIED_EULER, 0,
                1.0, .5, 0.0, 1.005);
        over.nextStepWithSignals(.01, DynamicSimuMethod.MODIFIED_EULER, 1,
                1.0, .5, 0.0, 1.005);
        double overPi = over.getStateSnapshot()[6];

        assertTrue(underPi > .5);
        assertTrue(overPi < .5);
        assertTrue(.5 - overPi > underPi - .5,
                "Dup=22 must produce a larger response than Ddn=18 for this input pair");
    }

    @Test
    void rejectsWrongAllocationExtraFieldsAndInvalidLimits(@TempDir Path tempDir) throws Exception {
        Path wrong = tempDir.resolve("wrong.dyr");
        Files.writeString(wrong,
                "1 'USRMDL' '1' 'DERAU1' 101 1 6 41 9 23 " + parameters() + " /\n"
                        + "2 'GENCLS' '1' 99999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2_psse36.raw").toString(), wrong.toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1").getContributeGen("1");
        assertTrue(!(gen.getDynamicGenDevice() instanceof Dera1Model));

        assertThrows(IllegalArgumentException.class, () -> new Dera1Data(
                2, 1, 1, 1, 1, 1, .025, .03, -.11, .09, 4.5, 0,
                .024, .03, 18, 22, -.0007, .0008, .8, -.8, 1.05, 0,
                8, -7, .035, .12, .07, 1.25, .42, .78, 1.22, 1.14,
                .12, .18, .14, .19, .55, 59.1, 60.7, .18, .17,
                .025, 4.5, .03, .76, 1.1, -1.05));
    }

    private static Dera1Model load(String dyr) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2_psse36.raw").toString(), CASE.resolve(dyr).toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1").getContributeGen("1");
        Dera1Model model = assertInstanceOf(Dera1Model.class, gen.getDynamicGenDevice());
        model.initializeWithSignals(1.04, .7164, .2705, 1.0);
        return model;
    }

    private static Dera1Data data() {
        return withPriority(1);
    }

    private static Dera1Data withPriority(int pqFlag) {
        return new Dera1Data(1, 1, pqFlag, 1, 1, 1,
                .025, .03, -.11, .09, 4.5, 0, .024, .03, 18, 22,
                -.0007, .0008, .8, -.8, 1.05, 0, 8, -7, .035, .12,
                .07, 1.25, .42, .78, 1.22, 1.14, .12, .18, .14, .19,
                .55, 59.1, 60.7, .18, .17, .025, 4.5, .03, .76, 1.1, -1.05);
    }

    private static String parameters() {
        return "1 1 1 1 1 1 0.025 0.030 -0.11 0.09 4.5 0.0 0.024 0.030 "
                + "18.0 22.0 -0.0007 0.0008 0.8 -0.8 1.05 0.0 8.0 -7.0 0.035 "
                + "0.12 0.07 1.25 0.42 0.78 1.22 1.14 0.12 0.18 0.14 0.19 0.55 "
                + "59.1 60.7 0.18 0.17 0.025 4.5 0.030 0.76 1.10 -1.05";
    }
}
