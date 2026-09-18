package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exac4.Exac4Data;
import org.interpss.dstab.control.exc.psse.exac4.Exac4Exciter;
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

/** Exact import, PowerWorld topology, and ANDES equation tests for EXAC4. */
public class Exac4ExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;

    @Test void parsesExactRecordAndCatalogsRuntimeSupport(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("exac4.dyr");
        Files.writeString(dyr,"1 'EXAC4' 1 .01 7 -2 .2 .4 80 .04 8 -1 .05 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Exac4Exciter e=(Exac4Exciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(e);Exac4Data d=e.getData();assertEquals(.01,d.getTr(),TOL);assertEquals(7,d.getVimax(),TOL);
        assertEquals(-2,d.getVimin(),TOL);assertEquals(.2,d.getTc(),TOL);assertEquals(.4,d.getTb(),TOL);
        assertEquals(80,d.getKa(),TOL);assertEquals(.04,d.getTa(),TOL);assertEquals(8,d.getVrmax(),TOL);
        assertEquals(-1,d.getVrmin(),TOL);assertEquals(.05,d.getKc(),TOL);assertTrue(p.getLastImportReport().isStrictlyComplete());
        var descriptor=DynamicModelCatalog.find("EXAC4").orElseThrow();assertEquals(10,descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("EXAC4").orElseThrow().isImplementedExactly());
    }

    @Test void threeStateTrajectoryMatchesAndesExac4Equations()throws Exception{
        Exac4Data data=baseData();Fixture f=fixture(data);f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double vi0=1.2/2,ratio=.2/.4;double[] x={1.2,1.04,vi0-ratio*vi0};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){
            double[] d0=andesDerivatives(x,.1),predicted=add(x,d0,dt),d1=andesDerivatives(predicted,.1);
            for(int j=0;j<3;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            max=Math.max(max,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            double expectedLeadLag=ratio*clamp(1.04+vi0+.1-x[1],-5,5)+x[2];
            max=Math.max(max,Math.abs(expectedLeadLag-f.exciter.getLeadLagOutput()));
        }
        assertTrue(max<1e-9,"EXAC4/ANDES max state error="+max);
    }

    @Test void sumsBothExternalLimitersBeforeInputClamp()throws Exception{
        Exac4Data d=baseData();d.setVimax(.65);d.setVimin(-.65);Fixture f=fixture(d);
        f.exciter.setVuel(.2);f.exciter.setVoel(-.1);
        assertEquals(.7,f.exciter.getVoltageError(),TOL);
        assertEquals(.65,f.exciter.getLimitedError(),TOL);
    }

    @Test void shiftsBothFieldLimitsByCommutatingCurrent()throws Exception{
        Fixture f=fixture(baseData());DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(f.builder.getDStabNetwork());
        a.setSimuOutputHandler(new StateMonitor());
        assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());
        double ifd=f.machine.calculateIfd(MachineIfdBase.EXCITER);assertTrue(Double.isFinite(ifd));
        assertEquals(f.exciter.vrmax-f.exciter.kc*ifd,f.exciter.getDynamicUpperLimit(),TOL);
        assertEquals(f.exciter.vrmin-f.exciter.kc*ifd,f.exciter.getDynamicLowerLimit(),TOL);
        assertEquals(f.exciter.vrmax-f.exciter.vrmin,
                f.exciter.getDynamicUpperLimit()-f.exciter.getDynamicLowerLimit(),TOL);
    }

    @Test void appliesCorrectionsExpandsLimitsAndParticipatesInSolver()throws Exception{
        Exac4Data d=baseData();d.setTr(.004);d.setTb(.015);d.setTa(.01);d.setVimax(-1);d.setVimin(-2);d.setVrmax(-1);d.setVrmin(-2);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Exac4Exciter e=b.addExcExac4("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.tb,TOL);assertEquals(.02,e.ta,TOL);
        assertTrue(e.vimax>=e.getLimitedError());assertTrue(e.vimin<=e.getLimitedError());
        assertTrue(e.getDynamicUpperLimit()>=1.2-TOL);assertTrue(e.getDynamicLowerLimit()<=1.2+TOL);
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(Double.isFinite(m.calculateIfd(MachineIfdBase.EXCITER)));assertTrue(a.performSimulation());
    }

    private static Fixture fixture(Exac4Data d)throws Exception{DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Exac4Exciter e=b.addExcExac4("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(b,m,e);}
    private static Exac4Data baseData(){Exac4Data d=new Exac4Data();d.setTr(.1);d.setVimax(5);d.setVimin(-5);d.setTc(.2);d.setTb(.4);
        d.setKa(2);d.setTa(.3);d.setVrmax(5);d.setVrmin(-5);d.setKc(.1);return d;}
    private static double[] andesDerivatives(double[] x,double step){double error=1.04+.6+step-x[1],vi=clamp(error,-5,5),vll=.5*vi+x[2];
        return new double[]{(2*vll-x[0])/.3,(1.04-x[1])/.1,((1-.5)*vi-x[2])/.4};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Exac4Exciter e,Machine m,double dt){e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0);e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1);}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private record Fixture(DStabNetworkBuilder builder,Machine machine,Exac4Exciter exciter){}
}
