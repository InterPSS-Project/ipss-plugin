package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Accepted samples have one owner and survive pure trial/observation work. */
public class Pss2bAcceptedPathHistoryTest {
    @BeforeAll static void setup() { IpssCorePlugin.init(); }

    @Test void primitiveHistoryValidatesBeforeMutationAndPreservesEventPublication() {
        var history=new Pss2bAcceptedPathHistory(); history.initialize(1.,2.);
        history.beginStep(.001); history.accept(.001,.001,3.,4.);
        assertEquals(1.,history.firstOutput()); assertEquals(2.,history.secondOutput());
        history.synchronize(5.,6.);
        assertEquals(1.,history.firstOutput()); assertEquals(5.,history.firstAccepted());
        history.beginStep(0.); assertEquals(1.,history.firstOutput());
        history.accept(.001,.001,7.,8.); assertEquals(5.,history.firstAccepted());
        history.accept(.002,0.,7.,8.); assertEquals(.001,history.acceptedTime());
        assertThrows(IllegalArgumentException.class,()->history.accept(.002,.001,7.,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->history.synchronize(7.,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->history.accept(0.,.001,7.,8.));
        assertEquals(5.,history.firstAccepted()); assertEquals(6.,history.secondAccepted());
        history.beginStep(.001); assertEquals(5.,history.firstOutput());
        history.accept(.002,.001,7.,8.);
        assertEquals(5.,history.firstOutput()); assertEquals(7.,history.firstAccepted());
        history.initialize(0.,0.); assertEquals(0.,history.acceptedTime()); assertEquals(0.,history.firstOutput());
    }

    @Test void eventsAndAcceptedEndpointsPublishThePrecedingBypassCoordinate() throws Exception {
        var model=model(); var bus=model.getMachine().getDStabBus();
        double[] rates=new double[model.staggeredStateCount()];
        bus.setVoltage(new Complex(.99999,0.));
        model.evaluateStaggered(.0005,rates,0);
        double instantaneous=model.getInstantaneousOutput(model.getMachine());
        assertTrue(Math.abs(instantaneous)>1e-6);
        assertEquals(0.,model.getOutput(model.getMachine()),1e-12);
        model.synchronizeVoltageDerivativeMemory();
        assertEquals(0.,model.getOutput(model.getMachine()),1e-12,"Event does not republish the output sample");
        model.completeStaggeredStep(.0005);
        assertEquals(instantaneous,model.getOutput(model.getMachine()),1e-12);
        model.acceptVoltageDerivativeEndpoint(.0005,.0005);
        assertEquals(instantaneous,model.getOutput(model.getMachine()),1e-12);
        bus.setVoltage(new Complex(.99998,0.));
        model.acceptVoltageDerivativeEndpoint(.001,.0005);
        assertEquals(instantaneous,model.getOutput(model.getMachine()),1e-12);
        var accepted=history(model);
        model.acceptVoltageDerivativeEndpoint(.001,.0005);
        assertThrows(IllegalArgumentException.class,()->model.acceptVoltageDerivativeEndpoint(Double.NaN,.0005));
        assertEquals(accepted,history(model));
        model.setRefPoint(1e-6);
        assertNotEquals(instantaneous,model.getOutput(model.getMachine()),"Current algebraic setpoints remain live");
        assertEquals(accepted,history(model));
    }

    @Test void trialStateRestorationAndObservationsCannotAdvanceAcceptedPaths() throws Exception {
        var model=model();var bus=model.getMachine().getDStabBus();
        bus.setVoltage(new Complex(.99999,0.));model.acceptVoltageDerivativeEndpoint(.0005,.0005);
        var accepted=history(model);int count=model.staggeredStateCount();
        var original=new double[count];var trial=new double[count];var rates=new double[count];
        model.readStaggeredState(original,0);System.arraycopy(original,0,trial,0,count);trial[0]+=1e-6;
        for(int i=0;i<4;i++){
            model.applyStaggeredState(trial,0,.0005);model.evaluateStaggered(.0005,rates,0);
            model.getOutput(model.getMachine());model.getInstantaneousOutput(model.getMachine());
            model.applyStaggeredState(original,0,.0005);
            assertEquals(accepted,history(model));
        }
        model.completeStaggeredStep(0.); assertEquals(accepted,history(model));
        model.invalidateRuntimePlan();
        assertThrows(IllegalStateException.class,()->model.getOutput(model.getMachine()),
                "Sampled observations must preserve the shared graph's invalidation guard");
        assertTrue(model.initStates(bus,model.getMachine()));assertEquals(0.,model.getNamedState("inputPathAcceptedTime"));
    }

    @Test void genericAndCompiledEulerShareTheAcceptedPublicationContract() throws Exception {
        var generic=model();var compiled=model();compiled.compileRuntimePlan();assertTrue(compiled.hasCompiledStageEvaluator());
        double step=.0005;
        for(int tick=0;tick<80;tick++){
            for(int flag=0;flag<2;flag++){
                double voltage=1.+1e-5*Math.sin((tick+.5*flag)*.17);
                for(var model:new Ieee1992PSS2BStabilizer[]{generic,compiled}){
                    model.getMachine().getDStabBus().setVoltage(new Complex(voltage,0.));
                    assertTrue(model.nextStep(step,DynamicSimuMethod.MODIFIED_EULER,model.getMachine(),flag));
                }
                assertEquals(generic.getNamedStates(),compiled.getNamedStates());
                assertEquals(generic.getOutput(generic.getMachine()),compiled.getOutput(compiled.getMachine()),1e-14);
            }
            generic.acceptVoltageDerivativeEndpoint((tick+1)*step,step);
            compiled.acceptVoltageDerivativeEndpoint((tick+1)*step,step);
            assertEquals(generic.getNamedStates(),compiled.getNamedStates());
            assertEquals(generic.getOutput(generic.getMachine()),compiled.getOutput(compiled.getMachine()),1e-14);
        }
    }

    @Test void pendingPredictorCannotCommitAnEndpointOrEvent() throws Exception {
        var model=model();double step=.0005;
        var before=history(model);
        assertTrue(model.nextStep(step,DynamicSimuMethod.MODIFIED_EULER,model.getMachine(),0));
        assertThrows(IllegalStateException.class,()->model.acceptVoltageDerivativeEndpoint(step,step));
        assertThrows(IllegalStateException.class,model::synchronizeVoltageDerivativeMemory);
        assertEquals(before,history(model));
        assertTrue(model.nextStep(step,DynamicSimuMethod.MODIFIED_EULER,model.getMachine(),1));
        model.acceptVoltageDerivativeEndpoint(step,step);
        assertEquals(step,model.getNamedState("inputPathAcceptedTime"));
    }

    @Test void warmedAcceptedSamplingDoesNotAllocatePerStep() throws Exception {
        var model=model();int count=model.staggeredStateCount();var states=new double[count];var rates=new double[count];
        for(int i=1;i<=20000;i++)cycle(model,states,rates,i*.0005);
        var bean=java.lang.management.ManagementFactory.getThreadMXBean();
        if(bean instanceof com.sun.management.ThreadMXBean allocation && allocation.isThreadAllocatedMemorySupported()){
            allocation.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();
            long before=allocation.getThreadAllocatedBytes(thread);
            for(int i=20001;i<=120000;i++)cycle(model,states,rates,i*.0005);
            long bytes=allocation.getThreadAllocatedBytes(thread)-before;
            System.out.println("PSS2B accepted sampling allocation bytes="+bytes);
            assertTrue(bytes<4096,"Accepted sampling must reuse its storage");
        }
    }

    private static void cycle(Ieee1992PSS2BStabilizer model,double[] states,double[] rates,double time){
        model.readStaggeredState(states,0);model.evaluateStaggered(.0005,rates,0);
        model.applyStaggeredState(states,0,.0005);model.completeStaggeredStep(.0005);
        model.acceptVoltageDerivativeEndpoint(time,.0005);model.getOutput(model.getMachine());
    }

    private static Ieee1992PSS2BStabilizer model() throws Exception {
        var model=Pss2bCompiledStageTest.model(true,5,1);model.getData().setIcs2(5);
        model.getData().setSampledInputPathOutputs(true);model.getMachine().getDStabBus().setVoltage(Complex.ONE);
        assertTrue(model.initStates(model.getMachine().getDStabBus(),model.getMachine()));
        model.invalidateRuntimePlan();return model;
    }

    private static Map<String,Double> history(Ieee1992PSS2BStabilizer model){
        return Map.of("first",model.getNamedState("input1AcceptedPath"),"second",model.getNamedState("input2AcceptedPath"),
                "previousFirst",model.getNamedState("input1PreviousPath"),"previousSecond",model.getNamedState("input2PreviousPath"),
                "time",model.getNamedState("inputPathAcceptedTime"));
    }
}
