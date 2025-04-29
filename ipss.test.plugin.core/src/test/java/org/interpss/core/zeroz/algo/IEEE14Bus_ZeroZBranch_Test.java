 /*
  * @(#)SampleLoadflow.java   
  *
  * Copyright (C) 2006 www.interpss.org
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
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.zeroz.algo;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Branch;
import com.interpss.core.net.BranchFlowDirection;


public class IEEE14Bus_ZeroZBranch_Test extends CorePluginTestSetup {
	@Test 
	public void regularMethod() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		
		net.setZeroZBranchThreshold(0.000000000000001);
		
	  	//System.out.println(net.net2String());

	  	// create the default loadflow algorithm
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);

	  	// use the loadflow algorithm to perform loadflow calculation
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
	  	
	  	// output loadflow calculation results
	  	//System.out.println(aclfResultBusStyle.apply(net));
	  	
		net.setZeroZBranchThreshold(0.00001);
	  	// (-0.12495394051074982, 0.08613362908363342)
	  	//System.out.println(net.getBus("Bus14").currentIntoNet());
	  	//System.out.println(net.getBus("Bus18").currentIntoNet());
	  	//System.out.println(net.getBus("Bus17").currentIntoNet());
	  	
	  	/*
	  	AclfBranch bra18_14 = net.getBranch("Bus18->Bus14(1)");
	  	System.out.println("Bus18 side: " + powerFrom2To(bra18_14));
	  	System.out.println("Bus14 side: " + powerTo2From(bra18_14));

	  	AclfBranch bra17_14 = net.getBranch("Bus17->Bus14(1)");
	  	System.out.println("Bus17 side: " + powerFrom2To(bra17_14));
	  	System.out.println("Bus14 side: " + powerTo2From(bra17_14));	  	
	  	
/*
	  	//System.out.println("Active buses: " + net.getNoActiveBus() + ", branches: " + net.getNoActiveBranch());
	  	assertTrue(net.getNoActiveBus() == 23);
	  	assertTrue(net.getNoActiveBranch() == 30);
	  	
  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBus swing = swingBus.toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.3240)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.1655)<0.0001);
*/  		
    }	
	
	public Complex currentIntoNet(BaseAclfBus bus) throws InterpssException {
		return currentIntoNet(bus, null);
	}
	
	public Complex currentIntoNet(BaseAclfBus bus, AclfBranch excludeBranch) throws InterpssException {
		Complex sum = bus.getVoltage().multiply(bus.yii());
		for( Branch b : bus.getBranchList()) {
			if ( b.isActive() && b instanceof AclfBranch) {
				if (excludeBranch != null && b.getId().equals(excludeBranch.getId()))
					;  // bypass the branch
				else {
					AclfBranch bra = (AclfBranch)b;
					if (bra.isZeroZBranch())
						sum = sum.add(smallZBranchCurrent(bra, bra.isFromBus(bus)? BranchFlowDirection.FROM_TO : BranchFlowDirection.TO_FROM));
					else {
						Complex vj = bra.isGroundBranch() ?	new Complex(0.0,0.0) :
				                ( this.equals(bra.getFromBus()) ?
									bra.getToAclfBus().getVoltage() : bra.getFromAclfBus().getVoltage() );
						if ( this.equals(bra.getFromBus()) ) {
							sum = sum.add( bra.yft().multiply(vj) );
							//sum = sum.add( bra.currentIntoNetFromSide());
						}
						else {
							sum = sum.add( bra.ytf().multiply(vj) );
							//sum = sum.add( bra.currentIntoNetToSide());
						}
					}
				}
			}
		}
		
		if (excludeBranch != null) {
			if (!bus.isSwing() && !bus.isGenPV()) {
				// p = getVoltage().multiply(currentIntoNet().conjugate());
				// cur = conjugate( p / v )
				Complex p = new Complex(0.0,0.0);
				if (bus.isGenPQ())
					p = p.subtract(new Complex(bus.getGenP(),bus.getGenQ()));
				if (bus.isConstPLoad())
					p = p.add(new Complex(bus.getLoadP(),bus.getLoadQ()).multiply(bus.getVoltageMag()));
				else if (bus.isConstILoad())
					p = p.add(new Complex(bus.getLoadP(),bus.getLoadQ()));
				sum = sum.add(p.divide(bus.getVoltage()).conjugate());
			}
			else {
				
			}
		}
		
		return sum;
	}
	
	Complex smallZBranchCurrent(AclfBranch branch, BranchFlowDirection dir) throws InterpssException {
		// FROM-TO dir, currnt is calculated at the TO-side
		return currentIntoNet(dir == BranchFlowDirection.FROM_TO? branch.getToAclfBus() : branch.getFromAclfBus(), branch);
	}
	
	/*
	 *            ToSide of a ZeroZ Branch
	 * 
	 *                     |  -> Bus power (bus cannot be PV or Swing)
	 *          To2From <- |  -> Sum ( non-zeroZ branch power)
	 *                     |  -> Sum ( recursive calculate zero-Z branch power)
	 */
	Complex powerTo2From(AclfBranch branch) throws InterpssException {
		if (branch.isZeroZBranch()) {
			return samllZBranchFlowTo2From(branch);
		}
		else
			return branch.powerTo2From();
	}

	Complex powerFrom2To(AclfBranch branch)  throws InterpssException {
		if (branch.isZeroZBranch()) {
			return samllZBranchFlowTo2From(branch).multiply(-1.0);
		}
		else
			return branch.powerFrom2To();
	}
	
	Complex samllZBranchFlowTo2From(AclfBranch branch) throws InterpssException {
		Complex to = samllZBranchFlow(branch, branch.getToAclfBus()),
				from = samllZBranchFlow(branch, branch.getFromAclfBus());
		if (to != null && from != null)
			return to.subtract(from).divide(2.0);
		else if (to != null)
			return to;
		else if (from != null)			
			return from.multiply(-1.0);
		else 
			throw new InterpssException("");		
	}
	
	/*
	 * calculate branch power at the bus side, out of the bus as the
	 * positive direction

	 *           Bus of a ZeroZ Branch
	 * 
	 *               |  <- Bus power (bus cannot be PV or Swing)
	 *          p <- |  <- Sum ( non-zeroZ branch power)
	 *               |  <- Sum ( recursive calculate zero-Z branch power on the opposite side)
	 */
	Complex samllZBranchFlow(AclfBranch branch, BaseAclfBus bus) throws InterpssException {
		if (bus.isSwing() || bus.isGenPV())
			return null; 
		Complex p = new Complex(0.0,0.0);
		if (bus.isGenPQ())
			p = p.add(new Complex(bus.getGenP(),bus.getGenQ()));
		else if (bus.isCapacitor())
			p = p.add(new Complex(0.0, bus.toCapacitorBus().getQResults(UnitType.PU)));
		if (bus.isLoad())
			p = p.subtract(bus.calNetLoadResults());
		
		for( Branch b : bus.getBranchList()) {
			if ( !b.getId().equals(branch.getId()) && b.isActive() && b instanceof AclfBranch) {
				AclfBranch bra = (AclfBranch)b;
				if (bra.isZeroZBranch()) {
					// since the branch is a small Z branch, we need to use the opposite side to 
					// recursively continue the calculation
					Complex x = samllZBranchFlow(bra, (AclfBus)bra.getOppositeBus(bus).get());
					if (x == null)
						return null;
					p = p.add(x);
				}
				else {
					p = p.subtract(bra.isFromBus(bus)? bra.powerFrom2To() : bra.powerTo2From());
				}
			}
		}
		
		return p;
	}
	
}
