package org.interpss.dstab.control.gov.psse.gast;

import org.interpss.dstab.control.base.BaseControllerData;

public class PsseGASTGasTurGovernorData extends BaseControllerData {
	

	    private double t1       = 0.25;
	    private double t2       = 0.04;
	    private double t3       = 5.0;
		private double R    = 0.05;
		private double loadLimit  = 0.5;
		private double Kt  = 1.0;
		private double Dturb =0.0;
		private double VMax =0.0;
		public double getVMax() {
			return VMax;
		}
		public void setVMax(double vMax) {
			VMax = vMax;
		}
		public double getVMin() {
			return VMin;
		}
		public void setVMin(double vMin) {
			VMin = vMin;
		}
		private double VMin =0.0;
		
		/**
		 * @return the t1
		 */
		public double getT1() {
			return t1;
		}
		/**
		 * @return the t2
		 */
		public double getT2() {
			return t2;
		}
		/**
		 * @return the t3
		 */
		public double getT3() {
			return t3;
		}
		/**
		 * @param t1 the t1 to set
		 */
		public void setT1(double t1) {
			this.t1 = t1;
		}
		/**
		 * @param t2 the t2 to set
		 */
		public void setT2(double t2) {
			this.t2 = t2;
		}
		/**
		 * @param t3 the T3 to set
		 */
		public void setT3(double t3) {
			this.t3 = t3;
		}
		
		/**
		 * @return the Speed droop R
		 */
		public double getR() {
			return R;
		}
		
		public void setR(double r) {
			R = r;
		}

		
		
	    public double getLoadLimit() {
			return loadLimit;
		}
		public void setLoadLimit(double loadLimit) {
			this.loadLimit = loadLimit;
		}
		public double getKt() {
			return Kt;
		}
		public void setKt(double kt) {
			Kt = kt;
		}
		
		
		@Override
		public void setValue(String name, int value) {
		
			
		}
		@Override
		public void setValue(String name, double value) {
			// TODO Auto-generated method stub
			
		}
		public double getDturb() {
			return Dturb;
		}
		public void setDturb(double dturb) {
			Dturb = dturb;
		}
	   
	    
	    
}
