 /*
  * @(#)IEEE14TestSubAreaSearch.java   
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
import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.piecewise.SubAreaNetProcessor;
import org.interpss.piecewise.seqPos.CuttingBranchPos;
import org.interpss.piecewise.seqPos.SubAreaPos;
import org.interpss.piecewise.seqPos.impl.SubAreaPosProcessorImpl;
import org.junit.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

/*
 * This test case is for testing sub-area search algorithm implementation.
 */
public class IEEE14TestSubAreaSearch extends PiecewiseAlgoTestSetup {
	private static final int DefaultFlag = -1;
	
	@Test
	public void testCase1() throws Exception {
		AclfNetwork net = getTestNet();
		
		
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> proc = 
				new SubAreaPosProcessorImpl<>(net, new CuttingBranchPos[] { 
						new CuttingBranchPos("4->71(1)"),
						new CuttingBranchPos("4->91(1)"),
						new CuttingBranchPos("5->61(1)")});	
  		
  		proc.processSubAreaNet();
  		
  		proc.getSubAreaNetList().forEach(subarea -> {
  			//System.out.println(subarea);
  		});
  		
  		assertTrue(proc.getSubAreaNetList().size() == 2);
  		assertTrue(proc.getSubAreaNet(1).getInterfaceBusIdList().size() == 2);
  		assertTrue(proc.getSubAreaNet(2).getInterfaceBusIdList().size() == 3);
  		
  		/*
  		 * The subarea info is stored at
  		 *   (1) SubArea.flag field
  		 *   (2) Bus.infFlag field
  		 *   (3) CuttingBranch.fromSubAreaFlag/toSubAreaFlag fields
  		 */
  		net.getBusList().forEach(bus -> {
  			assertTrue(bus.getSubAreaFlag() != DefaultFlag);
  			//System.out.println(bus.getId() + "," + bus.getIntFlag());
  			if (bus.getId().equals("2")) assertTrue(bus.getSubAreaFlag() == 1);
  			if (bus.getId().equals("13")) assertTrue(bus.getSubAreaFlag() == 2);
  		});
  		
  		assertTrue(proc.getSubAreaNetList().get(0).getFlag() == 1);
  		assertTrue(proc.getSubAreaNetList().get(1).getFlag() == 2);
  		proc.getSubAreaNetList().forEach(subarea -> {
  			subarea.getInterfaceBusIdList().forEach(id -> {
  				assertTrue(net.getBus(id).getSubAreaFlag() == subarea.getFlag());
  			});
  		});
  		
  		// [0] "4->71(1)"
  		assertTrue(proc.getCuttingBranches()[0].getFromSubAreaFlag() == 1);
  		assertTrue(proc.getCuttingBranches()[0].getToSubAreaFlag() == 2);
	}

	@Test
	public void testCase2() throws Exception {
		AclfNetwork net = getTestNet();
		
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
				proc = new SubAreaPosProcessorImpl<>(net, new CuttingBranchPos[] { 
							new CuttingBranchPos("4->71(1)"),
							new CuttingBranchPos("4->91(1)"),
							new CuttingBranchPos("5->61(1)"),
							new CuttingBranchPos("9->14(1)"),
							new CuttingBranchPos("14->13(1)")});	
		// make sure all cutting branches are in the network
  		for (int i = 0; i < proc.getCuttingBranches().length; i++) {
  			AclfBranch branch = net.getBranch(proc.getCuttingBranches()[i].getBranchId());
  			assertTrue(proc.getCuttingBranches()[i].getBranchId() + " not found!", branch != null);
  		};
  		
  		proc.processSubAreaNet();
  		
  		proc.getSubAreaNetList().forEach(subarea -> {
  			//System.out.println(subarea);
  		});
  		
  		assertTrue(proc.getSubAreaNetList().size() == 3);
  		assertTrue(proc.getSubAreaNet(1).getInterfaceBusIdList().size() == 2);
  		assertTrue(proc.getSubAreaNet(2).getInterfaceBusIdList().size() == 5);
  		assertTrue(proc.getSubAreaNet(3).getInterfaceBusIdList().size() == 1);  		
  		
  		/*
  		 * The subarea info is stored at
  		 *   (1) SubArea.flag field
  		 *   (2) Bus.infFlag field
  		 *   (3) CuttingBranch.fromSubAreaFlag/toSubAreaFlag fields
  		 */
  		net.getBusList().forEach(bus -> {
  			assertTrue(bus.getSubAreaFlag() != DefaultFlag);
  			//System.out.println(bus.getId() + "," + bus.getIntFlag());
  			if (bus.getId().equals("2")) assertTrue(bus.getSubAreaFlag() == 1);
  			if (bus.getId().equals("61")) assertTrue(bus.getSubAreaFlag() == 2);
  			if (bus.getId().equals("14")) assertTrue(bus.getSubAreaFlag() == 3);  			
  		});
  		
  		assertTrue(proc.getSubAreaNetList().get(0).getFlag() == 1);
  		assertTrue(proc.getSubAreaNetList().get(1).getFlag() == 2);
  		assertTrue(proc.getSubAreaNetList().get(2).getFlag() == 3);
  		proc.getSubAreaNetList().forEach(subarea -> {
  			subarea.getInterfaceBusIdList().forEach(id -> {
  				assertTrue(net.getBus(id).getSubAreaFlag() == subarea.getFlag());
  			});
  		});
  		
  		// [0] "4->71(1)"
  		assertTrue(proc.getCuttingBranches()[0].getFromSubAreaFlag() == 1);
  		assertTrue(proc.getCuttingBranches()[0].getToSubAreaFlag() == 2);
  		// [4] "14->13(1)
  		assertTrue(proc.getCuttingBranches()[4].getFromSubAreaFlag() == 3);
  		assertTrue(proc.getCuttingBranches()[4].getToSubAreaFlag() == 2);
	}
	
	public static AclfNetwork getTestNet() throws Exception {
		/*
		 * Load the network and run Loadflow
		 */
		AclfNetwork net = CorePluginFactory
					.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
					.load("testdata/ieee14.ipssdat")
					.getAclfNet();	
		
  		//System.out.println(net.net2String());
  		assertTrue((net.getBusList().size() == 17 && net.getBranchList().size() == 23));

  		return net;
	}
}
