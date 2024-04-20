package org.interpss.dstab.control.gov.ieee.hydro1981Type3;

import org.interpss.dstab.control.base.BaseControllerData;

public class Ieee1981Type3HydroGovernorData extends BaseControllerData {
	
	    private double pmax     = 1.0;
	    private double pmin     = 0.0;
	    private double tg       = 0.25;
	    private double tp       = 0.04;
	    private double tr       = 5.0;
	    private double tw       = 1.0;
	    private double velClose = 0.2;
	    private double velOpen  = 0.3;
	    private double sigma    = 0.05;
		private double delta  = 1.0;
		private double a11  = 0.5;
		private double a13  = 1.0;
		private double a21  = 1.5;
		private double a23  = 1.0;
		/**
		 * @return the pmax
		 */
		public double getPmax() {
			return pmax;
		}
		
		/**
		 * @return the pmin
		 */
		public double getPmin() {
			return pmin;
		}

		/**
		 * @return the tg
		 */
		public double getTg() {
			return tg;
		}
		/**
		 * @return the tp
		 */
		public double getTp() {
			return tp;
		}
		/**
		 * @return the td
		 */
		public double getTr() {
			return tr;
		}
		/**
		 * @return the tw
		 */
		public double getTw() {
			return tw;
		}
		/**
		 * @return the velClose
		 */
		public double getVelClose() {
			return velClose;
		}
		/**
		 * @return the velOpen
		 */
		public double getVelOpen() {
			return velOpen;
		}
		/**
		 * @return the delta
		 */
		public double getDelta() {
			return delta;
		}
		/**
		 * @return the sigma
		 */
		public double getSigma() {
			return sigma;
		}
		/**
		 * @param pmax the pmax to set
		 */
		public void setPmax(double pmax) {
			this.pmax = pmax;
		}
		/**
		 * @param pmax the pmin to set
		 */
		public void setPmin(double pmin) {
			this.pmin = pmin;
		}


		/**
		 * @param tg the tg to set
		 */
		public void setTg(double tg) {
			this.tg = tg;
		}
		/**
		 * @param tp the tp to set
		 */
		public void setTp(double tp) {
			this.tp = tp;
		}
		/**
		 * @param tr the Tr to set
		 */
		public void setTr(double tr) {
			this.tr = tr;
		}
		/**
		 * @param tw the tw to set
		 */
		public void setTw(double tw) {
			this.tw = tw;
		}
		/**
		 * @param velClose the velClose to set
		 */
		public void setVelClose(double velClose) {
			this.velClose = velClose;
		}
		/**
		 * @param velOpen the velOpen to set
		 */
		public void setVelOpen(double velOpen) {
			this.velOpen = velOpen;
		}
		/**
		 * @param delta the delta to set
		 */
		public void setDelta(double delta) {
			this.delta = delta;
		}
		/**
		 * @param sigma the sigma to set
		 */
		public void setSigma(double s) {
			this.sigma = s;
		}
		public double getA11() {
			return a11;
		}
		public void setA11(double a11) {
			this.a11 = a11;
		}
		public double getA13() {
			return a13;
		}
		public void setA13(double a13) {
			this.a13 = a13;
		}
		public double getA21() {
			return a21;
		}
		public void setA21(double a21) {
			this.a21 = a21;
		}
		public double getA23() {
			return a23;
		}
		public void setA23(double a23) {
			this.a23 = a23;
		}
		@Override
		public void setValue(String name, double value) {
			if(name.equalsIgnoreCase("pmax"))
		        pmax     = value;
			else if(name.equalsIgnoreCase("pmin"))
		        pmin    = value;
		    else if(name.equals("tg"))
		    tg       = value;
		    else if(name.equals("tp"))
		    tp       = value;
		    else if(name.equals("tr"))
		    tr       = value;
		    else if(name.equals("tw"))
		    tw       = value;
		    else if(name.equals("velClose"))
		    velClose = value;
		    else if(name.equals("velOpen"))
		    velOpen  = value;
		    else if(name.equalsIgnoreCase("delta")||name.equals("Dd"))
		    delta    = value;
		    else if(name.equalsIgnoreCase("Sigma"))
			sigma  = value;
		    else if(name.equalsIgnoreCase("a11"))
				a11  = value;
		    else if(name.equalsIgnoreCase("a13"))
				a13  = value;
		    else if(name.equalsIgnoreCase("a21"))
				a21  = value;
		    else if(name.equalsIgnoreCase("a23"))
				a23  = value;
			
		}
		@Override
		public void setValue(String name, int value) {
		
			
		}
	   
	    
	    
}
