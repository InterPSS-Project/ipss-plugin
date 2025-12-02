package org.interpss.plugin.exchange.bean;

/**
 * Aclf bus result info bean
 * 
 * @author mzhou
 *
 */
public class AclfBusExchangeInfo extends BaseElemExchangeBean {
	// bus voltage results
	public double[] volt_mag;   // in p.u.
	public double[] volt_ang;   // in degree
	
	public AclfBusExchangeInfo(String[] ids) {
		super(ids);
	}
}
