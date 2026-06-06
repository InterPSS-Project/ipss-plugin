package org.interpss.plugin.optadj.result;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.interpss.datatype.base.BaseJSONBean;
/** 

*/
public class SsaResultContainer extends BaseJSONBean{	
	private double baseLoadingThreshold;
	private List<SsaBranchOverLimitInfo> baseOverLimitInfo;
	
	private double caLoadingThreshold;
	private List<SsaBranchOverLimitInfo> caOverLimitInfo;
	
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

	public void setBaseOverLimitInfo(List<SsaBranchOverLimitInfo> baseOverLimitInfo) {
		this.baseOverLimitInfo = baseOverLimitInfo;
	}

	public List<SsaBranchOverLimitInfo> getCaOverLimitInfo() {
		return caOverLimitInfo;
	}

	public void setCaOverLimitInfo(List<SsaBranchOverLimitInfo> caOverLimitInfo) {
		this.caOverLimitInfo = caOverLimitInfo;
	}

	public void printBaseOverLimitInfo() {
		baseOverLimitInfo.stream().forEach(info -> {
			double loading = Math.abs(info.getBaseFlowMW() / info.getLimitMW()) * 100;
			System.out.printf("Over Limit Branch: %s  %.2f rating: %.2f loading: %.2f%n",
					info.getOverLimitBranchId(),
					info.getBaseFlowMW(),
					info.getLimitMW(),
					loading);
		});
	}
}
