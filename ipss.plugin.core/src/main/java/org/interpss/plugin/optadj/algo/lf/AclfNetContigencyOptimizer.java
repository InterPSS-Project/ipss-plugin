package org.interpss.plugin.optadj.algo.lf;

import java.util.Arrays;
import java.util.Map;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

import org.interpss.plugin.optadj.optimizer.GenStateOptimizer;
import org.interpss.plugin.optadj.result.SsaBranchOverLimitInfo;
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
public class AclfNetContigencyOptimizer extends AclfNetLoadFlowOptimizer {

	@Override
	protected void buildSsaSectionConstrain(ContingencyAnalysisAlgorithm dclfAlgo, 
			Map<Integer, AclfGen> controlGenMap, GenStateOptimizer opt, double threshold,
			SsaResultContainer ssaResult) {
		super.buildSsaSectionConstrain(dclfAlgo, controlGenMap, opt, threshold, ssaResult);
		if (ssaResult.getCaOverLimitInfo().isEmpty()) {
			return;
		}
		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();
		double baseMva = net.getBaseMva();
		ssaResult.getCaOverLimitInfo().forEach(info -> addSsaSectionConstraint(dclfAlgo,
				controlGenMap, opt, threshold, net, baseMva, info));
	}

	@Override
	protected void buildSectionConstrain(ContingencyAnalysisAlgorithm dclfAlgo, 
			Map<Integer, AclfGen> controlGenMap, GenStateOptimizer opt, double threshold) {
		super.buildSectionConstrain(dclfAlgo, controlGenMap, opt, threshold);
		AclfNetwork net = (AclfNetwork) dclfAlgo.getNetwork();
		net.getBranchList().stream()
						.filter(AclfBranch::isActive)
						.forEach(outBranch -> addSectionConstraints(
									dclfAlgo, controlGenMap, opt, threshold, net, outBranch));
	}

	private void addSsaSectionConstraint(ContingencyAnalysisAlgorithm dclfAlgo,
			Map<Integer, AclfGen> controlGenMap, GenStateOptimizer opt, double threshold, AclfNetwork net,
			double baseMva, SsaBranchOverLimitInfo info) {
		AclfBranch monBranch = net.getAclfBranchNameLookupTable().get(info.getOverLimitBranchId());
		AclfBranch outBranch = net.getAclfBranchNameLookupTable().get(info.getOutageBranchId());
		if (monBranch == null || outBranch == null) {
			return;
		}
		int monBranchNo = (int) (monBranch.getNumber() - 1);
		int outBranchNo = (int) (outBranch.getNumber() - 1);
		DclfAlgoBranch outDclfBranch = dclfAlgo.getDclfAlgoBranch(outBranch.getId());
		try {
			double[] lodf = computeLodf(dclfAlgo, outDclfBranch);
			double postFlowPu = info.getShftedFlowMW() / baseMva;
			double[] genSenArray = buildContingencyGenSenArray(controlGenMap, monBranchNo, outBranchNo,
					lodf[monBranch.getSortNumber()], postFlowPu > 0);
			addSectionConstraint(opt, Math.abs(info.getShftedFlowMW()), info.getLimitMW(), threshold, genSenArray);
		} catch (InterpssException e) {
			e.printStackTrace();
		}
	}

	private void addSectionConstraints(ContingencyAnalysisAlgorithm dclfAlgo,
			Map<Integer, AclfGen> controlGenMap, GenStateOptimizer opt, double threshold, AclfNetwork net,
			AclfBranch outBranch) {
		int outBranchNo = (int) (outBranch.getNumber() - 1);
		double[] genSenArray = buildOutageGenSenArray(controlGenMap, outBranchNo);
		if (!hasSignificantSensitivity(genSenArray)) {
			return;
		}
		DclfAlgoBranch outDclfBranch = dclfAlgo.getDclfAlgoBranch(outBranch.getId());
		try {
			double[] lodf = computeLodf(dclfAlgo, outDclfBranch);
			net.getBranchList().stream().filter(AclfBranch::isActive)
					.forEach(monBranch -> addMonitoredPostContingencyConstraint(dclfAlgo, controlGenMap,
							opt, threshold, outBranchNo, outDclfBranch, lodf, genSenArray, monBranch));
		} catch (InterpssException e) {
			e.printStackTrace();
		}
	}

	private void addMonitoredPostContingencyConstraint(ContingencyAnalysisAlgorithm dclfAlgo, 
			Map<Integer, AclfGen> controlGenMap, GenStateOptimizer opt, double threshold, int outBranchNo,
			DclfAlgoBranch outDclfBranch, double[] lodf, double[] genSenArray, AclfBranch monBranch) {
		double lodfFactor = lodf[monBranch.getSortNumber()];
		if (!hasSignificantLodfImpact(genSenArray, lodfFactor)) {
			return;
		}
		int monBranchNo = (int) (monBranch.getNumber() - 1);
		DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(monBranch.getId());
		double postFlow = dclfBranch.getDclfFlow() + lodfFactor * outDclfBranch.getDclfFlow();
		fillContingencyGenSenArray(genSenArray, controlGenMap, monBranchNo, outBranchNo, lodfFactor,
				postFlow > 0);
		addSectionConstraintUnchecked(opt, Math.abs(postFlow * 100), dclfBranch.getBranch().getRatingMvaB(), threshold,
				genSenArray);
	}

	private double[] computeLodf(ContingencyAnalysisAlgorithm dclfAlgo, DclfAlgoBranch outDclfBranch)
			throws InterpssException {
		DclfOutageBranch caOutBranch = DclfAlgoObjectFactory.createCaOutageBranch(outDclfBranch,
				ContingencyBranchOutageType.OPEN);
		return dclfAlgo.lineOutageDFactors(caOutBranch);
	}

	private double[] buildOutageGenSenArray(Map<Integer, AclfGen> controlGenMap,
			int outBranchNo) {
		double[] genSenArray = new double[controlGenMap.size()];
		controlGenMap.forEach((no, gen) -> {
			int busNo = (int) (gen.getParentBus().getNumber() - 1);
			genSenArray[no] = getSen(busNo, outBranchNo);
		});
		return genSenArray;
	}

	private double[] buildContingencyGenSenArray(Map<Integer, AclfGen> controlGenMap,
			int monBranchNo, int outBranchNo, double lodfFactor, boolean flowPositive) {
		double[] genSenArray = new double[controlGenMap.size()];
		fillContingencyGenSenArray(genSenArray, controlGenMap, monBranchNo, outBranchNo, lodfFactor,
				flowPositive);
		return genSenArray;
	}

	private void fillContingencyGenSenArray(double[] genSenArray,
			Map<Integer, AclfGen> controlGenMap, int monBranchNo, int outBranchNo, double lodfFactor,
			boolean flowPositive) {
		controlGenMap.forEach((no, gen) -> {
			int busNo = (int) (gen.getParentBus().getNumber() - 1);
			float sen = (float) (getSen(busNo, monBranchNo) + lodfFactor * getSen(busNo, outBranchNo));
			genSenArray[no] = flowPositive ? sen : -sen;
		});
	}

	private boolean hasSignificantLodfImpact(double[] genSenArray, double lodfFactor) {
		return Arrays.stream(genSenArray).anyMatch(sen -> Math.abs(sen * lodfFactor) > SEN_THRESHOLD);
	}

}
