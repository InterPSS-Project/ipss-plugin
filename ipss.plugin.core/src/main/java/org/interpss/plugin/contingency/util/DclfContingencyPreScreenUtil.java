package org.interpss.plugin.contingency.util;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.interpss.core.contingency.definition.ContingencyAction;
import com.interpss.core.contingency.definition.ContingencyActionType;
import com.interpss.core.contingency.definition.ContingencyDefinition;
import com.interpss.core.contingency.definition.ContingencyObjectType;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

/**
 * Fast pre-screening utility for large DCLF contingency files. It classifies each
 * branch contingency before full violation analysis into:
 * islanding, non-islanding but singular/compacted [E-PTDF], normal MLODF, or
 * unsupported/invalid. Prefer {@link #scanStructurally(AclfNetwork, List, int)}
 * for bulk input; the other scan overloads retain the numerical plugin behavior
 * for compatibility and diagnostics.
 */
public final class DclfContingencyPreScreenUtil {
    private DclfContingencyPreScreenUtil() {
    }

    /** Execution route indicated by contingency pre-screening. */
    public enum Classification {
        /** Final branch states disconnect one or more components from the reference. */
        ISLANDING,
        /** Topology remains connected but the outage E-PTDF system is singular. */
        E_PTDF_SINGULAR,
        /** Contingency is eligible for normal DCLF contingency processing. */
        NORMAL,
        /** Definition is invalid or contains an unsupported action. */
        UNSUPPORTED
    }

    /**
     * Aggregate and detailed results for one pre-screen operation.
     *
     * @param totalContingencies number of screened definitions
     * @param islandingContingencies number classified as islanding
     * @param ePtdfSingularContingencies number classified as connected singular
     * @param normalContingencies number eligible for normal processing
     * @param unsupportedContingencies number rejected as invalid/unsupported
     * @param results immutable diagnostics in input order
     */
    public record ContingencyPreScreenReport(
            int totalContingencies,
            int islandingContingencies,
            int ePtdfSingularContingencies,
            int normalContingencies,
            int unsupportedContingencies,
            List<ContingencyPreScreenResult> results) {
        /** Copies detailed diagnostics into an immutable list. */
        public ContingencyPreScreenReport {
            results = List.copyOf(results);
        }
    }

    /**
     * Diagnostic result for one contingency definition.
     *
     * @param contingencyId input contingency identifier
     * @param classification selected execution route
     * @param actionCount total branch actions
     * @param openActionCount OPEN actions
     * @param closeActionCount CLOSE actions
     * @param islandCount disconnected components outside the reference component
     * @param islandBusCounts bus count in each disconnected component
     * @param ePtdfOriginalSize E-PTDF order before duplicate-action compaction
     * @param ePtdfEffectiveSize E-PTDF order after compaction
     * @param message human-readable classification detail
     */
    public record ContingencyPreScreenResult(
            String contingencyId,
            Classification classification,
            int actionCount,
            int openActionCount,
            int closeActionCount,
            int islandCount,
            List<Integer> islandBusCounts,
            int ePtdfOriginalSize,
            int ePtdfEffectiveSize,
            String message) {
        /** Copies disconnected-component sizes into an immutable list. */
        public ContingencyPreScreenResult {
            islandBusCounts = List.copyOf(islandBusCounts);
        }
    }

    /**
     * Imports plugin JSON definitions and runs the compatibility numerical scan.
     *
     * @param net network used to resolve and screen branch actions
     * @param contingencyJson grouped contingency-definition JSON file
     * @return aggregate and per-definition screening results
     * @throws IOException if the JSON file cannot be read
     * @throws InterpssException if DCLF preparation or screening fails
     */
    public static ContingencyPreScreenReport scanJson(AclfNetwork net, File contingencyJson)
            throws IOException, InterpssException {
        return scan(net, ContingencyFileUtil.importContingencyDefinitionsFromJson(contingencyJson));
    }

