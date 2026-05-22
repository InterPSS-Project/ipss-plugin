package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.optadj.IEEE14_SensHelper_Test;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class DclfTransferPanelCacheTest extends CorePluginTestSetup {
    @Test
    public void panelMatchesInterpssLineOutageFactors() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, sourceAlgo, 3);
        Set<String> monitors = new LinkedHashSet<>(Arrays.asList(
                "Bus2->Bus3(1)",
                "Bus2->Bus4(1)",
                "Bus3->Bus4(1)",
                "Bus4->Bus5(1)"));

        DclfContingencyStudySpec spec = DclfContingencyStudySpec.builder(net)
                .contingencies(contingencies)
                .monitoredBranchIds(monitors)
                .build();

        DclfTransferPanelCache cache = DclfTransferPanelBuilder.build(spec);
        ContingencyAnalysisAlgorithm oracle = cache.getDclfAlgorithm();
        AclfBranch[] monitoredBranches = cache.getMonitoredBranches();
        DclfBranchOutage[] cachedContingencies = cache.getContingencies();

        assertEquals(monitors.size(), cache.getMonitorCount());
        assertEquals(contingencies.size(), cache.getOutageCount());
        assertTrue(cache.estimatePanelBytes() > 0);

        for (int outageIndex = 0; outageIndex < cachedContingencies.length; outageIndex++) {
            assertTrue(cache.isValidOutage(outageIndex));
            DclfOutageBranch outage = cachedContingencies[outageIndex].getOutageEquip();
            for (int monitorIndex = 0; monitorIndex < monitoredBranches.length; monitorIndex++) {
                double expected = oracle.lineOutageDFactor(outage, monitoredBranches[monitorIndex]);
                assertEquals(expected, cache.getLodf(monitorIndex, outageIndex), 1.0e-10);
            }
        }
    }

    private static List<DclfBranchOutage> firstNonRefBranchOutages(
            AclfNetwork net,
            ContingencyAnalysisAlgorithm dclfAlgo,
            int count) {
        List<DclfBranchOutage> contingencies = new ArrayList<>();

        for (AclfBranch branch : net.getBranchList()) {
            if (branch.isConnect2RefBus()) {
                continue;
            }
            DclfBranchOutage contingency = createContingency("contBranch:" + branch.getId());
            DclfOutageBranch outage =
                    createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()), ContingencyBranchOutageType.OPEN);
            contingency.setOutageEquip(outage);
            contingencies.add(contingency);
            if (contingencies.size() == count) {
                break;
            }
        }

        return contingencies;
    }
}
