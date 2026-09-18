package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac3c.Ac3cData;
import org.interpss.dstab.control.exc.psse.ac3c.Ac3cExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderAc3cTest extends CorePluginTestSetup {
    private static final double TOL=1.0e-8;
    @TempDir Path tempDir;

    @Test
    void parsesExactThirtyParameterPsseRecordAndResponds() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("ac3c.dyr");
        Files.writeString(dyr,"1 'AC3C' '1' 0 0 0.1 0.2 0.05 10.0 0.3 20.0 -20.0 "
                +"0.4 0.0 1.0 0.2 0.5 0.4 2.0 0.0 0.0 1.0 99.0 "
                +"0.0 0.0 1.0 0.0 1.0 2.0 0.5 0.25 20.0 -20.0 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);machine.setEfd(1.2);
        Ac3cExciter exc=(Ac3cExciter)machine.getExciter();
        assertNotNull(exc);assertEquals(.4,exc.getData().getTe(),TOL);
        assertEquals(.4,exc.getData().getKn(),TOL);assertEquals(2.0,exc.getData().getEfdn(),TOL);
        assertEquals(.5,exc.getData().getKdr(),TOL);assertEquals(.25,exc.getData().getTdr(),TOL);
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
        Ac3cData sumData=simpleData();sumData.setUelLocation(Ac3cExciter.INPUT_SUMMATION);
        Ac3cExciter sum=builder.addExcAc3c("Bus1","1",sumData);
        assertTrue(sum.initStates(machine.getDStabBus(),machine));sum.setVuel(.1);
        assertEquals(2.4,sum.getExciterFieldVoltage(),1e-6);

        Ac3cData oelData=simpleData();oelData.setOelLocation(Ac3cExciter.INPUT_SUMMATION);
        Ac3cExciter oel=builder.addExcAc3c("Bus1","1",oelData);
        assertTrue(oel.initStates(machine.getDStabBus(),machine));oel.setVoel(.1);
        assertEquals(0.0,oel.getExciterFieldVoltage(),1e-6);

        Ac3cData gateData=simpleData();gateData.setUelLocation(Ac3cExciter.INPUT_TAKEOVER);
        gateData.setOelLocation(Ac3cExciter.INPUT_TAKEOVER);
        Ac3cExciter gate=builder.addExcAc3c("Bus1","1",gateData);
        assertTrue(gate.initStates(machine.getDStabBus(),machine));
        gate.setVuel(.2);assertEquals(2.4,gate.getExciterFieldVoltage(),TOL);
        gate.setVoel(.08);assertEquals(.96,gate.getExciterFieldVoltage(),TOL);

        Ac3cData sclData=simpleData();sclData.setSclLocation(Ac3cExciter.INPUT_TAKEOVER);
        Ac3cExciter scl=builder.addExcAc3c("Bus1","1",sclData);
        assertTrue(scl.initStates(machine.getDStabBus(),machine));
        scl.setVsclUel(.18);assertEquals(2.16,scl.getExciterFieldVoltage(),TOL);
    }

    @Test
    void appliesPowerWorldCorrectionsAndExpandsInitializationLimits() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Ac3cData data=simpleData();data.setTr(.0025);data.setTb(.005);data.setTa(.003);
        data.setTe(.004);data.setTf(.005);data.setTdr(.005);data.setVemin(2.0);
        data.setVamax(.5);data.setVamin(-.5);data.setVpidmax(.05);data.setVpidmin(-.05);
        Ac3cExciter exc=builder.addExcAc3c("Bus1","1",data);exc.configureIntegrationStep(.01);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        assertEquals(.005,exc.tr,TOL);assertEquals(.01,exc.tb,TOL);assertEquals(0,exc.ta,TOL);
        assertEquals(.01,exc.te,TOL);assertEquals(.01,exc.tf,TOL);assertEquals(.01,exc.tdr,TOL);
        assertEquals(1.2,exc.vemin,TOL);assertEquals(1.0,exc.vamax,TOL);
        assertEquals(.1,exc.vpidmax,TOL);
    }

    @Test
    void modifiedEulerSevenStateStepMatchesIndependentEquationOracle() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        machine.getDStabBus().setVoltage(new Complex(1.0,0));
        Ac3cData data=simpleData();data.setTr(.1);data.setTb(.2);data.setTc(.05);
        data.setKa(10);data.setTa(.3);data.setTe(.4);data.setKf(.2);data.setTf(.5);
        data.setKn(.4);data.setEfdn(2);data.setKpr(1);data.setKir(2);
        data.setKdr(.5);data.setTdr(.25);
        Ac3cExciter exc=builder.addExcAc3c("Bus1","1",data);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        machine.getDStabBus().setVoltage(new Complex(.98,0));
        assertTrue(exc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(exc.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,machine,1));

        assertEquals(.9981,exc.getSensedVoltage(),1e-10);
        assertEquals(.10002,exc.getPiState(),1e-10);
        assertEquals(.00004,exc.getDerivativeLagState(),1e-10);
        assertEquals(.10015,exc.getLeadLagState(),1e-10);
        assertEquals(.10564,exc.getPidOutput(),1e-10);
        assertEquals(.1015225,exc.getLeadLagOutput(),1e-10);
        assertEquals(1.00025,exc.getRegulatorState(),1e-10);
        assertEquals(1.2003,exc.getExciterFieldVoltage(),1e-10);
        assertEquals(1.2,exc.getInternalFieldVoltage(),1e-10);
        assertEquals(.24,exc.getFeedbackLagState(),1e-10);
        assertEquals(0.0,exc.getRateFeedback(),1e-10);
    }

    @Test
    void nonlinearFeedbackUsesKfBelowAndKnAboveEfdn() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Ac3cData belowData=simpleData();belowData.setKf(.2);belowData.setKn(.4);belowData.setEfdn(2);
        Ac3cExciter below=builder.addExcAc3c("Bus1","1",belowData);
        assertTrue(below.initStates(machine.getDStabBus(),machine));
        assertEquals(.24,below.getNonlinearFeedback(),TOL);

        Ac3cData aboveData=simpleData();aboveData.setKf(.2);aboveData.setKn(.4);aboveData.setEfdn(.5);
        aboveData.setKd(.5);
        Ac3cExciter above=builder.addExcAc3c("Bus1","1",aboveData);
        assertTrue(above.initStates(machine.getDStabBus(),machine));
        assertEquals(.38,above.getNonlinearFeedback(),TOL,
                "AC3C nonlinear feedback is a characteristic of EFD, not VFE");
    }

    private static Ac3cData simpleData() {
        Ac3cData d=new Ac3cData();d.setTr(0);d.setTb(0);d.setTc(0);d.setKa(10);d.setTa(0);
        d.setVamax(20);d.setVamin(-20);d.setTe(.5);d.setVemin(0);d.setKr(1);
        d.setKf(0);d.setTf(1);d.setKn(0);d.setEfdn(2);d.setKc(0);d.setKd(0);
        d.setKe(1);d.setVfemax(99);d.setE1(0);d.setSe1(0);d.setE2(1);d.setSe2(0);
        d.setKpr(1);d.setKir(1);d.setKdr(0);d.setTdr(.1);d.setVpidmax(20);d.setVpidmin(-20);
        return d;
    }

    private static void advance(Ac3cExciter exc,Machine machine,int steps,double dt) {
        for(int i=0;i<steps;i++){
            assertTrue(exc.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
    }
}
