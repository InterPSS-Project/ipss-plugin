package org.interpss.plugin.contingency.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.util.DclfContingencyPreScreenUtil.Classification;
import org.interpss.plugin.contingency.util.DclfContingencyPreScreenUtil.ContingencyPreScreenReport;
import org.interpss.plugin.contingency.util.DclfContingencyPreScreenUtil.ContingencyPreScreenResult;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.contingency.definition.ContingencyDefinition;
import com.interpss.core.contingency.dclf.DclfMultiOutage;

/**
 * Plugin-format adapter for the core multi-outage contingency helper.
 */
public class DclfMultiOutageContingencyHelper {
    public static final double DEFAULT_HIGH_IMPEDANCE_REACTANCE_PU =
            com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper
                    .DEFAULT_HIGH_IMPEDANCE_REACTANCE_PU;

    private final com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper coreHelper;

    public DclfMultiOutageContingencyHelper(ContingencyAnalysisAlgorithm dclfAlgo) {
        this.coreHelper =
                new com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper(dclfAlgo);
    }

    public List<DclfMultiOutage> createDclfMultiOutageContList(List<BranchContingencyRecord> records)
            throws InterpssException {
        return createDclfMultiOutageContListFromDefinitions(
                ContingencyDefinitionAdapter.fromBranchRecords(records));
    }

    public List<DclfMultiOutage> createDclfMultiOutageContListFromDefinitions(
            List<ContingencyDefinition> definitions) throws InterpssException {
        return coreHelper.createDclfMultiOutageContingencies(definitions);
    }

    public DclfMultiOutageContingencyPlan createPreScreenedDclfMultiOutagePlan(
            List<BranchContingencyRecord> records) throws InterpssException {
        return createPreScreenedDclfMultiOutagePlan(records, 1, DclfMethod.STD);
    }

    public DclfMultiOutageContingencyPlan createPreScreenedDclfMultiOutagePlan(
            List<BranchContingencyRecord> records,
            int parallelismLevel,
            DclfMethod method) throws InterpssException {
        return createPreScreenedDclfMultiOutagePlanFromDefinitions(
                ContingencyDefinitionAdapter.fromBranchRecords(records), parallelismLevel, method);
    }

    public DclfMultiOutageContingencyPlan createPreScreenedDclfMultiOutagePlanFromDefinitions(
            List<ContingencyDefinition> definitions) throws InterpssException {
        return createPreScreenedDclfMultiOutagePlanFromDefinitions(definitions, 1, DclfMethod.STD);
    }

    public DclfMultiOutageContingencyPlan createPreScreenedDclfMultiOutagePlanFromDefinitions(
            List<ContingencyDefinition> definitions,
            int parallelismLevel,
            DclfMethod method) throws InterpssException {
        if (definitions == null || definitions.isEmpty()) {
            return new DclfMultiOutageContingencyPlan(List.of(), List.of(), List.of(), null);
        }
        var corePlan = coreHelper.createPreScreenedPlanFromDefinitions(definitions, parallelismLevel);
        Map<String, ContingencyDefinition> byName = new LinkedHashMap<>();
        for (ContingencyDefinition definition : definitions) {
            byName.put(definition.name, definition);
        }
        List<ContingencyDefinition> islanding = definitionsFor(corePlan.islandingContingencies(), byName);
        List<ContingencyDefinition> singular = definitionsFor(corePlan.ePtdfSingularContingencies(), byName);
        return new DclfMultiOutageContingencyPlan(
                corePlan.normalContingencies(), islanding, singular, toPluginReport(corePlan.results()));
    }

    public DclfMultiOutage createDclfMultiOutage(ContingencyDefinition definition)
            throws InterpssException {
        return coreHelper.createDclfMultiOutage(definition);
    }

    public DclfMultiOutage createDclfMultiOutage(
            String name,
            List<BranchContingencyRecord> records) throws InterpssException {
        List<ContingencyDefinition> definitions = ContingencyDefinitionAdapter.fromBranchRecords(records);
        if (definitions.size() != 1) {
            throw new InterpssException("Expected one grouped contingency definition: " + name);
        }
        definitions.get(0).name = name;
        return coreHelper.createDclfMultiOutage(definitions.get(0));
    }

    public HighImpedanceDclfReplayWorkspace createHighImpedanceDclfReplayWorkspace()
            throws InterpssException {
        return createHighImpedanceDclfReplayWorkspace(DclfMethod.STD);
    }

