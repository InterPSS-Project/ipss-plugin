package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.st6b.St6bData;
import org.interpss.dstab.control.exc.psse.st6b.St6bExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabObjectFactory;import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;import com.interpss.dstab.cache.StateMonitor;import com.interpss.dstab.mach.Machine;

/** Import and equation-level tests for ST6B/ESST6B. */
public class St6bExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;
    @Test void parsesRealMultilineSt6bAndHoldsEquilibrium(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("st6b.dyr");
        Files.writeString(dyr,"1 'ST6B' 1 1\n .051 55 5 0\n 1 4.81 -3.85 1.4\n .2 1.5 17.33 4.164\n 4.5 -7.5 .1 .5 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);St6bExciter e=(St6bExciter)m.getExciter();
        assertNotNull(e);assertEquals(1,e.getData().getOel());assertEquals(1,e.getData().getVrmult());assertEquals(55,e.getData().getKpa(),TOL);
        assertEquals(17.33,e.getData().getKlr(),TOL);assertEquals(.5,e.getData().getTg(),TOL);
        assertTrue(p.getLastImportReport().isStrictlyComplete());assertTrue(e.initStates(m.getDStabBus(),m));
        double initial=e.getOutput(m);for(int i=0;i<1000;i++)step(e,m,.0001);assertEquals(initial,e.getOutput(m),1e-8);
    }
    @Test void parsesEsst6bLayoutAndOptionalBlocks(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("esst6b.dyr");
        Files.writeString(dyr,"1 'ESST6B' 1 .1 3 4 10 -10 .4 .6 .2 .3 5 -5 1 2 100 1 1 .2 /\n");
        new PSSEDStabDirectParser(b).setStrictImport(true).parseDynFile(dyr.toString());Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        St6bExciter e=(St6bExciter)m.getExciter();assertNotNull(e);assertEquals("ESST6B",e.getModelName());
        assertEquals(.4,e.getData().getKff(),TOL);assertEquals(1,e.getData().getVrmult());assertEquals(.2,e.getData().getTs(),TOL);
        assertTrue(e.initStates(m.getDStabBus(),m));for(int i=0;i<1000;i++)step(e,m,.0001);assertEquals(1.2,e.getOutput(m),1e-8);
    }
    @Test void fiveStateTrajectoryMatchesPublishedEquations()throws Exception{
        St6bData d=baseData();d.setKda(0);d.setTs(.2);Fixture f=fixture("ESST6B",d);f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.2,1.04,1.344,0,.24};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){double[] d0=derivatives(x,.1),p=add(x,d0,dt),d1=derivatives(p,.1);for(int j=0;j<5;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            double err=1.14-x[1],va=3*err+x[2],vr=va-.6*x[4];
            max=Math.max(max,Math.abs(x[0]-f.exciter.getOutput(f.machine)));max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            max=Math.max(max,Math.abs(va-f.exciter.getVaOutput()));max=Math.max(max,Math.abs(x[4]-f.exciter.getVgOutput()));max=Math.max(max,Math.abs(vr-f.exciter.getVrOutput()));}
        assertTrue(max<1e-10,"ST6B five-state max error="+max);
    }
    @Test void preservesOelGateOrderCurrentLimitAndVoltageMultiplier()throws Exception{
        St6bData before=baseData();before.setOel(1);Fixture a=fixture("ST6B",before);a.exciter.setVuel(0);a.exciter.setVoel(.5);assertEquals(0,a.exciter.getError(),TOL);
        St6bData after=baseData();after.setOel(2);Fixture b=fixture("ST6B",after);b.exciter.setVuel(0);b.exciter.setVoel(.5);assertEquals(-.5,b.exciter.getError(),TOL);
        St6bData limited=baseData();limited.setKcl(0);limited.setIlr(0);limited.setKlr(1);Fixture c=fixture("ST6B",limited);assertTrue(c.exciter.getVrOutput()<=0);
        St6bData scaled=baseData();scaled.setVrmult(1);scaled.setTs(0);scaled.setVrmax(2);scaled.setVrmin(-2);Fixture s=fixture("ESST6B",scaled);
        s.machine.getDStabBus().setVoltage(new Complex(.5,0));s.exciter.setRefPoint(s.exciter.getRefPoint()+100);assertEquals(1,s.exciter.getOutput(s.machine),TOL);
    }
    @Test void appliesPowerWorldCorrectionsAndInitializationExpansion()throws Exception{
        St6bData d=baseData();d.setTr(.004);d.setTg(.015);d.setTs(.015);d.setKpa(0);d.setKia(0);d.setVamax(-5);d.setVamin(5);d.setVrmax(-3);d.setVrmin(-2);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St6bExciter e=b.addExcSt6b("Bus1","1","ESST6B",d);Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(0,e.tr,TOL);assertEquals(.02,e.tg,TOL);assertEquals(.02,e.ts,TOL);
        assertEquals(1,e.kpa,TOL);assertEquals(1,e.kia,TOL);assertTrue(e.vrmax>=e.getVrOutput()&&e.vrmin<=e.getVrOutput());
    }
    @Test void catalogAndFullSolverRecognizeBothNames()throws Exception{
        var descriptor=DynamicModelCatalog.find("ESST6B").orElseThrow();assertEquals("ST6B",descriptor.canonicalName());assertEquals(17,descriptor.parameterCount());assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();assertNotNull(b.addExcSt6b("Bus1","1","ST6B",baseData()));DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());
        assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());assertTrue(a.performSimulation());
    }
    private static Fixture fixture(String name,St6bData d)throws Exception{DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St6bExciter e=b.addExcSt6b("Bus1","1",name,d);Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);}
    private static St6bData baseData(){St6bData d=new St6bData();d.setTr(.1);d.setKpa(3);d.setKia(4);d.setKda(.5);d.setTda(.1);d.setVamax(10);d.setVamin(-10);d.setKff(.4);d.setKm(.6);d.setKcl(1);d.setKlr(1);d.setIlr(100);d.setVrmax(5);d.setVrmin(-5);d.setKg(.2);d.setTg(.3);return d;}
    private static double[] derivatives(double[]x,double step){double err=1.04+step-x[1],va=3*err+x[2],vr=va-.6*x[4];return new double[]{(vr-x[0])/.2,(1.04-x[1])/.1,4*err,0,(.2*vr-x[4])/.3};}
    private static double[] add(double[]x,double[]d,double dt){double[]y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(St6bExciter e,Machine m,double dt){e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0);e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1);}private record Fixture(Machine machine,St6bExciter exciter){}
}
