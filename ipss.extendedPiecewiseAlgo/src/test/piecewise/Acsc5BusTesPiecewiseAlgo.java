 /*
  * @(#)Acsc5BusTesPiecewiseAlgo.java   
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

package test.piecewise;

import static org.junit.Assert.assertTrue;

import java.util.function.Function;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.piecewise.PiecewiseAlgorithm;
import org.interpss.piecewise.SubAreaNetProcessor;
import org.interpss.piecewise.base.BaseCuttingBranch;
import org.interpss.piecewise.seq012.CuttingBranch012;
import org.interpss.piecewise.seq012.SubAcscNetwork;
import org.interpss.piecewise.seq012.SubArea012;
import org.interpss.piecewise.seq012.algo.PiecewiseAlgoAcscImpl;
import org.interpss.piecewise.seq012.impl.SubAreaAcscProcessorImpl;
import org.interpss.pssl.simu.net.IpssAcscNet;
import org.interpss.pssl.simu.net.IpssAcscNet.AcscNetworkDSL;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BusScCode;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.algo.LoadflowAlgorithm;

public class Acsc5BusTesPiecewiseAlgo {
	/*
	 * Function to compute bus injection current for the testing purpose
	 */
	Function<AcscBus, Complex3x1> injCurFunc = bus -> {
			if (bus.getId().equals("2")) {  // Bus '2' (0.0001,0.0), (1.0,0.0),  (0.05,0.0)
				return new Complex3x1(new Complex(0.01,0.0), new Complex(1.0,0.0),  new Complex(0.05,0.0));
			}
			else if (bus.getId().equals("3")) {  // Bus '3'
				return new Complex3x1(new Complex(-0.01,0.0), new Complex(-1.0,0.0),  new Complex(-0.05,0.0));
			}
			else 
				return new Complex3x1();
		};
		
	@Test
	public void subNetworkTest() throws Exception {
		IpssCorePlugin.init();
		
  		AcscNetwork net = getAcscNet();  	
  		
		SubAreaNetProcessor<AcscBus, AcscBranch, SubAcscNetwork, Complex3x1> proc = 
				new SubAreaAcscProcessorImpl<SubAcscNetwork>(net, new CuttingBranch012[] { 
						new CuttingBranch012("2->21(1)"),
						new CuttingBranch012("2->22(1)")});	
  		
  		proc.processSubAreaNet(); 		
  		
  		/*
  		 * Solve [Y][I] = [V] using the piecewise method
  		 * =============================================
  		 */
  		PiecewiseAlgorithm<AcscBus, Complex3x1, SubAcscNetwork> pieceWiseAlgo = new PiecewiseAlgoAcscImpl<>(net, proc.getSubAreaNetList());
  		
  		/*//////////////////////////////////
  		 * Step-1: Solve for the open-circuit voltage
  		 *//////////////////////////////////
  		


  		pieceWiseAlgo.buildNortonEquivNet(injCurFunc);
  		//System.out.println("Open Circuit Voltage\n" + pieceWiseAlgo.getNetVoltage().toString());
/*
5=-0.0000 + j-0.0021  -0.00419 + j-0.01832  -0.00021 + j-0.00092, 
4=-0.0000 + j0.0021  0.0013 + j0.02024  0.00007 + j0.00101, 
3=-0.0000 + j-0.00254  -0.0110 + j-0.0481  -0.00055 + j-0.0024, 
22=-0.0000 + j-0.00254  -0.02139 + j-0.02564  -0.00107 + j-0.00128, 
21=-0.0000 + j-0.00254  -0.01301 + j-0.05171  -0.00065 + j-0.00259}
2=0.0000 + j0.00254  0.00239 + j0.03719  0.00012 + j0.00186, 
1=-0.0000 + j-0.00254  -0.0198 + j-0.02425  -0.00099 + j-0.00121, 
 */
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").a_0, 
				     	new Complex(-0.0000, -0.00254), 1.0e-5));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").b_1, 
			     		new Complex(-0.0110, -0.0481), 1.0e-5));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").c_2, 
			     		new Complex(-0.00055, -0.0024), 1.0e-5));
  		
  		/*/////////////////////////////
  		 * Step-2: calculate cutting branch current
  		 */////////////////////////////
 
    	pieceWiseAlgo.calculateCuttingBranchCurrent(proc.getCuttingBranches());
    	for (BaseCuttingBranch<Complex3x1> cbra : proc.getCuttingBranches()) {
  			//System.out.println(cbra.getBranchId() + ": " + cbra.getCurrent());
  		}
