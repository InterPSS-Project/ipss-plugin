package org.interpss.plugin.optadj.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 

* @author  Donghao.F 

* @date 2025��3��5�� ����9:35:21 

* 

*/
public class OptAdjConfigureInfo {

	List<OPtAdjControlLimit> optimizedUnitControlLimits;
	
	public OptAdjConfigureInfo() {
		super();
		this.optimizedUnitControlLimits = new ArrayList<OPtAdjControlLimit>();
	}

	public void disableGeneratorControl(String genName, double originP) {
		this.optimizedUnitControlLimits.add(new OPtAdjControlLimit(genName, originP, originP, originP));
	}

	/**
	 * @param genName
	 * @param originP
	 * @param percent 1~100
	 */
	public void setGeneratorMaxPercentage(String genName, double originP, double originPMax) {
		this.optimizedUnitControlLimits.add(new OPtAdjControlLimit(genName, originP, originPMax , -1));
	}
	
	/**
	 * @param genName
	 * @param originP
	 * @param percent 1~100
	 */
	public void setGeneratorMinPercentage(String genName, double originP, double originPMin) {
		this.optimizedUnitControlLimits.add(new OPtAdjControlLimit(genName, originP, -1, originPMin));
	}
	
	
	public void fillIndex(Map<String, Integer> indexMap) {
		this.optimizedUnitControlLimits.forEach(limit -> {
			limit.setIndex(indexMap.get(limit.getGenName()));
		});
	}

	public List<OPtAdjControlLimit> getOptimizedUnitControlLimits() {
		return optimizedUnitControlLimits;
	}
	
	
}
