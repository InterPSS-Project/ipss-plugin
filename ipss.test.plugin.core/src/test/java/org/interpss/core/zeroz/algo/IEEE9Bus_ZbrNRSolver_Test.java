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

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import static org.interpss.CorePluginFunction.aclfResultBusStyle;
import org.interpss.CorePluginTestSetup;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.impl.solver.ZbrNrSolver;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;


public class IEEE9Bus_ZbrNRSolver_Test extends CorePluginTestSetup {
	@Test 
	public void regularMethod() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = createTestCase();
		
		net.setZeroZBranchThreshold(1.0E-5);

	  	//System.out.println(net.net2String());

	  	// create the default loadflow algorithm
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);

	  	// use the loadflow algorithm to perform loadflow calculation
	  	algo.setLfMethod(AclfMethodType.NR);
		algo.setInitBusVoltage(true);
	  	algo.loadflow();
		//assertTrue(!net.isLfConverged());
	  	
	  	// output loadflow calculation results
	  	System.out.println(aclfResultBusStyle.apply(net));

		/**
		 * ------------------------------------------------------------------------------------------------------------------------------------------
			Bus ID            Bus Voltage          Generation           Load             To             Branch P+jQ          Xfr Ratio   PS-Xfr Ang
						baseKV  Mag/pu  Ang/deg   (mW)    (mVar)    (mW)    (mVar)      Bus ID      (mW)    (mVar)   (kA)   (From)  (To) (from)   (to)
			------------------------------------------------------------------------------------------------------------------------------------------
			Bus1          16.500  1.0400   0.00    71.64    27.10     0.00     0.00  Bus4            71.64    27.10   2.577              
			Bus2          18.000  1.0250   9.32   163.00     6.59     0.00     0.00  Bus7           163.00     6.59   5.105 
		 *  Bus3          13.800  1.0250   4.70    85.00   -10.92     0.00     0.00  Bus9            85.00   -10.92   3.498              
			Bus4          230.00  1.0260  -2.18     0.00     0.00     0.00     0.00  Bus1           -71.64   -24.03   0.185              
																					Bus5            40.94    22.95   0.115 
																					Bus6            30.70     1.08   0.075 
			Bus5          230.00  0.9958  -3.95     0.00     0.00    25.00     5.00  Bus4           -40.68   -38.74   0.115 
																					Bus7           -84.32   -11.26   0.214 
																					Bus50           50.00    22.50   0.138 
																					Bus52           50.00    22.50   0.138 
			Bus6          230.00  1.0128  -3.65     0.00     0.00    90.00    30.00  Bus4           -30.54   -16.60   0.075 
																					Bus9           -59.46   -13.40   0.151 
			Bus7          230.00  1.0258   3.76     0.00     0.00     0.00     0.00  Bus5            86.62    -8.44   0.214 
																					Bus2          -163.00     9.24     0.4              
																					Bus8            76.38    -0.80   0.187 
			Bus8          230.00  1.0159   0.76     0.00     0.00   100.00    35.00  Bus7           -75.90   -10.71   0.187 
																					Bus9           -24.10   -24.29   0.085 
			Bus9          230.00  1.0324   2.00     0.00     0.00     0.00     0.00  Bus6            60.82   -18.14   0.151 
																					Bus8            24.18     3.12   0.085 
																					Bus3           -85.00    15.02    0.21              
			Bus50         230.00  0.9958  -3.95     0.00     0.00     0.00     0.00  Bus5           -50.00   -22.50   0.138 
																					Bus51           50.00    22.50   0.138 
			Bus51         230.00  0.9958  -3.95     0.00     0.00   100.00    45.00  Bus50          -50.00   -22.50   0.138 
																					Bus52          -50.00   -22.50   0.138 
			Bus52         230.00  0.9958  -3.95     0.00     0.00     0.00     0.00  Bus51           50.00    22.50   0.138 
																					Bus5           -50.00   -22.50   0.138
		 */

		 //check the bus voltage magnitude and angle of bus 5, 50, 51, 52
		assertEquals(0.9958, net.getBus("Bus5").getVoltageMag(), 1e-4);
		assertEquals(-3.95/180*Math.PI, net.getBus("Bus5").getVoltageAng(), 1e-4);
		assertEquals(0.9958, net.getBus("Bus50").getVoltageMag(), 1e-4);
		assertEquals(-3.95/180*Math.PI, net.getBus("Bus50").getVoltageAng(), 1e-4);
		assertEquals(0.9958, net.getBus("Bus51").getVoltageMag(), 1e-4);
		assertEquals(-3.95/180*Math.PI, net.getBus("Bus51").getVoltageAng(), 1e-4);
		assertEquals(0.9958, net.getBus("Bus52").getVoltageMag(), 1e-4);
		assertEquals(-3.95/180*Math.PI, net.getBus("Bus52").getVoltageAng(), 1e-4);
	}

	@Test
	public void zbrNRSolverMethod() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = createTestCase();
		
		//net.setDataChecked(true); // so the Z of ZBR lines are not adjusted to the threshold value
		// Instead, we set the network model type to ZBR_MODEL, this will make sure the ZBR lines are treated as zero impedance branches
		net.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);
		net.setZeroZBranchThreshold(1.0E-5);
		
	  	//System.out.println(net.net2String());

		

	  	// use the new ZBRNRSolver loadflow algorithm to perform loadflow calculation
	  	ZbrNrSolver zbrSolver = new ZbrNrSolver(net);
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfCalculator().setNrSolver(zbrSolver);
		algo.setTolerance(1.0E-6);
		algo.setInitBusVoltage(true);
		algo.loadflow();
		assertTrue(net.isLfConverged());
	  	
	  	// output loadflow calculation results
	  	System.out.println(aclfResultBusStyle.apply(net));

		/**
		ZBR branch Bus8->Bus82(0) power injection: (0.5, 0.18749999999999978)
		ZBR branch Bus6->Bus62(0) power injection: (0.29999999999999977, 0.09999999999999996)
		ZBR branch Bus51->Bus52(0) power injection: (-0.4999999999999996, -0.2249999999999999)
		ZBR branch Bus5->Bus50(0) power injection: (0.5000000000000004, 0.22500000000000012)
		ZBR branch Bus80->Bus81(0) power injection: (0.30000000000000004, 0.11250000000000018)
		ZBR branch Bus8->Bus80(0) power injection: (0.30000000000000004, 0.11250000000000018)
		ZBR branch Bus61->Bus62(0) power injection: (-0.29999999999999977, -0.09999999999999996)
		ZBR branch Bus6->Bus60(0) power injection: (0.3000000000000002, 0.10000000000000005)
		ZBR branch Bus50->Bus51(0) power injection: (0.5000000000000004, 0.22500000000000012)
		ZBR branch Bus5->Bus52(0) power injection: (0.4999999999999996, 0.2249999999999999)
		ZBR branch Bus81->Bus82(0) power injection: (-0.09999999999999994, -0.037499999999999804)
		ZBR branch Bus60->Bus61(0) power injection: (0.3000000000000002, 0.10000000000000005)
		 */
		zbrSolver.getZbrPowerMap().forEach((zbrId, power) -> {
			System.out.println("ZBR branch " + zbrId + " power injection: " + power);
			if (zbrId.equals("5->52(0)")) {
				assertEquals(0.5, power.getReal(), 1e-6);
				assertEquals(0.224, power.getImaginary(), 1e-6);
			} else if (zbrId.equals("51->52(0)")) {
				assertEquals(-0.50, power.getReal(), 1e-6);
				assertEquals(-0.225, power.getImaginary(), 1e-6);
			}
			else if (zbrId.equals("50->51(0)")) {
				assertEquals(0.50, power.getReal(), 1e-6);
				assertEquals(0.225, power.getImaginary(), 1e-6);
			} else if (zbrId.equals("5->50(0)")) {
				assertEquals(0.50, power.getReal(), 1e-6);
				assertEquals(0.225, power.getImaginary(), 1e-6);
			}
			else if (zbrId.equals("6->60(0)")) {
				assertEquals(0.30, power.getReal(), 1e-6);
				assertEquals(0.10, power.getImaginary(), 1e-6);
			} else if (zbrId.equals("61->62(0)")) {
				assertEquals(-0.30, power.getReal(), 1e-6);
				assertEquals(-0.10, power.getImaginary(), 1e-6);
			} else if (zbrId.equals("8->80(0)")) {
				assertEquals(0.30, power.getReal(), 1e-6);
				assertEquals(0.1125, power.getImaginary(), 1e-6);
			} else if (zbrId.equals("81->82(0)")) {
				assertEquals(-0.10, power.getReal(), 1e-6);
				assertEquals(-0.0375, power.getImaginary(), 1e-6);
			}
			//Bus80->Bus81(0)
			else if (zbrId.equals("80->81(0)")) {
				assertEquals(0.30, power.getReal(), 1e-6);
				assertEquals(0.1125, power.getImaginary(), 1e-6);
			}



		});

				 //check the bus voltage magnitude and angle of bus 5, 50, 51, 52
		assertEquals(0.9958, net.getBus("Bus5").getVoltageMag(), 1e-4);
		assertEquals(-3.95/180*Math.PI, net.getBus("Bus5").getVoltageAng(), 1e-4);
		assertEquals(0.9958, net.getBus("Bus50").getVoltageMag(), 1e-4);
		assertEquals(-3.95/180*Math.PI, net.getBus("Bus50").getVoltageAng(), 1e-4);
		assertEquals(0.9958, net.getBus("Bus51").getVoltageMag(), 1e-4);
		assertEquals(-3.95/180*Math.PI, net.getBus("Bus51").getVoltageAng(), 1e-4);
		assertEquals(0.9958, net.getBus("Bus52").getVoltageMag(), 1e-4);
		assertEquals(-3.95/180*Math.PI, net.getBus("Bus52").getVoltageAng(), 1e-4);


	    		
    }
	
	private AclfNetwork createTestCase() {
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile("testData/psse/v33/ieee9_zbr_v33.raw"));
		
		AclfModelParser parser = (AclfModelParser)adapter.getModel();
		//parser.stdout();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
  	  		System.out.println("Error: ODM model to InterPSS SimuCtx mapping error");
  	  		return null;
		}		
		
		AclfNetwork net = simuCtx.getAclfNet();
		//System.out.println(net.net2String());		
		
		return net;
	}
	
}
