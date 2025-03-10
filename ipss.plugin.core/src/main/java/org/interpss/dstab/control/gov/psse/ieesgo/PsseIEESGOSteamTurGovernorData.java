package org.interpss.dstab.control.gov.psse.ieesgo;

import org.interpss.dstab.control.base.BaseControllerData;

public class PsseIEESGOSteamTurGovernorData extends BaseControllerData {
	

	    private double t1       = 0.25;
	    private double t2       = 0.04;
	    private double t3       = 5.0;
	    
	    private double k1       = 20.0;
	    private double pmax       = 1.0;
	    private double pmin       = 0.0;
	    private double t4       = 5.0;
	    private double t5       = 5.0;
	    private double t6       = 5.0;
	    
	    private double k2       = 0.5;
	    private double k3       = 0.5;
		
		public double getT1() {
			return t1;
		}
		public void setT1(double t1) {
			this.t1 = t1;
		}
		public double getT2() {
			return t2;
		}
		public void setT2(double t2) {
			this.t2 = t2;
		}
		public double getT3() {
			return t3;
		}
		public void setT3(double t3) {
			this.t3 = t3;
		}
		public double getK1() {
			return k1;
		}
		public void setK1(double k1) {
			this.k1 = k1;
		}
		public double getPmax() {
			return pmax;
		}
		public void setPmax(double pmax) {
			this.pmax = pmax;
		}
		public double getPmin() {
			return pmin;
		}
		public void setPmin(double pmin) {
			this.pmin = pmin;
		}
		public double getT4() {
			return t4;
		}
		public void setT4(double t4) {
			this.t4 = t4;
		}
		public double getT5() {
			return t5;
		}
		public void setT5(double t5) {
			this.t5 = t5;
		}
		public double getT6() {
			return t6;
		}
		public void setT6(double t6) {
			this.t6 = t6;
		}
		public double getK2() {
			return k2;
		}
		public void setK2(double k2) {
			this.k2 = k2;
		}
		public double getK3() {
			return k3;
		}
		public void setK3(double k3) {
			this.k3 = k3;
		}
		@Override
		public void setValue(String name, int value) {
		
			
		}
		@Override
		public void setValue(String name, double value) {
			// TODO Auto-generated method stub
			
		}
	
	   
	    
}
