package org.interpss.plugin.optadj.algo;

import java.util.Arrays;
import java.util.Set;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.plugin.optadj.algo.util.AclfNetLODFsHelper;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;

/**
 * 
 * @author Donghao.F
 * 
 * @date 2025年4月17日 上午11:20:49
 * 
 * 
 * 
 */
public class AclfNetBusContigencyOptimizer extends AclfNetBusOptimizer {
    private static final Logger log = LoggerFactory.getLogger(AclfNetBusContigencyOptimizer.class);

	// loading threshold for the optimization, ignore if the loading is less than this threshold
	public static double LOADING_THRESHOLD = 0.5;

	// sensitivity threshold for N-1 contingency constraints
	private static final double SEN_THRESHOLD = 0.02;
    
    private Set<String> outBranchIdSet;
    
	/**
	 * Constructor
	 * 
	 * @param dclfAlgo
	 */
	public AclfNetBusContigencyOptimizer(ContingencyAnalysisAlgorithm dclfAlgo) {
		super(dclfAlgo);
	}

	public void optimize(double threshold, Set<String> outBranchIdSet) {
		this.outBranchIdSet = outBranchIdSet;
		
		super.optimize(threshold, true);
	}

	public void optimize(double threshold, Set<String> outBranchIdSet, boolean adjustGenOnly) {
		this.outBranchIdSet = outBranchIdSet;
		
		super.optimize(threshold, adjustGenOnly);
	}

	@Override
	public void optimize(double threshold, boolean adjustGenOnly) {
		super.optimize(threshold, adjustGenOnly);
	}

	@Override
	protected void buildSectionConstraints(Sen2DMatrix gfsMatrix, double threshold) {
		super.buildSectionConstraints(gfsMatrix, threshold);

		AclfNetLODFsHelper lodfHelper = new AclfNetLODFsHelper(network);
		Sen2DMatrix lodfMatrix = this.outBranchIdSet == null
				? lodfHelper.calLODF() : lodfHelper.calLODF(this.outBranchIdSet);

		double baseMva = network.getBaseMva();
		network.getBranchList().stream()
			.filter(branch -> branch.isActive() &&
					(this.outBranchIdSet == null || this.outBranchIdSet.contains(branch.getId())))
			.forEach(outBranch -> {
				int outBranchNo = outBranch.getSortNumber();
				double[] busSenArray = new double[controlBusMap.size()];
				DclfAlgoBranch outDclfBranch = dclfAlgo.getDclfAlgoBranch(outBranch.getId());

				controlBusMap.forEach((no, bus) -> {
					int busNo = bus.getSortNumber();
					double gfsSen = gfsMatrix.get(busNo, outBranchNo);
					busSenArray[no] = toSectionSensitivity(outDclfBranch, gfsSen, getControlBusRole(bus));
				});

				if (Arrays.stream(busSenArray).anyMatch(sen -> Math.abs(sen) > SEN_THRESHOLD)) {
					network.getBranchList().stream()
						.filter(branch -> branch.isActive())
						.forEach(monBranch -> {
							int monBranchNo = monBranch.getSortNumber();
							double lodf = lodfMatrix.get(outBranchNo, monBranchNo);
							if (Arrays.stream(busSenArray)
									.anyMatch(sen -> Math.abs(sen * lodf) > SEN_THRESHOLD)) {
								DclfAlgoBranch monDclfBranch = dclfAlgo.getDclfAlgoBranch(monBranch.getId());
								double postFlow = monDclfBranch.getDclfFlow() + lodf * outDclfBranch.getDclfFlow();

								controlBusMap.forEach((no, bus) -> {
									int busNo = bus.getSortNumber();
									double combinedGfs = gfsMatrix.get(busNo, monBranchNo)
											+ lodf * gfsMatrix.get(busNo, outBranchNo);
									busSenArray[no] = toContingencySectionSensitivity(
											postFlow, combinedGfs, getControlBusRole(bus));
								});

								double limit = monDclfBranch.getBranch().getRatingMvaB() * threshold / 100;
								double postFlowMw = Math.abs(postFlow * baseMva);
								double loading = limit > 0.0 ? postFlowMw / limit : 0.0;
								if (loading > LOADING_THRESHOLD) {
									getOptimizer().addConstraint(new SectionConstrainData(
											postFlowMw, Relationship.LEQ, limit, busSenArray));
								}
							}
						});
				}
			});
	}

	private double toContingencySectionSensitivity(double postFlow, double combinedGfs, ControlBusRole role) {
		if (role == ControlBusRole.LOAD) {
			return postFlow > 0 ? -combinedGfs : combinedGfs;
		}
		return postFlow > 0 ? combinedGfs : -combinedGfs;
	}
}
