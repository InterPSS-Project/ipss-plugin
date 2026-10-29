package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real RAW import exercises the default core data-check path, without a tap override. */
class Ieee39TransformerTapDataCheckTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void loadflowUsesConfiguredDiscreteTapNormalization(boolean snap) throws Exception {
        IpssCorePlugin.init();
        String directory = "testData/adpter/psse/v30/IEEE39Bus/";
        var context = new PSSEMultiFileLoader().loadDStab(
                directory + "IEEE39bus_v30.raw", directory + "IEEE39bus.dyr");
        var branch = context.getDStabilityNet().getBranch("Bus6->Bus31(1)");
        assertEquals(1.07, branch.getFromTurnRatio(), 1.e-12);
        var algo = context.getDynSimuAlgorithm().getAclfAlgorithm();
        assertTrue(algo.getDataCheckConfig().isAutoSnapTransformerTap());
        if (!snap) algo.getDataCheckConfig().setAutoSnapTransformerTap(false);
        algo.setTolerance(1.e-10);
        algo.setMaxIterations(50);
        assertTrue(algo.loadflow());
        assertEquals(snap ? 1.06875 : 1.07, branch.getFromTurnRatio(), 1.e-12);
    }
}
