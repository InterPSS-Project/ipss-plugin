package org.interpss.plugin.contingency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.plugin.contingency.result.AclfContingencyResultContainer;
import org.interpss.plugin.contingency.result.AclfContingencyResultRec;
import org.interpss.plugin.contingency.aux_fmt.mapper.AclfNetworkAuxBranchResolver;
import org.interpss.plugin.contingency.definition.ContingencyAction;
import org.interpss.plugin.contingency.definition.ContingencyActionType;
import org.interpss.plugin.contingency.definition.ContingencyDefinition;
import org.interpss.plugin.contingency.definition.ContingencyObjectType;
import org.interpss.plugin.contingency.util.AclfContingencyDefinitionHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.opf.OpfBranch;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfNetwork;
import com.interpss.opf.OpfObjectFactory;

class ParallelAclfContingencyAnalyzerTest {
    @BeforeAll
    static void initializeCore() {
        IpssCorePlugin.init();
    }

    @Test
    void aclfParallelResultsMatchSequentialAndPreserveInputOrder() throws Exception {
        AclfNetwork network = createAclfTriangle();
        assertEquivalentAndUnchanged(network);
    }

    @Test
    void opfParallelResultsMatchSequentialAndPreserveSourceNetwork() throws Exception {
        OpfNetwork network = createOpfTriangle();
        assertEquivalentAndUnchanged(network);

        String branchId = network.getBranchList().get(0).getId();
        assertTrue(new AclfNetworkAuxBranchResolver(network).resolve(branchId).isPresent());
        ContingencyDefinition definition = new ContingencyDefinition(
                "opf-outage",
                List.of(new ContingencyAction(
                        ContingencyObjectType.BRANCH,
                        ContingencyActionType.OPEN,
                        branchId)));
        assertEquals(branchId, new AclfContingencyDefinitionHelper(network)
                .createAclfMultiOutage(definition)
                .getOutageEquips().get(0)
                .getOutageEquip().getId());
    }

    private static void assertEquivalentAndUnchanged(
            com.interpss.core.aclf.BaseAclfNetwork<?, ?> network) {
        List<String> expectedOrder = network.getBranchList().stream().map(AclfBranch::getId).toList();
        List<Boolean> originalStatuses = network.getBranchList().stream()
                .map(AclfBranch::isActive)
                .toList();
        AclfContingencyConfig config = new AclfContingencyConfig();
        config.setParallelism(2);

        ParallelAclfContingencyAnalyzer<AclfContingencyResultRec> analyzer =
                new ParallelAclfContingencyAnalyzer<>(network);
        AclfContingencyResultContainer<AclfContingencyResultRec> sequential =
                analyzer.analyzeContingencies(expectedOrder.size(), config, false);
        AclfContingencyResultContainer<AclfContingencyResultRec> parallel =
                analyzer.analyzeContingencies(expectedOrder.size(), config, true);

        assertEquals(expectedOrder, new ArrayList<>(sequential.getCAResults().keySet()));
        assertEquals(expectedOrder, new ArrayList<>(parallel.getCAResults().keySet()));
        assertEquals(sequential.getTotalSuccessCount(), parallel.getTotalSuccessCount());
        for (String branchId : expectedOrder) {
            assertEquals(sequential.getCAResults().get(branchId).isConverged(),
                    parallel.getCAResults().get(branchId).isConverged());
        }
        assertEquals(originalStatuses, network.getBranchList().stream()
                .map(AclfBranch::isActive)
                .toList());
        assertTrue(parallel.getDiagnostics().isEmpty());
    }

    private static AclfNetwork createAclfTriangle() throws InterpssException {
        AclfNetwork network = CoreObjectFactory.createAclfNetwork();
        network.setId("aclf-triangle");
        network.setBaseKva(100000.0);

        AclfBus bus1 = CoreObjectFactory.createAclfBus("Bus1", network).orElseThrow();
        bus1.setGenCode(AclfGenCode.SWING);
        bus1.setGenP(1.2);
        bus1.setVoltageMag(1.0);
        bus1.setBaseVoltage(138000.0);
        AclfBus bus2 = CoreObjectFactory.createAclfBus("Bus2", network).orElseThrow();
        bus2.setLoadCode(AclfLoadCode.CONST_P);
        bus2.setLoadP(0.5);
        bus2.setVoltageMag(1.0);
        bus2.setBaseVoltage(138000.0);
        AclfBus bus3 = CoreObjectFactory.createAclfBus("Bus3", network).orElseThrow();
        bus3.setLoadCode(AclfLoadCode.CONST_P);
        bus3.setLoadP(0.7);
        bus3.setVoltageMag(1.0);
        bus3.setBaseVoltage(138000.0);

        addAclfBranch(network, "Bus1", "Bus2", 0.10);
        addAclfBranch(network, "Bus2", "Bus3", 0.12);
        addAclfBranch(network, "Bus1", "Bus3", 0.15);
        return network;
    }

    private static void addAclfBranch(
            AclfNetwork network,
            String fromBusId,
            String toBusId,
            double reactance) throws InterpssException {
        AclfBranch branch = CoreObjectFactory.createAclfBranch();
        branch.setZ(new Complex(0.0, reactance));
        network.addBranch(branch, fromBusId, toBusId, "1");
    }

    private static OpfNetwork createOpfTriangle() throws InterpssException {
        OpfNetwork network = OpfObjectFactory.createOpfNetwork();
        network.setId("opf-triangle");
        network.setBaseKva(100000.0);

        OpfBus bus1 = OpfObjectFactory.createOpfGenBus("Bus1", network);
        bus1.setGenCode(AclfGenCode.SWING);
        bus1.setGenP(1.2);
        bus1.setVoltageMag(1.0);
        bus1.setBaseVoltage(138000.0);
        bus1.getOpfGen().setId("Gen1");
        bus1.getOpfGen().setGen(new Complex(1.2, 0.0));
        OpfBus bus2 = OpfObjectFactory.createOpfBus("Bus2", network);
        bus2.setLoadCode(AclfLoadCode.CONST_P);
        bus2.setLoadP(0.5);
        bus2.setVoltageMag(1.0);
        bus2.setBaseVoltage(138000.0);
        OpfBus bus3 = OpfObjectFactory.createOpfBus("Bus3", network);
        bus3.setLoadCode(AclfLoadCode.CONST_P);
        bus3.setLoadP(0.7);
        bus3.setVoltageMag(1.0);
        bus3.setBaseVoltage(138000.0);

        addOpfBranch(network, "Bus1", "Bus2", 0.10);
        addOpfBranch(network, "Bus2", "Bus3", 0.12);
        addOpfBranch(network, "Bus1", "Bus3", 0.15);
        return network;
    }

    private static void addOpfBranch(
            OpfNetwork network,
            String fromBusId,
            String toBusId,
            double reactance) throws InterpssException {
        OpfBranch branch = OpfObjectFactory.createOpfBranch();
        branch.setZ(new Complex(0.0, reactance));
        network.addBranch(branch, fromBusId, toBusId, "1");
    }
}
