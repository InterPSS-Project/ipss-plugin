package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.st1c.St1cData;
import org.interpss.dstab.control.exc.psse.st1c.St1cExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Exact-schema, equation, limiter, correction and solver tests for PSS/E ST1C. */
public class St1cExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;
    private static final Path CORPUS_ROOT=Path.of(System.getProperty("psse.testcases.root",
            Path.of(System.getProperty("user.home"),"OneDrive","Documents","qiuhua","private_cases").toString()));

    @Test void parsesExactThreeIconEighteenConRecord(@TempDir Path dir)throws Exception{
        Path dyr=dir.resolve("st1c.dyr");
        Files.writeString(dyr,"1 'ST1C' 1 2 1 3 .01 9 -8 .03 .2 .04 .3 150 .05 7 -6 5 -4 .1 .2 1 .3 2 /\n");
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);
        p.parseDynFile(dyr.toString());St1cExciter e=(St1cExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();assertNotNull(e);
        St1cData d=e.getData();assertEquals(2,d.getUel());assertEquals(1,d.getVos());assertEquals(3,d.getOel());
        assertEquals(.01,d.getTr(),TOL);assertEquals(9,d.getVimax(),TOL);assertEquals(-8,d.getVimin(),TOL);
        assertEquals(.03,d.getTc(),TOL);assertEquals(.2,d.getTb(),TOL);assertEquals(.04,d.getTc1(),TOL);assertEquals(.3,d.getTb1(),TOL);
        assertEquals(150,d.getKa(),TOL);assertEquals(.05,d.getTa(),TOL);assertEquals(7,d.getVamax(),TOL);assertEquals(-6,d.getVamin(),TOL);
        assertEquals(5,d.getVrmax(),TOL);assertEquals(-4,d.getVrmin(),TOL);assertEquals(.1,d.getKc(),TOL);assertEquals(.2,d.getKf(),TOL);
        assertEquals(1,d.getTf(),TOL);assertEquals(.3,d.getKlr(),TOL);assertEquals(2,d.getIlr(),TOL);assertTrue(p.getLastImportReport().isStrictlyComplete());
        var descriptor=DynamicModelCatalog.find("ST1C").orElseThrow();assertEquals(21,descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST1C").orElseThrow().isImplementedExactly());
    }

    @Test void rejectsShortRecordAndAllTwentySuppliedRecordsHaveExactSchema(@TempDir Path dir)throws Exception{
        Path shortDyr=dir.resolve("short.dyr");Files.writeString(shortDyr,"1 'ST1C' 1 2 1 3 .01 9 -8 .03 .2 .04 .3 150 .05 7 -6 5 -4 .1 .2 1 .3 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(DStabBuilderTestFixture.createWithMachine()).setStrictImport(false);p.parseDynFile(shortDyr.toString());
        assertEquals(1,p.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
        Path[] files={CORPUS_ROOT.resolve("private_case_package/24HSP11p.dyr"),
                CORPUS_ROOT.resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr")};
        assumeTrue(Files.isRegularFile(files[0])&&Files.isRegularFile(files[1]),"Missing supplied ST1C corpus");
        Pattern pattern=Pattern.compile("(?ims)^\\s*\\d+\\s+'ST1C'\\s+[^/]+/");int count=0;
        for(Path file:files){Matcher matcher=pattern.matcher(Files.readString(file));while(matcher.find()){
            String record=matcher.group();assertEquals(24,PsseDyrRecordReader.tokenize(record.substring(0,record.lastIndexOf('/'))).size());count++;}}
        assertEquals(20,count);
    }

    @Test void fiveStatesMatchIndependentModifiedEulerOracle()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);double[] x=f.exciter.getStateSnapshot();
        double ref=f.exciter.getRefPoint(),dt=.0001,max=0;
        for(int n=0;n<2000;n++){
            double[] d0=derivatives(x,ref),predicted=add(x,d0,dt),d1=derivatives(predicted,ref);
            for(int i=0;i<x.length;i++)x[i]+=.5*(d0[i]+d1[i])*dt;step(f.exciter,f.machine,dt);
            double[] actual=f.exciter.getStateSnapshot();for(int i=0;i<x.length;i++)max=Math.max(max,Math.abs(x[i]-actual[i]));
        }
        assertTrue(max<1e-10,"ST1C five-state maximum error="+max);
    }

    @Test void routesUelAndOelAtAllThreePublishedLocations()throws Exception{
        St1cData d=algebraicData();d.setUel(1);Fixture u1=fixture(d);u1.exciter.setVuel(.2);assertEquals(1.4,u1.exciter.getPreFieldSignal(),TOL);
        d=algebraicData();d.setOel(1);Fixture o1=fixture(d);o1.exciter.setVoel(-.2);assertEquals(1.0,o1.exciter.getPreFieldSignal(),TOL);
        d=algebraicData();d.setUel(2);Fixture u2=fixture(d);u2.exciter.setVuel(1.5);assertEquals(1.5,u2.exciter.getFirstGateOutput(),TOL);
        d=algebraicData();d.setOel(2);Fixture o2=fixture(d);o2.exciter.setVoel(1.0);assertEquals(1.0,o2.exciter.getFirstGateOutput(),TOL);
        d=algebraicData();d.setUel(3);Fixture u3=fixture(d);u3.exciter.setVuel(1.5);assertEquals(1.5,u3.exciter.getPreFieldSignal(),TOL);
        d=algebraicData();d.setOel(3);Fixture o3=fixture(d);o3.exciter.setVoel(1.0);assertEquals(1.0,o3.exciter.getPreFieldSignal(),TOL);
    }

    @Test void appliesFieldCurrentFeedbackTerminalLimitsAndStationaryInitialization()throws Exception{
        St1cData d=baseData();d.setKlr(.5);d.setIlr(-1);d.setKc(.1);d.setVrmax(2);d.setVrmin(-2);Fixture f=fixture(d);
        double ifd=f.machine.calculateIfd(com.interpss.dstab.mach.MachineIfdBase.EXCITER);
        if(!Double.isFinite(ifd))ifd=0;
        assertEquals(1.2+.5*(ifd+1),f.exciter.getRegulatorOutput(),TOL);assertEquals(1.2,f.exciter.getPreFieldSignal(),TOL);
        double[] initial=f.exciter.getStateSnapshot();for(int i=0;i<1000;i++)step(f.exciter,f.machine,.0001);
        assertArrayEquals(initial,f.exciter.getStateSnapshot(),1e-10);assertEquals(1.2,f.exciter.getOutput(f.machine),1e-10);
        f.exciter.setRefPoint(f.exciter.getRefPoint()+100);for(int i=0;i<4000;i++)step(f.exciter,f.machine,.0001);
        assertEquals(f.exciter.getFieldUpperLimit(),f.exciter.getOutput(f.machine),1e-8);
    }

    @Test void appliesPowerWorldCorrectionsLimitExpansionAndAlgebraicPaths()throws Exception{
        St1cData d=baseData();d.setTr(.004);d.setTb(.015);d.setTb1(.004);d.setTf(.015);d.setKa(0);
        d.setVimax(-2);d.setVimin(-3);d.setVamax(-2);d.setVamin(-3);d.setVrmax(-2);d.setVrmin(-3);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St1cExciter e=b.addExcSt1c("Bus1","1",d);Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(0,e.tr,TOL);assertEquals(.02,e.tb,TOL);
        assertEquals(0,e.tb1,TOL);assertEquals(.02,e.tf,TOL);assertEquals(.02,e.ka,TOL);assertTrue(e.vimax>=e.getLimitedVoltageError());
        assertTrue(e.vamax>=e.getRegulatorOutput());assertTrue(e.vrmax>=e.getOutput(m)/m.getDStabBus().getVoltageMag());
        Fixture algebraic=fixture(algebraicData());algebraic.exciter.setRefPoint(algebraic.exciter.getRefPoint()+.1);
        assertEquals(1.3,algebraic.exciter.getOutput(algebraic.machine),TOL);
    }

    @Test void participatesInFullDynamicSolver()throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();assertNotNull(b.addExcSt1c("Bus1","1",baseData()));
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(St1cData data)throws Exception{DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St1cExciter e=b.addExcSt1c("Bus1","1",data);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertNotNull(e);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);}
    private static St1cData baseData(){St1cData d=new St1cData();d.setUel(1);d.setVos(1);d.setOel(1);d.setTr(.1);d.setVimax(99);d.setVimin(-99);
        d.setTc(.05);d.setTb(.2);d.setTc1(.04);d.setTb1(.15);d.setKa(2);d.setTa(.25);d.setVamax(99);d.setVamin(-99);d.setVrmax(99);d.setVrmin(-99);
        d.setKc(0);d.setKf(.2);d.setTf(.3);d.setKlr(0);d.setIlr(99);return d;}
    private static St1cData algebraicData(){St1cData d=baseData();d.setTr(0);d.setTc(0);d.setTb(0);d.setTc1(0);d.setTb1(0);d.setKa(1);d.setTa(0);d.setKf(0);d.setTf(0);return d;}
    private static double[] derivatives(double[] x,double ref){double feedback=.2/.3*(x[3]-x[4]),error=ref-x[0]-feedback;
        double y1=.05/.2*error+(1-.05/.2)*x[1],y2=.04/.15*y1+(1-.04/.15)*x[2];
        return new double[]{(1.04-x[0])/.1,(error-x[1])/.2,(y1-x[2])/.15,(2*y2-x[3])/.25,(x[3]-x[4])/.3};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(St1cExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(Machine machine,St1cExciter exciter){}
}
