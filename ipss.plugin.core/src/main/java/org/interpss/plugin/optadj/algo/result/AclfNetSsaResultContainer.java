package org.interpss.plugin.optadj.algo.result;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.interpss.datatype.base.BaseJSONBean;

import com.interpss.algo.parallel.BranchCAResultRec;


/** 
* A container class for storing the results of the AclfNetwork SSA algorithm.

* @author  Donghao.F 

* @date 2023 Mar 11 09:50:58 

* 

*/
public class AclfNetSsaResultContainer extends BaseJSONBean {
	private boolean hasOptAdjInfo = false;

	// a list of branches that are over the limit in the base case
	private List<? extends BranchDclfResultRec> baseOverLimitInfo;

	private Map<String, Double> optAdjBaseResultMap;

	// a list of branches that are over the limit in the contingency situation	
	private List<? extends BranchCAResultRec> caOverLimitInfo;

	private Map<String, Double> optAdjCAOverLimitResultMap;

	public AclfNetSsaResultContainer(boolean hasOptAdjInfo) {
		super();
		this.hasOptAdjInfo = hasOptAdjInfo;
		baseOverLimitInfo = new CopyOnWriteArrayList<BranchDclfResultRec>();
		caOverLimitInfo = new CopyOnWriteArrayList<BranchCAResultRec>();
	}

	public <T extends BranchDclfResultRec> List<T> getBaseOverLimitInfo() {
		return (List<T>) baseOverLimitInfo;
	}

	public <T extends BranchDclfResultRec> Map<String, T> toBaseOverLimitInfoMap() {
		return (Map<String, T>) baseOverLimitInfo.stream()
			.collect(Collectors.toMap(rec -> rec.dclfBranch.getId(), Function.identity()));
	}

	public void setBaseOverLimitInfo(List<? extends BranchDclfResultRec> baseOverLimitInfo) {
		this.baseOverLimitInfo = baseOverLimitInfo;
	}

	public Map<String, Double> getOptAdjBaseResultMap() {
		return optAdjBaseResultMap;
	}

	public void setOptAdjBaseResultMap(Map<String, Double> optAdjBaseResultMap) {
		this.optAdjBaseResultMap = optAdjBaseResultMap;
	}

	public <T extends BranchCAResultRec> List<T> getCaOverLimitInfo() {
		return (List<T>) caOverLimitInfo;
	}

	public <T extends BranchCAResultRec> Map<String, T> toCaOverLimitInfoMap() {
		return (Map<String, T>) caOverLimitInfo.stream()
			.collect(Collectors.toMap(rec -> caOverLimitInfoMapId(rec), Function.identity()));
	}

	public static String caOverLimitInfoMapId(BranchCAResultRec rec) {
		return rec.contingency.getId() + "_" + rec.aclfBranch.getId();
	}

	public void setCaOverLimitInfo(List<? extends BranchCAResultRec> caOverLimitInfo) {
		this.caOverLimitInfo = caOverLimitInfo;
	}

	public Map<String, Double> getOptAdjCAOverLimitResultMap() {
		return optAdjCAOverLimitResultMap;
	}

	public void setOptAdjCAOverLimitResultMap(Map<String, Double> optAdjCAOverLimitResultMap) {
		this.optAdjCAOverLimitResultMap = optAdjCAOverLimitResultMap;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("AclfNetSsaResultContainer [").append("\n");

		sb.append("hasOptAdjInfo=").append(hasOptAdjInfo).append("\n");

		sb.append("optAdjBaseResultMap=").append("\n");
		sb.append(optAdjBaseResultMap).append("\n");

		sb.append("baseOverLimitInfo=").append("\n");
		this.baseOverLimitInfo.forEach(rec -> {
			if (hasOptAdjInfo) {
				BranchOptAdjustResultRec recAdj = (BranchOptAdjustResultRec) rec;
				sb.append(String.format("Branch: %s flowMw(optadj): %.2f rating: %.2f loading%%(optadj): %.2f | flowMw(original): %.2f loading%%(original): %.2f",
				recAdj.dclfBranch.getId(), recAdj.adjustedMwFlow, recAdj.dclfBranch.getBranch().getRatingMvaA(), recAdj.adjustedLoadingPercent, 
				recAdj.mwFlow, recAdj.loadingPercent))
				.append("\n");
			} else {
				sb.append(String.format("Branch: %s flowMw(original): %.2f rating: %.2f loading%%(original): %.2f",
							rec.dclfBranch.getId(), rec.mwFlow,
							rec.dclfBranch.getBranch().getRatingMvaA(), rec.loadingPercent))
				  .append("\n");
			}
		});

		sb.append("optAdjCAOverLimitResultMap=").append("\n");
		sb.append(optAdjCAOverLimitResultMap).append("\n");

		sb.append("caOverLimitInfo=").append("\n");
		this.caOverLimitInfo.forEach(rec -> {
			if (hasOptAdjInfo) {
				BranchOptAdjustCAResultRec recAdj = (BranchOptAdjustCAResultRec) rec;
				sb.append(String.format("OverLimit Branch: %s outage: %s postFlow(optadj): %.2f rating: %.2f loading(optadj): %.2f postFlow(original): %.2f loading%%(original): %.2f",
						recAdj.aclfBranch.getId(), recAdj.contingency.getId(),
						recAdj.adjustedMwFlow, recAdj.aclfBranch.getRatingMvaB(), recAdj.adjustedLoadingPercent,
						rec.getPostFlowMW(), rec.calLoadingPercent())
				).append("\n");
			} else {
				sb.append(String.format("OverLimit Branch: %s outage: %s postFlow: %.2f rating: %.2f loading: %.2f",
						rec.aclfBranch.getId(), rec.contingency.getId(),
						rec.getPostFlowMW(), rec.aclfBranch.getRatingMvaB(), rec.calLoadingPercent())
				).append("\n");
			}
		});

		sb.append("]");
		return sb.toString();
	}
}
