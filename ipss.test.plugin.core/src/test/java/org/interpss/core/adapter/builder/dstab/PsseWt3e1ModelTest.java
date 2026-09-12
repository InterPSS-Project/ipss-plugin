package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.dstab.mach.Wt3e1Data;
import org.interpss.dstab.mach.Wt3e1Model;
import org.interpss.dstab.mach.Wt3g1Data;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;

/** Equation-mode, limit, and named-state checks for WT3E1. */
public class PsseWt3e1ModelTest {
    @Test void allReactiveAndVoltageLimitModesRemainFinite() throws Exception {
        for (int varFlag : new int[] {-1,0,1}) for (int voltageFlag : new int[] {0,1,2}) {
            var builder=DStabBuilderTestFixture.createBuilder();
            var generator=builder.addWt3g1("Bus1","1",new Wt3g1Data(40,.33403,24,.6,.12,1.63));
            Wt3e1Model controller=builder.addWt3e1("Bus1","1",data(varFlag,voltageFlag));
            assertNotNull(controller);assertTrue(generator.initStates(generator.getDStabBus()));
            controller.setSpeedDeviation(.18);
            assertTrue(generator.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));
            assertTrue(generator.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1));
            assertEquals(10,controller.getNamedStates().size());
            assertTrue(controller.getNamedStates().values().stream().allMatch(Double::isFinite));
        }
    }

    @Test void rejectsInvalidFlagsAndBreakpoints() {
        assertThrows(IllegalArgumentException.class,()->data(2,1));
        assertThrows(IllegalArgumentException.class,()->data(1,3));
        assertThrows(IllegalArgumentException.class,()->new Wt3e1Data(0,1,1,0,0,0,
                .12,15,4,0,.04,2.5,.5,1.1,.05,.45,-.45,1.1,.02,.4,-.4,4,.1,
                .88,1.12,35,-.5,1.45,.05,.05,1,.3,.69,.78,.98,.5,1.2));
    }

    private static Wt3e1Data data(int varFlag,int voltageFlag){return new Wt3e1Data(
            0,varFlag,voltageFlag,0,0,0,.12,15,4,0,.04,2.5,.5,1.1,.05,.45,-.45,
            1.1,.02,.4,-.4,4,.1,.88,1.12,35,-.5,1.45,.05,.05,1,.3,.69,.78,.98,.74,1.2);}
}
