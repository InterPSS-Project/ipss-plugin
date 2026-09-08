package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Data;
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

public class DStabNetworkBuilderExac1Test extends CorePluginTestSetup {
    private static final double TOL=1e-8;
    private static final Path CORPUS_ROOT=Path.of(System.getProperty("psse.testcases.root",
            Path.of(System.getProperty("user.home"),"OneDrive","Documents","qiuhua","private_cases").toString()));
    @TempDir Path tempDir;

    @Test
    void parsesCompleteRecordInitializesAndResponds() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("exac1.dyr");
        Files.writeString(dyr,"1 'EXAC1' '1' 0 0 0 400 .066667 8.104 -8.104 .4617 .035 1 .2774 .5 1 2.0831 .0819 2.7774 .4095 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setEfd(1.2);
        Exac1Exciter exc=(Exac1Exciter)machine.getExciter();
        assertNotNull(exc);
        assertEquals(400,exc.getData().getKa(),TOL);
        assertEquals(.2774,exc.getData().getKc(),TOL);
        assertEquals(.4095,exc.getData().getSe2(),TOL);
        assertEquals(0,exc.getData().getSpdmlt(),TOL);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);
        assertEquals(1.2,initial,1e-6);
        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for(int i=0;i<40;i++){
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            assertTrue(exc.nextStep(.005,DynamicSimuMethod.MODIFIED_EULER,machine,1));
        }
        assertTrue(exc.getOutput(machine)>initial,
                () -> "initial="+initial+", final="+exc.getOutput(machine));
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void allThreeHundredFortySixSuppliedRecordsUseExactSeventeenParameterSchema() throws Exception {
        List<Path> files=List.of(
                CORPUS_ROOT.resolve("31hs1ap/31hs1ap_348 (1)/31hs1ap.dyr"),
                CORPUS_ROOT.resolve("private_case_package/24HSP11p.dyr"),
                CORPUS_ROOT.resolve("Texas7k_20210804_Plus2023/Texas7k_20210804.dyr"),
                CORPUS_ROOT.resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr"),
                CORPUS_ROOT.resolve("TamuTestCases/ACTIVSg10k/ACTIVSg10k_dynamics.dyr"),
                CORPUS_ROOT.resolve("TamuTestCases/ACTIVSg25k/ACTIVSg25k.dyr"));
        assumeTrue(files.stream().allMatch(Files::isRegularFile),"Missing supplied EXAC1 corpus under "+CORPUS_ROOT);
        Pattern pattern=Pattern.compile("(?ims)^\\s*\\d+\\s+'EXAC1'\\s+[^/]+/");int count=0;
        for(Path file:files){Matcher matcher=pattern.matcher(Files.readString(file));while(matcher.find()){
            String record=matcher.group();assertEquals(20,
                    PsseDyrRecordReader.tokenize(record.substring(0,record.lastIndexOf('/'))).size(),file.toString());count++;}}
        assertEquals(346,count);
    }

    @Test
    void catalogAndParserEnforceNativePsseSchema(@TempDir Path dir) throws Exception {
        var descriptor=DynamicModelCatalog.find("EXAC1").orElseThrow();
        assertEquals(17,descriptor.parameterCount());
        assertTrue(descriptor.recordSchema().accepts(17));
        assertFalse(descriptor.recordSchema().accepts(18));
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("EXAC1").orElseThrow().isImplementedExactly());
        Path dyr=dir.resolve("powerworld-extension.dyr");
        Files.writeString(dyr,"1 'EXAC1' 1 0 0 0 400 .02 8 -8 1 .1 1 .2 .5 1 2 .1 3 .2 1 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(DStabBuilderTestFixture.createWithMachine())
                .setStrictImport(true);
        assertThrows(Exception.class,()->parser.parseDynFile(dyr.toString()));
    }

