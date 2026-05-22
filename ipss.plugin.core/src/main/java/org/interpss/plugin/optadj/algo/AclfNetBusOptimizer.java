package org.interpss.plugin.optadj.algo;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.optadj.algo.util.AclfNetGFSsHelper;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.interpss.plugin.optadj.optimizer.GenStateOptimizer;
import org.interpss.plugin.optadj.optimizer.bean.DeviceConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBus;
import com.interpss.core.algo.dclf.adapter.DclfAlgoGen;
import com.interpss.core.algo.dclf.adapter.DclfAlgoLoad;

/**
 * AC Load Flow Bus Optimizer for eliminating line overloads by optimizing bus-level
 * generator and optional load injection. Uses GFS (Generation Shift Factor) analysis
 * to identify control buses. Load-only buses at heavily loaded branch terminals may
 * be included. It is recommended to use this optimizer for off-line large scale optimization.
 * 
 * @author Donghao.F
 */
public class AclfNetBusOptimizer extends BaseAclfNetOptimizer {
    private static final Logger log = LoggerFactory.getLogger(AclfNetBusOptimizer.class);
    
    // Configuration constants
    // Gfs adjustment sensitivity threshold
    private static final double BUS_GFS_THRESHOLD = 0.20;
    // Section adjustment sensitivity threshold
    private static final double SECTION_GFS_THRESHOLD = 0.05;
    // Gen/Load optimization adjustment threshold in MW
    private static final double OPT_ADJUSTMENT_THRESHOLD_MW = 0.1;
    
    public static enum ControlBusRole {
        GEN, LOAD
    }
    
    protected final AclfNetGFSsHelper helper;
    protected final AclfNetwork network;
    
    private Map<String, DclfAlgoBranch> dclfBranchCache;
    
    protected Map<Integer, AclfBus> controlBusMap;
    /** Bus id -> role (GEN or LOAD) for buses in {@link #controlBusMap}. */
    private Map<String, ControlBusRole> controlBusRoles;

    // heavy loaded branch threshold factor, used to calculate the heavy loaded branch threshold
    private final double HEAVYLOAD_THRESHOLD_FACTOR = 0.9;
    protected Set<String> heavyLoadedBranchList;
    
    /**
     * Constructor for AclfNetLoadFlowBusOptimizer.
     * 
     * @param dclfAlgo DCLF algorithm object used for optimization
     */
    public AclfNetBusOptimizer(ContingencyAnalysisAlgorithm dclfAlgo) {
        super(dclfAlgo);
        this.network = (AclfNetwork) dclfAlgo.getNetwork();
        this.helper = new AclfNetGFSsHelper(network);
        this.dclfBranchCache = new HashMap<>();
    }

    /**
     * Get the GFSs helper instance.
     * 
     * @return the GFSs helper instance
     */
    public AclfNetGFSsHelper getGFSsHelper() {
        return helper;
    }

