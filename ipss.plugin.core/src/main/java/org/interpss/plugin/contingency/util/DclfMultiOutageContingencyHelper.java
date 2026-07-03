package org.interpss.plugin.contingency.util;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createMultiOutageContingency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.ContingencyDefinition;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class DclfMultiOutageContingencyHelper {
    private final ContingencyAnalysisAlgorithm dclfAlgo;

    public DclfMultiOutageContingencyHelper(ContingencyAnalysisAlgorithm dclfAlgo) {
        this.dclfAlgo = dclfAlgo;
    }

    public List<DclfMultiOutage> createDclfMultiOutageContList(List<BranchContingencyRecord> records)
            throws InterpssException {
        Map<String, List<BranchContingencyRecord>> byName = new LinkedHashMap<>();
        for (BranchContingencyRecord record : records) {
            byName.computeIfAbsent(record.name, ignored -> new ArrayList<>()).add(record);
        }

        List<DclfMultiOutage> contingencies = new ArrayList<>();
        for (Map.Entry<String, List<BranchContingencyRecord>> entry : byName.entrySet()) {
            contingencies.add(createDclfMultiOutage(entry.getKey(), entry.getValue()));
        }
        return contingencies;
    }

    public List<DclfMultiOutage> createDclfMultiOutageContListFromDefinitions(
            List<ContingencyDefinition> definitions)
            throws InterpssException {
        List<DclfMultiOutage> contingencies = new ArrayList<>();
        if (definitions == null) {
            return contingencies;
        }
        for (ContingencyDefinition definition : definitions) {
            contingencies.add(createDclfMultiOutage(definition));
        }
        return contingencies;
    }

    public DclfMultiOutage createDclfMultiOutage(ContingencyDefinition definition)
            throws InterpssException {
        if (definition == null) {
            throw new InterpssException("Contingency definition cannot be null");
        }
        List<BranchContingencyRecord> records =
                ContingencyDefinitionAdapter.toBranchRecords(List.of(definition));
        return createDclfMultiOutage(definition.name, records);
    }

    public DclfMultiOutage createDclfMultiOutage(String name, List<BranchContingencyRecord> records)
            throws InterpssException {
        if (records == null || records.isEmpty()) {
            throw new InterpssException("No branch records for contingency " + name);
        }

        ContingencyBranchOutageType outageType = outageType(records.get(0));
        DclfMultiOutage contingency = createMultiOutageContingency(name, outageType);
        for (BranchContingencyRecord record : records) {
            ContingencyBranchOutageType recordOutageType = outageType(record);
            if (recordOutageType != outageType) {
                throw new InterpssException("Mixed branch action types are not supported for contingency " + name);
            }

            String branchId = resolveBranchId(record);
            DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(branchId);
            if (dclfBranch == null) {
                throw new InterpssException("Could not resolve DCLF branch for contingency "
                        + name + ": " + branchId);
            }
            DclfOutageBranch outage = createCaOutageBranch(dclfBranch, outageType);
            outage.setDclfFlow(dclfBranch.getDclfFlow());
            contingency.getOutageEquips().add(outage);
        }
        return contingency;
    }

    private String resolveBranchId(BranchContingencyRecord record) throws InterpssException {
        String forward = record.fromBus + "->" + record.toBus + "(" + record.ckt + ")";
        if (dclfAlgo.getAclfNet().getBranch(forward) != null) {
            return forward;
        }
        String reverse = record.toBus + "->" + record.fromBus + "(" + record.ckt + ")";
        if (dclfAlgo.getAclfNet().getBranch(reverse) != null) {
            return reverse;
        }
        throw new InterpssException("Could not resolve branch " + forward + " or " + reverse);
    }

    private static ContingencyBranchOutageType outageType(BranchContingencyRecord record) {
        String actionType = record.actionType == null ? "" : record.actionType.toLowerCase(Locale.ROOT);
        if ("close".equals(actionType)) {
            return ContingencyBranchOutageType.CLOSE;
        }
        return ContingencyBranchOutageType.OPEN;
    }
}
