package org.interpss.core.dstab;

import org.junit.jupiter.api.Test;

/** Native PSS/E ST5C common-profile comparison against Independent ST5C. */
public class St5cIndependentSmibConformanceTest {
    @Test void threeCycleFaultMatchesIndependentBoundaryMachineAndExciterStates()throws Exception{
        St5bIndependentSmibConformanceTest.assertConformance(
                "SMIB_v33_genrou_st5c.dyr","smib-genrou-st5c","ST5C");
    }
}
