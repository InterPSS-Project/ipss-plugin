package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.interpss.dstab.renewable.Reecc1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;

/** Exact flat/wrapper schema and attachment checks for REECC1. */
public class PsseReecc1ControllerTest {
    private static final String REGCA =
            "1 'REGCA1' '1' 1 .02 10 .9 .5 1.22 1.2 .8 .4 -1.3 .02 .7 0 0 .8 /\n";
    private static final String PARAMETERS =
            "0 1 1 1 1 .88 1.12 .023 -.04 .03 .55 .91 -.87 0 .031 "
            + ".92 -.89 1.08 -.95 .12 .18 1.15 4.7 .019 1.8 -1.6 .96 -.88 "
            + "1.17 .037 .2 .75 1.3 1.05 0 0 0 0 .2 .72 1.3 1.08 0 0 0 0 "
            + "13750 .64 .92 .14";

    @Test
    void flatAndWrappedFormsAttachTheSameSevenStateController(@TempDir Path tempDir)
            throws Exception {
        Reecc1Model flat = parse(tempDir.resolve("flat.dyr"),
                REGCA + "1 'REECC1' '1' " + PARAMETERS + " /\n");
        Reecc1Model wrapped = parse(tempDir.resolve("wrapped.dyr"),
                REGCA + "1 'USRMDL' '1' 'REECCU1' 102 0 5 45 7 6 "
                        + PARAMETERS + " /\n");

        assertEquals(flat.getData(), wrapped.getData());
        assertEquals(7, flat.getNamedStates().size());
        assertEquals(.64, flat.getStateOfCharge(), 0.0);
        var descriptor = DynamicModelCatalog.find("REECCU1").orElseThrow();
        assertEquals("REECC1", descriptor.canonicalName());
        assertEquals(Set.of(50, 56), descriptor.recordSchema().acceptedParameterCounts());
    }

