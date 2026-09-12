package org.interpss.core.dstab;

import org.junit.jupiter.api.Test;

/** Native PSS/E ST5C common-profile comparison against PowerWorld ST5C. */
public class St5cPowerWorldSmibConformanceTest {
    @Test void threeCycleFaultMatchesPowerWorldBoundaryMachineAndExciterStates()throws Exception{
        St5bPowerWorldSmibConformanceTest.assertConformance(
                "SMIB_v33_genrou_st5c.dyr","smib-genrou-st5c","ST5C");
    }
}
