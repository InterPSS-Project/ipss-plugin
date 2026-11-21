package com.interpss.dstab.algo.dynamic;

import org.interpss.fadapter.psse.PsseDStabSolver;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Hashtable;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabObjectFactory;

/** Ownership is per unchanged initialized entry, never inferred from current size. */
class PassiveJunctionCompensationOwnershipTest {
    @Test void unchangedResidualQualifiesButCallerReplacementOrMutationDoesNot() throws Exception {
        var net=com.interpss.simu.util.sample.SampleDStabTestingCases.sample3BusDStab_StaticLoads();
        assertTrue(com.interpss.core.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net).loadflow());
        // A small saved-point rounding residual is precisely what the initializer owns.
        net.getDStabBus("Bus-2").setVoltageMag(net.getDStabBus("Bus-2").getVoltageMag()+1e-7);
        var algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(net);
        algorithm.setSimuStepSec(.001);
        var monitor=new com.interpss.dstab.cache.StateMonitor();monitor.prepareMonitoring();algorithm.setSimuOutputHandler(monitor);
        var solver=new PsseDStabSolver(algorithm);algorithm.setSolver(solver);
        assertTrue(algorithm.initialization());
        var owned=net.getCustomBusCurrInjHashtable();
        assertNotNull(owned);assertTrue(owned.containsKey("Bus-2"));
        var initial=owned.get("Bus-2");
        assertTrue(solver.isInitializationCompensationCurrentSource(owned,"Bus-2"));
        owned.put("Bus-2",initial.add(new Complex(1e-12,0.)));
        assertFalse(solver.isInitializationCompensationCurrentSource(owned,"Bus-2"),"Even tiny caller edits lose numerical-residual ownership");
        owned.put("Bus-2",initial);
        assertTrue(solver.isInitializationCompensationCurrentSource(owned,"Bus-2"));
        owned.put("caller-entry",new Complex(1e-15,0.));
        assertFalse(solver.isInitializationCompensationCurrentSource(owned,"caller-entry"));
        assertFalse(solver.isInitializationCompensationCurrentSource(null,"Bus-2"));
        var replacement=new Hashtable<String,Complex>(owned);
        net.setCustomBusCurrInjHashtable(replacement);
        assertFalse(solver.isInitializationCompensationCurrentSource(replacement,"Bus-2"));
        assertSame(initial,replacement.get("Bus-2"));
    }

    @Test void preexistingCallerSourceIsNeverAdoptedAsInitializationCompensation() throws Exception {
        var net=com.interpss.simu.util.sample.SampleDStabTestingCases.sample3BusDStab_StaticLoads();
        assertTrue(com.interpss.core.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net).loadflow());
        var caller=new Hashtable<String,Complex>();caller.put("Bus-2",new Complex(1e-15,0.));
        net.setCustomBusCurrInjHashtable(caller);
        var algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(net);
        algorithm.setSimuStepSec(.001);
        var monitor=new com.interpss.dstab.cache.StateMonitor();monitor.prepareMonitoring();algorithm.setSimuOutputHandler(monitor);
        var solver=new PsseDStabSolver(algorithm);algorithm.setSolver(solver);
        assertTrue(algorithm.initialization());assertSame(caller,net.getCustomBusCurrInjHashtable());
        assertFalse(solver.isInitializationCompensationCurrentSource(caller,"Bus-2"));
    }
}
