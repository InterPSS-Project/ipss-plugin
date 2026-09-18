package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac5c.Ac5cData;
import org.interpss.dstab.control.exc.psse.ac5c.Ac5cExciter;
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

/** PowerWorld/IEEE AC5C topology tests with an ANDES ESAC5A common-profile oracle. */
public class DStabNetworkBuilderAc5cTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;

    @Test
    void parsesExactTwentyOneParameterPsseRecordWithoutPslfAlias(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("ac5c.dyr");
        Files.writeString(dyr,"1 'AC5C' 1 2 1 .01 80 .04 8 -1 1 .8 .03 1 .8 0 5.6 .86 4.2 .5 .2 .3 9 0 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Ac5cExciter e=(Ac5cExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(e);Ac5cData d=e.getData();assertEquals(2,d.getOelLocation());assertEquals(1,d.getUelLocation());
        assertEquals(.01,d.getTr(),TOL);assertEquals(80,d.getKa(),TOL);assertEquals(.04,d.getTa(),TOL);
        assertEquals(8,d.getVamax(),TOL);assertEquals(-1,d.getVamin(),TOL);assertEquals(1,d.getKe(),TOL);
        assertEquals(.8,d.getTe(),TOL);assertEquals(.03,d.getKf(),TOL);assertEquals(1,d.getTf1(),TOL);
        assertEquals(.8,d.getTf2(),TOL);assertEquals(0,d.getTf3(),TOL);assertEquals(5.6,d.getE1(),TOL);
        assertEquals(.86,d.getSe1(),TOL);assertEquals(4.2,d.getE2(),TOL);assertEquals(.5,d.getSe2(),TOL);
        assertEquals(.2,d.getKc(),TOL);assertEquals(.3,d.getKd(),TOL);assertEquals(9,d.getVfemax(),TOL);
        assertEquals(0,d.getVemin(),TOL);assertTrue(p.getLastImportReport().isStrictlyComplete());
        var descriptor=DynamicModelCatalog.find("AC5C").orElseThrow();assertEquals(21,descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(DynamicModelCatalog.find("ESAC5C").isEmpty());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESAC5C").orElseThrow().isImplementedExactly());
    }

    @Test
    void fiveStateTrajectoryMatchesAndesEsac5aCommonProfile()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.2,1.04,1.2,1.2,1.2};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){
            double[] d0=derivatives(x,.1),predict=add(x,d0,dt),d1=derivatives(predict,.1);
            for(int j=0;j<5;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            max=Math.max(max,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            max=Math.max(max,Math.abs(x[2]-f.exciter.getRegulatorState()));
            max=Math.max(max,Math.abs(x[3]-f.exciter.getFeedback1State()));
            max=Math.max(max,Math.abs(x[4]-f.exciter.getFeedback2State()));
        }
        assertTrue(max<1e-9,"AC5C/ANDES ESAC5A common-profile max error="+max);
    }

    @Test
    void routesPublishedSummationAndTakeoverLimiterLocations()throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        Ac5cData uelData=algebraicRegulatorData();uelData.setUelLocation(Ac5cExciter.INPUT_SUMMATION);
        Ac5cExciter uel=b.addExcAc5c("Bus1","1",uelData);assertTrue(uel.initStates(m.getDStabBus(),m));uel.setVuel(.1);assertEquals(1.4,uel.getExciterInput(),TOL);
        Ac5cData oelData=algebraicRegulatorData();oelData.setOelLocation(Ac5cExciter.INPUT_SUMMATION);
        Ac5cExciter oel=b.addExcAc5c("Bus1","1",oelData);assertTrue(oel.initStates(m.getDStabBus(),m));oel.setVoel(.1);assertEquals(1.0,oel.getExciterInput(),TOL);
        Ac5cData sumSclData=algebraicRegulatorData();sumSclData.setSclLocation(Ac5cExciter.INPUT_SUMMATION);
        Ac5cExciter sumScl=b.addExcAc5c("Bus1","1",sumSclData);assertTrue(sumScl.initStates(m.getDStabBus(),m));sumScl.setVsclSum(.1);assertEquals(1.0,sumScl.getExciterInput(),TOL);
        Ac5cData gateData=algebraicRegulatorData();gateData.setUelLocation(Ac5cExciter.INPUT_TAKEOVER);gateData.setOelLocation(Ac5cExciter.INPUT_TAKEOVER);
        Ac5cExciter gate=b.addExcAc5c("Bus1","1",gateData);assertTrue(gate.initStates(m.getDStabBus(),m));gate.setVuel(1.4);assertEquals(1.4,gate.getExciterInput(),TOL);gate.setVoel(.7);assertEquals(.7,gate.getExciterInput(),TOL);
        Ac5cData sclData=algebraicRegulatorData();sclData.setSclLocation(Ac5cExciter.INPUT_TAKEOVER);
        Ac5cExciter scl=b.addExcAc5c("Bus1","1",sclData);assertTrue(scl.initStates(m.getDStabBus(),m));scl.setVsclUel(1.5);assertEquals(1.5,scl.getExciterInput(),TOL);scl.setVsclOel(.65);assertEquals(.65,scl.getExciterInput(),TOL);
    }

    @Test
    void solvesAlgebraicRegulatorWithFeedbackDirectFeedthrough()throws Exception{
        Ac5cData d=baseData();d.setTa(0);Fixture f=fixture(d);f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double direct=.2/.5,stored=(1-direct)*1.2;
        double offset=.05*(stored-1.2)/.7;
        double expected=2*(.7-offset)/(1+2*.05*direct/.7);
        assertEquals(expected,f.exciter.getRegulatorOutput(),TOL);
        assertEquals(2*(.7-f.exciter.getRateFeedback()),f.exciter.getRegulatorOutput(),TOL);
    }

    @Test
    void enforcesRotatingExciterLimitAndLoadedRectifier()throws Exception{
        Ac5cData d=baseData();d.setKc(.2);d.setKd(.1);d.setVfemax(1.5);
        Fixture f=fixture(d);DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(f.builder.getDStabNetwork());
        a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());
        double upper=f.exciter.getDynamicFieldUpperLimit();assertTrue(Double.isFinite(upper));
        f.exciter.setRefPoint(f.exciter.getRefPoint()+10);for(int i=0;i<500;i++)step(f.exciter,f.machine,.001);
        assertTrue(f.exciter.getInternalFieldVoltage()<=f.exciter.getDynamicFieldUpperLimit()+TOL);
        assertTrue(f.exciter.getOutput(f.machine)<f.exciter.getInternalFieldVoltage());
    }

    @Test
    void appliesCorrectionsExpandsRegulatorLimitsAndParticipatesInSolver()throws Exception{
        Ac5cData d=baseData();d.setTr(.004);d.setTa(.015);d.setTf1(.01);d.setTf2(.004);d.setTe(.01);
        d.setVamax(-1);d.setVamin(-2);DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        Ac5cExciter e=b.addExcAc5c("Bus1","1",d);Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.ta,TOL);assertEquals(.02,e.tf1,TOL);assertEquals(0,e.tf2,TOL);assertEquals(.02,e.te,TOL);
        assertTrue(e.vamax>=e.getRegulatorOutput());assertTrue(e.vamin<=e.getRegulatorOutput());
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());
        assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(Ac5cData d)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Ac5cExciter e=b.addExcAc5c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(b,m,e);
    }
    private static Ac5cData baseData(){Ac5cData d=new Ac5cData();d.setTr(.1);d.setKa(2);d.setTa(.3);d.setVamax(10);d.setVamin(-10);
        d.setKe(1);d.setTe(.4);d.setKf(.05);d.setTf1(.7);d.setTf2(.5);d.setTf3(.2);d.setE1(0);d.setSe1(0);d.setE2(0);d.setSe2(0);
        d.setKc(0);d.setKd(0);d.setVfemax(100);d.setVemin(0);return d;}
    private static Ac5cData algebraicRegulatorData(){Ac5cData d=baseData();d.setTr(0);d.setTa(0);d.setKf(0);return d;}
    private static double[] derivatives(double[] x,double step){double lead=.4*x[2]+.6*x[3],feedback=.05*(lead-x[4])/.7;
        double error=1.04+.6+step-x[1]-feedback;return new double[]{(x[2]-x[0])/.4,(1.04-x[1])/.1,(2*error-x[2])/.3,(x[2]-x[3])/.5,(lead-x[4])/.7};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Ac5cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(DStabNetworkBuilder builder,Machine machine,Ac5cExciter exciter){}
}
