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
import com.interpss.core.aclf.BaseAclfBus;
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
public abstract class BaseAclfNetLoadFlowOptimizer {

	protected final static double SEN_THRESHOLD = 0.02;
	protected final static double GEN_DISPATCH_THRESHOLD = 1.0;

	//protected float[][] senMatrix = null;

	/**
	 * Optimize the generator dispatch by adjusting the generator output power to minimize the branch loading.
	 * @param dclfAlgo ContingencyAnalysisAlgorithm object, the DCLF algorithm object.
	 * @param result OptAdjResultContainer object, if null, all active generators are control candidates, otherwise, the control generators are the ones that are associatd with the over limit branches in the SSA result
	 * @param threshold The loading limit in percent (e.g. 100.0 for 100% of rating).
	 * @return Map<String, OptAdjResultContainer.GenAdjustResult> keyed by generator name.
	 */
	public Map<String, OptAdjResultContainer.GenAdjustResult> optimize(ContingencyAnalysisAlgorithm dclfAlgo, OptAdjResultContainer ssaResult, double threshold) {
		if (ssaResult != null) {
			ssaResult.setOptAdjThreshold(threshold);
		}

		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();

		// we use branch id to get the branch object. So we don't need to create the branch name lookup table.
		//if (net.getAclfBranchNameLookupTable() == null) {
		//	net.createAclfBranchNameLookupTable(true);
		//}
		if (net.getAclfGenNameLookupTable() == null) {
			net.createAclfGenNameLookupTable(true);
		}

		createSenMatrix(net, ssaResult);
		
		Map<Integer, AclfGen> controlGenMap = null;
		if (ssaResult == null) {
			// if result is null, all active generators are control candidates
			controlGenMap = arrangeIndex(net.getAclfGenNameLookupTable().values().stream().filter(gen -> gen.isActive())
					.collect(Collectors.toSet()));
		} else {
			// if result is not null, the control generators are the ones that are associatd with the over limit branches in the SSA result
			controlGenMap = arrangeIndex(buildControlGenSet(net, ssaResult));
		}
		
		GenStateOptimizer opt = new GenStateOptimizer();
		
		if (ssaResult == null) {
			buildSectionConstrain(dclfAlgo, controlGenMap, opt, threshold);
		}
		else {
			buildSsaSectionConstrain(dclfAlgo, controlGenMap, opt, threshold, ssaResult);
		}
	
		buildGenConstrain(controlGenMap, opt, net);

		opt.optimize();

		Map<String, OptAdjResultContainer.GenAdjustResult> optResults = updatedDclfAlgo(dclfAlgo, controlGenMap, opt);
		if (ssaResult != null) {
			ssaResult.setOptAdjResults(optResults);
		}
		return optResults;
	}

	abstract void createSenMatrix(AclfNetwork net, SsaResultContainer ssaResult);

	abstract float getSen(int busNo, int branchNo);

	protected int getBusSenIndex(BaseAclfBus<?, ?> bus) {
		return (int) (bus.getNumber() - 1);
	}

	protected int getBranchSenIndex(AclfBranch branch) {
		return (int) (branch.getNumber() - 1);
	}

	/* Please note: this method is not used anymore, since we use branch id to get the branch object.
	protected AclfBranch resolveBranch(AclfNetwork net, String branchKey) {
		if (net.getAclfBranchNameLookupTable() != null) {
			AclfBranch branch = net.getAclfBranchNameLookupTable().get(branchKey);
			if (branch != null) {
				return branch;
			}
		}
		return net.getBranch(branchKey);
	}
	*/

	private Map<Integer, AclfGen> arrangeIndex(Set<AclfGen> controlGenSet) {
		Map<Integer, AclfGen> genMap = new HashMap<Integer, AclfGen>();
		int index = 0;
		for (AclfGen gen : controlGenSet) {
			genMap.put(index++, gen);
//			System.out.print(gen.getName()+",");
		}
		return genMap;
	}

	protected Set<String> buildGenParentBusSet(AclfNetwork net) {
		return net.getAclfGenNameLookupTable().values().stream()
				.filter(AclfGen::isActive)
				.map(gen -> gen.getParentBus().getId())
				.collect(Collectors.toSet());
	}

