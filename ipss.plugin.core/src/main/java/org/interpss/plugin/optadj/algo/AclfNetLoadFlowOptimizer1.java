package org.interpss.plugin.optadj.algo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.interpss.plugin.optadj.algo.util.AclfNetGFSsHelper;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.interpss.plugin.optadj.optimizer.BaseStateOptimizer;
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

/**
 * 
 * @author Donghao.F
 * 
 * @date 2025��4��17�� ����11:20:49
 * 
 * 
 * 
 */
public class AclfNetLoadFlowOptimizer1 {
    private static final Logger log = LoggerFactory.getLogger(AclfNetLoadFlowOptimizer1.class);
    
	// sensitivity threshold for the optimization
	final static double SEN_THRESHOLD = 0.4;
	
	// branch loading threshold for the optimization
	final static double LOADING_THRESHOLD = 50.0;
	
	// a contingency analysis algorithm object based on which the optimization is performed
	protected ContingencyAnalysisAlgorithm dclfAlgo;
	
	// control generator map used in the optimization, key: index used in the optimizer, value: AclfGen object
	protected Map<Integer, AclfGen> controlGenMap;
	
	protected Map<Integer, AclfLoad> controlLoadMap;
	
	// the GenState optimizer object created during the optimization
	protected BaseStateOptimizer optimizer;
	
	/**
	 * Constructor
	 * 
	 * @param dclfAlgo
	 */
	public AclfNetLoadFlowOptimizer1(ContingencyAnalysisAlgorithm dclfAlgo) {
		this.dclfAlgo = dclfAlgo;
	}
	
