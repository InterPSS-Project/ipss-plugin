package org.interpss.plugin.optadj.result;

import org.interpss.datatype.base.BaseJSONBean;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

/** 
* 
*/

public class SsaBranchOverLimitInfo extends BaseJSONBean{
	private String overLimitBranchId;
	
	private double limitMW;
	
	private double baseFlowMW;

	private String outageBranchId;

	private double shftedFlowMW;

	private double loadingPercent;
	
	/**
	 * Constructor for base case over limit info
	 * 
	 * @param overLimitBranchId
	 * @param limitMw
	 * @param baseFlowMw
	 */
	public SsaBranchOverLimitInfo(String overLimitBranchId, double limitMw, double baseFlowMw) {
		super();
		this.overLimitBranchId = overLimitBranchId;
		this.limitMW = limitMw;
		this.baseFlowMW = baseFlowMw;
		this.loadingPercent = 100.0 * Math.abs(baseFlowMW) / limitMW;
	}
	/**
	 * Constructor for contingency over limit info
	 * 
	 * @param outageBranchId
	 * @param overLimitBranchId
	 * @param limitMw
	 * @param baseFlowMw
	 * @param shftedFlowMw
	 */
	public SsaBranchOverLimitInfo(String outageBranchId, String overLimitBranchId, double limitMw, double baseFlowMw, double shftedFlowMw) {
		this(overLimitBranchId, limitMw, baseFlowMw);
		this.outageBranchId = outageBranchId;
		this.shftedFlowMW = shftedFlowMw;
		this.loadingPercent = 100.0 * Math.abs(baseFlowMw + shftedFlowMW) / limitMW;
	}

	public String getOutageBranchId() {
		return outageBranchId;
	}

	public String getOverLimitBranchId() {
		return overLimitBranchId;
	}

	public double getBaseFlowMW() {
		return baseFlowMW;
	}	

	public double getLimitMW() {
		return limitMW;
	}

	public double getShftedFlowMW() {
		return shftedFlowMW;
	}

	public double getLoadingPercent() {
		return loadingPercent;
	}

	public double calCombinedShiftingFactor(String busId, ContingencyAnalysisAlgorithm dclfAlgo) throws InterpssException {
		// create a contingency object for the branch outage analysis
		DclfBranchOutage contingency = DclfAlgoObjectFactory.createContingency("contBranch:" + outageBranchId);
		// create an open CA outage branch object for the branch outage analysis
		DclfOutageBranch outage = DclfAlgoObjectFactory.createCaOutageBranch(
					dclfAlgo.getDclfAlgoBranch(outageBranchId),
					ContingencyBranchOutageType.OPEN);
		contingency.setOutageEquip(outage);

		AclfNetwork aclfNet = dclfAlgo.getAclfNet();
		AclfBranch monitoredBranch = aclfNet.getBranch(overLimitBranchId);
		double csf = 0.0;
		if (contingency != null && monitoredBranch != null) {
			BranchCAResultRec rec = new BranchCAResultRec(contingency, monitoredBranch,
					this.getBaseFlowMW(), this.getShftedFlowMW());
			csf = rec.calCombinedShiftingFactor(busId, dclfAlgo);	
		}
		return csf;
	}
}
