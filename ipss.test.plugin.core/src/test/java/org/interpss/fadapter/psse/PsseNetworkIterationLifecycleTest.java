package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import com.interpss.dstab.controller.runtime.IntegrationStepAware;
import org.interpss.dstab.renewable.Regca1Model;
import org.junit.jupiter.api.Test;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.NetworkIterationAware;

/** Shared-core lifecycle integration, not an implementation of REGCA1 HV management. */
public class PsseNetworkIterationLifecycleTest {
    @Test void optInGeneratorGetsCallbacksWithoutChangingLoadedFlatOrFaultTrajectories() throws Exception {
        for (boolean fault : new boolean[]{false,true}) {
            var baseline=fixture(false,fault);
            var observed=fixture(true,fault);
            assertEquals(run(baseline),run(observed),
                    "A no-op lifecycle must preserve every captured endpoint value");
            var probe=observed.probe();
            assertEquals(500*3,probe.begins);
            assertEquals(probe.begins,probe.accepts);
            assertEquals(probe.accepts,probe.finalOutputs);
            assertEquals(0,probe.aborts);
            assertTrue(probe.iterations>=probe.begins*2);
            assertEquals(List.of("INITIAL","PREDICTOR","CORRECTOR"),probe.stages.subList(0,3));
        }
    }

    @Test void callbackFailureStopsPublicStepWithoutPublishingAnEndpoint() throws Exception {
        var fixture=fixture(true,false);
        var algorithm=fixture.algorithm();
        int initialSamples=fixture.monitor().getBusVoltTable().get("Bus1").size();
        fixture.probe().failStage="PREDICTOR";
        assertFalse(algorithm.solveDEqnStep(true));
        assertEquals(0,algorithm.getSimuTime(),0);
        assertFalse(algorithm.getNetwork().isLfConverged());
        assertFalse(algorithm.getNetwork().isDStabNetInitialized());
        assertEquals(initialSamples,fixture.monitor().getBusVoltTable().get("Bus1").size());
        assertEquals(1,fixture.probe().accepts);
        assertEquals(1,fixture.probe().finalOutputs);
        assertEquals(1,fixture.probe().aborts);
        int begins=fixture.probe().begins;
        assertFalse(algorithm.solveDEqnStep(true));
        assertEquals(begins,fixture.probe().begins,"A failed partial step cannot be retried");
    }

    private static Fixture fixture(boolean observe,boolean fault) throws Exception {
        IpssCorePlugin.init();
        String root="testData/adpter/psse/v33/renewable/";
        var context=new PSSEMultiFileLoader().loadDStab(root+"regca_zero_q_lossless.raw",
                root+"regca_iolim_native.dyr");
        var network=context.getDStabilityNet();
        var algorithm=context.getDynSimuAlgorithm();
        network.setBypassDataCheck(true);network.setAllowGenWithoutMach(true);
        algorithm.getAclfAlgorithm().setTolerance(1e-10);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var generator=(DStabGen)network.getBus("Bus1").getContributeGen("1");
        var model=(Regca1Model)generator.getDynamicGenDevice();
        var probe=new Probe();
        if(observe) {
            var proxy=(DynamicGenDevice)Proxy.newProxyInstance(DynamicGenDevice.class.getClassLoader(),
                    new Class<?>[]{DynamicGenDevice.class,IntegrationStepAware.class,
                            ICMLStateProvider.class,NetworkIterationAware.class},(object,method,args)->{
                        if(method.getDeclaringClass()==NetworkIterationAware.class) {
                            switch(method.getName()) {
                                case "beginNetworkSolution" -> probe.begin((NetworkIterationAware.Context)args[0]);
                                case "beforeNetworkIteration" -> probe.before((int)args[0]);
                                case "afterNetworkConvergence" -> probe.finalOutputs++;
                                case "endNetworkSolution" -> probe.end((boolean)args[0]);
                                default -> throw new AssertionError(method.getName());
                            }
                            return null;
                        }
                        try{return method.invoke(model,args);}
                        catch(InvocationTargetException exception){throw exception.getCause();}
                    });
            generator.setDynamicGenDevice(proxy);
        }
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.00025);algorithm.setTotalSimuTimeSec(.125);
        algorithm.setOutPutPerSteps(1);
        var monitor=new StateMonitor();
        monitor.addBusStdMonitor(new String[]{"Bus1","Bus2","Bus3"});
        algorithm.setSimuOutputHandler(monitor);
        if(fault) network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",network,
                SimpleFaultCode.GROUND_3P,new Complex(0,.01),null,.05,.05),"LifecycleFault");
        assertTrue(algorithm.initialization());
        return new Fixture(algorithm,model,monitor,probe);
    }

    private static List<Double> run(Fixture fixture) {
        var values=new ArrayList<Double>();
        var algorithm=fixture.algorithm();
        for(int i=0;i<500;i++) {
            assertTrue(algorithm.solveDEqnStep(true));
            var model=fixture.model();
            var before=model.getNamedStates();
            model.getOutputObject();model.getOutputObject();
            assertEquals(before,model.getNamedStates(),"Extra output reads must remain pure");
            for(String bus:List.of("Bus1","Bus2","Bus3")) {
                Complex v=algorithm.getNetwork().getBus(bus).getVoltage();
                values.add(v.getReal());values.add(v.getImaginary());
            }
            values.add(model.getIpRegulatorState());values.add(model.getIqRegulatorState());
            values.add(model.getFilteredVoltage());
            values.add(algorithm.getNetwork().getDStabBus("Bus3").getMachine().getSpeed());
        }
        assertEquals(.125,algorithm.getSimuTime(),1e-12);
        return values;
    }

    private record Fixture(DynamicSimuAlgorithm algorithm,Regca1Model model,StateMonitor monitor,Probe probe) {}
    private static final class Probe {
        final List<String> stages=new ArrayList<>();
        NetworkIterationAware.Context context;
        int begins,iterations,accepts,aborts,nextIteration,finalOutputs;
        String failStage;
        void begin(NetworkIterationAware.Context context) {
            this.context=context;stages.add(context.stage());begins++;nextIteration=0;
        }
        void before(int iteration) throws DStabSimuException {
            assertEquals(nextIteration++,iteration);iterations++;
            if(context.stage().equals(failStage)) throw new DStabSimuException("Injected callback failure");
        }
        void end(boolean converged){if(converged) accepts++;else aborts++;}
    }
}
