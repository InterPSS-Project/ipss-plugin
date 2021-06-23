 /*
  * @(#)IpssAcsc.java   
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
  * @Date 01/02/2011
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.simu;

import static com.interpss.common.util.NetUtilFunc.ToBranchId;

import org.apache.commons.math3.complex.Complex;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.BaseAcscNetwork;
import com.interpss.core.acsc.fault.AcscBranchFault;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.sc.SimpleFaultAlgorithm;
import com.interpss.core.algo.sc.result.IFaultResult;

/**
 * DSL (domain specific language) for AC Short Circuit calculation
 * 
 */
public class IpssAcsc extends BaseDSL {
	// ================ public methods =======================

	/**
	 * create a fault DSL for fault calculation of Acsc Net
	 * 
	 * @param net
	 * @return
	 */
	public static FaultAlgoDSL createAcscAlgo(BaseAcscNetwork<?,?> net) {
		return new FaultAlgoDSL(net);
	}
	
	// ================ FaultAlgoDSL implementation =======================

	/**
	 * DSL for fault calculation of Acsc network
	 * 
	 * @author mzhou
	 *
	 */
	public static class FaultAlgoDSL {
		private static enum FaultType { BusFault, BranchFault };
		
		private SimpleFaultAlgorithm algo = null;
		
		private FaultType faultType = null;
		private AcscBusFault fault = null;
		private AcscBranchFault getBranchFault() { return (AcscBranchFault)this.fault; }
		
		/**
		 * constructor
		 * 
		 * @param net
		 */
		public FaultAlgoDSL(BaseAcscNetwork<?,?> net) {
			this.algo = CoreObjectFactory.createSimpleFaultAlgorithm(net);	 }
		
		/**
		 * get the BaseAcscNetwork<?,?> object
		 * 
		 * @return
		 */
		public BaseAcscNetwork<?,?> getAcscNet() { return (BaseAcscNetwork<?,?>)this.algo.getNetwork(); }
 		
		/**
		 * create a bus fault
		 * 
		 * @param busId
		 * @return
		 */
		public FaultAlgoDSL createBusFault(String busId) {
	  		this.fault = CoreObjectFactory.createAcscBusFault(busId, this.algo);
	  		this.faultType = FaultType.BusFault;
  			return this; }

		/**
		 * create a branch fault
		 * 
		 * @param fromBusId
		 * @param toBusId
		 * @param cirId
		 * @return
		 */
		public FaultAlgoDSL createBranchFault(String fromBusId, String toBusId, String cirId) {
			String braId = ToBranchId.f(fromBusId, toBusId, cirId);
	  		this.fault = CoreObjectFactory.createAcscBranchFault(braId, this.algo);
	  		this.faultType = FaultType.BranchFault;
  			return this; }
		
		/**
		 * set fault code
		 * 
		 * @param code
		 * @return
		 */
		public FaultAlgoDSL faultCode(SimpleFaultCode code) {
			this.fault.setFaultCode(code);
  			return this; }
		
		/**
		 * set Line-ground fault Z
		 * 
		 * @param z
		 * @return
		 */
		public FaultAlgoDSL zLGFault(Complex z){
			this.fault.setZLGFault(z);
  			return this; }
	    
		/**
		 * set Line-line fault z
		 * 
		 * @param z
		 * @return
		 */
		public FaultAlgoDSL zLLFault(Complex z){
			this.fault.setZLLFault(z);
  			return this; }		
		
		/**
		 * set branch fault distance
		 * 
		 * @param d
		 * @return
		 */
		public FaultAlgoDSL distance(double d){
			this.getBranchFault().setDistance(d);
  			return this; }		

		/**
		 * calculate the fault
		 * 
		 * @return
		 */
		public void calculateFault() throws InterpssException {
			if (this.faultType == FaultType.BusFault)
				this.algo.calBusFault(fault);
			else if (this.faultType == FaultType.BranchFault)
				this.algo.calBranchFault((AcscBranchFault)fault);
		}
		
		/**
		 * get fault calculation results
		 * 
		 * @return
		 */
		public IFaultResult getResult() {
			return fault.getFaultResult();
		}
	}
}
