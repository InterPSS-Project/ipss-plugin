package org.interpss.plugin.exchange;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.exchange.bean.AclfBranchExchangeInfo;
import org.interpss.plugin.exchange.bean.AclfBusExchangeInfo;
import org.interpss.plugin.exchange.bean.AclfNetExchangeInfo;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Base adapter class to create the Aclf analysis result exchange info beans
 * 
 * @author mzhou
 *
 */
public abstract class BaseResultExchangeAdapter<T extends AclfNetExchangeInfo> {
	// the Aclf network object
	protected AclfNetwork aclfNet;

	/** Constructor
	 * 
	 * @param aclfNet the Aclf network object
	 */
	public BaseResultExchangeAdapter(AclfNetwork aclfNet) {
		this.aclfNet = aclfNet;
	}

	/** Create exchange info bean
	 * 
	 * @param busIds the bus ids array
	 * @param branchIds the branch ids array
	 * @return AclfNetExchangeInfo bean
	 */
	public abstract T createInfoBean(String[] busIds, String[] branchIds);
	
	/** create bus result info beans
	 * 
	 * @param netInfoBean the AclfNetExchangeInfo bean
	 * @param ids the bus ids array
	 * @return true if success
	 */
	protected boolean createBusResult(AclfNetExchangeInfo netInfoBean, String[] ids) {
		netInfoBean.busResultBean = new AclfBusExchangeInfo(ids);
		AclfBusExchangeInfo busResultBean = netInfoBean.busResultBean;
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
	
	/** Create branch result info beans
	 * 
	 * @param netInfoBean the AclfNetExchangeInfo bean
	 * @param ids the branch ids array
	 * @return true if success
	 */
	protected boolean fillBranchResult(AclfNetExchangeInfo netInfoBean, String[] ids) {
		netInfoBean.branchResultBean = new AclfBranchExchangeInfo(ids);
		AclfBranchExchangeInfo branchResultBean = netInfoBean.branchResultBean;
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
}
