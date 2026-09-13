package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.dc4c.Dc4cData;
import org.interpss.dstab.control.exc.psse.dc4c.Dc4cExciter;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
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

/** Native PSS/E DC4C import and IEEE/PowerWorld equation checks. */
public class DStabNetworkBuilderDc4cTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;
    private static final Path CORPUS_ROOT=Path.of(System.getProperty("psse.testcases.root",
            Path.of("testData", "private", "model-corpus").toString()));

    @Test void parsesRealCorpusRecordAndHoldsEquilibrium(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=dir.resolve("dc4c.dyr");
        Files.writeString(dyr,"1 'DC4C' 1 1 1 1 1\n"
                +" 0 200 50 70 .05 10 -10 1 .017 1 3 0 1 .2 1\n"
                +" .05 2 .18 .91 0 0 0 0 3.5 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());Machine m=builder.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1);m.setEfd(1.2);Dc4cExciter e=(Dc4cExciter)m.getExciter();assertNotNull(e);
        assertEquals(1,e.getData().getScl());assertEquals(1,e.getData().getSw1());
        assertEquals(200,e.getData().getKpr(),TOL);assertEquals(.91,e.getData().getKp(),TOL);
        assertEquals(3.5,e.getData().getVbmax(),TOL);assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertTrue(e.initStates(m.getDStabBus(),m));double initial=e.getOutput(m);
        for(int i=0;i<1000;i++)step(e,m,.0001);assertEquals(initial,e.getOutput(m),1e-8);
    }

    @Test void allSuppliedDc4cRecordsUseReviewedFourIconTwentyFourConSchema()throws Exception{
        Path path=CORPUS_ROOT.resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr");
        assumeTrue(Files.isRegularFile(path),"Missing supplied DC4C corpus under "+CORPUS_ROOT);
        Pattern pattern=Pattern.compile("(?ims)^\\s*\\d+\\s+'DC4C'\\s+[^/]+/");Matcher matcher=pattern.matcher(Files.readString(path));int count=0;
        while(matcher.find()){String record=matcher.group();assertEquals(31,
                PsseDyrRecordReader.tokenize(record.substring(0,record.lastIndexOf('/'))).size(),path.toString());count++;}
        assertEquals(15,count);
    }

    @Test void parsesExactDc4cu1WrapperAllocationAndPublishedFieldOrder(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("dc4cu1.dyr");
        Files.writeString(dyr,"1 'USRMDL' '1' 'DC4CU1' 2 0 4 24 6 6 0 0 0 1"
                +" .1 3 4 .5 .1 10 -10 2 .2 1 .4 .2 .3 0 0 0 0 0 .9 .1 .05 10 0 10 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        Dc4cExciter exciter=(Dc4cExciter)machine.getExciter();assertNotNull(exciter);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());assertEquals(4,exciter.getData().getKir(),TOL);
        assertEquals(.9,exciter.getData().getKp(),TOL);assertEquals(10,exciter.getData().getVbmax(),TOL);
        machine.setSpeed(1);machine.setEfd(1.2);assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        assertEquals(6,exciter.getNamedStates().size());assertTrue(exciter.getNamedStates().containsKey("Rate Feedback"));
    }

    @Test void rejectsDc4cu1WithIncorrectStateAllocation(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("dc4cu1-invalid.dyr");
        Files.writeString(dyr,"1 'USRMDL' '1' 'DC4CU1' 2 0 4 24 5 6 0 0 0 1"
                +" .1 3 4 .5 .1 10 -10 2 .2 1 .4 .2 .3 0 0 0 0 0 .9 .1 .05 10 0 10 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder);parser.parseDynFile(dyr.toString());
        assertTrue(!parser.getLastImportReport().failures().isEmpty());
    }

    @Test void sixStateTrajectoryMatchesIndependentPublishedEquations()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.2,1.04,.6,0,1.2,1.2};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){
            double[] d0=derivatives(x,.1),p=add(x,d0,dt),d1=derivatives(p,.1);
            for(int j=0;j<x.length;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            double fb=.2*(x[0]-x[5])/.3,error=1.14-x[1]-fb;
            double pi=3*error+x[2],derivative=.5*(error-x[3])/.1,pid=pi+derivative;
            max=Math.max(max,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            max=Math.max(max,Math.abs(pid-f.exciter.getPidOutput()));
            max=Math.max(max,Math.abs(pi-f.exciter.getProportionalIntegralOutput()));
            max=Math.max(max,Math.abs(derivative-f.exciter.getDerivativeOutput()));
            max=Math.max(max,Math.abs(x[4]-f.exciter.getRegulatorOutput()));
            max=Math.max(max,Math.abs(fb-f.exciter.getFeedbackOutput()));
        }
        assertTrue(max<1e-10,"DC4C maximum six-state error="+max);
    }

    @Test void implementsPotentialSourceAndExciterFieldLoadedRectifier()throws Exception{
        Dc4cData d=baseData();d.setSw1(1);d.setKp(.8);d.setKi(.2);d.setXl(.1);
        d.setThetaP(30);d.setKc1(.15);d.setVbmax(10);Fixture f=fixture(d);
        Complex angle=new Complex(Math.cos(Math.toRadians(30)),Math.sin(Math.toRadians(30)));
        Complex kpPhasor=angle.multiply(.8),vt=f.machine.getDStabBus().getVoltage();
        Complex it=f.machine.getIxy().divide(f.machine.getIMultiFactor());
        double source=kpPhasor.multiply(vt)
                .add(Complex.I.multiply(new Complex(.2,0).add(kpPhasor.multiply(.1))).multiply(it)).abs();
        assertEquals(source,f.exciter.getCompoundSource(),TOL);
        double expected=Math.min(10,source*Exac1Exciter.rectifierFactor(.15*f.exciter.getFieldFeedback()/source));
        assertEquals(expected,f.exciter.getAvailableSupply(),TOL);

        Dc4cData independent=baseData();independent.setSw1(2);independent.setKp(.77);
        Fixture b=fixture(independent);assertEquals(.77,b.exciter.getSelectedSource(),TOL);
    }

    @Test void routesSummationAndTakeoverLimitersWithPublishedSigns()throws Exception{
        Dc4cData gates=baseData();gates.setTa(0);gates.setUel(2);gates.setOel(2);gates.setScl(2);
        Fixture g=fixture(gates);g.exciter.setRefPoint(g.exciter.getRefPoint()-10);
        g.exciter.setVuel(.8);g.exciter.setVsclUel(.9);assertEquals(.9,g.exciter.getGatedPidOutput(),TOL);
        g.exciter.setVoel(.7);assertEquals(.7,g.exciter.getGatedPidOutput(),TOL);
        g.exciter.setVsclOel(.6);assertEquals(.6,g.exciter.getGatedPidOutput(),TOL);

        Dc4cData sum=baseData();sum.setUel(1);sum.setOel(1);sum.setScl(1);Fixture s=fixture(sum);
        s.exciter.setVuel(.3);s.exciter.setVoel(.1);s.exciter.setVsclSum(.2);
        assertEquals(.4,s.exciter.getPidError(),TOL);
    }

    @Test void appliesTimeCorrectionsConstantLimitsAndInitializationExpansion()throws Exception{
        Dc4cData d=baseData();d.setTr(.004);d.setTdr(.015);d.setTf(.015);d.setTa(.005);d.setTe(.005);
        d.setVrmax(.1);d.setVrmin(-.1);DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        Dc4cExciter e=b.addExcDc4c("Bus1","1",d);Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setSpeed(1);m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.tdr,TOL);assertEquals(.02,e.tf,TOL);
        assertEquals(.02,e.ta,TOL);assertEquals(.02,e.te,TOL);assertTrue(e.vrmax>=e.getRegulatorOutput());
        m.getDStabBus().setVoltage(new Complex(.5,0));e.setRefPoint(e.getRefPoint()+100);
        assertEquals(e.vrmax,e.getRegulatorOutput(),TOL);
    }

    @Test void keepsSpeedMultiplierTypedOnlyAndSupportsAlgebraicBlocks()throws Exception{
        Dc4cData d=baseData();d.setTa(0);d.setTe(0);d.setSpdmlt(1);Fixture f=fixture(d,1.02);
        assertEquals(1.2,f.exciter.getOutput(f.machine),TOL);
        assertEquals(1.2/1.02,f.exciter.getInternalFieldVoltage(),1e-8);
        f.exciter.setRefPoint(f.exciter.getRefPoint()+.02);step(f.exciter,f.machine,.001);
        assertTrue(Double.isFinite(f.exciter.getOutput(f.machine)));
    }

    @Test void catalogAndFullDynamicSolverRecognizeDc4c()throws Exception{
        var descriptor=DynamicModelCatalog.find("DC4C").orElseThrow();
        assertEquals(28,descriptor.parameterCount());assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESDC4C").orElseThrow().isImplementedExactly());
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();assertNotNull(b.addExcDc4c("Bus1","1",baseData()));
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);
        a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(Dc4cData d)throws Exception{return fixture(d,1);}
    private static Fixture fixture(Dc4cData d,double speed)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Dc4cExciter e=b.addExcDc4c("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setSpeed(speed);m.setEfd(1.2);
        assertNotNull(e);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);
    }
    private static Dc4cData baseData(){Dc4cData d=new Dc4cData();d.setOel(0);d.setUel(0);d.setScl(0);d.setSw1(2);
        d.setTr(.1);d.setKpr(3);d.setKir(4);d.setKdr(.5);d.setTdr(.1);d.setVrmax(100);d.setVrmin(-100);
        d.setKa(2);d.setTa(.2);d.setKe(1);d.setTe(.4);d.setKf(.2);d.setTf(.3);d.setVemin(0);
        d.setE1(0);d.setSe1(0);d.setE2(0);d.setSe2(0);d.setKp(1);d.setKi(0);d.setXl(0);d.setThetaP(0);
        d.setKc1(0);d.setVbmax(10);return d;}
    private static double[] derivatives(double[] x,double refStep){double fb=.2*(x[0]-x[5])/.3,err=1.04+refStep-x[1]-fb;
        double pid=3*err+x[2]+.5*(err-x[3])/.1;return new double[]{(x[4]-x[0])/.4,(1.04-x[1])/.1,4*err,(err-x[3])/.1,(2*pid-x[4])/.2,(x[0]-x[5])/.3};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Dc4cExciter e,Machine m,double dt){e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0);e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1);}
    private record Fixture(Machine machine,Dc4cExciter exciter){}
}
