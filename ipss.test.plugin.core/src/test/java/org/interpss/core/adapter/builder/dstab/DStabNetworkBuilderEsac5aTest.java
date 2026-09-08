package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.esac5a.Esac5aData;
import org.interpss.dstab.control.exc.psse.esac5a.Esac5aExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.fadapter.psse.dyr.WeccApprovedDynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** PowerWorld/IEEE ESAC5A checks with an independent ANDES-equation oracle. */
public class DStabNetworkBuilderEsac5aTest extends CorePluginTestSetup {
    private static final double TOL=1e-10;
    private static final Path CORPUS_ROOT=Path.of(System.getProperty("psse.testcases.root",
            Path.of(System.getProperty("user.home"),"OneDrive","Documents","qiuhua","private_cases").toString()));

    @Test void parsesExactRealPsseSchemaAndHoldsEquilibrium(@TempDir Path dir)throws Exception{
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();Path dyr=dir.resolve("esac5a.dyr");
        Files.writeString(dyr,"1 'ESAC5A' '1' .02 400 .03 9.9 0 1 1.2 .0015 .1 .6 0 1 1.08 1.2 1.16 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);machine.setSpeed(1);
        Esac5aExciter exciter=(Esac5aExciter)machine.getExciter();assertNotNull(exciter);
        assertEquals(400,exciter.getData().getKa(),TOL);assertEquals(.6,exciter.getData().getTf2(),TOL);
        assertEquals(0,exciter.getData().getTf3(),TOL);assertEquals(1.16,exciter.getData().getSe2(),TOL);
        assertEquals(0,exciter.getData().getSpdmlt(),TOL);assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));double initial=exciter.getOutput(machine);
        for(int i=0;i<1000;i++)step(exciter,machine,.0001);assertEquals(initial,exciter.getOutput(machine),1e-8);
    }

    @Test void allSixtyEightSuppliedRecordsUseReviewedFifteenParameterSchema()throws Exception{
        List<Path> files=List.of(
                CORPUS_ROOT.resolve("31hs1ap/31hs1ap_348 (1)/31hs1ap.dyr"),
                CORPUS_ROOT.resolve("private_case_package/24HSP11p.dyr"),
                CORPUS_ROOT.resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr"));
        assumeTrue(files.stream().allMatch(Files::isRegularFile),"Missing supplied ESAC5A corpus under "+CORPUS_ROOT);
        Pattern pattern=Pattern.compile("(?ims)^\\s*\\d+\\s+'ESAC5A'\\s+[^/]+/");int count=0;
        for(Path file:files){Matcher matcher=pattern.matcher(Files.readString(file));while(matcher.find()){
            String record=matcher.group();assertEquals(18,
                    PsseDyrRecordReader.tokenize(record.substring(0,record.lastIndexOf('/'))).size(),file.toString());count++;}}
        assertEquals(68,count);
    }

    @Test void fiveStateTrajectoryMatchesUpstreamAndesEquations()throws Exception{
        Fixture f=fixture(baseData());f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);
        double[] x={1.2,1.04,1.2,1.2,1.2};double dt=.0001,max=0;
        for(int i=0;i<2000;i++){
            double[] d0=andesDerivatives(x,.1),predict=add(x,d0,dt),d1=andesDerivatives(predict,.1);
            for(int j=0;j<x.length;j++)x[j]+=.5*(d0[j]+d1[j])*dt;step(f.exciter,f.machine,dt);
            max=Math.max(max,Math.abs(x[0]-f.exciter.getInternalFieldVoltage()));
            max=Math.max(max,Math.abs(x[1]-f.exciter.getSensedVoltage()));
            max=Math.max(max,Math.abs(x[2]-f.exciter.getRegulatorState()));
            max=Math.max(max,Math.abs(x[3]-f.exciter.getFeedback1State()));
            max=Math.max(max,Math.abs(x[4]-f.exciter.getFeedback2State()));
        }
        assertTrue(max<1e-9,"ESAC5A/ANDES five-state maximum error="+max);
    }

    @Test void appliesPublishedTimeCorrectionsAndAlgebraicRegulator()throws Exception{
        Esac5aData d=baseData();d.setTr(.004);d.setTa(.015);d.setTf1(.01);d.setTf2(.004);d.setTe(.01);
        d.setVrmax(-1);d.setVrmin(-2);DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        Esac5aExciter e=b.addExcEsac5a("Bus1","1",d);Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(1.2);
        e.configureIntegrationStep(.01,2);assertTrue(e.initStates(m.getDStabBus(),m));
        assertEquals(0,e.tr,TOL);assertEquals(.02,e.ta,TOL);assertEquals(.02,e.tf1,TOL);
        assertEquals(0,e.tf2,TOL);assertEquals(.02,e.te,TOL);assertTrue(e.vrmax>=e.getRegulatorOutput());

        Esac5aData algebraic=baseData();algebraic.setTa(0);Fixture f=fixture(algebraic);
        f.exciter.setRefPoint(f.exciter.getRefPoint()+.1);double direct=.2/.5,stored=(1-direct)*1.2;
        double offset=.05*(stored-1.2)/.7,expected=2*(.7-offset)/(1+2*.05*direct/.7);
        assertEquals(expected,f.exciter.getRegulatorOutput(),TOL);
    }

    @Test void preservesSaturationAndTypedOnlySpeedMultiplier()throws Exception{
        Esac5aData d=baseData();d.setE1(1);d.setSe1(.1);d.setE2(2);d.setSe2(.3);d.setSpdmlt(1);
        Fixture f=fixture(d,1.02);assertEquals(1.2,f.exciter.getOutput(f.machine),1e-8);
        assertEquals(1.2/1.02,f.exciter.getInternalFieldVoltage(),1e-8);
        assertTrue(f.exciter.getFieldFeedback()>f.exciter.ke*f.exciter.getInternalFieldVoltage());
    }

    @Test void normalizesLimitsRejectsInvalidParametersAndRunsFullSolver()throws Exception{
        DStabNetworkBuilder invalid=DStabBuilderTestFixture.createWithMachine();Esac5aData d=baseData();d.setTe(0);
        assertNull(invalid.addExcEsac5a("Bus1","1",d));d=baseData();d.setTf2(-.1);assertNull(invalid.addExcEsac5a("Bus1","1",d));

        d=baseData();d.setVrmax(-1);d.setVrmin(1);DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();
        Esac5aExciter e=b.addExcEsac5a("Bus1","1",d);Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setEfd(2);
        assertTrue(e.initStates(m.getDStabBus(),m));assertTrue(e.vrmax>=2);assertEquals(-1,e.vrmin,TOL);
        DynamicSimuAlgorithm a=DStabObjectFactory.createDynamicSimuAlgorithm(b.getDStabNetwork());
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.005);a.setTotalSimuTimeSec(.02);
        a.setSimuOutputHandler(new StateMonitor());assertTrue(a.getAclfAlgorithm().loadflow());assertTrue(a.initialization());assertTrue(a.performSimulation());
    }

    @Test void approvedCatalogMapsEsac5aExactly(){
        assertEquals(15,DynamicModelCatalog.find("ESAC5A").orElseThrow().parameterCount());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("ESAC5A").orElseThrow().isImplementedExactly());
    }

    private static Fixture fixture(Esac5aData d)throws Exception{return fixture(d,1);}
    private static Fixture fixture(Esac5aData d,double speed)throws Exception{
        DStabNetworkBuilder b=DStabBuilderTestFixture.createWithMachine();Esac5aExciter e=b.addExcEsac5a("Bus1","1",d);
        Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");m.setSpeed(speed);m.setEfd(1.2);
        assertNotNull(e);assertTrue(e.initStates(m.getDStabBus(),m));return new Fixture(m,e);
    }
    private static Esac5aData baseData(){Esac5aData d=new Esac5aData();d.setTr(.1);d.setKa(2);d.setTa(.3);
        d.setVrmax(10);d.setVrmin(-10);d.setKe(1);d.setTe(.4);d.setKf(.05);d.setTf1(.7);
        d.setTf2(.5);d.setTf3(.2);d.setE1(0);d.setSe1(0);d.setE2(0);d.setSe2(0);return d;}
    private static double[] andesDerivatives(double[] x,double step){double lead=.4*x[2]+.6*x[3];
        double feedback=.05*(lead-x[4])/.7,error=1.04+.6+step-x[1]-feedback;
        return new double[]{(x[2]-x[0])/.4,(1.04-x[1])/.1,(2*error-x[2])/.3,
                (x[2]-x[3])/.5,(lead-x[4])/.7};}
    private static double[] add(double[] x,double[] d,double dt){double[] y=new double[x.length];for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;return y;}
    private static void step(Esac5aExciter e,Machine m,double dt){assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));assertTrue(e.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));}
    private record Fixture(Machine machine,Esac5aExciter exciter){}
}
