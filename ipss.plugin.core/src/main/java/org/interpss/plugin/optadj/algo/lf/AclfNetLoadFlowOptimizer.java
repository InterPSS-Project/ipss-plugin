package org.interpss.plugin.optadj.algo.lf;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.util.AclfNetSensHelper;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBus;
import com.interpss.core.algo.dclf.adapter.DclfAlgoGen;

import org.interpss.plugin.optadj.optimizer.GenStateOptimizer;
import org.interpss.plugin.optadj.optimizer.bean.GenConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
import org.interpss.plugin.optadj.result.SsaResultContainer;

/**
 * 
 * @author Donghao.F
 * 
 * @date 2025 Apr 17 11:20:49
 * 
 * 
 * 
 */
public class AclfNetLoadFlowOptimizer {

	final static double SEN_THRESHOLD = 0.02;
	final static double GEN_DISPATCH_THRESHOLD = 1.0;

	/**
	 * Optimize the generator dispatch by adjusting the generator output power to minimize the branch loading.
	 * @param dclfAlgo ContingencyAnalysisAlgorithm object, the DCLF algorithm object.
	 * @param result OptAdjResultContainer object, if null, all active generators are control candidates, otherwise, the control generators are the ones that are associatd with the over limit branches in the SSA result
	 * @param threshold The loading limit in percent (e.g. 100.0 for 100% of rating).
	 * @return Map<String, OptAdjResultContainer.GenAdjustResult> keyed by generator name.
	 */
	public Map<String, OptAdjResultContainer.GenAdjustResult> optimize(ContingencyAnalysisAlgorithm dclfAlgo, OptAdjResultContainer result, double threshold) {
		if (result != null) {
			result.setOptAdjThreshold(threshold);
		}

		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();

		AclfNetSensHelper helper = new AclfNetSensHelper(net);
		float[][] senMatrix = helper.calSen();

		if (net.getAclfBranchNameLookupTable() == null) {
			net.createAclfBranchNameLookupTable(true);
		}
		if (net.getAclfGenNameLookupTable() == null) {
			net.createAclfGenNameLookupTable(true);
		}
		
		Map<Integer, AclfGen> controlGenMap = null;
		if (result == null) {
			// if result is null, all active generators are control candidates
			controlGenMap = arrangeIndex(net.getAclfGenNameLookupTable().values().stream().filter(gen -> gen.isActive())
					.collect(Collectors.toSet()));
		} else {
			// if result is not null, the control generators are the ones that are associatd with the over limit branches in the SSA result
			controlGenMap = arrangeIndex(buildControlGenSet(net, senMatrix, result));
		}
		
		GenStateOptimizer opt = new GenStateOptimizer();
		
		if (result != null) {
			buildSsaSectionConstrain(dclfAlgo, senMatrix, controlGenMap, opt, threshold, result);
		}
		else
			buildSectionConstrain(dclfAlgo, senMatrix, controlGenMap, opt, threshold);

		buildGenConstrain(controlGenMap, opt, net);

		opt.optimize();

		Map<String, OptAdjResultContainer.GenAdjustResult> optResults = updatedDclfAlgo(dclfAlgo, controlGenMap, opt);
		if (result != null) {
			result.setOptAdjResults(optResults);
		}
		return optResults;
	}

	private Map<Integer, AclfGen> arrangeIndex(Set<AclfGen> controlGenSet) {
		Map<Integer, AclfGen> genMap = new HashMap<Integer, AclfGen>();
		int index = 0;
		for (AclfGen gen : controlGenSet) {
			genMap.put(index++, gen);
//			System.out.print(gen.getName()+",");
		}
		return genMap;
	}

	protected Set<AclfGen> buildControlGenSet(AclfNetwork net, float[][] senMatrix,
			SsaResultContainer result) {
		Set<AclfGen> genSet = new LinkedHashSet<AclfGen>();
		result.getBaseOverLimitInfo().forEach(info -> {
			processGenSet(net, senMatrix, genSet, info.getOverLimitBranchId());

		});

		result.getCaOverLimitInfo().forEach(info -> {
			processGenSet(net, senMatrix, genSet, info.getOverLimitBranchId());
			processGenSet(net, senMatrix, genSet, info.getOutageBranchId());
		});
		return genSet;
	}

	private void processGenSet(AclfNetwork net, float[][] senMatrix, Set<AclfGen> genSet, String branchName) {
		AclfBranch branch = net.getAclfBranchNameLookupTable().get(branchName);
		if (branch == null) {
			return;
		}
		int branchNo = (int) (branch.getNumber() - 1);
		net.getAclfGenNameLookupTable().forEach((name, gen) -> {
			if (gen.isActive()) {
				int busNo = (int) (gen.getParentBus().getNumber() - 1);
				float sen = senMatrix[busNo][branchNo];
				if (Math.abs(sen) > SEN_THRESHOLD) {
					genSet.add(gen);
				}
			}
		});
	}

