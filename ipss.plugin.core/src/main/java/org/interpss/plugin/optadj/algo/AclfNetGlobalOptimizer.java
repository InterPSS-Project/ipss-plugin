package org.interpss.plugin.optadj.algo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.interpss.core.net.NameTag;
import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.interpss.plugin.optadj.algo.util.AclfNetGFSsHelper;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.interpss.plugin.optadj.optimizer.GenStateOptimizer;
import org.interpss.plugin.optadj.optimizer.bean.DeviceConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBus;

/**
 * AC Load Flow Global Optimizer for eliminating line overloads and/or N-1 contingency limit violations by optimizing 
 * generator and optional load injection. It uses full GFS matrix to identify control buses. It is recommended to be used 
 * for on-line application optimization.
 * 
 * @author Donghao.F
 */
public class AclfNetGlobalOptimizer extends BaseAclfNetOptimizer {
    private static final Logger log = LoggerFactory.getLogger(AclfNetGlobalOptimizer.class);
    
	// sensitivity threshold for the optimization
	final static double SEN_THRESHOLD = 0.02;

	// load limit factor, used to calculate the load limit
	//final static double LOAD_LIMIT_FACTOR = 1.5;
	
	// a contingency analysis algorithm object based on which the optimization is performed
	//protected ContingencyAnalysisAlgorithm dclfAlgo;
	
	// control generator map used in the optimization, key: index used in the optimizer, value: AclfGen object
	protected Map<Integer, AclfGen> controlGenMap;
	
	// control load map used in the optimization, key: index used in the optimizer, value: AclfLoad object
	protected Map<Integer, AclfLoad> controlLoadMap;
	
	/**
	 * Constructor
	 * 
	 * @param dclfAlgo
	 */
	public AclfNetGlobalOptimizer(ContingencyAnalysisAlgorithm dclfAlgo) {
		super(dclfAlgo);
	}
	
	/**
	 * Get the control generator map used in the optimization
	 * 
	 * @return the control generator map
	 */
	public Map<Integer, AclfGen> getControlGenMap() {
		return controlGenMap;
	}

	/**
	 * Get the control load map used in the optimization
	 *
	 * @return the control load map
	 */
	public Map<Integer, AclfLoad> getControlLoadMap() {
		return controlLoadMap;
	}

	/**
	 * Optimize the generator/load state of the network
	 * 
	 * @param threshold over limit threshold in percentage
	 * @param adjustGenOnly whether only adjust the generator state
	 */
	public void optimize(double threshold, boolean adjustGenOnly) {
		optimize(null, threshold, adjustGenOnly);
	}
	
	/**
	 * Optimize the generator/load state of the network based on the SSA analysis results.
	 * 
	 * Please note : When SSA result is provided, we only select the generators/loads with large enough Sen
		             related to the over limit branches for the optimization
	 * 
	 * @param result SSA (basecase plus optional N-1) analysis result container
	 * @param threshold over limit threshold in percentage
	 * @param adjustGenOnly whether only adjust the generator state
	 */
	public void optimize(AclfNetSsaResultContainer result, double threshold, boolean adjustGenOnly) {
		if (this.getOptimizer() == null)
			this.setOptimizer(new GenStateOptimizer());
		
		Set<AclfGen> fullGenSet = buildControlGenSet();
		Set<AclfLoad> fullLoadSet = buildControlLoadSet();
		AclfNetGFSsHelper helper = new AclfNetGFSsHelper((AclfNetwork) dclfAlgo.getNetwork());
		Sen2DMatrix gsfMatrix = helper.calGenLoadGFS(fullGenSet,
				adjustGenOnly ? Collections.emptySet() : fullLoadSet);
		
		Set<AclfGen> controlGenSet;
		Set<AclfLoad> controlLoadSet;
		if (result != null) {
			controlGenSet = buildControlGenSet(gsfMatrix, result);
			if (controlGenSet.isEmpty()) {
				controlGenSet = new LinkedHashSet<>(fullGenSet);
			}
			controlLoadSet = adjustGenOnly ? Collections.emptySet() : buildControlLoadSet(gsfMatrix, result);
		} else {
			controlGenSet = fullGenSet;
			controlLoadSet = adjustGenOnly ? Collections.emptySet() : fullLoadSet;
		}
		
		// create the control generator map from the control generator set
		controlGenMap = AclfNetGFSsHelper.arrangeIndex(controlGenSet);
		controlLoadMap = AclfNetGFSsHelper.arrangeIndex(controlLoadSet, controlGenMap.size());
		
		// build the branch section constraints based on the GFS matrix
		buildSectionConstrain(gsfMatrix, threshold);

		// build the generator output constraints
		buildGenConstrain();
		buildLoadConstrain();

		// perform the optimization
		getOptimizer().optimize(this.genOptSizeLimit, this.secOptSizeLimit);

		//
		updatedDclfAlgo();
	}

