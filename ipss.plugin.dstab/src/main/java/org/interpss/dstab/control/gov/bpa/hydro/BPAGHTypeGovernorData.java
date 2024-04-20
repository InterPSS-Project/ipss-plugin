package org.interpss.dstab.control.gov.bpa.hydro;

import org.interpss.dstab.control.base.BaseControllerData;

public class BPAGHTypeGovernorData extends BaseControllerData {
	
	    private double pmax     = 1.0;
	    private double r        = 0.05;
	    private double tg       = 0.25;
	    private double tp       = 0.04;
	    private double td       = 5.0;
	    private double tw       = 1.0;
	    private double velClose = 0.2;
	    private double velOpen  = 0.3;
	    private double delta    = 0.5;
		private double epsilon  = 1.0;
		/**
		 * @return the pmax
		 */
		public double getPmax() {
			return pmax;
		}
		/**
		 * @return the r
		 */
		public double getR() {
			return r;
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
		public double getTd() {
			return td;
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
		 * @return the epsilon
		 */
		public double getEpsilon() {
			return epsilon;
		}
		/**
		 * @param pmax the pmax to set
		 */
		public void setPmax(double pmax) {
			this.pmax = pmax;
		}
		/**
		 * @param r the r to set
		 */
		public void setR(double r) {
			this.r = r;
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
		 * @param td the td to set
		 */
		public void setTd(double td) {
			this.td = td;
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
		 * @param epsilon the epsilon to set
		 */
		public void setEpsilon(double epsilon) {
			this.epsilon = epsilon;
		}
		@Override
		public void setValue(String name, double value) {
			if(name.equals("pmax"))
		        pmax     = value;
		    else if(name.equals("r"))
		    	r        = value;
		    else if(name.equals("tg"))
		    tg       = value;
		    else if(name.equals("tp"))
		    tp       = value;
		    else if(name.equals("td"))
		    td       = value;
		    else if(name.equals("tw"))
		    tw       = value;
		    else if(name.equals("velClose"))
		    velClose = value;
		    else if(name.equals("velOpen"))
		    velOpen  = value;
		    else if(name.equals("delta")||name.equals("Dd"))
		    delta    = value;
		    else if(name.equals("epsilon"))
			epsilon  = value;
		}
		@Override
		public void setValue(String name, int value) {
		
			
		}
	   
	    
	    
}
