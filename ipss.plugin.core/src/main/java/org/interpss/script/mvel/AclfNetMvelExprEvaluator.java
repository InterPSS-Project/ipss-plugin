package org.interpss.script.mvel;

import com.interpss.core.aclf.AclfNetwork;

/**
 * AclfNetwork MEVL expression evaluator implementation.
 *
 */
public class AclfNetMvelExprEvaluator extends BaseMvelExprEvaluator {
	private AclfNetwork aclfNet;
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet
	 */
	public AclfNetMvelExprEvaluator(AclfNetwork aclfNet) {
		super();
		this.aclfNet = aclfNet;
		this.context.put("aclfnet", aclfNet);
	}
	
	/**
	 * Apply the factor to all generation in the AclfNetwork
	 * 
	 * @param factor adjustment factor in PU
	 */
	public void adjustGen(double factor) {
		this.aclfNet.getBusList().stream()
			.filter(bus -> bus.isActive() && bus.isGen())
			.forEach(bus -> {
				if (this.aclfNet.isContributeGenLoadModel()) {
					bus.getContributeGenList().forEach(gen -> {
						gen.setGen(gen.getGen().multiply(factor));
					});
				}
				else {
					bus.setGenP(factor * bus.getGenP());
					if (bus.isGenPQ())
						bus.setGenQ(factor * bus.getGenQ());
				}
			});
	}
	
	/**
	 * Apply the factor to all loads in the AclfNetwork
	 * 
	 * @param factor adjustment factor in PU
	 */
	public void adjustLoad(double factor) {
		this.aclfNet.getBusList().stream()
			.filter(bus -> bus.isActive() && bus.isLoad())
			.forEach(bus -> {
				if (this.aclfNet.isContributeGenLoadModel()) {
					bus.getContributeLoadList().forEach(load -> {
						load.setLoadCP(load.getLoadCP().multiply(factor));
						load.setLoadCI(load.getLoadCI().multiply(factor));
						load.setLoadCZ(load.getLoadCZ().multiply(factor));
					});
				}
				else {
					bus.setLoadP(factor * bus.getLoadP());
					bus.setLoadQ(factor * bus.getLoadQ());
				}
			});
	}
}