    @Test
    void fiveCmlStatesMatchIndependentBlockwiseModifiedEulerOracle() throws Exception {
        Fixture fixture=fixture(baseData());
        double dt=.0001,maxError=0;fixture.exciter.setRefPoint(fixture.exciter.getRefPoint()+.1);
        for(int n=0;n<2000;n++){
            double[] old=states(fixture.exciter);
            assertTrue(fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,0));
            double[] predicted=states(fixture.exciter),firstInputs=fixture.exciter.getStateInputSnapshot();
            double[] d0=blockDerivatives(old,firstInputs);
            for(int i=0;i<old.length;i++)maxError=Math.max(maxError,Math.abs(predicted[i]-old[i]-d0[i]*dt));
            assertTrue(fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,1));
            double[] corrected=states(fixture.exciter),secondInputs=fixture.exciter.getStateInputSnapshot();
            double[] d1=blockDerivatives(predicted,secondInputs);
            for(int i=0;i<old.length;i++){
                double expected=predicted[i]+.5*(d1[i]-d0[i])*dt;
                maxError=Math.max(maxError,Math.abs(corrected[i]-expected));
            }
        }
        assertTrue(maxError<1e-12,"EXAC1 blockwise modified-Euler max error="+maxError);
    }

    @Test
    void appliesPublishedTimeCorrectionsAndInitializationLimitExpansion() throws Exception {
        Exac1Data data=baseData();data.setTr(.004);data.setTb(.009);data.setTa(.015);
        data.setTe(.005);data.setTf(.006);data.setVrmax(-2);data.setVrmin(-3);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Exac1Exciter exciter=builder.addExcExac1("Bus1","1",data);exciter.configureIntegrationStep(.01,2);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        assertEquals(0,exciter.tr,TOL);assertEquals(0,exciter.tb,TOL);
        assertEquals(.02,exciter.ta,TOL);assertEquals(50,exciter.integratorGain,TOL);
        assertEquals(.02,exciter.tf,TOL);assertTrue(exciter.vrmax>=exciter.getRegulatorOutput());
        assertTrue(exciter.vrmin<=exciter.getRegulatorOutput());
    }

    @Test
    void participatesInFullSimulation() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcExac1("Bus1","1",baseData()));
        DynamicSimuAlgorithm algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.005);
        algorithm.setTotalSimuTimeSec(.02);algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    @Test
    void rectifierMatchesAllAndesPiecewiseRegionsAndInverseInitialization() {
        double[] inputs={-.1,.2,.5,.8,1.2};
        double[] expected={1,1-.577*.2,Math.sqrt(.75-.25),1.732*.2,0};
        for(int i=0;i<inputs.length;i++) assertEquals(expected[i],Exac1Exciter.rectifierFactor(inputs[i]),TOL);
        for(double loading:new double[]{.2,.5,.8}) {
            double efd=Exac1Exciter.rectifierFactor(loading);
            assertEquals(1.0,Exac1Exciter.solveInternalVoltage(efd,loading),TOL);
        }
    }

    private static Fixture fixture(Exac1Data data) throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Exac1Exciter exciter=builder.addExcExac1("Bus1","1",data);exciter.configureIntegrationStep(.001);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));return new Fixture(machine,exciter);
    }

    private static Exac1Data baseData(){
        Exac1Data data=new Exac1Data();data.setTr(.1);data.setTb(.1);data.setTc(.02);
        data.setKa(2);data.setTa(.15);data.setVrmax(99);data.setVrmin(-99);data.setTe(.2);
        data.setKf(.5);data.setTf(.3);data.setKc(0);data.setKd(0);data.setKe(1);
        data.setE1(0);data.setSe1(0);data.setE2(0);data.setSe2(0);return data;
    }

    private static double[] states(Exac1Exciter exciter){return exciter.getStateSnapshot();}

    private static double[] blockDerivatives(double[] x,double[] u){
        return new double[]{(u[0]-x[0])/.1,(u[1]-x[1])/.1,
                (2*u[2]-x[2])/.15,5*u[3],(u[4]-x[4])/.3};
    }

    private static void step(Exac1Exciter exciter,Machine machine,double dt){
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1));
    }

    private record Fixture(Machine machine,Exac1Exciter exciter){}
}
