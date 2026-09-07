package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.dc4b.Dc4bData;
import org.interpss.dstab.control.exc.psse.dc4b.Dc4bExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Import and equation-level regression tests for DC4B/ESDC4B. */
public class Dc4bExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;

    @Test void parsesRealMultilineDc4bRecordAndHoldsEquilibrium(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=dir.resolve("dc4b.dyr");
        Files.writeString(dyr,"1 'DC4B' 1 2 2\n .017 60 6 16\n .017 4 -3.6 1\n .017 .3 .95 0\n 1 0 3.61 .22\n 4.82 .95 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());Machine m=builder.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1);m.setEfd(1.2);Dc4bExciter e=(Dc4bExciter)m.getExciter();
        assertNotNull(e);assertEquals(2,e.getData().getOel());assertEquals(60,e.getData().getKp(),TOL);
        assertEquals(-3.6,e.getData().getVrmin(),TOL);assertEquals(.95,e.getData().getSe2(),TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());assertTrue(e.initStates(m.getDStabBus(),m));
        double initial=e.getOutput(m);for(int i=0;i<1000;i++)step(e,m,.0001);
        assertEquals(initial,e.getOutput(m),1e-8);
    }

    @Test void parsesEsdc4bReorderedSchemaAndAppliesSpeedMultiplier(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=dir.resolve("esdc4b.dyr");
        Files.writeString(dyr,"1 'ESDC4B' 1 .1 2 .2 3 4 .5 .3 9 -8 1 .4 .2 .6 2 .1 4 .2 -.1 1 2 1 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine m=builder.getDStabNetwork().getMachine("Bus1-mach1");m.setSpeed(1.02);m.setEfd(1.2);
        Dc4bExciter e=(Dc4bExciter)m.getExciter();assertNotNull(e);assertEquals("ESDC4B",e.getModelName());
        assertEquals(2,e.getData().getKa(),TOL);assertEquals(3,e.getData().getKp(),TOL);
        assertEquals(-.1,e.getData().getVemin(),TOL);assertEquals(1,e.getData().getSpdmlt(),TOL);
        assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(1.2,e.getOutput(m),TOL);
        assertEquals(1.2/1.02,e.getInternalFieldVoltage(),TOL);
    }

    @Test void sixStateTrajectoryMatchesOpenIpslEquations()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.2,1.04,1.2/(2*1.04),0,1.2,1.2};double dt=.0001,maxField=0,maxSense=0,maxPid=0,maxVr=0,maxFb=0;
        for(int i=0;i<2000;i++){
            double[] d0=derivatives(x,.1),p=add(x,d0,dt),d1=derivatives(p,.1);
            for(int j=0;j<x.length;j++)x[j]+=.5*(d0[j]+d1[j])*dt;
            step(f.exciter,f.machine,dt);
            double feedback=.2*(x[0]-x[5])/.3,error=1.14-x[1]-feedback;
            double pid=3*error+x[2]+.5*(error-x[3])/.1;
            maxField=Math.max(maxField,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));
            maxSense=Math.max(maxSense,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            maxPid=Math.max(maxPid,Math.abs(pid-f.exciter.getPidOutput()));
            maxVr=Math.max(maxVr,Math.abs(x[4]-f.exciter.getRegulatorOutput()));
            maxFb=Math.max(maxFb,Math.abs(feedback-f.exciter.getFeedbackOutput()));
        }
        double maxError=Math.max(maxField,Math.max(maxSense,Math.max(maxPid,Math.max(maxVr,maxFb))));
        assertTrue(maxError<1e-10,"DC4B errors field="+maxField+", sense="+maxSense+", pid="+maxPid+", vr="+maxVr+", fb="+maxFb);
    }

    @Test void implementsLimiterGatesAndTerminalScaledLimits()throws Exception{
        Dc4bData d=baseData();d.setTa(0);d.setVrmax(2);d.setVrmin(-2);d.setUel(2);d.setOel(2);
        Fixture f=fixture(d);f.exciter.setVuel(.8);f.exciter.setRefPoint(f.exciter.getRefPoint()-10);
        assertEquals(.8,f.exciter.getGatedPidOutput(),TOL);
        f.exciter.setVoel(.7);assertEquals(.7,f.exciter.getGatedPidOutput(),TOL);
        Dc4bData scaling=baseData();scaling.setTa(0);scaling.setVrmax(2);scaling.setVrmin(-2);
        Fixture scaled=fixture(scaling);scaled.machine.getDStabBus().setVoltage(new Complex(.5,0));
        scaled.exciter.setRefPoint(scaled.exciter.getRefPoint()+100);
        assertEquals(1,scaled.exciter.getRegulatorOutput(),TOL);
    }

    @Test void appliesPowerWorldCorrectionsAutomaticParametersAndLimitExpansion()throws Exception{
        Dc4bData d=baseData();d.setTr(.004);d.setTd(.015);d.setTf(.015);d.setTa(.005);d.setTe(.005);
        d.setVrmax(0);d.setVrmin(0);d.setKe(0);d.setE2(5);d.setSe2(.5);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Dc4bExciter e=b.addExcDc4b("Bus1","1","DC4B",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setSpeed(1);m.setEfd(1.2);
        e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.td,TOL);assertEquals(.02,e.tf,TOL);
        assertEquals(.02,e.ta,TOL);assertEquals(.02,e.te,TOL);assertTrue(Double.isFinite(e.ke));
        double normalized=e.getRegulatorOutput()/m.getDStabBus().getVoltageMag();
        assertTrue(e.vrmax>=normalized&&e.vrmin<=normalized);
    }

    @Test void catalogAndFullDynamicSolverRecognizeBothNames()throws Exception{
        var descriptor=DynamicModelCatalog.find("ESDC4B").orElseThrow();
        assertEquals("DC4B",descriptor.canonicalName());assertEquals(20,descriptor.parameterCount());
        assertTrue(descriptor.recordSchema().accepts(21));
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();assertNotNull(b.addExcDc4b("Bus1","1","DC4B",baseData()));
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());
        assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(Dc4bData d)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Dc4bExciter e=b.addExcDc4b("Bus1","1","DC4B",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setSpeed(1);m.setEfd(1.2);
        assertNotNull(e);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);
    }
    private static Dc4bData baseData(){Dc4bData d=new Dc4bData();d.setTr(.1);d.setKp(3);d.setKi(4);d.setKd(.5);d.setTd(.1);
        d.setVrmax(100);d.setVrmin(-100);d.setKa(2);d.setTa(.2);d.setKe(1);d.setTe(.4);d.setKf(.2);d.setTf(.3);
        d.setVemin(0);d.setE1(0);d.setSe1(0);d.setE2(0);d.setSe2(0);return d;}
    private static double[] derivatives(double[] x,double refStep){double fb=.2*(x[0]-x[5])/.3,err=1.04+refStep-x[1]-fb;
        double pid=3*err+x[2]+.5*(err-x[3])/.1;return new double[]{(x[4]-x[0])/.4,(1.04-x[1])/.1,4*err,(err-x[3])/.1,(2*1.04*pid-x[4])/.2,(x[0]-x[5])/.3};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Dc4bExciter e,Machine m,double dt){e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0);e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1);}
    private record Fixture(Machine machine,Dc4bExciter exciter){}
}
