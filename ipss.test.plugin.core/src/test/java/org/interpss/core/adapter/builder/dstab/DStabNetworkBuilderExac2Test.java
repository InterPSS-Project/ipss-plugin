package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.interpss.dstab.control.exc.psse.exac2.Exac2Data;
import org.interpss.dstab.control.exc.psse.exac2.Exac2Exciter;
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

public class DStabNetworkBuilderExac2Test extends CorePluginTestSetup {
    private static final double TOL=1e-8;
    private static final Path CORPUS_ROOT=Path.of(System.getProperty("psse.testcases.root",
            Path.of("testData", "private", "model-corpus").toString()));
    @TempDir Path tempDir;

    @Test void parsesNativeRecordInitializesAndResponds() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("exac2.dyr");
        Files.writeString(dyr,"1 'EXAC2' '1' .066667 1 1 1000 .066667 9.8378 -9.8378 1 29.0988 -29.098801 1.3 4 0 .49 1 .1 1.6 1 10 3.0789 .0084 4.1053 .0189 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1);machine.setEfd(1.2);Exac2Exciter exc=(Exac2Exciter)machine.getExciter();
        assertNotNull(exc);assertEquals(1000,exc.getData().getKa(),TOL);
        assertEquals(9.8378,exc.getData().getVamax(),TOL);assertEquals(4,exc.getData().getKl(),TOL);
        assertEquals(10,exc.getData().getVlr(),TOL);assertEquals(.0189,exc.getData().getSe2(),TOL);
        assertEquals(0,exc.getData().getSpdmlt(),TOL);
        exc.configureIntegrationStep(.005);assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);assertEquals(1.2,initial,1e-6);
        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for(int i=0;i<40;i++)step(exc,machine,.005);
        assertTrue(Double.isFinite(exc.getOutput(machine)));assertNotEquals(initial,exc.getOutput(machine),1e-4);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test void allThreeHundredSeventySevenSuppliedRecordsUseExactTwentyThreeParameterSchema() throws Exception {
        List<Path> files=List.of(
                CORPUS_ROOT.resolve("31hs1ap/31hs1ap_348 (1)/31hs1ap.dyr"),
                CORPUS_ROOT.resolve("private_case_package/24HSP11p.dyr"),
                CORPUS_ROOT.resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr"),
                CORPUS_ROOT.resolve("TamuTestCases/ACTIVSg10k/ACTIVSg10k_dynamics.dyr"),
                CORPUS_ROOT.resolve("TamuTestCases/ACTIVSg25k/ACTIVSg25k.dyr"));
        assumeTrue(files.stream().allMatch(Files::isRegularFile),"Missing supplied EXAC2 corpus under "+CORPUS_ROOT);
        Pattern pattern=Pattern.compile("(?ims)^\\s*\\d+\\s+'EXAC2'\\s+[^/]+/");int count=0;
        for(Path file:files){Matcher matcher=pattern.matcher(Files.readString(file));while(matcher.find()){
            String record=matcher.group();assertEquals(26,
                    PsseDyrRecordReader.tokenize(record.substring(0,record.lastIndexOf('/'))).size(),file.toString());count++;}}
        assertEquals(377,count);
    }

    @Test void catalogAndParserEnforceNativePsseSchema() throws Exception {
        var descriptor=DynamicModelCatalog.find("EXAC2").orElseThrow();
        assertEquals(23,descriptor.parameterCount());assertTrue(descriptor.recordSchema().accepts(23));
        assertFalse(descriptor.recordSchema().accepts(24));
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("EXAC2").orElseThrow().isImplementedExactly());
        Path dyr=tempDir.resolve("powerworld-extension.dyr");
        Files.writeString(dyr,"1 'EXAC2' 1 .1 .4 .1 20 .1 10 -10 1 10 -10 .4 4 .5 .1 .7 0 0 1 10 0 0 0 0 1 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(DStabBuilderTestFixture.createWithMachine())
                .setStrictImport(true);
        assertThrows(Exception.class,()->parser.parseDynFile(dyr.toString()));
    }

