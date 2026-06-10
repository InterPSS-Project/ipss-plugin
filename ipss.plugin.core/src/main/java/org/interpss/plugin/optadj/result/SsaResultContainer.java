package org.interpss.plugin.optadj.result;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.interpss.datatype.base.BaseJSONBean;
/** 

*/
public class SsaResultContainer extends BaseJSONBean{	
	private double baseLoadingThreshold;
	protected List<SsaBranchOverLimitInfo> baseOverLimitInfo;
	
	private double caLoadingThreshold;
	protected List<SsaBranchOverLimitInfo> caOverLimitInfo;
	
	public SsaResultContainer() {
		super();
		baseOverLimitInfo = new CopyOnWriteArrayList<SsaBranchOverLimitInfo>();
		caOverLimitInfo = new CopyOnWriteArrayList<SsaBranchOverLimitInfo>();
	}

	public double getBaseLoadingThreshold() {
		return baseLoadingThreshold;
	}

	public void setBaseLoadingThreshold(double baseLoadingThreshold) {
		this.baseLoadingThreshold = baseLoadingThreshold;
	}

	public double getCaLoadingThreshold() {
		return caLoadingThreshold;
	}

	public void setCaLoadingThreshold(double caLoadingThreshold) {
		this.caLoadingThreshold = caLoadingThreshold;
	}

	public List<SsaBranchOverLimitInfo> getBaseOverLimitInfo() {
		return baseOverLimitInfo;
	}

	//public void setBaseOverLimitInfo(List<SsaBranchOverLimitInfo> baseOverLimitInfo) {
	//	this.baseOverLimitInfo = baseOverLimitInfo;
	//}

	public List<SsaBranchOverLimitInfo> getCaOverLimitInfo() {
		return caOverLimitInfo;
	}

	//public void setCaOverLimitInfo(List<SsaBranchOverLimitInfo> caOverLimitInfo) {
	//	this.caOverLimitInfo = caOverLimitInfo;
	//}

	public void printBaseOverLimitInfo() {
		baseOverLimitInfo.forEach(info -> System.out.printf(
				"Over Limit Branch: %s  %.2f rating: %.2f loading: %.2f%n",
				info.getOverLimitBranchId(),
				info.getBaseFlowMW(),
				info.getLimitMW(),
				info.getLoadingPercent()));
		printOverLimitSummary(baseOverLimitInfo);
	}

	/**
	 * Print the base over limit info after optimization with the before optimization over limit info comparison.
	 * @param beforeOptOverLimitInfo The list of base over limit info before optimization.
	 */
	public void printBaseOverLimitInfo(List<SsaBranchOverLimitInfo> beforeOptOverLimitInfo) {
		Map<String, SsaBranchOverLimitInfo> beforeOptOverLimitInfoMap = beforeOptOverLimitInfo.stream()
			.collect(Collectors.toMap(SsaBranchOverLimitInfo::getOverLimitBranchId, Function.identity()));
		baseOverLimitInfo.forEach(afterOptInfo -> {
			SsaBranchOverLimitInfo beforeOptInfo = beforeOptOverLimitInfoMap.get(afterOptInfo.getOverLimitBranchId());
			System.out.printf(
				"Over Limit Branch: %s  afterOpt: %.2f rating: %.2f loading: %.2f; beforeOpt: %.2f loading: %.2f%%%n",
				afterOptInfo.getOverLimitBranchId(),
				afterOptInfo.getBaseFlowMW(),
				afterOptInfo.getLimitMW(),
				afterOptInfo.getLoadingPercent(),
				beforeOptInfo.getBaseFlowMW(),
				beforeOptInfo.getLoadingPercent());
			});
		printOverLimitSummary(baseOverLimitInfo);
	}

	public void printCaOverLimitInfo() {
		caOverLimitInfo.forEach(info -> {
			double postFlowMW = info.getBaseFlowMW() + info.getShftedFlowMW();
			System.out.printf("OverLimit Branch: %s outage: %s postFlow: %.2f rating: %.2f loading: %.2f%n",
					info.getOverLimitBranchId(), info.getOutageBranchId(),
					postFlowMW, info.getLimitMW(), info.getLoadingPercent());
		});
		printOverLimitSummary(caOverLimitInfo);
	}

	public void printCaOverLimitInfo(List<SsaBranchOverLimitInfo> beforeOptOverLimitInfo) {
		Map<String, SsaBranchOverLimitInfo> beforeOptOverLimitInfoMap = beforeOptOverLimitInfo.stream()
			.collect(Collectors.toMap(
				info -> info.getOutageBranchId() + "_" + info.getOverLimitBranchId(),
				Function.identity()));

		caOverLimitInfo.forEach(afterOptInfo -> {
			String key = afterOptInfo.getOutageBranchId() + "_" + afterOptInfo.getOverLimitBranchId();
			if (beforeOptOverLimitInfoMap.containsKey(key)) {
				double postFlowMW = afterOptInfo.getBaseFlowMW() + afterOptInfo.getShftedFlowMW();
				SsaBranchOverLimitInfo beforeOptInfo = beforeOptOverLimitInfoMap.get(key);
				System.out.printf("OverLimit Branch: %s outage: %s afterOpt: %.2f rating: %.2f loading: %.2f; beforeOpt: %.2f loading: %.2f%n",
						afterOptInfo.getOverLimitBranchId(), afterOptInfo.getOutageBranchId(),
						postFlowMW, afterOptInfo.getLimitMW(), afterOptInfo.getLoadingPercent(),
						beforeOptInfo.getBaseFlowMW()+beforeOptInfo.getShftedFlowMW(), beforeOptInfo.getLoadingPercent());
			}
		});
		printOverLimitSummary(caOverLimitInfo);
	}

	private void printOverLimitSummary(List<SsaBranchOverLimitInfo> overLimitInfo) {
		double maxLoading = overLimitInfo.stream()
				.mapToDouble(SsaBranchOverLimitInfo::getLoadingPercent)
				.max()
				.orElse(0.0);
		System.out.printf("Total branches over limit: %d, max loading: %.2f%%%n",
				overLimitInfo.size(), maxLoading);
	}
}