	protected Map<String, OptAdjResultContainer.GenAdjustResult> updatedDclfAlgo(ContingencyAnalysisAlgorithm dclfAlgo, Map<Integer, AclfGen> genMap, GenStateOptimizer opt) {
		Map<String, OptAdjResultContainer.GenAdjustResult> results = new HashMap<>();
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		for (int i = 0; i < genMap.size(); i++) {
			double dP = opt.getDGenP(i);
			if (Math.abs(dP) > GEN_DISPATCH_THRESHOLD) {
				AclfGen gen = genMap.get(i);
				DclfAlgoBus dclfBus = dclfAlgo.getDclfAlgoBus(gen.getParentBus().getId());
				// Please note: we use gen Id to get the DclfAlgoGen object.
				DclfAlgoGen dclfGen = dclfBus.getGen(gen.getId()).get();
				results.put(gen.getName(), new OptAdjResultContainer.GenAdjustResult(dclfGen.getGenP()*baseMva, dP, gen.getPGenLimit().multiply(baseMva)));
				dclfGen.setAdjust(dP * 0.01);
				//System.out.println(gen.getName() + ", dP:" + dP + ", genP:" + dclfGen.getGenP() + ", genLimit: " + gen.getPGenLimit());
			}
		}
		return results;
	}

	protected void buildSsaSectionConstrain(ContingencyAnalysisAlgorithm dclfAlgo, float[][] senMatrix,
			Map<Integer, AclfGen> controlGenMap, GenStateOptimizer opt, double threshold,
			SsaResultContainer ssaResult) {
		if (ssaResult.getBaseOverLimitInfo().isEmpty()) {
			return;
		}
		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();
		ssaResult.getBaseOverLimitInfo().forEach(info -> {
			AclfBranch branch = net.getAclfBranchNameLookupTable().get(info.getOverLimitBranchId());
			if (branch == null) {
				return;
			}
			int branchNo = (int) (branch.getNumber() - 1);
			addBranchSectionConstraint(senMatrix, controlGenMap, opt, branchNo, info.getBaseFlowMW() > 0,
					Math.abs(info.getBaseFlowMW()), info.getLimitMW(), threshold);
		});
	}

	protected void buildSectionConstrain(ContingencyAnalysisAlgorithm dclfAlgo, float[][] senMatrix,
			Map<Integer, AclfGen> controlGenMap, GenStateOptimizer opt, double threshold) {
		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();
		net.getBranchList().stream().filter(AclfBranch::isActive).forEach(branch -> {
			int branchNo = (int) (branch.getNumber() - 1);
			DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(branch.getId());
			addBranchSectionConstraint(senMatrix, controlGenMap, opt, branchNo, dclfBranch.getDclfFlow() > 0,
					Math.abs(dclfBranch.getDclfFlow() * 100), dclfBranch.getBranch().getRatingMvaA(), threshold);
		});
	}

	protected double[] buildGenSenArray(float[][] senMatrix, Map<Integer, AclfGen> controlGenMap, int branchNo,
			boolean flowPositive) {
		double[] genSenArray = new double[controlGenMap.size()];
		controlGenMap.forEach((no, gen) -> {
			int busNo = (int) (gen.getParentBus().getNumber() - 1);
			float sen = senMatrix[busNo][branchNo];
			genSenArray[no] = flowPositive ? sen : -sen;
		});
		return genSenArray;
	}

	protected boolean hasSignificantSensitivity(double[] genSenArray) {
		return Arrays.stream(genSenArray).anyMatch(sen -> Math.abs(sen) > SEN_THRESHOLD);
	}

	protected void addSectionConstraint(GenStateOptimizer opt, double flowMw, double ratingMw, double threshold,
			double[] genSenArray) {
		if (!hasSignificantSensitivity(genSenArray)) {
			return;
		}
		double limit = ratingMw * threshold / 100;
		opt.adConstraint(new SectionConstrainData(flowMw, Relationship.LEQ, limit, genSenArray));
	}

	protected void addBranchSectionConstraint(float[][] senMatrix, Map<Integer, AclfGen> controlGenMap,
			GenStateOptimizer opt, int branchNo, boolean flowPositive, double flowMw, double ratingMw,
			double threshold) {
		double[] genSenArray = buildGenSenArray(senMatrix, controlGenMap, branchNo, flowPositive);
		addSectionConstraint(opt, flowMw, ratingMw, threshold, genSenArray);
	}

	protected void buildGenConstrain(Map<Integer, AclfGen> genMap, GenStateOptimizer opt, AclfNetwork net) {
		genMap.forEach((no, gen) -> {
			LimitType genLimit = gen.getPGenLimit();
			opt.adConstraint(
					new GenConstrainData(gen.getGen().getReal() * 100, Relationship.LEQ, genLimit.getMax() * 100, no));
			opt.adConstraint(
					new GenConstrainData(gen.getGen().getReal() * 100, Relationship.GEQ, genLimit.getMin() * 100, no));
		});
	}

}
