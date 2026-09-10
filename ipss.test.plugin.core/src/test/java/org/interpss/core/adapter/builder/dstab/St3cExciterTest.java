package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.exc.psse.st3c.St3cData;
import org.interpss.dstab.control.exc.psse.st3c.St3cExciter;
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

/** Official-schema, equation, limiter and solver tests for native PSS/E ST3C. */
public class St3cExciterTest extends CorePluginTestSetup{
    private static final double TOL=1e-9;
    @Test void parsesExactThirtyOneParameterSchema(@TempDir Path dir)throws Exception{
        Path dyr=dir.resolve("st3c.dyr");Files.writeString(dyr,
                "1 'ST3C' 1 1 2 3 1 .1 5 -5 3 4 .5 .2 6 -6 .05 .15 2 .4 4 -4 1.5 .3 5 -5 .2 6 1 0 0 0 0 2 /\n");
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);
        p.parseDynFile(dyr.toString());Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");St3cExciter e=(St3cExciter)m.getExciter();assertNotNull(e);
        St3cData d=e.getData();assertEquals(1,d.getOel());assertEquals(2,d.getUel());assertEquals(3,d.getScl());assertEquals(1,d.getSw1());
        assertEquals(.1,d.getTr(),TOL);assertEquals(5,d.getVimax(),TOL);assertEquals(-5,d.getVimin(),TOL);assertEquals(3,d.getKpr(),TOL);
        assertEquals(4,d.getKir(),TOL);assertEquals(.5,d.getKdr(),TOL);assertEquals(.2,d.getTdr(),TOL);assertEquals(6,d.getVpidmax(),TOL);
        assertEquals(.05,d.getTc(),TOL);assertEquals(.15,d.getTb(),TOL);assertEquals(2,d.getKa(),TOL);assertEquals(.4,d.getTa(),TOL);
        assertEquals(1.5,d.getKm(),TOL);assertEquals(.3,d.getTm(),TOL);assertEquals(.2,d.getKg(),TOL);assertEquals(6,d.getVgmax(),TOL);
        assertEquals(1,d.getKp(),TOL);assertEquals(2,d.getVbmax(),TOL);assertTrue(p.getLastImportReport().isStrictlyComplete());
        var descriptor=DynamicModelCatalog.find("ST3C").orElseThrow();assertEquals(31,descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST3C").orElseThrow().isImplementedExactly());
    }
    @Test void fiveDynamicStatesAndAlgebraicRegulatorMatchIndependentModifiedEulerOracle()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.04,.52,0,.52,1.04,1.2};double dt=.0001,max=0;
        for(int n=0;n<2000;n++){double[] d0=derivatives(x,.1),p=add(x,d0,dt),d1=derivatives(p,.1);
            for(int i=0;i<6;i++)x[i]+=.5*(d0[i]+d1[i])*dt;step(f.exciter,f.machine,dt);
            double error=1.14-x[0],derivative=.5*(error-x[2])/.2,pid=3*error+x[1]+derivative;
            double lead=1.0/3*pid+2.0/3*x[3],efd=x[5];
            max=Math.max(max,Math.abs(x[0]-f.exciter.getSensedVoltage()));max=Math.max(max,Math.abs(error-f.exciter.getGatedError()));
            max=Math.max(max,Math.abs(pid-f.exciter.getPidOutput()));max=Math.max(max,Math.abs(lead-f.exciter.getLeadLagOutput()));
            double regulator=2*lead;
            max=Math.max(max,Math.abs(regulator-f.exciter.getRegulatorOutput()));max=Math.max(max,Math.abs(efd-f.exciter.getOutput(f.machine)));}
        assertTrue(max<1e-10,"ST3C state/algebraic max error="+max);
    }
    @Test void regulatorTrajectoryIsInvariantToRetainedTaExchangeParameter()throws Exception{
        St3cData firstData=baseData(),secondData=baseData();firstData.setTa(.4);secondData.setTa(.8);
        Fixture first=fixture(firstData),second=fixture(secondData);first.exciter.setRefPoint(first.exciter.getRefPoint()+.1);
        second.exciter.setRefPoint(second.exciter.getRefPoint()+.1);
        for(int n=0;n<2000;n++){step(first.exciter,first.machine,.0001);step(second.exciter,second.machine,.0001);}
        assertEquals(first.exciter.getRegulatorOutput(),second.exciter.getRegulatorOutput(),TOL);
        assertArrayEquals(first.exciter.getStateSnapshot(),second.exciter.getStateSnapshot(),TOL);
    }
    @Test void appliesInputLimitRoutesLimitersAndNormalizesSelectors()throws Exception{
        St3cData d=baseData();d.setVimax(.05);d.setOel(1);d.setUel(1);d.setScl(99);Fixture direct=fixture(d);
        direct.exciter.setRefPoint(direct.exciter.getRefPoint()+1);assertEquals(.05,direct.exciter.getGatedError(),TOL);
        direct.exciter.setRefPoint(direct.exciter.getRefPoint()-1);direct.exciter.setVuel(.2);direct.exciter.setVoel(.1);
        assertEquals(.1,direct.exciter.getRawError(),TOL);assertEquals(.05,direct.exciter.getGatedError(),TOL);
        d=baseData();d.setOel(2);d.setUel(2);Fixture gated=fixture(d);gated.exciter.setVuel(.3);gated.exciter.setVoel(.2);
        assertEquals(.2,gated.exciter.getGatedError(),TOL);
        d=baseData();d.setOel(7);d.setUel(-2);d.setSw1(9);Fixture normalized=fixture(d);
        assertEquals(1,normalized.exciter.oel);assertEquals(1,normalized.exciter.uel);assertEquals(2,normalized.exciter.sw1);
    }
    @Test void appliesBothSourceChoicesLoadedRectifierAndUnityFallback()throws Exception{
        St3cData d=baseData();d.setSw1(1);d.setKp(1);d.setKi(.1);d.setXl(.2);d.setThetaP(10);Fixture source=fixture(d);
        Complex angle=new Complex(Math.cos(Math.toRadians(10)),Math.sin(Math.toRadians(10)));
        Complex vt=source.machine.getDStabBus().getVoltage(),it=source.machine.getIxy().divide(source.machine.getIMultiFactor());
        double expected=angle.multiply(vt).add(Complex.I.multiply(new Complex(.1,0).add(angle.multiply(.2))).multiply(it)).abs();
        assertEquals(expected,source.exciter.getCompoundSource(),TOL);assertEquals(expected,source.exciter.getAvailableBridge(),TOL);
        d.setKc(.2);d.setVbmax(.9);Fixture loaded=fixture(d);double potential=loaded.exciter.getCompoundSource();
        double ifd=loaded.machine.calculateIfd(MachineIfdBase.EXCITER);if(!Double.isFinite(ifd))ifd=0;
        assertEquals(Math.min(.9,potential*Exac1Exciter.rectifierFactor(.2*ifd/potential)),loaded.exciter.getAvailableBridge(),TOL);
        d=baseData();d.setSw1(2);d.setKp(1.3);Fixture independent=fixture(d);assertEquals(1.3,independent.exciter.getAvailableBridge(),TOL);
        assertEquals(1,fixture(baseData()).exciter.getAvailableBridge(),TOL);
    }
    @Test void appliesCorrectionsDefaultsLimitExpansionAndAlgebraicInnerLoop()throws Exception{
        St3cData d=baseData();d.setTr(.004);d.setTb(.015);d.setTa(.004);d.setTm(.015);d.setKa(0);d.setKm(0);d.setKpr(0);d.setKir(0);
        d.setVpidmax(-2);d.setVpidmin(-3);d.setVrmax(-2);d.setVrmin(-3);d.setVmmax(-2);d.setVmmin(-3);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St3cExciter e=b.addExcSt3c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.tb,TOL);assertEquals(0,e.ta,TOL);assertEquals(.02,e.tm,TOL);
        assertEquals(.02,e.ka,TOL);assertEquals(.02,e.km,TOL);assertEquals(40,e.kpr,TOL);
        assertTrue(e.vpidmax>=e.getPidOutput()&&e.vpidmin<=e.getPidOutput());assertTrue(e.vrmax>=e.getRegulatorOutput()&&e.vrmin<=e.getRegulatorOutput());
        assertTrue(e.vmmax>=e.getVmOutput()&&e.vmmin<=e.getVmOutput());
        d=baseData();d.setKdr(0);d.setTb(0);d.setTa(0);d.setTm(0);Fixture algebraic=fixture(d);
        algebraic.exciter.setRefPoint(algebraic.exciter.getRefPoint()+.1);assertEquals(1.8923076923076922,algebraic.exciter.getOutput(algebraic.machine),TOL);
    }
    @Test void preservesStrictCorrectionBoundariesAndRejectsInvalidParameters()throws Exception{
        St3cData d=baseData();d.setTr(.01);d.setTb(.01);d.setTa(.01);d.setTm(.01);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St3cExciter e=b.addExcSt3c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(.01,e.tr,TOL);assertEquals(.01,e.tb,TOL);assertEquals(.01,e.ta,TOL);assertEquals(.01,e.tm,TOL);
        d=baseData();d.setVimax(-1);d.setVimin(1);b=DStabBuilderTestFixture.createWithMachine();e=b.addExcSt3c("Bus1","1",d);
        m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertFalse(e.initStates(m.getDStabBus(),m));
    }
    @Test void holdsEquilibriumAndParticipatesInFullSimulation()throws Exception{
        Fixture f=fixture(baseData());double initial=f.exciter.getOutput(f.machine);for(int i=0;i<1000;i++)step(f.exciter,f.machine,.0001);
        assertEquals(initial,f.exciter.getOutput(f.machine),TOL);DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        assertNotNull(b.addExcSt3c("Bus1","1",baseData()));DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());
        assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());assertTrue(a.performSimulation());
    }
    private static Fixture fixture(St3cData d)throws Exception{DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");St3cExciter e=b.addExcSt3c("Bus1","1",d);m.setEfd(1.2);
        assertNotNull(e);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);}
    private static St3cData baseData(){St3cData d=new St3cData();d.setOel(1);d.setUel(1);d.setScl(0);d.setSw1(2);d.setTr(.1);
        d.setVimax(99);d.setVimin(-99);d.setKpr(3);d.setKir(4);d.setKdr(.5);d.setTdr(.2);d.setVpidmax(99);d.setVpidmin(-99);
        d.setTc(.05);d.setTb(.15);d.setKa(2);d.setTa(.4);d.setVrmax(99);d.setVrmin(-99);d.setKm(1.5);d.setTm(.3);
        d.setVmmax(99);d.setVmmin(-99);d.setKg(.2);d.setVgmax(99);d.setKp(0);d.setKi(0);d.setXl(0);d.setThetaP(0);d.setKc(0);d.setVbmax(99);return d;}
    private static double[] derivatives(double[] x,double step){double error=1.04+step-x[0],derivative=.5*(error-x[2])/.2;
        double pid=3*error+x[1]+derivative,lead=1.0/3*pid+2.0/3*x[3],regulator=2*lead,efd=x[5];
        return new double[]{(1.04-x[0])/.1,4*error,(error-x[2])/.2,(pid-x[3])/.15,0,(1.5*(regulator-.2*efd)-x[5])/.3};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(St3cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(Machine machine,St3cExciter exciter){}
}