	/**
	 * Build the control load set based on the SSA result.
	 *
	 * @param gfsMatrix GFS matrix for candidate control buses
	 * @param result SSA result container
	 * @return the control load set
	 */
	protected Set<AclfLoad> buildControlLoadSet(Sen2DMatrix gfsMatrix, AclfNetSsaResultContainer result) {
		Set<AclfLoad> loadSet = new LinkedHashSet<>();
		result.getBaseOverLimitInfo().forEach(info -> {
			processLoadSet(gfsMatrix, loadSet, info.dclfBranch.getId());
		});

		result.getCaOverLimitInfo().forEach(info -> {
			processLoadSet(gfsMatrix, loadSet, info.aclfBranch.getId());
			info.getOutageEquips().forEach(outage -> processLoadSet(gfsMatrix, loadSet, outage.getBranch().getId()));
		});
		return loadSet;
	}

	private void processLoadSet(Sen2DMatrix gfsMatrix, Set<AclfLoad> loadSet, String branchId) {
		AclfNetwork net = dclfAlgo.getAclfNet();
		if (net.getAclfLoadNameLookupTable() == null) {
			net.createAclfLoadNameLookupTable(true);
		}
		AclfBranch branch = net.getBranch(branchId);
		int branchNo = branch.getSortNumber();
		net.getAclfLoadNameLookupTable().forEach((name, load) -> {
			if (load.isActive() && !load.getParentBus().isGen()) {
				int busNo = load.getParentBus().getSortNumber();
				double sen = gfsMatrix.get(busNo, branchNo);
				if (Math.abs(sen) > SEN_THRESHOLD) {
					loadSet.add(load);
				}
			}
		});
	}

	protected void buildLoadConstrain() {
		AclfNetwork net = dclfAlgo.getAclfNet();
		double baseMva = net.getBaseMva();
		controlLoadMap.forEach((no, load) -> {
			double currentLoadP = load.getLoadCP().getReal() * baseMva;
			double upperLimit = currentLoadP > 0 ? currentLoadP * LOAD_LIMIT_FACTOR : 0.0;
			double lowerLimit = currentLoadP > 0 ? 0.0 : currentLoadP * LOAD_LIMIT_FACTOR;
			getOptimizer().addConstraint(new DeviceConstrainData(currentLoadP, Relationship.LEQ, upperLimit, no,true));
			getOptimizer().addConstraint(new DeviceConstrainData(currentLoadP, Relationship.GEQ, lowerLimit, no,true));
		});
	}

	protected Set<AclfLoad> buildControlLoadSet() {
		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();
		if (net.getAclfLoadNameLookupTable() == null) {
			net.createAclfLoadNameLookupTable(true);
		}
		return net.getAclfLoadNameLookupTable().values().stream()
				.filter(load -> load.isActive() && !load.getParentBus().isGen())
				.collect(Collectors.toSet());
	}

	protected Set<AclfGen> buildControlGenSet() {
		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();
		if (net.getAclfGenNameLookupTable() == null) {
			net.createAclfGenNameLookupTable(true);
		}
		return net.getAclfGenNameLookupTable().values().stream()
				.filter(gen -> gen.isActive()).collect(Collectors.toSet());
	}

	/**
	 * Build the control generator set based on the SSA result.
	 *
	 * @param gfsMatrix GFS matrix for candidate control buses
	 * @param result SSA result container
	 * @return the control generator set
	 */
	protected Set<AclfGen> buildControlGenSet(Sen2DMatrix gfsMatrix, AclfNetSsaResultContainer result) {
		Set<AclfGen> genSet = new LinkedHashSet<>();
		result.getBaseOverLimitInfo().forEach(info -> {
			processGenSet(gfsMatrix, genSet, info.dclfBranch.getId());
		});

		result.getCaOverLimitInfo().forEach(info -> {
			processGenSet(gfsMatrix, genSet, info.aclfBranch.getId());
			info.getOutageEquips().forEach(outage -> processGenSet(gfsMatrix, genSet, outage.getBranch().getId()));
		});
		return genSet;
	}

	private void processGenSet(Sen2DMatrix gfsMatrix, Set<AclfGen> genSet, String branchId) {
		AclfNetwork net = dclfAlgo.getAclfNet();
		if (net.getAclfGenNameLookupTable() == null) {
			net.createAclfGenNameLookupTable(true);
		}
		AclfBranch branch = net.getBranch(branchId);
		int branchNo = branch.getSortNumber();
		net.getAclfGenNameLookupTable().forEach((name, gen) -> {
			if (gen.isActive()) {
				int busNo = gen.getParentBus().getSortNumber();
				double sen = gfsMatrix.get(busNo, branchNo);
				if (Math.abs(sen) > SEN_THRESHOLD) {
					genSet.add(gen);
				}
			}
		});
	}

