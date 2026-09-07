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
import org.interpss.dstab.control.exc.psse.dc3a.Dc3aData;
import org.interpss.dstab.control.exc.psse.dc3a.Dc3aExciter;
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

/** Import and equation-level tests for DC3A/ESDC3A. */
public class Dc3aExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;
    private static final Path CORPUS_ROOT=Path.of(System.getProperty("psse.testcases.root",
            Path.of(System.getProperty("user.home"),"OneDrive","Documents","qiuhua","private_cases").toString()));

    @Test void parsesBothLayoutsAndCatalogsExactSupport(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder dcBuilder=DStabBuilderTestFixture.createWithMachine();Path dc=dir.resolve("dc3a.dyr");
        Files.writeString(dc,"1 'DC3A' 1 0 .058 2.2 0 15 .3 1 0 3 .05 5 .2 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(dcBuilder).setStrictImport(true);parser.parseDynFile(dc.toString());
        Dc3aExciter exciter=(Dc3aExciter)dcBuilder.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);Dc3aData d=exciter.getData();assertEquals(.058,d.getKv(),TOL);
        assertEquals(2.2,d.getVrmax(),TOL);assertEquals(15,d.getTrh(),TOL);assertEquals(.3,d.getTe(),TOL);
        assertEquals(3,d.getE1(),TOL);assertEquals(.2,d.getSe2(),TOL);assertTrue(parser.getLastImportReport().isStrictlyComplete());

        DStabNetworkBuilder esBuilder=DStabBuilderTestFixture.createWithMachine();Path es=dir.resolve("esdc3a.dyr");
        Files.writeString(es,"1 'ESDC3A' 1 .01 20 .05 3 -2 .4 1.1 3 .1 2.2 .03 1 1 /\n");
        new PSSEDStabDirectParser(esBuilder).setStrictImport(true).parseDynFile(es.toString());
        Dc3aExciter esExciter=(Dc3aExciter)esBuilder.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(esExciter);assertEquals("ESDC3A",esExciter.getModelName());assertEquals(20,esExciter.getData().getTrh(),TOL);
        assertEquals(1,esExciter.getData().getSpdmlt(),TOL);assertEquals(1,esExciter.getData().getExclim());
        var descriptor=DynamicModelCatalog.find("ESDC3A").orElseThrow();assertEquals("DC3A",descriptor.canonicalName());
        assertEquals(12,descriptor.parameterCount());assertTrue(descriptor.recordSchema().accepts(13));
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESDC3A").orElseThrow().isImplementedExactly());
    }

    @Test void allSuppliedDc3aRecordsUseReviewedSchema()throws Exception{
        List<Path> paths=List.of(CORPUS_ROOT.resolve("private_case_package/24HSP11p.dyr"),
                CORPUS_ROOT.resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr"),
                CORPUS_ROOT.resolve("31hs1ap/31hs1ap_348 (1)/31hs1ap.dyr"));
        assumeTrue(paths.stream().allMatch(Files::isRegularFile),"Missing supplied DC3A corpus under "+CORPUS_ROOT);
        Pattern pattern=Pattern.compile("(?ims)^\\s*\\d+\\s+'DC3A'\\s+[^/]+/");int count=0;
        for(Path path:paths){Matcher matcher=pattern.matcher(Files.readString(path));while(matcher.find()){
            String record=matcher.group();assertEquals(15,PsseDyrRecordReader.tokenize(record.substring(0,record.lastIndexOf('/'))).size(),path.toString());count++;}}
        assertEquals(12,count);
    }

    @Test void threeStateTrajectoryMatchesPublishedEquations()throws Exception{
        Fixture f=fixture("DC3A",baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.2,1.04,1.2};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){double[] d0=derivatives(x),p=add(x,d0,dt),d1=derivatives(p);
            for(int j=0;j<3;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            max=Math.max(max,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));max=Math.max(max,Math.abs(x[2]-f.exciter.getRheostatPosition()));}
        assertTrue(max<1e-10,"DC3A three-state max error="+max);
    }

    @Test void implementsThresholdSwitchSaturationLimitsAndEsdcExtensions()throws Exception{
        Fixture high=fixture("DC3A",baseData());high.exciter.setRefPoint(high.exciter.getRefPoint()+.3);assertEquals(2,high.exciter.getRegulatorOutput(),TOL);
        Fixture low=fixture("DC3A",baseData());low.exciter.setRefPoint(low.exciter.getRefPoint()-.3);assertEquals(0,low.exciter.getRegulatorOutput(),TOL);
        Dc3aData sat=baseData();sat.setE1(3);sat.setSe1(.1);sat.setE2(2);sat.setSe2(.03);
        Fixture sf=fixture("DC3A",sat);assertTrue(sf.exciter.getSaturation()>0);
        Dc3aData es=baseData();es.setSpdmlt(1);es.setExclim(1);Fixture speed=fixture("ESDC3A",es);
        speed.machine.setSpeed(1.02);assertEquals(speed.exciter.getInternalFieldVoltage()*1.02,speed.exciter.getOutput(speed.machine),TOL);
    }

    @Test void appliesCorrectionsExpansionAndParticipatesInSolver()throws Exception{
        Dc3aData d=baseData();d.setTr(.004);d.setTe(.015);d.setKv(-1);d.setTrh(-1);d.setVrmax(-2);d.setVrmin(-3);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Dc3aExciter e=b.addExcDc3a("Bus1","1","DC3A",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setSpeed(1);m.setEfd(1.2);e.configureIntegrationStep(.01,2);
        assertTrue(e.initStates(m.getDStabBus(),m));assertEquals(0,e.tr,TOL);assertEquals(.02,e.te,TOL);assertEquals(0,e.kv,TOL);assertEquals(0,e.trh,TOL);
        assertTrue(e.vrmax>=e.getRheostatPosition());assertTrue(e.vrmin<=e.getRheostatPosition());
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    private static Fixture fixture(String model,Dc3aData data)throws Exception{DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Dc3aExciter e=b.addExcDc3a("Bus1","1",model,data);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setSpeed(1);m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);}
    private static Dc3aData baseData(){Dc3aData d=new Dc3aData();d.setTr(.1);d.setKv(.2);d.setVrmax(2);d.setVrmin(0);d.setTrh(1);d.setTe(.3);d.setKe(1);d.setVemin(0);return d;}
    private static double[] derivatives(double[] x){double error=1.04+.1-x[1],vr=error>=.2?2:error<=-.2?0:x[2];return new double[]{(vr-x[0])/.3,(1.04-x[1])/.1,2*error/(.2*1)};}
    private static double[] add(double[] x,double[] d,double dt){double[] r=new double[x.length];for(int i=0;i<x.length;i++)r[i]=x[i]+d[i]*dt;return r;}
    private static void step(Dc3aExciter e,Machine m,double dt){e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0);e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1);}
    private record Fixture(Machine machine,Dc3aExciter exciter){}
}
