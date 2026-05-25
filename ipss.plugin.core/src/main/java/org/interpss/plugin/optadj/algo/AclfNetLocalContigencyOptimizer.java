package org.interpss.plugin.optadj.algo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.plugin.optadj.algo.util.AclfNetLODFsHelper;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;

/**
 * 
 * @author Donghao.F
 * 
 * @date 2025年4月17日 上午11:20:49
 * 
 * 
 * 
 */
public class AclfNetLocalContigencyOptimizer extends AclfNetLocalOptimizer {
    private static final Logger log = LoggerFactory.getLogger(AclfNetLocalContigencyOptimizer.class);

	// loading threshold for the optimization, ignore if the loading is less than this threshold
	public static double LOADING_THRESHOLD = 0.5;

	// sensitivity threshold for N-1 contingency constraints
	private static final double SEN_THRESHOLD = 0.02;
    
    //private Set<String> outBranchIdSet = Collections.emptySet();

	private List<DclfBranchOutage> dclfContList = new ArrayList<>();

	Set<String> monitoredBranchIds = Collections.emptySet();
    
	/**
	 * Constructor
	 * 
	 * @param dclfAlgo
	 */
	public AclfNetLocalContigencyOptimizer(ContingencyAnalysisAlgorithm dclfAlgo) {
		super(dclfAlgo);
	}

	public void optimize(double threshold, List<DclfBranchOutage> dclfContList, Set<String> monitoredBranchIds) {
		this.optimize(threshold, dclfContList, monitoredBranchIds, true);
	}

	public void optimize(double threshold, List<DclfBranchOutage> dclfContList, boolean adjustGenOnly) {
		this.dclfContList = dclfContList;
		
		this.optimize(threshold, adjustGenOnly);
	}

	public void optimize(double threshold, List<DclfBranchOutage> dclfContList, Set<String> monitoredBranchIds, boolean adjustGenOnly) {
		this.dclfContList = dclfContList;
		this.monitoredBranchIds = monitoredBranchIds;
		
		this.optimize(threshold, adjustGenOnly);
	}

	@Override
	public void optimize(double threshold, boolean adjustGenOnly) {
		super.optimize(threshold, adjustGenOnly);
	}

	/**
     * Identify over limit branches in the network.
     */
	@Override
	protected void identifyOverlimitBranches(double threshold) {
		heavyLoadedBranchIdSet = new HashSet<>();
			
		// define a contingency list
		if (this.dclfContList.isEmpty()) {
			dclfAlgo.getNetwork().getBranchList().stream()
				.filter(branch -> !((AclfBranch)branch).isConnect2RefBus())
				.forEach(branch -> {
					DclfBranchOutage cont = createContingency("contBranch:"+branch.getId());
					DclfOutageBranch outage = createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()), ContingencyBranchOutageType.OPEN);
					cont.setOutageEquip(outage);
					this.dclfContList.add(cont);
				});
		}
		
		dclfContList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW());
						double loading = resultRec.calLoadingPercent(resultRec.aclfBranch.getRatingMvaB());
						if (loading > threshold) {
							//System.out.println("OverLimit Branch: " + resultRec.aclfBranch.getId() + " outage: "
							//				+ resultRec.contingency.getId() + " postFlow: " + resultRec.getPostFlowMW()
							//				+ " rating: " + resultRec.aclfBranch.getRatingMvaB() + " loading: "
							//				+ loading);
							if (this.monitoredBranchIds.isEmpty() || this.monitoredBranchIds.contains(resultRec.aclfBranch.getId())) 				
								heavyLoadedBranchIdSet.add(resultRec.aclfBranch.getId());				
						}
					});
			});
			
		log.info("Found {} over limit branches", heavyLoadedBranchIdSet.size());
	}
		
	@Override
	protected void buildSectionConstraints(Sen2DMatrix gfsMatrix, double threshold) {
		super.buildSectionConstraints(gfsMatrix, threshold);

		AclfNetLODFsHelper lodfHelper = new AclfNetLODFsHelper(network);
		Set<String> outBranchIdSet = this.dclfContList.stream()
			.map(cont -> cont.getOutageEquip().getBranch().getId())
			.collect(Collectors.toSet());
			
		Sen2DMatrix lodfMatrix = outBranchIdSet.isEmpty()
				? lodfHelper.calLODF() : lodfHelper.calLODF(outBranchIdSet);

		double baseMva = network.getBaseMva();
		network.getBranchList().stream()
			.filter(branch -> branch.isActive() &&
					(outBranchIdSet.isEmpty() || outBranchIdSet.contains(branch.getId())))
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
