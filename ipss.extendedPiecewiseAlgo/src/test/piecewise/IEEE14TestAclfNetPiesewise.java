 /*

  * @(#)IEEE14TestPiesewise.java   
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

import java.util.function.Function;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.piecewise.PiecewiseAlgorithm;
import org.interpss.piecewise.SubAreaNetProcessor;
import org.interpss.piecewise.seqPos.CuttingBranchPos;
import org.interpss.piecewise.seqPos.SubAreaPos;
import org.interpss.piecewise.seqPos.SubNetworkPos;
import org.interpss.piecewise.seqPos.algo.PiecewiseAlgoPosImpl;
import org.interpss.piecewise.seqPos.impl.SubAreaPosProcessorImpl;
import org.interpss.piecewise.seqPos.impl.SubNetworkPosProcessorImpl;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

/*
 * This test case is for testing piecewise algorithm implementation
 */
public class IEEE14TestAclfNetPiesewise extends PiecewiseAlgoTestSetup {
	/*
	 * Function to compute bus injection current for the testing purpose
	 */
	Function<AclfBus, Complex> injCurFunc = bus -> {
			// The bus injection current is based on gen bus load flow results.
		if (bus.isGen()) {
			//System.out.println("Inj cur -- id, sortNumber, cur: " + bus.getId() + ", " + bus.getSortNumber() + ", " + ComplexFunc.toStr(i));
			return bus.getNetGenResults().divide(bus.getVoltage());
		}
		else 
			return new Complex(0.0, 0.0);
	};
	
	/*
	 * Solve the network Y-matrix using the full matrix approach
	 */
	@Test
	public void testCase1() throws Exception {
		AclfNetwork net = getTestNet();
  		
  		/*
  		 * Solve [Y][I] = [V] using the full network Y matrix	
  		 */
  		ISparseEqnComplex y = net.formYMatrix();
  		//System.out.println(y.toString());
  		
  		net.getBusList().forEach(bus -> {
				if (bus.isGen()) {
					Complex i = injCurFunc.apply(bus);
					y.setBi(i, bus.getSortNumber());
			  		//System.out.println("id, sortNumber, cur: " + bus.getId() + ", " + bus.getSortNumber() + ", " + ComplexFunc.toStr(i));
				}
			});
  		//System.out.println(y.toString());
  		
  		y.solveEqn(1.0e-10);
  		//System.out.println(y.toString());
  		
  		double[] results = {
  				1.1264825855469776, // 1
  				1.045072983281173, // 2
  				0.9516187434024647, // 3
  				0.9786980900382406, // 4
  				0.9936986307025286, // 5
  				0.9701571633081829, // 6
  				0.9701934924024054, // 61
  				0.9511592837364155, // 7
  				0.9511824267205802, // 71
  				0.9240514781352395, // 8
  				0.9431074355212721, // 9
  				0.9431188971724775, // 91
  				0.9405426956916583, // 10
  				0.9516742329989206, // 11
  				0.9543919777573243, // 12
  				0.9489171867669032, // 13
  				0.928346927784917, // 14				
  		};

  		for (int i = 0; i < y.getDimension(); i++) {
  			//System.out.println(y.getX(i).abs() + ", // " + y.getBusId(i));
  			assertTrue(NumericUtil.equals(y.getX(i).abs(), results[i], 1.0e-10));
  		}
  		
  		// turn off the cutting branches
  		/*
  		for (AclfBranch branch : net.getBranchList()) {
  			Complex cur = y.getX(branch.getFromBus().getSortNumber())
  					          .subtract(y.getX(branch.getToBus().getSortNumber()))
  					          .multiply(branch.yft()).multiply(-1.0);
  			//System.out.println(branch.getId() + ": " + ComplexFunc.toStr(cur));
  		}
  		*/
	}
	