	/**
	 * Get the GenState optimizer object
	 * 
	 * @return the GenState optimizer object
	 */
	public BaseStateOptimizer getGenOptimizer() {
		return optimizer;
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
	 * Optimize the generator state of the network
	 * 
	 * @param threshold over limit threshold in percentage
	 */
	public void optimize(double threshold) {
		optimize(null, threshold);
	}
	
	/**
	 * Optimize the generator state of the network based on the SSA analysis results.
	 * 
	 * Please note : When SSA result is provided, we only select the generators with large enough Sen
		             related to the over limit branches for the optimization
	 * 
	 * @param result SSA (basecase plus optional N-1) analysis result container
	 * @param threshold over limit threshold in percentage
	 */
	public void optimize(AclfNetSsaResultContainer result, double threshold) {
		if (this.optimizer == null)
			this.optimizer = new GenStateOptimizer();
		
		
		Set<AclfGen> controlGenSet = buildControlGenSet();
		Set<AclfLoad> controlLoadSet = buildControlLoadSet();
		
		// we only calculate GFS matrix for all generators
		AclfNetGFSsHelper helper = new AclfNetGFSsHelper((AclfNetwork) dclfAlgo.getNetwork());
		Sen2DMatrix gsfMatrix = helper.calGenLoadGFS(controlGenSet, controlLoadSet);
		
		// When SSA result is provided, we only select the generators with large enough Sen
		// related to the over limit branches for the optimization
		if (result != null) {
			controlGenSet = buildControlGenSet(gsfMatrix, result);
			//TODO loadSet
		}
		
		// create the control generator map from the control generator set
		controlGenMap = AclfNetGFSsHelper.arrangeIndex(controlGenSet);
		
		// create the control generator map from the control generator set
		controlLoadMap = AclfNetGFSsHelper.arrangeIndex(controlLoadSet, controlGenMap.size());
		
		// build the branch section constraints based on the GFS matrix
		buildSectionConstrain(gsfMatrix, threshold);

		// build the generator output constraints
		buildGenConstrain();
		
		// build the generator output constraints
		buildLoadConstrain();

		// perform the optimization
		optimizer.optimize();

		//
		updatedDclfalgo();
	}
	
	protected void buildLoadConstrain() {
		// TODO Auto-generated method stub
		
	}

	protected Set<AclfLoad> buildControlLoadSet() {
		return new HashSet<AclfLoad>();
	}

	protected Set<AclfGen> buildControlGenSet() {
		return ((AclfNetwork) dclfAlgo.getNetwork()).getAclfGenNameLookupTable().values().stream()
				.filter(gen -> gen.isActive()).collect(Collectors.toSet());
	}

	/**
	 * Build the control generator set based on the SSA result
	 * 
	 * @param gfsMatrix GFS matrix for all generators
	 * @param result SSA result container
	 * @return the control generator set
	 */
	protected Set<AclfGen> buildControlGenSet(Sen2DMatrix gfsMatrix, AclfNetSsaResultContainer result) {
		Set<AclfGen> genSet = new LinkedHashSet<AclfGen>();
		result.getBaseOverLimitInfo().forEach(info -> {
			processGenSet(gfsMatrix, genSet, info.getBranch().getId());

		});

		result.getCaOverLimitInfo().forEach(info -> {
			processGenSet(gfsMatrix, genSet, info.aclfBranch.getId());
			processGenSet(gfsMatrix, genSet, info.contingency.getOutageBranch().getBranch().getId());
		});
		return genSet;
	}

	private void processGenSet(Sen2DMatrix gfsMatrix, Set<AclfGen> genSet, String branchId) {
		AclfNetwork net = dclfAlgo.getAclfNet();
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
	 * Get the optimization result map
	 * 
	 * @return the optimization result map, key: generator name, value: adjusted Pgen in PU
	 */
	public Map<String, Double> getResultMap() {
		Map<String, Double> resultMap = new HashMap<>();
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		for (int i = 0; i < controlGenMap.size(); i++) {
			if (Math.abs(optimizer.getPoint()[i]) > 1) {
				AclfGen gen = controlGenMap.get(i);
				resultMap.put(gen.getId(), optimizer.getPoint()[i] / baseMva);
			}
		}
		for (int i = controlGenMap.size(); i < controlLoadMap.size(); i++) {
			if (Math.abs(optimizer.getPoint()[i]) > 1) {
				AclfLoad load = controlLoadMap.get(i);
				resultMap.put(load.getId(), optimizer.getPoint()[i] / baseMva);
			}
		}
		return resultMap;
	}

	/**
	 * Update the DCLF algorithm object gen.adjust based on the optimization result
	 */
	protected void updatedDclfalgo() {
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		for (int i = 0; i < controlGenMap.size(); i++) {
			if (Math.abs(optimizer.getPoint()[i]) > 1) {
				AclfGen gen = controlGenMap.get(i);
				log.debug("genId: " + gen.getId() + ", genName: " + gen.getName() + ", adj gen: " + optimizer.getPoint()[i] + " mw");
				dclfAlgo.getDclfAlgoBus(gen.getParentBus().getId()).getGen(gen.getId()).get()
						.setAdjust(optimizer.getPoint()[i] / baseMva);
			}
		}

		for (int i = controlGenMap.size(); i < controlLoadMap.size(); i++) {
			if (Math.abs(optimizer.getPoint()[i]) > 1) {
				AclfLoad load = controlLoadMap.get(i);
				log.debug("loadId: " + load.getId() + ", loadName: " + load.getName() + ", adj load: " + optimizer.getPoint()[i] + " mw");
				dclfAlgo.getDclfAlgoBus(load.getParentBus().getId()).getLoad(load.getId()).get()
						.setAdjust(-optimizer.getPoint()[i] / baseMva); 
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
		net.getBranchList().stream()
			.filter(branch -> {
				double ratingMva = branch.getRatingMva1();
				double flowMw = dclfAlgo.getBranchFlow(branch, UnitType.mW);
				double loadingPercent = ratingMva > 0 ? (Math.abs(flowMw) / ratingMva) * 100.0 : 0.0;
				return branch.isActive() && loadingPercent > LOADING_THRESHOLD; })
			.forEach(branch -> {
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
					genSenArray[no] = dclfBranch.getDclfFlow() > 0 ? sen : -sen;
				});
				
				if (Arrays.stream(genSenArray).anyMatch(sen -> Math.abs(sen) > SEN_THRESHOLD)) {
					double limit = dclfBranch.getBranch().getRatingMva1() * threshold / 100;
					double flowMw = Math.abs(dclfBranch.getDclfFlow() * baseMva);
					double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMva1())*100;
					log.debug("Branch: " + dclfBranch.getId() + "  " + flowMw +
							" rating: " + dclfBranch.getBranch().getRatingMva1() +
							" loading: " + loading +
							" limit for OptAdj: " + limit);
					optimizer.addConstraint(new SectionConstrainData(flowMw, Relationship.LEQ, limit, genSenArray));
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
			optimizer.addConstraint(new DeviceConstrainData(gen.getGen().getReal() * baseMva, 
												Relationship.LEQ, genLimit.getMax() * baseMva, no));
			optimizer.addConstraint(new DeviceConstrainData(gen.getGen().getReal() * baseMva, 
												Relationship.GEQ, genLimit.getMin() * baseMva, no));
		});
	}

	public void setOptimizer(BaseStateOptimizer genOptimizer) {
		this.optimizer = genOptimizer;
	}
	
	

}
