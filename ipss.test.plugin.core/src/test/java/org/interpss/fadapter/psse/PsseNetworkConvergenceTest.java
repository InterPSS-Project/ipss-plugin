package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.defaultImpl.DStabSolverImpl;

/** End-to-end dependency guard: a rebuilt core must propagate network failures. */
public class PsseNetworkConvergenceTest {
    @Test void everyFailedStageStopsThePublicAlgorithmStep() {
        for (int failedStage=1;failedStage<=3;failedStage++) {
            var algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(
                    DStabObjectFactory.createDStabilityNetwork());
            algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            algorithm.setSimuStepSec(.01);
            int failAt=failedStage;
            AtomicInteger calls=new AtomicInteger();
            var solver=new DStabSolverImpl(algorithm) {
                @Override public boolean networkSolutionStep() { return calls.incrementAndGet()!=failAt; }
                @Override public void diffEqnIntegrationStep(double t,double h,DynamicSimuMethod method,int flag) {}
            };
            algorithm.setSolver(solver);
            assertFalse(algorithm.solveDEqnStep(true),"Core must propagate network failure; rebuild the required core revision");
            assertEquals(failedStage,calls.get());
            assertEquals(0,algorithm.getSimuTime(),0);
            assertFalse(algorithm.solveDEqnStep(true),"Do not retry partially advanced state");
            assertEquals(failedStage,calls.get());
        }
    }

    @Test void realIterationExhaustionStopsBeforeIntegrationAndClockAdvance() {
        assertIterationExhaustionStops(false);
        assertIterationExhaustionStops(true);
    }

    @Test void anExplicitHigherBudgetStillFailsAtItsLimitWithoutAdvancingTime() {
        assertIterationExhaustionStops(true,200);
    }

    private void assertIterationExhaustionStops(boolean importedSolver) {
        assertIterationExhaustionStops(importedSolver,importedSolver?50:3);
    }
    private void assertIterationExhaustionStops(boolean importedSolver,int budget) {
        var physical=DStabObjectFactory.createDStabilityNetwork();
        var bus=DStabObjectFactory.createDStabBus("UnsettledBus",physical).orElseThrow();
        AtomicInteger calls=new AtomicInteger();
        var network=(DStabilityNetwork)Proxy.newProxyInstance(DStabilityNetwork.class.getClassLoader(),
                new Class<?>[]{DStabilityNetwork.class},(proxy,method,args)->{
                    if(method.getName().equals("solveNetEqn")) {
                        bus.setVoltage(new Complex(calls.incrementAndGet()%2==0?1.1:.9));
                        return true;
                    }
                    return method.invoke(physical,args);
                });
        var algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(network);
        algorithm.setDisableDynamicEvent(true);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.01);
        var solver=importedSolver?new PsseDStabSolver(algorithm):new DStabSolverImpl(algorithm);
        solver.setMaxIterationTimes(budget);
        algorithm.setSolver(solver);
        assertFalse(algorithm.solveDEqnStep(true));
        assertEquals(budget,calls.get(),"Exhaustion must stop at the first network stage");
        assertEquals(0,algorithm.getSimuTime(),0);
        assertFalse(network.isDStabNetInitialized());
    }

    @Test void importedSolverDefaultsUseFiftyIterations() {
        var algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(
                DStabObjectFactory.createDStabilityNetwork());
        var solver=new PsseDStabSolver(algorithm);
        assertEquals(50,solver.getMaxIterationTimes());
        assertEquals(50,new org.interpss.dstab.algo.PsseStaggeredEulerSolver(algorithm).getMaxIterationTimes());
        solver.setMaxIterationTimes(37);
        assertEquals(37,solver.getMaxIterationTimes());
        for(var bounded:new PsseDStabSolver[]{solver,new org.interpss.dstab.algo.PsseStaggeredEulerSolver(algorithm)}) {
            for(int invalid:new int[]{0,-1})
                assertThrows(IllegalArgumentException.class,()->bounded.setMaxIterationTimes(invalid));
            for(int requested:new int[]{37,50,100,200,500}) {
                bounded.setMaxIterationTimes(requested);
                assertEquals(requested,bounded.getMaxIterationTimes());
            }
        }
    }
}
