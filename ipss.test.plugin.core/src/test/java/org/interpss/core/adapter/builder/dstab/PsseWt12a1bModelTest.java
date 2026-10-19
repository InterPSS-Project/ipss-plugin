package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt1g1Machine;
import org.interpss.dstab.mach.Wt12a1bData;
import org.interpss.dstab.mach.Wt12a1bModel;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.cache.StateMonitor;

public class PsseWt12a1bModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void rateLimitedRampAndTimerMatchPublishedDiagram() {
        Wt12a1bModel model = new Wt12a1bModel(data());
        model.initialize(.8, 0, .8, 1.0);
        model.step(.01, .8, 0, .5, 0);
        model.step(.01, .8, 0, .5, 1);
        assertEquals(.794, model.getNamedStates().get("Mechanical power ramp integrator"), 1e-12);
        assertEquals(.79985, model.getOutput(), 1e-12);
        assertEquals(.19, model.getRemainingTime(), 1e-12);
        assertEquals(.5, model.getNamedStates().get("Terminal voltage measurement filter"), 0.0);
        assertEquals(3, model.getNamedStates().size());

        for (int i=0;i<19;i++) { model.step(.01,.8,0,.5,0); model.step(.01,.8,0,.5,1); }
        double low = model.getNamedStates().get("Mechanical power ramp integrator");
        model.step(.01,.8,0,.5,0); model.step(.01,.8,0,.5,1);
        assertTrue(model.getNamedStates().get("Mechanical power ramp integrator") < low,
                "continued low voltage starts another reduction interval after expiry");
    }

    @Test
    void exactWrapperAttachesToDriveTrain(@TempDir Path tempDir) throws Exception {
        IpssCorePlugin.init();
        Path dyr=tempDir.resolve("synthetic-wt12a1b.dyr");
        Files.writeString(dyr, """
                1 'WT1G1' '1' .810 .035 3.600 .210 .150 .100 1.000 .025 1.200 .140 /
                1 'WT12T1' '1' 5.7 .06 .79 4.4 .83 /
                1 'USRMDL' 'WT12A1U_B' 105 0 1 14 3 2 0
                  .023 3.7 -2.9 .31 .17 .61 .27 .24 .48 .16 .69 .09 .94 .04 /
                2 'GENCLS' '1' 99999 0 /
                """);
        var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1.raw").toString(),dyr.toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        context.getDynSimuAlgorithm().setSimuOutputHandler(new StateMonitor());
        assertTrue(context.getDynSimuAlgorithm().initialization());
        Wt1g1Machine machine=(Wt1g1Machine)context.getDStabilityNet().getMachine("Bus1-mach1");
        assertInstanceOf(Wt12a1bModel.class,machine.getDriveTrain().getAerodynamicController());
        assertTrue(DynamicModelCatalog.find("WT12A1U_B").orElseThrow()
                .recordSchema().accepts(21));
    }

    @Test
    void strictImportRejectsWrongAllocation(@TempDir Path tempDir) throws Exception {
        IpssCorePlugin.init();
        var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1.raw").toString());
        Path dyr=tempDir.resolve("wrong.dyr");
        Files.writeString(dyr,"1 'USRMDL' 'WT12A1U_B' 106 0 1 14 3 2 0 .02 3 -2 .3 .2 .6 .2 .2 .5 .1 .8 .05 .95 .03 /");
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(context.getDStabilityNet()))
                .setStrictImport(true);
        assertThrows(Exception.class,()->parser.parseDynFile(dyr.toString()));
        assertThrows(IllegalArgumentException.class,()->new Wt12a1bData(0,.1,2,.1,.2,.1,.5,
                .4,.1,.3,.1,.8,.1,.9,.1));
    }

    private static Wt12a1bData data() {
        return new Wt12a1bData(0,0,2,-1,.2,.2,.5,
                .4,.3,.6,.2,.8,.1,.95,.05);
    }

    @Test void explicitIdWrapperAndStandardRecordUseNativePowerReference(@TempDir Path dir)throws Exception{
        for(boolean wrapper:new boolean[]{false,true}){
            IpssCorePlugin.init();Path file=dir.resolve("native"+wrapper+".dyr");
            String head=wrapper?"1 'USRMDL' '1' 'WT12A1U_B' 105 0 1 14 3 2 0 ":"1 'WT12A1B' '1' ";
            Files.writeString(file,"1 'WT1G1' '1' .810 .035 3.600 .210 .150 .100 1.000 .025 1.200 .140 /\n1 'WT12T1' '1' 5.4 .05 .82 4.7 .9 /\n"+head+".02 .4 -.6 .2 .1 .3 .3 .3 .6 .2 .8 .1 .95 .05 /\n2 'GENCLS' '1' 99999 0 /\n");
            var context=new PSSEMultiFileLoader().loadDStab(CASE.resolve("SMIB_v33_wt1g1.raw").toString(),file.toString());
            assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());context.getDynSimuAlgorithm().setSimuOutputHandler(new StateMonitor());assertTrue(context.getDynSimuAlgorithm().initialization());
            var machine=(Wt1g1Machine)context.getDStabilityNet().getMachine("Bus1-mach1");var model=(Wt12a1bModel)machine.getDriveTrain().getAerodynamicController();
            assertEquals(Wt12a1bModel.Specification.NATIVE_STANDARD,model.getSpecification());assertEquals(machine.getPe(),model.getPowerReference(),1e-9);
        }
    }

    @Test void nativeReferenceDoesNotReplaceMechanicalStateSeed(){
        var published=new Wt12a1bModel(data());var nativeModel=new Wt12a1bModel(data(),Wt12a1bModel.Specification.NATIVE_STANDARD);
        published.initialize(.7,0,.8,1);nativeModel.initialize(.7,0,.8,1);
        assertEquals(.8,published.getPowerReference(),0);assertEquals(.7,nativeModel.getPowerReference(),0);assertEquals(published.getNamedStates(),nativeModel.getNamedStates());
        nativeModel.step(.01,.7,0,1,0);nativeModel.step(.01,.7,0,1,1);assertTrue(nativeModel.getNamedStates().get("Mechanical power ramp integrator")<.8);
        published.step(.01,.7,0,1,0);published.step(.01,.7,0,1,1);assertEquals(.8,published.getOutput(),0);
    }

    @Test void zeroTimeObservationDoesNotIntegrateOrConsumeRampWindow(){
        var model=new Wt12a1bModel(data());model.initialize(.8,0,.8,1);model.step(.01,.8,0,.5,0);model.step(.01,.8,0,.5,1);
        double time=model.getRemainingTime(),power=model.getOutput(),ramp=model.getNamedStates().get("Mechanical power ramp integrator");
        model.step(0,.8,0,.4,0);model.step(0,.8,0,.3,1);assertEquals(time,model.getRemainingTime(),0);assertEquals(power,model.getOutput(),0);assertEquals(ramp,model.getNamedStates().get("Mechanical power ramp integrator"),0);
        assertEquals(.3,model.getNamedStates().get("Terminal voltage measurement filter"),0);
    }

    @Test void stagesMustBePairedAndReinitializationClearsPendingMemory(){
        var model=new Wt12a1bModel(data());model.initialize(.8,0,.8,1);
        assertThrows(IllegalStateException.class,()->model.step(.01,.8,0,.5,1));model.step(.01,.8,0,.5,0);
        assertThrows(IllegalStateException.class,()->model.step(.01,.8,0,.5,0));assertThrows(IllegalStateException.class,()->model.step(.02,.8,0,.5,1));
        model.step(0,.8,0,.5,0);model.step(.01,.8,0,.5,1);model.initialize(.8,0,.8,1);assertEquals(0,model.getRemainingTime(),0);assertEquals(.8,model.getOutput(),0);
        assertThrows(UnsupportedOperationException.class,()->model.getNamedStates().clear());
    }

    @Test void powerThresholdDisablesReductionAndRecoveredVoltagePermitsRampBack(){
        var model=new Wt12a1bModel(data());model.initialize(.4,0,.4,1);
        for(int i=0;i<30;i++){model.step(.01,.4,0,.2,0);model.step(.01,.4,0,.2,1);}assertEquals(.4,model.getOutput(),0);assertEquals(0,model.getRemainingTime(),0);
        model.initialize(.8,0,.8,1);for(int i=0;i<30;i++){model.step(.01,.8,0,.5,0);model.step(.01,.8,0,.5,1);}double low=model.getOutput();
        for(int i=0;i<200;i++){model.step(.01,.8,0,1,0);model.step(.01,.8,0,1,1);}assertTrue(model.getOutput()>low);assertEquals(0,model.getRemainingTime(),0);
        assertFalse(model.getNamedStates().isEmpty());
    }
}
