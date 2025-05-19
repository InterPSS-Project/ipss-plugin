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
	// sensitivity threshold for the optimization
	final static double SEN_THRESHOLD = 0.02;
	
	// a contingency analysis algorithm object based on which the optimization is performed
	protected ContingencyAnalysisAlgorithm dclfAlgo;
	
	/**
	 * Constructor
	 * 
	 * @param dclfAlgo
	 */
	public AclfNetLoadFlowOptimizer(ContingencyAnalysisAlgorithm dclfAlgo) {
		this.dclfAlgo = dclfAlgo;
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
	 * Optimize the generator state of the network based on the SSA analysis results
	 * 
	 * @param result SSA analysis result container
	 * @param threshold over limit threshold in percentage
	 */
	public void optimize(AclfNetSsaResultContainer result, double threshold) {
		AclfNetwork aclfNet = dclfAlgo.getAclfNet();

		AclfNetSensHelper helper = new AclfNetSensHelper(aclfNet);
		float[][] senMatrix = helper.calSen();
		
		Map<Integer, AclfGen> controlGenMap = null;
		if (result == null) {
			controlGenMap = arrangeIndex(aclfNet.getAclfGenNameLookupTable().values().stream().filter(gen -> gen.isActive())
					.collect(Collectors.toSet()));
		} else {
			controlGenMap = arrangeIndex(buildControlGenSet(senMatrix, result));
		}
		
		GenStateOptimizer opt = new GenStateOptimizer();
		
		buildSectionConstrain(senMatrix, controlGenMap, opt, threshold);

		buildGenConstrain(controlGenMap, opt);

		opt.optimize();

		updatedDclfalgo(controlGenMap, opt);
	}

	private static Map<Integer, AclfGen> arrangeIndex(Set<AclfGen> controlGenSet) {
		Map<Integer, AclfGen> genMap = new HashMap<Integer, AclfGen>();
		int index = 0;
		for (AclfGen gen : controlGenSet) {
			genMap.put(index++, gen);
//			System.out.print(gen.getName()+",");
		}
		return genMap;
	}

	protected Set<AclfGen> buildControlGenSet(float[][] senMatrix, AclfNetSsaResultContainer result) {
		Set<AclfGen> genSet = new LinkedHashSet<AclfGen>();
		result.getBaseOverLimitInfo().forEach(info -> {
			processGenSet(senMatrix, genSet, info.getBranch().getId());

		});

		result.getCaOverLimitInfo().forEach(info -> {
			processGenSet(senMatrix, genSet, info.aclfBranch.getId());
			processGenSet(senMatrix, genSet, info.contingency.getOutageBranch().getBranch().getId());
		});
		return genSet;
	}

	private void processGenSet(float[][] senMatrix, Set<AclfGen> genSet, String branchId) {
		AclfNetwork net = dclfAlgo.getAclfNet();
		AclfBranch branch = net.getBranch(branchId);
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

	protected void updatedDclfalgo(Map<Integer, AclfGen> genMap, GenStateOptimizer opt) {
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		for (int i = 0; i < genMap.size(); i++) {
			if (Math.abs(opt.getPoint()[i]) > 1) {
				AclfGen gen = genMap.get(i);
				// TODO output the result
				System.out.println(gen.getName() + "," + opt.getPoint()[i] + " mw");
				dclfAlgo.getDclfAlgoBus(gen.getParentBus().getId()).getGen(gen.getName()).get()
						.setAdjust(opt.getPoint()[i] / baseMva);
			}
		}
	}

	protected void buildSectionConstrain(float[][] senMatrix,
			Map<Integer, AclfGen> controlGenMap, GenStateOptimizer opt, double threshold) {
		AclfNetwork net = dclfAlgo.getAclfNet();
		double baseMva = net.getBaseMva();
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
				double flowMw = Math.abs(dclfBranch.getDclfFlow() * baseMva);
				opt.adConstraint(new SectionConstrainData(flowMw, Relationship.LEQ, limit, genSenArray));
			}
		});
	}

	protected void buildGenConstrain(Map<Integer, AclfGen> genMap, GenStateOptimizer opt) {
		AclfNetwork net = dclfAlgo.getAclfNet();
		double baseMva = net.getBaseMva();
		genMap.forEach((no, gen) -> {
			LimitType genLimit = gen.getPGenLimit();
			opt.adConstraint(
					new GenConstrainData(gen.getGen().getReal() * baseMva, Relationship.LEQ, genLimit.getMax() * 100, no));
			opt.adConstraint(
					new GenConstrainData(gen.getGen().getReal() * baseMva, Relationship.GEQ, genLimit.getMin() * 100, no));
		});
	}

}