	protected Set<String> buildSsaBranchSet(SsaResultContainer ssaResult) {
		Set<String> branchSet = new LinkedHashSet<>();
		ssaResult.getBaseOverLimitInfo().forEach(info -> branchSet.add(info.getOverLimitBranchId()));
		ssaResult.getCaOverLimitInfo().forEach(info -> {
			branchSet.add(info.getOverLimitBranchId());
			branchSet.add(info.getOutageBranchId());
		});
		return branchSet;
	}

	protected Set<AclfGen> buildControlGenSet(AclfNetwork net, SsaResultContainer result) {
		Set<AclfGen> genSet = new LinkedHashSet<AclfGen>();
		result.getBaseOverLimitInfo().forEach(info -> {
			processGenSet(net, genSet, info.getOverLimitBranchId());

		});

		result.getCaOverLimitInfo().forEach(info -> {
			processGenSet(net, genSet, info.getOverLimitBranchId());
			processGenSet(net, genSet, info.getOutageBranchId());
		});
		return genSet;
	}

	private void processGenSet(AclfNetwork net, Set<AclfGen> genSet, String branchId) {
		AclfBranch branch = net.getBranch(branchId);
		if (branch == null) {
			return;
		}
		int branchNo = getBranchSenIndex(branch);
		net.getAclfGenNameLookupTable().forEach((name, gen) -> {
			if (gen.isActive()) {
				int busNo = getBusSenIndex(gen.getParentBus());
				float sen = getSen(busNo, branchNo);
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

	protected void buildSsaSectionConstrain(ContingencyAnalysisAlgorithm dclfAlgo, 
			Map<Integer, AclfGen> controlGenMap, GenStateOptimizer opt, double threshold,
			SsaResultContainer ssaResult) {
		if (ssaResult.getBaseOverLimitInfo().isEmpty()) {
			return;
		}
		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();
		ssaResult.getBaseOverLimitInfo().forEach(info -> {
			AclfBranch branch = net.getBranch(info.getOverLimitBranchId());
			if (branch == null) {
				return;
			}
			int branchNo = getBranchSenIndex(branch);
			addBranchSectionConstraint(controlGenMap, opt, branchNo, info.getBaseFlowMW() > 0,
					Math.abs(info.getBaseFlowMW()), info.getLimitMW(), threshold);
		});
	}

	protected void buildSectionConstrain(ContingencyAnalysisAlgorithm dclfAlgo,
			Map<Integer, AclfGen> controlGenMap, GenStateOptimizer opt, double threshold) {
		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();
		net.getBranchList().stream().filter(AclfBranch::isActive).forEach(branch -> {
			int branchNo = getBranchSenIndex(branch);
			DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(branch.getId());
			addBranchSectionConstraint(controlGenMap, opt, branchNo, dclfBranch.getDclfFlow() > 0,
					Math.abs(dclfBranch.getDclfFlow() * 100), dclfBranch.getBranch().getRatingMvaA(), threshold);
		});
	}

	protected double[] buildGenSenArray(Map<Integer, AclfGen> controlGenMap, int branchNo,
			boolean flowPositive) {
		double[] genSenArray = new double[controlGenMap.size()];
		controlGenMap.forEach((no, gen) -> {
			int busNo = getBusSenIndex(gen.getParentBus());
			float sen = getSen(busNo, branchNo);
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
		addSectionConstraintUnchecked(opt, flowMw, ratingMw, threshold, genSenArray);
	}

	protected void addSectionConstraintUnchecked(GenStateOptimizer opt, double flowMw, double ratingMw,
			double threshold, double[] genSenArray) {
		double limit = ratingMw * threshold * 0.01;
		opt.adConstraint(new SectionConstrainData(flowMw, Relationship.LEQ, limit, genSenArray));
	}

	protected void addBranchSectionConstraint(Map<Integer, AclfGen> controlGenMap,
			GenStateOptimizer opt, int branchNo, boolean flowPositive, double flowMw, double ratingMw,
			double threshold) {
		double[] genSenArray = buildGenSenArray(controlGenMap, branchNo, flowPositive);
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
