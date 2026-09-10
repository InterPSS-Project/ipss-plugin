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
import java.util.Set;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.st5b.St5bData;
import org.interpss.dstab.control.exc.psse.st5b.St5bExciter;
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

/** Import, selector, equation, and solver tests for ST5B/ESST5B. */
public class St5bExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;
    private static final Path CORPUS_ROOT=Path.of(System.getProperty("psse.testcases.root",
            Path.of(System.getProperty("user.home"),"OneDrive","Documents","qiuhua","private_cases").toString()));

    @Test void parsesBothSourceLayoutsAndCatalogsExactSupport(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder stBuilder=DStabBuilderTestFixture.createWithMachine();
        Path st=dir.resolve("st5b.dyr");
        Files.writeString(st,"1 'ST5B' 1 0 1.2 2.667 .01 .017 200 10 -9 .004 .004 1.5 3.33 .1 .15 1.52 3.378 .1 .15 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(stBuilder).setStrictImport(true);parser.parseDynFile(st.toString());
        St5bExciter exciter=(St5bExciter)stBuilder.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);St5bData d=exciter.getData();
        assertEquals(1.2,d.getTc1(),TOL);assertEquals(2.667,d.getTb1(),TOL);
        assertEquals(200,d.getKr(),TOL);assertEquals(.004,d.getT1(),TOL);
        assertEquals(1.5,d.getTuc1(),TOL);assertEquals(3.378,d.getTob1(),TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());

        DStabNetworkBuilder esBuilder=DStabBuilderTestFixture.createWithMachine();
        Path es=dir.resolve("esst5b.dyr");
        Files.writeString(es,"1 'ESST5B' 1 .01 200 .004 .004 10 -9 1.2 2.667 .01 .017 1.52 3.378 .1 .15 1.5 3.33 .1 .15 /\n");
        new PSSEDStabDirectParser(esBuilder).setStrictImport(true).parseDynFile(es.toString());
        St5bExciter esExciter=(St5bExciter)esBuilder.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(esExciter);assertEquals("ESST5B",esExciter.getModelName());
        assertEquals(1.52,esExciter.getData().getToc1(),TOL);assertEquals(3.33,esExciter.getData().getTub1(),TOL);

        var descriptor=DynamicModelCatalog.find("ESST5B").orElseThrow();
        assertEquals("ST5B",descriptor.canonicalName());assertEquals(18,descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESST5B").orElseThrow().isImplementedExactly());
    }

    @Test void allSuppliedActiveSt5bRecordsUseReviewedSchema()throws Exception{
        List<Path> paths=List.of(CORPUS_ROOT.resolve("private_case_package/24HSP11p.dyr"),
                CORPUS_ROOT.resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr"),
                CORPUS_ROOT.resolve("31hs1ap/31hs1ap_348 (1)/31hs1ap.dyr"));
        assumeTrue(paths.stream().allMatch(Files::isRegularFile),"Missing supplied ST5B corpus under "+CORPUS_ROOT);
        Pattern pattern=Pattern.compile("(?ims)^\\s*\\d+\\s+'ST5B'\\s+[^/]+/");int count=0;
        for(Path path:paths){Matcher matcher=pattern.matcher(Files.readString(path));while(matcher.find()){
            String record=matcher.group();assertEquals(21,PsseDyrRecordReader.tokenize(record.substring(0,record.lastIndexOf('/'))).size(),path.toString());count++;}}
        assertEquals(47,count);
    }

    @Test void initializesWithoutDriftAndUsesPublishedTakeoverLogic()throws Exception{
        Fixture normal=fixture(baseData());for(int i=0;i<2000;i++)step(normal.exciter,normal.machine,.0001);
        assertEquals(1.2,normal.exciter.getOutput(normal.machine),1e-10);assertEquals(0,normal.exciter.getSelectedPath());
        assertEquals(Set.of("Efd","Sensed Vt","LL1","LL2","LLU1","LLU2","LLO1","LLO2"),normal.exciter.getNamedStates().keySet());
        Fixture over=fixture(baseData());over.exciter.setVoel(.1);for(int i=0;i<500;i++)step(over.exciter,over.machine,.0001);
        assertEquals(1,over.exciter.getSelectedPath());assertEquals(.12,over.exciter.getGatedError(),TOL);
        Fixture under=fixture(baseData());under.exciter.setVuel(.2);for(int i=0;i<500;i++)step(under.exciter,under.machine,.0001);
        assertEquals(-1,under.exciter.getSelectedPath());assertEquals(.12,under.exciter.getGatedError(),TOL);
    }

    @Test void normalPathAndFinalLagMatchPublishedEquations()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.2,1.04,.12,.12};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){
            double[] d0=derivatives(x),p=add(x,d0,dt),d1=derivatives(p);
            for(int j=0;j<x.length;j++)x[j]+=.5*(d0[j]+d1[j])*dt;
            step(f.exciter,f.machine,dt);max=Math.max(max,Math.abs(x[0]-f.exciter.getOutput(f.machine)));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            double error=1.16+.1-x[1],y1=.05/.2*error+(1-.05/.2)*x[2];
            max=Math.max(max,Math.abs(10*((.1/.3)*y1+(1-.1/.3)*x[3])-f.exciter.getRegulatorOutput()));
        }
        assertTrue(max<1e-10,"ST5B normal-path max error="+max);
    }

    @Test void appliesLimitsCompensationCorrectionsAndSolverIntegration()throws Exception{
        St5bData d=baseData();d.setTr(.004);d.setTb1(.015);d.setVrmax(-2);d.setVrmin(-3);d.setKc(.2);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();St5bExciter e=builder.addExcSt5b("Bus1","1","ST5B",d);
        Machine m=builder.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);
        assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(0,e.tr,TOL);assertEquals(.02,e.tb1,TOL);
        assertTrue(e.vrmax>=e.getRegulatorOutput());assertTrue(e.vrmin<=e.getRegulatorOutput());
        double ifd=m.calculateIfd(com.interpss.dstab.mach.MachineIfdBase.EXCITER);
        if(!Double.isFinite(ifd))ifd=0;
        assertEquals(e.getRegulatorOutput()-.2*ifd,e.getFinalLagInput(),TOL);

        DynamicSimuAlgorithm algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.005);algorithm.setTotalSimuTimeSec(.02);
        algorithm.setSimuOutputHandler(new StateMonitor());assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());assertTrue(algorithm.performSimulation());
    }

    private static Fixture fixture(St5bData data)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();St5bExciter e=b.addExcSt5b("Bus1","1","ST5B",data);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);
    }
    private static St5bData baseData(){St5bData d=new St5bData();d.setTr(.1);d.setTc1(.05);d.setTb1(.2);d.setTc2(.1);d.setTb2(.3);
        d.setKr(10);d.setVrmax(20);d.setVrmin(-20);d.setT1(.2);d.setKc(0);d.setTuc1(.02);d.setTub1(.4);
        d.setTuc2(.03);d.setTub2(.5);d.setToc1(.04);d.setTob1(.6);d.setToc2(.05);d.setTob2(.7);return d;}
    private static double[] derivatives(double[] x){double error=1.16+.1-x[1];double y1=.05/.2*error+(1-.05/.2)*x[2];
        double y2=.1/.3*y1+(1-.1/.3)*x[3];return new double[]{(10*y2-x[0])/.2,(1.04-x[1])/.1,(error-x[2])/.2,(y1-x[3])/.3};}
    private static double[] add(double[] x,double[] d,double dt){double[] r=new double[x.length];for(int i=0;i<x.length;i++)r[i]=x[i]+d[i]*dt;return r;}
    private static void step(St5bExciter e,Machine m,double dt){e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0);e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1);}
    private record Fixture(Machine machine,St5bExciter exciter){}
}
