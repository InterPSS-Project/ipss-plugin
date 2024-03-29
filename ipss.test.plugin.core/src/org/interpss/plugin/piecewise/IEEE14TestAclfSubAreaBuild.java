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

package org.interpss.plugin.piecewise;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.junit.Test;

import com.interpss.algo.subAreaNet.SubAreaNetProcessor;
import com.interpss.algo.subAreaNet.seqPos.CuttingBranchPos;
import com.interpss.algo.subAreaNet.seqPos.SubAreaPos;
import com.interpss.algo.subAreaNet.seqPos.impl.SubNetworkPosProcessorImpl;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.BranchBusSide;

/*
 * This test case is for testing sub-area search algorithm implementation.
 */
public class IEEE14TestAclfSubAreaBuild extends PiecewiseAlgoTestSetup {
	@Test
	public void splitNetworkTest1() throws Exception {
		AclfNetwork net = IEEE14TestSubAreaSearch.getTestNet();
		
		
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
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
	}
	
	@Test
	public void splitNetworkTest_FromSide() throws Exception {
		AclfNetwork net = IEEE14TestSubAreaSearch.getTestNet();
				
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
				proc = new SubNetworkPosProcessorImpl<>(net, new CuttingBranchPos[] { 
							new CuttingBranchPos("4->71(1)", BranchBusSide.FROM_SIDE),
							new CuttingBranchPos("4->91(1)", BranchBusSide.FROM_SIDE),
							new CuttingBranchPos("5->61(1)", BranchBusSide.FROM_SIDE)});	
  		
  		List<SubAreaPos> subAreaList = proc.processSubAreaNet(true);  // SingleSide SubArea case.
  		
  		assertTrue("We should have 1 sub-network objects", proc.getSubAreaNetList().size() == 1);
  		assertTrue("We should have 1 sub-network objects", subAreaList.size() == 1);
  		//System.out.println(proc.getSubAreaList().toString());
  		
  		assertTrue(net.getBus("14").getSubAreaFlag() == 1);
	}

	@Test
	public void splitNetworkTest_ToSide() throws Exception {
		AclfNetwork net = IEEE14TestSubAreaSearch.getTestNet();
				
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
				proc = new SubNetworkPosProcessorImpl<>(net, new CuttingBranchPos[] { 
							new CuttingBranchPos("4->71(1)", BranchBusSide.TO_SIDE),
							new CuttingBranchPos("4->91(1)", BranchBusSide.TO_SIDE),
							new CuttingBranchPos("5->61(1)", BranchBusSide.TO_SIDE)});	
  		
		List<SubAreaPos> subAreaList = proc.processSubAreaNet(true);  // SingleSide SubArea case.
  		
  		assertTrue("We should have 1 sub-network objects", proc.getSubAreaNetList().size() == 1);
  		assertTrue("We should have 1 sub-network objects", subAreaList.size() == 1);
  		//System.out.println(proc.getSubAreaList().toString());
  		
  		assertTrue(net.getBus("4").getSubAreaFlag() == 1);
	}
	
	@Test
	public void splitNetworkTest2() throws Exception {
		AclfNetwork net = IEEE14TestSubAreaSearch.getTestNet();
	
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
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
	}
	
	@Test
	public void splitMergeNetworkTest1() throws Exception {
		AclfNetwork net = IEEE14TestSubAreaSearch.getTestNet();
		net.setId("Parenet Net");
		
		assertTrue("", net.getBusList().size() == 17);
		
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
				proc = new SubNetworkPosProcessorImpl<>(net, new CuttingBranchPos[] { 
							new CuttingBranchPos("4->71(1)"),
							new CuttingBranchPos("4->91(1)"),
							new CuttingBranchPos("5->61(1)"),
							new CuttingBranchPos("9->14(1)"),
							new CuttingBranchPos("14->13(1)")});	
  		
  		proc.processSubAreaNet();
  		
  		/*
  		 * Please note: The following behavior has been changed. The Parent Net still has
  		 * references to all Bus/Branch objects after the splitting. 
  		 */
  		assertTrue("Parent Net should have 17 still buses", net.getBusList().size() == 17);

  		assertTrue("Bus 1 in the SubNet-1",  net.getBus("1").getNetwork().getId().equals("SubNet-1"));
  		assertTrue("Bus 9 in the SubNet-2", net.getBus("9").getNetwork().getId().equals("SubNet-2"));
  		assertTrue("Bus 14 in the SubNet-14", net.getBus("14").getNetwork().getId().equals("SubNet-3"));
  		
  		//System.out.println(net.getBranch("1->2(1)").getNetwork().getId());
  		//System.out.println(net.getBranch("5->61(1)").getNetwork().getId());
  		assertTrue("Branch 1->2(1) should be in SubNet-1", net.getBranch("1->2(1)").getNetwork().getId().equals("SubNet-1"));
  		assertTrue("Branch 5->61(1) should be in SubNet-2", net.getBranch("5->61(1)").getNetwork().getId().equals("SubNet-2"));
	}
	
	@Test
	public void splitMergeNetworkTest2() throws Exception {
		AclfNetwork net = IEEE14TestSubAreaSearch.getTestNet();
		net.setId("Parenet Net");
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		assertTrue(net.isLfConverged());
  		//System.out.println(net.net2String());
		
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
				proc = new SubNetworkPosProcessorImpl<>(net, new CuttingBranchPos[] { 
							new CuttingBranchPos("4->71(1)"),
							new CuttingBranchPos("4->91(1)"),
							new CuttingBranchPos("5->61(1)"),
							new CuttingBranchPos("9->14(1)"),
							new CuttingBranchPos("14->13(1)")});	
  		
  		proc.processSubAreaNet();
  		
  		assertTrue("Bus 1 in the SubNet-1",  net.getBus("1").getNetwork().getId().equals("SubNet-1"));
  		assertTrue("Bus 9 in the SubNet-2", net.getBus("9").getNetwork().getId().equals("SubNet-2"));
  		assertTrue("Bus 14 in the SubNet-14", net.getBus("14").getNetwork().getId().equals("SubNet-3"));  		
	}
}