/*
2->21(1): 0.00342 + j0.0000  0.19328 + j0.01941  0.00966 + j0.00097
2->22(1): 0.00151 + j0.0000  0.09527 + j0.00752  0.00476 + j0.00038
 */
		assertTrue(NumericUtil.equals(proc.getCuttingBranches()[0].getCurrent().a_0, 
		     	new Complex(0.00342, 0.0000), 1.0e-5));
		assertTrue(NumericUtil.equals(proc.getCuttingBranches()[0].getCurrent().b_1, 
		     	new Complex(0.19328, 0.01941), 1.0e-5));
		assertTrue(NumericUtil.equals(proc.getCuttingBranches()[0].getCurrent().c_2, 
		     	new Complex(0.00966, 0.00097), 1.0e-5));
		
  		/*//////////////////////////////////////////
  		 * Step-3
  		 *//////////////////////////////////////////
		pieceWiseAlgo.calcuateSubAreaNetVoltage(proc.getCuttingBranches());  		
  		//System.out.println("Closed Circuit Voltage\n" + pieceWiseAlgo.getNetVoltage().toString());
/*
5= -0.0000 + j-0.00106  -0.00291 + j-0.01343  -0.00015 + j-0.00067, 
4= -0.0000 + j0.00106    0.00147 + j0.01437    0.00007 + j0.00072, 
3= -0.0000 + j-0.00128  -0.00765 + j-0.03524  -0.00038 + j-0.00176, 
22=-0.0000 + j0.00128    0.00271 + j0.0263     0.00014 + j0.00132, 
21=-0.0000 + j0.00128    0.00272 + j0.0262     0.00014 + j0.00131}
2=  0.0000 + j0.00128    0.0027 + j0.0264      0.00014 + j0.00132, 
1= -0.0000 + j0.00023    0.00035 + j0.00057    0.00002 + j0.00003, 
 */
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").a_0, 
			     	new Complex(-0.0000, -0.00128), 1.0e-5));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").b_1, 
					new Complex(-0.00765, -0.03524), 1.0e-5));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").c_2, 
					new Complex(-0.00038, -0.00176), 1.0e-5));		
	}

	@Test
	public void subAreaTest() throws Exception {
		IpssCorePlugin.init();
		
  		AcscNetwork net = getAcscNet();  	
  		
		SubAreaNetProcessor<AcscBus, AcscBranch, SubArea012, Complex3x1> proc = 
				new SubAreaAcscProcessorImpl<SubArea012>(net, new CuttingBranch012[] { 
						new CuttingBranch012("2->21(1)"),
						new CuttingBranch012("2->22(1)")});	
  		
  		proc.processSubAreaNet(); 	
  		//System.out.println(net.net2String());
  		
  		
  		/*
  		 * Solve [Y][I] = [V] using the piecewise method
  		 * =============================================
  		 */
  		PiecewiseAlgorithm<AcscBus, Complex3x1, SubArea012> pieceWiseAlgo = new PiecewiseAlgoAcscImpl<>(net, proc.getSubAreaNetList());
  		
  		/*//////////////////////////////////
  		 * Step-1: Solve for the open-circuit voltage
  		 *//////////////////////////////////
   		pieceWiseAlgo.buildNortonEquivNet(injCurFunc);
  		//System.out.println("Open Circuit Voltage\n" + pieceWiseAlgo.getNetVoltage().toString());
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").a_0, 
		     	new Complex(-0.0000, -0.00254), 1.0e-5));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").b_1, 
	     		new Complex(-0.0110, -0.0481), 1.0e-5));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").c_2, 
	     		new Complex(-0.00055, -0.0024), 1.0e-5));

  		/*/////////////////////////////
  		 * Step-2: calculate cutting branch current
  		 */////////////////////////////
 
    	pieceWiseAlgo.calculateCuttingBranchCurrent(proc.getCuttingBranches());
    	for (BaseCuttingBranch<Complex3x1> cbra : proc.getCuttingBranches()) {
  			//System.out.println(cbra.getBranchId() + ": " + cbra.getCurrent());
  		}
		assertTrue(NumericUtil.equals(proc.getCuttingBranches()[0].getCurrent().a_0, 
		     	new Complex(0.00342, 0.0000), 1.0e-5));
		assertTrue(NumericUtil.equals(proc.getCuttingBranches()[0].getCurrent().b_1, 
		     	new Complex(0.19328, 0.01941), 1.0e-5));
		assertTrue(NumericUtil.equals(proc.getCuttingBranches()[0].getCurrent().c_2, 
		     	new Complex(0.00966, 0.00097), 1.0e-5));
		
  		/*//////////////////////////////////////////
  		 * Step-3
  		 *//////////////////////////////////////////
		
		pieceWiseAlgo.calcuateSubAreaNetVoltage(proc.getCuttingBranches());  		
  		//System.out.println("Closed Circuit Voltage\n" + pieceWiseAlgo.getNetVoltage().toString());
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").a_0, 
		     	new Complex(-0.0000, -0.00128), 1.0e-5));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").b_1, 
				new Complex(-0.00765, -0.03524), 1.0e-5));
		assertTrue(NumericUtil.equals(pieceWiseAlgo.getBusVoltage(2).get("3").c_2, 
				new Complex(-0.00038, -0.00176), 1.0e-5));

	}
	
	/*
	 * Full matrix approach
	 */
	@Test
	public void fullMatrixTest() throws Exception {
		IpssCorePlugin.init();
		
  		AcscNetwork net = getAcscNet();  	
/*
a(0,0,'1'): 0.0000 + j-2.42857
a(1,1,'2'): 0.0000 + j-2.7619
a(2,2,'21'): 0.0000 + j-2.7619
a(3,3,'22'): 0.0000 + j-2.7619
a(4,4,'3'): 0.0000 + j-2.33333
a(5,5,'4'): 0.0000 + j-0.0000
a(6,6,'5'): 0.0000 + j-0.0000

Bus '2' injection current [(0.0001,0.0), (1.0,0.0),  (0.05,0.0)]
Bus '3' injection current [(-0.0001,0.0), (-1.0,0.0), (-0.05,0.0)]
*/
  		ISparseEqnComplex y1 = net.formScYMatrix(SequenceCode.POSITIVE, false);
  		y1.setBi(new Complex(1.0,0.0), 1);
  		y1.setBi(new Complex(-1.0,0.0), 4);
  		y1.solveEqn();
  		//System.out.println(y1);
  		/*
  		Complex cur2_21 = y1.getX(1).subtract(y1.getX(2)).multiply(net.getBranch("2->21(1)").getY());
  		System.out.println("cur(1) 2->21: " + ComplexFunc.toStr(cur2_21));
  		Complex cur2_22 = y1.getX(1).subtract(y1.getX(3)).multiply(net.getBranch("2->22(1)").getY());
  		System.out.println("cur(1) 2->22: " + ComplexFunc.toStr(cur2_22));
  		*/
/*
cur(1) 2->21: 0.19328 + j0.01941
cur(1) 2->22: 0.09527 + j0.00752
 */
  		
/*
b(0): 0.00035 + j0.00057
b(1): 0.0027 + j0.0264
b(2): 0.00272 + j0.0262
b(3): 0.00271 + j0.0263
b(4): -0.00765 + j-0.03524
b(5): 0.00147 + j0.01437
b(6): -0.00291 + j-0.01343 		
 */
  		
  		ISparseEqnComplex y2 = net.formScYMatrix(SequenceCode.NEGATIVE, false);
  		y2.setBi(new Complex(0.05,0.0), 1);
  		y2.setBi(new Complex(-0.05,0.0), 4);
  		y2.solveEqn();
  		//System.out.println(y2);
  		/*
  		cur2_21 = y2.getX(1).subtract(y2.getX(2)).multiply(net.getBranch("2->21(1)").getY());
  		System.out.println("cur(2) 2->21: " + ComplexFunc.toStr(cur2_21));
  		cur2_22 = y2.getX(1).subtract(y2.getX(3)).multiply(net.getBranch("2->22(1)").getY());
  		System.out.println("cur(2) 2->22: " + ComplexFunc.toStr(cur2_22));
  		/*
  		cur(2) 2->21: 0.00966 + j0.00097
  		cur(2) 2->22: 0.00476 + j0.00038
  		 */

  		/*
b(0): 0.00002 + j0.00003
b(1): 0.00014 + j0.00132
b(2): 0.00014 + j0.00131
b(3): 0.00014 + j0.00132
b(4): -0.00038 + j-0.00176
b(5): 0.00007 + j0.00072
b(6): -0.00015 + j-0.00067 		
 */
  		ISparseEqnComplex y0 = net.formScYMatrix(SequenceCode.ZERO, false);
  		y0.setBi(new Complex(0.01,0.0), 1);
  		y0.setBi(new Complex(-0.01,0.0), 4);
  		y0.solveEqn();
  		//System.out.println(y0);
  		/*
  		Complex cur2_21 = y0.getX(1).subtract(y0.getX(2)).multiply(net.getBranch("2->21(1)").getY0());
  		System.out.println("cur(0) 2->21: " + ComplexFunc.toStr(cur2_21));
  		Complex cur2_22 = y0.getX(1).subtract(y0.getX(3)).multiply(net.getBranch("2->22(1)").getY0());
  		System.out.println("cur(0) 2->22: " + ComplexFunc.toStr(cur2_22));
        */
  		/*
cur(0) 2->21: 0.00342 + j0.0000
cur(0) 2->22: 0.00151 + j0.0000
  		 */
  		
  		/*
b(0): -0.0000 + j0.00023
b(1): -0.0000 + j0.00128
b(2): -0.0000 + j0.00128
b(3): -0.0000 + j0.00128
b(4): -0.0000 + j-0.00128
b(5): -0.0000 + j0.00106
b(6): -0.0000 + j-0.00106
 */
  	}	

	private AcscNetwork getAcscNet() throws Exception {
  		AcscNetwork net = create5BusSampleNet();
  		//System.out.println(net.net2String());
  		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());  	
  		
  		return net;
	}
	
	private AcscNetwork create5BusSampleNet() throws InterpssException {
		AcscNetworkDSL netDsl = IpssAcscNet.createAcscNetwork("Sample AcscNetwork");
		netDsl.baseMva(100.0);

		netDsl.addAcscBus("1", "name-Bus 1")
		            .baseVoltage(13800.0)
		            .loadCode(AclfLoadCode.CONST_P)
		            .load(new Complex(1.6, 0.8), UnitType.PU)
		            .scCode(BusScCode.NON_CONTRI);
		         
		netDsl.addAcscBus("2", "name-Bus 2")
		            .baseVoltage(13800.0)  
		            .loadCode(AclfLoadCode.CONST_P)
		            .load(new Complex(2.0, 1.0), UnitType.PU)
		            .scCode(BusScCode.NON_CONTRI);

		netDsl.addAcscBus("21", "name-Bus 21")
        			.baseVoltage(13800.0)  
        			.scCode(BusScCode.NON_CONTRI);
		
		netDsl.addAcscBus("22", "name-Bus 22")
					.baseVoltage(13800.0)  
					.scCode(BusScCode.NON_CONTRI);

		netDsl.addAcscBus("3", "name-Bus 3")
        			.baseVoltage(13800.0)  
        			.loadCode(AclfLoadCode.CONST_P)
        			.load(new Complex(3.7, 1.3), UnitType.PU)
        			.scCode(BusScCode.NON_CONTRI);		
		
		netDsl.addAcscBus("4", "name-Bus 4")
        			.baseVoltage(1000.0)  
        			.genCode(AclfGenCode.GEN_PV)
        			.genP_vMag(5.0, UnitType.PU, 1.05, UnitType.PU)
        			.scCode(BusScCode.CONTRIBUTE)
        			.z(new Complex(0.0,0.02), SequenceCode.POSITIVE, UnitType.PU) 
        			.z(new Complex(0.0,0.02), SequenceCode.NEGATIVE, UnitType.PU) 
        			.z(new Complex(0.0,0.2), SequenceCode.ZERO, UnitType.PU) 
        			.groundCode("SolidGrounded")
        			.groundZ(new Complex(0.0, 0.0), UnitType.PU);	
		
		netDsl.addAcscBus("5", "name-Bus 5")
        			.baseVoltage(4000.0)  
        			.genCode(AclfGenCode.SWING)
        			.voltageSpec(1.05, UnitType.PU, 5.0, UnitType.Deg)
        			.scCode(BusScCode.CONTRIBUTE)
        			.z(new Complex(0.0,0.02), SequenceCode.POSITIVE, UnitType.PU) 
        			.z(new Complex(0.0,0.02), SequenceCode.NEGATIVE, UnitType.PU) 
        			.z(new Complex(0.0,0.2), SequenceCode.ZERO, UnitType.PU) 
        			.groundCode("SolidGrounded")
        			.groundZ(new Complex(0.0, 0.0), UnitType.PU);	
		
		netDsl.addAcscBranch("2", "21")
        			.branchCode(AclfBranchCode.LINE)
        			.z(new Complex(0.0, 0.001), UnitType.PU)
        			.z0(new Complex(0.0, 0.001), UnitType.PU);  
		
		netDsl.addAcscBranch("2", "22")
        			.branchCode(AclfBranchCode.LINE)
        			.z(new Complex(0.0, 0.001), UnitType.PU)
        			.z0(new Complex(0.0, 0.001), UnitType.PU);  
		
		netDsl.addAcscBranch("1", "22")
		            .branchCode(AclfBranchCode.LINE)
		            .z(new Complex(0.04, 0.25), UnitType.PU)
		            .shuntB(0.5, UnitType.PU)
		            .z0(new Complex(0.0, 0.7), UnitType.PU);     
		
		netDsl.addAcscBranch("1", "3")
        			.branchCode(AclfBranchCode.LINE)
        			.z(new Complex(0.1, 0.35), UnitType.PU) 
        			.z0(new Complex(0.0,1.0), UnitType.PU);
		
		netDsl.addAcscBranch("21", "3")
        			.branchCode(AclfBranchCode.LINE)
        			.z(new Complex(0.08, 0.3), UnitType.PU)
        			.shuntB(0.5, UnitType.PU)
        			.z0(new Complex(0.0,0.75), UnitType.PU);
		
		netDsl.addAcscBranch("4", "2")
					.branchCode(AclfBranchCode.XFORMER)
					.z(new Complex(0.0, 0.015), UnitType.PU)
					.turnRatio(1.0,  1.05, UnitType.PU)
					.z0( new Complex(0.0, 0.03), UnitType.PU)
					.fromGrounding(XfrConnectCode.WYE_SOLID_GROUNDED)
					.toGrounding(XfrConnectCode.WYE_SOLID_GROUNDED);
		
		netDsl.addAcscBranch("5", "3")
					.branchCode(AclfBranchCode.XFORMER)
					.z(new Complex(0.0, 0.03), UnitType.PU)
					.turnRatio(1.0,  1.05, UnitType.PU)
					.z0(new Complex(0.0, 0.03), UnitType.PU)
					.fromGrounding(XfrConnectCode.WYE_SOLID_GROUNDED)
					.toGrounding(XfrConnectCode.WYE_SOLID_GROUNDED);

		//System.out.println(netDsl.getAcscNet().net2String());
		return (AcscNetwork)netDsl.getAclfNet();
	}	
}

