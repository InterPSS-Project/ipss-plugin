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

package org.interpss.dstab;

import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.MachineState;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;

import org.ieee.odm.ODMObjectFactory;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.mapper.odm.ODMDStabDataMapper;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateVariableRecorder;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class DStab_2Bus extends DStabTestSetupBase {
	
	@Test
	public void OdmTestCase() throws Exception {
		File file = new File("testData/ieee_odm/Tran_2Bus_062011.xml");
		DStabModelParser parser = ODMObjectFactory.createDStabModelParser();
		if (parser.parse(new FileInputStream(file))) {
			//System.out.println(parser.toXmlDoc(false));

			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabDataMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			//System.out.println(simuCtx.getDStabilityNet().net2String());
			
			//IpssLogger.getLogger().setLevel(Level.INFO);
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			//System.out.println(AclfOutFunc.loadFlowSummary(simuCtx.getDStabilityNet()));
				
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
}
