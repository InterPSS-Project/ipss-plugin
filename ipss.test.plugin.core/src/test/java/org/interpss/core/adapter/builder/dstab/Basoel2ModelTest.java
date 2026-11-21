package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.controller.exc.ac1c.Ac1cExciter;
import com.interpss.dstab.controller.oel.basoel2.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Explicit typed host connection, not automatic native-dialect acceptance. */
public class Basoel2ModelTest extends CorePluginTestSetup {
    @TempDir Path directory;
    private static final String AC="1 'AC1C' '1' 1 0 0 .2 .05 10 .3 20 -20 .4 .2 .5 .2 .1 1.1 .75 0 1 0 20 -20 99 0 /\n";
    private DStabNetworkBuilder host(int location)throws Exception{
        var b=DStabBuilderTestFixture.createWithMachine();var file=directory.resolve("host-"+System.nanoTime()+".dyr");
        Files.writeString(file,AC.replace("'1' 1 0","'1' "+location+" 0"));
        new PSSEDStabDirectParser(b).setStrictImport(true).parseDynFile(file.toString());return b;
    }
    private static Machine machine(DStabNetworkBuilder b){return b.getDStabNetwork().getMachine("Bus1-mach1");}
    private static Basoel2Data data(int selector,double tr){return new Basoel2Data(1,1,selector,tr,.2,.4,3,.02,2,.02,1.5,.02,0,1.05,.1);}
    private static Basoel2Model attach(DStabNetworkBuilder b,int selector,double tr){
        return b.addBasoel2("Bus1","1",data(selector,tr),Basoel2Kernel.TimerSemantics.POWERWORLD_DOCUMENTED);
    }
    private static void initialize(Machine m,Basoel2Model limiter){
        m.setEfd(1.2);m.setSpeed(1);assertTrue(limiter.initStates());assertFalse(limiter.isInitialized());
        assertTrue(m.getExciter().initStates(m.getDStabBus(),m));assertTrue(limiter.isInitialized());
    }
    @ParameterizedTest @ValueSource(ints={0,2})
    void deferredInitializationSamplesPhysicalSignalsAndExposesImmutableState(int selector)throws Exception{
        var b=host(1);var m=machine(b);var o=attach(b,selector,.02);assertNotNull(o);initialize(m,o);
        var exc=(Ac1cExciter)m.getExciter();
        assertEquals(selector==0?m.getEfd():exc.getFieldFeedback(),o.getSampledInput().selectedInput(),1e-12);
        assertEquals(m.getDStabBus().getFreq(),o.getSampledInput().frequency());
        assertTrue(o.getSampledInput().voltageReference()>1);assertEquals(exc.reference,o.getSampledInput().voltageReference(),1e-12);
        assertEquals(0,o.getOutput());assertEquals(1.2,exc.getOutput(m),1e-9);
        assertEquals(0,o.getLoadPQ().abs());assertEquals(0,o.getEquivY().abs());assertEquals(0,o.getNortonCurInj().abs());
        assertThrows(UnsupportedOperationException.class,()->o.getNamedStates().clear());
        assertThrows(UnsupportedOperationException.class,()->o.getPublishedStates().clear());
        assertThrows(IllegalStateException.class,()->exc.setVoel(.1));
    }
    @Test void unavailableFieldCurrentIsRejectedWithoutFieldVoltageFallback()throws Exception{
        var b=host(1);var m=machine(b);var o=attach(b,1,.02);assertTrue(o.initStates());m.setEfd(1.2);
        assertFalse(m.getExciter().initStates(m.getDStabBus(),m));assertFalse(o.isInitialized());
    }
    @ParameterizedTest @ValueSource(ints={0,2})
    void disabledAndTakeoverPortsDoNotReceiveTypedDevices(int location)throws Exception{
        var b=host(location);assertNull(attach(b,0,.02));
        assertTrue(machine(b).getDStabBus().getDynamicBusDeviceList().stream().noneMatch(Basoel2Model.class::isInstance));
    }
    @Test void explicitDialectAndExclusiveOwnershipAreRequired()throws Exception{
        var b=host(1);var m=machine(b);
        assertThrows(NullPointerException.class,()->b.addBasoel2("Bus1","1",data(0,0),null));
        assertNotNull(attach(b,0,0));assertNull(attach(b,0,0));
        assertNull(b.addOel3c("Bus1","1",new com.interpss.dstab.controller.oel.oel3c.Oel3cData(1,1,4,1,0,.1,.1,1,0,-1,0,-1)));
        assertEquals(1,m.getDStabBus().getDynamicBusDeviceList().stream().filter(Basoel2Model.class::isInstance).count());
    }
    @Test void timerMemoryAdvancesOnceOnlyAfterTheAcceptedContinuousStep()throws Exception{
        var b=host(1);var m=machine(b);var o=attach(b,0,0);initialize(m,o);m.setEfd(4);
        assertTrue(o.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0));assertFalse(o.afterStep(.01));
        assertTrue(o.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,1));assertTrue(o.afterStep(0));
        assertEquals(0,o.getTimers().get(0).elapsed());assertTrue(o.afterStep(.01));
        assertEquals(.01,o.getTimers().get(0).elapsed());assertTrue(o.afterStep(.01));
        assertEquals(.01,o.getTimers().get(0).elapsed());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void disablingAnUnacceptedPredictorOrCorrectorRestoresAllHistory(boolean correct)throws Exception{
        var b=host(1);var m=machine(b);var o=attach(b,0,.02);initialize(m,o);
        var state=o.getNamedStates();var timers=o.getTimers();var sampled=o.getSampledInput();m.setEfd(4);
        assertTrue(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));
        if(correct)assertTrue(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));
        o.setStatus(false);assertTrue(o.afterStep(.001));assertEquals(0,o.getOutput());
        assertEquals(state,o.getNamedStates());assertEquals(timers,o.getTimers());assertEquals(sampled,o.getSampledInput());
        o.setStatus(true);assertFalse(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));
    }
    @Test void networkIterationsRefreshAlgebraicBoundariesWithoutAdvancingTimers()throws Exception{
        var b=host(1);var m=machine(b);var o=attach(b,0,0);initialize(m,o);
        var state=o.getNamedStates();var timers=o.getTimers();var initial=o.getSampledInput();
        o.beginNetworkSolution(null);m.setEfd(4);o.beforeNetworkIteration(0);
        assertEquals(4,o.getSampledInput().selectedInput());assertEquals(state,o.getNamedStates());assertEquals(timers,o.getTimers());
        o.endNetworkSolution(false);assertEquals(initial,o.getSampledInput());
        o.beginNetworkSolution(null);o.beforeNetworkIteration(0);o.afterNetworkConvergence();o.endNetworkSolution(true);
        assertEquals(4,o.getSampledInput().selectedInput());assertEquals(state,o.getNamedStates());
    }
    @Test void ownerConnectionChangesInvalidateTheRuntime()throws Exception{
        var b=host(1);var m=machine(b);var o=attach(b,0,0);initialize(m,o);
        ((Ac1cExciter)m.getExciter()).getData().setOelLocation(2);
        assertEquals(0,o.getOutput());assertFalse(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));assertFalse(o.initStates());
    }
    @Test void frequencyIsReadFromTheTerminalBusNotMachineShaftSpeed()throws Exception{
        var b=host(1);var m=machine(b);var o=attach(b,0,0);initialize(m,o);m.setSpeed(1.2);
        o.beginNetworkSolution(null);o.beforeNetworkIteration(0);o.endNetworkSolution(true);
        assertEquals(m.getDStabBus().getFreq(),o.getSampledInput().frequency());
        assertNotEquals(m.getSpeed(),o.getSampledInput().frequency());
    }
    @Test void anInitiallyActiveVhzSignalIsIncludedInExciterReferenceInitialization()throws Exception{
        var b=host(1);var m=machine(b);var p=new Basoel2Data(0,1,0,0,.2,.4,3,.02,2,.02,1.5,.02,0,.9,.1);
        var o=b.addBasoel2("Bus1","1",p,Basoel2Kernel.TimerSemantics.POWERWORLD_DOCUMENTED);initialize(m,o);
        double expected=Math.min(0,Math.max(.9*m.getDStabBus().getFreq(),.1)-m.getDStabBus().getVoltageMag());
        assertEquals(expected,o.getOutput(),1e-12);assertTrue(expected<0);
        assertEquals(1.2,m.getExciter().getOutput(m),1e-9);
        assertTrue(((Ac1cExciter)m.getExciter()).reference>o.getSampledInput().voltageReference());
    }
    @Test void recordImportDeclaresItsDocumentedPolicyWithoutClaimingReferenceParity()throws Exception{
        var b=host(1);var file=directory.resolve("unresolved.dyr");
        Files.writeString(file,"1 'BASOEL2' '1' 1 0 1 .02 .2 .4 3 .02 2 .02 1.5 .02 .02 1.05 .1 /\n");
        var parser=new PSSEDStabDirectParser(b).setStrictImport(true);parser.parseDynFile(file.toString());
        var o=(Basoel2Model)machine(b).getDStabBus().getDynamicBusDeviceList().stream().filter(Basoel2Model.class::isInstance).findFirst().orElseThrow();
        assertEquals(Basoel2Kernel.TimerSemantics.POWERWORLD_DOCUMENTED,o.getTimerSemantics());
        String detail=parser.getLastImportReport().entries().getFirst().message();
        assertTrue(detail.contains("POWERWORLD_DOCUMENTED"));assertTrue(detail.contains("acceptance remains open"));
    }
}
