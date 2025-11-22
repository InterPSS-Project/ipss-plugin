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
	
	/** Constructor
	 * 
	 * @param aclfNet the Aclf network object
	 */
	public AclfResultExchangeAdapter(AclfNetwork aclfNet) {
		this.aclfNet = aclfNet;
	}

	/** Fill bus result info bean
	 * 
	 * @param bean the bus result info bean with ids set
	 * @return true if success
	 */
	public boolean fillBusResult(AclfBusExchangeInfo bean) {
		bean.volt_mag = new double[bean.lenght];
		bean.volt_ang = new double[bean.lenght];
		
		for (int i = 0; i < bean.lenght; i++) {
			String id = bean.ids[i];
			AclfBus bus = aclfNet.getBus(id);
			if (bus == null)
				continue;
			bean.volt_mag[i] = bus.getVoltageMag();
			bean.volt_ang[i] = bus.getVoltageAng(UnitType.Deg);
		}
		return true;
	}
	
	/** Fill branch result info bean
	 * 
	 * @param bean the branch result info bean with ids set
	 * @return true if success
	 */
	public boolean fillBranchResult(AclfBranchExchangeInfo bean) {
		bean.p_f2t = new double[bean.lenght];
		bean.q_f2t = new double[bean.lenght];
		bean.p_t2f = new double[bean.lenght];
		bean.q_t2f = new double[bean.lenght];
		
		for (int i = 0; i < bean.lenght; i++) {
			String id = bean.ids[i];
			AclfBranch branch = aclfNet.getBranch(id);
			if (branch == null)
				continue;
			Complex powerF2t = branch.powerFrom2To(UnitType.mVA);
			Complex powerT2f = branch.powerTo2From(UnitType.mVA);
			bean.p_f2t[i] = powerF2t.getReal();
			bean.q_f2t[i] = powerF2t.getImaginary();
			bean.p_t2f[i] = powerT2f.getReal();
			bean.q_t2f[i] = powerT2f.getImaginary();
		}
		return true;
	}
}
