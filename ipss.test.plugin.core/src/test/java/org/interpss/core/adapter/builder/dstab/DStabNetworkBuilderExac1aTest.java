package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import org.interpss.dstab.control.exc.psse.exac1.Exac1Data;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.exc.psse.exac1a.Exac1aData;
import org.interpss.dstab.control.exc.psse.exac1a.Exac1aExciter;
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
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderExac1aTest extends CorePluginTestSetup {
    private static final double TOL=1e-8;
    private static final Path CORPUS_ROOT=Path.of(System.getProperty("psse.testcases.root",
            Path.of("testData", "private", "model-corpus").toString()));
    @TempDir Path tempDir;

    @Test
    void parsesRealRecordAndUsesEfdFeedback() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Path dyr=tempDir.resolve("exac1a.dyr");
        Files.writeString(dyr,"1 'EXAC1A' '1' .016668 1 1 150 .016668 10 -10 .15 .01 2.6 .36 .89 1 4.65 .037 6.19 .113 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);machine.setEfd(1.2);
        Exac1aExciter exc=(Exac1aExciter)machine.getExciter();
        assertNotNull(exc);
        assertEquals(150.0,exc.getData().getKa(),TOL);
        assertEquals(.36,exc.getData().getKc(),TOL);
        assertEquals(.89,exc.getData().getKd(),TOL);
        assertEquals(0,exc.getData().getSpdmlt(),TOL);
        AnControllerField washout=Exac1aExciter.class.getField("washout")
                .getAnnotation(AnControllerField.class);
        assertEquals("this.rectifier.y",washout.input());

        exc.configureIntegrationStep(.005);
        assertTrue(exc.initStates(machine.getDStabBus(),machine));
        double initial=exc.getOutput(machine);
        assertEquals(1.2,initial,1e-6);
        assertEquals(initial,exc.getStateInputSnapshot()[4],TOL,
                "EXAC1A washout must be driven by rectified EFD");
        machine.getDStabBus().setVoltage(new Complex(.99,0));
        for(int i=0;i<40;i++)step(exc,machine,.005);
        assertTrue(Double.isFinite(exc.getOutput(machine)));
        assertTrue(exc.getOutput(machine)>initial);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void bothSuppliedRecordsUseExactSeventeenParameterSchema() throws Exception {
        List<Path> files=List.of(
                CORPUS_ROOT.resolve("private_case_package/24HSP11p.dyr"),
                CORPUS_ROOT.resolve("24LW1a1p_package (1)/24LW1a1p_package/24LW11p.dyr"));
        assumeTrue(files.stream().allMatch(Files::isRegularFile),
                "Missing supplied EXAC1A corpus under "+CORPUS_ROOT);
        Pattern pattern=Pattern.compile("(?ims)^\\s*\\d+\\s+'EXAC1A'\\s+[^/]+/");int count=0;
        for(Path file:files){Matcher matcher=pattern.matcher(Files.readString(file));while(matcher.find()){
            String record=matcher.group();assertEquals(20,
                    PsseDyrRecordReader.tokenize(record.substring(0,record.lastIndexOf('/'))).size(),
                    file.toString());count++;}}
        assertEquals(2,count);
    }

    @Test
    void catalogAndParserEnforceNativePsseSchema() throws Exception {
        var descriptor=DynamicModelCatalog.find("EXAC1A").orElseThrow();
        assertEquals(17,descriptor.parameterCount());
        assertTrue(descriptor.recordSchema().accepts(17));
        assertFalse(descriptor.recordSchema().accepts(18));
        assertEquals(DynamicModelSupportStatus.LOADABLE,descriptor.supportStatus());
        assertTrue(WeccApprovedDynamicModelCatalog.findExciter("EXAC1A")
                .orElseThrow().isImplementedExactly());
        Path dyr=tempDir.resolve("powerworld-extension.dyr");
        Files.writeString(dyr,"1 'EXAC1A' 1 0 0 0 400 .02 8 -8 1 .1 1 .2 .5 1 2 .1 3 .2 1 /\n");
        PSSEDStabDirectParser parser=new PSSEDStabDirectParser(DStabBuilderTestFixture.createWithMachine())
                .setStrictImport(true);
        assertThrows(Exception.class,()->parser.parseDynFile(dyr.toString()));
    }

    @Test
    void fiveCmlStatesMatchIndependentBlockwiseModifiedEulerOracle() throws Exception {
        Fixture fixture=fixture(baseData());double dt=.0001;double[] maxError=new double[5];
        fixture.exciter.setRefPoint(fixture.exciter.getRefPoint()+.1);
        for(int n=0;n<2000;n++){
            double[] old=fixture.exciter.getStateSnapshot();
            assertTrue(fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,0));
            double[] predicted=fixture.exciter.getStateSnapshot();
            double[] firstInputs=fixture.exciter.getStateInputSnapshot();
            double[] d0=blockDerivatives(old,firstInputs);
            for(int i=0;i<old.length;i++)maxError[i]=Math.max(maxError[i],
                    Math.abs(predicted[i]-old[i]-d0[i]*dt));
            assertTrue(fixture.exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,fixture.machine,1));
            double[] corrected=fixture.exciter.getStateSnapshot();
            double[] secondInputs=fixture.exciter.getStateInputSnapshot();
            double[] d1=blockDerivatives(predicted,secondInputs);
            for(int i=0;i<old.length;i++){
                double expected=predicted[i]+.5*(d1[i]-d0[i])*dt;
                maxError[i]=Math.max(maxError[i],Math.abs(corrected[i]-expected));
            }
        }
        for(int i=0;i<maxError.length;i++)assertTrue(maxError[i]<1e-12,
                "EXAC1A state "+i+" blockwise modified-Euler max error="+maxError[i]);
    }

    @Test
    void efdFeedbackIsDistinctFromExac1InternalFieldFeedback() throws Exception {
        Exac1aData data=baseData();data.setKe(.5);
        Fixture fixture=fixture(data);
        double efd=fixture.exciter.getOutput(fixture.machine);
        double washoutInput=fixture.exciter.getStateInputSnapshot()[4];
        assertEquals(efd,washoutInput,TOL);
        double ve=fixture.exciter.getStateSnapshot()[3];
        double vfe=ve*(data.getKe()+Exac1aExciter.saturation(
                ve,data.getE1(),data.getSe1(),data.getE2(),data.getSe2()));
        assertTrue(Math.abs(washoutInput-vfe)>.1,
                "test must distinguish EFD feedback from EXAC1's internal VFE feedback");
    }

    @Test
    void zeroRateFeedbackMatchesExac1CommonProfile() throws Exception {
        DStabNetworkBuilder exac1Builder=DStabBuilderTestFixture.createWithMachine();
        Machine exac1Machine=exac1Builder.getDStabNetwork().getMachine("Bus1-mach1");
        exac1Machine.setEfd(1.2);
        Exac1Data exac1Data=new Exac1Data();copyCommonData(baseData(),exac1Data);exac1Data.setKf(0);
        Exac1Exciter exac1=exac1Builder.addExcExac1("Bus1","1",exac1Data);exac1.configureIntegrationStep(.001);

        DStabNetworkBuilder exac1aBuilder=DStabBuilderTestFixture.createWithMachine();
        Machine exac1aMachine=exac1aBuilder.getDStabNetwork().getMachine("Bus1-mach1");
        exac1aMachine.setEfd(1.2);
        Exac1aData exac1aData=baseData();exac1aData.setKf(0);
        Exac1aExciter exac1a=exac1aBuilder.addExcExac1a("Bus1","1",exac1aData);
        exac1a.configureIntegrationStep(.001);
        assertTrue(exac1.initStates(exac1Machine.getDStabBus(),exac1Machine));
        assertTrue(exac1a.initStates(exac1aMachine.getDStabBus(),exac1aMachine));
        exac1.setRefPoint(exac1.getRefPoint()+.1);exac1a.setRefPoint(exac1a.getRefPoint()+.1);
        for(int i=0;i<500;i++){
            step(exac1,exac1Machine,.0002);step(exac1a,exac1aMachine,.0002);
            assertEquals(exac1.getOutput(exac1Machine),exac1a.getOutput(exac1aMachine),1e-12);
            assertArrayEquals(exac1.getStateSnapshot(),exac1a.getStateSnapshot(),1e-12);
        }
    }

    @Test
    void appliesPublishedTimeCorrectionsAndInitializationLimitExpansion() throws Exception {
        Exac1aData data=baseData();data.setTr(.004);data.setTb(.009);data.setTa(.015);
        data.setTe(.005);data.setTf(.006);data.setVrmax(-2);data.setVrmin(-3);
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Exac1aExciter exciter=builder.addExcExac1a("Bus1","1",data);exciter.configureIntegrationStep(.01,2);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));
        assertEquals(0,exciter.tr,TOL);assertEquals(0,exciter.tb,TOL);
        assertEquals(.02,exciter.ta,TOL);assertEquals(50,exciter.integratorGain,TOL);
        assertEquals(.02,exciter.tf,TOL);assertTrue(exciter.vrmax>=exciter.getRegulatorOutput());
        assertTrue(exciter.vrmin<=exciter.getRegulatorOutput());
    }

    @Test
    void participatesInFullSimulation() throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        assertNotNull(builder.addExcExac1a("Bus1","1",baseData()));
        DynamicSimuAlgorithm algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.005);
        algorithm.setTotalSimuTimeSec(.02);algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
    }

    private Fixture fixture(Exac1aData data) throws Exception {
        DStabNetworkBuilder builder=DStabBuilderTestFixture.createWithMachine();
        Machine machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setEfd(1.2);
        Exac1aExciter exciter=builder.addExcExac1a("Bus1","1",data);exciter.configureIntegrationStep(.001);
        assertTrue(exciter.initStates(machine.getDStabBus(),machine));return new Fixture(machine,exciter);
    }

    private static Exac1aData baseData(){
        Exac1aData data=new Exac1aData();data.setTr(.1);data.setTb(.1);data.setTc(.02);
        data.setKa(2);data.setTa(.15);data.setVrmax(99);data.setVrmin(-99);data.setTe(.2);
        data.setKf(.5);data.setTf(.3);data.setKc(0);data.setKd(0);data.setKe(1);
        data.setE1(0);data.setSe1(0);data.setE2(0);data.setSe2(0);return data;
    }

    private static void copyCommonData(Exac1aData source,Exac1Data target){
        target.setTr(source.getTr());target.setTb(source.getTb());target.setTc(source.getTc());
        target.setKa(source.getKa());target.setTa(source.getTa());target.setVrmax(source.getVrmax());
        target.setVrmin(source.getVrmin());target.setTe(source.getTe());target.setKf(source.getKf());
        target.setTf(source.getTf());target.setKc(source.getKc());target.setKd(source.getKd());
        target.setKe(source.getKe());target.setE1(source.getE1());target.setSe1(source.getSe1());
        target.setE2(source.getE2());target.setSe2(source.getSe2());
    }

    private static double[] blockDerivatives(double[] x,double[] u){
        return new double[]{(u[0]-x[0])/.1,(u[1]-x[1])/.1,
                (2*u[2]-x[2])/.15,5*u[3],(u[4]-x[4])/.3};
    }

    private static void step(Exac1aExciter exciter,Machine machine,double dt){
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1));
    }

    private static void step(Exac1Exciter exciter,Machine machine,double dt){
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        assertTrue(exciter.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1));
    }

    private record Fixture(Machine machine,Exac1aExciter exciter){}
}