	/*
	 * Break the network into two SubAreas and the calculate the bus voltage
	 * 
	 *    In this test case, the subareas are defined by manually
	 */
	@Test
	public void testCase2() throws Exception {
		AclfNetwork net = getTestNet();
  		
  		int areaFlag1 = 1, areaFlag2 = 2;
		
  		PiecewiseAlgorithm<AclfBus, Complex, SubAreaPos> pieceWiseAlgo = new PiecewiseAlgoPosImpl<>(net);
  		SubAreaPos[] subareas = {
  					new SubAreaPos(areaFlag1, new String[] {"4", "5"}), 
  					new SubAreaPos(areaFlag2, new String[] {"71", "91", "61"})};
  		
  		for( SubAreaPos area : subareas)
  			pieceWiseAlgo.getSubAreaNetList().add(area); 

  		CuttingBranchPos[] cuttingBranches = { 
  							new CuttingBranchPos("4->71(1)", areaFlag1, areaFlag2),
  							new CuttingBranchPos("4->91(1)", areaFlag1, areaFlag2),
  							new CuttingBranchPos("5->61(1)", areaFlag1, areaFlag2)};
  		
  		String[][] subAreaBusSet = { {"1", "2", "3", "4", "5"},
                 {"61", "71", "91",  "6", "7", "8", "9", "10", "11", "12", "13", "14"}};
  		for(String s : subAreaBusSet[0]) {
  			net.getBus(s).setSubAreaFlag(areaFlag1);
  		}

  		for(String s : subAreaBusSet[1]) {
  			net.getBus(s).setSubAreaFlag(areaFlag2);
  		}
  		
  		// turn off the cutting branches
  		for (CuttingBranchPos cbra : cuttingBranches) {
  			AclfBranch branch = net.getBranch(cbra.getBranchId());
  			branch.setStatus(false);
  		}
  		
  		/*
  		 * 	4->71(1): 0.31752 + j-0.09918
			5->61(1): 0.54067 + j-0.16539
			4->91(1): 0.18765 + j-0.04213
  		 */
  		
  		/*//////////////////////////////////
  		 * Step-1: Solve for the open-circuit voltage
  		 *//////////////////////////////////
  		
  		pieceWiseAlgo.buildNortonEquivNet(this.injCurFunc);

  		//System.out.println("\n" + pieceWiseAlgo.getNetVoltage().toString());
  		/*
  		 *  91=(-0.19048141263861904, 0.2197102250245422), 
			61=(-0.19176392245354015, 0.22064086630614643), 
			71=(-0.20752175437304488, 0.21566017403601423)
  		 */
  		/*
  		 * Check open circuit voltage results
  		 */
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(areaFlag2).get("91"), new Complex(-0.19048141263861904, 0.2197102250245422), 1.0e-5));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(areaFlag2).get("61"), new Complex(-0.19176392245354015, 0.22064086630614643), 1.0e-5));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(areaFlag2).get("71"), new Complex(-0.20752175437304488, 0.21566017403601423), 1.0e-5));
  		
  		
  		/*/////////////////////////////
  		 * Step-2: calculate cutting branch current
  		 */////////////////////////////
 
    	pieceWiseAlgo.calculateCuttingBranchCurrent(cuttingBranches);
    	
  		// turn off the cutting branches
  		/*
    	for (CuttingBranch cbra : cuttingBranches) {
  			System.out.println(ComplexFunc.toStr(cbra.cur));
  		}
  		*/
    	/*
			Banch cur: 4->71(1) 0.33667 + j-0.1398
			Banch cur: 4->91(1) 0.18592 + j-0.04959
			Banch cur: 5->61(1) 0.46265 + j-0.1147
    	 */
    	/*
    	 * Check cutting branch currents
    	 */
		assertTrue(NumericUtil.equals(cuttingBranches[0].getCurrent(), new Complex(0.33667, -0.1398), 1.0e-4));
		
  		/*//////////////////////////////////////////
  		 * Step-3
  		 *//////////////////////////////////////////
		
		pieceWiseAlgo.calcuateSubAreaNetVoltage(cuttingBranches);  		
 		
		//pieceWiseAlgo.getNetVoltage().forEach((id, v) -> {
  			//System.out.println(v.abs() + ",   //  " + id);
  		//});
  		/*
			0.9336218714652498,   //  10
			1.1582643132837784,   //  1
  		 */
		/*
		 * Checking bus voltage results
		 */
  		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(areaFlag2).get("10").abs(), 0.9336218714652498, 1.0e-10));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(areaFlag1).get("1").abs(), 1.1582643132837784, 1.0e-10));
	}

	/*
	 * Break the network into two SubAreas and the calculate the bus voltage
	 * 
	 *    In this test case, the subareas are calculated by the SubAreaProcessor
	 */	
	@Test
	public void testCase2_1() throws Exception {
		AclfNetwork net = getTestNet();
  		
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
				proc = new SubAreaPosProcessorImpl<>(net, new CuttingBranchPos[] { 
							new CuttingBranchPos("4->71(1)"),
							new CuttingBranchPos("4->91(1)"),
							new CuttingBranchPos("5->61(1)")});	
		
		proc.processSubAreaNet();
  		/*
  		 * Solve [Y][I] = [V] using the piecewise method
  		 * =============================================
  		 */
  		PiecewiseAlgorithm<AclfBus, Complex, SubAreaPos> pieceWiseAlgo = new PiecewiseAlgoPosImpl<>(net, proc.getSubAreaNetList());
  		
  		/*//////////////////////////////////
  		 * Step-1: Solve for the open-circuit voltage
  		 *//////////////////////////////////
  		
  		pieceWiseAlgo.buildNortonEquivNet(this.injCurFunc);

  		//System.out.println("\n" + netVoltage.toString());
  		
  		/*/////////////////////////////
  		 * Step-2: calculate cutting branch current
  		 */////////////////////////////
 
    	pieceWiseAlgo.calculateCuttingBranchCurrent(proc.getCuttingBranches());

		
  		/*//////////////////////////////////////////
  		 * Step-3
  		 *//////////////////////////////////////////
		
		pieceWiseAlgo.calcuateSubAreaNetVoltage(proc.getCuttingBranches());  		
 		
		/*
		 * Checking bus voltage results
		 */
  		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("10").abs(), 0.9336218714652498, 1.0e-10));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(1).get("1").abs(), 1.1582643132837784, 1.0e-10));
	}

	/*
	 * Break the network into two SubNetworks and the calculate the bus voltage
	 * 
	 *    In this test case, the subNetworks are calculated by the SubAreaProcessor
	 */	
	@Test
	public void testCase2_2() throws Exception {
		AclfNetwork net = getTestNet();
  		
		SubAreaNetProcessor<AclfBus, AclfBranch, SubNetworkPos, Complex> proc = 
						new SubNetworkPosProcessorImpl<>(net, new CuttingBranchPos[] { 
				new CuttingBranchPos("4->71(1)"),
  				new CuttingBranchPos("4->91(1)"),
  				new CuttingBranchPos("5->61(1)")});	
		
		proc.processSubAreaNet();
  		/*
  		 * Solve [Y][I] = [V] using the piecewise method
  		 * =============================================
  		 */
  		PiecewiseAlgorithm<AclfBus, Complex, SubNetworkPos> pieceWiseAlgo = 
  						new PiecewiseAlgoPosImpl<>(net, proc.getSubAreaNetList());
  		
  		/*//////////////////////////////////
  		 * Step-1: Solve for the open-circuit voltage
  		 *//////////////////////////////////
  		
  		pieceWiseAlgo.buildNortonEquivNet(this.injCurFunc);

  		//System.out.println("\n" + netVoltage.toString());
  		
  		/*/////////////////////////////
  		 * Step-2: calculate cutting branch current
  		 */////////////////////////////
 
    	pieceWiseAlgo.calculateCuttingBranchCurrent(proc.getCuttingBranches());

		
  		/*//////////////////////////////////////////
  		 * Step-3
  		 *//////////////////////////////////////////
		
		pieceWiseAlgo.calcuateSubAreaNetVoltage(proc.getCuttingBranches());  		
 		
		/*
		 * Checking bus voltage results
		 */
  		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("10").abs(), 0.9336218714652498, 1.0e-10));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(1).get("1").abs(), 1.1582643132837784, 1.0e-10));
	}
	
	/*
	 * Break the network into three SubAreas
	 * 
	 * 	 In this test case, the subareas are defined by manually
	 */
	@Test
	public void testCase4() throws Exception {
		AclfNetwork net = getTestNet();
  		
  		/*
  		 * Solve [Y][I] = [V] using the piecewise method
  		 * =============================================
  		 */
  		
  		int areaFlag1 = 1, areaFlag2 = 2, areaFlag3 = 3;

  		PiecewiseAlgorithm<AclfBus, Complex, SubAreaPos> pieceWiseAlgo = new PiecewiseAlgoPosImpl<>(net);
  		
  		SubAreaPos[] subareas = {
					new SubAreaPos(areaFlag1, new String[] {"4", "5"}), 
					new SubAreaPos(areaFlag2, new String[] {"71", "91", "61", "9", "13"}),
					new SubAreaPos(areaFlag3, new String[] {"14"})};
		
		for( SubAreaPos area : subareas)
			pieceWiseAlgo.getSubAreaNetList().add(area); 

		CuttingBranchPos[] cuttingBranches = { 
					new CuttingBranchPos("4->71(1)", areaFlag1, areaFlag2),
					new CuttingBranchPos("4->91(1)", areaFlag1, areaFlag2),
					new CuttingBranchPos("5->61(1)", areaFlag1, areaFlag2),
					new CuttingBranchPos("9->14(1)", areaFlag2, areaFlag3),
					new CuttingBranchPos("14->13(1)", areaFlag3, areaFlag2) };
  		
  		String[][] subAreaBusSet = { 
					 {"1", "2", "3", "4", "5"},
					 {"61", "71", "91",  "6", "7", "8", "9", "10", "11", "12", "13"}, 
					 { "14"}};
  		
  		for(String s : subAreaBusSet[0]) {
  			net.getBus(s).setSubAreaFlag(areaFlag1);
  		}

  		for(String s : subAreaBusSet[1]) {
  			net.getBus(s).setSubAreaFlag(areaFlag2);
  		}
  		
  		for(String s : subAreaBusSet[2]) {
  			net.getBus(s).setSubAreaFlag(areaFlag3);
  		}  		
  		
  		// turn off the cutting branches
  		for (CuttingBranchPos cbra : cuttingBranches) {
  			AclfBranch branch = net.getBranch(cbra.getBranchId());
  			assertTrue(branch.getFromBus().getSubAreaFlag() != branch.getToBus().getSubAreaFlag());
  			branch.setStatus(false);
  		}
  		
  		/*//////////////////////////////////
  		 * Step-1: Solve for the open-circuit voltage
  		 *//////////////////////////////////
  		
  		pieceWiseAlgo.buildNortonEquivNet(this.injCurFunc);

  		//System.out.println("\n" + pieceWiseAlgo.getNetVoltage().toString());
  		/*
			91=(-0.23360441002180587, 0.25487152860615664), 
			14=(0.0, 0.0), 
			1=(1.5318751236170223, 0.53233297483084) 
  		 */
  		/*
  		 * Check open circuit voltage results
  		 */
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("91"), new Complex(-0.23360441002180587, 0.25487152860615664), 1.0e-10));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(3).get("14"), new Complex(0.0, 0.0), 1.0e-10));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(1).get("1"), new Complex(1.5318751236170223, 0.53233297483084), 1.0e-10));
		
		
  		/*/////////////////////////////
  		 * Step-2: calculate cutting branch current
  		 */////////////////////////////
  		
    	pieceWiseAlgo.calculateCuttingBranchCurrent(cuttingBranches);
    	/*
    	for (CuttingBranch branch: cuttingBranches) {
    		//System.out.println("Branch cur: " + branch.branchId + "  " + ComplexFunc.toStr(branch.cur));
    	}
    	*/	
    	
  		/*//////////////////////////////////////////
  		 * Step-3
  		 *//////////////////////////////////////////
    	
		pieceWiseAlgo.calcuateSubAreaNetVoltage(cuttingBranches);  		
 		/*
		pieceWiseAlgo.getNetVoltage().forEach((id, v) -> {
  			System.out.println(v.abs() + ",   //  " + id);
  		});	
		/*
			0.9336218714651833,   //  10
			1.1582643132837898,   //  1
		*/
		
  		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("10").abs(), 0.9336218714651833, 1.0e-10));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(1).get("1").abs(), 1.1582643132837898, 1.0e-10));
	}

	/*
	 * Break the network into three SubAreas
	 */
	@Test
	public void testCase4_1() throws Exception {
		AclfNetwork net = getTestNet();
		
		SubAreaNetProcessor<AclfBus, AclfBranch, SubAreaPos, Complex> 
				proc = new SubAreaPosProcessorImpl<>(net, new CuttingBranchPos[] { 
							new CuttingBranchPos("4->71(1)"),
							new CuttingBranchPos("4->91(1)"),
							new CuttingBranchPos("5->61(1)"),
							new CuttingBranchPos("9->14(1)"),
							new CuttingBranchPos("14->13(1)")});	
		
		proc.processSubAreaNet();
  		
  		/*
  		 * Solve [Y][I] = [V] using the piecewise method
  		 * =============================================
  		 */
  		
  		PiecewiseAlgorithm<AclfBus, Complex, SubAreaPos> pieceWiseAlgo = new PiecewiseAlgoPosImpl<>(net, proc.getSubAreaNetList());
  		
  		
  		/*//////////////////////////////////
  		 * Step-1: Solve for the open-circuit voltage
  		 *//////////////////////////////////
  		
  		pieceWiseAlgo.buildNortonEquivNet(this.injCurFunc);

  		//System.out.println("\n" + pieceWiseAlgo.getNetVoltage().toString());
		
  		/*/////////////////////////////
  		 * Step-2: calculate cutting branch current
  		 */////////////////////////////
  		
    	pieceWiseAlgo.calculateCuttingBranchCurrent(proc.getCuttingBranches());

    	
  		/*//////////////////////////////////////////
  		 * Step-3
  		 *//////////////////////////////////////////
    	
		pieceWiseAlgo.calcuateSubAreaNetVoltage(proc.getCuttingBranches());  		
 		
  		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("10").abs(), 0.9336218714651833, 1.0e-10));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(1).get("1").abs(), 1.1582643132837898, 1.0e-10));
	}	
	
	private AclfNetwork getTestNet() throws Exception {
		/*
		 * Load the network and run Loadflow
		 */
		AclfNetwork net = CorePluginFactory
					.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
					.load("testdata/ieee14.ipssdat")
					.getAclfNet();	
		
  		//System.out.println(net.net2String());
  		assertTrue((net.getBusList().size() == 17 && net.getBranchList().size() == 23));

  		/*
  		 * Get the default loadflow algorithm and Run loadflow analysis. By default, it uses
  		 * NR method with convergence error tolerance 0.0001 pu
  		 */
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
	  	/*
	  	 * Check if loadflow has converged
	  	 */
  		assertTrue(net.isLfConverged());
  		
  		/*
  		 * Turn all loads to Constant-Z load
  		 */
  		net.getBusList().forEach(bus -> {
  				if (bus.isLoad()) 
  					bus.setLoadCode(AclfLoadCode.CONST_Z);
  			}); 		
  		
  		return net;
	}
	
	/*
	 * This test has issues. It might caused by the condition of the 2nd SubArea Y-matrix
	 */
	//@Test
	public void testCase3() throws Exception {
		AclfNetwork net = getTestNet();
  		
  		/*
  		 * Solve [Y][I] = [V] using the piecewise method
  		 * =============================================
  		 */
  		
  		int areaFlag1 = 1, areaFlag2 = 2, areaFlag3 = 3;

  		PiecewiseAlgorithm<AclfBus, Complex, SubAreaPos> pieceWiseAlgo = new PiecewiseAlgoPosImpl<>(net);
  		
  		SubAreaPos[] subareas = {
					new SubAreaPos(areaFlag1, new String[] {"4", "5"}), 
					new SubAreaPos(areaFlag2, new String[] {"71", "91", "61", "9", "6", "12"}),
					new SubAreaPos(areaFlag3, new String[] {"13", "14"})};
		
		for( SubAreaPos area : subareas)
			pieceWiseAlgo.getSubAreaNetList().add(area); 

		CuttingBranchPos[] cuttingBranches = { 
					new CuttingBranchPos("4->71(1)", areaFlag1, areaFlag2),
					new CuttingBranchPos("4->91(1)", areaFlag1, areaFlag2),
					new CuttingBranchPos("5->61(1)", areaFlag1, areaFlag2),
					new CuttingBranchPos("9->14(1)", areaFlag2, areaFlag3),
					new CuttingBranchPos("6->13(1)", areaFlag2, areaFlag3),
					new CuttingBranchPos("12->13(1)", areaFlag2, areaFlag3)};
  		
  		String[][] subAreaBusSet = { 
					 {"1", "2", "3", "4", "5"},
					 {"61", "71", "91",  "6", "7", "8", "9", "10", "11", "12"}, 
					 { "13", "14"}};
  		
  		for(String s : subAreaBusSet[0]) {
  			net.getBus(s).setSubAreaFlag(areaFlag1);
  		}

  		for(String s : subAreaBusSet[1]) {
  			net.getBus(s).setSubAreaFlag(areaFlag2);
  		}
  		
  		for(String s : subAreaBusSet[2]) {
  			net.getBus(s).setSubAreaFlag(areaFlag3);
  		}  		
  		
  		// turn off the cutting branches
  		for (CuttingBranchPos cbra : cuttingBranches) {
  			AclfBranch branch = net.getBranch(cbra.getBranchId());
  			assertTrue(branch.getFromBus().getSubAreaFlag() != branch.getToBus().getSubAreaFlag());
  			branch.setStatus(false);
  		}
  		
  		/*//////////////////////////////////
  		 * Step-1: Solve for the open-circuit voltage
  		 *//////////////////////////////////
  		
  		pieceWiseAlgo.buildNortonEquivNet(this.injCurFunc);

  		//System.out.println("\n" + pieceWiseAlgo.getNetVoltage().toString());
  		/*
  		 *  91=(-0.21704264054476824, 0.22847433520573676), 
  		 *  14=(0.0, 0.0), 
  		 *  1=(1.5318884131122048, 0.5321466929013828)}

  		 */
  		/*
  		 * Check open circuit voltage results
  		 */
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("91"), new Complex(-0.21704264054476824, 0.22847433520573676), 1.0e-10));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("14"), new Complex(0.0, 0.0), 1.0e-10));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(1).get("1"), new Complex(1.5318884131122048, 0.5321466929013828), 1.0e-10));
		
  		/*/////////////////////////////
  		 * Step-2: calculate cutting branch current
  		 */////////////////////////////
    	pieceWiseAlgo.calculateCuttingBranchCurrent(cuttingBranches);
    	for (CuttingBranchPos branch: cuttingBranches) {
    		System.out.println("Branch cur: " + branch.getBranchId() + "  " + ComplexFunc.toStr(branch.getCurrent()));
    	}

    	/*
4->71(1): 0.31752 + j-0.09918
4->91(1): 0.18765 + j-0.04213
5->61(1): 0.54067 + j-0.16539
9->14(1): 0.08501 + j0.02301
6->13(1): 0.18075 + j0.00149
12->13(1): 0.01788 + j-0.00283
    	 */
    	/*
    	 * Check cutting branch currents
    	 */
		//assertTrue(NumericUtil.equals(cuttingBranches[0].cur, new Complex(0.31752,-0.09918), 1.0e-4));

    	/*//////////////////////////////////////////
  		 * Step-3
  		 *//////////////////////////////////////////
		
		pieceWiseAlgo.calcuateSubAreaNetVoltage(cuttingBranches);  		
 		
		//pieceWiseAlgo.getNetVoltage().forEach((id, v) -> {
  		//	System.out.println(v.abs() + ",   //  " + id);
  		//});		
	}	
}
