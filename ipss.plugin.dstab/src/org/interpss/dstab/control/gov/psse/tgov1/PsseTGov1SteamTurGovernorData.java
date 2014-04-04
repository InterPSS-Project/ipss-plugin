package org.interpss.dstab.control.gov.psse.tgov1;

import org.interpss.dstab.control.base.BaseControllerData;

public class PsseTGov1SteamTurGovernorData extends BaseControllerData {
	

	    private double t1       = 0.25;
	    private double t2       = 0.04;
	    private double t3       = 5.0;
		private double R        = 0.05;
		private double Dt       =0.0;
		private double vMax     =1.0;
		public double getvMax() {
			return vMax;
		}
		public void setvMax(double vMax) {
			this.vMax = vMax;
		}
		public double getvMin() {
			return vMin;
		}
		public void setvMin(double vMin) {
			this.vMin = vMin;
		}
		private double vMin     =0.0;
		
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
		
		@Override
		public void setValue(String name, int value) {
		
			
		}
		@Override
		public void setValue(String name, double value) {
			// TODO Auto-generated method stub
			
		}
		public double getDt() {
			return Dt;
		}
		public void setDt(double dt) {
			Dt = dt;
		}
	   
	    
	    
}
