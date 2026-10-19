package org.interpss.core.dstab;

import org.junit.jupiter.api.Test;

/** Native PSS/E ST5C common-profile comparison against Independent ST5C. */
public class St5cIndependentSmibConformanceTest {
    @Test void threeCycleFaultMatchesIndependentBoundaryMachineAndExciterStates()throws Exception{
        St5bIndependentSmibConformanceTest.assertConformance(
                "SMIB_v33_genrou_st5c.dyr","smib-genrou-st5c","ST5C");
    }
    @Test void freshColdPairedReferenceMatchesTheOriginalBounds(@org.junit.jupiter.api.io.TempDir java.nio.file.Path staged)throws Exception {
        var reference=org.interpss.core.dstab.reference.FreshExciterPowerWorldReference.reference("st5c");
        var inputs=org.interpss.core.dstab.reference.FreshExciterPowerWorldReference.stageInputs("st5c",java.nio.file.Path.of("testData/adpter/psse/v33/SMIB"),staged);
        St5bIndependentSmibConformanceTest.assertConformance("SMIB_v33_genrou_st5c.dyr","smib-genrou-st5c","ST5C",inputs,reference,"fresh");
    }
}
