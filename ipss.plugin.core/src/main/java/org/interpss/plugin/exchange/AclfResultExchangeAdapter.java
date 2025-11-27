package org.interpss.plugin.exchange;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.exchange.bean.AclfBranchExchangeInfo;
import org.interpss.plugin.exchange.bean.AclfBusExchangeInfo;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Adapter class to fill the Aclf analysis result into exchange info beans
 * 
 * @author mzhou
 *
 */
public class AclfResultExchangeAdapter {
	// the Aclf network object
	private AclfNetwork aclfNet;
	
	private AclfBusExchangeInfo busResultBean;
	
	private AclfBranchExchangeInfo branchResultBean;
	
	/** Constructor
	 * 
	 * @param aclfNet the Aclf network object
	 */
	public AclfResultExchangeAdapter(AclfNetwork aclfNet) {
		this.aclfNet = aclfNet;
	}

	public void setBusIds(String[] ids) {
		this.busResultBean = new AclfBusExchangeInfo(ids);
	}
	
	public void setBranchIds(String[] ids) {
		this.branchResultBean = new AclfBranchExchangeInfo(ids);
	}
	
	/** Fill bus result info bean
	 * 
	 * @return true if success
	 */
	public boolean fillBusResult() {
		busResultBean.volt_mag = new double[busResultBean.lenght];
		busResultBean.volt_ang = new double[busResultBean.lenght];
		
		for (int i = 0; i < busResultBean.lenght; i++) {
			String id = busResultBean.ids[i];
			AclfBus bus = aclfNet.getBus(id);
			if (bus == null)
				continue;
			busResultBean.volt_mag[i] = bus.getVoltageMag();
			busResultBean.volt_ang[i] = bus.getVoltageAng(UnitType.Deg);
		}
		return true;
	}
	
	/** Fill branch result info bean
	 * 
	 * @return true if success
	 */
	public boolean fillBranchResult() {
		branchResultBean.p_f2t = new double[branchResultBean.lenght];
		branchResultBean.q_f2t = new double[branchResultBean.lenght];
		branchResultBean.p_t2f = new double[branchResultBean.lenght];
		branchResultBean.q_t2f = new double[branchResultBean.lenght];
		
		for (int i = 0; i < branchResultBean.lenght; i++) {
			String id = branchResultBean.ids[i];
			AclfBranch branch = aclfNet.getBranch(id);
			if (branch == null)
				continue;
			Complex powerF2t = branch.powerFrom2To(UnitType.mVA);
			Complex powerT2f = branch.powerTo2From(UnitType.mVA);
			branchResultBean.p_f2t[i] = powerF2t.getReal();
			branchResultBean.q_f2t[i] = powerF2t.getImaginary();
			branchResultBean.p_t2f[i] = powerT2f.getReal();
			branchResultBean.q_t2f[i] = powerT2f.getImaginary();
		}
		return true;
	}
	
	public AclfBusExchangeInfo getBusResultBean() {
		return busResultBean;
	}
	
	public AclfBranchExchangeInfo getBranchResultBean() {
		return branchResultBean;
	}
}
