 /*
  * @(#)DStab_2Bus.java   
  *
  * Copyright (C) 2007 www.interpss.org
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
  * @Date 10/15/2007
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.adapter.odm.dstab;

import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.MachineState;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;

import org.ieee.odm.ODMObjectFactory;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.core.dstab.DStabTestSetupBase;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateVariableRecorder;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class DStab_2Bus extends DStabTestSetupBase {
	
	@Test
	public void OdmTestCase() throws Exception {
		//IpssLogger.getLogger().setLevel(Level.INFO);
		DynamicSimuAlgorithm dstabAlgo = IpssAdapter.importNet("testData/odm/dstab/Tran_2Bus_062011.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dstabAlgo.getNetwork()));
		DStabilityNetwork dsNet = dstabAlgo.getNetwork();
		System.out.println(dstabAlgo.getNetwork().net2String());
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.001);
		dstabAlgo.setTotalSimuTimeSec(0.01);
			
		double[] timePoints   = {0.0,    0.004,    0.007,    0.01},
   	      			 machPePoints = {0.3347, 0.3347,   0.3347,   0.3347},
   	      			 machVPoints  = {1.0841, 1.0841,   1.0841,   1.0841};

		StateVariableRecorder stateTestRecorder = new StateVariableRecorder(0.0001);
		stateTestRecorder.addTestRecords("Bus-1-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_EQ1, timePoints, machVPoints);
		stateTestRecorder.addTestRecords("Bus-1-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_PE, timePoints, machPePoints);
		dstabAlgo.setSimuOutputHandler(stateTestRecorder);
			
		//IpssLogger.getLogger().setLevel(Level.INFO);
		//dstabAlgo.setSimuOutputHandler(new TextSimuOutputHandler());
		if (dstabAlgo.initialization()) {
			//System.out.println(simuCtx.getDStabilityNet().net2String());

			System.out.println("Running DStab simulation ...");
			assertTrue(dstabAlgo.performSimulation());
		}
			
		assertTrue(stateTestRecorder.diffTotal("Bus-1-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_EQ1) < 0.0001);
		assertTrue(stateTestRecorder.diffTotal("Bus-1-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_PE) < 0.001);			
	}
}
