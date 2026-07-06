package org.interpss.plugin.contingency.util;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.interpss.plugin.contingency.definition.ContingencyAction;
import org.interpss.plugin.contingency.definition.ContingencyActionType;
import org.interpss.plugin.contingency.definition.ContingencyDefinition;
import org.interpss.plugin.contingency.definition.ContingencyObjectType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.aclf.AclfBranchOutage;
import com.interpss.core.contingency.aclf.AclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfMultiOutage;

public class ContingencyDefinitionRuntimeHelperTest {

    @Test
    public void createsDclfMultiOutageFromGroupedDefinition() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyAnalysisAlgorithm dclfAlgo =
                DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(), "IEEE9 base DCLF should converge");

        ContingencyDefinition definition = definition(
                "GROUPED_DCLF",
                "Bus4->Bus5(0)",
                "Bus7->Bus8(0)");

        List<DclfMultiOutage> outages =
                new DclfMultiOutageContingencyHelper(dclfAlgo)
                        .createDclfMultiOutageContListFromDefinitions(List.of(definition));

        assertEquals(1, outages.size());
        assertEquals("GROUPED_DCLF", outages.get(0).getId());
        assertEquals(2, outages.get(0).getOutageEquips().size());
        assertEquals("Bus4->Bus5(0)", outages.get(0).getOutageEquips().get(0).getBranch().getId());
    }

    @Test
    public void createsAclfMultiOutageFromGroupedDefinition() throws Exception {
        AclfNetwork net = importIeee9Labeled();
        ContingencyDefinition definition = definition(
                "GROUPED_ACLF",
                "Bus4->Bus5(0)",
                "Bus7->Bus8(0)");

        List<AclfMultiOutage> outages =
                new AclfContingencyDefinitionHelper(net)
                        .createAclfMultiOutageList(List.of(definition));

        assertEquals(1, outages.size());
        assertEquals("GROUPED_ACLF", outages.get(0).getId());
        assertEquals(2, outages.get(0).getOutageEquips().size());
        assertTrue(outages.get(0).getOutageEquips().get(0) instanceof AclfBranchOutage);

        AclfBranchOutage first =
                (AclfBranchOutage) outages.get(0).getOutageEquips().get(0);
        assertEquals("Bus4->Bus5(0)", first.getOutageEquip().getId());
    }

    private static ContingencyDefinition definition(String name, String... objectIds) {
        ContingencyDefinition definition = new ContingencyDefinition(name);
        for (String objectId : objectIds) {
            definition.addAction(new ContingencyAction(
                    ContingencyObjectType.BRANCH,
                    ContingencyActionType.OPEN,
                    objectId));
        }
        return definition;
    }

    private static AclfNetwork importIeee9Labeled() throws InterpssException {
        return IpssAdapter.importAclfNet(resolveTestDataPath(
                        "ipss.test.plugin.core/testData/adpter/psse/v36/ieee9_v36_labeled.raw",
                        "../ipss.test.plugin.core/testData/adpter/psse/v36/ieee9_v36_labeled.raw").toString())
                .setFormat(PSSE)
                .setPsseVersion(IpssAdapter.PsseVersion.PSSE_36)
                .load()
                .getImportedObj();
    }

    private static Path resolveTestDataPath(String first, String second) {
        Path firstPath = Path.of(first);
        if (Files.isRegularFile(firstPath)) {
            return firstPath;
        }
        return Path.of(second);
    }
}