    @Test void fiveCmlStatesMatchIndependentBlockwiseModifiedEulerOracle() throws Exception {
        Fixture fixture=fixture(baseData());double dt=.0001,maxError=0;
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint()+.1);
        for(int n=0;n<2000;n++){
            double[] old=fixture.exciter.getStateSnapshot();
            assertTrue(fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,0));
            double[] predicted=fixture.exciter.getStateSnapshot(),firstInputs=fixture.exciter.getStateInputSnapshot();
            double[] d0=blockDerivatives(old,firstInputs);
            for(int i=0;i<old.length;i++)maxError=Math.max(maxError,Math.abs(predicted[i]-old[i]-d0[i]*dt));
            assertTrue(fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,1));
            double[] corrected=fixture.exciter.getStateSnapshot(),secondInputs=fixture.exciter.getStateInputSnapshot();
            double[] d1=blockDerivatives(predicted,secondInputs);
            for(int i=0;i<old.length;i++){
                double expected=predicted[i]+.5*(d1[i]-d0[i])*dt;
                maxError=Math.max(maxError,Math.abs(corrected[i]-expected));
            }
        }
        assertTrue(maxError<1e-12,"EXAC2 blockwise modified-Euler max error="+maxError);
    }

    @Test void publishedGateAndInitializationEquationsAreApplied() throws Exception {
        Exac2Data data=baseData();data.setKh(.5);data.setKl(4);data.setKb(2);data.setVlr(0);
        Fixture fixture=fixture(data);double vfe=fixture.exciter.getFieldVaule("this.vfe.y");
        assertEquals(data.getKh()*vfe+vfe/data.getKb(),fixture.exciter.va0,TOL,
                "VA initialization uses KH, not KL");
        assertEquals(vfe+vfe/(data.getKl()*data.getKb()),fixture.exciter.vlrEffective,TOL);
        assertEquals(vfe,fixture.exciter.getGatedRegulatorOutput(),TOL);
        assertEquals(Math.min(5-.5,4*(1.25-1)),Exac2Exciter.lowValueGate(5,1,.5,4,1.25),TOL);
    }

    @Test void appliesPublishedTimeCorrectionsGainCorrectionAndLimitExpansion() throws Exception {
        Exac2Data data=baseData();data.setTr(.004);data.setTb(.009);data.setTa(.005);
        data.setTe(.006);data.setTf(.007);data.setKa(0);data.setVamax(-2);data.setVamin(-3);
        data.setVrmax(-2);data.setVrmin(-3);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Exac2Exciter exciter=builder.addExcExac2("Bus1","1",data);assertNotNull(exciter);
        exciter.configureIntegrationStep(.01,2);assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        assertEquals(0,exciter.tr,TOL);assertEquals(0,exciter.tb,TOL);assertEquals(.02,exciter.ta,TOL);
        assertEquals(.02,exciter.ka,TOL);assertEquals(50,exciter.integratorGain,TOL);assertEquals(.02,exciter.tf,TOL);
        assertTrue(exciter.vamax>=exciter.getRegulatorOutput());assertTrue(exciter.vamin<=exciter.getRegulatorOutput());
        assertTrue(exciter.vrmax>=exciter.getGatedRegulatorOutput());assertTrue(exciter.vrmin<=exciter.getGatedRegulatorOutput());
    }

    @Test void participatesInFullSimulation() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcExac2("Bus1","1",baseData()));
        DynamicSimuAlgorithm algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.005);
        algorithm.setTotalSimuTimeSec(.02);algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private Fixture fixture(Exac2Data data) throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Exac2Exciter exciter=builder.addExcExac2("Bus1","1",data);exciter.configureIntegrationStep(.001);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));return new Fixture(machine,exciter);
    }
    private static Exac2Data baseData(){
        Exac2Data data=new Exac2Data();data.setTr(.1);data.setTb(.1);data.setTc(.02);
        data.setKa(2);data.setTa(.15);data.setVamax(99);data.setVamin(-99);data.setKb(1);
        data.setVrmax(99);data.setVrmin(-99);data.setTe(.2);data.setKl(4);data.setKh(.5);
        data.setKf(.5);data.setTf(.3);data.setKc(0);data.setKd(0);data.setKe(1);data.setVlr(10);
        data.setE1(0);data.setSe1(0);data.setE2(0);data.setSe2(0);return data;
    }
    private static double[] blockDerivatives(double[] x,double[] u){
        return new double[]{(u[0]-x[0])/.1,(u[1]-x[1])/.1,(2*u[2]-x[2])/.15,
                5*u[3],(u[4]-x[4])/.3};
    }
    private static void step(Exac2Exciter exciter,Machine machine,double dt){
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1));
    }
    private record Fixture(Machine machine,Exac2Exciter exciter){}
}
