package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.controller.exc.ac1c.Ac1cExciter;
import com.interpss.dstab.controller.oel.oel3c.Oel3cData;
import com.interpss.dstab.controller.oel.oel3c.Oel3cModel;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** Exact importer, physical signal routing and auxiliary lifecycle checks. */
public class Oel3cModelTest extends CorePluginTestSetup {
    @TempDir Path directory;
    private static final String AC="1 'AC1C' '1' 1 0 0 .2 .05 10 .3 20 -20 .4 .2 .5 .2 .1 1.1 .75 0 1 0 20 -20 99 0 /\n";
    private static String record(int selector,double tf) {
        return "1 'OEL3C' '1' "+selector+" 1 .4 .3333 "+tf+" .1 .1 1 0 -1 0 -1 /\n";
    }
    private DStabNetworkBuilder parse(String text,boolean strict) throws Exception {
        var builder=DStabBuilderTestFixture.createWithMachine();
        Path file=directory.resolve("model-"+System.nanoTime()+".dyr");Files.writeString(file,text);
        new PSSEDStabDirectParser(builder).setStrictImport(strict).parseDynFile(file.toString());return builder;
    }
    private static Machine machine(DStabNetworkBuilder builder){return builder.getDStabNetwork().getMachine("Bus1-mach1");}
    private static Oel3cModel limiter(Machine machine){
        return (Oel3cModel)machine.getDStabBus().getDynamicBusDeviceList().stream()
            .filter(d->d instanceof Oel3cModel).findFirst().orElseThrow();
    }
    private static void initialize(Machine machine,Oel3cModel limiter){
        machine.setEfd(1.2);machine.setSpeed(1);
        assertTrue(limiter.initStates());assertFalse(limiter.isInitialized());
        assertTrue(machine.getExciter().initStates(machine.getDStabBus(),machine));assertTrue(limiter.isInitialized());
    }
    @ParameterizedTest @ValueSource(ints={1,3,17})
    void deferredImportAndPhysicalInitializationUseTheSelectedInput(int selector)throws Exception {
        var builder=parse(record(selector,.02)+AC,true);var m=machine(builder);var oel=limiter(m);initialize(m,oel);
        var exc=(Ac1cExciter)m.getExciter();
        double expected=selector==2?m.calculateIfd(MachineIfdBase.EXCITER):selector==3?exc.getFieldFeedback():m.getEfd();
        assertEquals(expected,oel.getSelectedInput(),1e-12);
        assertEquals(.3333*expected,oel.getPublishedStates().get("OEL Input"),1e-12);
        assertEquals(0,oel.getOutput(),0);assertEquals(1.2,exc.getOutput(m),1e-9);
        assertEquals(selector,oel.getData().inputFlag());
        assertEquals(0,oel.getLoadPQ().abs(),0);assertEquals(0,oel.getEquivY().abs(),0);
        assertThrows(UnsupportedOperationException.class,()->oel.getNamedStates().put("x",1.));
        assertThrows(UnsupportedOperationException.class,()->oel.getPublishedStates().put("x",1.));
        assertThrows(IllegalStateException.class,()->exc.setVoel(.1));
    }
    @Test void importerAlsoAcceptsExciterFirstAndRejectsDuplicateOwnership()throws Exception {
        var b=parse(AC+record(1,0),true);assertNotNull(limiter(machine(b)));
        assertThrows(Exception.class,()->parse(AC+record(1,0)+record(1,0),true));
    }
    @ParameterizedTest @ValueSource(strings={"1 1 .4 .3333 .02 .1 .1 1 0 -1 0", "1.5 1 .4 .3333 .02 .1 .1 1 0 -1 0 -1", "1 0 .4 .3333 .02 .1 .1 1 0 -1 0 -1", "1 1 .4 .3333 .02 .1 0 1 0 -1 0 -1", "1 1 .4 .3333 .02 .1 .1 1 0 -1 0 -1 9"})
    void malformedRecordsCannotBecomeLoadable(String parameters){
        assertThrows(Exception.class,()->parse(AC+"1 'OEL3C' '1' "+parameters+" /\n",true));
    }
    @Test void missingDisabledAndTakeoverHostsAreExplicitlyRejected(){
        assertThrows(Exception.class,()->parse(record(1,0),true));
        assertThrows(Exception.class,()->parse(AC.replace("'1' 1 0","'1' 0 0")+record(1,0),true));
        assertThrows(Exception.class,()->parse(AC.replace("'1' 1 0","'1' 2 0")+record(1,0),true));
    }
    @Test void algebraicRefreshAndFailedNetworkSolveDoNotAdvanceTheIntegrator()throws Exception {
        var b=parse(AC+record(1,0),true);var m=machine(b);var oel=limiter(m);initialize(m,oel);
        m.setEfd(3);
        assertTrue(oel.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));
        assertTrue(oel.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));
        var state=oel.getNamedStates();double initial=oel.getSelectedInput();double output=oel.getOutput();
        oel.beginNetworkSolution(null);m.setEfd(2);oel.beforeNetworkIteration(0);
        assertEquals(2,oel.getSelectedInput(),0);assertEquals(state,oel.getNamedStates());
        assertNotEquals(output,oel.getOutput());oel.endNetworkSolution(false);
        assertEquals(initial,oel.getSelectedInput(),0);assertEquals(output,oel.getOutput(),0);
        oel.beginNetworkSolution(null);oel.beforeNetworkIteration(0);oel.afterNetworkConvergence();oel.endNetworkSolution(true);
        assertEquals(2,oel.getSelectedInput(),0);assertEquals(state,oel.getNamedStates());
        var published=oel.getPublishedStates();m.setEfd(3);assertEquals(published,oel.getPublishedStates());
    }
    @Test void unavailablePhysicalFieldCurrentFailsRatherThanSubstitutingFieldVoltage()throws Exception {
        var b=parse(AC+record(2,.02),true);var m=machine(b);var oel=limiter(m);
        assertTrue(oel.initStates());m.setEfd(1.2);
        assertFalse(Double.isFinite(m.calculateIfd(MachineIfdBase.EXCITER)));
        assertFalse(m.getExciter().initStates(m.getDStabBus(),m));assertFalse(oel.isInitialized());
    }
    @Test void inactiveDeviceHoldsStateAndSuppressesItsSignalAndReinitializesCleanly()throws Exception {
        var b=parse(AC+record(1,.02),true);var m=machine(b);var oel=limiter(m);initialize(m,oel);
        var initial=oel.getNamedStates();m.setEfd(3);
        assertTrue(oel.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));assertNotEquals(initial,oel.getNamedStates());
        oel.setStatus(false);assertEquals(0,oel.getOutput(),0);
        assertTrue(oel.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));assertEquals(initial,oel.getNamedStates());
        oel.setStatus(true);assertEquals(0,oel.getOutput(),0);
        assertEquals(1.2,oel.getSelectedInput(),0);assertFalse(oel.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));
        initialize(m,oel);assertEquals(initial,oel.getNamedStates());
        m.setStatus(false);assertEquals(0,oel.getOutput(),0);
        assertTrue(oel.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));assertEquals(initial,oel.getNamedStates());
    }
    @Test void builderRejectsAnUnoccupiedButDisabledPortWithoutAttachingADevice()throws Exception {
        var b=parse(AC.replace("'1' 1 0","'1' 0 0"),true);var m=machine(b);
        assertNull(b.addOel3c("Bus1","1",new Oel3cData(1,1,.4,.3333,.02,.1,.1,1,0,-1,0,-1)));
        assertTrue(m.getDStabBus().getDynamicBusDeviceList().stream().noneMatch(d->d instanceof Oel3cModel));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void explicitGeneratorRemovalSkipsTheAuxiliaryWithoutAttaching(boolean gnet)throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();Path file=directory.resolve("removed.dyr");
        Files.writeString(file,record(1,0));var parser=new PSSEDStabDirectParser(b).setStrictImport(true);
        var removed=java.util.List.of(new org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey("Bus1","1"));
        if(gnet)parser.setGnetRemovedGenerators(removed);else parser.setModelRemovedGenerators(removed);
        parser.parseDynFile(file.toString());
        assertEquals(1,parser.getLastImportReport().count(gnet?
            org.interpss.fadapter.psse.dyr.DynamicModelImportStatus.SKIPPED_GNET:
            org.interpss.fadapter.psse.dyr.DynamicModelImportStatus.SKIPPED_MODEL_REMOVE));
        assertTrue(machine(b).getDStabBus().getDynamicBusDeviceList().stream().noneMatch(d->d instanceof Oel3cModel));
    }
    @Test void changedConnectionInvalidatesTheAttachedDevice()throws Exception {
        var b=parse(AC+record(1,0),true);var m=machine(b);var oel=limiter(m);initialize(m,oel);
        ((Ac1cExciter)m.getExciter()).getData().setOelLocation(Ac1cExciter.INPUT_TAKEOVER);
        assertEquals(0,oel.getOutput(),0);assertFalse(oel.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));
        assertFalse(oel.initStates());
    }
}
