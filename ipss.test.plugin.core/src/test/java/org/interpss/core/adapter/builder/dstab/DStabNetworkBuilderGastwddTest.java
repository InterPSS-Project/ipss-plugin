package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.gastwd.PsseGastwddGovernor;
import org.interpss.dstab.control.gov.psse.gastwd.PsseGastwddGovernorData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderGastwddTest extends CorePluginTestSetup {
    @TempDir Path tempDir;

    @Test void aliasImportsAllThirtyFourFieldsAndFlatRuns() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("gastwddu.dyr");
        Files.writeString(dyr,"1 'GASTWDDU' '1' .05 2 1 0 .02 .1 50 0 1.2 0 .03 1 1 .2 1 .1 .05 .4 .6 .2 .3 1 .1 .1 .1 .05 .2 .8 1 0 500 .1 .003 -.004 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1"); machine.setPm(.6); machine.setPe(.6); machine.setSpeed(1);
        PsseGastwddGovernor gov=(PsseGastwddGovernor)machine.getGovernor();
        assertNotNull(gov); assertEquals(.003,gov.getData().getDbH(),1e-12); assertEquals(-.004,gov.getData().getDbL(),1e-12);
        assertTrue(gov.initStates(machine.getDStabBus(),machine));
        assertEquals(gov.getEffectiveMaxLimit(),gov.getTemperatureCommand(),1e-12);
        double initial=gov.getOutput(machine); advance(gov,machine,20,.01);
        assertEquals(initial,gov.getOutput(machine),1e-8);
        assertEquals(gov.getEffectiveMaxLimit(),gov.getTemperatureCommand(),1e-12);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test void deadbandAndLowValueSelectorFollowPublishedDiagram() throws Exception {
        Fixture f=fixture();
        assertEquals(.007,f.gov.applySpeedDeadband(.01),1e-12);
        assertEquals(-.006,f.gov.applySpeedDeadband(-.01),1e-12);
        f.machine.setSpeed(1.02); advance(f.gov,f.machine,10,.01);
        assertEquals(Math.min(f.gov.getSpeedCommand(),f.gov.getTemperatureCommand()),f.gov.getLowValueSelect(),1e-10);
        assertTrue(Double.isFinite(f.gov.getOutput(f.machine)));
    }

    @Test void initializationExpandsLimitsWithoutChangingSourceRecord() throws Exception {
        Fixture f=fixture();
        f.gov.getData().setMaxLimit(.1); f.gov.getData().setMinLimit(.05);
        assertTrue(f.gov.initStates(f.machine.getDStabBus(),f.machine));
        assertTrue(f.gov.getEffectiveMaxLimit()>.1); assertEquals(.1,f.gov.getData().getMaxLimit(),1e-12);
        assertEquals(.05,f.gov.getData().getMinLimit(),1e-12);
    }

    @Test void invalidPhysicalParametersAreRejected() throws Exception {
        PsseGastwddGovernorData d=data(); d.setBf2(0);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        assertNull(b.addGovGastwdd("Bus1","1",d));
    }

    private static Fixture fixture() throws Exception {DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine(); Machine m=b.getDStabNetwork().getMachine("Bus1-mach1"); m.setPm(.6);m.setPe(.6);m.setSpeed(1); PsseGastwddGovernor g=b.addGovGastwdd("Bus1","1",data()); assertNotNull(g);g.configureIntegrationStep(.01);assertTrue(g.initStates(m.getDStabBus(),m));return new Fixture(m,g);}
    private static PsseGastwddGovernorData data(){PsseGastwddGovernorData d=new PsseGastwddGovernorData();d.setKdroop(.05);d.setKp(2);d.setKi(1);d.setKd(0);d.setEtd(.02);d.setTcd(.1);d.setTrate(0);d.setT(0);d.setMaxLimit(1.2);d.setMinLimit(0);d.setEcr(.03);d.setK3(1);d.setA(1);d.setB(.2);d.setC(1);d.setTauF(.1);d.setKf(.05);d.setK5(.4);d.setK4(.6);d.setT3(.2);d.setT4(.3);d.setTauT(1);d.setT5(.1);d.setAf1(.1);d.setBf1(.1);d.setAf2(.05);d.setBf2(.2);d.setCf2(.8);d.setTr(1);d.setK6(0);d.setTc(500);d.setTd(.1);d.setDbH(.003);d.setDbL(-.004);return d;}
    private static void advance(PsseGastwddGovernor g,Machine m,int n,double dt){for(int i=0;i<n;i++){assertTrue(g.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(g.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}}
    private record Fixture(Machine machine,PsseGastwddGovernor gov){}
}
