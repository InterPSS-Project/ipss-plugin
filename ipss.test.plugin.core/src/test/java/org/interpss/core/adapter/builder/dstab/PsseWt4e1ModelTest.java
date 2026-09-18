package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact record, attachment, initialization, and state checks for WT4E1. */
public class PsseWt4e1ModelTest {
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");
    @Test void exactPublishedRecordAttachesAndInitializes() throws Exception {
        IpssCorePlugin.init(); var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_wt4e1_psse36.dyr").toString());
        var network=context.getDStabilityNet(); var algorithm=context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow()); algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005); algorithm.setSimuOutputHandler(new StateMonitor()); assertTrue(algorithm.initialization());
        DStabGen gen=(DStabGen)network.getBus("Bus1").getContributeGen("1");
        Wt4g1Model host=assertInstanceOf(Wt4g1Model.class,gen.getDynamicGenDevice());
        Wt4e1Model model=assertInstanceOf(Wt4e1Model.class,host.getElectricalController());
        assertEquals(27,DynamicModelCatalog.find("WT4E1").orElseThrow().parameterCount());
        assertEquals(10,model.getNamedStates().size());
        assertEquals(0,model.getData().remoteBus()); assertEquals(1,model.getData().varFlag());
        assertEquals(.5,model.getNamedStates().get("Power filter"),2e-6);
        assertEquals(0.0,model.getNamedStates().get("Active-power regulator integrator"),1e-12);
    }
}