	/**
	 * Build the generator output constraints
	 */
	protected void buildGenConstrain() {
		AclfNetwork net = dclfAlgo.getAclfNet();
		double baseMva = net.getBaseMva();
		controlGenMap.forEach((no, gen) -> {
				LimitType genLimit = gen.getPGenLimit();
				getOptimizer().addConstraint(new DeviceConstrainData(gen.getGen().getReal() * baseMva, 
													Relationship.LEQ, genLimit.getMax() * baseMva, no));
				getOptimizer().addConstraint(new DeviceConstrainData(gen.getGen().getReal() * baseMva, 
													Relationship.GEQ, genLimit.getMin() * baseMva, no));
		});
	}
	
	
	/**
	 * Get the optimization result map
	 * 
	 * @return the optimization result map, key: generator or load name, value: adjustment in PU
	 */
	public Map<String, Double> getResultMap() {
		Map<String, Double> resultMap = new HashMap<>();
		resultMap.putAll(getResultGenMap());
		resultMap.putAll(getResultLoadMap());
		return resultMap;
	}

	/**
	 * Get the optimization result map
	 * 
	 * @return the optimization result map, key: generator or load name, value: adjustment in PU
	 */
	public Map<String, Double> getResultGenMap() {
		Map<String, Double> resultMap = new HashMap<>();
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		for (int i = 0; i < controlGenMap.size(); i++) {
			if (Math.abs(getOptimizer().getPoint()[i]) > 1) {
				AclfGen gen = controlGenMap.get(i);
				resultMap.put("Gen:" + gen.getName(), getOptimizer().getPoint()[i] / baseMva);
			}
		}
		return resultMap;
	}

	/**
	 * Get the optimization result map
	 * 
	 * @return the optimization result map, key: generator or load name, value: adjustment in PU
	 */
	public Map<String, Double> getResultLoadMap() {
		Map<String, Double> resultMap = new HashMap<>();
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		controlLoadMap.forEach((index, load) -> {
			if (Math.abs(getOptimizer().getPoint()[index]) > 1) {
				resultMap.put("Load:" + load.getName(), getOptimizer().getPoint()[index] / baseMva);
			}
		});
		return resultMap;
	}

	/**
	 * Update the DCLF algorithm object gen.adjust based on the optimization result
	 */
	protected void updatedDclfAlgo() {
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		for (int i = 0; i < controlGenMap.size(); i++) {
			if (Math.abs(getOptimizer().getPoint()[i]) > 1) {
				AclfGen gen = controlGenMap.get(i);
				log.debug(gen.getName() + ", adj gen: " + getOptimizer().getPoint()[i] + " mw");
				DclfAlgoBus dclfBus = dclfAlgo.getDclfAlgoBus(gen.getParentBus().getId());
				dclfBus.getGen(gen.getId()).get()
						.setAdjust(getOptimizer().getPoint()[i] / baseMva);
			}
		}

		for (int i = 0; i < controlLoadMap.size(); i++) {
			int index = controlGenMap.size() + i;
			if (Math.abs(getOptimizer().getPoint()[index]) > 1) {
				AclfLoad load = controlLoadMap.get(index);
				log.debug(load.getName() + ", adj load: " + getOptimizer().getPoint()[index] + " mw");
				DclfAlgoBus dclfBus = dclfAlgo.getDclfAlgoBus(load.getParentBus().getId());
				dclfBus.getLoad(load.getId()).get()
						.setAdjust(getOptimizer().getPoint()[index] / baseMva);
			}
		}
	}

	/**
	 * Build the branch section constraints based on the GFS matrix
	 * 
	 * @param gfsMatrix GFS matrix for all generators
	 * @param threshold over limit threshold in percentage
	 */
	protected void buildSectionConstrain(Sen2DMatrix gfsMatrix, double threshold) {
		AclfNetwork net = dclfAlgo.getAclfNet();
		double baseMva = net.getBaseMva();
		net.getBranchList().stream().filter(branch -> branch.isActive()).forEach(branch -> {
			int branchNo = branch.getSortNumber();
			double[] genSenArray = new double[controlGenMap.size() + controlLoadMap.size()];
			DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(branch.getId());

			controlGenMap.forEach((no, gen) -> {
				int busNo = gen.getParentBus().getSortNumber();
				double sen = gfsMatrix.get(busNo, branchNo);
				genSenArray[no] = dclfBranch.getDclfFlow() > 0 ? sen : -sen;
			});

			controlLoadMap.forEach((no, load) -> {
				int busNo = load.getParentBus().getSortNumber();
				double sen = gfsMatrix.get(busNo, branchNo);
				int index =  no;
				genSenArray[index] = dclfBranch.getDclfFlow() > 0 ? -sen : sen;
			});
			
			if (Arrays.stream(genSenArray).anyMatch(sen -> Math.abs(sen) > SEN_THRESHOLD)) {
				double limit = dclfBranch.getBranch().getRatingMvaA() * threshold / 100;
				double flowMw = Math.abs(dclfBranch.getDclfFlow() * baseMva);
				getOptimizer().addConstraint(new SectionConstrainData(flowMw, Relationship.LEQ, limit, genSenArray));
			}
		});
	}
}
