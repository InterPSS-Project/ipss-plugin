
package org.interpss.plugin.optadj.optimizer;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.plugin.optadj.config.OptAdjConfigureInfo;
import org.interpss.plugin.optadj.optimizer.bean.BaseConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.DeviceConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;

/**
 * 
 * @author Donghao.F
 * 
 * 
 * 
 * 
 */
public abstract class BaseStateOptimizer {


	protected List<DeviceConstrainData> deviceConstrainDataList ;
	
	protected List<SectionConstrainData> secConstrainDataList ;
	
	protected Optimisation.Result result;
	
	protected ExpressionsBasedModel model;
	
	protected int genSize;

	public int getGenSize() {
		return genSize;
	}

	public BaseStateOptimizer() {
		 model = new ExpressionsBasedModel();
		 deviceConstrainDataList = new ArrayList<DeviceConstrainData>();
		 secConstrainDataList = new ArrayList<SectionConstrainData>();
		
	}

	public List<DeviceConstrainData> getGenConstrainDataList() {
		return deviceConstrainDataList;
	}
	
	public List<SectionConstrainData> getSecConstrainDataList() {
		return secConstrainDataList;
	}

	public void addConstraint(BaseConstrainData data) {
		if (data instanceof DeviceConstrainData) {
			deviceConstrainDataList.add((DeviceConstrainData) data);
		} else {
			secConstrainDataList.add((SectionConstrainData) data);
		}
	}

	public abstract void optimize(); 
	
	
	public double[] getPoint() {
        return result.toRawCopy1D();
    }

    public double getValue() {
        return result.getValue();
    }

	public void addConfigure(OptAdjConfigureInfo info) {
		info.getOptimizedUnitControlLimits().forEach(limit -> {
			if (limit.getPMax() != -1) {
				this.addConstraint(
						new DeviceConstrainData(limit.getOrigin(), Relationship.LEQ, limit.getPMax(), limit.getIndex()));
			}
			if (limit.getPMin() != -1) {
				this.addConstraint(
						new DeviceConstrainData(limit.getOrigin(), Relationship.GEQ, limit.getPMin(), limit.getIndex()));
			}
		});
		
	};
	
	
}

