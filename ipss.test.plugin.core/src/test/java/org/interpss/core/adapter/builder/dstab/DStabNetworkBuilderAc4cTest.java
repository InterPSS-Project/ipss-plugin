package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ac4c.Ac4cData;
import org.interpss.dstab.control.exc.psse.ac4c.Ac4cExciter;
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
import com.interpss.dstab.mach.MachineIfdBase;

/** PowerWorld/IEEE topology tests with the ANDES EXAC4 common equation subset. */
public class DStabNetworkBuilderAc4cTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;

    @Test
    void parsesExactTwelveParameterPsseRecordWithoutPslfAlias(@TempDir Path dir)throws Exception {
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("ac4c.dyr");
        Files.writeString(dyr,"1 'AC4C' 1 2 1 .01 7 -2 .2 .4 80 .04 8 -1 .05 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Ac4cExciter e=(Ac4cExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(e);Ac4cData d=e.getData();assertEquals(2,d.getOelLocation());assertEquals(1,d.getUelLocation());
        assertEquals(.01,d.getTr(),TOL);assertEquals(7,d.getVimax(),TOL);assertEquals(-2,d.getVimin(),TOL);
        assertEquals(.2,d.getTc(),TOL);assertEquals(.4,d.getTb(),TOL);assertEquals(80,d.getKa(),TOL);
        assertEquals(.04,d.getTa(),TOL);assertEquals(8,d.getVrmax(),TOL);assertEquals(-1,d.getVrmin(),TOL);
        assertEquals(.05,d.getKc(),TOL);assertTrue(p.getLastImportReport().isStrictlyComplete());
        var descriptor=DynamicModelCatalog.find("AC4C").orElseThrow();assertEquals(12,descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(DynamicModelCatalog.find("ESAC4C").isEmpty());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESAC4C").orElseThrow().isImplementedExactly());
    }

    @Test
    void threeStateTrajectoryMatchesAndesExac4CommonProfile()throws Exception {
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double ratio=.2/.4,vi0=1.2/2;double[] x={1.2,1.04,vi0-ratio*vi0};double dt=.0001,max=0;
        for(int i=0;i<2000;i++) {
            double[] d0=derivatives(x,.1),predict=add(x,d0,dt),d1=derivatives(predict,.1);
            for(int j=0;j<3;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            max=Math.max(max,Math.abs(x[0]-f.exciter.getInternalFieldBeforeLimit()));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            max=Math.max(max,Math.abs(x[2]-f.exciter.getLeadLagState()));
            double vll=ratio*clamp(1.04+vi0+.1-x[1],-5,5)+x[2];
            max=Math.max(max,Math.abs(vll-f.exciter.getLeadLagOutput()));
        }
        assertTrue(max<1e-9,"AC4C/ANDES EXAC4 common-profile max error="+max);
    }

    @Test
    void routesPublishedSummationAndTakeoverLimiterLocations()throws Exception {
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        Ac4cData uelData=algebraicData();uelData.setUelLocation(Ac4cExciter.INPUT_SUMMATION);
        Ac4cExciter uel=b.addExcAc4c("Bus1","1",uelData);assertTrue(uel.initStates(m.getDStabBus(),m));
        uel.setVuel(.1);assertEquals(1.4,uel.getOutput(m),TOL);

        Ac4cData oelData=algebraicData();oelData.setOelLocation(Ac4cExciter.INPUT_SUMMATION);
        Ac4cExciter oel=b.addExcAc4c("Bus1","1",oelData);assertTrue(oel.initStates(m.getDStabBus(),m));
        oel.setVoel(.1);assertEquals(1.0,oel.getOutput(m),TOL);

        Ac4cData sumSclData=algebraicData();sumSclData.setSclLocation(Ac4cExciter.INPUT_SUMMATION);
        Ac4cExciter sumScl=b.addExcAc4c("Bus1","1",sumSclData);assertTrue(sumScl.initStates(m.getDStabBus(),m));
        sumScl.setVsclSum(.1);assertEquals(1.0,sumScl.getOutput(m),TOL);

        Ac4cData gateData=algebraicData();gateData.setUelLocation(Ac4cExciter.INPUT_TAKEOVER);
        gateData.setOelLocation(Ac4cExciter.INPUT_TAKEOVER);
        Ac4cExciter gate=b.addExcAc4c("Bus1","1",gateData);assertTrue(gate.initStates(m.getDStabBus(),m));
        gate.setVuel(.8);assertEquals(1.6,gate.getOutput(m),TOL);
        gate.setVoel(.7);assertEquals(1.4,gate.getOutput(m),TOL);

        Ac4cData sclData=algebraicData();sclData.setSclLocation(Ac4cExciter.INPUT_TAKEOVER);
        Ac4cExciter scl=b.addExcAc4c("Bus1","1",sclData);assertTrue(scl.initStates(m.getDStabBus(),m));
        scl.setVsclUel(.9);assertEquals(1.8,scl.getOutput(m),TOL);
        scl.setVsclOel(.65);assertEquals(1.3,scl.getOutput(m),TOL);
    }

    @Test
    void keepsFieldStateBeforeHardLimitAndShiftsOnlyUpperBoundary()throws Exception {
        Ac4cData d=baseData();d.setVrmax(1.3);d.setVrmin(-.5);d.setKc(0);
        Fixture f=fixture(d);f.exciter.setRefPoint(f.exciter.getRefPoint()+10);
        for(int i=0;i<200;i++)step(f.exciter,f.machine,.01);
        assertTrue(f.exciter.getInternalFieldBeforeLimit()>2.0);
        assertEquals(1.3,f.exciter.getOutput(f.machine),TOL);
        assertEquals(-.5,f.exciter.getDynamicLowerLimit(),TOL);

        Ac4cData loadedData=baseData();loadedData.setKc(.2);Fixture loaded=fixture(loadedData);
        DynamicSimuAlgorithm algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(
                loaded.builder.getDStabNetwork());algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());assertTrue(algorithm.initialization());
        double ifd=loaded.machine.calculateIfd(MachineIfdBase.EXCITER);
        assertTrue(Double.isFinite(ifd));
        assertEquals(loaded.exciter.vrmax-.2*ifd,loaded.exciter.getDynamicUpperLimit(),TOL);
        assertEquals(loaded.exciter.vrmin,loaded.exciter.getDynamicLowerLimit(),TOL);
    }

    @Test
    void appliesCorrectionsExpandsLimitsAndParticipatesInSolver()throws Exception {
        Ac4cData d=baseData();d.setTr(.004);d.setTb(.015);d.setTa(.01);
        d.setVimax(-1);d.setVimin(-2);d.setVrmax(-1);d.setVrmin(-2);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Ac4cExciter e=b.addExcAc4c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.tb,TOL);assertEquals(.02,e.ta,TOL);
        assertTrue(e.vimax>=e.getLimitedError());assertTrue(e.vimin<=e.getLimitedError());
        assertTrue(e.getDynamicUpperLimit()>=1.2-TOL);assertTrue(e.getDynamicLowerLimit()<=1.2+TOL);
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);
        a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(Ac4cData d)throws Exception {
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Ac4cExciter e=b.addExcAc4c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(b,m,e);
    }
    private static Ac4cData baseData(){Ac4cData d=new Ac4cData();d.setTr(.1);d.setVimax(5);d.setVimin(-5);
        d.setTc(.2);d.setTb(.4);d.setKa(2);d.setTa(.3);d.setVrmax(5);d.setVrmin(-5);d.setKc(.1);return d;}
    private static Ac4cData algebraicData(){Ac4cData d=baseData();d.setTr(0);d.setTb(0);d.setTc(0);d.setTa(0);d.setKc(0);return d;}
    private static double[] derivatives(double[] x,double step){double error=1.04+.6+step-x[1],vi=clamp(error,-5,5),vll=.5*vi+x[2];
        return new double[]{(2*vll-x[0])/.3,(1.04-x[1])/.1,((1-.5)*vi-x[2])/.4};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Ac4cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private record Fixture(DStabNetworkBuilder builder,Machine machine,Ac4cExciter exciter){}
}
