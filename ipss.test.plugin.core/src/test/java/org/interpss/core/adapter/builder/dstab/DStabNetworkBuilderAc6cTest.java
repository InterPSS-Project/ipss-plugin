package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac6c.Ac6cData;
import org.interpss.dstab.control.exc.psse.ac6c.Ac6cExciter;
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

/** PowerWorld/IEEE AC6C import, equation, limiter and solver tests. */
public class DStabNetworkBuilderAc6cTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;

    @Test
    void parsesExactTwentySevenParameterPsseRecordWithoutPslfAlias(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("ac6c.dyr");
        Files.writeString(dyr,"1 'AC6C' 1 2 1 .01 80 .04 .03 .8 .2 8 -1 5 -5 .7 .6 .4 2 .9 .1 .2 .3 1 5.6 .86 4.2 .5 9 0 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Ac6cExciter e=(Ac6cExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(e);Ac6cData d=e.getData();assertEquals(2,d.getOelLocation());assertEquals(1,d.getUelLocation());
        assertEquals(.01,d.getTr(),TOL);assertEquals(80,d.getKa(),TOL);assertEquals(.04,d.getTa(),TOL);
        assertEquals(.03,d.getTk(),TOL);assertEquals(.8,d.getTb(),TOL);assertEquals(.2,d.getTc(),TOL);
        assertEquals(8,d.getVamax(),TOL);assertEquals(-1,d.getVamin(),TOL);
        assertEquals(5,d.getEfemax(),TOL);assertEquals(-5,d.getEfemin(),TOL);assertEquals(.7,d.getTe(),TOL);
        assertEquals(.6,d.getVfelim(),TOL);assertEquals(.4,d.getKh(),TOL);assertEquals(2,d.getVhmax(),TOL);
        assertEquals(.9,d.getTh(),TOL);assertEquals(.1,d.getTj(),TOL);assertEquals(.2,d.getKc(),TOL);
        assertEquals(.3,d.getKd(),TOL);assertEquals(1,d.getKe(),TOL);assertEquals(5.6,d.getE1(),TOL);
        assertEquals(.86,d.getSe1(),TOL);assertEquals(4.2,d.getE2(),TOL);assertEquals(.5,d.getSe2(),TOL);
        assertEquals(9,d.getVfemax(),TOL);assertEquals(0,d.getVemin(),TOL);assertTrue(p.getLastImportReport().isStrictlyComplete());
        var descriptor=DynamicModelCatalog.find("AC6C").orElseThrow();assertEquals(27,descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(DynamicModelCatalog.find("ESAC6C").isEmpty());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESAC6C").orElseThrow().isImplementedExactly());
    }

    @Test
    void fiveStateTrajectoryMatchesIndependentPowerWorldEquationOracle()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.2,1.04,1.3,1.3,.1};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){
            double[] d0=derivatives(x,.1),predict=add(x,d0,dt),d1=derivatives(predict,.1);
            for(int j=0;j<5;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            max=Math.max(max,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            max=Math.max(max,Math.abs(x[2]-f.exciter.getTaOutput()));
            max=Math.max(max,Math.abs(x[3]-f.exciter.getVaOutput()));
            max=Math.max(max,Math.abs(x[4]-f.exciter.getFeedbackOutput()));
        }
        assertTrue(max<1e-9,"AC6C five-state equation max error="+max);
    }

    @Test
    void routesLimiterLocationsAndAppliesTerminalVoltageScaledEfeLimits()throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        Ac6cData uelData=algebraicData();uelData.setUelLocation(Ac6cExciter.INPUT_SUMMATION);
        Ac6cExciter uel=b.addExcAc6c("Bus1","1",uelData);assertTrue(uel.initStates(m.getDStabBus(),m));uel.setVuel(.1);assertEquals(1.4,uel.getExciterInput(),TOL);
        Ac6cData oelData=algebraicData();oelData.setOelLocation(Ac6cExciter.INPUT_SUMMATION);
        Ac6cExciter oel=b.addExcAc6c("Bus1","1",oelData);assertTrue(oel.initStates(m.getDStabBus(),m));oel.setVoel(.1);assertEquals(1.0,oel.getExciterInput(),TOL);
        Ac6cData sumScl=algebraicData();sumScl.setSclLocation(Ac6cExciter.INPUT_SUMMATION);
        Ac6cExciter sclSum=b.addExcAc6c("Bus1","1",sumScl);assertTrue(sclSum.initStates(m.getDStabBus(),m));sclSum.setVsclSum(.1);assertEquals(1.0,sclSum.getExciterInput(),TOL);
        Ac6cData gateData=algebraicData();gateData.setUelLocation(Ac6cExciter.INPUT_TAKEOVER);gateData.setOelLocation(Ac6cExciter.INPUT_TAKEOVER);
        Ac6cExciter gate=b.addExcAc6c("Bus1","1",gateData);assertTrue(gate.initStates(m.getDStabBus(),m));gate.setVuel(1.5);assertEquals(1.5,gate.getExciterInput(),TOL);gate.setVoel(.7);assertEquals(.7,gate.getExciterInput(),TOL);
        Ac6cData sclData=algebraicData();sclData.setSclLocation(Ac6cExciter.INPUT_TAKEOVER);
        Ac6cExciter scl=b.addExcAc6c("Bus1","1",sclData);assertTrue(scl.initStates(m.getDStabBus(),m));scl.setVsclUel(1.6);assertEquals(1.6,scl.getExciterInput(),TOL);scl.setVsclOel(.6);assertEquals(.6,scl.getExciterInput(),TOL);
        Ac6cData limitedData=algebraicData();limitedData.setEfemax(2);limitedData.setEfemin(-2);limitedData.setUelLocation(Ac6cExciter.INPUT_TAKEOVER);
        Ac6cExciter limited=b.addExcAc6c("Bus1","1",limitedData);assertTrue(limited.initStates(m.getDStabBus(),m));limited.setVuel(10);assertEquals(2*1.04,limited.getExciterInput(),TOL);
    }

    @Test
    void implementsFieldCurrentFeedbackLimitAndLeadLag()throws Exception{
        Ac6cData d=baseData();d.setVfelim(0);d.setKh(2);d.setVhmax(.3);d.setTj(.2);d.setTh(.5);
        Fixture f=fixture(d);assertEquals(1.2,f.exciter.getFieldFeedback(),TOL);
        assertEquals(.3,f.exciter.getVhOutput(),TOL);assertEquals(.3,f.exciter.getFeedbackOutput(),TOL);
        assertEquals(1.2,f.exciter.getExciterInput(),TOL);
        f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);step(f.exciter,f.machine,.001);
        assertTrue(Double.isFinite(f.exciter.getFeedbackOutput()));
    }

    @Test
    void enforcesRotatingExciterLimitAndLoadedRectifier()throws Exception{
        Ac6cData d=baseData();d.setKc(.2);d.setKd(.1);d.setVfemax(1.5);
        Fixture f=fixture(d);DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(f.builder.getDStabNetwork());
        a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());
        assertTrue(Double.isFinite(f.exciter.getDynamicFieldUpperLimit()));
        f.exciter.setRefPoint(f.exciter.getRefPoint()+10);for(int i=0;i<500;i++)step(f.exciter,f.machine,.001);
        assertTrue(f.exciter.getInternalFieldVoltage()<=f.exciter.getDynamicFieldUpperLimit()+TOL);
        assertTrue(f.exciter.getOutput(f.machine)<f.exciter.getInternalFieldVoltage());
    }

    @Test
    void appliesCorrectionsExpandsInitializationLimitsAndParticipatesInSolver()throws Exception{
        Ac6cData d=baseData();d.setTr(.004);d.setTe(.01);d.setVamax(-1);d.setVamin(-2);d.setEfemax(-1);d.setEfemin(-2);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Ac6cExciter e=b.addExcAc6c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.te,TOL);assertTrue(e.vamax>=e.getVaOutput());assertTrue(e.vamin<=e.getVaOutput());
        assertTrue(e.efemax>=e.getExciterInput()/1.04);assertTrue(e.efemin<=e.getExciterInput()/1.04);
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());
        assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(Ac6cData d)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Ac6cExciter e=b.addExcAc6c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(b,m,e);
    }
    private static Ac6cData baseData(){Ac6cData d=new Ac6cData();d.setTr(.1);d.setKa(2);d.setTa(.3);d.setTk(0);d.setTb(.5);d.setTc(0);
        d.setVamax(10);d.setVamin(-10);d.setEfemax(100);d.setEfemin(-100);d.setTe(.4);d.setVfelim(1);d.setKh(.5);d.setVhmax(10);
        d.setTh(.7);d.setTj(0);d.setKc(0);d.setKd(0);d.setKe(1);d.setE1(0);d.setSe1(0);d.setE2(0);d.setSe2(0);d.setVfemax(100);d.setVemin(0);return d;}
    private static Ac6cData algebraicData(){Ac6cData d=baseData();d.setTr(0);d.setTa(0);d.setTb(0);d.setTh(0);d.setKh(0);return d;}
    private static double[] derivatives(double[] x,double step){double error=1.04+.65+step-x[1];double vh=Math.max(0,Math.min(10,.5*(x[0]-1)));
        return new double[]{(x[3]-x[4]-x[0])/.4,(1.04-x[1])/.1,(2*error-x[2])/.3,(x[2]-x[3])/.5,(vh-x[4])/.7};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Ac6cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(DStabNetworkBuilder builder,Machine machine,Ac6cExciter exciter){}
}
