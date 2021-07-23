 /*
  * @(#)IEEE14BusSample.java   
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

package sample.subAreaNet;

import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.piecewise.subAreaNet.SubAreaNetProcessor;
import org.interpss.piecewise.subAreaNet.seqPos.CuttingBranchPos;
import org.interpss.piecewise.subAreaNet.seqPos.SubAreaPos;
import org.interpss.piecewise.subAreaNet.seqPos.SubNetworkPos;
import org.interpss.piecewise.subAreaNet.seqPos.impl.SubAreaPosProcessorImpl;
import org.interpss.piecewise.subAreaNet.seqPos.impl.SubNetworkPosProcessorImpl;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.BranchBusSide;

public class IEEE14BusSubAreaParallelSample {
	
	public static void main(String args[]) throws Exception {
		// initialize InterPSS plugin
		IpssCorePlugin.init();
		
		System.out.println("======================SubArea processing (2 areas, Parallel Branches) ==================");
		subAreaProcessing2();		
		
		System.out.println("======================SubArea processing (2 areas, Parallel Branches) ==================");
		subAreaProcessing3();	
	}
	
	static void subAreaProcessing2() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
				// there are two parallel branches between 7 and 8
				.load("testdata/ipssdata/ieee14-2.ipssdat")
				.getAclfNet();
				
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
			proc = new SubAreaPosProcessorImpl<>(net, new CuttingBranchPos[] { 
						new CuttingBranchPos("4->71(1)", BranchBusSide.FROM_SIDE),
						new CuttingBranchPos("4->91(1)", BranchBusSide.TO_SIDE),
						new CuttingBranchPos("5->61(1)", BranchBusSide.FROM_SIDE)});	
		
		proc.processSubAreaNet();
		
		Arrays.asList(proc.getCuttingBranches()).forEach(branch -> {
			System.out.println("Interface: " + branch.toString());
		});
		
		proc.getSubAreaNetList().forEach(subArea -> {
			System.out.println("----------------------");
			System.out.println(subArea);
		});		
	}
	
	static void subAreaProcessing3() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
				// there are two parallel xfrs 4->71
				.load("testdata/ipssdata/ieee14-3.ipssdat")
				.getAclfNet();
		//System.out.println(net.net2String());
				
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
		proc = new SubAreaPosProcessorImpl<>(net, new CuttingBranchPos[] { 
					new CuttingBranchPos("4->71(1)", BranchBusSide.FROM_SIDE),
					new CuttingBranchPos("4->71(2)", BranchBusSide.FROM_SIDE),					
					new CuttingBranchPos("4->91(1)", BranchBusSide.TO_SIDE),
					new CuttingBranchPos("5->61(1)", BranchBusSide.FROM_SIDE)});	
	
		proc.processSubAreaNet();
		
		Arrays.asList(proc.getCuttingBranches()).forEach(branch -> {
			System.out.println("Interface: " + branch.toString());
		});
		
		proc.getSubAreaNetList().forEach(subArea -> {
			System.out.println("----------------------");
			System.out.println(subArea);
		});				
	}
	
}
