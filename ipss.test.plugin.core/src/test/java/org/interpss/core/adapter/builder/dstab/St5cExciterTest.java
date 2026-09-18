package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.st5c.St5cData;
import org.interpss.dstab.control.exc.psse.st5c.St5cExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Official-schema, equation, routing, correction and solver tests for PSS/E ST5C. */
public class St5cExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;

    @Test void parsesExactTwentyParameterSchema(@TempDir Path dir)throws Exception{
        Path dyr=dir.resolve("st5c.dyr");Files.writeString(dyr,
                "1 'ST5C' 1 1 2 .1 .05 .2 .1 .3 10 20 -20 .2 0 .02 .4 .03 .5 .04 .6 .05 .7 /\n");
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);
        p.parseDynFile(dyr.toString());St5cExciter e=(St5cExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();assertNotNull(e);
        St5cData d=e.getData();assertEquals(1,d.getOel());assertEquals(2,d.getUel());assertEquals(.1,d.getTr(),TOL);
        assertEquals(.05,d.getTc1(),TOL);assertEquals(.2,d.getTb1(),TOL);assertEquals(.1,d.getTc2(),TOL);assertEquals(.3,d.getTb2(),TOL);
        assertEquals(10,d.getKr(),TOL);assertEquals(20,d.getVrmax(),TOL);assertEquals(-20,d.getVrmin(),TOL);assertEquals(.2,d.getT1(),TOL);
        assertEquals(.02,d.getTuc1(),TOL);assertEquals(.5,d.getTub2(),TOL);assertEquals(.04,d.getToc1(),TOL);assertEquals(.7,d.getTob2(),TOL);
        assertTrue(p.getLastImportReport().isStrictlyComplete());var descriptor=DynamicModelCatalog.find("ST5C").orElseThrow();
        assertEquals(20,descriptor.parameterCount());assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST5C").orElseThrow().isImplementedExactly());
    }

    @Test void eightStateModifiedEulerTrajectoryMatchesIndependentOracle()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);double[] expected=f.exciter.getStateSnapshot();double dt=.0001,max=0;
        for(int i=0;i<2000;i++){double[] d0=derivatives(expected),predict=add(expected,d0,dt),d1=derivatives(predict);
            for(int j=0;j<expected.length;j++)expected[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            double[] actual=f.exciter.getStateSnapshot();for(int j=0;j<actual.length;j++)max=Math.max(max,Math.abs(expected[j]-actual[j]));}
        assertTrue(max<1e-10,"ST5C eight-state max error="+max);
    }

    @Test void routesSummationGateOneAndGateTwoLimiterInputsAndNormalizesInvalidFlags()throws Exception{
        St5cData d=baseData();d.setUel(1);Fixture directUnder=fixture(d);directUnder.exciter.setVuel(.2);
        assertEquals(.12,directUnder.exciter.getVoltageError(),TOL);assertEquals(.32,directUnder.exciter.getSummedError(),TOL);
        assertEquals(.32,directUnder.exciter.getGatedError(),TOL);assertEquals(0,directUnder.exciter.getSelectedPath());
        d=baseData();d.setOel(1);Fixture directOver=fixture(d);directOver.exciter.setVoel(-.1);
        assertEquals(.02,directOver.exciter.getSummedError(),TOL);assertEquals(0,directOver.exciter.getSelectedPath());
        d=baseData();d.setUel(2);Fixture gatedUnder=fixture(d);gatedUnder.exciter.setVuel(.2);
        assertEquals(.12,gatedUnder.exciter.getSummedError(),TOL);assertEquals(.2,gatedUnder.exciter.getHighGateOutput(),TOL);
        assertEquals(.2,gatedUnder.exciter.getGatedError(),TOL);assertEquals(0,gatedUnder.exciter.getSelectedPath());
        d=baseData();d.setOel(2);Fixture gatedOver=fixture(d);gatedOver.exciter.setVoel(-.1);
        assertEquals(-.1,gatedOver.exciter.getGatedError(),TOL);assertEquals(0,gatedOver.exciter.getSelectedPath());
        d=baseData();d.setOel(3);d.setUel(-4);Fixture gateTwo=fixture(d);gateTwo.exciter.setVoel(-.1);
        for(int i=0;i<500;i++)step(gateTwo.exciter,gateTwo.machine,.0001);
        assertEquals(3,gateTwo.exciter.getOelInputMode());assertEquals(1,gateTwo.exciter.getUelInputMode());
        assertEquals(1,gateTwo.exciter.getSelectedPath());
        d=baseData();d.setUel(1);DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St5cExciter initialized=b.addExcSt5c("Bus1","1",d);
        Machine machine=b.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);initialized.setVuel(.2);assertTrue(initialized.initStates(machine.getDStabBus(),machine));
        for(int i=0;i<1000;i++)step(initialized,machine,.0001);assertEquals(1.2,initialized.getOutput(machine),TOL);
    }

    @Test void takeoverBranchesUseTheirPublishedTwoStageLeadLags()throws Exception{
        St5cData d=baseData();d.setUel(3);Fixture under=fixture(d);under.exciter.setVuel(.3);for(int i=0;i<500;i++)step(under.exciter,under.machine,.0001);
        assertEquals(-1,under.exciter.getSelectedPath());assertNotEquals(under.exciter.getGatedError(),under.exciter.getSelectedPathOutput(),1e-4);
        d=baseData();d.setOel(3);Fixture over=fixture(d);over.exciter.setVoel(-.2);for(int i=0;i<500;i++)step(over.exciter,over.machine,.0001);
        assertEquals(1,over.exciter.getSelectedPath());assertNotEquals(over.exciter.getGatedError(),over.exciter.getSelectedPathOutput(),1e-4);
    }

    @Test void appliesPublishedTimeCorrectionsAndInitializationLimitExpansion()throws Exception{
        St5cData d=baseData();d.setTr(.004);d.setTb1(.015);d.setTb2(.004);d.setTub1(.015);d.setTub2(.004);
        d.setTob1(.015);d.setTob2(.004);d.setT1(.004);d.setVrmax(-2);d.setVrmin(-3);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St5cExciter e=b.addExcSt5c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.tb1,TOL);assertEquals(0,e.tb2,TOL);assertEquals(.02,e.tub1,TOL);
        assertEquals(0,e.tub2,TOL);assertEquals(.02,e.tob1,TOL);assertEquals(0,e.tob2,TOL);assertEquals(.005,e.t1,TOL);
        assertTrue(e.vrmax>=e.getRegulatorOutput());assertTrue(e.vrmin<=e.getRegulatorOutput());
    }

    @Test void supportsAlgebraicFinalBlockAndRejectsInvalidParameters()throws Exception{
        St5cData d=baseData();d.setTc1(0);d.setTb1(0);d.setTc2(0);d.setTb2(0);d.setT1(0);Fixture algebraic=fixture(d);
        algebraic.exciter.setRefPoint(algebraic.exciter.getRefPoint()+.1);assertEquals(2.2,algebraic.exciter.getOutput(algebraic.machine),TOL);
        d=baseData();d.setTc1(-.1);DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St5cExciter e=b.addExcSt5c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertFalse(e.initStates(m.getDStabBus(),m));
        d=baseData();d.setKr(Double.NaN);b=DStabBuilderTestFixture.createWithMachine();e=b.addExcSt5c("Bus1","1",d);
        m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertFalse(e.initStates(m.getDStabBus(),m));
    }

    @Test void holdsEquilibriumAndParticipatesInFullSimulation()throws Exception{
        Fixture f=fixture(baseData());double initial=f.exciter.getOutput(f.machine);for(int i=0;i<1000;i++)step(f.exciter,f.machine,.0001);
        assertEquals(initial,f.exciter.getOutput(f.machine),TOL);DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();assertNotNull(b.addExcSt5c("Bus1","1",baseData()));
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(St5cData data)throws Exception{DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");St5cExciter e=b.addExcSt5c("Bus1","1",data);m.setEfd(1.2);
        assertNotNull(e);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);}
    private static St5cData baseData(){St5cData d=new St5cData();d.setOel(1);d.setUel(1);d.setTr(.1);d.setTc1(.05);d.setTb1(.2);
        d.setTc2(.1);d.setTb2(.3);d.setKr(10);d.setVrmax(20);d.setVrmin(-20);d.setT1(.2);d.setKc(0);
        d.setTuc1(.02);d.setTub1(.4);d.setTuc2(.03);d.setTub2(.5);d.setToc1(.04);d.setTob1(.6);d.setToc2(.05);d.setTob2(.7);return d;}
    private static double[] derivatives(double[] x){double error=1.16+.1-x[1];double n1=.05/.2*error+(1-.05/.2)*x[2];
        double n2=.1/.3*n1+(1-.1/.3)*x[3];return new double[]{(10*n2-x[0])/.2,(1.04-x[1])/.1,(error-x[2])/.2,
                (n1-x[3])/.3,(error-x[4])/.4,((.02/.4)*error+(1-.02/.4)*x[4]-x[5])/.5,
                (error-x[6])/.6,((.04/.6)*error+(1-.04/.6)*x[6]-x[7])/.7};}
    private static double[] add(double[] x,double[] d,double dt){double[] r=new double[x.length];for(int i=0;i<x.length;i++)r[i]=x[i]+d[i]*dt;return r;}
    private static void step(St5cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(Machine machine,St5cExciter exciter){}
}
