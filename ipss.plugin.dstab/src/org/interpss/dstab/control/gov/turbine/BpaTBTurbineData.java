package org.interpss.dstab.control.gov.turbine;

import org.interpss.dstab.control.base.BaseControllerData;

public class BpaTBTurbineData extends BaseControllerData{
	private double tch=0D;
	private double fhp=0D;
	private double trh=0D;
	private double fip=0D;
	private double tco=0D;
	private double flp=0D;
	private double lambda=0D;
	/**
	 * @return the tch
	 */
	public double getTch() {
		return tch;
	}
	/**
	 * @return the fhp
	 */
	public double getFhp() {
		return fhp;
	}
	/**
	 * @return the trh
	 */
	public double getTrh() {
		return trh;
	}
	/**
	 * @return the fip
	 */
	public double getFip() {
		return fip;
	}
	/**
	 * @return the tco
	 */
	public double getTco() {
		return tco;
	}
	/**
	 * @return the flp
	 */
	public double getFlp() {
		return flp;
	}
	/**
	 * @return the lambda
	 */
	public double getLambda() {
		return lambda;
	}
	/**
	 * @param tch the tch to set
	 */
	public void setTch(double tch) {
		this.tch = tch;
	}
	/**
	 * @param fhp the fhp to set
	 */
	public void setFhp(double fhp) {
		this.fhp = fhp;
	}
	/**
	 * @param trh the trh to set
	 */
	public void setTrh(double trh) {
		this.trh = trh;
	}
	/**
	 * @param fip the fip to set
	 */
	public void setFip(double fip) {
		this.fip = fip;
	}
	/**
	 * @param tco the tco to set
	 */
	public void setTco(double tco) {
		this.tco = tco;
	}
	/**
	 * @param flp the flp to set
	 */
	public void setFlp(double flp) {
		this.flp = flp;
	}
	/**
	 * @param lambda the lambda to set
	 */
	public void setLambda(double lambda) {
		this.lambda = lambda;
	}
	@Override
	public void setValue(String name, double value) {
		if (name.equals("tch"))
			this.tch = value;
		else if (name.equals("fhp"))
			this.fhp = value;
		else if (name.equals("trh"))
			this.trh = value;
		else if (name.equals("fip"))
			this.fip = value;
		else if (name.equals("tco"))
			this.tco = value;
		else if (name.equals("flp"))
			this.flp = value;
		else if (name.equals("lambda"))
			this.lambda = value;
		
	}
	@Override
	public void setValue(String name, int value) {
		
		
	}

}