    public HighImpedanceDclfReplayWorkspace createHighImpedanceDclfReplayWorkspace(DclfMethod method)
            throws InterpssException {
        return new HighImpedanceDclfReplayWorkspace(
                coreHelper.createHighImpedanceReplayWorkspace(method), coreHelper);
    }

    private static List<ContingencyDefinition> definitionsFor(
            List<DclfMultiOutage> contingencies,
            Map<String, ContingencyDefinition> byName) {
        List<ContingencyDefinition> definitions = new ArrayList<>(contingencies.size());
        for (DclfMultiOutage contingency : contingencies) {
            ContingencyDefinition definition = byName.get(contingency.getId());
            if (definition != null) {
                definitions.add(definition);
            }
        }
        return List.copyOf(definitions);
    }

    private static ContingencyPreScreenReport toPluginReport(
            List<com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper.ScreenResult>
                    coreResults) {
        List<ContingencyPreScreenResult> results = new ArrayList<>(coreResults.size());
        int islanding = 0;
        int singular = 0;
        int normal = 0;
        int unsupported = 0;
        for (var result : coreResults) {
            Classification classification = switch (result.classification()) {
                case ISLANDING -> Classification.ISLANDING;
                case E_PTDF_SINGULAR -> Classification.E_PTDF_SINGULAR;
                case NORMAL -> Classification.NORMAL;
                case UNSUPPORTED -> Classification.UNSUPPORTED;
            };
            switch (classification) {
                case ISLANDING -> islanding++;
                case E_PTDF_SINGULAR -> singular++;
                case NORMAL -> normal++;
                case UNSUPPORTED -> unsupported++;
            }
            results.add(new ContingencyPreScreenResult(
                    result.contingencyId(), classification, result.actionCount(),
                    result.openActionCount(), result.closeActionCount(), result.islandCount(),
                    result.islandBusCounts(), result.ePtdfOriginalSize(), result.ePtdfEffectiveSize(),
                    result.message()));
        }
        return new ContingencyPreScreenReport(
                results.size(), islanding, singular, normal, unsupported, results);
    }

    public record DclfMultiOutageContingencyPlan(
            List<DclfMultiOutage> normalContingencies,
            List<ContingencyDefinition> islandingDefinitions,
            List<ContingencyDefinition> ePtdfSingularDefinitions,
            ContingencyPreScreenReport preScreenReport) {
        public DclfMultiOutageContingencyPlan {
            normalContingencies = normalContingencies == null ? List.of() : List.copyOf(normalContingencies);
            islandingDefinitions = islandingDefinitions == null ? List.of() : List.copyOf(islandingDefinitions);
            ePtdfSingularDefinitions =
                    ePtdfSingularDefinitions == null ? List.of() : List.copyOf(ePtdfSingularDefinitions);
        }

        public List<ContingencyDefinition> replayRequiredDefinitions() {
            List<ContingencyDefinition> definitions =
                    new ArrayList<>(islandingDefinitions.size() + ePtdfSingularDefinitions.size());
            definitions.addAll(islandingDefinitions);
            definitions.addAll(ePtdfSingularDefinitions);
            return List.copyOf(definitions);
        }

        public int totalContingencies() {
            return normalContingencies.size()
                    + islandingDefinitions.size()
                    + ePtdfSingularDefinitions.size();
        }
    }

    public static final class HighImpedanceDclfReplayWorkspace implements AutoCloseable {
        private final com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper
                .HighImpedanceReplayWorkspace delegate;
        private final com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper coreHelper;

        private HighImpedanceDclfReplayWorkspace(
                com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper
                        .HighImpedanceReplayWorkspace delegate,
                com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper coreHelper) {
            this.delegate = delegate;
            this.coreHelper = coreHelper;
        }

        public boolean solve(ContingencyDefinition definition, double highXPu)
                throws InterpssException {
            return delegate.solve(coreHelper.createDclfMultiOutage(definition), highXPu);
        }

        public boolean solve(ContingencyDefinition definition) throws InterpssException {
            return solve(definition, DEFAULT_HIGH_IMPEDANCE_REACTANCE_PU);
        }

        public double getBranchFlow(AclfBranch branch, UnitType unit) {
            return delegate.getBranchFlow(branch, unit);
        }

        public ContingencyAnalysisAlgorithm dclfAlgo() {
            return delegate.dclfAlgo();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
