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

package org.interpss.core.ca;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.algo.ZeroZBranchProcesor;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.pssl.plugin.IpssAdapter;


public class IEEE14BusBreaker_equivCABranch_Test extends CorePluginTestSetup {
	@Test 
	public void case1_smallZ() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee_odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getAclfNet();
	  	//System.out.println(net.net2String());
		
	  	net.accept(new ZeroZBranchProcesor(true));
	  	assertTrue(net.isZeroZBranchProcessed());

	  	/*
	  	 * identify equivalent CA branches
	  	 */
	  	//IpssLogger.ipssLogger.setLevel(Level.INFO);
	  	//for (Branch branch : net.getBranchList()) 
	  	//	branch.identifyEquivCABranch();	  	
	  	
	  	//System.out.println("Branch Bus1->Bus15-1(1) equivCABranch: " + net.getBranch("Bus1->Bus15-1(1)").getEquivCABranchId());
	  	//System.out.println("Branch Bus15-1->Bus15(1) equivCABranch: " + net.getBranch("Bus15-1->Bus15(1)").getEquivCABranchId());
	  	
	  	/*
	  	 * Branch Bus1->Bus15-1(1) and Bus15-1->Bus15(1) are small-Z branches. Their equiv CA branch
	  	 * is branch Bus15->Bus2 with normal Z.
	  	 */
	  	//assertTrue(net.getBranch("Bus1->Bus15-1(1)").getEquivCABranchId().equals("Bus15->Bus2(1)"));
	  	//assertTrue(net.getBranch("Bus15-1->Bus15(1)").getEquivCABranchId().equals("Bus15->Bus2(1)"));
	}	
	
	@Test 
	public void case1_smallZ_1() throws  InterpssException {
		// test casa with a small-Z brach loop at Bus-14
		
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee_odm/ieee14Bus_breaker_1.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getAclfNet();
	  	//System.out.println(net.net2String());
		
	  	net.accept(new ZeroZBranchProcesor(true));
	  	assertTrue(net.isZeroZBranchProcessed());

	  	//IpssLogger.ipssLogger.setLevel(Level.INFO);
	  	//for (Branch branch : net.getBranchList()) 
	  	//	branch.identifyEquivCABranch();	  	
	  	
	  	//System.out.println("Branch Bus1->Bus15(1) equivCABranch: " + net.getBranch("Bus1->Bus15(1)").getEquivCABranchId());
	  	//assertTrue(net.getBranch("Bus1->Bus15(1)").getEquivCABranchId().equals("Bus15->Bus2(1)"));
    }
}
