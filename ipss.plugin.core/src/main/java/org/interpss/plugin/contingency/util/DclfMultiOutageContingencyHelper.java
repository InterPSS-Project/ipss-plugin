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
 * Plugin-format adapter for the core multi-outage contingency helper. It keeps
 * record/file-oriented plugin APIs source-compatible while delegating conversion,
 * structural screening, and high-impedance replay to core. New callers that
 * already hold core definitions should normally submit them directly to
 * {@code ParallelDclfContingencyAnalyzer.executeContingencyDefinitions(...)}.
 */
public class DclfMultiOutageContingencyHelper {
    /** Core default reactance, in per unit, used for high-impedance replay. */
    public static final double DEFAULT_HIGH_IMPEDANCE_REACTANCE_PU =
            com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper
                    .DEFAULT_HIGH_IMPEDANCE_REACTANCE_PU;

    private final com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper coreHelper;

    /**
     * Creates an adapter bound to one DCLF algorithm and network.
     *
     * @param dclfAlgo algorithm used by the delegated core helper
     */
    public DclfMultiOutageContingencyHelper(ContingencyAnalysisAlgorithm dclfAlgo) {
        this.coreHelper =
                new com.interpss.core.algo.dclf.solver.DclfMultiOutageContingencyHelper(dclfAlgo);
    }

    /**
     * Groups flat plugin branch records by contingency name and converts them to
     * core multi-outage objects.
     *
     * @param records flat branch action records
     * @return network-bound contingencies in first-name occurrence order
     * @throws InterpssException if an action cannot be converted or resolved
     */
    public List<DclfMultiOutage> createDclfMultiOutageContList(List<BranchContingencyRecord> records)
            throws InterpssException {
        return createDclfMultiOutageContListFromDefinitions(
                ContingencyDefinitionAdapter.fromBranchRecords(records));
    }

    /**
     * Converts core definitions to network-bound multi-outage objects.
     *
     * @param definitions definitions to convert
     * @return network-bound contingencies in input order
     * @throws InterpssException if an action cannot be resolved
     */
    public List<DclfMultiOutage> createDclfMultiOutageContListFromDefinitions(
            List<ContingencyDefinition> definitions) throws InterpssException {
        return coreHelper.createDclfMultiOutageContingencies(definitions);
    }

    /**
     * Converts and structurally screens plugin records using one worker and STD
     * DCLF compatibility defaults.
     *
     * @param records flat branch action records
     * @return normal and replay-required execution buckets
     * @throws InterpssException if conversion or screening fails
     */
    public DclfMultiOutageContingencyPlan createPreScreenedDclfMultiOutagePlan(
            List<BranchContingencyRecord> records) throws InterpssException {
        return createPreScreenedDclfMultiOutagePlan(records, 1, DclfMethod.STD);
    }

    /**
     * Converts and structurally screens plugin records. Structural screening is
     * topology-based and does not mutate the network; {@code method} is retained
     * for compatibility with the former plugin API.
     *
     * @param records flat branch action records
     * @param parallelismLevel requested maximum screening worker count
     * @param method caller's DCLF method context
     * @return normal, islanding, and E-PTDF-singular buckets
     * @throws InterpssException if conversion or screening fails
     */
    public DclfMultiOutageContingencyPlan createPreScreenedDclfMultiOutagePlan(
            List<BranchContingencyRecord> records,
            int parallelismLevel,
            DclfMethod method) throws InterpssException {
        return createPreScreenedDclfMultiOutagePlanFromDefinitions(
                ContingencyDefinitionAdapter.fromBranchRecords(records), parallelismLevel, method);
    }

    /**
     * Structurally screens core definitions using one worker.
     *
     * @param definitions definitions to classify
     * @return normal and replay-required execution buckets
     * @throws InterpssException if conversion or screening fails
     */
    public DclfMultiOutageContingencyPlan createPreScreenedDclfMultiOutagePlanFromDefinitions(
            List<ContingencyDefinition> definitions) throws InterpssException {
        return createPreScreenedDclfMultiOutagePlanFromDefinitions(definitions, 1, DclfMethod.STD);
    }

    /**
     * Structurally screens core definitions in parallel and maps core diagnostics
     * back to plugin report types. The input definitions are retained in the
     * replay buckets so callers can rebuild them on cloned worker networks.
     *
     * @param definitions definitions to classify
     * @param parallelismLevel requested maximum screening worker count
     * @param method caller's DCLF method context, retained for compatibility
     * @return immutable plugin execution plan
     * @throws InterpssException if conversion or screening fails
     */
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

    /**
     * Converts one core definition using the delegated core helper.
     *
     * @param definition definition to convert
     * @return network-bound multi-outage
     * @throws InterpssException if an action cannot be resolved
     */
    public DclfMultiOutage createDclfMultiOutage(ContingencyDefinition definition)
            throws InterpssException {
        return coreHelper.createDclfMultiOutage(definition);
    }

