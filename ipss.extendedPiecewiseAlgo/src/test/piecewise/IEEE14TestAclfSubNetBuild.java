 /*
  * @(#)IEEE14TestSubNetworkBuild.java   
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
  * @Date 04/15/2016
  * 
  *   Revision History
  *   ================
  *
  */

package test.piecewise;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.piecewise.SubAreaNetProcessor;
import org.interpss.piecewise.seqPos.CuttingBranchPos;
import org.interpss.piecewise.seqPos.SubNetworkPos;
import org.interpss.piecewise.seqPos.impl.SubNetworkPosProcessorImpl;
import org.junit.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

/*
 * This test case is for testing sub-area search algorithm implementation.
 */
public class IEEE14TestAclfSubNetBuild extends PiecewiseAlgoTestSetup {
	@Test
	public void testCase1() throws Exception {
		AclfNetwork net = IEEE14TestSubAreaSearch.getTestNet();
		
		
		SubAreaNetProcessor<AclfBus, AclfBranch, SubNetworkPos, Complex> 
				proc = new SubNetworkPosProcessorImpl<>(net, new CuttingBranchPos[] { 
							new CuttingBranchPos("4->71(1)"),
							new CuttingBranchPos("4->91(1)"),
							new CuttingBranchPos("5->61(1)")});	
  		
  		proc.processSubAreaNet();
  		
  		assertTrue("We should have two sub-network objects", proc.getSubAreaNetList().size() == 2);
  		//System.out.println(proc.getSubAreaList().toString());
  		
  		//System.out.println("Bus-1 subarea flag: " + net.getBus("1").getIntFlag());
  		//System.out.println("Bus-14 subarea flag: " + net.getBus("14").getIntFlag());
  		assertTrue(net.getBus("1").getSubAreaFlag() == 1);
  		assertTrue(net.getBus("14").getSubAreaFlag() == 2);

  		assertTrue("SubNetwork 1 should have 5 buses", proc.getSubAreaNet(1).getSubNet().getBusList().size() == 5);
	
  		assertTrue("SubNetwork 2 should have 12 buses", proc.getSubAreaNet(2).getSubNet().getBusList().size() == 12);
	}

	@Test
	public void testCase2() throws Exception {
		AclfNetwork net = IEEE14TestSubAreaSearch.getTestNet();
		
		SubAreaNetProcessor<AclfBus, AclfBranch, SubNetworkPos, Complex> 
				proc = new SubNetworkPosProcessorImpl<>(net, new CuttingBranchPos[] { 
							new CuttingBranchPos("4->71(1)"),
							new CuttingBranchPos("4->91(1)"),
							new CuttingBranchPos("5->61(1)"),
							new CuttingBranchPos("9->14(1)"),
							new CuttingBranchPos("14->13(1)")});	
  		
  		proc.processSubAreaNet();
  		
  		// we should have three sub-network objects
  		assertTrue("we should have three sub-network objects", proc.getSubAreaNetList().size() == 3);

  		//System.out.println("Bus-1 subarea flag: " + net.getBus("1").getIntFlag());
  		//System.out.println("Bus-14 subarea flag: " + net.getBus("14").getIntFlag());
  		assertTrue(net.getBus("1").getSubAreaFlag() == 1);
  		assertTrue(net.getBus("9").getSubAreaFlag() == 2);
  		assertTrue(net.getBus("14").getSubAreaFlag() == 3);

  		assertTrue("SubNetwork 1 should have 5 buses", proc.getSubAreaNet(1).getSubNet().getBusList().size() == 5);
  		
  		assertTrue("SubNetwork 2 should have 11 buses", proc.getSubAreaNet(2).getSubNet().getBusList().size() == 11);
  		
  		assertTrue("SubNetwork 3 should have 1 buses", proc.getSubAreaNet(3).getSubNet().getBusList().size() == 1);
	}
}
