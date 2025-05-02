package org.interpss.plugin.optadj.algo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.interpss.plugin.optadj.algo.util.AclfNetSensHelper;
import org.interpss.plugin.optadj.optimizer.GenStateOptimizer;
import org.interpss.plugin.optadj.optimizer.bean.GenConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
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
public class AclfNetLoadFlowOptimizer {

	final static double SEN_THRESHOLD = 0.02;

	public void optimize(ContingencyAnalysisAlgorithm dclfAlgo, AclfNetSsaResultContainer result, double threshold) {
		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();

		AclfNetSensHelper helper = new AclfNetSensHelper(net);
		float[][] senMatrix = helper.calSen();
		
		Map<Integer, AclfGen> controlGenMap = null;
		if (result == null) {
			controlGenMap = arrangeIndex(net.getAclfGenNameLookupTable().values().stream().filter(gen -> gen.isActive())
					.collect(Collectors.toSet()));
		} else {
			controlGenMap = arrangeIndex(buildControlGenSet(net, senMatrix, result));
		}
		
		GenStateOptimizer opt = new GenStateOptimizer();
		
		buildSectionConstrain(dclfAlgo, senMatrix, controlGenMap, opt, threshold);

		buildGenConstrain(controlGenMap, opt, net);

		opt.optimize();

		updatedDclfalgo(dclfAlgo, controlGenMap, opt);
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
			AclfNetSsaResultContainer result) {
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

	protected void updatedDclfalgo(ContingencyAnalysisAlgorithm dclfAlgo, Map<Integer, AclfGen> genMap,
			GenStateOptimizer opt) {
		for (int i = 0; i < genMap.size(); i++) {

			if (Math.abs(opt.getPoint()[i]) > 1) {
				AclfGen gen = genMap.get(i);
				// TODO output the result
				System.out.println(gen.getName() + "," + opt.getPoint()[i]);
				dclfAlgo.getDclfAlgoBus(gen.getParentBus().getId()).getGen(gen.getName()).get()
						.setAdjust(opt.getPoint()[i] / 100);
			}
		}
	}

	protected void buildSectionConstrain(ContingencyAnalysisAlgorithm dclfAlgo, float[][] senMatrix,
			Map<Integer, AclfGen> controlGenMap, GenStateOptimizer opt, double threshold) {
		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();
		net.getBranchList().stream().filter(branch -> branch.isActive()).forEach(branch -> {
			int branchNo = (int) (branch.getNumber() - 1);
			double[] genSenArray = new double[controlGenMap.size()];
			DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(branch.getId());

			controlGenMap.forEach((no, gen) -> {
				int busNo = (int) (gen.getParentBus().getNumber() - 1);
				float sen = senMatrix[busNo][branchNo];
				genSenArray[no] = dclfBranch.getDclfFlow() > 0 ? sen : -sen;
			});
			if (Arrays.stream(genSenArray).anyMatch(sen -> Math.abs(sen) > SEN_THRESHOLD)) {
				double limit = dclfBranch.getBranch().getRatingMva1() * threshold / 100;
				double flowMw = Math.abs(dclfBranch.getDclfFlow() * 100);
				opt.adConstraint(new SectionConstrainData(flowMw, Relationship.LEQ, limit, genSenArray));
			}
		});
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
