 /*
  * @(#)IpssAclf.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author Mike Zhou
  * @Version 1.0
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */
package org.interpss.plugin.pssl.simu;

import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.IChildNetLfSolver;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * DSL (domain specific language) for AC Loadflow calculation
 * 
 */
public class IpssAclf extends BaseDSL {
	// ================ public methods =======================

	/**
	 * create an AclfAlgo DSL
	 * 
	 * @param net AclfNetwork object
	 * @return
	 */
	public static LfAlgoDSL createAclfAlgo(BaseAclfNetwork<?,?> net) {
		return new LfAlgoDSL(net);
	}
	
	// ================ LfAlgoDSL implementation =======================

	/**
	 * Aclf algorithm DSL for aclf calculation
	 * 
	 * @author mzhou
	 *
	 */
	public static class LfAlgoDSL {
		private LoadflowAlgorithm algo = null;
		private BaseAclfNetwork<?,?> net = null;
		
		/**
		 * constructor
		 * 
		 * @param net AclfNetwork object
		 */
		public LfAlgoDSL(BaseAclfNetwork<?,?> net) {
			this.net = net;
			this.algo = CoreObjectFactory.createLoadflowAlgorithm(this.net);	 }
/*
	   * 	Loadflow calculation
	   *    ====================		
*/		
		/**
		 * set Aclf method
		 * 
		 * @param m
		 * @return
		 */
  		public LfAlgoDSL setLfMethod(AclfMethodType m) { this.algo.setLfMethod(m); return this; }
		/**
		 * set Aclf method
		 * 
		 * @param m
		 * @return
		 */
  		public LfAlgoDSL lfMethod(AclfMethodType m) { return setLfMethod(m); }

  		/**
  		 * set tolerance for Aclf calculation
  		 * 
  		 * @param e
  		 * @param unit
  		 * @return
  		 */
  		public LfAlgoDSL setTolerance(double e, UnitType unit) { this.algo.setTolerance(e, unit, this.net.getBaseKva()); return this; }
  		/**
  		 * set tolerance for Aclf calculation
  		 * 
  		 * @param e
  		 * @param unit
  		 * @return
  		 */
  		public LfAlgoDSL tolerance(double e, UnitType unit) { return setTolerance(e, unit); }

  		/**
  		 * set max iterations for aclf calculation 
  		 * 
  		 * @param n
  		 * @return
  		 */
  		public LfAlgoDSL setMaxIterations(int n) { this.algo.setMaxIterations(n); return this; }
  		/**
  		 * set max iterations for aclf calculation 
  		 * 
  		 * @param n
  		 * @return
  		 */
  		public LfAlgoDSL maxIterations(int n) { return setMaxIterations(n); }

  		/**
  		 * set applying adjustment status
  		 * 
  		 * @param b
  		 * @return
  		 */
  		public LfAlgoDSL setApplyAdjustAlgo(boolean b) { this.algo.getLfAdjAlgo().setApplyAdjustAlgo(b); return this; }
  		/**
  		 * set applying adjustment status
  		 * 
  		 * @param b
  		 * @return
  		 */
  		public LfAlgoDSL applyAdjustAlgo(boolean b) { return setApplyAdjustAlgo(b); }

  		/**
  		 * set none divergence status
  		 * 
  		 * @param b
  		 * @return
  		 */
  		public LfAlgoDSL setNonDivergent(boolean b) { this.algo.setNonDivergent(b); return this; } 		
  		/**
  		 * set none divergence status
  		 * 
  		 * @param b
  		 * @return
  		 */
  		public LfAlgoDSL nonDivergent(boolean b) { return setNonDivergent(b); } 		

  		/**
  		 * set initializing bus voltage status
  		 * 
  		 * @param b
  		 * @return
  		 */
  		public LfAlgoDSL setInitBusVoltage(boolean b) { this.algo.setInitBusVoltage(b); return this; } 		
  		/**
  		 * set initializing bus voltage status
  		 * 
  		 * @param b
  		 * @return
  		 */
  		public LfAlgoDSL initBusVoltage(boolean b) { return setInitBusVoltage(b); } 		

  		/**
  		 * set GS accelerator factor
  		 * 
  		 * @param x
  		 * @return
  		 */
  		public LfAlgoDSL setGsAccFactor(double x) { this.algo.setGsAccFactor(x); return this; } 		
  		/**
  		 * set GS accelerator factor
  		 * 
  		 * @param x
  		 * @return
  		 */
  		public LfAlgoDSL gsAccFactor(double x) { return setGsAccFactor(x); } 		

  		/**
  		 * set multi-network load solver
  		 * 
  		 * @param x
  		 * @return
  		 */
  		public LfAlgoDSL setMultiNetLfSolver(IChildNetLfSolver x) { this.algo.setChildNetSolver(x); return this; } 		
  		/**
  		 * set multi-network load solver
  		 * 
  		 * @param x
  		 * @return
  		 */
  		public LfAlgoDSL multiNetLfSolver(IChildNetLfSolver x) { return setMultiNetLfSolver(x); } 		

  		/**
  		 * run Aclf Loadlow
  		 * 
  		 * @return
  		 * @throws InterpssException 
  		 */
  		public boolean runLoadflow() throws InterpssException { return this.algo.loadflow(); }
	}
}
