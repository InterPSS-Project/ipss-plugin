package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.st7b.St7bData;
import org.interpss.dstab.control.exc.psse.st7b.St7bExciter;
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

/** Import and equation-level tests for ST7B/ESST7B. */
public class St7bExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;
    private static final Path CORPUS_ROOT=Path.of(System.getProperty("psse.testcases.root",
            Path.of(System.getProperty("user.home"),"OneDrive","Documents","qiuhua","private_cases").toString()));

    @Test void parsesBothLayoutsAndCatalogsExactSupport(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path st=dir.resolve("st7b.dyr");
        Files.writeString(st,"1 'ST7B' 1 0 0 .017 3.21 3 1.05 .95 87.8 5.77 -4.64 0 1 1 1 1 3 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(st.toString());
        St7bExciter e=(St7bExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();assertNotNull(e);
        St7bData d=e.getData();assertEquals(.017,d.getTr(),TOL);assertEquals(3.21,d.getTg(),TOL);assertEquals(87.8,d.getKpa(),TOL);
        assertEquals(5.77,d.getVrmax(),TOL);assertEquals(-4.64,d.getVrmin(),TOL);assertEquals(1,d.getKia(),TOL);assertEquals(3,d.getTia(),TOL);
        assertTrue(p.getLastImportReport().isStrictlyComplete());

        DStabNetworkBuilder eb=DStabBuilderTestFixture.createWithMachine();Path es=dir.resolve("esst7b.dyr");
        Files.writeString(es,"1 'ESST7B' 1 .02 40 1 3 1 .5 1 .8 1 .2 5 -4.5 1.1 .9 2 3 .04 /\n");
        new PSSEDStabDirectParser(eb).setStrictImport(true).parseDynFile(es.toString());St7bExciter ee=(St7bExciter)eb.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(ee);assertEquals("ESST7B",ee.getModelName());assertEquals(.04,ee.getData().getTs(),TOL);assertEquals(2,ee.getData().getUel());assertEquals(3,ee.getData().getOel());
        var descriptor=DynamicModelCatalog.find("ESST7B").orElseThrow();assertEquals("ST7B",descriptor.canonicalName());assertEquals(16,descriptor.parameterCount());
        assertTrue(descriptor.recordSchema().accepts(17));assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST7B").orElseThrow().isImplementedExactly());
    }

    @Test void allSuppliedSt7bRecordsUseReviewedSchema()throws Exception{
        List<Path> paths=List.of(CORPUS_ROOT.resolve("private_case_package/24HSP11p.dyr"),
                CORPUS_ROOT.resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr"),
                CORPUS_ROOT.resolve("31hs1ap/31hs1ap_348 (1)/31hs1ap.dyr"));
        assumeTrue(paths.stream().allMatch(Files::isRegularFile),"Missing supplied ST7B corpus under "+CORPUS_ROOT);
        Pattern pattern=Pattern.compile("(?ims)^\\s*\\d+\\s+'ST7B'\\s+[^/]+/");int count=0;
        for(Path path:paths){Matcher matcher=pattern.matcher(Files.readString(path));while(matcher.find()){
            String record=matcher.group();assertEquals(19,PsseDyrRecordReader.tokenize(record.substring(0,record.lastIndexOf('/'))).size(),path.toString());count++;}}
        assertEquals(4,count);
    }

    @Test void fiveStateTrajectoryMatchesPublishedEquations()throws Exception{
        St7bData d=baseData();d.setTs(.25);Fixture f=fixture("ESST7B",d);double ref=f.exciter.getRefPoint()+.1;f.exciter.setRefPoint(ref);
        double vt=1.04,ratioIn=.5,ratioOut=.6;double[] x={1.2,vt,vt-ratioIn*vt,1.2-ratioOut*1.2,1.2};double dt=.00005,max=0;
        for(int i=0;i<2000;i++){double[] d0=derivatives(x,ref),predicted=add(x,d0,dt),d1=derivatives(predicted,ref);
            for(int j=0;j<5;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            max=Math.max(max,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            max=Math.max(max,Math.abs((ratioIn*x[1]+x[2])-f.exciter.getInputLeadLag()));
            max=Math.max(max,Math.abs(feedback(x,ref)[1]-f.exciter.getFeedbackOutput()));}
        assertTrue(max<2e-8,"ST7B five-state max error="+max);
    }

    @Test void implementsAllThreeLimiterLocationsAndVoltageDependentLimits()throws Exception{
        St7bData u1=baseData();u1.setUel(1);Fixture a=fixture("ST7B",u1);double r=a.exciter.getReferenceFeedback();a.exciter.setVuel(.1);assertEquals(r+.1,a.exciter.getReferenceFeedback(),TOL);
        St7bData u2=baseData();u2.setUel(2);Fixture b=fixture("ST7B",u2);b.exciter.setVuel(2);assertEquals(2,b.exciter.getReferenceFeedback(),TOL);
        St7bData u3=baseData();u3.setUel(3);Fixture c=fixture("ST7B",u3);c.exciter.setVuel(2);assertTrue(c.exciter.getPreFiringField()>=2-TOL);
        St7bData o1=baseData();o1.setOel(1);Fixture d=fixture("ST7B",o1);double q=d.exciter.getReferenceFeedback();d.exciter.setVoel(-.1);assertEquals(q-.1,d.exciter.getReferenceFeedback(),TOL);
        St7bData o2=baseData();o2.setOel(2);Fixture e=fixture("ST7B",o2);e.exciter.setVoel(.5);assertEquals(.5,e.exciter.getReferenceFeedback(),TOL);
        St7bData o3=baseData();o3.setOel(3);Fixture g=fixture("ST7B",o3);g.exciter.setVoel(.8);assertTrue(g.exciter.getPreFiringField()<=.8+TOL);
        assertTrue(Math.abs(g.exciter.getPreFiringField())<=g.machine.getDStabBus().getVoltageMag()*Math.max(Math.abs(g.exciter.vrmax),Math.abs(g.exciter.vrmin))+TOL);
    }

    @Test void appliesCorrectionsExpansionAndParticipatesInSolver()throws Exception{
        St7bData d=baseData();d.setTr(.003);d.setTs(.015);d.setVmax(-1);d.setVmin(-2);d.setVrmax(-1);d.setVrmin(-2);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St7bExciter e=b.addExcSt7b("Bus1","1","ESST7B",d);Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(0,e.tr,TOL);assertEquals(.02,e.ts,TOL);
        assertTrue(e.vmax>=e.getRefPoint());assertTrue(e.vmin<=e.getRefPoint());assertTrue(e.vrmax*m.getDStabBus().getVoltageMag()>=1.2-TOL);
        DynamicSimuAlgorithm algo=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algo.setSimuStepSec(.005);algo.setTotalSimuTimeSec(.02);algo.setSimuOutputHandler(new StateMonitor());assertTrue(algo.getAclfAlgorithm().loadflow());
        assertTrue(algo.initialization());assertTrue(algo.performSimulation());
    }

    private static Fixture fixture(String model,St7bData d)throws Exception{DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St7bExciter e=b.addExcSt7b("Bus1","1",model,d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);}
    private static St7bData baseData(){St7bData d=new St7bData();d.setTr(.1);d.setTg(.2);d.setTf(.4);d.setVmax(5);d.setVmin(-5);d.setKpa(2);
        d.setVrmax(10);d.setVrmin(-10);d.setKh(.1);d.setKl(.1);d.setTc(.3);d.setTb(.5);d.setKia(.2);d.setTia(1);return d;}
    private static double[] derivatives(double[] x,double ref){double vt=1.04,input=.5*x[1]+x[2],error=ref-input,amp=2*error;
        double[] fp=feedback(x,ref);double pre=fp[0];return new double[]{(pre-x[0])/.25,(vt-x[1])/.1,((1-.5)*x[1]-x[2])/.4,
                ((1-.6)*amp-x[3])/.5,(pre-x[4])/1};}
    private static double[] feedback(double[] x,double ref){double input=.5*x[1]+x[2],amp=2*(ref-input),ll2=.6*amp+x[3],beta=.2,fb=beta/(1-beta)*(ll2-x[4]);return new double[]{ll2+fb,fb};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(St7bExciter e,Machine m,double dt){e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0);e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1);}
    private record Fixture(Machine machine,St7bExciter exciter){}
}
