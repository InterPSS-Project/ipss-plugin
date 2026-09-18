package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.exc.psse.st4c.St4cData;
import org.interpss.dstab.control.exc.psse.st4c.St4cExciter;
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
import com.interpss.dstab.controller.deqn.AbstractStabilizer;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** Official-schema, equation, limiter and solver tests for native PSS/E ST4C. */
public class St4cExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;

    @Test void parsesOfficialTwentySixParameterSchemaAndRealRecord(@TempDir Path dir)throws Exception{
        Path dyr=dir.resolve("st4c.dyr");
        Files.writeString(dyr,"1 'ST4C' 1 1 2 2 1 1 0 80 15.2 4.04 -3.11 1 0 4.04 -3.11 .02 4.04 -3.11 0 1 0 1 0 0 0 0 1.1 /\n");
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");St4cExciter e=(St4cExciter)m.getExciter();assertNotNull(e);
        St4cData d=e.getData();assertEquals(1,d.getVos());assertEquals(2,d.getOel());assertEquals(2,d.getUel());
        assertEquals(1,d.getScl());assertEquals(1,d.getSw1());assertEquals(80,d.getKpr(),TOL);assertEquals(15.2,d.getKir(),TOL);
        assertEquals(4.04,d.getVrmax(),TOL);assertEquals(-3.11,d.getVrmin(),TOL);assertEquals(1,d.getKpm(),TOL);
        assertEquals(.02,d.getTa(),TOL);assertEquals(1,d.getTg(),TOL);assertEquals(1,d.getKp(),TOL);assertEquals(1.1,d.getVbmax(),TOL);
        assertTrue(p.getLastImportReport().isStrictlyComplete());var descriptor=DynamicModelCatalog.find("ST4C").orElseThrow();
        assertEquals(26,descriptor.parameterCount());assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST4C").orElseThrow().isImplementedExactly());
        m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));double initial=e.getOutput(m);
        for(int i=0;i<1000;i++)step(e,m,.0001);assertEquals(initial,e.getOutput(m),1e-9);
    }

    @Test void parsesExactSt4cu1WrapperAllocationAndPublishedFieldOrder(@TempDir Path dir)throws Exception{
        Path dyr=dir.resolve("st4cu1.dyr");
        Files.writeString(dyr,"1 'USRMDL' '1' 'ST4CU1' 4 0 5 21 5 0 1 1 1 1 2"
                +" .1 3 4 99 -99 2 5 99 -99 .2 99 -99 .2 .3 99 1 0 0 0 0 99 /\n");
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");St4cExciter e=(St4cExciter)m.getExciter();assertNotNull(e);
        assertTrue(p.getLastImportReport().isStrictlyComplete());assertEquals(4,e.getData().getKir(),TOL);
        assertEquals(2,e.getData().getKpm(),TOL);assertEquals(99,e.getData().getVbmax(),TOL);
        m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(5,e.getNamedStates().size());
    }

    @Test void rejectsSt4cu1WithIncorrectStateAllocation(@TempDir Path dir)throws Exception{
        Path dyr=dir.resolve("st4cu1-invalid.dyr");
        Files.writeString(dyr,"1 'USRMDL' '1' 'ST4CU1' 4 0 5 21 4 0 1 1 1 1 2"
                +" .1 3 4 99 -99 2 5 99 -99 .2 99 -99 .2 .3 99 1 0 0 0 0 99 /\n");
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b);p.parseDynFile(dyr.toString());
        assertFalse(p.getLastImportReport().failures().isEmpty());
    }

    @Test void fiveStatesMatchIndependentModifiedEulerOracle()throws Exception{
        Fixture f=fixture(baseData(),0);f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.04,.24,1.2,.24,1.2};double dt=.0001,max=0;
        for(int n=0;n<2000;n++){
            double[] d0=derivatives(x,.1),p=add(x,d0,dt),d1=derivatives(p,.1);
            for(int i=0;i<5;i++)x[i]+=.5*(d0[i]+d1[i])*dt;step(f.exciter,f.machine,dt);
            double u=1.04+.1-x[0],vr=3*u+x[1],inner=vr-x[3],vm=2*inner+x[2];
            max=Math.max(max,Math.abs(x[0]-f.exciter.getSensedVoltage()));max=Math.max(max,Math.abs(u-f.exciter.getOuterInput()));
            max=Math.max(max,Math.abs(vr-f.exciter.getVrOutput()));max=Math.max(max,Math.abs(inner-f.exciter.getInnerInput()));
            max=Math.max(max,Math.abs(vm-f.exciter.getVaInput()));max=Math.max(max,Math.abs(x[4]-f.exciter.getOutput(f.machine)));
        }
        assertTrue(max<1e-10,"ST4C five-state max error="+max);
    }

    @Test void routesBothLimiterStagesAndBothPssLocations()throws Exception{
        St4cData d=baseData();d.setOel(1);d.setUel(1);d.setScl(1);Fixture sum=fixture(d,0);
        sum.exciter.setVoel(-.01);sum.exciter.setVuel(.02);sum.exciter.setVsclOel(-.03);sum.exciter.setVsclUel(.04);
        assertEquals(.02,sum.exciter.getOuterInput(),TOL);
        d=baseData();d.setOel(2);d.setUel(2);d.setScl(2);Fixture first=fixture(d,0);
        first.exciter.setVuel(.2);first.exciter.setVsclUel(.25);first.exciter.setVoel(.15);first.exciter.setVsclOel(.1);
        assertEquals(.1,first.exciter.getOuterInput(),TOL);
        d=baseData();d.setOel(3);d.setUel(3);d.setScl(3);Fixture second=fixture(d,0);
        second.exciter.setVuel(2);second.exciter.setVsclUel(2.5);second.exciter.setVoel(2.4);second.exciter.setVsclOel(2.3);
        assertEquals(2.3,second.exciter.getVaInput(),TOL);
        Fixture pss1=fixture(baseData(),.2);assertEquals(0,pss1.exciter.getOuterInput(),TOL);
        d=baseData();d.setVos(2);Fixture pss2=fixture(d,.2);assertEquals(0,pss2.exciter.getOuterInput(),TOL);
    }

    @Test void appliesSourceRectifierCorrectionsLimitsAndZeroTimeFeedback()throws Exception{
        St4cData d=baseData();d.setSw1(1);Fixture source=fixture(d,0);Complex vt=source.machine.getDStabBus().getVoltage();
        Complex it=source.machine.getIxy().divide(source.machine.getIMultiFactor());
        assertEquals(vt.add(Complex.I.multiply(it).multiply(source.exciter.xl)).abs(),source.exciter.getPotentialSource(),TOL);
        assertEquals(source.exciter.getPotentialSource(),source.exciter.getAvailableBridge(),TOL);
        d=baseData();d.setSw1(1);d.setKc(.2);d.setVbmax(.9);Fixture loaded=fixture(d,0);
        double potential=loaded.exciter.getPotentialSource();double ifd=loaded.machine.calculateIfd(MachineIfdBase.EXCITER);
        if(!Double.isFinite(ifd))ifd=0;
        assertEquals(Math.min(.9,potential*Exac1Exciter.rectifierFactor(.2*ifd/potential)),loaded.exciter.getAvailableBridge(),TOL);
        d=baseData();d.setTr(.004);d.setTg(.015);d.setTa(.015);d.setKpr(0);d.setKir(0);d.setKpm(0);d.setKim(0);
        d.setVrmax(-2);d.setVrmin(-3);d.setVmmax(-2);d.setVmmin(-3);d.setVamax(-2);d.setVamin(-3);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St4cExciter corrected=b.addExcSt4c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);corrected.configureIntegrationStep(.01,2);
        assertTrue(corrected.initStates(m.getDStabBus(),m));assertEquals(0,corrected.tr,TOL);assertEquals(.02,corrected.tg,TOL);
        assertEquals(.02,corrected.ta,TOL);assertEquals(40,corrected.kpr,TOL);assertEquals(1,corrected.kpm,TOL);
        assertTrue(corrected.vrmax>=corrected.getVrOutput()&&corrected.vrmin<=corrected.getVrOutput());
        assertTrue(corrected.vmmax>=corrected.getVmOutput()&&corrected.vmmin<=corrected.getVmOutput());
        assertTrue(corrected.vamax>=corrected.getVaOutput()&&corrected.vamin<=corrected.getVaOutput());
        d=baseData();d.setTg(0);d.setTa(0);Fixture algebraic=fixture(d,0);algebraic.exciter.setRefPoint(algebraic.exciter.getRefPoint()+.1);
        assertEquals(2.28/1.4,algebraic.exciter.getOutput(algebraic.machine),1e-9);
        assertEquals(.2*(2.28/1.4),algebraic.exciter.getVgOutput(),1e-9);
    }

    @Test void rejectsInvalidSelectorsAndPreservesStrictHalfStepBoundary()throws Exception{
        St4cData bad=baseData();bad.setOel(4);DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        St4cExciter invalid=b.addExcSt4c("Bus1","1",bad);Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        assertFalse(invalid.initStates(m.getDStabBus(),m));
        St4cData boundary=baseData();boundary.setTr(.01);boundary.setTg(.01);boundary.setTa(.01);
        b=DStabBuilderTestFixture.createWithMachine();St4cExciter e=b.addExcSt4c("Bus1","1",boundary);
        m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);
        assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(.01,e.tr,TOL);assertEquals(.01,e.tg,TOL);assertEquals(.01,e.ta,TOL);
    }

    @Test void holdsEquilibriumAndParticipatesInFullSimulation()throws Exception{
        Fixture f=fixture(baseData(),0);double initial=f.exciter.getOutput(f.machine);for(int i=0;i<1000;i++)step(f.exciter,f.machine,.0001);
        assertEquals(initial,f.exciter.getOutput(f.machine),1e-9);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();assertNotNull(b.addExcSt4c("Bus1","1",baseData()));
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(St4cData d,double pss)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        if(pss!=0)new FixedOutputStabilizer(m,pss);St4cExciter e=b.addExcSt4c("Bus1","1",d);m.setEfd(1.2);
        assertNotNull(e);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);
    }
    private static St4cData baseData(){St4cData d=new St4cData();d.setVos(1);d.setOel(1);d.setUel(1);d.setScl(1);d.setSw1(2);
        d.setTr(.1);d.setKpr(3);d.setKir(4);d.setVrmax(99);d.setVrmin(-99);d.setKpm(2);d.setKim(5);
        d.setVmmax(99);d.setVmmin(-99);d.setTa(.2);d.setVamax(99);d.setVamin(-99);d.setKg(.2);d.setTg(.3);
        d.setVgmax(99);d.setKp(1);d.setKi(0);d.setXl(0);d.setThetaP(0);d.setKc(0);d.setVbmax(99);return d;}
    private static double[] derivatives(double[] x,double step){double u=1.04+step-x[0],vr=3*u+x[1],inner=vr-x[3],vm=2*inner+x[2];
        return new double[]{(1.04-x[0])/.1,4*u,5*inner,(.2*x[4]-x[3])/.3,(vm-x[4])/.2};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(St4cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(Machine machine,St4cExciter exciter){}

    private static final class FixedOutputStabilizer extends AbstractStabilizer {
        private final double output;FixedOutputStabilizer(Machine machine,double output){super("fixed-pss","Fixed PSS","test");this.output=output;setMachine(machine);}
        @Override public double getOutput(Machine machine){return output;}
    }
}
