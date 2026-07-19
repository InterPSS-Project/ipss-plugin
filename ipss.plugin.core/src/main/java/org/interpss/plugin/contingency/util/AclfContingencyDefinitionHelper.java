package org.interpss.plugin.contingency.util;

import java.util.ArrayList;
import java.util.List;

import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.ContingencyDefinition;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.AclfContingencyObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.aclf.AclfMultiOutage;

public class AclfContingencyDefinitionHelper {
    private final BaseAclfNetwork<?, ?> network;

    public AclfContingencyDefinitionHelper(BaseAclfNetwork<?, ?> network) {
        this.network = network;
    }

    public List<AclfMultiOutage> createAclfMultiOutageList(
            List<ContingencyDefinition> definitions)
            throws InterpssException {
        List<AclfMultiOutage> outages = new ArrayList<>();
        if (definitions == null) {
            return outages;
        }
        for (ContingencyDefinition definition : definitions) {
            outages.add(createAclfMultiOutage(definition));
        }
        return outages;
    }

    public AclfMultiOutage createAclfMultiOutage(ContingencyDefinition definition)
            throws InterpssException {
        if (definition == null) {
            throw new InterpssException("Contingency definition cannot be null");
        }
        List<BranchContingencyRecord> records =
                ContingencyDefinitionAdapter.toBranchRecords(List.of(definition));
        if (records.isEmpty()) {
            throw new InterpssException("No branch actions for contingency " + definition.name);
        }

        AclfMultiOutage outage =
                AclfContingencyObjectFactory.createAclfMultiOutage(definition.name);
        for (BranchContingencyRecord record : records) {
            AclfBranch branch = resolveBranch(record);
            outage.getOutageEquips().add(
                    AclfContingencyObjectFactory.createAclfBranchOutage(
                            branch,
                            ContingencyBranchOutageType.OPEN));
        }
        return outage;
    }

    private AclfBranch resolveBranch(BranchContingencyRecord record)
            throws InterpssException {
        String forward = record.fromBus + "->" + record.toBus + "(" + record.ckt + ")";
        AclfBranch branch = network.getBranch(forward);
        if (branch != null) {
            return branch;
        }

        String reverse = record.toBus + "->" + record.fromBus + "(" + record.ckt + ")";
        branch = network.getBranch(reverse);
        if (branch != null) {
            return branch;
        }

        throw new InterpssException("Could not resolve branch " + forward + " or " + reverse);
    }
}