    @Test
    void rejectsAnIncorrectNativeAllocation(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("wrong.dyr");
        Files.writeString(dyr, REGCA
                + "1 'USRMDL' '1' 'REECCU1' 102 0 5 45 6 6 " + PARAMETERS + " /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder);
        parser.parseDynFile(dyr.toString());

        assertNull(controller(builder));
        assertTrue(!parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void energyIntegralRetainsOvershootWhileSocAndCurrentCommandsAreLimited(@TempDir Path tempDir)
            throws Exception {
        for (double direction : new double[]{1., -1.}) {
            Reecc1Model model = parse(tempDir.resolve("energy-" + direction + ".dyr"),
                    REGCA + "1 'REECC1' '1' "
                    + PARAMETERS.replace("13750 .64 .92 .14", ".2 .55 .6 .5") + " /\n");
            model.initialize(direction * .4, .1, 1.);
            for (int i=0; i<100; i++) model.step(.001, direction*.4, .1, 1., 1.);
            assertEquals(direction*.2, model.getEnergyOutput(), 1.e-14);
            assertEquals(direction>0 ? .5 : .6, model.getStateOfCharge(), 1.e-14);
            assertEquals(0., model.getIpcmd(), 0.);
            // Reversing terminal power must first repay retained energy overshoot.
            for (int i=0; i<50; i++) model.step(.001, -direction*.4, .1, 1., 1.);
            assertEquals(direction*.1, model.getEnergyOutput(), 1.e-14);
            assertEquals(direction>0 ? .5 : .6, model.getStateOfCharge(), 1.e-14);
            for (int i=0; i<30; i++) model.step(.001, -direction*.4, .1, 1., 1.);
            assertEquals(.55-direction*.04, model.getStateOfCharge(), 1.e-14);
            assertTrue(direction*model.getIpcmd()>0.);
            model.initialize(direction*.4, .1, 1.);
            assertEquals(0., model.getEnergyOutput(), 0.);
            assertEquals(.55, model.getStateOfCharge(), 0.);
        }
    }

    @org.junit.jupiter.api.Tag("extended")
    @Test
    void recordedChargeAndDischargeBoundariesRetainEnergyOvershoot(@TempDir Path tempDir)
            throws Exception {
        Path evidence = Path.of("testData/private/verification-artifacts/reecc1-energy-limits-20261104");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(evidence),
                "Private paired reference captures are required");
        for (String mode : new String[]{"charge-limit", "discharge-limit"}) {
            Path profile=evidence.resolve(mode+"-fine-a");
            var lines=Files.readAllLines(profile.resolve("native.csv"));
            var columns=java.util.Arrays.asList(lines.get(0).split(","));
            double[] previous=java.util.Arrays.stream(lines.get(1).split(",")).mapToDouble(Double::parseDouble).toArray();
            int p=columns.indexOf("P_PU"), q=columns.indexOf("Q_PU"), v=columns.indexOf("V1");
            int energy=columns.indexOf("ELEC_STATE6"), soc=columns.indexOf("ELEC_VAR4");
            Reecc1Model model=parse(tempDir.resolve(mode+".dyr"), Files.readAllLines(profile.resolve("dynamic.dyr")).stream()
                    .filter(line -> line.startsWith("1 ")).collect(java.util.stream.Collectors.joining("\n")));
            model.initialize(previous[p],previous[q],previous[v]);
            double maximumEnergyError=0., maximumSocError=0.;
            for (int i=1; i<lines.size(); i++) {
                double[] row=java.util.Arrays.stream(lines.get(i).split(",")).mapToDouble(Double::parseDouble).toArray();
                model.step(row[0]-previous[0], previous[p], previous[q], previous[v], 1.);
                maximumEnergyError=Math.max(maximumEnergyError,Math.abs(model.getEnergyOutput()-row[energy]));
                maximumSocError=Math.max(maximumSocError,Math.abs(model.getStateOfCharge()-row[soc]));
                previous=row;
            }
            // Includes every startup/event-side sample. One-step startup and
            // quadrature differences are bounded by the paired-capture envelope.
            assertTrue(maximumEnergyError<.00035, mode+" energy="+maximumEnergyError);
            assertTrue(maximumSocError<.00035, mode+" SOC="+maximumSocError);
            assertTrue(Math.abs(model.getEnergyOutput())>.07);
            System.out.println(mode+" energy replay maximum="+maximumEnergyError+", SOC="+maximumSocError);
        }
    }

    @Test
    void directVoltageSelectorUsesInitializedVoltageBeforeReactiveLimits(@TempDir Path tempDir)
            throws Exception {
        Reecc1Model model=parse(tempDir.resolve("voltage.dyr"), REGCA + "1 'REECC1' '1' "
                + PARAMETERS.replaceFirst("0 1 1 1 1", "0 0 0 1 1") + " /\n");
        model.initialize(.4,.1,1.01);
        var initial=model.getNamedStates();
        for(int i=0;i<100;i++)model.step(.001,.4,.1,1.01,1.);
        for(String state:initial.keySet()) if(!state.equals("Battery Energy Output"))
            assertEquals(initial.get(state),model.getNamedStates().get(state),1.e-13,state);
        assertEquals(-.1/1.01,model.getIqcmd(),1.e-13);
        // Qmax=.92 must not clip the direct voltage reference of1.01.
        assertEquals(0.,model.getReactivePowerIntegral(),0.);
    }

    @org.junit.jupiter.api.Tag("extended")
    @Test
    void recordedDirectVoltageControlRemainsFlatWithoutInventedBias(@TempDir Path tempDir)
            throws Exception {
        Path profile=Path.of("testData/private/verification-artifacts/reecc1-voltage-selector-20261104/voltage-flat-fine-a");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(profile),"Private paired selector capture required");
        var lines=Files.readAllLines(profile.resolve("native.csv"));
        var columns=java.util.Arrays.asList(lines.get(0).split(","));
        double[] previous=java.util.Arrays.stream(lines.get(1).split(",")).mapToDouble(Double::parseDouble).toArray();
        int p=columns.indexOf("P_PU"),q=columns.indexOf("Q_PU"),v=columns.indexOf("V1");
        Reecc1Model model=parse(tempDir.resolve("voltage-reference.dyr"),Files.readAllLines(profile.resolve("dynamic.dyr")).stream()
                .filter(line -> line.startsWith("1 ")).collect(java.util.stream.Collectors.joining("\n")));
        model.initialize(previous[p],previous[q],previous[v]);
        double maximum=0.;
        for(int i=1;i<lines.size();i++) {
            double[] row=java.util.Arrays.stream(lines.get(i).split(",")).mapToDouble(Double::parseDouble).toArray();
            model.step(row[0]-previous[0],previous[p],previous[q],previous[v],1.);
            double[] states={model.getMeasuredVoltage(),model.getMeasuredActivePower(),model.getReactivePowerIntegral(),
                    model.getVoltageIntegral(),model.getReactiveCurrentState(),model.getActivePowerOrder()};
            for(int k=0;k<states.length;k++)maximum=Math.max(maximum,Math.abs(states[k]-row[columns.indexOf("ELEC_STATE"+k)]));
            maximum=Math.max(maximum,Math.abs(model.getIpcmd()-row[columns.indexOf("ELEC_VAR2")]));
            maximum=Math.max(maximum,Math.abs(-model.getIqcmd()-row[columns.indexOf("ELEC_VAR3")]));
            previous=row;
        }
        assertTrue(maximum<1.e-8,"Direct-voltage state/command error="+maximum);
        System.out.println("Direct-voltage state/command maximum="+maximum);
    }

    private static Reecc1Model parse(Path dyr, String contents) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Files.writeString(dyr, contents);
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        Reecc1Model model = assertInstanceOf(Reecc1Model.class, controller(builder));
        model.initialize(.40, .10, 1.0);
        return model;
    }

    private static Object controller(DStabNetworkBuilder builder) {
        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        return ((Regca1Model) gen.getDynamicGenDevice()).getActiveElectricalController();
    }
}
