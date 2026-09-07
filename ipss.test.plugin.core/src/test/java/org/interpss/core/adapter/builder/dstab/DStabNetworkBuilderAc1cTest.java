package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac1c.Ac1cData;
import org.interpss.dstab.control.exc.psse.ac1c.Ac1cExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderAc1cTest extends CorePluginTestSetup {
    private static final double TOL=1.0e-8;
    @TempDir Path tempDir;

    @Test
    void parsesPublicTwentyThreeParameterPsseRecordAndResponds() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("ac1c.dyr");
        Files.writeString(dyr,"1 'AC1C' '1' 0 0 0.0 0.0 0.0 400.0 0.02 14.5 -14.5 "
                +"0.8 0.03 1.0 0.2 0.38 1.0 4.18 0.1 3.14 0.03 6.03 -5.43 99.0 0.0 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);machine.setEfd(1.2);
        Ac1cExciter exc=(Ac1cExciter)machine.getExciter();
        assertNotNull(exc);
        assertEquals(0,exc.getData().getOelLocation());assertEquals(0,exc.getData().getUelLocation());
        assertEquals(400.0,exc.getData().getKa(),TOL);assertEquals(.8,exc.getData().getTe(),TOL);
        assertEquals(.38,exc.getData().getKd(),TOL);assertEquals(6.03,exc.getData().getEfemax(),TOL);
        assertEquals(99.0,exc.getData().getVfemax(),TOL);assertEquals(0.0,exc.getData().getVemin(),TOL);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);assertEquals(1.2,initial,1e-6);
        advance(exc,machine,20,.005);assertEquals(initial,exc.getOutput(machine),1e-9);
        machine.getDStabBus().setVoltage(new Complex(.99,0));
        advance(exc,machine,40,.005);assertTrue(exc.getOutput(machine)>initial);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void routesSummationAndTakeoverLimiterInputsAtTheirPublishedLocations() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        Ac1cData sumData=simpleData();sumData.setUelLocation(Ac1cExciter.INPUT_SUMMATION);
        Ac1cExciter sum=builder.addExcAc1c("Bus1","1",sumData);
        assertTrue(sum.initStates(machine.getDStabBus(),machine));sum.setVuel(.1);
        assertEquals(2.2,sum.getExciterFieldVoltage(),1e-6);

        Ac1cData gateData=simpleData();gateData.setUelLocation(Ac1cExciter.INPUT_TAKEOVER);
        gateData.setOelLocation(Ac1cExciter.INPUT_TAKEOVER);
        Ac1cExciter gate=builder.addExcAc1c("Bus1","1",gateData);
        assertTrue(gate.initStates(machine.getDStabBus(),machine));
        gate.setVuel(2.0);assertEquals(2.0,gate.getExciterFieldVoltage(),TOL);
        gate.setVoel(.8);assertEquals(.8,gate.getExciterFieldVoltage(),TOL);

        Ac1cData sclData=simpleData();sclData.setSclLocation(Ac1cExciter.INPUT_TAKEOVER);
        Ac1cExciter scl=builder.addExcAc1c("Bus1","1",sclData);
        assertTrue(scl.initStates(machine.getDStabBus(),machine));
        scl.setVsclUel(1.8);assertEquals(1.8,scl.getExciterFieldVoltage(),TOL);
    }

    @Test
    void appliesPowerWorldTimeCorrectionsAndEnforcesRotatingExciterCeiling() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        machine.getDStabBus().setVoltage(new Complex(1.0,0));
        Ac1cData data=simpleData();data.setTr(.0025);data.setTb(.005);data.setTa(.003);
        data.setTe(.004);data.setTf(.005);data.setVfemax(1.3);
        Ac1cExciter exc=builder.addExcAc1c("Bus1","1",data);exc.configureIntegrationStep(.01);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        assertEquals(.005,exc.tr,TOL);assertEquals(.01,exc.tb,TOL);assertEquals(0,exc.ta,TOL);
        assertEquals(.01,exc.te,TOL);assertEquals(.01,exc.tf,TOL);
        machine.getDStabBus().setVoltage(new Complex(.8,0));advance(exc,machine,80,.0025);
        assertTrue(exc.getInternalFieldVoltage()<=1.3+TOL);
        assertTrue(exc.getInternalFieldVoltage()>=data.getVemin()-TOL);
    }

    @Test
    void modifiedEulerFiveStateStepMatchesIndependentEquationOracle() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        machine.getDStabBus().setVoltage(new Complex(1.0,0));
        Ac1cData data=simpleData();data.setTr(.1);data.setTb(.2);data.setTc(.05);
        data.setKa(10);data.setTa(.3);data.setTe(.4);data.setKf(.2);data.setTf(.5);
        Ac1cExciter exc=builder.addExcAc1c("Bus1","1",data);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        machine.getDStabBus().setVoltage(new Complex(.98,0));
        assertTrue(exc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(exc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,machine,1));

        // Independent Heun evaluation of the published five state equations.
        assertEquals(.9981,exc.getSensedVoltage(),1e-10);
        assertEquals(.12005,exc.getLeadLagState(),1e-10);
        assertEquals(.1205125,exc.getLeadLagOutput(),1e-10);
        assertEquals(1.2000833333333333,exc.getRegulatorState(),1e-10);
        assertEquals(1.2,exc.getInternalFieldVoltage(),1e-10);
        assertEquals(1.2,exc.getFieldFeedbackLagState(),1e-10);
        assertEquals(0.0,exc.getRateFeedback(),1e-10);
    }

    private static Ac1cData simpleData(){
        Ac1cData d=new Ac1cData();d.setTr(0);d.setTb(0);d.setTc(0);d.setKa(10);d.setTa(0);
        d.setVamax(20);d.setVamin(-20);d.setTe(.5);d.setKf(0);d.setTf(1);
        d.setKc(0);d.setKd(0);d.setKe(1);d.setE1(0);d.setSe1(0);d.setE2(1);d.setSe2(0);
        d.setEfemax(20);d.setEfemin(-20);d.setVfemax(99);d.setVemin(0);return d;
    }
    private static void advance(Ac1cExciter exc,Machine machine,int steps,double dt){
        for(int i=0;i<steps;i++){
            assertTrue(exc.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
    }
}
