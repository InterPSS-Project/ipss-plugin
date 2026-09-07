package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac2c.Ac2cData;
import org.interpss.dstab.control.exc.psse.ac2c.Ac2cExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderAc2cTest extends CorePluginTestSetup {
    private static final double TOL=1.0e-8;
    @TempDir Path tempDir;

    @Test
    void parsesExactTwentyFiveParameterPsseRecordAndResponds() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("ac2c.dyr");
        Files.writeString(dyr,"1 'AC2C' '1' 0 0 0.1 0.2 0.05 10.0 0.3 20.0 -20.0 "
                +"2.0 20.0 -20.0 0.4 99.0 0.5 0.2 0.5 0.0 0.0 1.0 0.0 0.0 1.0 0.0 0.0 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);machine.setEfd(1.2);
        Ac2cExciter exc=(Ac2cExciter)machine.getExciter();
        assertNotNull(exc);assertEquals(2.0,exc.getData().getKb(),TOL);
        assertEquals(20.0,exc.getData().getEfemax(),TOL);assertEquals(.5,exc.getData().getKh(),TOL);
        assertEquals(99.0,exc.getData().getVfemax(),TOL);assertEquals(0.0,exc.getData().getVemin(),TOL);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);assertEquals(1.2,initial,1e-6);
        advance(exc,machine,20,.005);assertEquals(initial,exc.getOutput(machine),1e-9);
        machine.getDStabBus().setVoltage(new Complex(.99,0));advance(exc,machine,40,.005);
        assertTrue(exc.getOutput(machine)>initial);assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void routesPublishedSummationAndTakeoverLimiterLocations() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Ac2cData sumData=simpleData();sumData.setUelLocation(Ac2cExciter.INPUT_SUMMATION);
        Ac2cExciter sum=builder.addExcAc2c("Bus1","1",sumData);
        assertTrue(sum.initStates(machine.getDStabBus(),machine));sum.setVuel(.1);
        assertEquals(3.2,sum.getExciterFieldVoltage(),1e-6);

        Ac2cData oelData=simpleData();oelData.setOelLocation(Ac2cExciter.INPUT_SUMMATION);
        Ac2cExciter oel=builder.addExcAc2c("Bus1","1",oelData);
        assertTrue(oel.initStates(machine.getDStabBus(),machine));oel.setVoel(.1);
        assertEquals(-.8,oel.getExciterFieldVoltage(),1e-6);

        Ac2cData gateData=simpleData();gateData.setUelLocation(Ac2cExciter.INPUT_TAKEOVER);
        gateData.setOelLocation(Ac2cExciter.INPUT_TAKEOVER);
        Ac2cExciter gate=builder.addExcAc2c("Bus1","1",gateData);
        assertTrue(gate.initStates(machine.getDStabBus(),machine));
        gate.setVuel(2.0);assertEquals(2.0,gate.getExciterFieldVoltage(),TOL);
        gate.setVoel(.8);assertEquals(.8,gate.getExciterFieldVoltage(),TOL);

        Ac2cData sclData=simpleData();sclData.setSclLocation(Ac2cExciter.INPUT_TAKEOVER);
        Ac2cExciter scl=builder.addExcAc2c("Bus1","1",sclData);
        assertTrue(scl.initStates(machine.getDStabBus(),machine));
        scl.setVsclUel(1.8);assertEquals(1.8,scl.getExciterFieldVoltage(),TOL);
    }

    @Test
    void appliesPowerWorldCorrectionsAndDynamicFieldCeiling() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Ac2cData data=simpleData();data.setTr(.0025);data.setTb(.005);data.setTa(.003);
        data.setTe(.004);data.setTf(.005);data.setVfemax(1.3);
        Ac2cExciter exc=builder.addExcAc2c("Bus1","1",data);exc.configureIntegrationStep(.01);
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
        Ac2cData data=simpleData();data.setTr(.1);data.setTb(.2);data.setTc(.05);
        data.setKa(10);data.setTa(.3);data.setTe(.4);data.setKh(.5);data.setKf(.2);data.setTf(.5);
        Ac2cExciter exc=builder.addExcAc2c("Bus1","1",data);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        machine.getDStabBus().setVoltage(new Complex(.98,0));
        assertTrue(exc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(exc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,machine,1));

        assertEquals(.9981,exc.getSensedVoltage(),1e-10);
        assertEquals(.12005,exc.getLeadLagState(),1e-10);
        assertEquals(.1205125,exc.getLeadLagOutput(),1e-10);
        assertEquals(1.2000833333333333,exc.getRegulatorState(),1e-10);
        assertEquals(1.2001666666666666,exc.getFieldCommand(),1e-10);
        assertEquals(1.2,exc.getInternalFieldVoltage(),1e-10);
        assertEquals(1.2,exc.getFieldFeedbackLagState(),1e-10);
        assertEquals(0.0,exc.getRateFeedback(),1e-10);
    }

    @Test
    void zeroExciterTimeConstantUsesPublishedAlgebraicBalance() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Ac2cData data=simpleData();data.setTe(0);data.setTa(0);data.setTb(0);data.setTr(0);
        Ac2cExciter exc=builder.addExcAc2c("Bus1","1",data);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        machine.getDStabBus().setVoltage(new Complex(.99,0));
        assertTrue(exc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(exc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        assertTrue(exc.getInternalFieldVoltage()>1.2);
        assertEquals(exc.getFieldFeedback(),exc.getExciterFieldVoltage(),1e-8);
    }

    private static Ac2cData simpleData() {
        Ac2cData d=new Ac2cData();d.setTr(0);d.setTb(0);d.setTc(0);d.setKa(10);d.setTa(0);
        d.setVamax(20);d.setVamin(-20);d.setKb(2);d.setEfemax(20);d.setEfemin(-20);
        d.setTe(.5);d.setVfemax(99);d.setKh(0);d.setKf(0);d.setTf(1);
        d.setKc(0);d.setKd(0);d.setKe(1);d.setE1(0);d.setSe1(0);d.setE2(1);d.setSe2(0);
        d.setVemin(0);return d;
    }
    private static void advance(Ac2cExciter exc,Machine machine,int steps,double dt) {
        for(int i=0;i<steps;i++){
            assertTrue(exc.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
    }
}
