package org.interpss.dstab.control.exc.ieee.y1981.ac1;

public class IEEE1981AC1ExciterData {
	
	 public IEEE1981AC1ExciterData() {}

	    // We need to put the default values here, so that the controller could be
	    // properly initialized
	 
	    private double tr       = 0;
	    private double tc       = 0;
	    private double tb       = 0;
	    private double ka       = 400;
	    private double ta       = 0.02;
	    private double vrmax    = 6.0;
	    private double vrmin    = -6.0;
	    private double ke       = 1.0;
	    private double te       = 0.8;
	    private double kf       = 0.03;
	    private double tf       = 1.0;
	    private double kc       = 0.20;
	    private double kd       = 0.38;
	    private double e1       = 4.18;
	    private double e2       = 3.14;
	    private double se_e1    = 0.1;
	    private double se_e2    = 0.05;
		public double getTr() {
			return tr;
		}
		public void setTr(double tr) {
			this.tr = tr;
		}
		public double getTc() {
			return tc;
		}
		public void setTc(double tc) {
			this.tc = tc;
		}
		public double getTb() {
			return tb;
		}
		public void setTb(double tb) {
			this.tb = tb;
		}
		public double getKa() {
			return ka;
		}
		public void setKa(double ka) {
			this.ka = ka;
		}
		public double getTa() {
			return ta;
		}
		public void setTa(double ta) {
			this.ta = ta;
		}
		public double getVrmax() {
			return vrmax;
		}
		public void setVrmax(double vrmax) {
			this.vrmax = vrmax;
		}
		public double getVrmin() {
			return vrmin;
		}
		public void setVrmin(double vrmin) {
			this.vrmin = vrmin;
		}
		public double getKe() {
			return ke;
		}
		public void setKe(double ke) {
			this.ke = ke;
		}
		public double getTe() {
			return te;
		}
		public void setTe(double te) {
			this.te = te;
		}
		public double getKf() {
			return kf;
		}
		public void setKf(double kf) {
			this.kf = kf;
		}
		public double getTf() {
			return tf;
		}
		public void setTf(double tf) {
			this.tf = tf;
		}
		public double getKc() {
			return kc;
		}
		public void setKc(double kc) {
			this.kc = kc;
		}
		public double getKd() {
			return kd;
		}
		public void setKd(double kd) {
			this.kd = kd;
		}
		public double getE1() {
			return e1;
		}
		public void setE1(double e1) {
			this.e1 = e1;
		}
		public double getE2() {
			return e2;
		}
		public void setE2(double e2) {
			this.e2 = e2;
		}
		public double getSe_e1() {
			return se_e1;
		}
		public void setSe_e1(double se_e1) {
			this.se_e1 = se_e1;
		}
		public double getSe_e2() {
			return se_e2;
		}
		public void setSe_e2(double se_e2) {
			this.se_e2 = se_e2;
		}

}
