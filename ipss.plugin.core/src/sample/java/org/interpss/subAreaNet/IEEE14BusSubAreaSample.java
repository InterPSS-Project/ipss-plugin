package org.interpss.subAreaNet;

import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;

import com.interpss.algo.subAreaNet.SubAreaNetProcessor;
import com.interpss.algo.subAreaNet.seqPos.CuttingBranchPos;
import com.interpss.algo.subAreaNet.seqPos.SubAreaPos;
import com.interpss.algo.subAreaNet.seqPos.impl.SubAreaPosProcessorImpl;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.BranchBusSide;

public class IEEE14BusSubAreaSample {
	
	public static void main(String args[]) throws Exception {
		// initialize InterPSS plugin
		IpssCorePlugin.init();
		
		System.out.println("======================SubArea processing (2 areas) ==================");
		subAreaProcessing();

		System.out.println("======================SubArea processing (3 areas) ==================");
		subAreaProcessing1();
	}
	
	static void subAreaProcessing() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
				.load("testdata/ipssdata/ieee14-1.ipssdat")
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
	
	static void subAreaProcessing1() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
				.load("testdata/ipssdata/ieee14-1.ipssdat")
				.getAclfNet();
				
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
			proc = new SubAreaPosProcessorImpl<>(net, new CuttingBranchPos[] { 
						new CuttingBranchPos("7->8(1)", BranchBusSide.FROM_SIDE),
						new CuttingBranchPos("9->14(1)", BranchBusSide.FROM_SIDE),
						new CuttingBranchPos("14->13(1)", BranchBusSide.FROM_SIDE)});	
		
		proc.processSubAreaNet();
		
		Arrays.asList(proc.getCuttingBranches()).forEach(branch -> {
			System.out.println("Interface: " + branch.toString());
		});
		
		proc.getSubAreaNetList().forEach(subArea -> {
			System.out.println("----------------------");
			System.out.println(subArea);
		});		
		
		net.getBusList().forEach(bus -> {
			System.out.println(bus.getId() + " " + bus.getSubAreaFlag());
		});	
	}
}
