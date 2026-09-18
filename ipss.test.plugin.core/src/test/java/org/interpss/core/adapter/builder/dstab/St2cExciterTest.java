package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.exc.psse.st2c.St2cData;
import org.interpss.dstab.control.exc.psse.st2c.St2cExciter;
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

/** Official-schema, equation, limiter and solver tests for native PSS/E ST2C. */
public class St2cExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;

    @Test void parsesExactTwentyFiveParameterSchema(@TempDir Path dir)throws Exception{
        Path dyr=dir.resolve("st2c.dyr");
        Files.writeString(dyr,"1 'ST2C' 1 1 2 3 .1 3 4 .5 .2 5 -5 2 .4 4 -4 .5 6 1.2 .2 .3 1 0 0 0 0 2 /\n");
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");St2cExciter e=(St2cExciter)m.getExciter();assertNotNull(e);
        St2cData d=e.getData();assertEquals(1,d.getOel());assertEquals(2,d.getUel());assertEquals(3,d.getScl());
        assertEquals(.1,d.getTr(),TOL);assertEquals(3,d.getKpr(),TOL);assertEquals(4,d.getKir(),TOL);
        assertEquals(.5,d.getKdr(),TOL);assertEquals(.2,d.getTdr(),TOL);assertEquals(5,d.getVpidmax(),TOL);
        assertEquals(-5,d.getVpidmin(),TOL);assertEquals(2,d.getKa(),TOL);assertEquals(.4,d.getTa(),TOL);
        assertEquals(4,d.getVrmax(),TOL);assertEquals(-4,d.getVrmin(),TOL);assertEquals(.5,d.getTe(),TOL);
        assertEquals(6,d.getEfdmax(),TOL);assertEquals(1.2,d.getKe(),TOL);assertEquals(.2,d.getKf(),TOL);
        assertEquals(.3,d.getTf(),TOL);assertEquals(1,d.getKp(),TOL);assertEquals(2,d.getVbmax(),TOL);
        assertTrue(p.getLastImportReport().isStrictlyComplete());var descriptor=DynamicModelCatalog.find("ST2C").orElseThrow();
        assertEquals(25,descriptor.parameterCount());assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST2C").orElseThrow().isImplementedExactly());
    }

    @Test void sixStatesMatchIndependentModifiedEulerOracle()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.04,.72,0,1.44,1.2,1.2};double dt=.0001,max=0;
        for(int n=0;n<2000;n++){
            double[] d0=derivatives(x,.1),p=add(x,d0,dt),d1=derivatives(p,.1);
            for(int i=0;i<6;i++)x[i]+=.5*(d0[i]+d1[i])*dt;step(f.exciter,f.machine,dt);
            double feedback=.2*(x[4]-x[5])/.3,error=1.14-x[0]-feedback;
            double derivative=.5*(error-x[2])/.2,pid=3*error+x[1]+derivative;
            max=Math.max(max,Math.abs(x[0]-f.exciter.getSensedVoltage()));
            max=Math.max(max,Math.abs(error-f.exciter.getPidError()));max=Math.max(max,Math.abs(pid-f.exciter.getPidOutput()));
            max=Math.max(max,Math.abs(x[3]-f.exciter.getRegulatorOutput()));max=Math.max(max,Math.abs(x[4]-f.exciter.getOutput(f.machine)));
        }
        assertTrue(max<1e-10,"ST2C six-state max error="+max);
    }

    @Test void routesOelAndUelAndIgnoresSclPerPsse()throws Exception{
        St2cData d=baseData();d.setOel(1);d.setUel(1);d.setScl(99);Fixture direct=fixture(d);
        direct.exciter.setVuel(.2);direct.exciter.setVoel(.1);assertEquals(.1,direct.exciter.getPidError(),TOL);
        d=baseData();d.setOel(2);d.setUel(2);Fixture gated=fixture(d);
        gated.exciter.setVuel(.9);gated.exciter.setVoel(.8);assertEquals(.8,gated.exciter.getGatedPidOutput(),TOL);
        d=baseData();d.setOel(7);d.setUel(-3);Fixture normalized=fixture(d);
        assertEquals(1,normalized.exciter.oel);assertEquals(1,normalized.exciter.uel);
    }

    @Test void appliesCompoundSourceLoadedRectifierAndUnityFallback()throws Exception{
        St2cData d=baseData();d.setKp(1);d.setKi(.1);d.setXl(.2);d.setThetaP(10);Fixture source=fixture(d);
        Complex angle=new Complex(Math.cos(Math.toRadians(10)),Math.sin(Math.toRadians(10)));
        Complex vt=source.machine.getDStabBus().getVoltage(),it=source.machine.getIxy().divide(source.machine.getIMultiFactor());
        double expected=angle.multiply(vt).add(Complex.I.multiply(new Complex(.1,0).add(angle.multiply(.2))).multiply(it)).abs();
        assertEquals(expected,source.exciter.getCompoundSource(),TOL);assertEquals(expected,source.exciter.getAvailableBridge(),TOL);
        d.setKc(.2);d.setVbmax(.9);Fixture loaded=fixture(d);double potential=loaded.exciter.getCompoundSource();
        double ifd=loaded.machine.calculateIfd(MachineIfdBase.EXCITER);if(!Double.isFinite(ifd))ifd=0;
        assertEquals(Math.min(.9,potential*Exac1Exciter.rectifierFactor(.2*ifd/potential)),loaded.exciter.getAvailableBridge(),TOL);
        Fixture fallback=fixture(baseData());assertEquals(1,fallback.exciter.getAvailableBridge(),TOL);
    }

    @Test void appliesCorrectionsDefaultsLimitExpansionAndAlgebraicPaths()throws Exception{
        St2cData d=baseData();d.setTr(.004);d.setTa(.015);d.setTe(.004);d.setTf(.015);d.setKa(0);d.setKpr(0);d.setKir(0);
        d.setVpidmax(-2);d.setVpidmin(-3);d.setVrmax(-2);d.setVrmin(-3);d.setEfdmax(-2);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St2cExciter e=b.addExcSt2c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);
        assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(0,e.tr,TOL);assertEquals(.02,e.ta,TOL);
        assertEquals(0,e.te,TOL);assertEquals(.02,e.tf,TOL);assertEquals(.02,e.ka,TOL);assertEquals(40,e.kpr,TOL);
        assertTrue(e.vpidmax>=e.getPidOutput()&&e.vpidmin<=e.getPidOutput());
        assertTrue(e.vrmax>=e.getRegulatorOutput()&&e.vrmin<=e.getRegulatorOutput());assertTrue(e.efdmax>=e.getOutput(m));
        e.setRefPoint(e.getRefPoint()+.01);assertTrue(Double.isFinite(e.getOutput(m)));
        d=baseData();d.setKdr(0);d.setTa(0);d.setTe(0);d.setTf(0);Fixture algebraic=fixture(d);algebraic.exciter.setRefPoint(algebraic.exciter.getRefPoint()+.1);
        assertEquals(1.2+2*3*.1/1.2,algebraic.exciter.getOutput(algebraic.machine),TOL);
        d=baseData();d.setKdr(0);d.setTa(0);d.setTe(0);Fixture coupled=fixture(d);coupled.exciter.setRefPoint(coupled.exciter.getRefPoint()+.1);
        assertEquals(1.3153846153846154,coupled.exciter.getOutput(coupled.machine),TOL);
    }

    @Test void preservesStrictCorrectionBoundariesAndRejectsInvalidParameters()throws Exception{
        St2cData d=baseData();d.setTr(.01);d.setTa(.01);d.setTe(.01);d.setTf(.01);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St2cExciter e=b.addExcSt2c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);
        assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(.01,e.tr,TOL);assertEquals(.01,e.ta,TOL);
        assertEquals(.01,e.te,TOL);assertEquals(.01,e.tf,TOL);
        d=baseData();d.setKa(-1);b=DStabBuilderTestFixture.createWithMachine();e=b.addExcSt2c("Bus1","1",d);
        m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertFalse(e.initStates(m.getDStabBus(),m));
    }

    @Test void holdsEquilibriumAndParticipatesInFullSimulation()throws Exception{
        Fixture f=fixture(baseData());double initial=f.exciter.getOutput(f.machine);for(int i=0;i<1000;i++)step(f.exciter,f.machine,.0001);
        assertEquals(initial,f.exciter.getOutput(f.machine),1e-9);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();assertNotNull(b.addExcSt2c("Bus1","1",baseData()));
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(St2cData d)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        St2cExciter e=b.addExcSt2c("Bus1","1",d);m.setEfd(1.2);assertNotNull(e);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);
    }
    private static St2cData baseData(){St2cData d=new St2cData();d.setOel(1);d.setUel(1);d.setScl(0);d.setTr(.1);
        d.setKpr(3);d.setKir(4);d.setKdr(.5);d.setTdr(.2);d.setVpidmax(99);d.setVpidmin(-99);
        d.setKa(2);d.setTa(.4);d.setVrmax(99);d.setVrmin(-99);d.setTe(.5);d.setEfdmax(99);d.setKe(1.2);
        d.setKf(.2);d.setTf(.3);d.setKp(0);d.setKi(0);d.setXl(0);d.setThetaP(0);d.setKc(0);d.setVbmax(99);return d;}
    private static double[] derivatives(double[] x,double step){double feedback=.2*(x[4]-x[5])/.3;
        double error=1.04+step-x[0]-feedback,derivative=.5*(error-x[2])/.2,pid=3*error+x[1]+derivative;
        return new double[]{(1.04-x[0])/.1,4*error,(error-x[2])/.2,(2*pid-x[3])/.4,(x[3]-1.2*x[4])/.5,(x[4]-x[5])/.3};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(St2cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(Machine machine,St2cExciter exciter){}
}
