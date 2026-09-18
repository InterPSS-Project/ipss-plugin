package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.esst2a.Esst2aExciter;
import org.interpss.dstab.control.exc.psse.exst2.Exst2Data;
import org.interpss.dstab.control.exc.psse.exst2.Exst2Exciter;
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

/** Exact import and PowerWorld equation tests for the additive IEEE Type ST2. */
public class Exst2ExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;

    @Test void parsesExactPsseRecordAndCatalogsRuntimeSupport(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("exst2.dyr");
        Files.writeString(dyr,"1 'EXST2' 1 .01 50 .2 1.5 0 1 .25 .015 .2 3.1 4.5 1.4 3.7 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Exst2Exciter e=(Exst2Exciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(e);Exst2Data d=e.getData();assertEquals(.01,d.getTr(),TOL);assertEquals(50,d.getKa(),TOL);
        assertEquals(.2,d.getTa(),TOL);assertEquals(1.5,d.getVrmax(),TOL);assertEquals(0,d.getVrmin(),TOL);
        assertEquals(1,d.getKe(),TOL);assertEquals(.25,d.getTe(),TOL);assertEquals(.015,d.getKf(),TOL);
        assertEquals(.2,d.getTf(),TOL);assertEquals(3.1,d.getKp(),TOL);assertEquals(4.5,d.getKi(),TOL);
        assertEquals(1.4,d.getKc(),TOL);assertEquals(3.7,d.getEfdmax(),TOL);assertTrue(p.getLastImportReport().isStrictlyComplete());
        var descriptor=DynamicModelCatalog.find("EXST2").orElseThrow();assertEquals(13,descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("EXST2").orElseThrow().isImplementedExactly());
    }

    @Test void reusesSt2EngineButPreservesAdditiveBridgeDistinction()throws Exception{
        Exst2Data d=baseData();DStabNetworkBuilder additiveBuilder=DStabBuilderTestFixture.createWithMachine();
        Exst2Exciter additive=additiveBuilder.addExcExst2("Bus1","1",d);Machine am=additiveBuilder.getDStabNetwork().getMachine("Bus1-mach1");
        am.setEfd(1.2);assertTrue(additive.initStates(am.getDStabBus(),am));
        DStabNetworkBuilder productBuilder=DStabBuilderTestFixture.createWithMachine();
        Esst2aExciter product=productBuilder.addExcEsst2a("Bus1","1",d);Machine pm=productBuilder.getDStabNetwork().getMachine("Bus1-mach1");
        pm.setEfd(1.2);assertTrue(product.initStates(pm.getDStabBus(),pm));
        assertEquals(.2,additive.getRegulatorOutput(),TOL);
        assertEquals(1.2,product.getRegulatorOutput(),TOL);
    }

    @Test void sumsOelAndUelAtVoltageErrorJunction()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setVuel(.2);f.exciter.setVoel(-.05);
        assertEquals(.19,f.exciter.getLeadLagInput(),TOL);
        assertEquals(.115,f.exciter.getLeadLagOutput(),TOL);
    }

    @Test void fiveStateTrajectoryMatchesPublishedAdditiveEquations()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.2,1.04,.2,1.2,.04};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){
            double[] d0=derivatives(x,.1),prediction=add(x,d0,dt),d1=derivatives(prediction,.1);
            for(int j=0;j<x.length;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            max=Math.max(max,Math.abs(x[0]-f.exciter.getOutput(f.machine)));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            max=Math.max(max,Math.abs(x[2]-f.exciter.getRegulatorOutput()));
            max=Math.max(max,Math.abs(.1*(x[0]-x[3])/.4-f.exciter.getFeedbackVoltage()));
            double input=1.08+.1-x[1]-.1*(x[0]-x[3])/.4;
            max=Math.max(max,Math.abs(.5*input+.5*x[4]-f.exciter.getLeadLagOutput()));
        }
        assertTrue(max<1e-9,"EXST2 five-state max error="+max);
    }

    @Test void appliesSourceCorrectionsAndParticipatesInSolver()throws Exception{
        Exst2Data d=baseData();d.setTr(.8);d.setTa(.004);d.setTe(.004);d.setTf(.015);d.setTb(.015);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Exst2Exciter e=b.addExcExst2("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(.5,e.tr,TOL);assertEquals(0,e.ta,TOL);assertEquals(.02,e.te,TOL);assertEquals(.02,e.tf,TOL);assertEquals(.02,e.tb,TOL);
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(Exst2Data d)throws Exception{DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Exst2Exciter e=b.addExcExst2("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);}
    private static Exst2Data baseData(){Exst2Data d=new Exst2Data();d.setTr(.1);d.setKa(5);d.setTa(.2);d.setVrmax(10);d.setVrmin(-10);
        d.setKe(1);d.setTe(.3);d.setKf(.1);d.setTf(.4);d.setKp(0);d.setKi(0);d.setKc(.2);d.setEfdmax(5);d.setTb(.4);d.setTc(.2);return d;}
    private static double[] derivatives(double[] x,double step){double vf=.1*(x[0]-x[3])/.4,input=1.08+step-x[1]-vf,output=.5*input+.5*x[4];
        return new double[]{(x[2]+1-x[0])/.3,(1.04-x[1])/.1,(5*output-x[2])/.2,(x[0]-x[3])/.4,(input-x[4])/.4};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Exst2Exciter e,Machine m,double dt){e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0);e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1);}
    private record Fixture(Machine machine,Exst2Exciter exciter){}
}
