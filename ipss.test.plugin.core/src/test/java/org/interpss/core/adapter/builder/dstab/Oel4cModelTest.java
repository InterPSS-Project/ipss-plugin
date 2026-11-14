package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.exc.ac1c.Ac1cExciter;
import org.interpss.dstab.control.oel.psse.oel4c.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real core-machine measurement/base and lifecycle tests; independent coupled acceptance is separate. */
public class Oel4cModelTest {
    @TempDir Path directory;
    private record Host(DStabNetworkBuilder builder,DynamicSimuAlgorithm algorithm,Machine machine) { }
    private Host host(int location,double mva)throws Exception {
        org.interpss.IpssCorePlugin.init();
        Path raw=directory.resolve("case-"+System.nanoTime()+".raw"),dyr=directory.resolve("case-"+System.nanoTime()+".dyr");
        String text=Files.readString(Path.of("testData/adpter/psse/v33/SMIB/SMIB_v33.raw"));
        String line=text.lines().filter(s->s.startsWith("1,'1',")).findFirst().orElseThrow();
        String[] fields=line.split(",");fields[8]=Double.toString(mva);text=text.replace(line,String.join(",",fields));Files.writeString(raw,text);
        Files.writeString(dyr,"1 'GENROU' '1' 6 .033 .54 .078 6.4 0 .8958 .8645 .1189 .1969 .089 .0521 0 0 /\n"
            +"2 'GENCLS' '1' 99999 0 /\n1 'AC1C' '1' "+location+" 0 0 .2 .05 10 .3 20 -20 .4 .2 .5 .2 .1 1.1 .75 0 1 0 20 -20 99 0 /\n");
        var context=new PSSEMultiFileLoader().loadDStab(raw.toString(),dyr.toString());var alg=context.getDynSimuAlgorithm();
        alg.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(alg.getAclfAlgorithm().loadflow());
        return new Host(new DStabNetworkBuilder(context.getDStabilityNet()),alg,context.getDStabilityNet().getMachine("Bus1-mach1"));
    }
    private static Oel4cData data(){return new Oel4cData(.35,0,.15,.25,-.25);}
    private static Oel4cModel attach(Host h,Oel4cData data){return h.builder().addOel4c("Bus1","1",data);}
    private static double physicalQ(Machine m){
        var v=m.getDStabBus().getVoltage();return v.multiply(m.getIgen().subtract(v.multiply(m.getYgen())).conjugate()).getImaginary()
            *m.getDStabBus().getNetwork().getBaseMva()/m.getParentGen().getMvaBase();
    }
    @ParameterizedTest @ValueSource(doubles={50,100,200})
    void deferredInitializationUsesOwnMachineBaseAndConvertsDefaultQmax(double mva)throws Exception {
        var h=host(1,mva);var o=attach(h,new Oel4cData(0,.01,.15,.25,-.25));assertNotNull(o);assertFalse(o.isInitialized());
        assertTrue(h.algorithm().initialization());assertTrue(o.isInitialized());
        assertEquals(100/mva,o.getEffectivePickup(),1e-12);
        assertEquals(physicalQ(h.machine()),o.getSampledReactivePower(),1e-10);assertEquals(0,o.getOutput());
        assertEquals(0,o.getLoadPQ().abs());assertEquals(0,o.getEquivY().abs());assertEquals(0,o.getNortonCurInj().abs());
        assertThrows(UnsupportedOperationException.class,()->o.getNamedStates().clear());
    }
    @ParameterizedTest @ValueSource(ints={0,2})
    void disabledAndTakeoverConnectionsAreRejected(int location)throws Exception {
        var h=host(location,100);assertNull(attach(h,data()));
        assertTrue(h.machine().getDStabBus().getDynamicBusDeviceList().stream().noneMatch(Oel4cModel.class::isInstance));
    }
    @Test void portOwnershipIsExclusiveWithoutReplacingTheExciter()throws Exception {
        var h=host(1,100);var exc=h.machine().getExciter();assertNotNull(attach(h,data()));assertNull(attach(h,data()));
        assertNull(h.builder().addOel3c("Bus1","1",new org.interpss.dstab.control.oel.psse.oel3c.Oel3cData(1,1,4,1,0,.1,.1,1,0,-1,0,-1)));
        assertSame(exc,h.machine().getExciter());assertThrows(IllegalStateException.class,()->((Ac1cExciter)exc).setVoel(.1));
    }
    @Test void explicitPickupDoesNotDependOnAnUnavailableStaticReactiveLimit()throws Exception {
        var h=host(1,100);h.machine().getParentGen().setQGenLimit(null);var o=attach(h,data());
        assertTrue(h.algorithm().initialization());assertEquals(.35,o.getEffectivePickup());
    }
    @Test void zeroPickupRejectsAnUnavailableStaticReactiveLimit()throws Exception {
        var h=host(1,100);h.machine().getParentGen().setQGenLimit(null);var o=attach(h,new Oel4cData(0,.01,.15,.25,-.25));
        assertFalse(h.algorithm().initialization());assertFalse(o.isInitialized());
    }
    @Test void samplingDoesNotUseTheMachineCachedReactiveAverage()throws Exception {
        var h=host(1,100);var o=attach(h,data());assertTrue(h.algorithm().initialization());double expected=physicalQ(h.machine());
        h.machine().setQGen(123);o.beginNetworkSolution(null);o.beforeNetworkIteration(0);o.endNetworkSolution(true);
        assertEquals(expected,o.getSampledReactivePower(),1e-12);assertNotEquals(123,o.getSampledReactivePower());
    }
    @Test void algebraicIterationsArePureAndFailedSolvesRestoreInputs()throws Exception {
        var h=host(1,100);var o=attach(h,data());assertTrue(h.algorithm().initialization());
        var state=o.getNamedStates();double q=o.getSampledReactivePower(),timer=o.getSignals().elapsed();
        o.beginNetworkSolution(null);var bus=h.machine().getDStabBus();bus.setVoltage(bus.getVoltage().multiply(.8));
        o.beforeNetworkIteration(0);assertEquals(physicalQ(h.machine()),o.getSampledReactivePower(),1e-12);
        assertNotEquals(q,o.getSampledReactivePower());assertEquals(state,o.getNamedStates());assertEquals(timer,o.getSignals().elapsed());
        o.endNetworkSolution(false);assertEquals(q,o.getSampledReactivePower());assertEquals(state,o.getNamedStates());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void disablingAProvisionalPredictorOrCorrectorRollsBackTheWholeStep(boolean corrector)throws Exception {
        var h=host(1,100);var o=attach(h,data());assertTrue(h.algorithm().initialization());
        var state=o.getNamedStates();double q=o.getSampledReactivePower();
        var bus=h.machine().getDStabBus();bus.setVoltage(bus.getVoltage().multiply(.9));
        assertTrue(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));
        if(corrector)assertTrue(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));
        assertNotEquals(state,o.getNamedStates());o.setStatus(false);assertTrue(o.afterStep(.001));
        assertEquals(state,o.getNamedStates());assertEquals(0,o.getSignals().elapsed());assertEquals(q,o.getSampledReactivePower());
        assertEquals(0,o.getOutput());o.setStatus(true);assertFalse(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));
    }
    @Test void rejectedPostCorrectorNetworkSolutionRestoresTheAcceptedHistory()throws Exception {
        var h=host(1,100);var o=attach(h,data());assertTrue(h.algorithm().initialization());var state=o.getNamedStates();
        var bus=h.machine().getDStabBus();bus.setVoltage(bus.getVoltage().multiply(.9));
        assertTrue(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));assertTrue(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));
        o.beginNetworkSolution(null);o.beforeNetworkIteration(0);o.endNetworkSolution(false);
        assertEquals(state,o.getNamedStates());assertEquals(0,o.getSignals().elapsed());
    }
    @Test void stageAcceptanceIsOnceOnlyAndRequiresTheMatchingCorrector()throws Exception {
        var h=host(1,100);var o=attach(h,data());assertTrue(h.algorithm().initialization());
        assertFalse(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));assertTrue(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));
        assertFalse(o.afterStep(.001));assertFalse(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));
        assertFalse(o.nextStep(.002,DynamicSimuMethod.MODIFIED_EULER,1));assertTrue(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,1));
        assertFalse(o.afterStep(.002));assertTrue(o.afterStep(0));assertTrue(o.afterStep(.001));
        var state=o.getNamedStates();assertTrue(o.afterStep(.001));assertEquals(state,o.getNamedStates());
    }
    @Test void changedHostConnectionInvalidatesTheRuntime()throws Exception {
        var h=host(1,100);var o=attach(h,data());assertTrue(h.algorithm().initialization());
        ((Ac1cExciter)h.machine().getExciter()).getData().setOelLocation(2);
        assertEquals(0,o.getOutput());assertFalse(o.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,0));assertFalse(o.initStates());
    }
}