    /**
     * Creates and calculates a DCLF algorithm, then runs the compatibility
     * numerical scan sequentially.
     *
     * @param net network used to resolve and screen actions
     * @param definitions definitions to screen
     * @return aggregate and per-definition screening results
     * @throws InterpssException if base DCLF or screening fails
     */
    public static ContingencyPreScreenReport scan(
            AclfNetwork net,
            List<ContingencyDefinition> definitions)
            throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        if (!dclfAlgo.calculateDclf()) {
            throw new InterpssException("DCLF calculation failed before contingency pre-screening");
        }
        return scan(dclfAlgo, definitions);
    }

    /**
     * Runs the compatibility numerical scan with an existing DCLF algorithm.
     * The algorithm's original outage-branch list is restored before return,
     * including when a contingency fails.
     *
     * @param dclfAlgo initialized DCLF algorithm
     * @param definitions definitions to screen; {@code null} means empty
     * @return aggregate and per-definition screening results
     * @throws InterpssException if screening cannot be completed
     */
    public static ContingencyPreScreenReport scan(
            ContingencyAnalysisAlgorithm dclfAlgo,
            List<ContingencyDefinition> definitions)
            throws InterpssException {
        Objects.requireNonNull(dclfAlgo, "dclfAlgo cannot be null");
        if (definitions == null) {
            definitions = List.of();
        }
        DclfMultiOutageContingencyHelper helper = new DclfMultiOutageContingencyHelper(dclfAlgo);
        List<DclfOutageBranch> originalOutages = new ArrayList<>(dclfAlgo.getOutageBranchList());
        List<ContingencyPreScreenResult> results = new ArrayList<>(definitions.size());
        try {
            for (ContingencyDefinition definition : definitions) {
                results.add(scanOne(dclfAlgo, helper, definition));
            }
        }
        finally {
            dclfAlgo.getOutageBranchList().clear();
            dclfAlgo.getOutageBranchList().addAll(originalOutages);
        }
        return summarize(results);
    }

    /**
     * Classifies branch contingencies from graph connectivity and repeated branch
     * actions. For a connected lossless DC network, a unique branch-outage
     * [E-PTDF] matrix loses rank exactly when the remaining graph disconnects.
     * This avoids cloning the network and solving endpoint RHS systems solely for
     * pre-screening. The topology scan is read-only and shares an immutable graph
     * index across workers.
     *
     * @param net network whose active branch topology is screened
     * @param definitions definitions to classify
     * @param parallelismLevel requested maximum screening worker count
     * @return aggregate and per-definition structural classifications
     * @throws InterpssException if DCLF preparation, conversion, or screening fails
     */
    public static ContingencyPreScreenReport scanStructurally(
            AclfNetwork net,
            List<ContingencyDefinition> definitions,
            int parallelismLevel)
            throws InterpssException {
        Objects.requireNonNull(net, "net cannot be null");
        if (definitions == null || definitions.isEmpty()) {
            return summarize(List.of());
        }
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        if (!dclfAlgo.calculateDclf()) {
            throw new InterpssException("DCLF calculation failed before structural pre-screening");
        }
        return new DclfMultiOutageContingencyHelper(dclfAlgo)
                .createPreScreenedDclfMultiOutagePlanFromDefinitions(
                        definitions, parallelismLevel, com.interpss.core.algo.dclf.DclfMethod.STD)
                .preScreenReport();
    }

    /**
     * Tests whether one definition disconnects buses from the reference component
     * after applying its effective final branch states.
     *
     * @param net network to screen
     * @param definition definition containing branch OPEN/CLOSE actions
     * @return {@code true} when the resulting topology is islanded
     */
    public static boolean isTopologyIslanding(
            AclfNetwork net,
            ContingencyDefinition definition) {
        return screenTopology(net, branchActions(definition)).islanded();
    }

    /**
     * Returns bus-id components disconnected from the reference component after
     * applying one definition. The reference component itself is omitted.
     *
     * @param net network to screen
     * @param definition definition containing branch OPEN/CLOSE actions
     * @return disconnected components, each represented by its bus ids
     */
    public static List<List<String>> postContingencyIslandBusIdComponents(
            AclfNetwork net,
            ContingencyDefinition definition) {
        return screenTopology(net, branchActions(definition)).islandBusIdComponents();
    }

    private static ContingencyPreScreenResult scanOne(
            ContingencyAnalysisAlgorithm dclfAlgo,
            DclfMultiOutageContingencyHelper helper,
            ContingencyDefinition definition) {
        String id = definition == null ? "<null>" : definition.name;
        List<ContingencyAction> branchActions = List.of();
        ActionCounts counts = new ActionCounts(0, 0);
        try {
            branchActions = branchActions(definition);
            counts = actionCounts(branchActions);
            TopologyScreen topology = screenTopology(dclfAlgo.getAclfNet(), branchActions);
            if (topology.islanded()) {
                return new ContingencyPreScreenResult(
                        id,
                        Classification.ISLANDING,
                        branchActions.size(),
                        counts.openCount(),
                        counts.closeCount(),
                        topology.islandBusCounts().size(),
                        topology.islandBusCounts(),
                        0,
                        0,
                        "Topology islanding relative to reference bus " + dclfAlgo.getAclfNet().getRefBusId());
            }
            if (branchActions.size() <= 1) {
                return new ContingencyPreScreenResult(
                        id,
                        Classification.NORMAL,
                        branchActions.size(),
                        counts.openCount(),
                        counts.closeCount(),
                        0,
                        List.of(),
                        branchActions.size(),
                        branchActions.size(),
                        "Single branch contingency is topology-connected; MLODF [E-PTDF] matrix is not required");
            }

            DclfMultiOutage contingency = helper.createDclfMultiOutage(definition);
            dclfAlgo.getOutageBranchList().clear();
            dclfAlgo.getOutageBranchList().addAll(contingency.getOutageEquips());
            int originalSize = dclfAlgo.getOutageBranchList().size();
            Object inv = dclfAlgo.calMultiOutageInvE_PTDF(contingency.getId());
            int effectiveSize = inv instanceof double[][] matrix ? matrix.length : originalSize;
            Classification classification = effectiveSize == originalSize
                    ? Classification.NORMAL
                    : Classification.E_PTDF_SINGULAR;
            return new ContingencyPreScreenResult(
                    id,
                    classification,
                    branchActions.size(),
                    counts.openCount(),
                    counts.closeCount(),
                    0,
                    List.of(),
                    originalSize,
                    effectiveSize,
                    classification == Classification.NORMAL
                            ? "MLODF [E-PTDF] is full rank"
                            : "MLODF [E-PTDF] was singular/compacted");
        }
        catch (Exception e) {
            if (isEptdfSingular(e)) {
                return new ContingencyPreScreenResult(
                        id,
                        Classification.E_PTDF_SINGULAR,
                        branchActions.size(),
                        counts.openCount(),
                        counts.closeCount(),
                        0,
                        List.of(),
                        branchActions.size(),
                        0,
                        e.getMessage());
            }
            return new ContingencyPreScreenResult(
                    id,
                    Classification.UNSUPPORTED,
                    branchActions.isEmpty() && definition != null && definition.actions != null
                            ? definition.actions.size()
                            : branchActions.size(),
                    counts.openCount(),
                    counts.closeCount(),
                    0,
                    List.of(),
                    0,
                    0,
                    e.getMessage());
        }
    }

    private static boolean isEptdfSingular(Exception e) {
        String message = e.getMessage();
        return message != null
                && (message.contains("[E - TPDF] is singular")
                        || message.contains("[E - PTDF] is singular")
                        || message.contains("Matrix [E - PTDF] is singular"));
    }

    private static ContingencyPreScreenReport summarize(List<ContingencyPreScreenResult> results) {
        int islanding = 0;
        int singular = 0;
        int normal = 0;
        int unsupported = 0;
        for (ContingencyPreScreenResult result : results) {
            switch (result.classification()) {
                case ISLANDING -> islanding++;
                case E_PTDF_SINGULAR -> singular++;
                case NORMAL -> normal++;
                case UNSUPPORTED -> unsupported++;
            }
        }
        return new ContingencyPreScreenReport(
                results.size(),
                islanding,
                singular,
                normal,
                unsupported,
                results);
    }

    private static List<ContingencyAction> branchActions(ContingencyDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Contingency definition cannot be null");
        }
        if (definition.actions == null || definition.actions.isEmpty()) {
            throw new IllegalArgumentException("Contingency has no actions: " + definition.name);
        }
        List<ContingencyAction> actions = new ArrayList<>();
        for (ContingencyAction action : definition.actions) {
            if (action == null) {
                throw new IllegalArgumentException("Null contingency action in " + definition.name);
            }
            if (action.objectType != ContingencyObjectType.BRANCH) {
                throw new IllegalArgumentException(
                        "Unsupported contingency object type in " + definition.name + ": " + action.objectType);
            }
            if (action.actionType != ContingencyActionType.OPEN
                    && action.actionType != ContingencyActionType.CLOSE) {
                throw new IllegalArgumentException(
                        "Unsupported contingency action type in " + definition.name + ": " + action.actionType);
            }
            actions.add(action);
        }
        return actions;
    }

    private static ActionCounts actionCounts(List<ContingencyAction> actions) {
        int open = 0;
        int close = 0;
        for (ContingencyAction action : actions) {
            if (action.actionType == ContingencyActionType.OPEN) {
                open++;
            }
            else if (action.actionType == ContingencyActionType.CLOSE) {
                close++;
            }
        }
        return new ActionCounts(open, close);
    }

    private static TopologyScreen screenTopology(AclfNetwork net, List<ContingencyAction> actions) {
        Map<String, AclfBus> activeBuses = new LinkedHashMap<>();
        for (AclfBus bus : net.getBusList()) {
            if (bus.isActive()) {
                activeBuses.put(bus.getId(), bus);
            }
        }
        if (activeBuses.isEmpty()) {
            return new TopologyScreen(List.of());
        }

        Set<String> openBranchIds = new HashSet<>();
        Set<String> closeBranchIds = new HashSet<>();
        for (ContingencyAction action : actions) {
            AclfBranch branch = resolveBranch(net, action);
            if (branch == null) {
                throw new IllegalArgumentException("Cannot resolve branch action: " + action.objectId);
            }
            if (action.actionType == ContingencyActionType.OPEN) {
                openBranchIds.add(branch.getId());
            }
            else if (action.actionType == ContingencyActionType.CLOSE) {
                closeBranchIds.add(branch.getId());
            }
        }

        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (String busId : activeBuses.keySet()) {
            adjacency.put(busId, new ArrayList<>());
        }
        Set<String> addedEdges = new HashSet<>();
        for (AclfBranch branch : net.getBranchList()) {
            boolean includeBase = branch.isActive() && !openBranchIds.contains(branch.getId());
            boolean includeClose = closeBranchIds.contains(branch.getId()) && !openBranchIds.contains(branch.getId());
            if (includeBase || includeClose) {
                addBranchEdge(branch, activeBuses, adjacency, addedEdges);
            }
        }

        String refBusId = net.getRefBusId();
        Set<String> refComponent = refBusId == null ? Set.of() : connectedComponent(adjacency, refBusId);
        List<List<String>> islandBusIdComponents = new ArrayList<>();
        Set<String> visited = new HashSet<>(refComponent);
        for (String busId : activeBuses.keySet()) {
            if (visited.contains(busId)) {
                continue;
            }
            Set<String> component = connectedComponent(adjacency, busId);
            visited.addAll(component);
            islandBusIdComponents.add(component.stream().sorted().toList());
        }
        islandBusIdComponents.sort(Comparator
                .<List<String>>comparingInt(List::size)
                .reversed()
                .thenComparing(component -> component.isEmpty() ? "" : component.get(0)));
        return new TopologyScreen(islandBusIdComponents);
    }

    private static AclfBranch resolveBranch(AclfNetwork net, ContingencyAction action) {
        AclfBranch branch = action.objectId == null ? null : net.getBranch(action.objectId);
        if (branch != null) {
            return branch;
        }
        if (action.objectId != null) {
            String reverse = reverseBranchId(action.objectId);
            if (reverse != null) {
                branch = net.getBranch(reverse);
                if (branch != null) {
                    return branch;
                }
            }
        }
        if (action.extUID != null && !action.extUID.isBlank()) {
            for (AclfBranch candidate : net.getBranchList()) {
                if (action.extUID.equals(candidate.getExtUID())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String reverseBranchId(String branchId) {
        int arrow = branchId.indexOf("->");
        int openParen = branchId.lastIndexOf('(');
        int closeParen = branchId.endsWith(")") ? branchId.length() - 1 : -1;
        if (arrow <= 0 || openParen <= arrow + 2 || closeParen <= openParen) {
            return null;
        }
        return branchId.substring(arrow + 2, openParen)
                + "->"
                + branchId.substring(0, arrow)
                + branchId.substring(openParen);
    }

    private static void addBranchEdge(
            AclfBranch branch,
            Map<String, AclfBus> activeBuses,
            Map<String, List<String>> adjacency,
            Set<String> addedEdges) {
        if (branch.isGroundBranch()) {
            return;
        }
        String from = branch.getFromBus().getId();
        String to = branch.getToBus().getId();
        if (!activeBuses.containsKey(from) || !activeBuses.containsKey(to)) {
            return;
        }
        String edgeKey = branch.getId();
        if (!addedEdges.add(edgeKey)) {
            return;
        }
        adjacency.get(from).add(to);
        adjacency.get(to).add(from);
    }

    private static Set<String> connectedComponent(Map<String, List<String>> adjacency, String seedBusId) {
        if (seedBusId == null || !adjacency.containsKey(seedBusId)) {
            return Set.of();
        }
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        visited.add(seedBusId);
        queue.add(seedBusId);
        while (!queue.isEmpty()) {
            String busId = queue.removeFirst();
            for (String next : adjacency.getOrDefault(busId, List.of())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    private record ActionCounts(int openCount, int closeCount) {
    }

    private record TopologyScreen(List<List<String>> islandBusIdComponents) {
        private TopologyScreen {
            islandBusIdComponents = List.copyOf(islandBusIdComponents);
        }

        boolean islanded() {
            return !islandBusIdComponents.isEmpty();
        }

        List<Integer> islandBusCounts() {
            return islandBusIdComponents.stream()
                    .map(List::size)
                    .toList();
        }
    }
}