    /**
     * Groups records into exactly one definition, assigns {@code name}, and
     * converts it to a network-bound multi-outage.
     *
     * @param name contingency identifier to assign
     * @param records records that must form one grouped contingency
     * @return network-bound multi-outage
     * @throws InterpssException if records form zero/multiple groups or cannot be resolved
     */
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

    /**
     * Creates a plugin-definition replay workspace using STD DCLF.
     *
     * @return thread-confined high-impedance replay workspace
     * @throws InterpssException if base DCLF preparation fails
     */
    public HighImpedanceDclfReplayWorkspace createHighImpedanceDclfReplayWorkspace()
            throws InterpssException {
        return createHighImpedanceDclfReplayWorkspace(DclfMethod.STD);
    }

    /**
     * Creates a plugin-definition wrapper around the core replay workspace.
     * High-impedance replay supports standard and loss-inclusive DCLF while
     * retaining the worker's sparse matrix structure.
     *
     * @param method DCLF method, with {@code null} interpreted as STD
     * @return thread-confined high-impedance replay workspace
     * @throws InterpssException if base DCLF preparation fails
     */
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

    /**
     * Plugin compatibility view of the core structural execution plan.
     *
     * @param normalContingencies network-bound cases eligible for E-PTDF analysis
     * @param islandingDefinitions definitions requiring island-aware replay
     * @param ePtdfSingularDefinitions connected definitions requiring replay
     * @param preScreenReport per-contingency diagnostics and totals
     */
    public record DclfMultiOutageContingencyPlan(
            List<DclfMultiOutage> normalContingencies,
            List<ContingencyDefinition> islandingDefinitions,
            List<ContingencyDefinition> ePtdfSingularDefinitions,
            ContingencyPreScreenReport preScreenReport) {
        /** Normalizes all execution buckets to immutable empty-or-copy lists. */
        public DclfMultiOutageContingencyPlan {
            normalContingencies = normalContingencies == null ? List.of() : List.copyOf(normalContingencies);
            islandingDefinitions = islandingDefinitions == null ? List.of() : List.copyOf(islandingDefinitions);
            ePtdfSingularDefinitions =
                    ePtdfSingularDefinitions == null ? List.of() : List.copyOf(ePtdfSingularDefinitions);
        }

        /**
         * Returns islanding definitions followed by connected singular definitions.
         *
         * @return immutable combined replay list
         */
        public List<ContingencyDefinition> replayRequiredDefinitions() {
            List<ContingencyDefinition> definitions =
                    new ArrayList<>(islandingDefinitions.size() + ePtdfSingularDefinitions.size());
            definitions.addAll(islandingDefinitions);
            definitions.addAll(ePtdfSingularDefinitions);
            return List.copyOf(definitions);
        }

        /**
         * Returns the number of contingencies represented by this plan.
         *
         * @return total number of cases across all execution buckets
         */
        public int totalContingencies() {
            return normalContingencies.size()
                    + islandingDefinitions.size()
                    + ePtdfSingularDefinitions.size();
        }
    }

    /**
     * Plugin-definition wrapper for the core high-impedance replay workspace.
     * It is not thread-safe; create one instance per worker and close it with
     * try-with-resources to restore the final temporary B1/impedance changes.
     */
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

        /**
         * Converts and solves one definition with the requested high reactance.
         * Read branch flows before the next solve.
         *
         * @param definition definition to replay
         * @param highXPu replacement reactance in per unit for OPEN branches
         * @return {@code true} when numerical factorization and solve succeed
         * @throws InterpssException if conversion or replay fails
         */
        public boolean solve(ContingencyDefinition definition, double highXPu)
                throws InterpssException {
            return delegate.solve(coreHelper.createDclfMultiOutage(definition), highXPu);
        }

        /**
         * Solves with {@link #DEFAULT_HIGH_IMPEDANCE_REACTANCE_PU}.
         *
         * @param definition definition to replay
         * @return {@code true} when numerical factorization and solve succeed
         * @throws InterpssException if conversion or replay fails
         */
        public boolean solve(ContingencyDefinition definition) throws InterpssException {
            return solve(definition, DEFAULT_HIGH_IMPEDANCE_REACTANCE_PU);
        }

        /**
         * Returns flow from the most recent successful solve.
         *
         * @param branch branch in the workspace network
         * @param unit requested power unit
         * @return solved active-power flow
         */
        public double getBranchFlow(AclfBranch branch, UnitType unit) {
            return delegate.getBranchFlow(branch, unit);
        }

        /**
         * Returns the DCLF algorithm owned by the delegated core workspace.
         *
         * @return delegated DCLF algorithm
         */
        public ContingencyAnalysisAlgorithm dclfAlgo() {
            return delegate.dclfAlgo();
        }

        /** Restores temporary replay changes and closes the delegated workspace. */
        @Override
        public void close() {
            delegate.close();
        }
    }
}
