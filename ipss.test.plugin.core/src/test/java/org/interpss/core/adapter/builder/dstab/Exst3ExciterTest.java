package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exst3.Exst3Data;
import org.interpss.dstab.control.exc.psse.exst3.Exst3Exciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Exact native-record, equation, limiter, and solver coverage for PSS/E EXST3. */
public class Exst3ExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;

    @Test void parsesExactEighteenConRecordAndRejectsPowerWorldSpeedField(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("exst3.dyr");
        Files.writeString(dyr,"1 'EXST3' '1' .02 .3 -.2 2 .1 .4 5 .2 4 -4 .3 1 .2 6 .1 .05 3 10 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Exst3Exciter e=(Exst3Exciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();assertNotNull(e);
        Exst3Data d=e.getData();assertEquals(.02,d.getTr(),TOL);assertEquals(.3,d.getVimax(),TOL);
        assertEquals(-.2,d.getVimin(),TOL);assertEquals(2,d.getKj(),TOL);assertEquals(.1,d.getTc(),TOL);
        assertEquals(.4,d.getTb(),TOL);assertEquals(5,d.getKa(),TOL);assertEquals(.2,d.getTa(),TOL);
        assertEquals(4,d.getVrmax(),TOL);assertEquals(-4,d.getVrmin(),TOL);assertEquals(.3,d.getKg(),TOL);
        assertEquals(1,d.getKp(),TOL);assertEquals(.2,d.getKi(),TOL);assertEquals(6,d.getEfdmax(),TOL);
        assertEquals(.1,d.getKc(),TOL);assertEquals(.05,d.getXl(),TOL);assertEquals(3,d.getVgmax(),TOL);
        assertEquals(10,d.getThetaP(),TOL);assertTrue(p.getLastImportReport().isStrictlyComplete());
        assertEquals(18,DynamicModelCatalog.find("EXST3").orElseThrow().parameterCount());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("EXST3").orElseThrow().isImplementedExactly());

        Path extra=dir.resolve("exst3-spdmlt.dyr");
        Files.writeString(extra,"1 'EXST3' '1' .02 .3 -.2 2 .1 .4 5 .2 4 -4 .3 1 .2 6 .1 .05 3 10 1 /\n");
        assertThrows(Exception.class,()->new PSSEDStabDirectParser(
                DStabBuilderTestFixture.createWithMachine()).setStrictImport(true).parseDynFile(extra.toString()));
    }

    @Test void threeStatesMatchPublishedEquations()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x=f.exciter.getStateSnapshot();double vi0=x[2],vb=f.exciter.getBridgeVoltage(),dt=.0001,max=0;
        for(int n=0;n<2000;n++){
            double[] d0=derivatives(x,.1,vi0,vb),prediction=add(x,d0,dt),d1=derivatives(prediction,.1,vi0,vb);
            for(int i=0;i<3;i++)x[i]+=.5*(d0[i]+d1[i])*dt;step(f.exciter,f.machine,dt);
            double[] actual=f.exciter.getStateSnapshot();for(int i=0;i<3;i++)max=Math.max(max,Math.abs(x[i]-actual[i]));
        }
        assertTrue(max<1e-11,"EXST3 three-state max error="+max);
    }

    @Test void implementsComplexCompoundSourceAndLoadedRectifier()throws Exception{
        Exst3Data d=baseData();d.setKp(2);d.setKi(3);d.setXl(.2);d.setThetaP(15);d.setKc(0);
        Fixture f=fixture(d);double a=Math.toRadians(15);Complex kpc=new Complex(2*Math.cos(a),2*Math.sin(a));
        Complex expected=f.machine.getDStabBus().getVoltage().multiply(kpc).add(
                Complex.I.multiply(kpc.multiply(.2).add(3)).multiply(f.machine.getIxy()));
        assertEquals(expected.abs(),f.exciter.getCompoundSourceVoltage(),TOL);
        assertEquals(expected.abs(),f.exciter.getBridgeVoltage(),TOL);
    }

    @Test void appliesPtiCorrectionsExpandsLimitsAndSolvesZeroTaLoop()throws Exception{
        Exst3Data d=baseData();d.setTr(.8);d.setTb(.015);d.setTa(.004);d.setKj(0);d.setKa(0);
        d.setVimax(-2);d.setVimin(-3);d.setVrmax(-4);d.setVrmin(-5);d.setEfdmax(.5);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        Exst3Exciter e=b.addExcExst3("Bus1","1",d);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(.5,e.tr,TOL);assertEquals(.02,e.tb,TOL);assertEquals(0,e.ta,TOL);
        assertEquals(.02,e.kj,TOL);assertEquals(.02,e.ka,TOL);assertTrue(e.vimax>=e.getLimitedError());
        assertTrue(e.vimin<=e.getLimitedError());assertTrue(e.vrmax>=e.getRegulatorOutput());
        assertTrue(e.vrmin<=e.getRegulatorOutput());assertTrue(e.efdmax>=m.getEfd());
        e.setRefPoint(e.getRefPoint()+.01);assertTrue(Double.isFinite(e.getOutput(m)));
    }

    @Test void enforcesInputFieldAndFeedbackLimitsOnThePublishedSideOfEachBlock()throws Exception{
        Exst3Data d=baseData();d.setTa(0);d.setTb(0);d.setTc(0);d.setVimax(.2);d.setVimin(-.2);
        d.setEfdmax(5);Fixture f=fixture(d);double vb=f.exciter.getBridgeVoltage();
        f.exciter.setRefPoint(f.exciter.getRefPoint()+1);
        assertEquals(.2,f.exciter.getLimitedError(),TOL);
        double va=2*.2,expectedVr=5*va/(1+5*.1*vb);
        assertEquals(expectedVr,f.exciter.getRegulatorOutput(),1e-9);
        assertEquals(expectedVr*vb,f.exciter.getOutput(f.machine),1e-9);

        f.exciter.setVuel(.2);f.exciter.setVoel(-.05);
        assertEquals(.2,f.exciter.getLimitedError(),TOL,
                "VUEL and VOEL are summed before the VI limiter");
        f.exciter.efdmax=1.3;assertEquals(1.3,f.exciter.getOutput(f.machine),TOL);
    }

    @Test void initializesStationaryAndParticipatesInFullSolver()throws Exception{
        Fixture f=fixture(baseData());for(int n=0;n<1000;n++)step(f.exciter,f.machine,.0001);
        assertEquals(1.2,f.exciter.getOutput(f.machine),1e-9);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();assertNotNull(b.addExcExst3("Bus1","1",baseData()));
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);
        a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());
        assertTrue(a.performSimulation());
    }

    private static Fixture fixture(Exst3Data d)throws Exception{DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);Exst3Exciter e=b.addExcExst3("Bus1","1",d);
        assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);}
    private static Exst3Data baseData(){Exst3Data d=new Exst3Data();d.setTr(.1);d.setVimax(99);d.setVimin(-99);
        d.setKj(2);d.setTc(.2);d.setTb(.4);d.setKa(5);d.setTa(.3);d.setVrmax(99);d.setVrmin(-99);
        d.setKg(.1);d.setKp(1);d.setKi(0);d.setEfdmax(99);d.setKc(0);d.setXl(0);d.setVgmax(99);d.setThetaP(0);return d;}
    private static double[] derivatives(double[] x,double refStep,double vi0,double vb){double error=vi0+refStep+1.04-x[1];
        double va=2*(.5*error+.5*x[2]),efd=x[0]*vb,vg=.1*efd;
        return new double[]{(5*(va-vg)-x[0])/.3,(1.04-x[1])/.1,(error-x[2])/.4};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=x.clone();for(int i=0;i<3;i++)y[i]+=d[i]*dt;return y;}
    private static void step(Exst3Exciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));
        assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(Machine machine,Exst3Exciter exciter){}
}
