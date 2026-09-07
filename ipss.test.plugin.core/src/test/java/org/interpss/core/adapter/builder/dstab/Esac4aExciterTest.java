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
import org.interpss.dstab.control.exc.psse.esac4a.Esac4aData;
import org.interpss.dstab.control.exc.psse.esac4a.Esac4aExciter;
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
import com.interpss.dstab.mach.MachineIfdBase;

/** Import, PowerWorld-topology, and ANDES-common-profile tests for ESAC4A. */
public class Esac4aExciterTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;
    private static final Path CORPUS_ROOT=Path.of(System.getProperty("psse.testcases.root",
            Path.of(System.getProperty("user.home"),"OneDrive","Documents","qiuhua","private_cases").toString()));

    @Test void parsesExactRecordAndCatalogsRuntimeSupport(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("esac4a.dyr");
        Files.writeString(dyr,"1 'ESAC4A' 1 0 99 -99 1 5 20 .05 5 -5 0 /\n");
        PSSEDStabDirectParser p=new PSSEDStabDirectParser(b).setStrictImport(true);p.parseDynFile(dyr.toString());
        Esac4aExciter e=(Esac4aExciter)b.getDStabNetwork().getMachine("Bus1-mach1").getExciter();
        assertNotNull(e);Esac4aData d=e.getData();assertEquals(0,d.getTr(),TOL);assertEquals(99,d.getVimax(),TOL);
        assertEquals(-99,d.getVimin(),TOL);assertEquals(1,d.getTc(),TOL);assertEquals(5,d.getTb(),TOL);
        assertEquals(20,d.getKa(),TOL);assertEquals(.05,d.getTa(),TOL);assertEquals(5,d.getVrmax(),TOL);
        assertEquals(-5,d.getVrmin(),TOL);assertEquals(0,d.getKc(),TOL);assertTrue(p.getLastImportReport().isStrictlyComplete());
        var descriptor=DynamicModelCatalog.find("ESAC4A").orElseThrow();assertEquals(10,descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESAC4A").orElseThrow().isImplementedExactly());
    }

    @Test void allSuppliedRecordsUseReviewedTenParameterSchema()throws Exception{
        List<Path> paths=List.of(CORPUS_ROOT.resolve("private_case_package/24HSP11p.dyr"),
                CORPUS_ROOT.resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr"),
                CORPUS_ROOT.resolve("31hs1ap/31hs1ap_348 (1)/31hs1ap.dyr"));
        assumeTrue(paths.stream().allMatch(Files::isRegularFile),"Missing supplied ESAC4A corpus under "+CORPUS_ROOT);
        Pattern pattern=Pattern.compile("(?ims)^\\s*\\d+\\s+'ESAC4A'\\s+[^/]+/");int count=0;
        for(Path path:paths){Matcher matcher=pattern.matcher(Files.readString(path));while(matcher.find()){
            String record=matcher.group();assertEquals(13,PsseDyrRecordReader.tokenize(record.substring(0,record.lastIndexOf('/'))).size(),path.toString());count++;}}
        assertEquals(9,count);
    }

    @Test void threeStateTrajectoryMatchesAndesExac4CommonProfile()throws Exception{
        Esac4aData data=baseData();data.setKc(0);Fixture f=fixture(data);f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double vi0=1.2/2,ratio=.2/.4;double[] x={1.2,1.04,vi0-ratio*vi0};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){
            double[] d0=derivatives(x,.1),predicted=add(x,d0,dt),d1=derivatives(predicted,.1);
            for(int j=0;j<3;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            max=Math.max(max,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            double expectedLeadLag=ratio*clamp(1.04+vi0+.1-x[1],-5,5)+x[2];
            max=Math.max(max,Math.abs(expectedLeadLag-f.exciter.getLeadLagOutput()));
        }
        assertTrue(max<1e-9,"ESAC4A/ANDES EXAC4 common-profile max error="+max);
    }

    @Test void implementsInputLimitLimiterGateAndCommutationBoundary()throws Exception{
        Esac4aData d=baseData();d.setVimax(.65);d.setVimin(-.65);Fixture f=fixture(d);
        f.exciter.setRefPoint(f.exciter.getRefPoint()+1);assertEquals(.65,f.exciter.getLimitedError(),TOL);
        f.exciter.setVuel(.9);assertEquals(.9,f.exciter.getGateOutput(),TOL);
        f.exciter.setVoel(.2);assertEquals(.65,f.exciter.getLimitedError(),TOL);
        assertEquals(f.exciter.vrmax,f.exciter.getDynamicUpperLimit(),TOL);
    }

    @Test void appliesCorrectionsExpansionAndParticipatesInSolver()throws Exception{
        Esac4aData d=baseData();d.setTr(.004);d.setTb(.015);d.setTa(.01);d.setVimax(-1);d.setVimin(-2);d.setVrmax(-1);d.setVrmin(-2);
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Esac4aExciter e=b.addExcEsac4a("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.tb,TOL);assertEquals(.02,e.ta,TOL);
        assertTrue(e.vimax>=e.getLimitedError());assertTrue(e.vimin<=e.getLimitedError());assertTrue(e.vrmax>=1.2-TOL);
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());
        assertTrue(a.initialization());double ifd=m.calculateIfd(MachineIfdBase.EXCITER);assertTrue(Double.isFinite(ifd));
        assertEquals(Math.max(e.vrmin,e.vrmax-e.kc*ifd),e.getDynamicUpperLimit(),TOL);assertTrue(a.performSimulation());
    }

    private static Fixture fixture(Esac4aData d)throws Exception{DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Esac4aExciter e=b.addExcEsac4a("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);}
    private static Esac4aData baseData(){Esac4aData d=new Esac4aData();d.setTr(.1);d.setVimax(5);d.setVimin(-5);d.setTc(.2);d.setTb(.4);
        d.setKa(2);d.setTa(.3);d.setVrmax(5);d.setVrmin(-5);d.setKc(.1);return d;}
    private static double[] derivatives(double[] x,double step){double error=1.04+.6+step-x[1],vi=clamp(error,-5,5),vll=.5*vi+x[2];
        return new double[]{(2*vll-x[0])/.3,(1.04-x[1])/.1,((1-.5)*vi-x[2])/.4};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Esac4aExciter e,Machine m,double dt){e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0);e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1);}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private record Fixture(Machine machine,Esac4aExciter exciter){}
}
