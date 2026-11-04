package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        for (boolean twoStep : new boolean[]{false,true}) for (String mode : new String[]{"charge-limit", "discharge-limit"}) {
            Path profile=evidence.resolve(mode+"-fine-a");
            var lines=Files.readAllLines(profile.resolve("native.csv"));
            var columns=java.util.Arrays.asList(lines.get(0).split(","));
            double[] previous=java.util.Arrays.stream(lines.get(1).split(",")).mapToDouble(Double::parseDouble).toArray();
            int p=columns.indexOf("P_PU"), q=columns.indexOf("Q_PU"), v=columns.indexOf("V1");
            int energy=columns.indexOf("ELEC_STATE6"), soc=columns.indexOf("ELEC_VAR4");
            Reecc1Model model=parse(tempDir.resolve(mode+".dyr"), Files.readAllLines(profile.resolve("dynamic.dyr")).stream()
                    .filter(line -> line.startsWith("1 ")).collect(java.util.stream.Collectors.joining("\n")));
            model.initialize(previous[p],previous[q],previous[v]);
            double maximumEnergyError=0., maximumSocError=0., maximumCurrentError=0.;
            double referenceBlockTime=Double.NaN, actualBlockTime=Double.NaN;
            int activeCommand=columns.indexOf("ELEC_VAR2");
            for (int i=1; i<lines.size(); i++) {
                double[] row=java.util.Arrays.stream(lines.get(i).split(",")).mapToDouble(Double::parseDouble).toArray();
                // The first two negative-time native samples are identical
                // initialization output; the first advancing stage ends at zero.
                double dt=row[0]<0.?0.:row[0]-previous[0];
                if(twoStep)model.stepTwoStep(dt,previous[p],previous[q],previous[v],1.);
                else model.step(dt,previous[p],previous[q],previous[v],1.);
                maximumEnergyError=Math.max(maximumEnergyError,Math.abs(model.getEnergyOutput()-row[energy]));
                maximumSocError=Math.max(maximumSocError,Math.abs(model.getStateOfCharge()-row[soc]));
                maximumCurrentError=Math.max(maximumCurrentError,Math.abs(model.getIpcmd()-row[activeCommand]));
                if(Double.isNaN(referenceBlockTime)&&row[activeCommand]==0.)referenceBlockTime=row[0];
                if(Double.isNaN(actualBlockTime)&&model.getIpcmd()==0.)actualBlockTime=row[0];
                previous=row;
            }
            assertTrue(Double.isFinite(referenceBlockTime)&&Double.isFinite(actualBlockTime));
            assertEquals(referenceBlockTime,actualBlockTime,0.,"SOC command-blocking sample");
            assertTrue(maximumCurrentError<1.e-8,"SOC current-command error="+maximumCurrentError);
            System.out.println(mode+" twoStep="+twoStep+" current maximum="+maximumCurrentError
                    +", command block time delta="+(actualBlockTime-referenceBlockTime));
            // Includes every startup/event-side sample. One-step startup and
            // quadrature differences are bounded by the paired-capture envelope.
            assertTrue(maximumEnergyError<(twoStep?2.e-7:.00035), mode+" energy="+maximumEnergyError);
            assertTrue(maximumSocError<(twoStep?8.e-8:.00035), mode+" SOC="+maximumSocError);
            assertTrue(Math.abs(model.getEnergyOutput())>.07);
            System.out.println(mode+" twoStep="+twoStep+" energy replay maximum="+maximumEnergyError+", SOC="+maximumSocError);
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
        for(boolean twoStep:new boolean[]{false,true}) {
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
            if(twoStep)model.stepTwoStep(row[0]-previous[0],previous[p],previous[q],previous[v],1.);
            else model.step(row[0]-previous[0],previous[p],previous[q],previous[v],1.);
            double[] states={model.getMeasuredVoltage(),model.getMeasuredActivePower(),model.getReactivePowerIntegral(),
                    model.getVoltageIntegral(),model.getReactiveCurrentState(),model.getActivePowerOrder()};
            for(int k=0;k<states.length;k++)maximum=Math.max(maximum,Math.abs(states[k]-row[columns.indexOf("ELEC_STATE"+k)]));
            maximum=Math.max(maximum,Math.abs(model.getIpcmd()-row[columns.indexOf("ELEC_VAR2")]));
            maximum=Math.max(maximum,Math.abs(-model.getIqcmd()-row[columns.indexOf("ELEC_VAR3")]));
            previous=row;
        }
        assertTrue(maximum<1.e-8,"Direct-voltage state/command error="+maximum);
        System.out.println("Direct-voltage twoStep="+twoStep+" state/command maximum="+maximum);
        }
    }

    @Test
    void twoStepStatesUseOldBoundaryAndRetainHistoryAcrossRefresh(@TempDir Path tempDir) throws Exception {
        Reecc1Model model=parse(tempDir.resolve("two-step.dyr"),REGCA+"1 'REECC1' '1' "+PARAMETERS+" /\n");
        assertTrue(model.getStateDerivativeSnapshot().rates().isEmpty());
        double h=.0005;
        model.stepTwoStep(h,.4,.1,.99,1.);
        assertEquals(1.-1.5*h*.01/.023,model.getMeasuredVoltage(),1.e-14);
        assertEquals(.1,model.getVoltageIntegral(),1.e-14,"First derivative uses old measured voltage");
        assertEquals(1.5*h*.4/13750,model.getEnergyOutput(),1.e-20);
        assertEquals(7,model.getStateDerivativeSnapshot().rates().size());
        assertEquals(-.01/.023,model.getStateDerivativeSnapshot().rates().get("Voltage Measurement Filter"),1.e-13);
        var states=model.getNamedStates();var rates=model.getStateDerivativeSnapshot();
        double publishedIp=model.getTwoStepIpcmd(),publishedIq=model.getTwoStepIqcmd();
        model.rebaseNextTwoStep();model.rebaseNextTwoStep();
        for(int i=0;i<5;i++) {
            model.stepTwoStep(0.,.4,.11,.8,1.);
            assertEquals(states,model.getNamedStates());
            assertEquals(rates,model.getStateDerivativeSnapshot());
            assertEquals(publishedIp,model.getTwoStepIpcmd(),0.);
            assertEquals(publishedIq,model.getTwoStepIqcmd(),0.);
        }
        model.stepTwoStep(h,.4,.11,.8,1.);
        assertEquals(org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.TWO_STEP_EVENT,
                model.getStateDerivativeSnapshot().stage());
        assertEquals(states.get("Reactive Power PI"),model.getReactivePowerIntegral(),0.);
        assertEquals(states.get("Voltage Error PI"),model.getVoltageIntegral(),0.);
        assertEquals(states.get("Power Order Lag"),model.getActivePowerOrder(),0.);
        model.stepTwoStep(h,.4,.11,.8,1.);
        assertEquals(org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.TWO_STEP,
                model.getStateDerivativeSnapshot().stage());
        states=model.getNamedStates();
        for(double invalid:new double[]{-h,Double.NaN,Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->model.stepTwoStep(invalid,.4,.1,1.,1.));
            assertEquals(states,model.getNamedStates());
        }
        model.initialize(.4,.1,1.);
        assertTrue(model.getStateDerivativeSnapshot().rates().isEmpty());
        model.stepTwoStep(h,.4,.1,.99,1.);
        assertEquals(1.-1.5*h*.01/.023,model.getMeasuredVoltage(),1.e-14);
        assertEquals(1.5*h*.4/13750,model.getEnergyOutput(),1.e-20);
    }

    @Test
    void bothIntegratorsRespectCurrentPrioritySelectors(@TempDir Path tempDir) throws Exception {
        for(boolean twoStep:new boolean[]{false,true})for(int priority:new int[]{0,1}) {
            String parameters=PARAMETERS.replaceFirst("0 1 1 1 1","0 1 1 1 "+priority)
                    .replace("1.17 .037",".25 .037");
            Reecc1Model model=parse(tempDir.resolve("priority-"+twoStep+priority+".dyr"),
                    REGCA+"1 'REECC1' '1' "+parameters+" /\n");
            if(twoStep)model.stepTwoStep(.0005,.4,.1,1.,1.);else model.step(.0005,.4,.1,1.,1.);
            assertTrue(Math.hypot(model.getIpcmd(),model.getIqcmd())<=.25+1.e-14);
            if(priority==1){assertEquals(.25,model.getIpcmd(),1.e-14);assertEquals(0.,model.getIqcmd(),0.);}
            else {assertEquals(-.1,model.getIqcmd(),1.e-14);assertEquals(Math.sqrt(.25*.25-.1*.1),model.getIpcmd(),1.e-14);}
        }
    }

    @Test
    void reactiveLagUsesOldVoltageAndDipClearsItsDerivativeHistory(@TempDir Path tempDir) throws Exception {
        Reecc1Model model=parse(tempDir.resolve("reactive-lag.dyr"),REGCA+"1 'REECC1' '1' "
                +PARAMETERS.replaceFirst("0 1 1 1 1","0 0 1 0 1")+" /\n");
        double h=.0005;
        model.stepTwoStep(h,.4,.1,.99,1.);
        assertEquals(.1,model.getReactiveCurrentState(),0.);
        double oldVoltage=model.getMeasuredVoltage();
        model.stepTwoStep(h,.4,.1,.99,1.);
        assertEquals(.1+1.5*h*(.1/oldVoltage-.1)/.019,model.getReactiveCurrentState(),1.e-14);
        double held=model.getReactiveCurrentState();
        model.stepTwoStep(h,.4,.1,.8,1.);
        assertEquals(held,model.getReactiveCurrentState(),0.);
        assertEquals(0.,model.getStateDerivativeSnapshot().rates().get("Reactive Current Lag"),0.);
    }

    @Test
    void voltagePiStopsAtItsDirectionalLimitAndRecoversForBothMethods(@TempDir Path tempDir) throws Exception {
        for(boolean twoStep:new boolean[]{false,true}) {
            Reecc1Model model=parse(tempDir.resolve("voltage-limit-"+twoStep+".dyr"),REGCA+"1 'REECC1' '1' "
                    +PARAMETERS.replaceFirst("0 1 1 1 1","0 0 0 1 1").replace("1.17 .037",".25 .037")+" /\n");
            model.initialize(.1,.1,1.);
            for(int i=0;i<1200;i++) {
                if(twoStep)model.stepTwoStep(.0005,.1,.1,.9,1.);else model.step(.0005,.1,.1,.9,1.);
                assertTrue(Math.hypot(model.getIpcmd(),model.getIqcmd())<=.25+1.e-14);
            }
            double held=model.getVoltageIntegral();
            for(int i=0;i<10;i++) {
                if(twoStep)model.stepTwoStep(.0005,.1,.1,.9,1.);else model.step(.0005,.1,.1,.9,1.);
                assertEquals(held,model.getVoltageIntegral(),0.);
            }
            for(int i=0;i<100;i++) {
                if(twoStep)model.stepTwoStep(.0005,.1,.1,1.1,1.);else model.step(.0005,.1,.1,1.1,1.);
            }
            assertTrue(model.getVoltageIntegral()<held);
        }
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
