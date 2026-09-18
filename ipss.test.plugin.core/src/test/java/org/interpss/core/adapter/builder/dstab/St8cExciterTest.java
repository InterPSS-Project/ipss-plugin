package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.exc.psse.st8c.St8cData;
import org.interpss.dstab.control.exc.psse.st8c.St8cExciter;
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

/** Official-schema, equation, limiter, correction and solver tests for PSS/E ST8C. */
public class St8cExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;

    @Test void parsesExactNativeSchemaAndRejectsPslfName(@TempDir Path dir)throws Exception{
        Path dyr=dir.resolve("st8c.dyr");
        Files.writeString(dyr,"1 'ST8C' 1 1 2 1 2 .1 2 1 10 -10 3 2 9 -9 1.5 .2 8 -8 .2 .3 .04 1 .1 .05 15 1.2 .03 .2 1.1 /\n");
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);
        p.parseDynFile(dyr.toString());St8cExciter e=(St8cExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();assertNotNull(e);
        St8cData d=e.getData();assertEquals(1,d.getOel());assertEquals(2,d.getUel());assertEquals(1,d.getScl());assertEquals(2,d.getSw1());
        assertEquals(.1,d.getTr(),TOL);assertEquals(2,d.getKpr(),TOL);assertEquals(1,d.getKir(),TOL);assertEquals(1.5,d.getKa(),TOL);
        assertEquals(.04,d.getKc1(),TOL);assertEquals(15,d.getThetaP(),TOL);assertEquals(1.2,d.getVb1max(),TOL);
        assertEquals(.03,d.getKc2(),TOL);assertEquals(.2,d.getKi2(),TOL);assertEquals(1.1,d.getVb2max(),TOL);
        assertTrue(p.getLastImportReport().isStrictlyComplete());var descriptor=DynamicModelCatalog.find("ST8C").orElseThrow();
        assertEquals(28,descriptor.parameterCount());assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST8C").orElseThrow().isImplementedExactly());
        assertTrue(DynamicModelCatalog.find("ESST8C").isEmpty());
        Files.writeString(dyr,"1 'ESST8C' 1 1 2 1 2 .1 2 1 10 -10 3 2 9 -9 1.5 .2 8 -8 .2 .3 .04 1 .1 .05 15 1.2 .03 .2 1.1 /\n");
        b=DStabBuilderTestFixture.createWithMachine();p=new PSSEDStabDirectParser(b).setStrictImport(true);
        PSSEDStabDirectParser strictParser=p;assertThrows(Exception.class,()->strictParser.parseDynFile(dyr.toString()));
    }

    @Test void fiveStatesMatchIndependentModifiedEulerOracle()throws Exception{
        Fixture f=fixture(baseData());double[] x=f.exciter.getStateSnapshot();double ifd=ifd(f.machine);
        f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);double dt=.0001,max=0;
        for(int n=0;n<2000;n++){
            double[] d0=derivatives(x,ifd,.1),predict=add(x,d0,dt),d1=derivatives(predict,ifd,.1);
            for(int i=0;i<x.length;i++)x[i]+=.5*(d0[i]+d1[i])*dt;step(f.exciter,f.machine,dt);
            double[] actual=f.exciter.getStateSnapshot();for(int i=0;i<x.length;i++)max=Math.max(max,Math.abs(x[i]-actual[i]));
        }
        assertTrue(max<1e-10,"ST8C five-state max error="+max);
    }

    @Test void routesDirectAndTakeoverLimiterInputs()throws Exception{
        St8cData d=baseData();Fixture sum=fixture(d);sum.exciter.setVuel(.02);sum.exciter.setVoel(-.03);sum.exciter.setVsclSum(.04);
        assertEquals(.09,sum.exciter.getOuterInput(),TOL);
        d=baseData();d.setOel(2);d.setUel(2);d.setScl(2);Fixture gates=fixture(d);
        gates.exciter.setVuel(.2);gates.exciter.setVsclUel(.25);gates.exciter.setVoel(.15);gates.exciter.setVsclOel(.1);
        assertEquals(.1,gates.exciter.getInnerInput(),TOL);
        d=baseData();d.setOel(3);DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St8cExciter invalid=b.addExcSt8c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertFalse(invalid.initStates(m.getDStabBus(),m));
    }

    @Test void implementsBothSupplyAndLoadedRectifierPaths()throws Exception{
        St8cData d=baseData();d.setSw1(1);d.setKp(.9);d.setKi1(.15);d.setXl(.07);d.setThetaP(20);d.setKc1(.04);
        d.setKi2(.2);d.setKc2(.03);Fixture f=fixture(d);Complex angle=new Complex(Math.cos(Math.toRadians(20)),Math.sin(Math.toRadians(20)));
        Complex kpa=angle.multiply(.9),vt=f.machine.getDStabBus().getVoltage(),it=f.machine.getIxy().divide(f.machine.getIMultiFactor());
        double source1=kpa.multiply(vt).add(Complex.I.multiply(new Complex(.15,0).add(kpa.multiply(.07))).multiply(it)).abs();
        double current2=it.multiply(.2).abs(),ifd=ifd(f.machine);
        assertEquals(source1,f.exciter.getCompoundSource(),TOL);assertEquals(current2,f.exciter.getCurrentSource2(),TOL);
        assertEquals(Math.min(99,source1*Exac1Exciter.rectifierFactor(.04*ifd/source1)),f.exciter.getBridge1(),TOL);
        assertEquals(Math.min(99,current2*Exac1Exciter.rectifierFactor(.03*ifd/Math.max(current2,.001))),f.exciter.getBridge2(),TOL);
        assertEquals(f.exciter.getBridgeControl()*f.exciter.getBridge1()+f.exciter.getBridge2(),f.exciter.getOutput(f.machine),TOL);
    }

    @Test void appliesPublishedCorrectionsAndInitializationLimitExpansion()throws Exception{
        St8cData d=baseData();d.setTr(.004);d.setTa(.015);d.setTf(.015);d.setKpr(0);d.setKir(0);d.setKpa(0);d.setKia(0);d.setKa(0);d.setKf(0);
        d.setVpimax(-2);d.setVpimin(-3);d.setVamax(-2);d.setVamin(-3);d.setVrmax(-2);d.setVrmin(-3);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St8cExciter e=b.addExcSt8c("Bus1","1",d);Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(0,e.tr,TOL);assertEquals(.02,e.ta,TOL);
        assertEquals(.02,e.tf,TOL);assertEquals(40,e.kpr,TOL);assertEquals(1,e.kpa,TOL);assertEquals(.02,e.ka,TOL);assertEquals(.02,e.kf,TOL);
        assertTrue(e.vpimax>=e.getIfdReference()&&e.vpimin<=e.getIfdReference());assertTrue(e.vamax>=e.getVaOutput()&&e.vamin<=e.getVaOutput());
        assertTrue(e.vrmax>=e.getBridgeControl()&&e.vrmin<=e.getBridgeControl());
    }

    @Test void holdsEquilibriumAndParticipatesInFullSimulation()throws Exception{
        Fixture f=fixture(baseData());double initial=f.exciter.getOutput(f.machine);for(int i=0;i<1000;i++)step(f.exciter,f.machine,.0001);
        assertEquals(initial,f.exciter.getOutput(f.machine),1e-9);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();assertNotNull(b.addExcSt8c("Bus1","1",baseData()));
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(St8cData d)throws Exception{DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        St8cExciter e=b.addExcSt8c("Bus1","1",d);m.setEfd(1.2);e.configureIntegrationStep(.001);assertNotNull(e);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);}
    private static St8cData baseData(){St8cData d=new St8cData();d.setOel(1);d.setUel(1);d.setScl(1);d.setSw1(2);d.setTr(.1);d.setKpr(2);d.setKir(1);
        d.setVpimax(99);d.setVpimin(-99);d.setKpa(3);d.setKia(2);d.setVamax(99);d.setVamin(-99);d.setKa(1.5);d.setTa(.2);
        d.setVrmax(99);d.setVrmin(-99);d.setKf(.2);d.setTf(.3);d.setKc1(0);d.setKp(1);d.setKi1(0);d.setXl(0);d.setThetaP(0);
        d.setVb1max(99);d.setKc2(0);d.setKi2(0);d.setVb2max(99);return d;}
    private static double[] derivatives(double[] x,double ifd,double referenceStep){double outer=referenceStep,ifdRef=2*outer+x[1];
        double inner=ifdRef-ifd-x[4],va=3*inner+x[2];return new double[]{(1.04-x[0])/.1,outer,2*inner,(1.5*va-x[3])/.2,(.2*ifd-x[4])/.3};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static double ifd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}
    private static void step(St8cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(Machine machine,St8cExciter exciter){}
}
