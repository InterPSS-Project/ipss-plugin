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
public class SsaResultContainer extends BaseJSONBean {	
	public static record OverLimitRec(SsaBranchOverLimitInfo baseOverLimtInfo, SsaBranchOverLimitInfo optOverLimitInfo, boolean hasOptInfo) {
		public OverLimitRec(SsaBranchOverLimitInfo baseOverLimtInfo, SsaBranchOverLimitInfo optOverLimitInfo, boolean hasOptInfo) {
			this.baseOverLimtInfo = baseOverLimtInfo;
			this.hasOptInfo = hasOptInfo;
			this.optOverLimitInfo = optOverLimitInfo;
		}

		public OverLimitRec(SsaBranchOverLimitInfo baseOverLimtInfo, SsaBranchOverLimitInfo optOverLimitInfo) {
			this(baseOverLimtInfo, optOverLimitInfo, true);	
		}

		public OverLimitRec(SsaBranchOverLimitInfo baseOverLimtInfo) {
			this(baseOverLimtInfo, null, false);
		}

		public String toString(boolean baseInfo) {
			if (hasOptInfo) {
				if (baseInfo) {
					return String.format("Over Limit Branch: %s  afterOpt| flow: %.2f rating: %.2f loading: %.2f%%; beforeOpt| flow: %.2f loading: %.2f%%%n",
						optOverLimitInfo.getOverLimitBranchId(), optOverLimitInfo.getBaseFlowMW(),
						optOverLimitInfo.getLimitMW(), optOverLimitInfo.getLoadingPercent(),
						baseOverLimtInfo.getBaseFlowMW(), baseOverLimtInfo.getLoadingPercent());
				}
				else {
					return String.format("OverLimit Branch: %s outage: %s afterOpt| postFlow: %.2f rating: %.2f loading: %.2f%%; beforeOpt| postFlow: %.2f loading: %.2f%%%n",
						optOverLimitInfo.getOverLimitBranchId(), optOverLimitInfo.getOutageBranchId(),
						optOverLimitInfo.getBaseFlowMW()+optOverLimitInfo.getShftedFlowMW(), optOverLimitInfo.getLimitMW(), optOverLimitInfo.getLoadingPercent(),
						baseOverLimtInfo.getBaseFlowMW()+baseOverLimtInfo.getShftedFlowMW(), baseOverLimtInfo.getLoadingPercent());
				}
			}
			else {
				if (baseInfo) {
					return String.format("Over Limit Branch: %s flow: %.2f rating: %.2f loading: %.2f%%%n",
								baseOverLimtInfo.getOverLimitBranchId(), 
								baseOverLimtInfo.getBaseFlowMW(), baseOverLimtInfo.getLimitMW(), baseOverLimtInfo.getLoadingPercent());
				}
				else {
					return String.format("OverLimit Branch: %s outage: %s postFlow: %.2f rating: %.2f loading: %.2f%%%n",
								baseOverLimtInfo.getOverLimitBranchId(), baseOverLimtInfo.getOutageBranchId(),
								baseOverLimtInfo.getBaseFlowMW()+baseOverLimtInfo.getShftedFlowMW(), 
								baseOverLimtInfo.getLimitMW(), baseOverLimtInfo.getLoadingPercent());
				}
			}
		}
	}

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
		baseOverLimitInfo.forEach(info -> System.out.print(new OverLimitRec(info).toString(true)));
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
				System.out.print(new OverLimitRec(beforeOptInfo, afterOptInfo).toString(true));
			});
		printOverLimitSummary(baseOverLimitInfo);
	}

	public void printCaOverLimitInfo() {
		caOverLimitInfo.forEach(info -> {
			System.out.print(new OverLimitRec(info).toString(false));
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
				SsaBranchOverLimitInfo beforeOptInfo = beforeOptOverLimitInfoMap.get(key);
				System.out.print(new OverLimitRec(beforeOptInfo, afterOptInfo).toString(false));
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
