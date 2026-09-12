package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Gewtgcu1Model;
import org.interpss.dstab.mach.Plntbu1Data;
import org.interpss.dstab.mach.Plntbu1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.cache.StateMonitor;

/** Synthetic equation, state, limit, and exact-wrapper checks for PLNTBU1. */
public class Plntbu1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void sevenStateModifiedEulerStepMatchesIndependentCalculation() {
        Plntbu1Model model = new Plntbu1Model(data(0, 0, 1, .2, .12, .25, .18));
        model.setBoundary(new Complex(1, 0), new Complex(1, 0),
                new Complex(.4, .12), 1.0);
        model.initialize();
        model.setAuxiliaryInputs(0, .02, .01);
        model.setBoundary(new Complex(.98, 0), new Complex(.98, 0),
                new Complex(.35, .08), .996);
        model.step(.01, 0);
        double predictorVoltage = model.getVoltageMeasurement();
        model.step(.01, 0);
        assertEquals(predictorVoltage, model.getVoltageMeasurement(), 0.0,
                "one shared plant controller must advance only once per predictor stage");
        model.step(.01, 1);

        assertEquals(1.003747, model.getVoltageMeasurement(), 1e-12);
        assertEquals(.11805, model.getReactiveMeasurement(), 1e-12);
        assertEquals(.000036, model.getReactiveIntegral(), 1e-12);
        assertEquals(.0006769444444444445, model.getReactiveLeadLagState(), 1e-12);
        assertEquals(.39804, model.getActiveMeasurement(), 1e-12);
        assertEquals(.0001755, model.getActiveIntegral(), 1e-12);
        assertEquals(.002323577160493827, model.getActiveLagState(), 1e-12);
        assertEquals(.002949208333333333, model.getReactiveOutput(), 1e-12);
        assertEquals(.002323577160493827, model.getActiveOutput(), 1e-12);
        assertEquals(7, model.getNamedStates().size());
        double correctedVoltage = model.getVoltageMeasurement();
        model.step(.01, 1);
        assertEquals(correctedVoltage, model.getVoltageMeasurement(), 0.0,
                "one shared plant controller must correct only once per solver stage");
    }

    @Test
    void voltageCompensationFreezeAndAlgebraicPathsRemainExplicit() {
        Plntbu1Model model = new Plntbu1Model(data(1, 1, 0, 0, 0, 0, 0));
        model.setBoundary(new Complex(1, 0), new Complex(1, 0),
                new Complex(.3, .09), 1.0);
        model.initialize();
        model.setAuxiliaryInputs(.04, 0, .03);
        model.setBoundary(new Complex(.50, 0), new Complex(.50, 0),
                new Complex(.25, .07), .985);
        model.step(.01, 0);
        model.step(.01, 1);

        assertEquals(0.0, model.getReactiveIntegral(), 0.0,
                "reactive integrator must freeze below Vfrz");
        assertEquals(.0882475, model.getActiveOutput(), 1e-12,
                "Fflag=0 must disable droop but retain active-power control");
        assertThrows(IllegalArgumentException.class, () -> data(0, 3, 1, .2, .1, .2, .1));
    }

    @Test
    void powerFactorReferenceUsesFilteredPAndQCoordinates() {
        Plntbu1Model model = new Plntbu1Model(data(0, 2, 0, 0, 0, 0, 0));
        model.setBoundary(Complex.ONE, Complex.ONE, new Complex(.4, .1), 1.0);
        assertTrue(model.initialize());
        model.setBoundary(Complex.ONE, Complex.ONE, new Complex(.32, .08), 1.0);
        model.step(.01, 0);
        model.step(.01, 1);
        assertEquals(0.0, model.getReactiveOutput(), 1e-12,
                "constant power factor must remain at equilibrium");

        model.setBoundary(Complex.ONE, Complex.ONE, new Complex(.32, .06), 1.0);
        model.step(.01, 0);
        model.step(.01, 1);
        assertTrue(model.getReactiveOutput() > 0.0);
    }

    @Test
    void exactUserBusWrapperCouplesToReaxbInEitherRecordOrder(@TempDir Path tempDir)
            throws Exception {
        IpssCorePlugin.init();
        Path dyr = tempDir.resolve("synthetic-plant-controller.dyr");
        Files.writeString(dyr, """
                1 'USRBUS' 'PLNTBU1' 504 0 7 28 7 15
                  1 1 2 '1' 0 0 1
                  .21 1.37 .63 .04 .13 .57 .012 .073 .045 .52 -.43 -.011 .016
                  .82 -.71 1.08 .47 .26 -.0025 .0035 .61 -.49 .91 -.79 .19 11.7 13.4 123 /
                1 'USRMDL' '1' 'GEWTGCU1' 101 1 2 18 3 3
                  40 0 1.5 .33403 .5 .9 2.775 1.2 1 .4 .9 10 .02 .4 0 .7 .55 .9 1 .1 /
                1 'USRMDL' '1' 'REAX3BU1' 107 0 1 7 2 4 1
                  .17 1.83 .94 .72 -.68 .81 -.63 /
                2 'GENCLS' '1' 99999 0 /
                """);
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(), dyr.toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1")
                .getContributeGen("1");
        Gewtgcu1Model host = (Gewtgcu1Model) gen.getDynamicGenDevice();
        assertNotNull(host.getAuxiliaryController());
        assertNotNull(host.getAuxiliaryController().getPlantController());
        assertEquals(41, DynamicModelCatalog.find("PLNTBU1").orElseThrow().parameterCount());
        assertEquals(1, host.getAuxiliaryController().getPlantController()
                .getData().frequencyFlag());

        var algorithm = context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        assertEquals(12, host.getNamedStates().size());
        assertTrue(algorithm.solveDEqnStep(true));
    }

    @Test
    void strictImportRejectsWrongUserBusAllocation(@TempDir Path tempDir) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString());
        Path dyr = tempDir.resolve("wrong-allocation.dyr");
        Files.writeString(dyr, """
                1 'USRBUS' 'PLNTBU1' 505 0 7 28 7 15
                  1 1 2 '1' 0 0 1
                  .21 1.37 .63 .04 .13 .57 .012 .073 .045 .52 -.43 -.011 .016
                  .82 -.71 1.08 .47 .26 -.0025 .0035 .61 -.49 .91 -.79 .19 11.7 13.4 123 /
                """);
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(
                new DStabNetworkBuilder(context.getDStabilityNet())).setStrictImport(true);
        assertThrows(Exception.class, () -> parser.parseDynFile(dyr.toString()));
    }

    private static Plntbu1Data data(int vcFlag, int refFlag, int frequencyFlag,
            double voltageTime, double reactiveLag, double activeTime, double activeLag) {
        return new Plntbu1Data(1, 1, 2, "1", vcFlag, refFlag, frequencyFlag,
                voltageTime, 1.4, .6, .03, reactiveLag, .55, .02, .08, .04,
                .5, -.4, -.01, .015, .8, -.7, 1.1, .45, activeTime,
                -.002, .003, .6, -.5, .9, -.8, activeLag, 12, 14, 0);
    }
}