    /**
     * Get bus IDs of control buses used for load adjustment.
     *
     * @return load control bus id set (empty when gen-only optimization was used)
     */
    public Set<String> getControlLoadBusIdSet() {
        if (controlBusRoles == null) {
            return new HashSet<>();
        }
        return controlBusRoles.entrySet().stream()
            .filter(e -> e.getValue() == ControlBusRole.LOAD)
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(HashSet::new));
    }
    
    /**
     * Optimize generator states to eliminate line overloads (generator buses only).
     * 
     * @param threshold Overload threshold in percentage (e.g., 120 for 120%)
     */
    public void optimize(double threshold) {
        optimize(threshold, true);
    }

    /**
     * Optimize bus-level generator and optional load states to eliminate line overloads.
     *
     * @param threshold Overload threshold in percentage (e.g., 120 for 120%)
     * @param adjustGenOnly when true, only generator buses are used as control variables
     */
    public void optimize(double threshold, boolean adjustGenOnly) {
        if (this.getOptimizer() == null) {
            this.setOptimizer(new GenStateOptimizer());
        } else {
            this.getOptimizer().getGenConstrainDataList().clear();
            this.getOptimizer().getSecConstrainDataList().clear();
        }
        controlBusRoles = new HashMap<>();
        
        identifyHeavyLoadedBranches(threshold * HEAVYLOAD_THRESHOLD_FACTOR);
        
        if (heavyLoadedBranchList.isEmpty()) {
            log.debug("No overloaded branches found");
            return;
        }
        
        Set<AclfBus> controlBusSet = buildControlBusSet(adjustGenOnly);
        
        if (controlBusSet.isEmpty()) {
            log.debug("No suitable control buses found");
            return;
        }
        
        controlBusMap = AclfNetGFSsHelper.arrangeIndex(controlBusSet);
        
        Sen2DMatrix gfsMatrix = calculateGFSMatrix(controlBusSet);
        
        buildSectionConstraints(gfsMatrix, threshold);
        buildDeviceConstraints();
        
        getOptimizer().optimize(this.genOptSizeLimit, this.secOptSizeLimit);
        
        updateDclfAlgorithm();
    }

    /**
     * Build control bus set with sensitivity analysis.
     *
     * @param adjustGenOnly when true, exclude load-only buses from the control set
     */
    private Set<AclfBus> buildControlBusSet(boolean adjustGenOnly) {
        Map<ControlBusRole, Set<AclfBus>> candidatesByRole = new EnumMap<>(ControlBusRole.class);
        candidatesByRole.put(ControlBusRole.GEN, collectGenCandidateBuses());
        candidatesByRole.put(ControlBusRole.LOAD,
            adjustGenOnly ? Collections.emptySet() : collectLoadCandidateBuses());
        
        Set<AclfBus> allCandidates = candidatesByRole.values().stream()
            .flatMap(Set::stream)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        
        if (allCandidates.isEmpty()) {
            return new LinkedHashSet<>();
        }
        
        Set<String> candidateBusIds = allCandidates.stream()
            .map(AclfBus::getId)
            .collect(Collectors.toCollection(HashSet::new));
        
        Sen2DMatrix gfsMatrix = helper.calGFS(candidateBusIds, heavyLoadedBranchList);
        
        Set<AclfBus> controlBusSet = new LinkedHashSet<>();
        for (Map.Entry<ControlBusRole, Set<AclfBus>> entry : candidatesByRole.entrySet()) {
            ControlBusRole role = entry.getKey();
            entry.getValue().stream()
                .filter(bus -> hasSufficientSensitivity(gfsMatrix, bus))
                .forEach(bus -> {
                    controlBusSet.add(bus);
                    controlBusRoles.put(bus.getId(), role);
                });
        }
        
        return controlBusSet;
    }

    /**
     * Collect active generator buses as generation control candidates.
     */
    private Set<AclfBus> collectGenCandidateBuses() {
        return network.getBusList().stream()
            .filter(bus -> bus.isActive() && bus.isGen())
            .map(bus -> (AclfBus) bus)
            .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Collect load-only buses at terminals of heavily loaded branches as load control candidates.
     */
    private Set<AclfBus> collectLoadCandidateBuses() {
        Set<AclfBus> loadCandidateBuses = new HashSet<>();
        for (String branchId : heavyLoadedBranchList) {
            AclfBranch branch = network.getBranch(branchId);
            if (branch == null) {
                continue;
            }
            addLoadCandidateBus(loadCandidateBuses, (AclfBus) branch.getFromAclfBus());
            addLoadCandidateBus(loadCandidateBuses, (AclfBus) branch.getToAclfBus());
        }
        return loadCandidateBuses;
    }

    private void addLoadCandidateBus(Set<AclfBus> loadCandidateBuses, AclfBus bus) {
        if (bus == null || !bus.isActive() || !bus.isLoad() || bus.isGen()) {
            return;
        }
        double currentLoadP = bus.getLoadP() * network.getBaseMva();
        if (currentLoadP > OPT_ADJUSTMENT_THRESHOLD_MW) {
            loadCandidateBuses.add(bus);
        }
    }
    
    /**
     * Check if a bus has sufficient sensitivity to any overloaded branch.
     */
    private boolean hasSufficientSensitivity(Sen2DMatrix gfsMatrix, AclfBus bus) {
        int busNo = bus.getSortNumber();
        
        for (String branchId : heavyLoadedBranchList) {
            AclfBranch branch = network.getBranch(branchId);
            if (branch == null) continue;
            
            double sensitivity = gfsMatrix.get(busNo, branch.getSortNumber());
            if (Math.abs(sensitivity) > BUS_GFS_THRESHOLD) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Identify overloaded branches in the network.
     */
    private void identifyHeavyLoadedBranches(double threshold) {
        heavyLoadedBranchList = new HashSet<>();
        
        for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
            if (!isNonSwingBranch(dclfBranch)) continue;
            
            AclfBranch branch = dclfBranch.getBranch();
            double powerFlowMW = dclfAlgo.getBranchFlow(branch, UnitType.mW);
            double ratingMVA = branch.getRatingMvaA();
            
            if (ratingMVA <= 0) continue;
            
            double loadingPercent = Math.abs(powerFlowMW) / ratingMVA * 100.0;
            if (loadingPercent > threshold) {
                heavyLoadedBranchList.add(branch.getId());
            }
        }
        
        log.info("Found {} heavyly loaded branches", heavyLoadedBranchList.size());
    }
    
    /**
     * Check if branch is between non-swing buses.
     */
    private boolean isNonSwingBranch(DclfAlgoBranch branch) {
        AclfBranch aclfBranch = branch.getBranch();
        return !aclfBranch.getFromAclfBus().isSwing() 
            && !aclfBranch.getToAclfBus().isSwing();
    }
    
    /**
     * Calculate GFS matrix for control buses.
     */
    private Sen2DMatrix calculateGFSMatrix(Set<AclfBus> controlBusSet) {
        Set<String> controlBusIds = controlBusSet.stream()
            .map(AclfBus::getId)
            .collect(Collectors.toCollection(HashSet::new));
        
        return helper.calGFS(controlBusIds);
    }
    
    /**
     * Build section constraints.
     */
    private void buildSectionConstraints(Sen2DMatrix gfsMatrix, double threshold) {
        double baseMva = network.getBaseMva();
        
        for (AclfBranch branch : network.getBranchList()) {
            if (!branch.isActive()) continue;
            
            DclfAlgoBranch dclfBranch = getCachedDclfBranch(branch.getId());
            if (dclfBranch == null) continue;
            
            double[] genSenArray = new double[controlBusMap.size()];
            boolean hasSignificantSensitivity = false;
            
            for (Map.Entry<Integer, AclfBus> entry : controlBusMap.entrySet()) {
                int index = entry.getKey();
                AclfBus bus = entry.getValue();
                double gfsSen = gfsMatrix.get(bus.getSortNumber(), branch.getSortNumber());
                double sen = toSectionSensitivity(dclfBranch, gfsSen, getControlBusRole(bus));
                genSenArray[index] = sen;
                
                if (Math.abs(sen) > SECTION_GFS_THRESHOLD) {
                    hasSignificantSensitivity = true;
                }
            }
            
            if (hasSignificantSensitivity) {
                double limit = dclfBranch.getBranch().getRatingMvaA() * threshold / 100.0;
                double flowMw = Math.abs(dclfBranch.getDclfFlow() * baseMva);
                
                getOptimizer().addConstraint(new SectionConstrainData(
                    flowMw, Relationship.LEQ, limit, genSenArray));
            }
        }
    }

    /**
     * Convert bus GFS to section-constraint sensitivity; sign convention differs for gen vs load.
     */
    private double toSectionSensitivity(DclfAlgoBranch dclfBranch, double gfsSen, ControlBusRole role) {
        boolean positiveFlow = dclfBranch.getDclfFlow() > 0;
        if (role == ControlBusRole.LOAD) {
            return positiveFlow ? -gfsSen : gfsSen;
        }
        return positiveFlow ? gfsSen : -gfsSen;
    }
    
    private ControlBusRole getControlBusRole(AclfBus bus) {
        return controlBusRoles.getOrDefault(bus.getId(), ControlBusRole.GEN);
    }
    
    /**
     * Get DCLF branch with caching for performance.
     */
    private DclfAlgoBranch getCachedDclfBranch(String branchId) {
        DclfAlgoBranch branch = dclfBranchCache.get(branchId);
        if (branch == null) {
            branch = dclfAlgo.getDclfAlgoBranch(branchId);
            if (branch != null) {
                dclfBranchCache.put(branchId, branch);
            }
        }
        return branch;
    }
    
    /**
     * Build generator and load device constraints.
     */
    private void buildDeviceConstraints() {
        double baseMva = network.getBaseMva();
        
        for (Map.Entry<Integer, AclfBus> entry : controlBusMap.entrySet()) {
            int index = entry.getKey();
            AclfBus bus = entry.getValue();
            
            if (getControlBusRole(bus) == ControlBusRole.LOAD) {
                addLoadDeviceConstraints(bus, index, baseMva);
            } else {
                addGenDeviceConstraints(bus, index, baseMva);
            }
        }
    }

    private void addGenDeviceConstraints(AclfBus bus, int index, double baseMva) {
        LimitType genLimit = bus.getPGenLimit();
        double currentGenP = bus.getGenP() * baseMva;
        
        getOptimizer().addConstraint(new DeviceConstrainData(
            currentGenP, Relationship.LEQ, genLimit.getMax() * baseMva, index));
        getOptimizer().addConstraint(new DeviceConstrainData(
            currentGenP, Relationship.GEQ, genLimit.getMin() * baseMva, index));
    }

    private void addLoadDeviceConstraints(AclfBus bus, int index, double baseMva) {
        double currentLoadP = bus.getLoadP() * baseMva;
        double upperLimit = currentLoadP > 0 ? currentLoadP * LOAD_LIMIT_FACTOR : 0.0;
        double lowerLimit = currentLoadP > 0 ? 0.0 : currentLoadP * LOAD_LIMIT_FACTOR;
        getOptimizer().addConstraint(new DeviceConstrainData(
            currentLoadP, Relationship.LEQ, upperLimit, index, true));
        getOptimizer().addConstraint(new DeviceConstrainData(
            currentLoadP, Relationship.GEQ, lowerLimit, index, true));
    }
    
    /**
     * Update DCLF algorithm with optimization results.
     */
    private void updateDclfAlgorithm() {
        double baseMva = network.getBaseMva();
        
        for (int i = 0; i < controlBusMap.size(); i++) {
            double adjustmentMW = getOptimizer().getPoint()[i];
            
            if (Math.abs(adjustmentMW) <= OPT_ADJUSTMENT_THRESHOLD_MW) continue;
            
            AclfBus bus = controlBusMap.get(i);
            DclfAlgoBus dcBus = dclfAlgo.getDclfAlgoBus(bus.getId());
            if (dcBus == null) continue;
            
            log.debug("Bus {} adjustment: {} MW", bus.getName(), adjustmentMW);
            if (getControlBusRole(bus) == ControlBusRole.LOAD) {
                distributeAdjustmentToLoads(bus, dcBus, adjustmentMW, baseMva);
            } else {
                distributeAdjustmentToGenerators(bus, dcBus, adjustmentMW, baseMva);
            }
        }
    }
    
    /**
     * Distribute adjustment to individual generators.
     */
    private void distributeAdjustmentToGenerators(AclfBus bus, DclfAlgoBus dcBus, double adjustmentMW, double baseMva) {
        double totalBusP = bus.getGenP() + dcBus.getGenAdjust();
        distributeAdjustmentProportional(
            dcBus.getGenList(), totalBusP, adjustmentMW, baseMva,
            DclfAlgoGen::getGenP, DclfAlgoGen::setAdjust, "Gen", DclfAlgoGen::getId);
    }

    /**
     * Distribute adjustment to individual loads.
     */
    private void distributeAdjustmentToLoads(AclfBus bus, DclfAlgoBus dcBus, double adjustmentMW, double baseMva) {
        double totalBusP = bus.getLoadP() + dcBus.getLoadAdjust();
        distributeAdjustmentProportional(
            dcBus.getLoadList(), totalBusP, adjustmentMW, baseMva,
            DclfAlgoLoad::getLoadP, DclfAlgoLoad::setAdjust, "Load", DclfAlgoLoad::getId);
    }

    private <T> void distributeAdjustmentProportional(
            Iterable<T> devices,
            double totalBusP,
            double adjustmentMW,
            double baseMva,
            ToDoubleFunction<T> getP,
            BiConsumer<T, Double> setAdjustPu,
            String deviceLabel,
            Function<T, String> getId) {
        if (Math.abs(totalBusP) < 1e-6) {
            return;
        }
        double adjustmentPU = adjustmentMW / baseMva;
        for (T device : devices) {
            double ratio = getP.applyAsDouble(device) / totalBusP;
            double individualAdjustmentPU = adjustmentPU * ratio;
            setAdjustPu.accept(device, individualAdjustmentPU);
            log.debug("{} {} adjustment: {} MW", deviceLabel, getId.apply(device), individualAdjustmentPU * baseMva);
        }
    }
    
    /**
     * Get optimization results as a map.
     */
    public Map<String, Double> getResultMap() {
        Map<String, Double> resultMap = new HashMap<>();
        if (controlBusMap == null) {
            return resultMap;
        }
        double baseMva = network.getBaseMva();
        
        for (int i = 0; i < controlBusMap.size(); i++) {
            double adjustmentMW = getOptimizer().getPoint()[i];
            
            if (Math.abs(adjustmentMW) > OPT_ADJUSTMENT_THRESHOLD_MW) {
                AclfBus bus = controlBusMap.get(i);
                String prefix = getControlBusRole(bus) == ControlBusRole.LOAD ? "Load:" : "Gen:";
                resultMap.put(prefix + bus.getId(), adjustmentMW / baseMva);
            }
        }
        return resultMap;
    }
    
    /**
     * Get the control bus map.
     * 
     * @return the control bus map, key: bus index, value: bus
     */
    public Map<Integer, AclfBus> getControlGenMap() {
        return controlBusMap != null ? new HashMap<>(controlBusMap) : new HashMap<>();
    }

    /**
     * Get the control bus id set.
     * 
     * @return the control bus id set
     */
    public Set<String> getControlBusIdSet() {
        return this.getControlGenMap().values().stream()
				.map(AclfBus::getId)
				.collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Get the control bus roles.
     * 
     * @return the control bus roles, key: bus id, value: control bus role
     */
    public Map<String, ControlBusRole> getControlBusRoleMap() {
        return controlBusRoles != null ? new HashMap<>(controlBusRoles) : new HashMap<>();
    }
}
